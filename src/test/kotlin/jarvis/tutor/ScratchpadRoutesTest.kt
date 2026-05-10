package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScratchpadRoutesTest {
    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSessionAndTask(ctx: TutorContext): Triple<String, String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        val taskId = TutorTypes.ulid()
        val now = Instant.now()
        TaskRepo(ctx.db).insert(Task(
            id = taskId, userId = userId, subject = "PA", title = "T",
            deadline = now.plusSeconds(86400),
            problemRef = ContentRef("user", "p.pdf", "x"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef("user", "p.pdf", "x"),
            scratchpad = null, submission = null, grade = null,
            cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = now, updatedAt = now,
        ))
        return Triple(userId, sid, taskId)
    }

    @Test
    fun `PUT then GET roundtrip persists scratchpad text`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, taskId) = seedSessionAndTask(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r1 = client.put("/api/v1/tasks/$taskId/scratchpad") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"text":"hello world"}""")
        }
        assertEquals(HttpStatusCode.OK, r1.status, r1.bodyAsText())
        val r2 = client.get("/api/v1/tasks/$taskId/scratchpad") {
            cookie("jarvis_session", sid)
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        assertTrue(r2.bodyAsText().contains("\"text\":\"hello world\""))
    }

    @Test
    fun `PUT on other user's task returns 404`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, _, taskIdA) = seedSessionAndTask(ctx!!)
        val userB = TutorTypes.ulid()
        UserRepo(ctx!!.db).insert(User(userB, "vB", UserScope.OWNER, Instant.now(), Instant.now()))
        val sidB = SessionRepo(ctx!!.db).create(userB, ttlSeconds = 3600)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.put("/api/v1/tasks/$taskIdA/scratchpad") {
            cookie("jarvis_session", sidB); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"text":"intrusion"}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }
}
