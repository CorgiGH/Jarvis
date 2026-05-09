package jarvis.tutor

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class LayerAAcceptanceTest {

    @Test
    fun `acceptance — token issue then setup then chat fetch then validator reject`() = testApplication {
        val tmp = Files.createTempDirectory("accept")

        var capturedDb: org.jetbrains.exposed.sql.Database? = null
        var capturedLedgerDir: java.nio.file.Path? = null

        application {
            installTutorContext(tmp.resolve("t.db").toString(), tmp)
            installTutorRoutes()
            // Capture the freshly-installed context for assertions outside the app block.
            val ctx = attributes[TutorContextKey]
            capturedDb = ctx.db
            capturedLedgerDir = ctx.ledgerDir
        }
        startApplication()

        val db = assertNotNull(capturedDb, "TutorContext should be installed")
        assertNotNull(capturedLedgerDir)

        // 1. Seed a user + token.
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "victor", UserScope.OWNER, Instant.now(), Instant.now()))
        val raw = TokenRepo(db).issue(userId, "primary")

        // 2. Hit /auth/setup, must redirect (no auto-follow).
        val client = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        val authRes = client.get("/auth/setup?t=$raw")
        assertEquals(HttpStatusCode.Found, authRes.status)

        // 3. /tutor/ now responds.
        val tutorRes = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, tutorRes.status)
        val body = tutorRes.bodyAsText()
        assertTrue(body.contains("<div id=\"root\">") || body.contains("<div id=\\\"root\\\">"),
            "expected root div in served bundle")

        // 4. /api/v1/health works.
        val healthRes = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, healthRes.status)
        assertTrue(healthRes.bodyAsText().contains("\"ok\":true"))

        // 5. Audit log starts clean and chain-valid for new user.
        val audit = AuditRepo(db)
        assertTrue(audit.verifyChain(userId), "audit chain must be valid for fresh user")

        // 6. Effector validator rejects an out-of-scope path even with grant.
        val validator = EffectorValidator(db, NonceCache())
        val grantId = TutorTypes.ulid()
        TrustGrantRepo(db).insert(TrustGrant(
            grantId = grantId,
            userId = userId,
            scope = listOf("file:///c/uaic/**"),
            ops = setOf(EffectorType.APPLY_EDIT),
            expiresAt = Instant.now().plusSeconds(3600),
            maxCalls = 50, callsUsed = 0,
            grantedFrom = GrantSource.UI, revokedAt = null,
            createdAt = Instant.now(),
        ))
        val req = ApplyEditRequest(
            taskId = "T", effectorId = TutorTypes.ulid(),
            targetUri = "file:///etc/passwd",
            expectedDocVersion = "v1",
            edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
            nonce = TutorTypes.ulid(), grantId = grantId,
        )
        val r = validator.validate(userId, req, currentDocVersion = "v1")
        assertIs<ValidationResult.Reject>(r)
        assertEquals(Outcome.PATH_DENIED, r.outcome)

        // 7. Multi-tenant isolation: a second user with no tasks lists empty.
        val u2 = TutorTypes.ulid()
        UserRepo(db).insert(User(u2, "alice", UserScope.FRIEND, Instant.now(), Instant.now()))
        assertEquals(0, TaskRepo(db).listForUser(u2).size,
            "fresh user should see no tasks")
    }
}
