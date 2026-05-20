package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SessionRepoHardeningTest {
    private val dbDir = Files.createTempDirectory("sessionhard")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init {
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable) }
        // Seed users required by FK constraint on sessions.user_id
        val now = Instant.now()
        UserRepo(db).insert(User("user-1", "u1", UserScope.OWNER, now, now))
        UserRepo(db).insert(User("user-2", "u2", UserScope.OWNER, now, now))
        UserRepo(db).insert(User("user-3", "u3", UserScope.OWNER, now, now))
        UserRepo(db).insert(User("user-4", "u4", UserScope.OWNER, now, now))
    }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `create returns a raw sid that resolves back to the user`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-1", ttlSeconds = 3600)
        assertEquals("user-1", repo.findUserId(raw))
    }

    @Test fun `the stored sid value is NOT the raw sid`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-2", ttlSeconds = 3600)
        val stored = transaction(db) {
            SessionsTable.selectAll().where { SessionsTable.userId eq "user-2" }.single()[SessionsTable.sid]
        }
        assertNotEquals(raw, stored)              // DB holds the hash, not the bearer token
    }

    @Test fun `a wrong sid does not resolve`() {
        val repo = SessionRepo(db)
        repo.create("user-3", ttlSeconds = 3600)
        assertNull(repo.findUserId("not-a-real-sid"))
    }

    @Test fun `revoke kills the session`() {
        val repo = SessionRepo(db)
        val raw = repo.create("user-4", ttlSeconds = 3600)
        repo.revoke(raw)
        assertNull(repo.findUserId(raw))
    }
}
