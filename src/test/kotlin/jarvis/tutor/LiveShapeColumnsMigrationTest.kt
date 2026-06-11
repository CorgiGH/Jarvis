package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-1 Tasks 4+5 — pins the EXACT live-DB column deltas the trust-net flip migration closes.
 * Live DDL captured READ-ONLY from ~/.jarvis/tutor.db on 2026-06-11:
 *   kc_verification_status = [kc_id, status, last_audit_run_id, updated_at]            (4 cols, 0 rows)
 *   report_wrong           = [id, user_id, kc_id, card_id, grade_attempt_raw,
 *                             reported_at, resolution]                                  (7 cols, 0 rows)
 * Code (Phase1Tables.kt) additionally defines content_hash/lecture_grounded/source_span_hash
 * (KcVerificationStatusTable) and resolved_by/resolved_at (ReportWrongTable, D-RF3).
 *
 * Replica technique: full-migrate a fresh DB, then DROP the new columns (sqlite-jdbc 3.45.3.0
 * bundles SQLite >= 3.35, which supports ALTER TABLE … DROP COLUMN) — yielding the exact live
 * shape — then re-migrate and assert the deltas.
 */
class LiveShapeColumnsMigrationTest {

    private fun liveShapeDb(tmp: Path, drops: Map<String, List<String>>): Database {
        val db = TutorDb.connect(tmp.resolve("live-shape.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        transaction(db) {
            for ((table, cols) in drops) for (c in cols) exec("ALTER TABLE $table DROP COLUMN $c")
        }
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `kc_verification_status gains exactly content_hash lecture_grounded source_span_hash`(@TempDir tmp: Path) {
        val db = liveShapeDb(
            tmp,
            mapOf("kc_verification_status" to listOf("content_hash", "lecture_grounded", "source_span_hash")),
        )
        assertEquals(
            listOf("kc_id", "status", "last_audit_run_id", "updated_at"),
            columnsOf(db, "kc_verification_status"),
            "replica must match the live 4-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            setOf(
                "kc_id", "status", "last_audit_run_id", "updated_at",
                "content_hash", "lecture_grounded", "source_span_hash",
            ),
            columnsOf(db, "kc_verification_status").toSet(),
        )

        // idempotent (M-IDEMP): a second run is a no-op
        TutorMigration.migrate(db)
        assertEquals(7, columnsOf(db, "kc_verification_status").size)
    }

    @Test
    fun `report_wrong gains exactly resolved_by and resolved_at, both nullable (D-RF3)`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp, mapOf("report_wrong" to listOf("resolved_by", "resolved_at")))
        assertEquals(
            listOf("id", "user_id", "kc_id", "card_id", "grade_attempt_raw", "reported_at", "resolution"),
            columnsOf(db, "report_wrong"),
            "replica must match the live 7-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            setOf(
                "id", "user_id", "kc_id", "card_id", "grade_attempt_raw",
                "reported_at", "resolution", "resolved_by", "resolved_at",
            ),
            columnsOf(db, "report_wrong").toSet(),
        )

        // Both NULLable (PRAGMA notnull == 0): additive, no backfill needed on the empty live
        // table; a pre-existing OPEN row would read NULL = not-yet-resolved (Phase1Tables KDoc).
        transaction(db) {
            exec("PRAGMA table_info(report_wrong)") { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    if (name == "resolved_by" || name == "resolved_at") {
                        assertEquals(0, rs.getInt("notnull"), "$name must be nullable")
                    }
                }
            }
        }
    }
}
