package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ActiveDocTest {

    private val now: Instant = Instant.parse("2026-05-09T11:00:00Z")

    private fun seedCatalog(@TempDir tmp: Path): Path {
        val sub = tmp.resolve("Probability/concepts")
        Files.createDirectories(sub)
        Files.writeString(sub.resolve("notes.md"), "## Markov chains\n## Bayes theorem\n")
        val sub2 = tmp.resolve("OS/concepts")
        Files.createDirectories(sub2)
        Files.writeString(sub2.resolve("sched.md"), "## CPU scheduling\n## Memory pages\n")
        ConceptCatalog.invalidate()
        return tmp
    }

    @Test
    fun detectMatchesActiveTitle(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        val entry = ActivityEntry(
            ts = now.toString(),
            title = "main.kt — studying Markov chains",
            process = "code.exe",
        )
        val concept = ActiveDoc.detect(entry, root)
        assertNotNull(concept)
        assertEquals("Markov chains", concept!!.name)
    }

    @Test
    fun detectReturnsLongestMatch(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        // Title contains both "Markov" prefix AND full "Markov chains" — longest wins.
        val entry = ActivityEntry(
            ts = now.toString(), title = "Markov chains lecture",
            process = "chrome.exe",
        )
        val c = ActiveDoc.detect(entry, root)
        assertEquals("Markov chains", c?.name, "longest concept-name match wins")
    }

    @Test
    fun detectNoMatchReturnsNull(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        val entry = ActivityEntry(ts = now.toString(), title = "weather forecast",
                                   process = "chrome.exe")
        assertNull(ActiveDoc.detect(entry, root))
    }

    @Test
    fun touchOnActivityWritesKnowledgeRow(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        val knowledge = tmp.resolve("knowledge.jsonl")
        val entry = ActivityEntry(
            ts = now.toString(), title = "CPU scheduling on the menu",
            process = "code.exe",
        )
        val concept = ActiveDoc.touchOnActivity(entry, root, knowledge, now)
        assertNotNull(concept)
        val rows = KnowledgeState.readAllFrom(knowledge)
        assertEquals(1, rows.size)
        assertEquals("CPU scheduling", rows[0].concept)
        assertEquals("OS", rows[0].subject)
        assertEquals("activity", rows[0].source)
    }

    @Test
    fun driftDetectedAcrossSubjects(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        // Schedule says we should be on Probability now.
        val schedule = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "study", "Probability", "Markov"),
        ))
        // But user is reading OS/CPU scheduling.
        val entry = ActivityEntry(
            ts = now.toString(), title = "CPU scheduling notes",
            process = "code.exe",
        )
        val drift = ActiveDoc.detectDrift(entry, schedule, now, root, java.time.ZoneId.of("UTC"))
        assertNotNull(drift)
        assertEquals("Probability", drift!!.expectedSubject)
        assertEquals("OS", drift.actualConcept?.subject)
    }

    @Test
    fun driftDetectedOnSocialMediaScrolling(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        val schedule = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "study", "Probability"),
        ))
        val entry = ActivityEntry(
            ts = now.toString(), title = "Home / X",
            process = "chrome.exe",
        )
        val drift = ActiveDoc.detectDrift(entry, schedule, now, root, java.time.ZoneId.of("UTC"))
        assertNotNull(drift)
        assertNull(drift!!.actualConcept)
        assertEquals("Probability", drift.expectedSubject)
    }

    @Test
    fun noDriftWhenOnTopic(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        val schedule = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "study", "Probability"),
        ))
        val entry = ActivityEntry(
            ts = now.toString(), title = "Markov chains review",
            process = "code.exe",
        )
        assertNull(ActiveDoc.detectDrift(entry, schedule, now, root, java.time.ZoneId.of("UTC")))
    }

    @Test
    fun noDriftOutsideStudyBlock(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        // No block scheduled at this time — no drift to detect.
        val schedule = ScheduleFile()
        val entry = ActivityEntry(
            ts = now.toString(), title = "Home / X",
            process = "chrome.exe",
        )
        assertNull(ActiveDoc.detectDrift(entry, schedule, now, root, java.time.ZoneId.of("UTC")))
    }

    @Test
    fun noDriftDuringBreakBlock(@TempDir tmp: Path) {
        val root = seedCatalog(tmp)
        // Break is not a focused-study kind — drift logic skips it.
        val schedule = ScheduleFile(listOf(
            ScheduleBlock("2026-05-09", "10:00", "12:00", "break", "lunch"),
        ))
        val entry = ActivityEntry(
            ts = now.toString(), title = "Home / X",
            process = "chrome.exe",
        )
        assertNull(ActiveDoc.detectDrift(entry, schedule, now, root, java.time.ZoneId.of("UTC")))
    }
}
