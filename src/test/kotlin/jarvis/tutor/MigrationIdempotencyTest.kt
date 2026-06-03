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
