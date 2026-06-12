package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 7 — seedKnowledgeMeta idempotency + INV-3.4 shape (spec §3.5).
 * A migrated temp DB with exactly one user; seed twice; assert identical counts + the INV-3.4
 * invariants (every grading_models row has a non-empty source_url; 12 exam_schedule_rows; every
 * schedule row sourceRef == the sweep anchor; at most one primary per subject; row esr-10's
 * subject is the raw 'Reverse Engineering', NEVER SORC).
 */
class Plan2SeedTest {

    private fun migratedDbWithUser(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("seed.db").toString())
        TutorMigration.migrate(db)
        transaction(db) {
            // UsersTable required non-null cols (Users.kt:25-33): id, name, scope, created_at,
            // last_seen_at; email is nullable, lang defaults 'ro'. Set every required column.
            UsersTable.insert {
                it[id] = "01HSEEDUSER000000000000000" // 26 chars — UsersTable.id is varchar(26)
                it[name] = "Alex"
                it[scope] = "OWNER"
                it[createdAt] = Instant.now()
                it[lastSeenAt] = Instant.now()
                it[email] = "alex@example.com"
            }
        }
        return db
    }

    private fun count(db: Database, table: org.jetbrains.exposed.sql.Table): Long =
        transaction(db) { table.selectAll().count() }

    @Test
    fun `seeding twice yields identical row counts (idempotent upsert-by-id)`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        val now = Instant.parse("2026-06-11T10:00:00Z")

        val first = Plan2Seed.seedKnowledgeMeta(db, now)
        val countsAfterFirst = listOf(
            GradingModelsTable, GradeComponentsTable, ExamScheduleRowsTable, ExamDatesTable,
        ).associateWith { count(db, it) }

        val second = Plan2Seed.seedKnowledgeMeta(db, now)
        val countsAfterSecond = listOf(
            GradingModelsTable, GradeComponentsTable, ExamScheduleRowsTable, ExamDatesTable,
        ).associateWith { count(db, it) }

        assertEquals(countsAfterFirst, countsAfterSecond, "re-seed must not duplicate rows")
        assertEquals(7L, countsAfterFirst[GradingModelsTable])
        assertEquals(12L, countsAfterFirst[ExamScheduleRowsTable])
        assertEquals(4L, countsAfterFirst[ExamDatesTable])
        // first run inserts everything; second inserts nothing (all updated)
        assertEquals(0, second.inserted, "second run must insert 0")
        assertTrue(first.inserted > 0, "first run must insert > 0")
    }

    @Test
    fun `INV-3-4 every grading_models row has a non-empty source_url`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            GradingModelsTable.selectAll().forEach {
                assertTrue(
                    it[GradingModelsTable.sourceUrl].isNotBlank(),
                    "grading_models ${it[GradingModelsTable.id]} has a blank source_url",
                )
            }
        }
    }

    @Test
    fun `INV-3-4 exactly 12 schedule rows all tracing the sweep anchor`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        val anchor = "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"
        transaction(db) {
            val rows = ExamScheduleRowsTable.selectAll().toList()
            assertEquals(12, rows.size)
            rows.forEach { assertEquals(anchor, it[ExamScheduleRowsTable.sourceRef]) }
        }
    }

    @Test
    fun `at most one primary grading model per subject`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            GradingModelsTable.selectAll()
                .filter { it[GradingModelsTable.isPrimary] }
                .groupBy { it[GradingModelsTable.subject] }
                .forEach { (subject, rows) ->
                    assertEquals(1, rows.size, "$subject has ${rows.size} primary models")
                }
        }
    }

    @Test
    fun `Romanian diacritics round-trip byte-identical through SQLite`(@TempDir tmp: Path) {
        // A JDBC/encoding regression would corrupt user-facing RO content while every
        // count-based test stays green — assert exact equality including ă/ț/ș/î.
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val aloFormula = GradingModelsTable.selectAll()
                .first { it[GradingModelsTable.id] == "gm-alo" }[GradingModelsTable.formula]
            assertEquals(
                "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris",
                aloFormula,
            )
            val esr01 = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-01" }
            assertEquals("Algebră liniară și optimizare", esr01[ExamScheduleRowsTable.rawDiscipline])
            val esr04 = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-04" }
            assertEquals("Educație fizică", esr04[ExamScheduleRowsTable.rawDiscipline])
        }
    }

    @Test
    fun `row esr-10 subject is the raw Reverse Engineering, never SORC (G13)`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val row = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-10" }
            assertEquals("Reverse Engineering", row[ExamScheduleRowsTable.subject])
        }
    }

    @Test
    fun `exam_dates primaries are the 4 stored-UTC subject dates`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val bySubject = ExamDatesTable.selectAll()
                .associate { it[ExamDatesTable.subject] to it[ExamDatesTable.startAt] }
            assertEquals(setOf("ALO", "SORC", "POO", "PA"), bySubject.keys)
            assertEquals(Instant.parse("2026-06-03T13:00:00Z"), bySubject["ALO"])
            assertEquals(Instant.parse("2026-06-06T08:00:00Z"), bySubject["SORC"])
            assertEquals(Instant.parse("2026-06-09T05:00:00Z"), bySubject["POO"])
            assertEquals(Instant.parse("2026-06-10T09:00:00Z"), bySubject["PA"])
        }
    }
}
