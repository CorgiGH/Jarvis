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
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
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

    suspend fun grade(
        problemStatement: String,
        userAttempt: String,
        expectedHint: String,
        llm: Llm,
        language: String? = null,
        referenceSolution: String? = null,
        rubricItems: List<String>? = null,
        prediction: String? = null,
    ): GradeAttempt {
        val isCode = !language.isNullOrBlank() && language.lowercase() != "text"
        val systemPrompt = if (isCode) GRADE_PROMPT_CODE else GRADE_PROMPT_TEXT
        val userMsg = if (isCode) buildCodeUserMessage(
            language = language!!,
            problemStatement = problemStatement,
            expectedHint = expectedHint,
            referenceSolution = referenceSolution,
            rubricItems = rubricItems.orEmpty(),
            userAttempt = userAttempt,
            prediction = prediction,
        ) else buildTextUserMessage(
            problemStatement = problemStatement,
            expectedHint = expectedHint,
            userAttempt = userAttempt,
            prediction = prediction,
        )
        val (raw, modelResolved) = llm.complete(
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userMsg),
            ),
            maxTokens = 600,
        )
        val parsed = parseGradeJson(raw.trim())
        if (parsed == null) {
            System.err.println("[drill grader] parse-fail lang=$language raw=${raw.take(600).replace('\n', ' ')}")
        }
        return GradeAttempt(parsed = parsed, rawOutput = raw, modelResolved = modelResolved)
    }

    private fun buildCodeUserMessage(
        language: String,
        problemStatement: String,
        expectedHint: String,
        referenceSolution: String?,
        rubricItems: List<String>,
        userAttempt: String,
        prediction: String?,
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
        sb.append("Student's code:\n```").append(language).append('\n').append(userAttempt).append("\n```\n")
        return sb.toString()
    }

    private fun buildTextUserMessage(
        problemStatement: String,
        expectedHint: String,
        userAttempt: String,
        prediction: String?,
    ): String {
        val sb = StringBuilder()
        sb.append("Problem: ").append(problemStatement).append('\n')
        sb.append("Expected answer hint: ").append(expectedHint).append('\n')
        if (!prediction.isNullOrBlank()) {
            sb.append("Student's prior prediction (committed before final attempt): ")
                .append(prediction.trim()).append('\n')
        }
        sb.append("Student's attempt: ").append(userAttempt).append('\n')
        return sb.toString()
    }
}
