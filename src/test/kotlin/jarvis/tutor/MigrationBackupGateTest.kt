package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * INV-3.1 (spec §3.8) — the migration runner REFUSES any schema-mutating run over a protected
 * corpus (fsrs_cards >= floor) unless a same-day VERIFIED off-box dump manifest exists
 * (integrity PASS + restore_drill PASS + row-count + schema-hash equality with THIS DB).
 * Fresh/empty DBs and no-op (zero pending DDL) runs are NOT gated.
 */
class MigrationBackupGateTest {

    private fun tempDb(tmp: Path, name: String = "gate.db"): Database =
        TutorDb.connect(tmp.resolve(name).toString())

    /**
     * Old-shape DB with N protected rows: a legacy fsrs_cards only — migrate() has pending DDL.
     * Must NOT include newer-CHANGE columns — keep the original legacy production columns.
     */
    private fun seedOldShape(db: Database, cards: Int) {
        transaction(db) {
            exec(
                "CREATE TABLE IF NOT EXISTS fsrs_cards (" +
                    "id VARCHAR(26) PRIMARY KEY, user_id VARCHAR(26), source VARCHAR(24), " +
                    "source_ref VARCHAR(32), front TEXT, back TEXT, difficulty DOUBLE, " +
                    "stability DOUBLE, retrievability DOUBLE, due_at TEXT, last_reviewed_at TEXT, lapses INT)",
            )
            repeat(cards) { i ->
                exec(
                    "INSERT INTO fsrs_cards (id, user_id, source, source_ref, front, back, difficulty, " +
                        "stability, retrievability, due_at, last_reviewed_at, lapses) VALUES (" +
                        "'gate-$i','u','MANUAL','r','f','b',5.0,0.5,1.0," +
                        "'2026-06-11 00:00:00.000000','2026-06-11 00:00:00.000000',0)",
                )
            }
        }
    }

    private fun gate(dir: Path, floor: Long = 5, today: LocalDate = LocalDate.now()) =
        MigrationBackupGate(backupDir = dir, minExpectedCards = floor, today = { today })

    private fun schemaHashOf(db: Database): String =
        transaction(db) { MigrationBackupGate.liveSchemaHash(this) }

    private fun writeManifest(
        dir: Path,
        cards: Long,
        schemaHash: String,
        date: String = LocalDate.now().toString(),
        drill: String = "PASS",
        integrity: String = "PASS",
    ) {
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("jarvis-tutor-db-test.sql.gz.manifest.json"),
            """{"tool":"db-backup.py","manifest_version":1,"created_date":"$date",""" +
                """"created_at":"${date}T00:00:00Z","db_path":"t","dump_file":"jarvis-tutor-db-test.sql.gz",""" +
                """"fsrs_cards":$cards,"schema_hash":"$schemaHash","integrity":"$integrity","restore_drill":"$drill"}""",
        )
    }

    private fun tableExists(db: Database, name: String): Boolean = transaction(db) {
        var found = false
        exec("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'") { rs -> found = rs.next() }
        found
    }

    @Test
    fun `refuses pending DDL over a protected corpus without a manifest`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, cards = 10)

        val e = assertFailsWith<MigrationException> {
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty")))
        }

        assertIs<BackupGateRefusal>(e.cause)
        assertTrue(e.message!!.contains("INV-3.1"), "refusal must name the invariant: ${e.message}")
        // refusal fired BEFORE any DDL: the new tables were never created…
        assertFalse(tableExists(db, "kc_verification_status"))
        // …and the protected rows are intact.
        assertEquals(10L, transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) })
    }

    @Test
    fun `refuses a stale (yesterday) manifest`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db), date = LocalDate.now().minusDays(1).toString())

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("not today"), e.cause!!.message)
    }

    @Test
    fun `refuses a manifest whose card count does not cover the live data`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, cards = 9, schemaHash = schemaHashOf(db))

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("fsrs_cards"), e.cause!!.message)
    }

    @Test
    fun `refuses a failed restore drill`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db), drill = "FAIL")

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("restore_drill"), e.cause!!.message)
    }

    @Test
    fun `refuses a schema-hash mismatch (dump predates a schema change)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHash = "deadbeef")

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("schema_hash"), e.cause!!.message)
    }

    @Test
    fun `valid same-day verified manifest lets the migration proceed and data survives`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db))

        assertEquals(MigrationResult.Success, TutorMigration.migrate(db, backupGate = gate(dir)))

        assertEquals(10L, transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) })
        assertTrue(tableExists(db, "kc_verification_status"))
    }

    @Test
    fun `fresh empty DB is not gated (nothing to protect)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // no manifest anywhere; the gate WOULD refuse if consulted
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty"))),
        )
    }

    @Test
    fun `no pending DDL means no gate (routine re-boot needs no fresh dump)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db))
        TutorMigration.migrate(db, backupGate = gate(dir)) // first run migrates with the manifest

        // second run: schema is current -> zero pending DDL -> a refusing gate must NOT fire
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty"))),
        )
    }

    @Test
    fun `corrupt manifest json refuses like no manifest`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups-corrupt")
        // Write a malformed JSON file as the ONLY *.manifest.json — silent-skip must land on refusal
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("jarvis-tutor-db-corrupt.sql.gz.manifest.json"), "{not json")

        val e = assertFailsWith<MigrationException> {
            TutorMigration.migrate(db, backupGate = gate(dir))
        }

        assertIs<BackupGateRefusal>(e.cause)
        assertTrue(
            e.cause!!.message!!.contains("no backup manifest"),
            "corrupt manifest must be skipped, landing on 'no backup manifest' refusal: ${e.cause!!.message}",
        )
    }
}
