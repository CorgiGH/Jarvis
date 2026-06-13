# Lane B Plan 6 — Merge Manifest

**Branch:** `lane-b/plan6`
**Head commit (at manifest write):** see `git log --oneline main..HEAD` below
**Branch cut from main:** `bab5d3cb` (Plan 4a merge point)
**Date:** 2026-06-13

---

## Commits on `lane-b/plan6` since branch cut

```
b70701b fix(plan6/task13): mock-exam spec — REQ-17 assertions for all 4 selectors; ExamRoute wired to real API
6d9fa66 test(plan6): practice e2e — INV-6.5 five surfaces vs seeded real corpus, INV-6.6 attempt gate; CI patch (PM-applied)
262484f feat(plan6): DrillStack CHECK card = real graded input via grader chain; chrome strings → practiceStrings (E8/REQ-29)
a0cf829 feat(plan6): mock-exam additive — timer/phases/G-item rubric/synthetic tag + formats data; legacy contract pinned (R-6-Q7)
578c8b4 feat(plan6): CodePractice (textarea, R-6-Q9) + DeliverableTracker + verified deliverable seeds (R-6-Q10)
446b9b8 feat(plan6): ProofDrill + StepTraceDrill surfaces, practiceStrings (RO), mounted (REQ-2..6)
19ce503 feat(plan6): PracticeRoutes (proof/trace/code/deliverables) — server-enforced attempt gate + practiceApi client
c43aa94 feat(plan6): /drill/grade chain integration — oracle/execution/rubric before LLM; additive decided_by/degraded/item_verdicts (lock §O additive)
d989427 feat(plan6): RetryingLlm (relay/freellmapi) + recorded-replay llm-judge goldens (R-6-Q5, INV-6.4)
309f552 feat(plan6): execution grader — subprocess sandbox, 4 runners, INV-6.2 vs real corpus problems (E17, R-6-Q1)
e500fc8 feat(plan6): numeric-ATTEMPT tolerance via single oracle source + route pin tests (carried follow-up)
be74ef3 feat(plan6): grader chain + numeric oracle + rubric leg + routing table; INV-6.1 pending + INV-6.3 (E16)
2e9ec1e fix(plan6): task 2 fix-round — remove fabricated POO seed, add ProblemSeedTest, PA exam_language=alk
939ebe5 feat(plan6): ProblemsRepo + real-corpus problem seeds w/ verbatim provenance (R-6-Q3)
fcf23ab feat(plan6): data-driven misconception taxonomy + per-subject judge prompts (E18, R-6-Q4)
0c2e9d5 feat(plan6): task 0 follow-up — portable static wrappers, strip emit from fetch-alk.mjs (PM ruling 2026-06-13)
bce072c feat(plan6): task 0 — lane branch, intersection probe, toolchain probe, Alk pinned fetch (R-6-Q2)
83f153d feat(plan6): task 0 — lane branch, intersection probe, toolchain probe, Alk investigation BLOCKED (R-6-Q2)
```

Plus Task 14 commit (manifest + lock patch + e2e fixes, this commit).

---

## Gate results (Task 14 Step 1)

### Backend (`:check`)

```
BUILD SUCCESSFUL
Total: 1575 tests, Failures: 0, Errors: 0, Skipped: 3, Passed: 1572
```

Key test classes verified green:
- `GraderRoutingTotalityTest` (INV-6.1 named pending set)
- `LlmNeverAloneTest` (INV-6.3)
- `ExecutionGraderSandboxTest` (INV-6.2 — R/Python/C++/Alk)
- `GraderGoldenHarnessTest` (extended: grade-scoring ≥12 + llm-judge ≥6 = INV-6.4)
- `LessonBeatGradeRouteTest` (numeric-ATTEMPT tolerance pins)
- `MockExamAdditiveRouteTest` (legacy pins + new semantics)
- `PracticeRoutesTest` (no-solution-leak assert, INV-6.6 server half)

### Frontend (vitest)

```
Test Files: 175 passed (175)
Tests: 926 passed (926)
```

### Playwright e2e

