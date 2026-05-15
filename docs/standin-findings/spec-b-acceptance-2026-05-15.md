# Spec B acceptance evidence — 2026-05-15

**Spec:** `docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md` (commit `4832be2`)
**Plan:** `docs/superpowers/plans/2026-05-15-surface-claude-cli-provider.md` (commit `66be2b2`)
**Council triad:** `council-1778785261`, `council-1778787266`, `council-1778839098-y-competence-band`
**Run time:** 2026-05-15T10:33Z

Per the load-bearing `feature-shipped` rule in `C:\Users\User\.claude\CLAUDE.md`: bundle hash + tests green is NOT proof of feature shipped — the user-facing surface (or CLI command) must run live. This doc captures the three live gates.

---

## Gate 1: Full test suite green

Command: `npm run test:tools --prefix tools`

```
ℹ tests 111
ℹ pass 111
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
```

Pre-Spec-B baseline was 84 tests (per BRIDGE 2026-05-15T02:03 entry from the prior session). Spec B added 27 tests:
- `lib/claude-cli.test.mjs`: +5 (Task 1 subprocess provider)
- `lib/provenance.test.mjs`: +2 (Task 2 provider_name field)
- `surface-x.test.mjs`: +3 (Task 3 `--provider` flag)
- `surface-z.test.mjs`: +3 (Task 4 `--provider` flag)
- `surface-y-tripwire.test.mjs`: +5 (Task 5 tripwire postprocessor)
- `surface-y.test.mjs`: +2 (Task 6 tripwire embedding) + +7 (Task 7 fallback list) = +9

84 + 27 = 111. Matches.

## Gate 2: Live Claude CLI smoke probe

Command:
```bash
node -e "import('./tools/lib/claude-cli.mjs').then(async m => {
  const r = await m.callLlm({
    systemPrompt: 'You are a calculator. Reply with only digits.',
    userPrompt: 'What is 2+2?'
  });
  console.log('text:', r.text);
  console.log('cli_version:', r.cli_version);
  console.log('model_resolved:', r.model_resolved);
  console.log('latency_ms:', r.latency_ms);
})"
```

Result:
```
text: "4\n"
cli_version: 2.1.141
model_resolved: claude-cli@2.1.141
latency_ms: 6418
prompt_sha256: fe064149e9294b3f...
```

✓ Subprocess wrapper works end-to-end. Stdin-piped prompt + stdout text capture + version probe via `claude --version` all green. `tokens_in`/`tokens_out` return `null` (CLI does not emit usage info on `--print`; this is documented per spec).

## Gate 3: Live Surface Y burn — pinned `:free` + tripwire active

Command:
```bash
JARVIS_AUTH_COOKIE=$(cat tools/AUTH_TOKEN.txt) \
OPENROUTER_API_KEY_STANDIN=<from .env> \
node tools/surface-y.mjs \
  --task=01KR6K07T6PATPRR5KH1JXYF8E \
  --schema=docs/standin-findings/schemas/PS-Tema-A.yaml \
  --max-calls=8 \
  --session=spec-b-acceptance-1778841118
```

Output file: `docs/standin-findings/DRAFT-Y-01KR6K07T6PATPRR5KH1JXYF8E-2026-05-15T10-33-33-020Z.md` (gitignored per the standin-findings DRAFT pattern).

Frontmatter:
```yaml
surface: Y
session_id: spec-b-acceptance-1778841118
task_id: 01KR6K07T6PATPRR5KH1JXYF8E
provenance:
  git_head: b3823a0
  bundle_hash: B-Xy35Ve
  judge_model_resolved: openai/gpt-oss-120b:free
model_resolved: openai/gpt-oss-120b:free
calls_used: 4
duration_min: 1.6
gate_violations: 0
tripwire_status: clean
```

Body contains:
```
## Behavioral-competence tripwire
**Status:** clean

**Signals:**
- ask_sidekick_count: 0
- confusion_step_count: 0

**Thresholds:**
- min_steps_for_zero_confusion_flag: 6
- submit_step_index_max_for_flawless_flag: 3

**Rationale:**
- (none — run is clean)
```

✓ `tripwire_status` field present in frontmatter (Task 6 wiring).
✓ `## Behavioral-competence tripwire` section present in body (Task 6 wiring).
✓ Pinned model `openai/gpt-oss-120b:free` resolved (Task 7 default unchanged).
✓ Tripwire correctly returns `clean` — transcript is only 4 steps (< Path A's 6-step floor) and never reached `submit` (Path B requires submit ≤ step 3). Both suspect paths correctly inactive.
✓ Schema-gate `gate_violations: 0` — no concept leakage either.

### Observed Surface Y findings (informational, not Spec B gates)

The gpt-oss-120b persona again hit the known capability floor on terminal-action selection — typed R, said "ready to submit", never emitted the `submit` token, hit the 3-identical-action loop detector at step 4. This is the SAME behavior observed in prior sessions and is the friction-discovery signal Y exists to produce. NOT a regression.

The `## Discovered unknown-unknowns` section emitted 3 entries (steps 1-3). The transcript shows the model partially completed the R code but stalled before invoking `rlaplace` or any specific Laplace-sampling primitive.

## Conclusion

All 3 gates pass. Spec B is live-verified. Per the `feature-shipped` rule the work is shippable:
- Tests: 111/111 green.
- CLI provider: live invocation succeeds end-to-end.
- Y naivety-hardening: pinned default + tripwire embedded + DRAFT emission confirmed against the live VPS, no regressions.

The Spec B-built CLI provider is built but unexercised on X/Z yet (`--provider=claude-cli` is opt-in; the acceptance gate per plan covered the smoke probe in isolation). First live X+CLI / Z+CLI invocation is deferred to next session per the spec's success-metric note.

## Carry-overs for next session

- **CLI provider's first live X/Z opt-in** — run `node tools/surface-x.mjs --provider=claude-cli ...` against a real task and inspect the DRAFT for `provider_name: claude-cli`. Same for Z.
- **Behavioral tripwire corpus growth** — this session's clean run is added as a positive sample (n=2 authentic-naive after gpt-oss-120b's original run). Tripwire thresholds may want tuning after 2-3 more Y runs.
- **X-fixture promotion** — `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` has unlabeled candidate Traces 9+10 from Spec A v2 events; Alex's manual call to label/promote (deferred per BRIDGE — Alex's judgment).
- **Grader smell from Spec A v2** still open — both synthetic drill_grade events graded `correct=false score=0`, including the known-good `rlaplace` solution. The candidate fixture scaffolding above flags this for Alex's review.
