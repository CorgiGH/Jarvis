package jarvis.tutor.verify

import jarvis.tutor.AuditOutcome
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorTypes
import jarvis.tutor.VerificationAuditTable
import jarvis.tutor.VerificationStatus
import jarvis.tutor.VerificationStatus_
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Batch-3 — the OFFLINE batch composer (master-plan Area B, FAIL-LOUD H4/H5).
 *
 * For EACH [VerificationClaim] the runner:
 *  1. runs each leg in its OWN try/catch — a thrown leg NEVER crashes the batch (it is RECORDED as
 *     a failure and folded into the outcome, never silently swallowed);
 *  2. computes the §I [AuditOutcome] — `faithful` requires ALL THREE: families agree, the non-LLM
 *     leg ran AND passed, and the span↔claim round-trip passes (and NOTHING threw);
 *  3. applies the pure [VerificationStatus_.transition] over the KC's CURRENT resolved status
 *     (read from the `kc_verification_status` B8 table, falling back to the claim's seed/unverified);
 *  4. RESOLVES ALL legs BEFORE any DB write (resolve-before-write) — a leg throwing leaves NO partial
 *     row;
 *  5. writes ONE `verification_audit` row per claim per run AND upserts `kc_verification_status` (B8).
 *
 * FAIL-LOUD distinctions (H5): never-ran (NONLLM `kind==NONE`, `ran=false`) is NOT confused with
 * disagreed; a thrown leg is recorded and yields `failed`/`uncertain`, NEVER a silent `faithful`.
 *
 * Idempotency: a re-audit on the SAME [auditRunId] for the SAME claim does NOT duplicate the row.
 *
 * Hermetic by construction: the two LLM legs ([legA]/[legB]), the per-subject non-LLM leg
 * ([nonLlmLegFor]), the raw source-of-record ([rawSourceFor]), and the [clock] are ALL injected
 * seams. The default unit suite passes fakes — NO network, NO sympy, NO live clock. This is an
 * offline batch — it NEVER runs on a request path.
 *
 * @param db            the tutor DB (temp/in-memory SQLite in tests).
 * @param legA          LLM family A (e.g. RELAY) — independently re-derives each claim.
 * @param legB          LLM family B (e.g. OPENROUTER :free) — the independent second family.
 * @param nonLlmLegFor  resolves the non-LLM machine checker for a subject (PA ⇒ SymPy; else NONE).
 * @param rawSourceFor  resolves the LIVE raw source-of-record text for a claim's `source.doc`.
 * @param clock         injected wall-clock seam (the `audited_at`/`updated_at` timestamp).
 */
class VerificationRunner(
    private val db: Database,
    private val legA: TwoFamilyDeriver.Leg,
    private val legB: TwoFamilyDeriver.Leg,
    private val nonLlmLegFor: (subject: String) -> NonLlmLeg,
    private val rawSourceFor: (claim: VerificationClaim) -> String,
    private val clock: () -> Instant = Instant::now,
) {

    /**
     * The per-claim verdict the runner returns (and writes). NOT a wire type — the offline CLI /
     * admin route reads it to report what changed. Carries enough to prove the FAIL-LOUD invariants
     * in tests without re-reading the DB.
     */
    data class AuditResult(
        val claimId: String,
        val kcId: String,
        val priorStatus: VerificationStatus,
        val outcome: AuditOutcome,
        val newStatus: VerificationStatus,
        /** True iff the audit row was written this call (false ⇒ skipped, idempotent on auditRunId). */
        val written: Boolean,
        /** Human-readable forensic detail for the audit row's `notes`. */
        val notes: String,
    )

    /**
     * Audit every [claims] entry. Returns one [AuditResult] per claim (in input order). A thrown leg
     * on one claim never aborts the batch — every claim is audited and recorded.
     *
     * @param auditRunId the run identifier; the same id audited twice does NOT duplicate rows
     *                   (idempotent). Defaults to a fresh ULID per call.
     */
    suspend fun audit(
        claims: List<VerificationClaim>,
        auditRunId: String = TutorTypes.ulid(),
    ): List<AuditResult> {
        // D8: the per-KC content fingerprint over the AUDITED claim set for that KC. Computed ONCE up
        // front over the whole batch (a KC's hash folds in every claim being audited for it), then
        // stamped on each KC's kc_verification_status row. The serve gate recomputes the same hash from
        // the live content and shows `faithful` only on a match (else fail-closed to unverified).
        val hashByKc: Map<String, String> = claims
            .groupBy { it.kcId }
            .mapValues { (_, kcClaims) -> jarvis.content.ContentReconcile.kcContentHashOf(kcClaims) }

        // Per-claim audit (FIX-A): each claim's OWN verdict is computed from a FRESH `pending` prior —
        // NOT the rolling shared kc_verification_status row — so it is ORDER-INDEPENDENT. The audit row
        // carries this per-claim verdict.
        val results = ArrayList<AuditResult>(claims.size)
        for (claim in claims) {
            results.add(auditOne(claim, auditRunId))
        }

        // KC-level finalization (FIX-B): a single CONJUNCTION over ALL of each KC's per-claim verdicts
        // in THIS run, written ONCE per KC (order-independent). faithful iff EVERY claim faithful; any
        // failed ⇒ failed; otherwise uncertain. Carries the D8 content_hash (B3).
        for ((kcId, kcResults) in results.groupBy { it.kcId }) {
            finalizeKc(kcId, kcResults, auditRunId, hashByKc[kcId])
        }
        return results
    }

    /**
     * FIX-B — the KC-level [VerificationStatus] is an explicit CONJUNCTION over every per-claim verdict
     * for this KC in this run (a single finalization step, order-independent):
     *  - `faithful` iff EVERY claim's per-claim verdict is `faithful`;
     *  - any `failed`  ⇒ `failed`;
     *  - otherwise (any `uncertain`/`pending`, no `failed`) ⇒ `uncertain`.
     * Writes the aggregate ONCE per KC, stamping the D8 [contentHash].
     */
    private fun aggregateKc(perClaim: List<VerificationStatus>): VerificationStatus = when {
        perClaim.isEmpty() -> VerificationStatus.uncertain
        perClaim.any { it == VerificationStatus.failed } -> VerificationStatus.failed
        perClaim.all { it == VerificationStatus.faithful } -> VerificationStatus.faithful
        else -> VerificationStatus.uncertain
    }

    /** Upsert kc_verification_status ONCE for [kcId] with the conjunction over its per-claim verdicts. */
    private fun finalizeKc(
        kcId: String,
        kcResults: List<AuditResult>,
        auditRunId: String,
        contentHash: String?,
    ) {
        val aggregate = aggregateKc(kcResults.map { it.newStatus })
        val now = clock()
        transaction(db) {
            val existing = KcVerificationStatusTable.selectAll()
                .where { KcVerificationStatusTable.kcId eq kcId }
                .singleOrNull()
            if (existing == null) {
                KcVerificationStatusTable.insert {
                    it[KcVerificationStatusTable.kcId] = kcId
                    it[status] = aggregate.name
                    it[lastAuditRunId] = auditRunId
                    it[KcVerificationStatusTable.contentHash] = contentHash
                    it[updatedAt] = now
                }
            } else {
                KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                    it[status] = aggregate.name
                    it[lastAuditRunId] = auditRunId
                    it[KcVerificationStatusTable.contentHash] = contentHash
                    it[updatedAt] = now
                }
            }
        }
    }

    /** Resolve every leg (try/catch each), compute the outcome + the PER-CLAIM verdict, THEN write. */
    private suspend fun auditOne(
        claim: VerificationClaim,
        auditRunId: String,
    ): AuditResult {
        // ---- 1. RESOLVE ALL LEGS (no DB; each leg in its OWN try/catch) -----------------------
        val legs = resolveLegs(claim)

        // ---- 2. COMPUTE THE OUTCOME ----------------------------------------------------------
        val outcome = decideOutcome(claim, legs)

        // ---- 3. THE PER-CLAIM VERDICT (FIX-A) ------------------------------------------------
        // ORDER-INDEPENDENT: a claim's own verdict is `transition(pending, thisClaimsOutcome)` from a
        // FRESH `pending` prior — NEVER the rolling shared kc_verification_status row. Reading that
        // shared row would make claim N's verdict depend on claims 1..N-1 audited earlier this run
        // (the multi-claim KC-poisoning bug). The KC-level aggregate is computed separately in
        // finalizeKc as an explicit conjunction.
        val prior = VerificationStatus.pending
        val perClaimStatus = VerificationStatus_.transition(prior, outcome)

        val notes = buildNotes(claim, legs, outcome)

        // ---- 4/5. WRITE THE AUDIT ROW ONCE (idempotent on auditRunId) ------------------------
        val written = writeOnce(claim, legs, outcome, perClaimStatus, auditRunId, notes)

        return AuditResult(
            claimId = claim.claimId,
            kcId = claim.kcId,
            priorStatus = prior,
            outcome = outcome,
            newStatus = perClaimStatus,
            written = written,
            notes = notes,
        )
    }

    /** All three legs' resolved outputs (each captured even when it threw). */
    private data class ResolvedLegs(
        val twoFamily: TwoFamilyResult?,   // null ⇒ the LLM leg threw
        val twoFamilyThrew: Boolean,
        val twoFamilyError: String?,
        val collapsed: Boolean,            // known from the CONFIGURED family tags even if a leg threw
        val nonLlm: NonLlmResult,
        val nonLlmThrew: Boolean,
        val nonLlmError: String?,
        val roundTrip: RoundTripResult,
        val roundTripThrew: Boolean,
        val roundTripError: String?,
    ) {
        /** True iff ANY leg threw — the FAIL-LOUD signal that blocks `faithful`. */
        val anyThrew: Boolean get() = twoFamilyThrew || nonLlmThrew || roundTripThrew
        /** Both families reached the same non-UNCLEAR verdict (false when the LLM leg threw). */
        val agree: Boolean get() = twoFamily?.agree == true
        /**
         * Both families reached SUPPORTED — the ONLY agreement that may certify (§2.5 / F1). Strictly
         * stronger than [agree]: a both-REFUTED is `agree` but NOT `bothSupported`, so case-3 reads THIS
         * to keep an agreed-but-refuted claim out of `faithful`. False when the LLM leg threw.
         */
        val bothSupported: Boolean get() = twoFamily?.bothSupported == true
        /**
         * B5-RESHAPE — the families AGREED on a non-SUPPORTED verdict (in practice: both REFUTED). The
         * agreed-but-REFUTED VETO: it must NEVER be `faithful` for ANY claim kind. The equational path
         * already excludes it via the `bothSupported` requirement; the prose/DEFINITION path does NOT
         * read the LLM vote to PROMOTE (self-entailment is zero-signal there), but it reads THIS to keep
         * the explicit veto even though the LIVE round-trip is the positive anchor. False when the LLM
         * leg threw (an UNCLEAR/absent verdict is not an agreed REFUTED).
         */
        val agreedNonSupported: Boolean get() = agree && twoFamily?.bothSupported == false
    }

    private suspend fun resolveLegs(claim: VerificationClaim): ResolvedLegs {
        // Collapse is decided on the CONFIGURED family tags — knowable without any LLM call, so it
        // survives a thrown LLM leg (§L/H3: compare family tags, not model strings).
        val collapsed = legA.family == legB.family

        // -- LLM two-family leg --
        var twoFamily: TwoFamilyResult? = null
        var twoFamilyThrew = false
        var twoFamilyError: String? = null
        try {
            twoFamily = TwoFamilyDeriver(legA, legB).derive(claim)
        } catch (e: Throwable) {
            twoFamilyThrew = true
            twoFamilyError = "${e.javaClass.simpleName}: ${e.message?.take(160)}"
        }

        // -- non-LLM leg --
        var nonLlm = NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = null)
        var nonLlmThrew = false
        var nonLlmError: String? = null
        try {
            nonLlm = nonLlmLegFor(claim.subject).check(claim)
        } catch (e: Throwable) {
            nonLlmThrew = true
            nonLlmError = "${e.javaClass.simpleName}: ${e.message?.take(160)}"
            // A thrown non-LLM leg is NOT "never-ran NONE" — keep ran=false but record the throw so
            // the outcome lands in DISAGREE_OR_…_THREW, never NONLLM_LEG_NONE (FAIL-LOUD H5).
            nonLlm = NonLlmResult(kind = NonLlmLegKind.SYMPY, ran = false, pass = false, detail = nonLlmError)
        }

        // -- round-trip leg --
        var roundTrip = RoundTripResult(pass = false, pageAnchorStatus = PageAnchorStatus.NONE, span = null)
        var roundTripThrew = false
        var roundTripError: String? = null
        try {
            val rawSource = rawSourceFor(claim)
            roundTrip = SpanClaimRoundTrip(rawSource).check(claim)
        } catch (e: Throwable) {
            roundTripThrew = true
            roundTripError = "${e.javaClass.simpleName}: ${e.message?.take(160)}"
        }

        return ResolvedLegs(
            twoFamily = twoFamily,
            twoFamilyThrew = twoFamilyThrew,
            twoFamilyError = twoFamilyError,
            collapsed = collapsed,
            nonLlm = nonLlm,
            nonLlmThrew = nonLlmThrew,
            nonLlmError = nonLlmError,
            roundTrip = roundTrip,
            roundTripThrew = roundTripThrew,
            roundTripError = roundTripError,
        )
    }

    /**
     * The §2.5 outcome decision (priority order):
     *  1. no gold span                                    → DEFINITIONAL_NO_GOLD_SPAN (uncertain)
     *  2. two legs share a configured family              → FAMILY_COLLAPSE (uncertain)
     *  3. EQUATIONAL claim, BOTH-SUPPORTED && nonllm
     *     ran&pass && roundtrip && !threw                 → ALL_AGREE_ROUNDTRIP_NONLLM_PASS (faithful)
     *  3p. PROSE/DEFINITION claim, roundtrip && !threw
     *     && !agreed-REFUTED                              → ALL_AGREE_ROUNDTRIP_NONLLM_PASS (faithful)
     *  4. EQUATIONAL claim whose SymPy checker could NOT
     *     run (NONE/ran=false), BOTH-SUPPORTED, roundtrip
     *     passed, nothing threw                           → NONLLM_LEG_NONE (uncertain floor)
     *  5. anything else (disagree / agreed-REFUTED /
     *     roundtrip fail / threw)                          → DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW (failed)
     *
     * F1: case 3 / case 4 read [ResolvedLegs.bothSupported], NOT [ResolvedLegs.agree]. An AGREED
     * verdict that is not SUPPORTED (e.g. both families REFUTED) is NOT faithful and NOT the uncertain
     * floor — it falls through to the `else` (failed). Agreement on the WRONG answer must never certify.
     *
     * B5-RESHAPE (B5r-1, Alex GO 2026-06-04) — the §2.5 "non-LLM leg" is RE-SCOPED PER CLAIM KIND, so
     * the `faithful` path is now ROUTED on whether the CLAIM KIND is equational or prose:
     *  - EQUATIONAL kind ([isEquationalKind]: INVARIANT, or GRADER_RULE carrying an invariant — the
     *    kinds SymPy is meant to check): UNCHANGED strong requirement (case 3) — `bothSupported &&
     *    nonLlm.ran && nonLlm.pass && roundTrip.pass && !anyThrew`. SymPy stays load-bearing for
     *    equations; an equational claim whose checker could NOT run still floors to case-4 uncertain
     *    (the round-trip is NOT a substitute for the equational machine check).
     *  - PROSE / DEFINITION kind (no equational machine check applies — DEFINITION / MISCONCEPTION /
     *    STEM / prose GRADER_RULE; the leg returns NONE): the LIVE span↔claim ROUND-TRIP *is* the
     *    deterministic non-LLM leg (case 3p). `faithful` iff `roundTrip.pass && !anyThrew && NOT
     *    agreed-REFUTED`. The LLM/NLI `bothSupported` self-vote is NOT counted to PROMOTE (a DEFINITION's
     *    content==quote makes self-entailment zero-signal) — round-trip is the load-bearing anchor. The
     *    agreed-but-REFUTED VETO ([ResolvedLegs.agreedNonSupported]) still blocks faithful, so the LLM
     *    can only VETO a prose claim, never CERTIFY it. This is the structural unblock: a DEFINITION had
     *    NO machine check (NONE/ran=false) ⇒ every KC emits ≥1 DEFINITION ⇒ under the old conjunction NO
     *    KC could EVER be faithful. signatures-lock §2.5 amended per-kind with sign-off.
     *
     * No path reaches `faithful` without an applicable non-LLM-leg pass (SymPy for equational kinds, the
     * LIVE round-trip for prose kinds) AND no agreed-REFUTED veto AND nothing throwing (the §2.5 LOCKED
     * invariant, per-kind re-scoped).
     */
    private fun decideOutcome(claim: VerificationClaim, legs: ResolvedLegs): AuditOutcome = when {
        claim.source?.span == null -> AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN
        legs.collapsed -> AuditOutcome.FAMILY_COLLAPSE
        // case 3 — EQUATIONAL claim, SymPy ran+passed: UNCHANGED strong path.
        isEquationalKind(claim) &&
            legs.bothSupported && legs.nonLlm.ran && legs.nonLlm.pass && legs.roundTrip.pass && !legs.anyThrew ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        // case 3p (B5-RESHAPE) — PROSE / DEFINITION claim: the LIVE round-trip IS the deterministic
        // non-LLM leg. faithful iff round-trip passes + nothing threw + NOT agreed-REFUTED. The LLM vote
        // is NOT read to promote (self-entailment is zero-signal); it can only veto (agreedNonSupported).
        !isEquationalKind(claim) &&
            legs.roundTrip.pass && !legs.anyThrew && !legs.agreedNonSupported ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        // case 4 — an EQUATIONAL claim whose SymPy/non-LLM checker could NOT run (NONE/ran=false) but
        // otherwise looks good: UNCERTAIN floor, never faithful (never-ran ≠ disagreed, FAIL-LOUD H5; the
        // round-trip is NOT a substitute for the equational check). Prose claims never reach here — case 3p
        // already routed a passing-round-trip prose claim to faithful and a non-anchoring one to `else`.
        isEquationalKind(claim) && legs.nonLlm.kind == NonLlmLegKind.NONE && !legs.nonLlm.ran &&
            !legs.anyThrew && legs.bothSupported && legs.roundTrip.pass ->
            AuditOutcome.NONLLM_LEG_NONE
        else -> AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW
    }

    /**
     * B5-RESHAPE — is this claim of an EQUATIONAL KIND (a machine-checkable equation is expected)?
     * Mirrors [SymPyLeg]'s own gate: INVARIANT, or GRADER_RULE that carries an invariant. Everything
     * else (DEFINITION, MISCONCEPTION_REFUTATION, STEM, prose GRADER_RULE with no invariant) is PROSE —
     * its deterministic non-LLM leg is the LIVE span↔claim round-trip, not SymPy. Keying on the CLAIM
     * KIND (not the resolved leg kind) keeps an equational claim whose checker merely COULDN'T RUN on the
     * uncertain floor (case 4) instead of letting the prose round-trip silently certify it.
     */
    private fun isEquationalKind(claim: VerificationClaim): Boolean =
        (claim.kind == ClaimKind.INVARIANT || claim.kind == ClaimKind.GRADER_RULE) &&
            !claim.invariant.isNullOrBlank()

    /**
     * Write the per-claim audit row in ONE transaction. Idempotent: if a row for (claimId, auditRunId)
     * already exists, write nothing and return false. The KC-level kc_verification_status aggregate is
     * written SEPARATELY, once per KC, by [finalizeKc] (FIX-B) — NOT here per claim.
     */
    private fun writeOnce(
        claim: VerificationClaim,
        legs: ResolvedLegs,
        outcome: AuditOutcome,
        perClaimStatus: VerificationStatus,
        auditRunId: String,
        notes: String,
    ): Boolean = transaction(db) {
        val already = VerificationAuditTable.selectAll()
            .where {
                (VerificationAuditTable.claimId eq claim.claimId) and
                    (VerificationAuditTable.auditRunId eq auditRunId)
            }
            .limit(1)
            .any()
        if (already) return@transaction false

        val now = clock()
        val span = legs.roundTrip.span ?: claim.source?.span
        val tf = legs.twoFamily

        VerificationAuditTable.insert {
            it[id] = TutorTypes.ulid()
            it[claimId] = claim.claimId
            it[kcId] = claim.kcId
            it[subject] = claim.subject
            it[claimKind] = claim.kind.name
            // FIX-A: the row carries this claim's OWN order-independent verdict, not a shared KC status.
            it[status] = perClaimStatus.name
            it[doc] = claim.source?.doc ?: ""
            it[page] = claim.source?.page?.takeIf { p -> p > 0 }
            it[pageAnchorStatus] = legs.roundTrip.pageAnchorStatus.name
            it[spanStart] = span?.start
            it[spanEnd] = span?.end
            it[relocatedOffset] = legs.roundTrip.span?.start
            it[fuzzyDistance] = 0
            it[familyA] = legA.family.name
            it[familyB] = legB.family.name
            it[nonllmLeg] = legs.nonLlm.kind.name
            it[nonllmResult] = legs.nonLlm.detail
            it[agree] = legs.agree
            it[roundtripPass] = legs.roundTrip.pass
            it[collapsedToOneFamily] = legs.collapsed
            it[auditedAt] = now
            it[VerificationAuditTable.auditRunId] = auditRunId
            it[VerificationAuditTable.notes] = (notes + (tf?.let { r -> " | ${r.details}" } ?: "")).take(8000)
        }
        true
    }

    private fun buildNotes(claim: VerificationClaim, legs: ResolvedLegs, outcome: AuditOutcome): String =
        buildString {
            append("outcome=").append(outcome)
            append(" agree=").append(legs.agree)
            append(" collapsed=").append(legs.collapsed)
            append(" nonllm[").append(legs.nonLlm.kind).append("]ran=").append(legs.nonLlm.ran)
                .append(",pass=").append(legs.nonLlm.pass)
            append(" roundtrip=").append(legs.roundTrip.pass)
            append(" anchor=").append(legs.roundTrip.pageAnchorStatus)
            if (legs.twoFamilyThrew) append(" LLM_LEG_THREW{").append(legs.twoFamilyError).append('}')
            if (legs.nonLlmThrew) append(" NONLLM_LEG_THREW{").append(legs.nonLlmError).append('}')
            if (legs.roundTripThrew) append(" ROUNDTRIP_LEG_THREW{").append(legs.roundTripError).append('}')
        }
}
