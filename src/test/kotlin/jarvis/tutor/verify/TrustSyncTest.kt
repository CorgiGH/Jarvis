package jarvis.tutor.verify

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D9 — the PC→VPS trust-verdict sync, against the FROZEN audit-column allowlist
 * (interface-signatures-lock §I.1): export carries EXACTLY {kc_id, status, last_audit_run_id,
 * content_hash, lecture_grounded, source_span_hash, updated_at}; import is idempotent (same
 * dump twice = 0 applied) and monotonic (an older dump never overwrites a newer verdict);
 * report_wrong is NEVER touched; never the whole tutor.db.
 */
class TrustSyncTest {

    private fun db(tmp: Path, name: String): Database {
        val d = TutorDb.connect(tmp.resolve(name).toString())
        TutorMigration.migrate(d)
        return d
    }

    private fun seedVerdict(db: Database, kcId: String, status: String, runId: String, at: Instant) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status
                it[lastAuditRunId] = runId
                it[contentHash] = "00aa11bb22cc33dd"
                it[lectureGrounded] = true
                it[sourceSpanHash] = "44ee55ff66aa77bb"
                it[updatedAt] = at
            }
        }
    }

    @Test
    fun `export carries exactly the frozen allowlist columns`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))

        val dump = TrustSync.export(pc)

        assertEquals(1, dump.rows.size)
        val out = tmp.resolve("dump.json")
        TrustSync.writeDump(dump, out)
        val text = Files.readString(out)
        for (key in listOf(
            "kc_id", "status", "last_audit_run_id",
            "content_hash", "lecture_grounded", "source_span_hash", "updated_at",
        )) {
            assertTrue(key in text, "dump must carry $key")
        }
        assertFalse("report_wrong" in text, "D9 NEVER syncs report_wrong")
        assertFalse("grade_attempt_raw" in text)
    }

    @Test
    fun `export with a run filter carries only that audit run's rows`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        seedVerdict(pc, "pa-kc-002", "faithful", "run-2", Instant.parse("2026-06-11T11:00:00Z"))

        val dump = TrustSync.export(pc, auditRunId = "run-2")

        assertEquals(listOf("pa-kc-002"), dump.rows.map { it.kc_id })
    }

    // ── Task 10: import contract — idempotent, monotonic, report_wrong untouched ──────────────

    @Test
    fun `import inserts then re-import is a no-op (idempotent)`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        seedVerdict(pc, "pa-kc-002", "uncertain", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        val dump = TrustSync.export(pc)

        val first = TrustSync.importDump(vps, dump)
        assertEquals(2, first.inserted)
        assertEquals(0, first.updated)
        assertEquals(0, first.skipped)

        val second = TrustSync.importDump(vps, dump)
        assertEquals(0, second.inserted)
        assertEquals(0, second.updated)
        assertEquals(2, second.skipped)

        val statuses = transaction(vps) {
            KcVerificationStatusTable.selectAll()
                .associate { it[KcVerificationStatusTable.kcId] to it[KcVerificationStatusTable.status] }
        }
        assertEquals(mapOf("pa-kc-001" to "faithful", "pa-kc-002" to "uncertain"), statuses)
    }

    @Test
    fun `an older dump never overwrites a newer verdict`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        val oldDump = TrustSync.export(pc)
        // the VPS later holds a NEWER verdict (run-2)
        seedVerdict(vps, "pa-kc-001", "failed", "run-2", Instant.parse("2026-06-12T09:00:00Z"))

        val report = TrustSync.importDump(vps, oldDump)

        assertEquals(0, report.applied)
        assertEquals(1, report.skipped)
        val status = transaction(vps) {
            KcVerificationStatusTable.selectAll().single()[KcVerificationStatusTable.status]
        }
        assertEquals("failed", status, "a stale dump must not clobber the newer verdict")
    }

    @Test
    fun `a newer re-audit run updates the same kc (re-audits stay runnable, keyed by audit_run_id)`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "uncertain", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        TrustSync.importDump(vps, TrustSync.export(pc))

        // PC re-audits: same KC, NEW run id, new verdict, later updated_at
        transaction(pc) {
            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq "pa-kc-001" }) {
                it[status] = "faithful"
                it[lastAuditRunId] = "run-2"
                it[updatedAt] = Instant.parse("2026-06-12T10:00:00Z")
            }
        }

        val report = TrustSync.importDump(vps, TrustSync.export(pc))

        assertEquals(1, report.updated)
        val row = transaction(vps) { KcVerificationStatusTable.selectAll().single() }
        assertEquals("faithful", row[KcVerificationStatusTable.status])
        assertEquals("run-2", row[KcVerificationStatusTable.lastAuditRunId])
    }

    @Test
    fun `import never touches report_wrong`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))

        fun reportWrongCount(): Long = transaction(vps) {
            var n = -1L
            exec("SELECT COUNT(*) FROM report_wrong") { rs -> if (rs.next()) n = rs.getLong(1) }
            n
        }
        val before = reportWrongCount()
        assertEquals(0L, before)

        TrustSync.importDump(vps, TrustSync.export(pc))

        assertEquals(before, reportWrongCount(), "D9 must not write report_wrong (frozen allowlist)")
    }
}
