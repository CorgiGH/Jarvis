package jarvis.tutor.verify

import jarvis.content.SourceRef
import jarvis.content.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §K / §L — leaf type shape + the FAIL-LOUD never-ran-≠-disagreed invariant on NonLlmResult. */
class VerificationTypesTest {

    /** §K — ClaimKind is a frozen wire enum: the EXACT literal set (UPPER) flows to
     *  verification_audit.claim_kind. The grounded-teaching layer adds EXPLANATION + WORKED_EXAMPLE. */
    @Test
    fun `ClaimKind has exactly the seven wire literals`() {
        val names = ClaimKind.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "DEFINITION", "INVARIANT", "GRADER_RULE",
                "MISCONCEPTION_REFUTATION", "STEM",
                "EXPLANATION", "WORKED_EXAMPLE",
            ),
            names,
        )
    }

    @Test
    fun `the two new teaching kinds serialize as their UPPER name`() {
        assertEquals("EXPLANATION", ClaimKind.EXPLANATION.name)
        assertEquals("WORKED_EXAMPLE", ClaimKind.WORKED_EXAMPLE.name)
        assertTrue(ClaimKind.valueOf("EXPLANATION") == ClaimKind.EXPLANATION)
        assertTrue(ClaimKind.valueOf("WORKED_EXAMPLE") == ClaimKind.WORKED_EXAMPLE)
    }

    @Test
    fun `NonLlmLegKind has exactly the four frozen literals`() {
        assertEquals(
            listOf("SYMPY", "TEST_EXEC", "HUMAN_GOLD", "NONE"),
            NonLlmLegKind.entries.map { it.name },
        )
    }

    @Test
    fun `LegFamily has RELAY OPENROUTER NONLLM plus the D6 NLI family`() {
        // §L was {RELAY, OPENROUTER, NONLLM}; D6/D-R12 adds NLI (the PC-side local-entailment family
        // wired as the offline audit's family-B). LegFamily is not a frozen WIRE literal (it lives only
        // in the audit detail string), so adding NLI is additive, not a lock break.
        assertEquals(listOf("RELAY", "OPENROUTER", "NONLLM", "NLI"), LegFamily.entries.map { it.name })
    }

    @Test
    fun `VerificationClaim carries the frozen fields including a nullable source`() {
        val ref = SourceRef(doc = "pa-lecture-01", quote = "x", page = 3, span = Span(10, 11))
        val claim = VerificationClaim(
            claimId = "pa-kc-005:DEFINITION:deadbeef",
            kcId = "pa-kc-005",
            subject = "PA",
            kind = ClaimKind.DEFINITION,
            content = "the size of representation must be mentioned",
            invariant = null,
            source = ref,
        )
        assertEquals("pa-kc-005", claim.kcId)
        assertEquals(ClaimKind.DEFINITION, claim.kind)
        assertEquals(ref, claim.source)
        // source is nullable per §K (null span ⇒ definitional ⇒ UNCERTAIN floor)
        val noSource = claim.copy(source = null)
        assertNull(noSource.source)
    }

    @Test
    fun `NonLlmLeg fun interface can be supplied as a lambda and returns a NonLlmResult`() {
        val leg = NonLlmLeg { c ->
            NonLlmResult(kind = NonLlmLegKind.SYMPY, ran = true, pass = true, detail = "simplified(${c.kcId})")
        }
        val claim = VerificationClaim("id", "pa-kc-005", "PA", ClaimKind.INVARIANT, "1+1=2", "1+1=2", null)
        val r = leg.check(claim)
        assertEquals(NonLlmLegKind.SYMPY, r.kind)
        assertTrue(r.ran)
        assertTrue(r.pass)
        assertEquals("simplified(pa-kc-005)", r.detail)
    }

    @Test
    fun `NonLlmResult NONE means ran is false - never-ran is not disagreed`() {
        val none = NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = null)
        assertEquals(NonLlmLegKind.NONE, none.kind)
        assertFalse(none.ran, "kind==NONE must carry ran=false (FAIL-LOUD: never-ran != disagreed)")
    }
}
