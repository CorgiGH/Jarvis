# Plan 4b: Rendered Gates 3+4 on the Real Lesson Route (Lane A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **LANE A — executes in the MAIN working tree `C:\Users\User\jarvis-kotlin`, directly on `main`** (Plan 3 is DONE and merged; Plan 4a is DONE and merged at `bab5d3c`). The concurrent Lane B (Plan 6) runs in its own worktree — Lane A NEVER touches Lane-B-exclusive files (lane table below) and routes every SHARED file through PM-applied patch files under `build-review/tmp/lane-a-patches/`. **Executors NEVER edit this plan doc** — a plan/reality mismatch is a BLOCKED task escalated to the PM verbatim.

**Goal:** Spec §9.2 gates 3+4, rendered on the REAL lesson route over the REAL corpus: per-family trace-match + semantic invariant asserts (INV-5.1/5.2), no-clip per-live-instance + CI-blocking (INV-5.3), per-element legibility/contrast (gate 4b), interaction test (gate 4c, INV-4.4), rendered language gate (gate 4d, INV-8.4); the language ADMISSION gate + record (INV-8.1/8.2) and the chrome RO sweep + grep (INV-8.3); `FigureReveal` + a real figure binding so none of it is vacuous (R-4b-Q1); `LessonErrorBoundary`; `RetryBudget` + INV-9.2; the gate-3/4 INV-9.4 seeded-violation drills; the INV-5.5 fold; the `viz/theme.ts` INK/PAPER fix under the new gates' cover.

**Architecture:** All learner-visible content RO, code/identifiers EN. The PM rulings in `build-review/2026-06-12-plan4b6-pm-rulings.md` are BINDING and applied verbatim throughout (§0.5) — no relitigation. Spec stays canonical; `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` is canonical-on-conflict and consulted by every route/payload-touching task.

**Tech stack:** @playwright/test on the real Ktor server over a seeded SQLite (CI job per R-4b-Q2), vitest for the trace-match harness, JUnit 5 for admission/retry/record tests, no new runtime dependencies, no paid APIs (zero LLM calls anywhere in this plan).

---

## Section 0 — Verified ground truth (recon 2026-06-12 + plan-write re-verification 2026-06-12, post-4a-merge)

