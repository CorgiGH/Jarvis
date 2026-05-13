<!-- docs/standin-findings/golden/2026-05-13-bootstrap-traces.md -->
# Surface X golden fixture set — 2026-05-13 bootstrap

Hand-curated labeled traces for Surface X calibration. Each `### Trace N` block has YAML with `events:` (synthesized to match real `tutor_events.jsonl` shape) and `labels:` (hand-labelled PASS/FAIL/N_A per invariant per `tools/surface-x-invariants.mjs`).

Run calibration: `cd tools && node surface-x.mjs --from-fixture=../docs/standin-findings/golden/2026-05-13-bootstrap-traces.md --calibrate --threshold=0.80`

Ship gate: ≥K of N where K = ceil(0.80 × N). With 8 starter traces × ~1-3 labels each = ~14 pairs → K ≈ 12.

## Parser constraints

`parseFixture` in `tools/surface-x.mjs` is intentionally narrow:
- Events use inline flow form: `- {key: value, key: value}`.
- Values may NOT contain `,` (splits pairs) or `:` (splits key/value) — encode as `|`-joined enums or boolean flags instead.
- Labels match `^\s+INV-\d{2}:\s*(\w+)\s*$` — comments after the value break parsing.

Seed traces below model real envelope fields (`event_type`, `task_id`, `correct`, `is_synthetic`, `model_resolved`, `rubric_chip_text`, `retrieved_context_count`, `has_src_marker`, `selection_jaccard_to_drill`, `predict_filled`).

The judge LLM reads each event slice via `JSON.stringify`, so a parser-truncated value still surfaces to the model — just keep values flat.

---

### Trace 1
Real smoke envelope shape — drill_grade with structured rubric (numeric|mechanism|justification), correct=false, all single-word lowercase rubric chips. Source: `/opt/jarvis/data/private/tutor_events.2026-05-13.jsonl` event `94e7570dfca7464bb677400f88b46987`.

```yaml
events:
  - {event_id: e1, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: false, rubric_chip_text: numeric|mechanism|justification, misconception: OTHER, is_synthetic: true}
labels:
  INV-02: PASS
  INV-08: PASS
```

### Trace 2
Motivating snake_case bug — drill_grade with `uses_rlaplace_or_inverse_cdf_sampler` rubric chip surfaced to the user. INV-02 still PASS (criterion is named), INV-08 FAIL (snake_case as user-facing text).

```yaml
events:
  - {event_id: e2, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: false, rubric_chip_text: uses_rlaplace_or_inverse_cdf_sampler|mechanism|justification, misconception: WRONG_SAMPLER, is_synthetic: false}
labels:
  INV-02: PASS
  INV-08: FAIL
```

### Trace 3
Drill grade with correctness=false but NO named rubric chips — the grader produced a vague verdict without naming which rubric failed. INV-02 FAIL (no named criterion). INV-08 N_A (no chips means no snake_case to flag).

```yaml
events:
  - {event_id: e3, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: false, rubric_chip_text: empty, misconception: OTHER, is_synthetic: false}
labels:
  INV-02: FAIL
  INV-08: N_A
```

### Trace 4
Drill self-paste guard fires correctly — user selected text that matches the drill statement at jaccard>=0.7, sidekick replied with synthetic guard model name. INV-03 PASS.

```yaml
events:
  - {event_id: e4, event_type: sidekick_ask, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, selection_jaccard_to_drill: 0.85, model_resolved: drill-self-paste-guard, is_synthetic: false}
labels:
  INV-03: PASS
```

### Trace 5
Drill self-paste guard MISSED — selection matches drill statement (jaccard 0.82) but a real LLM model handled the reply. INV-03 FAIL.

```yaml
events:
  - {event_id: e5, event_type: sidekick_ask, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, selection_jaccard_to_drill: 0.82, model_resolved: gemma-4-26b-a4b-it-free, is_synthetic: false}
labels:
  INV-03: FAIL
```

### Trace 6
Sidekick reply on corpus-eligible Romanian/PS vocab — retrieved 2 chunks, output includes `(src: _extras/PS/laplace.md)`. INV-05 PASS.

```yaml
events:
  - {event_id: e6, event_type: sidekick_ask, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, retrieved_context_count: 2, has_src_marker: true, selection_kind: corpus-eligible-romanian, is_synthetic: false}
labels:
  INV-05: PASS
```

### Trace 7
Sidekick reply on corpus-eligible vocab WITHOUT a citation marker in the output — retrieved chunks were available but the LLM ignored them. INV-05 FAIL.

```yaml
events:
  - {event_id: e7, event_type: sidekick_ask, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, retrieved_context_count: 2, has_src_marker: false, selection_kind: corpus-eligible-romanian, is_synthetic: false}
labels:
  INV-05: FAIL
```

### Trace 8
Clean predict-before-rcode flow — page_nav opens drill with predict_filled=true, then drill_grade fires. INV-01 PASS.

```yaml
events:
  - {event_id: e8a, event_type: page_nav, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, route: drill_open, predict_filled: true, is_synthetic: false}
  - {event_id: e8b, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: true, rubric_chip_text: numeric|mechanism|justification, is_synthetic: false}
labels:
  INV-01: PASS
```

---

## TODO — Alex to extend to 15-25 traces

Plan target: 15-25 traces × 1-3 labels each → ~30-50 (trace, invariant) pairs.

Remaining gaps to cover:
- **INV-04**: sidekick_ask via inline-chip during an active DRILL card (FAIL) + same flow but card not yet active (PASS).
- **INV-06**: locked card click attempts while DRILL is open — should be no-op.
- **INV-07**: PDF stepper A1→A2 preserving drill state.
- **INV-09 / INV-10**: INFO-only latency observations — sidekick p95 ≥ 8000 ms OR drill_grade p95 ≥ 12000 ms across a session. Multi-event traces required.
- More INV-01 traces with `predict_filled: false` (FAIL case) to balance.
- More INV-08 snake_case cases beyond the motivating example.

Pull tomorrow's envelopes via `scp root@46.247.109.91:/opt/jarvis/data/private/tutor_events.$(date -u +%Y-%m-%d).jsonl tools/.fixtures-cache/` and harvest real session shapes.
