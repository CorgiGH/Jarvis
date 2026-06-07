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
import jarvis.web.drillGraderLlmFactory
import jarvis.web.drillKcLookup
import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * E3 Track A end-to-end smoke: generate via fakes → grade the generated drill
 * → mastery observation moves to 1 on the generated drill's KC.
 * Client sends NO kcIds / NO canonicalAnswer — server resolves both from the
 * persisted Problem written by the generate-drills route.
 */
class E3GenerateGradeSmokeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("e3-gen-grade-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    @AfterEach
    fun reset() {
        drillGeneratorLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCriticLlmFactory = { jarvis.RelayLlm() }
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillKcLookup = { _, _ -> null }
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    /** Phase-3 GROUP 2: the grade path is FAITHFUL-GATED. Seed a span-bearing faithful KC + B8 row
     *  (in a temp content dir, not the real corpus) so the generate→grade→mastery loop records. */
    private fun seedFaithful(ctx: TutorContext, content: Path, kcId: String) {
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

    private fun seedTask(ctx: TutorContext, userId: String, taskId: String) {
        val now = Instant.now()
        TaskRepo(ctx.db).insert(Task(
            id = taskId, userId = userId, subject = "PA", title = "T-e3-smoke",
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
    fun `generated drill grades and records mastery on its KC`(@TempDir tmp: Path) = testApplication {
        // Wire all three seams before the app starts
        drillKcLookup = { _, _ ->
            KnowledgeConcept("pa-kc-fixture-compute", "PA", "a", "a", "c", "apply", 1, 1, 0.0, 2)
        }
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0)
                    """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"d","check":"c","expected_answer_hint":"42"}"""
                else
                    "42") to "g"
        }
        drillGeneratorLlmFactory = { gen }
        drillCriticLlmFactory = { object : Llm {
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}""" to "claude"
        } }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-1")
        // Phase-3 GROUP 2: the grade path is FAITHFUL-GATED — seed pa-kc-fixture-compute as faithful
        // in a temp content dir so the generate→grade→mastery loop records (not via the real corpus).
        val content = tmp.resolve("content")
        seedFaithful(ctx!!, content, "pa-kc-fixture-compute")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())

        val csrf = "test-csrf-e3-smoke"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // 1. Generate — creates the Problem persisted server-side
        val genResp = client.post("/api/v1/task/task-1/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-fixture-compute","shape":"computational","count":1}""")
        }
        assertEquals(HttpStatusCode.OK, genResp.status, "generate-drills must succeed: ${genResp.bodyAsText()}")

        // 2. Pre-check: no mastery yet
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-fixture-compute"),
            "mastery must be null before grade fires")

        // 3. Wire the grader fake — client sends NO kcIds / NO canonicalAnswer
        drillGraderLlmFactory = { object : Llm {
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                """{"correct":true,"rubric":{"ok":true},"score":1.0,"misconception":null,"elaborated_feedback":"good"}""" to "fake-grader"
        } }

        // 4. Grade the generated drill — problemId is "gen-pa-kc-fixture-compute-0"
        val gradeResp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"gen-pa-kc-fixture-compute-0","problemStatement":"Compute 6*7.","userAttempt":"42","expectedAnswerHint":"42"}""")
        }
        assertEquals(HttpStatusCode.OK, gradeResp.status, "grade must succeed: ${gradeResp.bodyAsText()}")

        // 5. Mastery row must now exist with observations == 1
        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-fixture-compute")
        assertNotNull(mastery, "KcMastery row must exist after grade")
        assertEquals(1, mastery.observations,
            "exactly one observation — generate→grade→mastery loop fired; mastery=$mastery")
    }
}
