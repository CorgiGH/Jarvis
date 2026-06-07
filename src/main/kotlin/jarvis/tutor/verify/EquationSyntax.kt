package jarvis.tutor.verify

/**
 * The ONE canonical "is this a plain `lhs = rhs` equation, and what are its two sides" parser.
 *
 * Previously this exact logic was COPY-PASTED in three places (each with a "Mirrors SymPyLeg" comment
 * but no shared helper and no sync test):
 *  - [SymPyLeg]'s `splitEquation` (the canonical owner — the SymPy leg can only machine-check a
 *    plain equality),
 *  - `jarvis.content.ContentReconcile.isPlainEquation` (decides whether an authored rule routes to the
 *    SymPy leg or floors to the UNCERTAIN prose path),
 *  - `jarvis.content.ContentValidator.checkTautologicalInvariants` (the D4 tautology guard splits the
 *    invariant to compare `lhs == rhs`).
 *
 * Divergence between the three would let a tautology escape the D4 guard OR floor a valid equation to
 * UNCERTAIN. Extracting ONE function (delegated to by all three) makes that class of bug impossible;
 * `EquationSyntaxAgreementTest` then guards against future re-divergence.
 *
 * `internal` so it is module-scoped (shared across `jarvis.tutor.verify` + `jarvis.content`, same
 * gradle module) and directly unit-testable, without widening the public API.
 */
internal object EquationSyntax {

    /**
     * Split [text] on its single top-level `=`. Returns the `(lhs, rhs)` (each trimmed) when [text] is a
     * plain equality — exactly one `=`, NO relational operator (`<=` / `>=` / `!=` / `==`), and both
     * sides non-empty — else null.
     *
     * This is the canonical logic that used to live in `SymPyLeg.splitEquation`. A TAUTOLOGY (`t = t`)
     * is intentionally NOT rejected here (it splits to `("t","t")`): the D4 tautology guard compares
     * `lhs == rhs` on TOP of this split, and the hermetic SymPy suite uses `x = x` as a generic
     * equation placeholder.
     */
    fun split(text: String): Pair<String, String>? {
        // Reject relational operators that aren't a plain equality.
        if (text.contains("<=") || text.contains(">=") || text.contains("!=") || text.contains("==")) return null
        val idx = text.indexOf('=')
        if (idx < 0 || idx != text.lastIndexOf('=')) return null
        val lhs = text.substring(0, idx).trim()
        val rhs = text.substring(idx + 1).trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        return lhs to rhs
    }

    /** True iff [text] is a plain `lhs = rhs` equation (i.e. [split] returns non-null). */
    fun isPlainEquation(text: String): Boolean = split(text) != null
}
