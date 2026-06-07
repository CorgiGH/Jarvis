package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.AttemptsTable
import jarvis.tutor.CardStatus
import jarvis.tutor.FsrsCardRepo
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.FsrsSource
import jarvis.tutor.FsrsState
import jarvis.tutor.KcMasteryRepo
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.Phase
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorCard
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import jarvis.tutor.VerificationAuditTable
import jarvis.tutor.VerificationStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
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
 * Phase-3 Area C, GROUP 3 — the READ routes (queue/today, mastery, calibration).
 *
 * Class-killers:
 *  - queue/today: due cards appear; next-KC respects prereqs; verification_status carried per item;
 *    non-faithful / OPEN-dispute KCs OMITTED (route-table line 109 + QueueItem §C + D-RF2); empty ⇒ empty list.
 *  - mastery: band shape (ewma_score + observations, NO history series); bilingual subject name; degrade clean.
 *  - calibration: bucket aggregation (high-confidence-but-wrong lands right); 0-attempts degrades; bucket math asserted.
 */
class QueueMasteryCalibrationRoutesTest {

    @AfterTest
    fun resetSeam() {
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    // ── harness (mirrors TrustRoutesTest) ────────────────────────────────────────────────────────

    private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installTutorRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, FsrsCardsTable, KcMasteryTable,
                AttemptsTable, ReportWrongTable, KcVerificationStatusTable, VerificationAuditTable,
            )
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database, scope: UserScope = UserScope.FRIEND): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, scope.name.lowercase(), scope, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    /**
     * A PA subject with TWO KCs: pa-kc-001 (a root) and pa-kc-002 (gated behind pa-kc-001 via edges.yaml).
     * Both authored faithful so the only thing that gates them out is the runtime B8 status.
     */
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
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n",
        )
        pa.resolve("kcs/pa-kc-002.yaml").writeText(
            "id: pa-kc-002\nsubject: PA\nname_ro: \"Master theorem\"\nname_en: \"Master theorem\"\n" +
                "cluster: f\nbloom_level: apply\ndifficulty: 2\ntime_minutes: 15\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n",
        )
        // pa-kc-002 depends on pa-kc-001 (the prereq gate).
        pa.resolve("edges.yaml").writeText(
            "subject: PA\nedges:\n  - kc: pa-kc-002\n    prereq: pa-kc-001\n    rationale: needs Big-O first\n",
        )
    }

    /** Stamp a runtime trust status for a KC (B8 table). Content hash absent ⇒ the serve gate
     *  needs the KC-content match; tests that want faithful-to-survive supply the matching hash. */
    private fun seedB8(
        db: org.jetbrains.exposed.sql.Database,
        content: Path,
        kcId: String,
        status: VerificationStatus,
    ) = transaction(db) {
        val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == kcId }
        KcVerificationStatusTable.insert {
            it[KcVerificationStatusTable.kcId] = kcId
            it[KcVerificationStatusTable.status] = status.name
            it[contentHash] = if (status == VerificationStatus.faithful)
                jarvis.content.ContentReconcile.kcContentHash(kc) else null
            it[sourceSpanHash] = if (status == VerificationStatus.faithful) "seedspanhash" else null
            it[updatedAt] = Instant.now()
        }
    }

    private fun seedAttempt(
        db: org.jetbrains.exposed.sql.Database,
        userId: String,
        kcId: String,
        studentConfidence: String?,
        correct: Boolean,
        score: Double = if (correct) 1.0 else 0.0,
    ) = transaction(db) {
        AttemptsTable.insert {
            it[id] = TutorTypes.ulid()
            it[AttemptsTable.userId] = userId
            it[AttemptsTable.kcId] = kcId
            it[taskId] = "task-1"
            it[problemId] = "prob-1"
            it[phase] = Phase.practice.name
            it[AttemptsTable.studentConfidence] = studentConfidence
            it[AttemptsTable.correct] = correct
            it[AttemptsTable.score] = score
            it[scaffoldLevel] = 0
            it[isFarTransfer] = false
            it[recorded] = true
            it[gradedAt] = Instant.now()
        }
    }

    private fun seedDueCard(
        db: org.jetbrains.exposed.sql.Database,
        userId: String,
        kcId: String,
        dueAt: Instant,
    ): String {
        val cardId = TutorTypes.ulid()
        FsrsCardRepo(db).insert(
            TutorCard(
                id = cardId, userId = userId,
                source = FsrsSource.RUBRIC_CRITERION, sourceRef = kcId,
                front = "front", back = "back",
                state = FsrsState(5.0, 1.0, 0.9, dueAt, dueAt, 0),
                kcId = kcId, status = CardStatus.ACTIVE,
            ),
        )
        return cardId
    }

    // ══════════════════════════════ ROUTE 1 — GET /api/v1/queue/today ══════════════════════════════

    @Test
    fun `queue today requires auth — 401 without a session`() = testApplication {
        val dir = Files.createTempDirectory("queue-noauth")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/queue/today").status)
    }

    @Test
    fun `queue today empty corpus degrades to an empty item list — no throw`() = testApplication {
        val dir = Files.createTempDirectory("queue-empty")
        val content = dir.resolve("content")
        content.createDirectories()
        content.resolve("subjects.yaml").writeText("version: 1\nsubjects: []\n")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/queue/today") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"items\":[]"), body)
        assertTrue(body.contains("\"total_due\":0"), body)
        assertTrue(body.contains("\"day\":"), body)
    }

    @Test
    fun `queue today surfaces an FSRS-due review card AND a new faithful KC, each carrying verification_status`() = testApplication {
        val dir = Files.createTempDirectory("queue-due")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        // pa-kc-001 is the root, faithful at runtime ⇒ eligible for new-KC selection.
        seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        // A due review card for pa-kc-001.
        seedDueCard(db, uid, "pa-kc-001", Instant.now().minus(1, ChronoUnit.DAYS))
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/queue/today") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // The faithful KC is surfaced and carries its verification_status.
        assertTrue(body.contains("pa-kc-001"), body)
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        // total_due counts the due review card.
        assertTrue(body.contains("\"total_due\":1"), body)
    }

    @Test
    fun `queue today respects prereqs — a gated KC is NOT offered until its prereq is mastered`() = testApplication {
        // pa-kc-002 depends on pa-kc-001. Neither mastered ⇒ pa-kc-002 (the gated KC) must NOT appear
        // as a new-KC pick; only the unlocked root pa-kc-001 may be offered.
        val dir = Files.createTempDirectory("queue-prereq")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        seedB8(db, content, "pa-kc-002", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/queue/today") { header("Cookie", "jarvis_session=$sid") }
        val body = r.bodyAsText()
        // The gated KC (pa-kc-002) is locked behind pa-kc-001 (not mastered) ⇒ never the new-KC pick.
        assertFalse(body.contains("pa-kc-002"), "gated KC must not be offered before its prereq: $body")
    }

    @Test
    fun `queue today OMITS a non-faithful KC — never surfaces it (route-table line 109, QueueItem C)`() = testApplication {
        // pa-kc-001 is the ONLY root, but its runtime status is uncertain (not faithful) ⇒ it must be
        // EXCLUDED from the queue entirely (never surfaced with a degraded badge — the lock OMITS it).
        val dir = Files.createTempDirectory("queue-nonfaithful")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/queue/today") { header("Cookie", "jarvis_session=$sid") }
        val body = r.bodyAsText()
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(body.contains("pa-kc-001"), "a non-faithful KC must be OMITTED from the queue: $body")
        // Nothing surfaced ⇒ empty item list.
        assertTrue(body.contains("\"items\":[]"), body)
    }

    @Test
    fun `queue today OMITS a KC with an OPEN report_wrong dispute (D-RF2 always-on serve refusal)`() = testApplication {
        // D-RF2: the Phase-3 queue/today filter consults the shared hasOpenReportWrong. A faithful KC
        // with an OPEN dispute must NOT be offered for new-KC study (the serve refusal is always-on).
        val dir = Files.createTempDirectory("queue-dispute")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        transaction(db) {
            ReportWrongTable.insert {
                it[id] = TutorTypes.ulid()
                it[ReportWrongTable.userId] = uid
                it[ReportWrongTable.kcId] = "pa-kc-001"
                it[cardId] = null
                it[gradeAttemptRaw] = "disputed"
                it[reportedAt] = Instant.now()
                it[resolution] = "OPEN"
            }
        }
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/queue/today") { header("Cookie", "jarvis_session=$sid") }
        val body = r.bodyAsText()
        assertFalse(body.contains("pa-kc-001"), "an OPEN-dispute KC must be omitted from the queue (D-RF2): $body")
    }

    // ══════════════════════════════ ROUTE 2 — GET /api/v1/mastery ══════════════════════════════════

    @Test
    fun `mastery requires auth — 401 without a session`() = testApplication {
        val dir = Files.createTempDirectory("mastery-noauth")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/mastery").status)
    }

    @Test
    fun `mastery returns the BAND shape — ewma_score plus observations, NO history series, bilingual subject name`() = testApplication {
        val dir = Files.createTempDirectory("mastery-band")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        // Grade pa-kc-001 a few times so it has a mastery row with observations.
        val repo = KcMasteryRepo(db)
        repo.record(uid, "pa-kc-001", 1.0)
        repo.record(uid, "pa-kc-001", 1.0)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/mastery") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        // Bilingual subject name (NOT hardcoded — straight from subjects.yaml).
        assertTrue(body.contains("\"subject_id\":\"PA\""), body)
        assertTrue(body.contains("\"subject_name_ro\":\"Proiectarea Algoritmilor\""), body)
        assertTrue(body.contains("\"subject_name_en\":\"Algorithm Design\""), body)
        // Band shape: scalar ewma_score + observations, per KC.
        assertTrue(body.contains("\"kc_id\":\"pa-kc-001\""), body)
        assertTrue(body.contains("\"ewma_score\":"), body)
        assertTrue(body.contains("\"observations\":2"), body)
        assertTrue(body.contains("\"verification_status\":"), body)
        assertTrue(body.contains("\"last_graded_at\":"), body)
        // NO time-series array (band, not series): there is no `history`/`series`/`ewma_history` key.
        assertFalse(body.contains("history"), "band shape must NOT carry a history series: $body")
        assertFalse(body.contains("\"series\""), "band shape must NOT carry a series array: $body")
    }

    @Test
    fun `mastery degrades cleanly for a subject with no mastery rows — last_graded_at null at 0 observations`() = testApplication {
        // A subject whose KCs have never been graded ⇒ the KC still appears (band shape) but with
        // 0 observations + null last_graded_at + a cold ewma — no throw, no missing subject.
        val dir = Files.createTempDirectory("mastery-cold")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/mastery") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"subject_id\":\"PA\""), body)
        assertTrue(body.contains("\"observations\":0"), body)
        assertTrue(body.contains("\"last_graded_at\":null"), body)
    }

    // ══════════════════════════════ ROUTE 3 — GET /api/v1/calibration ══════════════════════════════

    @Test
    fun `calibration requires auth — 401 without a session`() = testApplication {
        val dir = Files.createTempDirectory("calib-noauth")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/calibration").status)
    }

    @Test
    fun `calibration 0 attempts degrades to an empty bucket list — no throw`() = testApplication {
        val dir = Files.createTempDirectory("calib-empty")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/calibration") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"buckets\":[]"), body)
        assertTrue(body.contains("\"total_attempts\":0"), body)
    }

    @Test
    fun `calibration aggregates per-confidence buckets — high-confidence-but-wrong lands in the DEFINITELY bucket with the right accuracy`() = testApplication {
        // The class-killer on the aggregation: bucket by student_confidence, count attempts + correct,
        // accuracy = correct/attempts. Seed a confident-but-wrong attempt (DEFINITELY, correct=false)
        // alongside two confident-correct ones ⇒ DEFINITELY bucket: attempts=3, correct=2, accuracy=2/3.
        val dir = Files.createTempDirectory("calib-agg")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        seedAttempt(db, uid, "pa-kc-001", "DEFINITELY", correct = true)
        seedAttempt(db, uid, "pa-kc-001", "DEFINITELY", correct = true)
        seedAttempt(db, uid, "pa-kc-001", "DEFINITELY", correct = false) // the overconfident miss
        seedAttempt(db, uid, "pa-kc-001", "GUESS", correct = false)
        application { installRoutes(db, dir) }
        val r = client.get("/api/v1/calibration") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val reply = Json { ignoreUnknownKeys = true }
            .decodeFromString(ApiCalibrationReply.serializer(), r.bodyAsText())

        assertEquals(4, reply.total_attempts)
        val byConf = reply.buckets.associateBy { it.student_confidence }
        val definitely = byConf["DEFINITELY"] ?: error("DEFINITELY bucket missing: ${reply.buckets}")
        assertEquals(3, definitely.attempts, "DEFINITELY: 3 attempts")
        assertEquals(2, definitely.correct, "DEFINITELY: 2 correct (the confident miss is wrong)")
        assertEquals(2.0 / 3.0, definitely.accuracy, 1e-9)
        val guess = byConf["GUESS"] ?: error("GUESS bucket missing: ${reply.buckets}")
        assertEquals(1, guess.attempts)
        assertEquals(0, guess.correct)
        assertEquals(0.0, guess.accuracy, 1e-9)
    }

    @Test
    fun `calibration scopes to the calling user — never aggregates another user's attempts`() = testApplication {
        val dir = Files.createTempDirectory("calib-scope")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db)
        val (otherUid, _) = seedUser(db)
        seedAttempt(db, uid, "pa-kc-001", "MAYBE", correct = true)
        // Another user's attempts must NOT leak into this user's calibration.
        seedAttempt(db, otherUid, "pa-kc-001", "MAYBE", correct = false)
        seedAttempt(db, otherUid, "pa-kc-001", "MAYBE", correct = false)
        application { installRoutes(db, dir) }
        val reply = Json { ignoreUnknownKeys = true }.decodeFromString(
            ApiCalibrationReply.serializer(),
            client.get("/api/v1/calibration") { header("Cookie", "jarvis_session=$sid") }.bodyAsText(),
        )
        assertEquals(1, reply.total_attempts, "only the calling user's single MAYBE attempt counts")
        assertEquals(1, reply.buckets.single { it.student_confidence == "MAYBE" }.attempts)
    }
}