```
JARVIS_PRACTICE_E2E=1: 11/11 passed
  - phase5-core-loop.spec.ts: 1/1 passed
  - proof-drill.spec.ts: 2/2 passed (INV-6.5, INV-6.6 half)
  - step-trace.spec.ts: 2/2 passed (REQ-5 wrong-step-3)
  - code-practice.spec.ts: 2/2 passed (INV-6.6 both halves)
  - mock-exam.spec.ts: 2/2 passed (REQ-17 selectors)
  - deliverable-tracker.spec.ts: 2/2 passed (REQ-19/21)

Without JARVIS_PRACTICE_E2E: 10/10 self-skipped (env-gate verified)
```

---

## Zero-intersection re-check (Step 3)

Lane A post-cut commits on `main` since `bab5d3c`: 9 commits (Plan 4b Tasks 1-8 + PM playwright config fix).

Files touched by Lane A: `tutor-web/src/components/lesson/*`, `tutor-web/src/lib/chromeStrings.ts`, `tutor-web/src/lib/lessonStrings.ts`, `tutor-web/src/components/viz/*`, `src/main/kotlin/jarvis/content/*`, `tutor-web/playwright.config.ts`, `build-review/tmp/lane-a-patches/*`.

**Intersection with Lane B CREATE+MODIFY manifest: ZERO.**

---

## PM merge recipe

1. On `main`: `git merge --no-ff lane-b/plan6`
2. Apply patches (in order):
   - `git apply build-review/tmp/lane-b-plan6-patches/test-yml-practice.patch`
   - `git apply build-review/tmp/lane-b-plan6-patches/signatures-lock-plan6.patch`
3. Update `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` — set Plan 6 status to MERGED.
4. Rebuild frontend: `npm --prefix tutor-web run build`
5. Run FULL gate on merged main: `gradle --no-daemon :check` + `npm --prefix tutor-web test` + `JARVIS_PRACTICE_E2E=1 npx playwright test e2e/practice e2e/phase5-core-loop.spec.ts`
6. Push only after the full gate is green.
7. Proceed to Task 15 (VPS deploy + live probe) — **no feature-shipped claim before Task 15's live probe passes.**

---

## Patches the PM applies at merge

| Patch | Purpose |
|---|---|
| `test-yml-practice.patch` | CI: R toolchain + Alk fetch for backend job; new `practice-e2e` job with `JARVIS_PRACTICE_E2E=1` |
| `signatures-lock-plan6.patch` | Lock doc: §NEW-L (`ApiBeatAttempt` additive fields) + §NEW-P (practice endpoints, drill-grade additive, mock-exam additive, ItemVerdict, P2-5 reconciliation) |

---

## Re-carry lines (explicit — NOT done in this plan)

Per the rulings final-disposition section and plan §0.5:

1. **Cross-language schema-hash CI test** — carried from before Plan 6; deferred.
2. **Linux visual-baseline regeneration** — carried from Plan 4a; deferred.
3. **FSRS re-seed wart** — carried from earlier plans; deferred.
4. **CodeMirror editor (R-6-Q9 deferral)** — named follow-up: the practice code surface uses `<textarea>` per the ruling; CodeMirror is a named future enhancement, never silently dropped.
5. **REQ-1 queue/arc mastery movement (spec 6.1)** — RE-SCOPED OUT of Plan 6 at plan-fix: no task in this plan touches queue ordering / arc stage / mastery-driven progression. Goes to the tracking plan. **Not shipped by this manifest — never claimed.**
6. **R-MULTISELECT drill-side variant (spec 6.2.4/REQ-13)** — only the mock-exam grid half lands here; the drills half is a named follow-up, never a silent drop.
7. **ALO/PS/POO pending problem subjects** — Task 2 seeded PA proof + ALO trace + PS code (3 archetype-classes covered). POO has no locatable real corpus on disk (Task 2 Step 3 exhaustive search finding) → honest pending, zero fabrications. ALO proof archetype pending (no ALO proof problem seeded). Named pending set in `GraderRoutingTotalityTest`.
8. **FreeLLMAPI Node service deploy** — client `FreeLlmApiLlm.kt` built (SESSION-63); the Node service deploy+configure deferred.

---

## Post-merge task

**Task 15 (PM-gated):** VPS deploy + toolchain self-detect probe + production problem-seed behind INV-3.1 backup gate + live interaction smoke. No feature-shipped claim before Task 15's live probe passes.
