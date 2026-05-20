package jarvis.web

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AiLiteracyGateTest {
    private fun seed(): Triple<String, String, java.nio.file.Path> {
        val dbDir = Files.createTempDirectory("ailitgate")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable, AiLiteracyConfirmationTable)
        }
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        return Triple(uid, SessionRepo(db).create(uid, 3600), dbDir)
    }

    @Test fun `sidekick ask is blocked 403 when the user has not confirmed literacy`() = testApplication {
        val (_, sid, dbDir) = seed()
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/sidekick/ask") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"question":"hi"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assert(r.bodyAsText().contains("ai-literacy"))
    }

    @Test fun `sidekick ask passes the gate once literacy is confirmed`() = testApplication {
        val (uid, sid, dbDir) = seed()
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        AiLiteracyRepo(db).confirm(uid, AI_LITERACY_VERSION, "ro")
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/api/v1/sidekick/ask") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"question":"hi"}""")
        }
        assertEquals(true, r.status != HttpStatusCode.Forbidden)  // not blocked by the literacy gate
    }
}
