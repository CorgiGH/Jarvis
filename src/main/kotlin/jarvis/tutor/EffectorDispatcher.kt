package jarvis.tutor

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * Tutor Layer B1 — effector dispatcher with state-machine guarantees.
 *
 * Council R3 First-Principles + fix #1 (council-1778325410-layer-b.md):
 * the dispatcher is the SINGLE caller of the daemon. Server-side
 * v0 clipboard write goes through the same dispatcher with a
 * `Backend.CLIPBOARD` slot — one audit chain, one rate window, one
 * read-only check. v1 keystroke injection goes through `Backend.DAEMON`.
 *
 * Sequence per dispatch:
 *  1. Insert EffectorAttempt row in PRE_PENDING
 *  2. Run EffectorValidator (path blocklist + grant + scope + nonce)
 *  3. ShadowGit.preSeal(targetUri); transition row to PRE_SEALED with
 *     snapshot id
 *  4. Call backend (daemon HTTP w/ HMAC, OR clipboard write)
 *  5a. SUCCESS: ShadowGit.postSeal; transition POST_SEALED
 *  5b. FAILURE: ShadowGit.rollback; transition ROLLED_BACK
 *  6. Append AuditLine with outcome
 *
 * Watchdog runs separately every 1s, scans EffectorAttempt rows in
 * PRE_SEALED state older than 2s, calls ShadowGit.rollback +
 * transitions ROLLED_BACK + appends audit. Defends against the
 * "daemon hung mid-effector" path.
 */
class EffectorDispatcher(
    private val db: Database,
    private val nonces: NonceCache,
    private val shadowRoot: Path,
    private val ledgerDir: Path,
    private val daemonClient: DaemonClient,
    private val backend: Backend = Backend.DAEMON,
) {
    enum class Backend { CLIPBOARD, DAEMON }

    @Serializable
    data class DispatchOutcome(
        val attemptId: String,
        val outcome: String,
        val state: String,
        val detail: String? = null,
        val snapshotId: String? = null,
    )

    private val attempts = EffectorAttemptRepo(db)
    private val validator = EffectorValidator(db, nonces)
    private val audit = AuditRepo(db)

    suspend fun dispatch(
        userId: String,
        req: ApplyEditRequest,
        currentDocVersion: String,
    ): DispatchOutcome {
        // 1. PRE_PENDING row.
        val attemptId = TutorTypes.ulid()
        attempts.insertPrePending(
            id = attemptId,
            userId = userId,
            taskId = req.taskId,
            effectorType = EffectorType.APPLY_EDIT,
            targetUri = req.targetUri,
            grantId = req.grantId,
            nonce = req.nonce,
        )

        // 2. Validate (blocklist / grant / scope / nonce).
        val validation = validator.validate(userId, req, currentDocVersion)
        if (validation is ValidationResult.Reject) {
            attempts.transition(attemptId, EffectorAttemptState.FAILED, failureReason = validation.reason)
            audit.append(
                userId = userId,
                canonicalLine = "EFFECTOR_REJECTED|$attemptId|outcome=${validation.outcome.name}|reason=${validation.reason}",
                outcome = validation.outcome,
            )
            return DispatchOutcome(attemptId, validation.outcome.name, EffectorAttemptState.FAILED.name,
                detail = validation.reason)
        }

        // 3. Pre-seal shadow snapshot.
        val snap = try {
            ShadowGit.preSeal(req.targetUri, shadowRoot)
        } catch (e: Exception) {
            attempts.transition(attemptId, EffectorAttemptState.FAILED,
                failureReason = "preSeal: ${e.message?.take(160)}")
            return DispatchOutcome(attemptId, Outcome.REJECTED.name, EffectorAttemptState.FAILED.name,
                detail = "preSeal failed: ${e.message?.take(160)}")
        }
        attempts.transition(attemptId, EffectorAttemptState.PRE_SEALED, snapshotId = snap.id)

        // 4. Backend dispatch.
        val newText = req.edits.joinToString("") { it.newText }
        val backendResult = when (backend) {
            Backend.CLIPBOARD -> Result.success("clipboard write enqueued (server side, no daemon)")
            Backend.DAEMON -> daemonClient.dispatch(req.effectorId, req.targetUri, newText)
        }

        // 5. Post-seal or rollback.
        return if (backendResult.isSuccess) {
            attempts.transition(attemptId, EffectorAttemptState.FIRED)
            ShadowGit.postSeal(snap.id, shadowRoot)
            attempts.transition(attemptId, EffectorAttemptState.POST_SEALED)
            audit.append(
                userId = userId,
                canonicalLine = "EFFECTOR_SUCCESS|$attemptId|snapshot=${snap.id}|backend=${backend.name}",
                outcome = Outcome.SUCCESS,
            )
            DispatchOutcome(attemptId, Outcome.SUCCESS.name, EffectorAttemptState.POST_SEALED.name,
                detail = backendResult.getOrNull(), snapshotId = snap.id)
        } else {
            val err = backendResult.exceptionOrNull()?.message?.take(200) ?: "unknown daemon error"
            ShadowGit.rollback(snap.id, shadowRoot)
            attempts.transition(attemptId, EffectorAttemptState.ROLLED_BACK,
                failureReason = err)
            audit.append(
                userId = userId,
                canonicalLine = "EFFECTOR_ROLLED_BACK|$attemptId|snapshot=${snap.id}|reason=$err",
                outcome = Outcome.ROLLED_BACK,
            )
            DispatchOutcome(attemptId, Outcome.ROLLED_BACK.name, EffectorAttemptState.ROLLED_BACK.name,
                detail = err, snapshotId = snap.id)
        }
    }

    /** Watchdog tick — rolls back any PRE_SEALED attempts older than
     *  [staleAfter]. Called by a coroutine on a 1s tick from runWeb. */
    fun reapStale(staleAfter: Duration = Duration.ofSeconds(2), now: Instant = Instant.now()): Int {
        val stale = attempts.listStalePreSealed(staleAfter, now)
        var reaped = 0
        for (att in stale) {
            try {
                att.shadowSnapshotId?.let { ShadowGit.rollback(it, shadowRoot) }
                attempts.transition(att.id, EffectorAttemptState.ROLLED_BACK,
                    failureReason = "watchdog: pre_sealed > ${staleAfter.seconds}s without post_seal")
                audit.append(
                    userId = att.userId,
                    canonicalLine = "EFFECTOR_WATCHDOG_ROLLBACK|${att.id}|snapshot=${att.shadowSnapshotId}|age=${Duration.between(att.updatedAt, now).seconds}s",
                    outcome = Outcome.ROLLED_BACK,
                )
                reaped++
            } catch (e: Exception) {
                attempts.transition(att.id, EffectorAttemptState.FAILED,
                    failureReason = "watchdog rollback failed: ${e.message?.take(160)}")
            }
        }
        return reaped
    }
}

