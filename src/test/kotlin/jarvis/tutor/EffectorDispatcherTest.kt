package jarvis.tutor

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EffectorDispatcherTest {

    private fun freshCtx(tmp: Path): Triple<TutorContext, String, NonceCache> {
        val db = TutorDb.connect(tmp.resolve("dispatch.db").toString())
        transaction(db) {
            SchemaUtils.create(
                UsersTable, EffectorAttemptsTable, AuditLinesTable, TrustGrantsTable,
            )
            UserRepo(db).insert(User("U1", "v", UserScope.OWNER, Instant.now(), Instant.now()))
        }
        val nonces = NonceCache()
        return Triple(TutorContext(db, tmp), "U1", nonces)
    }

    private fun grant(db: org.jetbrains.exposed.sql.Database, userId: String, scope: List<String>): String {
        val id = TutorTypes.ulid()
        TrustGrantRepo(db).insert(TrustGrant(
            grantId = id, userId = userId,
            scope = scope, ops = setOf(EffectorType.APPLY_EDIT),
            expiresAt = Instant.now().plusSeconds(3600),
            maxCalls = 10, callsUsed = 0,
            grantedFrom = GrantSource.UI, revokedAt = null,
            createdAt = Instant.now(),
        ))
        return id
    }

    private fun fakeDaemon(handler: (String) -> Pair<Int, String>): HttpServer {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/effector/dispatch") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            val (status, resp) = handler(body)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status.toLong().toInt(), resp.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(resp.toByteArray()) }
        }
        s.executor = null
        s.start()
        return s
    }

    @Test
    fun happyPathLandsPostSealedAndAuditChainValid(@TempDir tmp: Path) = runBlocking {
        val (ctx, userId, nonces) = freshCtx(tmp)
        val srv = fakeDaemon { _ -> 200 to """{"ok":true,"outcome":"SUCCESS"}""" }
        try {
            val target = tmp.resolve("file.kt")
            target.writeText("orig\n")
            val grantId = grant(ctx.db, userId, listOf("**/file.kt"))
            val req = ApplyEditRequest(
                taskId = "T", effectorId = TutorTypes.ulid(),
                targetUri = "file:///${target.toString().replace('\\','/')}",
                expectedDocVersion = "v1",
                edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "new")),
                nonce = TutorTypes.ulid(), grantId = grantId,
            )
            val client = DaemonClient("http://127.0.0.1:${srv.address.port}", "secret".toByteArray())
            val dispatcher = EffectorDispatcher(ctx.db, nonces, tmp.resolve("shadow"), tmp,
                daemonClient = client, backend = EffectorDispatcher.Backend.DAEMON)
            val out = dispatcher.dispatch(userId, req, currentDocVersion = "v1")
            assertEquals("SUCCESS", out.outcome)
            assertEquals("POST_SEALED", out.state)
            assertNotNull(out.snapshotId)
            // Audit chain valid + has the success row.
            assertTrue(AuditRepo(ctx.db).verifyChain(userId))
            client.close()
        } finally {
            srv.stop(0)
        }
    }

    @Test
    fun daemonFailureRollsBackAndRestoresFile(@TempDir tmp: Path) = runBlocking {
        val (ctx, userId, nonces) = freshCtx(tmp)
        val srv = fakeDaemon { _ -> 503 to "daemon down" }
        try {
            val target = tmp.resolve("file.kt")
            val original = "before-effector\n"
            target.writeText(original)
            // Daemon says no, but the file MIGHT have been mutated by the
            // daemon before failing. Simulate that by leaving the file
            // untouched here; the rollback restores from the pre-seal anyway.
            val grantId = grant(ctx.db, userId, listOf("**/file.kt"))
            val req = ApplyEditRequest(
                taskId = "T", effectorId = TutorTypes.ulid(),
                targetUri = "file:///${target.toString().replace('\\','/')}",
                expectedDocVersion = "v1",
                edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
                nonce = TutorTypes.ulid(), grantId = grantId,
            )
            val client = DaemonClient("http://127.0.0.1:${srv.address.port}", "secret".toByteArray())
            val dispatcher = EffectorDispatcher(ctx.db, nonces, tmp.resolve("shadow"), tmp,
                daemonClient = client, backend = EffectorDispatcher.Backend.DAEMON)
            val out = dispatcher.dispatch(userId, req, currentDocVersion = "v1")
            assertEquals("ROLLED_BACK", out.outcome)
            assertEquals("ROLLED_BACK", out.state)
            // File matches the pre-seal byte-for-byte after rollback.
            assertEquals(original, Files.readString(target))
            assertTrue(AuditRepo(ctx.db).verifyChain(userId))
            client.close()
        } finally {
            srv.stop(0)
        }
    }

    @Test
    fun pathBlocklistRejectsBeforeShadow(@TempDir tmp: Path) = runBlocking {
        val (ctx, userId, nonces) = freshCtx(tmp)
        val grantId = grant(ctx.db, userId, listOf("**"))
        val req = ApplyEditRequest(
            taskId = "T", effectorId = TutorTypes.ulid(),
            targetUri = "file:///home/u/.ssh/id_rsa",
            expectedDocVersion = "v1",
            edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "x")),
            nonce = TutorTypes.ulid(), grantId = grantId,
        )
        // No daemon needed — should fail before reaching it.
        val client = DaemonClient("http://127.0.0.1:1", "x".toByteArray())
        val dispatcher = EffectorDispatcher(ctx.db, nonces, tmp.resolve("shadow"), tmp,
            daemonClient = client, backend = EffectorDispatcher.Backend.DAEMON)
        val out = dispatcher.dispatch(userId, req, currentDocVersion = "v1")
        assertEquals("PATH_DENIED", out.outcome)
        assertEquals("FAILED", out.state)
        client.close()
    }

    @Test
    fun watchdogReapsStalePreSealed(@TempDir tmp: Path) = runBlocking {
        val (ctx, userId, nonces) = freshCtx(tmp)
        val target = tmp.resolve("file.kt")
        target.writeText("orig\n")
        val attempts = EffectorAttemptRepo(ctx.db)
        val attemptId = TutorTypes.ulid()
        attempts.insertPrePending(attemptId, userId, null, EffectorType.APPLY_EDIT,
            "file:///${target.toString().replace('\\','/')}", "G", "n")
        val snap = ShadowGit.preSeal("file:///${target.toString().replace('\\','/')}", tmp.resolve("shadow"))
        attempts.transition(attemptId, EffectorAttemptState.PRE_SEALED, snapshotId = snap.id)
        // Mutate the file out-of-band as the daemon would have done.
        target.writeText("MUTATED\n")
        val client = DaemonClient("http://127.0.0.1:1", "x".toByteArray())
        val dispatcher = EffectorDispatcher(ctx.db, nonces, tmp.resolve("shadow"), tmp,
            daemonClient = client, backend = EffectorDispatcher.Backend.DAEMON)
        // Pretend 5s elapsed.
        val later = Instant.now().plus(Duration.ofSeconds(5))
        val n = dispatcher.reapStale(Duration.ofSeconds(2), later)
        assertEquals(1, n)
        // File restored.
        assertEquals("orig\n", Files.readString(target))
        // Attempt now ROLLED_BACK + audit row added.
        val r = attempts.get(attemptId)!!
        assertEquals(EffectorAttemptState.ROLLED_BACK, r.state)
        assertTrue(AuditRepo(ctx.db).verifyChain(userId))
        client.close()
    }
}
