package jarvis.web

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.UsersTable
import jarvis.tutor.VerificationAuditTable
import jarvis.tutor.VerificationStatus
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * P0-2 watch (b) — the **resolveStatus regression-canary** (class-killer, NOT enumerate-the-siblings).
 *
 * EVERY served surface that gates on faithfulness — the grade gate, queue/today, mastery, mock-exam,
 * and the drill-serve H15 status — resolves a KC's runtime trust through the SINGLE chokepoint
 * [VerifyAdmin.resolveStatus] (it folds the D8 content-staleness gate + the D1 source-span presence
 * check + the D3 OPEN-dispute refusal, then every consumer compares the result `== faithful`). So a
 * single edit to `resolveStatus` that wrongly let a stale / NULL-hash / disputed `faithful` row serve
 * faithful would silently regress ALL FIVE surfaces at once.
 *
 * This canary pins the fail-CLOSED contract at that ONE chokepoint (so it can never silently weaken)
 * AND structurally asserts that each named consumer still routes through `resolveStatus` (so a consumer
 * can never quietly grow its own faithfulness check that bypasses the chokepoint).
 */
class ResolveStatusFailClosedCanaryTest {

    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-005.yaml").writeText(
            "id: pa-kc-005\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
                "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
                "exam_weight: 1.0\ntier: 1\nversion: 1\nverification_status: \"faithful\"\n" +
                "source:\n  - doc: pa-lecture-01\n    quote: \"Algorithm\"\n    page: 1\n" +
                "    span:\n      start: 0\n      end: 9\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("Algorithm is a finite sequence.\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, ReportWrongTable,
                KcVerificationStatusTable, VerificationAuditTable,
            )
        }
    }

    private fun loadKc(content: Path) =
        ContentRepo(content).loadSubject("PA").kcs.single { it.id == "pa-kc-005" }

    private fun seedFaithfulRow(
        db: org.jetbrains.exposed.sql.Database,
        contentHash: String?,
        sourceSpanHash: String?,
    ) = transaction(db) {
        KcVerificationStatusTable.insert {
            it[kcId] = "pa-kc-005"
            it[status] = VerificationStatus.faithful.name
            it[KcVerificationStatusTable.contentHash] = contentHash
            it[KcVerificationStatusTable.sourceSpanHash] = sourceSpanHash
            it[updatedAt] = Instant.now()
        }
    }

    // ── 1. the happy path: a FRESH faithful row (hash matches + source-span present) serves faithful ──
    @Test
    fun `a fresh faithful row with a matching content hash and present source-span resolves faithful`() {
        val dir = Files.createTempDirectory("canary-fresh")
        val content = dir.resolve("content"); seedContent(content)
        val db = freshDb(dir)
        val kc = loadKc(content)
        seedFaithfulRow(db, ContentReconcile.kcContentHash(kc), "spanhash01")
        assertEquals(VerificationStatus.faithful, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc),
            "the control: a genuinely fresh faithful row MUST serve faithful (else the canary proves nothing)")
    }

    // ── 2. STALE content hash (lecture edited after audit) ⇒ fail CLOSED (NOT faithful) ──
    @Test
    fun `a faithful row whose content hash no longer matches the live content fails CLOSED`() {
        val dir = Files.createTempDirectory("canary-stale")
        val content = dir.resolve("content"); seedContent(content)
        val db = freshDb(dir)
        val kc = loadKc(content)
        // A hash from BEFORE an edit ⇒ no longer matches the live content ⇒ stale. (varchar(16) cap.)
        seedFaithfulRow(db, "stalehash01", "spanhash01")
        assertNotEquals(VerificationStatus.faithful, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc),
            "a STALE content_hash must demote faithful (D8) — the badge never lies over edited text")
    }

    // ── 3. NULL content hash (legacy/partial row that cannot prove a match) ⇒ fail CLOSED ──
    @Test
    fun `a faithful row with a NULL content hash fails CLOSED`() {
        val dir = Files.createTempDirectory("canary-nullhash")
        val content = dir.resolve("content"); seedContent(content)
        val db = freshDb(dir)
        val kc = loadKc(content)
        seedFaithfulRow(db, null, "spanhash01")
        assertNotEquals(VerificationStatus.faithful, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc),
            "a NULL content_hash cannot prove a match ⇒ must fail CLOSED (D8)")
    }

    // ── 3b. NULL source-span hash (never source-audited / NULLed by the watcher) ⇒ fail CLOSED ──
    @Test
    fun `a faithful row with a NULL source-span hash fails CLOSED`() {
        val dir = Files.createTempDirectory("canary-nullspan")
        val content = dir.resolve("content"); seedContent(content)
        val db = freshDb(dir)
        val kc = loadKc(content)
        seedFaithfulRow(db, ContentReconcile.kcContentHash(kc), null)
        assertNotEquals(VerificationStatus.faithful, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc),
            "a NULL source_span_hash must fail CLOSED (D1) — the VPS can only presence-check it")
    }

    // ── 4. an OPEN report_wrong dispute outranks a fresh faithful row ⇒ fail CLOSED ──
    @Test
    fun `a fresh faithful row with an OPEN report_wrong dispute fails CLOSED`() {
        val dir = Files.createTempDirectory("canary-dispute")
        val content = dir.resolve("content"); seedContent(content)
        val db = freshDb(dir)
        val kc = loadKc(content)
        seedFaithfulRow(db, ContentReconcile.kcContentHash(kc), "spanhash01")
        transaction(db) {
            ReportWrongTable.insert {
                it[id] = TutorTypes.ulid()
                it[userId] = TutorTypes.ulid()
                it[kcId] = "pa-kc-005"
                it[cardId] = null
                it[gradeAttemptRaw] = "disputed"
                it[reportedAt] = Instant.now()
                it[resolution] = "OPEN"
            }
        }
        assertNotEquals(VerificationStatus.faithful, VerifyAdmin.resolveStatus(db, "pa-kc-005", kc),
            "an OPEN dispute outranks a faithful row (D3) ⇒ must fail CLOSED")
    }

    // ── 5. STRUCTURAL class-killer: every named consumer routes faithfulness through resolveStatus ──
    // If a consumer ever grows its OWN faithfulness check that bypasses the single chokepoint, the
    // fail-closed contract above no longer covers it. Pin that each consumer source file calls
    // VerifyAdmin.resolveStatus (the grade gate ALSO feeds it into VerificationGate.gate).
    @Test
    fun `every faithfulness consumer routes through the single resolveStatus chokepoint`() {
        val mainKt = Path.of("src/main/kotlin/jarvis")
        data class Consumer(val file: Path, val label: String)
        val consumers = listOf(
            Consumer(mainKt.resolve("web/QueueMasteryCalibrationRoutes.kt"), "queue/today + mastery"),
            Consumer(mainKt.resolve("web/MockExamRoutes.kt"), "mock-exam"),
            Consumer(mainKt.resolve("web/TutorRoutes.kt"), "grade gate + drill-serve H15"),
        )
        for (c in consumers) {
            assertTrue(Files.exists(c.file), "consumer source missing: ${c.file}")
            val src = c.file.readText()
            assertTrue(
                src.contains("resolveStatus("),
                "consumer '${c.label}' (${c.file}) must resolve faithfulness through the single " +
                    "VerifyAdmin.resolveStatus chokepoint — a bypass silently escapes the fail-closed canary",
            )
        }
    }
}
