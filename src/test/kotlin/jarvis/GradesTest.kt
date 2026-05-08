package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradesTest {

    @Test
    fun recordAppendsRow(@TempDir tmp: Path) {
        val f = tmp.resolve("grades.jsonl")
        val ts = Instant.parse("2026-05-08T10:00:00Z")
        val e = Grades.recordTo(f, "PA", "Test 1", 14.5, 35.0, "midterm", ts)
        assertEquals("PA", e.subject)
        assertEquals(14.5, e.earned)
        val rows = Grades.readAllFrom(f)
        assertEquals(1, rows.size)
        assertEquals(e.id, rows[0].id)
    }

    @Test
    fun latestRowWinsPerComponent(@TempDir tmp: Path) {
        val f = tmp.resolve("grades.jsonl")
        val t1 = Instant.parse("2026-05-08T10:00:00Z")
        val t2 = Instant.parse("2026-05-08T11:00:00Z")
        Grades.recordTo(f, "PA", "Test 1", 10.0, 35.0, null, t1)
        Grades.recordTo(f, "PA", "Test 1", 14.5, 35.0, "regraded", t2)
        val latest = Grades.latestPerComponent(f)
        assertEquals(1, latest.size, "duplicates collapse on (subject, component)")
        assertEquals(14.5, latest[0].earned, "latest ts wins")
        assertEquals("regraded", latest[0].note)
    }

    @Test
    fun summaryRollsUpPerSubjectSortedByRatio(@TempDir tmp: Path) {
        val f = tmp.resolve("grades.jsonl")
        val t = Instant.parse("2026-05-08T10:00:00Z")
        // POO weak: 0/30 lab + 5/10 quiz = 5/40 = 0.125
        Grades.recordTo(f, "POO", "Lab 1", 0.0, 30.0, null, t)
        Grades.recordTo(f, "POO", "Quiz", 5.0, 10.0, null, t)
        // ALO strong: 7.75/10 + 55/55 = 62.75/65 = 0.965
        Grades.recordTo(f, "ALO", "Seminar", 7.75, 10.0, null, t)
        Grades.recordTo(f, "ALO", "Tema 1", 55.0, 55.0, null, t)

        val summary = Grades.summaryBySubject(f)
        assertEquals(2, summary.size)
        assertEquals("POO", summary[0].subject, "weakest subject first")
        assertEquals(5.0, summary[0].totalEarned)
        assertEquals(40.0, summary[0].totalMax)
        assertEquals(0.125, summary[0].ratio, "5/40 = 0.125")
        assertEquals(2, summary[0].componentCount)

        assertEquals("ALO", summary[1].subject)
        assertTrue(summary[1].ratio > 0.95, "ALO ratio above 0.95: ${summary[1].ratio}")
    }

    @Test
    fun summaryHandlesZeroMaxGracefully(@TempDir tmp: Path) {
        val f = tmp.resolve("grades.jsonl")
        val t = Instant.parse("2026-05-08T10:00:00Z")
        Grades.recordTo(f, "PS", "Pending", 0.0, 0.0, "TBD", t)
        val summary = Grades.summaryBySubject(f)
        assertEquals(1, summary.size)
        assertEquals(0.0, summary[0].ratio, "0/0 → 0.0 ratio (NaN-safe)")
    }

    @Test
    fun emptyLedgerEmptySummary(@TempDir tmp: Path) {
        val f = tmp.resolve("grades.jsonl")
        assertEquals(emptyList(), Grades.readAllFrom(f))
        assertEquals(emptyList(), Grades.summaryBySubject(f))
    }

    @Test
    fun idIsDeterministic(@TempDir tmp: Path) {
        val a = Grades.computeId("PA", "Test 1")
        val b = Grades.computeId("PA", "Test 1")
        val c = Grades.computeId("PA", "Test 2")
        assertEquals(a, b)
        assertNotNull(a)
        assertTrue(a != c)
    }
}
