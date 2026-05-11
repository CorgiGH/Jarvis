package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import jarvis.HybridRetriever

class CitationExtractorTest {

    private fun hit(path: String, snippet: String = "snip", score: Double = 0.5) =
        HybridRetriever.HybridHit(source = "lexical", id = path, snippet = snippet, score = score)

    @Test
    fun extracts_verified_citations_only() {
        val reply = "The Laplace MLE is median (src: _extras/PS/courses/ps_c4.md). Also see (src: fake/file.md)."
        val hits = listOf(hit("_extras/PS/courses/ps_c4.md"))
        val citations = CitationExtractor.extract(reply, hits)
        assertEquals(1, citations.size, "fake path should be dropped")
        assertEquals("_extras/PS/courses/ps_c4.md", citations[0].path)
    }

    @Test
    fun zero_citations_when_reply_has_no_markers() {
        val reply = "I don't have a source for this."
        val hits = listOf(hit("_extras/PS/concepts.md"))
        assertEquals(emptyList(), CitationExtractor.extract(reply, hits).map { it.path })
    }

    @Test
    fun deduplicates_repeated_citations() {
        val reply = "X (src: _extras/POO/courses/poo_c5.md). Y. Z (src: _extras/POO/courses/poo_c5.md)."
        val hits = listOf(hit("_extras/POO/courses/poo_c5.md"))
        val out = CitationExtractor.extract(reply, hits)
        assertEquals(1, out.size, "duplicate markers should dedupe")
    }

    @Test
    fun citation_score_and_snippet_propagate_from_hit() {
        val reply = "Foo (src: _extras/PS/foo.md)."
        val hits = listOf(hit("_extras/PS/foo.md", snippet = "expected snippet text", score = 0.83))
        val c = CitationExtractor.extract(reply, hits).single()
        assertEquals("expected snippet text", c.snippet)
        assertEquals(0.83, c.score)
    }
}
