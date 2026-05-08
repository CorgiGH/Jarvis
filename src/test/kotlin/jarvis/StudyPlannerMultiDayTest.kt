package jarvis

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyPlannerMultiDayTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-05-09T08:00:00Z")
    private val today: LocalDate = LocalDate.parse("2026-05-09")

    @Test
    fun multiDayEmptyInputsReturnsRequestedDayCount() {
        val plan = StudyPlanner.multiDay(7, ScheduleFile(), emptyList(), emptyList(), emptyList(), now, utc)
        assertEquals(7, plan.size)
        assertEquals(today.toString(), plan[0].date)
        assertEquals(today.plusDays(6).toString(), plan[6].date)
        assertTrue(plan.all { it.blocks.isEmpty() && it.reviews.isEmpty() && it.newCatchup.isEmpty() })
        assertTrue(plan.all { it.reviewDebt == 0 })
    }

    @Test
    fun multiDayBlocksDistributedToCorrectDate() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "13:00", "15:00", "lab", "OS", "scheduling"),
            ScheduleBlock("2026-05-10", "10:00", "12:00", "lecture", "PA", "DP"),
            ScheduleBlock("2026-05-11", "09:00", "10:30", "study", "ALO", "vectors"),
        ))
        val plan = StudyPlanner.multiDay(3, s, emptyList(), emptyList(), emptyList(), now, utc)
        assertEquals(3, plan.size)
        assertEquals(1, plan[0].blocks.size)
        assertEquals("OS", plan[0].blocks[0].subject)
        assertEquals("PA", plan[1].blocks[0].subject)
        assertEquals("ALO", plan[2].blocks[0].subject)
    }

    @Test
    fun reviewsCappedAtEightPerDayWithReviewDebtOverflow() {
        // 12 stale concepts → day 1 should render 8 + reviewDebt=4.
        val stats = (1..12).map {
            ConceptStat("Concept-$it", "PS", "2026-04-25T00:00:00Z", 14L, 0.10f - it * 0.001f)
        }
        val plan = StudyPlanner.multiDay(1, ScheduleFile(), stats, emptyList(), emptyList(), now, utc)
        assertEquals(8, plan[0].reviews.size, "renderer cap is 8 reviews/day")
        assertEquals(4, plan[0].reviewDebt, "surplus surfaces as reviewDebt; 12 stale - 8 rendered = 4 debt")
    }

    @Test
    fun newCatchupCappedAtThreePerDayWithCrossDayDedup() {
        val catalog = (1..10).map { ConceptCatalog.Concept("Topic-$it", "POO", "POO/c.md") }
        val plan = StudyPlanner.multiDay(2, ScheduleFile(), emptyList(), emptyList(), catalog, now, utc)
        assertEquals(3, plan[0].newCatchup.size, "day 1 capped at 3")
        assertEquals(3, plan[1].newCatchup.size, "day 2 capped at 3")
        // No concept appears on both days.
        val day1Names = plan[0].newCatchup.map { it.topic }.toSet()
        val day2Names = plan[1].newCatchup.map { it.topic }.toSet()
        assertTrue(day1Names.intersect(day2Names).isEmpty(), "no duplicates across days")
    }

    @Test
    fun examWindowBiasesNewCatchupTowardExamSubject() {
        val examIn5d = ScheduleFile(listOf(
            ScheduleBlock("2026-05-14", "09:00", "12:00", "exam", "PS"),
        ))
        val catalog = listOf(
            ConceptCatalog.Concept("PS-A", "PS", "PS/a.md"),
            ConceptCatalog.Concept("PS-B", "PS", "PS/b.md"),
            ConceptCatalog.Concept("ALO-A", "ALO", "ALO/a.md"),
            ConceptCatalog.Concept("ALO-B", "ALO", "ALO/b.md"),
            ConceptCatalog.Concept("POO-A", "POO", "POO/a.md"),
        )
        val plan = StudyPlanner.multiDay(1, examIn5d, emptyList(), emptyList(), catalog, now, utc)
        assertEquals(2, plan[0].newCatchup.size, "only PS concepts available, capped naturally")
        assertTrue(plan[0].newCatchup.all { it.subject == "PS" }, "exam-window bias picks PS only")
    }

    @Test
    fun examNoteAttachedWhenExamWithinFourteenDays() {
        val examIn5d = ScheduleFile(listOf(
            ScheduleBlock("2026-05-14", "09:00", "12:00", "exam", "PS", "placeholder"),
        ))
        val plan = StudyPlanner.multiDay(2, examIn5d, emptyList(), emptyList(), emptyList(), now, utc)
        val note = plan[0].examNote
        assertNotNull(note, "exam in 5d → note attached")
        assertTrue(note.contains("PS"), "note names subject")
        assertTrue(note.contains("5"), "note shows day count")
    }

    @Test
    fun examNoteAbsentWhenExamFurtherThanFourteenDays() {
        val examIn30d = ScheduleFile(listOf(
            ScheduleBlock("2026-06-08", "09:00", "12:00", "exam", "POO"),
        ))
        val plan = StudyPlanner.multiDay(7, examIn30d, emptyList(), emptyList(), emptyList(), now, utc)
        // Day 1 (today) — exam is 30 days away, beyond 14d window.
        assertNull(plan[0].examNote, "exam 30d away → no note on day 1")
    }

    @Test
    fun renderMultiDayShowsAllSections() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "PS", "Markov"),
            ScheduleBlock("2026-05-14", "09:00", "12:00", "exam", "PS", "PS HW"),
        ))
        val stats = listOf(
            ConceptStat("Stale-X", "PS", "2026-04-25T00:00:00Z", 14L, 0.05f),
        )
        val catalog = listOf(
            ConceptCatalog.Concept("Untouched-Y", "PS", "PS/y.md"),
        )
        val plan = StudyPlanner.multiDay(2, s, stats, emptyList(), catalog, now, utc)
        val rendered = StudyPlanner.renderMultiDay(plan, s, now, utc)
        assertTrue(rendered.contains("2026-05-09"), "first day date in render")
        assertTrue(rendered.contains("Markov"), "scheduled block topic")
        assertTrue(rendered.contains("Stale-X"), "stale review concept")
        assertTrue(rendered.contains("Untouched-Y"), "catchup concept")
        assertTrue(rendered.contains("PS"), "subject mentioned")
    }

    @Test
    fun renderShowsReviewDebtLineWhenOverflow() {
        val stats = (1..15).map {
            ConceptStat("Stale-$it", "PA", "2026-04-25T00:00:00Z", 14L, 0.05f - it * 0.001f)
        }
        val plan = StudyPlanner.multiDay(1, ScheduleFile(), stats, emptyList(), emptyList(), now, utc)
        val rendered = StudyPlanner.renderMultiDay(plan, ScheduleFile(), now, utc)
        assertTrue(rendered.contains("review debt") || rendered.contains("Review debt"),
            "render mentions review debt when overflow exists; got: $rendered")
        assertTrue(rendered.contains("7"), "debt count shown (15 - 8 cap = 7); got: $rendered")
    }
}
