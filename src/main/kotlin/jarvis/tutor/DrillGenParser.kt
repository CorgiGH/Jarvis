package jarvis.tutor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Parsed generator output — carries BOTH grading fields and render fields. */
data class GeneratedDrill(
    val statement: String,
    val canonicalAnswer: String?,
    val rubricItems: List<String>,
    val referenceSolution: String?,
    val worked: String,
    val definition: String,
    val check: String,
    val expectedAnswerHint: String,
)

object DrillGenParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Extract the first balanced {...} block (CLI/relay providers wrap JSON in prose). */
    private fun firstBalancedBraceBlock(s: String): String? {
        val start = s.indexOf('{'); if (start < 0) return null
        var depth = 0; var inString = false; var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) { '{' -> depth++; '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) } }
        }
        return null
    }

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun arr(o: JsonObject, k: String): List<String> =
        (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    fun parse(raw: String): GeneratedDrill? {
        val block = firstBalancedBraceBlock(raw) ?: return null
        val obj = try { json.parseToJsonElement(block) as? JsonObject } catch (_: Exception) { null } ?: return null
        val statement = str(obj, "statement") ?: return null
        val worked = str(obj, "worked") ?: return null
        val definition = str(obj, "definition") ?: return null
        val check = str(obj, "check") ?: return null
        val hint = str(obj, "expected_answer_hint") ?: ""
        return GeneratedDrill(
            statement = statement,
            canonicalAnswer = str(obj, "canonical_answer"),
            rubricItems = arr(obj, "rubric_items"),
            referenceSolution = str(obj, "reference_solution"),
            worked = worked, definition = definition, check = check, expectedAnswerHint = hint,
        )
    }
}
