package jarvis

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresenceTest {

    private val now = Instant.parse("2026-05-09T10:00:00Z")

    private fun ev(minutesAgo: Long, proc: String = "code.exe", imp: Float = 0.5f) =
        ActivityEntry(
            ts = now.minus(Duration.ofMinutes(minutesAgo)).toString(),
            title = "title",
            process = proc,
            pid = 1L,
            importance = imp,
        )

    @Test
    fun emptyActivityIsAwakeFailOpen() {
        // No evidence = AWAKE. Cold-start safe: empty activity.jsonl after
        // deploy must not silently kill all nudges.
        val r = Presence.observe(emptyList(), now)
        assertEquals(Presence.State.AWAKE, r.state)
    }

    @Test
    fun longGapWithoutRecentActivityIsSleeping() {
        // last event 3 h ago — no activity in 90+ min ⇒ asleep
        val r = Presence.observe(listOf(ev(180)), now)
        assertEquals(Presence.State.SLEEPING, r.state)
        assertTrue(r.gapMinutes >= 90)
    }

    @Test
    fun fullyAwakeAfterContinuousActivity() {
        // 2h of evenly-spaced activity, last sample 2 min ago
        val acts = (0..120 step 15).map { ev(it.toLong()) }
        val r = Presence.observe(acts, now)
        assertEquals(Presence.State.AWAKE, r.state, Presence.describe(r))
    }

    @Test
    fun firstActivityAfterLongGapIsJustWoke() {
        // Yesterday: activity until 6 h ago. Then nothing. Then ONE event 5 min ago.
        // Sleep gap = 6h - 5min ≈ 5h55m ≥ 4h. sinceWake = 5min < 45min ⇒ JUST_WOKE.
        val acts = listOf(
            ev(360),  // 6h ago — last pre-sleep event
            ev(5),    // 5 min ago — first post-sleep event (wake)
        )
        val r = Presence.observe(acts, now)
        assertEquals(Presence.State.JUST_WOKE, r.state, Presence.describe(r))
        assertTrue(r.minutesSinceWake in 0..45, "sinceWake reasonable: ${r.minutesSinceWake}")
    }

    @Test
    fun pastWakeBufferIsAwake() {
        // Same as above but wake event happened 60 min ago — buffer expired.
        val acts = listOf(
            ev(360),  // pre-sleep
            ev(60),   // wake event
            ev(2),    // recent activity
        )
        val r = Presence.observe(acts, now)
        assertEquals(Presence.State.AWAKE, r.state, Presence.describe(r))
    }

    @Test
    fun shouldNudgeFalseWhileSleeping() {
        // 180min gap — definite SLEEPING (≥90min cutoff).
        val r = Presence.shouldNudge(listOf(ev(180)), now, soft = false)
        assertEquals(false, r)
        val rSoft = Presence.shouldNudge(listOf(ev(180)), now, soft = true)
        assertEquals(false, rSoft, "soft mode still false during sleep")
    }

    @Test
    fun shouldNudgeTrueOnEmptyActivityFailOpen() {
        // Cold start: no recorded activity. Default fail-open = nudge.
        assertEquals(true, Presence.shouldNudge(emptyList(), now, soft = false))
    }

    @Test
    fun shouldNudgeSoftAllowsJustWoke() {
        val acts = listOf(ev(360), ev(5))
        assertEquals(false, Presence.shouldNudge(acts, now, soft = false),
            "hard nudge suppressed in just-woke buffer")
        assertEquals(true, Presence.shouldNudge(acts, now, soft = true),
            "soft nudge allowed in just-woke buffer")
    }

    @Test
    fun shouldNudgeTrueWhenFullyAwake() {
        val acts = (0..120 step 15).map { ev(it.toLong()) }
        assertEquals(true, Presence.shouldNudge(acts, now, soft = false))
    }
}
