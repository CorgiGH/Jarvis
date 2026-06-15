package jarvis.tutor

import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-language schema-hash PARITY (master-plan-v2 Phase-0, audit F-schema-parity).
 *
 * Two independent impls compute a SHA-256 over the SQLite schema and MUST stay byte-identical:
 *  - Kotlin: [MigrationBackupGate.liveSchemaHash] (src/main/kotlin/jarvis/tutor/MigrationBackupGate.kt:93-101)
 *  - Python: schema_hash() in tools/db-backup.py:88-99
 *
 * [MigrationBackupGate.assertSafeToMutate] REFUSES a backup when manifest.schema_hash != live hash
 * (MigrationBackupGate.kt:65-67). A SILENT divergence between the two impls therefore DoSes the
 * migration path (a Python-written manifest would never match a Kotlin-computed live hash).
 *
 * This is a GOLDEN-VECTOR test: a tiny fixed canonical schema with a PINNED expected hex. Editing
 * either impl's canonicalisation (the SELECT, strip/trim, sort, "\n"-join, or sha256-hex) reds this
 * test. Its byte-identical twin is [tools/test_schema_hash_parity.py], which asserts the SAME
 * [EXPECTED_HEX] from Python over the SAME DDL.
 *
 * --- SHARED CANONICAL VECTOR (keep byte-identical with tools/test_schema_hash_parity.py) ---
 *   DDL (created verbatim; SQLite stores sqlite_master.sql exactly as written):
 *     CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)
 *     CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)
 *   Canonical string fed to sha256 (rows trimmed, sorted, joined with '\n'):
 *     "CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)\n" +
 *     "CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)"
 *   EXPECTED_HEX = f5fb8caadbfb1315238437e81ebb6b745841f898349bdaabe89450ab59ec7860
 * -------------------------------------------------------------------------------------------
 */
class SchemaHashParityTest {

    companion object {
        /** The fixed canonical schema. Single-spaced, no indentation -> stored verbatim. */
        private val CANONICAL_DDL = listOf(
            "CREATE TABLE alpha (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
            "CREATE TABLE beta (alpha_id INTEGER REFERENCES alpha(id), payload TEXT)",
        )

        /**
         * Pinned sha256 hex of the canonical schema. MUST equal the Python side's EXPECTED_HEX in
         * tools/test_schema_hash_parity.py. Recompute (offline) ONLY if the canonical DDL changes,
         * and update BOTH files in lockstep.
         */
        private const val EXPECTED_HEX =
            "f5fb8caadbfb1315238437e81ebb6b745841f898349bdaabe89450ab59ec7860"
    }

    @Test
    fun `liveSchemaHash matches the pinned golden vector (parity with Python schema_hash)`(
        @TempDir tmp: Path,
    ) {
        val db = TutorDb.connect(tmp.resolve("parity.db").toString())
        transaction(db) {
            // Build the EXACT canonical schema, inserted in NON-sorted order on purpose so the
            // test also exercises liveSchemaHash's sort step (beta created before alpha).
            for (ddl in CANONICAL_DDL.reversed()) exec(ddl)
        }

        val actual = transaction(db) { MigrationBackupGate.liveSchemaHash(this) }

        assertEquals(
            EXPECTED_HEX,
            actual,
            "Kotlin liveSchemaHash diverged from the pinned golden vector. Either the Kotlin " +
                "canonicalisation changed, or it no longer matches Python schema_hash(). Both impls " +
                "MUST agree byte-for-byte (MigrationBackupGate.kt:65-67 refuses backups on mismatch). " +
                "Reconcile MigrationBackupGate.liveSchemaHash and tools/db-backup.py schema_hash().",
        )
    }
}
