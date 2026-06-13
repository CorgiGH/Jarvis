package jarvis.tutor.grader

import jarvis.tutor.SympyTool

/**
 * Numeric oracle leg (E16) + THE single-source numeric comparator (§0.9-B).
 *
 * [matches] is the ONE numeric-tolerance comparator in the codebase — Task 4 folds
 * `GradeScoring.answerMatches`, the lesson CHECK/ATTEMPT branches, and this leg all
 * onto it. Plain numerics never shell out; only an `expr:`-prefixed symbolic expected
 * answer routes through the SymPy subprocess bridge ([SympyTool], the existing pattern).
 */
class NumericOracleGrader : GraderLeg {

    override val kind: GraderLegKind = GraderLegKind.NUMERIC_ORACLE

    /**
     * Grade a numeric/symbolic answer. DEFERS (never throws) when:
     *  - no expected answer is supplied, or
     *  - the attempt is not a number AND the expected is not symbolic.
     * Symbolic compare (`expr:` prefix) routes through SymPy; if the bridge is
     * unavailable the leg DEFERS honestly.
     */
    override suspend fun grade(input: GradeInput): LegOutcome {
        val expected = input.expected?.trim()
        if (expected.isNullOrEmpty()) return LegOutcome.Defer("no expected answer for numeric oracle")
        val attempt = input.attempt.trim()
        if (attempt.isEmpty()) return LegOutcome.Defer("empty attempt")

        return if (expected.startsWith(SYMBOLIC_PREFIX)) {
            gradeSymbolic(expected.removePrefix(SYMBOLIC_PREFIX).trim(), attempt)
        } else {
            gradeNumeric(expected, attempt, input.tolerance)
        }
    }

    private fun gradeNumeric(expected: String, attempt: String, tol: Double?): LegOutcome {
        val expectedNum = expected.toDoubleOrNull()
            ?: return LegOutcome.Defer("expected '$expected' is not numeric and not 'expr:'-symbolic")
        val gotNum = attempt.toDoubleOrNull()
            ?: return LegOutcome.Defer("attempt is not numeric — oracle defers")
        val ok = matches(expectedNum, gotNum, tol)
        return decided(ok)
    }

    private fun gradeSymbolic(expectedExpr: String, attempt: String): LegOutcome {
        // simplify(expected - (attempt)) == 0  ⟺  the two expressions are equal.
        val diff = "($expectedExpr) - ($attempt)"
        val res = SympyTool.run("simplify", diff)
        if (!res.ok) return LegOutcome.Defer("symbolic oracle unavailable: ${res.error}")
        val ok = res.plain.trim() == "0"
        return decided(ok)
    }

    private fun decided(ok: Boolean): LegOutcome.Decided = LegOutcome.Decided(
        correct = ok,
        score = if (ok) 1.0 else 0.0,
        feedbackRo = if (ok) FEEDBACK_OK_RO else FEEDBACK_WRONG_RO,
    )

    companion object {
        /** §0.9-B: the single default absolute tolerance. */
        const val DEFAULT_ABS_TOL: Double = 1e-9

        /** Symbolic expected answers carry this prefix in the problem solution payload. */
        const val SYMBOLIC_PREFIX = "expr:"

        // Server-side RO copy (the practiceStrings-equivalent for the numeric leg).
        private const val FEEDBACK_OK_RO = "Răspuns numeric corect."
        private const val FEEDBACK_WRONG_RO = "Răspuns numeric incorect — verifică valoarea."

        /**
         * THE numeric-tolerance comparator (§0.9-B). `abs(got - expected) <= (tol ?: DEFAULT_ABS_TOL)`.
         * Every numeric comparison in the codebase folds onto this in Task 4.
         */
        fun matches(expected: Double, got: Double, tol: Double?): Boolean =
            kotlin.math.abs(got - expected) <= (tol ?: DEFAULT_ABS_TOL)
    }
}
