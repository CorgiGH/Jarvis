# PM rulings — Plan 4b + Plan 6 plan-write (2026-06-12)

Input: `build-review/2026-06-12-plan4b-recon.md` §5 (Q1–Q11) + `build-review/2026-06-12-plan6-recon.md` §5 (Q1–Q10). These rulings are BINDING on the plan-writers. Spec stays canonical; `interface-signatures-lock.md` canonical-on-conflict.

## Plan 4b rulings

- **R-4b-Q1 (figure vacuity): option (a).** Build `FigureReveal` + bind `viz-pa-mergesort-001` to ONE served KC's reveal. Plan must carry an accommodation ledger: faithfulness-hash ripple of editing an authored KC (trust-net/Plan-1 coupling — re-run admission verification), off-box dump precondition before any live-DB mutation (fresh dumps exist 2026-06-12), `lesson-beats.spec.ts` fixture anchor updates. Add an anti-vacuity totality assert: gate job reds if zero served KCs bind a figure.
- **R-4b-Q2 (real corpus in CI): option (a), spec letter.** New CI job boots the Ktor server over a seeded SQLite (seed fixture from the real `content/` corpus via the admission path; `DrillGradeAtomicTest`/`Plan2Seed` patterns). `lesson-gates.spec.ts` runs against the real server + real corpus. Existing stubbed specs stay as-is. CI yml = PM-merge-only patch.
- **R-4b-Q3 (retry greenfield): confirmed split.** 4b ships `RetryBudget` config constant + park-as-gap-record seam + INV-9.2 boundedness test; REJECT-loop UI integration lands with Plan 5's checkpoint screen. Not dead config — Plan 5 consumes it; plan doc says so explicitly.
- **R-4b-Q4 (contrast method): define in plan, not at execution.** DOM text: computed fg vs effective bg = nearest opaque ancestor (walk up until alpha 1), WCAG 2.1 ratio ≥4.5:1. SVG text: fill vs figure PAPER / nearest opaque bg. Scope: learner-visible text on the lesson route; exclude aria-hidden decorative. Determinism: reducedMotion + locked theme (visual-config pattern).
- **R-4b-Q5 (language heuristic): calibrate, two-sided.** Glossary exemption source = Plan-2 `GlossaryTermsTable` seeds (writer verifies seed state) + per-call exempt list. Heuristic: diacritic presence OR EN-stopword ratio under threshold; short strings exempt from the diacritic leg only. Acceptance: zero false reds on the 4 authored faithful KCs' RO fields (over-blocking guard) + INV-8.2 seeded set all red (under-blocking guard). **AMENDED + PM-RATIFIED 2026-06-12 (plan-fix F1):** a third pinned `EN_VOCAB` loanword-stem leg is added (plan 4b §0.9E) — the two ruled legs provably cannot red the ruling's own INV-8.2 flagship seed ("skeletonul…": EN-stopword ratio 0); re-ruling the seed out would gut the gate. One tuning round in Task 6 calibration, then pinned.
- **R-4b-Q6 (INV-8.1 record): record at ADMISSION/SYNC time, not serve time.** Language-check record written to DB when content is admitted/synced; INV-8.1's query stays satisfiable ("served fields lacking a record → 0 rows") because every served field passes admission. Serve route (`QueueMasteryCalibrationRoutes.kt`) NOT touched by Lane A — that file is Lane-B-exclusive (see lane assignments). Any migration behind the INV-3.1 backup gate, both DBs; `Migration.kt` edits = PM-applied patch.
- **R-4b-Q7 (RO sweep scope): grep scope = lesson/practice/dashboard learner-visible components.** Explicit allowlist file in `tools/`; excludes demo gallery, door demos (untracked — never touch), data-testids, code identifiers.
- **R-4b-Q8 (stray win32 baselines): PM deleted 2026-06-12** (targeted `Remove-Item` of the three untracked `*-snapshots/` dirs; `__screenshots__/` untouched).
- **R-4b-Q9 (StateCache flake): confirmed.** Final gate names it if it fires; never absorbs. Standing carve-out.
- **R-4b-Q10 (frozen signatures): plan-write includes a signatures-lock check** on every route/payload-touching task; conflict → halt for PM.
- **R-4b-Q11 (lane discipline): see lane assignments.**

## Plan 6 rulings

