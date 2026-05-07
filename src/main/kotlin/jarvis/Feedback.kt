package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * R3 (deep-research recommendation #3 / Mem0 feedback API parity) — append-only
 * record of user actions on [ProactiveSignal] rows. Fed into Phase 5 retraining
 * to re-tune scorer weights.
 *
 * Schema kept minimal: {signalId, ts, action, v=1}. Actions whitelisted to:
 *   - "dismissed": user swiped away
 *   - "pinned": user marked as important / save for later
 *   - "useful": user acknowledged value
 *   - "noise": user marked as a false positive
 */
@Serializable
data class FeedbackEntry(
    val signalId: String,
    val ts: String,
    val action: String,
    val v: Int = 1,
)

object Feedback {
    val ALLOWED_ACTIONS = setOf("dismissed", "pinned", "useful", "noise")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun append(entry: FeedbackEntry) {
        appendTo(Config.feedbackFile, entry)
    }

    fun appendTo(file: Path, entry: FeedbackEntry) {
        val line = json.encodeToString(FeedbackEntry.serializer(), entry) + "\n"
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

    fun readAll(): List<FeedbackEntry> = readAllFrom(Config.feedbackFile)

    fun readAllFrom(file: Path): List<FeedbackEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<FeedbackEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(FeedbackEntry.serializer(), line)
                    if (entry.v != 1) {
                        System.err.println(
                            "[Feedback] WARN skipping unknown schema version v=${entry.v} (id=${entry.signalId})",
                        )
                        return@forEach
                    }
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }
}
