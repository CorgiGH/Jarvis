package jarvis.tutor

import jarvis.ChatMessage
import jarvis.OpenRouterChatLlm
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

object DrillGrader {
    private const val GRADE_PROMPT = """You are grading a student's one-line answer to a homework problem.

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

    fun parseGradeJson(raw: String): GradeResult? {
        return try {
            val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw) as? JsonObject
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
        llm: OpenRouterChatLlm,
    ): GradeResult? {
        val userMsg = """Problem: $problemStatement
Expected answer hint: $expectedHint
Student's attempt: $userAttempt
"""
        val (raw, _) = llm.complete(
            listOf(
                ChatMessage("system", GRADE_PROMPT),
                ChatMessage("user", userMsg),
            ),
            maxTokens = 400,
        )
        return parseGradeJson(raw.trim())
    }
}
