package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepoEmailTest {
    private val dbDir = Files.createTempDirectory("userrepoemail")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(UsersTable) } }

    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `findByEmail returns the user when present`() {
        val repo = UserRepo(db)
        val u = User("01AAAAAAAAAAAAAAAAAAAAAAAA", "alex", UserScope.OWNER,
            Instant.now(), Instant.now(), email = "alex@example.com", lang = "ro")
        repo.insert(u)
        assertEquals("alex@example.com", repo.findByEmail("alex@example.com")?.email)
        assertNull(repo.findByEmail("nobody@example.com"))
    }

    @Test fun `upsertByEmail creates a new FRIEND user then returns the same on second call`() {
        val repo = UserRepo(db)
        val first = repo.upsertByEmail("friend@example.com", "en")
        assertEquals(UserScope.FRIEND, first.scope)
        assertEquals("en", first.lang)
        val second = repo.upsertByEmail("friend@example.com", "ro")
        assertEquals(first.id, second.id)            // same user, not a duplicate
        assertNotNull(repo.findByEmail("friend@example.com"))
    }
}
