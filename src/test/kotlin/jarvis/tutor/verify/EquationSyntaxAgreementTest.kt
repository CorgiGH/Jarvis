package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentValidator
import jarvis.content.KnowledgeConcept
import jarvis.content.LoadedSubject
import jarvis.content.SourceRef
import jarvis.content.Span
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MED-b — the equation-syntax parser was COPY-PASTED in three places (SymPyLeg.splitEquation,
 * ContentReconcile.isPlainEquation, ContentValidator.checkTautologicalInvariants). Divergence on ANY
 * input would let a tautology escape the D4 guard OR floor a valid equation to UNCERTAIN.
 *
 * After extraction to the single [EquationSyntax] helper, this test guards against future
 * re-divergence: for EVERY corpus input, all three call paths must AGREE on whether the text is a
 * plain `lhs = rhs` equation (SymPyLeg machine-checks it ⇔ ContentReconcile routes it to SymPy ⇔ the
 * validator's tautology guard splits it). It also tests the shared function directly.
 */
class EquationSyntaxAgreementTest {

    // A corpus spanning: a real equation, tautologies, every rejected relational operator, a
    // multi-`=` string, whitespace-only sides, and a non-equation.
    private val corpus = listOf(
        "1+1+1=3",   // plain equation
        "t=t",       // tautology — still a plain equation (lhs==rhs handled by the D4 guard ON TOP)
        "x = x",     // tautology with spaces
        "a<=b",      // relational ≤ — rejected
        "x==y",      // relational == — rejected
        "a>=b",      // relational ≥ — rejected
        "x!=y",      // relational ≠ — rejected
        "lhs=rhs=z", // two top-level '=' — rejected
        " = ",       // both sides blank — rejected
        "f(x)",      // no '=' at all — rejected
        "2x = x + x",// meaningful (non-identity) equation
    )

    private fun symPyAccepts(text: String): Boolean {
        val leg = SymPyLeg(pythonEquals = { _, _ -> SymPyLeg.PyResult(ran = true, equal = true, detail = "x") })
        val claim = VerificationClaim(
            claimId = "pa-kc-005:INVARIANT:deadbeef", kcId = "pa-kc-005", subject = "PA",
            kind = ClaimKind.INVARIANT, content = text, invariant = text,
            source = SourceRef(doc = "d", quote = "q", page = 1, span = Span(0, 1)),
        )
        // The SymPy leg runs (kind==SYMPY) iff it could split the equation; otherwise it floors to NONE.
        return leg.check(claim).kind == NonLlmLegKind.SYMPY
    }

    // ContentReconcile.directiveEquation(bare-rule) returns the equation iff it is a plain equation
    // (its `isPlainEquation` gate). None of the corpus inputs start with `sympy:`, so this isolates
    // the plain-equation predicate.
    private fun reconcileAccepts(text: String): Boolean = ContentReconcile.directiveEquation(text) != null

    // The validator's tautology guard fires iff it both (a) splits the text as a plain equation AND
    // (b) finds lhs==rhs. So "validator splits it as an equation" is observable as: a tautology-shaped
    // sibling of the same syntactic CLASS fires. We probe the SPLIT directly by asserting the guard
    // fires exactly when EquationSyntax.split(text) yields equal sides.
    private fun validatorFiresTautology(text: String): Boolean {
        val kc = KnowledgeConcept(
            id = "a", subject = "PA", name_ro = "ro", name_en = "en", cluster = "c",
            bloom_level = "understand", difficulty = 1, time_minutes = 10, exam_weight = 1.0,
            tier = 1, invariant = text,
        )
        val sub = LoadedSubject("PA", kcs = listOf(kc), edges = emptyList(), misconceptions = emptyList())
        return ContentValidator.checkTautologicalInvariants(sub).any { it.rule == "tautological_invariant" }
    }

    @Test
    fun `all three call paths agree on plain-equation acceptance for every corpus input`() {
        for (text in corpus) {
            val shared = EquationSyntax.isPlainEquation(text)
            assertEquals(
                shared, symPyAccepts(text),
                "SymPyLeg must agree with EquationSyntax on '$text' (plain-equation acceptance)",
            )
            assertEquals(
                shared, reconcileAccepts(text),
                "ContentReconcile.isPlainEquation must agree with EquationSyntax on '$text'",
            )
        }
    }

    @Test
    fun `the validator tautology guard splits identically to the shared parser for every input`() {
        for (text in corpus) {
            val split = EquationSyntax.split(text)
            val expectedFires = split != null && split.first == split.second
            assertEquals(
                expectedFires, validatorFiresTautology(text),
                "ContentValidator's tautology split must agree with EquationSyntax on '$text' " +
                    "(fires iff plain equation with lhs==rhs)",
            )
        }
    }

    @Test
    fun `EquationSyntax split returns the trimmed sides of a plain equation and null otherwise`() {
        assertEquals("1+1+1" to "3", EquationSyntax.split("1+1+1=3"))
        assertEquals("a" to "b", EquationSyntax.split("  a  =  b  "))
        assertEquals("t" to "t", EquationSyntax.split("t = t"), "a tautology still splits (lhs==rhs)")
        for (rejected in listOf("a<=b", "x==y", "a>=b", "x!=y", "lhs=rhs=z", " = ", "f(x)")) {
            assertEquals(null, EquationSyntax.split(rejected), "'$rejected' must NOT split as a plain equation")
        }
    }
}
