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
        val results = ArrayList<AuditResult>(claims.size)
        for (claim in claims) {
            results.add(auditOne(claim, auditRunId))
        }
        return results
    }

    /** Resolve every leg (try/catch each), compute the outcome+status, THEN write once. */
    private suspend fun auditOne(claim: VerificationClaim, auditRunId: String): AuditResult {
        // ---- 1. RESOLVE ALL LEGS (no DB; each leg in its OWN try/catch) -----------------------
        val legs = resolveLegs(claim)

        // ---- 2. COMPUTE THE OUTCOME ----------------------------------------------------------
        val outcome = decideOutcome(claim, legs)

        // ---- 3. READ CURRENT STATUS + APPLY THE PURE TRANSITION ------------------------------
        val prior = readStatus(claim)
        val newStatus = VerificationStatus_.transition(prior, outcome)

        val notes = buildNotes(claim, legs, outcome)

        // ---- 4/5. WRITE ONCE (idempotent on auditRunId) --------------------------------------
        val written = writeOnce(claim, legs, outcome, newStatus, auditRunId, notes)

        return AuditResult(
            claimId = claim.claimId,
            kcId = claim.kcId,
            priorStatus = prior,
            outcome = outcome,
            newStatus = newStatus,
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
     *  3. BOTH-SUPPORTED && nonllm ran&pass && roundtrip
     *     && !threw                                       → ALL_AGREE_ROUNDTRIP_NONLLM_PASS (faithful)
     *  4. the ONLY shortfall is a NONE non-LLM checker
     *     (BOTH-SUPPORTED, roundtrip passed, nothing threw)→ NONLLM_LEG_NONE (uncertain floor)
     *  5. anything else (disagree / agreed-REFUTED /
     *     roundtrip fail / threw)                          → DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW (failed)
     *
     * F1: case 3 + case 4 read [ResolvedLegs.bothSupported], NOT [ResolvedLegs.agree]. An AGREED
     * verdict that is not SUPPORTED (e.g. both families REFUTED) is NOT faithful and NOT the uncertain
     * floor — it falls through to the `else` (failed). Agreement on the WRONG answer must never certify.
     *
     * No path reaches `faithful` without BOTH a non-LLM-leg pass AND families-agree-on-SUPPORTED (the
     * §2.5 LOCKED invariant), enforced by case 3's conjunction.
     */
    private fun decideOutcome(claim: VerificationClaim, legs: ResolvedLegs): AuditOutcome = when {
        claim.source?.span == null -> AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN
        legs.collapsed -> AuditOutcome.FAMILY_COLLAPSE
        legs.bothSupported && legs.nonLlm.ran && legs.nonLlm.pass && legs.roundTrip.pass && !legs.anyThrew ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        legs.nonLlm.kind == NonLlmLegKind.NONE && !legs.nonLlm.ran && !legs.anyThrew &&
            legs.bothSupported && legs.roundTrip.pass ->
            AuditOutcome.NONLLM_LEG_NONE
        else -> AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW
    }

    /** Read the KC's CURRENT resolved status from the B8 table; fall back to unverified (no row yet). */
    private fun readStatus(claim: VerificationClaim): VerificationStatus = transaction(db) {
        val row = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq claim.kcId }
            .singleOrNull()
        val literal = row?.get(KcVerificationStatusTable.status)
        literal?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
            ?: VerificationStatus.unverified
    }

    /**
     * Write the audit row + upsert kc_verification_status in ONE transaction. Idempotent: if a row
     * for (claimId, auditRunId) already exists, write nothing and return false.
     */
    private fun writeOnce(
        claim: VerificationClaim,
        legs: ResolvedLegs,
        outcome: AuditOutcome,
        newStatus: VerificationStatus,
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
            it[status] = newStatus.name
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

        // -- B8 upsert: SELECT-then-UPDATE-or-INSERT (the codebase idiom) ----------------------
        val existing = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq claim.kcId }
            .singleOrNull()
        if (existing == null) {
            KcVerificationStatusTable.insert {
                it[kcId] = claim.kcId
                it[status] = newStatus.name
                it[lastAuditRunId] = auditRunId
                it[updatedAt] = now
            }
        } else {
            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq claim.kcId }) {
                it[status] = newStatus.name
                it[lastAuditRunId] = auditRunId
                it[updatedAt] = now
            }
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
