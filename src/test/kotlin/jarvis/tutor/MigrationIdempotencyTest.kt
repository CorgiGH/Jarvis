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

/**
 * Task 15 — Migration-idempotency CI (M-IDEMP).
 *
 * Seed a LEGACY fixture DB (pre-CHANGE-1/2: status-less fsrs_cards + phase-null kc_mastery)
 * → run TutorMigration.migrate TWICE → assert survival + status='ACTIVE' + phase backfilled,
 * and that the SECOND run is a clean no-op (no double-backfill, no row loss, no error).
 *
 * All DB tests use a TEMP SQLite DB (never ~/.jarvis/tutor.db).
 */
class MigrationIdempotencyTest {

    private fun tempDb(tmp: Path): Database =
        TutorDb.connect(tmp.resolve("idem.db").toString())

    private val sqliteTs: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)

    private fun seedLegacy(db: Database): Triple<String, String, String> {
        transaction(db) { SchemaUtils.create(UsersTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val cardId = TutorTypes.ulid()
        val kcId = "pa-kc-001"
        transaction(db) {
            val ts = sqliteTs.format(Instant.now().minusSeconds(60))
            exec(
                "CREATE TABLE IF NOT EXISTS fsrs_cards (" +
                    "id VARCHAR(26) PRIMARY KEY, user_id VARCHAR(26), source VARCHAR(24), " +
                    "source_ref VARCHAR(32), front TEXT, back TEXT, difficulty DOUBLE, " +
                    "stability DOUBLE, retrievability DOUBLE, due_at TEXT, last_reviewed_at TEXT, lapses INT)",
            )
            exec(
                "INSERT INTO fsrs_cards (id, user_id, source, source_ref, front, back, difficulty, " +
                    "stability, retrievability, due_at, last_reviewed_at, lapses) VALUES (" +
                    "'$cardId','$userId','MANUAL','ref','f','b',5.0,0.5,1.0,'$ts','$ts',0)",
            )
            exec(
                "CREATE TABLE IF NOT EXISTS kc_mastery (" +
                    "user_id VARCHAR(26), kc_id VARCHAR(64), ewma_score DOUBLE, " +
                    "observations INT, last_graded_at TEXT, PRIMARY KEY (user_id, kc_id))",
            )
            exec(
                "INSERT INTO kc_mastery (user_id, kc_id, ewma_score, observations, last_graded_at) " +
                    "VALUES ('$userId','$kcId',0.9,4,'$ts')",
            )
        }
        return Triple(userId, cardId, kcId)
    }

    private fun cardStatus(db: Database, cardId: String): String? = transaction(db) {
        var s: String? = null
        exec("SELECT status FROM fsrs_cards WHERE id='$cardId'") { rs -> if (rs.next()) s = rs.getString(1) }
        s
    }

    private fun phase(db: Database, userId: String, kcId: String): String? = transaction(db) {
        var p: String? = null
        exec("SELECT phase FROM kc_mastery WHERE user_id='$userId' AND kc_id='$kcId'") { rs ->
            if (rs.next()) p = rs.getString(1)
        }
        p
    }

    private fun cardCount(db: Database): Int = transaction(db) {
        var c = 0
        exec("SELECT COUNT(*) FROM fsrs_cards") { rs -> if (rs.next()) c = rs.getInt(1) }
        c
    }

    /**
     * Seed N legacy fsrs_cards rows ALL sharing (user_id, source='MANUAL', kc_id absent/NULL),
     * mirroring the 828 live cards. The legacy table has NO kc_id column yet — the migration ADDs
     * it (NULL on every existing row) and creates the (user_id, source, kc_id) UNIQUE index. The
     * whole live corpus coexists only because SQLite treats each NULL as DISTINCT in a UNIQUE
     * index. Returns the seeded count.
     */
    private fun seedManyManualNullKcCards(db: Database, n: Int): Int {
        transaction(db) { SchemaUtils.create(UsersTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        transaction(db) {
            val ts = sqliteTs.format(Instant.now().minusSeconds(60))
            // Legacy fsrs_cards shape: NO kc_id / status / paused_at columns (the migration adds them).
            exec(
                "CREATE TABLE IF NOT EXISTS fsrs_cards (" +
                    "id VARCHAR(26) PRIMARY KEY, user_id VARCHAR(26), source VARCHAR(24), " +
                    "source_ref VARCHAR(32), front TEXT, back TEXT, difficulty DOUBLE, " +
                    "stability DOUBLE, retrievability DOUBLE, due_at TEXT, last_reviewed_at TEXT, lapses INT)",
            )
            repeat(n) {
                val cardId = TutorTypes.ulid()
                // Every row: SAME user_id, source='MANUAL', kc_id implicitly NULL (column absent).
                exec(
                    "INSERT INTO fsrs_cards (id, user_id, source, source_ref, front, back, difficulty, " +
                        "stability, retrievability, due_at, last_reviewed_at, lapses) VALUES (" +
                        "'$cardId','$userId','MANUAL','ref','f','b',5.0,0.5,1.0,'$ts','$ts',0)",
                )
            }
        }
        return n
    }

    /**
     * Item (b) — NULL-distinct UNIQUE-index regression lock. Seeds 100 MANUAL/NULL-kc cards that
     * all share (owner, 'MANUAL', NULL kc_id) — exactly like the 828 live cards — then runs the
     * migration (which creates fsrs_cards_user_source_kc_unique) TWICE. Asserts: every row survives
     * (COUNT unchanged) and NO unique-constraint violation / SQLiteException is thrown. This locks
     * the SQLite-NULL-is-distinct assumption the entire live corpus depends on.
     */
    @Test
    fun `100 MANUAL null-kc cards survive the unique index across two migrates`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val seeded = seedManyManualNullKcCards(db, 100)
        assertEquals(seeded, cardCount(db), "all seeded rows present before migrate")

        // FIRST migrate — creates fsrs_cards_user_source_kc_unique over (user, source, kc_id).
        // With all rows at (user, MANUAL, NULL), SQLite NULL-distinct must let them all coexist.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        assertEquals(seeded, cardCount(db), "no row dropped by unique-index creation (NULL-distinct)")

        // SECOND migrate — idempotent; index already present, still no constraint violation.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        assertEquals(seeded, cardCount(db), "second migrate keeps every row (no unique violation)")
    }

    @Test
    fun `migrate twice is safe — survival, ACTIVE, phase, no row loss, no error`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        val (userId, cardId, kcId) = seedLegacy(db)

        // FIRST migrate.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        assertEquals(1, cardCount(db), "no row loss after first migrate")
        assertEquals("ACTIVE", cardStatus(db, cardId), "legacy card backfilled to ACTIVE")
        assertEquals("mastered", phase(db, userId, kcId), "kc_mastery phase replayed (ewma 0.9, obs 4 ⇒ mastered)")
        // The legacy MANUAL card is returned by the queue (ACTIVE).
        assertEquals(1, FsrsCardRepo(db).findDueForUser(userId, asOf = Instant.now()).size)

        // SECOND migrate — clean no-op.
        assertEquals(MigrationResult.Success, TutorMigration.migrate(db))
        assertEquals(1, cardCount(db), "second run drops no rows")
        assertEquals("ACTIVE", cardStatus(db, cardId), "status unchanged on re-run")
        assertEquals("mastered", phase(db, userId, kcId), "phase unchanged on re-run (WHERE phase IS NULL no-op)")
        assertEquals(1, FsrsCardRepo(db).findDueForUser(userId, asOf = Instant.now()).size)
    }
}
