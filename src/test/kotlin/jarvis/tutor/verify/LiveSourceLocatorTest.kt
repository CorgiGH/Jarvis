package jarvis.tutor.verify

import jarvis.content.SourceOfRecord
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §J / H7 — fuzzy-locate a quote in the whitespace-folded source, map back to RAW offsets,
 * round-trip via SourceOfRecord.slice. Anchored on the REAL committed source (49 form-feeds).
 */
class LiveSourceLocatorTest {

    private val realSource: String =
        Files.readString(Paths.get("content", "PA", "_sources", "pa-lecture-01.md"))

    /** pa-kc-005's first authored quote — crosses a line break (`…must be\n  mentioned.`). */
    private val paKc005Quote =
        "For the values of each data type, the size of representation must be\n  mentioned."

    private fun fold(s: String) = s.replace(Regex("\\s+"), " ").trim()

    @Test
    fun `the real source carries form-feed page breaks`() {
        assertTrue(
            realSource.indexOf(SourceOfRecord.PAGE_BREAK) >= 0,
            "the committed pa-lecture-01.md must carry form-feeds for the LIVE anchor",
        )
    }

    @Test
    fun `locates pa-kc-005 quote across a line break - span round-trips, LIVE, page greater than 0`() {
        val r = LiveSourceLocator.locate(realSource, paKc005Quote)

        val span = r.span
        assertNotNull(span, "the cross-line quote must locate after whitespace-fold")

        // RAW slice round-trips: folding the raw slice equals folding the quote.
        val rawSlice = SourceOfRecord.slice(realSource, span)
        assertNotNull(rawSlice, "the mapped RAW span must be in bounds")
        assertEquals(fold(paKc005Quote), fold(rawSlice), "folded RAW slice must equal the folded quote")

        // Exact-after-fold + form-feeds present ⇒ LIVE, with a real page anchor.
        assertEquals(0, r.fuzzyDistance, "exact-after-fold ⇒ distance 0")
        assertEquals(PageAnchorStatus.LIVE, r.pageAnchorStatus)
        assertNotNull(r.page)
        assertTrue(r.page!! > 0, "page must be a real 1-indexed page")
    }

    @Test
    fun `a quote with collapsed interior whitespace still locates`() {
        // Same quote, already whitespace-collapsed by the caller: must still locate against the
        // folded source (the fold is symmetric).
        val collapsed = "For the values of each data type, the size of representation must be mentioned."
        val r = LiveSourceLocator.locate(realSource, collapsed)
        assertNotNull(r.span, "a pre-collapsed quote must still locate")
        val rawSlice = SourceOfRecord.slice(realSource, r.span!!)
        assertNotNull(rawSlice)
        assertEquals(fold(collapsed), fold(rawSlice))
        // Source HAS form-feeds and match is exact-after-fold ⇒ LIVE.
        assertEquals(PageAnchorStatus.LIVE, r.pageAnchorStatus)
    }

    @Test
    fun `a source with no form-feed yields DEGRADED and a null page`() {
        // A clean stand-in source with NO form-feeds: located, but no page anchor exists.
        val noFf = "On data types. For the values of each data type, the size of representation must be mentioned. End."
        val r = LiveSourceLocator.locate(noFf, paKc005Quote)
        assertNotNull(r.span, "must still locate in a form-feed-less source")
        assertEquals(PageAnchorStatus.DEGRADED, r.pageAnchorStatus, "no form-feed ⇒ DEGRADED, never LIVE")
        assertNull(r.page, "no form-feed ⇒ no page anchor")
        // Round-trip still holds.
        val rawSlice = SourceOfRecord.slice(noFf, r.span!!)
        assertEquals(fold(paKc005Quote), fold(rawSlice!!))
    }

    @Test
    fun `an absent quote yields span null and NONE`() {
        val r = LiveSourceLocator.locate(realSource, "this sentence does not appear anywhere in the lecture at all")
        assertNull(r.span, "an absent quote must not locate")
        assertEquals(PageAnchorStatus.NONE, r.pageAnchorStatus)
        assertNull(r.page)
    }

    @Test
    fun `the second pa-kc-005 quote also locates LIVE`() {
        // "There are (at least) three ways to define the size of values:" appears verbatim.
        val q = "There are (at least) three ways to define the size of values:"
        val r = LiveSourceLocator.locate(realSource, q)
        assertNotNull(r.span)
        assertEquals(PageAnchorStatus.LIVE, r.pageAnchorStatus)
        assertEquals(fold(q), fold(SourceOfRecord.slice(realSource, r.span!!)!!))
    }
}
