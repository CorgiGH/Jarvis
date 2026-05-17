package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class GradeResult(
    val correct: Boolean,
    val rubric: Map<String, Boolean>,
    val score: Double,
    val misconception: String?,
    val elaboratedFeedback: String,
)

/**
 * Forensic wrapper around a grading attempt — A.2 plan output.
 *
 * Carries the raw LLM text + resolved model id alongside the parsed
 * [GradeResult] (null on parse failure). Lets the caller route the raw
 * output into the envelope's `llm_output_raw_truncated` field even when
 * parsing fails, so Surface X status_in=parse_error queries can see what
 * the LLM actually returned. Without this, parse_error envelopes carried
 * only the rendered API reply — useless for debugging the grader prompt.
 *
 * See docs/superpowers/plans/2026-05-16-grader-tripwire-reseed.md (A.2) +
 * council 1778881174 (Risk Analyst layer-3 fix).
 */
@Serializable
data class GradeAttempt(
    val parsed: GradeResult?,
    val rawOutput: String,
    val modelResolved: String,
)

object DrillGrader {
    private const val GRADE_PROMPT_TEXT = """You are grading a student's one-line answer to a homework problem.

Return STRICT JSON of this shape:
  {
    "correct": true|false,
    "rubric": {"numeric": true|false, "mechanism": true|false, "justification": true|false},
    "score": 0.0..1.0,
    "misconception": null | "L2_ESTIMATOR_CONFUSION" | "MINIMAX_CONFUSION" | "MODE_CONFUSION" | "OTHER",
    "elaborated_feedback": "short explanation"
  }

Misconception codes (use only when the answer is wrong AND matches the pattern):
- L2_ESTIMATOR_CONFUSION: student computed the mean (Σx/n) when median was expected
- MINIMAX_CONFUSION: student used midrange ((min+max)/2)
- MODE_CONFUSION: student gave the mode when median was expected
- OTHER: wrong but unclassified

correct=true requires the numeric answer is correct. score reflects the rubric (1/3 per dimension).
Output ONLY the JSON object. No code fences."""

    /**
     * Code-grading prompt for R / Python / C++. Score = fraction of rubric items
     * satisfied. correct=true ⟺ all items pass. NO EXECUTION: judge from code
     * alone — read the user's code against the reference + statement + rubric.
     */
    private const val GRADE_PROMPT_CODE = """You are grading a student's code answer to a homework problem.

You do NOT execute the code. Judge from reading the code: would it compile/run
correctly, and does it satisfy each rubric item?

Return STRICT JSON of this shape:
  {
    "correct": true|false,
    "rubric": {"<rubric_item_name>": true|false, ...},
    "score": 0.0..1.0,
    "misconception": null | "<short_code_like_OFF_BY_ONE>" | "OTHER",
    "elaborated_feedback": "1-3 short paragraphs — name what works, what doesn't, what to fix next"
  }

The rubric keys MUST match exactly the rubric item names supplied in the user
message. score = (# rubric items true) / (# rubric items total). correct=true
iff every rubric item is true AND the code is free of syntax errors that would
prevent it from running. Output ONLY the JSON object. No code fences."""

