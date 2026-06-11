package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Transaction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * INV-3.1 (Plan-1 / spec §3.8) — the BACKUP-FIRST refusal gate for [TutorMigration.migrate].
 *
 * The migration runner refuses to run any SCHEMA-MUTATING step over a PROTECTED corpus
 * (fsrs_cards >= [minExpectedCards], the same M-DB floor `tools/db-backup.py` enforces) unless a
 * SAME-DAY VERIFIED dump manifest exists: `tools/db-backup.py` writes a `*.manifest.json` sidecar
 * next to every dump (Plan-1 Task 2) carrying integrity + restore-drill verdicts, the dumped
 * row count, and a schema hash; this gate demands integrity==PASS, restore_drill==PASS,
 * created_date == today, fsrs_cards == the live count, and schema_hash == the live schema hash
 * (so the dump provably covers THIS data at THIS schema).
 *
 * Scoping (deliberate): fresh DBs (0 protected rows — tests, first boot) and no-DDL boots are NOT
 * gated; the gate keys on data-at-risk, not on a default-off flag (the D-RF2 anti-pattern).
 */
@Serializable
data class BackupManifest(
    val tool: String = "db-backup.py",
    val manifest_version: Int = 1,
    val created_date: String,
    val created_at: String = "",
    val db_path: String = "",
    val dump_file: String = "",
    val fsrs_cards: Long,
    val schema_hash: String,
    val integrity: String,
    val restore_drill: String,
)

/** Thrown by the gate BEFORE any DDL has run. Wrapped into [MigrationException] by the runner. */
class BackupGateRefusal(message: String) : RuntimeException(message)

class MigrationBackupGate(
    private val backupDir: Path = defaultBackupDir(),
    val minExpectedCards: Long = defaultFloor(),
    private val today: () -> LocalDate = LocalDate::now,
) {

    /** Refuses (throws [BackupGateRefusal]) unless the newest manifest verifies against the live DB. */
    fun assertSafeToMutate(liveCards: Long, liveSchemaHash: String) {
        val m = newestManifest() ?: refuse(
            "no backup manifest (*.manifest.json) in $backupDir — run " +
                "`python tools/db-backup.py <off-box dir>` first (M-DB / same-day verified dump)",
        )
        val todayStr = today().toString()
        if (m.created_date != todayStr) {
            refuse("newest backup manifest is dated ${m.created_date}, not today ($todayStr) — take a fresh dump")
        }
        if (m.integrity != "PASS") refuse("newest backup manifest integrity=${m.integrity} (must be PASS)")
        if (m.restore_drill != "PASS") refuse("newest backup manifest restore_drill=${m.restore_drill} (must be PASS)")
        if (m.fsrs_cards != liveCards) {
            refuse("backup covers ${m.fsrs_cards} fsrs_cards but the live DB has $liveCards — the dump is not a backup of THIS data")
        }
        if (m.schema_hash != liveSchemaHash) {
            refuse("backup manifest schema_hash != live schema hash — the dump predates a schema change; take a fresh dump")
        }
    }

    private fun refuse(why: String): Nothing =
        throw BackupGateRefusal("migration REFUSED (INV-3.1 backup gate): $why")

    internal fun newestManifest(): BackupManifest? {
        if (!Files.isDirectory(backupDir)) return null
        val json = Json { ignoreUnknownKeys = true }
        return backupDir.listDirectoryEntries("*.manifest.json")
            .mapNotNull { p -> runCatching { json.decodeFromString<BackupManifest>(p.readText()) }.getOrNull() }
            .maxByOrNull { it.created_at }
    }

    companion object {
        fun defaultBackupDir(): Path =
            Path.of(System.getenv("JARVIS_BACKUP_DIR")?.takeIf { it.isNotBlank() } ?: "backups")

        fun defaultFloor(): Long =
            System.getenv("MIN_EXPECTED_CARDS")?.trim()?.toLongOrNull() ?: 800L

        /**
         * MUST stay byte-identical with tools/db-backup.py `schema_hash()`:
         * SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%';
         * strip each; sort; join with '\n'; sha256 hex digest.
         */
        fun liveSchemaHash(tx: Transaction): String {
            val sqls = ArrayList<String>()
            tx.exec("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'") { rs ->
                while (rs.next()) sqls.add(rs.getString(1).trim())
            }
            val canon = sqls.sorted().joinToString("\n")
            val digest = MessageDigest.getInstance("SHA-256").digest(canon.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** fsrs_cards row count; 0 when the table does not exist (fresh DB — nothing to protect). */
        fun liveFsrsCardCount(tx: Transaction): Long {
            var n = 0L
            try {
                tx.exec("SELECT COUNT(*) FROM fsrs_cards") { rs -> if (rs.next()) n = rs.getLong(1) }
            } catch (_: Exception) {
                return 0L
            }
            return n
        }
    }
}
