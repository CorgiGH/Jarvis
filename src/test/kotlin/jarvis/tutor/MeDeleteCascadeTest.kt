package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.AttemptsTable
import jarvis.tutor.ExamDatesTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionSummariesTable
import jarvis.web.FakeMailer
import jarvis.web.installTutorRoutes
import jarvis.web.testTutorContext
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Task 13 (B6) — me/delete cascade must include the 4 new user-scoped Phase-1 tables:
 * session_summaries, attempts, report_wrong, exam_dates.
 *
 * Runs under PRAGMA foreign_keys=ON (TutorDb.connect enables it) and asserts zero
 * leftover rows in every new table after erasure, with no FK exception thrown.
 */
class MeDeleteCascadeTest {

    @Test
    fun `delete removes rows in all new phase-1 tables and throws no FK error`() = testApplication {
        val dbDir = Files.createTempDirectory("me-delete-cascade")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())

        // Create all tables the route touches, plus the 4 new ones.
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable,
                ConsentLogTable, UserPreferencesTable, AiLiteracyConfirmationTable,
                TrustGrantsTable, SensorEventsTable, EffectorAttemptsTable,
                CardActionLogTable,
                jarvis.tutor.taskdetect.DetectedTaskMappingTable,
                TasksTable, FsrsCardsTable, KnowledgeGapsTable, TokensTable,
                // Phase-1 new user-scoped tables
                SessionSummariesTable, AttemptsTable, ReportWrongTable, ExamDatesTable,
            )
        }

        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "alice", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)

        // Seed ≥1 row in each of the four new tables for this user.
        transaction(db) {
            SessionSummariesTable.insert {
                it[id] = TutorTypes.ulid()
                it[userId] = uid
                it[startedAt] = Instant.now()
                it[closedAt] = Instant.now()
                it[cardsReviewed] = 5
                it[masteryDeltaJson] = "{}"
                it[narrative] = null
            }

            AttemptsTable.insert {
                it[id] = TutorTypes.ulid()
                it[userId] = uid
                it[kcId] = "pa-kc-001"
                it[taskId] = TutorTypes.ulid()
                it[problemId] = "A1"
                it[phase] = "practice"
                it[studentConfidence] = null
                it[correct] = true
                it[score] = 1.0
                it[scaffoldLevel] = 0
                it[isFarTransfer] = false
                it[selfExplanation] = null
                it[recorded] = true
                it[gradedAt] = Instant.now()
            }

            ReportWrongTable.insert {
                it[id] = TutorTypes.ulid()
                it[userId] = uid
                it[kcId] = "pa-kc-001"
                it[cardId] = null
                it[gradeAttemptRaw] = """{"score":0.5}"""
                it[reportedAt] = Instant.now()
                it[resolution] = null
            }

            ExamDatesTable.insert {
                it[id] = TutorTypes.ulid()
                it[userId] = uid
                it[subject] = "PA"
                it[startAt] = Instant.now().plusSeconds(3600)
                it[createdAt] = Instant.now()
            }
        }

        // Verify rows exist before deletion.
        transaction(db) {
            assertEquals(1, SessionSummariesTable.selectAll().where { SessionSummariesTable.userId eq uid }.count().toInt())
            assertEquals(1, AttemptsTable.selectAll().where { AttemptsTable.userId eq uid }.count().toInt())
            assertEquals(1, ReportWrongTable.selectAll().where { ReportWrongTable.userId eq uid }.count().toInt())
            assertEquals(1, ExamDatesTable.selectAll().where { ExamDatesTable.userId eq uid }.count().toInt())
        }

        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        // Execute me/delete (no FK exception should be thrown).
        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)

        // Assert the user row itself is gone.
        assertNull(UserRepo(db).findById(uid))

        // Assert zero leftover rows in each new table for that user.
        transaction(db) {
            assertEquals(
                0,
                SessionSummariesTable.selectAll().where { SessionSummariesTable.userId eq uid }.count().toInt(),
                "session_summaries must be empty after me/delete"
            )
            assertEquals(
                0,
                AttemptsTable.selectAll().where { AttemptsTable.userId eq uid }.count().toInt(),
                "attempts must be empty after me/delete"
            )
            assertEquals(
                0,
                ReportWrongTable.selectAll().where { ReportWrongTable.userId eq uid }.count().toInt(),
                "report_wrong must be empty after me/delete"
            )
            assertEquals(
                0,
                ExamDatesTable.selectAll().where { ExamDatesTable.userId eq uid }.count().toInt(),
                "exam_dates must be empty after me/delete"
            )
        }
    }

    @Test
    fun `delete with no rows in new tables still succeeds`() = testApplication {
        // Regression guard: the cascade is safe even if the user has no rows in the new tables.
        val dbDir = Files.createTempDirectory("me-delete-empty-new")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())

        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable,
                ConsentLogTable, UserPreferencesTable, AiLiteracyConfirmationTable,
                TrustGrantsTable, SensorEventsTable, EffectorAttemptsTable,
                CardActionLogTable,
                jarvis.tutor.taskdetect.DetectedTaskMappingTable,
                TasksTable, FsrsCardsTable, KnowledgeGapsTable, TokensTable,
                SessionSummariesTable, AttemptsTable, ReportWrongTable, ExamDatesTable,
            )
        }

        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "bob", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)

        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertNull(UserRepo(db).findById(uid))
    }
}
