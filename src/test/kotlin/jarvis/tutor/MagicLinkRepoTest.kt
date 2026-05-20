package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagicLinkRepoTest {
    private val dbDir = Files.createTempDirectory("magiclink")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(MagicLinkTokensTable) } }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `issue then consume returns the claim`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("a@example.com", "ro", ttlSeconds = 900)
        val claim = repo.consume(raw)
        assertEquals("a@example.com", claim?.email)
        assertEquals("ro", claim?.lang)
    }

    @Test fun `a token consumes only once`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("b@example.com", "en", ttlSeconds = 900)
        assertEquals("b@example.com", repo.consume(raw)?.email)
        assertNull(repo.consume(raw))                 // second use rejected
    }

    @Test fun `an expired token does not consume`() {
        val repo = MagicLinkRepo(db)
        val raw = repo.issue("c@example.com", "ro", ttlSeconds = -1) // already expired
        assertNull(repo.consume(raw))
    }

    @Test fun `an unknown token returns null`() {
        assertNull(MagicLinkRepo(db).consume("deadbeef"))
    }
}
