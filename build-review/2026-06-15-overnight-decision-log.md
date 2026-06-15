# Overnight autonomous session — decision log (2026-06-15 → 16)

PM (Alex) asleep. Claude works the **oracle-first foundation** of the locked master plan
(`docs/superpowers/plans/2026-06-15-end-to-end-master-plan-to-100-v2.md`). Every decision (mine + council's)
is appended here with grounding. Machine gates carry ship authority — NOT my "verified".

## Standing parameters (Alex-ratified, this session start)

- **Git:** commit to LOCAL `main` by explicit path only (reversible). **NO push to origin. NO VPS deploy.** Never `git add -A` / `git clean` on main.
- **VPS:** "i think so, test it" → reachability self-verified below. May use for testing only; **never deploy to the live site**.
- **Council:** local **council-lite** (adversarial Claude subagents) — respects no-paid-APIs. Full multi-provider council NOT used.
- **Scope:** Phase 0 (gate substrate) + A0 (commit+green viz WIP) + Phase A (generate-from-algorithm figures) + **attempt lesson generation** (folded into the SPIKE: measure relay-model capability vs the oracles; author depth/CHECK banks for the ~7 real KCs). No site-visible breadth shipped (oracle-first/deploy-last; "don't update the site").
- **Discipline:** suites FOREGROUND from `tutor-web` cwd (frontend) / repo root (gradle), explicit timeout, stream-to-log, NEVER `| tail`/`Select-Object -Last`; never 2 vitest at once. I re-run the gate myself before any commit — a subagent "green" is inadmissible.

## Live-state probes (session start)

- **VPS SSH = REACHABLE.** `root@46.247.109.91` → host `panel`, up 31 days, idle load. Usable for testing; **never deploy**.
- **Relay `:9999` LISTENING** (pid 13488) — auth not yet probed. Dev Ktor backend `:8080` (pid 45884, SANDBOX db) + vite `:5173` (pid 4464) still up from SESSION-73.
- Git: `main @ 759eeb4`, 5 ahead of origin, 0 behind (matches BRIDGE-HEAD).

## Phase-0 recon (workflow w8upm0b8f)

All 8 foundation defects still LIVE (no code ran since SESSION-73). Board in workflow output; recommended fix order 1→4→3 then registry #6, #2 parallel, #8 register-as-debt, #5 PM decision.

## PROCESS NOTE — PM-delegation correction

I started **hand-editing** the 3 trivial fixes myself (fail-open flip, relay-retry ×2, StateCache isolation ×2). Alex stopped me: *"you are PM not a worker."* Correct. Switched to full workflow-delegation: workflows build + run gates foreground→logs; I review machine evidence + gate commits; council for decisions. The 3 applied edits are KEPT (correct, reverting = theater) and **gated** by workflow `w6po39rko`, not redone by hand.

## Decisions

| # | Decision | Made by | Grounding | Status |
|---|----------|---------|-----------|--------|
| D0 | Overnight scope + parameters above | Alex (4-question audit) | this session | LOCKED |
| D1 | VPS reachable → may use for test, never deploy | probe | SSH OK to `panel` | LOCKED |
| D2 | Keep the 3 hand-applied fixes; gate (don't redo) them via workflow | Claude (PM) | reverting correct work = wasted tokens | LOCKED |
| D3 | **#1 impeccable blocking-flip REVERTED** — premature | gate evidence | local `detect`=EXIT 1, filter kept **25 of 37** real findings in committed prod components (ChatPane/DrillStack/Sidekick/beat comps). Flip would red CI. Becomes a **calibration work-item** (triage 25 → fix or de-scope antipattern), not a one-liner. | test.yml back to fail-open |
| D4 | **recon MISDIAGNOSED the StateCache flake** | my own re-run | recon called it a test-isolation leak; the REAL ~1-in-2 red is `IntegrationHarnessTest.stateCacheConcurrentPersistNeverTearsJson:160` — `StateCache.persist` does a **non-atomic** `Files.writeString(TRUNCATE_EXISTING)`, so K concurrent writers interleave → corrupt/empty file → `assertNotNull` fails. My loop: **2/3 red** (agent falsely claimed 4/4 — inadmissible-green lesson, live again). Fix = **atomic temp+rename** (production change to StateCache.kt). The test-isolation edit is kept as hygiene but is NOT the flake fix. | fix delegated |

### COMMITTED (local main, NOT pushed — now 10 ahead of origin)
- `e527386` fix(statecache): atomic persist + per-test isolation (#4) — self-verified 10/10
- `208669a` fix(verify): wrap relay legs in RetryingLlm (#3)
- `c066bef` test(backup): schema-hash parity golden-vector (#2)
- `7e6def7` test(viz): seeded-bad self-tests for assertNoClip legs 1+3 (#7)
- `85638dc` docs(overnight): decision log
All by explicit path; working-tree dirt (door/demo/tutor-dist) untouched.

### Remaining Phase-0
- **#6 gate matrix/coverage registry** = the Phase-0 DELIVERABLE → building now (workflow). Registers #8 auth as RED-unmapped security-debt + impeccable as fail-open-debt.
- **#1 impeccable calibration** = DEFERRED work-item: triage the 25 findings (real design defects vs over-broad antipattern) → then fix-or-narrow → then flip. Needs a triage agent + likely a decision.
- **D-B #5 baselines** = self-decided: keep documented fail-open tonight (zero-risk; Linux pixel-enforce = low ROI, needs Linux host + dual-baseline upkeep). Flagged for Alex; reopen if he wants CI pixel enforcement.

### Gate status (Phase-0 fixes, this run)
- **#2 schema-parity** (SchemaHashParityTest.kt + test_schema_hash_parity.py): GREEN both runtimes (pending my final consolidated re-gate).
- **#3 relay-retry** (VerifyContentCli + TrustRoutes): GREEN (TrustRoutesTest + verify.* pass in every run; only the concurrent-persist test reds).
- **#4 StateCache:** test-isolation done; **atomic-persist fix IN PROGRESS** (the real flake).
- **#7 no-clip leg seeds** (seeded-clip-legs.spec.ts): GREEN (leg1+leg3 RED+clean, 4/4).
- **Deferred (NOT Phase-0 fixes / not mine):** matrix-grid `mg-root`/`mgg-root` strict-mode double-mount on /tutor/viz-demo (uncommitted viz WIP → A0/Phase-A scope); drill-4b legibility self-test resolved-instead-of-rejected locally (pre-existing, committed, ?env/chromium-contrast — re-check in CI).

## Phase-0 deliverable #6 — gate-coverage registry: DONE + self-verified + CI-wired
- Files: `docs/superpowers/plans/2026-06-15-gate-coverage-registry.{json,md}` + `tools/gate-registry-check.mjs` + `tools/gate-registry-check.test.mjs`; wired into CI as a new `gate-registry` job in `.github/workflows/test.yml`.
- **86 clauses** registered (every spec INV-* + plan net-news + named non-INV gates): **18 LIVE-GREEN / 62 PLANNED / 6 RED-UNMAPPED**. The 6 accepted-debt ids (printed every run, never laundered green): auth-surface-security-debt, Phase0-Impeccable-blocking, Phase0-LinuxVisualBaselines, INV-9.5, Phase0-StandingRedTeam, a11y-keyboard-aria-live-gate.
- **Self-verified by me:** `node tools/gate-registry-check.mjs` → EXIT 0 ("zero UNMAPPED"); `node --test ...test.mjs` → EXIT 0. Adversarial audit verdict = **PRISTINE** (56 spec INVs all present, zero estimators laundered green, impeccable not falsely green, auth registered-not-absorbed).
- Checker reds on: UNMAPPED (missing status/tag), PROOF-clause-backed-only-by-estimator, RED-UNMAPPED-not-in-allow-list, duplicate id, counts drift, orphan debt.

### D-CENSUS — committed-KC count CORRECTED: 6, not 7 (verified by me)
`git ls-files content/ALO/**` → only `.gitkeep` tracked; `alo-kc-gauss-elim.yaml` exists on disk but is **UNTRACKED WIP**. So committed HEAD = **6 real KCs (PA pa-kc-001..006 only)**; the plan/MEMORY "7 incl. ALO gauss" overcounts the COMMITTED state by 1. Impact: Phase-B EARLY depth-authoring scope (the ALO KC isn't a committed input) + the content census. **Left untracked** — committing unverified content is gated Phase-B/F work, not tonight's scope.

### Phase-0 status
Fixes #2/#3/#4/#7 committed+verified; #6 registry done+wired+verified. **Remaining Phase-0 = #1 impeccable calibration only** (deferred work-item: triage the 25 findings → fix-or-narrow → flip). Phase-0 substrate is otherwise complete.

## Resolved decisions

### D-A — Phase-A figure engine = **B2 (TS-oracle-as-generator), gate REDESIGNED to 3 field-scoped legs** (council-1781489738, verdict FLAWED→fixed, 8/10)
Council: 1 REJECT, 4 CONDITIONAL. B2's mechanism (golden-file codegen from the instrumented TS oracle, frozen §NEW-V `data_json=keyframes` unchanged, families+scrubber unchanged) is industry-standard (VisuAlgo/Galles/Manim) and chosen over A (Kotlin backend — two-impl drift, zero code) and C (hybrid — frozen-sig churn). **My naive "data_json===oracle(seed)" gate was FLAWED on two counts the council killed:**
1. **Tautology (CRITICAL):** same oracle generating AND checking proves only that codegen ran, NOT that the algorithm is correct — mergesort-never-sorts would pass; `final==sorted` alone is necessary-not-sufficient (wrong method, right result).
2. **Callout/prose (HIGH):** byte-equality can't cover the generated NL `callout` field → certifies only half; snapshot-rot reds CI on first pedagogy edit.

**LOCKED contract (D-A) — the Phase-A figure gate = THREE field-scoped legs, never one equality:**
- **Leg 1 STATE re-derivation (PROOF, L1):** served data_json algorithm-state fields === oracle(seed)-regenerated state.
- **Leg 2 INDEPENDENT INVARIANTS (PROOF, L1):** per-frame structural invariants + postcondition vs a TRUSTED INDEPENDENT reference (NOT the oracle): final==knownCorrectSort(input), multiset conserved every frame, every-frame-advances over the state vector, monotone-progress, + the algorithm's defining per-frame invariant. **This leg kills the tautology + the never-sorts class.**
- **Leg 3 CALLOUT/PROSE (ESTIMATOR, L2):** callout excluded from legs 1/2; gated separately by the language (RO) gate + the Phase-C pedagogy estimator; never byte-pinned. (Matches the plan's A1 note + two-layer model.)
- Oracle + seed-normalization live in ONE shared module imported by both the codegen AND the invariant harness (no drift). A0 (commit WIP families/oracles) first. Mergesort re-bind rides leg 2.
- **OPEN A1 VERIFICATION ITEM (judge, why 8/10 not higher):** confirm the existing `arrayMergeTrace` harness authors its per-frame invariants INDEPENDENTLY of the oracle's step generator. If invariants are derived from the SAME step list, leg 2 needs a genuinely separate trusted reference (e.g. property-based / stdlib cross-check). Phase-A build must verify this before trusting leg 2.

### D-B — #5 visual baselines = keep documented fail-open (self-decided, not council)
Zero-risk; Linux pixel-enforce = low ROI tonight (needs Linux host + dual-baseline upkeep). INV-9.5 path-scope gate still runs. Flagged for Alex; reopen if he wants CI pixel enforcement.

---

## PHASE-A A0 — fully diagnosed (recon wev8auw00), NOT executed (awaiting Alex). Plan = `… recon output`; key findings:

**Reskin ⇄ family separate cleanly at FILE level** (family skins use literal hex, ZERO `var(--color)` → reverting the theme can't break them):
- **REVERT (pure rejected reskin):** `index.css` (:root dark flip) + 5 beats (Predict/Attempt/Name/BeatOrchestrator/RevealBeat). **CheckBeat** = revert size-bumps but KEEP 1 real bugfix (`bg-panel-dark`→`bg-panel-dark-bg`, the old class rendered transparent).
- **COMMIT (legit, additive, default skin untouched, vitest 14/14 green):** MatrixGridFamily, SortMergeFamily, the 2 oracle/trace + 2 invariant helpers, 2 tests, 1 fixture, 4 content `viz/*.yaml`, + modified seams familyRegistry / traceMatchHarness.test / SequenceArray(+925, default `white`) / GraphTree / ChartDist / GraphTreeFamily.test / AlgoStepperShell (viewBoxH + reading-paced autoplay).
- **Double-mount root cause = the rejected reskin:** VizDemoPage's `viz-demo-dark-figures` section re-mounts families with colliding `mg`/`mgg` prefixes → strict-mode fail. Removing that section (= the reskin revert) fixes it. No family/spec change.

**Why I did NOT auto-execute (despite recon marking steps 1–6 "safe"):** every step is either hand-surgery on Alex's rejected work OR commits substantial NEW VISUAL work (bars/dark skins, the SequenceArray refactor, dark figure stage) that a visual learner should review by eye first ([[feedback_render_before_claim_done]], [[feedback_design_by_proliferation]]). Quality over volume.

### D-A LEG-2 — verified NOT-INDEPENDENT (the council's 8/10 worry, confirmed). Fix QUEUED, needs Alex's Phase-A go.
The seed-recompute independence lives ENTIRELY in **leg 1** (trace-match vs `arrayMergeReference(seed)` / `matrixGridReference`). **Leg 2 (semantic invariants) is a tautology in isolation:** `INV-1 finalState` sorts the rendered frame against ITSELF (`[...rendered].sort()`), never `sort(seed)` — a frame showing `[1,2,3]` passes even if seed was `[5,2,8,1,9,3]`; `INV-2` anchors multiset on authored `frame[0]`; `INV-3/INV-4` slice authored `frame.state`. **System is sound TODAY only because leg 1 runs first** (seededWrongTrace proves leg 1 catches the wrong trace). Fix (thread the raw seed into leg 2): INV-1 vs `[...seed].sort()`, INV-2 vs `multiset(seed)`, INV-3 from independent replay, matrixGrid INV-4 vs `matrixGridReference`. **Flagged as a spec-gate change → Alex sign-off before Phase-A build.**

## ⏳ MORNING DECISION QUEUE FOR ALEX (3 forks — your call, not council's)
1. **Dark aesthetic disposition:** revert the dark *page theme* (clearly rejected) — but KEEP the dark *floating figure* stage (`LessonFigureShell`/`FigureReveal`; literal-hex, theme-independent; the v2 plan puts it in Phase D)? Or drop both? The `/lectie-*` demo routes violate branching-scope (→ feature branch, not main) regardless.
2. **A0 family-WIP commit:** OK to commit the green family/oracle cluster (incl. the bars + dark family skins + the +925 SequenceArray refactor) after you eyeball the renders? It's at-risk untracked WIP (one `git clean` from gone).
3. **D-A leg-2 fix** (thread seed into invariants) + **`alo-kc-gauss-elim.yaml`** (A0 or a separate content commit?) — both need your go.
