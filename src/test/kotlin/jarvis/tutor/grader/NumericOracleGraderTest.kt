package jarvis.tutor.grader

import jarvis.tutor.SympyTool
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-6 Task 3 Step 3 — the numeric oracle leg + the single-source [matches] comparator (§0.9-B).
 *
 * Boundary semantics are pinned EXACTLY: `matches` is `abs(diff) <= tol` (inclusive ≤).
 */
class NumericOracleGraderTest {

    private val grader = NumericOracleGrader()

    // ─── matches() boundary semantics (the ≤ contract, §0.9-B) ──────────────────

    @Test
    fun `matches default tolerance — diff just inside 1e-9 is true`() {
        // diff = 1e-10 ≤ 1e-9  → TRUE (the deliberate ≤-boundary case from the plan)
        assertTrue(NumericOracleGrader.matches(2.5, 2.5000000001, null))
    }

    @Test
    fun `matches default tolerance — diff exactly at 1e-9 is true (inclusive)`() {
        assertTrue(NumericOracleGrader.matches(0.0, 1e-9, null), "≤ is inclusive at the boundary")
    }

    @Test
    fun `matches default tolerance — diff just beyond 1e-9 is false`() {
        assertFalse(NumericOracleGrader.matches(0.0, 1.0000001e-9, null))
    }

    @Test
    fun `matches honors an explicit tolerance`() {
        assertTrue(NumericOracleGrader.matches(3.14, 3.141, 0.01))
        assertFalse(NumericOracleGrader.matches(3.14, 3.20, 0.01))
    }

    @Test
    fun `matches is symmetric in argument order via abs`() {
        assertEquals(
            NumericOracleGrader.matches(10.0, 9.5, 0.6),
            NumericOracleGrader.matches(9.5, 10.0, 0.6),
        )
    }

    // ─── leg behavior ───────────────────────────────────────────────────────────

    @Test
    fun `grading a correct numeric problem decides NUMERIC_ORACLE with RO feedback`() = runBlocking {
        val out = grader.grade(GradeInput(expected = "0.875", attempt = "0.875", tolerance = 1e-9))
        assertTrue(out is LegOutcome.Decided)
        out as LegOutcome.Decided
        assertTrue(out.correct)
        assertEquals(1.0, out.score, 1e-9)
        // RO copy (diacritic-bearing) — server-side equivalent of practiceStrings.
        assertTrue(out.feedbackRo.contains("corect"), "feedback is RO: ${out.feedbackRo}")
    }

    @Test
    fun `grading a wrong numeric answer decides not-correct`() = runBlocking {
        val out = grader.grade(GradeInput(expected = "0.875", attempt = "0.9", tolerance = 1e-9))
        assertTrue(out is LegOutcome.Decided)
        out as LegOutcome.Decided
        assertFalse(out.correct)
        assertEquals(0.0, out.score, 1e-9)
    }

    @Test
    fun `a non-numeric attempt makes the leg DEFER, never throw`() = runBlocking {
        val out = grader.grade(GradeInput(expected = "0.875", attempt = "approximately seven eighths"))
        assertTrue(out is LegOutcome.Defer, "non-numeric attempt → defer, not decide")
    }

    @Test
    fun `no expected answer makes the leg DEFER`() = runBlocking {
        val out = grader.grade(GradeInput(expected = null, attempt = "0.875"))
        assertTrue(out is LegOutcome.Defer)
    }

    @Test
    fun `a non-numeric non-symbolic expected makes the leg DEFER`() = runBlocking {
        val out = grader.grade(GradeInput(expected = "see the table", attempt = "0.875"))
        assertTrue(out is LegOutcome.Defer)
    }

    // ─── symbolic (expr:) path via SymPy — STRICT on PC/CI when python is present ──

    @Test
    fun `symbolic expr expected routes through SymPy and decides equality`() = runBlocking {
        org.junit.jupiter.api.Assumptions.assumeTrue(pythonSympyAvailable(), "python3+sympy not on PATH")
        // expected expr 2*x + 2  vs attempt 2*(x+1)  ⟹ simplify(diff)==0 ⟹ correct
        val ok = grader.grade(GradeInput(expected = "expr:2*x + 2", attempt = "2*(x+1)"))
        assertTrue(ok is LegOutcome.Decided && ok.correct, "symbolically equal → correct")

        val wrong = grader.grade(GradeInput(expected = "expr:2*x + 2", attempt = "2*x + 3"))
        assertTrue(wrong is LegOutcome.Decided && !wrong.correct, "symbolically unequal → not correct")
    }

    private fun pythonSympyAvailable(): Boolean {
        // Probe via the same SympyTool subprocess; a trivial simplify that needs sympy.
        val res = SympyTool.run("simplify", "x + x")
        return res.ok
    }
}
