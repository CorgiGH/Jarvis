package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-3 Task 1 — pins the live-DB column delta the lesson-completion migration adds to `attempts`
 * (spec §4.4, audit T3): the 3 additive nullable columns beat_type / prediction / first_encounter.
 *
 * Live `attempts` shape captured READ-ONLY from ~/.jarvis/tutor.db on 2026-06-12 (Task 0 Step 3),
 * 14 columns, 0 rows:
 *   [id, user_id, kc_id, task_id, problem_id, phase, student_confidence, correct, score,
 *    scaffold_level, is_far_transfer, self_explanation, recorded, graded_at]
 * Code (Phase1Tables.kt) additionally defines beat_type / prediction / first_encounter, all nullable.
 *
 * Replica technique (same as LiveShapeColumnsMigrationTest): full-migrate a fresh temp DB, DROP the
 * 3 new columns (sqlite-jdbc bundles SQLite >= 3.35 ⇒ ALTER TABLE … DROP COLUMN) to reproduce the
 * exact live shape, then re-migrate and assert the deltas. The INV-3.1 backup gate self-skips on a
 * fresh DB (0 protected rows) — this test NEVER touches a live DB.
 */
class AttemptsBeatColumnsMigrationTest {

    private val liveShape = listOf(
        "id", "user_id", "kc_id", "task_id", "problem_id", "phase", "student_confidence",
        "correct", "score", "scaffold_level", "is_far_transfer", "self_explanation",
        "recorded", "graded_at",
    )

    private fun liveShapeDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("attempts-shape.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        transaction(db) {
            for (c in listOf("beat_type", "prediction", "first_encounter")) {
                exec("ALTER TABLE attempts DROP COLUMN $c")
            }
        }
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `attempts gains exactly beat_type prediction first_encounter`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp)
        assertEquals(
            liveShape,
            columnsOf(db, "attempts"),
            "replica must match the live 14-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            (liveShape + listOf("beat_type", "prediction", "first_encounter")).toSet(),
            columnsOf(db, "attempts").toSet(),
        )

        // the 14 pre-existing columns are untouched (no rename / drop of an existing column)
        val after = columnsOf(db, "attempts").toSet()
        for (c in liveShape) assertEquals(true, c in after, "pre-existing column '$c' must survive")

        // idempotent (M-IDEMP): a second run is a no-op
        TutorMigration.migrate(db)
        assertEquals(17, columnsOf(db, "attempts").size)
    }

    @Test
    fun `the 3 new columns are all nullable (additive, no backfill on the empty live table)`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp)
        TutorMigration.migrate(db)
        transaction(db) {
            exec("PRAGMA table_info(attempts)") { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    if (name in setOf("beat_type", "prediction", "first_encounter")) {
                        assertEquals(0, rs.getInt("notnull"), "$name must be nullable")
                    }
                }
            }
        }
    }
}
