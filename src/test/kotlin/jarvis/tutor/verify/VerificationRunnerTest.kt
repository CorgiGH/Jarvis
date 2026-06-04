package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.SourceOfRecord
import jarvis.content.SourceRef
import jarvis.content.Span
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import jarvis.tutor.VerificationAuditTable

/**
 * Batch-3 — `VerificationRunner.audit`: the offline batch composer (master-plan Area B, FAIL-LOUD
 * H4/H5). Hermetic: fake LLM legs + an injected non-LLM leg + an injected clock; an in-memory /
 * temp SQLite DB. NO network, NO sympy, NO live clock.
 *
 * The load-bearing invariants under test:
 *  - all-agree + nonllm-pass + roundtrip → `faithful` row + B8 upsert;
 *  - a THROWN LLM leg never crashes the batch and NEVER yields a silent `faithful`;
 *  - NONLLM NONE → `uncertain` floor (never-ran ≠ disagreed, FAIL-LOUD H5);
 *  - FAMILY_COLLAPSE → `uncertain`;
 *  - re-audit is idempotent on `audit_run_id` (one row per claim per run);
 *  - RESOLVE-BEFORE-WRITE: a leg throwing leaves NO partial row.
 */
class VerificationRunnerTest {

    // --- fakes ---------------------------------------------------------------------------------

