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
    fun `audit writes the KC content_hash on the B8 upsert (D8)`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        val c = claim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        runner(db).audit(listOf(c))

        // The B8 row now carries a content_hash == the hash over THIS KC's audited claim set.
        val written = transaction(db) {
            var h: String? = null
            exec("SELECT content_hash FROM kc_verification_status WHERE kc_id='${c.kcId}'") { rs ->
                if (rs.next()) h = rs.getString(1)
            }
            h
        }
        assertNotNull(written, "the audit must stamp content_hash on the B8 row (D8)")
        assertEquals(
            jarvis.content.ContentReconcile.kcContentHashOf(listOf(c)),
            written,
            "content_hash must be the hash over the KC's audited claims (audit-side == serve-side)",
        )
    }

    @Test
    fun `per-claim verdict is computed from a fresh pending prior, independent of the B8 seed row (FIX-A)`(@TempDir tmp: Path) = runBlocking {
        // FIX-A: each claim's OWN verdict is `transition(pending, outcome)` from a FRESH pending prior —
        // NOT read off the shared kc_verification_status row. So the per-claim verdict is the SAME
        // whether the B8 row is absent, unverified, or pending. Here: no seed row at all; an all-agree
        // equational INVARIANT claim ⇒ faithful per-claim (the runner no longer needs a pre-seeded
        // pending row to certify, because it always starts the per-claim transition from pending).
        val db = freshDb(tmp)
        val c = claim()  // equational INVARIANT, all legs agree-SUPPORTED + non-LLM pass + round-trip
        val out = runner(db).audit(listOf(c))

        assertEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "a fully-passing equational claim ⇒ faithful per-claim from a fresh pending prior (seed-independent)",
        )
        // and a B8 row was upserted even though none existed (the KC aggregate over its single claim).
        assertEquals("faithful", statusOf(db, c.kcId), "the audit upserts the KC aggregate even with no prior row")
    }

    @Test
    fun `the per-claim verdict does NOT depend on the seeded B8 status (order-independent prior)`(@TempDir tmp: Path) = runBlocking {
        // Prove seed-independence directly: the SAME claim audited against a `failed`-seeded B8 row and
        // against a `pending`-seeded B8 row yields the SAME per-claim verdict (faithful). The OLD code
        // read the seed as the per-claim prior, so a `failed` seed would have poisoned it.
        val cFailedSeed = claim(kcId = "pa-kc-seed-a")
        val dbA = freshDb(tmp.resolve("seedfailed").also { java.nio.file.Files.createDirectories(it) })
        seedStatus(dbA, cFailedSeed.kcId, VerificationStatus.failed)
        val a = runner(dbA).audit(listOf(cFailedSeed))[0].newStatus

        val cPendingSeed = claim(kcId = "pa-kc-seed-b")
        val dbB = freshDb(tmp.resolve("seedpending").also { java.nio.file.Files.createDirectories(it) })
        seedStatus(dbB, cPendingSeed.kcId, VerificationStatus.pending)
        val b = runner(dbB).audit(listOf(cPendingSeed))[0].newStatus

        assertEquals(VerificationStatus.faithful, a, "a failed B8 seed must NOT poison the per-claim verdict")
        assertEquals(a, b, "the per-claim verdict is independent of the seeded B8 status")
    }

    // --- MULTI-CLAIM KC POISONING (post-fix audit wayrajnng) -----------------------------------

    /**
     * Build a REAL multi-claim pa-kc-005 claim set the way curate-tutor Stage-9 emits it:
     *  - 2 DEFINITION claims (source-anchored, no invariant) ⇒ no machine check applies ⇒ uncertain floor,
     *  - 2 GRADER_RULE claims that are PROSE (non-equational, invariant=null) ⇒ uncertain floor,
     *  - 1 equational INVARIANT claim ⇒ the SymPy leg runs + passes ⇒ faithful.
     * Every claim is span-anchored (round-trip passes against [rawSource]) so the ONLY differentiator
     * is the per-claim machine check, NOT a missing gold span.
     */
    private fun multiClaim(
        kind: ClaimKind,
        content: String,
        invariant: String?,
    ) = VerificationClaim(
        claimId = "pa-kc-005:$kind:${jarvis.content.ContentReconcile.sha256_8(content)}",
        kcId = "pa-kc-005",
        subject = "PA",
        kind = kind,
        content = content,
        invariant = invariant,
        source = goldSource(),
    )

    /** The 5-claim pa-kc-005 set (2 DEFINITION + 2 GRADER_RULE prose + 1 equational INVARIANT). */
    private fun multiClaimKc(): List<VerificationClaim> = listOf(
        multiClaim(ClaimKind.DEFINITION, goldQuote, invariant = null),
        multiClaim(ClaimKind.DEFINITION, "a data type is a set of values with operations", invariant = null),
        multiClaim(ClaimKind.GRADER_RULE, "award full marks when the size is mentioned for every type", invariant = null),
        multiClaim(ClaimKind.GRADER_RULE, "deduct one point per type whose representation size is omitted", invariant = null),
        multiClaim(ClaimKind.INVARIANT, "x + x = 2*x", invariant = "x + x = 2*x"),
    )

    /** A non-LLM leg that mirrors the REAL routing: equational INVARIANT/GRADER_RULE ⇒ SYMPY pass;
     *  a non-equational / DEFINITION claim ⇒ NONE/ran=false (no machine check applies). */
    private val realisticNonLlm = NonLlmLeg { cl ->
        nonLlmLegFor(cl.subject).check(cl).let { real ->
            // The real SymPyLeg shells to python on an equational claim; in the hermetic suite we
            // fake the equational pass but keep the REAL "no machine check applies" routing.
            if (real.kind == NonLlmLegKind.SYMPY && real.ran) real
            else if (cl.kind == ClaimKind.INVARIANT || (cl.kind == ClaimKind.GRADER_RULE && cl.invariant != null))
                NonLlmResult(NonLlmLegKind.SYMPY, ran = true, pass = true, detail = "fake simplify=0")
            else real
        }
    }

    @Test
    fun `multi-claim KC - DEFINITION and prose GRADER_RULE reach faithful via the LIVE round-trip (B5-RESHAPE), equational INVARIANT is faithful too`(
        @TempDir tmp: Path,
    ) = runBlocking {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-005", VerificationStatus.pending)
        val claims = multiClaimKc()

        val out = runner(db, nonLlm = realisticNonLlm).audit(claims)

        val byId = out.associateBy { it.claimId }
        // (a) NO claim ends failed when every family agrees-SUPPORTED + round-trips pass.
        assertTrue(
            out.none { it.newStatus == VerificationStatus.failed },
            "no claim may auto-route to failed: ${out.map { it.claimKind() to it.newStatus }}",
        )
        // B5-RESHAPE: a DEFINITION / prose GRADER_RULE has no equational machine check, so the LIVE
        // round-trip IS its deterministic non-LLM leg. Every claim here round-trips against the source
        // ⇒ faithful via the prose anchor (this is the unblock that supersedes the old uncertain floor).
        for (c in claims.filter { it.kind == ClaimKind.DEFINITION || (it.kind == ClaimKind.GRADER_RULE && it.invariant == null) }) {
            assertEquals(
                VerificationStatus.faithful, byId[c.claimId]!!.newStatus,
                "a ${c.kind} claim with a passing LIVE round-trip ⇒ faithful (the prose anchor), never the old uncertain floor",
            )
        }
        // The equational INVARIANT ⇒ faithful (SymPy ran + passed, families agree, round-trip passes).
        val inv = claims.single { it.kind == ClaimKind.INVARIANT }
        assertEquals(
            VerificationStatus.faithful, byId[inv.claimId]!!.newStatus,
            "the equational INVARIANT claim ⇒ faithful",
        )
    }

    @Test
    fun `multi-claim per-claim verdicts are IDENTICAL under shuffled claim order`(@TempDir tmp: Path) = runBlocking {
        val claims = multiClaimKc()

        // Audit in the natural order.
        val db1 = freshDb(tmp.resolve("a").also { java.nio.file.Files.createDirectories(it) })
        seedStatus(db1, "pa-kc-005", VerificationStatus.pending)
        val natural = runner(db1, nonLlm = realisticNonLlm).audit(claims)
            .associate { it.claimId to it.newStatus }

        // Audit the SAME claims in a reversed/shuffled order on a FRESH db.
        val db2 = freshDb(tmp.resolve("b").also { java.nio.file.Files.createDirectories(it) })
        seedStatus(db2, "pa-kc-005", VerificationStatus.pending)
        val shuffled = runner(db2, nonLlm = realisticNonLlm).audit(claims.shuffled(java.util.Random(7)))
            .associate { it.claimId to it.newStatus }

        assertEquals(
            natural, shuffled,
            "each claim's OWN verdict must be order-INDEPENDENT (no shared-KC-row prior poisoning)",
        )
    }

    @Test
    fun `multi-claim KC - ONE genuinely disagreeing claim ends failed and the KC aggregates to failed`(
        @TempDir tmp: Path,
    ) = runBlocking {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-005", VerificationStatus.pending)
        val claims = multiClaimKc()
        // The INVARIANT claim genuinely DISAGREES (family B refutes it); every other claim agrees.
        val disagreeingInvariant: (String) -> jarvis.Llm = { _ -> FakeLlm("SUPPORTED") }
        val r = VerificationRunner(
            db = db,
            legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED")),
            legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, object : Llm {
                override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> {
                    // Refute ONLY the equational invariant claim; support everything else.
                    val refute = messages.any { it.content.contains("x + x = 2*x") }
                    return (if (refute) "REFUTED" else "SUPPORTED") to "fake-model"
                }
            }),
            nonLlmLegFor = { realisticNonLlm },
            rawSourceFor = { rawSource },
            clock = { Instant.parse("2026-06-04T12:00:00Z") },
        )

        val out = r.audit(claims)
        val byId = out.associateBy { it.claimId }
        val inv = claims.single { it.kind == ClaimKind.INVARIANT }
        // (c) the disagreeing claim ⇒ failed.
        assertEquals(
            VerificationStatus.failed, byId[inv.claimId]!!.newStatus,
            "a genuinely-disagreeing claim ⇒ failed",
        )
        // The KC aggregates to failed (any failed ⇒ failed).
        assertEquals("failed", statusOf(db, "pa-kc-005"), "any failed per-claim verdict ⇒ KC aggregates to failed")
    }

    @Test
    fun `multi-claim KC - all-agree current PA shape aggregates to faithful via the LIVE round-trip (B5-RESHAPE)`(
        @TempDir tmp: Path,
    ) = runBlocking {
        // B5-RESHAPE supersedes the old "honest uncertain floor" for this shape: on the CURRENT PA
        // content shape (DEFINITION + prose GRADER_RULE + 1 equational INVARIANT), every family
        // agrees-SUPPORTED and every claim round-trips against the LIVE source. The DEFINITION/prose
        // GRADER_RULE claims now reach faithful through the round-trip prose anchor (their deterministic
        // non-LLM leg), and the equational INVARIANT is faithful too ⇒ the KC conjunction is faithful.
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-005", VerificationStatus.pending)
        runner(db, nonLlm = realisticNonLlm).audit(multiClaimKc())
        assertEquals(
            "faithful", statusOf(db, "pa-kc-005"),
            "every claim round-trips LIVE ⇒ each is faithful per its kind ⇒ the KC conjunction is faithful",
        )
    }

    // --- B5-RESHAPE: per-claim-kind `faithful` routing (B5r-1) ---------------------------------

    /**
     * A DEFINITION claim's prose source helper — span present so it is NOT the no-gold-span floor;
     * its quote round-trips against [rawSource]. The non-LLM leg is genuinely NONE (no machine
     * check applies to prose). This is the shape every KC emits ≥1 of (ContentReconcile.claimsFor).
     */
    private fun definitionClaim(
        content: String = goldQuote,
        source: SourceRef? = goldSource(),
    ) = VerificationClaim(
        claimId = "pa-kc-def:DEFINITION:${jarvis.content.ContentReconcile.sha256_8(content)}",
        kcId = "pa-kc-def",
        subject = "PA",
        kind = ClaimKind.DEFINITION,
        content = content,
        invariant = null,
        source = source,
    )

    /** The real NONE leg the runner sees for a DEFINITION (no machine check applies). */
    private val noneLeg = NonLlmLeg {
        NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = "no machine check for DEFINITION")
    }

    @Test
    fun `B5r-1 (a) - a DEFINITION with span + round-trip PASS + NONE non-LLM leg reaches faithful`(@TempDir tmp: Path) = runBlocking {
        // THE UNBLOCK: under the old conjunction a DEFINITION had no machine check (NONE/ran=false) so
        // it floored to NONLLM_LEG_NONE/uncertain and NO KC could ever be faithful. Per-kind routing
        // makes the LIVE round-trip the deterministic non-LLM leg for prose: round-trip PASS + nothing
        // threw ⇒ faithful. SymPy is NOT required for a DEFINITION.
        val db = freshDb(tmp)
        val c = definitionClaim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, nonLlm = noneLeg).audit(listOf(c))

        assertEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "a DEFINITION with a passing LIVE round-trip + NONE non-LLM leg ⇒ faithful (the prose anchor)",
        )
        assertEquals("faithful", statusOf(db, c.kcId))
    }

    @Test
    fun `B5r-1 (b) - a DEFINITION whose round-trip FAILS is never faithful`(@TempDir tmp: Path) = runBlocking {
        // The round-trip IS the load-bearing anchor for prose: a mutated/absent quote ⇒ round-trip
        // fail ⇒ NOT faithful (it routes to failed via DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW).
        val db = freshDb(tmp)
        val c = definitionClaim(
            source = SourceRef(
                doc = "pa-lecture-01",
                quote = "A QUOTE THAT APPEARS NOWHERE IN THE LIVE SOURCE zzz unrelated text",
                page = 2,
                span = Span(0, 1),
            ),
        )
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, nonLlm = noneLeg).audit(listOf(c))

        assertNotEquals(VerificationStatus.faithful, out[0].newStatus, "round-trip FAIL ⇒ a DEFINITION is never faithful")
        assertEquals(VerificationStatus.failed, out[0].newStatus, "a DEFINITION whose quote does not round-trip ⇒ failed")
    }

    @Test
    fun `B5r-1 (c) - SELF-ENTAILMENT GUARD - LLM agreement ALONE cannot make a DEFINITION faithful when round-trip fails`(@TempDir tmp: Path) = runBlocking {
        // The machine anchor for a DEFINITION is the LIVE round-trip, NOT the LLM/NLI vote (a
        // DEFINITION's content==quote makes NLI self-entailment zero-signal). So even with BOTH
        // families SUPPORTED (bothSupported=true) + non-LLM "passing", a FAILED round-trip must NOT
        // promote to faithful. bothSupported alone never certifies a DEFINITION.
        val db = freshDb(tmp)
        val c = definitionClaim(
            source = SourceRef(
                doc = "pa-lecture-01",
                quote = "ANOTHER QUOTE ABSENT FROM THE LIVE SOURCE so the round-trip cannot pass yyy",
                page = 2,
                span = Span(0, 1),
            ),
        )
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // Both families SUPPORTED + a (spurious) passing non-LLM leg — yet round-trip fails.
        val out = runner(db, legAReply = "SUPPORTED", legBReply = "SUPPORTED", nonLlm = passingNonLlm).audit(listOf(c))

        assertNotEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "bothSupported ALONE must NOT promote a DEFINITION to faithful — round-trip is the anchor",
        )
    }

    @Test
    fun `B5r-1 (c2) - SELF-ENTAILMENT GUARD - an agreed-REFUTED DEFINITION is never faithful even with a passing round-trip`(@TempDir tmp: Path) = runBlocking {
        // Belt-and-braces on the explicit guard: agreed-but-REFUTED must NEVER be faithful for ANY
        // kind. A DEFINITION whose round-trip passes but whose families AGREE on REFUTED is vetoed.
        val db = freshDb(tmp)
        val c = definitionClaim()
        seedStatus(db, c.kcId, VerificationStatus.pending)

        val out = runner(db, legAReply = "REFUTED", legBReply = "REFUTED", nonLlm = noneLeg).audit(listOf(c))

        assertNotEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "an agreed-REFUTED DEFINITION must NEVER be faithful (the agreed-REFUTED veto holds for prose too)",
        )
    }

    @Test
    fun `B5r-1 (d) - REGRESSION - an INVARIANT whose equational non-LLM leg did NOT run is never faithful`(@TempDir tmp: Path) = runBlocking {
        // The equational path is UNCHANGED: an INVARIANT keeps the strong requirement
        // (bothSupported && sympy.ran && sympy.pass && roundTrip.pass && !threw). A SYMPY leg that
        // could not run (ran=false) must NOT be promoted to faithful by the prose anchor — the
        // round-trip is NOT a substitute for the equational machine check on an equational claim.
        val db = freshDb(tmp)
        val c = claim()  // default = equational INVARIANT, round-trip passes
        seedStatus(db, c.kcId, VerificationStatus.pending)

        // A SYMPY-kind leg that did NOT run (e.g. python/sympy unavailable) — NOT a NONE leg.
        val sympyNotRun = NonLlmLeg {
            NonLlmResult(kind = NonLlmLegKind.SYMPY, ran = false, pass = false, detail = "sympy unavailable")
        }
        val out = runner(db, nonLlm = sympyNotRun).audit(listOf(c))

        assertNotEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "an INVARIANT whose SymPy leg did not run must NOT be faithful (round-trip is not a substitute)",
        )
    }

    @Test
    fun `B5r-1 (e) - guards still hold for a DEFINITION - no gold span floors, a thrown leg blocks faithful`(@TempDir tmp: Path) = runBlocking {
        // span==null still floors to the definitional-no-gold-span UNCERTAIN floor (NOT faithful)
        // even on the prose path; and a thrown leg still blocks faithful for a DEFINITION.
        val dbA = freshDb(tmp.resolve("nospan").also { java.nio.file.Files.createDirectories(it) })
        val noSpan = definitionClaim(source = SourceRef(doc = "pa-lecture-01", quote = goldQuote, page = 0, span = null))
        seedStatus(dbA, noSpan.kcId, VerificationStatus.pending)
        val a = runner(dbA, nonLlm = noneLeg).audit(listOf(noSpan))
        assertEquals(VerificationStatus.uncertain, a[0].newStatus, "no gold span ⇒ uncertain floor, never faithful (prose path too)")

        val dbB = freshDb(tmp.resolve("threw").also { java.nio.file.Files.createDirectories(it) })
        val c = definitionClaim()
        seedStatus(dbB, c.kcId, VerificationStatus.pending)
        // a thrown LLM leg must block faithful even when the round-trip would pass.
        val b = runner(dbB, legALlm = ThrowingLlm(), nonLlm = noneLeg).audit(listOf(c))
        assertNotEquals(VerificationStatus.faithful, b[0].newStatus, "a thrown leg blocks faithful for a DEFINITION too")
    }

    private fun VerificationRunner.AuditResult.claimKind(): String = claimId.substringAfter(':').substringBefore(':')

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
