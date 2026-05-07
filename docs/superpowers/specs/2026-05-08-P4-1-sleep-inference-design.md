# Phase 4.1 — Sleep inference

## Context

Roadmap Phase 4.1: derive sleep windows from activity gaps. Feed ctx-model + R6 focus detector. ctx-model already infers ENERGY/STRESS but does it from raw activity blob; explicit sleep-window detection is cheaper signal that doesn't need an LLM call.

## Approach

Walk activity backwards. Find gaps ≥6h between consecutive entries. The gap IS a sleep window candidate. Validate by checking: (a) gap ends at "morning-shaped" hour (5-11 local), (b) entries before gap end at "night-shaped" hour (21-3 local). Both predicates pass → sleep window.

## Design

**New file:** `src/main/kotlin/jarvis/SleepInference.kt`
```kotlin
@Serializable data class SleepWindow(
    val startTs: String, val endTs: String, val durationHours: Double,
)
object SleepInference {
    fun detect(activity: List<ActivityEntry>, zone: ZoneId = ZoneId.systemDefault()): List<SleepWindow>
    fun lastSleep(activity: List<ActivityEntry>): SleepWindow?
}
```

Constants: `MIN_GAP_HOURS = 6`, NIGHT_HOURS = 21..27 (mod 24), MORNING_HOURS = 5..11.

**Wiring:** ctx-model subsystem prompt sees a "Recent sleep:" line. Add to `ContextModelSubsystem.run` ctx text builder. Optional — no schema change.

## Edge cases

- Empty activity → empty windows, lastSleep null.
- Single gap that's a meeting break (e.g., 2pm to 8pm with no activity) — fails morning-shaped predicate, ignored.
- DST shifts → use ZoneId.ofRules + acceptable approximation.
- All-nighters (no gap ≥6h) → no sleep window detected. Honest signal.

## Acceptance criteria

- 5+ tests: empty, single sleep, daytime gap rejected, multi-day, DST/timezone.

## LOC estimate

~80 main + ~80 tests.
