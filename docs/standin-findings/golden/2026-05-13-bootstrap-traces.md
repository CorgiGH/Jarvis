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

---

## CANDIDATE traces — Spec B scaffolding (Alex to label/promote)

> **Status:** unlabeled scaffolding. This session (Claude, 2026-05-15) did NOT curate or label these.
> Source: 2 synthetic `drill_grade` events produced by the Spec A v2 seeder live acceptance run.
> Plan: Alex inspects the real envelope shapes (via SCP below), edits the YAML to fill in real
> rubric chips / misconception codes, decides PASS/FAIL/N_A per applicable invariant, then promotes
> the trace from this section up into the numbered set (Trace 9, Trace 10).

**Provenance:**
- VPS path: `/opt/jarvis/data/private/tutor_events.2026-05-15.jsonl`
- Session: `607dbdbd3753`
- Task: `01KR6K07T6PATPRR5KH1JXYF8E` (PS Tema A — Laplace sampling)
- Timestamps: `2026-05-15T01:52:34Z` (known-good attempt — VGAM `rlaplace(1000)` solution) and `2026-05-15T01:52:57Z` (known-bad attempt — wrong sampler)
- Header: `X-Standin-Run: 1` → both events carry `is_synthetic: true`
- **Grader smell — INVESTIGATED 2026-05-15T21Z:** the v2 live run caught **both** events in the grader's `parse_error` fallback path. Real envelopes from VPS (`/opt/jarvis/data/private/tutor_events.2026-05-15.jsonl`):
  - Event 1: `status: "parse_error"`, `model_resolved: null`, `rubric: {}`, `elaboratedFeedback: "LLM grader returned malformed output; please re-attempt or ask sidekick."`, `misconception: "OTHER"`, latency 13864ms.
  - Event 2: identical shape, latency 23040ms.
  - The fallback string + empty rubric is hardcoded server-side when the grader's upstream LLM emits non-JSON. **The grader is FLAKY, not broken** — yesterday's last event (2026-05-14T23:43Z) was `status: "ok"` with full rubric populated (`uses_rlaplace_or_inverse_cdf_sampler: false`, `n_equals_10000: false`, etc.). The v2 seeder happened to catch 2 parse-error fallbacks back-to-back. Both events still carry `is_synthetic: true` and reach the log — they're useful as **parse-error-fallback fixtures** (proving the fallback path exists + has consistent shape) but NOT as positive/negative invariant fixtures (empty rubric ⇒ INV-02 FAIL by default; not a discriminating signal).
  - Followup for Alex: investigate which model the grader chain uses + why parse fails on long-but-valid R answers. Possibly a `:free` model emits markdown fences (```) around the JSON. Add a json-strip fallback to the server before parse?

**Pull the real envelopes:**
```bash
scp root@46.247.109.91:/opt/jarvis/data/private/tutor_events.2026-05-15.jsonl tools/.fixtures-cache/
# Then to extract just these 2 events:
node -e "
  const { readEvents, filterEvents } = await import('./tools/lib/event-log-reader.mjs');
  const all = await readEvents({ dir: 'tools/.fixtures-cache' });
  const filtered = filterEvents(all, {
    task_id: '01KR6K07T6PATPRR5KH1JXYF8E',
    session_id: '607dbdbd3753',
    event_type: 'drill_grade',
    include_synthetic: true,
  });
  console.log(JSON.stringify(filtered, null, 2));
"
```

### Trace 9 — synthetic known-good VGAM rlaplace (real-rubric, post-Phase-A re-seed)

```yaml
# REAL FIELDS pulled from VPS event b4f54170c3d64b4fbb0b8250dce626fc.
# Re-seeded 2026-05-16T13:40:57Z after Phase A grader hardening shipped (HEAD 8f7f34c).
# Grader returned status: ok with full rubric — labels are now real-rubric-grounded.
events:
  - {event_id: b4f54170c3d64b4fbb0b8250dce626fc, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: true, score: 1.0, rubric: {uses_rlaplace_or_inverse_cdf_sampler: true, n_equals_10000: true, iterates_over_b_in_half_one_two_four: true, plots_histogram_AND_theoretical_pdf_overlay: true}, misconception: null, is_synthetic: true, status: ok, model_resolved: z-ai/glm-4.5-air:free}
labels:
  INV-02: N_A   # correct=true → invariant only applies when marked wrong.
  INV-08: FAIL  # all 4 rubric chips are snake_case (uses_rlaplace_or_inverse_cdf_sampler etc.).
```

### Trace 10 — synthetic known-bad rnorm (real-rubric, post-Phase-A re-seed)

```yaml
# REAL FIELDS pulled from VPS event 07ef6e481ffd43b997d71539f1528701.
# Re-seeded 2026-05-16T13:41:18Z after Phase A grader hardening.
# correct: false, 3 of 4 chips fail (only n_equals_10000 passes) → INV-02 PASS (criteria named).
events:
  - {event_id: 07ef6e481ffd43b997d71539f1528701, event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, correct: false, score: 0.25, rubric: {uses_rlaplace_or_inverse_cdf_sampler: false, n_equals_10000: true, iterates_over_b_in_half_one_two_four: false, plots_histogram_AND_theoretical_pdf_overlay: false}, misconception: OTHER, is_synthetic: true, status: ok, model_resolved: z-ai/glm-4.5-air:free}
labels:
  INV-02: PASS  # 3 failing rubric chips are named explicitly (not a vague "wrong" verdict).
  INV-08: FAIL  # same snake_case chip names as Trace 9.
```

### Promotion notes (2026-05-16)

1. **Trace 9+10 are real-rubric-grounded** (re-seeded after Phase A grader hardening — council 1778881174 verdict shipped at HEAD `8f7f34c`). Both events landed `status: "ok"` with populated rubric chips. Labels mechanically derived from real rubric content — Trace 9 is INV-02-N_A / INV-08-FAIL (correct, snake_case chips); Trace 10 is INV-02-PASS / INV-08-FAIL (3 failing chips named, snake_case chips).
2. **The grader IS still flaky** but Phase A's three layers (raw output capture, response_format=json_object, maxTokens bump, balanced-brace regex fallback) successfully drove this re-seed through `status: ok` on first attempt. Both events latency 20-30s — within normal `:free`-model range.
3. **`is_synthetic: true` filter impact:** Surface X's `filterEvents` defaults `include_synthetic: false`. The judge needs `--include-synthetic` to see these traces. Precedent set by existing Trace 1 (is_synthetic: true) — golden fixture mixes synthetic with real.
4. **Pre-Phase-A parse-error fallback traces** (events `95acd374ff0d4a3384d798f80ac3c6d8` + `31d5f308c1e9491bba215c04a0301fb7` from 2026-05-15) are kept in the VPS log as observability witnesses. NOT promoted as golden fixtures because empty-rubric events are now consumer-filtered out of calibration by Surface X (Phase A.4 `status_in=["ok"]` default).
