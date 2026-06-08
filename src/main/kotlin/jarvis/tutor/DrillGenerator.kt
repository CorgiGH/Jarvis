package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

object DrillGenerator {
    const val CONFIDENCE_THRESHOLD = 0.7
    private val json = Json { ignoreUnknownKeys = true }

    data class Bundle(val problem: Problem, val content: DrillContentDto)
    data class GenerateResult(val bundles: List<Bundle>, val rejectReasons: List<String>)
    private data class CriticVerdict(val confidence: Double, val grounded: Boolean, val leak: Boolean, val solvable: Boolean)

    private fun shapePrompt(shape: String, kc: KnowledgeConcept, sources: List<String>): String = """
        You are authoring ONE fresh practice drill for the concept "${kc.name_en}" (${kc.name_ro}), bloom level ${kc.bloom_level}, difficulty ${kc.difficulty}.
        Shape: $shape.
        Ground it ONLY in this source material (do not invent facts beyond it):
        ${sources.joinToString("\n") { "- $it" }}
        Output ONE JSON object, no prose, with keys:
        statement (the problem; do NOT state the answer in it),
        ${if (shape == "computational") "canonical_answer (the exact numeric/string answer)," else "rubric_items (array of point-split grading criteria),"}
        reference_solution (or null), worked (a worked solution), definition (a one-sentence concept definition),
        check (a short transfer question), expected_answer_hint (a brief hint).
    """.trimIndent()

    private fun parseCritic(raw: String): CriticVerdict? {
        val block = DrillGenParser.firstBalancedBraceBlock(raw) ?: return null
        val obj = try { json.parseToJsonElement(block) as? JsonObject } catch (_: Exception) { null } ?: return null
        val conf = (obj["confidence"] as? JsonPrimitive)?.doubleOrNull ?: return null
        // Fail-closed defaults: grounded/solvable missing → false (reject); leak missing → true (assume leak present).
        fun b(k: String, failClosedDefault: Boolean) = (obj[k] as? JsonPrimitive)?.booleanOrNull ?: failClosedDefault
        return CriticVerdict(conf, b("grounded", false), b("leak", true), b("solvable", false))
    }

    private fun farTransferPrompt(kc: KnowledgeConcept, stem: String, sources: List<String>): String = """
        You are authoring ONE Gick-Holyoak FAR-TRANSFER drill for the concept "${kc.name_en}" (${kc.name_ro}), bloom level ${kc.bloom_level}, difficulty ${kc.difficulty}.
        A far-transfer drill applies the SAME underlying principle in a SUPERFICIALLY DIFFERENT surface context, to test transfer rather than recall.
        Anchor it to this authored far-transfer stem (keep its surface domain): "$stem".
        Ground the underlying principle ONLY in this source material (do not invent facts beyond it):
        ${sources.joinToString("\n") { "- $it" }}
        Output ONE JSON object, no prose, with keys:
        statement (the far-transfer problem in the stem's domain; do NOT state the answer in it),
        rubric_items (array of point-split grading criteria),
        reference_solution (or null), worked (a worked solution), definition (a one-sentence concept definition),
        check (a short transfer question), expected_answer_hint (a brief hint).
    """.trimIndent()

