package jarvis

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * R6 (deep-research recommendation #6 / Android Live Updates equivalent) —
 * detect when the user is in a sustained high-importance focus block.
 *
 * Definition: ≥30min run of same `process` AND `importance >= 0.7`. Walks
 * activity backwards from `now`, breaks on first different-process or
 * sub-threshold-importance row.
 */
@Serializable
data class FocusSession(
    val active: Boolean,
    val process: String? = null,
    val title: String? = null,
    val durationMin: Long = 0,
    val startedTs: String? = null,
)

object FocusDetector {

    private const val MIN_FOCUS_MINUTES = 30L
    private const val IMPORTANCE_FLOOR = 0.7f

    fun current(activity: List<ActivityEntry>, now: Instant): FocusSession {
        if (activity.isEmpty()) return FocusSession(active = false)
        val sorted = activity.sortedBy { it.ts }
        // Walk newest-first; collect contiguous run of same-process + high-imp.
        val newest = sorted.last()
        val newestTs = parseInstantOrNull(newest.ts) ?: return FocusSession(active = false)
        if ((newest.importance ?: 0f) < IMPORTANCE_FLOOR) return FocusSession(active = false)
        val proc = newest.process ?: return FocusSession(active = false)

        var runStart: Instant = newestTs
        for (entry in sorted.asReversed()) {
            if (entry === newest) continue
            if (entry.process != proc) break
            if ((entry.importance ?: 0f) < IMPORTANCE_FLOOR) break
            val ts = parseInstantOrNull(entry.ts) ?: break
            if (ts < runStart) runStart = ts
        }

        // Include the start sample's own ts in run if it matches.
        val startSample = sorted.firstOrNull {
            it.process == proc &&
                (it.importance ?: 0f) >= IMPORTANCE_FLOOR &&
                parseInstantOrNull(it.ts)?.let { ts -> ts >= runStart } == true
        } ?: newest

        val durationMin = Duration.between(runStart, newestTs).toMinutes()
        if (durationMin < MIN_FOCUS_MINUTES) {
            return FocusSession(active = false)
        }
        return FocusSession(
            active = true,
            process = proc,
            title = newest.title,
            durationMin = durationMin,
            startedTs = startSample.ts,
        )
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
