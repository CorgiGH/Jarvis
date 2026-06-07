package jarvis.web

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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.AiLiteracyRepo
import jarvis.tutor.AI_LITERACY_VERSION
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.VerificationStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase-3 Area C, GROUP 5 — mock-exam (SYNC-200, H13 / CONTRADICTION F1).
 *
 * THE FREEZE (NON-NEGOTIABLE): mock-exam is SYNC, 200-ONLY. submit ALWAYS returns 200.
 * NO 202, NO poll route, NO `mock_exam_jobs` table. Open-ended LLM-graded questions DEGRADE to
 * UNCERTAIN rather than blocking or going async.
 *
 * Class-killers proved here:
 *  - ALWAYS-200: a normal submit → 200 with kc_results; an open-ended question whose grader is
 *    unavailable/ambiguous → STILL 200, that kc_result graded UNCERTAIN (never 202/4xx/5xx).
 *  - NO async surface: no poll route exists; no `mock_exam_jobs` table is created; the reply carries
 *    no job_id and never a 202.
 *  - kc_results carry verification_status (per-KC trust badge, resolved from the B8 store).
 *  - per-user scoped (one user cannot read another's exam result).
 *  - empty/zero-question exam degrades cleanly (200, empty kc_results, score 0).
 *  - deterministic (closed-form) questions score correctly; the exam total aggregates per the lock.
 */
class MockExamRoutesTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("mock-exam-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    /** A grader LLM that always throws — exercises the open-ended degrade-to-UNCERTAIN path. */
    private class ThrowingGraderLlm : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = throw RuntimeException("boom-llm-down")
    }

    /** A grader LLM returning ambiguous/incoherent JSON — also must degrade to UNCERTAIN. */
    private class AmbiguousGraderLlm : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = "not json at all, just chatter" to "fake-ambiguous-model"
    }

    @AfterEach
    fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext, scope: UserScope = UserScope.OWNER): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", scope, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        return userId to sid
    }

    /**
     * Seed a PA corpus.
     *  - pa-kc-001 carries an `invariant` (= a checkable canonical answer) ⇒ DETERMINISTIC question.
     *  - pa-kc-009 carries NO invariant ⇒ OPEN (LLM-graded) question.
     * Both are span-bearing + authored faithful so the B8 faithful seed below resolves.
     */
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        // Deterministic KC: invariant "6*7=42" → canonical answer "42".
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"Inmultire\"\nname_en: \"Multiply\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "stem_template: \"6*7?\"\ninvariant: \"42\"\nverification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n",
        )
        // Open KC: no invariant ⇒ LLM-graded ⇒ degrade-to-UNCERTAIN when grader unavailable.
        pa.resolve("kcs/pa-kc-009.yaml").writeText(
            "id: pa-kc-009\nsubject: PA\nname_ro: \"Explica\"\nname_en: \"Explain\"\n" +
                "cluster: e\nbloom_level: analyze\ndifficulty: 3\ntime_minutes: 20\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "stem_template: \"Explain divide and conquer.\"\nverification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n",
        )
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    /** Seed a B8 row that resolveStatus serves as FAITHFUL (matching content_hash + present span hash). */
    private fun seedFaithful(ctx: TutorContext, content: Path, kcId: String) {
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

    private fun newClient(app: ApplicationTestBuilder) = app.createClient {
        install(HttpCookies)
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    /** Extract the exam_id from a /start reply body. */
    private fun examIdOf(body: String): String {
        val m = Regex("\"exam_id\"\\s*:\\s*\"([^\"]+)\"").find(body)
            ?: error("no exam_id in start reply: $body")
        return m.groupValues[1]
    }

    // ══════════════════════════ auth ══════════════════════════

    @Test
    fun `mock-exam start requires auth — 401`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        application { installFreshTutor(tmp) }
        startApplication()
        val client = newClient(this)
        val r = client.post("/api/v1/mock-exam/start") {
            cookie("csrf", "c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    // ══════════════════════════ ALWAYS-200 + deterministic scoring ══════════════════════════

    @Test
    fun `normal submit returns 200 with kc_results and deterministic scoring is correct`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        // A grader that throws would NOT matter for the deterministic KC; pin it anyway to prove the
        // deterministic path never touches the LLM.
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-001")
        val client = newClient(this)
        val csrf = "csrf-1"

        // start an exam scoped to PA.
        val startResp = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }
        assertEquals(HttpStatusCode.OK, startResp.status)
        val startBody = startResp.bodyAsText()
        assertTrue(startBody.contains("\"exam_id\""), startBody)
        assertTrue(startBody.contains("\"questions\""), startBody)
        // the deterministic KC must be classified kind=deterministic.
        assertTrue(startBody.contains("\"kind\":\"deterministic\""), startBody)
        val examId = examIdOf(startBody)

        // answer the deterministic question "6*7?" correctly with "42".
        val submitResp = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-pa-kc-001","response":"42"}]}""")
        }
        assertEquals(HttpStatusCode.OK, submitResp.status)
        val sb = submitResp.bodyAsText()
        assertTrue(sb.contains("\"kc_results\""), sb)
        assertTrue(sb.contains("\"score\""), sb)
        assertTrue(sb.contains("\"narrative\""), sb)
        // deterministic: 42 matches the invariant ⇒ correct=true, this kc scored 1.0 ⇒ exam score 1.0.
        assertTrue(sb.contains("\"correct\":true"), sb)
        assertTrue(sb.contains("pa-kc-001"), sb)
        // kc_results carry verification_status (resolved faithful from the B8 store).
        assertTrue(sb.contains("\"verification_status\":\"faithful\""), sb)
    }

    @Test
    fun `deterministic wrong answer scores correct=false`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-2"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        val submitResp = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-pa-kc-001","response":"99"}]}""")
        }
        assertEquals(HttpStatusCode.OK, submitResp.status)
        val sb = submitResp.bodyAsText()
        assertTrue(sb.contains("pa-kc-001"), sb)
        assertTrue(sb.contains("\"correct\":false"), sb)
    }

    // ══════════════════════════ THE FREEZE: open-ended degrade-to-UNCERTAIN, STILL 200 ══════════════════════════

    @Test
    fun `open-ended question with unavailable grader STILL 200 and degrades to UNCERTAIN`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }   // grader UNAVAILABLE
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-3"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        // the open KC must be classified kind=open.
        assertTrue(startBody.contains("\"kind\":\"open\""), startBody)
        val examId = examIdOf(startBody)

        val submitResp = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(
                """{"exam_id":"$examId","answers":[""" +
                    """{"question_id":"meq-pa-kc-009","response":"some open-ended essay answer"}]}""",
            )
        }
        // CLASS-KILLER: degrade NEVER returns 202/4xx/5xx — ALWAYS 200.
        assertEquals(HttpStatusCode.OK, submitResp.status)
        val sb = submitResp.bodyAsText()
        assertFalse(sb.contains("job_id"), "no async job_id may appear: $sb")
        assertTrue(sb.contains("pa-kc-009"), sb)
        // CLASS-KILLER: the degraded open question/kc is graded UNCERTAIN.
        assertTrue(
            sb.contains("\"verification_status\":\"uncertain\""),
            "degraded open question must carry verification_status=uncertain: $sb",
        )
        // a degraded answer is NOT marked correct (no auto-pass).
        assertTrue(sb.contains("\"correct\":false"), sb)
    }

    @Test
    fun `open-ended question with ambiguous grader STILL 200 and degrades to UNCERTAIN`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { AmbiguousGraderLlm() }   // grader returns un-parseable chatter
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-4"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        val submitResp = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(
                """{"exam_id":"$examId","answers":[""" +
                    """{"question_id":"meq-pa-kc-009","response":"some answer"}]}""",
            )
        }
        assertEquals(HttpStatusCode.OK, submitResp.status)
        val sb = submitResp.bodyAsText()
        assertTrue(sb.contains("pa-kc-009"), sb)
        assertTrue(
            sb.contains("\"verification_status\":\"uncertain\""),
            "ambiguous grader must degrade the open question to uncertain: $sb",
        )
    }

    // ══════════════════════════ NO async surface ══════════════════════════

    @Test
    fun `no poll route exists — a GET on a poll-style path is not 200`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        // a /poll or /status route MUST NOT exist (no async polling surface, H13).
        val poll = client.get("/api/v1/mock-exam/some-id/poll") { cookie("jarvis_session", sid) }
        assertFalse(poll.status == HttpStatusCode.OK, "a mock-exam poll route must not exist")
        val statusRoute = client.get("/api/v1/mock-exam/some-id/status") { cookie("jarvis_session", sid) }
        assertFalse(statusRoute.status == HttpStatusCode.OK, "a mock-exam status/poll route must not exist")
    }

    @Test
    fun `no mock_exam_jobs table is created by the migration`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val dbPath = tmp.resolve("t.db").toString()
        application { installFreshTutor(tmp) }
        startApplication()
        // inspect sqlite_master directly: the forbidden async-job table must NOT exist.
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='mock_exam_jobs'",
                )
                assertFalse(rs.next(), "mock_exam_jobs table must NOT be created (H13 freeze)")
            }
        }
    }

    // ══════════════════════════ result endpoint + per-user scoping ══════════════════════════

    @Test
    fun `GET result re-reads the graded exam and is per-user scoped`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sidA) = seedSession(ctx!!)
        val (_, sidB) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-001")
        val client = newClient(this)
        val csrf = "csrf-5"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sidA); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sidA); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-pa-kc-001","response":"42"}]}""")
        }

        // owner re-reads the result.
        val resultA = client.get("/api/v1/mock-exam/$examId/result") { cookie("jarvis_session", sidA) }
        assertEquals(HttpStatusCode.OK, resultA.status)
        val rb = resultA.bodyAsText()
        assertTrue(rb.contains("\"exam_id\":\"$examId\""), rb)
        assertTrue(rb.contains("\"score\""), rb)
        assertTrue(rb.contains("\"kc_results\""), rb)
        assertTrue(rb.contains("\"narrative\""), rb)

        // a DIFFERENT user must NOT read user A's exam (per-user scoping class-killer).
        val resultB = client.get("/api/v1/mock-exam/$examId/result") { cookie("jarvis_session", sidB) }
        assertFalse(resultB.status == HttpStatusCode.OK, "another user must not read this exam result")
    }

    // ══════════════════════════ empty / zero-question exam degrades cleanly ══════════════════════════

    @Test
    fun `empty subject exam degrades cleanly — 200 empty kc_results score 0`(@TempDir tmp: Path) = testApplication {
        // a subject that has no KCs (or an unknown subject) ⇒ zero-question exam, no throw.
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-6"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"NOSUCH"}""")
        }.bodyAsText()
        assertTrue(startBody.contains("\"questions\":[]"), startBody)
        val examId = examIdOf(startBody)
        val submitResp = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[]}""")
        }
        assertEquals(HttpStatusCode.OK, submitResp.status)
        val sb = submitResp.bodyAsText()
        assertTrue(sb.contains("\"kc_results\":[]"), sb)
        assertTrue(sb.contains("\"score\":0.0"), sb)
    }
}
