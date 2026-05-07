package jarvis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.exp
import kotlin.math.ln

@Serializable
data class ConversationEntry(
    val role: String,
    val content: String,
    val ts: String,
    val model: String? = null,
    @SerialName("msg_id") val msgId: String,
    val kind: String = "chat",
    val v: Int = 1,
    /** Council 1778105576 (D): per-turn ordering when ts ties. user=0, assistant=1. */
    val seq: Int? = null,
    /** Phase 1.2 — heuristic 0..1 score from [ConversationScorer]. Nullable for
     *  pre-Phase-1.2 rows; readers tolerate null. NOT a v=2 schema bump:
     *  unknown-field tolerance + nullable-default keeps v=1 readers happy. */
    val importance: Float? = null,
)

/**
 * Append-only log of conversation turns at $JARVIS_DATA_DIR/conversations.jsonl.
 *
 * Council 1778104395 v1 ruling:
 * - No FIFO truncation in v1 (text is cheap; rolling segments deferred)
 * - Schema {role, content, ts, model, msg_id, kind="chat", v=1} day 1
 * - Mutex via synchronized(LOCK), same pattern as MemoryWiki + Activity
 * - Replaces MemoryWiki.recent() as chat recency window in buildChatContext
 */
object Conversations {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val LOCK = Any()

    fun append(entry: ConversationEntry) {
        appendTo(Config.conversationsFile, entry)
    }

