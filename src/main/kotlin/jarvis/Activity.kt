package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

@Serializable
data class ActivityEntry(
    val ts: String,
    val title: String? = null,
    val process: String? = null,
    val pid: Long? = null,
)

object Activity {
    private val json = Json { ignoreUnknownKeys = true }

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
            .joinToString("\n") { e -> "  [${e.ts}] ${e.process ?: "?"}: ${e.title ?: ""}" }
    }
}
