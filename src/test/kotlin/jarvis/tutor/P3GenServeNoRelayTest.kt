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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK P3-GEN (master-plan:204, D1, CONTRADICTION F2) — the two class-killer route proofs:
 *
 *  (1) a generated+persisted Problem carries `modelTag` == the relay's RETURNED model id
 *      (non-null, != the criticUsed="relay/claude" literal at TutorRoutes:1546), persisted to
 *      task_prep.problems_json by the generate route.
 *  (2) the live SERVE route (`GET /api/v1/tasks/{id}/prep`) reads that persisted Problem and
 *      makes ZERO relay/LLM calls — proven by a TRIPWIRE LLM factory that THROWS if any LLM is
 *      ever constructed on the serve path. The served Problem still carries the persisted modelTag.
 *
 * No live-relay / network dependency: the generate path uses an injected FAKE relay returning a
 * known model id; the serve path uses a tripwire that throws on construction so a stray relay call
 * fails loud (NOT a silent live-relay test).
 */
class P3GenServeNoRelayTest {
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""
    private val relayModelId = "anthropic/claude-sonnet-4-5"   // what the (fake) relay returns

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("p3gen-serve-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    @AfterEach fun reset() {
        drillGeneratorLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCriticLlmFactory = { jarvis.RelayLlm() }
        drillKcLookup = { _, _ -> null }
    }

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
            id = taskId, userId = userId, subject = "PA", title = "T-p3gen",
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

    @Test fun `generate persists modelTag from relay-returned id, then serve reads it with ZERO relay calls`(
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

        drillKcLookup = { _, _ -> KnowledgeConcept("pa-kc-001", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1) }
        // FAKE relay-backed generator: returns its model id as the Pair's second element.
        val genCalls = AtomicInteger(0)
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?): Pair<String, String> {
                genCalls.incrementAndGet()
                return (if (n++ == 0) goodDrill else "42") to relayModelId
            }
        }
        val criticCalls = AtomicInteger(0)
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = {
            object : Llm {
                override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?): Pair<String, String> {
                    criticCalls.incrementAndGet(); return goodCritic to "fake-critic"
                }
            }
        }

        val csrf = "test-csrf-p3gen"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // ── PHASE 1: generate + persist ───────────────────────────────────────────
        val genResp = client.post("/api/v1/task/task-1/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-001","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, genResp.status, genResp.bodyAsText())
        assertTrue(genCalls.get() > 0, "generator (relay) MUST be called on the generate path")

        // class-killer (1): persisted Problem carries modelTag == relay-returned id, not the literal.
        val prepAfterGen = TaskPrepRepo(ctx!!.db).findByTaskId("task-1")!!
        val persisted = Json.decodeFromString(ListSerializer(Problem.serializer()), prepAfterGen.problemsJson)
        val gen1 = persisted.first { it.problemId == "gen-pa-kc-001-0" }
        assertEquals(relayModelId, gen1.modelTag, "persisted Problem.modelTag must be the relay-returned model id")
        assertTrue(gen1.modelTag != "relay/claude", "modelTag must never be the criticUsed='relay/claude' literal")

        // ── PHASE 2: serve makes ZERO relay calls ─────────────────────────────────
        // Tripwire: any LLM factory invocation on the serve path THROWS (fail-loud).
        drillGeneratorLlmFactory = { error("TRIPWIRE: serve path constructed a generator LLM (must be zero)") }
        drillCriticLlmFactory = { error("TRIPWIRE: serve path constructed a critic LLM (must be zero)") }
        val genCallsBeforeServe = genCalls.get()
        val criticCallsBeforeServe = criticCalls.get()

        val serveResp = client.get("/api/v1/tasks/task-1/prep") {
            cookie("jarvis_session", sid)
        }
        assertEquals(HttpStatusCode.OK, serveResp.status, serveResp.bodyAsText())

        // class-killer (2): zero new LLM calls AND no LLM construction (the tripwire never threw → 200).
        assertEquals(genCallsBeforeServe, genCalls.get(), "serve path must make ZERO generator/relay calls")
        assertEquals(criticCallsBeforeServe, criticCalls.get(), "serve path must make ZERO critic/relay calls")

        // the served Problem still carries the persisted modelTag (read-through, no re-derivation).
        // ApiTaskPrepReply is package-private in jarvis.web; parse the envelope generically and
        // pull the nested problemsJson string field, then decode the Problem list from it.
        val envelope = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(serveResp.bodyAsText()) as kotlinx.serialization.json.JsonObject
        val problemsJsonStr = (envelope["problemsJson"] as kotlinx.serialization.json.JsonPrimitive).content
        val servedProblems = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(Problem.serializer()), problemsJsonStr)
        val servedGen1 = servedProblems.first { it.problemId == "gen-pa-kc-001-0" }
        assertEquals(relayModelId, servedGen1.modelTag, "served Problem must carry the persisted modelTag")
    }
}
