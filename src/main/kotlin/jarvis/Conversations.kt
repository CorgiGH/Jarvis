package jarvis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

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
}
