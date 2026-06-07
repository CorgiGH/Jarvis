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
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            json to "fake-grader-model"
    }

    @AfterEach fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
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
}
