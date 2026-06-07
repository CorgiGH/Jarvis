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
import kotlin.test.assertTrue

/**
 * E1 trustworthy-grader integration: POST /api/v1/drill/grade must apply the
 * deterministic scoring layer and record per-KC mastery ONLY for confident
 * grades. A fake grader Llm is injected through [drillGraderLlmFactory] (the
 * Step-B test seam) so the deterministic + mastery-write path runs without a
 * live model.
 */
class DrillGradeMasteryRouteTest {

    companion object {
        // The drill-grade handler always appends to TutorEventLog.GLOBAL, whose
        // dir freezes on FIRST access across the JVM. If this class runs before
        // TutorRoutesTest, pin the dir so GLOBAL does not freeze to the
        // production /opt/jarvis/data/private default (which fails on Windows /
        // pollutes the box). We use a manually-created temp dir (NOT @TempDir):
        // GLOBAL's async writer coroutine outlives the test methods, and a
        // per-class @TempDir gets cleaned out from under the still-running
        // writer, raising a FileNotFoundException on a background thread that
        // surfaces as a spurious test failure. A non-auto-cleaned dir avoids
        // that race. Only set if unset so we don't fight a dir another class
        // already pinned.
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("drill-grade-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    /** A fake Llm that returns a fixed grader JSON body regardless of input. */
    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = json to "fake-grader-model"
    }

    @AfterEach
    fun resetSeam() {
        // Restore the production factory so we don't leak the fake into other
        // tests that share the same JVM (the seam is a module-level var).
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    /**
     * Phase-3 GROUP 2: the grade path is now FAITHFUL-GATED (master §2.2 line 113). A confident grade
     * records mastery ONLY when the KC resolves to faithful via the B8 store. These helpers seed a
     * span-bearing PA KC + a matching faithful B8 row so the E1/E2 recording tests below exercise the
     * deterministic-recording contract over a faithful KC (mirrors TrustRoutesTest.seedContent).
     */
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

    private fun gradeBody(conceptIds: List<String>): String {
        val ids = conceptIds.joinToString(",") { "\"$it\"" }
        return """{
            "taskId":"task-1",
            "problemId":"d1",
            "userAttempt":"the median is 3",
            "problemStatement":"compute the median",
            "expectedAnswerHint":"3",
            "conceptIds":[$ids]
        }""".trimIndent()
    }

    @Test
    fun `confident grade records mastery`(@TempDir tmp: Path) = testApplication {
        // COHERENT GradeResult: correct=true AND every rubric item true →
        // GradeScoring.isConfident == true → grade is recorded.
        // Phase-3 GROUP 2: also FAITHFUL-GATED — pa-kc-001 is seeded faithful so it records.
        val content = tmp.resolve("content"); seedFaithfulContent(content, "pa-kc-001")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
                    """"score":1.0,"misconception":null,"elaborated_feedback":"correct"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithfulB8(ctx!!, content, "pa-kc-001")
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "p",
                    kcIds = listOf("pa-kc-001"),
                    canonicalAnswer = null))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody(listOf("pa-kc-001")))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNotNull(mastery, "confident grade must record KC mastery")
        assertEquals(1, mastery.observations, "exactly one observation recorded")
    }

    @Test
    fun `deferred incoherent grade records nothing`(@TempDir tmp: Path) = testApplication {
        // INCOHERENT GradeResult: correct=true but a rubric item is false →
        // correctFromRubric == false → disagrees with llm.correct →
        // GradeScoring.isConfident == false → grade is deferred (not recorded).
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":false},""" +
                    """"score":0.5,"misconception":"OTHER","elaborated_feedback":"partial"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody(listOf("pa-kc-001")))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNull(mastery, "incoherent (deferred) grade must NOT record KC mastery")
    }

    /** Build a grade-request body with explicit userAttempt and optional canonicalAnswer. */
    private fun gradeBodyWith(
        userAttempt: String,
        conceptIds: List<String>,
        canonicalAnswer: String? = null,
    ): String {
        val ids = conceptIds.joinToString(",") { "\"$it\"" }
        val canonicalField = if (canonicalAnswer != null) ""","canonicalAnswer":"$canonicalAnswer"""" else ""
        return """{
            "taskId":"task-1",
            "problemId":"d1",
            "userAttempt":"$userAttempt",
            "problemStatement":"sort n elements",
            "expectedAnswerHint":"O(n log n)"
            $canonicalField,
            "conceptIds":[$ids]
        }""".trimIndent()
    }

    @Test
    fun `canonical answer match records and reports answerMatch true`(@TempDir tmp: Path) = testApplication {
        // COHERENT grade: correct=true, all rubric items true → rubricCorrect=true,
        // coherent=true. canonicalAnswer normalises equal to userAttempt →
        // answerMatch=true, answerAgrees=true → confident=true → recorded=true.
        // Phase-3 GROUP 2: also FAITHFUL-GATED — pa-kc-001 seeded faithful so it records.
        val content = tmp.resolve("content"); seedFaithfulContent(content, "pa-kc-001")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
                    """"score":1.0,"misconception":null,"elaborated_feedback":"correct"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithfulB8(ctx!!, content, "pa-kc-001")
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "p",
                    kcIds = listOf("pa-kc-001"),
                    canonicalAnswer = "o(n log n)."))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            // userAttempt "O(n log n)" normalises to "o(n log n)"
            // server canonicalAnswer "o(n log n)." normalises to "o(n log n)" (trailing dot stripped)
            // → answerMatches returns true
            setBody(gradeBodyWith("O(n log n)", listOf("pa-kc-001"), canonicalAnswer = "o(n log n)."))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"answerMatch\":true"), "body=$body")
        assertTrue(body.contains("\"recorded\":true"), "body=$body")
        assertTrue(body.contains("\"confidence\":\"HIGH\""), "body=$body")

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNotNull(mastery, "canonical-match confident grade must record KC mastery")
        assertEquals(1, mastery.observations, "exactly one observation recorded")
    }

    @Test
    fun `canonical answer disagreeing with rubric defers and records nothing`(@TempDir tmp: Path) = testApplication {
        // COHERENT grade: correct=true, all rubric items true → rubricCorrect=true,
        // coherent=true. But canonicalAnswer "O(n^2)" does NOT match userAttempt
        // "O(n log n)" → answerMatch=false, answerAgrees=false (false ≠ true) →
        // confident=false → recorded=false, correct=false (deterministicCorrect=answerMatch=false).
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
                    """"score":1.0,"misconception":null,"elaborated_feedback":"correct"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            // userAttempt "O(n log n)" vs canonicalAnswer "O(n^2)" → no match
            setBody(gradeBodyWith("O(n log n)", listOf("pa-kc-001"), canonicalAnswer = "O(n^2)"))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"answerMatch\":false"), "body=$body")
        assertTrue(body.contains("\"recorded\":false"), "body=$body")
        assertTrue(body.contains("\"confidence\":\"LOW\""), "body=$body")
        assertTrue(body.contains("\"correct\":false"), "body=$body")

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNull(mastery, "disagreeing canonical answer must NOT record KC mastery")
    }
}
