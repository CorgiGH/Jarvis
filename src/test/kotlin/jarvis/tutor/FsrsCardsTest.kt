package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsCardsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("fsrs-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) } }

    @Test
    fun `insert and findDue returns due cards in dueAt order`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        val a = TutorCard(TutorTypes.ulid(), u, FsrsSource.MANUAL, "ref-a",
            "front a", "back a", FsrsState(2.5, 1.0, 0.9, now.plusSeconds(60), now, 0))
        val b = TutorCard(TutorTypes.ulid(), u, FsrsSource.MANUAL, "ref-b",
            "front b", "back b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(60), now, 0))
        repo.insert(a); repo.insert(b)
        val due = repo.findDueForUser(u, asOf = now)
        assertEquals(1, due.size)
        assertEquals(b.id, due[0].id)
    }

    @Test
    fun `inserted card defaults to status ACTIVE (M-SEED)`() {
        // M-SEED: a card inserted via the repo (incl. FsrsSeedMain seeds) is ACTIVE so it
        // surfaces in the status='ACTIVE'-filtered queue. A null TutorCard.status -> ACTIVE.
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        repo.insert(
            TutorCard(
                TutorTypes.ulid(), u, FsrsSource.MANUAL, "ref",
                "f", "b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(10), now, 0),
            ),
        )
        val due = repo.findDueForUser(u, asOf = now)
        assertEquals(1, due.size)
        assertEquals(CardStatus.ACTIVE, due[0].status)
    }

    @Test
    fun `findDueForUser scopes to userId`() {
        val db = freshDb()
        val u1 = TutorTypes.ulid(); val u2 = TutorTypes.ulid()
        UserRepo(db).insert(User(u1, "a", UserScope.OWNER, Instant.now(), Instant.now()))
        UserRepo(db).insert(User(u2, "b", UserScope.FRIEND, Instant.now(), Instant.now()))
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        repo.insert(TutorCard(TutorTypes.ulid(), u1, FsrsSource.MANUAL, "x",
            "f", "b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(10), now, 0)))
        repo.insert(TutorCard(TutorTypes.ulid(), u2, FsrsSource.MANUAL, "y",
            "f", "b", FsrsState(2.5, 1.0, 0.9, now.minusSeconds(10), now, 0)))
        assertEquals(1, repo.findDueForUser(u1, now).size)
        assertEquals(1, repo.findDueForUser(u2, now).size)
        assertTrue(repo.findDueForUser(u1, now).all { it.userId == u1 })
    }
}