    /**
     * Phase-3 GROUP 7 (TASK P3-GHOST-FIELDS(b)) — the FAR-TRANSFER generator branch. Reads the KC's
     * authored `far_transfer_stem` (CHANGE 3) and emits a far-transfer drill (Bundle with
     * `shape = "far-transfer"`) so the field is WIRED to a generator branch + surfaced in `DrillStack`
     * (served exactly like any other persisted Problem; its `is_far_transfer` attempt flag is the
     * existing `ApiDrillGradeRequest.is_far_transfer` path).
     *
     * H16 (no ghost): a KC with NO `far_transfer_stem` yields ZERO far-transfer bundles (a single
     * "no far-transfer stem authored" reject reason) — never a throw, never a hallucinated stem.
     * The same leak + cross-family-critic guards as [generate] apply (no self-solve: far-transfer
     * drills are rubric-graded, not single-canonical-answer).
     */
    suspend fun farTransfer(
        kc: KnowledgeConcept,
        sources: List<String>,
        generator: Llm,
        critic: Llm,
    ): GenerateResult {
        val stem = kc.far_transfer_stem?.takeIf { it.isNotBlank() }
            ?: return GenerateResult(emptyList(), listOf("no far-transfer stem authored for ${kc.id}"))
        val bundles = mutableListOf<Bundle>()
        val rejects = mutableListOf<String>()
        val (raw, genModelId) = generator.complete(
            listOf(ChatMessage("user", farTransferPrompt(kc, stem, sources))),
            maxTokens = 1200, responseFormat = "json_object",
        )
        val d = DrillGenParser.parse(raw)
        if (d == null) { rejects += "parse failure"; return GenerateResult(bundles, rejects) }
        // leak guard (rubric-graded ⇒ no self-solve reconcile).
        if (d.canonicalAnswer != null && d.statement.contains(d.canonicalAnswer, ignoreCase = true)) {
            rejects += "leak: stem contains canonical answer"; return GenerateResult(bundles, rejects)
        }
        val (criticRaw, criticModel) = critic.complete(
            listOf(ChatMessage("user",
                "Review this far-transfer drill. Reply ONLY JSON {confidence:0..1, grounded:bool, leak:bool, solvable:bool}.\nSOURCES:\n${sources.joinToString("\n")}\nDRILL:\n${d.statement}\nANSWER: ${d.canonicalAnswer ?: d.rubricItems.joinToString("; ")}")),
            maxTokens = 200,
        )
        val v = parseCritic(criticRaw)
        if (v == null) { rejects += "critic parse failure ($criticModel)"; return GenerateResult(bundles, rejects) }
        if (v.confidence < CONFIDENCE_THRESHOLD || !v.grounded || v.leak || !v.solvable) {
            rejects += "critic rejected (conf=${v.confidence}, grounded=${v.grounded}, leak=${v.leak}, solvable=${v.solvable})"
            return GenerateResult(bundles, rejects)
        }
        val pid = "ft-${kc.id}"
        bundles += Bundle(
            problem = Problem(problemId = pid, page = 0, statement = d.statement,
                kcIds = listOf(kc.id), rubricItems = d.rubricItems, referenceSolution = d.referenceSolution,
                canonicalAnswer = d.canonicalAnswer, shape = "far-transfer", modelTag = genModelId),
            content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                referenceSolution = d.referenceSolution,
                rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id,
                // Trust-leak fix: a generated drill's worked/definition is LLM-authored + unaudited.
                // Stamp it generated so the faithful badge can never span it (set at generate time).
                provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)),
        )
        return GenerateResult(bundles, rejects)
    }

    /** Generate up to [count] drills for [kc] of [shape]; each must pass leak + self-solve + cross-family critic. */
    suspend fun generate(
        kc: KnowledgeConcept,
        sources: List<String>,
        shape: String,
        count: Int,
        generator: Llm,
        critic: Llm,
    ): GenerateResult {
        val bundles = mutableListOf<Bundle>()
        val rejects = mutableListOf<String>()
        for (i in 0 until count) {
            // P3-GEN (D1, CONTRADICTION F2): the generator runs Claude via the relay; its
            // complete() returns (text, modelId). modelId is the WRITER the relay named — it is
            // what stamps Problem.modelTag below. NEVER the criticUsed="relay/claude" literal,
            // NEVER left null. The live serve path reads the persisted Problem and never re-derives.
            val (raw, genModelId) = generator.complete(listOf(ChatMessage("user", shapePrompt(shape, kc, sources))), maxTokens = 1200, responseFormat = "json_object")
            val d = DrillGenParser.parse(raw)
            if (d == null) { rejects += "parse failure"; continue }
            // (1) answer-leak: the stem must not contain its own canonical answer.
            if (d.canonicalAnswer != null && d.statement.contains(d.canonicalAnswer, ignoreCase = true)) { rejects += "leak: stem contains canonical answer"; continue }
            // (2) self-solve reconcile (computational only).
            if (d.canonicalAnswer != null) {
                val (solvedRaw, _) = generator.complete(listOf(ChatMessage("user", "Solve and reply with ONLY the final answer:\n${d.statement}")), maxTokens = 200)
                if (!GradeScoring.answerMatches(d.canonicalAnswer, solvedRaw.trim())) { rejects += "self-solve mismatch (${d.canonicalAnswer} vs ${solvedRaw.trim().take(40)})"; continue }
            }
            // (3) cross-family critic.
            val (criticRaw, criticModel) = critic.complete(listOf(ChatMessage("user",
                "Review this drill. Reply ONLY JSON {confidence:0..1, grounded:bool, leak:bool, solvable:bool}.\nSOURCES:\n${sources.joinToString("\n")}\nDRILL:\n${d.statement}\nANSWER: ${d.canonicalAnswer ?: d.rubricItems.joinToString("; ")}")), maxTokens = 200)
            val v = parseCritic(criticRaw)
            if (v == null) { rejects += "critic parse failure ($criticModel)"; continue }
            if (v.confidence < CONFIDENCE_THRESHOLD || !v.grounded || v.leak || !v.solvable) { rejects += "critic rejected (conf=${v.confidence}, grounded=${v.grounded}, leak=${v.leak}, solvable=${v.solvable})"; continue }
            // accept.
            val pid = "gen-${kc.id}-$i"
            bundles += Bundle(
                problem = Problem(problemId = pid, page = 0, statement = d.statement,
                    kcIds = listOf(kc.id), rubricItems = d.rubricItems, referenceSolution = d.referenceSolution,
                    canonicalAnswer = d.canonicalAnswer, shape = shape,
                    // P3-GEN: stamp the relay-RETURNED model id (the WRITER), set at generate+persist time.
                    modelTag = genModelId),
                content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                    check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                    referenceSolution = d.referenceSolution,
                    rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id,
                    // Trust-leak fix: a generated drill's worked/definition is LLM-authored + unaudited.
                    // Stamp it generated so the faithful badge can never span it (set at generate time).
                    provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)),
            )
        }
        return GenerateResult(bundles, rejects)
    }
}
