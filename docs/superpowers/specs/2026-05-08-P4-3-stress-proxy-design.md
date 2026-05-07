# Phase 4.3 — Stress proxy

## Context

Roadmap Phase 4.3: high churn + late hours + scrolling apps = stress signal. Already partially in ctx-model's qualitative inference; encode explicitly so other subsystems can read a numeric stress score without an LLM call.

## Design

**New file:** `src/main/kotlin/jarvis/StressProxy.kt`

Score 0..1 from three components:
1. **Process churn** — distinct procs in last 30min / 30. Cap at 1.0.
2. **Late-hour flag** — current local hour ∈ {22, 23, 0, 1, 2, 3}: +0.3.
3. **Scrolling-app share** — fraction of last 30min in `BROWSER_PROCS` viewing social-media titles (twitter/x.com/reddit/instagram/tiktok). Multiply by 0.4.

Final score clamped 0..1. Linear sum.

**API:**
```kotlin
@Serializable data class StressLevel(val score: Float, val reasons: List<String>)
object StressProxy { fun current(activity: List<ActivityEntry>, now: Instant, zone: ZoneId): StressLevel }
```

Wire into ctx-model alongside sleep.

## Acceptance criteria

- 5+ tests: empty, calm, high-churn, late-hour, scrolling-heavy, combined.

## LOC estimate

~70 main + ~70 tests.
