package jarvis.tutor.verify

import jarvis.tutor.KcVerificationStatusTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * D9 — the PC→VPS trust-verdict sync (spec §9.2 gate 2 step 4; FROZEN allowlist,
 * interface-signatures-lock §I.1):
 *
 *   "the set D9 surgically upserts PC→VPS is exactly {status, content_hash, lecture_grounded,
 *    source_span_hash, last_audit_run_id}. D9 NEVER syncs report_wrong rows and NEVER the whole
 *    tutor.db. These five plus the PK kc_id + bookkeeping updated_at are the table."
 *
 * Export runs PC-side after a verifyContent batch (`jarvis trust-export`); import runs on the
 * VPS via the dist binary (`jarvis trust-import <file>`). Both idempotent:
 *  - re-importing the same dump applies 0 changes (skip when existing.updated_at >= incoming);
 *  - an OLDER dump never overwrites a NEWER verdict (same monotonic guard);
 *  - rows carry last_audit_run_id, so every applied verdict is keyed to the audit run that
 *    produced it (re-audits produce a new run id + later updated_at and flow through).
 * The serve gate (VerifyAdmin.resolveStatus) independently re-checks content_hash against the
 * VPS's own content/ at read time, so a verdict synced over stale content fails CLOSED to
 * unverified — the import cannot create a lying badge.
 */
object TrustSync {

    @Serializable
    data class VerdictRow(
        val kc_id: String,
        val status: String,
        val last_audit_run_id: String?,
        val content_hash: String?,
        val lecture_grounded: Boolean?,
        val source_span_hash: String?,
        val updated_at: String, // ISO-8601 Instant
    )

    @Serializable
    data class VerdictDump(
        val version: Int = 1,
        val exported_at: String,
        val rows: List<VerdictRow>,
    )

    data class ImportReport(val inserted: Int, val updated: Int, val skipped: Int) {
        val applied: Int get() = inserted + updated
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** All verdict rows (or only those of [auditRunId]), kc_id-sorted for a deterministic dump. */
    fun export(db: Database, auditRunId: String? = null, clock: () -> Instant = Instant::now): VerdictDump =
        transaction(db) {
            val rows = KcVerificationStatusTable.selectAll()
                .map {
                    VerdictRow(
                        kc_id = it[KcVerificationStatusTable.kcId],
                        status = it[KcVerificationStatusTable.status],
                        last_audit_run_id = it[KcVerificationStatusTable.lastAuditRunId],
                        content_hash = it[KcVerificationStatusTable.contentHash],
                        lecture_grounded = it[KcVerificationStatusTable.lectureGrounded],
                        source_span_hash = it[KcVerificationStatusTable.sourceSpanHash],
                        updated_at = it[KcVerificationStatusTable.updatedAt].toString(),
                    )
                }
                .filter { auditRunId == null || it.last_audit_run_id == auditRunId }
                .sortedBy { it.kc_id }
            VerdictDump(exported_at = clock().toString(), rows = rows)
        }

    /**
     * Surgical upsert of [dump] into kc_verification_status. Touches NOTHING else (no
     * report_wrong, no other table). Idempotent + monotonic: a row is skipped when the existing
     * row's updated_at >= the incoming row's (same dump re-applied, or a stale dump).
     */
    fun importDump(db: Database, dump: VerdictDump): ImportReport {
        var inserted = 0
        var updated = 0
        var skipped = 0
        transaction(db) {
            for (row in dump.rows) {
                val incoming = Instant.parse(row.updated_at)
                val existing = KcVerificationStatusTable.selectAll()
                    .where { KcVerificationStatusTable.kcId eq row.kc_id }
                    .singleOrNull()
                when {
                    existing == null -> {
                        KcVerificationStatusTable.insert {
                            it[kcId] = row.kc_id
                            it[status] = row.status
                            it[lastAuditRunId] = row.last_audit_run_id
                            it[contentHash] = row.content_hash
                            it[lectureGrounded] = row.lecture_grounded
                            it[sourceSpanHash] = row.source_span_hash
                            it[updatedAt] = incoming
                        }
                        inserted++
                    }
                    existing[KcVerificationStatusTable.updatedAt] >= incoming -> skipped++
                    else -> {
                        KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq row.kc_id }) {
                            it[status] = row.status
                            it[lastAuditRunId] = row.last_audit_run_id
                            it[contentHash] = row.content_hash
                            it[lectureGrounded] = row.lecture_grounded
                            it[sourceSpanHash] = row.source_span_hash
                            it[updatedAt] = incoming
                        }
                        updated++
                    }
                }
            }
        }
        return ImportReport(inserted, updated, skipped)
    }

    fun writeDump(dump: VerdictDump, out: Path) {
        Files.writeString(out, json.encodeToString(dump))
    }

    fun readDump(path: Path): VerdictDump = json.decodeFromString(Files.readString(path))
}

/** Thin process shells for the Main.kt subcommands (mirrors ContentReconcileCli's split). */
object TrustSyncCli {

    private fun connectDb(): org.jetbrains.exposed.sql.Database {
        val dbPath = System.getenv("JARVIS_TUTOR_DB")
            ?: System.getProperty("JARVIS_TUTOR_DB")
            ?: jarvis.Config.tutorDbPath
        return jarvis.tutor.TutorDb.connect(dbPath)
    }

    /** `jarvis trust-export [out.json] [--run <auditRunId>]` — PC-side. */
    fun runExport(args: List<String>) {
        val runIdx = args.indexOf("--run")
        val auditRunId = if (runIdx >= 0) args.getOrNull(runIdx + 1) else null
        val out = Path.of(
            args.firstOrNull { !it.startsWith("--") && it != auditRunId } ?: "trust-verdicts.json",
        )
        val db = connectDb()
        jarvis.tutor.TutorMigration.migrate(db)
        val dump = TrustSync.export(db, auditRunId)
        TrustSync.writeDump(dump, out)
        println(
            "[trust-export] wrote ${dump.rows.size} verdict row(s) -> $out" +
                (auditRunId?.let { " (run=$it)" } ?: ""),
        )
    }

    /** `jarvis trust-import <in.json>` — VPS-side (dist binary). */
    fun runImport(args: List<String>) {
        val path = args.firstOrNull() ?: run {
            System.err.println("Usage: jarvis trust-import <verdicts.json>")
            kotlin.system.exitProcess(2)
        }
        val db = connectDb()
        jarvis.tutor.TutorMigration.migrate(db)
        val report = TrustSync.importDump(db, TrustSync.readDump(Path.of(path)))
        println(
            "[trust-import] inserted=${report.inserted} updated=${report.updated} " +
                "skipped=${report.skipped} (rows=${report.inserted + report.updated + report.skipped})",
        )
    }
}
