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
        val source = requireSource(claim)
        // STOPGAP heuristic (Batch-1): with no audited status available, infer conservatively from
        // whether the citation is span-anchored. Batch-3 callers (post-audit) MUST use the
        // `attach(claim, status)` overload below so the RUNNER's real status wins — that is the only
        // path that can ever say `faithful` (no faithful without non-LLM-pass AND families-agree).
        val status =
            if (source.span != null) VerificationStatus.faithful else VerificationStatus.uncertain
        return CitedClaim(claimKind = claim.kind, status = status, source = source)
    }

    /**
     * Batch-3 overload — attach with the **runner-resolved** audited status (the §I `VerificationStatus`
     * the `VerificationRunner.audit` wrote to `kc_verification_status`, B8). The runner is the source
     * of a claim's REAL status, so this overload carries it onto the `CitedClaim` verbatim — the
     * span-heuristic default in the no-arg overload is a stopgap only and NEVER over-claims `faithful`.
     *
     * Same FAIL-LOUD contract: a claim with a null/unresolved `source` THROWS first (an un-cited
     * claim never ships, even with an audited status in hand).
     */
    fun attach(claim: VerificationClaim, status: VerificationStatus): CitedClaim {
        val source = requireSource(claim)
        return CitedClaim(claimKind = claim.kind, status = status, source = source)
    }

    /** Resolve + require a non-null SourceRef, or THROW (FAIL-LOUD, TASK P2-RULE8). */
    private fun requireSource(claim: VerificationClaim): SourceRef =
        claim.source
            ?: throw IllegalStateException(
                "CitationGuard.attach: claim '${claim.claimId}' has no resolved SourceRef — " +
                    "an un-cited claim must NEVER be emitted to the learner (FAIL-LOUD, TASK P2-RULE8)",
            )
}
