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
    /**
     * D3 / D-RF3 — the SINGLE-USER OWNER id stamped onto `report_wrong.resolved_by` when this
     * re-audit closes an OPEN dispute as REVERIFIED_FAITHFUL. The owner is the admin/verify caller (a
     * PC-side, owner-triggered re-audit, D7-legal) — NOT a new moderation actor (multi-user DEFERRED).
     * Defaulted so the hermetic test suite + the existing constructors need no change; production
     * passes the actual owner id.
     */
    private val ownerId: String = "owner",
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
        /**
         * B5r-2 — did THIS claim's cited quote relocate LIVE (the span↔claim round-trip passed)?
         * Read by [finalizeKc] to compute the KC-level `lecture_grounded` signal (D-R5/D-R6): a KC is
         * grounded iff EVERY claim round-tripped AND no claim resolved to `failed`. Carried on the
         * result (not just the audit row) so the conjunction is computed without re-reading the DB.
         */
        val roundTripPass: Boolean,
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
        /**
         * D3 — when true, THIS audit is the OWNER's explicit RE-VERIFY of a disputed KC: a re-grounding
         * audit closes the OPEN `report_wrong` as REVERIFIED_FAITHFUL (the resolution event) and then
         * relights. A routine batch re-audit (the default, false) NEVER closes a dispute and is held
         * DARK while any dispute is OPEN — the dispute strictly outranks. Owner-triggered, PC-side,
         * D7-legal (single-user owner, D-RF3).
         */
        ownerReverify: Boolean = false,
    ): List<AuditResult> {
        // D8: the per-KC content fingerprint over the AUDITED claim set for that KC. Computed ONCE up
        // front over the whole batch (a KC's hash folds in every claim being audited for it), then
        // stamped on each KC's kc_verification_status row. The serve gate recomputes the same hash from
        // the live content and shows `faithful` only on a match (else fail-closed to unverified).
        val hashByKc: Map<String, String> = claims
            .groupBy { it.kcId }
            .mapValues { (_, kcClaims) -> jarvis.content.ContentReconcile.kcContentHashOf(kcClaims) }

        // D1: the per-KC SOURCE-SPAN fingerprint over the round-trip's FOLDED located slice of the LIVE
        // `_sources` bytes (PC-side; the VPS never recomputes this). Computed up front over the whole
        // batch and stamped on each KC's B8 row alongside content_hash. The injected [rawSourceFor] is
        // claim-shaped; adapt it to the (subject, doc) resolver sourceSpanHashOf expects, mapping any
        // resolver throw (absent source) to null so the KC stores a NULL hash and fails CLOSED at serve.
        val sourceSpanByKc: Map<String, String?> = claims
            .groupBy { it.kcId }
            .mapValues { (_, kcClaims) ->
                jarvis.content.ContentReconcile.sourceSpanHashOf(kcClaims) { _, doc ->
                    val byDoc = kcClaims.firstOrNull { it.source?.doc == doc }
                    if (byDoc == null) null else runCatching { rawSourceFor(byDoc) }.getOrNull()
                }
            }

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
            finalizeKc(kcId, kcResults, auditRunId, hashByKc[kcId], sourceSpanByKc[kcId], ownerReverify)
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

    /**
     * B5r-2 (D-R5/D-R6), TIGHTENED by MF-1 (D-R17) — the KC-level LECTURE-GROUNDED signal: a claim
     * contributes to "grounded" ONLY if it is itself `faithful` (its OWN content was validated against
     * the lecture per its kind: SymPy + the promoted LLM vote for equational; the content-relocating
     * round-trip for DEFINITION; the LLM `bothSupported` vote on the rule text + the anchor round-trip
     * for prose non-DEFINITION). Computed from THIS run's per-claim results as a CONJUNCTION,
     * order-independent (mirrors [aggregateKc]):
     *   `grounded = perClaim.isNotEmpty() && perClaim.all { it.newStatus == faithful }`.
     *
     * This DROPS the old `roundTripPass`-based grounding, which trusted the mere relocation of an
     * UNRELATED anchor quote — the exact MF-1 false-faithful hole (a hallucinated prose rule whose anchor
     * relocated lit "matches your lecture"). It also subsumes the D-R14 fixture edge (a quote that
     * relocates but whose claim floored to uncertain no longer grounds the KC). An EMPTY KC ⇒ false.
     *
     * CONSEQUENCE (honest, per D-R17): a KC is now grounded == it is faithful at the KC level (every
     * claim faithful ⇒ [aggregateKc] is faithful too). A KC with any merely-`uncertain` claim (e.g. an
     * equational claim still awaiting its math confirmation) is NO LONGER grounded — it drops to
     * `unverified` at serve time until that claim's own content is independently confirmed. Expected +
     * safe: the badge promises lecture-grounding ONLY over content that was actually content-validated.
     */
    private fun groundedKc(perClaim: List<AuditResult>): Boolean =
        perClaim.isNotEmpty() &&
            perClaim.all { it.newStatus == VerificationStatus.faithful }

    /** Upsert kc_verification_status ONCE for [kcId] with the conjunction over its per-claim verdicts. */
    private fun finalizeKc(
        kcId: String,
        kcResults: List<AuditResult>,
        auditRunId: String,
        contentHash: String?,
        sourceSpanHash: String?,
        ownerReverify: Boolean,
    ) {
        val aggregate = aggregateKc(kcResults.map { it.newStatus })
        // B5r-2 — written ALONGSIDE [status], in the SAME transaction, from THIS run's per-claim results.
        val grounded = groundedKc(kcResults)
        val now = clock()
        transaction(db) {
            // D3 CLOSING EDGE (REVERIFIED_FAITHFUL) — when THIS audit is the OWNER's explicit re-verify
            // of the disputed KC ([ownerReverify]) AND it re-grounds at the current content, the owner's
            // re-verification IS the resolution event: CLOSE the OPEN dispute(s) FIRST (stamping the
            // single-owner resolver + timestamp, DELTA-3 provenance), so the relight-guard below then
            // sees NO open dispute and is permitted to relight — atomically, in THIS txn. A routine
            // (non-owner-reverify) re-audit NEVER auto-closes a dispute: the dispute strictly outranks.
            if (ownerReverify && aggregate == VerificationStatus.faithful && grounded) {
                ReportWrongQuery.closeOpenReports(
                    this, kcId, ReportResolution.REVERIFIED_FAITHFUL,
                    resolvedBy = ownerId, resolvedAt = now,
                )
            }

            // D3 RELIGHT-GUARD — re-read AFTER any owner close above (so the close is honored in-txn):
            // does an OPEN report_wrong dispute STILL exist? (fail-closed on throw ⇒ assume OPEN). While
            // a dispute is OPEN the dispute OUTRANKS the re-audit: hold the badge DARK (grounded=false +
            // NULL both staleness hashes) so servedHonestFloor stays UNVERIFIED. The status aggregate
            // still records what the re-audit found (honest), but the badge state is held dark. Reading
            // + writing in ONE txn means a crash can never re-open the relight hole.
            val stillDisputed = ReportWrongQuery.hasOpenReportWrong(this, kcId)
            val writeGrounded = if (stillDisputed) false else grounded
            val writeContentHash = if (stillDisputed) null else contentHash
            val writeSourceSpanHash = if (stillDisputed) null else sourceSpanHash

            val existing = KcVerificationStatusTable.selectAll()
                .where { KcVerificationStatusTable.kcId eq kcId }
                .singleOrNull()
            if (existing == null) {
                KcVerificationStatusTable.insert {
                    it[KcVerificationStatusTable.kcId] = kcId
                    it[status] = aggregate.name
                    it[lastAuditRunId] = auditRunId
                    it[KcVerificationStatusTable.contentHash] = writeContentHash
                    it[KcVerificationStatusTable.sourceSpanHash] = writeSourceSpanHash
                    it[lectureGrounded] = writeGrounded
                    it[updatedAt] = now
                }
            } else {
                KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                    it[status] = aggregate.name
                    it[lastAuditRunId] = auditRunId
                    it[KcVerificationStatusTable.contentHash] = writeContentHash
                    it[KcVerificationStatusTable.sourceSpanHash] = writeSourceSpanHash
                    it[lectureGrounded] = writeGrounded
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
            roundTripPass = legs.roundTrip.pass,
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
        /**
         * B5r-3 (D-R9) — did EITHER family return REFUTED? A REFUTED verdict (agreed OR disagreeing) is
         * an explicit contradiction signal that must stay `failed`, NOT soften to the D-R9 uncertain
         * floor. The D-R9 floor is for the genuinely-INCONCLUSIVE case (both families UNCLEAR / one
         * SUPPORTED + one UNCLEAR) where SymPy already proved the math but the LLM couldn't confirm the NL
         * meaning. False when the LLM leg threw (no verdict to read).
         */
        val anyRefuted: Boolean
            get() = twoFamily?.let { it.familyAVerdict == Verdict.REFUTED || it.familyBVerdict == Verdict.REFUTED } == true
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
     *  3p. DEFINITION claim (content==quote), roundtrip
     *     && !threw && !agreed-REFUTED                    → ALL_AGREE_ROUNDTRIP_NONLLM_PASS (faithful)
     *  3pr. PROSE non-DEFINITION claim (content≠quote:
     *     GRADER_RULE w/ invariant==null, MISCONCEPTION,
     *     STEM), BOTH-SUPPORTED && roundtrip && !threw    → ALL_AGREE_ROUNDTRIP_NONLLM_PASS (faithful)
     *  4. EQUATIONAL claim whose SymPy checker could NOT
     *     run (NONE/ran=false), BOTH-SUPPORTED, roundtrip
     *     passed, nothing threw                           → NONLLM_LEG_NONE (uncertain floor)
     *  4b. EQUATIONAL claim, SymPy RAN+PASSED, roundtrip
     *     passed, nothing threw, NOT agreed-REFUTED, but
     *     NOT bothSupported (LLM merely UNCLEAR)          → EQUATIONAL_LLM_UNCONFIRMED (uncertain, D-R9)
     *  4p. PROSE non-DEFINITION claim, roundtrip passed,
     *     nothing threw, NO family REFUTED, but NOT
     *     bothSupported (LLM did not confirm the RULE)    → PROSE_LLM_UNCONFIRMED (uncertain, D-R17)
     *  5. anything else (disagree / agreed-REFUTED /
     *     SymPy-FAIL / roundtrip fail / threw)             → DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW (failed)
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
     *  - DEFINITION kind (content == the cited quote): the LIVE span↔claim ROUND-TRIP *is* the
     *    deterministic non-LLM leg (case 3p). `faithful` iff `roundTrip.pass && !anyThrew && NOT
     *    agreed-REFUTED`. The round-trip re-locates the EXACT content (content==quote), so it is a
     *    genuine content check; the LLM/NLI `bothSupported` self-vote is NOT counted to PROMOTE (a
     *    DEFINITION's content==quote makes self-entailment zero-signal). The agreed-but-REFUTED VETO
     *    ([ResolvedLegs.agreedNonSupported]) still blocks faithful, so the LLM can only VETO a DEFINITION,
     *    never CERTIFY it. This is the structural unblock: a DEFINITION had NO machine check (NONE/
     *    ran=false) ⇒ every KC emits ≥1 DEFINITION ⇒ under the old conjunction NO KC could EVER be
     *    faithful. signatures-lock §2.5 amended per-kind with sign-off.
     *  - PROSE non-DEFINITION kind (content ≠ the cited quote — GRADER_RULE with invariant==null,
     *    MISCONCEPTION, STEM, …): MF-1 (D-R17). Here the round-trip only proves the UNRELATED anchor quote
     *    relocates — it does NOT validate the claim's OWN text (the rule / misconception / stem prose). So
     *    the round-trip ALONE is NOT a sufficient content check: it must be paired with an INDEPENDENT
     *    positive signal on the content. `faithful` iff `bothSupported && roundTrip.pass && !anyThrew`
     *    (case 3pr) — the LLM/NLI is PROMOTED from veto-only to a REQUIRED vote on the rule text. A
     *    round-tripping prose claim that is NOT bothSupported but had NO family REFUTED + nothing threw
     *    floors to UNCERTAIN ([AuditOutcome.PROSE_LLM_UNCONFIRMED], case 4p) — not-yet-content-confirmed,
     *    NOT a contradiction. A REFUTED (agreed or disagreeing) ⇒ `else` (failed). This closes the MF-1
     *    false-faithful hole where a FABRICATED rule reached faithful on an unrelated anchor's round-trip.
     *
     * No path reaches `faithful` without an applicable per-kind CONTENT check passing — SymPy + the
     * promoted LLM vote for equational kinds; the content-relocating round-trip for DEFINITION (content==
     * quote); the LLM `bothSupported` vote on the rule text PLUS the anchor round-trip for prose non-
     * DEFINITION (content≠quote) — AND no agreed/any REFUTED veto AND nothing throwing (the §2.5 LOCKED
     * invariant, per-kind re-scoped).
     */
    private fun decideOutcome(claim: VerificationClaim, legs: ResolvedLegs): AuditOutcome = when {
        claim.source?.span == null -> AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN
        legs.collapsed -> AuditOutcome.FAMILY_COLLAPSE
        // CLASS-KILLER (anyRefuted veto) — a REFUTED from EITHER family (agreed OR disagreeing) is an
        // explicit contradiction ⇒ NEVER faithful, for ANY claim kind. The [ResolvedLegs.anyRefuted]
        // rule (B5r-3 / D-R9) is enforced HERE, structurally, ABOVE every faithful-granting case so no
        // present-or-future branch can re-forget it (cases 3 / 3pr already exclude it via bothSupported;
        // this closes the DEFINITION case 3p, which previously vetoed only agreedNonSupported — the
        // disagreeing-REFUTED sibling of B5r-1(c2), test B5r-1(c3)). Routes to `failed` (the contradiction
        // signal), consistent with how the prose/equational floors treat anyRefuted.
        legs.anyRefuted -> AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW
        // case 3 — EQUATIONAL claim, SymPy ran+passed: UNCHANGED strong path.
        isEquationalKind(claim) &&
            legs.bothSupported && legs.nonLlm.ran && legs.nonLlm.pass && legs.roundTrip.pass && !legs.anyThrew ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        // case 3p (B5-RESHAPE, RESTRICTED by MF-1/D-R17 to DEFINITION) — a DEFINITION's content IS its
        // cited quote, so the LIVE round-trip re-locates the EXACT content ⇒ it is a genuine content
        // check. faithful iff round-trip passes + nothing threw + NOT agreed-REFUTED. The LLM vote is NOT
        // read to promote (self-entailment is zero-signal); it can only veto (agreedNonSupported).
        claim.kind == ClaimKind.DEFINITION &&
            legs.roundTrip.pass && !legs.anyThrew && !legs.agreedNonSupported ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        // case 3pr (MF-1 / D-R17) — a PROSE non-DEFINITION non-EQUATIONAL claim (content ≠ quote): the
        // round-trip only proves the UNRELATED anchor quote relocates, NOT the claim's own text. So it is
        // NOT sufficient alone — require an INDEPENDENT positive signal on the content: bothSupported (the
        // LLM/NLI family independently confirmed the RULE text) AND round-trip passes AND nothing threw.
        // This promotes the LLM from veto-only to a REQUIRED vote where content≠quote.
        !isEquationalKind(claim) && claim.kind != ClaimKind.DEFINITION &&
            legs.bothSupported && legs.roundTrip.pass && !legs.anyThrew ->
            AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS
        // case 4 — an EQUATIONAL claim whose SymPy/non-LLM checker could NOT run (NONE/ran=false) but
        // otherwise looks good: UNCERTAIN floor, never faithful (never-ran ≠ disagreed, FAIL-LOUD H5; the
        // round-trip is NOT a substitute for the equational check). Prose claims never reach here — case
        // 3p/3pr already routed a passing prose claim to faithful or case 4p to uncertain.
        isEquationalKind(claim) && legs.nonLlm.kind == NonLlmLegKind.NONE && !legs.nonLlm.ran &&
            !legs.anyThrew && legs.bothSupported && legs.roundTrip.pass ->
            AuditOutcome.NONLLM_LEG_NONE
        // case 4b (B5r-3 / D-R9) — an EQUATIONAL claim whose SymPy leg RAN + PASSED (the math is
        // machine-proved), round-trip passed, nothing threw, and NO family returned REFUTED — but the
        // families did not reach bothSupported either (they are merely UNCLEAR on the NL restatement, so
        // case 3 did not fire). This is a NOT-YET-CROSS-CHECKED state, NOT a contradiction: SymPy proved
        // the equation; the LLM simply couldn't confirm its plain-English meaning. Floor to UNCERTAIN,
        // never `failed`. A REFUTED from EITHER family (agreed-REFUTED OR a genuine SUPPORTED/REFUTED
        // disagreement) is an explicit contradiction signal ([anyRefuted]) and falls through to the
        // `else` (failed) — so this floor catches ONLY the genuinely-inconclusive LLM verdict.
        isEquationalKind(claim) &&
            legs.nonLlm.ran && legs.nonLlm.pass && legs.roundTrip.pass && !legs.anyThrew &&
            !legs.anyRefuted && !legs.bothSupported ->
            AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED
        // case 4p (MF-1 / D-R17) — a PROSE non-DEFINITION non-EQUATIONAL claim (content ≠ quote) whose
        // anchor round-tripped + nothing threw + NO family REFUTED, but the LLM family did NOT
        // independently confirm the RULE text (NOT bothSupported, so case 3pr did not fire). The round-
        // trip only proved the UNRELATED anchor quote — the claim's own text is NOT yet content-confirmed.
        // This is a not-yet-cross-checked state, NOT a contradiction ⇒ UNCERTAIN floor, never faithful.
        // A REFUTED from EITHER family ([anyRefuted], which also covers agreed-REFUTED) is an explicit
        // contradiction and falls through to the `else` (failed) — so this floor catches ONLY the
        // genuinely-inconclusive LLM verdict (both UNCLEAR / one SUPPORTED + one UNCLEAR).
        !isEquationalKind(claim) && claim.kind != ClaimKind.DEFINITION &&
            legs.roundTrip.pass && !legs.anyThrew && !legs.anyRefuted && !legs.bothSupported ->
            AuditOutcome.PROSE_LLM_UNCONFIRMED
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
