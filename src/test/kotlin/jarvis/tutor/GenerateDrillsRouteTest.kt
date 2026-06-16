package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?) = (if (n++ == 0) goodDrill else "42") to "g"
        }
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = { object : Llm { override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?) = goodCritic to "claude" } }

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
        val drills = Json { ignoreUnknownKeys = true }.decodeFromString(
            MapSerializer(serializer<String>(), DrillContentDto.serializer()),
            prep.drillsJson,
        )
        assertEquals("recursion-tree", drills["gen-pa-kc-001-0"]!!.vizId,
            "drillsJson must map gen-pa-kc-001-0 to vizId=recursion-tree; got: ${prep.drillsJson}")
    }

    // ── TASK P3-GHOST-FIELDS(b) — far-transfer WIRED into the production /generate-drills path ──
    // Class-killer (H16): a KC WITH an authored far_transfer_stem, driven through the PRODUCTION
    // /generate-drills route, yields a PERSISTED far-transfer Problem (shape=="far-transfer") that
    // reaches problems_json AND is returned by the serve route GET /tasks/{id}/prep. A KC with NO
    // stem produces no far-transfer Problem (no ghost, no throw). This closes the half-ghost: the
    // orphan DrillGenerator.farTransfer branch is now reachable + served from production.

    /** Generator that is content-aware: drill-shaped prompts → [goodDrill]; the computational
     *  self-solve prompt ("Solve and reply") → "42". One instance serves BOTH generate() and
     *  farTransfer() calls the route makes for one KC. */
    private fun contentAwareGen() = object : Llm {
        override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?): Pair<String, String> {
            val isSelfSolve = m.any { it.content.contains("Solve and reply") }
            return (if (isSelfSolve) "42" else goodDrill) to "g"
        }
    }

    @Test fun `generate-drills wires far-transfer — an authored far_transfer_stem yields a persisted+served far-transfer Problem`(
        @org.junit.jupiter.api.io.TempDir tmp: Path
    ) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-ft")

        // KC authors a far_transfer_stem ⇒ the route must ALSO emit a far-transfer drill.
        drillKcLookup = { _, _ -> KnowledgeConcept(
            "pa-kc-ft", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1,
            viz_id = "recursion-tree",
            far_transfer_stem = "A bakery doubles its recipe each hour; model the growth.",
        ) }
        drillGeneratorLlmFactory = { contentAwareGen() }
        drillCriticLlmFactory = { object : Llm { override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?) = goodCritic to "claude" } }

        val csrf = "test-csrf-ft"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-ft/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-ft","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        // (1) persisted to problems_json (read directly from the prep store).
        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-ft")!!
        val problems = Json.decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        val ft = problems.firstOrNull { it.shape == "far-transfer" }
        assertTrue(ft != null, "a KC with an authored far_transfer_stem must persist a far-transfer Problem; got: ${prep.problemsJson}")
        assertEquals("ft-pa-kc-ft", ft!!.problemId)
        assertEquals(listOf("pa-kc-ft"), ft.kcIds)
        // P3-GEN invariant: modelTag = the relay-returned generator id, never the criticUsed literal.
        assertEquals("g", ft.modelTag, "far-transfer Problem.modelTag must be the generator's returned model id")
        // the regular computational drill still rides alongside (additive, not replaced).
        assertTrue(problems.any { it.shape == "computational" }, "the regular drill must still be persisted alongside: ${prep.problemsJson}")

        // (2) returned by the SERVE route GET /tasks/{id}/prep — surfaced to DrillStack like any persisted Problem.
        val served = client.get("/api/v1/tasks/task-ft/prep") {
            cookie("jarvis_session", sid)
        }
        assertEquals(HttpStatusCode.OK, served.status, served.bodyAsText())
        val servedReply = Json { ignoreUnknownKeys = true }
            .decodeFromString(ApiTaskPrepView.serializer(), served.bodyAsText())
        val servedProblems = Json.decodeFromString(ListSerializer(Problem.serializer()), servedReply.problemsJson)
        assertTrue(servedProblems.any { it.shape == "far-transfer" && it.problemId == "ft-pa-kc-ft" },
            "the far-transfer Problem must be SERVED by GET /tasks/{id}/prep: ${servedReply.problemsJson}")
    }

    @Test fun `generate-drills does NOT persist a far-transfer Problem for a KC with no far_transfer_stem (no ghost, no throw)`(
        @org.junit.jupiter.api.io.TempDir tmp: Path
    ) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-noft")

        // KC has NO far_transfer_stem ⇒ zero far-transfer Problems, no throw.
        drillKcLookup = { _, _ -> KnowledgeConcept("pa-kc-noft", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1, viz_id = "recursion-tree") }
        drillGeneratorLlmFactory = { contentAwareGen() }
        drillCriticLlmFactory = { object : Llm { override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?) = goodCritic to "claude" } }

        val csrf = "test-csrf-noft"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/task/task-noft/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-noft","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())

        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-noft")!!
        val problems = Json.decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        assertTrue(problems.none { it.shape == "far-transfer" },
            "a KC with no far_transfer_stem must NOT persist a far-transfer Problem: ${prep.problemsJson}")
        // the regular drill still works.
        assertTrue(problems.any { it.shape == "computational" }, "the regular drill must still be persisted: ${prep.problemsJson}")
    }
}

/** Local mirror of the route's private ApiTaskPrepReply DTO so the test can decode the serve reply. */
@kotlinx.serialization.Serializable
private data class ApiTaskPrepView(
    val taskId: String,
    val generatedAt: String,
    val version: Int,
    val problemsJson: String,
    val drillsJson: String,
    val railJson: String,
)
