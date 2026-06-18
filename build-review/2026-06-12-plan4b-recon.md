# Plan 4b Recon — Gate Chain, Lesson-Gated Half (rendered gates 3–4, retry semantics, INV-9.4 seeds, error boundary)

Recon date: 2026-06-12. Spec: `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` §9.1–9.4, §5.4, §4.6–4.7, §8.2–8.3. Plan-4a sibling (format template): `docs/superpowers/plans/2026-06-12-plan4a-gate-tooling.md`. Every file path below was re-verified against the working tree on 2026-06-12 (Glob/grep/git ls-files) — including four corrections to the prior code inventory, flagged inline.

---

## 1. Spec requirements (numbered, with section refs)

**R1 — Semantic figure gates (§9.2 gate 3, §5.4).**
- §9.2 g3: "Semantic figure gates — per-family trace-match + invariant asserts (§5.4)."
- §5.4: "for each instance, a reference execution is computed from the instance data (run the actual algorithm); every rendered step's state is asserted equal to the reference state. A figure that animates something the algorithm doesn't do cannot pass."
- §5.4: "Semantic invariant asserts: per-family domain invariants checked in rendered pixels/coordinates, e.g. the reflection family asserts `len(Hx) ≈ len(x)` in rendered px." Motivating defect: "the QR demo's beat-1 reflection rendered x=[1,2,2] at ~60 px and Hx=[−3,0,0] at ~142 px — a visible 2.39× stretch in an animation whose stated teaching point was length preservation."
- **INV-5.1:** "Per family, the trace-match harness runs in CI over every live instance in the real DB (not sample fixtures): rendered step states equal reference execution states, 100%."
- **INV-5.2:** "Per family, semantic invariant asserts pass on every live instance (family-specific list maintained with the family code)."

**R2 — No-clip/overlap promoted into @playwright/test FIRST (§9.2 gate 4a, §9.4 amendment (c)).**
- §9.2 g4a: "no-clip/overlap promoted into the @playwright/test suite FIRST (council ordering — this lands before the other rendered gates and before any baseline exists; today the no-clip check lives only in the ad-hoc `shoot.qrqa2.mjs`, not in CI — audit V10)."
- §9.4 (c): "`assertNoOverflow` lands in the test suite BEFORE any baseline exists — 'a baseline of a clipped layout is worse than no baseline'."
- §5.3: "Deterministic layout that measures its own labels … no-clip by construction, not by post-hoc screenshot checking."
- **INV-5.3:** "No-clip: rendered DOM audit at 2+ viewport heights asserts zero overlapping/clipped label boxes on every live instance (the standing viz no-clip gate, now per-family and CI-blocking)."
- INV-9.4 (partial): a seeded clipped label must turn this gate red in CI.
- **Recon delta:** the spec's "today" claim is STALE — Plan 3 already landed `tutor-web/e2e/helpers/assertNoClip.ts` (verbatim port of audit.viz.mjs:76-125) used by `lesson-beats.spec.ts` (2 viewports) and `family-no-clip.spec.ts`. What remains for 4b: per-family/per-live-instance coverage, CI-blocking status, and the seeded-red drill.

**R3 — Per-element legibility/contrast gate on the real lesson route (§9.2 gate 4b).**
- §9.2 g4b: "per-element legibility/contrast — the concrete floor: the QR payoff zeros rendered at ~1.75:1 against a 4.5:1 requirement (rendered-audit QR-CONTRAST-ZERO), and truncation that hides meaning (the TLS 'Finished' ellipsis, VIZ-05) fails."
- §9.2 g4 satisfiability proof: "the three demo lessons pass zero-clip / zero-overlap / zero-truncation / gates-genuinely-lock in all 12 theme×viewport passes; its 23 findings (0 blocker / 9 high / 10 medium / 4 low: 7 clip-overlap, 8 legibility, 3 EN-leak, 1 broken-interaction) all fall inside what gates 3–4 catch going forward."
- INV-9.4 (partial): a seeded label below 4.5:1 contrast must turn gate 4b red in CI.

