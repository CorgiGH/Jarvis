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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase-2 WIRING test — proves the two built-but-unwired legs are REACHABLE from the PRODUCTION
 * entry point (the `reconcileContent` gradle-task main / [ContentReconcileCli.reconcileContent]), NOT by calling
 * [ContentReconcile.reconcileSourceSpans] / `setPending` directly.
 *
 * The whole point is to prove the functions are reachable from production:
 *  - Test W drives the D1 source-edit WATCHER through the entry: a faithful KC over a `_sources` doc
 *    that is then EDITED (the doc deleted so the cited quote no longer relocates — a real source
 *    edit that the validator still tolerates as a WARNING, so the reconcile is not aborted) must be
 *    NULLed + re-pended, dropping its served floor to UNVERIFIED.
 *  - Test S drives the Stage-9 setPending leg through the entry: a fresh UNVERIFIED KC flips to
 *    PENDING; idempotent on a re-run; a faithful KC is never regressed (H10).
 *
 * CLASS-KILLING: if a future change un-wires either leg from [ContentCli.reconcile] (removes the
 * `reconcileSourceSpans` call or the `reconcile`/setPending call), the corresponding test FAILS.
 *
 * The production entry resolves the tutor DB from the `JARVIS_TUTOR_DB` system property (mirroring
 * VerifyContentCli); each test points it at a hermetic temp SQLite and clears it in @AfterTest.
 */
class ContentReconcileCliWiringTest {

    // Page-one intro, a form-feed page break, then the cited quote at raw offsets [17, 81) on page 2
    // (span-exact: slice(sourceText, Span(17,81)) == quote, including the trailing period, so the
    // span-bearing ref passes the validator's diacritic-exact checkVerbatimSources gate).
    private val sourceText =
        "PAGE ONE intro.\n" +
            SourceOfRecord.PAGE_BREAK +
            "The size of representation must be mentioned for each data type. Trailing.\n"

    private val quote = "The size of representation must be mentioned for each data type."
    private val docId = "pa-lecture-01"
    private val kcId = "pa-kc-005"

    /** A standard (non-strict) KC with a single span-bearing source ref — span-exact at [17, 81). */
    private fun kc(): KnowledgeConcept = KnowledgeConcept(
        id = kcId, subject = "PA", name_ro = "RO", name_en = "Size",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 1.0, tier = 1, grounding_tier = "standard",
        source = listOf(SourceRef(doc = docId, quote = quote, page = 2, span = Span(17, 81))),
        version = 1,
    )

    // --- on-disk fixture content/ corpus (what the production ContentRepo loads) ----------------

    /** Write a minimal valid content dir under [root]: subjects.yaml + the KC yaml + the _sources md. */
    private fun writeCorpus(root: Path, kc: KnowledgeConcept, source: String) {
        val subjectDir = root.resolve("PA")
        Files.createDirectories(subjectDir.resolve("kcs"))
        Files.createDirectories(subjectDir.resolve("_sources"))

        root.resolve("subjects.yaml").writeText(
            """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
            """.trimIndent() + "\n",
        )

        subjectDir.resolve("kcs").resolve("${kc.id}.yaml").writeText(kcYaml(kc))
        subjectDir.resolve("_sources").resolve("$docId.md").writeText(source)
    }

    /** Serialize the fixture KC to the YAML shape ContentRepo.loadSubject parses. */
    private fun kcYaml(kc: KnowledgeConcept): String {
        val ref = kc.source.single()
        val span = ref.span!!
        return """
            id: ${kc.id}
            subject: ${kc.subject}
            name_ro: "${kc.name_ro}"
            name_en: "${kc.name_en}"
            cluster: ${kc.cluster}
            bloom_level: ${kc.bloom_level}
            difficulty: ${kc.difficulty}
            time_minutes: ${kc.time_minutes}
            exam_weight: ${kc.exam_weight}
            tier: ${kc.tier}
            grounding_tier: ${kc.grounding_tier}
            source:
              - doc: ${ref.doc}
                quote: "${ref.quote}"
                page: ${ref.page}
                span:
                  start: ${span.start}
                  end: ${span.end}
            version: ${kc.version}
        """.trimIndent() + "\n"
    }

    // --- DB harness (production entry resolves it from JARVIS_TUTOR_DB system property) ----------

    private var prevDbProp: String? = null

    private fun pointDbAt(path: Path): Database {
        prevDbProp = System.getProperty("JARVIS_TUTOR_DB")
        System.setProperty("JARVIS_TUTOR_DB", path.toString())
        val db = TutorDb.connect(path.toString())
        TutorMigration.migrate(db)
        return db
    }

    @AfterTest
    fun restoreDbProp() {
        if (prevDbProp == null) System.clearProperty("JARVIS_TUTOR_DB")
        else System.setProperty("JARVIS_TUTOR_DB", prevDbProp)
    }

