# R1 — Reflection tree (Park et al. 2023 §4.2 recursive reflection)

## Context

ProactiveLoop fires ctx-model on a single high-importance event. Park et al. layers reflections on top: when Σ recent importance > θ, LLM is asked to suggest 3 questions about recent memory, retrieves evidence per question, synthesizes a high-level insight, writes back as new memory. Recursive — reflections become input to next reflections.

## Approaches

- **(a) ReflectionLoop on a Σ-importance trigger over signals.jsonl.** ✓ Picked. Uses signals (already importance-tagged) instead of all events. Cheaper.
- **(b) Run reflection on activity+conversations directly.** Doubles input volume. Same insight available via signals.
- **(c) Pure cron daily reflection.** Already exists in `runReflect`. R1 is the *recursive* layer atop it.

## Design

**New file:** `src/main/kotlin/jarvis/ReflectionLoop.kt`

Trigger: at end of every `ProactiveLoop.considerSync` (after writing the signal), check if Σ importance of `signals.jsonl` rows in the last 24h ≥ θ (default 3.0, tunable). If yes AND no reflection-kind row in last 6h, fire reflection.

Reflection logic (one LLM call, simpler than Park's "3 questions then 3 retrievals"):
- Load last 24h of signals (kind=ctx_model_summary or kind=reflection both eligible — recursive).
- Compose prompt: "Here are N signals from the past 24h. Write 1 sentence describing the dominant pattern. No filler."
- LLM call via `LlmFactory.create()`, `withTimeout(60s)`, `Dispatchers.IO.limitedParallelism(1)` (reuse ProactiveLoop dispatcher? — separate to avoid contention).
- Output → `Signals.append(ProactiveSignal(kind="reflection", parent_ids=[...]))` — reusing existing `ProactiveSignal` schema since "kind" already discriminates.

**Schema:** add optional `parentIds: List<String>? = null` to `ProactiveSignal`. Nullable, backward compat.

**Recursion:** since reflections themselves write `ProactiveSignal` rows, the next firing eligibility check sees them in the Σ — natural tree.

**Default-OFF** behind `REFLECTION_LOOP_ENABLED=true` env (mirrors ProactiveLoop pattern).

## Edge cases

- No signals in 24h → no fire, no error.
- Signals all error rows → Σ importance = 0 → no fire (good — error rows shouldn't trigger reflections).
- Recursion runaway: reflection writes another high-importance row that triggers another reflection that triggers another... → 6h dedup cooldown breaks runaway. Plus reflection signals have explicit `kind="reflection"` for inspection.
- ctx-model output already in signals.jsonl includes phrases that might match PII regex → already filtered by `/api/signals` PII gate. Reflections inherit the same gate.

## Acceptance criteria

- 5+ tests: σ-trigger threshold, 6h dedup, error rows excluded from σ, env flag default-off.
- Smoke: enable env, watch `signals.jsonl` for `kind="reflection"` after Σ accumulates.

## LOC estimate

~120 main + ~80 tests.