    /** Canned-reply fake Llm. */
    private class FakeLlm(private val reply: String, private val model: String = "fake-model") : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            reply to model
    }

    /** A fake Llm that throws on the request path — proves a thrown LLM leg never crashes the batch. */
    private class ThrowingLlm : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> =
            throw RuntimeException("relay down (simulated)")
    }

    /** A non-LLM leg that throws — proves a thrown non-LLM leg never crashes the batch either. */
    private val throwingNonLlm = NonLlmLeg { throw IllegalStateException("sympy bridge exploded (simulated)") }

    /** A non-LLM leg that always passes (ran=true, pass=true). */
    private val passingNonLlm = NonLlmLeg {
        NonLlmResult(kind = NonLlmLegKind.SYMPY, ran = true, pass = true, detail = "simplify(lhs-rhs)=0")
    }

    /** A non-LLM leg that ran but did NOT pass. */
    private val failingNonLlm = NonLlmLeg {
        NonLlmResult(kind = NonLlmLegKind.SYMPY, ran = true, pass = false, detail = "simplify(lhs-rhs)=x")
    }

    // --- source-of-record fixture (form-feeds present so a LIVE anchor exists) -----------------

    /** A tiny raw source carrying a form-feed page break and the exact quote we anchor on. */
    private val rawSource =
        "PAGE ONE intro text\n" +
            SourceOfRecord.PAGE_BREAK +
            "For the values of each data type, the size of representation must be\n  mentioned. Trailing.\n"

    private val goldQuote = "For the values of each data type, the size of representation must be\n  mentioned."

    /** A SourceRef whose span we resolve fresh inside the runner (span here is a placeholder; the
     *  round-trip leg re-locates from the quote). */
    private fun goldSource(span: Span? = Span(0, 1)) =
        SourceRef(doc = "pa-lecture-01", quote = goldQuote, page = 2, span = span)

    private fun claim(
        kcId: String = "pa-kc-005",
        kind: ClaimKind = ClaimKind.INVARIANT,
        content: String = "the size of representation must be mentioned for each data type",
        invariant: String? = "size(t) is defined for every data type t",
        source: SourceRef? = goldSource(),
    ) = VerificationClaim(
        claimId = "$kcId:$kind:deadbeef",
        kcId = kcId,
        subject = "PA",
        kind = kind,
        content = content,
        invariant = invariant,
        source = source,
    )

    // --- DB harness ----------------------------------------------------------------------------

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("verify.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    /** Build a runner with two DISTINCT-family LLM legs (both return the same canned reply) and an
     *  injected non-LLM leg + raw-source resolver + a FIXED clock. */
    private fun runner(
        db: Database,
        legAReply: String = "SUPPORTED",
        legBReply: String = "SUPPORTED",
        legAFamily: LegFamily = LegFamily.RELAY,
        legBFamily: LegFamily = LegFamily.OPENROUTER,
        legALlm: Llm = FakeLlm(legAReply),
        legBLlm: Llm = FakeLlm(legBReply),
        nonLlm: NonLlmLeg = passingNonLlm,
        clock: () -> Instant = { Instant.parse("2026-06-04T12:00:00Z") },
    ) = VerificationRunner(
        db = db,
        legA = TwoFamilyDeriver.Leg(family = legAFamily, llm = legALlm),
        legB = TwoFamilyDeriver.Leg(family = legBFamily, llm = legBLlm),
        nonLlmLegFor = { nonLlm },
        rawSourceFor = { rawSource },
        clock = clock,
    )

    private fun statusOf(db: Database, kcId: String): String? = transaction(db) {
        var s: String? = null
        exec("SELECT status FROM kc_verification_status WHERE kc_id='$kcId'") { rs ->
            if (rs.next()) s = rs.getString(1)
        }
        s
    }

    private fun auditRowCount(db: Database, claimId: String): Int = transaction(db) {
        VerificationAuditTable.selectAll()
            .where { VerificationAuditTable.claimId eq claimId }
            .count().toInt()
    }

    // --- TESTS ---------------------------------------------------------------------------------

    @Test
    fun `all-agree + nonllm-pass + roundtrip yields faithful row and B8 upsert`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        // The KC must be pending before an audit can certify it (curate-tutor Stage-9 sets pending).
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db).audit(listOf(c))

        assertEquals(1, out.size)
        assertEquals(VerificationStatus.faithful, out[0].newStatus, "all legs agree+pass+roundtrip ⇒ faithful")
        // B8 upsert reflects faithful
        assertEquals("faithful", statusOf(db, c.kcId))
        // exactly one audit row written for this claim
        assertEquals(1, auditRowCount(db, c.claimId))
    }

    @Test
    fun `a thrown LLM leg never crashes the batch and never yields a silent faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // family A throws; even though non-LLM passes + (the other) leg would agree, a thrown leg
        // can never produce agreement ⇒ must NOT be faithful.
        val out = runner(db, legALlm = ThrowingLlm()).audit(listOf(c))

        assertEquals(1, out.size, "the batch completed despite the throwing leg")
        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "a thrown LLM leg must NEVER be faithful")
        assertTrue(
            out[0].newStatus == VerificationStatus.failed || out[0].newStatus == VerificationStatus.uncertain,
            "a thrown LLM leg ⇒ failed/uncertain, never silent faithful (got ${out[0].newStatus})",
        )
        assertNotEquals("faithful", statusOf(db, c.kcId))
        // a row was still written (the audit ran to completion)
        assertEquals(1, auditRowCount(db, c.claimId))
    }

    @Test
    fun `a thrown non-LLM leg never crashes the batch and never yields faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, nonLlm = throwingNonLlm).audit(listOf(c))

        assertEquals(1, out.size)
        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "a thrown non-LLM leg must NEVER be faithful")
        assertEquals(1, auditRowCount(db, c.claimId))
    }

    @Test
    fun `NONLLM NONE drives the uncertain floor - never faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // legs agree + roundtrip would pass, but the non-LLM leg is NONE (ran=false): UNCERTAIN floor.
        val noneLeg = NonLlmLeg { NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = "no checker") }
        val out = runner(db, nonLlm = noneLeg).audit(listOf(c))

        assertEquals(VerificationStatus.uncertain, out[0].newStatus, "NONLLM NONE ⇒ uncertain floor (never-ran ≠ disagreed)")
        assertEquals("uncertain", statusOf(db, c.kcId))
        assertEquals(1, auditRowCount(db, c.claimId))
    }

    @Test
    fun `a non-LLM leg that ran but did not pass is never faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, nonLlm = failingNonLlm).audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "non-LLM ran but failed ⇒ not faithful")
    }

    @Test
    fun `family collapse drives uncertain`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // both legs configured RELAY ⇒ FAMILY_COLLAPSE ⇒ uncertain (even though the text agrees).
        val out = runner(db, legAFamily = LegFamily.RELAY, legBFamily = LegFamily.RELAY).audit(listOf(c))

        assertEquals(VerificationStatus.uncertain, out[0].newStatus, "family collapse ⇒ uncertain")
        assertEquals("uncertain", statusOf(db, c.kcId))
    }

    @Test
    fun `legs that disagree drive failed`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, legAReply = "SUPPORTED", legBReply = "REFUTED").audit(listOf(c))

        assertEquals(VerificationStatus.failed, out[0].newStatus, "disagreeing families ⇒ failed")
    }

    @Test
    fun `both families REFUTED is an AGREED non-SUPPORTED verdict and must NEVER be faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // F1: both families REFUTED ⇒ they AGREE on a verdict, the non-LLM leg passes, the round-trip
        // passes, nothing threw — yet the agreed verdict is REFUTED, NOT SUPPORTED. The faithful path
        // must demand SUPPORTED==SUPPORTED, so this must route to failed (DISAGREE_OR_…), never faithful.
        val out = runner(db, legAReply = "REFUTED", legBReply = "REFUTED").audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "both REFUTED ⇒ NEVER faithful (agreed but not SUPPORTED)")
        assertEquals(VerificationStatus.failed, out[0].newStatus, "an agreed non-SUPPORTED verdict ⇒ failed")
        assertEquals("failed", statusOf(db, c.kcId))
        assertEquals(1, auditRowCount(db, c.claimId))
    }

    @Test
    fun `a claim with no gold span drives the definitional uncertain floor - never faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        // a purely definitional claim: source present but span null (no anchored gold span).
        val c = claim(
            kind = ClaimKind.DEFINITION,
            invariant = null,
            source = SourceRef(doc = "pa-lecture-01", quote = goldQuote, page = 0, span = null),
        )
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db).audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "no gold span ⇒ never faithful")
        assertEquals(VerificationStatus.uncertain, out[0].newStatus, "DEFINITIONAL_NO_GOLD_SPAN ⇒ uncertain floor")
    }

    @Test
    fun `a mutated quote that fails the round-trip is never faithful`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        // legs agree + non-LLM passes, but the quote does NOT exist in the raw source ⇒ round-trip fail.
        val c = claim(
            source = SourceRef(
                doc = "pa-lecture-01",
                quote = "THIS QUOTE DOES NOT APPEAR ANYWHERE IN THE SOURCE AT ALL whatsoever zzz",
                page = 2,
                span = Span(0, 1),
            ),
        )
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db).audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "round-trip fail ⇒ never faithful (R1 reject)")
        assertEquals(VerificationStatus.failed, out[0].newStatus)
    }

    @Test
    fun `re-audit is idempotent on audit_run_id - one row per claim per run`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val r = runner(db)
        // Run the SAME run twice with the SAME runId — the second pass must NOT add a duplicate row.
        val runId = "fixed-run-001"
        r.audit(listOf(c), auditRunId = runId)
        r.audit(listOf(c), auditRunId = runId)

        assertEquals(1, auditRowCount(db, c.claimId), "re-audit on the same audit_run_id must not duplicate the row")
    }

    @Test
    fun `distinct runs each write their own audit row`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val r = runner(db)
        r.audit(listOf(c), auditRunId = "run-A")
        r.audit(listOf(c), auditRunId = "run-B")

        assertEquals(2, auditRowCount(db, c.claimId), "two distinct runs ⇒ two audit rows")
    }

    @Test
    fun `resolve-before-write - a leg throwing leaves no partial row`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // Even when the LLM leg throws, the audit must produce a complete row (resolved legs first,
        // single write after) — NOT a partial / half-written one, and never zero rows that would
        // hide the failure. We assert exactly one complete row exists and it is NOT faithful.
        runner(db, legALlm = ThrowingLlm()).audit(listOf(c))

        assertEquals(1, auditRowCount(db, c.claimId))
        val row = transaction(db) {
            VerificationAuditTable.selectAll()
                .where { VerificationAuditTable.claimId eq c.claimId }
                .single()
        }
        // the row is fully populated (no NPE-on-read) and the status is honest (not faithful)
        assertNotNull(row[VerificationAuditTable.auditRunId])
        assertNotEquals("faithful", row[VerificationAuditTable.status])
        assertEquals(c.kcId, row[VerificationAuditTable.kcId])
    }

    @Test
    fun `the batch audits every claim even when one throws`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val good = claim(kcId = "pa-kc-005")
        val bad = claim(kcId = "pa-kc-006")
        seedStatus(db, good.kcId, VerificationStatus.pending)
        seedStatus(db, bad.kcId, VerificationStatus.pending)

        // A per-claim non-LLM leg: the second claim's leg throws; the first must still reach faithful.
        val perClaimNonLlm: (String) -> NonLlmLeg = { subject ->
            NonLlmLeg { cl ->
                if (cl.kcId == "pa-kc-006") throw IllegalStateException("boom")
                NonLlmResult(NonLlmLegKind.SYMPY, ran = true, pass = true, detail = "ok")
            }
        }
        val r = VerificationRunner(
            db = db,
            legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED")),
            legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED")),
            nonLlmLegFor = perClaimNonLlm,
            rawSourceFor = { rawSource },
            clock = { Instant.parse("2026-06-04T12:00:00Z") },
        )

        val out = r.audit(listOf(good, bad))

        assertEquals(2, out.size, "both claims audited despite one throwing")
        val byKc = out.associateBy { it.claimId }
        assertEquals(VerificationStatus.faithful, byKc[good.claimId]!!.newStatus, "the good claim still reaches faithful")
        assertNotEquals(VerificationStatus.faithful, byKc[bad.claimId]!!.newStatus, "the throwing claim is not faithful")
        assertEquals(1, auditRowCount(db, good.claimId))
        assertEquals(1, auditRowCount(db, bad.claimId))
    }

    @Test
    fun `audit reads the current status from the B8 table for the transition`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        // No seed row at all ⇒ runner must fall back to a sane default (unverified) and STILL not
        // emit a silent faithful (transition from unverified never yields faithful — FAIL-LOUD).
        val out = runner(db).audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "from unverified, all-agree must NOT short-cut to faithful")
        // and a B8 row now exists
        assertNotNull(statusOf(db, c.kcId), "the audit must upsert a B8 row even when none existed")
    }

    // helper: seed a kc_verification_status row at a chosen status
    private fun seedStatus(db: Database, kcId: String, status: VerificationStatus) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status.name
                it[updatedAt] = Instant.parse("2026-06-01T00:00:00Z")
            }
        }
    }
}
