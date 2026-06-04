package jarvis

import jarvis.content.SourceRef
import jarvis.content.Span
import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.VerificationClaim
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * D6 / D-R12 (B5r-6) — `NliEntailmentLlm`: the PC-side LOCAL NLI family-B adapter.
 *
 * All hermetic — NO python spawned. We test:
 *   1. PROMPT PARSING: the deriver's exact `buildPrompt` shape ⇒ correct (premise, hypothesis),
 *      across the no-quote, quote-only, and STATED-INVARIANT-present cases.
 *   2. OUTPUT MAPPING: a runner VERDICT line ⇒ the completion word; an ERR line / unparseable line
 *      ⇒ THROW (the FAIL-LOUD vs genuine-UNCLEAR distinction).
 *   3. The no-SOURCE-QUOTE ⇒ UNCLEAR short-circuit in `complete` (no python needed).
 *
 * The one GUARDED real-python integration test lives in `NliEntailmentLlmIntegrationTest`.
 */
class NliEntailmentLlmTest {

    /**
     * Reconstruct the deriver's user-message EXACTLY as `TwoFamilyDeriver.buildPrompt` does
     * (`CLAIM (<kind>):\n<content>[STATED INVARIANT][SOURCE QUOTE]`, invariant BEFORE quote). The
     * deriver's `buildPrompt` is private, so we mirror its format here verbatim — if the deriver's
     * format ever drifts, this fixture (and the parser) must move in lockstep.
     */
    private fun deriverPrompt(claim: VerificationClaim): List<ChatMessage> {
        val quote = claim.source?.quote?.let { "\nSOURCE QUOTE:\n$it" } ?: ""
        val invariant = claim.invariant?.let { "\nSTATED INVARIANT:\n$it" } ?: ""
        return listOf(
            ChatMessage(role = "system", content = "You independently re-derive ... UNCLEAR. Do not restate the claim."),
            ChatMessage(role = "user", content = "CLAIM (${claim.kind}):\n${claim.content}$invariant$quote"),
        )
    }

    private fun claim(
        kind: ClaimKind,
        content: String,
        invariant: String?,
        quote: String?,
    ) = VerificationClaim(
        claimId = "pa-kc-001:$kind:deadbeef",
        kcId = "pa-kc-001",
        subject = "PA",
        kind = kind,
        content = content,
        invariant = invariant,
        source = quote?.let { SourceRef(doc = "pa-lecture-01", quote = it, page = 1, span = Span(0, 1)) },
    )

    // --- 1. PROMPT PARSING -----------------------------------------------------------------

    @Test
    fun `parse extracts premise (after SOURCE QUOTE) and hypothesis (the CLAIM content) for a DEFINITION`() {
        val c = claim(
            kind = ClaimKind.DEFINITION,
            content = "An algorithm halts in a finite amount of time.",
            invariant = null,
            quote = "An algorithm is a well-ordered collection of operations that halts in finite time.",
        )
        val parsed = NliEntailmentLlm.parseNliInput(deriverPrompt(c))
        assertEquals(
            "An algorithm is a well-ordered collection of operations that halts in finite time.",
            parsed.premise,
            "PREMISE = the text after SOURCE QUOTE:\\n",
        )
        assertEquals(
            "An algorithm halts in a finite amount of time.",
            parsed.hypothesis,
            "HYPOTHESIS = the CLAIM content (no invariant present)",
        )
    }

    @Test
    fun `parse stops the hypothesis at STATED INVARIANT and still reads the premise after SOURCE QUOTE`() {
        // INVARIANT claim: content, THEN \nSTATED INVARIANT:\n<eq>, THEN \nSOURCE QUOTE:\n<quote>.
        // HYPOTHESIS must be JUST the content (up to STATED INVARIANT) — never the equation, never the quote.
        val c = claim(
            kind = ClaimKind.INVARIANT,
            content = "The size of a uniform-cost array element is one.",
            invariant = "|n|_unif = 1",
            quote = "Under the uniform cost criterion every element has size one.",
        )
        val parsed = NliEntailmentLlm.parseNliInput(deriverPrompt(c))
        assertEquals("The size of a uniform-cost array element is one.", parsed.hypothesis)
        assertEquals("Under the uniform cost criterion every element has size one.", parsed.premise)
    }

