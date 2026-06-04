package jarvis.tutor.verify

import jarvis.content.SourceRef
import jarvis.content.Span
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch-2 leg 3 — `SpanClaimRoundTrip`: re-locate `claim.source.quote` via
 * `LiveSourceLocator.locate`, slice the span out of the LIVE source, confirm the slice
 * folds-equal to the quote (deterministic round-trip). Anchored on the REAL committed
 * `content/PA/_sources/pa-lecture-01.md` (49 form-feeds). No LLM, no network — fully deterministic.
 */
class SpanClaimRoundTripTest {

    private val realSource: String =
        Files.readString(Paths.get("content", "PA", "_sources", "pa-lecture-01.md"))

    /** pa-kc-005's first authored quote — crosses a line break (`…must be\n  mentioned.`). */
    private val faithfulQuote =
        "For the values of each data type, the size of representation must be\n  mentioned."

    private fun claimWithQuote(quote: String) = VerificationClaim(
        claimId = "pa-kc-005:DEFINITION:deadbeef",
        kcId = "pa-kc-005",
        subject = "PA",
        kind = ClaimKind.DEFINITION,
        content = quote,
        invariant = null,
        source = SourceRef(doc = "pa-lecture-01", quote = quote, page = 0, span = null),
    )

    @Test
    fun `a faithful quote round-trips - pass with a LIVE page anchor and a resolved span`() {
        val r = SpanClaimRoundTrip(realSource).check(claimWithQuote(faithfulQuote))
        assertTrue(r.pass, "the verbatim quote must round-trip against the live source")
        assertEquals(PageAnchorStatus.LIVE, r.pageAnchorStatus, "exact-after-fold + form-feeds ⇒ LIVE")
        assertNotNull(r.span, "a passing round-trip resolves the RAW span")
        // The resolved span actually slices back to the quote (folds-equal).
        val slice = jarvis.content.SourceOfRecord.slice(realSource, r.span!!)
        assertNotNull(slice)
        assertEquals(
            faithfulQuote.replace(Regex("\\s+"), " ").trim(),
            slice.replace(Regex("\\s+"), " ").trim(),
        )
    }

    @Test
    fun `a mutated quote does NOT round-trip - fail and NONE anchor`() {
        // Swap a load-bearing word so the text no longer appears in the source.
        val mutated = "For the values of each data type, the COLOR of representation must be mentioned."
        val r = SpanClaimRoundTrip(realSource).check(claimWithQuote(mutated))
        assertFalse(r.pass, "a mutated quote must NOT round-trip (proves the net can reject)")
        assertEquals(PageAnchorStatus.NONE, r.pageAnchorStatus, "absent quote ⇒ NONE anchor")
        assertNull(r.span, "no located span for an absent quote")
    }

    @Test
    fun `an absent quote does NOT round-trip`() {
        val r = SpanClaimRoundTrip(realSource).check(
            claimWithQuote("this sentence does not appear anywhere in the lecture at all"),
        )
        assertFalse(r.pass)
        assertEquals(PageAnchorStatus.NONE, r.pageAnchorStatus)
        assertNull(r.span)
    }

    @Test
    fun `a claim with no source quote cannot round-trip - fail, never a silent pass`() {
        val noSource = claimWithQuote(faithfulQuote).copy(source = null)
        val r = SpanClaimRoundTrip(realSource).check(noSource)
        assertFalse(r.pass, "no source ⇒ nothing to re-locate ⇒ fail (FAIL-LOUD)")
        assertEquals(PageAnchorStatus.NONE, r.pageAnchorStatus)
        assertNull(r.span)
    }

    @Test
    fun `the second pa-kc-005 quote also round-trips LIVE`() {
        val q = "There are (at least) three ways to define the size of values:"
        val r = SpanClaimRoundTrip(realSource).check(claimWithQuote(q))
        assertTrue(r.pass)
        assertEquals(PageAnchorStatus.LIVE, r.pageAnchorStatus)
        assertNotNull(r.span)
    }
}
