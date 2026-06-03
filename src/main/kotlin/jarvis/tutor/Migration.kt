package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Phase-1 migration runner (data-model-lock Task 3, master-impl-plan-v2 §2.1).
 *
 * SOLE place schema ALTERs + the explicit post-ALTER backfills live. Every CHANGE-1/2
 * column add + its backfill runs inside ONE try/catch guard.
 *
 * LOAD-BEARING migration facts (memorize):
 *  - Exposed `.default()` is CLIENT-SIDE insert default only — it does NOT emit a SQL DEFAULT.
 *    `createMissingTablesAndColumns` emits `ALTER TABLE … ADD COLUMN … <type>` with NO default,
 *    so on SQLite every existing row gets the new column = NULL. ⇒ every non-null-intent new
 *    column needs an explicit post-ALTER `UPDATE … WHERE col IS NULL` in the SAME boot txn.
 *  - SQLite `ALTER TABLE` is NOT transactional: a multi-column add that fails midway leaves
 *    the DB half-migrated and CANNOT be rolled back by the surrounding `transaction{}`. ⇒ on a
 *    failed ALTER we log the failing column, fire an auto off-box backup, and abort non-zero.
 *    Recovery = restore-from-backup (M-PARTIAL).
 *  - Schema is NEVER ALTERed before a verified off-box `tools/db-backup.py` dump asserting
 *    MIN_EXPECTED_CARDS=800 (M-DB). The operator runs that BEFORE boot; the [backupHook] here
 *    is the M-PARTIAL *recovery* dump fired only when a mid-migration ALTER fails.
 *
 * IDEMPOTENT by design: `createMissingTablesAndColumns` only adds what is missing, and every
 * backfill is `WHERE col IS NULL`-guarded, so a second run is a no-op (M-IDEMP).
 */

/** Every table the migration registers — existing schema + Phase-1 new tables (CHANGE 4–10). */
private val ALL_TABLES = arrayOf(
    UsersTable,
    TokensTable,
    SessionsTable,
    MagicLinkTokensTable,
    AiLiteracyConfirmationTable,
    ConsentLogTable,
    UserPreferencesTable,
    TasksTable,
    SensorEventsTable,
    TrustGrantsTable,
    AuditLinesTable,
    KnowledgeGapsTable,
    FsrsCardsTable,
    ProviderConfigTable,
    EffectorAttemptsTable,
    CardActionLogTable,
    jarvis.tutor.taskdetect.DetectedTaskMappingTable,
    TaskPrepTable,
    KcMasteryTable,
    // Phase-1 new tables (CHANGE 4–10)
    SessionSummariesTable,
    AttemptsTable,
    VerificationAuditTable,
    ReportWrongTable,
    ExamDatesTable,
    KcVerificationStatusTable,
)

/** Outcome of a migration run. */
sealed class MigrationResult {
    object Success : MigrationResult()
    data class Failure(val failingColumn: String?, val cause: Throwable) : MigrationResult()
}

/**
 * Thrown when a mid-migration ALTER/backfill fails. Carries the best-effort failing-column
 * name so the operator knows what to inspect before restoring from backup.
 */
class MigrationException(
    val failingColumn: String?,
    cause: Throwable,
) : RuntimeException("Phase-1 migration failed at column=${failingColumn ?: "<unknown>"}: ${cause.message}", cause)

object TutorMigration {

