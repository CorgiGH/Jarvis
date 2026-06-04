package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm

/**
 * Batch-2 leg 2 — the two-family LLM re-derivation leg (§L / H3).
 *
 * Family A (`RELAY`) and family B (`OPENROUTER`) EACH independently re-derive the claim from its
 * own model family; the leg then:
 *   - detects **agreement** (both families reach the same verdict), and
 *   - detects **FAMILY_COLLAPSE** when both legs are configured with the SAME [LegFamily] — the
 *     independence assumption is violated, so an "agreement" is worthless. Collapse is decided on
 *     the configured family TAG, NOT on the reported model string (§L: both clients substitute a
 *     non-blank model id, so a model-string compare would miss a same-family mis-provision).
 *
 * Hermetic by construction: the [Llm] is injected, so tests pass a fake and the default suite
 * never hits the network. The result type ([TwoFamilyResult]) is NOT frozen — designed cleanly so
 * Batch-3 `VerificationRunner` folds `agree`/`collapsed` into the §I `AuditOutcome`.
 */
class TwoFamilyDeriver(
    private val legA: Leg,
    private val legB: Leg,
) {
    /** One configured re-derivation leg: a model family tag + the client that speaks for it. */
    data class Leg(val family: LegFamily, val llm: Llm)

    /**
     * Re-derive [claim] on both families and combine.
     *
     * - `collapsed` is computed PURELY from the two configured family tags (no LLM call needed to
     *   decide it), but we still run both derivations so the audit row records each leg's verdict.
     * - `agree` is true iff both families return the SAME non-UNCLEAR verdict. An UNCLEAR verdict
     *   from either side is NOT agreement (FAIL-LOUD: an unparseable / hedged answer ≠ a match).
     */
    suspend fun derive(claim: VerificationClaim): TwoFamilyResult {
        val a = deriveOne(legA, claim)
        val b = deriveOne(legB, claim)

        val collapsed = legA.family == legB.family
        val agree = a.verdict != Verdict.UNCLEAR && a.verdict == b.verdict

        val details = buildString {
            append("A[").append(legA.family).append('/').append(a.model).append("]=").append(a.verdict)
            append(" | ")
            append("B[").append(legB.family).append('/').append(b.model).append("]=").append(b.verdict)
            if (collapsed) append(" | FAMILY_COLLAPSE")
        }

        return TwoFamilyResult(
            agree = agree,
            collapsed = collapsed,
            familyA = legA.family,
            familyB = legB.family,
            familyAVerdict = a.verdict,
            familyBVerdict = b.verdict,
            details = details,
        )
    }

    private suspend fun deriveOne(leg: Leg, claim: VerificationClaim): OneDerivation {
        val (text, model) = leg.llm.complete(buildPrompt(claim))
        return OneDerivation(verdict = classify(text), model = model)
    }

    private data class OneDerivation(val verdict: Verdict, val model: String)

    private fun buildPrompt(claim: VerificationClaim): List<ChatMessage> {
        val quote = claim.source?.quote?.let { "\nSOURCE QUOTE:\n$it" } ?: ""
        val invariant = claim.invariant?.let { "\nSTATED INVARIANT:\n$it" } ?: ""
        return listOf(
            ChatMessage(
                role = "system",
                content =
                    "You independently re-derive whether a CLAIM is supported by its SOURCE QUOTE. " +
                        "Reply with exactly one word on the first line: SUPPORTED, REFUTED, or UNCLEAR. " +
                        "SUPPORTED = the quote entails the claim; REFUTED = the quote contradicts it; " +
                        "UNCLEAR = you cannot tell. Do not restate the claim.",
            ),
            ChatMessage(
                role = "user",
                content = "CLAIM (${claim.kind}):\n${claim.content}$invariant$quote",
            ),
        )
    }

    private companion object {
        /** Map a model reply to a verdict by its leading token; anything else ⇒ UNCLEAR. */
        fun classify(reply: String): Verdict {
            val head = reply.trimStart().uppercase()
            return when {
                head.startsWith("SUPPORTED") -> Verdict.SUPPORTED
                head.startsWith("REFUTED") -> Verdict.REFUTED
                else -> Verdict.UNCLEAR
            }
        }
    }
}

/** One family's re-derivation verdict. Wire-irrelevant; carried into the audit detail string. */
enum class Verdict { SUPPORTED, REFUTED, UNCLEAR }

/**
 * Result of the two-family re-derivation. NOT frozen — Batch-3 `VerificationRunner` reads
 * [agree] + [collapsed] to pick the §I `AuditOutcome` (collapse ⇒ FAMILY_COLLAPSE; !agree ⇒
 * DISAGREE_OR_…; both true-ish + the other legs pass ⇒ ALL_AGREE_…). [details] is the human-
 * readable string written to `verification_audit`.
 */
data class TwoFamilyResult(
    /** Both families reached the SAME non-UNCLEAR verdict. */
    val agree: Boolean,
    /** Both legs share a configured [LegFamily] ⇒ independence violated (§L/H3). */
    val collapsed: Boolean,
    val familyA: LegFamily,
    val familyB: LegFamily,
    val familyAVerdict: Verdict,
    val familyBVerdict: Verdict,
    /** Human-readable per-leg breakdown for the `verification_audit` row. */
    val details: String,
)