    fun appendTo(file: Path, entry: ConversationEntry) {
        val line = json.encodeToString(ConversationEntry.serializer(), entry) + "\n"
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file)
            file.parent?.createDirectories()
            Files.writeString(
                file,
                line,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    /** Council 1778105576 (A): write two related rows under ONE lock acquire so
     *  a crash between user and assistant lines is impossible. */
    fun appendBothTo(file: Path, first: ConversationEntry, second: ConversationEntry) {
        val payload = json.encodeToString(ConversationEntry.serializer(), first) + "\n" +
            json.encodeToString(ConversationEntry.serializer(), second) + "\n"
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file)
            file.parent?.createDirectories()
            Files.writeString(
                file,
                payload,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    fun readAll(): List<ConversationEntry> = readAllFrom(Config.conversationsFile)

    fun readAllFrom(file: Path): List<ConversationEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<ConversationEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(ConversationEntry.serializer(), line)
                    // Council 1778105576 (4): unknown schema version skipped + warned,
                    // fails LOUD instead of silently coercing v=2 fields into v=1 defaults.
                    if (entry.v != 1) {
                        System.err.println(
                            "[Conversations] WARN skipping row with unknown schema version v=${entry.v} " +
                                "(msg_id=${entry.msgId}); upgrade reader before re-enabling.",
                        )
                        return@forEach
                    }
                    out.add(entry)
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    fun recent(n: Int = Config.CONVERSATION_RECENT_N): List<ConversationEntry> =
        recentFrom(Config.conversationsFile, n)

    fun recentFrom(file: Path, n: Int): List<ConversationEntry> {
        if (n <= 0) return emptyList()
        val all = readAllFrom(file)
        return if (all.size <= n) all else all.subList(all.size - n, all.size)
    }

    /** Letta-style: chat history derived from the log on every turn so server
     *  restart preserves context and the message array does not grow unbounded. */
    fun recentAsChatMessages(n: Int = Config.CONVERSATION_RECENT_N): List<ChatMessage> =
        recentAsChatMessagesFrom(Config.conversationsFile, n)

    fun recentAsChatMessagesFrom(file: Path, n: Int): List<ChatMessage> =
        recentFrom(file, n).map { ChatMessage(it.role, it.content) }

    /** F7 — token-context-pressure-aware variant. Walks newest→oldest,
     *  accumulates entries up to [charBudget] characters of content. Drops
     *  oldest first when budget exhausted. Heuristic char-budget (chars/4 ≈
     *  tokens). Cap n at [Config.CONVERSATION_RECENT_N] so chronological
     *  pairing semantics hold. Daily ReflectMain summarizes older context
     *  separately; this path doesn't do LLM summarization inline. */
    fun recentAsChatMessagesWithBudget(
        charBudget: Int = 60_000,
        maxN: Int = Config.CONVERSATION_RECENT_N,
    ): List<ChatMessage> = recentAsChatMessagesWithBudgetFrom(
        Config.conversationsFile, charBudget, maxN,
    )

    fun recentAsChatMessagesWithBudgetFrom(
        file: Path,
        charBudget: Int,
        maxN: Int,
    ): List<ChatMessage> {
        val pool = recentFrom(file, maxN)
        if (pool.isEmpty()) return emptyList()
        var used = 0
        val out = ArrayDeque<ChatMessage>()
        for (entry in pool.asReversed()) {
            val len = entry.content.length
            if (out.isNotEmpty() && used + len > charBudget) break
            out.addFirst(ChatMessage(entry.role, entry.content))
            used += len
        }
        return out.toList()
    }

    private const val SALIENT_POOL_SIZE = 500
    private const val SALIENT_HALF_LIFE_HOURS = 24.0
    private const val SALIENT_NULL_FALLBACK = 0.4f

    /**
     * Phase 1.3 (council 1778164081) — importance-weighted variant of [recent].
     * Returns up to [n] entries scored by Generative-Agents-style recency × importance:
     *
     *   score(e) = exp(-λ * hoursSince(e.ts)) + (e.importance ?: nullFallback)
     *   λ        = ln(2) / halfLifeHours
     *
     * Pool capped to last [poolSize] rows so cost stays bounded as the log grows.
     * Null importance defaults to 0.4 (below-median) so pre-1.2 rows participate
     * without being skipped or over-weighted.
     *
     * Returns ConversationEntry, NOT ChatMessage. The salient view feeds the
     * SYSTEM PROMPT context block — reordering chat-replay messages by
     * importance corrupts user/assistant pairing.
     */
    fun recentByImportance(
        n: Int = Config.SALIENT_PRIOR_N,
        poolSize: Int = SALIENT_POOL_SIZE,
        halfLifeHours: Double = SALIENT_HALF_LIFE_HOURS,
        nullFallback: Float = SALIENT_NULL_FALLBACK,
        now: Instant = Instant.now(),
    ): List<ConversationEntry> = recentByImportanceFrom(
        Config.conversationsFile, n, poolSize, halfLifeHours, nullFallback, now,
    )

    fun recentByImportanceFrom(
        file: Path,
        n: Int,
        poolSize: Int = SALIENT_POOL_SIZE,
        halfLifeHours: Double = SALIENT_HALF_LIFE_HOURS,
        nullFallback: Float = SALIENT_NULL_FALLBACK,
        now: Instant = Instant.now(),
        accessFile: Path = Config.lastAccessFile,
        touchOnPick: Boolean = true,
    ): List<ConversationEntry> {
        if (n <= 0) return emptyList()
        val all = readAllFrom(file)
        if (all.isEmpty()) return emptyList()
        val candidates = if (all.size > poolSize) all.takeLast(poolSize) else all
        val lambda = ln(2.0) / halfLifeHours
        // R2: max(creation_ts, lastAccessedAt). Frequently-recalled rows
        // stay surfaced; never-touched rows decay from creation as before.
        val accessMap = ConversationAccess.lastAccessByMsgIdFrom(accessFile)
        val ranked = candidates.map { entry ->
            val createdAt = parseInstantOrNull(entry.ts)
            val lastAccessed = accessMap[entry.msgId]
            val anchor = when {
                createdAt == null && lastAccessed == null -> null
                createdAt == null -> lastAccessed
                lastAccessed == null -> createdAt
                else -> if (lastAccessed > createdAt) lastAccessed else createdAt
            }
            val recency = anchor?.let { ts ->
                val hours = Duration.between(ts, now).toMinutes()
                    .coerceAtLeast(0).toDouble() / 60.0
                exp(-lambda * hours)
            } ?: 0.0
            val imp = (entry.importance ?: nullFallback).toDouble()
            entry to (recency + imp)
        }
        val topN = ranked.sortedByDescending { it.second }.take(n).map { it.first }
        // R2: touch picked rows so the next call sees them as freshly accessed.
        // Chronological recent() does NOT touch — only the importance-weighted
        // view counts as "access" per Park et al. semantics.
        if (touchOnPick && topN.isNotEmpty()) {
            ConversationAccess.touchTo(accessFile, topN.map { it.msgId }, now)
        }
        // Chronological re-sort: LLM reads top-down, so emit in turn order even
        // though we picked by importance.
        return topN.sortedWith(compareBy({ it.ts }, { it.seq ?: 0 }))
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
