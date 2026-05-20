package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesRepoTest {
    private val dbDir = Files.createTempDirectory("prefs")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(UserPreferencesTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `defaults are returned for an unknown user`() {
        val prefs = UserPreferencesRepo(db).get("nobody")
        assertEquals("static", prefs.hintMode)
        assertNull(prefs.loggingPausedUntil)
    }

    @Test fun `set then get round-trips`() {
        val repo = UserPreferencesRepo(db)
        val until = Instant.now().plusSeconds(3600)
        repo.set("u1", hintMode = "llm", loggingPausedUntil = until)
        val prefs = repo.get("u1")
        assertEquals("llm", prefs.hintMode)
        assertEquals(until.epochSecond, prefs.loggingPausedUntil?.epochSecond)
    }
}
