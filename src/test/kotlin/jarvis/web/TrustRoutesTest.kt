package jarvis.web

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.tutor.AuditOutcome
import jarvis.tutor.CardStatus
import jarvis.tutor.FsrsCardRepo
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.FsrsSource
import jarvis.tutor.FsrsState
import jarvis.tutor.KcVerificationStatusTable
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
import jarvis.tutor.verify.LegFamily
import jarvis.tutor.verify.TwoFamilyDeriver
import jarvis.tutor.verify.VerificationRunner
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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

class TrustRoutesTest {

    @AfterTest
    fun resetSeam() {
        verifyRunnerFactory = { db, repo -> VerifyAdmin.liveRunnerFor(db, repo) }
        System.clearProperty("JARVIS_CONTENT_DIR")
    }

    /** A faithful PA KC with a span-bearing source so claimsFor emits a cited DEFINITION claim. */
    private fun seedContent(content: Path, status: String = "faithful") {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        // span 0..9 over the source quote "Algorithm".
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "verification_status: \"$status\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    /** Install ContentNegotiation + the tutor routes against a pre-built db (mirrors FsrsGradeRouteTest). */
    private fun Application.installTrust(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installTutorRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, FsrsCardsTable,
                ReportWrongTable, KcVerificationStatusTable, VerificationAuditTable,
            )
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database, scope: UserScope): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, scope.name.lowercase(), scope, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    // ── GET /verify/{kcId}/status ──────────────────────────────────────────────────────────────

