package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import jarvis.tutor.GraderProvider
import jarvis.tutor.GraderProviderSettingRepo
import jarvis.tutor.GraderProviderSettingTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import jarvis.tutor.SessionsTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Route tests for GET + PUT /api/v1/me/grader-provider.
 *
 * Test matrix:
 *  - GET returns "free" when no row exists (default)
 *  - GET returns set value after PUT
 *  - PUT sets a valid provider and echoes it back
 *  - PUT rejects an unknown provider with 400
 *  - GET with no session → 401
 *  - PUT with no session → 401
 *  - PUT with missing CSRF → 403
 */
class GraderProviderRoutesTest {

    private fun makeDb() = run {
        val dbDir = Files.createTempDirectory("grader-provider-test")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable, GraderProviderSettingTable)
        }
        Triple(db, dbDir, dbDir)
    }

    private fun makeUser(db: org.jetbrains.exposed.sql.Database): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    @Test
    fun `GET returns free as default when no row exists`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (_, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        val r = client.get("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains(""""provider":"free""""), "body: ${r.bodyAsText()}")
    }

    @Test
    fun `PUT sets provider and GET reflects new value`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (_, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        val put = client.put("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"claude"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        assertTrue(put.bodyAsText().contains(""""provider":"claude""""), "put body: ${put.bodyAsText()}")

        val get = client.get("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid")
        }
        assertEquals(HttpStatusCode.OK, get.status)
        assertTrue(get.bodyAsText().contains(""""provider":"claude""""), "get body: ${get.bodyAsText()}")
    }

    @Test
    fun `PUT accepts all valid provider values`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (_, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        for (provider in GraderProvider.entries) {
            val r = client.put("/api/v1/me/grader-provider") {
                header("Cookie", "jarvis_session=$sid; csrf=tok")
                header("X-CSRF-Token", "tok")
                contentType(ContentType.Application.Json)
                setBody("""{"provider":"${provider.name}"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status, "provider=${provider.name}: ${r.bodyAsText()}")
        }
    }

    @Test
    fun `PUT with unknown provider returns 400`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (_, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        val r = client.put("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"anthropic"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("anthropic"), "body: ${r.bodyAsText()}")
    }

    @Test
    fun `GET with no session returns 401`() = testApplication {
        val (db, dbDir, _) = makeDb()
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        val r = client.get("/api/v1/me/grader-provider")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `PUT with no session returns 401`() = testApplication {
        val (db, dbDir, _) = makeDb()
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        val r = client.put("/api/v1/me/grader-provider") {
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"free"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `PUT with missing CSRF returns 403`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (_, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        // Session cookie present, but no CSRF cookie/header.
        val r = client.put("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid")
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"free"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `PUT persists to DB and repo reflects correct enum value`() = testApplication {
        val (db, dbDir, _) = makeDb()
        val (uid, sid) = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installGraderProviderRoutes()
        }
        client.put("/api/v1/me/grader-provider") {
            header("Cookie", "jarvis_session=$sid; csrf=x")
            header("X-CSRF-Token", "x")
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"freellmapi"}""")
        }
        val stored = GraderProviderSettingRepo(db).get(uid)
        assertEquals(GraderProvider.freellmapi, stored)
    }
}
