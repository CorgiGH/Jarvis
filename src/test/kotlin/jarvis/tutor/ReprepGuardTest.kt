package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import jarvis.web.reprepExtractorFn
import jarvis.web.reprepExtractorLlmFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E3 Task 12: /reprep preserves existing Problems with non-empty kcIds + existing drillsJson.
 *
 * The test overrides reprepExtractorFn (internal seam in TutorRoutes.kt) to avoid any
 * real PDF read or network call. The extractor seam returns a predetermined list of
 * fresh empty-kcIds problems. The test verifies that the authored kcId-bearing problem
 * survives the merge and that drillsJson is not reset to "{}".
 */
class ReprepGuardTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("reprep-guard-evtlog")
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
            id = taskId, userId = userId, subject = "PA", title = "T-reprep",
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

    @AfterEach fun reset() {
        reprepExtractorFn = { pdfPath, llm -> jarvis.tutor.PdfProblemExtractor.identifyProblems(pdfPath, llm) }
        reprepExtractorLlmFactory = { jarvis.OpenRouterChatLlm() }
    }

    @Test fun `reprep preserves kcId-bearing problems and existing drillsJson`(
        @TempDir tmp: Path
    ) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-1")

        // Seed: an authored, kcId-bearing problem + non-empty drillsJson that must survive /reprep.
        val authoredProblem = Problem("authored-1", 1, "What is O(n)?", kcIds = listOf("pa-kc-001"))
        val existingDrillsJson = """{"authored-1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}"""
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()), listOf(authoredProblem)),
            drillsJson = existingDrillsJson,
            railJson = "[]",
        ))

        // Override the extractor seam: returns one fresh empty-kcIds problem (simulates LLM extraction).
        // No PDF read or network call occurs.
        val freshProblems = listOf(Problem("fresh-1", 1, "Compute fib(5).", kcIds = emptyList()))
        reprepExtractorFn = { _, _ -> freshProblems }
        reprepExtractorLlmFactory = { object : jarvis.Llm {
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int, responseFormat: String?) =
                "[]" to "fake"
            override fun close() {}
        } }

        val csrf = "test-csrf-reprep-guard"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-1/reprep") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-1")
        assertNotNull(prep, "TaskPrep must exist after reprep")

        // The authored kcId-bearing problem must still be present.
        val problems = Json { ignoreUnknownKeys = true }.decodeFromString(
            ListSerializer(Problem.serializer()), prep.problemsJson
        )
        val preserved = problems.find { it.problemId == "authored-1" }
        assertNotNull(preserved, "authored-1 with kcIds must survive /reprep merge")
        assertEquals(listOf("pa-kc-001"), preserved.kcIds, "kcIds must be unchanged")

        // The fresh extracted problem must also be present.
        assertTrue(problems.any { it.problemId == "fresh-1" }, "fresh extracted problem must be present")

        // The drillsJson must not be reset to "{}".
        assertTrue(prep.drillsJson.contains("authored-1"),
            "drillsJson must preserve existing drills; got: ${prep.drillsJson}")
    }

    @Test fun `reprep with no prior prep still works (no existing to preserve)`(
        @TempDir tmp: Path
    ) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-2")

        val freshProblems = listOf(Problem("fresh-a", 1, "State the sorting theorem.", kcIds = emptyList()))
        reprepExtractorFn = { _, _ -> freshProblems }
        reprepExtractorLlmFactory = { object : jarvis.Llm {
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int, responseFormat: String?) =
                "[]" to "fake"
            override fun close() {}
        } }

        val csrf = "test-csrf-reprep-no-prior"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-2/reprep") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-2")
        assertNotNull(prep)
        val problems = Json { ignoreUnknownKeys = true }.decodeFromString(
            ListSerializer(Problem.serializer()), prep.problemsJson
        )
        assertTrue(problems.any { it.problemId == "fresh-a" }, "fresh problem must be in problemsJson")
        assertEquals("{}", prep.drillsJson, "drillsJson defaults to '{}' when no prior exists")
    }
}
