package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.MigrationBackupGate
import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Plan-1 final gate — machine-checked "trust-net is ON" invariants over the REAL DB + the REAL
 * content corpus (spec §3.8 / §9.2 gate 2). Operator-run, PC-side: `gradle trustInvariants`.
 * READ-ONLY — never mutates the DB. Checks:
 *
 *   INV-3.3  fsrs_cards >= floor AND == the newest backup manifest's count (when a backup dir
 *            is supplied via JARVIS_BACKUP_DIR) — all 828 cards survived.
 *   SCHEMA   kc_verification_status carries the 7 frozen columns (lock §I.1); report_wrong
 *            carries resolved_by/resolved_at (D-RF3).
 *   FLIP     >= 1 row WHERE status='faithful' — the zero-faithful-rows-now-nonzero assertion.
 *   PARITY   every faithful row's content_hash == ContentReconcile.kcContentHashOf(claimsFor(kc))
 *            recomputed from content/ — the MACHINE parity check that replaces any manual PDF
 *            spot-check (no-oracle-inversion: the user never verifies content).
 */
object TrustInvariantsCli {

    data class Failure(val check: String, val detail: String)

    fun check(db: Database, contentDir: Path, backupDir: Path?, floor: Long): List<Failure> {
        val failures = ArrayList<Failure>()

        // ── INV-3.3: survival vs floor + the backup manifest ─────────────────────────────
        val cards = transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) }
        if (cards < floor) failures += Failure("INV-3.3", "fsrs_cards=$cards < floor=$floor")
        if (backupDir != null) {
            val manifest = MigrationBackupGate(backupDir = backupDir, minExpectedCards = floor).newestManifest()
            when {
                manifest == null ->
                    failures += Failure("INV-3.3", "no *.manifest.json in $backupDir")
                manifest.fsrs_cards != cards ->
                    failures += Failure("INV-3.3", "fsrs_cards=$cards != backup manifest's ${manifest.fsrs_cards}")
            }
        }

        // ── SCHEMA: the frozen column sets ───────────────────────────────────────────────
        val kvs = transaction(db) { columnsOf("kc_verification_status") }
        val expectedKvs = setOf(
            "kc_id", "status", "last_audit_run_id", "content_hash",
            "lecture_grounded", "source_span_hash", "updated_at",
        )
        if (kvs.toSet() != expectedKvs) {
            failures += Failure("SCHEMA", "kc_verification_status=$kvs expected=$expectedKvs")
        }
        val rw = transaction(db) { columnsOf("report_wrong") }
        for (c in listOf("resolved_by", "resolved_at")) {
            if (c !in rw) failures += Failure("SCHEMA", "report_wrong missing $c (D-RF3)")
        }

        // ── FLIP: zero-faithful-rows-now-nonzero ─────────────────────────────────────────
        // Fail-soft on a PRE-MIGRATION schema (no content_hash column yet): the query throwing
        // means the trust columns are absent — report it as a FLIP failure, never crash.
        val faithfulRows: List<Pair<String, String?>> = runCatching {
            transaction(db) {
                val rows = ArrayList<Pair<String, String?>>()
                exec("SELECT kc_id, content_hash FROM kc_verification_status WHERE status='faithful'") { rs ->
                    while (rs.next()) rows.add(rs.getString(1) to rs.getString(2))
                }
                rows
            }
        }.getOrElse {
            failures += Failure("FLIP", "kc_verification_status not queryable with trust columns (pre-migration schema?): ${it.message?.take(120)}")
            emptyList()
        }
        if (faithfulRows.isEmpty() && failures.none { it.check == "FLIP" }) {
            failures += Failure("FLIP", "0 faithful rows in kc_verification_status — the trust-net is still OFF")
        }

        // ── PARITY: stored content_hash == recompute from the live corpus ────────────────
        if (faithfulRows.isNotEmpty()) {
            val repo = ContentRepo(contentDir)
            val kcsById = repo.loadManifest().subjects
                .flatMap { repo.loadSubject(it.id).kcs }
                .associateBy { it.id }
            for ((kcId, stored) in faithfulRows) {
                val kc = kcsById[kcId]
                when {
                    kc == null ->
                        failures += Failure("PARITY", "$kcId is faithful in the DB but absent from $contentDir")
                    stored == null ->
                        failures += Failure("PARITY", "$kcId is faithful with NULL content_hash (fails closed at serve — re-audit it)")
                    else -> {
                        val recomputed = ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
                        if (recomputed != stored) {
                            failures += Failure(
                                "PARITY",
                                "$kcId stored=$stored recomputed=$recomputed (content edited after the audit?)",
                            )
                        }
                    }
                }
            }
        }
        return failures
    }

    private fun org.jetbrains.exposed.sql.Transaction.columnsOf(table: String): List<String> {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        return cols
    }
}

/** Gradle `trustInvariants` main. Read-only. Exit 0 = ALL PASS; exit 1 = failures on stderr. */
fun main(args: Array<String>) {
    val contentDir = Path.of(
        args.getOrNull(0)
            ?: System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )
    val dbPath = System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    val backupDir = System.getenv("JARVIS_BACKUP_DIR")?.takeIf { it.isNotBlank() }?.let(Path::of)
    val floor = MigrationBackupGate.defaultFloor()

    val failures = TrustInvariantsCli.check(TutorDb.connect(dbPath), contentDir, backupDir, floor)
    if (failures.isEmpty()) {
        println("[trustInvariants] ALL PASS (db=$dbPath, content=$contentDir, floor=$floor)")
        return
    }
    for (f in failures) System.err.println("[trustInvariants] FAIL ${f.check}: ${f.detail}")
    exitProcess(1)
}
