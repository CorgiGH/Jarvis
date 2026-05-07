package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduleTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun missingFileReturnsEmpty() {
        val s = Schedule.loadFrom(java.nio.file.Paths.get("does-not-exist.json"))
        assertEquals(emptyList(), s.blocks)
    }

    @Test
    fun loadsValidJson(@TempDir tmp: Path) {
        val file = tmp.resolve("schedule.json")
        Files.writeString(file, """
            {"blocks":[
              {"date":"2026-05-09","start":"10:00","end":"12:00",
               "kind":"lecture","subject":"Probability","topic":"Markov"}
            ]}
        """.trimIndent())
        val s = Schedule.loadFrom(file)
        assertEquals(1, s.blocks.size)
        assertEquals("Probability", s.blocks[0].subject)
    }

    @Test
    fun corruptJsonReturnsEmpty(@TempDir tmp: Path) {
        val file = tmp.resolve("schedule.json")
        Files.writeString(file, "not json at all")
        val s = Schedule.loadFrom(file)
        assertEquals(emptyList(), s.blocks)
    }

    @Test
    fun currentBlockMatchesNow() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "Probability"),
            ScheduleBlock("2026-05-09", "13:00", "15:00", "lab", "OS"),
        ))
        // 11:00 UTC on 2026-05-09 → in the lecture block.
        val now = Instant.parse("2026-05-09T11:00:00Z")
        val block = Schedule.currentBlock(s, now, utc)
        assertNotNull(block)
        assertEquals("Probability", block!!.subject)
    }

    @Test
    fun currentBlockReturnsNullBetweenBlocks() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "Probability"),
            ScheduleBlock("2026-05-09", "13:00", "15:00", "lab", "OS"),
        ))
        val now = Instant.parse("2026-05-09T12:30:00Z")  // gap
        assertNull(Schedule.currentBlock(s, now, utc))
    }

    @Test
    fun todaysBlocksOnlyReturnsToday() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "A"),
            ScheduleBlock("2026-05-09", "13:00", "15:00", "lab", "B"),
            ScheduleBlock("2026-05-10", "10:00", "12:00", "lecture", "C"),
        ))
        val now = Instant.parse("2026-05-09T08:00:00Z")
        val today = Schedule.todaysBlocks(s, now, utc)
        assertEquals(2, today.size)
        // Sorted by start.
        assertEquals("A", today[0].subject)
    }

    @Test
    fun nextExamFindsSoonest() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "A"),
            ScheduleBlock("2026-05-21", "09:00", "12:00", "exam", "Probability"),
            ScheduleBlock("2026-06-01", "09:00", "12:00", "exam", "OS"),
        ))
        val now = Instant.parse("2026-05-09T08:00:00Z")
        val exam = Schedule.nextExam(s, now, utc)
        assertNotNull(exam)
        assertEquals("Probability", exam!!.subject)
        assertEquals(12L, Schedule.daysUntilNextExam(s, now, utc))
    }

    @Test
    fun nextExamReturnsNullWhenNoneScheduled() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "A"),
        ))
        val now = Instant.parse("2026-05-09T08:00:00Z")
        assertNull(Schedule.nextExam(s, now, utc))
        assertNull(Schedule.daysUntilNextExam(s, now, utc))
    }
}
