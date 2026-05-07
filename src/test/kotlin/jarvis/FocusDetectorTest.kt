package jarvis

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FocusDetectorTest {

    private val now: Instant = Instant.parse("2026-05-08T15:00:00Z")

    private fun e(minutesAgo: Long, process: String, imp: Float?) =
        ActivityEntry(
            ts = now.minus(Duration.ofMinutes(minutesAgo)).toString(),
            title = "$process - work",
            process = process,
            pid = 1L,
            importance = imp,
        )

    @Test
    fun emptyLogReturnsInactive() {
        val s = FocusDetector.current(emptyList(), now)
        assertEquals(false, s.active)
    }

    @Test
    fun shortRunReturnsInactive() {
        // 10 minutes of high-imp code.exe — under 30min threshold.
        val log = (0L..10L step 5).map { e(it, "code.exe", 0.85f) }
        val s = FocusDetector.current(log, now)
        assertEquals(false, s.active)
    }

    @Test
    fun longRunReturnsActive() {
        // 35 minutes of high-imp same-process.
        val log = (0L..35L step 5).map { e(it, "code.exe", 0.85f) }
        val s = FocusDetector.current(log, now)
        assertEquals(true, s.active)
        assertEquals("code.exe", s.process)
        assertTrue(s.durationMin >= 30, "duration ≥30 (got ${s.durationMin})")
    }

    @Test
    fun processSwitchResetsRun() {
        // 60min code.exe but interrupted by chrome.exe at minute 20 — newest
        // run is only the last 15min of code.exe.
        val log = mutableListOf<ActivityEntry>()
        log += (0L..15L step 5).map { e(it, "code.exe", 0.85f) }    // 0-15 ago
        log += listOf(e(20L, "chrome.exe", 0.05f))                    // gap
        log += (25L..60L step 5).map { e(it, "code.exe", 0.85f) }   // older code
        val s = FocusDetector.current(log, now)
        assertEquals(false, s.active, "interrupted run resets; last run only 15min")
    }

    @Test
    fun lowImportanceBreaksRun() {
        // 35min same-proc but importance dropped to 0.3 mid-run.
        val log = mutableListOf<ActivityEntry>()
        log += (0L..10L step 5).map { e(it, "code.exe", 0.85f) }
        log += listOf(e(15L, "code.exe", 0.3f))                       // dip
        log += (20L..35L step 5).map { e(it, "code.exe", 0.85f) }
        val s = FocusDetector.current(log, now)
        assertEquals(false, s.active, "low-importance row breaks run")
    }

    @Test
    fun nullImportanceTreatedAsLow() {
        val log = (0L..35L step 5).map { e(it, "code.exe", null) }
        val s = FocusDetector.current(log, now)
        assertEquals(false, s.active)
    }

    @Test
    fun allLowImportanceReturnsInactive() {
        val log = (0L..40L step 5).map { e(it, "code.exe", 0.4f) }
        val s = FocusDetector.current(log, now)
        assertEquals(false, s.active)
    }

    @Test
    fun returnedFieldsIncludeProcessAndDuration() {
        val log = (0L..40L step 5).map { e(it, "idea64.exe", 0.85f) }
        val s = FocusDetector.current(log, now)
        assertEquals(true, s.active)
        assertEquals("idea64.exe", s.process)
        assertTrue(s.title?.contains("idea64.exe") == true)
        assertTrue(s.durationMin >= 30L, "duration ≥30, got ${s.durationMin}")
        assertTrue(!s.startedTs.isNullOrEmpty())
    }
}
