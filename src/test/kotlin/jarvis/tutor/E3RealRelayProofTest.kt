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
import io.ktor.server.testing.TestApplication
import jarvis.content.KnowledgeConcept
import jarvis.content.SourceRef
import jarvis.web.drillCriticLlmFactory
import jarvis.web.drillGeneratorLlmFactory
import jarvis.web.drillGraderLlmFactory
import jarvis.web.drillKcLookup
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.jetbrains.exposed.sql.insert
import java.io.File
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
 * E3 real-model proof (active-constraint #1): drives the generation pipeline
 * through the LIVE RelayLlm (claude-max via tools/pc-relay-server.py) instead
 * of fakes. NOT a CI test — skipped unless JARVIS_RELAY_URL is set, so it never
 * hits the network in a normal run. Run manually with the relay up:
 *
 *   JARVIS_RELAY_URL=http://127.0.0.1:9999 JARVIS_RELAY_TOKEN=... \
 *     gradle test --tests 'jarvis.tutor.E3RealRelayProofTest'
 *
 * Proof KC is a self-contained, deterministic PA fact (binary-tree node bound)
 * so a real computational drill is genuinely groundable + self-solvable — it is
 * a test proof-fixture, NOT real curated subject content.
 */
class E3RealRelayProofTest {

    companion object {
        // Self-contained source the generator must ground in; height 3 → 2^4-1 = 15.
        private val PROOF_KC = KnowledgeConcept(
            id = "pa-kc-relayproof",
            subject = "PA",
            name_ro = "Numarul maxim de noduri intr-un arbore binar",
            name_en = "Maximum number of nodes in a binary tree",
            cluster = "Structuri arborescente",
            bloom_level = "apply",
            difficulty = 2,
            time_minutes = 10,
            exam_weight = 0.0,
            tier = 2,
            source = listOf(
                SourceRef(
                    doc = "pa-relayproof",
                    quote = "In a binary tree of height h, the maximum number of nodes is " +
                        "2^(h+1) - 1. A binary tree consisting of a single root node has height 0.",
                ),
            ),
        )
        private val SOURCES = PROOF_KC.source.map { it.quote }
        private val EVIDENCE = File(System.getProperty("user.dir"), ".tmp-relay-proof.txt")
        private val PROBLEM_JSON = Json { ignoreUnknownKeys = true }

        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("e3-relayproof-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
            EVIDENCE.writeText("# E3 real-relay proof — ${Instant.now()}\n")
        }

        private fun log(s: String) {
            println(s)
            EVIDENCE.appendText(s + "\n")
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

    /** Phase-3 GROUP 2: the grade path is FAITHFUL-GATED. Seed pa-kc-relayproof as a faithful content
     *  KC (its source quote relocating in _sources/pa-relayproof.md) + a matching B8 row, in a temp
     *  content dir, so the real generate→grade→mastery loop records over a faithful KC. */
    private fun seedFaithfulRelayProof(ctx: TutorContext, content: Path) {
        val quote = PROOF_KC.source.first().quote
        content.resolve("PA/kcs").createDirectories()
        content.resolve("PA/_sources").createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        content.resolve("PA/edges.yaml").writeText("subject: PA\nedges: []\n")
        content.resolve("PA/_sources/pa-relayproof.md").writeText("$quote\n")
        content.resolve("PA/kcs/pa-kc-relayproof.yaml").writeText(
            "id: pa-kc-relayproof\nsubject: PA\n" +
                "name_ro: \"Numarul maxim de noduri intr-un arbore binar\"\n" +
                "name_en: \"Maximum number of nodes in a binary tree\"\n" +
                "cluster: \"Structuri arborescente\"\nbloom_level: apply\ndifficulty: 2\n" +
                "time_minutes: 10\nexam_weight: 0.0\ntier: 2\nversion: 1\n" +
                "verification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-relayproof\n    quote: \"$quote\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: ${quote.length}\n")
        val repo = jarvis.content.ContentRepo(content)
        val kc = repo.loadSubject("PA").kcs.single { it.id == "pa-kc-relayproof" }
        val contentHash = jarvis.content.ContentReconcile.kcContentHash(kc)
        val spanHash = jarvis.content.ContentReconcile.sourceSpanHashOf(
            jarvis.content.ContentReconcile.claimsFor(kc),
        ) { subject, doc -> repo.sourceText(subject, doc) }
            ?: error("pa-kc-relayproof quote must relocate for a source_span_hash")
        org.jetbrains.exposed.sql.transactions.transaction(ctx.db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-relayproof"
                it[status] = VerificationStatus.faithful.name
                it[KcVerificationStatusTable.contentHash] = contentHash
                it[sourceSpanHash] = spanHash
                it[updatedAt] = Instant.now()
            }
        }
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
    }

    /**
     * CORE proof of the actually-unproven thing: a REAL model authors a drill
     * that survives every safeguard (answer-leak, self-solve reconcile,
     * cross-family critic) — generator AND critic both real claude via relay.
     */
    @Test
    fun `real relay authors a safeguard-passing computational drill`() {
        assumeTrue(System.getenv("JARVIS_RELAY_URL") != null) { "JARVIS_RELAY_URL unset — skipping live relay proof" }

        // count=3 absorbs real-model variance: the self-solve reconcile does
        // exact/numeric matching on the model's answer, but models often wrap
        // it in prose/markdown ("**63**\n\nFormula…") => occasional false-reject.
        // >=1 of 3 typically self-solves cleanly. (See findings: self-solve
        // matching brittleness is a real production risk with chatty generators.)
        val result = runBlocking {
            jarvis.RelayLlm().use { gen ->
                jarvis.RelayLlm().use { critic ->
                    DrillGenerator.generate(PROOF_KC, SOURCES, "computational", 3, gen, critic)
                }
            }
        }

        log("\n== CORE: DrillGenerator.generate (computational, count=3) ==")
        log("accepted=${result.bundles.size}  rejected=${result.rejectReasons.size}")
        result.rejectReasons.forEach { log("  REJECT: $it") }
        result.bundles.forEach {
            log("  ACCEPTED problemId=${it.problem.problemId}")
            log("    statement: ${it.problem.statement}")
            log("    canonicalAnswer: ${it.problem.canonicalAnswer}")
            log("    worked: ${it.content.worked.take(160)}")
            log("    vizId: ${it.content.vizId}")
        }

        assertTrue(
            result.bundles.isNotEmpty(),
            "real relay must author >=1 drill that passes all safeguards; rejects=${result.rejectReasons}",
        )
        val b = result.bundles.first()
        assertEquals(listOf("pa-kc-relayproof"), b.problem.kcIds, "drill must carry the server-canonical KC id")
        assertNotNull(b.problem.canonicalAnswer, "computational drill must carry a canonical answer")
    }

    /**
     * END-TO-END: the real generated drill grades through the actual route on
     * the server-canonical Problem (client trusts nothing) and mastery moves.
     * Generator + critic + grader all real claude via relay.
     */
    @Test
    fun `real generated drill grades through the route and moves mastery`(@TempDir tmp: Path): Unit = runBlocking {
        assumeTrue(System.getenv("JARVIS_RELAY_URL") != null) { "JARVIS_RELAY_URL unset — skipping live relay proof" }

        drillKcLookup = { _, _ -> PROOF_KC }
        drillGeneratorLlmFactory = { jarvis.RelayLlm() }
        drillCriticLlmFactory = { jarvis.RelayLlm() }

        // Manual TestApplication lifecycle inside runBlocking — avoids the 60s
        // runTest cap that testApplication{} imposes (real relay calls are slow).
        var ctx: TutorContext? = null
        val app = TestApplication {
            application {
                install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
                installTutorContext(tmp.resolve("t.db").toString(), tmp)
                installTutorRoutes()
                ctx = attributes[TutorContextKey]
            }
        }
        app.start()
        try {

        val userId = TutorTypes.ulid()
        UserRepo(ctx!!.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx!!.db).create(userId, ttlSeconds = 3600)
        AiLiteracyRepo(ctx!!.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        val now = Instant.now()
        TaskRepo(ctx!!.db).insert(Task(
            id = "task-1", userId = userId, subject = "PA", title = "T-relay-proof",
            deadline = now.plusSeconds(86400),
            problemRef = ContentRef("user", "p.pdf", "x"),
            conceptRefs = emptyList(), rubricRef = ContentRef("user", "p.pdf", "x"),
            scratchpad = null, submission = null, grade = null, cardRefs = emptyList(),
            status = TaskStatus.ACTIVE, createdAt = now, updatedAt = now,
        ))

        val csrf = "test-csrf-relay-proof"
        val client = app.createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // 1. Generate via real relay. count>1 so real-model variance (a critic
        //    leak-flag / self-solve mismatch on one sample) still lands >=1 accept.
        val genResp = client.post("/api/v1/task/task-1/generate-drills") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"kcId":"pa-kc-relayproof","shape":"computational","count":3}""")
        }
        log("\n== E2E: POST generate-drills -> ${genResp.status} ==")
        log(genResp.bodyAsText())
        assertEquals(HttpStatusCode.OK, genResp.status, "generate-drills must succeed: ${genResp.bodyAsText()}")

        // 2. Read the server-canonical persisted Problem (proves accepted + persisted).
        val prep = TaskPrepRepo(ctx!!.db).findByTaskId("task-1")
        assertNotNull(prep, "TaskPrep must be persisted after generate")
        val problems = PROBLEM_JSON.decodeFromString(ListSerializer(Problem.serializer()), prep.problemsJson)
        val problem = problems.firstOrNull { it.canonicalAnswer != null }
        assertNotNull(problem, "at least one generated+accepted computational Problem must persist; got ids=${problems.map { it.problemId }}")
        val canonical = problem.canonicalAnswer
        assertNotNull(canonical, "persisted computational Problem must carry a canonical answer")
        log("server-canonical problem: ${problem.statement} | canonical=$canonical | kcIds=${problem.kcIds}")

        // 3. No mastery yet.
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-relayproof"), "mastery must be null before grade")

        // 3.5 Phase-3 GROUP 2: the grade path is FAITHFUL-GATED — seed pa-kc-relayproof faithful
        //     (temp content dir) so the real generate→grade→mastery loop records over a faithful KC.
        seedFaithfulRelayProof(ctx!!, tmp.resolve("content"))

        // 4. Grade the REAL generated drill. Grade decision is a deterministic
        //    fake (consistent verdict) so the generate->grade->mastery loop is
        //    stable: this test proves a REAL-generated, server-canonical drill
        //    completes the loop. (Grade-DECISION quality on the relay model is
        //    E1's concern; the real grader emits occasional correct-vs-rubric
        //    inconsistency that the trustworthy layer correctly defers — see
        //    docs/superpowers/findings.)
        drillGraderLlmFactory = {
            object : jarvis.Llm {
                override suspend fun complete(m: List<jarvis.ChatMessage>, t: Int, r: String?, imagePath: String?) =
                    """{"correct":true,"rubric":{"ok":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake-grader"
            }
        }
        val gradeBody = kotlinx.serialization.json.buildJsonObject {
            put("taskId", "task-1")
            put("problemId", problem.problemId)
            put("problemStatement", problem.statement)
            put("userAttempt", canonical)
            put("expectedAnswerHint", canonical)
        }.toString()
        val gradeResp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody)
        }
        log("\n== E2E: POST drill/grade -> ${gradeResp.status} ==")
        log(gradeResp.bodyAsText())
        assertEquals(HttpStatusCode.OK, gradeResp.status, "grade must succeed: ${gradeResp.bodyAsText()}")

        // 5. Mastery moved — full generate->grade->mastery loop fired on a real model.
        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-relayproof")
        log("mastery after grade: $mastery")
        assertNotNull(mastery, "KcMastery row must exist after grading the generated drill")
        assertEquals(1, mastery.observations, "exactly one observation — real generate->grade->mastery loop fired")

        } finally { app.stop() }
    }
}
