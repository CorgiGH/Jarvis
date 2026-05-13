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

    @Test
    fun build_rejectsSelectionThatMatchesDrillStatementVerbatim() {
        val drill = "Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati."
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = drill,
            drillStatement = drill,
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertTrue(q.drillSelfPaste, "verbatim drill paste must set drillSelfPaste flag")
        assertFalse(q.shouldFetch, "drill self-paste must NOT pre-fetch")
    }

    @Test
    fun build_rejectsSelectionThatMatchesDrillStatementWithNoise() {
        // Selection has extra whitespace + punctuation noise but is still the drill.
        val drill = "Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati."
        val noisy = "   Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati!!  "
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = noisy,
            drillStatement = drill,
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertTrue(q.drillSelfPaste, "drill paste with whitespace/punctuation noise must still trip the gate")
    }

    @Test
    fun build_allowsSelectionThatIsSubstantiallyDifferentFromDrill() {
        // Real comprehension question: small substring of drill ("distributia Laplace") = 2 tokens.
        // Drill has 12 tokens. Jaccard = 2/12 = 0.167 — well below 0.7 threshold.
        val drill = "Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati."
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "distributia Laplace",
            drillStatement = drill,
            userQuestion = "what is the laplace distribution?"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.drillSelfPaste, "narrow concept selection must NOT trip the drill-paste gate")
        assertTrue(q.shouldFetch, "narrow concept selection should still pre-fetch")
    }

    @Test
    fun build_skipsJaccardGateWhenDrillStatementMissing() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati.",
            drillStatement = null,
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.drillSelfPaste, "no drillStatement means no gate")
        assertTrue(q.shouldFetch, "long valid selection still pre-fetches when no drill context provided")
    }
}
