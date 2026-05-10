package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class Problem(
    val problemId: String,
    val page: Int,
    val statement: String,
    val equationRefs: List<String> = emptyList(),
    val dataGivens: List<String> = emptyList(),
)

object PdfProblemExtractor {
    fun parseLlmJson(raw: String): List<Problem> {
        val json = try {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(raw)
        } catch (_: Exception) { return emptyList() }
        val arr = (json as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val pid = (obj["problem_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val page = (obj["page"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val stmt = (obj["statement"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val eqs = (obj["equation_refs"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            } ?: emptyList()
            val givens = (obj["data_givens"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            } ?: emptyList()
            Problem(pid, page, stmt, eqs, givens)
        }
    }

    /** Pure text-layer extraction. Returns empty string on any failure
     *  (missing file, encrypted, scanned-image-only). */
    fun extractText(pdf: Path): String {
        if (!Files.isRegularFile(pdf)) return ""
        return try {
            PDDocument.load(pdf.toFile()).use { doc ->
                if (doc.isEncrypted) return ""
                PDFTextStripper().getText(doc)
            }
        } catch (e: Exception) {
            System.err.println("[pdf-extractor] $pdf: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            ""
        }
    }

    private const val EXTRACT_PROMPT = """You are reading a homework PDF and identifying the numbered problems.

Return STRICT JSON: an array where each entry has shape:
  {"problem_id": "A1", "page": 4, "statement": "...", "equation_refs": [...], "data_givens": [...]}

- problem_id: a short stable id like A1, A2, B1, P1, 1, 2, etc. Use what the PDF uses.
- page: 1-indexed page number where the problem starts
- statement: the verbatim problem statement, max ~400 chars
- equation_refs: optional list of equation numbers/labels mentioned
- data_givens: optional list of concrete data the problem provides

Output ONLY the JSON array. No prose. No code fences."""

    suspend fun identifyProblems(pdf: Path, llm: Llm): List<Problem> {
        val text = extractText(pdf)
        if (text.isBlank()) return emptyList()
        val capped = text.take(20_000)
        val (raw, _) = llm.complete(
            listOf(
                ChatMessage("system", EXTRACT_PROMPT),
                ChatMessage("user", capped),
            ),
            maxTokens = 2000,
        )
        return parseLlmJson(raw.trim())
    }
}
