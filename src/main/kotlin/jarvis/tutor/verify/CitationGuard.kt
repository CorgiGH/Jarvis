package jarvis.tutor.verify

import jarvis.content.SourceRef
import jarvis.tutor.VerificationStatus
import kotlinx.serialization.Serializable

/**
 * §Q — a claim that has PASSED the citation chokepoint: it carries a resolved SourceRef and is
 * the ONLY claim shape allowed to be serialized into a client envelope (the
 * `/verify/{kcId}/status` reply `claims[]` element + the `/drill/grade` served payload).
 * The OUTPUT shape at the serialization boundary (input = `VerificationClaim`, §K).
 */
@Serializable
data class CitedClaim(
    /** §K — DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM. */
    val claimKind: ClaimKind,
    /** §I — the claim's resolved trust status. */
    val status: VerificationStatus,
    /** The verbatim citation backing this claim. NON-NULL by construction: a claim with no
     *  resolved SourceRef NEVER becomes a CitedClaim — `CitationGuard.attach` throws first.
     *  Serialized as `source:{doc, page|null, span:{start,end}|null, quote}` (L2). */
    val source: SourceRef,
)

/**
 * §Q — the per-claim, per-emit citation chokepoint (TASK P2-RULE8). DISTINCT from
 * `VerificationGate.gate(kc)` (§I2, the binary ALLOW|DENY KC-admission gate): THIS is the
 * serialization-boundary guard run on EVERY claim before it ships to the learner, so no
 * un-cited claim ever reaches the surface. Pure over its input.
 */
object CitationGuard {
    /**
     * Attach + REQUIRE a resolved SourceRef. FAIL-LOUD: a claim whose `source` is null/unresolved
     * THROWS (never ships un-cited); a resolved claim returns a `CitedClaim` carrying
     * `{doc, span|page, quote}`.
     *
     * The status carried onto the [CitedClaim] is inferred conservatively from whether the
     * citation is span-anchored: a claim with a non-null span is treated as `faithful` (it has a
     * gold span the round-trip can confirm); a claim cited by quote alone (no span) pins at
     * `uncertain` (no anchored span ⇒ UNCERTAIN floor, §2.5). The runner overwrites this with the
     * audited status; this default keeps `attach` pure with no extra arg while never over-claiming.
     */
    fun attach(claim: VerificationClaim): CitedClaim {
        val source = claim.source
            ?: throw IllegalStateException(
                "CitationGuard.attach: claim '${claim.claimId}' has no resolved SourceRef — " +
                    "an un-cited claim must NEVER be emitted to the learner (FAIL-LOUD, TASK P2-RULE8)",
            )
        val status =
            if (source.span != null) VerificationStatus.faithful else VerificationStatus.uncertain
        return CitedClaim(claimKind = claim.kind, status = status, source = source)
    }
}
