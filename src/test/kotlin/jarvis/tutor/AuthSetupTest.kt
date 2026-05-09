package jarvis.tutor

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthSetupTest {
    @Test
    fun `GET auth setup with valid token sets session and csrf cookies`() = testApplication {
        val dbDir = Files.createTempDirectory("authtest")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, TokensTable, SessionsTable)
        }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val rawToken = TokenRepo(db).issue(u, "test")
        application {
            attributes.put(TutorContextKey, TutorContext(db, dbDir))
            installTutorRoutes()
        }
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        val r = client.get("/auth/setup?t=$rawToken")
        assertEquals(HttpStatusCode.Found, r.status)
        val setCookies = r.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        assertTrue(setCookies.any { it.startsWith("jarvis_session=") && it.contains("HttpOnly") },
            "expected session cookie with HttpOnly, got: $setCookies")
        assertTrue(setCookies.any { it.startsWith("csrf=") },
            "expected csrf cookie, got: $setCookies")
    }

    @Test
    fun `GET auth setup with bad token returns 401`() = testApplication {
        val dbDir = Files.createTempDirectory("authtest2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, TokensTable, SessionsTable)
        }
        application {
            attributes.put(TutorContextKey, TutorContext(db, dbDir))
            installTutorRoutes()
        }
        val r = client.get("/auth/setup?t=bogus")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `GET auth setup without token param returns 401`() = testApplication {
        val dbDir = Files.createTempDirectory("authtest3")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, TokensTable, SessionsTable)
        }
        application {
            attributes.put(TutorContextKey, TutorContext(db, dbDir))
            installTutorRoutes()
        }
        val r = client.get("/auth/setup")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
