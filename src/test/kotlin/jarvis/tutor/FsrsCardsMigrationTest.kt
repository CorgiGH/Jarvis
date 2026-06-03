package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 4 — CHANGE 1: fsrs_cards ADD kc_id/status/paused_at + post-ALTER backfill status='ACTIVE'.
 *
 * The load-bearing tests simulate a LEGACY fsrs_cards table (no status/kc_id/paused_at columns)
 * with rows, then run TutorMigration.migrate, then assert the columns were added + status
 * backfilled to ACTIVE + rows survive due()/queue.
 *
 * All DB tests use a TEMP SQLite DB (never ~/.jarvis/tutor.db).
 */
class FsrsCardsMigrationTest {

    private fun tempDb(tmp: Path): Database =
        TutorDb.connect(tmp.resolve("c1.db").toString())

    // Exposed's SQLite timestamp on-disk format (yyyy-MM-dd HH:mm:ss.SSSSSS, UTC) — match it
    // exactly in the legacy fixture so post-migrate Exposed reads round-trip the legacy row.
    private val sqliteTs: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)

    /**
     * Build a LEGACY-shaped fsrs_cards (pre-CHANGE-1: no kc_id/status/paused_at) plus the
     * users table, and insert one legacy row. Returns (userId, cardId).
     */
    private fun seedLegacy(
        db: Database,
        source: String = "MANUAL",
        sourceRef: String = "ref-legacy",
        dueAt: Instant,
    ): Pair<String, String> {
        // users table: create at its CURRENT shape via Exposed so migrate only ALTERs fsrs_cards
        // (the table under test). Avoids a spurious users-table ALTER masking the fsrs_cards path.
        transaction(db) { SchemaUtils.create(UsersTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val cardId = TutorTypes.ulid()
        return transaction(db) {
        // Legacy fsrs_cards — the OLD column set ONLY (no kc_id/status/paused_at).
        exec(
            "CREATE TABLE IF NOT EXISTS fsrs_cards (" +
                "id VARCHAR(26) PRIMARY KEY, user_id VARCHAR(26), source VARCHAR(24), " +
                "source_ref VARCHAR(32), front TEXT, back TEXT, difficulty DOUBLE, " +
                "stability DOUBLE, retrievability DOUBLE, due_at TEXT, last_reviewed_at TEXT, lapses INT)",
        )
        val due = sqliteTs.format(dueAt)
        exec(
            "INSERT INTO fsrs_cards (id, user_id, source, source_ref, front, back, difficulty, " +
                "stability, retrievability, due_at, last_reviewed_at, lapses) VALUES (" +
                "'$cardId','$userId','$source','$sourceRef','f','b',5.0,0.5,1.0,'$due','$due',0)",
        )
        userId to cardId
        }
    }

    private fun statusOf(db: Database, cardId: String): String? = transaction(db) {
        var s: String? = null
        exec("SELECT status FROM fsrs_cards WHERE id='$cardId'") { rs -> if (rs.next()) s = rs.getString(1) }
        s
    }

    private fun kcIdOf(db: Database, cardId: String): String? = transaction(db) {
        var s: String? = null
        exec("SELECT kc_id FROM fsrs_cards WHERE id='$cardId'") { rs -> if (rs.next()) s = rs.getString(1) }
        s
    }

    @Test
    fun `legacy row gets status ACTIVE after backfill and kc_id paused_at null`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val (_, cardId) = seedLegacy(db, dueAt = Instant.now().minusSeconds(60))

        TutorMigration.migrate(db)

        assertEquals("ACTIVE", statusOf(db, cardId))
        assertNull(kcIdOf(db, cardId), "MANUAL legacy card kc_id stays NULL")
        // paused_at is NULL (only set by REPORT WRONG, Phase 3).
        transaction(db) {
            var pausedNull = true
            exec("SELECT paused_at FROM fsrs_cards WHERE id='$cardId'") { rs ->
                if (rs.next()) pausedNull = rs.getObject(1) == null
            }
            assertTrue(pausedNull, "paused_at must be NULL post-migration")
        }
    }

    @Test
    fun `legacy MANUAL card survives due and queue after migration`(@TempDir tmp: Path) {
        // THE load-bearing "corpus doesn't disappear" test.
        val db = tempDb(tmp)
        val now = Instant.now()
        val (userId, cardId) = seedLegacy(db, dueAt = now.minusSeconds(60))

        TutorMigration.migrate(db)

        val due = FsrsCardRepo(db).findDueForUser(userId, asOf = now)
        assertEquals(1, due.size, "the legacy MANUAL card must still be returned (ACTIVE post-backfill)")
        assertEquals(cardId, due[0].id)
        assertEquals(CardStatus.ACTIVE, due[0].status)
    }

    @Test
    fun `kc_id backfill touches zero rows on a MANUAL-only corpus`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val (_, cardId) = seedLegacy(db, source = "MANUAL", sourceRef = "pa-kc-999", dueAt = Instant.now())
        TutorMigration.migrate(db)
        // MANUAL source ⇒ the RUBRIC_CRITERION-gated kc_id backfill does NOT fire.
        assertNull(kcIdOf(db, cardId), "MANUAL card kc_id must remain NULL (backfill is RUBRIC_CRITERION-only)")
    }

    @Test
    fun `kc_id backfill copies source_ref for a RUBRIC_CRITERION card`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // A RUBRIC_CRITERION fixture whose source_ref holds a KC id — exercises the future-proof UPDATE.
        val (_, cardId) = seedLegacy(db, source = "RUBRIC_CRITERION", sourceRef = "pa-kc-001", dueAt = Instant.now())
        TutorMigration.migrate(db)
        assertEquals("pa-kc-001", kcIdOf(db, cardId), "RUBRIC_CRITERION kc_id must be backfilled from source_ref")
    }

    @Test
    fun `findDueForUser excludes non-ACTIVE cards`(@TempDir tmp: Path) {
        // After migration (so columns exist), a QUARANTINED / PAUSED card is NOT returned by the queue.
        val db = tempDb(tmp)
        val now = Instant.now()
        val (userId, activeId) = seedLegacy(db, dueAt = now.minusSeconds(60))
        TutorMigration.migrate(db)

        // Insert a QUARANTINED + a PAUSED card directly (post-migration shape).
        val repo = FsrsCardRepo(db)
        val quarantinedId = TutorTypes.ulid()
        val pausedId = TutorTypes.ulid()
        repo.insert(
            TutorCard(
                quarantinedId, userId, FsrsSource.MANUAL, "q", "f", "b",
                FsrsState(5.0, 0.5, 1.0, now.minusSeconds(30), now, 0),
                status = CardStatus.QUARANTINED,
            ),
        )
        repo.insert(
            TutorCard(
                pausedId, userId, FsrsSource.MANUAL, "p", "f", "b",
                FsrsState(5.0, 0.5, 1.0, now.minusSeconds(30), now, 0),
                status = CardStatus.PAUSED,
            ),
        )

        val due = repo.findDueForUser(userId, asOf = now)
        assertEquals(setOf(activeId), due.map { it.id }.toSet(), "only the ACTIVE card is returned")
    }
}