1. **Git state:** `main` @ `bab5d3c` ("Plan 4a DONE — first two-lane merge landed clean"). Plan 4a is MERGED into main: `playwright.visual.config.ts`, `e2e/visual/` (3 baselines under `__screenshots__/`), `tools/impeccable-*`, `tools/generate-design-md.mjs`, fonts, and the Impeccable fail-open CI step (test.yml:72-73) are all live on main. Do NOT trust the recon's pre-merge framing of these as Lane-B-pending.
2. **The figure stub (R1's vacuity):** `tutor-web/src/components/lesson/RevealBeat.tsx:50-57` — when `reveal.figure` is set it renders only `data-testid="beat-figure-scrubber"` + a hardcoded `pas 1/1` counter and the comment `{/* Task 8: <FigureReveal … /> */}`. `FigureReveal` exists nowhere. The 4 beat-complete KCs (`pa-kc-001..004`) all carry `figure: null`. `pa-kc-005`/`006` exist but have NO beats (404 on the lesson route — not served).
3. **The wire contract for figures already exists and is frozen:** `ApiFigureBinding { family_id, instance_id }` on `ApiBeatReveal.figure` (lock §NEW-L; Kotlin `QueueMasteryCalibrationRoutes.kt:196-197`, TS `lesson.ts:87-94`). The payload does NOT carry `data_json` — the client must resolve the instance data separately (→ §0.9B viz serve route).
4. **Hash no-ripple (load-bearing for R-4b-Q1):** `ContentReconcile.kcContentHash(kc)` = hash over `claimsFor(kc)` which emits DEFINITION/INVARIANT/GRADER_RULE claims only — **beats do not feed `claimsFor`** (zero `beats` references in `ContentReconcile.kt`; `ContentValidator.checkFigureBindings` doc line states "ZERO serve/sync/hash ripple (beats don't feed claimsFor)"). Editing `beats.ro.reveal.figure` in a KC YAML therefore does NOT stale the D8 hash. Task 5's ledger re-proves this with a pinned test, not by trusting this paragraph.
5. **Family layer:** `familyRegistry.ts` = `{ "graph-tree": GraphTreeFamily }`; `FamilyRendererProps { instanceId, dataJson, language, labels? }` (no step callback — Task 4 adds one, additive). `GraphTreeFamily` mounts `AlgoStepperShell` with `testIdPrefix="graph-tree"`; the shell exposes `{prefix}-{root,controls,frame-counter,scrubber,step-back,step-fwd,reset,share,voice,live}` testids and an `onStep` callback, but **NO play/autoplay control** (§4.6 requires back/play/forward/reset — Task 4 adds `{prefix}-play`, additive). `ShellLabels` = 6 optional fields with EN defaults at `AlgoStepperShell.tsx:97-102`; no caller passes RO today.
6. **Trace-match today:** `families/__tests__/GraphTreeFamily.test.tsx` raw-imports the shipped `content/PA/viz/viz-pa-mergesort-001.yaml` (`?raw`, via `vite-raw.d.ts`), pins 8 steps against the independent `mergesortTrace.ts` reference. Exactly ONE live instance exists in the whole corpus (`Glob content/*/viz/*.yaml` → 1 file). What's missing for INV-5.1/5.2: totality over every instance + every family, rendered-coordinate semantic invariants, the seeded wrong-trace red.
7. **e2e layer:** all specs in `tutor-web/e2e/` are backend-stubbed (`page.route`); `playwright.config.ts` baseURL `http://localhost:5173`, `webServer: npm run dev`; vite proxies `/api` + `/auth` → `http://localhost:8080` (vite.config.ts:14-20). `assertNoClip.ts` (Plan-3) is in the suite, used by `lesson-beats.spec.ts` (2 viewports) + `family-no-clip.spec.ts`. CI frontend job runs NO JVM. `lesson-beats.spec.ts` fixture `e2e/fixtures/pa-kc-001-beats.json` pins 3 anchors verbatim to `pa-kc-001.yaml` (NOT pa-kc-002 — Task 5's binding target).
8. **Server boot facts (for §0.9J):** entry = `gradle run --args=web` (`jarvis.MainKt` → `runWeb()`); DB path = `JARVIS_TUTOR_DB` env (`Config.kt:38-39`); content dir = `JARVIS_CONTENT_DIR` env/prop, default `content` (`QueueMasteryCalibrationRoutes.kt:58-64`); port = `JARVIS_PORT`, default 8080. Auth = `jarvis_session` cookie resolved via `SessionRepo.findUserId` (`/api/v1/tutor/auto-session` returns 401 without it — never auto-mints). Faithful gate: `VerifyAdmin.resolveStatus` requires a `kc_verification_status` row with `status=faithful` + `content_hash == ContentReconcile.kcContentHash(kc)` + non-null `source_span_hash` + no open report-wrong. Seeding precedent: `DrillGradeAtomicTest.seedFaithful` (real `kcContentHash` + real `sourceSpanHashOf`), `LessonServeRouteTest.freshDb`.
9. **Admission/sync seam (R-4b-Q6):** `ContentValidator.validate()` (`ContentValidator.kt:151-172`, 11 checks; `checkBilingual` at 112-128 is the non-blank-only audit-L7 hole; `checkFigureBindings` at 290-312 implemented but NOT in `validate()` — only `FigureBindingValidationTest` calls it). DB-side admission = `ContentReconcile.reconcile` (stage-9; runs AFTER validateContent; writes claims + `pending` status). `GlossaryTermsTable` exists in schema (`Plan2Tables.kt:120-128`, registered in `Migration.kt:79`) but has **ZERO production write-sites — the table is empty everywhere** (verified: only Migration/Plan2Tables/SignatureLockPinTest reference it). R-4b-Q5's "writer verifies seed state" = verified: empty; the per-call exempt list carries the working exemptions until Plan 5/6 seed glossaries.
10. **§8.1 structural fact for the heuristic:** the 4 authored KCs' RO callouts deliberately embed verbatim EN source quotes inside guillemets — e.g. `pa-kc-002.yaml` reveal callout `Cursul spune: «The general formal description of this is that of computation model»`. The RO heuristic MUST strip `«…»`/`‹…›` quoted spans before checking or the over-blocking acceptance (zero false reds on the 4 authored KCs) is unreachable.
11. **theme.ts drift (carried 4a→4b):** `tutor-web/src/components/viz/theme.ts:5-6` hardcodes `INK="#0a0a0a"` / `PAPER="#f5f5f0"` vs DESIGN.md `#000000`/`#ffffff`. The 3 visual baselines (shell + theme-dark + theme-light) render from index.css tokens via the self-contained `themeRefHarness` — they do NOT import viz/theme.ts; expected zero baseline drift from the fix (Task 11 verifies, STOP on drift — baselines are human-recommit-only, spec gate 6).
12. **R-4b-Q8 confirmed executed:** the three stray untracked `e2e/visual/*-snapshots/` dirs are GONE (verified `ls`); only `__screenshots__/` remains. Task 0 re-asserts.
13. **Known flake (R-4b-Q9):** `stateCacheConcurrentPersistNeverTearsJson` reds the full backend suite ~1-in-2. The final gate NAMES it if it fires; never absorbs; standing carve-out.
14. **Retry is greenfield:** zero pipeline retry/budget config under `src/main/kotlin` (grep `retry` → only transport retries in OpenRouterChatLlm/JarvisToolset/Google*/ShadowGit). R6 ships fresh per R-4b-Q3.
15. **Standing tree rules:** 5 door demo files + assorted `public/` artifacts are deliberately UNTRACKED — **never `git add -A`, never `git clean`, never touch `src/door/` demos** beyond what a task names. Commits stage explicit paths only. Non-ASCII in Kotlin **test method names** caused a 4a merge fix (`86e120c`) — keep Kotlin test titles ASCII.

## Section 0.5 — PM rulings applied (BINDING, `build-review/2026-06-12-plan4b6-pm-rulings.md`)

| Ruling | Where applied in this plan |
|---|---|
| R-4b-Q1 (a): build FigureReveal + bind `viz-pa-mergesort-001` to ONE served KC; accommodation ledger; anti-vacuity totality assert | Tasks 3, 4, 5 (§0.9A/B; ledger = Task 5 Steps 1–3; anti-vacuity = `FigureBindingNonVacuityTest` + lesson-gates figure assertion) |
| R-4b-Q2 (a): real Ktor server over seeded SQLite in CI; seed from real `content/` via the admission path; existing stubbed specs stay; CI yml = PM patch | Task 9 (§0.9J); `lesson-beats.spec.ts` untouched |
| R-4b-Q3: 4b ships RetryBudget constant + park seam + INV-9.2 only; REJECT-loop UI = Plan 5; explicitly not dead config | Task 10 (§0.9H) |
| R-4b-Q4: contrast method defined IN PLAN | §0.9D verbatim; Task 8 |
| R-4b-Q5: two-sided calibration; glossary source = GlossaryTermsTable seeds (verified empty, §0 #9) + per-call exempt list; diacritic OR stopword; short strings exempt diacritic leg only | §0.9E; Task 6 acceptance. **AMENDED (plan-fix F1) — PM-RATIFIED 2026-06-12 at freeze (rulings doc updated):** §0.9E adds a pinned `EN_VOCAB` loanword leg — the two ruled legs alone provably cannot red the ruling's own INV-8.2 flagship seed ("skeletonul …": EN-stopword ratio 0), so the ruled under-blocking acceptance is unsatisfiable without it |
| R-4b-Q6: language record at ADMISSION/SYNC, never serve; `QueueMasteryCalibrationRoutes.kt` untouched (Lane B exclusive); migration behind INV-3.1 backup gate, both DBs; Migration.kt = PM patch | Task 6 (§0.9F); zero serve-route edits anywhere in this plan |
| R-4b-Q7: grep scope = explicit allowlist file in `tools/`; excludes demo gallery, door demos, data-testids, identifiers | Task 7 (§0.9G, `tools/chrome-scope.json`) |
| R-4b-Q8: PM deleted stray baselines | §0 #12; Task 0 re-check |
| R-4b-Q9: StateCache flake named, never absorbed | Task 12 Step 2 |
| R-4b-Q10: signatures-lock check on every route/payload-touching task; conflict → HALT for PM | "Lock check" line in Tasks 3, 4, 5, 6, 9; final sweep Task 12 |
| R-4b-Q11 + lane table: DrillStack + its tests = Lane B (4b RO sweep EXCLUDES it); strings = Lane A `chromeStrings.ts` / Lane B `practiceStrings.ts`, INV-8.3 grep accepts both; `QueueMasteryCalibrationRoutes.kt`/`LessonBeatGradeRouteTest.kt`/`main.tsx`/`WebMain.kt`/`TutorRoutes.kt` = Lane B (Lane-A need discovered → PM patch — exercised for the ONE-LINE viz-route mount, §0.9B); Migration.kt/test.yml/package.json+lock/playwright.config.ts/build.gradle.kts/plan index/signatures lock = PM-MERGE-ONLY patches; `content/PA/kcs/*.yaml` figure binding = Lane A; ContentValidator/ContentRepo/lesson components/viz families/e2e gates = Lane A | §0.8 manifest; every task's file list |

**Carried follow-ups — disposition (rulings doc final section):** 4b folds → BeatOrchestrator error boundary (Task 1) · AlgoStepperShell RO chrome with FigureReveal (Tasks 4+7) · INV-5.5 `checkFigureBindings` into `validate()` (Task 5) · `viz/theme.ts` INK/PAPER (Task 11). Explicit re-carries → Task 12 Step 6 (cross-language schema-hash CI test; Linux baseline regeneration; FSRS re-seed wart; CodeMirror editor; numeric-ATTEMPT tolerance = **Plan 6 Lane B's fold** per the lane table, not 4b's).

## Section 0.8 — File manifest (machine-checked: CREATE+MODIFY must intersect Plan 6's CREATE+MODIFY to ZERO; SHARED excluded)

### CREATE

| Path | Task |
|---|---|
| `tutor-web/src/components/lesson/LessonErrorBoundary.tsx` | 1 |
| `tutor-web/src/components/lesson/LessonErrorBoundary.test.tsx` | 1 |
| `tutor-web/src/components/viz/families/graphTreeInvariants.ts` | 2 |
| `tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx` | 2 |
| `tutor-web/src/components/viz/families/__tests__/seededWrongTrace.test.tsx` | 2 |
| `tutor-web/src/components/viz/families/__tests__/fixtures/seeded-wrong-trace-mergesort.yaml` | 2 |
| `src/main/kotlin/jarvis/web/VizInstanceRoutes.kt` | 3 |
| `src/test/kotlin/jarvis/web/VizInstanceRouteTest.kt` | 3 |
| `build-review/tmp/lane-a-patches/tutor-routes-viz-mount.patch` | 3 (PM applies) |
| `build-review/tmp/lane-a-patches/signatures-lock-viz-route.patch` | 3 (PM applies) |
| `tutor-web/src/components/lesson/FigureReveal.tsx` | 4 |
| `tutor-web/src/components/lesson/FigureReveal.test.tsx` | 4 |
| `tutor-web/src/lib/chromeStrings.ts` | 4 (extended in 7) |
| `src/test/kotlin/jarvis/content/FigureBindingNonVacuityTest.kt` | 5 |
| `src/test/kotlin/jarvis/content/FigureBindingHashNoRippleTest.kt` | 5 |
| `src/main/kotlin/jarvis/content/LanguageGate.kt` | 6 |
| `src/test/kotlin/jarvis/content/LanguageGateTest.kt` | 6 |
| `src/test/kotlin/jarvis/content/LanguageGateSeededRegressionTest.kt` | 6 |
| `src/main/kotlin/jarvis/tutor/Plan4bTables.kt` (`LanguageCheckTable`) | 6 |
| `src/test/kotlin/jarvis/content/LanguageCheckRecordTest.kt` | 6 |
| `build-review/tmp/lane-a-patches/migration-language-check.patch` | 6 (PM applies, INV-3.1 backup gate, both DBs) |
| `tools/chrome-en-grep.mjs` | 7 |
| `tools/chrome-en-grep.test.mjs` | 7 |
| `tools/chrome-scope.json` | 7 |
| `tutor-web/e2e/helpers/assertLegibility.ts` | 8 |
| `tutor-web/e2e/helpers/roHeuristic.ts` | 8 |
| `tutor-web/e2e/helpers/assertNextGateContract.ts` | 8 |
| `tutor-web/e2e/seeded-violations.spec.ts` | 8 |
| `tutor-web/e2e/fixtures/seeded-violations/clip.json` | 8 |
| `tutor-web/e2e/fixtures/seeded-violations/en-in-ro.json` | 8 |
| `src/main/kotlin/jarvis/tutor/e2e/E2eSeed.kt` | 9 |
| `src/test/kotlin/jarvis/tutor/e2e/E2eSeedTest.kt` | 9 |
| `tutor-web/e2e/lesson-gates.spec.ts` | 9 |
| `build-review/tmp/lane-a-patches/build-gradle-seed-e2e.patch` | 9 (PM applies) |
| `build-review/tmp/lane-a-patches/test-yml-plan4b.patch` | 9 (PM applies; also carries Task 7's grep step) |
| `src/main/kotlin/jarvis/tutor/gate/RetryBudget.kt` | 10 |
| `src/test/kotlin/jarvis/tutor/gate/RetryBoundednessTest.kt` | 10 |
| `build-review/tmp/lane-a-patches/plan-index-4b.patch` | 12 (PM applies) |
| `build-review/tmp/lane-a-patches/PM-APPLY-MANIFEST.md` | 12 |

### MODIFY

| Path | Task | Change |
|---|---|---|
| `tutor-web/src/components/lesson/BeatOrchestrator.tsx` | 1 | wrap the active-beat body in `LessonErrorBoundary`; no contract change |
| `tutor-web/src/components/lesson/BeatOrchestrator.test.tsx` | 1 | + boundary cases |
| `tutor-web/src/lib/lessonStrings.ts` | 1 | additive `beatError` RO key (lesson-surface string; file is Lane-A, not in any Lane-B manifest) |
| `tutor-web/src/components/viz/families/GraphTreeFamily.tsx` | 2, 4 | stamp `data-node-id` per rendered node (test hook, additive); thread `onStep` to the shell |
| `tutor-web/src/components/viz/families/familyRegistry.ts` | 4 | additive `onStep?: (idx: number, lastIdx: number) => void` on `FamilyRendererProps` |
| `tutor-web/src/components/viz/AlgoStepperShell.tsx` | 4 | additive `{prefix}-play` autoplay control + `ShellLabels.play`; EN defaults unchanged |
| `tutor-web/src/components/lesson/RevealBeat.tsx` | 4 | replace the line-50 stub with `<FigureReveal …/>`; move the predict echo banner above the branch; stepped-text path untouched |
| `content/PA/kcs/pa-kc-002.yaml` | 5 | `beats.ro.reveal.figure: null` → the mergesort binding (the ONE edit) |
| `src/main/kotlin/jarvis/content/ContentValidator.kt` | 5, 6 | fold `checkFigureBindings` into `validate()` (thread `knownInstances` id→family_id MAP; assert the instance EXISTS **and** `binding.family_id == instance.family_id` — BOTH INV-5.5 halves at admission); add `LanguageGate.checkRomanianFields` to `validate()` |
| `src/main/kotlin/jarvis/content/ContentCli.kt` | 5 | thread `knownInstances = ContentRepo::loadVizInstances` id→family_id map into the `validate()` call(s) |
| `src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt` | 5 | extend: dangling binding now reds `validate()` itself |
| `src/main/kotlin/jarvis/content/ContentRepo.kt` | 3 | additive helper `loadAllVizInstances(): List<VizInstance>` (route + tests enumerate the corpus once) |
| `src/main/kotlin/jarvis/content/ContentReconcile.kt` | 6 | additive `recordLanguageChecks(...)` write-site called from `reconcile` (admission/sync time, R-4b-Q6) |
| `tutor-web/src/components/Scratchpad.tsx` | 7 | RO sweep → `chromeStrings.ts` |
| `tutor-web/src/components/ScratchpadDrawer.tsx` | 7 | RO sweep |
| `tutor-web/src/components/TaskQuickStart.tsx` | 7 | RO sweep |
| `tutor-web/src/components/ConceptDrawer.tsx` | 7 | RO sweep |
| `tutor-web/src/components/KnowledgeLedger.tsx` | 7 | RO sweep |
| `tutor-web/src/components/Sidekick.tsx` | 7 | RO sweep |
| `tutor-web/src/components/ChatPane.tsx` | 7 | RO sweep |
| `tutor-web/src/components/TrustSettings.tsx` | 7 | RO sweep |
| `tutor-web/src/__tests__/Scratchpad.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/ScratchpadDrawer.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/TaskQuickStart.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/ConceptDrawer.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/KnowledgeLedger.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/Sidekick.test.tsx` | 7 | lockstep literal updates `[lockstep]` (Sidekick has 3 test files) |
| `tutor-web/src/__tests__/Sidekick.citations.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/Sidekick.mathtext.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/ChatPaneEnvelopes.test.tsx` | 7 | lockstep literal updates `[lockstep]` (ChatPane's ONLY existing test file — NOT co-located, NOT `ChatPane.test.tsx`) |
| `tutor-web/src/__tests__/TrustSettings.test.tsx` | 7 | lockstep literal updates `[lockstep]` |
| `tutor-web/src/__tests__/axe.dashboard.test.tsx` | 7 | lockstep literal updates `[lockstep]` (imports swept components) |
| `tutor-web/src/__tests__/gapPersist.test.tsx` | 7 | lockstep literal updates `[lockstep]` (imports KnowledgeLedger) |
| `tutor-web/src/components/viz/theme.ts` | 11 | INK `#0a0a0a`→`#000000`, PAPER `#f5f5f0`→`#ffffff` |

**`[lockstep]` row semantics (machine-checkable):** the 12 tagged rows are the COMPLETE resolved set of existing test files that import any of the 8 swept components (verified by import-grep at plan-fix 2026-06-12 — concrete paths, no parentheticals, no globs). They are **allowed-but-not-required** in the final diff: a tagged file changes only if it asserted a swept literal (axe/persist-style tests may not). Task 12 Step 4's set-equality treats them as an optional subset; a swept-literal-asserting test discovered OUTSIDE this set at execution = plan/reality mismatch → STOP, PM (protocol rule 3), never a silent extra file.

**NOT touched (named so the reviewer can verify):** `DrillStack.tsx` + its 7 test files (Lane B), `QueueMasteryCalibrationRoutes.kt`, `LessonBeatGradeRouteTest.kt`, `main.tsx`, `WebMain.kt`, `TutorRoutes.kt` (mount ships as a patch), `lesson-beats.spec.ts`, `e2e/fixtures/pa-kc-001-beats.json`, `playwright.config.ts` (the env-skip pattern makes a config edit unnecessary), everything under `src/door/`, all `grader/`/`MockExam*`/`Tasks.kt`/`TaskPrep.kt`/`GradeScoring.kt`/`DrillGrader.kt`/`GradeTeachingPayload.kt`/`drillGrader.ts` (Lane B).

### SHARED:PM-MERGE-ONLY (delivered as patch files; PM applies — never edited directly by any 4b task)

- `.github/workflows/test.yml` — Task 7 grep step + Task 9 `lesson-gates` job (one combined patch `test-yml-plan4b.patch`).
- `build.gradle.kts` — Task 9 `seedE2eDb` JavaExec task (`build-gradle-seed-e2e.patch`).
- `src/main/kotlin/jarvis/tutor/Migration.kt` — Task 6 `LanguageCheckTable` registration (`migration-language-check.patch`; live application behind the INV-3.1 backup gate on BOTH DBs; fresh off-box dumps exist 2026-06-12 per ruling).
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — Task 3 one-line `installVizInstanceRoutes()` mount (`tutor-routes-viz-mount.patch`; the R-4b-Q11 "discovered need → PM patch" path).
- `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` — Task 3 §NEW-V freeze (`signatures-lock-viz-route.patch`, same commit as the route code per the lock's durable rule).
- `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` — Task 12 status flip + follow-up appends (`plan-index-4b.patch`).
- `tutor-web/package.json` + `package-lock.json` — NO 4b edit planned (no new deps, no new scripts required; `lesson-gates` runs via `npx playwright test e2e/lesson-gates.spec.ts`). If a need is discovered → PM patch, never direct.

**PM-apply checkpoints:** (CP-1) after Task 3 lands — PM applies `tutor-routes-viz-mount.patch` + `signatures-lock-viz-route.patch` (Task 9's real-server run needs the live mount). (CP-2) before Task 9's CI proof — PM applies `build-gradle-seed-e2e.patch` + `test-yml-plan4b.patch`. (CP-3) at plan close — PM applies `migration-language-check.patch` (backup-gated, both DBs) + `plan-index-4b.patch` per `PM-APPLY-MANIFEST.md`.

## Section 0.9 — Canonical contracts

### A. FigureReveal (fills the RevealBeat stub; R-4b-Q1, spec §4.6/§4.7/§5.3)

- When `reveal.figure` is set, the figure IS the reveal: `FigureReveal` resolves `figure.family_id` via `familyRegistry`, fetches the instance via §0.9B, and mounts the family (which mounts `AlgoStepperShell`). The instance's per-step callouts (instance data, RO, language-keyed) are the fused explanation (§5.3) — the authored `reveal.steps` are NOT rendered in figure mode (single scrubber, no duplicate `beat-figure-scrubber`), but they are NOT dead: they are the boundary fallback (a figure fetch/render failure degrades honestly to the stepped-text path) and stay required by `KcBeats.isCompleteFor` (steps non-empty regardless of figure — verified `KcBeats.kt:35`).
- The predict echo banner (§4.1) renders in BOTH modes — `RevealBeat` moves it above the figure/text branch.
- Selectors (§4.7): the wrapper carries `data-testid="beat-figure-scrubber"`; `FigureReveal` renders its own `data-testid="scrubber-step-counter"` showing `pas k/N` (`lessonStrings.step`, driven by the family `onStep` callback); the shell contributes `graph-tree-step-back`/`-step-fwd`/`-reset`/`-play` (Task 4 adds play) + the range scrubber.
- Gate: `onGateClear` fires when the final frame has been reached at least once (`onStep(idx === lastIdx)`) — "animation watched to final step at least once" (§4.1). Reduced motion: autoplay steps without animation (MotionConfig already honors it); the gate logic is identical.
- Labels: `FigureReveal` passes `labels = chromeStrings.shellLabels` (RO, §0.9G) — the first real `labels=` caller (closes the Plan-3 Task-14 carry).
- Unknown `family_id` / fetch failure / parse failure → render the stepped-text path + a one-line RO note (`chromeStrings.figureFallback`), zero console.error. `FigureReveal` dispatches on the **BINDING's** `family_id` — so unknown-family is unreachable for admitted content ONLY because Task 5's fold checks BOTH INV-5.5 halves at admission (instance exists AND `binding.family_id == instance.family_id`) and Task 2's harness reds any instance whose `family_id` lacks a registry entry. Without the family-match half, a family_id-mismatched/typo'd binding would pass admission AND the instance-keyed harness, hit the registry-miss fallback, and ship a silent text-only ghost figure with every gate green (the ghost-component class). The fallback is defense, not a license.

### B. Viz instance serve route — `GET /api/v1/viz/{instanceId}` (NEW frozen contract; R-4b-Q10)

- New file `VizInstanceRoutes.kt` (Lane A), mounted by a ONE-LINE PM patch in `TutorRoutes.kt`'s `installTutorRoutes` (Lane B file — R-4b-Q11 discovered-need path).
- Reply: `ApiVizInstanceReply { id: String, subject: String, family_id: String, language: String, data_json: String }` — exactly the `VizInstance` fields `ContentRepo.loadVizInstances` already parse-validates. 404 unknown id (OMIT). 401 without a session (same cookie check as the other tutor GET routes). Read-only, no DB, no LLM.
- **Lock check:** `interface-signatures-lock.md` has NO existing `/api/v1/viz` entry (verified: zero hits) — this is a NEW freeze, not a deviation. Per the lock's DURABLE RULE the amendment (`§NEW-V`) ships as a patch in the SAME commit as the route code; PM applies at CP-1. Snake_case on the wire (`family_id`, `data_json`) — backend casing wins, consistent with §NEW-L.
- The TS client adds `getVizInstance(instanceId)` to `tutor-web/src/lib/lesson.ts`? NO — keep `lesson.ts` minimal: the fetch lives inside `FigureReveal.tsx` via `jarvisFetch` (one consumer; promote to a lib module only when a second consumer appears).

### C. Trace-match harness + semantic invariants (INV-5.1/5.2, gate 3)

- `traceMatchHarness.test.tsx` (vitest): enumerates EVERY instance via `import.meta.glob("../../../../../../content/*/viz/*.yaml", { query: "?raw", import: "default", eager: true })` (the same out-of-root raw-import mechanism `GraphTreeFamily.test.tsx` already uses — adjust the relative depth at execution and STOP if vite refuses the path rather than silently matching zero files: **the harness REDS if the glob yields 0 instances** — totality is itself asserted).
- Per instance: parse → `family_id` MUST have (a) a registry entry, (b) a registered reference executor, (c) a registered semantic-invariant list — any missing = RED (unregistered family / uncovered instance can never pass silently).
- graph-tree reference executor: derive the algorithm input from the instance root label, run the real mergesort-style reference (`mergesortTrace.ts` generalized: reference states computed from the instance data, never from the rendered output), assert every rendered frame's labels+highlight equal the reference (the existing 1-instance pin, made total).
- Semantic invariants `GRAPH_TREE_INVARIANTS` (new `graphTreeInvariants.ts`, exported WITH the family per INV-5.2 "family-specific list maintained with the family code"), checked in RENDERED SVG coordinates (the family computes its own layout coordinates — they are real geometry, readable in jsdom):
  1. **depth-monotone-y:** for every rendered frame, y(node at depth d+1) > y(node at depth d) — levels render as levels (the "numără nivelurile: log₂ n" teaching point is geometric).
  2. **sibling-x-order:** within a level, rendered x order equals data child order.
  3. **final-state:** the final frame's root label equals the reference execution's final state (rendered, not just in the frame data).
  4. **highlight-existence:** every `highlight` id at step k resolves to a rendered element at step k.
- `GraphTreeFamily` stamps `data-node-id={node.id}` on each rendered node group (additive test hook; no visual change).
- Seeded wrong-trace (INV-9.4 gate-3 seed): committed known-bad fixture `seeded-wrong-trace-mergesort.yaml` (one merge step's delta swapped so a rendered state contradicts the reference). `seededWrongTrace.test.tsx` runs the SAME harness assertion function over it and asserts it THROWS naming the offending step (assert-red shape, `GraderGoldenHarnessTest` precedent). The fixture lives under `__tests__/fixtures/`, NEVER under `content/` (it must not enter admission).

### D. Legibility/contrast method (R-4b-Q4 — defined here, not at execution; gate 4b)

- **DOM text:** for every visible text node in scope, computed `color` vs the EFFECTIVE background = walk up ancestors until the first with fully-opaque background (alpha 1), compositing any translucent layers passed on the way; WCAG 2.1 contrast ratio ≥ 4.5:1.
- **SVG text:** computed `fill` vs the figure PAPER (the rendered svg background) or the nearest opaque ancestor background.
- **Scope:** learner-visible text on the lesson route (inside `[data-testid="lesson-beat-active"]`, the pips, the gate button, the completion screen, the figure svg); EXCLUDE `aria-hidden="true"` decorative elements and zero-area/`visibility:hidden` nodes.
- **Truncation (meaning-hiding, the VIZ-05 class):** any scoped element with `text-overflow: ellipsis` and `scrollWidth > clientWidth`, or an SVG `<text>` whose rendered box exceeds its clip — RED.
- **Determinism:** `emulateMedia({ reducedMotion: "reduce" })` + the locked default theme (the visual-config pattern); assertions run at settled state (after fonts ready — the 4a self-hosted fonts make this deterministic).
- Implementation = `assertLegibility(page, scopeTestId)` in `tutor-web/e2e/helpers/assertLegibility.ts`, same shape as `assertNoClip` (throws with a per-element violation list).

### E. Rendered-RO heuristic (R-4b-Q5; gate 4d / INV-8.4) — shared logic with the admission gate

- Input text is normalized first: strip `«…»` and `‹…›` quoted spans (§8.1 — quoted source spans stay original-language by DESIGN, §0 #10), strip glossary terms (param) and the per-call exempt list, strip numbers/code-ish tokens (`pas 3/8`, identifiers, formulas).
- **EN-vocabulary leg (any string length, checked FIRST):** tokenize the normalized string; lowercase each token and strip the pinned RO inflection suffixes (`-ul`, `-ului`, `-urile`, `-uri`, `-ii`, `-ilor`, `-ele`); any resulting stem in the pinned `EN_VOCAB` list → FAIL regardless of the other legs. `EN_VOCAB` = a fixed ~30-stem known-EN-loanword/vocabulary list (`skeleton`, `heap`, `array`, `loop`, `stack`, `string`, … + the §0.9G `enWords` chrome class DEFINITELY/MAYBE/GUESS/IDK/Save/Reset/Loading/Share — seeded from the same vocabulary class, tuned ONCE in Task 6 calibration alongside the threshold, then pinned). Glossary terms + the per-call exempt list are stripped BEFORE this leg (normalization), so a legitimately-seeded glossary term never reds. This leg is what makes the INV-8.2 flagship `"skeletonul rămâne fix"` reddable AT ALL: a single-EN-loanword leak has EN-stopword ratio 0 and can sit in an otherwise-diacritic'd RO sentence — structurally invisible to both other legs.
- Then, two-sided: a string ≥ 3 words must satisfy **(diacritic presence [ăâîșțĂÂÎȘȚ] anywhere) OR (EN-stopword ratio < threshold)** — EN stopwords = a fixed ~40-word list (the, and, of, to, is, are, with, for, this, that, …), threshold start 0.18, tuned ONCE in Task 6 calibration and then pinned. Strings < 3 words are exempt from the diacritic leg ONLY (the stopword leg still applies — "the heap" stays catchable).
- TS side (`roHeuristic.ts`, e2e) and Kotlin side (`LanguageGate.kt`, admission) implement the SAME normalization + legs with the SAME pinned constants (stopword list, threshold, shortStringWords, `EN_VOCAB`, suffix-strip rules); both cite this section; a divergence is a bug (a cross-language shared-constants test is the carried schema-hash follow-up, Task 12 Step 6 — not built here).
- **Plan-fix F1 note — PM-RATIFIED 2026-06-12:** the EN-vocabulary leg AMENDS R-4b-Q5's two-leg wording. It is required because the ruling's own under-blocking acceptance (the INV-8.2 / spec §8.3 "skeleton"/"skeletonul" seed MUST red) is unsatisfiable on diacritic+stopword alone — ratio 0 is under every threshold, and rewording the fixture into stopword-heavy EN would game the letter of INV-8.2 while the actual leak class ships green. The PM ratified the leg (rulings doc R-4b-Q5 amendment); this plan builds it.

### F. Language admission gate + record (R-4b-Q6; INV-8.1/8.2)

- `LanguageGate.checkRomanianFields(sub, glossaryTerms: Set<String>, exempt: Set<String>): List<ValidationIssue>` — pure, runs over every learner-facing RO field of every KC: `name_ro`, `explanation_ro`, `worked_example_ro`, stems, misconception `label_ro`, and EVERY `beats.ro.*` text field (prompts, option texts+callbacks, statements, choices+feedback, reveal steps text+callout, name definition/invariant/why, check stem). Wired into `validate()` (the validateContent CI chokepoint — closes audit L7's non-blank-only hole).
- Record at ADMISSION/SYNC (never serve): new `LanguageCheckTable` (`language_check`: `kc_id`, `field`, `lang`, `status` pass|fail, `detail` nullable, `validator_version`, `checked_at`; PK (kc_id, field, lang)). `ContentReconcile.reconcile` writes/updates one row per checked field in the same transaction that admits the KC. INV-8.1's query stays satisfiable: every served field passes admission ⇒ "served fields lacking a record → 0 rows". The serve route is NOT touched (Lane B exclusive).
- Migration: `LanguageCheckTable` registration = PM patch, applied behind the INV-3.1 backup gate on BOTH DBs (fresh off-box dumps verified 2026-06-12, ruling). Honest note: the LIVE DB has 0 `kc_verification_status` rows (trust-net never run in production) — INV-8.1 is vacuously 0-row on live until the trust-net flip; the CI proof runs on the seeded DB.
- Acceptance is two-sided (R-4b-Q5): **(over-blocking guard)** zero false reds across ALL currently-admitted corpus RO fields — the full `validateContent` over `content/` stays green, in particular the 4 authored faithful KCs with their embedded «EN quotes»; **(under-blocking guard)** the INV-8.2 seeded set ALL red: QR-EN-LEAK `"skeleton"/"skeletonul"` rendered-audit case, ≥2 EN drill stems and ≥2 EN grader-feedback strings from the SESSION-55/56/57 class — committed as test fixtures inside `LanguageGateSeededRegressionTest`, never under `content/`.

### G. chromeStrings + EN-grep (R-4b-Q7; INV-8.3)

- `tutor-web/src/lib/chromeStrings.ts` — Lane A's strings file: `shellLabels: ShellLabels` (RO: `frame:"Cadru"`, `reset:"resetează"`, `share:"🔗 distribuie"`, `voiceOn:"🔊 voce pornită"`, `voiceOff:"🔇 voce oprită"`, `predict:"⚡ Prezice"`, `play:"▶ redă"`), `figureFallback`, and the swept chrome of the 8 Task-7 components. `lessonStrings.ts` STAYS (lesson-surface strings, Plan-3 ownership); `practiceStrings.ts` is Lane B's (DrillStack + practice) — no shared strings file.
- `tools/chrome-scope.json` — the explicit R-4b-Q7 allowlist: `files` (the scoped component paths — the 8 swept components + the `lesson/` components + `OggiScreen.tsx` + **`tutor-web/src/components/practice/*.tsx`**: spec INV-8.3 names PRACTICE surfaces and the ruling's exclusion list does NOT exclude them; the practice files don't exist yet — the tool tolerates missing/unmatched `files` entries (Task 7 Step 4) and Lane B creates them already-swept against `practiceStrings.ts`, so they enter the grep at Lane B merge with ZERO scope edits; **NOT** `viz/` demo gallery components, **NOT** anything under `src/door/`, **NOT** test files), `excluded` (documenting key, each entry WITH its re-entry owner): `DrillStack.tsx` — it EXISTS today with unswept EN chrome and Lane B sweeps it in Plan 6; the PM widens `files` to include `DrillStack.tsx` at Lane B merge, a NAMED action in `PM-APPLY-MANIFEST.md` (Task 12 Step 5) — INV-8.3's practice half is deferred-with-an-owner, never silently dropped; `enWords` (the detector vocabulary incl. the catalogued DEFINITELY/MAYBE/GUESS/IDK/Save/Reset/Loading/Share class), `allowPatterns` (data-testid values, `className` strings, import paths, code identifiers, console/error strings not learner-visible).
- `tools/chrome-en-grep.mjs` — node script: for each scoped file, extract JSX text nodes + learner-visible string attribute values (`title`, `placeholder`, `aria-label`, `alt`); flag literals containing `enWords` tokens or ≥3-word EN-stopword-heavy runs, unless the literal is imported from `chromeStrings.ts` / `practiceStrings.ts` / `lessonStrings.ts` or matches `allowPatterns`. Exit 1 with a file:line listing on any hit. CI step (in `test-yml-plan4b.patch`) runs it in the frontend job, BLOCKING (it is calibrated by construction: green is achieved in Task 7 before the patch is delivered).

### H. RetryBudget (R-4b-Q3; §9.1; INV-9.2)

```kotlin
/** Spec §9.1 — "the retry budget is a constant in config, not a vibe." Consumed by Plan 5's
 *  digestion pipeline (R-4b-Q3 split: seam + proof here, REJECT-loop UI with Plan 5's
 *  checkpoint screen). NOT dead config — Plan 5 builds against exactly these names. */
object RetryBudget {
    /** §9.1 row (a): machine gate red → ≤3 self-retries, failure data fed back; still red → park. */
    const val MACHINE_GATE_SELF_RETRIES: Int = 3
    /** §9.1 row (b): user REJECT #1 → exactly one re-generation incorporating the one-line note. */
    const val USER_REJECT_REGENERATIONS: Int = 1
    /** §9.1 row (c): REJECT #2 on the same artifact → STOP (checkpoint-design-review, §10.3). */
    fun rejectAction(rejectCount: Int): RejectAction =
        if (rejectCount <= 1) RejectAction.REGENERATE_ONCE else RejectAction.STOP_DESIGN_REVIEW
}
enum class RejectAction { REGENERATE_ONCE, STOP_DESIGN_REVIEW }
data class GateAttempt(val attempt: Int, val failureData: String)
/** Park-as-gap-record seam: Plan 5 wires its gap-record store; tests use an in-memory sink. */
fun interface GapRecordSink { fun park(artifactId: String, gate: String, attempts: List<GateAttempt>) }
class RetryLoop(private val budget: Int, private val sink: GapRecordSink) {
    /** Runs [attempt] with the prior failure fed back (§9.1 row a); parks at budget exhaustion. */
    fun <T> run(artifactId: String, gate: String, attempt: (priorFailure: String?) -> Result<T>): T?
}
```

- INV-9.2 (`RetryBoundednessTest`): an always-failing gate runs EXACTLY `MACHINE_GATE_SELF_RETRIES` attempts then parks (sink receives the full attempt list, size == budget — a 4th attempt is structurally impossible and asserted); a gate succeeding on attempt 2 → 2 attempts, no park; failure data of attempt k is fed into attempt k+1 (asserted via the lambda's argument); `rejectAction(1)==REGENERATE_ONCE`, `rejectAction(2)==STOP_DESIGN_REVIEW`, `rejectAction(3)==STOP_DESIGN_REVIEW` (never a third regeneration).

### I. INV-9.4 seeded-violation drills, gates 3+4 (assert-red shape, never polluting `content/`)

| Seed | Gate it reds | Mechanism (Task) |
|---|---|---|
| wrong-trace figure (swapped merge delta) | 3 | harness assert-throws over the committed bad fixture (Task 2) |
| clipped label (600-char unbroken token in a reveal step) | 4a | stubbed lesson from `fixtures/seeded-violations/clip.json` → `assertNoClip` must throw (Task 8) |
| label below 4.5:1 (the QR-CONTRAST-ZERO class) | 4b | stubbed lesson + injected `addStyleTag` low-contrast override → `assertLegibility` must throw (Task 8) |
| broken interaction (next-gate not locked) | 4c | stubbed lesson + `page.evaluate` strips the `disabled` attr pre-gate → `assertNextGateContract` must throw (Task 8) |
| EN-in-RO field (incl. "skeletonul") | 4d | stubbed lesson from `fixtures/seeded-violations/en-in-ro.json` → `roHeuristic` must flag (Task 8) + the Kotlin INV-8.2 set (Task 6) |

(Garbled PDF = Plan 5 / gate 1; unfaithful KC = done, Plan 1 / gate 2; broken golden answer = done, Plan 4a / gate 7.) All five drills run inside the normal CI suites (vitest + `npm run e2e`) — a gate red on its seed = the drill test PASSES; a drill failing = the gate is dead = CI red (the §9.3 "gates are alive" proof).

### J. Real-backend CI architecture (R-4b-Q2 option (a), spec letter)

- **Seeder** `jarvis.tutor.e2e.E2eSeed` (`fun main`): (1) `TutorDb.connect("build/e2e/tutor.db")` (fresh file; delete if exists) + `TutorMigration.migrate` + `SchemaUtils.create(LanguageCheckTable)`; (2) `ContentRepo(Path.of("content"))` → `ContentValidator.validate` over all subjects (the REAL admission path — abort non-zero if invalid); (3) `ContentReconcile.reconcile` (claims + pending + language-check records); (4) promote every beat-complete KC to `faithful` with the REAL `kcContentHash(kc)` + REAL `sourceSpanHashOf(claimsFor(kc), repo::sourceText)` (the `DrillGradeAtomicTest.seedFaithful` pattern — this stamps what `VerificationRunner` would, deterministically, zero LLM); (5) seed one user + session (`SessionRepo` insert, sid `e2e-lesson-gates`, far expiry) + FSRS/queue rows for EVERY promoted KC (Plan2Seed/FsrsGradeRouteTest patterns) so the handoff target has data; (6) write `build/e2e/seed.json` `{ "sid": "...", "kcIds": [...], "figureKcIds": [...] }` — `kcIds` is **DERIVED from step (4)'s promoted set** (every beat-complete KC; resolves to `pa-kc-001..004` today, NEVER a hardcoded literal — the seeder aborts non-zero if the set is empty) and `figureKcIds` is the subset whose beats bind a `reveal.figure` (resolves to `["pa-kc-002"]` today; non-empty by Task 5's non-vacuity — abort non-zero if empty). When Plan 5/6 admits a 5th beat-complete KC or a 2nd figure binding, the seed manifest — and therefore the gate's coverage — grows with ZERO seeder/spec edits (the same no-silent-under-coverage standard Task 2's harness sets; INV-4.4 "every animated" / INV-5.3 "every live instance" are quantified claims, not enumerations).
- **Gradle:** JavaExec task `seedE2eDb` (mainClass `jarvis.tutor.e2e.E2eSeedKt`, classpath main runtime) — `build-gradle-seed-e2e.patch`, PM-merge-only.
- **CI job** (in `test-yml-plan4b.patch`): ubuntu; JDK + Node setups copied verbatim from the existing backend/frontend jobs; `npm ci` + chromium install in `tutor-web`; `gradle --no-daemon :seedE2eDb`; boot `JARVIS_TUTOR_DB=$PWD/build/e2e/tutor.db JARVIS_CONTENT_DIR=$PWD/content JARVIS_PORT=8080 gradle --no-daemon run --args=web` in the background, redirecting to `build/e2e/server.log`; readiness = curl loop until `/api/v1/tutor/auto-session` returns ANY HTTP status (401 = up; 60×2 s budget, then `cat server.log` + fail); then `REAL_BACKEND=1 npx playwright test e2e/lesson-gates.spec.ts` in `tutor-web` (the existing `playwright.config.ts` `webServer: npm run dev` starts vite, which proxies `/api` → 8080 — verified §0 #7; zero config edits); upload `server.log` + playwright report on failure.
- **Spec gating:** `lesson-gates.spec.ts` begins with `test.skip(!process.env.REAL_BACKEND, "real-backend only (CI lesson-gates job / local recipe in the spec header)")` — the default `npm run e2e` (frontend job, no JVM) skips it honestly; NO `playwright.config.ts` edit. The spec reads `../build/e2e/seed.json`, injects the `jarvis_session` cookie (`secure: false`, localhost) before navigation, and never stubs any route.
- Existing stubbed specs stay as-is (ruling, verbatim).

---

## Execution protocol (two-stage, per task)

1. **Implementer subagent** executes the task's steps exactly; TDD where the task marks it (test first, watch it fail, then implement); commits to `main` with explicit paths only (never `git add -A` — §0 #15); SHARED files only ever as patch files.
2. **Independent reviewer subagent** (fresh context, given ONLY this plan section + the diff): re-runs every acceptance command itself (never trusts the implementer's claimed green — review-workflow rule), checks the diff touches ONLY the task's listed files, checks the lock-check line where present, and adversarially compares behavior to the contract. Findings → implementer fixes → reviewer re-verifies.
3. Any plan/reality mismatch (an anchor that doesn't exist, a frozen signature that conflicts, a file the lane table forbids): **STOP — blocked task, escalate to the PM verbatim.** Executors never edit this plan doc.
4. Per-task gate: the FULL relevant suite (backend tasks: `gradle --no-daemon :check`; frontend tasks: `npm --prefix tutor-web test` and, where the task touches e2e, `npm --prefix tutor-web run e2e`) — never pipe through `| tail`, never partial-run-and-claim.
5. Model routing: implementers default Sonnet-class; the reviewer for Tasks 5, 6, 9 (content faithfulness / admission semantics / CI architecture) Opus-class; trivial tasks (10, 11) Sonnet both stages.

## Tasks

| # | Task |
|---|---|
| 0 | Delta-recon probe in the main tree (no edits): HEAD, baselines green, R-4b-Q8 re-check, manifest publish for the zero-intersection probe |
| 1 | `LessonErrorBoundary` + `BeatOrchestrator` wrap (R8; TDD) |
| 2 | Trace-match harness totality + graph-tree semantic invariants + seeded wrong-trace red (INV-5.1/5.2 + INV-9.4 gate-3; TDD) |
| 3 | Viz instance serve route + route test + mount patch + §NEW-V lock patch (§0.9B) |
| 4 | Shell `play` control + family `onStep` + `FigureReveal` + `RevealBeat` mount + `chromeStrings.ts` seed (§0.9A; TDD) |
| 5 | Figure binding on `pa-kc-002` + INV-5.5 fold into `validate()` + anti-vacuity + accommodation ledger (R-4b-Q1) |
| 6 | Language admission gate + INV-8.2 seeded set + calibration + `LanguageCheckTable` record at reconcile + migration patch (§0.9E/F; TDD) |
| 7 | Chrome RO sweep (8 components, NOT DrillStack) + `chrome-en-grep` tool + scope file + CI step in the combined patch (§0.9G) |
| 8 | Rendered-gate helpers (`assertLegibility`, `roHeuristic`, `assertNextGateContract`) + the four gate-4 seeded-violation drills (§0.9D/E/I) |
| 9 | `E2eSeed` + `lesson-gates.spec.ts` on the real server/corpus + gradle/CI patches (§0.9J; INV-4.4/5.3/8.4, gates 4a–4d) |
| 10 | `RetryBudget` + park seam + INV-9.2 boundedness test (§0.9H) |
| 11 | `viz/theme.ts` INK/PAPER fix under the gates' cover |
| 12 | Whole-plan final gate (FULL suites) + PM-apply manifest + plan-index patch + re-carries |

---

## Task 0 — Delta-recon probe (main tree, NO edits)

**Files:** none created/modified (report-only).

- [ ] Step 1: `git rev-parse --abbrev-ref HEAD` → `main`; `git log --oneline -1` → `bab5d3c` or a descendant. `git status --short` → only the known untracked artifacts (door demos, build-review, etc.); NO modified tracked source. **STOP if** tracked source is dirty.
- [ ] Step 2: Re-assert §0 anchors that gate later tasks: `RevealBeat.tsx:50` figure stub present; `familyRegistry.ts` single entry; `checkFigureBindings` absent from `validate()`; `GlossaryTermsTable` write-sites still zero; `content/*/viz/*.yaml` glob still = 1 file; `e2e/visual/` contains ONLY the 3 specs + `__screenshots__/` (R-4b-Q8 re-check). **STOP on any mismatch** — escalate, don't improvise.
- [ ] Step 3: Baseline gates, all green before any 4b edit (attributability): `gradle --no-daemon :check` (if `stateCacheConcurrentPersistNeverTearsJson` reds, re-run once and NAME it in the task report — R-4b-Q9, never absorb); `npm --prefix tutor-web test`; `npm --prefix tutor-web run e2e`; `npm --prefix tutor-web run e2e:visual` (3 passed). **STOP and report** any other red — do not build on a red baseline.
- [ ] Step 4: Publish this plan's §0.8 CREATE+MODIFY lists to the PM for the plan-freeze zero-intersection machine check against Plan 6's manifest (rulings: intersection must be ZERO excluding SHARED; one fix round max, then PM halt).

**Acceptance:** all four suites green (or the named flake), anchors confirmed, manifest delivered.

---

## Task 1 — `LessonErrorBoundary` + `BeatOrchestrator` wrap (R8)

The carried follow-up, verbatim (plan-index line 19): "BeatOrchestrator has NO error boundary (malformed lesson payload unmounts the whole lesson UI — found by the final-gate stub bug 2026-06-12; add a boundary in Plan 4b's rendered-gate work)". Gate 4c (zero console errors, zero unmount failures) is not claimably green without it.

**Files:** Create `tutor-web/src/components/lesson/LessonErrorBoundary.tsx`, `LessonErrorBoundary.test.tsx`. Modify `BeatOrchestrator.tsx` (wrap the active-beat body), `BeatOrchestrator.test.tsx` (add cases).

- [ ] Step 1 (TDD): write `LessonErrorBoundary.test.tsx` FIRST — a child component that throws on render: (a) the boundary catches (no test-level unhandled error), (b) the RO fallback paints (`data-testid="lesson-beat-error"`, text from `lessonStrings`-style constant — add `beatError: "Ceva n-a mers la acest pas — încearcă să reîncarci lecția."` to the boundary's local strings or `lessonStrings` if Plan-3 ownership allows; if `lessonStrings.ts` must not be edited, keep the string IN the boundary file with an INV-8.3 note — decide by checking whether `lessonStrings.ts` is in any Lane-B manifest: it is NOT → add the key to `lessonStrings.ts`), (c) sibling lesson chrome OUTSIDE the boundary (pips) stays mounted. Run → red.
- [ ] Step 2: implement the class component (`getDerivedStateFromError` + `componentDidCatch` logging via `console.warn` NOT `console.error` — gate 4c asserts zero console errors; a caught render error is handled, not silent: the fallback is visible) and wrap ONLY the per-beat body inside `BeatOrchestrator` (pips, gate button, completion screen stay outside). `key={beatIndex}` on the boundary so advancing beats resets the error state. No prop/contract change (lock check not required — no wire surface touched).
- [ ] Step 3: `BeatOrchestrator.test.tsx` — add: a beat that throws does not unmount `lesson-beat-pips`; the next-gate stays disabled while the fallback shows (a crashed beat must not open the gate).
- [ ] Step 4: full `npm --prefix tutor-web test` green; commit (explicit paths).

**Acceptance:** new tests green; full vitest green; reviewer confirms a thrown beat leaves pips mounted + gate locked, and that no `console.error` escapes.

---

## Task 2 — Trace-match harness totality + semantic invariants + seeded wrong-trace (INV-5.1/5.2, INV-9.4 gate 3)

**Files:** Create `graphTreeInvariants.ts`, `__tests__/traceMatchHarness.test.tsx`, `__tests__/seededWrongTrace.test.tsx`, `__tests__/fixtures/seeded-wrong-trace-mergesort.yaml`. Modify `GraphTreeFamily.tsx` (stamp `data-node-id`, additive).

- [ ] Step 1 (TDD): write `traceMatchHarness.test.tsx` per §0.9C: glob-enumerate `content/*/viz/*.yaml`; assert ≥1 instance found (totality self-check); per instance dispatch `{referenceExecutor, invariants}` from a registry keyed by `family_id`; unregistered family or missing executor/invariants = explicit fail with the instance id. Reference path: generalize the existing `mergesortTrace.ts` approach — compute reference states FROM the instance's root data, never by reading the rendered frames. Run → red (invariants not yet implemented).
- [ ] Step 2: implement `graphTreeInvariants.ts` (the 4 invariants of §0.9C, operating on the mounted family DOM via `data-node-id` + SVG x/y attributes) and stamp `data-node-id` in `GraphTreeFamily`'s renderFrame (additive attribute only — `npm --prefix tutor-web run e2e:visual` must stay 3/3 after this task; the harness must not change pixels).
- [ ] Step 3: harness green over the real corpus (currently 1 instance — the totality mechanics are what this task ships; instance count grows in Plan 5/6 with zero harness edits).
- [ ] Step 4: seeded wrong-trace — author the bad fixture (copy `viz-pa-mergesort-001.yaml`, swap one merge delta's labels so step 6's rendered state contradicts the reference), `seededWrongTrace.test.tsx` asserts the harness assertion function THROWS and the message names the step index. The fixture path is under `__tests__/fixtures/` — verify `ContentRepo.loadVizInstances` cannot see it (it globs `content/{subject}/viz/` only).
- [ ] Step 5: full vitest + `e2e:visual` green; commit.

**Acceptance:** harness reds on (a) zero instances, (b) unregistered family, (c) the seeded fixture; green on the real corpus; visual baselines untouched; reviewer re-runs all three red-paths by temporary local mutation (not committed).

---

## Task 3 — Viz instance serve route (§0.9B) + §NEW-V lock patch + mount patch

**Files:** Create `VizInstanceRoutes.kt`, `VizInstanceRouteTest.kt`, `build-review/tmp/lane-a-patches/tutor-routes-viz-mount.patch`, `…/signatures-lock-viz-route.patch`. Modify `ContentRepo.kt` (additive `loadAllVizInstances()`).

**Lock check (R-4b-Q10):** grep `interface-signatures-lock.md` for `api/v1/viz` → expected ZERO hits (verified at plan-write). If a hit exists at execution → STOP, PM. The new §NEW-V freeze ships as a patch in the SAME commit as the route (the lock's DURABLE RULE); reply shape exactly §0.9B; snake_case wire.

- [ ] Step 1 (TDD): `VizInstanceRouteTest.kt` (testApplication pattern from `LessonServeRouteTest`): 200 + exact JSON fields for `viz-pa-mergesort-001` (id/subject/family_id/language/data_json verbatim from the YAML); 404 unknown id; 401 no session. Run → red.
- [ ] Step 2: implement `installVizInstanceRoutes()` in the new file (session-cookie check copied from the neighboring tutor GET routes; `ContentRepo(groupThree-style content dir).loadAllVizInstances()` — add that additive helper to `ContentRepo.kt` looping its existing per-subject `loadVizInstances`); no DB, no LLM.
- [ ] Step 3: produce `tutor-routes-viz-mount.patch` — ONE line adding `installVizInstanceRoutes()` inside `installTutorRoutes` (generate with `git diff` from a THROWAWAY local edit, then `git checkout -- src/main/kotlin/jarvis/web/TutorRoutes.kt` so the tracked file is untouched; verify `git apply --check` passes against the clean tree). Produce `signatures-lock-viz-route.patch` the same way (§NEW-V section: route, gate, reply shape, 404/401 semantics, the one-consumer note).
- [ ] Step 4: `gradle --no-daemon :check` green (the route test exercises the route via its own install — it must not depend on the unapplied mount patch; mount-dependent behavior is Task 9's). Commit code + patches together.
- [ ] Step 5: **PM checkpoint CP-1** — request PM apply of both patches; Task 9 is blocked on CP-1 (Tasks 4–8 are not).

**Acceptance:** :check green; both patches `git apply --check`-clean; reviewer confirms the reply field names against §0.9B byte-for-byte and that no Lane-B file was edited.

---

## Task 4 — Shell play control + family `onStep` + `FigureReveal` + `RevealBeat` mount + `chromeStrings.ts` seed (§0.9A)

**Files:** Create `FigureReveal.tsx`, `FigureReveal.test.tsx`, `tutor-web/src/lib/chromeStrings.ts`. Modify `AlgoStepperShell.tsx` (additive `play`), `familyRegistry.ts` (additive `onStep`), `GraphTreeFamily.tsx` (thread `onStep`), `RevealBeat.tsx` (mount + echo-banner move).

**Lock check (R-4b-Q10):** this task CONSUMES `ApiBeatReveal.figure` / `ApiFigureBinding` exactly as frozen in lock §NEW-L (`{family_id, instance_id}`, snake_case) and the §0.9B reply — zero wire changes. Cite §NEW-L in the FigureReveal header comment. Any needed payload change → STOP, PM.

- [ ] Step 1 (TDD): `FigureReveal.test.tsx` FIRST — mock `jarvisFetch` to return the REAL `viz-pa-mergesort-001` reply (raw-import the YAML, mirror §0.9B); assert: family mounts (`graph-tree-root`); `scrubber-step-counter` shows `pas 1/8`; stepping forward updates the counter; **back control exists and functions** (forward → back → counter decrements — the INV-4.4 one-shot kill); reset returns to `pas 1/8` AFTER reaching the final frame (replay possible — the DIJ-ONESHOT class is structurally dead); `onGateClear` fires exactly once when the final frame is first reached; unknown family / fetch reject → stepped-text fallback renders + `chromeStrings.figureFallback` visible, no thrown error. Run → red.
- [ ] Step 2: `AlgoStepperShell.tsx` — additive `{testIdPrefix}-play` button: toggles a bounded interval stepping to the last frame then auto-stops; label `labels?.play ?? "▶ play"` (`ShellLabels` gains optional `play`); honors reduced motion (steps still advance; MotionConfig already suppresses animation); demo gallery visuals unchanged otherwise — `npm --prefix tutor-web run e2e:visual` must stay 3/3 (the baselines don't render the shell; `family-no-clip.spec.ts` must stay green at both viewports with the extra button).
- [ ] Step 3: `familyRegistry.ts` — additive `onStep?: (idx: number, lastIdx: number) => void` on `FamilyRendererProps`; `GraphTreeFamily` threads it to the shell's `onStep` (computing `lastIdx` from its frames).
- [ ] Step 4: implement `FigureReveal` per §0.9A (fetch via `jarvisFetch("/api/v1/viz/" + encodeURIComponent(instance_id))`; registry dispatch; `labels = chromeStrings.shellLabels`; wrapper `data-testid="beat-figure-scrubber"`; own `scrubber-step-counter` `pas k/N`; gate on final frame; fallback = the stepped-text renderer extracted from `RevealBeat` or duplicated minimally — prefer extracting the stepped-text JSX into a local `SteppedTextReveal` used by both paths so the fallback IS the real text path).
- [ ] Step 5: `RevealBeat.tsx` — predict echo banner moves above the branch; the figure branch becomes `<FigureReveal figure={reveal.figure} steps={reveal.steps} predictedOption={…} onGateClear={onGateClear} />`; stepped-text path byte-identical behavior (its existing tests must pass unchanged).
- [ ] Step 6: create `chromeStrings.ts` with `shellLabels` (RO set, §0.9G values) + `figureFallback: "Figura nu s-a putut încărca — pașii sunt afișați ca text."`. Learner-visible strings RO; identifiers EN.
- [ ] Step 7: full vitest + `npm --prefix tutor-web run e2e` + `e2e:visual` green; commit.

**Acceptance:** all Step-1 assertions green; `lesson-beats.spec.ts` untouched and green (stepped-text path unaffected); reviewer verifies the §4.7 selector set renders in figure mode (`beat-figure-scrubber`, `scrubber-step-counter` "pas k/N", back/play/forward/reset present) and that NO second `beat-figure-scrubber` exists in figure mode.

---

## Task 5 — Figure binding on `pa-kc-002` + INV-5.5 fold + anti-vacuity + the accommodation ledger (R-4b-Q1)

Binds `viz-pa-mergesort-001` (graph-tree) to `pa-kc-002`'s reveal — chosen over the other three served KCs because the instance literally renders the (intrare, ieșire) pair the KC teaches: root `5 2 8 1 9 3` = configurația de start, final `1 2 3 5 8 9` = configurația finală (the KC's own check beat uses "sortează o listă de numere"). NOT `pa-kc-001`: the e2e fixture pins pa-kc-001 anchors verbatim — binding there would force fixture surgery on a Plan-3-owned spec (ledger item 3 instead verifies no update is needed).

**Files:** Modify `content/PA/kcs/pa-kc-002.yaml` (the ONE line: `figure: null` → `figure: { family_id: graph-tree, instance_id: viz-pa-mergesort-001 }`), `ContentValidator.kt` (fold), `ContentCli.kt` (thread ids), `FigureBindingValidationTest.kt` (extend). Create `FigureBindingNonVacuityTest.kt`, `FigureBindingHashNoRippleTest.kt`.

**Lock check (R-4b-Q10):** the binding changes a payload VALUE, not a shape — `ApiFigureBinding` is already frozen (§NEW-L) and `ApiLessonReply` is unchanged. `validate()` signature gains `knownInstances` (id→family_id map) — `validate()` is NOT a frozen wire surface (lock covers routes/payloads; grep the lock for `validate(` → zero hits, verified) but IS called by the validateContent chokepoint: thread ALL callers in the same commit (accommodation, [[feedback_account_for_accommodations]]).

**Accommodation ledger (R-4b-Q1, executed as steps — each line is verified, not assumed):**

1. **Faithfulness-hash ripple:** `FigureBindingHashNoRippleTest` — load `pa-kc-002`, compute `ContentReconcile.kcContentHash` + `claimsFor` with the figure binding present vs a copy with `figure = null`: claims and hash MUST be identical (beats don't feed `claimsFor`, §0 #4 — this test PINS that, so a future claimsFor change that starts hashing beats turns this red and forces the re-verification the ruling demands). If red at execution → STOP, PM: the edit then requires backup → re-run admission verification before serve, per ruling.
2. **Off-box dump precondition:** this task mutates ZERO databases (YAML + Kotlin only). Recorded: fresh off-box dumps exist (2026-06-12, ruling); the only live-DB mutation in Plan 4b is Task 6's PM-applied migration at CP-3 behind the INV-3.1 backup gate.
3. **`lesson-beats.spec.ts` fixture anchors:** the fixture pins `pa-kc-001` — binding lands on `pa-kc-002`, so NO fixture update is needed; VERIFY by re-running `npm --prefix tutor-web run e2e` (lesson-beats green, fixture diff empty).
4. **Live serve ripple:** live DB has 0 `kc_verification_status` rows (trust-net not yet run in production) — `pa-kc-002` serves 404 on live regardless; the binding goes live only when the trust-net flip lands (out of 4b scope, recorded in the PM manifest).

- [ ] Step 1: ledger item 1 — write + run `FigureBindingHashNoRippleTest` (green = no ripple).
- [ ] Step 2 (TDD for the fold): extend `FigureBindingValidationTest` — (a) a KC binding a NONEXISTENT instance must now red `validate()` itself (not just the standalone check); (b) a KC binding a REAL instance id with a MISMATCHED `family_id` (e.g. `family_id: sequence-array` — or a typo — on `viz-pa-mergesort-001`) must ALSO red `validate()`: the INV-5.5 family-REGISTRY half at admission. `FigureReveal` dispatches on the BINDING's family_id (§0.9A), so a family mismatch that passes admission ships a silent text-fallback ghost — the frontend harness keys off the INSTANCE's family_id and can never see it. Run → red. Fold `checkFigureBindings` into `validate()`, upgrading its existing `knownInstanceIds: (String) -> Set<String>` param to a MAP — `knownInstances: (subject: String) -> Map<String, String>` (instance id → family_id; no default that silently passes — compile-forcing); the check asserts the binding's `instance_id` is a key AND the binding's `family_id` equals the mapped value; thread `ContentRepo.loadVizInstances(...)` as an id→family_id map at every caller (`ContentCli.kt` + any test caller the compiler flags). Run → green.
- [ ] Step 3: edit `pa-kc-002.yaml` (the one line). Run `gradle --no-daemon :check` — `validateContent` green WITH the fold active proves the binding resolves (INV-5.5 alive on the real corpus).
- [ ] Step 4: `FigureBindingNonVacuityTest` — loads all subjects; asserts ≥1 beat-complete KC binds a `reveal.figure` (the R-4b-Q1 anti-vacuity totality assert: if every binding is ever removed, CI reds — figure gates can never silently go vacuous again). ALSO asserts the bound instance id exists in the corpus.
- [ ] Step 5: ledger items 3+4 — run the e2e suite (lesson-beats green, no fixture diff); record the ledger results verbatim in the commit message body.
- [ ] Step 6: full `:check` + vitest + e2e green; commit (YAML + Kotlin + tests, one commit, ledger in the message).

**Acceptance:** all four ledger items verified with evidence; `:check` green including the fold + non-vacuity; reviewer independently recomputes the hash-no-ripple result, re-runs BOTH Step-2 red paths (dangling instance id AND family_id mismatch on a real id), and confirms `pa-kc-001.yaml`/fixture untouched.

---

## Task 6 — Language admission gate + record at reconcile + migration patch (§0.9E/F; INV-8.1/8.2; audit L7)

**Files:** Create `LanguageGate.kt`, `LanguageGateTest.kt`, `LanguageGateSeededRegressionTest.kt`, `Plan4bTables.kt`, `LanguageCheckRecordTest.kt`, `migration-language-check.patch`. Modify `ContentValidator.kt` (wire into `validate()`), `ContentReconcile.kt` (record write-site).

**Lock check (R-4b-Q10):** ZERO wire changes — admission-side only (R-4b-Q6: serve route untouched, Lane B exclusive). `LanguageCheckTable` is a NEW table: additive, not pinned by `SignatureLockPinTest` (pins existing shapes) nor `TrustInvariantsCli`'s column allowlist (checks only named tables — verified §0 #9 pattern). Migration registration = PM patch only.

- [ ] Step 1 (TDD): `LanguageGateTest` — the §0.9E normalization+legs over hand-built cases: RO with diacritics → pass; RO-without-diacritics short ("pas 3/8") → pass (diacritic leg exempt); 3+ word EN ("the heap property holds") → fail; RO framing with «EN quote» → pass (quote stripped); glossary-term-only string → pass; mixed RO+EN trailing EN sentence → fail; RO-inflected EN loanword in an otherwise-RO sentence — BOTH `"skeletonul rămâne fix"` (diacritics present) AND `"skeletonul ramane fix"` (diacritics absent) → fail via the EN-vocabulary leg (EN-stopword ratio 0 — the other two legs structurally CANNOT catch this class, §0.9E F1 note); the same string with the loanword glossary-exempted → pass (normalization strips it before the leg). Run → red; implement `LanguageGate` (pure object, pinned constants: stopword list + threshold 0.18 + shortStringWords=3 + `EN_VOCAB` stem list + RO suffix-strip rules, all named consts cited to §0.9E).
- [ ] Step 2: `LanguageGateSeededRegressionTest` (INV-8.2, the under-blocking guard) — the seeded set as in-test fixtures: QR-EN-LEAK ("skeletonul ramane fix" framing the EN "skeleton" leak — reds via the EN-vocabulary leg; the fixture text is NEVER reworded into stopword-heavy EN to make it pass another leg, that would gut the gate), ≥2 EN drill stems, ≥2 EN grader-feedback strings (SESSION-55/56/57 class). ALL must produce `error`-severity issues. ASCII test method names (§0 #15).
- [ ] Step 3 (calibration, the over-blocking guard): wire `LanguageGate.checkRomanianFields` into `validate()` (after `checkBilingual`), then run `gradle --no-daemon :check` over the REAL corpus (all 5 subjects). ANY red on currently-admitted content = calibration failure: tune threshold/exempt-list/`EN_VOCAB` ONCE (documented in the commit message with the offending strings), re-run. Acceptance is BOTH: corpus green AND Step-2 set red. If the two cannot hold simultaneously after one tuning round → STOP, PM (the heuristic needs a design decision, not silent threshold creep).
- [ ] Step 4: `Plan4bTables.kt` — `LanguageCheckTable` per §0.9F. `ContentReconcile.recordLanguageChecks(db, sub)` — additive function called inside `reconcile`'s transaction: upsert one row per checked field per KC (status from the gate result; `validator_version` = a `LanguageGate.VERSION` const). Tests create the table via `SchemaUtils.create` (the LessonServeRouteTest freshDb pattern) — production gets it only via the PM-applied migration.
- [ ] Step 5: `LanguageCheckRecordTest` (INV-8.1 shape) — fresh DB + real corpus: run reconcile; query "KCs with a `kc_verification_status` row whose beat-complete RO fields lack a `language_check` row" → 0; delete one record row → the query returns 1 (the invariant detects holes).
- [ ] Step 6: produce `migration-language-check.patch` (Plan4bTables registration in `Migration.kt`'s table list — throwaway-edit + `git diff` + checkout, as Task 3 Step 3; `git apply --check` clean). Live application = CP-3, PM, INV-3.1 backup gate, BOTH DBs (dumps fresh 2026-06-12); live INV-8.1 is honestly vacuous (0 status rows) until the trust-net flip — say so in the patch header.
- [ ] Step 7: full `:check` green; commit (code + patch together).

**Acceptance:** two-sided calibration holds (corpus green + seeded set red); record-invariant test proves both directions; reviewer re-runs `:check` and spot-checks 3 corpus RO fields' records in the test DB.

---

## Task 7 — Chrome RO sweep + `chrome-en-grep` (§0.9G; INV-8.3; R-4b-Q7)

**Files:** Create `tools/chrome-en-grep.mjs`, `tools/chrome-en-grep.test.mjs`, `tools/chrome-scope.json`. Modify `chromeStrings.ts` (extend) + the 8 components (`Scratchpad`, `ScratchpadDrawer`, `TaskQuickStart`, `ConceptDrawer`, `KnowledgeLedger`, `Sidekick`, `ChatPane`, `TrustSettings`) + the §0.8 `[lockstep]`-enumerated test files (the complete 12-path import-grep set — Sidekick has 3, ChatPane's is `ChatPaneEnvelopes.test.tsx`; only those asserting swept literals actually change; assert via the strings-file import, never re-hardcoding). **DrillStack.tsx and its 7 test files are NOT touched** (Lane B moves its chrome to `practiceStrings.ts` — lane table, verbatim).

- [ ] Step 1: inventory pass — for each of the 8 components, list every learner-visible EN literal (JSX text, `title`, `placeholder`, `aria-label`, `alt`). Move each to `chromeStrings.ts` with an RO value (translation register: short, imperative, consistent with `lessonStrings.ts` tone; learner-visible RO / identifiers EN). Update the components to import; update their tests in lockstep (assert on the new RO strings via the strings file import, never re-hardcoding).
- [ ] Step 2: `tools/chrome-scope.json` per §0.9G — `files` = the 8 components + `tutor-web/src/components/lesson/*.tsx` + `OggiScreen.tsx` + `tutor-web/src/components/practice/*.tsx` (not-yet-existing — tolerated-missing per Step 4; they enter the grep automatically at Lane B merge, created pre-swept against `practiceStrings.ts`); explicitly EXCLUDED (listed under a documenting `excluded` key, each with its re-entry owner): `DrillStack.tsx` (Lane B sweeps it; PM widens `files` to include it at Lane B merge — the named PM-APPLY-MANIFEST action, Task 12 Step 5), `viz/` gallery demos (EN-by-ruling, Plan-3), `src/door/**` (untracked, never touch), test files (`*.test.tsx` — the exclusion also filters the practice glob).
- [ ] Step 3 (TDD for the tool): `chrome-en-grep.test.mjs` (node:test, the 4a `tools/*.test.mjs` convention) — fixture strings: flags an `enWords` hit; flags a ≥3-word stopword-heavy run; passes a literal imported from a strings file; passes data-testid/className/identifier per `allowPatterns`; exit code 1 on hits, 0 clean. Then implement `chrome-en-grep.mjs`.
- [ ] Step 4: run the tool over the real scope → MUST be 0 hits (the sweep is complete) — if hits remain, finish the sweep, don't widen the allowlist (allowlist widening beyond §0.9G's enumerated classes = STOP, PM). INV-8.3 cross-check: the tool accepts imports from `chromeStrings.ts` OR `practiceStrings.ts` OR `lessonStrings.ts` (Lane B's file may not exist yet — the tool must not crash on its absence, NOR on scoped `files` entries/globs that match nothing yet: the `practice/*.tsx` rows resolve to zero files until Lane B merges and that is OK per-entry; the tool DOES red if the `files` list resolves to zero files overall — scope-level anti-vacuity).
- [ ] Step 5: the CI step (frontend job, after vitest: `node tools/chrome-en-grep.mjs`) is WRITTEN INTO the combined `test-yml-plan4b.patch` deliverable — drafted here, delivered with Task 9's patch (one shared-file patch, one PM apply).
- [ ] Step 6: full vitest + `npm --prefix tutor-web run e2e` green (swept components render in existing smokes); commit.

**Acceptance:** grep tool 0 hits on the real scope + its own tests green; a reviewer-seeded EN literal in a scoped component reds the tool (drill, not committed); DrillStack diff = empty; all component tests green in RO.

---

## Task 8 — Rendered-gate helpers + the four gate-4 seeded-violation drills (§0.9D/E/I; INV-9.4)

**Files:** Create `e2e/helpers/assertLegibility.ts`, `e2e/helpers/roHeuristic.ts`, `e2e/helpers/assertNextGateContract.ts`, `e2e/seeded-violations.spec.ts`, `e2e/fixtures/seeded-violations/clip.json`, `…/en-in-ro.json`.

- [ ] Step 1: implement the three helpers per §0.9D/E — same throw-with-violation-list shape as `assertNoClip.ts` (cite it). `assertNextGateContract(page)`: `beat-next-gate` has the `disabled` attribute whenever the active beat's gate condition is uncleared (checked at defined probe points), and clicking while logically gated never advances `lesson-beat-pips`'s active index. `roHeuristic.ts` implements §0.9E with the SAME pinned constants as `LanguageGate.kt` — stopword list, threshold, shortStringWords, `EN_VOCAB` stem list, RO suffix-strip rules (copy the constants; header cross-cites the Kotlin file + §0.9E).
- [ ] Step 2: `seeded-violations.spec.ts` — stub-mode (page.route, the `lesson-beats.spec.ts` scaffolding incl. auto-session stub + reducedMotion), 4 drills per the §0.9I table: (4a) `clip.json` = pa-kc-001 fixture with one reveal step text replaced by a 600-char unbroken token → expect `assertNoClip` to throw; (4b) clean fixture + `page.addStyleTag` forcing `[data-testid="lesson-beat-active"] * { color: #fafafa !important; }` on the light theme → expect `assertLegibility` to throw with a ratio < 4.5; (4c) clean fixture + `page.evaluate` removing `disabled` from `beat-next-gate` pre-commit → expect `assertNextGateContract` to throw; (4d) `en-in-ro.json` = fixture with the predict prompt in EN + the "skeletonul" leak → expect `roHeuristic` to flag ≥2 strings (the EN prompt via the stopword leg, the "skeletonul" leak via the §0.9E EN-vocabulary leg — both legs proven alive in the rendered gate). Each drill ALSO asserts the clean fixture passes the same helper (the gate is calibrated, not trigger-happy).
- [ ] Step 3: `npm --prefix tutor-web run e2e` — full suite green INCLUDING the new spec (drills pass = gates alive). Commit.

**Acceptance:** 4 drills red-on-seed + green-on-clean; helpers' violation messages name element + value (debuggability); reviewer verifies the fixtures contain the documented violations and nothing else differs from the clean fixture.

---

## Task 9 — Real backend in CI: `E2eSeed` + `lesson-gates.spec.ts` + gradle/CI patches (§0.9J; R-4b-Q2; INV-4.4/5.3/8.4; gates 4a–4d)

**Blocked on CP-1** (viz-route mount applied). **Files:** Create `E2eSeed.kt`, `E2eSeedTest.kt`, `e2e/lesson-gates.spec.ts`, `build-gradle-seed-e2e.patch`, `test-yml-plan4b.patch` (carries Task 7's grep step + this task's job).

**PM SCOPE AMENDMENT 2026-06-13 (authorized in-Task-9 fix — the gate did its job):** Task 9's real-route gate caught a REAL no-clip violation (Gate 4a / INV-5.3) that every prior gate missed because they ran on `/tutor/viz-demo` (short callouts) — the first exercise of the real `viz-pa-mergesort-001` binding on the lesson route. **Add `tutor-web/src/components/viz/families/GraphTreeFamily.tsx` to this task's allowed MODIFY set** and fix the defect: `GraphTreeFamily.renderFrame`'s callout `<text>` (line ~182-193) uses `textAnchor="middle"` with x clamped to `[8, SVG_W-8]` IGNORING the text's own width, so a wide callout anchored to a left-side node overflows to negative x (proven: "↓ ÎMPARTE — fiecare rând nou taie în jumătate" rendered left=-27, clipped). The family's OWN contract is no-clip-by-construction (§5.3, line 90 + the existing `measureLabelWidth` helper). REQUIRED fix properties: (1) measure the callout width via `measureLabelWidth`; (2) clamp its center-x by HALF that width so the FULL text stays within `[pad, SVG_W-pad]`; (3) handle the over-wide case — the instance's longer callout "↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)" may exceed `SVG_W - 2·pad` on one line: wrap to ≥2 lines (`<tspan>`) and/or shrink font, whichever keeps it legible AND inside bounds (the legibility gate 4b still applies — don't shrink into illegibility). ACCEPTANCE: re-run lesson-gates → `assertNoClip` GREEN on `beat-figure-scrubber` at BOTH viewports for EVERY frame of pa-kc-002; `family-no-clip.spec.ts` stays green (short-callout demo unaffected — the clamp only engages on overflow); the trace-match harness stays green (rendered node coords unchanged — only the callout text box moves); the 3 visual baselines stay 3/3 (they don't render this family); eyeball a fresh screenshot of `/tutor/lesson/pa-kc-002` confirming BOTH callouts fully visible (render-before-claim). This is `feedback_viz_no_clip_gate` firing on a real production figure — fix it, never absorb.

**Lock check (R-4b-Q10):** this task only CONSUMES frozen contracts — §NEW-L (`GET /lesson/{kcId}`), the beat-grade POST (`§0.9C` plan-3 shapes), §NEW-V (viz route). The spec asserts against served payloads; any shape mismatch found at execution = STOP, PM (it would mean code and lock diverged — the P1-4 class).

- [ ] Step 1 (TDD): `E2eSeedTest.kt` — run the seed routine against a temp dir: DB file created; all 4 beat-complete KCs resolve `faithful` via `VerifyAdmin.resolveStatus` (the REAL gate function, with the REAL recomputed hashes — proves the seed serves); session row resolves via `SessionRepo.findUserId`; `seed.json` shape exact (§0.9J #6) AND the derivation itself tested: `kcIds` equals the beat-complete promoted set computed independently in the test (resolves to `pa-kc-001..004` today), `figureKcIds` equals the corpus's figure-binding set (today `["pa-kc-002"]`), both asserted non-empty; language-check records present (Task 6's reconcile ran). Then implement `E2eSeed.kt` per §0.9J (refactor the routine into a callable `fun seed(dbPath, contentDir, outJson)` + a thin `main`).
- [ ] Step 2: produce `build-gradle-seed-e2e.patch` (JavaExec `seedE2eDb`; throwaway-edit + diff + checkout; `git apply --check` clean).
- [ ] Step 3: `lesson-gates.spec.ts` — header: `test.skip(!process.env.REAL_BACKEND, …)` + the LOCAL RECIPE comment (seed → boot server with the three env vars → `REAL_BACKEND=1 npx playwright test e2e/lesson-gates.spec.ts`). Setup: read `../build/e2e/seed.json`, `addCookies` `jarvis_session`, `emulateMedia reducedMotion`, collect console + pageerror + 4xx/5xx via `page.on("response")` for the WHOLE traversal. Then, for EVERY kcId in the seed manifest (totality over the served proof set — INV-4.4 "real lesson route against the real corpus"):
  - **§4.7 selector set on first paint:** `lesson-beat-pips` (N = payload plan length), exactly one `lesson-beat-active`, `beat-predict-options` (3–4 options) on ①, `beat-next-gate` disabled.
  - **Gate 4c interaction:** complete all beats by clicking; `assertNextGateContract` at each beat boundary; on EVERY figure-bearing KC — **detected from the SERVED payload** (a beat whose `reveal.figure` is non-null), never from the manifest: `beat-figure-scrubber` + `scrubber-step-counter` "pas k/N" + **back control exists and functions** + play + reset-replay after the final frame (INV-4.4 one-shot check on "every animated", on the REAL route — the R-4b-Q1 vacuity is dead); `lesson-complete-handoff` click lands on the drill/oggi surface with the same KC's `name_ro` visible; zero console errors, zero 4xx/5xx over the whole traversal.
  - **Gate 4a (INV-5.3):** `assertNoClip` at 2 viewport heights (the lesson-beats pair) on the active beat AND on the figure svg state at first/mid/final frame for EVERY payload-detected figure-bearing KC — per-live-instance no-clip, now CI-blocking by virtue of living in a blocking CI job.
  - **Gate 4b:** `assertLegibility` on the same states (§0.9D scope).
  - **Gate 4d (INV-8.4):** `roHeuristic` over the rendered learner-visible text of every beat (glossary param = the seeded glossary set, currently empty — §0 #9).
  - **Anti-vacuity (R-4b-Q1):** the spec FAILS if the payload-detected figure-bearing set is EMPTY, or if it differs from `seed.json.figureKcIds` (the corpus-derived set — the same set Task 5's `FigureBindingNonVacuityTest` proves non-empty), or if any figure-bearing KC's route renders no mounted family root (the registry `{prefix}-root` testid inside `beat-figure-scrubber`; today `graph-tree-root` on pa-kc-002). Detection from the served payload/DOM — not the manifest — means a binding Plan 5/6 mints later is exercised by THIS spec with zero edits (the rendered-DOM half of the totality assert; the Kotlin half is Task 5's test).
- [ ] Step 4: LOCAL full proof (this is the acceptance run, CI is the rerun). First PRODUCE the artifacts — `:check` proves the seed routine in a TEMP dir and writes NOTHING to `build/e2e/`, so seed explicitly via the sanctioned Task-3 throwaway pattern on the SHARED gradle file: `git apply build-review/tmp/lane-a-patches/build-gradle-seed-e2e.patch` (LOCAL throwaway — never committed) → `gradle --no-daemon :seedE2eDb` (writes `build/e2e/tutor.db` + `build/e2e/seed.json`) → `git checkout -- build.gradle.kts` (tracked file restored to clean; the patch file remains the deliverable; this doubles as the patch's executable proof. After CP-2 the task is live on main and the throwaway apply is unnecessary). Then boot the server per the recipe (PowerShell: `$env:JARVIS_TUTOR_DB="$PWD\build\e2e\tutor.db"; $env:JARVIS_CONTENT_DIR="$PWD\content"; $env:JARVIS_PORT="8080"; gradle run --args=web` in a background process), run `REAL_BACKEND=1 npx playwright test e2e/lesson-gates.spec.ts` → ALL green. Render-before-claim: also OPEN `http://localhost:5173/tutor/lesson/pa-kc-002` (today's figure KC per `seed.json.figureKcIds`) headed once and eyeball the figure + scrubber (feedback_render_before_claim_done) — screenshot to `build-review/tmp/plan4b-figure-route.png`. This seed recipe is THE local recipe — Task 11 Step 2 and Task 12 Step 2 re-run it verbatim.
- [ ] Step 5: write `test-yml-plan4b.patch` per §0.9J (lesson-gates job + Task 7's grep step; readiness curl-loop, log/report upload on failure); `git apply --check` clean. **PM checkpoint CP-2** — request apply of the gradle + yml patches; CI proof = the next push's Actions run, verified by the PM (the job must be GREEN there before Task 12 closes; a red names the failing gate).
- [ ] Step 6: commit (seeder, test, spec, patches; screenshot path in the message).

**Acceptance:** local real-server run all green across EVERY seeded KC (today 4) incl. every figure route (today pa-kc-002); `E2eSeedTest` green in `:check` incl. the derivation asserts; both patches apply-clean; reviewer re-runs the local recipe end-to-end themselves (throwaway-apply seed + boot + spec) and confirms zero stubbed routes in the spec file AND zero hardcoded kc-id lists in seeder or spec (ids appear only as the pinned current-expectation asserts in `E2eSeedTest`).

---

## Task 10 — `RetryBudget` + park seam + INV-9.2 (§0.9H; R-4b-Q3)

**Files:** Create `RetryBudget.kt`, `RetryBoundednessTest.kt`. No wire surface (no lock check needed — pure config + seam; the lane table's `gate/` package is unclaimed by Lane B).

- [ ] Step 1 (TDD): `RetryBoundednessTest` per §0.9H acceptance list (exact-budget attempts then park; success-at-2 no-park; failure-data feedback asserted; `rejectAction` pins for 1/2/3; park payload carries artifactId+gate+attempts). Run → red.
- [ ] Step 2: implement §0.9H verbatim (constants, enum, seam, `RetryLoop`). KDoc states the Plan-5 consumption contract explicitly (R-4b-Q3: "Not dead config — Plan 5 consumes it; plan doc says so" — and so does the code).
- [ ] Step 3: `:check` green; commit.

**Acceptance:** boundedness test pins every §9.1 row; reviewer confirms no UI/route integration snuck in (that is Plan 5's checkpoint screen, by ruling).

---

## Task 11 — `viz/theme.ts` INK/PAPER fix under the gates' cover (carried 4a→4b)

**Files:** Modify `tutor-web/src/components/viz/theme.ts` (INK `#0a0a0a`→`#000000`, PAPER `#f5f5f0`→`#ffffff` — DESIGN.md values).

**PM SCOPE AMENDMENT 2026-06-13 (accommodation — the theme edit has downstream pins):** the INK/PAPER change breaks 5 palette tests that hardcode COPIES of the old values as local `INK_HEX`/`PAPER_HEX` constants (proven: 6 failures in `TcpCwnd.palette.test.tsx`, `ProcessFSM.palette.test.tsx`, `SchedulerGantt.palette.test.tsx`, `BayesTree.palette.test.tsx`, `RaceMutex.palette.test.tsx`). That hardcoded copy is itself the defect (it desyncs from the source-of-truth token on every theme change). **Add those 5 test files to this task's MODIFY set and fix them DRIFT-PROOF: import `INK`/`PAPER` (and any other referenced token) FROM `tutor-web/src/components/viz/theme.ts` and reference the imported token — do NOT re-hardcode the new `#000000`/`#ffffff` literals** (re-hardcoding just moves the same time-bomb forward). Replace inline old-value string literals too (e.g. `BayesTree.palette.test.tsx:139`). The palette assertion's MEANING is "component renders the theme's INK/PAPER token, not an off-palette color" — importing the token expresses exactly that; the separate `designMdSync.test.ts` (Plan 4a) independently pins theme.ts↔DESIGN.md, so this is not tautological. If a specific assertion genuinely cannot use the import, updating that one literal to the new value is the fallback — note it. ACCEPTANCE: `grep -rn '#0a0a0a\|#f5f5f0' tutor-web/src` returns ONLY theme.ts comments/history if any (zero live literals outside the source token); full vitest GREEN; `e2e:visual` 3/3 (baselines don't import this file, §0 #11). None of the 5 files are in Plan 6's manifest — no lane conflict.

- [ ] Step 1: make the two-token edit. Nothing else in the file.
- [ ] Step 2: the COVER, in order: full vitest (trace harness — rendered coordinates unaffected by color, must stay green); `npm --prefix tutor-web run e2e` (no-clip + seeded drills green; legibility helper unaffected in stub specs); local `REAL_BACKEND=1` lesson-gates re-run with a FRESH seed per Task 9 Step 4's recipe (post-CP-2 `gradle --no-daemon :seedE2eDb` runs directly; pre-CP-2, throwaway-apply the gradle patch as sanctioned there) — gate 4b legibility over the new INK/PAPER (pure black/white can only raise contrast, but VERIFY, don't reason); `npm --prefix tutor-web run e2e:visual` → expected 3/3 (baselines don't import viz/theme.ts, §0 #11). **STOP if any baseline drifts** — baselines are human-recommit-only (spec gate 6): report to the PM, do NOT run `e2e:visual:update` autonomously.
- [ ] Step 3: commit.

**Acceptance:** all four suites green, baselines byte-identical; reviewer greps for any OTHER hardcoded `#0a0a0a`/`#f5f5f0` under `viz/` and reports (report-only — broader token sweeps are not this task).

---

## Task 12 — Whole-plan final gate + PM-apply manifest + re-carries

**Files:** Create `build-review/tmp/lane-a-patches/PM-APPLY-MANIFEST.md`, `…/plan-index-4b.patch`.

- [ ] Step 1: clean-tree check: `git status --short` → only known untracked artifacts; no uncommitted source.
- [ ] Step 2: the FULL suites, in the main tree, never piped through `| tail`:
  - `gradle --no-daemon :check` — green; if `stateCacheConcurrentPersistNeverTearsJson` fires, NAME it in the manifest + re-run to prove the rest is green (R-4b-Q9 — never absorbed into "flaky, ignore").
  - `npm --prefix tutor-web test` — green (incl. trace harness, seeded wrong-trace, FigureReveal, boundary, designMdSync, baselineScope).
  - `npm --prefix tutor-web run e2e` — green (incl. seeded-violations, lesson-beats untouched, family-no-clip with the play button; lesson-gates honestly SKIPPED here).
  - `npm --prefix tutor-web run e2e:visual` — 3 passed.
  - The Task-9 local real-backend run once more, end-to-end, fresh seed (Task 9 Step 4's recipe; post-CP-2 `gradle --no-daemon :seedE2eDb` is available directly on main — no throwaway apply needed by this point).
- [ ] Step 3: lock-conformance sweep: re-run the lock checks of Tasks 3/4/5/6/9 against the FINAL diff (`git diff <plan-start-sha>..HEAD` + the patch files); any frozen-shape divergence without a same-commit lock patch = STOP, PM.
- [ ] Step 4: zero-intersection final assert — the SAME filter on BOTH sides: LHS = `git diff --name-only <plan-start-sha>..HEAD` minus all `build-review/**` paths; RHS = §0.8 CREATE+MODIFY rows minus all `build-review/**` rows (the committed `lane-a-patches/*` deliverables are manifest rows AND diff entries — excluding them on only one side makes the equality unsatisfiable by construction). Rule: LHS must contain every non-`[lockstep]` RHS path, may additionally contain any subset of the `[lockstep]` rows (§0.8 semantics — they change only where a swept literal was asserted), and must contain NOTHING outside RHS. Every RHS row is a concrete path (the `[lockstep]` set was enumerated at plan-fix — no parentheticals to resolve). Any extra path, or any missing non-`[lockstep]` path = STOP, PM (the machine check vs Plan 6 runs on the published manifest; it must match reality).
- [ ] Step 5: write `PM-APPLY-MANIFEST.md`: the plan-start SHA, every commit, the actual changed-file list, the gate results verbatim (with the flake named if fired), the FIVE patches with apply order + `git apply --check` status — (1) `tutor-routes-viz-mount.patch` [CP-1, may already be applied], (2) `signatures-lock-viz-route.patch` [CP-1], (3) `build-gradle-seed-e2e.patch` [CP-2], (4) `test-yml-plan4b.patch` [CP-2], (5) `migration-language-check.patch` [CP-3 — **INV-3.1 backup gate, BOTH DBs, off-box dumps verified fresh before apply**] — plus the NAMED Lane-B merge-time action (no patch — a manifest TODO with the PM as owner): **widen `tools/chrome-scope.json` `files` to include `tutor-web/src/components/DrillStack.tsx`** once Lane B's `practiceStrings.ts` sweep lands, and verify the `practice/*.tsx` scope entries now resolve with `node tools/chrome-en-grep.mjs` still 0-hit (INV-8.3's practice half goes live at that moment; until then it is deferred-with-owner, not vacuous-by-omission) — plus the CI-green requirement (the lesson-gates job must pass on Actions before 4b is claimable DONE, §9.3) and the live-state honesty note (trust-net not yet flipped → live lesson routes still 404; the gates are proven on the seeded real-corpus DB, which is what R-4b-Q2 ratified).
- [ ] Step 6: re-carries (explicit, per the rulings' final-disposition): **cross-language schema-hash CI test** (TS/Kotlin shared-constant drift — now MORE load-bearing with §0.9E duplicated constants; carry to Plan 5); **Linux baseline regeneration** (4a follow-up #4, unchanged); **FSRS re-seed wart** (unchanged); **CodeMirror editor** (R-6-Q9, named follow-up); **numeric-ATTEMPT tolerance single-source** = Plan 6 Lane B's fold (lane table — re-carried TO that plan, not held here); **chrome-scope DrillStack widening at Lane B merge** (the Step-5 named PM-APPLY-MANIFEST action — INV-8.3's practice coverage is deferred-with-owner until the PM executes it); **`lessonStrings`/`chromeStrings` consolidation** (three strings files exist by design this cycle — revisit when Lane B's `practiceStrings.ts` lands). Each goes into `plan-index-4b.patch` (status flip Plan 4b → DONE-pending-PM-applies + the follow-up appends).
- [ ] Step 7: commit the manifest + patch; report to the PM: gates green, five patches pending per manifest, CI proof outstanding until the Actions run.

**Acceptance:** every suite green (flake named if fired); manifest complete and self-sufficient for the PM; plan-index patch apply-clean; the reviewer re-runs Step 2's suites in full before sign-off.
