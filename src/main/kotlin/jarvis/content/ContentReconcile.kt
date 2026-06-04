package jarvis.content

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.VerificationClaim
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant

/**
 * curate-tutor **Stage-9 reconcile** (Batch-4, master-plan Area B). The bridge between authored
 * content (`content/{subject}/`) and the runtime trust-net (Phase 2). It runs AFTER
 * `validateContent` succeeds (a malformed corpus must never be reconciled into the audit queue).
 *
 * For each [KnowledgeConcept] the reconcile:
 *  1. emits its [VerificationClaim](s) with a CONTENT-HASH `claimId`
 *     `"{kcId}:{KIND}:{sha256_8(content)}"` (M-CLAIM). Reorder-stable: the id is addressed by its
 *     content, never a positional ordinal, so re-running (or shuffling `grader_rules`) yields the
 *     SAME claimIds.
 *  2. sets `kc_verification_status = pending` DIRECTLY (the §2.4 UNVERIFIED→PENDING edge) — NOT via
 *     [jarvis.tutor.VerificationStatus_.transition]. An unaudited KC simply enters the audit queue
 *     at `pending`; certification to `faithful` happens later, only through `VerificationRunner.audit`.
 *  3. is IDEMPOTENT and FAIL-SAFE (H10): a re-run yields identical claimIds, and it NEVER regresses a
 *     KC whose runtime status is already `faithful` (or `uncertain`/`failed`/`pending`) back to
 *     `pending`. ONLY a missing row or an explicit `unverified` row is promoted to `pending`.
 *
 * The claim emission ([claimsFor]) is PURE (no DB) so it is unit-testable in isolation; only
 * [reconcile] touches the B8 table. NOT a wire type — the offline curate/CLI path consumes the
 * [ReconcileReport].
 */
object ContentReconcile {

    /**
     * The set of claims emitted by reconcile + the set of KCs moved to `pending` this run. NOT a
     * wire type — the curate/CLI path reads it to report what was reconciled.
     */
    data class ReconcileReport(
        /** Every [VerificationClaim] emitted across all reconciled KCs (the audit queue input). */
        val claims: List<VerificationClaim>,
        /** kcIds whose `kc_verification_status` was set to `pending` this run (the UNVERIFIED→PENDING
         *  promotions). Already-audited KCs (faithful/uncertain/failed) are NOT in this set. */
        val pendingSet: Set<String>,
    )

    /**
     * Emit one KC's [VerificationClaim]s — PURE, reorder-stable, no DB.
     *
     * Emission rules (each claim's `content` is what `sha256_8` hashes for the id):
     *  - **DEFINITION** — one per `source` ref, `content = ref.quote`, `source = that ref` (so the
     *    span-anchored round-trip leg can re-locate it).
     *  - **INVARIANT** — one when `invariant != null`, `content = invariant`,
     *    `source = the KC's first span-bearing ref` (the gold span the round-trip leg anchors on).
     *  - **GRADER_RULE** — one per `grader_rules` entry, `content = the rule`,
     *    `source = the KC's first span-bearing ref`.
     *
     * Reorder-stability: claimIds are content-addressed, so the RETURNED set is independent of the
     * order of `grader_rules` / `source`. The list is sorted by `claimId` for a deterministic order.
     */
    fun claimsFor(kc: KnowledgeConcept): List<VerificationClaim> {
        val out = ArrayList<VerificationClaim>()
        // The gold span the equational legs anchor on: the KC's first span-bearing source ref.
        val anchorRef = kc.source.firstOrNull { it.span != null }

        // DEFINITION claims — one per source ref, anchored on that ref's own span.
        for (ref in kc.source) {
            out += claim(kc, ClaimKind.DEFINITION, content = ref.quote, invariant = null, source = ref)
        }
        // INVARIANT claim — the KC's precise machine-checkable equation.
        kc.invariant?.let { inv ->
            out += claim(kc, ClaimKind.INVARIANT, content = inv, invariant = inv, source = anchorRef)
        }
        // GRADER_RULE claims — one per authored rule.
        for (rule in kc.grader_rules) {
            out += claim(kc, ClaimKind.GRADER_RULE, content = rule, invariant = kc.invariant, source = anchorRef)
        }
        // Deterministic, reorder-stable order by the content-addressed id.
        return out.sortedBy { it.claimId }
    }

    private fun claim(
        kc: KnowledgeConcept,
        kind: ClaimKind,
        content: String,
        invariant: String?,
        source: SourceRef?,
    ) = VerificationClaim(
        claimId = claimId(kc.id, kind, content),
        kcId = kc.id,
        subject = kc.subject,
        kind = kind,
        content = content,
        invariant = invariant,
        source = source,
    )

    /** The frozen content-hash id `"{kcId}:{KIND}:{sha256_8(content)}"` (M-CLAIM). */
    fun claimId(kcId: String, kind: ClaimKind, content: String): String =
        "$kcId:${kind.name}:${sha256_8(content)}"

    /** First 8 lowercase-hex chars of `SHA-256(content)`. Reorder-stable content hash (M-CLAIM). */
    fun sha256_8(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(8)
        for (i in 0 until 4) sb.append("%02x".format(digest[i]))
        return sb.toString()
    }

    /**
     * Run the reconcile over every KC in [subjects], writing to the B8 `kc_verification_status`
     * table on [db]. Returns the emitted claims + the set of KCs promoted to `pending`.
     *
     * H10 / FAIL-SAFE upsert per KC:
     *  - NO existing row  ⇒ INSERT `status = pending` (UNVERIFIED→PENDING, the §2.4 edge, set DIRECTLY).
     *  - row == `unverified` ⇒ UPDATE to `pending` (an explicit seed is promoted into the queue).
     *  - any other status (`pending`/`faithful`/`uncertain`/`failed`) ⇒ LEFT UNTOUCHED. A faithful KC
     *    is NEVER regressed to pending; an already-pending KC stays pending (idempotent).
     *
     * @param clock injected wall-clock seam for the `updated_at` write (hermetic in tests).
     */
    fun reconcile(
        subjects: List<LoadedSubject>,
        db: Database,
        clock: () -> Instant = Instant::now,
    ): ReconcileReport {
        val allClaims = ArrayList<VerificationClaim>()
        val promoted = LinkedHashSet<String>()

        for (sub in subjects) {
            for (kc in sub.kcs) {
                allClaims += claimsFor(kc)
                if (setPending(db, kc.id, clock())) promoted += kc.id
            }
        }
        return ReconcileReport(claims = allClaims, pendingSet = promoted)
    }

    /**
     * Set a single KC's runtime status to `pending` iff it is currently absent or `unverified`.
     * Returns true iff this call moved the KC into `pending` (a fresh promotion this run).
     * NEVER regresses an already-audited status (H10).
     */
    private fun setPending(db: Database, kcId: String, now: Instant): Boolean = transaction(db) {
        val existing = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()

        if (existing == null) {
            // No row yet: enter the audit queue at pending (UNVERIFIED→PENDING, set DIRECTLY).
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = now
            }
            return@transaction true
        }

        val current = existing[KcVerificationStatusTable.status]
        // ONLY an explicit `unverified` seed is promoted. faithful/uncertain/failed/pending stay put
        // (H10: never regress a faithful KC back to pending; never re-open an audited verdict).
        if (current == VerificationStatus.unverified.name) {
            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = now
            }
            return@transaction true
        }
        false
    }
}