    fun parseGradeJson(raw: String): GradeResult? {
        // Attempt 1: existing strip-fence path. Preserves all prior happy-path tests.
        val direct = tryParse(raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        if (direct != null) return direct

        // Attempt 2: A.7 — CLI providers (claude --print, copilot, relay) silently
        // ignore the responseFormat=json_object hint from A.5 and wrap the JSON in
        // preamble/trailing chatter. Extract first balanced `{...}` block and try
        // parsing that. Type-check inside tryParse still rejects garbage blocks
        // (missing rubric/score/elaborated_feedback → null), so a non-grade
        // `{"meta":...}` first-block returns null rather than a malformed result.
        // Council 1778881174 — Layer 1 fix. Risk Analyst note: bounded by
        // existing typed-field gates.
        val extracted = extractFirstBalancedBraceBlock(raw) ?: return null
        return tryParse(extracted)
    }

    private fun extractFirstBalancedBraceBlock(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun tryParse(cleaned: String): GradeResult? {
        return try {
            val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(cleaned) as? JsonObject
                ?: return null
            val correct = (obj["correct"] as? JsonPrimitive)?.boolean ?: return null
            val rubricObj = obj["rubric"] as? JsonObject ?: return null
            val rubric = rubricObj.mapValues {
                (it.value as? JsonPrimitive)?.boolean ?: return null
            }
            val score = (obj["score"] as? JsonPrimitive)?.doubleOrNull ?: return null
            val misconception = (obj["misconception"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() && it != "null" }
            val feedback = (obj["elaborated_feedback"] as? JsonPrimitive)?.contentOrNull ?: return null
            GradeResult(correct, rubric, score, misconception, feedback)
        } catch (_: Exception) { null }
    }

    /**
     * Sentinel string the frontend sends as `userAttempt` when the user clicks
     * "GIVE UP" without typing anything. Pre-2026-05-17 this string travelled
     * verbatim into the grader prompt; the LLM echoed it into elaborated_feedback,
     * leaking SCREAMING_SNAKE_CASE into the rendered UI. See hot-work item 1 in
     * the 2026-05-17 BRIDGE entry. Defense layers: (1) substitute in prompt
     * input, (2) post-parse output scrub.
     */
    private const val GIVE_UP_SENTINEL = "ATTEMPTED_NOT_SOLVED"
    private const val GIVE_UP_HUMAN = "the student gave up"

    suspend fun grade(
        problemStatement: String,
        userAttempt: String,
        expectedHint: String,
        llm: Llm,
        language: String? = null,
        referenceSolution: String? = null,
        rubricItems: List<String>? = null,
        prediction: String? = null,
        giveUp: Boolean = false,
    ): GradeAttempt {
        val isCode = !language.isNullOrBlank() && language.lowercase() != "text"
        // Sentinel auto-detection: legacy frontend callers may post the sentinel
        // as userAttempt without setting giveUp=true. Treat either signal as
        // give-up to ensure the LLM never sees the raw token.
        val effectiveGiveUp = giveUp || userAttempt.trim() == GIVE_UP_SENTINEL
        val effectiveAttempt = if (effectiveGiveUp) {
            "(no attempt submitted — student gave up before answering)"
        } else userAttempt
        val systemPrompt = if (isCode) GRADE_PROMPT_CODE else GRADE_PROMPT_TEXT
        val userMsg = if (isCode) buildCodeUserMessage(
            language = language!!,
            problemStatement = problemStatement,
            expectedHint = expectedHint,
            referenceSolution = referenceSolution,
            rubricItems = rubricItems.orEmpty(),
            userAttempt = effectiveAttempt,
            prediction = prediction,
            giveUp = effectiveGiveUp,
        ) else buildTextUserMessage(
            problemStatement = problemStatement,
            expectedHint = expectedHint,
            userAttempt = effectiveAttempt,
            prediction = prediction,
            giveUp = effectiveGiveUp,
        )
        // A.6 — code-grading produces longer JSON (multi-line elaborated_feedback
        // citing specific code lines + dynamic rubric_chip_text), so bump the
        // cap to 1200 for code paths. Text grading stays at 600 — its rubric
        // is fixed at 3 booleans and feedback is a short one-liner.
        val effectiveMaxTokens = if (isCode) 1200 else 600
        val (raw, modelResolved) = llm.complete(
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userMsg),
            ),
            maxTokens = effectiveMaxTokens,
            // A.5 — ask the substrate to emit JSON-only output. OpenRouter
            // honors this on `:free` models that expose it; CLI providers
            // (claude --print, copilot, relay) silently ignore the hint and
            // fall through to the A.7 regex-extract path. Plan:
            // docs/superpowers/plans/2026-05-16-grader-tripwire-reseed.md
            responseFormat = "json_object",
        )
        val parsed = parseGradeJson(raw.trim())
        if (parsed == null) {
            System.err.println("[drill grader] parse-fail lang=$language raw=${raw.take(600).replace('\n', ' ')}")
        }
        // Defense layer 2: scrub residual sentinel from LLM-emitted text even if
        // the prompt translation worked. Case-insensitive replace because some
        // models normalize-case unpredictably.
        val sanitized = parsed?.copy(
            elaboratedFeedback = scrubSentinel(parsed.elaboratedFeedback),
            misconception = parsed.misconception?.let { m ->
                if (m.equals(GIVE_UP_SENTINEL, ignoreCase = true)) null else m
            },
        )
        return GradeAttempt(parsed = sanitized, rawOutput = raw, modelResolved = modelResolved)
    }

    private fun scrubSentinel(s: String): String =
        s.replace(Regex(Regex.escape(GIVE_UP_SENTINEL), RegexOption.IGNORE_CASE), GIVE_UP_HUMAN)

    private fun buildCodeUserMessage(
        language: String,
        problemStatement: String,
        expectedHint: String,
        referenceSolution: String?,
        rubricItems: List<String>,
        userAttempt: String,
        prediction: String?,
        giveUp: Boolean = false,
    ): String {
        val sb = StringBuilder()
        sb.append("Language: ").append(language).append('\n')
        sb.append("Problem:\n").append(problemStatement).append("\n\n")
        if (expectedHint.isNotBlank()) {
            sb.append("Expected answer hint:\n").append(expectedHint).append("\n\n")
        }
        if (!referenceSolution.isNullOrBlank()) {
            sb.append("Reference solution (one correct shape — alternative valid solutions exist):\n```")
                .append(language).append('\n').append(referenceSolution).append("\n```\n\n")
        }
        if (rubricItems.isNotEmpty()) {
            sb.append("Rubric items (return one boolean per item under these exact keys):\n")
            for (item in rubricItems) sb.append("- ").append(item).append('\n')
            sb.append('\n')
        }
        if (!prediction.isNullOrBlank()) {
            sb.append("Student's prior prediction (plain-language commitment BEFORE they wrote code — testing-effect / generation-effect anchor):\n")
                .append(prediction.trim()).append("\n\n")
            sb.append("In your elaborated_feedback, briefly name how the student's prediction aligns or diverges from what their code actually does — recognize the commitment first, then evaluate the code against the rubric.\n\n")
        }
        if (giveUp) {
            sb.append("Note: the student gave up without submitting code. ")
                .append("All rubric items should be marked false. ")
                .append("In elaborated_feedback, walk through what a correct solution would look like against each rubric item — frame as a teaching pass, not a critique. Refer to the student as \"the student\" or \"you\"; do not echo any internal status code.\n\n")
        }
        sb.append("Student's code:\n```").append(language).append('\n').append(userAttempt).append("\n```\n")
        return sb.toString()
    }

    private fun buildTextUserMessage(
        problemStatement: String,
        expectedHint: String,
        userAttempt: String,
        prediction: String?,
        giveUp: Boolean = false,
    ): String {
        val sb = StringBuilder()
        sb.append("Problem: ").append(problemStatement).append('\n')
        sb.append("Expected answer hint: ").append(expectedHint).append('\n')
        if (!prediction.isNullOrBlank()) {
            sb.append("Student's prior prediction (committed before final attempt): ")
                .append(prediction.trim()).append('\n')
        }
        if (giveUp) {
            sb.append("Note: the student gave up without submitting an attempt. ")
                .append("Mark all rubric items false and use elaborated_feedback to explain ")
                .append("what the correct answer should be, treating it as a teaching pass. ")
                .append("Refer to \"the student\" or \"you\"; do not echo any internal status code.\n")
        }
        sb.append("Student's attempt: ").append(userAttempt).append('\n')
        return sb.toString()
    }
}
