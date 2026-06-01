# E3 generation pipeline — real-model proof + reliability findings (2026-05-30)

**Context:** active-constraint #1 gates real content "until the generation pipeline is PROVEN on a REAL model." This session proved it on claude-max via the local relay, and — more valuably — surfaced the pipeline's real-model **reliability characteristics**, which are exactly what the gate exists to catch before real content flows.

## What was proven

- **Relay-critic verified** (E3 plan Task 0 Step 2): the dev box runs the `claude` CLI, so `tools/pc-relay-server.py` was started **locally** on `127.0.0.1:9999` (wraps `claude --print`). `/healthz` + `/complete` → 200. No separate home PC needed.
- **Generation pipeline works on a real model.** Test: `src/test/kotlin/jarvis/tutor/E3RealRelayProofTest.kt` (env-gated on `JARVIS_RELAY_URL`; **skips in CI**, never hits the network in a normal run). Because Llama-free is 429'd account-wide, **both** generator and critic ran through the relay (claude-max).
  - **CORE** — `DrillGenerator.generate(...)` authored a correct, grounded, safeguard-passing computational drill: *"A binary tree has height h = 5 … maximum number of nodes?"* → canonical **63** (2⁶−1). Passed answer-leak + self-solve reconcile + cross-family critic.
  - **E2E** — through the real `POST /api/v1/task/{id}/generate-drills` route → ≥1 accepted + persisted **server-canonical** `Problem` → `POST /api/v1/drill/grade` → `KcMastery(kcId=pa-kc-relayproof, ewmaScore=1.0, observations=1)`. (E2E uses a **deterministic** grade so the mastery loop is stable; the grade-DECISION on the relay model is the F4 finding below.)

**Verdict: the pipeline is mechanically correct on a real model.** It produces correct, grounded drills, the safeguards fire, drills persist server-canonically, grading moves mastery. But it has a real **false-reject rate** and grader-consistency gap (below) that should be hardened before / alongside real content.

## Reliability findings (the valuable part)

Observed across ~6 live runs at `count=1..3`, computational shape, claude-max + haiku via relay:

### F1 — self-solve match brittleness (HIGH; affects production generator directly)
The self-solve reconcile asks the model to *"Solve and reply with ONLY the final answer"* then does `GradeScoring.answerMatches(canonical, reply)` (exact/numeric normalize). Real models routinely wrap the answer in prose/markdown, so a **correct** drill is **false-rejected**:
- `self-solve mismatch (63 vs "Height 5, root height 0. Max nodes = 2^6…")`
- `self-solve mismatch (31 vs "**31**\n\nFormula: 2^(h+1) − 1 = 2^5 − 1 =…")`

Hit on BOTH the default model and (more often) haiku. **The intended production generator is free Llama — chattier than claude — so this would false-reject a high fraction of valid drills.** This is the single biggest production-readiness risk.
**Fix candidate:** extract the answer from the self-solve reply before matching (strip markdown; take the first number/normalized token; or reuse the canonical-answer extraction path), instead of exact-matching the whole reply. Needs its own TDD against `DrillGenerator`.

### F2 — critic JSON parse-failure (MEDIUM)
The cross-family critic occasionally returns non-JSON / unparseable output → `critic parse failure (claude-max-relay)` → reject. `DrillGenerator.parseCritic` already fail-closes (good), but it's another contributor to the false-reject rate.

### F3 — critic leak false-positive (MEDIUM)
On a grounded computational drill, the critic returned `conf=1.0, grounded=true, leak=true, solvable=true` → rejected on the leak flag. The drill did **not** mechanically leak (no canonical answer in the stem); the critic judged the formula-in-stem as "leaking." Reject-don't-ship is the correct posture, but the leak judgment is noisy.

### F4 — real grader correct-vs-rubric inconsistency (MEDIUM; E1's domain, not generation)
The relay grader emitted `rubric:{numeric:true,mechanism:true,justification:true}` + prose *"Correct. All rubric dimensions satisfied"* but stamped `correct:false`. The E1 deterministic trustworthy-grader **correctly detected the inconsistency** (`llm.correct != correctFromRubric(rubric)`) → `confidence:LOW, recorded:false` → mastery NOT recorded. Working as designed — but it means a **real** grade won't reliably record mastery (a real-grader mastery assertion is inherently flaky). Separately, a **bare** correct answer also defers, because a multi-dimension rubric (mechanism/justification) isn't satisfied by an answer with no shown work.

## Net implication for active-constraint #1

The pipe is **proven correct on a real model**, so the "garbage through an unproven pipe" risk is retired. What remains before real content is a **reliability/efficiency** gap (F1 especially), not a correctness gap. Hardening F1 (and optionally F2/F3) makes generation viable on the chatty free generator; F4 is an E1 grader-reliability item, separate from generation.

## Operational note — relay cold-start

`claude --print` spawned in the project dir cold-starts **40–60s+ (sometimes hangs)** because it loads the heavy project `.claude/` (SessionStart hooks) + interactive-auth MCP servers. Fix: spawn from a **clean cwd** with `--strict-mcp-config --mcp-config '{"mcpServers":{}}' --setting-sources user --no-session-persistence` → **~8s, reliable**. Added **additive** `JARVIS_CLAUDE_ARGS` + `JARVIS_CLAUDE_CWD` env to `tools/pc-relay-server.py` (empty = prior behavior; also lets the VPS relay pin flags). Note: claude-max throttles under rapid headless calls — one `/complete` hung past 120s after ~15 rapid calls; `JARVIS_RELAY_READ_S` headroom + `count>1` redundancy absorb it.
