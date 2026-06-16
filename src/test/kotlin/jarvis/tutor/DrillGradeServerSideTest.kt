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
import jarvis.web.drillGraderLlmFactory
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2 server-side grading contract: the grade route must resolve Problem from
 * the persisted TaskPrep (server-side) and use Problem.kcIds / Problem.canonicalAnswer
 * — client-supplied conceptIds and canonicalAnswer are never trusted.
 */
class DrillGradeServerSideTest {
    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?) =
            json to "fake-grader-model"
    }

    /** A resolver/factory that THROWS an Error if the handler ever CALLS the LLM — proves the oracle
     *  short-circuit path never touches the LLM (Plan-6 Task 7 Step 2 (a)). AssertionError (not Exception)
     *  so the handler's `catch (Exception)` degraded path can NEVER mask it as UNGRADED. */
    private class ThrowingLlm : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?): Pair<String, String> =
            throw AssertionError("LLM must NOT be constructed/called on the oracle short-circuit path")
        override fun close() {}
    }

    /** A fake LLM that fails like a transient transport error (a normal Exception) — drives the handler's
     *  graceful UNGRADED degraded path (Plan-6 Task 7 Step 2 (b)). */
    private class FailingLlm : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?): Pair<String, String> =
            throw java.io.IOException("simulated transient LLM failure")
        override fun close() {}
    }

    @AfterEach fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        jarvis.web.drillGraderLlmResolver = null
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    /** Phase-3 GROUP 2: the grade path is FAITHFUL-GATED. Seed a span-bearing faithful KC + B8 row so
     *  the E2 server-side-kcIds recording test exercises recording over a faithful KC. */
    private fun seedFaithfulContent(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            "id: $kcId\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    private fun seedFaithfulB8(ctx: TutorContext, content: Path, kcId: String) {
        val repo = ContentRepo(content)
        val kc = repo.loadSubject("PA").kcs.single { it.id == kcId }
        val contentHash = ContentReconcile.kcContentHash(kc)
        val spanHash = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc)) { subject, doc ->
            repo.sourceText(subject, doc)
        } ?: error("kc $kcId quote must relocate for a source_span_hash")
        transaction(ctx.db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = VerificationStatus.faithful.name
                it[KcVerificationStatusTable.contentHash] = contentHash
                it[sourceSpanHash] = spanHash
                it[updatedAt] = Instant.now()
            }
        }
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

    private val COHERENT = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
        """"score":1.0,"misconception":null,"elaborated_feedback":"ok"}"""

    @Test
    fun `mastery records on persisted Problem kcIds, ignoring client conceptIds`(@TempDir tmp: Path) = testApplication {
        // Phase-3 GROUP 2: FAITHFUL-GATED — pa-kc-002 seeded faithful so it records.
        val content = tmp.resolve("content"); seedFaithfulContent(content, "pa-kc-002")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithfulB8(ctx!!, content, "pa-kc-002")
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "6*7?",
                    kcIds = listOf("pa-kc-002"), canonicalAnswer = "42"))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"6*7?","expectedAnswerHint":"42","conceptIds":["WRONG-KC"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"answerMatch\":true"), "server canonical used; body=${resp.bodyAsText()}")
        assertNotNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-002"), "records on persisted Problem.kcIds")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "WRONG-KC"), "client conceptIds NOT trusted")
    }

    @Test
    fun `confident grade with no persisted kcIds records nothing and recorded is false`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"x","problemStatement":"p","expectedAnswerHint":"h","conceptIds":["pa-kc-001"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"recorded\":false"), "no server kcIds → not recorded; body=${resp.bodyAsText()}")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001"), "client conceptIds must not record")
    }

    // ── Plan-6 Task 7 — grader-CHAIN integration route tests ──────────────────────────────────

    /** (a) A numeric problem grades through the ORACLE leg: decided_by="numeric-oracle" and the LLM is
     *  NEVER constructed (a throwing resolver + throwing factory would AssertionError if it were). */
    @Test
    fun `numeric problem decided by oracle leg without constructing the LLM`(@TempDir tmp: Path) = testApplication {
        // Throwing seams: if the handler resolves/constructs an LLM, the test fails loudly.
        jarvis.web.drillGraderLlmResolver = { _, _ -> ThrowingLlm() }
        drillGraderLlmFactory = { ThrowingLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "6*7?",
                    kcIds = emptyList(), canonicalAnswer = "42"))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"6*7?","expectedAnswerHint":"42"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"decided_by\":\"numeric-oracle\""), "oracle decided; body=$body")
        assertTrue(body.contains("\"correct\":true"), "42 == 42; body=$body")
    }

    /** (b) LLM failure on a PROSE problem → the existing UNGRADED degraded reply still ships (regression
     *  pin): the non-LLM legs defer, the LLM throws, the 200/UNGRADED body is byte-compatible. */
    @Test
    fun `LLM failure on prose problem still yields the UNGRADED degraded reply`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FailingLlm() } // simulate transient LLM failure (normal Exception)
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        // Prose: no canonical answer, no language → chain [RUBRIC, LLM_JUDGE]; rubric defers (label-only),
        // LLM throws → UNGRADED.
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"some prose","problemStatement":"explain","expectedAnswerHint":"h"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"misconception\":\"UNGRADED\""), "UNGRADED preserved; body=$body")
        assertTrue(body.contains("\"recorded\":false"), "nothing recorded on UNGRADED; body=$body")
    }

    /** (c) The reply JSON carries the three additive chain fields with correct shapes on the LLM path. */
    @Test
    fun `reply carries the additive chain fields decided_by degraded_legs_ro item_verdicts`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"x","problemStatement":"p","expectedAnswerHint":"h"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        // LLM decided this prose drill → decided_by="llm-judge"; the additive fields are present.
        assertTrue(body.contains("\"decided_by\":\"llm-judge\""), "LLM-judge decided; body=$body")
        assertTrue(body.contains("\"degraded_legs_ro\":["), "degraded_legs_ro present; body=$body")
        assertTrue(body.contains("\"item_verdicts\":["), "item_verdicts present; body=$body")
    }

    /** (d) A degraded chain serves non-empty degraded_legs_ro whose copy is RO (diacritic-bearing). The
     *  rubric leg deferred (label-only items) → its RO degraded copy rides the LLM-decided reply. */
    @Test
    fun `degraded chain serves RO degraded leg copy`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        // Rubric items ride the request (label-only) → the rubric leg defers → its RO copy surfaces.
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"x","problemStatement":"p","expectedAnswerHint":"h","rubricItems":["criteriu unu","criteriu doi"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"decided_by\":\"llm-judge\""), "LLM decided after rubric deferral; body=$body")
        // RO diacritic-bearing degraded copy for the deferred rubric leg.
        assertTrue(
            body.contains("evaluat de corectorul lingvistic") && body.contains("verificabil"),
            "degraded_legs_ro carries RO rubric-deferral copy; body=$body",
        )
    }

    /** (e) Anti-vacuity: the dominant task_prep shape — request-borne rubricItems (no bank row) — NEVER
     *  builds [LLM_JUDGE] alone. With no machine-checkable item the rubric leg defers and the reply
     *  honestly names the LLM decision + the deferral (REQ-26 / INV-6.3 on real traffic). */
    @Test
    fun `request-rubric task_prep drill is never LLM-alone and reports honest deferral`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        // A real task_prep code drill: rubricItems ride the request, NO bank problem row, language=cpp.
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "write a function",
                    kcIds = emptyList(), rubricItems = listOf("compiles", "returns the right value")))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"int f(){return 1;}","problemStatement":"write a function","expectedAnswerHint":"h","language":"cpp","rubricItems":["compiles","returns the right value"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        // The execution leg has no runnable source on /drill/grade → defers; rubric (label-only) defers →
        // the LLM decides, and BOTH deferrals surface in degraded_legs_ro. Never [LLM_JUDGE] alone.
        assertTrue(body.contains("\"decided_by\":\"llm-judge\""), "LLM decided after non-LLM deferral; body=$body")
        assertTrue(body.contains("\"degraded_legs_ro\":["), "deferrals named; body=$body")
        assertTrue(body.contains("verificabil") || body.contains("indisponibil"),
            "RO deferral copy present (rubric and/or execution); body=$body")
    }
}
