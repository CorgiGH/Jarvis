package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import jarvis.tutor.AttemptsTable
import jarvis.tutor.ExamDatesTable
import jarvis.tutor.KcMasteryRepo
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.Phase
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionSummariesTable
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase-3 Area C, GROUP 4 — session/close · placement · exam-dates.
 *
 * Class-killers:
 *  - session/close: deltas are SERVER-computed from the session window (client sends NONE);
 *    per-user scoped; empty session degrades cleanly; writes a session_summaries row.
 *  - placement: questions returns 1/cluster from the corpus; submit WRITES entry_phase per placed KC;
 *    entry_phase write is per-user AND NEVER regresses an already-higher phase (monotonicity class-killer);
 *    malformed answers handled.
 *  - exam-dates: PUT then GET round-trips; per-user scoped (one user cannot read another's date).
 */
class SessionPlacementExamRoutesTest {

    @AfterTest
    fun resetSeam() {
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    // ── harness (mirrors QueueMasteryCalibrationRoutesTest) ────────────────────────────────────────

    private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installTutorRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, KcMasteryTable,
                AttemptsTable, SessionSummariesTable, ExamDatesTable,
            )
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database, scope: UserScope = UserScope.FRIEND): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, scope.name.lowercase(), scope, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    /** A PA subject with 2 KCs across 2 clusters (cluster F = pa-kc-001; cluster E = pa-kc-002). */
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"Notatia O\"\nname_en: \"Big-O notation\"\n" +
                "cluster: \"Fundamentele algoritmilor\"\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nstem_template: \"Define Big-O.\"\nverification_status: \"faithful\"\n",
        )
        pa.resolve("kcs/pa-kc-002.yaml").writeText(
            "id: pa-kc-002\nsubject: PA\nname_ro: \"Master theorem\"\nname_en: \"Master theorem\"\n" +
                "cluster: \"Eficienta algoritmilor\"\nbloom_level: apply\ndifficulty: 2\ntime_minutes: 15\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n",
        )
    }

    private fun seedAttempt(
        db: org.jetbrains.exposed.sql.Database,
        userId: String,
        kcId: String,
        correct: Boolean,
        gradedAt: Instant,
    ) = transaction(db) {
        AttemptsTable.insert {
            it[id] = TutorTypes.ulid()
            it[AttemptsTable.userId] = userId
            it[AttemptsTable.kcId] = kcId
            it[taskId] = "task-1"
            it[problemId] = "prob-1"
            it[phase] = Phase.practice.name
            it[AttemptsTable.correct] = correct
            it[score] = if (correct) 1.0 else 0.0
            it[scaffoldLevel] = 0
            it[isFarTransfer] = false
            it[recorded] = true
            it[AttemptsTable.gradedAt] = gradedAt
        }
    }

    // ══════════════════════════════ ROUTE 1 — POST /api/v1/session/close ══════════════════════════

    @Test
    fun `session close requires auth — 401 without a session`() = testApplication {
        val dir = Files.createTempDirectory("sc-noauth")
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/session/close") {
            header("Cookie", "csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"reviewed_card_ids":[],"session_start_at":"${Instant.now().minus(1, ChronoUnit.HOURS)}"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `session close computes deltas SERVER-side from the window and writes a summary row`() = testApplication {
        val dir = Files.createTempDirectory("sc-deltas")
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        val start = Instant.now().minus(1, ChronoUnit.HOURS)
        // Three in-window correct attempts on pa-kc-001 ⇒ recordIn-style EWMA climbs; phase advances.
        // We drive mastery via the repo so the server can read kc_mastery; the deltas are recomputed
        // server-side from the in-window `attempts`, NOT sent by the client.
        repo(db).record(uid, "pa-kc-001", 1.0)
        repo(db).record(uid, "pa-kc-001", 1.0)
        repo(db).record(uid, "pa-kc-001", 1.0)
        seedAttempt(db, uid, "pa-kc-001", correct = true, gradedAt = start.plus(5, ChronoUnit.MINUTES))
        seedAttempt(db, uid, "pa-kc-001", correct = true, gradedAt = start.plus(6, ChronoUnit.MINUTES))
        seedAttempt(db, uid, "pa-kc-001", correct = true, gradedAt = start.plus(7, ChronoUnit.MINUTES))
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/session/close") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"reviewed_card_ids":["c1","c2"],"session_start_at":"$start"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // server-computed reply shape (L1): session_id, narrative, cards_reviewed, kcs_moved_to_mastered, mastery_deltas
        assertTrue(body.contains("\"session_id\""), body)
        assertTrue(body.contains("\"cards_reviewed\":2"), body)          // == reviewed_card_ids.size
        assertTrue(body.contains("\"mastery_deltas\""), body)
        assertTrue(body.contains("pa-kc-001"), body)                     // the in-window KC appears
        assertTrue(body.contains("\"kcs_moved_to_mastered\""), body)
        // a summary row was persisted for this user.
        val rows = transaction(db) {
            SessionSummariesTable.selectAll().where { SessionSummariesTable.userId eq uid }.count()
        }
        assertEquals(1L, rows)
    }

    @Test
    fun `session close on an empty window degrades cleanly — empty deltas, no throw`() = testApplication {
        val dir = Files.createTempDirectory("sc-empty")
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/session/close") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"reviewed_card_ids":[],"session_start_at":"${Instant.now().minus(1, ChronoUnit.HOURS)}"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"cards_reviewed\":0"), body)
        assertTrue(body.contains("\"mastery_deltas\":[]"), body)
        assertTrue(body.contains("\"kcs_moved_to_mastered\":[]"), body)
    }

    @Test
    fun `session close is per-user scoped — another user's in-window attempts never leak into the deltas`() = testApplication {
        val dir = Files.createTempDirectory("sc-scope")
        val db = freshDb(dir)
        val (uidA, sidA) = seedUser(db)
        val (uidB, _) = seedUser(db)
        val start = Instant.now().minus(1, ChronoUnit.HOURS)
        // user B has in-window attempts on pa-kc-002 — they must NOT appear in user A's close.
        seedAttempt(db, uidB, "pa-kc-002", correct = true, gradedAt = start.plus(5, ChronoUnit.MINUTES))
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/session/close") {
            header("Cookie", "jarvis_session=$sidA; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"reviewed_card_ids":[],"session_start_at":"$start"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertFalse(body.contains("pa-kc-002"), "user B's KC leaked into user A's session close: $body")
    }

    // ══════════════════════════════ ROUTE 2 — placement ══════════════════════════════

    @Test
    fun `placement questions returns one question per cluster from the corpus`() = testApplication {
        val dir = Files.createTempDirectory("pl-q")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/placement/questions") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // 2 clusters in the seeded corpus ⇒ exactly 2 questions (1/cluster). question_id, cluster, kc_id, stem.
        assertTrue(body.contains("\"questions\""), body)
        assertTrue(body.contains("\"cluster\""), body)
        assertTrue(body.contains("\"kc_id\""), body)
        assertTrue(body.contains("\"stem\""), body)
        // Exactly one question per distinct cluster: the two distinct clusters each appear once.
        val clusterCount = Regex("\"question_id\"").findAll(body).count()
        assertEquals(2, clusterCount, body)
    }

    @Test
    fun `placement submit writes entry_phase per placed KC, per-user`() = testApplication {
        val dir = Files.createTempDirectory("pl-sub")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        // Get the questions to learn the question_ids.
        val q = client.get("/api/v1/placement/questions") { header("Cookie", "jarvis_session=$sid") }.bodyAsText()
        val qid1 = Regex("\"question_id\":\"([^\"]+)\"").find(q)!!.groupValues[1]
        // A correct response on the first question ⇒ that KC gets placed with an entry_phase > intro.
        val r = client.post("/api/v1/placement/submit") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"answers":[{"question_id":"$qid1","response":"correct answer demonstrating mastery"}]}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"placed\""), body)
        assertTrue(body.contains("\"entry_phase\""), body)
        assertTrue(body.contains("\"started_subject\""), body)
        // entry_phase row persisted for THIS user only.
        val placedKc = Regex("\"kc_id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val ep = transaction(db) {
            KcMasteryTable.selectAll()
                .where { (KcMasteryTable.userId eq uid) and (KcMasteryTable.kcId eq placedKc) }
                .map { it[KcMasteryTable.entryPhase] }
                .singleOrNull()
        }
        assertTrue(ep != null, "entry_phase not written for $placedKc")
    }

    @Test
    fun `placement submit derives entry_phase from answer strength — a strong answer seeds practice not intro`() = testApplication {
        // P1-3(a): the old code passed observations=1 to PhaseModel.transition, which ALWAYS returns
        // intro (obs < PRACTICE_OBS_MIN), so placement was a no-op. A strong (non-blank) placement
        // answer MUST seed entry_phase=practice; a blank answer stays intro.
        val dir = Files.createTempDirectory("pl-strength")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val q = client.get("/api/v1/placement/questions") { header("Cookie", "jarvis_session=$sid") }.bodyAsText()
        val qids = Regex("\"question_id\":\"([^\"]+)\"").findAll(q).map { it.groupValues[1] }.toList()
        val strongQid = qids[0]
        val blankQid = qids[1]
        val r = client.post("/api/v1/placement/submit") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody(
                """{"answers":[{"question_id":"$strongQid","response":"a substantive correct answer"},""" +
                    """{"question_id":"$blankQid","response":""}]}""",
            )
        }
        assertEquals(HttpStatusCode.OK, r.status)
        // Map each placed question back to its KC.
        val strongKc = strongQid.removePrefix("plq-")
        val blankKc = blankQid.removePrefix("plq-")
        val phases = transaction(db) {
            KcMasteryTable.selectAll()
                .where { KcMasteryTable.userId eq uid }
                .associate { it[KcMasteryTable.kcId] to it[KcMasteryTable.entryPhase] }
        }
        assertEquals(
            Phase.practice.name, phases[strongKc],
            "a strong placement answer must seed entry_phase=practice (was a no-op intro): $phases",
        )
        assertEquals(
            Phase.intro.name, phases[blankKc],
            "a blank placement answer must stay entry_phase=intro: $phases",
        )
    }

    @Test
    fun `placement submit NEVER regresses an already-higher entry_phase (monotonicity class-killer)`() = testApplication {
        val dir = Files.createTempDirectory("pl-mono")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val q = client.get("/api/v1/placement/questions") { header("Cookie", "jarvis_session=$sid") }.bodyAsText()
        val qid1 = Regex("\"question_id\":\"([^\"]+)\"").find(q)!!.groupValues[1]
        val kc1 = Regex("\\{\"question_id\":\"$qid1\",\"cluster\":\"[^\"]*\",\"kc_id\":\"([^\"]+)\"").find(q)?.groupValues?.get(1)
            ?: Regex("\"kc_id\":\"([^\"]+)\"").find(q)!!.groupValues[1]
        // Pre-seed a HIGHER entry_phase (mastered) for kc1, this user.
        transaction(db) {
            KcMasteryTable.insert {
                it[KcMasteryTable.userId] = uid
                it[KcMasteryTable.kcId] = kc1
                it[ewmaScore] = 0.9
                it[observations] = 5
                it[lastGradedAt] = Instant.now()
                it[phase] = Phase.mastered.name
                it[entryPhase] = Phase.mastered.name
            }
        }
        // A weak/incorrect placement answer would compute a LOW entry_phase (intro) — must NOT overwrite mastered.
        client.post("/api/v1/placement/submit") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"answers":[{"question_id":"$qid1","response":""}]}""")
        }
        val ep = transaction(db) {
            KcMasteryTable.selectAll()
                .where { (KcMasteryTable.userId eq uid) and (KcMasteryTable.kcId eq kc1) }
                .map { it[KcMasteryTable.entryPhase] }
                .single()
        }
        assertEquals(Phase.mastered.name, ep, "placement regressed an already-higher entry_phase")
    }

    @Test
    fun `placement submit tolerates malformed answers — no throw`() = testApplication {
        val dir = Files.createTempDirectory("pl-bad")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/placement/submit") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"answers":[{"question_id":"does-not-exist","response":"x"}]}""")
        }
        // unknown question ids are dropped; reply still 200 with an empty placed list.
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"placed\":[]"), r.bodyAsText())
    }

    // ══════════════════════════════ ROUTE 3 — exam-dates ══════════════════════════════

    @Test
    fun `exam-dates PUT then GET round-trips for the same user`() = testApplication {
        val dir = Files.createTempDirectory("ed-rt")
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val examAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val put = client.put("/api/v1/me/exam-dates") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","start_at":"$examAt"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val get = client.get("/api/v1/me/exam-dates") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, get.status)
        val body = get.bodyAsText()
        assertTrue(body.contains("PA"), body)
        assertTrue(body.contains(examAt.toString()), body)
    }

    @Test
    fun `exam-dates PUT is idempotent per subject — re-PUT updates, never duplicates`() = testApplication {
        val dir = Files.createTempDirectory("ed-idem")
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val d1 = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val d2 = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        for (d in listOf(d1, d2)) {
            client.put("/api/v1/me/exam-dates") {
                header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
                contentType(ContentType.Application.Json)
                setBody("""{"subject":"PA","start_at":"$d"}""")
            }
        }
        val rows = transaction(db) {
            ExamDatesTable.selectAll()
                .where { (ExamDatesTable.userId eq uid) and (ExamDatesTable.subject eq "PA") }
                .count()
        }
        assertEquals(1L, rows, "re-PUT duplicated the exam date instead of updating")
    }

    @Test
    fun `exam-dates is per-user scoped — one user cannot read another's date`() = testApplication {
        val dir = Files.createTempDirectory("ed-scope")
        val db = freshDb(dir)
        val (_, sidA) = seedUser(db)
        val (_, sidB) = seedUser(db)
        application { installRoutes(db, dir) }
        val examAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        client.put("/api/v1/me/exam-dates") {
            header("Cookie", "jarvis_session=$sidA; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"subject":"PA","start_at":"$examAt"}""")
        }
        // user B reads — must NOT see user A's date.
        val get = client.get("/api/v1/me/exam-dates") { header("Cookie", "jarvis_session=$sidB") }
        assertEquals(HttpStatusCode.OK, get.status)
        val body = get.bodyAsText()
        assertFalse(body.contains(examAt.toString()), "user A's exam date leaked to user B: $body")
    }

    private fun repo(db: org.jetbrains.exposed.sql.Database) = KcMasteryRepo(db)
}
