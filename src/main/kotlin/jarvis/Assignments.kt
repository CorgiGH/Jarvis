package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Append-only assignment / homework tracker. Three row kinds:
 *   "set"      — declare an assignment with a due date
 *   "progress" — note on movement against an existing assignment id
 *   "done"     — mark complete (separate row so history preserved)
 *
 * id = sha256(subject|title|dueDate)[:16] so repeated set-of-same-task
 * collapses cleanly. Aggregated view returns active (not-done) items
 * sorted by due-date ascending; flags overdue + due-within-3-days.
 */
@Serializable
data class AssignmentEntry(
    val id: String,
    val ts: String,
    val kind: String,           // "set" | "progress" | "done"
    val subject: String,
    val title: String,
    val dueDate: String? = null,  // YYYY-MM-DD
    val note: String? = null,
    val v: Int = 1,
)

@Serializable
data class AssignmentView(
    val id: String,
    val subject: String,
    val title: String,
    val dueDate: String?,
    val daysToDue: Long?,
    val status: String,          // "active" | "overdue" | "due-soon" | "done"
    val latestNoteTs: String?,
    val latestNote: String?,
)

object Assignments {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun computeId(subject: String, title: String, dueDate: String?): String {
        val raw = "$subject|$title|${dueDate ?: ""}"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    fun append(entry: AssignmentEntry) {
        appendTo(Config.assignmentsFile, entry)
    }

    fun appendTo(file: Path, entry: AssignmentEntry) {
        val line = json.encodeToString(AssignmentEntry.serializer(), entry) + "\n"
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

    fun readAll(): List<AssignmentEntry> = readAllFrom(Config.assignmentsFile)

    fun readAllFrom(file: Path): List<AssignmentEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<AssignmentEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(AssignmentEntry.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    fun current(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<AssignmentView> =
        currentFrom(Config.assignmentsFile, now, zone)

    fun currentFrom(file: Path, now: Instant, zone: ZoneId): List<AssignmentView> {
        val all = readAllFrom(file)
        val sets = all.filter { it.kind == "set" }.associateBy { it.id }
        val doneIds = all.filter { it.kind == "done" }.map { it.id }.toSet()
        val notesByParent = all.filter { it.kind == "progress" }.groupBy { it.id }
        val today = java.time.LocalDateTime.ofInstant(now, zone).toLocalDate()
        return sets.values.map { row ->
            val due = row.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val daysToDue = due?.let {
                Duration.between(today.atStartOfDay(), it.atStartOfDay()).toDays()
            }
            val status = when {
                row.id in doneIds -> "done"
                daysToDue == null -> "active"
                daysToDue < 0 -> "overdue"
                daysToDue <= 3 -> "due-soon"
                else -> "active"
            }
            val latest = notesByParent[row.id]?.maxByOrNull { it.ts }
            AssignmentView(
                id = row.id,
                subject = row.subject,
                title = row.title,
                dueDate = row.dueDate,
                daysToDue = daysToDue,
                status = status,
                latestNoteTs = latest?.ts,
                latestNote = latest?.note,
            )
        }.sortedWith(compareBy(
            { if (it.status == "done") 1 else 0 },
            { it.daysToDue ?: Long.MAX_VALUE },
        ))
    }
}
