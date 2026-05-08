package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("audit-test").resolve("audit.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, AuditLinesTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    @Test
    fun `append assigns monotonic seq and chains hashes`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = AuditRepo(db)
        val a = repo.append(u, """{"event":"first"}""", Outcome.SUCCESS)
        val b = repo.append(u, """{"event":"second"}""", Outcome.SUCCESS)
        assertEquals(1L, a.seq)
        assertEquals(2L, b.seq)
        assertEquals(a.thisHash, b.prevHash)
    }

    @Test
    fun `verifyChain returns true for intact log`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = AuditRepo(db)
        repo.append(u, """{"event":"a"}""", Outcome.SUCCESS)
        repo.append(u, """{"event":"b"}""", Outcome.SUCCESS)
        repo.append(u, """{"event":"c"}""", Outcome.REJECTED)
        assertTrue(repo.verifyChain(u))
    }

    @Test
    fun `seq starts at 1 per user`() {
        val db = freshDb()
        val u1 = seedUser(db)
        val u2 = seedUser(db)
        val repo = AuditRepo(db)
        val a = repo.append(u1, """{"e":1}""", Outcome.SUCCESS)
        val b = repo.append(u2, """{"e":2}""", Outcome.SUCCESS)
        assertEquals(1L, a.seq)
        assertEquals(1L, b.seq)
    }
}
