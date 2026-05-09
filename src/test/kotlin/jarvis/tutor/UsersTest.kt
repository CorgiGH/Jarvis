package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UsersTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("users-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable) } }

    @Test
    fun `insert and find owner user`() {
        val db = freshDb()
        val repo = UserRepo(db)
        val u = User(id = TutorTypes.ulid(), name = "victor", scope = UserScope.OWNER,
                     createdAt = Instant.now(), lastSeenAt = Instant.now())
        repo.insert(u)
        val found = repo.findById(u.id)
        assertNotNull(found)
        assertEquals("victor", found.name)
        assertEquals(UserScope.OWNER, found.scope)
    }

    @Test
    fun `findById returns null when absent`() {
        val repo = UserRepo(freshDb())
        assertEquals(null, repo.findById("MISSING"))
    }

    @Test
    fun `touchLastSeen updates timestamp`() {
        val db = freshDb()
        val repo = UserRepo(db)
        val u = User(id = TutorTypes.ulid(), name = "v", scope = UserScope.OWNER,
                     createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                     lastSeenAt = Instant.parse("2026-01-01T00:00:00Z"))
        repo.insert(u)
        val before = repo.findById(u.id)!!.lastSeenAt
        Thread.sleep(5)
        repo.touchLastSeen(u.id, Instant.now())
        val after = repo.findById(u.id)!!.lastSeenAt
        assert(after.isAfter(before))
    }
}
