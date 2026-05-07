package jarvis

import jarvis.subsystem.ContextModelSubsystem
import jarvis.subsystem.SubsystemInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * F4 — rolling cached user-state model. Background-refreshed every 30 min
 * via ctx-model. Read-side served instantly from the atomic cell — chat
 * callers + reflective consumers don't pay an LLM call.
 *
 * Default-OFF behind `STATE_CACHE_ENABLED` env. Refresh function is also
 * directly callable via [refresh] for on-demand invalidation.
 */
@Serializable
data class UserState(
    val ts: String,
    val text: String,
    val model: String,
    val ageSeconds: Long = 0,
)

object StateCache {

    private val REFRESH_INTERVAL = Duration.ofMinutes(30)
    private val LLM_TIMEOUT = Duration.ofSeconds(60)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cacheDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + cacheDispatcher)
    private val refreshLock = Mutex()
    private val cell = AtomicReference<UserState?>(null)

    fun isEnabled(): Boolean {
        if (LoopsKillSwitch.loopsDisabled()) return false
        return System.getenv("STATE_CACHE_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false
    }

    fun current(now: Instant = Instant.now()): UserState? {
        val s = cell.get() ?: return null
        val cached = runCatching { Instant.parse(s.ts) }.getOrNull() ?: return s
        val age = Duration.between(cached, now).seconds.coerceAtLeast(0L)
        return s.copy(ageSeconds = age)
    }

    /** Trigger a refresh in background. Returns immediately. Idempotent —
     *  refreshLock prevents two concurrent computations. */
    fun maybeRefresh(client: Llm) {
        if (!isEnabled()) return
        scope.launch {
            refreshLock.withLock {
                refreshSync(client, Instant.now())
            }
        }
    }

    /** Direct synchronous refresh — used by tests + on-demand chat-time
     *  invalidation. Holds for the full LLM call. */
    suspend fun refreshSync(client: Llm, now: Instant): UserState? {
        return try {
            val out = withTimeout(LLM_TIMEOUT.toMillis()) {
                ContextModelSubsystem().run(
                    client,
                    SubsystemInput(
                        activity = Activity.loadEntries(),
                        wiki = MemoryWiki.recent(),
                        recentChat = Conversations.recentAsChatMessages(),
                        userQuery = null,
                    ),
                )
            }
            // Domain spec on ContextModelSubsystem returns wikiEntry like
            // "ctx-model (model-name)". Extract model name.
            val modelName = Regex("""\(([^)]+)\)""").find(out.wikiEntry ?: "")
                ?.groupValues?.getOrNull(1) ?: "unknown"
            val state = UserState(
                ts = now.toString(),
                text = out.text.take(2000),
                model = modelName,
            )
            cell.set(state)
            state
        } catch (e: TimeoutCancellationException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Returns true if cache is missing or older than refresh-interval. */
    fun isStale(now: Instant = Instant.now()): Boolean {
        val s = cell.get() ?: return true
        val cached = runCatching { Instant.parse(s.ts) }.getOrNull() ?: return true
        return Duration.between(cached, now) >= REFRESH_INTERVAL
    }
}
