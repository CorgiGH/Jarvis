package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustGrantsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("grants-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TrustGrantsTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `insert and findActive grant`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(
            grantId = TutorTypes.ulid(), userId = u,
            scope = listOf("~/uaic/ps-hw/**"),
            ops = setOf(EffectorType.APPLY_EDIT, EffectorType.INSERT_SCRATCHPAD),
            expiresAt = Instant.now().plusSeconds(3600),
            maxCalls = 50, callsUsed = 0,
            grantedFrom = GrantSource.UI, revokedAt = null,
            createdAt = Instant.now(),
        )
        repo.insert(g)
        val found = repo.findActive(g.grantId, Instant.now())
        assertNotNull(found)
        assertEquals(g.grantId, found.grantId)
    }

    @Test
    fun `expired grant not returned`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().minusSeconds(1),
            10, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        assertNull(repo.findActive(g.grantId, Instant.now()))
    }

    @Test
    fun `revoked grant not returned`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            10, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        repo.revoke(g.grantId, Instant.now())
        assertNull(repo.findActive(g.grantId, Instant.now()))
    }

    @Test
    fun `tryConsume increments callsUsed and enforces maxCalls cap`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TrustGrantRepo(db)
        val g = TrustGrant(TutorTypes.ulid(), u, listOf("~/x/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            2, 0, GrantSource.UI, null, Instant.now())
        repo.insert(g)
        assertTrue(repo.tryConsume(g.grantId))
        assertTrue(repo.tryConsume(g.grantId))
        assertEquals(false, repo.tryConsume(g.grantId))
    }
}
