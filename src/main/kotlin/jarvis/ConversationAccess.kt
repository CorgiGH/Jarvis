package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * R2 (deep-research recommendation #2 / Park et al. 2023 Algorithm 1 line 6 /
 * LangChain TimeWeightedVectorStoreRetriever) — sidecar `last_access.jsonl`
 * tracking when each conversation row was last *retrieved* (not just created).
 *
 * Append-only by design: an in-place mutation on conversations.jsonl would
 * cost a full-file rewrite every recall. Latest-ts-wins on read.
 *
 * Used by [Conversations.recentByImportanceFrom] to compute decay anchor =
 * max(creation_ts, lastAccessedAt). Frequently-recalled important rows stay
 * surfaced; never-touched rows decay normally.
 */
@Serializable
data class AccessEntry(val msgId: String, val ts: String)

object ConversationAccess {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun touch(msgIds: List<String>) {
        touchTo(Config.lastAccessFile, msgIds, Instant.now())
    }

    fun touchTo(file: Path, msgIds: List<String>, now: Instant) {
        if (msgIds.isEmpty()) return
        val nowStr = now.toString()
        val payload = buildString {
            for (id in msgIds) {
                append(json.encodeToString(AccessEntry.serializer(), AccessEntry(id, nowStr)))
                append('\n')
            }
        }
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

    fun lastAccessByMsgId(): Map<String, Instant> =
        lastAccessByMsgIdFrom(Config.lastAccessFile)

    fun lastAccessByMsgIdFrom(file: Path): Map<String, Instant> {
        if (!file.exists()) return emptyMap()
        val out = mutableMapOf<String, Instant>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(AccessEntry.serializer(), line)
                    val ts = Instant.parse(entry.ts)
                    val prev = out[entry.msgId]
                    if (prev == null || ts > prev) out[entry.msgId] = ts
                } catch (_: Exception) {
                }
            }
        }
        return out
    }
}
