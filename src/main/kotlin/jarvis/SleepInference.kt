package jarvis

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 4.1 — sleep-window detection from activity gaps. Walks activity
 * chronologically, picks gaps ≥ [MIN_GAP_HOURS] long that look "sleep-shaped"
 * (gap entry was at night, gap exit was at morning). Cheap signal — no LLM
 * call. Feeds ctx-model + future R6 focus detector logic.
 */
@Serializable
data class SleepWindow(
    val startTs: String,
    val endTs: String,
    val durationHours: Double,
)

object SleepInference {

    private const val MIN_GAP_HOURS = 6L
    /** Local hour bands for the gap edges. */
    private val NIGHT_END_HOURS = setOf(21, 22, 23, 0, 1, 2, 3)
    private val MORNING_START_HOURS = setOf(5, 6, 7, 8, 9, 10, 11)

    fun detect(
        activity: List<ActivityEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SleepWindow> {
        if (activity.size < 2) return emptyList()
        val sorted = activity
            .mapNotNull { e -> parseInstantOrNull(e.ts)?.let { it to e } }
            .sortedBy { it.first }
        val out = mutableListOf<SleepWindow>()
        for (i in 1 until sorted.size) {
            val (prevTs, _) = sorted[i - 1]
            val (currTs, _) = sorted[i]
            val gap = Duration.between(prevTs, currTs)
            if (gap.toHours() < MIN_GAP_HOURS) continue
            val prevHour = LocalDateTime.ofInstant(prevTs, zone).hour
            val currHour = LocalDateTime.ofInstant(currTs, zone).hour
            if (prevHour !in NIGHT_END_HOURS) continue
            if (currHour !in MORNING_START_HOURS) continue
            out += SleepWindow(
                startTs = prevTs.toString(),
                endTs = currTs.toString(),
                durationHours = gap.toMinutes() / 60.0,
            )
        }
        return out
    }

    fun lastSleep(
        activity: List<ActivityEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SleepWindow? = detect(activity, zone).lastOrNull()

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
