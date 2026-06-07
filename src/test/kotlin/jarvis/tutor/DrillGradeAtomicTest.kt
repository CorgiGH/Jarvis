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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
 * Phase-3 GROUP 2 — the ATOMIC GRADE (B1/B2/B3/H1/H4/M-GATE-PATHS/M-B1-CARD).
 *
 * Verifies the locked grade-path contract (master-impl-plan-v2 §2.2, line 113):
 *  - LLM grade resolves OUTSIDE the txn (a throwing grader writes NOTHING — H4).
 *  - ONE atomic txn does recordIn (+phase, B3) + attempts (H1) + upsertRubricCriterion (B2)
 *    + card upsert per server-resolved KC; a failure on the LAST step rolls back ALL (B1).
 *  - faithful-gated via VerificationGate.gate (D-RF2 admission gate; non-faithful ⇒ no mastery).
 *  - 409 if a targeted card is not ACTIVE (M-GATE-PATHS).
 *  - 1:N over server-resolved Problem.kcIds, each gated independently (M-B1-CARD).
 */
class DrillGradeAtomicTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("drill-grade-atomic-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = json to "fake-grader-model"
    }

    private class ThrowingGraderLlm : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = throw RuntimeException("boom-llm-down")
    }

    private val COHERENT = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
        """"score":1.0,"misconception":null,"elaborated_feedback":"ok"}"""

    @AfterEach
    fun resetSeam() {
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
        drillCardUpsertHook = { }
        System.clearProperty("JARVIS_CONTENT_DIR")
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

    /** A span-bearing faithful PA KC corpus (mirrors TrustRoutesTest.seedContent). */
    private fun seedContent(content: Path, vararg kcIds: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        for (kcId in kcIds) {
            pa.resolve("kcs/$kcId.yaml").writeText(
                "id: $kcId\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                    "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                    "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                    "verification_status: \"faithful\"\n" +
                    "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                    "    span:\n      start: 0\n      end: 9\n")
        }
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

    private fun seedProblem(ctx: TutorContext, taskId: String, problemId: String, kcIds: List<String>) {
        TaskPrepRepo(ctx.db).upsert(TaskPrep(
            taskId = taskId, generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(
                ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = problemId, page = 1, statement = "6*7?",
                    kcIds = kcIds, canonicalAnswer = "42")),
            ),
            drillsJson = "{}", railJson = "[]",
        ))
    }

    private fun gradeBody(taskId: String = "task-1", problemId: String = "d1", userAttempt: String = "42") =
        """{"taskId":"$taskId","problemId":"$problemId","userAttempt":"$userAttempt",""" +
            """"problemStatement":"6*7?","expectedAnswerHint":"42","student_confidence":"DEFINITELY","scaffold_level":0}"""

    private fun newClient(app: io.ktor.server.testing.ApplicationTestBuilder) = app.createClient {
        install(HttpCookies)
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun attemptCount(ctx: TutorContext, userId: String, kcId: String): Int = transaction(ctx.db) {
        AttemptsTable.selectAll()
            .where { (AttemptsTable.userId eq userId) and (AttemptsTable.kcId eq kcId) }
            .count().toInt()
    }

    private fun rubricCard(ctx: TutorContext, userId: String, kcId: String) = transaction(ctx.db) {
        FsrsCardsTable.selectAll()
            .where {
                (FsrsCardsTable.userId eq userId) and
                    (FsrsCardsTable.sourceType eq FsrsSource.RUBRIC_CRITERION.name) and
                    (FsrsCardsTable.kcId eq kcId)
            }
            .singleOrNull()
    }

    // ── faithful happy path: mastery + attempt + rubric card all written in one txn ──────────────
    @Test
    fun `faithful KC - confident grade writes mastery + attempt + rubric card`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"recorded\":true"), resp.bodyAsText())
        assertNotNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-005"), "faithful confident grade records mastery")
        assertEquals(1, attemptCount(ctx!!, userId, "pa-kc-005"), "one attempt row written")
        assertNotNull(rubricCard(ctx!!, userId, "pa-kc-005"), "rubric card upserted")
    }

    // ── faithful-gating DANGEROUS direction: a non-faithful KC must NEVER gain mastery ──────────
    @Test
    fun `non-faithful KC - confident grade records NO mastery (gate DENY)`(@TempDir tmp: Path) = testApplication {
        // Corpus exists but there is NO B8 faithful row ⇒ resolveStatus = unverified ⇒ gate DENY.
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"recorded\":false"), body)
        assertTrue(body.contains("\"kc_quarantined\":true"), body)
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-005"), "a non-faithful KC must NEVER gain mastery")
        assertEquals(0, attemptCount(ctx!!, userId, "pa-kc-005"), "no attempt written for a gated KC")
        assertNull(rubricCard(ctx!!, userId, "pa-kc-005"), "no rubric card for a gated KC")
    }

    // ── resolve-outside-txn (H4): a throwing grader writes NOTHING and opens no txn ──────────────
    @Test
    fun `throwing grader writes nothing (resolve outside txn, H4)`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { ThrowingGraderLlm() }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        // The grader threw BEFORE any txn ⇒ degraded 200 "UNGRADED", nothing persisted.
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"recorded\":false"), resp.bodyAsText())
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-005"), "no mastery written when the grader throws")
        assertEquals(0, attemptCount(ctx!!, userId, "pa-kc-005"), "no attempt written when the grader throws")
        assertNull(rubricCard(ctx!!, userId, "pa-kc-005"), "no card written when the grader throws")
    }

    // ── atomicity (B1 class-killer): a failure on the LAST write step rolls back ALL ─────────────
    @Test
    fun `failure on the last write step rolls back mastery + attempt + rubric (B1)`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        // Inject a throw at the LAST in-txn write step (the card upsert) — the whole txn must roll back.
        drillCardUpsertHook = { throw RuntimeException("boom-card-upsert") }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        // The route fails loud (500) on the in-txn throw — but the txn rolled back: ZERO persisted.
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-005"), "B1: mastery must roll back with the card")
        assertEquals(0, attemptCount(ctx!!, userId, "pa-kc-005"), "B1: attempt must roll back with the card")
        assertNull(rubricCard(ctx!!, userId, "pa-kc-005"), "B1: no orphaned card / partial write")
    }

    // ── 409-if-not-ACTIVE (M-GATE-PATHS): a targeted non-ACTIVE card ⇒ 409, no write ─────────────
    @Test
    fun `targeted non-ACTIVE card returns 409 and writes nothing`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005"))
        // Pre-seed a PAUSED RUBRIC_CRITERION card for (user, pa-kc-005) — grading must 409, not write.
        transaction(ctx!!.db) {
            FsrsCardsTable.insert {
                it[id] = TutorTypes.ulid()
                it[FsrsCardsTable.userId] = userId
                it[sourceType] = FsrsSource.RUBRIC_CRITERION.name
                it[sourceRef] = "pa-kc-005"
                it[front] = "old front"; it[back] = "old back"
                it[difficulty] = 5.0; it[stability] = 1.0; it[retrievability] = 1.0
                it[dueAt] = Instant.now(); it[lastReviewedAt] = Instant.now(); it[lapses] = 0
                it[kcId] = "pa-kc-005"
                it[status] = CardStatus.PAUSED.name
                it[pausedAt] = Instant.now()
            }
        }
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-005"), "409: no mastery written")
        assertEquals(0, attemptCount(ctx!!, userId, "pa-kc-005"), "409: no attempt written")
    }

    // ── 1:N over server-resolved kcIds: both faithful KCs written atomically ─────────────────────
    @Test
    fun `grade resolving to 2 faithful KCs writes mastery+attempt+rubric for BOTH`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content, "pa-kc-005", "pa-kc-006")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        seedFaithful(ctx!!, content, "pa-kc-005")
        seedFaithful(ctx!!, content, "pa-kc-006")
        seedProblem(ctx!!, "task-1", "d1", listOf("pa-kc-005", "pa-kc-006"))
        val csrf = "test-csrf-12345"
        val client = newClient(this)
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(gradeBody())
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        for (kc in listOf("pa-kc-005", "pa-kc-006")) {
            assertNotNull(KcMasteryRepo(ctx!!.db).get(userId, kc), "mastery for $kc")
            assertEquals(1, attemptCount(ctx!!, userId, kc), "attempt for $kc")
            assertNotNull(rubricCard(ctx!!, userId, kc), "rubric card for $kc")
        }
    }
}
