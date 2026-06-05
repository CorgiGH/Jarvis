package jarvis.content

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.HonestFloor
import jarvis.web.VerifyAdmin
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D1 (council-corrected) — the SEPARATE `source_span_hash` column + the PC-side source-edit watcher.
 *
 * The HOLE D1 closes: `content_hash` fingerprints the YAML quote+span+doc-FILENAME but never the
 * BYTES of `_sources/{doc}.md`. Edit / re-OCR the source after an audit (YAML unchanged) ⇒
 * `rowHash == currentHash` ⇒ the badge served over a quote that no longer relocates. The serve path
 * (VPS) has no `_sources` bytes (D7) so it can only PRESENCE-check the column; the actual edit
 * DETECTION is PC-side ([ContentReconcile.reconcileSourceSpans]).
 *
 * Hermetic: temp SQLite, an in-test fake source resolver. No network, no real corpus dependency.
 */
class SourceSpanHashTest {

    // A live source carrying the cited quote (with form-feed so a LIVE anchor exists).
    private val sourceText =
        "PAGE ONE intro.\n" +
            SourceOfRecord.PAGE_BREAK +
            "The size of representation must be mentioned for each data type. Trailing.\n"

    private val quote = "The size of representation must be mentioned for each data type."

    private fun kc(): KnowledgeConcept = KnowledgeConcept(
        id = "pa-kc-005", subject = "PA", name_ro = "RO", name_en = "Size",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 1.0, tier = 1, grounding_tier = "standard",
        source = listOf(SourceRef(doc = "pa-lecture-01", quote = quote, page = 2, span = Span(17, 80))),
        version = 1,
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("sourcespan.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private val fixedClock: () -> Instant = { Instant.parse("2026-06-05T12:00:00Z") }

    /** Seed a faithful + grounded B8 row carrying the live content_hash + source_span_hash. */
    private fun seedFaithful(db: Database, kc: KnowledgeConcept, src: String) {
        val contentHash = ContentReconcile.kcContentHash(kc)
        val sourceSpanHash = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc)) { _, _ -> src }
        assertNotNull(sourceSpanHash, "the cited quote must relocate so a source_span_hash exists")
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kc.id
                it[status] = VerificationStatus.faithful.name
                it[KcVerificationStatusTable.contentHash] = contentHash
                it[KcVerificationStatusTable.sourceSpanHash] = sourceSpanHash
                it[lectureGrounded] = true
                it[updatedAt] = Instant.parse("2026-06-04T00:00:00Z")
            }
        }
    }

    private fun rowOf(db: Database, kcId: String) = transaction(db) {
        KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq kcId }.single()
    }

    // --- pure source-span hash ------------------------------------------------------------------

    @Test
    fun `sourceSpanHashOf is sha256_8 over the FOLDED located slice, deterministic`() {
        val h1 = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc())) { _, _ -> sourceText }
        val h2 = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc())) { _, _ -> sourceText }
        assertNotNull(h1)
        assertEquals(h1, h2, "deterministic over the same source")
        assertEquals(8, h1!!.length, "sha256_8 width")
    }

    @Test
    fun `sourceSpanHashOf is NULL when the cited quote no longer relocates (fail-closed signal)`() {
        val absent = "A SOURCE FILE WITH NONE OF THE CITED TEXT IN IT whatsoever zzz.\n"
        val h = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc())) { _, _ -> absent }
        assertNull(h, "a quote that does not relocate ⇒ no source-span hash ⇒ serve fails closed")
    }

    // --- D1 (b): whitespace-only edit does NOT flip the hash (pa-kc anti-regression guard) -------

    @Test
    fun `D1(b) a whitespace-only source edit leaves source_span_hash UNCHANGED (fold tolerates it)`() {
        val base = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc())) { _, _ -> sourceText }
        // Re-OCR churn: collapse + double spaces + a newline mid-quote. The round-trip fold absorbs it.
        val rewhitespaced = sourceText
            .replace("must be mentioned", "must  be\n  mentioned")
            .replace("each data type", "each   data type")
        val edited = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc())) { _, _ -> rewhitespaced }
        assertEquals(
            base, edited,
            "a whitespace/re-OCR-only edit must NOT change source_span_hash (hashing the FOLDED slice tolerates it) — this is the pa-kc anti-regression guard, RED if raw bytes are hashed",
        )
    }

    // --- D1 (a): a real source edit ⇒ reconcile NULLs both hashes + re-pends ⇒ serve UNVERIFIED ---

    @Test
    fun `D1(a) a real source edit (quote no longer relocates) NULLs content_hash + source_span_hash and re-pends`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedFaithful(db, kc(), sourceText)

        // BEFORE: the KC serves the lecture badge (faithful + grounded + matching hashes + present span hash).
        assertEquals(
            HonestFloor.FAITHFUL_TO_SOURCE, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc()),
            "a faithful KC over matching live source serves the lecture badge before any source edit",
        )

        // The source file is EDITED so the cited quote no longer relocates (a real semantic edit).
        val editedSource = "The lecture was rewritten and the cited sentence was deleted entirely.\n"
        val result = ContentReconcile.reconcileSourceSpans(
            listOf(loaded(kc())), db, rawSourceFor = { _, _ -> editedSource }, clock = fixedClock,
        )

        assertTrue(result.invalidated.contains("pa-kc-005"), "the watcher must fire on the edited KC")
        val row = rowOf(db, "pa-kc-005")
        assertNull(row[KcVerificationStatusTable.contentHash], "content_hash NULLed (fail-closed)")
        assertNull(row[KcVerificationStatusTable.sourceSpanHash], "source_span_hash NULLed (fail-closed)")
        assertEquals(VerificationStatus.pending.name, row[KcVerificationStatusTable.status], "KC re-pended for audit")

        // AFTER: the serve floor fails CLOSED to UNVERIFIED.
        assertEquals(
            HonestFloor.UNVERIFIED, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc()),
            "after a real source edit the disputed KC must NOT serve the lecture badge",
        )
    }

    @Test
    fun `D1(b) a whitespace-only source edit does NOT invalidate the KC (reconcile is a no-op, stays faithful)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedFaithful(db, kc(), sourceText)

        val rewhitespaced = sourceText
            .replace("must be mentioned", "must  be\n  mentioned")
            .replace("each data type", "each   data type")
        val result = ContentReconcile.reconcileSourceSpans(
            listOf(loaded(kc())), db, rawSourceFor = { _, _ -> rewhitespaced }, clock = fixedClock,
        )

        assertTrue(result.invalidated.isEmpty(), "a whitespace-only edit folds-equal ⇒ no invalidation (idempotent)")
        val row = rowOf(db, "pa-kc-005")
        assertNotNull(row[KcVerificationStatusTable.contentHash], "content_hash preserved on a no-op re-run (H10)")
        assertNotNull(row[KcVerificationStatusTable.sourceSpanHash], "source_span_hash preserved on a no-op re-run")
        assertEquals(VerificationStatus.faithful.name, row[KcVerificationStatusTable.status], "the KC stays faithful")
        assertEquals(
            HonestFloor.FAITHFUL_TO_SOURCE, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc()),
            "the KC still serves the lecture badge after a whitespace-only re-OCR (pa-kc anti-regression)",
        )
    }

    // --- D1 serve gate: a NULL source_span_hash fails CLOSED ------------------------------------

    @Test
    fun `D1 a faithful row with a NULL source_span_hash fails CLOSED at serve (presence check)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // Seed faithful + matching content_hash but a NULL source_span_hash (legacy/partial row).
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = "pa-kc-005"
                it[status] = VerificationStatus.faithful.name
                it[contentHash] = ContentReconcile.kcContentHash(kc())
                it[sourceSpanHash] = null
                it[lectureGrounded] = true
                it[updatedAt] = Instant.parse("2026-06-04T00:00:00Z")
            }
        }
        assertEquals(
            HonestFloor.UNVERIFIED, VerifyAdmin.servedHonestFloor(db, "pa-kc-005", kc()),
            "a NULL source_span_hash must fail CLOSED at serve exactly like a NULL content_hash (D1)",
        )
        assertEquals(
            VerificationStatus.unverified, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc()),
            "resolveStatus also fails closed on a NULL source_span_hash",
        )
    }
}
