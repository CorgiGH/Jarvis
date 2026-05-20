package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLiteracyRepoTest {
    private val dbDir = Files.createTempDirectory("ailit")
    private val db = TutorDb.connect(dbDir.resolve("t.db").toString())
    init { transaction(db) { SchemaUtils.create(AiLiteracyConfirmationTable) } }
    @AfterTest fun cleanup() { dbDir.toFile().deleteRecursively() }

    @Test fun `a user is unconfirmed until they confirm the current version`() {
        val repo = AiLiteracyRepo(db)
        assertFalse(repo.hasConfirmedCurrent("u1"))
        repo.confirm("u1", AI_LITERACY_VERSION, "ro")
        assertTrue(repo.hasConfirmedCurrent("u1"))
    }

    @Test fun `confirming an old version does not satisfy the current gate`() {
        val repo = AiLiteracyRepo(db)
        repo.confirm("u2", "v0.0-old", "ro")
        assertFalse(repo.hasConfirmedCurrent("u2"))
    }

    @Test fun `confirming the same version twice is a no-op`() {
        val repo = AiLiteracyRepo(db)
        repo.confirm("u3", AI_LITERACY_VERSION, "ro")
        repo.confirm("u3", AI_LITERACY_VERSION, "en") // second call must not throw
        assertTrue(repo.hasConfirmedCurrent("u3"))
    }
}
