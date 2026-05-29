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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * E2 Task 4b: POST /api/v1/task/{id}/prep-authored persists authored,
 * kcId-bearing Problems so the curate-tutor flow can populate the grade loop
 * in production (without an LLM reprep round-trip).
 */
class PrepAuthoredRouteTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("prep-authored-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        return userId to sid
    }

    private fun seedTask(ctx: TutorContext, userId: String, taskId: String) {
        val now = Instant.now()
        TaskRepo(ctx.db).insert(Task(
            id = taskId, userId = userId, subject = "PA", title = "T-authored",
            deadline = now.plusSeconds(86400),
            problemRef = ContentRef("user", "p.pdf", "x"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef("user", "p.pdf", "x"),
            scratchpad = null, submission = null, grade = null,
            cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = now, updatedAt = now,
        ))
    }

    @Test
    fun `POST prep-authored persists problems with kcIds`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-1")

        val csrf = "test-csrf-prep-authored"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val body = """{"problems":[{"problemId":"d1","page":1,"statement":"6*7?","kcIds":["pa-kc-002"],"canonicalAnswer":"42"}]}"""

        val resp = client.post("/api/v1/task/task-1/prep-authored") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-1")
        assertNotNull(prep, "TaskPrep row must exist after POST")
        val problems = Json { ignoreUnknownKeys = true }.decodeFromString(
            ListSerializer(Problem.serializer()), prep.problemsJson
        )
        val d1 = problems.find { it.problemId == "d1" }
        assertNotNull(d1, "problem d1 must be persisted")
        assertEquals(listOf("pa-kc-002"), d1.kcIds, "kcIds must be persisted verbatim")
    }

    @Test
    fun `POST prep-authored returns 404 for task not owned by caller`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        // seed caller (no task seeded for this user)
        val (_, sid) = seedSession(ctx!!)
        // seed a different user who owns "task-other"
        val (ownerId, _) = seedSession(ctx!!)
        seedTask(ctx!!, ownerId, "task-other")

        val csrf = "test-csrf-404"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-other/prep-authored") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"problems":[]}""")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}