    @Test
    fun `parse returns null premise when there is no SOURCE QUOTE - nothing to entail against`() {
        val c = claim(
            kind = ClaimKind.DEFINITION,
            content = "A data type is a set of values.",
            invariant = null,
            quote = null, // no source ⇒ no SOURCE QUOTE marker in the prompt
        )
        val parsed = NliEntailmentLlm.parseNliInput(deriverPrompt(c))
        assertNull(parsed.premise, "no SOURCE QUOTE ⇒ premise is null")
        assertEquals("A data type is a set of values.", parsed.hypothesis)
    }

    @Test
    fun `parse handles a multi-line source quote - premise keeps every line after the marker`() {
        val c = claim(
            kind = ClaimKind.DEFINITION,
            content = "Algorithms terminate.",
            invariant = null,
            quote = "Line one of the quote.\nLine two continues the quote.",
        )
        val parsed = NliEntailmentLlm.parseNliInput(deriverPrompt(c))
        assertEquals("Line one of the quote.\nLine two continues the quote.", parsed.premise)
    }

    // --- 2. OUTPUT MAPPING -----------------------------------------------------------------

    @Test
    fun `mapRunnerOutput maps a VERDICT line to the leading verdict word`() {
        assertEquals("SUPPORTED", NliEntailmentLlm.mapRunnerOutput("SUPPORTED\t0.9123\n"))
        assertEquals("REFUTED", NliEntailmentLlm.mapRunnerOutput("REFUTED\t0.8001"))
        assertEquals("UNCLEAR", NliEntailmentLlm.mapRunnerOutput("UNCLEAR\t0.4210\n"))
    }

    @Test
    fun `mapRunnerOutput returns UNCLEAR for a genuine low-confidence model verdict - NOT a throw`() {
        // The load-bearing distinction: a real "UNCLEAR\t0.4x" line is a genuine model abstain ⇒ return it.
        // (Only ERR / unparseable ⇒ throw.)
        assertEquals("UNCLEAR", NliEntailmentLlm.mapRunnerOutput("UNCLEAR\t0.4900"))
    }

    @Test
    fun `mapRunnerOutput THROWS on an ERR line - an infra-model failure is fail-loud, never a verdict`() {
        val ex = assertFailsWith<RuntimeException> {
            NliEntailmentLlm.mapRunnerOutput("ERR\timport failed: No module named 'transformers'")
        }
        assertEquals(true, ex.message?.contains("transformers"))
    }

    @Test
    fun `mapRunnerOutput THROWS on empty output - unparseable is fail-loud`() {
        assertFailsWith<RuntimeException> { NliEntailmentLlm.mapRunnerOutput("   \n") }
    }

    @Test
    fun `mapRunnerOutput THROWS on an unrecognised first token - unparseable is fail-loud`() {
        assertFailsWith<RuntimeException> { NliEntailmentLlm.mapRunnerOutput("MAYBE\t0.5") }
    }

    // --- 3. complete short-circuit (no python) ---------------------------------------------

    @Test
    fun `complete returns UNCLEAR with the model id when the prompt has no SOURCE QUOTE - no python spawned`() = runBlocking {
        val c = claim(ClaimKind.DEFINITION, "A data type is a set of values.", invariant = null, quote = null)
        val (text, model) = NliEntailmentLlm().complete(deriverPrompt(c))
        assertEquals("UNCLEAR", text, "no premise ⇒ abstain to UNCLEAR (genuine, not infra failure)")
        assertEquals(NliEntailmentLlm.NLI_MODEL_ID, model)
    }
}
