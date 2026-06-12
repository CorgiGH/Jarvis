package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-2 Task 6 — pins the exact shapes of the course-meta tables (spec §3.5): grading_models,
 * grade_components, glossary_terms, exam_schedule_rows. Same fresh-DB / 0-card / gate-self-skip
 * technique as Plan2ProblemTablesMigrationTest.
 */
class Plan2MetaTablesMigrationTest {

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("plan2-meta.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `grading_models has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "variant", "is_primary", "formula", "max_total",
                "pass_rule_json", "curve_json", "evidence_tier", "source_url",
                "notes_json", "sweep_ref", "created_at",
            ),
            columnsOf(db, "grading_models").toSet(),
        )
    }

    @Test
    fun `grade_components has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "model_id", "name", "max_points", "weight", "min_gate_json",
                "reexam_policy", "evidence_tier", "source_url", "detail_json", "position",
            ),
            columnsOf(db, "grade_components").toSet(),
        )
    }

    @Test
    fun `glossary_terms has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf("id", "subject", "term_en", "term_ro", "source_doc", "created_at"),
            columnsOf(db, "glossary_terms").toSet(),
        )
    }

    @Test
    fun `exam_schedule_rows has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "raw_discipline", "exam_type", "start_at", "end_at",
                "room", "date_precision", "source_ref", "created_at",
            ),
            columnsOf(db, "exam_schedule_rows").toSet(),
        )
    }

    @Test
    fun `migration is idempotent for the new meta tables`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val tables = listOf("grading_models", "grade_components", "glossary_terms", "exam_schedule_rows")
        val before = tables.associateWith { columnsOf(db, it).size }
        TutorMigration.migrate(db) // second run = no-op
        assertEquals(before, tables.associateWith { columnsOf(db, it).size })
    }

    @Test
    fun `exam_dates stays the frozen one-row-per-subject 5-column shape`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // Lock §R: exam_dates is NOT widened by Plan-2; the 12 schedule rows live in
        // exam_schedule_rows instead (core §0.8 #1).
        assertEquals(
            setOf("id", "user_id", "subject", "start_at", "created_at"),
            columnsOf(db, "exam_dates").toSet(),
        )
    }
}
