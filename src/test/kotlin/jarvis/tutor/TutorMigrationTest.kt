package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Task 3 — migration runner skeleton + M-PARTIAL.
 *
 * All DB tests use a TEMP SQLite DB (never ~/.jarvis/tutor.db).
 */
class TutorMigrationTest {

    private fun tempDb(tmp: Path): Database =
        TutorDb.connect(tmp.resolve("mig.db").toString())

    @Test
    fun `migrate on clean db succeeds and creates all new columns and tables`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val result = TutorMigration.migrate(db)
        assertEquals(MigrationResult.Success, result)

        // CHANGE 1 columns exist on fsrs_cards.
        assertTrue(columnExists(db, "fsrs_cards", "kc_id"))
        assertTrue(columnExists(db, "fsrs_cards", "status"))
        assertTrue(columnExists(db, "fsrs_cards", "paused_at"))
        // CHANGE 2 columns exist on kc_mastery.
        assertTrue(columnExists(db, "kc_mastery", "phase"))
        assertTrue(columnExists(db, "kc_mastery", "entry_phase"))
        // CHANGE 4–10 tables exist.
        for (t in listOf(
            "session_summaries", "attempts", "verification_audit",
            "report_wrong", "exam_dates", "kc_verification_status",
        )) {
            assertTrue(tableExists(db, t), "expected table $t to exist after migrate")
        }
    }

    @Test
    fun `partial failure aborts naming the failing column and fires auto-backup`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val backupCalls = AtomicInteger(0)
        var reportedColumn: String? = null

        val ex = assertFails {
            TutorMigration.migrate(
                db,
                backupHook = { col ->
                    backupCalls.incrementAndGet()
                    reportedColumn = col
                },
                failAfter = "status",
            )
        }
        // Aborts with a MigrationException naming the failing column.
        assertTrue(ex is MigrationException, "expected MigrationException, got ${ex::class.simpleName}")
        assertEquals("status", (ex as MigrationException).failingColumn)
        // M-PARTIAL: the auto-backup hook fired exactly once, with the failing column.
        assertEquals(1, backupCalls.get(), "auto-backup hook must fire exactly once on abort")
        assertEquals("status", reportedColumn)
    }

    @Test
    fun `a backup hook failure does not mask the original migration error`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val ex = assertFails {
            TutorMigration.migrate(
                db,
                backupHook = { throw IllegalStateException("backup blew up") },
                failAfter = "phase",
            )
        }
        // The thrown error is still the MigrationException naming the migration column,
        // NOT the backup's IllegalStateException.
        assertTrue(ex is MigrationException)
        assertEquals("phase", (ex as MigrationException).failingColumn)
    }

    @Test
    fun `migrate is idempotent — second run is a clean no-op`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        // Second boot — must not throw, must still report success.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        assertTrue(columnExists(db, "fsrs_cards", "status"))
    }

    // --- DELTA-4: the v2 hash-flip pre-flight HARD abort ----------------------------------------

    @Test
    fun `DELTA-4 the v2 flip ABORTS when a live content_hash row exists`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // First boot (no flip) creates the schema incl. kc_verification_status.content_hash.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db, v2HashFlipEnabled = { false }))
        // Seed a STRAY live content_hash row (the empty-table premise violated).
        transaction(db) {
            exec(
                "INSERT INTO kc_verification_status (kc_id, status, content_hash, updated_at) " +
                    "VALUES ('pa-kc-001', 'faithful', 'deadbeef', '2026-06-04T00:00:00Z')",
            )
        }
        // Now the operator opts into the v2 flip ⇒ HARD abort (a stray v1 row must not be re-keyed).
        val ex = assertFails {
            TutorMigration.migrate(db, backupHook = {}, v2HashFlipEnabled = { true })
        }
        assertTrue(ex is MigrationException, "the v2 flip must abort loudly via MigrationException, got ${ex::class.simpleName}")
        assertTrue(
            ex.message?.contains("DELTA-4") == true || ex.cause is V2HashFlipAbort,
            "the abort must name the DELTA-4 v2-flip pre-flight: ${ex.message}",
        )
    }

    @Test
    fun `DELTA-4 the v2 flip SUCCEEDS on an empty content_hash table`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // Flip enabled, but 0 content_hash rows (the live reality) ⇒ the assertion passes, migrate ok.
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, v2HashFlipEnabled = { true }),
            "the v2 flip is a no-op + succeeds when there are 0 live content_hash rows",
        )
    }

    @Test
    fun `DELTA-4 a non-flip boot does NOT abort even when a content_hash row exists`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db, v2HashFlipEnabled = { false }))
        transaction(db) {
            exec(
                "INSERT INTO kc_verification_status (kc_id, status, content_hash, updated_at) " +
                    "VALUES ('pa-kc-001', 'faithful', 'deadbeef', '2026-06-04T00:00:00Z')",
            )
        }
        // A normal boot (flip OFF) must NOT run the pre-flight ⇒ no abort, success.
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, v2HashFlipEnabled = { false }),
            "a normal (non-flip) boot skips the pre-flight assertion entirely",
        )
    }

    companion object {
        /** True iff [table] has a column named [column] (PRAGMA table_info). */
        fun columnExists(db: Database, table: String, column: String): Boolean = transaction(db) {
            var found = false
            exec("PRAGMA table_info($table)") { rs ->
                while (rs.next()) {
                    if (rs.getString("name").equals(column, ignoreCase = true)) found = true
                }
            }
            found
        }

        /** True iff [table] exists in sqlite_master. */
        fun tableExists(db: Database, table: String): Boolean = transaction(db) {
            var found = false
            exec("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'") { rs ->
                if (rs.next()) found = true
            }
            found
        }
    }
}
