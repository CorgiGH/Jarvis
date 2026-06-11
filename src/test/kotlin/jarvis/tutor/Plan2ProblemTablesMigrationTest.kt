package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-2 Task 5 — pins the EXACT shapes of the three problem-bank tables (spec §3.4) the
 * additive migration creates, their idempotence, and that NO existing table shifts. Mirrors
 * LiveShapeColumnsMigrationTest's fresh-DB technique: a fresh temp SQLite DB has 0 fsrs_cards,
 * so the INV-3.1 backup gate self-skips (nothing to protect) and migrate() runs unguarded.
 */
class Plan2ProblemTablesMigrationTest {

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("plan2-problems.db").toString())
        TutorMigration.migrate(db) // 0 protected rows -> gate self-skips
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `problems table has the exact spec-3-4 column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "archetype", "statement_json", "parameter_slots_json",
                "solution_present", "solution_json", "exam_language",
                "exam_language_constraints_json", "data_files_json", "source_doc", "source_page",
                "provenance", "synthetic_tag", "created_at", "updated_at",
            ),
            columnsOf(db, "problems").toSet(),
        )
    }

    @Test
    fun `problem_rubric_items table has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "problem_id", "label", "points", "kind",
                "all_or_nothing", "penalty_rules_json", "position",
            ),
            columnsOf(db, "problem_rubric_items").toSet(),
        )
    }

    @Test
    fun `problem_kc_links table is the bare many-to-many join`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf("problem_id", "kc_id"),
            columnsOf(db, "problem_kc_links").toSet(),
        )
    }

    @Test
    fun `migration is idempotent for the new problem tables`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val before = listOf("problems", "problem_rubric_items", "problem_kc_links")
            .associateWith { columnsOf(db, it).size }
        TutorMigration.migrate(db) // second run = no-op
        val after = listOf("problems", "problem_rubric_items", "problem_kc_links")
            .associateWith { columnsOf(db, it).size }
        assertEquals(before, after)
    }

    @Test
    fun `existing tables keep their frozen column counts`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // kc_verification_status: 7 cols (Phase1Tables.kt KcVerificationStatusTable, post Plan-1)
        assertEquals(7, columnsOf(db, "kc_verification_status").size, columnsOf(db, "kc_verification_status").toString())
        // exam_dates: 5 cols (Phase1Tables.kt:104 — id, user_id, subject, start_at, created_at)
        assertEquals(5, columnsOf(db, "exam_dates").size, columnsOf(db, "exam_dates").toString())
        // fsrs_cards: 15 cols (FROZEN live shape — must never shift under an additive plan)
        assertEquals(15, columnsOf(db, "fsrs_cards").size, columnsOf(db, "fsrs_cards").toString())
    }
}
