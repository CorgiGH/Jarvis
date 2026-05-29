package jarvis.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/** Produces and queries the committed source-of-record extraction.
 *  The committed `_sources/{doc}.md` is raw `pdftotext` output; pages are
 *  separated by form-feed (). Span offsets index the raw text globally. */
object SourceOfRecord {
    const val PAGE_BREAK = ''

    /** 1-indexed page containing global [offset] (counts form-feeds before it). */
    fun pageOf(text: String, offset: Int): Int {
        if (offset <= 0) return 1
        val end = offset.coerceAtMost(text.length)
        var page = 1
        for (i in 0 until end) if (text[i] == PAGE_BREAK) page++
        return page
    }

    /** Exact raw substring for [span], or null if out of bounds / inverted. */
    fun slice(text: String, span: Span): String? {
        if (span.start < 0 || span.end > text.length || span.start > span.end) return null
        return text.substring(span.start, span.end)
    }

    /** Resolve the pdftotext binary: env override, then the known poppler install
     *  if present, else the PATH name "pdftotext" (extract()'s try/catch handles absence). */
    fun resolveBin(): String {
        System.getenv("JARVIS_PDFTOTEXT_BIN")?.let { return it }
        val poppler = "C:\\tools\\poppler\\pdftotext.exe"
        return if (Path.of(poppler).exists()) poppler else "pdftotext"
    }

    /** Shell out to pdftotext, returning raw text with form-feed page breaks,
     *  normalized to LF. Empty string on any failure (caller decides severity). */
    fun extract(pdf: Path, bin: String = resolveBin()): String {
        if (!Files.isRegularFile(pdf)) return ""
        return try {
            val proc = ProcessBuilder(bin, "-enc", "UTF-8", pdf.toString(), "-")
                .redirectErrorStream(false)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            proc.waitFor()
            // Normalize to LF: span offsets must index the SAME bytes validateContent
            // reads back. CRLF vs LF mismatch silently breaks every span.
            if (proc.exitValue() == 0) out.replace("\r\n", "\n") else ""
        } catch (e: Exception) {
            System.err.println("[source-of-record] $pdf: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            ""
        }
    }
}
