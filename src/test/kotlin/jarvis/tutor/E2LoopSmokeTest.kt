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
import jarvis.web.drillGraderLlmFactory
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
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
 * E2 end-to-end smoke: proves the full mastery loop fires through the two real
 * production HTTP routes in sequence.
 *
 * The client supplies NO kcIds and NO canonicalAnswer in the grade request.
 * The server resolves both from the persisted Problem (written by prep-authored).
 * Mastery must move from null → observations == 1.
 */
class E2LoopSmokeTest {

    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            json to "fake-grader-model"
    }

    @AfterEach
    fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    /** Phase-3 GROUP 2: the grade path is FAITHFUL-GATED. Seed a span-bearing faithful KC + B8 row
     *  (temp content dir) so the prep-authored→grade→mastery loop records over a faithful KC. */
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

    companion object {
        private val COHERENT = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
            """"score":1.0,"misconception":null,"elaborated_feedback":"ok"}"""

        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("e2-loop-smoke-evtlog")
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
    fun `prep-authored then grade fires mastery server-side without client supplying kcIds or canonicalAnswer`(
        @org.junit.jupiter.api.io.TempDir tmp: Path
    ) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        // 1. Seed session + task owned by userId
        val (userId, sid) = seedSession(ctx!!)
        seedTask(ctx!!, userId, "task-1")
        // Phase-3 GROUP 2: FAITHFUL-GATED — seed pa-kc-001 faithful (temp content dir) so it records.
        val content = tmp.resolve("content")
        seedFaithful(ctx!!, content, "pa-kc-001")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())

        val csrf = "test-csrf-e2-smoke"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // 2. POST prep-authored — writes Problem with kcId="pa-kc-001" and canonicalAnswer="42"
        val prepBody = """{"problems":[{"problemId":"d1","page":1,"statement":"compute 6*7","kcIds":["pa-kc-001"],"canonicalAnswer":"42"}]}"""
        val prepResp = client.post("/api/v1/task/task-1/prep-authored") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(prepBody)
        }
        assertEquals(HttpStatusCode.OK, prepResp.status, "prep-authored must succeed: ${prepResp.bodyAsText()}")

        // 3. Pre-check: mastery not yet recorded
        assertNull(
            KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001"),
            "mastery must be null before grade fires"
        )

        // 4. POST grade — deliberately omit conceptIds and canonicalAnswer; server resolves both from persisted Problem
        val gradeBody = """{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"compute 6*7","expectedAnswerHint":"42"}"""
        val gradeResp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody)
        }
        assertEquals(HttpStatusCode.OK, gradeResp.status, "grade must succeed: ${gradeResp.bodyAsText()}")

        val gradeText = gradeResp.bodyAsText()
        assertTrue(gradeText.contains("\"answerMatch\":true"),
            "server used persisted canonical '42' → answerMatch must be true; body=$gradeText")
        assertTrue(gradeText.contains("\"recorded\":true"),
            "mastery must be recorded; body=$gradeText")

        // 5. Post-check: mastery row exists with observations == 1 — loop fired end-to-end
        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNotNull(mastery, "KcMastery row must exist after grade")
        assertEquals(1, mastery.observations,
            "exactly one observation recorded — loop fired end-to-end; mastery=$mastery")
    }
}
