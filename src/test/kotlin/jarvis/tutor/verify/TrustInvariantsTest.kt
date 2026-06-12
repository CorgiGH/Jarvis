package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.ExamScheduleRowsTable
import jarvis.tutor.GradingModelsTable
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-1 Task 6 — the `gradle trustInvariants` core: INV-3.3 / SCHEMA / FLIP / PARITY.
 * Uses the REAL in-repo corpus (content/, gradle test workingDir = project root) so the PARITY
 * leg exercises the exact ContentRepo + kcContentHashOf path the serve gate uses.
 */
class TrustInvariantsTest {

    private val contentDir: Path = Path.of("content")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("inv.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun insertVerdict(db: Database, kcId: String, hash: String?) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = "faithful"
                it[lastAuditRunId] = "run-test-1"
                it[contentHash] = hash
                it[lectureGrounded] = true
                it[sourceSpanHash] = "0123456789abcdef"
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun realHashOf(kcId: String): String {
        val repo = ContentRepo(contentDir)
        val kc = repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .single { it.id == kcId }
        return ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
    }

    private val sweepAnchor =
        "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"

    /** Minimal Plan-2 data so INV-3.4 is green: 1 grade model + 12 sweep-anchored schedule rows. */
    private fun seedPlan2Meta(db: Database) {
        transaction(db) {
            GradingModelsTable.insert {
                it[id] = "gm-x"; it[subject] = "ALO"; it[variant] = null; it[isPrimary] = true
                it[formula] = "F"; it[passRuleJson] = "{}"; it[evidenceTier] = "official-site"
                it[GradingModelsTable.sourceUrl] = "https://edu.info.uaic.ro/x"
                it[sweepRef] = "verified-grade-models-exam-schedule.md#alo"
                it[createdAt] = Instant.now()
            }
            repeat(12) { i ->
                ExamScheduleRowsTable.insert {
                    it[id] = "es-$i"; it[subject] = "ALO"; it[rawDiscipline] = "d"
                    it[examType] = "examen"; it[startAt] = Instant.now(); it[datePrecision] = "exact"
                    it[ExamScheduleRowsTable.sourceRef] = sweepAnchor; it[createdAt] = Instant.now()
                }
            }
        }
    }

    @Test
    fun `all green when a faithful row carries the recomputed corpus hash`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))
        seedPlan2Meta(db)

        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)

        assertEquals(emptyList(), failures)
    }

    @Test
    fun `zero faithful rows fails the FLIP invariant`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "FLIP" }, "$failures")
    }

    @Test
    fun `tampered content_hash fails PARITY (machine parity, no human spot-check)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", "ffffffffffffffff")
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "PARITY" && it.detail.contains("pa-kc-001") }, "$failures")
    }

    @Test
    fun `card floor violation surfaces INV-3-3`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 800)
        assertTrue(failures.any { it.check == "INV-3.3" }, "$failures")
    }
}
