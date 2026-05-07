package jarvis

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StressProxyTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    /** Daytime UTC = daytime everywhere. */
    private val day: Instant = Instant.parse("2026-05-08T13:00:00Z")
    private val night: Instant = Instant.parse("2026-05-09T01:00:00Z")

    private fun e(minutesAgoFrom: Instant, minutesAgo: Long, process: String, title: String = "") =
        ActivityEntry(
            ts = minutesAgoFrom.minus(Duration.ofMinutes(minutesAgo)).toString(),
            title = title,
            process = process,
            pid = 1L,
            importance = 0.5f,
        )

    @Test
    fun emptyReturnsZero() {
        val s = StressProxy.current(emptyList(), day, utc)
        assertEquals(0f, s.score)
        assertEquals(emptyList(), s.reasons)
    }

    @Test
    fun calmDeepWorkScoresLow() {
        // 30 minutes of single process at midday.
        val log = (0..6).map { i -> e(day, (i * 5).toLong(), "code.exe", "main.kt") }
        val s = StressProxy.current(log, day, utc)
        assertTrue(s.score < 0.2f, "calm deep work low (got ${s.score})")
    }

    @Test
    fun highChurnRaisesScore() {
        // 8 distinct processes in 30 minutes.
        val procs = listOf("code.exe", "chrome.exe", "slack.exe", "spotify.exe",
                           "term.exe", "explorer.exe", "discord.exe", "outlook.exe")
        val log = procs.mapIndexed { i, p -> e(day, (i * 3).toLong(), p) }
        val s = StressProxy.current(log, day, utc)
        assertTrue(s.score > 0.2f, "high churn raises score (got ${s.score})")
        assertTrue(s.reasons.any { it.contains("churn") })
    }

    @Test
    fun lateHourFlags() {
        // Single calm process but at 01:00 UTC.
        val log = (0..6).map { i -> e(night, (i * 5).toLong(), "code.exe") }
        val s = StressProxy.current(log, night, utc)
        assertTrue(s.score >= 0.3f, "late hour adds ≥0.3 (got ${s.score})")
        assertTrue(s.reasons.any { it.contains("late-hour") })
    }

    @Test
    fun socialScrollingFlags() {
        // Half the last 30 minutes on twitter.
        val log = mutableListOf<ActivityEntry>()
        repeat(3) { i -> log += e(day, (i * 5).toLong(), "chrome.exe", "Home / X") }
        repeat(3) { i -> log += e(day, ((i + 3) * 5).toLong(), "code.exe", "main.kt") }
        val s = StressProxy.current(log, day, utc)
        assertTrue(s.score > 0.1f, "scrolling raises score (got ${s.score})")
        assertTrue(s.reasons.any { it.contains("scrolling") })
    }

    @Test
    fun combinedPressuresStack() {
        // Late hour + churn + scrolling.
        val procs = listOf("chrome.exe", "discord.exe", "slack.exe", "term.exe", "spotify.exe")
        val log = mutableListOf<ActivityEntry>()
        procs.forEachIndexed { i, p -> log += e(night, (i * 4).toLong(), p) }
        log += e(night, 25L, "chrome.exe", "TikTok - Make Your Day")
        log += e(night, 28L, "chrome.exe", "Home / X")
        val s = StressProxy.current(log, night, utc)
        assertTrue(s.score > 0.5f, "combined stressors stack (got ${s.score})")
        assertTrue(s.reasons.size >= 2, "multiple reasons cited")
    }

    @Test
    fun outsideWindowIgnored() {
        // Activity all from 2 hours ago — outside 30-min window.
        val log = (0..6).map { i -> e(day, (60L + i * 5L), "chrome.exe", "Home / X") }
        val s = StressProxy.current(log, day, utc)
        assertTrue(s.score < 0.3f, "old activity ignored (got ${s.score})")
    }

    @Test
    fun scoreClampedToOne() {
        // Maximize all signals.
        val procs = listOf("a.exe", "b.exe", "c.exe", "d.exe", "e.exe", "f.exe",
                           "g.exe", "h.exe", "i.exe", "j.exe")
        val log = mutableListOf<ActivityEntry>()
        procs.forEachIndexed { i, p -> log += e(night, (i * 2).toLong(), p) }
        repeat(5) { i ->
            log += e(night, ((i + 1) * 5L).toLong(), "chrome.exe", "TikTok - Make Your Day")
        }
        val s = StressProxy.current(log, night, utc)
        assertTrue(s.score in 0f..1f, "clamp respected (got ${s.score})")
    }
}