**R4 — Interaction test on the real lesson route (§9.2 gate 4c, §4.6, §4.7).**
- §9.2 g4c: "interaction test — all beats clickable, gates actually lock, zero console errors, zero 4xx/5xx."
- §4.7: "`[data-testid='beat-next-gate']` — the Next control, asserted `disabled` before the beat gate clears and enabled after." Interaction smoke: "complete all beats by clicking; zero 4xx/5xx during the whole traversal; zero console errors; `beat-next-gate` is verifiably disabled before each gate condition and enabled after; clicking `lesson-complete-handoff` lands in the drill surface with the same KC visible."
- §4.6: "A one-shot animation (plays once, no way back) is a gate violation caught by the rendered interaction test (§9.2 gate 4) … canonical violation: the Dijkstra demo's beat-③ one-shot ~17 s run whose play button is destroyed on completion (lectie-dijkstra.html:188 — rendered-audit DIJ-ONESHOT)."
- §4.7 required-visible selectors: `lesson-beat-pips`, `lesson-beat-active`, `beat-predict-options` (① beat), `beat-next-gate` (disabled→enabled), `beat-figure-scrubber` on any ③ with a figure (back/play/forward/reset + `scrubber-step-counter` "pas k/N"), `lesson-complete-handoff`.
- **INV-4.4:** "Playwright suite (§4.7 selectors + interaction smoke) runs on the real lesson route against the real corpus in CI; the one-shot-animation check asserts the scrubber's back control exists and functions on every animated ③."

**R5 — Language gate rendered on the real lesson route (§9.2 gate 4d, §8.2, §8.3).**
- §8.2: "Language validator as an admission gate on every learner-facing field: diacritics presence + stopword-distribution heuristic flags EN text in RO fields; glossary terms exempt. Runs in the §9.2 gate chain, not as advice." Closes audit L7: "`ContentValidator.kt:112-128` currently checks non-blank only — English-authored teaching fields pass silently; the `_ro` suffix is a naming convention, not an enforcement."
- §8.2 chrome sweep: the L1–L14 + VIZ-01/VIZ-02 catalogue ("100% of the 20 gallery components' captions/labels/legends/aria-live + the AlgoStepperShell chrome — FRAME/RESET/SHARE/VOICE OFF/PREDICT — in English"); "DrillStack chrome incl. DEFINITELY/MAYBE/GUESS/IDK, Scratchpad, TaskQuickStart, ConceptDrawer/KnowledgeLedger, Sidekick/ChatPane, TrustSettings, stepper shell controls moves to a strings file; new chrome strings go through it by construction."
- **INV-8.1:** "Language validator runs on 100% of admitted learner-facing fields: real-DB query for served fields lacking a language-check record → 0 rows."
- **INV-8.2:** "Seeded EN-in-RO regression set (taken from actual catalogued violations: the QR demo's 'skeleton'/'skeletonul' leak — rendered-audit QR-EN-LEAK — plus EN drill stems and EN grader feedback from the SESSION-55/56/57 class) all fail the validator in CI."
- **INV-8.3:** "UI chrome: CI greps lesson/practice/dashboard component sources for hardcoded EN learner-visible literals outside the strings file → 0 (allowlist: data-testids, code identifiers)."
- **INV-8.4:** "Playwright language gate (§9.2 gate 4) asserts rendered lesson text is RO (diacritic/stopword heuristic) on the real proof-set lessons."

**R6 — Retry-semantics table enforced in the pipeline (§9.1).** Verbatim table:
> | Loop | Trigger | Bound |
> | (a) Machine gate red | any gate in the chain fails | ≤3 self-retries with the failure data fed back into the failing stage; still red → park as gap-record |
> | (b) User REJECT #1 at the checkpoint | first rejection of an artifact | one re-generation incorporating the one-line note |
> | (c) User REJECT #2 on the same artifact | second rejection of the same artifact | STOP — pipeline design review (the §10.3 rule); never a third regeneration |
- §9.1: "The retry budget is a constant in config, not a vibe."
- **INV-9.2:** "Retry boundedness: pipeline logs show ≤ retry-budget attempts per gate per artifact; exceeding parks."

**R7 — INV-9.4 seeded-violation drill for gates 3 and 4 (§9.3, §9.4).**
- §9.3: "a gate that exists as a script someone remembers to run does not exist. Each of the seven gates has a CI job; 'this gate is implemented' is claimable only when the CI job exists, runs on the real corpus/DB, and a seeded violation turns it red."
- **INV-9.4:** "Seeded-violation drill per gate (garbled PDF, unfaithful KC, wrong-trace figure, clipped label, EN-in-RO field, broken golden answer): each turns its gate red in CI."
- Plan 4b's four seeds: wrong-trace figure → gate 3 red; clipped label → gate 4a red; EN-in-RO field → gate 4d red; broken interaction (next-gate not locked) → gate 4c red. (Garbled PDF = Plan 5 / gate 1; unfaithful KC = done, Plan 1 / gate 2; broken golden answer = done, Plan 4a / gate 7.)

