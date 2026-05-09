package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EffectorValidatorTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("ev-test").resolve("t.db").toString()
    ).also { db -> transaction(db) {
        SchemaUtils.create(UsersTable, TrustGrantsTable, AuditLinesTable)
    } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    private fun seedGrant(db: org.jetbrains.exposed.sql.Database, userId: String, scope: List<String>): String =
        TutorTypes.ulid().also {
            TrustGrantRepo(db).insert(TrustGrant(it, userId, scope, setOf(EffectorType.APPLY_EDIT),
                Instant.now().plusSeconds(3600), 50, 0, GrantSource.UI, null, Instant.now()))
        }

    private fun req(taskId: String = "T01", uri: String = "file:///c/uaic/ps/x.R",
                    grantId: String, version: String = "v1") = ApplyEditRequest(
        taskId = taskId, effectorId = TutorTypes.ulid(),
        targetUri = uri, expectedDocVersion = version,
        edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "a")),
        nonce = TutorTypes.ulid(), grantId = grantId,
    )

    @Test
    fun `valid request passes`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///c/uaic/ps/x.R"), currentDocVersion = "v1")
        assertIs<ValidationResult.Pass>(r)
    }

    @Test
    fun `path outside grant scope rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///c/uaic/alo/x.R"), currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.PATH_DENIED, r.outcome)
    }

    @Test
    fun `stale doc version rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, version = "v1"), currentDocVersion = "v2")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.STALE_DOC, r.outcome)
    }

    @Test
    fun `expired grant rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val expired = TutorTypes.ulid().also {
            TrustGrantRepo(db).insert(TrustGrant(it, u, listOf("file:///c/**"),
                setOf(EffectorType.APPLY_EDIT), Instant.now().minusSeconds(1),
                10, 0, GrantSource.UI, null, Instant.now()))
        }
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = expired), currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.REJECTED, r.outcome)
    }

    @Test
    fun `duplicate nonce rejected`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("file:///c/uaic/ps/**"))
        val nonces = NonceCache()
        val v = EffectorValidator(db, nonces)
        val first = req(grantId = g)
        val second = first.copy(effectorId = TutorTypes.ulid())  // same nonce
        val r1 = v.validate(u, first, currentDocVersion = "v1"); assertIs<ValidationResult.Pass>(r1)
        val r2 = v.validate(u, second, currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r2)
        assertEquals(Outcome.REJECTED, r2.outcome)
    }

    @Test
    fun `path matches blocklist always denied even with grant`() {
        val db = freshDb()
        val u = seedUser(db)
        val g = seedGrant(db, u, listOf("**"))
        val v = EffectorValidator(db, NonceCache())
        val r = v.validate(u, req(grantId = g, uri = "file:///home/user/.ssh/config"),
                           currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.PATH_DENIED, r.outcome)
    }
}
