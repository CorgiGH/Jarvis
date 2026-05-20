package jarvis.web

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthVerifyRouteTest {
    @Test fun `a valid token mints a session, creates the user, and redirects`() = testApplication {
        val dbDir = Files.createTempDirectory("verify")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable, SessionsTable) }
        val raw = MagicLinkRepo(db).issue("new@example.com", "ro", 900)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val c = createClient { install(HttpCookies); followRedirects = false }
        val r = c.get("/auth/verify?token=$raw")
        assertEquals(HttpStatusCode.Found, r.status)
        val cookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertTrue(cookies.any { it.startsWith("jarvis_session=") && it.contains("HttpOnly") })
        assertNotNull(UserRepo(db).findByEmail("new@example.com"))
    }

    @Test fun `an already-consumed token does not mint a session`() = testApplication {
        val dbDir = Files.createTempDirectory("verify2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable, SessionsTable) }
        val raw = MagicLinkRepo(db).issue("x@example.com", "ro", 900)
        MagicLinkRepo(db).consume(raw)             // burn it first
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val c = createClient { install(HttpCookies); followRedirects = false }
        val r = c.get("/auth/verify?token=$raw")
        assertEquals(HttpStatusCode.Found, r.status)
        assertTrue(r.headers[HttpHeaders.Location]?.startsWith("/tutor/login") == true)
        val cookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertTrue(cookies.none { it.startsWith("jarvis_session=") })
    }
}
