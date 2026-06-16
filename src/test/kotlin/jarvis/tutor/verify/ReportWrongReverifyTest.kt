package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.ContentReconcile
import jarvis.content.SourceOfRecord
import jarvis.content.SourceRef
import jarvis.content.Span
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.TutorTypes
import jarvis.tutor.UserScope
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.HonestFloor
import jarvis.web.VerifyAdmin
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D3 — the report_wrong re-audit RELIGHT lifecycle (the minimal-but-COMPLETE state machine, no
 * permanent trap). Three coordinated parts under test:
 *  1. serve refusal while OPEN (lives in servedHonestFloor / resolveStatus — see TrustRoutesTest);
 *  2. finalizeKc RELIGHT-GUARD: a re-audit on unchanged content does NOT relight the badge while a
 *     report is OPEN;
 *  3. the terminal CLOSING edge: an owner re-audit that RE-GROUNDS the disputed KC closes the OPEN
 *     row as REVERIFIED_FAITHFUL (stamping resolved_by + resolved_at) and ONLY THEN relights.
 *
 * Hermetic: temp SQLite, fake LLM legs, an in-test source resolver. No network.
 */
class ReportWrongReverifyTest {

    private class FakeLlm(private val reply: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?) =
            reply to "fake-model"
    }

    private val rawSource =
        "PAGE ONE intro.\n" +
            SourceOfRecord.PAGE_BREAK +
            "For the values of each data type, the size of representation must be\n  mentioned. Trailing.\n"
    private val goldQuote = "For the values of each data type, the size of representation must be\n  mentioned."

    /** A DEFINITION claim that reaches faithful via the content-relocating round-trip. */
    private fun definitionClaim(kcId: String = "pa-kc-005") = VerificationClaim(
        claimId = "$kcId:DEFINITION:${ContentReconcile.sha256_8(goldQuote)}",
        kcId = kcId, subject = "PA", kind = ClaimKind.DEFINITION,
        content = goldQuote, invariant = null,
        source = SourceRef(doc = "pa-lecture-01", quote = goldQuote, page = 2, span = Span(0, 1)),
    )

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("reverify.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private val fixedClock: () -> Instant = { Instant.parse("2026-06-05T12:00:00Z") }

    /** Build a runner whose DEFINITION claim reaches faithful (both families SUPPORTED + round-trip). */
    private fun runner(db: Database, ownerId: String = "owner-1") = VerificationRunner(
        db = db,
        legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED")),
        legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED")),
        nonLlmLegFor = { NonLlmLeg { NonLlmResult(NonLlmLegKind.NONE, ran = false, pass = false, detail = "none") } },
        rawSourceFor = { rawSource },
        clock = fixedClock,
        ownerId = ownerId,
    )

    private fun seedPending(db: Database, kcId: String) = transaction(db) {
        KcVerificationStatusTable.insert {
            it[KcVerificationStatusTable.kcId] = kcId
            it[status] = VerificationStatus.pending.name
            it[updatedAt] = Instant.parse("2026-06-01T00:00:00Z")
        }
    }

    /** A reporter user (the report_wrong.user_id FK requires a real user row). */
    private fun seedUser(db: Database): String {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        return uid
    }

    private fun openReport(db: Database, kcId: String, userId: String): String {
        val id = TutorTypes.ulid()
        transaction(db) {
            ReportWrongTable.insert {
                it[ReportWrongTable.id] = id
                it[ReportWrongTable.userId] = userId
                it[ReportWrongTable.kcId] = kcId
                it[ReportWrongTable.cardId] = null
                it[gradeAttemptRaw] = "disputed"
                it[reportedAt] = Instant.parse("2026-06-02T00:00:00Z")
                it[resolution] = "OPEN"
            }
        }
        return id
    }

    private fun row(db: Database, kcId: String) = transaction(db) {
        KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq kcId }.single()
    }

    private fun reportRow(db: Database, id: String) = transaction(db) {
        ReportWrongTable.selectAll().where { ReportWrongTable.id eq id }.single()
    }

    // --- the core D3 RED test (relight-guard) ---------------------------------------------------

    @Test
    fun `D3 a re-audit on unchanged content does NOT relight the badge while a report_wrong is OPEN`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        seedPending(db, "pa-kc-005")
        val uid = seedUser(db)
        openReport(db, "pa-kc-005", uid)
        val c = definitionClaim()

        // A re-audit on unchanged content (would, absent the guard, relight to faithful + grounded).
        runner(db).audit(listOf(c))

        // RELIGHT-GUARD: while the report is OPEN, the badge state is held DARK — grounded=false +
        // NULL hashes ⇒ servedHonestFloor fails closed to UNVERIFIED (RED today: relights to FAITHFUL).
        val r = row(db, "pa-kc-005")
        assertEquals(false, r[KcVerificationStatusTable.lectureGrounded], "OPEN dispute ⇒ grounded held false")
        assertNull(r[KcVerificationStatusTable.contentHash], "OPEN dispute ⇒ content_hash held NULL")
        assertNull(r[KcVerificationStatusTable.sourceSpanHash], "OPEN dispute ⇒ source_span_hash held NULL")
        assertEquals(
            HonestFloor.UNVERIFIED, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", null),
            "a re-audit must NOT relight the lecture badge while a report_wrong is OPEN (D3 relight-guard)",
        )
    }

    // --- the terminal closing edge (no permanent trap): owner re-audit ⇒ REVERIFIED_FAITHFUL ------

    @Test
    fun `D3 the owner re-audit closing transition closes the OPEN report REVERIFIED_FAITHFUL and relights`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        seedPending(db, "pa-kc-005")
        val uid = seedUser(db)
        val reportId = openReport(db, "pa-kc-005", uid)
        val c = definitionClaim()

        // FIRST re-audit is a ROUTINE batch re-audit (ownerReverify=false): held dark (the relight-
        // guard); the report STAYS OPEN — a routine re-audit must never auto-close a learner's dispute.
        runner(db).audit(listOf(c))
        assertEquals("OPEN", reportRow(db, reportId)[ReportWrongTable.resolution], "a routine re-audit leaves the report OPEN")
        assertEquals(false, row(db, "pa-kc-005")[KcVerificationStatusTable.lectureGrounded], "a routine re-audit stays dark while OPEN")

        // The OWNER explicitly RE-VERIFIES the disputed KC (ownerReverify=true). The re-grounding audit
        // IS the resolution event: finalizeKc closes the OPEN row REVERIFIED_FAITHFUL (stamping owner +
        // timestamp) and ONLY THEN relights — atomically. This is the terminal exit edge (no DoS-trap).
        runner(db).audit(listOf(c), ownerReverify = true)

        val report = reportRow(db, reportId)
        assertEquals(
            ReportResolution.REVERIFIED_FAITHFUL.name, report[ReportWrongTable.resolution],
            "the owner re-audit that re-grounds CLOSES the OPEN dispute as REVERIFIED_FAITHFUL (the exit edge exists)",
        )
        assertEquals("owner-1", report[ReportWrongTable.resolvedBy], "the single-owner resolver id is stamped (DELTA-3)")
        assertNotNull(report[ReportWrongTable.resolvedAt], "the resolution timestamp is stamped (DELTA-3)")

        // …and ONLY THEN does the badge relight.
        val r = row(db, "pa-kc-005")
        assertEquals(VerificationStatus.faithful.name, r[KcVerificationStatusTable.status], "the KC is faithful again")
        assertEquals(true, r[KcVerificationStatusTable.lectureGrounded], "the badge relit after the closing edge")
        assertNotNull(r[KcVerificationStatusTable.contentHash], "content_hash re-stamped on relight")
        assertNotNull(r[KcVerificationStatusTable.sourceSpanHash], "source_span_hash re-stamped on relight")
        assertEquals(
            HonestFloor.UNVERIFIED, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", null),
            "serve with no live kc still fails closed (no kc to hash) — the relight is proven via the row + report state",
        )
    }
}
