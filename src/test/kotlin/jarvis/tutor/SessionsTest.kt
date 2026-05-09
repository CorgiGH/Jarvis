package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("sess-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `create returns opaque sid and find resolves to userId`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = SessionRepo(db)
        val sid = repo.create(u, ttlSeconds = 3600)
        assertEquals(64, sid.length)
        assertEquals(u, repo.findUserId(sid))
    }

    @Test
    fun `expired session is not returned`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = SessionRepo(db)
        val sid = repo.create(u, ttlSeconds = -1)
        assertNull(repo.findUserId(sid))
    }

    @Test
    fun `revoke deletes session`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = SessionRepo(db)
        val sid = repo.create(u, 60)
        assertNotNull(repo.findUserId(sid))
        repo.revoke(sid)
        assertNull(repo.findUserId(sid))
    }
}
