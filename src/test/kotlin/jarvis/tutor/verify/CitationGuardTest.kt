package jarvis.tutor.verify

import jarvis.content.SourceRef
import jarvis.content.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** §Q — the per-emit citation chokepoint. FAIL-LOUD: a null source THROWS; resolved ⇒ CitedClaim. */
class CitationGuardTest {

    private fun claim(source: SourceRef?) = VerificationClaim(
        claimId = "pa-kc-005:DEFINITION:deadbeef",
        kcId = "pa-kc-005",
        subject = "PA",
        kind = ClaimKind.DEFINITION,
        content = "the size of representation must be mentioned",
        invariant = null,
        source = source,
    )

    @Test
    fun `a claim with a null source THROWS - never ships un-cited`() {
        assertFailsWith<IllegalStateException> {
            CitationGuard.attach(claim(source = null))
        }
    }

    @Test
    fun `a resolved span-anchored claim becomes a CitedClaim carrying doc span quote`() {
        val ref = SourceRef(
            doc = "pa-lecture-01",
            quote = "the size of representation must be mentioned",
            page = 34,
            span = Span(900, 944),
        )
        val cited = CitationGuard.attach(claim(source = ref))
        assertEquals(ClaimKind.DEFINITION, cited.claimKind)
        assertNotNull(cited.source)
        assertEquals("pa-lecture-01", cited.source.doc)
        assertEquals(Span(900, 944), cited.source.span)
        assertEquals("the size of representation must be mentioned", cited.source.quote)
    }

    @Test
    fun `a claim cited by quote alone (no span) still attaches but does not over-claim faithful`() {
        val ref = SourceRef(doc = "pa-lecture-01", quote = "some quote", page = 34, span = null)
        val cited = CitationGuard.attach(claim(source = ref))
        assertNotNull(cited.source)
        // No anchored span ⇒ must NOT be reported faithful (UNCERTAIN floor, §2.5).
        assertEquals(jarvis.tutor.VerificationStatus.uncertain, cited.status)
    }

    // --- Batch-3 fix: the runner is the source of a claim's REAL status -----------------------

    @Test
    fun `the audited-status overload carries the runner-resolved status onto the CitedClaim`() {
        // The runner KNOWS the real audited status; the span-heuristic default must NOT override it.
        val ref = SourceRef(doc = "pa-lecture-01", quote = "q", page = 34, span = Span(900, 944))
        val cited = CitationGuard.attach(claim(source = ref), jarvis.tutor.VerificationStatus.failed)
        // even though the span is present (the heuristic default would say faithful), the explicit
        // audited status wins: no faithful without the runner's say-so.
        assertEquals(jarvis.tutor.VerificationStatus.failed, cited.status)
    }

    @Test
    fun `the audited-status overload still THROWS on a null source - never ships un-cited`() {
        assertFailsWith<IllegalStateException> {
            CitationGuard.attach(claim(source = null), jarvis.tutor.VerificationStatus.faithful)
        }
    }

    @Test
    fun `the audited-status overload pins faithful only when the runner certifies it`() {
        // A span-anchored claim the runner certified faithful: the explicit status is faithful.
        val ref = SourceRef(doc = "pa-lecture-01", quote = "q", page = 34, span = Span(900, 944))
        val cited = CitationGuard.attach(claim(source = ref), jarvis.tutor.VerificationStatus.faithful)
        assertEquals(jarvis.tutor.VerificationStatus.faithful, cited.status)
    }
}
