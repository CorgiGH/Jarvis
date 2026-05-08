package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokensTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("tokens-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TokensTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database, name: String = "u"): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, name, UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `issue stores hashed token and findUserIdByToken returns userId`() {
        val db = freshDb()
        val u = seedUser(db, "victor")
        val tokens = TokenRepo(db)
        val raw = tokens.issue(userId = u, label = "laptop")
        assertEquals(64, raw.length)
        assertEquals(u, tokens.findUserIdByToken(raw))
    }

    @Test
    fun `revoke removes the token`() {
        val db = freshDb()
        val u = seedUser(db, "u2")
        val tokens = TokenRepo(db)
        val raw = tokens.issue(u, "phone")
        assertNotNull(tokens.findUserIdByToken(raw))
        tokens.revoke(raw)
        assertEquals(null, tokens.findUserIdByToken(raw))
    }

    @Test
    fun `findUserIdByToken returns null on bogus token`() {
        val db = freshDb()
        val tokens = TokenRepo(db)
        assertEquals(null, tokens.findUserIdByToken("bogus"))
    }
}
