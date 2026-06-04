package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.SourceRef
import jarvis.content.Span
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batch-2 leg 2 — `TwoFamilyDeriver`: family A (RELAY) + family B (OPENROUTER) each INDEPENDENTLY
 * re-derive the claim; agreement detect + FAMILY_COLLAPSE when both legs resolve the same
 * configured `LegFamily` (§L/H3 — compare family TAGS, not model strings). All hermetic: the Llm
 * is a fake; NO network.
 */
class TwoFamilyDeriverTest {

    /** Canned-reply fake Llm; records the model id it reports so we can prove family tags
     *  (not model strings) drive collapse detection. */
    private class FakeLlm(private val reply: String, private val model: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            reply to model
    }

    private val claim = VerificationClaim(
        claimId = "pa-kc-005:INVARIANT:deadbeef",
        kcId = "pa-kc-005",
        subject = "PA",
        kind = ClaimKind.INVARIANT,
        content = "the size of representation must be mentioned for each data type",
        invariant = "size(t) is defined for every data type t",
        source = SourceRef(doc = "pa-lecture-01", quote = "q", page = 1, span = Span(0, 1)),
    )

    @Test
    fun `two distinct families that re-derive the same verdict AGREE and do not collapse`() = runBlocking {
        val a = TwoFamilyDeriver.Leg(family = LegFamily.RELAY, llm = FakeLlm("SUPPORTED: the claim follows", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(family = LegFamily.OPENROUTER, llm = FakeLlm("SUPPORTED: yes, it holds", "llama-3.3-70b:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.agree, "both families re-derived SUPPORTED ⇒ agree")
        assertFalse(r.collapsed, "two DISTINCT families ⇒ no collapse")
        assertEquals(LegFamily.RELAY, r.familyA)
        assertEquals(LegFamily.OPENROUTER, r.familyB)
    }

    @Test
    fun `two families that both re-derive REFUTED agree on the verdict but are NOT bothSupported`() = runBlocking {
        // F1: both families REFUTED is AGREEMENT on a verdict, but it is NOT a SUPPORTED agreement.
        // The faithful path must demand SUPPORTED==SUPPORTED, so `bothSupported` must be FALSE here
        // even though `agree` is true. Certifying a both-REFUTED claim as faithful is the live
        // false-faithful bug this guards.
        val a = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("REFUTED: the source contradicts it", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("REFUTED: not entailed at all", "llama-3.3-70b:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.agree, "both families reached the SAME non-UNCLEAR verdict ⇒ agree")
        assertFalse(r.bothSupported, "both REFUTED is agreement, but NOT a SUPPORTED agreement (no faithful)")
        assertEquals(Verdict.REFUTED, r.familyAVerdict)
        assertEquals(Verdict.REFUTED, r.familyBVerdict)
    }

    @Test
    fun `two families that both re-derive SUPPORTED are bothSupported`() = runBlocking {
        val a = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED: the claim follows", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED: yes, it holds", "llama-3.3-70b:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.agree, "both SUPPORTED ⇒ agree")
        assertTrue(r.bothSupported, "both families SUPPORTED ⇒ bothSupported true (the only faithful-eligible case)")
    }

    @Test
    fun `two families that re-derive opposite verdicts DISAGREE`() = runBlocking {
        val a = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED: the claim follows", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("REFUTED: the claim does not follow", "llama-3.3-70b:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertFalse(r.agree, "opposing verdicts ⇒ disagree")
        assertFalse(r.collapsed)
    }

    @Test
    fun `same configured family on both legs is a FAMILY_COLLAPSE even when verdicts agree`() = runBlocking {
        // Both legs configured RELAY (a mis-provision). Even though their text agrees, the
        // independence assumption is violated — collapse must fire (§L/H3).
        val a = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED", "claude-max-relay-2"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.collapsed, "both legs RELAY ⇒ FAMILY_COLLAPSE (family tags collapse, not model strings)")
        assertEquals(LegFamily.RELAY, r.familyA)
        assertEquals(LegFamily.RELAY, r.familyB)
    }

    @Test
    fun `collapse is decided on family TAGS not model strings - different models same family still collapses`() = runBlocking {
        // Distinct MODEL strings but the SAME family tag ⇒ still a collapse. Proves the compare
        // is on LegFamily, not the reported model id.
        val a = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED", "meta-llama/llama-3.3-70b:free"))
        val b = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED", "google/gemini-2.0-flash-exp:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.collapsed, "same family tag, different model strings ⇒ still collapse")
    }

    @Test
    fun `the result records both per-leg verdicts in details for the audit row`() = runBlocking {
        val a = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED: follows directly", "claude-max-relay"))
        val b = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("REFUTED: contradicts the source", "llama-3.3-70b:free"))

        val r = TwoFamilyDeriver(a, b).derive(claim)

        assertTrue(r.details.isNotBlank(), "details must carry something for the verification_audit row")
        assertTrue(r.familyAVerdict != r.familyBVerdict, "per-leg verdicts differ here")
    }
}
