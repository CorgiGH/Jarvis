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

/**
 * Task 6 — CHANGE 2: kc_mastery ADD phase/entry_phase + post-ALTER replay backfill.
 *
 * Seeds a LEGACY kc_mastery (pre-CHANGE-2: no phase/entry_phase) with known (ewma, obs),
 * runs TutorMigration.migrate, then asserts phase == PhaseModel.transition(...).
 *
 * All DB tests use a TEMP SQLite DB (never ~/.jarvis/tutor.db).
 */
class KcMasteryMigrationTest {

    private fun tempDb(tmp: Path): Database =
        TutorDb.connect(tmp.resolve("c2.db").toString())

    private val sqliteTs: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)

    /** Build a LEGACY kc_mastery (no phase/entry_phase) + users table, seed one row. */
    private fun seedLegacyMastery(db: Database, userId: String, kcId: String, ewma: Double, obs: Int) {
        transaction(db) { SchemaUtils.create(UsersTable) }
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        transaction(db) {
            exec(
                "CREATE TABLE IF NOT EXISTS kc_mastery (" +
                    "user_id VARCHAR(26), kc_id VARCHAR(64), ewma_score DOUBLE, " +
                    "observations INT, last_graded_at TEXT, PRIMARY KEY (user_id, kc_id))",
            )
            val ts = sqliteTs.format(Instant.now())
            exec(
                "INSERT INTO kc_mastery (user_id, kc_id, ewma_score, observations, last_graded_at) " +
                    "VALUES ('$userId','$kcId',$ewma,$obs,'$ts')",
            )
        }
    }

    private fun phaseOf(db: Database, userId: String, kcId: String): String? = transaction(db) {
        var p: String? = null
        exec("SELECT phase FROM kc_mastery WHERE user_id='$userId' AND kc_id='$kcId'") { rs ->
            if (rs.next()) p = rs.getString(1)
        }
        p
    }

    @Test
    fun `phase backfilled by replay — mastered row`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedLegacyMastery(db, "u1", "pa-kc-001", ewma = 0.9, obs = 4)

        TutorMigration.migrate(db)

        // mastered == ewma>=0.8 && obs>=3 == true ⇒ transition returns mastered.
        val expected = PhaseModel.transition(0.9, 4, mastered = true, current = null).name
        assertEquals("mastered", expected) // sanity on the golden expectation
        assertEquals(expected, phaseOf(db, "u1", "pa-kc-001"))
    }

    @Test
    fun `phase backfilled by replay — practice row`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // ewma 0.6 (>=0.50, <0.65), obs 2 ⇒ practice per PhaseModel thresholds.
        seedLegacyMastery(db, "u2", "pa-kc-002", ewma = 0.6, obs = 2)

        TutorMigration.migrate(db)

        val mastered = 0.6 >= KcMastery.MASTERY_THRESHOLD && 2 >= KcMastery.MIN_OBSERVATIONS
        val expected = PhaseModel.transition(0.6, 2, mastered, current = null).name
        assertEquals("practice", expected) // sanity
        assertEquals(expected, phaseOf(db, "u2", "pa-kc-002"))
    }

    @Test
    fun `entry_phase null treated as intro via effectiveEntryPhase`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedLegacyMastery(db, "u3", "pa-kc-003", ewma = 0.4, obs = 1)
        TutorMigration.migrate(db)

        val m = KcMasteryRepo(db).get("u3", "pa-kc-003")
        assertNull(m!!.entryPhase, "entry_phase column is NULL post-migration (not backfilled)")
        assertEquals(Phase.intro, m.effectiveEntryPhase, "NULL entry_phase reads as intro")
    }

    @Test
    fun `backfill is a no-op on an empty kc_mastery table`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // No legacy seed — migrate creates kc_mastery fresh and the replay matches 0 rows.
        val result = TutorMigration.migrate(db)
        assertEquals(MigrationResult.Success, result)
        // table is empty, no error.
        val count = transaction(db) {
            var c = 0
            exec("SELECT COUNT(*) FROM kc_mastery") { rs -> if (rs.next()) c = rs.getInt(1) }
            c
        }
        assertEquals(0, count)
    }
}
