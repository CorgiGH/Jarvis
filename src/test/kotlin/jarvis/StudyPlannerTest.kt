package jarvis

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyPlannerTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-05-09T08:00:00Z")

    @Test
    fun emptyInputsRendersHint() {
        val items = StudyPlanner.today(ScheduleFile(), emptyList(), emptyList(), now, utc)
        assertEquals(emptyList(), items)
        val rendered = StudyPlanner.render(items, ScheduleFile(), now, utc)
        assertTrue(rendered.contains("populate schedule.json"))
    }

    @Test
    fun scheduleBlocksRenderInOrder() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "13:00", "15:00", "lab", "OS", "scheduling"),
            ScheduleBlock("2026-05-09", "10:00", "12:00", "lecture", "Probability", "Markov"),
        ))
        val items = StudyPlanner.today(s, emptyList(), emptyList(), now, utc)
        val blocks = items.filter { it.kind == "block" }
        assertEquals(2, blocks.size)
        assertEquals("Probability", blocks[0].subject, "earlier block first")
        assertEquals("OS", blocks[1].subject)
    }

    @Test
    fun staleConceptsSurfaceAsReview() {
        val stats = listOf(
            ConceptStat("Markov", "Probability", "2026-05-01T00:00:00Z", 8L, 0.1f),
            ConceptStat("Bayes", "Probability", "2026-05-08T00:00:00Z", 1L, 0.9f),
        )
        val items = StudyPlanner.today(ScheduleFile(), stats, emptyList(), now, utc)
        val reviews = items.filter { it.kind == "review" }
        assertEquals(1, reviews.size, "only stale + low-confidence surfaces")
        assertEquals("Markov", reviews[0].topic)
    }

    @Test
    fun examProximityBoostsSubject() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-13", "09:00", "12:00", "exam", "Probability"),
        ))
        val stats = listOf(
            ConceptStat("Stale-A", "OS", "2026-04-01T00:00:00Z", 30L, 0.05f),
            ConceptStat("Stale-B", "Probability", "2026-04-10T00:00:00Z", 20L, 0.1f),
        )
        val items = StudyPlanner.today(s, stats, emptyList(), now, utc)
        val reviews = items.filter { it.kind == "review" }
        // Probability is the soonest exam (4 days away); should rank ahead.
        assertEquals("Probability", reviews[0].subject,
            "exam-proximity boost: Probability ahead of OS (got ${reviews.map { it.subject }})")
    }

    @Test
    fun catchupSurfacesUntouchedConcepts() {
        val catalog = listOf(
            ConceptCatalog.Concept("New 1", "OS", "OS/n1.md"),
            ConceptCatalog.Concept("New 2", "OS", "OS/n2.md"),
        )
        val items = StudyPlanner.today(ScheduleFile(), emptyList(), catalog, now, utc)
        val catchup = items.filter { it.kind == "catchup" }
        assertEquals(2, catchup.size)
        assertTrue(catchup.all { it.subject == "OS" })
    }

    @Test
    fun catchupCappedAtThree() {
        val catalog = (1..10).map { ConceptCatalog.Concept("C$it", "OS", "OS/c.md") }
        val items = StudyPlanner.today(ScheduleFile(), emptyList(), catalog, now, utc)
        val catchup = items.filter { it.kind == "catchup" }
        assertEquals(3, catchup.size, "catch-up capped at 3")
    }

    @Test
    fun renderIncludesNextExamCountdown() {
        val s = ScheduleFile(listOf(
            ScheduleBlock("2026-05-21", "09:00", "12:00", "exam", "Probability"),
        ))
        val items = StudyPlanner.today(s, emptyList(), emptyList(), now, utc)
        val rendered = StudyPlanner.render(items, s, now, utc)
        assertTrue(rendered.contains("Probability"))
        assertTrue(rendered.contains("12d"), "12 days until exam (got: $rendered)")
    }
}