    @Test
    fun `verify status reply shape — faithful KC carries cited claims + FAITHFUL floor + lecture badge`() = testApplication {
        val dir = Files.createTempDirectory("trust-status")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // F4-serve: faithful is now B8-backed only — the KC must carry an audited B8 row to serve
        // faithful (the YAML seed alone serves unverified).
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-005"
                it[status] = VerificationStatus.faithful.name
                it[updatedAt] = Instant.now()
            }
        }
        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-005/status") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("\"honest_floor\":\"FAITHFUL_TO_SOURCE\""), body)
        assertTrue(body.contains("matches your lecture"), body)
        // The cited claim carries its resolved SourceRef (doc + quote).
        assertTrue(body.contains("pa-lecture-01"), body)
        assertTrue(body.contains("Algorithm"), body)
    }

    @Test
    fun `verify status badge NEVER says verified correct — even for a faithful KC`() = testApplication {
        val dir = Files.createTempDirectory("trust-nobadge")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertFalse(body.contains("verified correct", ignoreCase = true), body)
        assertFalse(body.contains("\"correct\"", ignoreCase = true), body)
    }

    @Test
    fun `verify status honest_floor caps an unverified KC — badge unverified + UNVERIFIED floor`() = testApplication {
        val dir = Files.createTempDirectory("trust-cap")
        val content = dir.resolve("content"); seedContent(content, status = "unverified")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
    }

    @Test
    fun `verify status degrades to unverified for an unknown kcId (H15) — no claims, no 500`() = testApplication {
        val dir = Files.createTempDirectory("trust-h15")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-999/status") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertTrue(body.contains("\"claims\":[]"), body)
    }

    @Test
    fun `F4-serve - a faithful YAML seed with NO B8 row serves unverified, never the seed faithful`() = testApplication {
        // F4-serve: `faithful` is authorable as a YAML seed, but with ZERO audit legs run (no B8
        // row) the served status must be `unverified` — NOT the authored seed. resolveStatus must
        // NOT fall back to the YAML `verification_status: faithful`.
        val dir = Files.createTempDirectory("trust-f4-serve")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // NB: NO kc_verification_status (B8) row inserted — the KC was never audited.
        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-005/status") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"unverified\""), body)
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
        assertFalse(body.contains("\"verification_status\":\"faithful\""), body)
    }

    @Test
    fun `F4-serve - a B8 row IS still honored - audited faithful serves faithful`() = testApplication {
        // The flip side: once the KC has actually been audited (a B8 row exists), the B8 status
        // IS the served truth. This proves F4-serve only kills the YAML-seed fallback, not the
        // legitimate B8-backed faithful.
        val dir = Files.createTempDirectory("trust-f4-b8")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-005"
                it[status] = VerificationStatus.faithful.name
                it[updatedAt] = Instant.now()
            }
        }
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("matches your lecture"), body)
    }

    @Test
    fun `verify status requires auth — 401 without a session`() = testApplication {
        val dir = Files.createTempDirectory("trust-noauth")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-005/status")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    // ── POST /admin/verify/{kcId} (owner-gated) ────────────────────────────────────────────────

    /** Fake LLM that always returns the given verdict word. */
    private class FixedLlm(private val word: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            word to "fake-model"
    }

    @Test
    fun `admin verify is owner-gated — 401 unauth, 403 friend`() = testApplication {
        val dir = Files.createTempDirectory("trust-admin-gate")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, friendSid) = seedUser(db, UserScope.FRIEND)
        application { installTrust(db, dir) }
        // unauthenticated
        val unauth = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)
        // authenticated but FRIEND scope
        val friend = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "jarvis_session=$friendSid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.Forbidden, friend.status)
    }

    @Test
    fun `admin verify runs the offline audit for an OWNER and writes the B8 status`() = testApplication {
        val dir = Files.createTempDirectory("trust-admin-run")
        val content = dir.resolve("content"); seedContent(content, status = "pending")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, ownerSid) = seedUser(db, UserScope.OWNER)
        // Seed the KC at pending so the runner can transition it (curate Stage-9 normally does this).
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-005"
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = Instant.now()
            }
        }
        // Inject a fake two-family runner (no network): both families SUPPORTED + fake source.
        verifyRunnerFactory = { d, _ ->
            VerificationRunner(
                db = d,
                legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FixedLlm("SUPPORTED")),
                legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FixedLlm("SUPPORTED")),
                nonLlmLegFor = { jarvis.tutor.verify.nonLlmLegFor(it) },
                rawSourceFor = { "Algorithm is a finite sequence." },
            )
        }
        application { installTrust(db, dir) }
        val r = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "jarvis_session=$ownerSid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"kcId\":\"pa-kc-005\""), body)
        // An audit row was written.
        val auditCount = transaction(db) { VerificationAuditTable.selectAll().count() }
        assertTrue(auditCount >= 1, "expected >=1 verification_audit row, got $auditCount")
    }

    // ── POST /fsrs/{id}/report-wrong ───────────────────────────────────────────────────────────

    private fun seedFaithfulCard(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String): String {
        val cardId = TutorTypes.ulid()
        val now = Instant.now()
        FsrsCardRepo(db).insert(
            TutorCard(
                id = cardId, userId = userId,
                source = FsrsSource.RUBRIC_CRITERION, sourceRef = kcId,
                front = "front", back = "back",
                state = FsrsState(5.0, 1.0, 0.9, now, now, 0),
                kcId = kcId, status = CardStatus.ACTIVE,
            ),
        )
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = VerificationStatus.faithful.name
                it[updatedAt] = now
            }
        }
        return cardId
    }

    @Test
    fun `report-wrong writes report_wrong OPEN, pauses the card, and flips faithful to pending`() = testApplication {
        val dir = Files.createTempDirectory("trust-report")
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db, UserScope.FRIEND)
        val cardId = seedFaithfulCard(db, uid, "pa-kc-005")
        application { installTrust(db, dir) }
        val r = client.post("/api/v1/fsrs/$cardId/report-wrong") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"gradeAttemptRaw":"the grader marked me wrong but my answer is right"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"cardPaused\":true"), body)
        assertTrue(body.contains("\"newStatus\":\"pending\""), body)

        // report_wrong row written with resolution=OPEN
        val (reportCount, resolution) = transaction(db) {
            val rows = ReportWrongTable.selectAll().where { ReportWrongTable.cardId eq cardId }.toList()
            rows.size to rows.firstOrNull()?.get(ReportWrongTable.resolution)
        }
        assertEquals(1, reportCount)
        assertEquals("OPEN", resolution)

        // card paused
        val cardStatus = transaction(db) {
            FsrsCardsTable.selectAll().where { FsrsCardsTable.id eq cardId }
                .single()[FsrsCardsTable.status]
        }
        assertEquals(CardStatus.PAUSED.name, cardStatus)

        // KC flipped faithful -> pending
        val kcStatus = transaction(db) {
            KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq "pa-kc-005" }
                .single()[KcVerificationStatusTable.status]
        }
        assertEquals(VerificationStatus.pending.name, kcStatus)
    }

    @Test
    fun `report-wrong 404s for an unknown card`() = testApplication {
        val dir = Files.createTempDirectory("trust-report-404")
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        application { installTrust(db, dir) }
        val r = client.post("/api/v1/fsrs/NONEXISTENT/report-wrong") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.NotFound, r.status)
    }
}
