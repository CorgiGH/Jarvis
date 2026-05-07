# Phase 2.3 — Surface decision routing

## Context

Roadmap Phase 2.3: given a signal kind+importance, pick the right channel.
- Pin to `core_memory.md` — low-cost, passive, persistent.
- Append to `wiki.md` under "proactive note" — medium, browsable.
- Push notification — high-cost, attention-stealing.

Currently every signal goes only to `signals.jsonl` + Android Notification (push). All-or-nothing.

## Approach

Pure routing function `SurfaceRouter.route(signal)` returns `Set<Surface>`. ProactiveLoop + ReflectionLoop call it to fan out write side-effects.

Routing rules (start simple, tune later):
- **importance < 0.5** → wiki only (browsable, not interruptive).
- **0.5 ≤ importance < 0.8** → wiki + push (default behavior preserved).
- **importance ≥ 0.8** → wiki + push + pin to core_memory.
- `kind="reflection"` → wiki + pin (always durable, never push — reflections shouldn't notify).
- `kind="error"` → wiki only (don't push errors at the user).

## Design

**New file:** `src/main/kotlin/jarvis/SurfaceRouter.kt`
```kotlin
enum class Surface { PUSH, WIKI, PIN }
object SurfaceRouter {
    fun route(signal: ProactiveSignal): Set<Surface>
    fun apply(signal: ProactiveSignal)  // wiki + pin side effects
}
```

`apply` performs the wiki append + core_memory pin. PUSH is implicit — the existing `/api/signals` polling path delivers it. So `apply` only handles WIKI and PIN.

**Wiring:**
- `ProactiveLoop.considerSync` after `Signals.appendTo`: call `SurfaceRouter.apply(signal)`.
- `ReflectionLoop.reflectSync` after `Signals.appendTo`: same.

`Notifications.postSignal` (Android side) gates on `Surface.PUSH`. Server filters `/api/signals` to push-eligible only? — no, simpler: client can show all but server marks. Defer client filter; server filters /api/signals on PUSH route only.

Actually simplest: keep /api/signals returning everything (history feed wants everything). Add `/api/signals/push` that filters to PUSH-routed only. SignalWorker switches to /api/signals/push.

## Edge cases

- core_memory.md PII pin → run scanTextForPii BEFORE pin. Skip-with-WARN if trips.
- Same signal routed twice → idempotent (wiki appends are dedup-tolerant via msgId in signal id; pin uses sha-id-prefix).
- Disabled by env? — no, this is structural; user opt-in is via R7 settings client-side.

## Acceptance criteria

- 6+ tests on routing rules (low/mid/high importance, reflection kind, error kind).
- `apply` smoke: wiki gets the entry, core_memory accumulates pins.

## LOC estimate

~80 main + ~70 tests + 1 endpoint addition.
