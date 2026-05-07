# R3 — User feedback loop (ack / pin / dismiss / noise)

> Autonomous-mode brainstorm + spec.

## Context

Post-impl council 1778164815 + deep-research report identified the missing feedback loop as the #1 predictor of importance-score drift over multi-week use. Mem0, Letta, Khoj all expose user-correction signals; jarvis treats every signal as fire-and-forget. Phase 5 of the existing roadmap depends on this data.

## Approaches

- **(a) New POST endpoint + `feedback.jsonl` + Notification action buttons.** ✓ Picked. Standard wire format. Async, append-only.
- **(b) PUT /api/signals/{id}** with status-update field. Rewrites signals.jsonl rows → fatal at scale.
- **(c) In-app-only feed with long-press menu, no Notification actions.** Loses lockscreen ergonomics.

## Design

**New file:** `src/main/kotlin/jarvis/Feedback.kt`
```kotlin
@Serializable data class FeedbackEntry(
    val signalId: String, val ts: String, val action: String, val v: Int = 1,
)
object Feedback { fun append/appendTo/readAll/readAllFrom }
```

Actions whitelist: `dismissed`, `pinned`, `useful`, `noise`.

**New endpoint:** `POST /api/signals/ack` body `{signalId, action}` → 204. Validates action in whitelist; 400 if not. Auth via existing bearer middleware.

**Android:** `Notifications.postSignal` adds two action buttons — "Pin" and "Dismiss". Each fires a `BroadcastReceiver` that POSTs the ack via `JarvisClient.ackSignal`. Kept lightweight: no foreground service.

`SignalActionReceiver` extends `BroadcastReceiver`. PendingIntent for each action goes through it. On receive, launch a coroutine, fire ack, cancel that notification.

## Edge cases

- Network down during ack POST → drop silently, do NOT requeue. v1 is best-effort; missed acks are not load-bearing for Phase 5 retraining (training is on aggregates, not individual rows).
- Unknown action → server 400.
- Duplicate ack on same signalId → both rows survive (append-only). Aggregator dedups on read.
- 401 → silent. Notification re-auth banner already covers this in SignalWorker.

## Acceptance criteria

- 5+ tests: feedback append+read round-trip, action whitelist (good + bad), readAllFrom missing file empty, schema v=1 default.
- Server smoke: `curl POST /api/signals/ack` with valid + invalid action.
- APK builds cleanly with action buttons.

## LOC estimate

~60 server + ~70 client (BroadcastReceiver + manifest + JarvisClient method + Notifications action buttons). Within budget.
