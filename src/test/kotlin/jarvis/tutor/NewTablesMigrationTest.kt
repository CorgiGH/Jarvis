package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 12 — register the 6 new tables (CHANGE 4–10) in the migration.
 *
 * All DB tests use a TEMP SQLite DB (never ~/.jarvis/tutor.db).
 */
class NewTablesMigrationTest {

    private fun tempDb(tmp: Path): Database =
        TutorDb.connect(tmp.resolve("nt.db").toString())

    private val sixTables = listOf(
        "session_summaries", "attempts", "verification_audit",
        "report_wrong", "exam_dates", "kc_verification_status",
    )

    @Test
    fun `all six tables created`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        TutorMigration.migrate(db)
        for (t in sixTables) {
            assertTrue(TutorMigrationTest.tableExists(db, t), "expected table $t after migrate")
        }
    }

    @Test
    fun `kc_verification_status default is unverified`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        TutorMigration.migrate(db)
        // Insert a row WITHOUT specifying status (relies on the Exposed client-side default).
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-001"
                it[updatedAt] = java.time.Instant.now()
            }
        }
        val status = transaction(db) {
            var s: String? = null
            exec("SELECT status FROM kc_verification_status WHERE kc_id='pa-kc-001'") { rs ->
                if (rs.next()) s = rs.getString(1)
            }
            s
        }
        assertEquals("unverified", status)
    }

    @Test
    fun `attempts index on user_id kc_id graded_at is present`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        TutorMigration.migrate(db)
        // PRAGMA index_list + index_info — find an index over (user_id, kc_id, graded_at).
        val cols = transaction(db) {
            val indexNames = mutableListOf<String>()
            exec("PRAGMA index_list(attempts)") { rs ->
                while (rs.next()) indexNames.add(rs.getString("name"))
            }
            indexNames.firstNotNullOfOrNull { idx ->
                val c = mutableListOf<String>()
                exec("PRAGMA index_info($idx)") { rs -> while (rs.next()) c.add(rs.getString("name")) }
                if (c.containsAll(listOf("user_id", "kc_id", "graded_at"))) c else null
            }
        }
        assertTrue(
            cols != null,
            "expected an attempts index covering (user_id, kc_id, graded_at)",
        )
    }

    @Test
    fun `enum columns are varchar not native enum`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        TutorMigration.migrate(db)
        // SQLite has no native enum; assert the enum-typed columns report a VARCHAR/TEXT affinity
        // (sample a few representative ones across the new tables).
        val checks = mapOf(
            "attempts" to "student_confidence",
            "attempts" to "phase",
            "verification_audit" to "claim_kind",
            "report_wrong" to "resolution",
            "kc_verification_status" to "status",
        )
        for ((table, col) in checks) {
            val type = columnType(db, table, col)
            assertTrue(
                type != null && (type.uppercase().startsWith("VARCHAR") || type.uppercase() == "TEXT"),
                "$table.$col should be VARCHAR/TEXT (enum-as-varchar), was $type",
            )
        }
    }

    private fun columnType(db: Database, table: String, col: String): String? = transaction(db) {
        var t: String? = null
        exec("PRAGMA table_info($table)") { rs ->
            while (rs.next()) {
                if (rs.getString("name").equals(col, ignoreCase = true)) t = rs.getString("type")
            }
        }
        t
    }
}
