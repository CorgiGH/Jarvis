package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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

class TaskPrepRouteTest {

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    /** Seeds a user + session + task. Returns Triple(userId, sessionId, taskId). */
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

    /** Inserts a task_prep row directly via TaskPrepRepo. */
    private fun seedTaskPrep(
        ctx: TutorContext,
        taskId: String,
        problemsJson: String = """[{"problem_id":"A1","page":4,"statement":"derive MLE"}]""",
        drillsJson: String = """{"A1":{"drill":"derive μ̂","definition":"...","worked":"...","check":"...","expectedAnswerHint":"median"}}""",
        railJson: String = """[{"type":"PDF","label":"Tema_A.pdf","action":"OPEN_DRAWER","payload":{"path":"_extras/PS/ps_hw/Tema_A.pdf"}}]""",
    ) {
        TaskPrepRepo(ctx.db).upsert(TaskPrep(
            taskId = taskId,
            generatedAt = Instant.now(),
            version = 1,
            problemsJson = problemsJson,
            drillsJson = drillsJson,
            railJson = railJson,
        ))
    }

    @Test
    fun `GET tasks-id-prep returns 404 when no prep cached`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, taskId) = seedUserAndTask(ctx!!)
        val client = createClient { install(HttpCookies) }
        val resp = client.get("/api/v1/tasks/$taskId/prep") {
            cookie("jarvis_session", sid)
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `GET tasks-id-prep returns 200 with shape when prep cached`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, taskId) = seedUserAndTask(ctx!!)
        seedTaskPrep(ctx!!, taskId)
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.get("/api/v1/tasks/$taskId/prep") {
            cookie("jarvis_session", sid)
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(taskId, body["taskId"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("problemsJson"))
        assertTrue(body.containsKey("drillsJson"))
        assertTrue(body.containsKey("railJson"))
        assertTrue(body.containsKey("generatedAt"))
    }

    @Test
    fun `GET tasks-id-prep returns 401 without session`(@TempDir tmp: Path) = testApplication {
        application { installFreshTutor(tmp) }
        startApplication()
        val client = createClient { install(HttpCookies) }
        val resp = client.get("/api/v1/tasks/anything/prep")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
