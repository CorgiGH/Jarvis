# Autonomous roadmap — Phase 1..6

> Read `docs/superpowers/specs/2026-05-08-agi-harness-vision.md` for the why and scope. This file is the *do*.

Each step ships in one commit + smoke. After every 2-3 commits convene a post-impl council. Halt on WRONG APPROACH; fix on FLAWED.

## Phase 1 — observe with weights

### 1.1 Activity importance scoring (~80 LOC, no schema migration)

**Files:** `Activity.kt` (add nullable `importance: Float?`), new `ActivityScorer.kt`, `ActivityCapture.kt` (call scorer at snapshot time), tests in `ActivityScorerTest.kt`.

**Algorithm v1 (heuristic):**
```
score = base(process_name)
      + window_title_keyword_bonus
      + duration_continuity_bonus
      - distraction_penalty
clamped 0..1
```

`base` table:
- IDE / editor / terminal: 0.7
- docs / man / read-the-docs / stackoverflow: 0.6
- code-host (github.com, gitlab.com): 0.5
- meetings / calendar / email: 0.5
- chat / slack / discord (work context): 0.4
- youtube / netflix / twitch: 0.1
- twitter / reddit / instagram / tiktok: 0.05
- unknown: 0.3

`window_title_keyword_bonus`: regex hits boost (e.g. `error`, `exception`, `TODO`) +0.1 each, capped at 0.2.

`duration_continuity_bonus`: same process for ≥10 min: +0.1; ≥30 min: +0.2.

`distraction_penalty`: process changed >5 times in last 5 min: −0.2.

**TDD:** synthetic activity series with known expected ordering. No LLM call.

**Backwards compat:** old rows have `importance=null`; readers tolerate.

**Smoke:** after deploy, `[[activity: 1]]` should show scores. New activity entries from PC logger get scored on the server side at /api/activity write time.

### 1.2 Conversation importance (~60 LOC)

Same shape. Add nullable field. Heuristic v1: emotion-word presence (regex from a small lexicon — `frustrated`, `excited`, `stuck`, `breakthrough`), question marks (lower importance — questions answered are ephemeral), code blocks (slightly lower — task-mechanical), pin-marker phrases ("remember this", "important") boost.

**Council before shipping if:** the lexicon ends up >50 words. Subjectively-loaded scoring is a council-y decision.

### 1.3 Importance-weighted recent (~30 LOC + buildChatContext rewire)

`Conversations.recentByImportance(n)` blends recency × importance. New `[[recall_important]]` tool? Or add as parameter to existing `recall`? Design call needed.

**Pre-impl council on the API shape.**

## Phase 2 — decide on a clock

### 2.1 ProactiveLoop coroutine

Ktor-side scheduled coroutine. Fires every N min (start N=30, cron-like via `delay` in a launch). Reads activity+chat+activity-importance. Calls ctx-model + dots subsystems. If any signal scores above threshold T (start T=0.7), writes a `ProactiveSignal` row to new `signals.jsonl`.

**Pre-impl council.** Concerns to surface to council: cost (every 30 min = 48 LLM calls/day = ~$0 on Copilot subscription, but real wall-clock in subprocess); waking up the LLM provider unnecessarily.

### 2.2 Quiet hours + dedup

Sleep window inferred from activity gaps. Rate limit: max 1 surfaced signal / 60 min. Dedup by (signal_kind, signal_subject) in last 24 h.

### 2.3 Surface channels

Pin / wiki-write / push. Phone push needs Phase 3.

## Phase 3 — notification path

### 3.1 Research agent: phone notification options

**Spawn agent first.** Brief: "Read the existing Android APK code in `android/`. Determine if it has any push/poll mechanism today. Recommend the cheapest path: (a) FCM (free, requires Firebase project + token plumbing), (b) APK polls `/api/signals` when foregrounded, (c) WebSocket persistent connection. Report tradeoffs in <300 words."

Then design + council + ship.

### 3.2 Phone ack feedback loop

Once channel exists, dismiss/ack POSTs back to `/api/signal_ack`. Updates `feedback.jsonl`.

## Phase 4 — cross-correlate

(designed in detail later — needs Phase 1-3 grounding first)

## Phase 5 — learn

(designed later — needs labelled `feedback.jsonl` from Phase 3.2)

## Phase 6 — extend input surface

Calendar / email — needs OAuth (interactive) which is on the "CAN'T without user" list. Block on user availability.

---

## Concrete first commit (do this first)

```kotlin
// src/main/kotlin/jarvis/Activity.kt — add nullable importance field
@Serializable
data class ActivityEntry(
    val ts: String,
    val title: String? = null,
    val process: String? = null,
    val pid: Long? = null,
    val importance: Float? = null,   // NEW — Phase 1.1
)
```

```kotlin
// src/main/kotlin/jarvis/ActivityScorer.kt — NEW
object ActivityScorer {
    fun score(entry: ActivityEntry, recent: List<ActivityEntry>): Float { ... }
    // ...
}
```

```kotlin
// src/main/kotlin/jarvis/ActivityCapture.kt — score at snapshot time
fun snapshot(): ActivityEntry {
    val raw = /* current behavior */
    val recent = Activity.loadEntries(hours = 1)
    return raw.copy(importance = ActivityScorer.score(raw, recent))
}
```

Tests + commit + deploy + smoke.
