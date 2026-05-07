package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.io.path.exists

/**
 * S1 — manual `schedule.json` reader. User edits the file directly; system
 * never writes. Each block: date + start + end + kind + subject + topic.
 *
 * Supported kinds: lecture, lab, exam, study, review, break.
 *
 * Schema (file at $JARVIS_DATA_DIR/schedule.json):
 * {
 *   "blocks": [
 *     {"date": "2026-05-09", "start": "10:00", "end": "12:00",
 *      "kind": "lecture", "subject": "Probability", "topic": "Markov chains"}
 *   ]
 * }
 */
@Serializable
data class ScheduleBlock(
    val date: String,
    val start: String,
    val end: String,
    val kind: String,
    val subject: String,
    val topic: String? = null,
)

@Serializable
data class ScheduleFile(val blocks: List<ScheduleBlock> = emptyList())

object Schedule {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): ScheduleFile = loadFrom(Config.scheduleFile)

    fun loadFrom(file: Path): ScheduleFile {
        if (!file.exists()) return ScheduleFile()
        return try {
            json.decodeFromString(ScheduleFile.serializer(), Files.readString(file, Charsets.UTF_8))
        } catch (e: Exception) {
            System.err.println("[Schedule] WARN failed to parse $file: ${e.message?.take(200)}")
            ScheduleFile()
        }
    }

    fun currentBlock(
        schedule: ScheduleFile,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScheduleBlock? {
        val ldt = LocalDateTime.ofInstant(now, zone)
        val date = ldt.toLocalDate().toString()
        val time = ldt.toLocalTime()
        return schedule.blocks.firstOrNull { b ->
            b.date == date && parseTime(b.start)?.let { time >= it } == true &&
                parseTime(b.end)?.let { time < it } == true
        }
    }

    fun todaysBlocks(
        schedule: ScheduleFile,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduleBlock> {
        val date = LocalDateTime.ofInstant(now, zone).toLocalDate().toString()
        return schedule.blocks.filter { it.date == date }
            .sortedBy { it.start }
    }

    fun nextExam(
        schedule: ScheduleFile,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScheduleBlock? {
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        return schedule.blocks
            .filter { it.kind == "exam" }
            .filter {
                runCatching { LocalDate.parse(it.date) }.getOrNull()
                    ?.isAfter(today.minusDays(1)) == true
            }
            .sortedBy { it.date }
            .firstOrNull()
    }

    fun daysUntilNextExam(
        schedule: ScheduleFile,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val exam = nextExam(schedule, now, zone) ?: return null
        val examDate = runCatching { LocalDate.parse(exam.date) }.getOrNull() ?: return null
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        return Duration.between(today.atStartOfDay(), examDate.atStartOfDay()).toDays()
    }

    private fun parseTime(s: String): LocalTime? =
        runCatching { LocalTime.parse(s) }.getOrNull()
}
