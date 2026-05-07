package jarvis

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * F5 — daily cross-correlator: runs over recent activity + stress proxy +
 * sleep windows + signals to detect simple non-LLM patterns the user might
 * not see. Pure function; emits zero or more pattern strings.
 *
 * Patterns checked (extensible):
 *   1. day-of-week stress concentration (M-F vs weekend)
 *   2. late-night activity sustained over multiple days
 *   3. sleep-debt (avg < 6h over last 5 days)
 *   4. focus-block scarcity (≤1 R6-style focus block in last 3 days)
 *   5. importance-rich days clustering
 */
@Serializable
data class CorrelationFinding(
    val pattern: String,
    val evidence: String,
)

object CrossCorrelator {

    fun analyze(
        activity: List<ActivityEntry>,
        signals: List<ProactiveSignal>,
        sleeps: List<SleepWindow>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CorrelationFinding> {
        val findings = mutableListOf<CorrelationFinding>()
        listOfNotNull(
            checkSleepDebt(sleeps),
            checkLateNight(activity, now, zone),
            checkDayOfWeekStress(signals, zone),
            checkFocusScarcity(activity, now),
            checkImportanceClusters(signals),
        ).forEach { findings += it }
        return findings
    }

    private fun checkSleepDebt(sleeps: List<SleepWindow>): CorrelationFinding? {
        if (sleeps.size < 3) return null
        val recent = sleeps.takeLast(5)
        val avg = recent.sumOf { it.durationHours } / recent.size
        if (avg >= 6.0) return null
        return CorrelationFinding(
            "sleep-debt",
            "average ${"%.1f".format(avg)}h over last ${recent.size} sleep windows",
        )
    }

    private fun checkLateNight(
        activity: List<ActivityEntry>,
        now: Instant,
        zone: ZoneId,
    ): CorrelationFinding? {
        val cutoff = now.minus(Duration.ofDays(5))
        val late = activity.mapNotNull { e ->
            val ts = parseInstantOrNull(e.ts) ?: return@mapNotNull null
            if (ts < cutoff) null else {
                val hour = LocalDateTime.ofInstant(ts, zone).hour
                if (hour in 0..3 || hour == 23) ts else null
            }
        }
        if (late.size < 10) return null
        val days = late.map { LocalDateTime.ofInstant(it, zone).toLocalDate() }.distinct().size
        if (days < 3) return null
        return CorrelationFinding(
            "late-night-pattern",
            "${late.size} late-night samples across $days days in last 5",
        )
    }

    private fun checkDayOfWeekStress(
        signals: List<ProactiveSignal>,
        zone: ZoneId,
    ): CorrelationFinding? {
        val weekdayCount = mutableMapOf<DayOfWeek, Int>()
        for (sig in signals) {
            if (sig.importance < 0.7f) continue
            val ts = parseInstantOrNull(sig.ts) ?: continue
            val dow = LocalDateTime.ofInstant(ts, zone).dayOfWeek
            weekdayCount.merge(dow, 1) { a, b -> a + b }
        }
        if (weekdayCount.values.sum() < 5) return null
        val (top, count) = weekdayCount.maxByOrNull { it.value } ?: return null
        val total = weekdayCount.values.sum()
        if (count.toFloat() / total < 0.5f) return null
        return CorrelationFinding(
            "day-of-week-stress",
            "$count of $total high-importance signals fall on $top",
        )
    }

    private fun checkFocusScarcity(
        activity: List<ActivityEntry>,
        now: Instant,
    ): CorrelationFinding? {
        val cutoff = now.minus(Duration.ofDays(3))
        val window = activity.mapNotNull { e ->
            val ts = parseInstantOrNull(e.ts) ?: return@mapNotNull null
            if (ts < cutoff) null else e
        }
        if (window.size < 30) return null
        val focusBlocks = countFocusBlocks(window)
        if (focusBlocks > 1) return null
        return CorrelationFinding(
            "focus-scarcity",
            "$focusBlocks focus blocks (≥30min same-process imp≥0.7) in last 3 days",
        )
    }

    private fun countFocusBlocks(window: List<ActivityEntry>): Int {
        val sorted = window.mapNotNull { e ->
            val ts = parseInstantOrNull(e.ts) ?: return@mapNotNull null
            ts to e
        }.sortedBy { it.first }
        var blocks = 0
        var runStart: Instant? = null
        var runProc: String? = null
        for ((ts, e) in sorted) {
            val highImp = (e.importance ?: 0f) >= 0.7f
            val proc = e.process
            if (highImp && proc != null && proc == runProc && runStart != null) {
                if (Duration.between(runStart, ts).toMinutes() >= 30L) {
                    blocks++
                    runStart = null
                    runProc = null
                }
            } else {
                runStart = if (highImp) ts else null
                runProc = if (highImp) proc else null
            }
        }
        return blocks
    }

    private fun checkImportanceClusters(signals: List<ProactiveSignal>): CorrelationFinding? {
        val emitted = signals.filter { it.status == "emitted" }
        if (emitted.size < 5) return null
        val recent = emitted.takeLast(20)
        val avg = recent.map { it.importance }.average().toFloat()
        if (avg < 0.85f) return null
        return CorrelationFinding(
            "importance-cluster",
            "last ${recent.size} signals avg imp=${"%.2f".format(avg)} — sustained-pressure window",
        )
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
