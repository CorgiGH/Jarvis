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
        auditRouteEnabled = {
            System.getenv("JARVIS_AUDIT_ROUTE")?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
        }
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
        // faithful (the YAML seed alone serves unverified). D8: the row's content_hash must match the
        // live content for the faithful to survive the staleness gate.
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-005"
                it[status] = VerificationStatus.faithful.name
                it[contentHash] = seededKcHash(content)
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
                it[contentHash] = seededKcHash(content)
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

    // ── D8 content-hash staleness gate ───────────────────────────────────────────────────────────

    /** The content hash of the seeded pa-kc-005 (matches what the serve gate recomputes). */
    private fun seededKcHash(content: Path): String {
        val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == "pa-kc-005" }
        return jarvis.content.ContentReconcile.kcContentHash(kc)
    }

    private fun seedB8(
        db: org.jetbrains.exposed.sql.Database,
        kcId: String,
        status: VerificationStatus,
        contentHash: String?,
        lectureGrounded: Boolean? = null,
    ) = transaction(db) {
        KcVerificationStatusTable.insert {
            it[KcVerificationStatusTable.kcId] = kcId
            it[KcVerificationStatusTable.status] = status.name
            it[KcVerificationStatusTable.contentHash] = contentHash
            it[KcVerificationStatusTable.lectureGrounded] = lectureGrounded
            it[updatedAt] = Instant.now()
        }
    }

    @Test
    fun `D8 - a faithful B8 row whose content_hash MATCHES current content serves faithful`() = testApplication {
        val dir = Files.createTempDirectory("trust-d8-match")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // Audited faithful AT the current content (matching hash) ⇒ serves faithful.
        seedB8(db, "pa-kc-005", VerificationStatus.faithful, seededKcHash(content))
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("\"honest_floor\":\"FAITHFUL_TO_SOURCE\""), body)
        assertTrue(body.contains("matches your lecture"), body)
    }

    @Test
    fun `D8 - a faithful B8 row whose content_hash MISMATCHES current content serves UNVERIFIED (stale)`() = testApplication {
        // The lecture was edited after the audit: the B8 row still reads `faithful` (audited at C1)
        // but the live content is now C2 ⇒ hash mismatch ⇒ the gate must fall to honest UNVERIFIED
        // (never a lying "matches your lecture" badge over text that was never checked).
        val dir = Files.createTempDirectory("trust-d8-mismatch")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // Audited at a DIFFERENT (stale) content hash than the live content now hashes to.
        seedB8(db, "pa-kc-005", VerificationStatus.faithful, "stale000")
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
        assertFalse(body.contains("matches your lecture"), body)
    }

    @Test
    fun `D8 - a faithful B8 row with a NULL content_hash serves UNVERIFIED (fails closed)`() = testApplication {
        // A legacy/partial row with no recorded content_hash cannot prove it matches the live content
        // ⇒ fail CLOSED to unverified rather than fail-stale to a lying faithful.
        val dir = Files.createTempDirectory("trust-d8-null")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        seedB8(db, "pa-kc-005", VerificationStatus.faithful, null)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
    }

    // ── B5r-2: the "matches your lecture" badge is DECOUPLED from strict `faithful` (D-R5/D-R6) ────

    @Test
    fun `B5r-2 (a) - a lecture_grounded but NOT-faithful KC serves the lecture badge while status stays uncertain`() = testApplication {
        // D-R5: the badge promises lecture-GROUNDING, not the stronger `faithful`. A KC whose cited
        // quotes all relocate LIVE (lecture_grounded=true) but is only `uncertain` (e.g. an equational
        // claim still awaiting its math check) shows "matches your lecture" — yet verification_status
        // honestly stays `uncertain` (not faithful).
        val dir = Files.createTempDirectory("trust-b5r2-grounded")
        val content = dir.resolve("content"); seedContent(content, status = "uncertain")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // uncertain + grounded=true + a MATCHING content hash (survives the D8 gate).
        seedB8(db, "pa-kc-005", VerificationStatus.uncertain, seededKcHash(content), lectureGrounded = true)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        // The served status is honest: still uncertain, NOT faithful.
        assertTrue(body.contains("\"verification_status\":\"uncertain\""), body)
        assertFalse(body.contains("\"verification_status\":\"faithful\""), body)
        // …but the badge + honest floor reflect grounding.
        assertTrue(body.contains("matches your lecture"), body)
        assertTrue(body.contains("\"honest_floor\":\"FAITHFUL_TO_SOURCE\""), body)
        // Trust-language pin holds.
        assertFalse(body.contains("verified correct", ignoreCase = true), body)
    }

    @Test
    fun `B5r-2 (b) - a KC with a failed claim is NOT grounded - serves unverified`() = testApplication {
        // D-R6: a failed claim suppresses grounding. lecture_grounded=false + status=failed ⇒ the badge
        // pins at "unverified" (a contradicted claim is never "matches your lecture").
        val dir = Files.createTempDirectory("trust-b5r2-failed")
        val content = dir.resolve("content"); seedContent(content, status = "uncertain")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        seedB8(db, "pa-kc-005", VerificationStatus.failed, seededKcHash(content), lectureGrounded = false)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertFalse(body.contains("matches your lecture"), body)
    }

    @Test
    fun `B5r-2 (c) - grounded=true but content_hash MISMATCH fails CLOSED to unverified (D8 wraps grounded)`() = testApplication {
        // CRITICAL: the D8 staleness gate MUST wrap lecture_grounded exactly as it wraps faithful.
        // A grounded badge over edited content is forbidden — the lecture was edited after the audit
        // (hash mismatch) ⇒ fall closed to "unverified", never a lying "matches your lecture".
        val dir = Files.createTempDirectory("trust-b5r2-stale")
        val content = dir.resolve("content"); seedContent(content, status = "uncertain")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        // grounded=true but a STALE content hash (≠ the live content hash).
        seedB8(db, "pa-kc-005", VerificationStatus.uncertain, "stale000", lectureGrounded = true)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertFalse(body.contains("matches your lecture"), body)
    }

    @Test
    fun `B5r-2 (d) - grounded=NULL (legacy row) fails CLOSED to unverified`() = testApplication {
        // A legacy/partial row predating B5r-2 reads lecture_grounded=NULL ⇒ fail-closed default ⇒
        // the badge pins at "unverified" even though the content hash matches.
        val dir = Files.createTempDirectory("trust-b5r2-null")
        val content = dir.resolve("content"); seedContent(content, status = "uncertain")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        seedB8(db, "pa-kc-005", VerificationStatus.uncertain, seededKcHash(content), lectureGrounded = null)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"badge_text\":\"unverified\""), body)
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertFalse(body.contains("matches your lecture"), body)
    }

    @Test
    fun `B5r-2 (e) - REGRESSION - a genuinely faithful KC still serves the strong badge`() = testApplication {
        // B5r-2 must not weaken the faithful path: a faithful B8 row with a matching content hash
        // still serves "matches your lecture / faithful to your source" + FAITHFUL_TO_SOURCE, exactly
        // as before — independent of lecture_grounded.
        val dir = Files.createTempDirectory("trust-b5r2-faithful")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)
        seedB8(db, "pa-kc-005", VerificationStatus.faithful, seededKcHash(content), lectureGrounded = true)
        application { installTrust(db, dir) }
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("matches your lecture / faithful to your source"), body)
        assertTrue(body.contains("\"honest_floor\":\"FAITHFUL_TO_SOURCE\""), body)
    }

    // ── F5: per-claim verdicts (NOT the KC-level status broadcast onto every claim) ────────────────

    /**
     * A PA KC with BOTH an invariant (⇒ an INVARIANT claim) AND a span-bearing source quote
     * (⇒ a DEFINITION claim), so `claimsFor` emits TWO distinct claims the serve path stamps.
     */
    private fun seedMultiClaimKc(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "verification_status: \"uncertain\"\n" +
                "invariant: \"1 + 1 + 1 = 3\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    private fun loadKc(content: Path, kcId: String): jarvis.content.KnowledgeConcept =
        jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == kcId }

    private fun seedAuditRow(
        db: org.jetbrains.exposed.sql.Database,
        claim: jarvis.tutor.verify.VerificationClaim,
        status: VerificationStatus,
    ) = transaction(db) {
        VerificationAuditTable.insert {
            it[id] = TutorTypes.ulid()
            it[claimId] = claim.claimId
            it[kcId] = claim.kcId
            it[subject] = claim.subject
            it[claimKind] = claim.kind.name
            it[VerificationAuditTable.status] = status.name
            it[doc] = claim.source?.doc ?: ""
            it[page] = claim.source?.page?.takeIf { p -> p > 0 }
            it[pageAnchorStatus] = "LIVE"
            it[spanStart] = claim.source?.span?.start
            it[spanEnd] = claim.source?.span?.end
            it[fuzzyDistance] = 0
            it[familyA] = "RELAY"
            it[familyB] = "OPENROUTER"
            it[nonllmLeg] = "SYMPY"
            it[agree] = (status == VerificationStatus.faithful)
            it[roundtripPass] = (status == VerificationStatus.faithful)
            it[collapsedToOneFamily] = false
            it[auditedAt] = Instant.now()
            it[auditRunId] = "run-1"
        }
    }

    @Test
    fun `F5 - serve stamps each claim's OWN verdict from verification_audit, not the KC-level status`() = testApplication {
        // A KC whose B8 row is faithful (hash-matched) — the OLD bug stamped that single faithful
        // onto EVERY claim. F5: each claim must carry its OWN per-claim verdict read from
        // verification_audit. Here the INVARIANT claim audited faithful but the DEFINITION claim
        // audited uncertain ⇒ the served claims must be a MIX, never all-faithful.
        val dir = Files.createTempDirectory("trust-f5")
        val content = dir.resolve("content"); seedMultiClaimKc(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)

        val kc = loadKc(content, "pa-kc-005")
        val claims = jarvis.content.ContentReconcile.claimsFor(kc)
        val invariantClaim = claims.single { it.kind == jarvis.tutor.verify.ClaimKind.INVARIANT }
        val definitionClaim = claims.single { it.kind == jarvis.tutor.verify.ClaimKind.DEFINITION }

        // KC-level B8 row is faithful with a MATCHING content hash (survives the D8 gate) — exactly
        // the condition under which the broadcast bug would mark every claim faithful.
        seedB8(db, "pa-kc-005", VerificationStatus.faithful, jarvis.content.ContentReconcile.kcContentHash(kc))
        // Per-claim audit verdicts: INVARIANT faithful, DEFINITION uncertain.
        seedAuditRow(db, invariantClaim, VerificationStatus.faithful)
        seedAuditRow(db, definitionClaim, VerificationStatus.uncertain)

        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-005/status") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, r.status)
        val reply = Json { ignoreUnknownKeys = true }
            .decodeFromString(ApiVerifyStatusReply.serializer(), r.bodyAsText())

        val byKind = reply.claims.associateBy { it.claimKind }
        assertEquals(
            VerificationStatus.faithful,
            byKind[jarvis.tutor.verify.ClaimKind.INVARIANT]?.status,
            "the INVARIANT claim audited faithful must serve faithful",
        )
        assertEquals(
            VerificationStatus.uncertain,
            byKind[jarvis.tutor.verify.ClaimKind.DEFINITION]?.status,
            "the DEFINITION claim audited uncertain must serve uncertain — NOT the broadcast KC faithful",
        )
        // The fail-the-old-bug assertion: NOT every claim is faithful.
        assertFalse(
            reply.claims.all { it.status == VerificationStatus.faithful },
            "per-claim verdicts must NOT all be the broadcast KC-level faithful: ${reply.claims.map { it.claimKind to it.status }}",
        )
    }

    @Test
    fun `F5 - a claim with NO audit row falls closed to unverified, never the KC-level faithful`() = testApplication {
        // Fail-closed: a claim the audit never recorded must NOT inherit the KC's faithful status.
        val dir = Files.createTempDirectory("trust-f5-norow")
        val content = dir.resolve("content"); seedMultiClaimKc(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, sid) = seedUser(db, UserScope.FRIEND)

        val kc = loadKc(content, "pa-kc-005")
        val claims = jarvis.content.ContentReconcile.claimsFor(kc)
        val invariantClaim = claims.single { it.kind == jarvis.tutor.verify.ClaimKind.INVARIANT }

        seedB8(db, "pa-kc-005", VerificationStatus.faithful, jarvis.content.ContentReconcile.kcContentHash(kc))
        // ONLY the invariant claim has an audit row; the definition claim has none.
        seedAuditRow(db, invariantClaim, VerificationStatus.faithful)

        application { installTrust(db, dir) }
        val r = client.get("/api/v1/verify/pa-kc-005/status") { header("Cookie", "jarvis_session=$sid") }
        val reply = Json { ignoreUnknownKeys = true }
            .decodeFromString(ApiVerifyStatusReply.serializer(), r.bodyAsText())
        val byKind = reply.claims.associateBy { it.claimKind }
        assertEquals(VerificationStatus.faithful, byKind[jarvis.tutor.verify.ClaimKind.INVARIANT]?.status)
        assertEquals(
            VerificationStatus.unverified,
            byKind[jarvis.tutor.verify.ClaimKind.DEFINITION]?.status,
            "an unaudited claim must fall closed to unverified, never inherit the KC faithful",
        )
    }

    @Test
    fun `resolvePerClaimStatuses serves each claim's OWN runner verdict over a REAL multi-claim audit`() = testApplication {
        // End-to-end (case d): run the REAL runner over a multi-claim pa-kc-005 (2 DEFINITION +
        // 2 GRADER_RULE prose + 1 equational INVARIANT), then assert resolvePerClaimStatuses serves
        // each claim its OWN per-claim verdict matching the runner output — NOT a broadcast KC status.
        val dir = Files.createTempDirectory("trust-perclaim-e2e")
        val content = dir.resolve("content"); seedMultiGraderKc(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val kc = loadKc(content, "pa-kc-005")
        val claims = jarvis.content.ContentReconcile.claimsFor(kc)

        // Seed the KC pending so the runner can transition it.
        seedB8(db, "pa-kc-005", VerificationStatus.pending, null)

        // A HERMETIC runner (no python/network): both families SUPPORTED; the DEFINITION / prose-
        // GRADER_RULE claims reach `faithful` through their LIVE round-trip (the B5-RESHAPE prose
        // anchor — their quote round-trips against rawSource). To keep a genuine MIX (and prove serve
        // returns each claim's OWN verdict, not a KC broadcast), the equational INVARIANT's non-LLM leg
        // is UNCONDITIONALLY faked to FAIL ⇒ that claim ends `failed` while the prose claims end
        // `faithful`. (D-R16: the fake is unconditional so the mix does NOT depend on whether the real
        // SymPy bridge happens to run in the test env — a TrustRoutesTest must stay hermetic.)
        val runner = VerificationRunner(
            db = db,
            legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FixedLlm("SUPPORTED")),
            legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FixedLlm("SUPPORTED")),
            nonLlmLegFor = {
                jarvis.tutor.verify.NonLlmLeg { cl ->
                    if (cl.kind == jarvis.tutor.verify.ClaimKind.INVARIANT)
                        jarvis.tutor.verify.NonLlmResult(jarvis.tutor.verify.NonLlmLegKind.SYMPY, ran = true, pass = false, detail = "fake!=0")
                    else jarvis.tutor.verify.NonLlmResult(jarvis.tutor.verify.NonLlmLegKind.NONE, ran = false, pass = false, detail = "prose")
                }
            },
            rawSourceFor = { "Algorithm is a finite sequence." },
        )
        val runnerOut = kotlinx.coroutines.runBlocking { runner.audit(claims) }
        val runnerById = runnerOut.associate { it.claimId to it.newStatus }

        // The D8 content-staleness signal the serve path passes into resolvePerClaimStatuses. The
        // content is unchanged since the audit (finalizeKc stamped the matching hash), so it is NOT
        // stale. The cap must NOT fire here.
        val stale = VerifyAdmin.contentStale(db, "pa-kc-005", kc)
        val served = VerifyAdmin.resolvePerClaimStatuses(db, claims.map { it.claimId }, contentStale = stale)

        for (c in claims) {
            assertEquals(
                runnerById[c.claimId], served[c.claimId],
                "serve must return claim ${c.claimId} (${c.kind})'s OWN runner verdict, not a broadcast",
            )
        }
        // And the verdicts are a genuine MIX (not all one value) — the prose claims are faithful via
        // the round-trip anchor (B5-RESHAPE) while the equational INVARIANT failed its SymPy check.
        assertTrue(
            served.values.toSet().size >= 2,
            "the served per-claim verdicts must differ by claim, not be one broadcast value: $served",
        )
    }

    /**
     * A PA KC with an equational invariant + TWO prose grader_rules (non-equational) + a span-bearing
     * source quote — so claimsFor emits a DEFINITION + 2 GRADER_RULE + 1 INVARIANT claim set.
     */
    private fun seedMultiGraderKc(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\n" +
                "verification_status: \"pending\"\n" +
                "invariant: \"x + x = 2*x\"\n" +
                "grader_rules:\n" +
                "  - \"award full marks when the size is mentioned for every type\"\n" +
                "  - \"deduct one point per type whose representation size is omitted\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
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
        // Bundle-2: the in-handler audit is OWNER-CLI-ONLY by default. This test exercises the
        // opt-in PC-side path (the owner explicitly enabled JARVIS_AUDIT_ROUTE), so flip the seam on.
        auditRouteEnabled = { true }
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

    @Test
    fun `TOPOLOGY GUARD - on the served build admin verify does NOT run the audit in-thread (503, owner-CLI-only)`() = testApplication {
        // Bundle-2 (D7): the served build leaves JARVIS_AUDIT_ROUTE unset ⇒ the route must short-
        // circuit with 503 AFTER owner-auth + CSRF and NEVER call runner.audit on the request
        // thread (no model-load on the weak VPS; the canonical entry is the verifyContent CLI).
        val dir = Files.createTempDirectory("trust-admin-served")
        val content = dir.resolve("content"); seedContent(content, status = "pending")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, ownerSid) = seedUser(db, UserScope.OWNER)
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[kcId] = "pa-kc-005"
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = Instant.now()
            }
        }
        // Served-build config: the audit route is DISABLED.
        auditRouteEnabled = { false }
        // A tripwire runner: if the handler ever calls runner.audit on the request path, the legs
        // execute and (because nonLlmLegFor / liveRunnerFor would touch the real source) the audit
        // body would write a row / respond 502 — neither of which may happen on the served build.
        var runnerBuilt = false
        verifyRunnerFactory = { d, _ ->
            runnerBuilt = true
            VerificationRunner(
                db = d,
                legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FixedLlm("SUPPORTED")),
                legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FixedLlm("SUPPORTED")),
                nonLlmLegFor = { jarvis.tutor.verify.nonLlmLegFor(it) },
                rawSourceFor = { error("served build must NOT reach runner.audit on the request path") },
            )
        }
        application { installTrust(db, dir) }
        val r = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "jarvis_session=$ownerSid; csrf=c"); header("X-CSRF-Token", "c")
        }
        // 503 owner-CLI-only — NOT 200 (ran), NOT 502 (audit threw), NOT 422 (claims path reached).
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("owner-CLI-only", ignoreCase = true), body)
        assertTrue(body.contains("verifyContent", ignoreCase = true), body)
        // The audit body never ran: no runner was built and no verification_audit row exists.
        assertFalse(runnerBuilt, "served build must not build/invoke the audit runner on the request path")
        val auditCount = transaction(db) { VerificationAuditTable.selectAll().count() }
        assertEquals(0L, auditCount, "served build must write ZERO verification_audit rows from the request path")
    }

    @Test
    fun `TOPOLOGY GUARD - the 503 fires only AFTER owner-auth — a friend still gets 403 on the served build`() = testApplication {
        // Owner-auth + CSRF stay enforced; the topology guard is the LAST check, so a non-owner is
        // rejected 403 (not leaked the 503) and an unauthenticated caller is rejected 401.
        val dir = Files.createTempDirectory("trust-admin-served-gate")
        val content = dir.resolve("content"); seedContent(content)
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (_, friendSid) = seedUser(db, UserScope.FRIEND)
        auditRouteEnabled = { false }
        application { installTrust(db, dir) }
        // unauthenticated ⇒ 401 (before the guard)
        val unauth = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)
        // authenticated FRIEND ⇒ 403 (before the guard)
        val friend = client.post("/api/v1/admin/verify/pa-kc-005") {
            header("Cookie", "jarvis_session=$friendSid; csrf=c"); header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.Forbidden, friend.status)
    }

    // ── POST /fsrs/{id}/report-wrong ───────────────────────────────────────────────────────────

    private fun seedFaithfulCard(
        db: org.jetbrains.exposed.sql.Database,
        userId: String,
        kcId: String,
        // MF-2 (D-R18): a faithful KC in PRODUCTION also carries lecture_grounded=true and a content_hash
        // (written together by finalizeKc), so servedHonestFloor serves "matches your lecture" before any
        // report. Seed that reality so the report-wrong test proves the badge is CLEARED, not that it was
        // already absent. Default null hash for the legacy test that doesn't seed live content.
        contentHash: String? = null,
    ): String {
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
                it[KcVerificationStatusTable.contentHash] = contentHash
                it[lectureGrounded] = true
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

        // KC flipped faithful -> pending, AND (MF-2 / D-R18) the lecture-grounded badge state is CLEARED
        // in the same txn: lecture_grounded=false + content_hash NULL + last_audit_run_id NULL.
        val row = transaction(db) {
            KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq "pa-kc-005" }
                .single()
        }
        assertEquals(VerificationStatus.pending.name, row[KcVerificationStatusTable.status])
        assertEquals(false, row[KcVerificationStatusTable.lectureGrounded], "report-wrong must clear lecture_grounded (MF-2)")
        assertEquals(null, row[KcVerificationStatusTable.contentHash], "report-wrong must null content_hash (MF-2)")
        assertEquals(null, row[KcVerificationStatusTable.lastAuditRunId], "report-wrong must null last_audit_run_id (MF-2)")
    }

    @Test
    fun `MF-2 (D-R18) - report-wrong drops the servedHonestFloor from FAITHFUL_TO_SOURCE to UNVERIFIED on the disputed KC`() = testApplication {
        // The full MF-2 contract through the SERVE surface: a faithful + lecture_grounded KC over LIVE
        // content (matching content_hash) serves the "matches your lecture" badge (FAITHFUL_TO_SOURCE).
        // After a learner files report-wrong, the SAME serve path must fall to UNVERIFIED — the badge
        // can no longer claim the disputed content matches the lecture.
        val dir = Files.createTempDirectory("trust-mf2-floor")
        val content = dir.resolve("content"); seedContent(content, status = "faithful")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db, UserScope.FRIEND)
        val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == "pa-kc-005" }
        // Production reality: faithful + grounded=true + a MATCHING content hash (survives the D8 gate).
        val cardId = seedFaithfulCard(db, uid, "pa-kc-005", contentHash = seededKcHash(content))
        application { installTrust(db, dir) }

        // BEFORE the report: the serve floor is FAITHFUL_TO_SOURCE (badge "matches your lecture").
        assertEquals(
            jarvis.tutor.verify.HonestFloor.FAITHFUL_TO_SOURCE,
            VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc),
            "a faithful + grounded KC over matching live content serves FAITHFUL_TO_SOURCE before any report",
        )

        val r = client.post("/api/v1/fsrs/$cardId/report-wrong") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"gradeAttemptRaw":"the grader marked me wrong but my answer is right"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)

        // AFTER the report: grounded cleared + hash nulled ⇒ the serve floor fails CLOSED to UNVERIFIED.
        assertEquals(
            jarvis.tutor.verify.HonestFloor.UNVERIFIED,
            VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc),
            "after report-wrong the disputed KC must NOT serve the lecture badge (MF-2 / D-R18)",
        )
        // And the public status reply pins to unverified, not the lecture badge.
        val body = client.get("/api/v1/verify/pa-kc-005/status") {
            header("Cookie", "jarvis_session=$sid")
        }.bodyAsText()
        assertTrue(body.contains("\"honest_floor\":\"UNVERIFIED\""), body)
        assertFalse(body.contains("matches your lecture"), body)
    }

    /** A GAP_PROMOTION-style card with NO kc_id (the orphan-row case F6 must reject). */
    private fun seedKclessCard(db: org.jetbrains.exposed.sql.Database, userId: String): String {
        val cardId = TutorTypes.ulid()
        val now = Instant.now()
        FsrsCardRepo(db).insert(
            TutorCard(
                id = cardId, userId = userId,
                source = FsrsSource.GAP_PROMOTION, sourceRef = "some-gap",
                front = "front", back = "back",
                state = FsrsState(5.0, 1.0, 0.9, now, now, 0),
                kcId = null, status = CardStatus.ACTIVE,
            ),
        )
        return cardId
    }

    @Test
    fun `F6 - report-wrong on a null-kcId card is rejected (422), never writes a report_wrong keyed on empty-string`() = testApplication {
        // F6: a kc-less card (GAP_PROMOTION) has no KC to flip. The OLD code wrote a report_wrong row
        // keyed on '' (an orphan that pollutes the kc_id space). It must instead reject with 422 and
        // write NO report_wrong row keyed on the empty string.
        val dir = Files.createTempDirectory("trust-f6")
        val db = freshDb(dir)
        val (uid, sid) = seedUser(db, UserScope.FRIEND)
        val cardId = seedKclessCard(db, uid)
        application { installTrust(db, dir) }
        val r = client.post("/api/v1/fsrs/$cardId/report-wrong") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"gradeAttemptRaw":"x"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
        // No orphan report_wrong row keyed on '' was written.
        val emptyKeyed = transaction(db) {
            ReportWrongTable.selectAll().where { ReportWrongTable.kcId eq "" }.count()
        }
        assertEquals(0L, emptyKeyed, "F6: must not write a report_wrong row keyed on the empty string")
        // And nothing at all was written for this card.
        val total = transaction(db) {
            ReportWrongTable.selectAll().where { ReportWrongTable.cardId eq cardId }.count()
        }
        assertEquals(0L, total)
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
