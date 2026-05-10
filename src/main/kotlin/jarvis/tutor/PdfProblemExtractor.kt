package jarvis.tutor

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import java.nio.file.Path

object PdfProblemExtractor {
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
}
