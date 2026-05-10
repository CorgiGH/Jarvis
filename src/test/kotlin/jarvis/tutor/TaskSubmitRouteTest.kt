package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskSubmitRouteTest {

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    /** Returns (userId, sessionId, taskId). */
    private fun seedUserAndTask(ctx: TutorContext, username: String = "v"): Triple<String, String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, username, UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        val taskId = TutorTypes.ulid()
        val now = Instant.now()
        TaskRepo(ctx.db).insert(Task(
            id = taskId, userId = userId, subject = "PA", title = "T-$username",
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

    /** Seeds a second user (no task). Returns (userId, sessionId). */
    private fun seedUser(ctx: TutorContext, username: String): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, username, UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        return userId to sid
    }

    @Test
    fun `POST tasks-id-submit flips status to SUBMITTED for owner`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, taskId) = seedUserAndTask(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/tasks/$taskId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(taskId, body["taskId"]!!.jsonPrimitive.content)
        assertEquals("SUBMITTED", body["status"]!!.jsonPrimitive.content)
        assertTrue(body["submittedAt"]!!.jsonPrimitive.content.startsWith("20"))
    }

    @Test
    fun `POST tasks-id-submit returns 401 without session`(@TempDir tmp: Path) = testApplication {
        application { installFreshTutor(tmp) }
        startApplication()
        val csrf = "test-csrf-12345"
        val client = createClient { install(HttpCookies) }
        val resp = client.post("/api/v1/tasks/anything/submit") {
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST tasks-id-submit returns 403 for cross-user task`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, _, taskId) = seedUserAndTask(ctx!!, "owner")         // task owned by "owner"
        val (_, sidB) = seedUser(ctx!!, "other")                     // logged in as "other"
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/tasks/$taskId/submit") {
            cookie("jarvis_session", sidB); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST tasks-id-submit returns 404 for missing task`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, _) = seedUserAndTask(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/tasks/nonexistent-ulid/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}
