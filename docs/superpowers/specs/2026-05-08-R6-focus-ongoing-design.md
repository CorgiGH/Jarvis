# R6 — Live focus-session ongoing notification

## Context

Android Live Updates / iOS-Live-Activities equivalent. When jarvis observes a sustained high-importance focus block (≥30min same-process, importance ≥0.7), surface as quiet ongoing notification — glanceable, no sound/vibrate, not a normal "alert" interrupt.

## Approaches

- **(a) Server-side detection + new GET /api/focus + APK polls and posts/clears ongoing notification.** ✓ Picked. Reuses existing polling infra. Server already has activity importance + recent.
- **(b) On-device activity-window inference.** APK doesn't have activity logger.
- **(c) Foreground service running on phone.** Battery cost; APK has no service today.

## Design

**Server side:**

New file: `src/main/kotlin/jarvis/FocusSession.kt`
```kotlin
@Serializable data class FocusSession(
    val active: Boolean,
    val process: String? = null,
    val title: String? = null,
    val durationMin: Long = 0,
    val startedTs: String? = null,
)
object FocusDetector {
    fun current(activity: List<ActivityEntry>, now: Instant): FocusSession
}
```

Logic:
- Walk activity backwards from `now`, find run of same `process` AND `importance >= 0.7`.
- If run ≥30min, return `active=true` with details.
- Else `active=false`.

New endpoint: `GET /api/focus` returns `FocusSession`.

**Android side:**

`SignalWorker` extended: after fetching signals, also fetch focus state. If `active=true` AND no current focus notif → post one. If `active=false` AND notif exists → cancel.

New file: `Notifications.postFocus(ctx, session)` + `clearFocus(ctx)`. Quiet channel `jarvis-focus` IMPORTANCE_LOW (no sound, no vibrate). `setOngoing(true)` so user can't accidentally swipe.

## Edge cases

- Activity log empty → active=false.
- Single high-importance event but <30min run → active=false.
- Process name changes mid-run → run resets.
- Polling 15-min — focus state can be stale up to 15 min. Acceptable.

## Acceptance criteria

- 6+ tests on FocusDetector: empty/short/long run/process-switch/no-importance/all-low-importance.
- Server endpoint smoke.
- APK builds.

## LOC estimate

~70 server + ~50 client + ~80 tests.
