package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EffectorFuzzerTest {
    private val rng = Random(0xC0FFEE)

    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("fuzzer").resolve("t.db").toString()
    ).also { db -> transaction(db) {
        SchemaUtils.create(UsersTable, TrustGrantsTable, AuditLinesTable)
    } }

    private fun seedSetup(): Triple<EffectorValidator, String, String> {
        val db = freshDb()
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val grantId = TutorTypes.ulid()
        TrustGrantRepo(db).insert(TrustGrant(grantId, userId, listOf("file:///c/uaic/**"),
            setOf(EffectorType.APPLY_EDIT), Instant.now().plusSeconds(3600),
            1_000_000, 0, GrantSource.UI, null, Instant.now()))
        return Triple(EffectorValidator(db, NonceCache(capacity = 10_000)), userId, grantId)
    }

    private fun goodReq(grantId: String, version: String) = ApplyEditRequest(
        taskId = "T", effectorId = TutorTypes.ulid(),
        targetUri = "file:///c/uaic/ps/x.R",
        expectedDocVersion = version,
        edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
        nonce = TutorTypes.ulid(), grantId = grantId,
    )

    @Test
    fun `500 valid requests all pass`() {
        val (v, u, g) = seedSetup()
        repeat(500) {
            val r = v.validate(u, goodReq(g, "v1"), currentDocVersion = "v1")
            assertEquals(ValidationResult.Pass, r, "iter $it must pass")
        }
    }

    @Test
    fun `stale doc version always rejects`() {
        val (v, u, g) = seedSetup()
        repeat(200) {
            val r = v.validate(u, goodReq(g, "vSTALE-$it"), currentDocVersion = "vCURRENT")
            assertIs<ValidationResult.Reject>(r)
            assertEquals(Outcome.STALE_DOC, r.outcome)
        }
    }

    @Test
    fun `200 requests targeting paths outside scope all reject`() {
        val (v, u, g) = seedSetup()
        val outside = listOf(
            "file:///c/other/foo.R",
            "file:///etc/passwd",
            "file:///c/Users/x/.ssh/id_rsa",
            "file:///c/proj/.env.production",
            "file:///c/proj/.git/config",
        )
        repeat(200) {
            val pick = outside[rng.nextInt(outside.size)]
            val r = v.validate(u, goodReq(g, "v1").copy(targetUri = pick), currentDocVersion = "v1")
            assertIs<ValidationResult.Reject>(r, "iter $it path=$pick")
            assertEquals(Outcome.PATH_DENIED, r.outcome)
        }
    }

    @Test
    fun `replayed nonce rejects every time after first`() {
        val (v, u, g) = seedSetup()
        repeat(100) {
            val req = goodReq(g, "v1")
            val r1 = v.validate(u, req, currentDocVersion = "v1")
            assertEquals(ValidationResult.Pass, r1)
            val r2 = v.validate(u, req.copy(effectorId = TutorTypes.ulid()),
                                currentDocVersion = "v1")
            assertIs<ValidationResult.Reject>(r2)
            assertEquals(Outcome.REJECTED, r2.outcome)
        }
    }

    @Test
    fun `random shuffle never approves invalid`() {
        val (v, u, g) = seedSetup()
        repeat(1000) {
            val staleVersion = rng.nextBoolean()
            val outsidePath = rng.nextBoolean()
            val invalid = staleVersion || outsidePath
            val req = goodReq(g, if (staleVersion) "vBAD" else "v1").copy(
                targetUri = if (outsidePath) "file:///etc/passwd" else "file:///c/uaic/ps/x.R",
            )
            val r = v.validate(u, req, currentDocVersion = "v1")
            if (invalid) {
                assertIs<ValidationResult.Reject>(r,
                    "iter $it invalid={stale=$staleVersion path=$outsidePath} got $r")
            }
        }
    }
}
