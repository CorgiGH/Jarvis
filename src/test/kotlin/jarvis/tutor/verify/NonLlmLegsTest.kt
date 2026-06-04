package jarvis.tutor.verify

import jarvis.content.SourceRef
import jarvis.content.Span
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch-2 leg 1 — `SymPyLeg` (PA), `NoneLeg` (other subjects → UNCERTAIN floor), and the
 * `nonLlmLegFor(subject)` selector. Hermetic: SymPyLeg's python call is an INJECTED seam, so
 * the default suite never shells to python / needs sympy. ONE integration test is GUARDED by
 * sympy-availability (assumeTrue) and never fails the suite when sympy is absent.
 */
class NonLlmLegsTest {

    private fun claim(
        kind: ClaimKind,
        content: String,
        invariant: String?,
    ) = VerificationClaim(
        claimId = "pa-kc-005:$kind:deadbeef",
        kcId = "pa-kc-005",
        subject = "PA",
        kind = kind,
        content = content,
        invariant = invariant,
        source = SourceRef(doc = "pa-lecture-01", quote = "q", page = 1, span = Span(0, 1)),
    )

    // --- SymPyLeg: true invariant → pass --------------------------------------------------

    @Test
    fun `SymPyLeg passes a TRUE invariant - sympy simplifies the difference to zero`() {
        // Fake python seam: a true invariant simplifies to 0.
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(ran = true, equal = true, detail = "simplify(lhs-rhs)=0") })
        val r = leg.check(claim(ClaimKind.INVARIANT, "x + x = 2*x", invariant = "x + x = 2*x"))
        assertEquals(NonLlmLegKind.SYMPY, r.kind)
        assertTrue(r.ran, "an INVARIANT claim runs the checker")
        assertTrue(r.pass, "a true invariant must pass")
        assertEquals("simplify(lhs-rhs)=0", r.detail)
    }

    // --- SymPyLeg: false invariant → !pass ------------------------------------------------

    @Test
    fun `SymPyLeg fails a FALSE invariant - sympy difference is non-zero`() {
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(ran = true, equal = false, detail = "simplify(lhs-rhs)=x") })
        val r = leg.check(claim(ClaimKind.INVARIANT, "x + x = 3*x", invariant = "x + x = 3*x"))
        assertEquals(NonLlmLegKind.SYMPY, r.kind)
        assertTrue(r.ran)
        assertFalse(r.pass, "a false invariant must NOT pass")
    }

    @Test
    fun `SymPyLeg also runs a GRADER_RULE claim`() {
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(ran = true, equal = true, detail = "ok") })
        val r = leg.check(claim(ClaimKind.GRADER_RULE, "2*(x+1) = 2*x + 2", invariant = "2*(x+1) = 2*x + 2"))
        assertEquals(NonLlmLegKind.SYMPY, r.kind)
        assertTrue(r.ran)
        assertTrue(r.pass)
    }

    @Test
    fun `SymPyLeg does NOT run a DEFINITION claim - returns NONE-kind ran false (no machine check applies)`() {
        // A purely definitional PA claim has no machine-checkable invariant: the SYMPY leg can't run
        // it. FIX-C: it must report kind=NONE (ran=false), NOT kind=SYMPY — so the runner's
        // NONLLM_LEG_NONE uncertain floor catches it instead of auto-routing the claim to failed.
        var seamCalled = false
        val leg = SymPyLeg(pythonEquals = { _, _ -> seamCalled = true; SymPyLeg.PyResult(true, true, "x") })
        val r = leg.check(claim(ClaimKind.DEFINITION, "a data type is a set of values", invariant = null))
        assertEquals(NonLlmLegKind.NONE, r.kind, "a non-equational claim ⇒ NONE-kind (uncertain floor), never SYMPY")
        assertFalse(r.ran, "no machine check applies ⇒ ran=false")
        assertFalse(seamCalled, "the python seam must not be invoked for a non-invariant claim")
    }

    @Test
    fun `SymPyLeg returns NONE for a prose GRADER_RULE with no invariant - no machine check applies`() {
        // FIX-C: a prose GRADER_RULE carrying NO invariant equation is not machine-checkable. It must
        // report kind=NONE (ran=false) so the runner floors it to uncertain — NOT a SYMPY tag that
        // would fall through decideOutcome's else ⇒ failed.
        var seamCalled = false
        val leg = SymPyLeg(pythonEquals = { _, _ -> seamCalled = true; SymPyLeg.PyResult(true, true, "x") })
        val r = leg.check(claim(ClaimKind.GRADER_RULE, "award full marks when every size is mentioned", invariant = null))
        assertEquals(NonLlmLegKind.NONE, r.kind, "a prose GRADER_RULE (no equation) ⇒ NONE-kind uncertain floor")
        assertFalse(r.ran)
        assertFalse(seamCalled, "the python seam must not be invoked for a non-equational rule")
    }

    @Test
    fun `SymPyLeg returns NONE when the invariant is not a plain equation - no machine check applies`() {
        // An INVARIANT whose text has no single '=' to split (e.g. a relational inequality) is not
        // machine-checkable by the sympy equality bridge ⇒ NONE-kind uncertain floor, never failed.
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(true, true, "x") })
        val r = leg.check(claim(ClaimKind.INVARIANT, "n <= 2*n for n >= 0", invariant = "n <= 2*n"))
        assertEquals(NonLlmLegKind.NONE, r.kind, "a non-equational invariant ⇒ NONE-kind uncertain floor")
        assertFalse(r.ran)
    }

    @Test
    fun `SymPyLeg surfaces a python seam that could not run as ran false - never a silent pass`() {
        // The seam itself reports ran=false (e.g. sympy unavailable / parse error): the leg must
        // propagate ran=false, never coerce it to a pass.
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(ran = false, equal = false, detail = "sympy not installed") })
        val r = leg.check(claim(ClaimKind.INVARIANT, "x = x", invariant = "x = x"))
        assertEquals(NonLlmLegKind.SYMPY, r.kind)
        assertFalse(r.ran, "a seam that could not run ⇒ ran=false")
        assertFalse(r.pass)
    }

    // --- NoneLeg: ran=false ---------------------------------------------------------------

    @Test
    fun `NoneLeg returns kind NONE and ran false - the UNCERTAIN floor`() {
        val r = NoneLeg.check(claim(ClaimKind.INVARIANT, "anything", invariant = "anything"))
        assertEquals(NonLlmLegKind.NONE, r.kind)
        assertFalse(r.ran, "kind==NONE ⇒ ran=false (never-ran ≠ disagreed, FAIL-LOUD H5)")
        assertFalse(r.pass, "ran=false ⇒ pass is meaningless and pinned false")
    }

    // --- nonLlmLegFor selector ------------------------------------------------------------

    @Test
    fun `nonLlmLegFor PA returns a SymPy-kind leg`() {
        val leg = nonLlmLegFor("PA")
        val r = leg.check(claim(ClaimKind.INVARIANT, "x = x", invariant = "x = x"))
        // PA routes to the SYMPY leg. With sympy possibly absent the real seam may report ran=false,
        // but the KIND must be SYMPY (not NONE) — PA HAS a checker.
        assertEquals(NonLlmLegKind.SYMPY, r.kind, "PA must route to the SYMPY leg")
    }

    @Test
    fun `nonLlmLegFor a non-PA subject returns the NONE leg`() {
        for (subject in listOf("PS", "SO-RC", "POO", "ALO", "unknown")) {
            val r = nonLlmLegFor(subject).check(claim(ClaimKind.INVARIANT, "x = x", invariant = "x = x"))
            assertEquals(NonLlmLegKind.NONE, r.kind, "$subject has no runnable checker ⇒ NONE leg")
            assertFalse(r.ran, "$subject ⇒ ran=false")
        }
    }

    @Test
    fun `nonLlmLegFor is case-insensitive on the PA subject tag`() {
        assertEquals(NonLlmLegKind.SYMPY, nonLlmLegFor("pa").check(claim(ClaimKind.INVARIANT, "x=x", "x=x")).kind)
    }

    // --- GUARDED integration test (never fails the suite when sympy is absent) -------------

    @Test
    fun `INTEGRATION SymPyLeg with the real python seam confirms a true invariant when sympy present`() {
        val realLeg = SymPyLeg()  // default ctor wires the real python+sympy seam
        val r = realLeg.check(claim(ClaimKind.INVARIANT, "x + x = 2*x", invariant = "x + x = 2*x"))
        // If sympy/python is unavailable the seam returns ran=false — SKIP rather than fail.
        assumeTrue(r.ran) { "sympy/python unavailable (ran=false) — skipping the live SymPy integration check" }
        assertTrue(r.pass, "with sympy present, x + x = 2*x must verify as a true invariant")
    }
}