- **R-6-Q1 (sandbox): subprocess isolation, no Docker this plan.** Fresh temp dir + hard timeout + bounded output capture; no-network NOT guaranteed — documented limitation (single-user app, own machine). Required-green environments: PC (Windows/MinGW) + CI (ubuntu). VPS: runners self-detect toolchain at boot; missing tool → leg disabled, chain degrades to next leg honestly (logged; grade reply says which leg decided, RO copy). VPS provisioning = explicit deploy task; live probe before any feature-shipped claim.
- **R-6-Q2 (Alk jar): plan Task 0 investigates** artifact form + license of `alk-language/java-semantics`. Prefer pinned-release fetch + checksum; commit-the-jar fallback if no stable artifact; license unclear → halt for PM. **RESOLVED 2026-06-12 (Task-0 HALTs):** no published license (`license: null`, no LICENSE file, no pom `<licenses>`) → **fetch-only**: pinned `alki-v4.3.zip` from the official repo, sha256-verified, extracted git-ignored; jar/zip NEVER committed (redistribution under unclear license forbidden); local fetch+run for personal educational use accepted. Commit-the-jar fallback RESCINDED. Real CLI = classpath launch w/ Z3 (`main.ExecutionDriver -a`, needs `java.library.path` natives) — plan §0.9-C PM-amended to a wrapper-script contract; verified working (gcd → 4).
- **R-6-Q3 (problem seeds): hand-seed ≥1 real-corpus problem per subject where the corpus has real material** (PA/ALO/PS minimum — grader goldens already cover those 3). Provenance = source doc + quote anchors, trust-net style; agents extract, system verifies — NEVER Alex (no-oracle-inversion). Subjects without corpus problems stay pending; INV-6.1 pending-not-green logic keyed on live row counts per spec.
- **R-6-Q4 (misconceptions): confirmed mechanism-now / mining-later.** Data-driven registry; hand-seed PA flipped-reduction flagship + the existing 3 stats codes; Plan 5 mines R-ERRORS breadth.
- **R-6-Q5 (LLM goldens): RATIFIED as recorded-replay.** Recorded LLM JSON replayed through `parseGradeJson` + scoring; deterministic; zero live LLM calls in CI; no-paid-APIs compliant.
- **R-6-Q6 (hub files): see lane assignments.**
- **R-6-Q7 (mock-exam additive): confirmed.** Delta-recon `MockExamRoutes.kt`/`MockExamShell.tsx` contracts at plan-write; additive only; live endpoints must not break.
- **R-6-Q8 (bash→rubric): confirmed.** Explicit routing-table row; INV-6.1 totality accepts rubric as a first leg for SO code archetypes. No WSL.
- **R-6-Q9 (editor): DEFERRED.** Textarea + `code-practice-language-badge` satisfies the spec selectors. CodeMirror = named follow-up, not in 4b/6.
- **R-6-Q10 (deliverable data): course-meta/`ExamScheduleRowsTable` seeds + corpus course docs with provenance** (POO lab specs etc.); honest empty state where no data exists.

## Lane assignments (zero-intersection pre-ruling — BINDING)

| File / area | Lane |
|---|---|
| `tutor-web/src/components/DrillStack.tsx` (+ its tests) | **Lane B exclusive** — CHECK-card graded input AND moves its own chrome strings to `practiceStrings.ts`. 4b's RO sweep EXCLUDES DrillStack. |
| Strings files | Lane A creates `tutor-web/src/lib/chromeStrings.ts` (its sweep components). Lane B creates `tutor-web/src/lib/practiceStrings.ts` (practice surfaces + DrillStack). INV-8.3 grep accepts both. No shared strings file. |
| `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` + `LessonBeatGradeRouteTest.kt` | **Lane B exclusive** (numeric-ATTEMPT tolerance fold through `NumericOracleGrader` + pin test). Lane A records language checks at admission, never at serve (R-4b-Q6). |
| `main.tsx`, `WebMain.kt`, `TutorRoutes.kt` | **Lane B** (practice routes/mounts). Lane A has no listed need; if 4b discovers one → PM patch. |
| `Migration.kt`, `.github/workflows/test.yml`, `package.json`+lock, `playwright.config.ts`, `build.gradle.kts`, plan index, signatures lock | **PM-MERGE-ONLY for BOTH lanes** — patch files under `build-review/tmp/`, PM applies (4a precedent). |
| `content/PA/kcs/*.yaml` (figure binding) | Lane A. Lane B's problem seeds use NEW files (`content/*/problems/` or DB seed) — no overlap. |
| `ContentValidator.kt`, `ContentRepo.kt`, lesson components, viz families, e2e gates | Lane A. |
| `grader/` package, `ProblemsRepo`, practice components/routes/e2e, `MockExam*`, `Tasks.kt`/`TaskPrep.kt`, `GradeScoring.kt`, `DrillGrader.kt`, `GradeTeachingPayload.kt`, `drillGrader.ts` | Lane B. |
| `src/main/kotlin/jarvis/content/KcBeats.kt` (additive `BeatAttempt` numeric fields, Plan-6 Task 4) | **Lane B** — assigned at plan freeze 2026-06-12; was unassigned; Plan 4b's manifest does not list it (machine-checked zero hit). |

Machine check at plan freeze: CREATE+MODIFY lists across the two plan docs intersect to ZERO (SHARED/PM-merge lists excluded). Any hit → plan-writer fix round (max 1) → still hit → PM halt.

## Carried follow-ups — final disposition

- 4b folds: BeatOrchestrator error boundary · AlgoStepperShell RO chrome (with FigureReveal) · INV-5.5 `checkFigureBindings` into `validate()` · `viz/theme.ts` INK/PAPER drift (fix under the new rendered gates' cover).
- Plan 6 folds: numeric-ATTEMPT tolerance single-source · relay retry/backoff (bounded, shared `RetryingLlm` decorator) for `RelayLlm` + `FreeLlmApiLlm`. **Factual correction (plan-write verification 2026-06-12):** `RelayLlm` is NOT a `productionGraderResolver` member (resolver = OpenRouter|ClaudeMax|FreeLlmApi); `RelayLlm` lives at the `drillCriticLlmFactory` seam (`TutorRoutes.kt:198`). Both named clients are wrapped at their REAL seams; deliverable unchanged.
- Stays carried: cross-language schema-hash CI test · Linux baseline regeneration · FSRS re-seed wart · CodeMirror editor (new, from R-6-Q9).
