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
import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.web.drillCardUpsertHook
import jarvis.web.drillGraderLlmFactory
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase-3 GROUP 7 (SERVE WIRING) — route-level proof that the served teaching + H15 fields ride the
 * `/drill/grade` reply (TASK P3-MISC-SERVE / P3-LADDER-SERVE / P3-GHOST-FIELDS + H15).
 *
 * Class-killer (H16): with stored content present the served fields are NON-null/non-empty on the wire
 * (not always-null ghosts); with content absent they degrade to null/empty. The existing G2 grade shape
 * (DrillGradeAtomicTest) is unchanged — these fields are purely additive.
 */
class DrillGradeServeWiringTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("drill-grade-serve-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            json to "fake-grader-model"
    }

    // A coherent grade that fires the OFF_BY_ONE misconception code so the inline payload matches a stored row.
    private val COHERENT_WITH_MISC = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
        """"score":1.0,"misconception":"OFF_BY_ONE","elaborated_feedback":"Aproape — ai greșit la margine."}"""

    @AfterEach
    fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCardUpsertHook = { }
        System.clearProperty("JARVIS_CONTENT_DIR")
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

    /** Seed a faithful PA KC WITH a self_explanation_prompt + a matching misconception (refutation+figure_spec). */
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("misconceptions").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "verification_status: \"faithful\"\n" +
                "self_explanation_prompt: \"Explică de ce indicele începe de la 0.\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("misconceptions/pa-misc-off-by-one.yaml").writeText(
            "id: pa-misc-off-by-one\nkc_id: pa-kc-005\n" +
                "label_ro: \"Off by one\"\nlabel_en: \"Off by one\"\n" +
                "trigger: \"Crezi că array-ul începe de la 1.\"\n" +
                "refutation: \"Nu — în C indexarea începe de la 0.\"\n" +
                "figure_spec: \"diagram:array-index\"\n" +
                "self_explanation_prompt: \"De ce primul element are indicele 0?\"\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

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

    private fun seedProblem(ctx: TutorContext, kcIds: List<String>) {
        TaskPrepRepo(ctx.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(
                ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "6*7?", kcIds = kcIds, canonicalAnswer = "42")),
            ),
            drillsJson = "{}", railJson = "[]",
        ))
    }

    private fun gradeBody() =
        """{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"6*7?",""" +
            """"expectedAnswerHint":"42","student_confidence":"DEFINITELY","scaffold_level":0}"""

    private fun newClient(app: io.ktor.server.testing.ApplicationTestBuilder) = app.createClient {
        install(HttpCookies)
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ── stored content present ⇒ inline misconception + ladder + self-explanation + H15 fields ride the reply ──
    @Test
    fun `served teaching + H15 fields ride the grade reply when stored content is present`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT_WITH_MISC) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        // TASK P3-MISC-SERVE + P3-GHOST-FIELDS(c): inline payload with refutation + figure_spec (non-null).
        assertTrue(body.contains("\"misconception_payload\""), "inline misconception payload field present: $body")
        assertTrue(body.contains("pa-misc-off-by-one"), "matched misconception id served: $body")
        assertTrue(body.contains("în C indexarea începe de la 0"), "refutation served (P3-MISC-SERVE): $body")
        assertTrue(body.contains("diagram:array-index"), "figure_spec served for the ribbon (P3-GHOST-FIELDS c): $body")
        // TASK P3-LADDER-SERVE: the L0–L4 rung array + drill-level self_explanation_prompt.
        assertTrue(body.contains("\"ladder_rungs\""), "ladder rung array served (P3-LADDER-SERVE): $body")
        assertTrue(body.contains("\"level\":3"), "refutation rung (L3) present in the ladder: $body")
        assertTrue(body.contains("\"self_explanation_prompt\":\"Explică de ce indicele începe de la 0.\""),
            "drill-level self_explanation_prompt served (P3-GHOST-FIELDS a): $body")
        // H15: verification_status (honest faithful) + phase + next_phase_action + cross_checked.
        assertTrue(body.contains("\"verification_status\":\"faithful\""), "honest B8 verification_status served: $body")
        assertTrue(body.contains("\"phase\":"), "phase served on the grade reply (H15): $body")
        assertTrue(body.contains("\"next_phase_action\":"), "next_phase_action served (H15): $body")
        assertTrue(body.contains("\"cross_checked\":true"), "cross_checked true on a confident agreeing grade: $body")
        // Trust-language guard: NEVER claims "verified correct" — only the honest status value.
        assertTrue(!body.contains("verified correct", ignoreCase = true), "badge language must never say 'verified correct': $body")
    }

    // ── absent content ⇒ inline payload null, ladder degrades; the G2 verdict still ships ──
    @Test
    fun `served teaching fields degrade to null-empty when stored content is absent`(@TempDir tmp: Path) = testApplication {
        // KC with NO self_explanation_prompt + NO misconception; grader fires a code that matches nothing.
        val content = tmp.resolve("content"); content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories(); pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        // grader fires a code with no stored misconception ⇒ no inline payload.
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT_WITH_MISC) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"misconception_payload\":null"), "no stored misconception ⇒ null inline payload: $body")
        assertTrue(!body.contains("\"level\":3"), "no misconception ⇒ no L3 refutation rung: $body")
        // The G2 verdict still ships unchanged.
        assertTrue(body.contains("\"recorded\":true"), "the G2 grade verdict still ships: $body")
    }
}
