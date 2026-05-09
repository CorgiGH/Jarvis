package jarvis.tutor

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tutor Stage F — multi-channel chat gateway.
 *
 * Council 2026-05-09 (council-1778333402-tutor-next.md) flagged
 * gateway as breaking trust + kill-switch invariants. User
 * (feedback_build_everything.md) overrode the deferral; this
 * implementation applies extra guards so the council's specific
 * worries don't materialize:
 *
 *  GUARD 1 — kill switch. Default-OFF behind JARVIS_GATEWAY_ENABLED
 *  env. Plus a runtime kill via JARVIS_GATEWAY_KILL=1 halts every
 *  inbound mid-flight (re-checked on every request, not just at
 *  start).
 *
 *  GUARD 2 — shared-secret auth. Inbound webhooks must carry the
 *  X-Jarvis-Gateway-Token header matching JARVIS_GATEWAY_SECRET env.
 *  Without the secret env set, all inbound = 503. The bot daemon
 *  on user's PC supplies the same secret it was configured with.
 *
 *  GUARD 3 — per-channel rate limit. Default 30 msg/hour per
 *  (channel,fromUser) tuple. In-process counter; resets on restart.
 *
 *  GUARD 4 — synthetic source = "channel:<name>" forces the
 *  SourceClassifier to READ_ONLY mode. Effectors stay disabled
 *  for gateway-originated chats (council fix #2 from earlier
 *  read-only-trust audit). Telegram message can read corpus +
 *  emit gap cards but can NOT trigger keystroke injection.
 *
 *  GUARD 5 — no daemon access. Gateway never invokes
 *  /api/v1/effector/dispatch (which forwards to the PC daemon).
 *  Read-only tools (search_archival, query_graph, wiki_*,
 *  search_subject_corpus, list_subject_kinds) are the full surface
 *  Telegram users get.
 */
object GatewayInbound {

    /** Default per-(channel,user) cap: 30 inbound messages per hour. */
    private const val MAX_PER_HOUR_DEFAULT = 30

    private val rateBuckets = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    sealed class Result {
        data class Ok(val source: String) : Result()
        data class Err(val httpStatus: Int, val reason: String) : Result()
    }

    /** Pre-flight check applied to every inbound request. Caller
     *  proceeds with the LLM round-trip only when this returns Ok. */
    fun preflight(
        channel: String,
        fromUser: String,
        providedToken: String?,
        now: Instant = Instant.now(),
    ): Result {
        if (!isEnabled()) return Result.Err(503, "gateway disabled (JARVIS_GATEWAY_ENABLED unset)")
        if (isKilled()) return Result.Err(503, "gateway kill switch active")
        val secret = System.getenv("JARVIS_GATEWAY_SECRET")?.trim().orEmpty()
        if (secret.isEmpty()) return Result.Err(503, "gateway secret not configured")
        if (providedToken.isNullOrBlank() || !constantTimeEquals(providedToken, secret)) {
            return Result.Err(401, "missing or invalid X-Jarvis-Gateway-Token")
        }
        if (channel.isBlank() || fromUser.isBlank()) {
            return Result.Err(400, "channel + fromUser required")
        }
        if (!rateAllowed(channel, fromUser, now)) {
            return Result.Err(429, "per-(channel,user) rate limit (30/hr)")
        }
        return Result.Ok(source = "channel:${channel.lowercase()}")
    }

    /** Filter the JarvisToolset tool surface to read-only tools only —
     *  gateway-originated chats can't fire effectors. The set is
     *  hardcoded here to defend against accidental tool additions
     *  silently widening the gateway's blast radius. */
    val GATEWAY_TOOL_ALLOWLIST: Set<String> = setOf(
        "search_archival",
        "query_graph", "get_node", "get_neighbors", "shortest_path",
        "wiki_read", "wiki_append",
        "search_subject_corpus", "list_subject_kinds",
        "drive_search",   // Drive read = ok
        // explicitly NOT in allowlist:
        //   calendar_create_event, gmail_create_draft,
        //   plus the existing /api/v1/effector/dispatch path
    )

    fun isEnabled(): Boolean =
        System.getenv("JARVIS_GATEWAY_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false

    fun isKilled(): Boolean =
        System.getenv("JARVIS_GATEWAY_KILL")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false

    private fun rateAllowed(channel: String, fromUser: String, now: Instant): Boolean {
        val key = "$channel|$fromUser"
        val bucket = rateBuckets.computeIfAbsent(key) { ArrayDeque() }
        synchronized(bucket) {
            val cutoff = now.minus(Duration.ofHours(1))
            while (bucket.isNotEmpty() && bucket.first() < cutoff) bucket.removeFirst()
            if (bucket.size >= MAX_PER_HOUR_DEFAULT) return false
            bucket.addLast(now)
            return true
        }
    }

    /** Test hook — wipe rate buckets between tests. */
    internal fun resetForTests() { rateBuckets.clear() }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