    private fun rowOf(db: Database, id: String) = transaction(db) {
        KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq id }.single()
    }

    private fun statusOf(db: Database, id: String): String? = transaction(db) {
        KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq id }
            .singleOrNull()
            ?.get(KcVerificationStatusTable.status)
    }

    /** Seed a faithful + grounded B8 row carrying the live content_hash + source_span_hash. */
    private fun seedFaithful(db: Database, kc: KnowledgeConcept, src: String) {
        val contentHash = ContentReconcile.kcContentHash(kc)
        val sourceSpanHash =
            ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc)) { _, _ -> src }
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

    // === Test W — the D1 source-edit WATCHER is REACHABLE from the production entry =============

    @Test
    fun `W - reconcileContent entry runs the source-edit watcher - a real source edit NULLs hashes + re-pends + drops the served floor`(
        @TempDir tmp: Path,
    ) {
        val contentDir = tmp.resolve("content")
        writeCorpus(contentDir, kc(), sourceText)
        val db = pointDbAt(tmp.resolve("reconcile-w.db"))

        // The KC has already been audited faithful over the live source.
        seedFaithful(db, kc(), sourceText)

        // BEFORE: the KC serves the lecture badge (faithful + grounded + matching hashes + span hash).
        assertEquals(
            HonestFloor.FAITHFUL_TO_SOURCE, VerifyAdmin.servedHonestFloor(db, kcId, kc()),
            "a faithful KC over matching live source serves the lecture badge before any source edit",
        )

        // A REAL source edit: delete the _sources doc so the cited quote no longer relocates. The
        // validator tolerates an absent source as a WARNING (not an error) so the reconcile proceeds,
        // and the watcher's recomputed live hash (null — quote does not relocate) differs from stored.
        contentDir.resolve("PA").resolve("_sources").resolve("$docId.md").deleteExisting()

        // PRODUCTION ENTRY: the gradle reconcileContent task main.
        ContentReconcileCli.reconcileContent(arrayOf(contentDir.toString()))

        // The watcher fired through the entry: both hashes NULLed, the KC re-pended.
        val row = rowOf(db, kcId)
        assertNull(row[KcVerificationStatusTable.contentHash], "content_hash NULLed (fail-closed)")
        assertNull(row[KcVerificationStatusTable.sourceSpanHash], "source_span_hash NULLed (fail-closed)")
        assertEquals(
            VerificationStatus.pending.name, row[KcVerificationStatusTable.status],
            "the edited KC is re-pended for re-audit",
        )

        // AFTER: the real serve helper drops the KC to UNVERIFIED (no longer the lecture badge).
        assertEquals(
            HonestFloor.UNVERIFIED, VerifyAdmin.servedHonestFloor(db, kcId, kc()),
            "after a real source edit the disputed KC must NOT serve the lecture badge",
        )
    }

    // === Test S — the Stage-9 setPending leg is REACHABLE from the production entry =============

    @Test
    fun `S - reconcileContent entry runs Stage-9 reconcile - a fresh UNVERIFIED KC flips to PENDING, idempotent`(
        @TempDir tmp: Path,
    ) {
        val contentDir = tmp.resolve("content")
        writeCorpus(contentDir, kc(), sourceText)
        val db = pointDbAt(tmp.resolve("reconcile-s.db"))

        // No prior B8 row for the KC.
        assertNull(statusOf(db, kcId), "precondition: the fresh KC has no runtime status row yet")

        // PRODUCTION ENTRY, first run: UNVERIFIED -> PENDING.
        ContentReconcileCli.reconcileContent(arrayOf(contentDir.toString()))
        assertEquals(
            VerificationStatus.pending.name, statusOf(db, kcId),
            "Stage-9 reconcile (reached via the entry) sets a fresh KC to pending",
        )

        // Re-run: idempotent — still pending, exactly one row.
        ContentReconcileCli.reconcileContent(arrayOf(contentDir.toString()))
        assertEquals(VerificationStatus.pending.name, statusOf(db, kcId), "a re-run keeps pending (idempotent)")
        val rows = transaction(db) {
            KcVerificationStatusTable.selectAll().where { KcVerificationStatusTable.kcId eq kcId }.count()
        }
        assertEquals(1L, rows, "one B8 row per KC — the reconcile upserts, never duplicates")
    }

    @Test
    fun `S - reconcileContent entry NEVER regresses a faithful KC back to pending (H10)`(
        @TempDir tmp: Path,
    ) {
        val contentDir = tmp.resolve("content")
        writeCorpus(contentDir, kc(), sourceText)
        val db = pointDbAt(tmp.resolve("reconcile-h10.db"))

        // The KC is already audited faithful over the UNCHANGED live source (so the watcher is a no-op
        // and the Stage-9 leg must not regress it).
        seedFaithful(db, kc(), sourceText)

        ContentReconcileCli.reconcileContent(arrayOf(contentDir.toString()))

        assertEquals(
            VerificationStatus.faithful.name, statusOf(db, kcId),
            "a faithful KC over unchanged source must stay faithful through the entry (H10)",
        )
        assertEquals(
            HonestFloor.FAITHFUL_TO_SOURCE, VerifyAdmin.servedHonestFloor(db, kcId, kc()),
            "the unchanged faithful KC still serves the lecture badge after a reconcile",
        )
    }
}
