package jarvis.web

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.AiLiteracyConfirmationTable
import jarvis.tutor.AttemptsTable
import jarvis.tutor.CardActionLogTable
import jarvis.tutor.ConsentLogTable
import jarvis.tutor.EffectorAttemptsTable
import jarvis.tutor.ExamDatesTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.KnowledgeGapsTable
import jarvis.tutor.MockExamsTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SensorEventsTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionSummariesTable
import jarvis.tutor.SessionsTable
import jarvis.tutor.taskdetect.DetectedTaskMappingTable
import jarvis.tutor.TasksTable
import jarvis.tutor.TrustGrantsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserPreferencesTable
import jarvis.tutor.TokensTable
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeRoutesTest {
    @Test fun `export returns the caller's own account data as JSON`() = testApplication {
        val dbDir = Files.createTempDirectory("meexport")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, ConsentLogTable, UserPreferencesTable, AiLiteracyConfirmationTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now(), email = "e@x.io"))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.get("/api/v1/me/export") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("e@x.io"))
        assertTrue(r.headers["Content-Disposition"]?.contains("attachment") == true)
    }

    @Test fun `delete removes the user and revokes the session`() = testApplication {
        val dbDir = Files.createTempDirectory("medelete")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, ConsentLogTable, UserPreferencesTable, AiLiteracyConfirmationTable, TrustGrantsTable, SensorEventsTable, EffectorAttemptsTable, CardActionLogTable, DetectedTaskMappingTable, TasksTable, FsrsCardsTable, KnowledgeGapsTable, TokensTable, SessionSummariesTable, AttemptsTable, ReportWrongTable, ExamDatesTable, MockExamsTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(null, UserRepo(db).findById(uid))
        assertEquals(null, SessionRepo(db).findUserId(sid))
    }

    @Test fun `restrict toggles logging-paused on then off`() = testApplication {
        val dbDir = Files.createTempDirectory("merestrict")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, UserPreferencesTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val first = client.post("/api/v1/me/restrict") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains(""""loggingPaused":true"""))
        val second = client.post("/api/v1/me/restrict") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertTrue(second.bodyAsText().contains(""""loggingPaused":false"""))
    }
}
