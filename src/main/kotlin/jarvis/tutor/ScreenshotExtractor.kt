package jarvis.tutor

import jarvis.VisionLlm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Layer B Task 1 — vision-LLM screenshot extractor.
 *
 * The browser captures a frame via `getDisplayMedia` (or the daemon
 * pushes a region capture), POSTs base64-encoded PNG to
 * `/api/v1/sensor/screenshot`. The route hands the bytes here; we
 * craft a structured-extraction prompt, send to the configured
 * VisionLlm, parse the JSON reply.
 *
 * The reply schema is intentionally tiny — these are the four fields
 * that turn a screenshot into a useful sensor event for the tutor:
 *  - file_path: where the user is editing
 *  - cursor: line/col under the caret
 *  - console_output: tail of any visible terminal/log
 *  - error: any visible error/exception text
 *
 * Reply keys not in this set are ignored. Missing keys parse to null.
 *
 * Council R3 follow-up: prompt is deliberately strict about "JSON
 * object only, no markdown fences" because vision LLMs love to wrap
 * structured replies in ```json blocks. We strip those defensively in
 * [parseExtraction] before parsing.
 */
@Serializable
data class ScreenshotExtraction(
    val filePath: String? = null,
    val cursor: CursorPosition? = null,
    val consoleOutput: String? = null,
    val error: String? = null,
    /** Raw LLM reply for forensics; bounded length. */
    val rawReply: String = "",
)

@Serializable
data class CursorPosition(val line: Int, val col: Int)

object ScreenshotExtractor {

    /** Stable across edits — vision LLMs respond more reliably to the same
     *  exact prompt; making this configurable per-call would degrade
     *  extraction accuracy without a clear use case. */
    const val EXTRACTION_PROMPT: String = """You are a screen-extraction sensor for a programming tutor. Look at this screenshot and reply with ONLY a JSON object — no markdown fences, no preamble, no trailing prose. Use these keys:

  file_path        : string|null — path of the file the user appears to be viewing or editing (look at title bar, tab labels, breadcrumbs). null if not visible.
  cursor           : {"line": int, "col": int}|null — visible cursor / caret position if shown (1-indexed line, 0-indexed column). null if not visible.
  console_output   : string|null — last few lines of any visible terminal / REPL / log pane (max 1000 chars). null if no console visible.
  error            : string|null — any visible error / exception / diagnostic message text. null if no error visible.

Reply with ONE JSON object containing exactly these four keys. Use null for any key whose value is not visible. Do not invent values."""

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extract(
        llm: VisionLlm,
        imageBase64: String,
        mediaType: String = "image/png",
        promptHint: String? = null,
    ): ScreenshotExtraction {
        val finalPrompt = if (promptHint.isNullOrBlank()) EXTRACTION_PROMPT
            else EXTRACTION_PROMPT + "\n\nAdditional hint from caller: " + promptHint.take(500)
        val raw = llm.analyze(
            prompt = finalPrompt,
            imageBase64 = imageBase64,
            mediaType = mediaType,
            maxTokens = 1024,
        )
        return parseExtraction(raw)
    }

    /** Robust parse: strips ```json fences, then decodes the first
     *  JSON object found in the reply. Returns an extraction with the
     *  raw reply preserved so callers can audit / debug. */
    internal fun parseExtraction(raw: String): ScreenshotExtraction {
        val trimmed = stripFences(raw).trim()
        val rawTrunc = raw.take(2000)
        if (trimmed.isEmpty()) {
            return ScreenshotExtraction(rawReply = rawTrunc)
        }
        val obj = try {
            json.parseToJsonElement(trimmed).jsonObject
        } catch (_: Exception) {
            // Find the first { ... } block in the reply.
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start < 0 || end <= start) return ScreenshotExtraction(rawReply = rawTrunc)
            val sub = trimmed.substring(start, end + 1)
            try { json.parseToJsonElement(sub).jsonObject }
            catch (_: Exception) { return ScreenshotExtraction(rawReply = rawTrunc) }
        }
        val filePath = obj["file_path"]?.asStringOrNull()
        val consoleOutput = obj["console_output"]?.asStringOrNull()?.take(1000)
        val error = obj["error"]?.asStringOrNull()?.take(2000)
        val cursor = (obj["cursor"] as? JsonObject)?.let { c ->
            val line = c["line"]?.let { (it as? JsonPrimitive)?.intOrNull }
            val col = c["col"]?.let { (it as? JsonPrimitive)?.intOrNull }
            if (line != null && col != null) CursorPosition(line, col) else null
        }
        return ScreenshotExtraction(filePath, cursor, consoleOutput, error, rawTrunc)
    }

    private fun stripFences(raw: String): String {
        var s = raw.trim()
        // Common patterns: ```json\n{...}\n``` or ```\n{...}\n```
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
        }
        if (s.endsWith("```")) {
            s = s.removeSuffix("```").trim()
        }
        return s
    }

    private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? =
        when (this) {
            is JsonPrimitive -> if (this.contentOrNull == "null" && !this.isString) null
                else this.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
}
