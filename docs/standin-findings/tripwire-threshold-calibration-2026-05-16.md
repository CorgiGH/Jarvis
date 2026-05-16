# Y Tripwire Threshold Calibration Analysis (2026-05-16)

Triggered after corpus grew to n=5 authentic-naive (commit `91a3793` Y
burns) + n=3 adversarial-leaked (commit `c78a84b` test fixtures).

## Thresholds under review

`tools/surface-y-tripwire.mjs` (DEFAULT_THRESHOLDS):

| Knob | Default | Path |
|------|---------|------|
| `min_steps_for_zero_confusion_flag` | 6 | A |
| `submit_step_index_max_for_flawless_flag` | 3 | B |

## Authentic-naive corpus inspection (n=5)

Programmatically replayed each transcript through `flagSuspectRun` with
the matching `findings_count`:

| Run | Steps | findings | ask_sidekick | confusion | suspect |
|-----|-------|----------|--------------|-----------|---------|
| 2026-05-15T10:33 (gpt-oss-120b prior) | 4 | 3 | 0 | 0 | false |
| 2026-05-16T20:29 burn-1 | 4 | 3 | 3 | 3 | false |
| 2026-05-16T20:30 burn-2 | 4 | 3 | 3 | 3 | false |
| 2026-05-16T20:32 burn-3 | 4 | 3 | 0 | 0 | false |
| (1 earlier 2026-05-14 run pre-tripwire — shape comparable) | ~4 | n/a | n/a | n/a | n/a |

All terminate at step 4 via the 3-identical-action loop-detect in
`tools/surface-y.mjs:258-268`. None submit. None reach Path A's
`min_steps: 6` floor; Path B's submit gate cannot trigger without a
submit.

burn-3 is the structurally-most-interesting data point: zero verbal
confusion AND zero ask_sidekick (same shape as the adversarial fixtures),
but findings_count=3 because the persona's observations encoded
UX-friction ("Need to fill the code field before submitting") which the
findings filter counts as positive signal. The AND-gate correctly clears
it.

## Adversarial-leaked corpus inspection (n=3)

All hand-authored fixtures from `tools/surface-y-tripwire.test.mjs`:

| Fixture | Steps | submit idx | ask_sidekick | confusion | Path fired |
|---------|-------|------------|--------------|-----------|------------|
| ADVERSARIAL_LEAKED_1 | 2 | 1 | 0 | 0 | B only |
| ADVERSARIAL_LEAKED_2 | 7 | (none) | 0 | 0 | A only |
| ADVERSARIAL_LEAKED_3 | 6 | 3 | 0 | 0 | A + B |

All produce `suspect=true` under current thresholds. Tests pass green.

## Verdict: HOLD thresholds

Justification per knob:

### `min_steps_for_zero_confusion_flag: 6`

- AL_2 fires at 7 steps; AL_3 fires at 6 (boundary).
- Real corpus tops out at 4 — no false-positive risk from real naive runs.
- Lowering to 5 would not affect any current real or adversarial fixture.
- Lowering to 4 would risk false-positive on burn-3 (4 steps, zero
  confusion, zero ask_sidekick) — but burn-3 has findings=3 so AND-gate
  clears it. Lower bound for safe lowering is therefore ≥4 conditional
  on the AND-gate; no concrete signal motivates a move.
- **Hold at 6.** Conservative on false positives; correctly flags both
  adversarial Path-A fixtures (AL_2 boundary +1, AL_3 boundary exact).

### `submit_step_index_max_for_flawless_flag: 3`

- AL_1 fires at submit_idx=1; AL_3 fires at submit_idx=3 (boundary exact).
- No real corpus run submits — threshold cannot generate a false positive
  on current real data.
- Tightening to 2 would un-fire AL_3 Path B (but AL_3 still flags via
  Path A — redundancy preserved).
- Loosening to 5 would have no observable effect on either real or
  adversarial fixtures.
- **Hold at 3.** Boundary exact on AL_3 is the strongest signal we have;
  moving in either direction loses fidelity for no observed gain.

## Calibration-corpus shape gap (flagged)

Real authentic-naive runs all terminate at step 4 because the persona
loops the same `(action, target)` 3× and the loop-detector in
`surface-y.mjs:258-268` ends the session. Consequence: **Path A's
`min_steps: 6` boundary cannot be validated against real naive corpus**
because no real naive run currently reaches 6 steps.

This is a CORPUS-shape limitation, not a tripwire-threshold problem.
Resolutions, in order of cost:

1. **Cheap (do nothing):** accept the adversarial fixtures (AL_2 at 7
   steps) as the only test of Path A's step-count boundary. The
   adversarial corpus is hand-authored to span the boundary cleanly.
2. **Medium:** raise loop-detect threshold in `surface-y.mjs` from 3 to
   5 — allow the naive persona to wander further before getting cut off.
   Risk: longer/more expensive runs.
3. **Expensive:** swap to a different persona-model with more varied
   action selection so it doesn't fall into the same-action loop as
   readily.

No action recommended this session. Flag the gap for future calibration
work if/when authentic-naive Path A false positives ever surface in
production runs.

## Confidence band

After this analysis, the runtime emits `tripwire_confidence_band:
thin_corpus_n5` (per `tripwireCorpusN = 5` at `surface-y.mjs:338`,
commit `91a3793`). "Thin" remains an honest descriptor — 5 naive runs +
3 hand-authored adversarial fixtures is a starting point, not a
production calibration.

## Related artifacts

- `tools/surface-y-tripwire.mjs` — `flagSuspectRun` + `DEFAULT_THRESHOLDS`.
- `tools/surface-y-tripwire.test.mjs` — 11 tests, includes the 3
  adversarial fixtures.
- `tools/surface-y.mjs:338` — `tripwireCorpusN = 5` constant.
- `.claude/council-cache/council-1778881175-y-tripwire-design.md` — first
  council that established Path A + Path B + AND-gate semantics.
- `.claude/council-cache/council-1778960917-y-shape-capture.md` — second
  council on whether Playwright MCP shape-capture for live runs (REJECTED).