    /**
     * Run the Phase-1 migration: create/alter all tables, then the explicit post-ALTER
     * backfills, inside one try/catch guard.
     *
     * @param db          the target DB (tests pass an in-memory / temp SQLite DB — NEVER the live DB).
     * @param backupHook  M-PARTIAL recovery hook, fired ONLY on a mid-migration failure (auto
     *                    off-box dump). Default shells out to tools/db-backup.py. Tests pass a
     *                    no-op / spy. The hook itself is best-effort — a backup failure must NOT
     *                    mask the original migration error.
     * @param failAfter   TEST SEAM ONLY. When non-null, forces a failure naming this column AFTER
     *                    tables are created but during the backfill phase, to exercise the
     *                    abort + backup-trigger path (M-PARTIAL). Production callers leave it null.
     * @throws MigrationException on any failure (after firing [backupHook]).
     */
    fun migrate(
        db: Database,
        backupHook: (failingColumn: String?) -> Unit = ::defaultBackupHook,
        failAfter: String? = null,
    ): MigrationResult {
        return try {
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)

                // TEST SEAM (M-PARTIAL): simulate a failed ALTER/backfill. Throws AFTER the table
                // create so the abort+backup path runs over a partially-migrated shape, mirroring
                // the real SQLite "ALTER added some columns then died" scenario.
                if (failAfter != null) {
                    throw MigrationColumnFailure(failAfter)
                }

                // --- Explicit post-ALTER backfills (same boot txn, immediately after the ALTERs) ---

                // CHANGE 1: existing rows got status=NULL from the bare ALTER; set ACTIVE.
                exec("UPDATE fsrs_cards SET status = 'ACTIVE' WHERE status IS NULL")

                // CHANGE 1: RUBRIC_CRITERION cards carry their KC id in source_ref; copy it into
                // the new kc_id column. 0-row on the live MANUAL-only corpus (Conflict §3); additive.
                exec(
                    "UPDATE fsrs_cards SET kc_id = source_ref " +
                        "WHERE source = 'RUBRIC_CRITERION' AND kc_id IS NULL",
                )

                // CHANGE 2: replay PhaseModel.transition over existing (ewma_score, observations)
                // and write phase WHERE phase IS NULL. No SQL-side replay — iterate in-txn.
                backfillKcMasteryPhase()
            }
            MigrationResult.Success
        } catch (e: Throwable) {
            val failingColumn = extractFailingColumn(e)
            System.err.println(
                "[TutorMigration] ABORT — migration failed" +
                    (failingColumn?.let { " at column '$it'" } ?: "") +
                    ": ${e.message?.take(200)}. Firing M-PARTIAL auto-backup; " +
                    "recovery = restore-from-backup (SQLite ALTER is NOT transactional).",
            )
            // M-PARTIAL: fire the auto off-box dump. Best-effort — never let a backup failure
            // swallow the original migration error.
            try {
                backupHook(failingColumn)
            } catch (be: Throwable) {
                System.err.println("[TutorMigration] WARN auto-backup hook failed: ${be.message?.take(160)}")
            }
            throw MigrationException(failingColumn, e)
        }
    }

    /**
     * CHANGE-2 phase backfill. Reads each kc_mastery row with phase IS NULL, computes
     * PhaseModel.transition over its (ewma_score, observations), and writes the phase.
     * Idempotent: the WHERE phase IS NULL guard makes a re-run a no-op.
     *
     * Uses raw SQL via exec for the read so it does not depend on the Exposed column set
     * matching exactly; the write is per-row UPDATE in the same txn.
     */
    private fun org.jetbrains.exposed.sql.Transaction.backfillKcMasteryPhase() {
        data class Row(val userId: String, val kcId: String, val ewma: Double, val obs: Int)
        val rows = mutableListOf<Row>()
        exec(
            "SELECT user_id, kc_id, ewma_score, observations FROM kc_mastery WHERE phase IS NULL",
        ) { rs ->
            while (rs.next()) {
                rows.add(
                    Row(
                        userId = rs.getString(1),
                        kcId = rs.getString(2),
                        ewma = rs.getDouble(3),
                        obs = rs.getInt(4),
                    ),
                )
            }
        }
        for (r in rows) {
            val mastered = r.ewma >= KcMastery.MASTERY_THRESHOLD && r.obs >= KcMastery.MIN_OBSERVATIONS
            val phase = PhaseModel.transition(r.ewma, r.obs, mastered, current = null)
            // Parameterized-by-escaping: user_id/kc_id are app-controlled ids (no quotes); the
            // phase literal is a fixed enum name. Single-quote-escape defensively anyway.
            val u = r.userId.replace("'", "''")
            val k = r.kcId.replace("'", "''")
            exec(
                "UPDATE kc_mastery SET phase = '${phase.name}' " +
                    "WHERE user_id = '$u' AND kc_id = '$k' AND phase IS NULL",
            )
        }
    }

    /** Best-effort failing-column extraction from an exception message / our test seam. */
    private fun extractFailingColumn(e: Throwable): String? {
        if (e is MigrationColumnFailure) return e.column
        if (e is MigrationException) return e.failingColumn
        // SQLite ALTER errors typically mention the column; surface the message tail as a hint.
        return e.message?.let { msg ->
            Regex("column[ :\"']+([a-zA-Z0-9_]+)").find(msg)?.groupValues?.getOrNull(1)
        }
    }

    /**
     * Default M-PARTIAL recovery backup hook: shell out to tools/db-backup.py for an off-box dump.
     * Best-effort and synchronous. Only fires on a mid-migration failure.
     */
    private fun defaultBackupHook(failingColumn: String?) {
        val scriptPath = java.nio.file.Path.of("tools", "db-backup.py")
        if (!java.nio.file.Files.exists(scriptPath)) {
            System.err.println(
                "[TutorMigration] M-PARTIAL: tools/db-backup.py not found at $scriptPath; " +
                    "operator MUST take a manual off-box dump before restoring (failing column=$failingColumn).",
            )
            return
        }
        try {
            val proc = ProcessBuilder("python3", scriptPath.toString(), "backups")
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
            System.err.println("[TutorMigration] M-PARTIAL auto-backup exit=${proc.exitValue()}")
        } catch (e: Throwable) {
            System.err.println("[TutorMigration] M-PARTIAL auto-backup invoke failed: ${e.message?.take(160)}")
        }
    }
}

/** Internal test-seam / forced-failure carrier (named so the column surfaces in the abort log). */
internal class MigrationColumnFailure(val column: String) :
    RuntimeException("forced migration failure at column '$column'")
