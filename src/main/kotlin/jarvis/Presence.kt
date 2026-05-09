package jarvis

import java.time.Duration
import java.time.Instant

/**
 * 2026-05-09 user feedback: static QuietHours window doesn't match real
 * sleep. User wakes 7-12am, but exact wake time varies. Static end=12:00
 * means at 12:01 system spams "STUDY NOW" even if user is still asleep,
 * and conversely the eat/wake-up buffer is missing — first hour after
 * waking should be soft.
 *
 * Presence solves this dynamically off /api/activity gaps. Definitions:
 *
 *  - SLEEPING: no activity in the last [INACTIVITY_TO_SLEEP_MIN] minutes.
 *    Suppress everything that costs the user attention OR provider tokens.
 *
 *  - JUST_WOKE: last gap before current activity was ≥ [LONG_GAP_HOURS],
 *    and time since first post-gap activity is < [WAKE_BUFFER_MIN]. Soft
 *    nudges only — drift_alert allowed (you might be doomscrolling) but
 *    "STUDY NOW" pomodoro pulses suppressed.
 *
 *  - AWAKE: everything else — full nudge cadence allowed.
 *
 * Composite gate `shouldNudge(soft = false)` returns true only when AWAKE
 * AND not in any QuietHours window. `shouldNudge(soft = true)` allows
 * JUST_WOKE through.
 */
object Presence {

    private const val INACTIVITY_TO_SLEEP_MIN = 90L  // 1.5h gap = asleep
    private const val LONG_GAP_HOURS = 4L            // gap qualifying as sleep
    private const val WAKE_BUFFER_MIN = 45L          // post-wake soft window

    enum class State { SLEEPING, JUST_WOKE, AWAKE }

    data class Reading(
        val state: State,
        val lastActivityTs: Instant?,
        val gapMinutes: Long,
        val minutesSinceWake: Long,
    )

    fun observe(activity: List<ActivityEntry>, now: Instant): Reading {
        // Empty = no evidence. Fail-open to AWAKE so cold-start (fresh
        // deploy, empty activity.jsonl) does not silently kill all
        // nudges. Once even one activity row exists, gap calculation
        // kicks in and we can detect sleep properly.
        if (activity.isEmpty()) {
            return Reading(State.AWAKE, null, 0L, Long.MAX_VALUE)
        }
        val sorted = activity.mapNotNull { e ->
            runCatching { Instant.parse(e.ts) }.getOrNull()?.let { it to e }
        }.sortedBy { it.first }
        if (sorted.isEmpty()) {
            return Reading(State.AWAKE, null, 0L, Long.MAX_VALUE)
        }
        val lastTs = sorted.last().first
        val gap = Duration.between(lastTs, now).toMinutes().coerceAtLeast(0)
        if (gap >= INACTIVITY_TO_SLEEP_MIN) {
            return Reading(State.SLEEPING, lastTs, gap, Long.MAX_VALUE)
        }
        // Walk backward from the newest event; find the largest gap inside
        // the recent window. If that gap was ≥ LONG_GAP_HOURS, the event
        // immediately AFTER the gap is the "wake event".
        val wakeTs = findWakeEvent(sorted)
        val sinceWake = wakeTs?.let { Duration.between(it, now).toMinutes() } ?: Long.MAX_VALUE
        val state = if (wakeTs != null && sinceWake < WAKE_BUFFER_MIN) {
            State.JUST_WOKE
        } else {
            State.AWAKE
        }
        return Reading(state, lastTs, gap, sinceWake)
    }

    /** Walk newest→oldest looking for the first inter-event gap ≥
     *  LONG_GAP_HOURS. Returns the timestamp of the event that came
     *  AFTER that gap (the wake event). Null if no qualifying gap. */
    private fun findWakeEvent(sorted: List<Pair<Instant, ActivityEntry>>): Instant? {
        if (sorted.size < 2) return null
        val longGap = Duration.ofHours(LONG_GAP_HOURS)
        for (i in sorted.size - 1 downTo 1) {
            val newer = sorted[i].first
            val older = sorted[i - 1].first
            if (Duration.between(older, newer) >= longGap) {
                return newer
            }
        }
        return null
    }

    /**
     * Composite gate. [soft]=true allows JUST_WOKE through (low-friction
     * surfaces like drift_alert). [soft]=false (default) requires AWAKE.
     * Both modes still hard-gate on QuietHours when env-configured —
     * QuietHours is a manual override the user trusts.
     */
    fun shouldNudge(
        activity: List<ActivityEntry>,
        now: Instant,
        soft: Boolean = false,
    ): Boolean {
        if (QuietHours.isActive(now)) return false
        val r = observe(activity, now)
        return when (r.state) {
            State.AWAKE -> true
            State.JUST_WOKE -> soft
            State.SLEEPING -> false
        }
    }

    /** Diagnostic string for logs / signal rationales. */
    fun describe(reading: Reading): String =
        "presence=${reading.state} gap=${reading.gapMinutes}min sinceWake=${reading.minutesSinceWake}min"
}
