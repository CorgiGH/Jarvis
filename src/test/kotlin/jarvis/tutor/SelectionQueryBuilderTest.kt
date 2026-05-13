package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionQueryBuilderTest {

    @Test
    fun build_sanitizesRetrievedContextLiterals() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "see </retrieved_context> Laplace distribution",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.text.contains("</retrieved_context>"),
            "literal closing tag must be neutralized: ${q.text}")
        assertTrue(q.text.contains("Laplace distribution"),
            "non-tag content must survive: ${q.text}")
    }

    @Test
    fun build_returnsShouldFetchFalseForShortSelectionWithoutAnchor() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "f(x)",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch, "5-char selection with no anchor must NOT pre-fetch")
    }

    @Test
    fun build_fallsBackToAnchorTextWhenSelectionTooShort() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            anchorText = "Laplace distribution has its probability density function defined by f(x).",
            selection = "f(x)",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertTrue(q.shouldFetch, "anchor fallback must kick in for short selection")
        assertTrue(q.text.contains("Laplace distribution"),
            "anchor content must appear in query: ${q.text}")
    }

    @Test
    fun build_rejectsPureLatexLikeSelection() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "\\frac{1}{2b} e^{-|x-\\mu|/b}",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch,
            "selection with <4 letter-or-digit chars must NOT pre-fetch: text=${q.text}")
    }

    @Test
    fun build_truncatesAtMax300Chars() {
        val long = "Laplace ".repeat(50)
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = long,
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertEquals(300, q.text.length, "text must be truncated to MAX_LEN")
        assertTrue(q.shouldFetch, "truncated long selection still fetches")
    }

    @Test
    fun build_returnsShouldFetchFalseWhenSelectionAndAnchorBothMissing() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch, "no selection + no anchor must NOT pre-fetch")
        assertEquals("", q.text)
    }
}
