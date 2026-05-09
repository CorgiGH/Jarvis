package jarvis.tutor

import jarvis.LoopsKillSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Tutor Layer B1 — background watchdog that calls
 * [EffectorDispatcher.reapStale] on a 1-second tick.
 *
 * Council R3 fix #1, watchdog half: pre_sealed rows older than the
 * threshold (default 2s) get rolled back via shadow-git + transitioned
 * ROLLED_BACK + emit a WATCHDOG_ROLLBACK audit row. Without this loop,
 * a daemon hang or process crash mid-effector leaves the live file in
 * an indeterminate state and the EffectorAttempt row stuck at
 * pre_sealed forever.
 *
 * Default-OFF unless `EFFECTOR_WATCHDOG_ENABLED` is set, mirroring
 * the existing JARVIS_LOOPS gate pattern.
 */
object EffectorWatchdog {

    private const val TICK_MS = 1_000L
    private val STALE_AFTER = Duration.ofSeconds(2)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun isEnabled(): Boolean {
        if (LoopsKillSwitch.loopsDisabled()) return false
        return System.getenv("EFFECTOR_WATCHDOG_ENABLED")?.lowercase()
            ?.let { it == "1" || it == "true" || it == "yes" } ?: false
    }

    fun start(db: Database, ledgerDir: Path) {
        if (!isEnabled()) {
            System.err.println("[EffectorWatchdog] EFFECTOR_WATCHDOG_ENABLED not set — loop dormant.")
            return
        }
        System.err.println("[EffectorWatchdog] started (tick every ${TICK_MS}ms)")
        scope.launch {
            // Build dispatcher ONCE. The watchdog never invokes the
            // daemon path; it only consults shadow-git + transitions
            // attempt rows + writes audit lines, so passing a no-op
            // DaemonClient at a closed port is safe — reapStale never
            // touches it.
            val noopClient = DaemonClient("http://127.0.0.1:1", ByteArray(0))
            val effectorDispatcher = EffectorDispatcher(
                db = db, nonces = NonceCache(),
                shadowRoot = ledgerDir.resolve("shadow"),
                ledgerDir = ledgerDir,
                daemonClient = noopClient,
                backend = EffectorDispatcher.Backend.DAEMON,
            )
            try {
                while (true) {
                    try {
                        val n = effectorDispatcher.reapStale(STALE_AFTER, Instant.now())
                        if (n > 0) {
                            System.err.println("[EffectorWatchdog] reaped $n stale pre_sealed attempt(s)")
                        }
                    } catch (e: Exception) {
                        System.err.println("[EffectorWatchdog] tick failed: ${e.message?.take(160)}")
                    }
                    delay(TICK_MS)
                }
            } finally {
                noopClient.close()
            }
        }
    }
}