**R8 — BeatOrchestrator error boundary (carried follow-up, plan-index line 19, verbatim).**
> "BeatOrchestrator has NO error boundary (malformed lesson payload unmounts the whole lesson UI — found by the final-gate stub bug 2026-06-12; add a boundary in Plan 4b's rendered-gate work)"
- Gating-correctness requirement: gate 4c (zero console errors, zero unmount failures) cannot be claimably green without it. Verified 2026-06-12: zero `ErrorBoundary`/`componentDidCatch`/`getDerivedStateFromError` hits anywhere under `tutor-web/src/`.

**Invariant ownership table (4a vs 4b):** INV-5.1, INV-5.2, INV-5.3, INV-4.4, INV-8.1..8.4, INV-9.2, INV-9.4(gates 3+4) = all Plan 4b. INV-9.5 = DONE (4a). INV-9.1 gate-chain totality = partial (gates 1–2, 5–7 exist; 3–4 are 4b's).

---

## 2. Current code state (verified 2026-06-12)

### Inventory corrections (prior recon claims that did NOT survive re-verification)

1. **`tutor-web/playwright.visual.config.ts` EXISTS** (landed Plan-4a commit `483cf39`). Env-locked: workers:1, dsf:1, reducedMotion, snapshotPathTemplate under `e2e/visual/__screenshots__/`, maxDiffPixelRatio 0. Prior inventory's gap #1 is stale.
2. **`BeatSelector` exists — server-side Kotlin**: `src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt` (+ `src/test/kotlin/jarvis/tutor/lesson/BeatSelectorTest.kt`), landed by Plan 3. There is no frontend component of that name (and none is needed).
3. **`Plan2Seed.kt` path** is `src/main/kotlin/jarvis/tutor/Plan2Seed.kt` (not `jarvis/Plan2Seed.kt`).
4. **No-clip is already IN the @playwright/test suite**: `tutor-web/e2e/helpers/assertNoClip.ts` (Plan-3 §0.9H, verbatim-logic port of audit.viz.mjs:76-125: viewport overflow + text clipping + interactive overlap >16px²). Used by `lesson-beats.spec.ts` (2 viewports, on `lesson-beat-active` and `lesson-complete`) and `family-no-clip.spec.ts` (2 viewports on `/tutor/viz-demo`, re-checked after stepping). R2 is therefore *substantially landed*; 4b's remaining no-clip work = per-live-instance coverage + seeded-red drill + CI-blocking claim.
5. **Stray untracked baselines:** `tutor-web/e2e/visual/{shell,theme-dark,theme-light}.visual.spec.ts-snapshots/*-win32.png` exist UNTRACKED (default-pathTemplate leftovers from a pre-config run). Only `__screenshots__/` PNGs are git-tracked (verified `git ls-files`). Never `git add -A`; PM cleanup candidate.

### Frontend lesson route

- `tutor-web/src/main.tsx` — `BeatOrchestratorRoute` wrapper (~line 23); route `<Route path="/lesson/:kcId" element={<BeatOrchestratorRoute />} />` at line 85, under `BrowserRouter basename="/tutor"`. Loads `getLesson(id)`; undefined → `lessonStrings.loading`; null → orchestrator's `lesson-unavailable` fallback.
- `tutor-web/src/components/lesson/BeatOrchestrator.tsx` — 5-beat state machine (predict/attempt/reveal/name/check), gate logic, pips, `lessonStrings` throughout. **No error boundary** (confirmed). Beats: `PredictBeat.tsx`, `AttemptBeat.tsx`, `RevealBeat.tsx`, `NameBeat.tsx`, `CheckBeat.tsx` + `BeatOrchestrator.test.tsx`.
- **CRITICAL GAP — the lesson route's figure path is a stub.** `RevealBeat.tsx:48-57`: when `reveal.figure` is set it renders `<div data-testid="beat-figure-scrubber">` containing only `scrubber-step-counter` "pas 1/1" and the comment `{/* Task 8: <FigureReveal figure={...} onGateClear={...} /> */}`. `FigureReveal` does not exist anywhere (grep: only this comment). No served KC binds a figure (the 4 authored KCs are figure-less), so the branch is typed-but-unreachable. Consequence: gates 3/4's "on the real lesson route" figure assertions (trace-match in rendered DOM, scrubber back-control on animated ③, figure no-clip) are **vacuous on the lesson route today**. The family only actually renders on `/tutor/viz-demo` (`VizDemoPage`). This is the ghost-component class of bug the global feature-shipped rule exists for.
- `tutor-web/src/lib/lesson.ts` (`getLesson`, `ApiLessonReply` + beat types incl. `numeric_answer`/`numeric_tolerance` on CHECK), `tutor-web/src/lib/beatGrade.ts` (`postBeatGrade` → `POST /api/v1/lesson/{kcId}/beat`), `tutor-web/src/lib/dwell.ts`.
- `tutor-web/src/lib/lessonStrings.ts` — RO chrome for the lesson surface ONLY (Plan-3 Task 6; file header says "the app-wide sweep is Plan 4"). All 5 beat components + orchestrator consume it.
- `tutor-web/src/components/OggiScreen.tsx` — navigates to `/lesson/:kcId`.

### Viz family layer

- `tutor-web/src/components/viz/AlgoStepperShell.tsx` — `ShellLabels` interface at lines 45-51 (`frame/reset/share/voiceOn/voiceOff/predict`, all optional), EN defaults applied at lines 97-102 (`"Frame"`, `"reset"`, `"🔗 share"`, `"🔊 voice on"`, `"🔇 voice off"`, `"⚡ Predict"`). `labels` prop wired but **no caller anywhere passes RO values** (grep `labels=` in lesson/: zero hits — RO labels were intended to flow lesson→family→shell, but the lesson figure mount doesn't exist, see above).
- `tutor-web/src/components/viz/families/familyRegistry.ts` — `FamilyRendererProps { instanceId, dataJson, language, labels? }`; registry = `{ "graph-tree": GraphTreeFamily }` only.
- `tutor-web/src/components/viz/families/GraphTreeFamily.tsx` — exports `parseGraphTreeData` + `framesFromGraphTree`; passes `labels` through to the shell; no-clip-by-construction layout (measureText label reservation).
- **Trace-match already exists at vitest level for 1 family × 1 instance:** `families/__tests__/GraphTreeFamily.test.tsx` raw-imports the SHIPPED `content/PA/viz/viz-pa-mergesort-001.yaml` (`?raw`), pins 8 steps, and asserts every frame's labels+highlight equal the independent `mergesortTrace.ts` reference (test header cites INV-5.1). What's missing for INV-5.1/5.2: iteration over EVERY live instance (currently the corpus has exactly one), the harness-as-CI-job framing, rendered-pixel semantic invariant asserts (INV-5.2 — nothing exists), and the seeded wrong-trace red.
- Other shell consumers (demo gallery, EN, out of lesson scope): `ArrayStepper`, `BayesTree`, `CppVTable`, `DPWastedWork`, `MatrixTransform`, `AlgoStepperShellSmoke`, etc. under `tutor-web/src/components/viz/`.

### Playwright e2e (all in `tutor-web/e2e/`, all backend-stubbed at `page.route` level)

- `lesson-beats.spec.ts` (144 lines) — **the main 4b extension target**: stubs `GET /api/v1/lesson/pa-kc-001` from `fixtures/pa-kc-001-beats.json` + `POST .../beat` via `gradeReply()`; collects `console`/`pageerror`; `emulateMedia({reducedMotion:"reduce"})`; walks all 5 beats; asserts gate lock/unlock, scrub-back, `lesson-complete` handoff, `assertNoClip` at 2 viewports, zero 4xx/5xx, zero console errors. Fixture pins 3 content anchors verbatim to the authored `content/PA/kcs/pa-kc-001.yaml`.
- `family-no-clip.spec.ts` — GraphTreeFamily no-clip at 2 viewports on `/tutor/viz-demo` + scrubber back/forward counter checks.
- `tutor-shell-api-contract.spec.ts`, `drill-viz-paint.spec.ts`, `phase5-core-loop.spec.ts`, `oggi-interaction-smoke.spec.ts`, `new-surfaces-smoke.spec.ts` — existing paint/interaction smokes, all stubbed.
- `visual/{shell,theme-dark,theme-light}.visual.spec.ts` + tracked `__screenshots__/` PNGs (Plan 4a, INV-9.5-scoped).
- `playwright.config.ts` — baseURL `http://localhost:5173`, `webServer: npm run dev`, `testDir ./e2e`, no storageState; auth handled by stubbing `**/api/v1/tutor/auto-session`. **No spec touches a real backend; the CI frontend job runs no JVM.**

### Gate tooling from Plan 4a (context, don't touch)

- `tools/impeccable-filter.mjs`, `tools/impeccable-rules.json`, `tools/generate-design-md.mjs` (+`.test.mjs`), `DESIGN.md` AUTOGEN block, `tutor-web/src/__tests__/designMdSync.test.ts`, `tutor-web/src/__tests__/baselineScope.test.ts` (INV-9.5), `tutor-web/src/__tests__/fontLoading.test.ts`, fonts under `tutor-web/public/fonts/`.
- npm scripts (verified): `e2e`, `e2e:visual`, `e2e:visual:update`, `design:check`.

### CI — `.github/workflows/test.yml` (verified line numbers)

- backend job: `gradle :check` (includes validateContent + SignatureLockPinTest).
- frontend job: `npm test` (vitest) → Impeccable fail-open at lines 72-73 (`npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true`) → chromium install → `npm run e2e` at lines 76-77. No visual job (by design), no Kotlin server.
- `verifier-deps-loud-red` job at lines 111-153 = the existing INV-9.3 seeded-drill PATTERN (env-hide deps → assert red + named) — the closest precedent for the INV-9.4 seeded-violation jobs.

### Kotlin admission/serve layer

- `src/main/kotlin/jarvis/content/ContentValidator.kt` — `checkBilingual` at lines 112-128 is exactly the audit-L7 non-blank check. `validate()` (lines 151-172) runs 11 checks; **`checkFigureBindings` (lines 290-312, INV-5.5) is NOT among them** — its only callers are in `src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt` (the fold-into-validate() follow-up is real; note `validate()` callers must then supply `knownInstanceIds`).
- `src/main/kotlin/jarvis/content/ContentRepo.kt` — `loadVizInstances(subject)` (line 56) loads `content/{subject}/viz/*.yaml`, parse-validates `data_json`, dup-id check. **Viz instances live in the git corpus, not the DB** — INV-5.1's "every live instance" enumeration source is `content/*/viz/` (+ any future DB-side pedagogical_instance rows from Plan 2 schema).
- Lesson serve + beat-grade routes: `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (the only `api/v1/lesson` hit besides `WebMain.kt`). Route tests: `src/test/kotlin/jarvis/web/LessonServeRouteTest.kt`, `LessonBeatGradeRouteTest.kt`.
- Seeding patterns for INV-9.4/real-backend work: `src/test/kotlin/jarvis/tutor/DrillGradeAtomicTest.kt` (`seedContent`/`seedFaithful`/`seedProblem` — richest), `GenerateDrillsRouteTest.kt` (`seedSession`/`seedTask`), `FsrsGradeRouteTest.kt`. Production seeder `src/main/kotlin/jarvis/tutor/Plan2Seed.kt`. **No Playwright-facing live-server seed fixture or test-mode seed endpoint exists** — all lesson e2e is route-stubbed.
- Grader harness (gate 7, done): `src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt` + `src/test/resources/fixtures/grader-golden/{PA,ALO,PS}/` — the seeded-red proof pattern to copy for gates 3–4.
- Verify package (gate 2 precedent): `src/main/kotlin/jarvis/tutor/verify/` (VerificationRunner, VerificationGate, ExtractionConfidence, NonLlmLegs, …). **No pipeline retry/budget config exists anywhere in `src/main/kotlin`** (grep `retry`: only OpenRouterChatLlm, JarvisToolset, Google*, ShadowGit — all unrelated transport retries). R6 is greenfield.

### Chrome components for the RO sweep (all exist under `tutor-web/src/components/`)

`DrillStack.tsx` (+7 test files), `Scratchpad.tsx`, `ScratchpadDrawer.tsx`, `TaskQuickStart.tsx`, `ConceptDrawer.tsx`, `KnowledgeLedger.tsx`, `Sidekick.tsx`, `ChatPane.tsx`, `TrustSettings.tsx`, plus `AlgoStepperShell.tsx` chrome (above).

---

## 3. Proposed file list

### CREATE

| Path | Purpose |
|---|---|
| `tutor-web/src/components/lesson/LessonErrorBoundary.tsx` | R8 — class-component boundary wrapping the active beat + orchestrator body; renders an honest-degraded RO fallback (via strings file); test asserts a throwing beat does NOT unmount the lesson chrome |
| `tutor-web/src/components/lesson/LessonErrorBoundary.test.tsx` | seeded throw → boundary catches, fallback paints, no unmount |
| `tutor-web/src/components/lesson/FigureReveal.tsx` | R1/R4 — fills the `RevealBeat.tsx:53` stub: resolves `reveal.figure.family_id` via `familyRegistry`, passes `labels` = RO ShellLabels from the strings file, wires `onGateClear` to final-frame-reached, exposes §4.7 scrubber testids | 
| `tutor-web/src/components/lesson/FigureReveal.test.tsx` | mounts registry family with the shipped mergesort instance; asserts scrubber back-control exists/functions (INV-4.4 one-shot kill) |
| `tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx` | INV-5.1/5.2 — enumerates EVERY instance under `content/*/viz/*.yaml` (vite glob raw-import, mirroring GraphTreeFamily.test.tsx), dispatches per-family reference executor + per-family semantic invariant list; fails on any unregistered family or uncovered instance (totality) |
| `tutor-web/src/components/viz/families/__tests__/seededWrongTrace.test.tsx` | INV-9.4 gate-3 seed — a known-bad instance (swapped merge step) must make the harness red (assert-throws shape, per GraderGoldenHarnessTest precedent) |
| `tutor-web/e2e/helpers/assertLegibility.ts` | R3 — rendered DOM audit: per-text-element WCAG contrast ≥4.5:1 (computed fg vs effective bg) + meaning-hiding truncation (ellipsis on labels) |
| `tutor-web/e2e/helpers/roHeuristic.ts` | R5/INV-8.4 — diacritic-presence + EN-stopword-distribution heuristic over rendered text, glossary-exempt |
| `tutor-web/e2e/lesson-gates.spec.ts` | gates 4a–4d composed on the real lesson route for the proof-set KCs: §4.7 selector set + interaction smoke + assertNoClip (2+ viewports) + assertLegibility + roHeuristic + zero 4xx/5xx + zero console errors |
| `tutor-web/e2e/fixtures/seeded-violations/` (clip / low-contrast / en-in-ro / unlocked-gate fixtures) | INV-9.4 gate-4 seeds — each drill asserts its gate REDS on the seed |
| `tutor-web/src/lib/strings.ts` (or `chromeStrings.ts`) | INV-8.3 — app-wide learner-visible chrome strings file (lessonStrings stays; new file covers DrillStack DEFINITELY/MAYBE/GUESS/IDK, Scratchpad, TaskQuickStart, ConceptDrawer/KnowledgeLedger, Sidekick/ChatPane, TrustSettings, ShellLabels RO set) |
| `tools/chrome-en-grep.mjs` + `tools/chrome-en-grep.test.mjs` | INV-8.3 CI grep: lesson/practice/dashboard component sources, hardcoded EN learner-visible literals outside the strings file → 0 (allowlist: data-testids, identifiers; door demos excluded) |
| `src/main/kotlin/jarvis/content/LanguageGate.kt` (or additive `ContentValidator.checkRomanianFields`) | R5 admission gate: diacritics + stopword heuristic on every `_ro` learner-facing field; glossary exempt; wired into `validate()` |
| `src/test/kotlin/jarvis/content/LanguageGateTest.kt` + `LanguageGateSeededRegressionTest.kt` | INV-8.2 — seeded set: QR-EN-LEAK "skeleton/skeletonul", EN drill stems, EN grader feedback (SESSION-55/56/57 class) all FAIL the validator |
| `src/main/kotlin/jarvis/tutor/gate/RetryBudget.kt` (config constant + park-as-gap-record seam) | R6 — the §9.1 table as code: budget=3 self-retries, REJECT#1→one regen, REJECT#2→STOP; consumed by Plan 5's pipeline |
| `src/test/kotlin/jarvis/tutor/gate/RetryBoundednessTest.kt` | INV-9.2 — attempts ≤ budget, exceeding parks; seeded over-budget run reds |
| CI patch or new jobs for the seeded-violation drills (INV-9.4) | modelled on the `verifier-deps-loud-red` job (test.yml:111-153) — **delivered as patch, see SHARED below** |

### MODIFY

| Path | Change |
|---|---|
| `tutor-web/src/components/lesson/RevealBeat.tsx` | replace the line-53 stub with `<FigureReveal …/>`; keep stepped-text path untouched |
| `tutor-web/src/components/lesson/BeatOrchestrator.tsx` | wrap beat body in `LessonErrorBoundary`; no contract change |
| `src/main/kotlin/jarvis/content/ContentValidator.kt` | (a) fold `checkFigureBindings` into `validate()` (carried follow-up — needs `knownInstanceIds` param threaded to callers); (b) add the language check to the `validate()` list |
| `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` | only if INV-8.1's per-served-field language-check record is recorded at serve time (PM decision Q6) — frozen-signatures check required |
| `tutor-web/src/components/{DrillStack,Scratchpad,ScratchpadDrawer,TaskQuickStart,ConceptDrawer,KnowledgeLedger,Sidekick,ChatPane,TrustSettings}.tsx` | RO sweep: EN literals → strings file (their existing test files update in lockstep) |
| `tutor-web/src/components/viz/AlgoStepperShell.tsx` | none required structurally (labels prop exists); RO values flow from FigureReveal. Touch only if a chrome literal exists outside the ShellLabels six |
| `tutor-web/e2e/lesson-beats.spec.ts` | minimal: keep as-is (Plan-3 ownership); new assertions go in `lesson-gates.spec.ts` |
| `content/PA/kcs/pa-kc-00X.yaml` (one KC) + possibly `content/PA/viz/` | bind `viz-pa-mergesort-001` to one served KC's reveal so gates 3/4c are non-vacuous on the real route (PM decision Q1; re-runs faithfulness hash implications — check Plan-1 trust-net coupling before editing a faithful KC) |

### SHARED:PM-MERGE-ONLY (two-lane contract, plan-index header — deliver as patch files / PM-applied edits)

- `.github/workflows/test.yml` — new INV-9.4 seeded-drill jobs, INV-8.3 grep step, any served-backend e2e job. **SHARED:PM-MERGE-ONLY** (4a precedent: patch under `build-review/tmp/`).
- `tutor-web/playwright.config.ts` — only if projects/testMatch split needed. **SHARED:PM-MERGE-ONLY**.
- `tutor-web/package.json` + `package-lock.json` — additive script keys only. **SHARED:PM-MERGE-ONLY** (4a precedent: additive, PM rebases lockfile).
- `build.gradle.kts` — only if a new gradle task (e.g. language-gate CI entry) is added. **SHARED:PM-MERGE-ONLY**.
- `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` — status flip + follow-up appends. **SHARED:PM-MERGE-ONLY**.
- `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` — consult-on-conflict; any serve-route change must be checked against it first. **SHARED:PM-MERGE-ONLY**.

---

## 4. Carried follow-ups to fold in

1. **BeatOrchestrator error boundary** — plan-index line 19 verbatim: "BeatOrchestrator has NO error boundary (malformed lesson payload unmounts the whole lesson UI — found by the final-gate stub bug 2026-06-12; add a boundary in Plan 4b's rendered-gate work)". Confirmed absent. = R8, Tasked above.
2. **AlgoStepperShell full RO chrome sweep** — Plan-3 Task 14 carry (plan3 doc line 5847): "the additive labels prop ships RO on the lesson surface only; demos/gallery keep EN defaults) = Plan 4 / spec §8.2". Reality: the labels prop exists but NO caller passes RO yet (the lesson figure mount is a stub) — the sweep lands together with FigureReveal + the strings file (R5).
3. **INV-5.5 fold into validate()** — `ContentValidator.checkFigureBindings` (lines 290-312) is implemented but `validate()` (lines 151-172) never calls it; only `FigureBindingValidationTest` does. A dangling figure binding currently passes the `validateContent` chokepoint. Fold it in (thread `knownInstanceIds` from `ContentRepo.loadVizInstances`), exactly when Plan 4b starts binding real figures to served KCs.
4. **Numeric-ATTEMPT tolerance source** — Plan-3 Task 14 carry (plan3 doc line 5848, verbatim): "the no-choices attempt branch is a deliberate placeholder (exact-string match vs trace_steps.last().value, a formula string — would grade a real numeric answer wrong). DEAD on the 4 faithful choice-variant KCs Plan 3 serves. When a numerical-variant KC (FORMULA_APPLICATION/PROCEDURE/PROBABILISTIC) is served, add attempt.numeric_answer + tolerance (mirror the numeric CHECK path) and a LessonBeatGradeRouteTest case pinning the match". Fold ONLY if 4b authors/serves a numerical-variant KC for gate coverage; otherwise re-carry explicitly in the manifest.
5. **`viz/theme.ts` INK/PAPER drift** — Plan-4a Task 2 carry, explicitly deferred TO 4b: theme.ts hardcodes `#0a0a0a`/`#f5f5f0` vs DESIGN.md `#000000`/`#ffffff`; 4a ruled "do NOT fix here — changes viz rendering = needs Plan-4b rendered gates". Now that the rendered gates exist, fix it under their cover (legibility/contrast gate will catch regressions) or re-carry with a reason.

---

## 5. Risks + open questions for the PM

**Q1 — Figure-on-lesson-route vacuity (biggest correctness risk).** `RevealBeat`'s figure path is a comment-stub and no served KC binds a figure, so every "on the real lesson route" figure assertion (trace-match in rendered DOM, scrubber back on animated ③, figure no-clip) would pass VACUOUSLY — the exact ghost-component failure class. Options: (a) build `FigureReveal` + bind `viz-pa-mergesort-001` to one served KC (touches an authored, possibly faithful KC — hash/trust-net ripple must be accounted, accommodation-ledger rule); (b) run figure gates on `/tutor/viz-demo` only and scope INV-4.4's scrubber clause to "every animated ③ in the served corpus" (currently zero — honest but empty). Recommend (a); needs PM ratification because it changes served content.

**Q2 — "Real corpus in CI" architecture.** INV-4.4/8.4/5.1 say real lesson route + real corpus, but all e2e is route-stubbed and CI's frontend job runs no JVM. Options: (a) new CI job boots the Ktor server over a seeded SQLite (no seed fixture exists — must build one off the `DrillGradeAtomicTest` pattern or a test-mode seed endpoint); (b) keep stubs but derive fixtures mechanically from `content/` YAML at test time (lesson-beats already verbatim-pins 3 anchors — extend to full payload generation). (a) matches the spec letter; (b) is cheaper and still corpus-grounded. PM must rule before the plan is written — it shapes half the task list.

**Q3 — Retry semantics is greenfield with no pipeline to enforce against.** The gate-chain orchestrator (digestion) is Plan 5; 4b can ship the config constant + park/gap-record seam + INV-9.2 boundedness test, but loops (b)/(c) (user REJECT) have no UI until Plan 5's checkpoint screen. Risk: dead config. Proposal: constant + seam + test in 4b, integration in 5 — confirm split.

**Q4 — Contrast-measurement scope.** Per-element 4.5:1 over computed styles is well-defined for DOM text; SVG `<text>` over arbitrary figure fills and the accent-on-dark theme cases need an explicit method (nearest opaque ancestor bg? sampled pixels?) and an explicit element scope, or the gate will flake. Define in the plan, not at execution.

**Q5 — Language-heuristic calibration.** Diacritic-presence fails on legitimately diacritic-free short RO strings ("pas 3/8"); the stopword leg needs a tuned threshold + glossary exemption source (no glossary file exists yet — where does it live, `content/` or strings file?). INV-8.2's seeded set protects against under-blocking; a calibration step against the 4 authored KCs' RO fields protects against over-blocking (zero false reds on faithful content = acceptance).

**Q6 — INV-8.1 needs a language-check RECORD in the live DB** ("served fields lacking a language-check record → 0 rows") ⇒ new table/column ⇒ live-DB migration behind the Plan-1 backup gate (INV-3.1) on PC + VPS. High-ceremony; alternatively record at admission (validateContent) and query the corpus, not the DB — weaker than the spec letter. PM ruling needed.

**Q7 — RO-sweep scope boundary.** Plan-3 ruling: demos/gallery keep EN defaults; spec §8.2 catalogues "100% of the 20 gallery components". INV-8.3's grep scopes "lesson/practice/dashboard component sources" — the grep allowlist must explicitly exclude the demo gallery + the deliberately-untracked door files (standing rule: never touch/track `src/door` demos) or CI will demand edits to files we must not commit.

**Q8 — Stray untracked win32 baselines** under `e2e/visual/*-snapshots/` — delete or ignore; they will confuse the INV-9.5 scope gate's future readers. Never `git add -A` (standing rule; door demo files + these).

**Q9 — Known flake:** `stateCacheConcurrentPersistNeverTearsJson` reds the full backend suite ~1-in-2 (plan-index carry, re-confirmed 2026-06-12) — the 4b final gate must name it if it fires, not absorb it.

**Q10 — Frozen signatures:** any serve-payload or route change (FigureReveal contract, language-check record) must be checked against `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` (canonical-on-conflict, §NEW-L added SESSION-63) before the plan freezes file edits.

**Q11 — Lane discipline:** Plan 4b is the gated Lane-A sequence and (Plan 3 being DONE) can run in the main tree, but the W-theme/Plan-6 Lane-B candidate may be cut concurrently — the plan-writer must publish 4b's full deliverable list for the zero-intersection probe, and route all SHARED files through the PM patch mechanism (4a precedent).
