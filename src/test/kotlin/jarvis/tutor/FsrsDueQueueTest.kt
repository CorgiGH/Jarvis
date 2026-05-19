package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsDueQueueTest {
    private fun freshDb(tmp: Path) = TutorDb.connect(tmp.resolve("t.db").toString()).also { db ->
        transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) }
    }

    private fun seedUserAndCards(db: org.jetbrains.exposed.sql.Database): String {
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val now = Instant.now()
        val r = FsrsCardRepo(db)
        r.insert(TutorCard(
            id = "c1", userId = u, source = FsrsSource.GAP_PROMOTION, sourceRef = "g1",
            front = "Q1", back = "A1",
            state = FsrsState(2.0, 1.0, 0.9, now.minusSeconds(60), now, 0),
        ))
        r.insert(TutorCard(
            id = "c2", userId = u, source = FsrsSource.GAP_PROMOTION, sourceRef = "g2",
            front = "Q2", back = "A2",
            state = FsrsState(2.0, 1.0, 0.9, now.plusSeconds(86400), now, 0),
        ))
        return u
    }

    @Test
    fun `due returns cards with dueAt before now`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val u = seedUserAndCards(db)
        val cards = FsrsDueQueue.due(db, u, Instant.now(), limit = 10)
        assertEquals(1, cards.size)
        assertEquals("c1", cards[0].id)
    }

    @Test
    fun `forecast counts cards due in time windows`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val u = seedUserAndCards(db)
        val now = Instant.now()
        val f = FsrsDueQueue.forecast(db, u, now)
        assertTrue(f.tomorrow >= 1)
        assertTrue(f.thisWeek >= 1)
    }

    @Test
    fun `forecast dueNow counts only cards past due as of now`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val u = seedUserAndCards(db)
        val now = Instant.now()
        // seedUserAndCards: c1 due now-60s, c2 due now+1d.
        val f = FsrsDueQueue.forecast(db, u, now)
        assertEquals(1, f.dueNow)       // only c1 is past due
        assertEquals(2, f.tomorrow)     // c1 + c2 both within 24h
        assertEquals(2, f.thisMonth)
    }
}
