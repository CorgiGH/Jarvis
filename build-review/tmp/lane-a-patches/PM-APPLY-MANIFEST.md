# PM-APPLY-MANIFEST — Plan 4b Lane A (Rendered Gates 3+4)

Generated: Task 12 final gate, 2026-06-13  
Plan-start SHA: `bab5d3c` (Plan 4a DONE — first two-lane merge landed clean)  
Plan-end (HEAD): `2088289` (fix(plan4b/task11): viz/theme.ts INK/PAPER fix + drift-proof palette tests)

---

## Commits (bab5d3c..HEAD, chronological)

| SHA | Message |
|---|---|
| `dc12c9f` | fix(e2e): exclude e2e/visual from the default playwright config (PM-applied) |
| `311d3de` | feat(plan4b/task1): LessonErrorBoundary + BeatOrchestrator wrap (TDD) |
| `0b0a5b8` | feat(plan4b/task2): trace-match harness totality + graph-tree semantic invariants + seeded wrong-trace (INV-5.1/5.2, INV-9.4 gate-3) |
| `4c9d0ce` | feat(plan4b/task3): viz instance serve route GET /api/v1/viz/{instanceId} + §NEW-V lock + mount patch |
| `847c488` | feat(plan4b/task4): shell play control + family onStep + FigureReveal + RevealBeat mount + chromeStrings (§0.9A; TDD) |
| `73396ec` | feat(plan4b/task5): figure binding pa-kc-002 + INV-5.5 fold + anti-vacuity + accommodation ledger |
| `76a023f` | feat(plan4b/task6): language admission gate + INV-8.2 seeded set + record at reconcile + migration patch (§0.9E/F; TDD) |
| `1c4c2ad` | feat(plan4b/task7): chrome RO sweep (8 components) + chrome-en-grep tool + scope file (§0.9G; INV-8.3) |
| `ee9d2f3` | fix(plan4b/task7): chrome-en-grep bug fixes + ChatPane sweep completion (bounded-fix-round) |
| `eac00f0` | feat(plan4b/task8): rendered-gate helpers + four gate-4 seeded-violation drills (§0.9D/E/I; INV-9.4) |
| `8842983` | chore(plan4b): apply CP-1 — viz instance route mount + §NEW-V lock freeze (PM-applied) |
| `587d069` | fix(plan4b): viz route session-auth allowlist — CP-1 companion (PM-applied) |
| `67b44b3` | feat(plan4b/task9): real-backend lesson-gates 4a-4d on the real corpus + no-clip fix (§0.9J; INV-4.4/5.3/8.4) |
| `c27a835` | feat(plan4b/task10): RetryBudget + park seam + INV-9.2 boundedness test (§0.9H; R-4b-Q3) |
| `2088289` | fix(plan4b/task11): viz/theme.ts INK/PAPER fix + drift-proof palette tests (§0 #11) |

---

## Changed-file list (non-build-review/ files, bab5d3c..HEAD)

```
content/PA/kcs/pa-kc-002.yaml
docs/superpowers/plans/2026-06-02-interface-signatures-lock.md    [CP-1 PM-applied]
src/main/kotlin/jarvis/content/ContentCli.kt
src/main/kotlin/jarvis/content/ContentReconcile.kt
src/main/kotlin/jarvis/content/ContentRepo.kt
src/main/kotlin/jarvis/content/ContentValidator.kt
src/main/kotlin/jarvis/content/LanguageGate.kt
src/main/kotlin/jarvis/tutor/Plan4bTables.kt
src/main/kotlin/jarvis/tutor/e2e/E2eSeed.kt
src/main/kotlin/jarvis/tutor/gate/RetryBudget.kt
src/main/kotlin/jarvis/web/TutorRoutes.kt                        [CP-1 PM-applied]
src/main/kotlin/jarvis/web/VizInstanceRoutes.kt
src/main/kotlin/jarvis/web/WebMain.kt                            [CP-1 companion PM-applied]
src/test/kotlin/jarvis/content/FigureBindingHashNoRippleTest.kt
src/test/kotlin/jarvis/content/FigureBindingNonVacuityTest.kt
src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt
src/test/kotlin/jarvis/content/LanguageCheckRecordTest.kt
src/test/kotlin/jarvis/content/LanguageGateSeededRegressionTest.kt
src/test/kotlin/jarvis/content/LanguageGateTest.kt
src/test/kotlin/jarvis/tutor/e2e/E2eSeedTest.kt
src/test/kotlin/jarvis/tutor/gate/RetryBoundednessTest.kt
src/test/kotlin/jarvis/web/VizInstanceRouteTest.kt
tools/chrome-en-grep.mjs
tools/chrome-en-grep.test.mjs
tools/chrome-scope.json
tutor-web/e2e/fixtures/seeded-violations/clip.json
tutor-web/e2e/fixtures/seeded-violations/en-in-ro.json
tutor-web/e2e/helpers/assertLegibility.ts
tutor-web/e2e/helpers/assertNextGateContract.ts
tutor-web/e2e/helpers/roHeuristic.ts
tutor-web/e2e/lesson-gates.spec.ts
tutor-web/e2e/seeded-violations.spec.ts
tutor-web/playwright.config.ts                                   [PM-applied pre-task-1]
tutor-web/src/__tests__/ChatPaneEnvelopes.test.tsx
tutor-web/src/__tests__/ConceptDrawer.test.tsx
tutor-web/src/__tests__/ConceptInline.test.tsx                   [MANIFEST DEVIATION — see below]
tutor-web/src/__tests__/KnowledgeLedger.test.tsx
tutor-web/src/__tests__/Scratchpad.test.tsx
tutor-web/src/__tests__/Sidekick.test.tsx
tutor-web/src/__tests__/TrustSettings.test.tsx
tutor-web/src/__tests__/gapPersist.test.tsx
tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx            [MANIFEST DEVIATION — see below]
tutor-web/src/__tests__/viz/BayesTree.palette.test.tsx
tutor-web/src/__tests__/viz/ProcessFSM.palette.test.tsx
tutor-web/src/__tests__/viz/RaceMutex.palette.test.tsx
tutor-web/src/__tests__/viz/SchedulerGantt.palette.test.tsx
tutor-web/src/__tests__/viz/TcpCwnd.palette.test.tsx
tutor-web/src/components/ChatPane.tsx
tutor-web/src/components/ConceptDrawer.tsx
tutor-web/src/components/KnowledgeLedger.tsx
tutor-web/src/components/Scratchpad.tsx
tutor-web/src/components/Sidekick.tsx
tutor-web/src/components/TaskQuickStart.tsx
tutor-web/src/components/TrustSettings.tsx
tutor-web/src/components/lesson/BeatOrchestrator.test.tsx
tutor-web/src/components/lesson/BeatOrchestrator.tsx
tutor-web/src/components/lesson/FigureReveal.test.tsx
tutor-web/src/components/lesson/FigureReveal.tsx
tutor-web/src/components/lesson/LessonErrorBoundary.test.tsx
tutor-web/src/components/lesson/LessonErrorBoundary.tsx
tutor-web/src/components/lesson/RevealBeat.tsx
tutor-web/src/components/viz/AlgoStepperShell.tsx
tutor-web/src/components/viz/families/GraphTreeFamily.tsx         [Task9 PM SCOPE AMENDMENT]
tutor-web/src/components/viz/families/__tests__/fixtures/seeded-wrong-trace-mergesort.yaml
tutor-web/src/components/viz/families/__tests__/seededWrongTrace.test.tsx
tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx
tutor-web/src/components/viz/families/familyRegistry.ts
tutor-web/src/components/viz/families/graphTreeInvariants.ts
tutor-web/src/components/viz/theme.ts
tutor-web/src/lib/chromeStrings.ts
tutor-web/src/lib/lessonStrings.ts
```

### Manifest deviations discovered at Task 12 (PM: review and ratify or reject)

**EXTRA files (in diff, NOT in §0.8 manifest):**

1. `tutor-web/src/__tests__/ConceptInline.test.tsx` — NOT in the `[lockstep]` set. Changed in Task 7 (`1c4c2ad`): `ConceptInline.test.tsx` asserts the `ConceptDrawer` close button `aria-label`, which was swept to `chromeStrings.ts`. The test was updated to import `conceptDrawer.closeAriaLabel` from `chromeStrings` (the same lockstep pattern). The `[lockstep]` set enumerated at plan-write missed this indirect importer. **Assessment: benign, correct, tests pass. PM ratification requested.**

2. `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx` — NOT in the manifest. Changed in Task 4 (`847c488`): two tests that asserted "no play button rendered" were updated to assert "play button rendered" after the additive `{prefix}-play` button was added to `AlgoStepperShell.tsx` (which IS in the MODIFY manifest). This is a natural consequence of modifying the component. **Assessment: benign, correct, tests pass. PM ratification requested.**

**MISSING files (in §0.8 MODIFY manifest but NOT in diff):**

3. `tutor-web/src/components/ScratchpadDrawer.tsx` — Listed as MODIFY (RO sweep) in §0.8, but not changed. Reason: the component delegates entirely to `Scratchpad` and has NO learner-visible EN literals of its own — nothing to sweep. **Assessment: plan over-enumerated this file; the RO sweep target was already clean. Zero action needed.**

4. `tutor-web/src/__tests__/ScratchpadDrawer.test.tsx` — Listed as `[lockstep]` in §0.8. Not changed because `ScratchpadDrawer.tsx` had no swept literals. **Assessment: permitted by `[lockstep]` semantics — "changes only if it asserted a swept literal".**

5. `tutor-web/src/__tests__/axe.dashboard.test.tsx` — Listed as `[lockstep]`. Not changed. **Assessment: axe tests assert accessibility, not literal strings — did not need updating.**

6. `tutor-web/src/__tests__/Sidekick.citations.test.tsx` — Listed as `[lockstep]`. Not changed. **Assessment: likely does not assert swept Sidekick literals. Permitted.**

7. `tutor-web/src/__tests__/Sidekick.mathtext.test.tsx` — Listed as `[lockstep]`. Not changed. **Assessment: math text tests do not assert swept chrome strings. Permitted.**

---

## Gate results (Task 12 Step 2)

| Suite | Command | Result |
|---|---|---|
| Backend | `gradle --no-daemon :check` (forced `--rerun`) | **GREEN** — 1519 tests, 0 failures, 2 skipped; `stateCacheConcurrentPersistNeverTearsJson` did NOT fire this run |
| Frontend vitest | `npm --prefix tutor-web test` | **GREEN** — 175 test files, 901 tests, 0 failures |
| e2e (stubbed) | `npm --prefix tutor-web run e2e` | **GREEN** — 22 passed, 1 skipped (lesson-gates, correctly skipped: no REAL_BACKEND) |
| e2e:visual | `npm --prefix tutor-web run e2e:visual` | **GREEN** — 3/3 baselines matched |
| e2e (real backend) | `REAL_BACKEND=1 npx playwright test e2e/lesson-gates.spec.ts` | **GREEN** — 1 passed (fresh seed from seedE2eDb; server booted with JARVIS_AUTH_TOKEN) |

**StateCache flake note (R-4b-Q9):** `stateCacheConcurrentPersistNeverTearsJson` did not fire in this Task-12 run. Standing: if it fires in CI, NAME it and re-run; never absorb.

---

## Patches — apply order + status

### CP-1 — ALREADY APPLIED (8842983 + 587d069)

(1) `tutor-routes-viz-mount.patch` — `git apply --check` shows conflict because the patch was already applied at `8842983`. **Status: DONE** (one-line `installVizInstanceRoutes()` mount in `TutorRoutes.kt` is live on main).

(2) `signatures-lock-viz-route.patch` — same; §NEW-V freeze applied at `8842983`. **Status: DONE.**

Note: `webmain-viz-allowlist.patch` (not a plan §0.8 deliverable but a CP-1 companion) was applied at `587d069`.

### CP-2 — PENDING (not yet applied)

(3) `build-gradle-seed-e2e.patch` — adds `seedE2eDb` JavaExec task to `build.gradle.kts`. `git apply --check`: **CLEAN**. Apply order: BEFORE the first CI push that should run the lesson-gates job.

(4) `test-yml-plan4b.patch` — adds the `lesson-gates` CI job (real backend) + the `chrome-en-grep` CI step (frontend job). `git apply --check`: **CLEAN**. Apply order: same push as `build-gradle-seed-e2e.patch`.

**CP-2 action: PM applies (3) and (4) together, commits, pushes. CI proof: the lesson-gates job must pass on GitHub Actions before Plan 4b is claimable DONE (§9.3).**

### CP-3 — PENDING (backup-gated, apply after BOTH DBs backed up)

(5) `migration-language-check.patch` — registers `LanguageCheckTable` in `Migration.kt`'s table list. `git apply --check`: **CLEAN**. 

**INV-3.1 backup gate: before applying, take off-box dumps of BOTH DBs (PC: `~/.jarvis/tutor.db` + VPS: remote tutor.db). Fresh dumps were verified 2026-06-12 per ruling; if significant time has passed, re-dump before applying.**

**CP-3 action: PM applies (5) after backup verification, commits, deploys to both PC + VPS (the migration runs on server start: `TutorMigration.migrate()`).**

---

## NAMED Lane-B merge-time action (NO patch — PM TODO)

At Lane B merge (`lane-b/plan6` → `main`), the PM MUST:
- **Widen `tools/chrome-scope.json` `files` to include `tutor-web/src/components/DrillStack.tsx`** once Lane B's `practiceStrings.ts` sweep lands.
- Then verify: `node tools/chrome-en-grep.mjs` exits 0 (the `practice/*.tsx` scope entries now resolve to real files swept against `practiceStrings.ts`).
- This is the moment INV-8.3's practice coverage goes live. Until then it is deferred-with-owner, not vacuous-by-omission.

---

## CI-green requirement

Plan 4b is not claimable DONE until the `lesson-gates` CI job passes on GitHub Actions (§9.3). This requires CP-2 (the CI patches) to be applied and pushed. The local proof (Task 9 + Task 12 Step 2) demonstrates all gates pass; CI is the official record.

---

## Live-state honesty note

The trust-net has NOT been run in production on either DB. Live `kc_verification_status` = 0 rows. This means:
- Live lesson routes still 404 for all KCs (the faithful gate rejects them: no verification row).
- INV-8.1 (served fields lacking a language_check record → 0 rows) is vacuously true on live (no rows served).
- The `LanguageCheckTable` (CP-3 migration) will be empty on live until the trust-net flip runs (out of 4b scope — see BRIDGE-HEAD for the NEXT action: backup → 3-col migration → verifyContent → D9 sync).

The gates are proven on the seeded real-corpus DB (`build/e2e/tutor.db`), which is what R-4b-Q2 ratified.

---

## Re-carries (Task 12 Step 6 per the rulings)

These carry forward to the named plans:

1. **Cross-language schema-hash CI test** — TS/Kotlin shared-constant drift (§0.9E constants now duplicated). Carry to **Plan 5**.
2. **Linux baseline regeneration** — 4a follow-up #4, unchanged. Carry to **Plan 5** (or first CI run on Linux).
3. **FSRS re-seed wart** — unchanged. Carry to **Plan 5**.
4. **CodeMirror editor** — R-6-Q9, named follow-up. Carry to **Plan 6** (or post-Lane-B-merge).
5. **Numeric-ATTEMPT tolerance single-source** — Plan 6 Lane B's fold (lane table). Carry TO that plan, not held here.
6. **chrome-scope DrillStack widening at Lane B merge** — the named PM-APPLY-MANIFEST action above. Deferred-with-owner (PM executes at Lane B merge); INV-8.3's practice coverage is not vacuous-by-omission.
7. **lessonStrings / chromeStrings consolidation** — three strings files exist by design this cycle (`lessonStrings.ts`, `chromeStrings.ts`, `practiceStrings.ts` Lane B). Revisit when Lane B's `practiceStrings.ts` lands. Carry to **post-Lane-B-merge tidy**.
