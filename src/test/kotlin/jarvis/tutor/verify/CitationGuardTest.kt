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
}
