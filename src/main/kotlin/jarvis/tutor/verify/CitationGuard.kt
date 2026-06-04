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
     * F3 — this 1-arg form has NO audited status in hand, so it ALWAYS pins the carried status to
     * `uncertain` and is NEVER faithful-capable. Span-presence alone certifies NOTHING: a gold span
     * is necessary but not sufficient for faithfulness (the runner still must round-trip it AND have
     * both families agree AND a non-LLM leg pass, §2.5). The ONLY path that may ever carry `faithful`
     * onto a [CitedClaim] is the [attach]`(claim, status)` overload below, which takes the RUNNER's
     * audited status verbatim. This keeps "no faithful without the runner's say-so" true by
     * construction at the emit chokepoint.
     */
    fun attach(claim: VerificationClaim): CitedClaim {
        val source = requireSource(claim)
        // F3: the 1-arg path is never faithful-capable — span-presence does not launder trust into
        // faithful. Pin to UNCERTAIN; the audited-status overload is the sole faithful-capable path.
        return CitedClaim(claimKind = claim.kind, status = VerificationStatus.uncertain, source = source)
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
