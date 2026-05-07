package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * F6 — append-only goal log. Two row kinds:
 *   "set"      — user (or model on user's behalf) declares a goal
 *   "progress" — note on movement against an existing goal id
 *
 * Goal id is sha256(text + ts-bucket-day) so duplicate `[[goal_set]]` calls
 * within the same day collapse to one. Read API returns goals with their
 * latest progress note + age.
 */
@Serializable
data class GoalEntry(
    val id: String,
    val ts: String,
    val kind: String,         // "set" | "progress"
    val text: String,
    val parentId: String? = null,  // for "progress" rows; null for "set"
    val v: Int = 1,
)

@Serializable
data class GoalView(
    val id: String,
    val text: String,
    val setTs: String,
    val ageDays: Long,
    val latestProgressTs: String? = null,
    val latestProgressText: String? = null,
    val staleDays: Long = 0,
)

object Goals {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun append(entry: GoalEntry) {
        appendTo(Config.goalsFile, entry)
    }

    fun appendTo(file: Path, entry: GoalEntry) {
        val line = json.encodeToString(GoalEntry.serializer(), entry) + "\n"
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

    fun computeId(text: String, ts: Instant): String {
        val day = ts.toString().take(10)  // YYYY-MM-DD bucket
        val raw = "$text|$day"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    fun readAll(): List<GoalEntry> = readAllFrom(Config.goalsFile)

    fun readAllFrom(file: Path): List<GoalEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<GoalEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(GoalEntry.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Aggregated view of currently-set goals with their latest progress. */
    fun current(now: Instant = Instant.now()): List<GoalView> = currentFrom(Config.goalsFile, now)

    fun currentFrom(file: Path, now: Instant): List<GoalView> {
        val all = readAllFrom(file)
        val sets = all.filter { it.kind == "set" }
            .associateBy { it.id }
        val progressByParent = all.filter { it.kind == "progress" && it.parentId != null }
            .groupBy { it.parentId!! }
        return sets.values.map { setRow ->
            val setTs = runCatching { Instant.parse(setRow.ts) }.getOrNull()
            val latest = progressByParent[setRow.id]?.maxByOrNull { it.ts }
            val latestTs = latest?.let { runCatching { Instant.parse(it.ts) }.getOrNull() }
            val ageDays = setTs?.let { Duration.between(it, now).toDays().coerceAtLeast(0L) } ?: 0L
            val staleDays = (latestTs ?: setTs)?.let {
                Duration.between(it, now).toDays().coerceAtLeast(0L)
            } ?: 0L
            GoalView(
                id = setRow.id,
                text = setRow.text,
                setTs = setRow.ts,
                ageDays = ageDays,
                latestProgressTs = latest?.ts,
                latestProgressText = latest?.text,
                staleDays = staleDays,
            )
        }.sortedByDescending { it.staleDays }
    }
}
