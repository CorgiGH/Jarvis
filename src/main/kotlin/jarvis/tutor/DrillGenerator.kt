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
            val (raw, _) = generator.complete(listOf(ChatMessage("user", shapePrompt(shape, kc, sources))), maxTokens = 1200, responseFormat = "json_object")
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
                    canonicalAnswer = d.canonicalAnswer, shape = shape),
                content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                    check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                    referenceSolution = d.referenceSolution,
                    rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id),
            )
        }
        return GenerateResult(bundles, rejects)
    }
}
