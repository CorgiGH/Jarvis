# Spec B follow-up — live `--provider=claude-cli` probes + grader-smell investigation

**Date:** 2026-05-15T21:10Z (same session as Spec B ship)
**Trigger:** User flagged that 2 of 4 "carry-overs" in the original Spec B wrap were doable inline. This doc captures what got done.

## What was deferred → now done

### Carry-over #1: Live `--provider=claude-cli` opt-in on Surface X + Z

**Surface X probe.** First attempt FAILED with `claude CLI exit 1 — (no stderr)` — a real bug in `tools/lib/claude-cli.mjs`.

Root cause: Surface X's default model is `openai/gpt-oss-120b:free` (an OpenRouter-shaped ID). The CLI provider passed it as `--model openai/gpt-oss-120b:free`, which Claude CLI rejects with exit 1 — and writes the error to **stdout, not stderr**. My provider's error path only inspected `stderrBuf`, so the caller got `(no stderr)` with zero debugging signal.

Fix-forward (commit `7225824`):
- If `model` doesn't match `^claude-`, drop it silently — let the CLI use its own default. `model_resolved` reflects the substitution as `claude-cli@<version>` so the caller sees what actually ran.
- Error path now includes stdout when stderr is empty.
- 2 regression tests added (111 → 113).

**Re-probe Surface X.** Success.
```
$ node tools/surface-x.mjs --provider=claude-cli --from-fixture=docs/standin-findings/golden/2026-05-13-bootstrap-traces.md --threshold=0.80
Calibration: 10/11 match (K=9). Threshold=0.8. Passed: true
Per-pair:
  INV-02: judge=PASS gold=PASS OK
  INV-08: judge=N_A gold=PASS MISS   ← 1 miss
  INV-02: judge=PASS gold=PASS OK
  INV-08: judge=FAIL gold=FAIL OK
  INV-02: judge=FAIL gold=FAIL OK
  INV-08: judge=N_A gold=N_A OK
  INV-03: judge=PASS gold=PASS OK
  INV-03: judge=FAIL gold=FAIL OK
  INV-05: judge=PASS gold=PASS OK
  INV-05: judge=FAIL gold=FAIL OK
  INV-01: judge=PASS gold=PASS OK
```

Calibration **PASSED at 10/11**. Compare to `:free` (`gpt-oss-120b`) baseline of 9/11 — Claude-as-judge edges out by one. Single miss is on Trace 1 INV-08 (the "structured rubric" trace): Claude returned N_A, gold says PASS. Subjective — Trace 1's chips are `numeric|mechanism|justification`, all legitimate criterion names; both verdicts are defensible. Not a calibration regression.

**Surface Z probe.** Success without fix-forward (Z's path through callLlm doesn't hit the bad model name issue).
```
$ node tools/surface-z.mjs --provider=claude-cli
Wrote: docs/standin-findings/DRAFT-Z-auto-1778878998494-2026-05-15T21-03-52-859Z.md
```

DRAFT frontmatter:
```yaml
provenance:
  judge_model_resolved: claude-cli@2.1.142
  provider_name: claude-cli
pages_visited: 2
```

Real findings (Claude judging the live tutor pages):
- `/tutor/` desktop → severity: `readability`. One-liner: "Looks like dev console mockup — readable but text too small and flat, nothing pops as primary action."
- `/tutor/review` desktop → severity: `cosmetic`. One-liner: "Clean but barren — empty state needs more presence and footer/header type bumped a couple px for comfortable reading."

(Side note: CLI auto-updated mid-session 2.1.141 → 2.1.142. The `cli_version` field caught the drift.)

### Carry-over #3: Grader smell

Pulled the 2 v2 envelopes from VPS `/opt/jarvis/data/private/tutor_events.2026-05-15.jsonl`. Both events:
- `status: "parse_error"`
- `model_resolved: null`
- `rubric: {}` (empty)
- `elaboratedFeedback: "LLM grader returned malformed output; please re-attempt or ask sidekick."` (hardcoded server-side fallback string)

**Finding: grader is FLAKY, not BROKEN.** Yesterday's last drill_grade (`2026-05-14T23:43:41.344Z`) was `status: "ok"` with full rubric populated:
```json
"rubric": {
  "uses_rlaplace_or_inverse_cdf_sampler": false,
  "n_equals_10000": false,
  "iterates_over_b_in_half_one_two_four": false,
  "plots_histogram_AND_theoretical_pdf_overlay": false
}
```
…with a real elaborated feedback explaining the missing Laplace pieces.

So the grader's upstream LLM emits non-JSON intermittently — server falls back to the hardcoded error string. The v2 seeder happened to catch 2 parse-errors back-to-back (13.9s + 23.0s latency, both single-attempt — the server doesn't retry before falling back).

Updated `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` (commit `78b37a2`): Trace 9+10 scaffolding now reflects real envelope fields; labels follow mechanically (INV-02 FAIL because rubric is empty; INV-08 N_A because no chips to scan). Decision points reframed — these are parse-error-fallback exemplars, not discriminating invariant fixtures. Re-seed when grader is healthy to get richer fixtures.

Grader-fix options noted in the fixture doc as out-of-band followup:
1. Prompt grader's LLM with "Reply JSON only, no markdown fences" + server-side json-fence-strip pass before parse.
2. Switch grader chain to a non-`:free` model.
3. N=2 retry on parse_error before falling back to hardcoded error.

## What was NOT done

- **Carry-over #2 (X-fixture promotion to numbered traces):** still BRIDGE-marked "Alex's manual judgment." Trace 9+10 scaffolding now has real fields + mechanically-derived labels; Alex still owns the call to (a) accept the FAIL/N_A labels or (b) re-seed when grader is healthy and use those instead.
- **Carry-over #4 (rotate `OPENROUTER_API_KEY_STANDIN`):** requires Alex's OpenRouter account access. Genuine blocker.

## State after this followup

- HEAD: `78b37a2` on `main` (pre-push; will push at end of session along with `7225824`).
- Tests: **113/113** green via `npm run test:tools --prefix tools`.
- CLI provider live-verified on its 2 target consumers (X + Z) and via direct smoke probe.
- Grader smell: classified (flaky parse-error fallback, intermittent), with fix candidates noted.