/**
 * Daemon HTTP client — signs requests with the same canonical scheme
 * the daemon's hmac_auth.rs verifies.
 */
class DaemonClient(
    private val baseUrl: String,
    private val secret: ByteArray,
) : AutoCloseable {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 2_000
        }
    }
    private val rng = SecureRandom()
    private val json = Json { encodeDefaults = true }

    suspend fun dispatch(effectorId: String, targetUri: String, newText: String): Result<String> {
        return try {
            val nonce = ByteArray(16).also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            val ts = Instant.now().toEpochMilli()
            val body = json.encodeToString(
                DaemonDispatchRequest.serializer(),
                DaemonDispatchRequest(effectorId = effectorId, targetUri = targetUri, newText = newText),
            ).toByteArray(Charsets.UTF_8)
            val canon = DaemonAuth.canonical("POST", "/effector/dispatch", ts, nonce, body)
            val sig = DaemonAuth.sign(secret, canon)

            val resp = client.post("${baseUrl.trimEnd('/')}/effector/dispatch") {
                header("X-Jarvis-Timestamp", ts.toString())
                header("X-Jarvis-Nonce", nonce)
                header("X-Jarvis-Hmac", sig)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!resp.status.isSuccess()) {
                Result.failure(Exception("daemon HTTP ${resp.status.value}: ${resp.bodyAsText().take(200)}"))
            } else {
                Result.success(resp.bodyAsText().take(400))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun close() { client.close() }

    @Serializable
    private data class DaemonDispatchRequest(
        val effectorId: String,
        val targetUri: String,
        val newText: String,
    )
}
