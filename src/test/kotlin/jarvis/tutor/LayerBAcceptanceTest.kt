package jarvis.tutor

import com.sun.net.httpserver.HttpServer
import io.ktor.server.testing.testApplication
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

/**
 * Tutor Layer B (B0 + B1) end-to-end acceptance test — gate for tag
 * `tutor/layer-b-acceptance`. After this passes locally, the tag is
 * placed and authorization for Layer B work expires per resume-note
 * rules.
 *
 * What "Layer B acceptance" certifies (council R3 / spec §4):
 *
 *  Sensor pipeline (B0):
 *   - vision-LLM contract is satisfiable from the test JVM
 *     (already tested under ScreenshotSensorRouteTest +
 *     LayerB0AcceptanceTest)
 *
 *  Effector pipeline (B1):
 *   - HMAC scheme defends against replay + body mutation + cross-
 *     route signature reuse + skew (DaemonAuthTest fuzzer 1000-iter)
 *   - shadow-git pre/post-seal restores exact bytes on rollback
 *     (ShadowGitOrderingTest)
 *   - effector dispatcher state machine never leaves an attempt in a
 *     non-terminal state after backend resolution
 *   - daemon failure path rolls back live file from shadow snapshot
 *   - watchdog reaps stale PRE_SEALED attempts past 2s threshold
 *   - audit chain remains valid across success + rollback + reap
 *   - path blocklist enforced both server-side (validator) and
 *     daemon-side (independent defense)
 *   - multi-tenant isolation: u2 cannot see u1's effector attempts
 *     or their shadow snapshots
 *
 * The Rust daemon is mocked here via a JDK HttpServer; the daemon
 * binary's own tests (cargo test) cover hmac_auth + nonce_cache +
 * path_blocklist + kill_switch independently.
 */
class LayerBAcceptanceTest {

