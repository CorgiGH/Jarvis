---
date: 2026-05-16
git_head: post-b6f691e
context: Phase C re-seed + Trace 9+10 promotion calibration verify
---

# Calibration verify — Trace 9+10 real-rubric labels

## Result

`node tools/surface-x.mjs --from-fixture=docs/standin-findings/golden/2026-05-13-bootstrap-traces.md --calibrate --threshold=0.80`

**13/15 PASS (K=12, threshold 0.80) → PASSED.** Better than the pre-Phase-C baseline of 9/11 (81.8%) — new score is 86.7%.

## Per-pair

| Trace | Inv | Judge | Gold | Match |
|-------|-----|-------|------|-------|
| 1 | INV-02 | PASS | PASS | OK |
| 1 | INV-08 | PASS | PASS | OK |
| 2 | INV-02 | PASS | PASS | OK |
| 2 | INV-08 | FAIL | FAIL | OK |
| 3 | INV-02 | FAIL | FAIL | OK |
| 3 | INV-08 | PASS | N_A | MISS (carried from baseline) |
| 4 | INV-03 | PASS | PASS | OK |
| 5 | INV-03 | FAIL | FAIL | OK |
| 6 | INV-05 | PASS | PASS | OK |
| 7 | INV-05 | N_A | FAIL | MISS (carried from baseline) |
| 8 | INV-01 | PASS | PASS | OK |
| **9** | **INV-02** | **N_A** | **N_A** | **OK** |
| **9** | **INV-08** | **FAIL** | **FAIL** | **OK** |
| **10** | **INV-02** | **PASS** | **PASS** | **OK** |
| **10** | **INV-08** | **FAIL** | **FAIL** | **OK** |

## Promotion outcome

Trace 9 + Trace 10 (4 new label pairs) ALL agree with judge. Mechanically-derived labels (Trace 9 = correct/score 1.0 / INV-02 N_A + INV-08 FAIL snake_case; Trace 10 = wrong/score 0.25 / INV-02 PASS chips-named + INV-08 FAIL snake_case) are stable under the `:free`-band judge model.

## Bug found + fixed during verify

`parseFixture` in `tools/surface-x.mjs` silently dropped any label line carrying a trailing YAML comment (`INV-XX: VALUE  # explanation`). Trace 1-8 had bare labels so this defect was latent. Trace 9+10 (added in `b6f691e`) used trailing comments to encode the labels' real-rubric reasoning — those 4 pairs never reached the judge, so the b6f691e promotion was unverified against the calibration suite.

Fix: pre-strip `\s+#.*$` per label line before the existing regex. Added regression test `parseFixture: label lines with trailing YAML comments still parse` covering the failure mode. Pre-fix calibration: 9/11 (T9+T10 dropped). Post-fix: 13/15.

## Carried-over MISSes (NOT regressions)

The 2 MISSes (T3 INV-08, T7 INV-05) were already present in the pre-Phase-C baseline (`docs/standin-findings/spec-b-followup-2026-05-15.md`). They reflect judge-vs-gold disagreement on edge-case labels, not Phase A grader behavior. Out of scope for this verify.
