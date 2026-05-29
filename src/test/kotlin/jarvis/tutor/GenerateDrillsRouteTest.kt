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
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import jarvis.web.drillCriticLlmFactory
import jarvis.web.drillGeneratorLlmFactory
import jarvis.web.drillKcLookup
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateDrillsRouteTest {
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("generate-drills-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    @AfterEach fun reset() {
        drillGeneratorLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCriticLlmFactory = { jarvis.RelayLlm() }
        drillKcLookup = { _, _ -> null }
    }

    /** Local helper — mirrors PrepAuthoredRouteTest / DrillGradeServerSideTest app setup. */
    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
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

    @Test fun `generate-drills persists a gradable Problem + a renderable DrillContent and preserves existing`(
        @org.junit.jupiter.api.io.TempDir tmp: Path
    ) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-1")

        // pre-existing authored drill must survive the merge
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep("task-1", Instant.now(), 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem("authored-1", 1, "old", kcIds = listOf("pa-kc-001")))),
            drillsJson = "{}", railJson = "[]"))

        drillKcLookup = { _, _ -> KnowledgeConcept("pa-kc-001", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1, viz_id = "recursion-tree") }
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) goodDrill else "42") to "g"
        }
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = { object : Llm { override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = goodCritic to "claude" } }

        val csrf = "test-csrf-generate-drills"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-1/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-001","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-1")!!
        val problems = Json.decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        assertTrue(problems.any { it.problemId == "authored-1" }, "existing authored problem must survive")
        val gen1 = problems.first { it.problemId == "gen-pa-kc-001-0" }
        assertEquals(listOf("pa-kc-001"), gen1.kcIds)
        assertTrue(prep.drillsJson.contains("gen-pa-kc-001-0") && prep.drillsJson.contains("recursion-tree"),
            "drillsJson must contain generated problem id and viz id; got: ${prep.drillsJson}")
    }
}
