package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 2 — Card cull SQL + test (in-memory SQLite only — NEVER touches ~/.jarvis/tutor.db).
 *
 * Canonical 43-id list is derived from tools/card-cull.sql at test-time:
 * the test FAILS if card-cull.sql and the embedded CANONICAL_CULL_IDS set ever diverge.
 * This enforces that the SQL file and the test fixture are always in sync.
 *
 * Fixture design:
 *  - Seed the 43 audited junk-shape rows (ids from the audit) + N_GOOD known-good rows.
 *  - Apply card-cull.sql via exec().
 *  - Assert: every audited id is GONE; every good id SURVIVES; final_count == N_GOOD.
 *  - Note: the >=800 floor check uses N_GOOD + 43 seeded total. For the live corpus check
 *    (871 total -> 828 >= 800), the db-backup.py precondition (Task 1 / test_db_backup.py)
 *    is the enforcing gate. Here we assert the math: final == N_GOOD == seeded - 43.
 */
class CardCullTest {

    companion object {
        /**
         * CANONICAL 43-id list from docs/superpowers/findings/2026-06-03-card-cull-audit.md §(c).
         *
         * This is the SOURCE OF TRUTH embedded in the test. The test asserts that
         * tools/card-cull.sql contains EXACTLY these 43 ids — no more, no fewer.
         * A divergence (edit to the SQL that adds/removes/changes an id) is a test failure.
         */
        val CANONICAL_CULL_IDS: Set<String> = setOf(
            "01KS037V6PVYYSQJXHW0J7W3FH",
            "01KS037V7TRJZS07YD77KFHB9A",
            "01KS03CS4S1JCHXYR6G0BZX6CZ",
            "01KS0Z80WVSWHA9MWXT4NXVAE2",
            "01KS0Z80XDXHAPD027V0RAQ4HC",
            "01KS0Z80XX3ZDQD84JPSQ25M21",
            "01KS0Z80YC1G3TRJV2E13PCA5Y",
            "01KS0Z80YT2CRDX5XKZKB23Q3E",
            "01KS0Z80Z82NTHF1FGKQB76CDY",
            "01KS0Z80ZPC4K1B2532M2W2DFG",
            "01KS0Z810355G47EHKJRH0RKBC",
            "01KS03XB5R800M7PGB10APW3Z2",
            "01KS03XB79TXAWS5F1PT69V0QP",
            "01KS03XB7QXTCJDQGT06F0GK2C",
            "01KS03XB847ZEP9DWB77F4DGJ0",
            "01KS03XB8J6PVPM5H25DMZS1WC",
            "01KS03XB90RYRK7VDTZGT5G4A8",
            "01KS03XB9D8C038TMZ9JZME73X",
            "01KS03XB9V8WGGAP0W4P76BYF8",
            "01KS104GYVXC6T4K00S9NVEV3B",
            "01KS104GZMCC7WBT4HFS1F37P3",
            "01KS104H0A54GA50FYD06QF0GS",
            "01KS104H0RWE2CMAWZ4D5964VT",
            "01KS104H15F8RASK1A6KYRMN1S",
            "01KS104H1JA9DZCESQDSBQYK5A",
            "01KS104H1XV49V3709880V9FGZ",
            "01KS104H2A0GH68ZJ2VF5TYR09",
            "01KS0ZY24C72J3YNK7BRT341BK",
            "01KS0ZY24TS9W2W2ADKYAT0G1F",
            "01KS0ZY25E65HTQ78642BE1FQM",
            "01KS0ZY26013RT8XD2WNMFQ4SS",
            "01KS0ZY26H0CYXQDXABWRJZX00",
            "01KS0ZY2718576XQB4ZXFGCYYH",
            "01KS0ZY27EK7N887BAWPSDY737",
            "01KS0ZY27SKRB4NJNZCTDTD0EQ",
            "01KS0ZZ3W391RQ439ABVY4MNYC",
            "01KS1004DGPK057JBRAYZKS8WC",
            "01KS030V3C862QHBP3S8879RTF",
            "01KS030V48EBH2KHD0DFM3DAY4",
            "01KS030V5GBFWQPEYH6APPG4GP",
            "01KS0ZSND8QAJBZR5T5YRR3D2Y",
            "01KS0ZSNDRZERQ762E7PAHAG04",
            "01KS0ZERWGRX5GNNHVCZYWSYMY",
        )

        /** Number of known-good rows that must survive the cull. */
        const val N_GOOD = 50

        /**
         * Parse the id list from tools/card-cull.sql.
         * Extracts all 26-char ULID-shaped tokens in single quotes from the IN(...) clause.
         * This is how the test enforces that the SQL file and CANONICAL_CULL_IDS never diverge.
         */
        fun parseSqlIds(): Set<String> {
            val sqlFile = findCardCullSql()
            val text = sqlFile.readText()
            // Match quoted 26-character uppercase alphanumeric tokens (ULID format: 0-9A-Z).
            val ulidPattern = Regex("'([0-9A-Z]{26})'")
            return ulidPattern.findAll(text).map { it.groupValues[1] }.toSet()
        }

        private fun findCardCullSql(): java.io.File {
            // Try relative to the project root (works from both Gradle test and IDE).
            val candidates = listOf(
                java.io.File("tools/card-cull.sql"),
                java.io.File("../tools/card-cull.sql"),
                java.io.File(System.getProperty("user.dir")).resolve("tools/card-cull.sql"),
            )
            return candidates.firstOrNull { it.exists() }
                ?: error(
                    "Cannot find tools/card-cull.sql. Searched: " +
                        candidates.map { it.absolutePath },
                )
        }
    }

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private fun tempDb(tmp: Path) =
        TutorDb.connect(tmp.resolve("cull-test.db").toString())
            .also { db ->
                transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) }
            }

    private val userId = "01TSTUSER000000000000000001"

    /**
     * Seed the DB with one user + the given set of card ids (minimal junk-shape rows),
     * then additional N_GOOD known-good rows with deterministic ids.
     *
     * Returns the set of known-good ids (must survive cull).
     */
    private fun seedFixture(db: org.jetbrains.exposed.sql.Database): Set<String> {
        // Insert a user (FK requirement).
        transaction(db) {
            exec(
                "INSERT OR IGNORE INTO users (id, name, scope, created_at, last_seen_at) " +
                    "VALUES ('$userId', 'test', 'OWNER', '2026-01-01 00:00:00.000000', '2026-01-01 00:00:00.000000')",
            )
        }

        val ts = "2026-01-01 00:00:00.000000"

        // Insert the 43 audited (junk) rows.
        transaction(db) {
            for (id in CANONICAL_CULL_IDS) {
                exec(
                    "INSERT INTO fsrs_cards " +
                        "(id, user_id, source, source_ref, front, back, difficulty, stability, " +
                        "retrievability, due_at, last_reviewed_at, lapses, status) VALUES " +
                        "('$id', '$userId', 'MANUAL', 'ref', 'junk front', 'junk back', " +
                        "5.0, 0.5, 1.0, '$ts', '$ts', 0, 'ACTIVE')",
                )
            }
        }

        // Insert N_GOOD known-good rows with deterministic ids (prefix GOODCARD0...).
        val goodIds = (1..N_GOOD).map { i -> "GOODCARD%021d".format(i) }.toSet()
        transaction(db) {
            for (id in goodIds) {
                exec(
                    "INSERT INTO fsrs_cards " +
                        "(id, user_id, source, source_ref, front, back, difficulty, stability, " +
                        "retrievability, due_at, last_reviewed_at, lapses, status) VALUES " +
                        "('$id', '$userId', 'MANUAL', 'ref', 'good front', 'good back', " +
                        "5.0, 0.5, 1.0, '$ts', '$ts', 0, 'ACTIVE')",
                )
            }
        }

        return goodIds
    }

    /** Apply tools/card-cull.sql to the given DB. */
    private fun applyCull(db: org.jetbrains.exposed.sql.Database) {
        val sql = findCardCullSql().readText()
        transaction(db) { exec(sql) }
    }

    private fun cardCount(db: org.jetbrains.exposed.sql.Database): Int = transaction(db) {
        var c = 0
        exec("SELECT COUNT(*) FROM fsrs_cards") { rs -> if (rs.next()) c = rs.getInt(1) }
        c
    }

    private fun allIds(db: org.jetbrains.exposed.sql.Database): Set<String> = transaction(db) {
        val ids = mutableSetOf<String>()
        exec("SELECT id FROM fsrs_cards") { rs -> while (rs.next()) ids.add(rs.getString(1)) }
        ids
    }

    // -------------------------------------------------------------------------
    // Guard: card-cull.sql must contain EXACTLY the canonical 43 ids
    // -------------------------------------------------------------------------

    @Test
    fun `card-cull sql contains exactly the canonical 43 ids — divergence detection`() {
        val sqlIds = parseSqlIds()
        val missing = CANONICAL_CULL_IDS - sqlIds
        val extra = sqlIds - CANONICAL_CULL_IDS
        assertTrue(
            missing.isEmpty(),
            "tools/card-cull.sql is MISSING ids vs canonical list: $missing",
        )
        assertTrue(
            extra.isEmpty(),
            "tools/card-cull.sql has EXTRA ids vs canonical list: $extra",
        )
        assertEquals(43, sqlIds.size, "Expected exactly 43 ids in card-cull.sql")
    }

    // -------------------------------------------------------------------------
    // Main cull test
    // -------------------------------------------------------------------------

    @Test
    fun `cull removes exactly the audited ids and floor holds`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val goodIds = seedFixture(db)

        // Pre-cull: 43 junk + N_GOOD good.
        val preCullCount = cardCount(db)
        assertEquals(CANONICAL_CULL_IDS.size + N_GOOD, preCullCount, "pre-cull count")

        applyCull(db)

        val surviving = allIds(db)
        val postCullCount = surviving.size

        // Every audited id is GONE.
        for (id in CANONICAL_CULL_IDS) {
            assertFalse(surviving.contains(id), "Audited id must be GONE after cull: $id")
        }

        // Every known-good id SURVIVES.
        for (id in goodIds) {
            assertTrue(surviving.contains(id), "Known-good id must SURVIVE cull: $id")
        }

        // Math: final == seeded_N - 43.
        assertEquals(N_GOOD, postCullCount, "post-cull count == N_GOOD (all junk removed, all good survive)")
        assertEquals(preCullCount - CANONICAL_CULL_IDS.size, postCullCount, "final == seeded - 43")

        // Live-corpus floor note: on the real corpus (871 rows seeded), post-cull = 828 >= 800.
        // Here we use a smaller fixture; the >=800 floor on the live corpus is enforced by
        // tools/db-backup.py (MIN_EXPECTED_CARDS=800) which is the Task-1 precondition gate.
        // The math above (final == seeded - 43) is sufficient to prove correctness of the id list.
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    fun `cull is idempotent — running twice leaves the same final set`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val goodIds = seedFixture(db)

        // First application.
        applyCull(db)
        val afterFirst = allIds(db)

        // Second application — re-deleting absent rows is a no-op in SQLite (no error).
        applyCull(db)
        val afterSecond = allIds(db)

        assertEquals(afterFirst, afterSecond, "second cull run must leave identical surviving set")
        assertEquals(N_GOOD, afterSecond.size, "idempotent: final count unchanged on second run")

        // Good ids survive both runs.
        for (id in goodIds) {
            assertTrue(afterSecond.contains(id), "Good id must survive both cull runs: $id")
        }
    }
}
