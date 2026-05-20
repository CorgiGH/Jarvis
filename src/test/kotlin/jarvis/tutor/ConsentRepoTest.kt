package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentRepoTest {
    private val dbDir = Files.createTempDirectory("consent")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(ConsentLogTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `record then list returns the consent events newest-first`() {
        val repo = ConsentRepo(db)
        repo.record("u1", "tos", granted = true)
        repo.record("u1", "privacy", granted = true)
        val events = repo.listForUser("u1")
        assertEquals(2, events.size)
        assertEquals("privacy", events.first().consentType)
    }
}
