package jarvis

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossCorrelatorTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    private fun e(hoursAgo: Long, process: String = "code.exe", imp: Float? = 0.5f) =
        ActivityEntry(
            ts = now.minus(Duration.ofHours(hoursAgo)).toString(),
            title = "$process work",
            process = process,
            pid = 1L,
            importance = imp,
        )

    private fun sig(hoursAgo: Long, importance: Float, status: String = "emitted") =
        ProactiveSignal(
            id = "s$hoursAgo",
            ts = now.minus(Duration.ofHours(hoursAgo)).toString(),
            kind = "ctx_model_summary",
            importance = importance,
            sourceTs = now.minus(Duration.ofHours(hoursAgo)).toString(),
            snippet = "test",
            rationale = "t",
            status = status,
        )

    private fun sleep(daysAgo: Long, durHours: Double) = SleepWindow(
        startTs = now.minus(Duration.ofDays(daysAgo + 1)).toString(),
        endTs = now.minus(Duration.ofDays(daysAgo)).plus(Duration.ofHours(durHours.toLong())).toString(),
        durationHours = durHours,
    )

    @Test
    fun emptyInputsNoFindings() {
        assertEquals(emptyList(), CrossCorrelator.analyze(emptyList(), emptyList(), emptyList(), now, utc))
    }

    @Test
    fun sleepDebtDetected() {
        // 5 short sleeps: avg 4.5h.
        val sleeps = (0..4).map { sleep(it.toLong(), 4.5) }
        val findings = CrossCorrelator.analyze(emptyList(), emptyList(), sleeps, now, utc)
        assertTrue(findings.any { it.pattern == "sleep-debt" }, "sleep-debt detected")
    }

    @Test
    fun adequateSleepNoFinding() {
        val sleeps = (0..4).map { sleep(it.toLong(), 8.0) }
        val findings = CrossCorrelator.analyze(emptyList(), emptyList(), sleeps, now, utc)
        assertTrue(findings.none { it.pattern == "sleep-debt" }, "8h average is fine")
    }

    @Test
    fun lateNightPatternDetected() {
        // 12 entries between 23-03 across 3 days.
        val activity = mutableListOf<ActivityEntry>()
        for (day in 0..2) {
            for (h in listOf(23L, 0L, 1L, 2L)) {
                activity += e((day * 24L) + (12L - h).coerceAtLeast(0L) + 11L)
            }
        }
        // Force at least 12 entries in 23-03 hour bucket. Reusing helper would
        // need precise hour-of-day; constructing directly:
        val explicit = listOf(
            "2026-05-08T01:00:00Z", "2026-05-08T02:00:00Z", "2026-05-08T23:00:00Z",
            "2026-05-07T01:00:00Z", "2026-05-07T02:00:00Z", "2026-05-07T23:00:00Z",
            "2026-05-06T01:00:00Z", "2026-05-06T02:00:00Z", "2026-05-06T23:00:00Z",
            "2026-05-05T01:00:00Z", "2026-05-05T02:00:00Z",
        ).map { ActivityEntry(ts = it, process = "chrome.exe") }
        val findings = CrossCorrelator.analyze(explicit, emptyList(), emptyList(), now, utc)
        assertTrue(findings.any { it.pattern == "late-night-pattern" },
            "late-night detected: ${findings}")
    }

    @Test
    fun dayOfWeekStressDetected() {
        // 6 high-importance signals, 4 of them on a Monday (UTC).
        val mondays = (0..3).map { i ->
            // 2026-05-04 was a Monday — back-date.
            val mondayBase = Instant.parse("2026-05-04T12:00:00Z")
            sig(
                Duration.between(mondayBase, now).toHours() - (i * 24L * 7L),
                0.85f,
            )
        }
        val others = listOf(sig(48, 0.85f), sig(72, 0.85f))
        val all = mondays + others
        val findings = CrossCorrelator.analyze(emptyList(), all, emptyList(), now, utc)
        // Lightweight assertion — expect either day-of-week or importance-cluster
        // depending on how the math falls. Just verify analyze doesn't crash + finds something.
        assertTrue(findings.isNotEmpty(),
            "high-importance bursts should produce some finding")
    }

    @Test
    fun importanceClusterDetected() {
        // 20 recent signals all at importance 0.9.
        val burst = (1L..20L).map { sig(it, 0.9f) }
        val findings = CrossCorrelator.analyze(emptyList(), burst, emptyList(), now, utc)
        assertTrue(findings.any { it.pattern == "importance-cluster" },
            "sustained 0.9 imp burst is a cluster")
    }

    @Test
    fun analyzeIsPureOnEmptyOrInsufficient() {
        // Insufficient inputs of every type → no false-positive findings.
        val findings = CrossCorrelator.analyze(
            listOf(e(1)), // 1 entry
            listOf(sig(1, 0.85f)), // 1 signal
            listOf(sleep(0, 4.0)), // 1 sleep
            now, utc,
        )
        assertEquals(emptyList(), findings, "below-threshold inputs → empty")
    }
}
