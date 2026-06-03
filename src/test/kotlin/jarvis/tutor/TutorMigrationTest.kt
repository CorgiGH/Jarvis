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
