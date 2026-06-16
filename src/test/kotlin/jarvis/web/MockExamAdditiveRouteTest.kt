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
import jarvis.tutor.AiLiteracyRepo
import jarvis.tutor.AI_LITERACY_VERSION
import jarvis.tutor.BankProblem
import jarvis.tutor.ProblemsRepo
import jarvis.tutor.RubricItem
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-6 Task 11 (§6.2.4, R-6-Q7) — mock-exam ADDITIVE semantics.
 *
 * Two halves:
 *  1. REGRESSION PINS (Step 2): the LEGACY start/submit/result shapes are unchanged by the additive
 *     diff — a pre-plan6 payload returns a pre-plan6-shaped reply, no new REQUIRED field, no behavior
 *     change, the SYNC-200 freeze intact. These pins are written FIRST and stay green through the task
 *     (live-endpoints-must-not-break, R-6-Q7).
 *  2. ADDITIVE SEMANTICS (Step 4): a `format_id` on start stamps format/timer/phase/synthetic_tag; the
 *     new `POST /{id}/phase` advances the phase (sync-200); submit grades bank-problem rubric G-items
 *     through the chain's RUBRIC leg → rubric_results; result adds timer/phase/rubric_result/
 *     common_errors_ro/synthetic_tag. The P2-5 freeze is preserved: KC questions still emit only
 *     `kind:"open"`; rubric grading touches bank-problem G-items ONLY, never KC-invariant questions.
 */
class MockExamAdditiveRouteTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("mock-exam-additive-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    /** A confident, coherent grader (rubric all-pass) — used where an open question needs a non-degraded grade. */
    private class CoherentGraderLlm : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> =
            """{"correct":true,"rubric":{"numeric":true},"score":1.0,"misconception":null,""" +
                """"elaborated_feedback":"ok"}""" to "fake-coherent-model"
    }

    private class ThrowingGraderLlm : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> = throw RuntimeException("boom-llm-down")
    }

    @AfterTest
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

    /** Minimal PA corpus (one open KC) so a legacy /start has a question. */
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
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

    /** Seed a bank problem with a machine-checkable rubric G-item so the rubric leg can decide. */
    private fun seedBankProblem(ctx: TutorContext) {
        val repo = ProblemsRepo(ctx.db)
        repo.upsert(
            BankProblem(
                id = "me-bank-001",
                subject = "PA",
                archetype = "proof",
                statementJson = """{"ro":"Demonstrați."}""",
                parameterSlotsJson = null,
                solutionPresent = true,
                solutionJson = null,
                examLanguage = null,
                examLanguageConstraintsJson = null,
                dataFilesJson = null,
                sourceDoc = "pa-lecture-01",
                sourcePage = 1,
                provenance = "located",
                syntheticTag = true,
            ),
        )
        repo.upsertRubricItems(
            listOf(
                RubricItem(
                    id = "G1",
                    problemId = "me-bank-001",
                    label = "G1 reducere",
                    points = 1.0,
                    kind = "AP",
                    allOrNothing = false,
                    // Machine-checkable: the answer must contain "reducere".
                    penaltyRulesJson = """{"matcher":{"kind":"contains","value":"reducere"}}""",
                    position = 0,
                ),
            ),
        )
    }

    private fun newClient(app: ApplicationTestBuilder) = app.createClient {
        install(HttpCookies)
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun examIdOf(body: String): String {
        val m = Regex("\"exam_id\"\\s*:\\s*\"([^\"]+)\"").find(body)
            ?: error("no exam_id in start reply: $body")
        return m.groupValues[1]
    }

    // ══════════════════════════ REGRESSION PINS (legacy shape unchanged) ══════════════════════════

    @Test
    fun `legacy start with no format_id returns the legacy reply shape and SYNC-200`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-legacy-start"
        val r = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // Legacy fields present and unchanged.
        assertTrue(body.contains("\"exam_id\""), body)
        assertTrue(body.contains("\"questions\""), body)
        // KC questions still emit only kind=open (P2-5 freeze).
        assertFalse(body.contains("\"kind\":\"deterministic\""), body)
        assertTrue(body.contains("\"kind\":\"open\""), body)
    }

    @Test
    fun `legacy submit returns legacy reply shape and is ALWAYS 200`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-legacy-submit"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        val r = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-pa-kc-009","response":"ceva"}]}""")
        }
        // The FREEZE: ALWAYS 200, no 202, no async job id.
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertFalse(body.contains("job_id"), body)
        assertTrue(body.contains("\"kc_results\""), body)
        assertTrue(body.contains("\"score\""), body)
        assertTrue(body.contains("\"narrative\""), body)
    }

    @Test
    fun `legacy result re-read shape unchanged`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-legacy-result"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"subject":"PA"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-pa-kc-009","response":"ceva"}]}""")
        }
        val r = client.get("/api/v1/mock-exam/$examId/result") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"exam_id\":\"$examId\""), body)
        assertTrue(body.contains("\"score\""), body)
        assertTrue(body.contains("\"kc_results\""), body)
        assertTrue(body.contains("\"narrative\""), body)
    }

    // ══════════════════════════ ADDITIVE: format / timer / phase / synthetic ══════════════════════════

    @Test
    fun `start with format_id stamps format timer phase synthetic-tag (additive)`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-fmt"
        val r = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"PA-standard"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // Additive fields present.
        assertTrue(body.contains("\"format\""), body)
        assertTrue(body.contains("\"timer\""), body)
        assertTrue(body.contains("\"phase\""), body)
        assertTrue(body.contains("\"synthetic_tag\":true"), body)
        // Format carries PA point brackets (REQ-14) — printed bracket copy survives serialization.
        assertTrue(body.contains("1.5p") || body.contains("point_bracket"), body)
        // Legacy fields still present.
        assertTrue(body.contains("\"exam_id\""), body)
        assertTrue(body.contains("\"questions\""), body)
    }

    @Test
    fun `phase endpoint advances phase_index and stays SYNC-200`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-phase"
        // ALO format has TWO phases (docs-allowed switch).
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"ALO-docs-allowed"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        val r = client.post("/api/v1/mock-exam/$examId/phase") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        // sync-200, never async.
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertFalse(body.contains("job_id"), body)
        assertTrue(body.contains("\"phase_index\":1"), body)
    }

    @Test
    fun `phase endpoint clamps at the last phase`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-clamp"
        // PA-standard has a SINGLE phase: advancing must clamp at index 0.
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"PA-standard"}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        val r = client.post("/api/v1/mock-exam/$examId/phase") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"phase_index\":0"), r.bodyAsText())
    }

    // ══════════════════════════ ADDITIVE: rubric-leg grading on a bank problem ══════════════════════════

    @Test
    fun `submit grades bank-problem rubric G-items through the rubric leg`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        seedBankProblem(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-rubric"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"PA-standard","problem_ids":["me-bank-001"]}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        // Answer the bank-problem question with text that satisfies the machine-checkable G1 matcher.
        val r = client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-prob-me-bank-001","response":"folosesc o reducere"}]}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // Per-G-item rubric breakdown present (REQ-16/17).
        assertTrue(body.contains("\"rubric_result\""), body)
        assertTrue(body.contains("G1"), body)
        // The rubric leg decided structurally (machine-checkable G1 passed) — points earned.
        assertTrue(body.contains("\"passed\":true"), body)
    }

    @Test
    fun `result reply carries additive timer phase rubric_result common_errors and synthetic_tag`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        seedBankProblem(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-resadd"
        val startBody = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"PA-standard","problem_ids":["me-bank-001"]}""")
        }.bodyAsText()
        val examId = examIdOf(startBody)
        client.post("/api/v1/mock-exam/$examId/submit") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"exam_id":"$examId","answers":[{"question_id":"meq-prob-me-bank-001","response":"o reducere gresita"}]}""")
        }
        val r = client.get("/api/v1/mock-exam/$examId/result") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"timer\""), body)
        assertTrue(body.contains("\"phase\""), body)
        assertTrue(body.contains("\"rubric_result\""), body)
        assertTrue(body.contains("\"common_errors_ro\""), body)
        assertTrue(body.contains("\"synthetic_tag\":true"), body)
    }

    // ══════════════════════════ P2-5 freeze preserved ══════════════════════════

    @Test
    fun `KC questions still emit only kind open under a format (P2-5 preserved)`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val client = newClient(this)
        val csrf = "csrf-p25"
        val body = client.post("/api/v1/mock-exam/start") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","format_id":"PA-standard"}""")
        }.bodyAsText()
        // Even with a format applied, KC questions are still only "open" — never "deterministic".
        assertFalse(body.contains("\"kind\":\"deterministic\""), body)
    }
}
