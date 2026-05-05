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

    fun loadRecent(hours: Long = Config.ACTIVITY_LOOKBACK_HOURS): String {
        if (!Config.activityFile.exists()) return "(no activity log yet)"
        val cutoff = Instant.now().minus(Duration.ofHours(hours))
        val lines = mutableListOf<String>()
        Config.activityFile.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString<ActivityEntry>(line)
                    val ts = Instant.parse(entry.ts)
                    if (ts >= cutoff) {
                        lines.add("  [${entry.ts}] ${entry.process ?: "?"}: ${entry.title ?: ""}")
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (lines.isEmpty()) return "(no activity in last ${hours}h)"
        return lines.takeLast(Config.ACTIVITY_LINE_CAP).joinToString("\n")
    }
}
