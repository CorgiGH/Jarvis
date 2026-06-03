package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Task 8 (B3, §G) — KcMasteryRepo.recordIn(tx, …) + record() wrapper split.
 *
 * TDD: these tests are written FIRST and must fail (compilation error) until the implementation lands.
 *
 * Tests:
 *  1. recordIn writes phase in the caller's txn, returns KcMastery with phase set.
 *  2. recordIn + a second op in ONE caller txn are atomic — both roll back on failure.
 *  3. record() wrapper still returns the same KcMastery as before (back-compat).
 */
class KcMasteryRecordInTest {

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("m.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KcMasteryTable) }
        UserRepo(db).insert(User("u1", "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return db
    }

    // A dummy table used to exercise the atomicity seam — if a write into it fails, the enclosing
    // txn (which also contains a recordIn) should roll back the kc_mastery write too.
    object AtomicSeamTable : Table("atomic_seam") {
        val id = varchar("id", 26)
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun `recordIn writes phase in same caller txn and returns it`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = KcMasteryRepo(db)
        // First observation: score=1.0 → ewma=1.0, obs=1 → mastered=false (obs<3) → phase=intro
        val mastery = transaction(db) { repo.recordIn(this, "u1", "pa-kc-001", 1.0) }
        assertNotNull(mastery)
        assertEquals("u1", mastery.userId)
        assertEquals("pa-kc-001", mastery.kcId)
        assertEquals(1.0, mastery.ewmaScore, 1e-9)
        assertEquals(1, mastery.observations)
        // phase must be computed and returned
        val expectedPhase = PhaseModel.transition(1.0, 1, mastered = false, current = null)
        assertEquals(expectedPhase, mastery.phase)
        // also verify the row in DB has the phase written
        val fetched = repo.get("u1", "pa-kc-001")
        assertNotNull(fetched)
        assertEquals(expectedPhase, fetched.phase)
    }

    @Test
    fun `recordIn reaches mastered phase after enough high-score observations`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = KcMasteryRepo(db)
        // 3 perfect scores → mastered
        transaction(db) { repo.recordIn(this, "u1", "pa-kc-002", 1.0) }
        transaction(db) { repo.recordIn(this, "u1", "pa-kc-002", 1.0) }
        val mastery = transaction(db) { repo.recordIn(this, "u1", "pa-kc-002", 1.0) }
        assertEquals(Phase.mastered, mastery.phase)
        // confirm DB reflects the phase
        val fetched = repo.get("u1", "pa-kc-002")
        assertNotNull(fetched)
        assertEquals(Phase.mastered, fetched.phase)
    }

    @Test
    fun `recordIn and a second write in ONE caller txn are atomic — rollback propagates`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        transaction(db) { SchemaUtils.create(AtomicSeamTable) }
        val repo = KcMasteryRepo(db)

        // Attempt: recordIn(u1, pa-kc-003) succeeds, then a forced constraint violation rolls
        // back the enclosing txn — the kc_mastery row must NOT be visible afterward.
        try {
            transaction(db) {
                repo.recordIn(this, "u1", "pa-kc-003", 0.8)
                // Force a rollback: insert the same PK twice → UNIQUE constraint failure
                AtomicSeamTable.insert { it[id] = "same-id" }
                AtomicSeamTable.insert { it[id] = "same-id" }
            }
        } catch (_: Throwable) { /* expected — the txn must have rolled back */ }

        // The kc_mastery write must have rolled back with the txn
        val fetched = repo.get("u1", "pa-kc-003")
        assertNull(fetched, "kc_mastery write must have rolled back when the enclosing txn failed")
    }

    @Test
    fun `record wrapper returns same result as calling recordIn in a transaction`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = KcMasteryRepo(db)
        // record() is the thin wrapper — it must produce the same observable result.
        val m = repo.record("u1", "pa-kc-004", 1.0)
        assertNotNull(m)
        assertEquals(1.0, m.ewmaScore, 1e-9)
        assertEquals(1, m.observations)
        // phase must be set (non-null) — this is the new side effect
        assertNotNull(m.phase)
        val expectedPhase = PhaseModel.transition(1.0, 1, mastered = false, current = null)
        assertEquals(expectedPhase, m.phase)
    }

    @Test
    fun `record wrapper correctly carries existing phase on subsequent calls`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = KcMasteryRepo(db)
        repo.record("u1", "pa-kc-005", 1.0)
        repo.record("u1", "pa-kc-005", 1.0)
        val m = repo.record("u1", "pa-kc-005", 1.0)
        // After 3 perfect scores the model computes mastered
        assertEquals(Phase.mastered, m.phase)
    }
}
