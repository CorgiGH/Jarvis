package jarvis.tutor.verify

import jarvis.tutor.ReportWrongTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * D3 / D-RF2 — the ONE shared, fail-closed-on-throw predicate over OPEN `report_wrong` disputes,
 * plus the single-owner closing-edge writer. Extracted as one named unit (council D-RF2 REFINE) so
 * the serve gate (`servedHonestFloor` / `resolveStatus`), the re-audit relight-guard
 * (`VerificationRunner.finalizeKc`), and the future Phase-3 queue/today filter ALL call the SAME
 * predicate — the fail-closed semantics live in ONE place and cannot drift across copy-pasted txn
 * blocks (3 chances to fail OPEN otherwise).
 *
 * This does NOT widen the frozen admission-only `VerificationGate.gate` caller-set (council UPHELD
 * option (b)): the gate stays pure + admission-only; D3's serve refusal is a SEPARATE always-on
 * inline lookup, never routed through a default-OFF feature-flagged gate (the fail-safe/feature-flag
 * anti-pattern). `report_wrong` is user-scoped, NOT an audit column, and is NOT in the D9 audit-col
 * allowlist — so neither OPEN nor RESOLVED rows ever sync to the VPS (D9 skip-OPEN-report stays).
 */
object ReportWrongQuery {

    /** The OPEN literal (an unresolved dispute). The resolution enum is [ReportResolution]. */
    const val OPEN: String = "OPEN"

    /**
     * Is there ANY OPEN `report_wrong` for [kcId]? FAIL-CLOSED: a query throw is treated as "assume
     * OPEN" (returns true ⇒ the caller refuses the badge / DENIes), NEVER "assume clear → serve". This
     * overload opens its own read txn (the serve-path entry point).
     */
    fun hasOpenReportWrong(db: Database, kcId: String): Boolean =
        try {
            transaction(db) { hasOpenReportWrong(this, kcId) }
        } catch (e: Throwable) {
            // FAIL-CLOSED: a lookup failure must darken the badge, never serve over a dispute. The
            // additive diagnostic closes the SILENT swallow (a recurring DB fault would otherwise
            // darken every faithful badge corpus-wide with no trail). kcId ONLY — never row data.
            failClosedDiag("db", kcId, e)
            true
        }

    /**
     * Is there ANY OPEN `report_wrong` for [kcId], WITHIN an already-open transaction (the
     * relight-guard path, so the read + the write are atomic in finalizeKc's txn). FAIL-CLOSED on
     * throw (assume OPEN).
     */
    fun hasOpenReportWrong(tx: Transaction, kcId: String): Boolean =
        try {
            with(tx) {
                ReportWrongTable.selectAll()
                    .where { (ReportWrongTable.kcId eq kcId) and (ReportWrongTable.resolution eq OPEN) }
                    .limit(1)
                    .any()
            }
        } catch (e: Throwable) {
            // FAIL-CLOSED (assume OPEN). Additive diagnostic — same silent-swallow fix as the Database
            // overload, on the relight-guard path (so a copy-pasted swallow can't darken silently here).
            failClosedDiag("tx", kcId, e)
            true
        }

    /**
     * Emit ONE bracketed `[ReportWrongQuery]` line on a fail-closed throw, BEFORE the `true` return.
     * States the overload (db|tx), the kcId, that it is failing CLOSED (assume OPEN dispute ⇒ badge
     * darkened), and the exception class + a truncated message. kcId ONLY — no report row data
     * (avoids leaking dispute content). No SLF4J in this codebase: System.err.println is the
     * convention (see BlockReminder / content.SourceOfRecord).
     */
    private fun failClosedDiag(overload: String, kcId: String, e: Throwable) {
        System.err.println(
            "[ReportWrongQuery] hasOpenReportWrong($overload) threw for kcId=$kcId — failing CLOSED " +
                "(assume OPEN dispute ⇒ badge darkened): ${e.javaClass.simpleName}: ${e.message?.take(160)}",
        )
    }

    /**
     * D3 closing edge — CLOSE every OPEN dispute for [kcId] with [resolution] (a terminal edge:
     * REVERIFIED_FAITHFUL or RETRACTED), stamping the single-owner [resolvedBy] + [resolvedAt]
     * (DELTA-3 provenance). Runs WITHIN the caller's transaction (atomic with the relight/dark write).
     * Returns the number of rows closed.
     */
    fun closeOpenReports(
        tx: Transaction,
        kcId: String,
        resolution: ReportResolution,
        resolvedBy: String,
        resolvedAt: Instant,
    ): Int = with(tx) {
        ReportWrongTable.update({
            (ReportWrongTable.kcId eq kcId) and (ReportWrongTable.resolution eq OPEN)
        }) {
            it[ReportWrongTable.resolution] = resolution.name
            it[ReportWrongTable.resolvedBy] = resolvedBy
            it[ReportWrongTable.resolvedAt] = resolvedAt
        }
    }
}

/**
 * D3 — the terminal closing transitions of a `report_wrong` dispute (the only escapes from OPEN, so
 * a frivolous report is never a permanent DoS-on-truth trap):
 *  - [REVERIFIED_FAITHFUL] — an OWNER re-audit re-grounded the disputed KC at the CURRENT content
 *    (the re-audit IS the resolution event); only THEN may the badge relight.
 *  - [RETRACTED] — the OWNER accepted the report (the content was genuinely wrong); the KC stays dark
 *    / gets corrected.
 * The full multi-actor moderation UI stays DEFERRED; these EXIT EDGES do not.
 */
enum class ReportResolution { REVERIFIED_FAITHFUL, RETRACTED }
