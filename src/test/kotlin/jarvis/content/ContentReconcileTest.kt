package jarvis.content

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.VerificationClaim
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Batch-4 — curate-tutor Stage-9 reconcile. After `validateContent`, the reconcile:
 *  - emits each KC's [VerificationClaim](s) with a content-hash `claimId`
 *    `"{kcId}:{kind}:{sha256_8(content)}"` (M-CLAIM, reorder-stable);
 *  - sets `kc_verification_status = pending` DIRECTLY (UNVERIFIED→PENDING, NOT via the §2.5
 *    transition — an unaudited KC enters the audit queue at `pending`);
 *  - is IDEMPOTENT: re-running yields IDENTICAL claimIds and NEVER regresses a `faithful` KC back
 *    to `pending` (H10).
 *
 * Hermetic: injected clock, temp SQLite, no network.
 */
class ContentReconcileTest {

    // --- KC builders ---------------------------------------------------------------------------

    private fun strictKc(
        id: String = "pa-kc-005",
        invariant: String? = "1 + 1 + 1 = 3",
        graderRules: List<String> = listOf("sympy: simplify((1+1+1)-3) == 0", "must distinguish the three measures"),
    ): KnowledgeConcept = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "RO", name_en = "Value size",
        cluster = "c", bloom_level = "apply", difficulty = 3, time_minutes = 30,
        exam_weight = 1.0, tier = 3, grounding_tier = "strict",
        source = listOf(
            SourceRef(doc = "pa-lecture-01", quote = "There are three ways", page = 34, span = Span(16691, 16711), provenance = "vision-confirmed"),
        ),
        invariant = invariant,
        grader_rules = graderRules,
        version = 1,
    )

    private fun standardKc(id: String = "pa-kc-001"): KnowledgeConcept = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "RO", name_en = "Algorithm",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 25,
        exam_weight = 1.0, tier = 1, grounding_tier = "standard",
        source = listOf(
            SourceRef(doc = "pa-lecture-01", quote = "It does not exists a standard definition", page = 4),
        ),
        version = 1,
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs = kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    // --- DB harness ----------------------------------------------------------------------------

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("reconcile.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private val fixedClock: () -> Instant = { Instant.parse("2026-06-04T12:00:00Z") }

    private fun statusOf(db: Database, kcId: String): String? = transaction(db) {
        KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()
            ?.get(KcVerificationStatusTable.status)
    }

    private fun seedStatus(db: Database, kcId: String, status: VerificationStatus) = transaction(db) {
        KcVerificationStatusTable.insert {
            it[KcVerificationStatusTable.kcId] = kcId
            it[KcVerificationStatusTable.status] = status.name
            it[updatedAt] = Instant.parse("2026-06-01T00:00:00Z")
        }
    }

    // --- claim-emission (pure) -----------------------------------------------------------------

    @Test
    fun `claimId is the content-hash form kcId-KIND-sha256_8 of content`() {
        val claims = ContentReconcile.claimsFor(strictKc())
        assertTrue(claims.isNotEmpty(), "a strict KC with invariant + grader_rules + source emits claims")
        for (c in claims) {
            val parts = c.claimId.split(":")
            assertEquals(3, parts.size, "claimId must be kcId:KIND:hash8 — got ${c.claimId}")
            assertEquals(c.kcId, parts[0])
            assertEquals(c.kind.name, parts[1], "the middle segment is the UPPER ClaimKind name")
            assertEquals(8, parts[2].length, "the hash segment is 8 hex chars (sha256_8)")
            assertTrue(parts[2].all { it in "0123456789abcdef" }, "hash8 is lowercase hex")
            // content-hash: the hash matches sha256_8(content)
            assertEquals(ContentReconcile.sha256_8(c.content), parts[2])
        }
    }

    @Test
    fun `a strict KC emits an INVARIANT claim, a GRADER_RULE per rule, and a span-anchored DEFINITION`() {
        val claims = ContentReconcile.claimsFor(strictKc())
        val kinds = claims.map { it.kind }
        assertTrue(ClaimKind.INVARIANT in kinds, "invariant != null ⇒ an INVARIANT claim")
        // CONTRACT CHANGE (D-R8): the fixture's two rules are (1) a `sympy:` DIRECTIVE and (2) a PROSE
        // rule. The directive no longer emits a bare-string GRADER_RULE — instead it routes its
        // extracted equation to the SymPy leg as an EQUATIONAL GRADER_RULE; the prose rule stays an
        // LLM-judged GRADER_RULE. So there are still 2 GRADER_RULE claims, but the directive's claim now
        // carries an invariant (the extracted equation), never the literal "sympy: …" string.
        assertEquals(2, claims.count { it.kind == ClaimKind.GRADER_RULE }, "one GRADER_RULE claim per rule (directive → equational, prose → LLM)")
        assertTrue(ClaimKind.DEFINITION in kinds, "each source ref ⇒ a DEFINITION claim")
        // the INVARIANT/GRADER_RULE claims carry the KC's invariant + a span-bearing source.
        val inv = claims.single { it.kind == ClaimKind.INVARIANT }
        assertEquals("1 + 1 + 1 = 3", inv.content)
        assertNotNull(inv.source?.span, "an INVARIANT claim must carry a span-anchored source for the round-trip leg")
    }

    // --- D-R7: invariant_statement → the LLM/NLI hypothesis for the INVARIANT claim ----------------

    @Test
    fun `D-R7 an INVARIANT claim's content is invariant_statement when present (the NLI hypothesis), invariant stays the raw equation`() {
        val kc = strictKc().copy(
            invariant = "1 + 1 + 1 = 3",
            invariant_statement = "the uniform size of a 3-element array whose elements each have size 1 is 3",
        )
        val inv = ContentReconcile.claimsFor(kc).single { it.kind == ClaimKind.INVARIANT }
        assertEquals(
            "the uniform size of a 3-element array whose elements each have size 1 is 3",
            inv.content,
            "the INVARIANT claim's content (= the NLI hypothesis) is the plain-English invariant_statement",
        )
        assertEquals("1 + 1 + 1 = 3", inv.invariant, "the invariant (SymPy input) stays the raw equation regardless")
    }

    @Test
    fun `D-R7 an INVARIANT claim falls back to the equation as content when invariant_statement is absent or blank`() {
        val noStatement = ContentReconcile.claimsFor(strictKc().copy(invariant_statement = null))
            .single { it.kind == ClaimKind.INVARIANT }
        assertEquals("1 + 1 + 1 = 3", noStatement.content, "no invariant_statement ⇒ content falls back to the equation (current behavior)")
        assertEquals("1 + 1 + 1 = 3", noStatement.invariant)

        val blankStatement = ContentReconcile.claimsFor(strictKc().copy(invariant_statement = "   "))
            .single { it.kind == ClaimKind.INVARIANT }
        assertEquals("1 + 1 + 1 = 3", blankStatement.content, "blank invariant_statement ⇒ falls back to the equation")
    }

    // --- D-R8: a `sympy:` directive grader_rule is stripped from the NLI path -----------------------

    @Test
    fun `D-R8 a sympy-directive grader_rule emits an EQUATIONAL claim (extracted equation as invariant), NEVER a bare sympy hypothesis`() {
        val kc = strictKc(
            invariant = "1 + 1 + 1 = 3",
            graderRules = listOf("sympy: simplify((1 + 1 + 1) - 3) == 0  # uniform array size"),
        ).copy(invariant_statement = "the uniform size of a 3-element array of unit-size elements is 3")
        val rule = ContentReconcile.claimsFor(kc).single { it.kind == ClaimKind.GRADER_RULE }

        // The directive's equation is extracted and routed to the SymPy leg.
        assertEquals("1 + 1 + 1 = 3", rule.invariant, "the sympy directive's extracted equation feeds the SymPy leg")
        // The claim content is NEVER the literal directive — no NLI hypothesis may contain `sympy:`.
        assertFalse(rule.content.lowercase().contains("sympy:"), "a `sympy:` directive must NEVER reach NLI as a hypothesis")
        // Content = invariant_statement when present (so the NLI family judges the NL meaning).
        assertEquals("the uniform size of a 3-element array of unit-size elements is 3", rule.content)
    }

    @Test
    fun `D-R8 a sympy-directive grader_rule with no invariant_statement uses the extracted equation as content`() {
        val kc = strictKc(
            invariant = "t + t + t = 3*t",
            graderRules = listOf("sympy: simplify((t + t + t) - 3*t) == 0  # step costs sum"),
        ).copy(invariant_statement = null)
        val rule = ContentReconcile.claimsFor(kc).single { it.kind == ClaimKind.GRADER_RULE }
        assertEquals("t + t + t = 3*t", rule.invariant, "extracted from simplify((A)-(B))==0 ⇒ A = B")
        assertEquals("t + t + t = 3*t", rule.content, "no invariant_statement ⇒ content = the extracted equation, not the directive")
        assertFalse(rule.content.lowercase().contains("sympy:"))
    }

    @Test
    fun `D-R8 an UNPARSEABLE sympy directive emits NO claim (never feeds garbage to NLI)`() {
        val kc = strictKc(graderRules = listOf("sympy: assert is_valid(tree)  # not an equation at all"))
        val rules = ContentReconcile.claimsFor(kc).filter { it.kind == ClaimKind.GRADER_RULE }
        assertTrue(rules.isEmpty(), "a sympy directive with no extractable equation emits NO GRADER_RULE claim (no garbage to NLI)")
    }

    @Test
    fun `D-R8 a bare lhs=rhs equation grader_rule is treated as equational, not an NLI hypothesis`() {
        // A rule that is itself a plain `lhs = rhs` equation (no sympy: prefix) is also a directive:
        // it routes to SymPy with the equation as its invariant, and its content is NOT the bare equation
        // string fed to NLI as prose — content = invariant_statement when present.
        val kc = strictKc(graderRules = listOf("1 + 1 + 1 = 3"))
            .copy(invariant_statement = "three unit-size elements sum to size 3")
        val rule = ContentReconcile.claimsFor(kc).single { it.kind == ClaimKind.GRADER_RULE }
        assertEquals("1 + 1 + 1 = 3", rule.invariant, "a plain equation rule feeds the SymPy leg")
        assertEquals("three unit-size elements sum to size 3", rule.content, "its NLI hypothesis = invariant_statement, not the bare equation")
    }

    @Test
    fun `D-R8 a PROSE grader_rule stays an LLM-judged prose claim (invariant null, content = the rule)`() {
        val kc = strictKc(graderRules = listOf("the answer must distinguish the three size measures"))
        val rule = ContentReconcile.claimsFor(kc).single { it.kind == ClaimKind.GRADER_RULE }
        assertEquals(null, rule.invariant, "a prose rule carries no equational invariant ⇒ stays the LLM/round-trip path")
        assertEquals("the answer must distinguish the three size measures", rule.content, "a prose rule's content is the rule text verbatim")
    }

    @Test
    fun `claim emission is reorder-stable - shuffling grader_rules does not change the claimId set`() {
        val a = ContentReconcile.claimsFor(strictKc(graderRules = listOf("rule-A", "rule-B", "rule-C")))
        val b = ContentReconcile.claimsFor(strictKc(graderRules = listOf("rule-C", "rule-A", "rule-B")))
        assertEquals(a.map { it.claimId }.toSet(), b.map { it.claimId }.toSet(), "claimIds are content-addressed, order-independent")
    }

    // --- kcContentHash (D8 — the staleness fingerprint) ----------------------------------------

    @Test
    fun `kcContentHash is deterministic and 8 lowercase-hex chars`() {
        val kc = strictKc()
        val h1 = ContentReconcile.kcContentHash(kc)
        val h2 = ContentReconcile.kcContentHash(kc)
        assertEquals(h1, h2, "hashing the same KC twice is deterministic")
        assertEquals(8, h1.length, "the KC content hash is 8 hex chars (sha256_8 form)")
        assertTrue(h1.all { it in "0123456789abcdef" }, "lowercase hex")
    }

    @Test
    fun `kcContentHash is reorder-stable - shuffling grader_rules does not change it`() {
        val a = ContentReconcile.kcContentHash(strictKc(graderRules = listOf("rule-A", "rule-B", "rule-C")))
        val b = ContentReconcile.kcContentHash(strictKc(graderRules = listOf("rule-C", "rule-A", "rule-B")))
        assertEquals(a, b, "the KC content hash is derived from the content-addressed claim set, order-independent")
    }

    @Test
    fun `kcContentHash CHANGES when an audited claim text changes (C1 to C2)`() {
        val c1 = ContentReconcile.kcContentHash(strictKc(invariant = "1 + 1 + 1 = 3"))
        val c2 = ContentReconcile.kcContentHash(strictKc(invariant = "1 + 1 + 1 = 4"))
        assertNotEquals(c1, c2, "editing a claim's text must change the KC content hash (the staleness fingerprint)")
    }

    @Test
    fun `kcContentHash CHANGES when invariant_statement changes (D-R7 staleness)`() {
        val base = strictKc().copy(invariant_statement = "size of a 3-element unit array is 3")
        val edited = strictKc().copy(invariant_statement = "size of a 3-element unit array is THREE")
        assertNotEquals(
            ContentReconcile.kcContentHash(base),
            ContentReconcile.kcContentHash(edited),
            "editing invariant_statement must re-trigger staleness (folded into the content hash)",
        )
        // and adding an invariant_statement where there was none also changes the hash.
        assertNotEquals(
            ContentReconcile.kcContentHash(strictKc().copy(invariant_statement = null)),
            ContentReconcile.kcContentHash(base),
            "adding an invariant_statement changes the content hash",
        )
    }

    @Test
    fun `kcContentHash CHANGES when a cited span changes`() {
        val kcA = strictKc()
        val kcB = strictKc().let { kc ->
            kc.copy(source = kc.source.map { it.copy(span = Span(99999, 100000)) })
        }
        assertNotEquals(
            ContentReconcile.kcContentHash(kcA),
            ContentReconcile.kcContentHash(kcB),
            "moving a cited span must change the KC content hash (span is part of the fingerprint)",
        )
    }

    @Test
    fun `kcContentHash over a KC equals the hash over its emitted claims`() {
        val kc = strictKc()
        assertEquals(
            ContentReconcile.kcContentHash(kc),
            ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc)),
            "the KC-level hash and the claim-list hash are the SAME function (audit-side == serve-side)",
        )
    }

    // --- reconcile (DB) ------------------------------------------------------------------------

    @Test
    fun `reconcile sets an unseeded KC to pending (UNVERIFIED to PENDING directly)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val report = ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        assertEquals("pending", statusOf(db, "pa-kc-005"), "an unaudited KC enters the audit queue at pending")
        assertTrue(report.claims.isNotEmpty())
        assertTrue(report.pendingSet.contains("pa-kc-005"))
    }

    @Test
    fun `reconcile twice yields identical claimIds`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val r1 = ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        val r2 = ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        assertEquals(
            r1.claims.map { it.claimId }.sorted(),
            r2.claims.map { it.claimId }.sorted(),
            "re-running reconcile emits the SAME claimIds",
        )
    }

    @Test
    fun `reconcile NEVER regresses a faithful KC back to pending (H10)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // The KC has already been audited faithful in the runtime B8 table.
        seedStatus(db, "pa-kc-005", VerificationStatus.faithful)

        ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)

        assertEquals("faithful", statusOf(db, "pa-kc-005"), "a faithful KC must NOT be regressed to pending by reconcile (H10)")
    }

    @Test
    fun `reconcile does not regress uncertain or failed runtime statuses either`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-005", VerificationStatus.uncertain)
        seedStatus(db, "pa-kc-006", VerificationStatus.failed)

        ContentReconcile.reconcile(
            listOf(loaded(strictKc(id = "pa-kc-005"), strictKc(id = "pa-kc-006"))),
            db, fixedClock,
        )

        // Only UNVERIFIED → PENDING is moved directly; an already-audited status is preserved.
        assertEquals("uncertain", statusOf(db, "pa-kc-005"))
        assertEquals("failed", statusOf(db, "pa-kc-006"))
    }

    @Test
    fun `reconcile moves an explicit unverified row to pending`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-005", VerificationStatus.unverified)
        ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        assertEquals("pending", statusOf(db, "pa-kc-005"), "an explicit unverified seed is promoted to pending")
    }

    @Test
    fun `reconcile is idempotent on the pending status - a second pass keeps pending`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        ContentReconcile.reconcile(listOf(loaded(strictKc())), db, fixedClock)
        assertEquals("pending", statusOf(db, "pa-kc-005"))
        // exactly one row per kc (upsert, never duplicate)
        val rows = transaction(db) {
            KcVerificationStatusTable.selectAll()
                .where { KcVerificationStatusTable.kcId eq "pa-kc-005" }
                .count()
        }
        assertEquals(1L, rows, "one B8 row per KC — reconcile upserts, never duplicates")
    }

    @Test
    fun `reconcile over the real PA corpus sets the authored strict KCs to pending`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = ContentRepo(Path.of("content"))
        val pa = repo.loadSubject("PA")

        val report = ContentReconcile.reconcile(listOf(pa), db, fixedClock)

        // The two authored strict KCs (pa-kc-005, pa-kc-006) are reconciled to pending and emit
        // span-anchored INVARIANT claims for the audit.
        assertEquals("pending", statusOf(db, "pa-kc-005"))
        assertEquals("pending", statusOf(db, "pa-kc-006"))
        val inv005 = report.claims.filter { it.kcId == "pa-kc-005" && it.kind == ClaimKind.INVARIANT }
        assertTrue(inv005.isNotEmpty(), "pa-kc-005 emits an INVARIANT claim")
        assertNotNull(inv005.first().source?.span, "pa-kc-005's INVARIANT claim is span-anchored")

        // D-R8 over the REAL corpus: pa-kc-005's `sympy:` directive grader_rule
        // ("sympy: simplify((1 + 1 + 1) - 3) == 0  # …") is routed to the SymPy leg with the EXTRACTED
        // equation as its invariant — and its content NEVER contains the literal `sympy:` string (no
        // garbage to NLI). NO emitted claim anywhere may carry a `sympy:` hypothesis.
        val gr005 = report.claims.filter { it.kcId == "pa-kc-005" && it.kind == ClaimKind.GRADER_RULE }
        val equational = gr005.filter { it.invariant != null }
        assertTrue(equational.isNotEmpty(), "pa-kc-005's sympy directive emits an EQUATIONAL grader_rule (invariant set)")
        assertEquals("1 + 1 + 1 = 3", equational.first().invariant, "the directive's extracted equation matches the KC invariant")
        assertTrue(
            report.claims.none { it.content.lowercase().contains("sympy:") },
            "NO emitted claim may contain a `sympy:` directive as its NLI hypothesis (D-R8)",
        )
        // pa-kc-006's directive extracts `t + t + t = 3*t` too.
        val gr006 = report.claims.filter { it.kcId == "pa-kc-006" && it.kind == ClaimKind.GRADER_RULE && it.invariant != null }
        assertEquals("t + t + t = 3*t", gr006.first().invariant, "pa-kc-006's sympy directive extracts t + t + t = 3*t")
    }
}