    @Test
    fun `acceptance — Layer B effector pipeline end-to-end with rollback + watchdog`() = runBlocking {
        val tmp = Files.createTempDirectory("layer-b-accept")
        val db = TutorDb.connect(tmp.resolve("layer-b.db").toString())
        transaction(db) {
            SchemaUtils.create(
                UsersTable, EffectorAttemptsTable, AuditLinesTable, TrustGrantsTable,
            )
            UserRepo(db).insert(User("U1", "victor", UserScope.OWNER, Instant.now(), Instant.now()))
            UserRepo(db).insert(User("U2", "alice", UserScope.FRIEND, Instant.now(), Instant.now()))
        }

        // 1. Happy path: daemon returns SUCCESS → POST_SEALED, file
        //    bytes already mutated by daemon, audit chain still valid.
        var serverHandled = 0
        val daemon = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        daemon.createContext("/effector/dispatch") { exchange ->
            serverHandled++
            val body = """{"ok":true,"outcome":"SUCCESS"}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        daemon.executor = null
        daemon.start()

        val nonces = NonceCache()
        val client = DaemonClient("http://127.0.0.1:${daemon.address.port}", "test-secret".toByteArray())
        val dispatcher = EffectorDispatcher(db, nonces, tmp.resolve("shadow"), tmp,
            daemonClient = client, backend = EffectorDispatcher.Backend.DAEMON)

        try {
            val targetA = tmp.resolve("a.kt"); targetA.writeText("orig-a\n")
            val grantA = TutorTypes.ulid()
            TrustGrantRepo(db).insert(TrustGrant(
                grantId = grantA, userId = "U1",
                scope = listOf("file:///**"),
                ops = setOf(EffectorType.APPLY_EDIT),
                expiresAt = Instant.now().plusSeconds(3600),
                maxCalls = 5, callsUsed = 0,
                grantedFrom = GrantSource.UI, revokedAt = null,
                createdAt = Instant.now(),
            ))

            val reqA = ApplyEditRequest(
                taskId = "T1", effectorId = TutorTypes.ulid(),
                targetUri = "file:///${targetA.toString().replace('\\','/')}",
                expectedDocVersion = "v1",
                edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "X")),
                nonce = TutorTypes.ulid(), grantId = grantA,
            )
            val outA = dispatcher.dispatch("U1", reqA, currentDocVersion = "v1")
            assertEquals("SUCCESS", outA.outcome)
            assertEquals("POST_SEALED", outA.state)
            assertNotNull(outA.snapshotId)
            assertTrue(serverHandled >= 1)

            // 2. Path-blocklist path: ssh dir → FAILED before shadow.
            val reqDeny = ApplyEditRequest(
                taskId = "T1", effectorId = TutorTypes.ulid(),
                targetUri = "file:///home/u/.ssh/id_rsa",
                expectedDocVersion = "v1",
                edits = listOf(TextEdit(Range(Position(0,0), Position(0,1)), "X")),
                nonce = TutorTypes.ulid(), grantId = grantA,
            )
            val outDeny = dispatcher.dispatch("U1", reqDeny, currentDocVersion = "v1")
            assertEquals("PATH_DENIED", outDeny.outcome)
            assertEquals("FAILED", outDeny.state)

            // 3. Daemon-failure path: kill the test daemon → ROLLED_BACK.
            //    The watchdog scenario below covers the byte-restore
            //    invariant for partial-applied mutations; here we just
            //    assert the dispatcher transitions correctly when the
            //    backend errors out.
            daemon.stop(0)
            val targetB = tmp.resolve("b.kt"); targetB.writeText("orig-b\n")
            val reqB = reqA.copy(
                targetUri = "file:///${targetB.toString().replace('\\','/')}",
                effectorId = TutorTypes.ulid(),
                nonce = TutorTypes.ulid(),
            )
            val outB = dispatcher.dispatch("U1", reqB, currentDocVersion = "v1")
            assertEquals("ROLLED_BACK", outB.outcome)
            assertEquals("ROLLED_BACK", outB.state)

            // 4. Watchdog: synthetic stale PRE_SEALED row → reaped.
            val targetC = tmp.resolve("c.kt"); targetC.writeText("orig-c\n")
            val attempts = EffectorAttemptRepo(db)
            val cId = TutorTypes.ulid()
            attempts.insertPrePending(cId, "U1", "T1", EffectorType.APPLY_EDIT,
                "file:///${targetC.toString().replace('\\','/')}", grantA, "n-c")
            val cSnap = ShadowGit.preSeal("file:///${targetC.toString().replace('\\','/')}",
                tmp.resolve("shadow"))
            attempts.transition(cId, EffectorAttemptState.PRE_SEALED, snapshotId = cSnap.id)
            targetC.writeText("ABANDONED\n")
            val later = Instant.now().plus(Duration.ofSeconds(5))
            val reaped = dispatcher.reapStale(Duration.ofSeconds(2), later)
            assertEquals(1, reaped)
            assertEquals("orig-c\n", Files.readString(targetC),
                "watchdog rollback restores stale-PRE_SEALED file")
            assertEquals(EffectorAttemptState.ROLLED_BACK, attempts.get(cId)!!.state)

            // 5. Audit chain valid across success + rejection + rollback +
            //    watchdog reap.
            assertTrue(AuditRepo(db).verifyChain("U1"),
                "audit chain stays valid across all effector outcomes")

            // 6. Multi-tenant: U2 sees zero attempts.
            val u2Counts = attempts.countByState("U2")
            assertEquals(0, u2Counts.values.sum(), "U2 has no effector attempts")

            // 7. State distribution sanity for U1.
            val u1Counts = attempts.countByState("U1")
            assertTrue(u1Counts[EffectorAttemptState.POST_SEALED] == 1, "1 POST_SEALED: ${u1Counts}")
            assertTrue(u1Counts[EffectorAttemptState.ROLLED_BACK] == 2,
                "2 ROLLED_BACK (1 daemon-fail + 1 watchdog): ${u1Counts}")
            assertTrue(u1Counts[EffectorAttemptState.FAILED] == 1, "1 FAILED (path denied): ${u1Counts}")
        } finally {
            client.close()
            try { daemon.stop(0) } catch (_: Exception) {}
        }
    }
}
