package jarvis.tutor

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfProblemExtractorTest {
    private val fixture = Paths.get("src/test/resources/fixtures/sample-tema.pdf")

    companion object {
        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun setupFixture() {
            val target = java.nio.file.Paths.get("src/test/resources/fixtures/sample-tema.pdf")
            if (java.nio.file.Files.exists(target)) return
            java.nio.file.Files.createDirectories(target.parent)
            org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
                val page = org.apache.pdfbox.pdmodel.PDPage()
                doc.addPage(page)
                org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText("Problem 1. Derive MLE for Laplace.")
                    cs.newLineAtOffset(0f, -20f)
                    cs.showText("Problem 2. Compute median.")
                    cs.endText()
                }
                doc.save(target.toFile())
            }
        }
    }

    @Test
    fun `extractText returns non-empty for fixture PDF`() {
        val text = PdfProblemExtractor.extractText(fixture)
        assertTrue(text.isNotBlank())
        assertTrue(text.contains("Problem 1"))
        assertTrue(text.contains("Problem 2"))
    }

    @Test
    fun `extractText returns empty for missing file`() {
        val text = PdfProblemExtractor.extractText(Paths.get("/no/such/file.pdf"))
        assertTrue(text.isBlank())
    }
}
