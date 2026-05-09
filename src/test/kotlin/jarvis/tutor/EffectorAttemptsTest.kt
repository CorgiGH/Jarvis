package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectorAttemptsTest {

    private fun freshDb(tmp: Path): TutorContext {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, EffectorAttemptsTable)
            UserRepo(db).insert(User("U1", "v", UserScope.OWNER, Instant.now(), Instant.now()))
        }
        return TutorContext(db, tmp)
    }

    @Test
    fun insertedRowStartsPrePending(@TempDir tmp: Path) {
        val ctx = freshDb(tmp)
        val repo = EffectorAttemptRepo(ctx.db)
        val a = repo.insertPrePending(
            id = TutorTypes.ulid(), userId = "U1", taskId = "T1",
            effectorType = EffectorType.APPLY_EDIT,
            targetUri = "file:///c/work/Foo.kt",
            grantId = "G1", nonce = "n1",
        )
        assertEquals(EffectorAttemptState.PRE_PENDING, a.state)
        assertNull(a.shadowSnapshotId)
        assertNull(a.failureReason)
    }

    @Test
    fun stateTransitionPersists(@TempDir tmp: Path) {
        val ctx = freshDb(tmp)
        val repo = EffectorAttemptRepo(ctx.db)
        val id = TutorTypes.ulid()
        repo.insertPrePending(id, "U1", null, EffectorType.APPLY_EDIT,
            "file:///x", "G1", "n1")
        repo.transition(id, EffectorAttemptState.PRE_SEALED, snapshotId = "shadow-1")
        val seal = repo.get(id)
        assertNotNull(seal)
        assertEquals(EffectorAttemptState.PRE_SEALED, seal.state)
        assertEquals("shadow-1", seal.shadowSnapshotId)
        repo.transition(id, EffectorAttemptState.FIRED)
        repo.transition(id, EffectorAttemptState.POST_SEALED)
        assertEquals(EffectorAttemptState.POST_SEALED, repo.get(id)!!.state)
        assertTrue(EffectorAttemptState.POST_SEALED.terminal)
    }

    @Test
    fun watchdogFindsStalePreSealed(@TempDir tmp: Path) {
        val ctx = freshDb(tmp)
        val repo = EffectorAttemptRepo(ctx.db)
        val a = TutorTypes.ulid(); val b = TutorTypes.ulid(); val c = TutorTypes.ulid()
        repo.insertPrePending(a, "U1", null, EffectorType.APPLY_EDIT, "file:///a", "G", "n-a")
        repo.transition(a, EffectorAttemptState.PRE_SEALED, snapshotId = "s-a")
        repo.insertPrePending(b, "U1", null, EffectorType.APPLY_EDIT, "file:///b", "G", "n-b")
        repo.transition(b, EffectorAttemptState.PRE_SEALED, snapshotId = "s-b")
        repo.transition(b, EffectorAttemptState.POST_SEALED)
        repo.insertPrePending(c, "U1", null, EffectorType.APPLY_EDIT, "file:///c", "G", "n-c")
        // c left in PRE_PENDING (never sealed) — also not picked up by
        // the PRE_SEALED-only watchdog query.

        // Pretend we look 5s in the future against a 2s threshold.
        val later = Instant.now().plus(Duration.ofSeconds(5))
        val stale = repo.listStalePreSealed(Duration.ofSeconds(2), later)
        // Only `a` qualifies — b is post-sealed (terminal), c never sealed.
        assertEquals(1, stale.size)
        assertEquals(a, stale[0].id)
    }

    @Test
    fun terminalEnumFlag() {
        assertTrue(EffectorAttemptState.POST_SEALED.terminal)
        assertTrue(EffectorAttemptState.ROLLED_BACK.terminal)
        assertTrue(EffectorAttemptState.FAILED.terminal)
        assertTrue(!EffectorAttemptState.PRE_PENDING.terminal)
        assertTrue(!EffectorAttemptState.PRE_SEALED.terminal)
        assertTrue(!EffectorAttemptState.FIRED.terminal)
    }

    @Test
    fun countByStateAggregates(@TempDir tmp: Path) {
        val ctx = freshDb(tmp)
        val repo = EffectorAttemptRepo(ctx.db)
        repeat(3) { i ->
            val id = TutorTypes.ulid()
            repo.insertPrePending(id, "U1", null, EffectorType.APPLY_EDIT,
                "file:///x$i", "G", "n$i")
            if (i == 0) repo.transition(id, EffectorAttemptState.PRE_SEALED)
            if (i == 1) {
                repo.transition(id, EffectorAttemptState.PRE_SEALED)
                repo.transition(id, EffectorAttemptState.FIRED)
                repo.transition(id, EffectorAttemptState.POST_SEALED)
            }
            // i==2 stays PRE_PENDING
        }
        val counts = repo.countByState("U1")
        assertEquals(1, counts[EffectorAttemptState.PRE_PENDING])
        assertEquals(1, counts[EffectorAttemptState.PRE_SEALED])
        assertEquals(1, counts[EffectorAttemptState.POST_SEALED])
    }

    @Test
    fun failureReasonRecordedOnTransitionToFailed(@TempDir tmp: Path) {
        val ctx = freshDb(tmp)
        val repo = EffectorAttemptRepo(ctx.db)
        val id = TutorTypes.ulid()
        repo.insertPrePending(id, "U1", null, EffectorType.APPLY_EDIT,
            "file:///x", "G", "n")
        repo.transition(id, EffectorAttemptState.PRE_SEALED, snapshotId = "s1")
        repo.transition(id, EffectorAttemptState.FAILED, failureReason = "daemon timeout")
        val a = repo.get(id)!!
        assertEquals(EffectorAttemptState.FAILED, a.state)
        assertEquals("daemon timeout", a.failureReason)
    }
}
