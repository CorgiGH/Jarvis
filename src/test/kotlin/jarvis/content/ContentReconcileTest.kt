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
        assertEquals(2, claims.count { it.kind == ClaimKind.GRADER_RULE }, "one GRADER_RULE claim per grader rule")
        assertTrue(ClaimKind.DEFINITION in kinds, "each source ref ⇒ a DEFINITION claim")
        // the INVARIANT/GRADER_RULE claims carry the KC's invariant + a span-bearing source.
        val inv = claims.single { it.kind == ClaimKind.INVARIANT }
        assertEquals("1 + 1 + 1 = 3", inv.content)
        assertNotNull(inv.source?.span, "an INVARIANT claim must carry a span-anchored source for the round-trip leg")
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
    }
}
