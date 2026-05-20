package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoSessionGuardTest {
    @Test fun `auto-session no longer auto-logs-in an unauthenticated caller`() = testApplication {
        val dbDir = Files.createTempDirectory("autosess")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, MagicLinkTokensTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.get("/api/v1/tutor/auto-session")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test fun `auto-session refreshes csrf for authenticated caller with no csrf cookie`() = testApplication {
        val dbDir = Files.createTempDirectory("autosess2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, MagicLinkTokensTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "tester", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        // Send request with a valid session but NO csrf cookie.
        val r = client.get("/api/v1/tutor/auto-session") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // The JSON csrf field must NOT be the literal string "null" and must be non-empty.
        assertTrue(body.contains("\"csrf\":"), "response JSON should contain a csrf field")
        assertFalse(body.contains("\"csrf\":\"null\""), "csrf field must not be the string \"null\"")
        assertFalse(body.contains("\"csrf\":\"\""), "csrf field must not be empty")
        // A Set-Cookie header for csrf= must be present.
        val setCookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertNotNull(setCookies.firstOrNull { it.startsWith("csrf=") }, "Set-Cookie csrf= header must be present")
    }
}
