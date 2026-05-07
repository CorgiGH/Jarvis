package jarvis

import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class SleepInferenceTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun e(ts: String) = ActivityEntry(ts = ts, process = "code.exe")

    @Test
    fun emptyReturnsEmpty() {
        assertEquals(emptyList(), SleepInference.detect(emptyList(), utc))
        assertNull(SleepInference.lastSleep(emptyList(), utc))
    }

    @Test
    fun singleNightSleepDetected() {
        // Last activity 23:00 → first next 07:00. 8h gap, night→morning shape.
        val log = listOf(
            e("2026-05-08T22:55:00Z"),
            e("2026-05-08T23:00:00Z"),
            e("2026-05-09T07:00:00Z"),
            e("2026-05-09T07:05:00Z"),
        )
        val out = SleepInference.detect(log, utc)
        assertEquals(1, out.size)
        assertTrue(out[0].durationHours in 7.5..8.5)
    }

    @Test
    fun daytimeGapRejected() {
        // Long gap but not night-shaped: 14:00 to 20:00. Doesn't look like sleep.
        val log = listOf(
            e("2026-05-08T14:00:00Z"),
            e("2026-05-08T20:00:00Z"),
        )
        val out = SleepInference.detect(log, utc)
        assertEquals(emptyList(), out, "daytime gap rejected (gap=6h but afternoon→evening)")
    }

    @Test
    fun shortGapsBelowSixHoursIgnored() {
        // Many small gaps; none ≥6h.
        val log = listOf(
            e("2026-05-08T08:00:00Z"),
            e("2026-05-08T11:00:00Z"),
            e("2026-05-08T15:00:00Z"),
            e("2026-05-08T19:00:00Z"),
        )
        val out = SleepInference.detect(log, utc)
        assertEquals(emptyList(), out)
    }

    @Test
    fun multipleNightsDetected() {
        val log = listOf(
            // night 1
            e("2026-05-07T23:30:00Z"),
            e("2026-05-08T07:30:00Z"),
            // day 1 activity
            e("2026-05-08T14:00:00Z"),
            // night 2
            e("2026-05-08T23:00:00Z"),
            e("2026-05-09T08:00:00Z"),
        )
        val out = SleepInference.detect(log, utc)
        assertEquals(2, out.size, "two consecutive sleep windows")
    }

    @Test
    fun lastSleepReturnsMostRecent() {
        val log = listOf(
            e("2026-05-07T23:00:00Z"),
            e("2026-05-08T07:00:00Z"),
            e("2026-05-08T22:00:00Z"),
            e("2026-05-09T06:30:00Z"),
        )
        val last = SleepInference.lastSleep(log, utc)
        assertTrue(last != null && last.endTs.startsWith("2026-05-09T06"),
            "last sleep ends 2026-05-09T06:30")
    }

    @Test
    fun allNighterReturnsEmpty() {
        // Continuous activity through the night with no gap ≥6h.
        val log = (0..23).map { hour ->
            e("2026-05-08T${"%02d".format(hour)}:30:00Z")
        }
        val out = SleepInference.detect(log, utc)
        assertEquals(emptyList(), out, "no sleep detected = honest signal")
    }
}
