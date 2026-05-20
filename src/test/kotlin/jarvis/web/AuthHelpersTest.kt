package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthHelpersTest {
    @Test fun `requireUser rejects a missing session with 401`() = testApplication {
        val dbDir = Files.createTempDirectory("authhelp")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            routing { get("/probe") { requireUser { uid -> call.respondText(uid) } } }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/probe").status)
    }

    @Test fun `requireUser passes the user id through for a valid session`() = testApplication {
        val dbDir = Files.createTempDirectory("authhelp2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            routing { get("/probe") { requireUser { u -> call.respondText(u) } } }
        }
        val r = client.get("/probe") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(uid, r.bodyAsText())
    }
}
