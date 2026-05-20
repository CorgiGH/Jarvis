package jarvis.web

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiLiteracyConfirmRouteTest {
    @Test fun `confirm records the confirmation for the session user`() = testApplication {
        val dbDir = Files.createTempDirectory("ailitroute")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable, ConsentLogTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val csrf = "test-csrf"
        val r = client.post("/api/v1/me/ai-literacy/confirm") {
            header("Cookie", "jarvis_session=$sid; csrf=$csrf")
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(AiLiteracyRepo(db).hasConfirmedCurrent(uid))
    }

    @Test fun `confirm without a session is 401`() = testApplication {
        val dbDir = Files.createTempDirectory("ailitroute2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/me/ai-literacy/confirm").status)
    }
}
