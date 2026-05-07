package jarvis

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

@Serializable
data class ActivityEntry(
    val ts: String,
    val title: String? = null,
    val process: String? = null,
    val pid: Long? = null,
    /** 0..1 score from [ActivityScorer]. Nullable for pre-Phase-1.1 rows
     *  and entries arriving from older logger builds. Readers tolerate null. */
    val importance: Float? = null,
)

object Activity {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Process-wide lock guarding every activity.jsonl append. Council 1778078829 HIGH +
     *  1778102666 elevation: /api/activity (server) and the PC logger fallback both
     *  hit Files.writeString(APPEND) and could interleave bytes mid-line, breaking
     *  the line-delimited JSON contract. */
    private val LOCK = Any()

    fun append(entry: ActivityEntry) {
        // Phase 1.1: score on the canonical write path so both /api/activity
        // (server) and the local-fallback path get importance populated.
        // Synthetic test data passing through appendTo(path, entry) skips this.
        //
        // DM2 fix (post-impl council 1778164815): read prior context + write
        // happen inside ONE synchronized block. Previously the loadEntries
        // call sat outside the LOCK so two concurrent writers could each
        // score against stale state and miss each other's churn signal.
        synchronized(LOCK) {
            val scored = if (entry.importance == null) {
                val recent = if (Config.activityFile.exists()) loadEntries(hours = 1) else emptyList()
                entry.copy(importance = ActivityScorer.score(entry, recent))
            } else entry
            val line = json.encodeToString(ActivityEntry.serializer(), scored) + "\n"
            JsonlRotate.maybeRotate(Config.activityFile)
            Config.activityFile.parent?.createDirectories()
            Files.writeString(
                Config.activityFile,
                line,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    fun appendTo(file: Path, entry: ActivityEntry) {
        val line = json.encodeToString(ActivityEntry.serializer(), entry) + "\n"
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

    fun loadEntries(hours: Long = Config.ACTIVITY_LOOKBACK_HOURS): List<ActivityEntry> {
        if (!Config.activityFile.exists()) return emptyList()
        val cutoff = Instant.now().minus(Duration.ofHours(hours))
        val out = mutableListOf<ActivityEntry>()
        Config.activityFile.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString<ActivityEntry>(line)
                    if (Instant.parse(entry.ts) >= cutoff) {
                        out.add(entry)
                    }
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    fun loadRecent(hours: Long = Config.ACTIVITY_LOOKBACK_HOURS): String {
        if (!Config.activityFile.exists()) return "(no activity log yet)"
        val entries = loadEntries(hours)
        if (entries.isEmpty()) return "(no activity in last ${hours}h)"
        return entries
            .takeLast(Config.ACTIVITY_LINE_CAP)
            .joinToString("\n") { e ->
                val imp = e.importance?.let { " [imp=${"%.2f".format(it)}]" } ?: ""
                "  [${e.ts}]$imp ${e.process ?: "?"}: ${e.title ?: ""}"
            }
    }
}
