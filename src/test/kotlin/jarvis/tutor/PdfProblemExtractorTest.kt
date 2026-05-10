package jarvis.tutor

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `parseLlmJson handles well-formed array`() {
        val raw = """[
      {"problem_id":"A1","page":4,"statement":"derive MLE","equation_refs":[],"data_givens":[]},
      {"problem_id":"A2","page":6,"statement":"compute median","equation_refs":["med"],"data_givens":["x=(1,2,5)"]}
    ]""".trimIndent()
        val problems = PdfProblemExtractor.parseLlmJson(raw)
        assertEquals(2, problems.size)
        assertEquals("A1", problems[0].problemId)
        assertEquals(4, problems[0].page)
        assertEquals("compute median", problems[1].statement)
        assertEquals(listOf("x=(1,2,5)"), problems[1].dataGivens)
    }

    @Test
    fun `parseLlmJson returns empty on malformed`() {
        assertTrue(PdfProblemExtractor.parseLlmJson("not json").isEmpty())
        assertTrue(PdfProblemExtractor.parseLlmJson("{\"single\":\"object\"}").isEmpty())
    }

    @Test
    fun `parseLlmJson tolerates missing optional fields`() {
        val raw = """[{"problem_id":"X1","page":1,"statement":"do thing"}]"""
        val problems = PdfProblemExtractor.parseLlmJson(raw)
        assertEquals(1, problems.size)
        assertEquals(emptyList(), problems[0].equationRefs)
        assertEquals(emptyList(), problems[0].dataGivens)
    }
}
