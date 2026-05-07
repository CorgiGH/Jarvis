package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoalsTest {

    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    @Test
    fun roundTripGoalSet(@TempDir tmp: Path) {
        val file = tmp.resolve("goals.jsonl")
        val id = Goals.computeId("ship phase 6", now)
        Goals.appendTo(file, GoalEntry(
            id = id, ts = now.toString(), kind = "set", text = "ship phase 6",
        ))
        val all = Goals.readAllFrom(file)
        assertEquals(1, all.size)
        assertEquals("set", all[0].kind)
        assertEquals(id, all[0].id)
    }

    @Test
    fun deterministicIdSameDay() {
        val a = Goals.computeId("same text", Instant.parse("2026-05-08T08:00:00Z"))
        val b = Goals.computeId("same text", Instant.parse("2026-05-08T20:00:00Z"))
        assertEquals(a, b, "same day-bucket → same id")
    }

    @Test
    fun differentDayDifferentId() {
        val a = Goals.computeId("same text", Instant.parse("2026-05-08T08:00:00Z"))
        val b = Goals.computeId("same text", Instant.parse("2026-05-09T08:00:00Z"))
        assertTrue(a != b, "different day → different id")
    }

    @Test
    fun progressLinkedToParent(@TempDir tmp: Path) {
        val file = tmp.resolve("goals.jsonl")
        val parentId = "abc123"
        Goals.appendTo(file, GoalEntry(parentId, now.toString(), "set", "build R8"))
        Goals.appendTo(file, GoalEntry(
            id = "progress1",
            ts = now.plus(Duration.ofHours(3)).toString(),
            kind = "progress",
            text = "wired oauth",
            parentId = parentId,
        ))
        val views = Goals.currentFrom(file, now.plus(Duration.ofHours(4)))
        assertEquals(1, views.size)
        assertEquals("build R8", views[0].text)
        assertNotNull(views[0].latestProgressText)
        assertEquals("wired oauth", views[0].latestProgressText)
    }

    @Test
    fun staleGoalSurfacesAtTop(@TempDir tmp: Path) {
        val file = tmp.resolve("goals.jsonl")
        // Two goals: one stale 14 days, one fresh.
        val stale = "stale1"
        val fresh = "fresh1"
        Goals.appendTo(file, GoalEntry(stale,
            now.minus(Duration.ofDays(14)).toString(), "set", "stale goal"))
        Goals.appendTo(file, GoalEntry(fresh,
            now.minus(Duration.ofDays(1)).toString(), "set", "fresh goal"))
        val views = Goals.currentFrom(file, now)
        // Sorted by staleDays descending — stale first.
        assertEquals("stale goal", views[0].text)
        assertEquals(14L, views[0].staleDays)
        assertEquals(1L, views[1].staleDays)
    }

    @Test
    fun missingFileReturnsEmpty(@TempDir tmp: Path) {
        assertEquals(emptyList(), Goals.readAllFrom(tmp.resolve("nope.jsonl")))
        assertEquals(emptyList(), Goals.currentFrom(tmp.resolve("nope.jsonl"), now))
    }

    @Test
    fun corruptLineSkipped(@TempDir tmp: Path) {
        val file = tmp.resolve("goals.jsonl")
        Goals.appendTo(file, GoalEntry("a", now.toString(), "set", "ok"))
        java.nio.file.Files.writeString(
            file, "garbage\n",
            java.nio.file.StandardOpenOption.APPEND,
        )
        Goals.appendTo(file, GoalEntry("b", now.toString(), "set", "good"))
        assertEquals(2, Goals.readAllFrom(file).size)
    }
}
