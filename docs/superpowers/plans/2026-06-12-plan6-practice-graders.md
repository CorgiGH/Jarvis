# Plan 6: Practice Surfaces + Graders (Spec §6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **LANE B — executes in the dedicated worktree `../jarvis-kotlin-lane-b` (fresh branch `lane-b/plan6` cut from current `main` HEAD), NEVER in the main working tree** (Lane A / Plan 4b executes there). PM merges to main after the full-suite gate.
>
> **Executors NEVER edit this plan doc.** If reality mismatches a plan assumption (a file moved, a contract differs, a ruling seems wrong), the task is BLOCKED — report the mismatch verbatim to the PM and stop that task. Do not improvise around the plan.
>
> **Binding inputs:** `build-review/2026-06-12-plan4b6-pm-rulings.md` (every R-6-Q* ruling + the lane-assignment table applied verbatim below — do not relitigate), `build-review/2026-06-12-plan6-recon.md` (REQ-1..29, INV-6.1..6.6), spec `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` §6 + §3.4/§3.5. `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` is canonical-on-conflict.
>
> **Language rule (hard line):** learner-visible content RO (all strings in `practiceStrings.ts`, grade-reply feedback, degraded-leg copy, mock-exam UI). Code, identifiers, comments, commit messages, test names EN.
>
> **No live-DB access of any kind in Tasks 0–14.** The only live-DB work is Task 15 (POST-MERGE deploy), which runs behind the INV-3.1 backup gate.

**Goal:** Spec §6 complete — the four-leg grader chain (numeric oracle → execution → rubric → LLM-last, REQ-22..28, E16/E17/E18), the subprocess execution sandbox (R-6-Q1), real-corpus problem seeds with provenance (R-6-Q3), the data-driven misconception taxonomy mechanism (R-6-Q4), recorded-replay LLM goldens (R-6-Q5), and the 5 practice surfaces (proof drill, step-trace drill, code practice, mock-exam ADDITIVE, deliverable tracker — REQ-2..21) with INV-6.1..6.6 as CI invariants. Plus the two carried follow-ups assigned to this lane: numeric-ATTEMPT tolerance single-source and relay retry/backoff. **REQ-1 (spec 6.1: the queue moves a KC along the arc as mastery rises) is explicitly OUT OF SCOPE — no Plan-6 task touches queue ordering, arc stage, or mastery-driven progression, and no §0.9-G practice endpoint writes per-KC state; REQ-1 is re-scoped to the tracking plan via a NAMED re-carry line in Task 14's manifest (never claimed shipped by this plan — no ghost requirement).**

**Architecture:** all grader logic lands in a new `jarvis.tutor.grader` package; practice endpoints land in a new `PracticeRoutes.kt` (keeping `TutorRoutes.kt` edits surgical: chain integration at `/drill/grade` + route registration). All schema changes are ADDITIVE NULLABLE COLUMNS on existing registered tables (`tasks`, `mock_exams`) — `Migration.kt` is NOT touched (Exposed `createMissingTablesAndColumns` adds columns to registered tables automatically; no new table ships in this plan). Frontend surfaces are new components under `tutor-web/src/components/practice/` mounted via `main.tsx` (Lane B owns `main.tsx` per the lane table). Shared files (CI yml, package.json, signatures lock, plan index) ship as patch files under `build-review/tmp/lane-b-plan6-patches/` — PM applies at merge.

**Tech stack:** Kotlin/Ktor + Exposed (existing), `java.lang.ProcessBuilder` subprocess sandbox (no Docker — R-6-Q1; no new Gradle dep), Alk interpreter jar via pinned-release fetch + checksum (R-6-Q2), JUnit 5 parameterized goldens (Plan-4a harness extended), React + plain `<textarea>` (R-6-Q9 — CodeMirror DEFERRED), Playwright e2e under the existing `testDir: "./e2e"` (no `playwright.config.ts` edit).

---

## Section 0 — Verified ground truth (delta-recon 2026-06-12, plan-writer verified against the live tree)

1. **Graders today:** `DrillGrader.kt` = LLM-judge with the hardcoded 3-statistics-code enum in `GRADE_PROMPT_TEXT` (`:50-58`: `L2_ESTIMATOR_CONFUSION | MINIMAX_CONFUSION | MODE_CONFUSION | OTHER` — the E18 target) and `GRADE_PROMPT_CODE` (`:70`: "You do NOT execute the code" — the E17 target). `GradeScoring.kt` = the deterministic layer: `scoreFromRubric` (fraction), `correctFromRubric` (non-empty && all), `isConfident`, `answerMatches` (`:33-40`, exact + numeric with hardcoded `1e-9`). No execution grader, no oracle leg, no chain. `parseGradeJson` (`DrillGrader.kt:87-103`) = two-pass brace-extract, deterministic over a fixed string — the replay engine for R-6-Q5 goldens.
2. **`/drill/grade`:** `TutorRoutes.kt:2036` — resolves the per-user LLM via `drillGraderLlmResolver` (= `productionGraderResolver`, `TutorRoutes.kt:189`, wired at `WebMain.kt:713`), calls `DrillGrader.grade(...)` directly, has a graceful "UNGRADED" degraded path on LLM failure. Server-side problem resolution from `task_prep.problems_json` already exists (`:2053-2065` — client payloads not trusted).
3. **Problem bank (Plan 2, live):** `Plan2Tables.kt` — `ProblemsTable` ("problems": id, subject, archetype, statementJson, parameterSlotsJson, solutionPresent, solutionJson, examLanguage `"alk"|"r"|"cpp"|"bash"|"posix-c"|null`, examLanguageConstraintsJson, dataFilesJson, sourceDoc, sourcePage, provenance, syntheticTag, timestamps), `ProblemRubricItemsTable` (label G1..Gn, points, kind AP/WP, allOrNothing, penaltyRulesJson, position), `ProblemKcLinksTable`. All registered in `Migration.kt` `ALL_TABLES` already. **Zero rows in any DB** — `Plan2Seed.kt` does not seed problems. **No `ProblemsRepo` exists.**
4. **Golden harness (Plan 4a, merged):** `GraderGoldenHarnessTest.kt` — JUnit 5 `@ParameterizedTest` over `src/test/resources/fixtures/grader-golden/{subject}/grade-scoring/*.json`, 12 fixtures (PA×5/ALO×4/PS×3), `require(files.size >= 12)` discovery guard, 1e-9 epsilon. `_README.md` reserves `{subject}/llm-judge/` + `{subject}/execution-grader/` for THIS plan. `junit-jupiter-api/params:5.10.2` already in `build.gradle.kts` (4a Task 5) — **no Gradle dep change needed in Plan 6**.
5. **Mock exam (EXISTS — additive ground, not greenfield):** `MockExamRoutes.kt` — `installMockExamRoutes()` at `:143` (registered from `TutorRoutes.kt:266`, NOT WebMain), `POST /api/v1/mock-exam/start` `:151`, `POST /api/v1/mock-exam/{id}/submit` `:201`, `GET /api/v1/mock-exam/{id}/result` `:292`, `gradeOpenQuestion` `:362`; DTOs `ApiMockExamStartRequest/StartReply/Question/SubmitRequest/Answer/SubmitReply/KcResult/ResultReply` (`:84-141`). `MockExamTable.kt` — `MockExamsTable` ("mock_exams"): id, userId, subject?, questionsJson, kcResultsJson?, score?, narrative?, createdAt, submittedAt?. `MockExamShell.tsx` + `.test.tsx` exist and are mounted in `main.tsx`. NO timer/phase/G-item-rubric/synthetic-tag semantics anywhere.
6. **Lesson numeric paths:** `QueueMasteryCalibrationRoutes.kt` — CHECK branch (`:665-670`) already compares `free_input` numerically with `c.numeric_tolerance ?: 0.0`; ATTEMPT no-choices branch (`:643-667` region) is the DELIBERATE PLACEHOLDER (exact-string vs `trace_steps.last().value`, dead on the 4 served choice-variant KCs) with the carried follow-up written in-code: add `attempt.numeric_answer` + tolerance, mirror the CHECK path, pin with a `LessonBeatGradeRouteTest` case. Content schema `KcBeats.kt`: `BeatCheck` has `numeric_answer`/`numeric_tolerance` (`:125-126`); `BeatAttempt` (`:81-91`) does NOT — the additive fold target.
7. **DrillStack:** CHECK card at `DrillStack.tsx:375-391` — read-only `MathText` + "MARK CHECK DONE" button (`handleCheckDone`), no input, no grading (audit E8 / REQ-29). Six `DrillStack.*.test.tsx` siblings. `phase5-core-loop.spec.ts` (at `tutor-web/e2e/` ROOT, NOT `e2e/practice/`) contains NO check-done/`handleCheckDone` reference — re-verified at plan-fix: its only CHECK-adjacent content is the `check: "Trace fib(4)"` fixture string (`:49`) and a `drill-check-btn` click (`:101`, the drill ANSWER-grade button — a different control). Lane B claims the file because Task 12's CHECK-card change may break its fixture/flow; whether an edit is needed is an execution-time finding, and the spec is RUN in Task 12 and in the Task 13/14 gates (it is stub-based — no backend needed). Code mode = plain `<textarea>` (R-6-Q9 keeps it).
8. **LLM clients:** `OpenRouterChatLlm.kt` has a single 429 retry; `RelayLlm.kt:43` and `FreeLlmApiLlm.kt:45` have ZERO retry/backoff. **Reachability (re-verified at plan-fix):** `productionGraderResolver` (`TutorRoutes.kt:189-194`) constructs exactly `OpenRouterChatLlm() | ClaudeMaxLlm() | FreeLlmApiLlm()` — `RelayLlm` is NOT a resolver member (`ClaudeMaxLlm` is a `claude --print` CLI subprocess, not the relay); `RelayLlm` lives at the SEPARATE seam `drillCriticLlmFactory` (`TutorRoutes.kt:198`, the E3 critic) and in verify/trust paths. The carried follow-up's two named clients are therefore wrapped at TWO seams: `FreeLlmApiLlm` inside the resolver, `RelayLlm` at `drillCriticLlmFactory` (Task 6). `Llm` interface (`Llm.kt:13-30`): `suspend fun complete(messages, maxTokens, responseFormat): Pair<String, String>`, `AutoCloseable`.
9. **Corpus state:** git-tracked `content/` has REAL material only for PA (`content/PA/_sources/pa-lecture-01.md`, 8 KC yamls, 2 misconception yamls — `Misconception` schema at `ContentSchema.kt:130` with `source[].{doc,quote}` provenance). `content/{ALO,PS,POO,SO-RC}/` dirs exist but `_sources/`, `kcs/`, `misconceptions/` are EMPTY. Real ALO/PS material must be located on disk (spec §10.2 names Desktop artifacts + live-site PDFs; `~/Desktop/cursuri RC/` verified present) — Task 2 does the exhaustive search.
10. **Sandbox environment:** PC = Windows, MinGW g++ at `C:\MinGW\bin\g++.exe` (workspace config), Python present (SymPy bridge `SympyTool.kt` is the existing subprocess pattern), JVM present (Gradle), **R unverified on PC**, Alk jar absent everywhere. CI = ubuntu-latest (python3 + g++ preinstalled; R + Alk need install steps → CI patch). VPS = Linux, toolchains unverified → R-6-Q1 honest-degradation + Task 15 deploy probe.
11. **Frontend plumbing:** `tutor-web/playwright.config.ts` `testDir: "./e2e"` — `e2e/practice/**` is collected with NO config edit. Vitest collects `src/**/*.test.tsx` — new component tests need no config. `drillGrader.ts` mirrors the `/drill/grade` wire 1:1 snake_case.
12. **Two-lane contract:** worktree `../jarvis-kotlin-lane-b` exists from Plan 4a (merged). Task 0 re-points it at a fresh `lane-b/plan6` branch off current `main`. Lane A (Plan 4b) runs in the MAIN tree. NEVER `git add -A` / `git clean` / branch-switch in the MAIN tree (untracked door demos). All Plan 6 commands target the worktree.

## Section 0.5 — Binding PM rulings applied (from `build-review/2026-06-12-plan4b6-pm-rulings.md`)

| Ruling | Applied where |
|---|---|
| **R-6-Q1** subprocess isolation, no Docker; fresh temp dir + hard timeout + bounded output; no-network NOT guaranteed (documented limitation); required-green = PC (Windows/MinGW) + CI (ubuntu); VPS = boot-time toolchain self-detect, missing tool → leg disabled, chain degrades honestly (logged; grade reply says which leg decided, RO copy); VPS provisioning = explicit deploy task; live probe before any feature-shipped claim | §0.9-C contract; Task 5 (sandbox + INV-6.2); Task 7 (decided-by/degraded RO copy on the reply); Task 15 (deploy + live probe) |
| **R-6-Q2** Task 0 investigates Alk artifact form + license; prefer pinned-release fetch + checksum; commit-the-jar fallback; license unclear → HALT for PM. **PM RULING 2026-06-12 (Task-0 HALTs resolved):** license = none published (`license: null`, no LICENSE file) → **fetch-only is the ONLY path**: pinned release `alki-v4.3.zip` from the official repo, sha256-verified, extracted under `tools/alk/` which is git-ignored — the jar/zip is NEVER committed (committing = redistribution under an unclear license, forbidden); README records the status + the no-redistribution constraint. The commit-the-jar fallback is RESCINDED. CLI = the §0.9-C PM-amended classpath/wrapper form (verified working: bundled gcd example → 4). | Task 0 Steps 6–8 + PM resolution note in Task 0 |
| **R-6-Q3** hand-seed ≥1 real-corpus problem per subject where the corpus has real material (PA/ALO/PS minimum); provenance = source doc + quote anchors, trust-net style; agents extract, system verifies — NEVER Alex; subjects without corpus problems stay pending; INV-6.1 pending-not-green keyed on row counts | Task 2; Task 3 (INV-6.1 pending logic) |
| **R-6-Q4** mechanism-now / mining-later; data-driven registry; hand-seed PA flipped-reduction flagship + the existing 3 stats codes; Plan 5 mines R-ERRORS breadth | Task 1 |
| **R-6-Q5** RATIFIED recorded-replay: recorded LLM JSON replayed through `parseGradeJson` + scoring; deterministic; zero live LLM calls in CI; no-paid-APIs compliant | Task 6; §0.9-E |
| **R-6-Q6 / lane assignments** | §0.8 manifest follows the table verbatim |
| **R-6-Q7** mock-exam additive; delta-recon `MockExamRoutes.kt`/`MockExamShell.tsx` contracts; live endpoints must not break | §0 #5 (plan-write delta-recon done); Task 11 Step 1 (execution-time delta-recon) + regression pin test |
| **R-6-Q8** bash→rubric explicit routing-table row; INV-6.1 totality accepts rubric as a first leg for SO code archetypes; no WSL | Task 3 routing table + totality test |
| **R-6-Q9** editor DEFERRED — textarea + `code-practice-language-badge` satisfies the spec selectors; CodeMirror = named follow-up | Task 10; re-carry line in Task 14 manifest |
| **R-6-Q10** deliverable data = course-meta/`ExamScheduleRowsTable` seeds + corpus course docs with provenance; honest empty state where no data exists | Task 10 |

**Carried follow-ups — disposition in this plan (rulings doc final-disposition section):**

- **Numeric-ATTEMPT tolerance single-source** → **Task 4** (concrete task).
- **Relay retry/backoff (`RelayLlm` + `FreeLlmApiLlm`)** → **Task 6** (concrete task: shared `RetryingLlm` decorator, applied at the VERIFIED seams per §0 #8 — `FreeLlmApiLlm` in `productionGraderResolver`, `RelayLlm` at `drillCriticLlmFactory`. The rulings doc's phrase "both reachable from `productionGraderResolver`" is factually corrected by §0 #8 — `RelayLlm` is not a resolver member; the ruling's deliverable, retry/backoff for both named clients, is preserved unchanged).
- **Stays carried (explicit re-carry lines in the Task 14 manifest, NOT done here):** cross-language schema-hash CI test · Linux visual-baseline regeneration · FSRS re-seed wart · CodeMirror editor (new, from R-6-Q9).

## Section 0.8 — FILE MANIFEST (machine-checked against Lane A / Plan 4b at plan freeze)

> The CREATE+MODIFY lists below must intersect to ZERO with Plan 4b's CREATE+MODIFY lists (SHARED excluded). Any hit → plan-writer fix round (max 1) → still hit → PM halt. Task 0 re-runs the intersection probe against Plan 4b's plan doc + any 4b commits already landed.

### CREATE

```
src/main/kotlin/jarvis/tutor/grader/GraderChain.kt
src/main/kotlin/jarvis/tutor/grader/GraderRouting.kt
src/main/kotlin/jarvis/tutor/grader/NumericOracleGrader.kt
src/main/kotlin/jarvis/tutor/grader/RubricGrader.kt
src/main/kotlin/jarvis/tutor/grader/ExecutionGrader.kt
src/main/kotlin/jarvis/tutor/grader/LanguageRunners.kt
src/main/kotlin/jarvis/tutor/MisconceptionTaxonomy.kt
src/main/kotlin/jarvis/tutor/ProblemsRepo.kt
src/main/kotlin/jarvis/RetryingLlm.kt
src/main/kotlin/jarvis/web/PracticeRoutes.kt
src/main/resources/grader/misconception-codes.json
src/main/resources/practice/mock-exam-formats.json
tools/fetch-alk.mjs
tools/alk/README.md
tools/alk/alki.cmd
tools/alk/alki.sh
content/PA/problems/pa-prob-001.yaml            (+ siblings as seeded)
content/ALO/problems/alo-prob-001.yaml          (+ source extract under content/ALO/_sources/)
content/PS/problems/ps-prob-001.yaml            (+ source extract under content/PS/_sources/)
src/main/kotlin/jarvis/tutor/ProblemSeed.kt
src/test/kotlin/jarvis/tutor/grader/GraderChainTest.kt
src/test/kotlin/jarvis/tutor/grader/GraderRoutingTotalityTest.kt
src/test/kotlin/jarvis/tutor/grader/LlmNeverAloneTest.kt
src/test/kotlin/jarvis/tutor/grader/NumericOracleGraderTest.kt
src/test/kotlin/jarvis/tutor/grader/RubricGraderTest.kt
src/test/kotlin/jarvis/tutor/grader/ExecutionGraderSandboxTest.kt
src/test/kotlin/jarvis/tutor/MisconceptionTaxonomyTest.kt
src/test/kotlin/jarvis/tutor/ProblemsRepoTest.kt
src/test/kotlin/jarvis/RetryingLlmTest.kt
src/test/kotlin/jarvis/web/PracticeRoutesTest.kt
src/test/kotlin/jarvis/web/MockExamAdditiveRouteTest.kt
src/test/resources/fixtures/grader-golden/{PA,ALO,PS}/llm-judge/*.json
src/test/resources/fixtures/grader-golden/{PA,ALO,PS,POO}/execution-grader/*.json
tutor-web/src/lib/practiceStrings.ts
tutor-web/src/lib/practiceApi.ts
tutor-web/src/components/practice/ProofDrill.tsx
tutor-web/src/components/practice/ProofDrill.test.tsx
tutor-web/src/components/practice/StepTraceDrill.tsx
tutor-web/src/components/practice/StepTraceDrill.test.tsx
tutor-web/src/components/practice/CodePractice.tsx
tutor-web/src/components/practice/CodePractice.test.tsx
tutor-web/src/components/practice/DeliverableTracker.tsx
tutor-web/src/components/practice/DeliverableTracker.test.tsx
tutor-web/e2e/practice/helpers/practiceServer.ts
tutor-web/e2e/practice/helpers/smoke.ts
tutor-web/e2e/practice/proof-drill.spec.ts
tutor-web/e2e/practice/step-trace.spec.ts
tutor-web/e2e/practice/code-practice.spec.ts
tutor-web/e2e/practice/mock-exam.spec.ts
tutor-web/e2e/practice/deliverable-tracker.spec.ts
build-review/tmp/lane-b-plan6-patches/test-yml-practice.patch
build-review/tmp/lane-b-plan6-patches/signatures-lock-plan6.patch
build-review/tmp/lane-b-plan6-patches/MERGE-MANIFEST.md
```

(`tools/alk/alk-<version>.jar` + `tools/alk/alk.sha256` are CREATE **only on the R-6-Q2 fallback path** — see Task 0. `content/POO/problems/*.yaml` + `content/POO/_sources/*` are CREATE **only if Task 2 Step 3's execution-time POO search finds real material** — they feed Task 10's POO deliverable seed-if-found; absence = named execution-time finding.)

### MODIFY (Lane B exclusive per the rulings lane table)

```
src/main/kotlin/jarvis/tutor/DrillGrader.kt
src/main/kotlin/jarvis/tutor/GradeScoring.kt
src/main/kotlin/jarvis/tutor/GradeTeachingPayload.kt
src/main/kotlin/jarvis/tutor/Tasks.kt
src/main/kotlin/jarvis/tutor/TaskPrep.kt
src/main/kotlin/jarvis/tutor/MockExamTable.kt
src/main/kotlin/jarvis/content/KcBeats.kt          (UNASSIGNED in the lane table — additive 2-field BeatAttempt edit; Task 0 flags it for the machine check; if Plan 4b also lists it → PM halt)
.gitignore                                         (Task 0 — Alk fetch-only ignore entries; PM amendment 2026-06-13 #2; not in any Plan-4b list)
src/main/kotlin/jarvis/web/TutorRoutes.kt
src/main/kotlin/jarvis/web/WebMain.kt
src/main/kotlin/jarvis/web/MockExamRoutes.kt
src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt
src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt
src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt
src/test/resources/fixtures/grader-golden/_README.md
tutor-web/src/components/DrillStack.tsx
tutor-web/src/components/DrillStack.confidence.test.tsx     (and the other 5 DrillStack.*.test.tsx as the CHECK change requires)
tutor-web/src/components/MockExamShell.tsx
tutor-web/src/components/MockExamShell.test.tsx
tutor-web/src/lib/drillGrader.ts
tutor-web/src/main.tsx
tutor-web/e2e/phase5-core-loop.spec.ts
```

### SHARED — PM-MERGE-ONLY (patch files under `build-review/tmp/lane-b-plan6-patches/`; Lane B NEVER edits these directly)

```
.github/workflows/test.yml                                  → test-yml-practice.patch (R toolchain + Alk fetch + practice-e2e job)
docs/superpowers/plans/2026-06-02-interface-signatures-lock.md → signatures-lock-plan6.patch (additive §NEW-P practice contracts; see the lock-amendment note below)
docs/superpowers/plans/2026-06-11-one-pass-plan-index.md     → PM updates status line at merge
src/main/kotlin/jarvis/tutor/Migration.kt                    → NOT EDITED (no new tables; additive columns ride createMissingTablesAndColumns)
build.gradle.kts                                             → NOT EDITED (no new dep; junit-params landed in 4a; Alk is a runtime subprocess jar, not a compile dep). If a need emerges → patch file + PM, never direct.
tutor-web/package.json + package-lock.json                   → NOT EDITED (no new npm dep — R-6-Q9 textarea; lane e2e runs via `npx playwright test e2e/practice e2e/phase5-core-loop.spec.ts` with `JARVIS_PRACTICE_E2E=1`, no script key needed)
tutor-web/playwright.config.ts                               → NOT EDITED (testDir ./e2e already collects e2e/practice/)
```

> **Lock-amendment note (DURABLE RULE reconciliation):** the signatures lock demands deviation-amends-lock in the SAME commit, but the lock file is PM-merge-only for both lanes. Resolution: Plan 6 makes **zero deviations** from frozen contracts (every wire change is additive-nullable; Tasks 4/7/8/11 each carry an explicit lock-check step). The NEW practice contracts are frozen in §0.9 of THIS plan doc and delivered to the lock as the additive patch `signatures-lock-plan6.patch` — which ALSO carries (a) the explicit **§NEW-L additive-amendment entry** for the `ApiBeatAttempt` `numeric_answer`/`numeric_tolerance` fields (Task 4 — `ApiBeatAttempt` is served inside the frozen `ApiLessonReply.beats`, lock `:616`; the §O amendment precedent applies) and (b) the **Task 11 P2-5 reconciliation note** (rubric-leg grading applies to bank-problem G-items only; the `kind` wire field and `kc_results` `verification_status` serialization stay untouched). The PM applies the patch in the merge commit — same-merge, never silently contradicting.

## Section 0.9 — Canonical contracts (frozen for this plan; the lock patch mirrors them)

- **A. Grader chain (`jarvis.tutor.grader`):**

```kotlin
enum class GraderLegKind { NUMERIC_ORACLE, EXECUTION, RUBRIC, LLM_JUDGE }

/** One per-item verdict — reused for proof sub-steps (REQ-2), trace steps (REQ-5) and rubric G-items (REQ-16/17). */
@Serializable
data class ItemVerdict(
    val id: String,            // substep id / step index / rubric label "G1".."Gn"
    val label: String,         // learner-visible RO label
    val passed: Boolean,
    val points_earned: Double? = null,
    val points_max: Double? = null,
)

data class ChainGradeResult(
    val decidedBy: GraderLegKind,        // the leg that produced the verdict (REQ-26 audit trail)
    val correct: Boolean,
    val score: Double,
    val itemVerdicts: List<ItemVerdict>,
    val misconception: String?,          // taxonomy code or null
    val feedbackRo: String,
    val degradedLegs: List<GraderLegKind>, // legs skipped because disabled/unavailable (R-6-Q1 honesty)
)

class GraderChain /* private ctor */ {
    companion object {
        /** Builder THROWS on: empty legs; first leg == LLM_JUDGE (INV-6.1); legs == [LLM_JUDGE] alone (INV-6.3). */
        fun of(legs: List<GraderLeg>): GraderChain
    }
    suspend fun grade(input: GradeInput): ChainGradeResult  // walks legs in order; a disabled/non-applicable leg defers to the next and is recorded in degradedLegs
}
```

- **B. Numeric tolerance — THE single source:** `NumericOracleGrader.matches(expected: Double, got: Double, tol: Double?): Boolean` = `abs(got - expected) <= (tol ?: DEFAULT_ABS_TOL)` with `DEFAULT_ABS_TOL = 1e-9`. Consumers after Task 4: `GradeScoring.answerMatches` (delegates, keeps its effective 1e-9), the lesson CHECK branch (delegates, keeps its `tol ?: 0.0` per-site default — behavior pinned), the new lesson ATTEMPT numeric path, and the oracle chain leg. SymPy (via the `SympyTool.kt` subprocess pattern) is used ONLY when the expected answer is symbolic (`expr:` prefix in the problem's solution payload); plain numerics never shell out.
- **C. Execution sandbox (R-6-Q1):** per run — fresh `Files.createTempDirectory("jarvis-exec-")`, student source written inside, working dir = the temp dir, `ProcessBuilder` with hard wall-clock timeout (compile ≤ 20s, run ≤ 10s — runtime constants in `ExecutionGrader.kt`, tunable), stdout+stderr captured bounded at 64 KiB (truncated with a marker), temp dir deleted in `finally`. **No-network is NOT guaranteed — documented limitation** (single-user app, own machine) in the `ExecutionGrader.kt` KDoc verbatim. Languages: R = `Rscript main.R`, Python = `python main.py` (fallback `python3`), C++ = `g++ -std=c++17 main.cpp -o main` then run, Alk = **(PM-AMENDED 2026-06-12 after Task-0 verification — the `java -jar` form was wrong)** the verified classpath invocation `java -Djava.library.path=<alkdir>/lib -cp "<alkdir>/alk.jar<sep><alkdir>/lib/com.microsoft.z3.jar" main.ExecutionDriver -a main.alk` (`<sep>` = `;` Windows / `:` Linux; needs the Z3 native libs in `<alkdir>/lib`), ENCAPSULATED in wrapper scripts `tools/alk/alki.cmd` + `tools/alk/alki.sh` emitted by `fetch-alk.mjs` — `LanguageRunners` calls the wrapper, never the raw classpath form. **bash/POSIX has NO runner — routed to rubric (R-6-Q8).** Toolchain self-detection at construction (`ToolchainProbe`), resolution order per binary: (1) env override (`JARVIS_RSCRIPT`, `JARVIS_ALK_DIR`, etc.), (2) PATH, (3) known default install locations (e.g. `C:\Program Files\R\R-*\bin\Rscript.exe` on Windows — the Task-0 finding: R 4.5.3 installed but not on PATH); then probe `--version`: missing → that language's leg reports `available=false`, the chain degrades to the next leg honestly, the degradation is logged AND surfaced (`degradedLegs` → RO copy on the reply). Tests (Task 5) run STRICT on PC+CI (missing toolchain = RED, not skip); env `JARVIS_EXEC_GRADER_OPTIONAL=1` downgrades to skip-with-log for non-required environments (VPS).
- **D. Misconception registry (R-6-Q4, E18):** data file `src/main/resources/grader/misconception-codes.json` — `{ "subjects": { "<subject>": [ { "code": "UPPER_SNAKE", "label_ro": "...", "judge_hint_en": "one-line pattern description for the prompt" } ] } }`. Loaded by `MisconceptionTaxonomy` (`codesFor(subject): List<MisconceptionCode>`; unknown subject → `[OTHER]` only). Seeds: PS = the existing 3 (`L2_ESTIMATOR_CONFUSION`, `MINIMAX_CONFUSION`, `MODE_CONFUSION`), PA flagship = `REDUCTION_DIRECTION_FLIPPED` ("students flip the arrow constantly" — reducing the new problem TO the known NP-hard problem instead of FROM it), plus `OTHER` everywhere. Plan 5 mines R-ERRORS breadth into this same file/registry — mechanism-now, mining-later. `DrillGrader` prompts are parameterized by subject: the hardcoded enum block is REPLACED by codes rendered from the registry; `GradeResult`/`parseGradeJson` shapes unchanged.
- **E. Golden fixture shapes (extends the 4a `_README` convention; INV-6.4):**

```json
// {subject}/llm-judge/{id}.json — RECORDED REPLAY (R-6-Q5): raw LLM output captured ONCE
// at implementation time (relay / :free — no paid APIs), replayed through the REAL
// parseGradeJson + GradeScoring in CI. Zero live LLM calls in CI.
{ "grader": "llm-judge", "subject": "PA", "id": "pa-llm-flipped-reduction",
  "input": { "raw_llm_output": "<verbatim recorded model text>" },
  "expected": { "parsed": true, "score": 0.3333333333333333, "correct": false,
                "misconception": "REDUCTION_DIRECTION_FLIPPED", "confident": true } }

// {subject}/execution-grader/{id}.json — INV-6.2 known-good / known-bad-mutant pairs
{ "grader": "execution", "subject": "PS", "id": "ps-exec-r-good-001", "language": "r",
  "input": { "source": "<full program>", "stdin": null, "expected_stdout": "<exact>" },
  "expected": { "pass": true },
  "corpus_note": "derived from content/PS/problems/ps-prob-001.yaml (real corpus)" }
```

- **F. `/drill/grade` reply — ADDITIVE fields only (lock §O check):** `ApiDrillGradeReply` (TutorRoutes.kt) gains `decided_by: String? = null` (`"numeric-oracle"|"execution"|"rubric"|"llm-judge"`), `degraded_legs_ro: List<String> = emptyList()` (RO copy, e.g. `"Rularea codului indisponibilă pe acest server — verificat structural"`), `item_verdicts: List<ItemVerdict> = emptyList()`. The frozen fields `misconception: String?` (scalar code), `misconception_payload`, `ladder_rungs`, `self_explanation_prompt` are NOT renamed/retyped (lock §O — verified verbatim at plan-write). `drillGrader.ts` mirrors additively.
- **G. Practice endpoints (NEW, additive — no frozen route touched; lock §13 additive contract):** all session-auth + `csrfProtect`, mirroring existing route style; installed by `installPracticeRoutes()` registered in `WebMain.kt`.
  - `GET  /api/v1/practice/problems?subject={s}&surface={proof|trace|code}` → `{ problems: [ApiPracticeProblem] }` — **NEVER includes the reference solution** (INV-6.6 is server-enforced, not CSS).
  - `POST /api/v1/practice/proof/{problemId}/grade` body `{ substeps: [{id, text}] }` → `{ item_verdicts, score, correct, decided_by, feedback_ro }` (per-sub-step, REQ-2).
  - `POST /api/v1/practice/trace/{problemId}/step` body `{ step_index, value }` → `{ verdict: ItemVerdict, feedback_ro }` (per-step, REQ-5 — sign error at step 3 caught at step 3).
  - `POST /api/v1/practice/code/{problemId}/run` body `{ source }` → `{ compiled, stdout_trunc, stderr_trunc, timed_out, degraded_legs_ro }` (execution leg only, no grade write).
  - `POST /api/v1/practice/code/{problemId}/grade` body `{ source }` → full chain reply **+ `reference_solution_ro: String?`** — the ONLY payload that ever carries the reference (attempt-gated, REQ-8/INV-6.6).
  - `GET  /api/v1/practice/deliverables` → `{ deliverables: [{ id, subject, title_ro, deadline: String?, sub_problems: [{label_ro, points}], prep_drill_ids: [String], source_doc, synthetic: Boolean }] }` (REQ-18/21; honest nulls).
- **H. Mock-exam ADDITIVE semantics (R-6-Q7; REQ-11..17):** `MockExamsTable` gains nullable columns `format_json` (paper structure: phases, question ordering, sub-parts, point brackets, multiselect flags), `started_at` (timer anchor), `phase_index` (current permitted-materials phase), `rubric_results_json` (per-G-item breakdown), `synthetic_tag` (bool default true until a real past paper is digested). Existing columns/DTOs untouched. Existing endpoints keep byte-compatible behavior for legacy payloads (pin test). Additive endpoint `POST /api/v1/mock-exam/{id}/phase` advances the phase. Formats data = `src/main/resources/practice/mock-exam-formats.json` encoding REQ-13/14 verbatim: PA 3×(a–d) with printed point brackets + 0.5 partial credit; POO course-exam MCQ with execution-trace reveal; SO 18-item grid mixing 0.5-pt radio + 1-pt "bifați TOATE" multiselect (R-MULTISELECT); SO TP / POO T1/T2 timed rubric SELF-assessment (all-or-nothing sub-items, score-halving penalty; the app NEVER auto-grades these — honest RO copy); PS = 15-minute mini-test simulation (6×15-min live-page format, erratum E3); ALO 30-min docs-allowed phase switch.
- **I. Deliverable data (R-6-Q10; REQ-18/19/20):** `TasksTable` gains nullable `deliverable_json` column: `{ sub_problems: [{label_ro, points}], prep_drill_ids: [String], source_doc: String, source_quote: String }`. Seeds from VERIFIED course-meta only: ALO Teme T1–T5 (points 50/60/60/60/70 — already in the Plan-2 grade-model registry rows, provenance = the 2026-06-11 verified sweep), PS Teme A–D (20-pt bucket, live-page contract). Deadlines unknown → `deadline` stays the honest existing column where known, UI renders "necunoscut" state otherwise. POO GitHub lab deliverable: contingent on Task 2 Step 3's EXECUTION-TIME exhaustive disk search for POO lab specs (R-6-Q10 names them as a data source) — found → seeded with provenance (committed extract + verified quote anchor, REQ-20); genuinely absent after the search → honest empty state, NO fabricated entry, absence NAMED in the task report (REQ-19: the app prepares, never grades real submissions — copy says so in RO).
- **J. `RetryingLlm` decorator:** `class RetryingLlm(private val inner: Llm, private val maxRetries: Int = 2, ...) : Llm` — retries `complete()` ONLY on transport-class failures (IOException / timeout / HTTP 5xx-shaped errors surfaced as exceptions), bounded exponential backoff, NEVER retries on successful-but-malformed output (parse errors are the caller's concern). Wrap sites (the VERIFIED seams, §0 #8): `productionGraderResolver` wraps its `FreeLlmApiLlm()` member; `drillCriticLlmFactory` wraps its `RelayLlm()` — `RelayLlm` is NOT a resolver member. OpenRouter keeps its own 429 handling; `ClaudeMaxLlm` (CLI subprocess) stays unwrapped — outside the carried follow-up's two named clients; double-wrap is harmless but skipped. Closes the pa-kc-006 false-negative class for the grader/critic paths: a transient relay timeout reads as RETRY, not as a failed grade.
- **K. Problem statement/solution sub-schemas per surface archetype-class (consumed by the §0.9-G endpoints; mirrored 1:1 in the Task-2 YAML seeds → `ProblemsTable.statementJson`/`solutionJson`):**
  - **proof** (REQ-2/3): `statement.proof_frame = { template_ro: String /* the fill-in frame, "{{slot-id}}" placeholders */, substeps: [{ id: String, label_ro: String, matcher: { kind: "exact"|"contains"|"regex", value: String } }] }` — the proof grade endpoint walks `substeps` and emits one `ItemVerdict` per NAMED sub-step (the REQ-2 per-sub-step verdicts come from these structural matchers, LLM-judge only after).
  - **trace** (REQ-5): `solution.trace_steps = [{ index: Int, label_ro: String?, expected: String /* numeric literal or exact string */, tolerance: Double? }]` — the trace step endpoint compares the submitted `value` against `trace_steps[step_index]` (numeric comparison via `NumericOracleGrader.matches`, §0.9-B). **The seeded trace problem MUST carry ≥3 trace steps** — Task 13's "wrong value at step 3 caught at step 3" pin (REQ-5/INV-6.5) is impossible otherwise.
  - **code** (REQ-7/8): `solution.reference_source: String` + `solution.expected_stdout: String` (the execution leg's pass contract; the §0.9-E execution fixture shape derives from it), `examLanguage` on the problem row per §3.4 R-LANG.

## Execution protocol (every task)

1. **Two-stage execution:** each task = an **implementer subagent** (does the work in the worktree, runs the listed acceptance) then an **independent reviewer subagent** (fresh context; re-runs the acceptance commands itself, greps for the STOP conditions, checks the diff against the task's Files list — any file outside the list = FAIL; verifies no SHARED file was edited directly). Reviewer FAIL → implementer fixes (max 1 round) → still failing → BLOCKED, escalate to PM. **PM clause (2026-06-13): a commit explicitly RATIFIED by a PM resolution note in this doc passes the Files-list check BY DEFINITION — the ratification is the amendment; reviewers flag any residual concern as a non-blocking note to the PM, never a FAIL on that commit.**
2. **TDD where the task builds logic:** failing test first, then implementation (Tasks 1–8, 11, 12). Surface-rendering tasks (9, 10) write component tests alongside.
3. **Full suite per task, never partial:** `gradle --no-daemon -p ../jarvis-kotlin-lane-b :check` AND `npm --prefix ../jarvis-kotlin-lane-b/tutor-web test` green before every commit (never pipe through `| tail`). The known `stateCacheConcurrentPersistNeverTearsJson` concurrency flake: if it fires, NAME it in the task report and re-run — never absorb any other red under it (R-4b-Q9 carve-out, applies repo-wide).
4. **Commits:** explicit paths only (NEVER `git add -A` — the worktree carries untracked demo artifacts), one commit per task on `lane-b/plan6`, message ends with the standard co-author line.
5. **Signatures-lock check:** Tasks 4, 7, 8, 11 (route/payload/serve contracts) each contain an explicit lock-check step. A forced deviation from any frozen shape = BLOCKED → PM (never amend the lock from the lane; the lock is PM-merge-only).
6. **Subagent model routing:** implementers on the default strong model for grader-chain/sandbox logic (Tasks 3, 5, 7); Sonnet-class is fine for fixtures, strings files, seeds, and e2e specs. Reviewers always independent.

## Tasks

| # | Task |
|---|---|
| 0 | Worktree on `lane-b/plan6` + zero-intersection probe vs Plan 4b + toolchain probe + Alk artifact/license investigation (R-6-Q2) |
| 1 | Misconception taxonomy mechanism: data registry + seeds + `DrillGrader` per-subject prompts (R-6-Q4, E18, REQ-27/28) |
| 2 | `ProblemsRepo` + real-corpus problem seeds with provenance (R-6-Q3, REQ-9; PA/ALO/PS) |
| 3 | Grader chain core: `GraderChain` + `NumericOracleGrader` + `RubricGrader` + routing table; INV-6.1 (pending logic) + INV-6.3 tests (REQ-22/23/26, E16; R-6-Q8) |
| 4 | Numeric-ATTEMPT tolerance fold → single source (carried follow-up #1) + `LessonBeatGradeRouteTest` pin [lock §NEW-L check] |
| 5 | Execution grader: subprocess sandbox + language runners + INV-6.2 + execution goldens (R-6-Q1, REQ-24/25, E17) |
| 6 | `RetryingLlm` decorator (carried follow-up #2) + recorded-replay LLM-judge goldens (R-6-Q5, INV-6.4) |
| 7 | `/drill/grade` chain integration + additive reply fields + `drillGrader.ts` mirror [lock §O check] |
| 8 | `PracticeRoutes.kt` + `practiceApi.ts` (proof/trace/code/deliverables endpoints; server-enforced attempt gate) [lock §13/§N check] |
| 9 | Surfaces I: `practiceStrings.ts` + `ProofDrill` + `StepTraceDrill` + mounts (REQ-2..6) |
| 10 | Surfaces II: `CodePractice` (textarea, R-6-Q9) + `DeliverableTracker` + deliverable seeds (REQ-7..10, 18..21; R-6-Q10) |
| 11 | Mock-exam ADDITIVE: delta-recon → table columns + route semantics + `MockExamShell` (REQ-11..17; R-6-Q7) [lock check + regression pin] |
| 12 | DrillStack CHECK card → real graded input + DrillStack chrome → `practiceStrings.ts` (REQ-29, E8) |
| 13 | Practice e2e suites: INV-6.5 + INV-6.6 + interaction smoke, against the seeded real-corpus server + CI patch file |
| 14 | Lane full-suite gate + merge manifest (re-carry lines; PM merge recipe) |
| 15 | POST-MERGE (PM-gated): VPS deploy + toolchain self-detect probe + production problem-seed behind INV-3.1 + live interaction smoke (R-6-Q1) |

---

## Task 0 — Worktree on `lane-b/plan6` + zero-intersection probe + toolchain probe + Alk investigation (R-6-Q2)

Re-points the existing Lane B worktree at a fresh branch off current `main`, machine-checks this plan's CREATE+MODIFY manifest against Plan 4b's manifest AND against any 4b commits already landed, probes the PC toolchain (R is the open question), and resolves the Alk interpreter artifact question. **No source edits except the Alk fetch tooling; HALT conditions are real.**

**Files:**
- Create: `tools/fetch-alk.mjs`, `tools/alk/README.md`, `tools/alk/alki.cmd`, `tools/alk/alki.sh` **(PM amendment 2026-06-13: the two wrappers are STATIC TRACKED files, added here + §0.8 — resolving the first fix-round contradiction; the commit-the-jar fallback is RESCINDED per the R-6-Q2 ruling, so the jar/sha256 CREATE entries are gone)**
- Modify: `.gitignore` **(PM amendment 2026-06-13 #2: the Alk ignore entries — `tools/alk/v4.3/`, the zip — are REQUIRED by the R-6-Q2 fetch-only ruling and were always intended; listed here + §0.8 now)**
- Read-only: `docs/superpowers/plans/2026-06-12-plan4b-*.md` (Lane A plan doc), this doc's §0.8

- [ ] **Step 1: Branch.** Confirm `main` in the MAIN tree is at the expected merge state (Plan 4a merged). Then in the worktree:

```powershell
git -C ..\jarvis-kotlin-lane-b status --short          # must be clean (only known-untracked artifacts)
git -C ..\jarvis-kotlin-lane-b switch -c lane-b/plan6 main
git -C ..\jarvis-kotlin-lane-b rev-parse --abbrev-ref HEAD   # → lane-b/plan6
```

**STOP if** the worktree has uncommitted tracked changes, or the worktree is missing (then `git -C C:\Users\User\jarvis-kotlin worktree add ..\jarvis-kotlin-lane-b -b lane-b/plan6 main`). Then `npm --prefix ..\jarvis-kotlin-lane-b\tutor-web ci` and a baseline `gradle --no-daemon -p ..\jarvis-kotlin-lane-b :check` + `npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test` — **STOP if the inherited baseline is red** (never build on a red lane).

- [ ] **Step 2: Zero-intersection probe.** Extract Plan 4b's CREATE+MODIFY file lists from its plan doc (`docs/superpowers/plans/2026-06-12-plan4b-*.md` §manifest) and intersect with §0.8 CREATE+MODIFY above (SHARED excluded on both sides). ALSO intersect §0.8 against files Plan 4b has ALREADY landed on `main` since this plan's branch-cut (`git log --name-only <cut>..main`). Expected: **zero intersection**. Special flag: `src/main/kotlin/jarvis/content/KcBeats.kt` is UNASSIGNED in the rulings lane table and claimed by Lane B here — if Plan 4b's manifest also lists it, that is a HIT → report to PM verbatim (fix round / PM halt per the rulings machine-check rule). **STOP on any hit.**

- [ ] **Step 3: Toolchain probe (R-6-Q1 required-green = PC + CI).** Probe in the worktree shell: `g++ --version` (MinGW), `python --version` (else `python3`), `java -version`, `Rscript --version`. Record results. **If `Rscript` is missing on the PC: BLOCKED — escalate to PM for install approval** (R is free software; installing mutates Alex's machine, so it is a PM decision, not an executor improvisation). The R leg's code still ships either way (self-detection); but INV-6.2's R case cannot go green on PC without it.

- [ ] **Step 4–8: Alk artifact + license investigation (R-6-Q2).**
  - Fetch the repo metadata: `gh api repos/alk-language/java-semantics` + `gh api repos/alk-language/java-semantics/releases` + the repo `LICENSE`/README (raw fetch).
  - Determine: (a) is there a stable release artifact (a runnable jar)? (b) what is the license? (c) what is the exact run CLI (`java -jar alk.jar <file>` vs a wrapper script — read the repo docs).
  - **Preferred path:** pinned-release fetch — write `tools/fetch-alk.mjs` (downloads the pinned release URL, verifies a recorded sha256, places the jar at `tools/alk/alk-<version>.jar` which is git-ignored on this path) + `tools/alk/README.md` recording URL, version, sha256, license, and the verified run CLI.
  - **Fallback (no stable artifact):** build/obtain the jar once, COMMIT it at `tools/alk/alk-<version>.jar` + `tools/alk/alk.sha256`, document the build provenance in the README.
  - **HALT FOR PM if the license is unclear or forbids redistribution/local use** — do not fetch-and-ship under an unknown license.
  - Acceptance: `node tools/fetch-alk.mjs` produces a jar whose sha256 matches the recorded value, and `java -jar tools/alk/alk-<version>.jar <hello-world.alk>` (a 3-line probe program written to a temp file) runs and prints the expected output. Record the exact CLI invocation in `tools/alk/README.md` — Task 5's Alk runner uses it verbatim.

- [ ] **Step 9: Commit** (`tools/fetch-alk.mjs`, `tools/alk/README.md`, + jar/sha256 only on the fallback path): `feat(plan6): task 0 — lane branch, intersection probe, toolchain probe, Alk pinned fetch (R-6-Q2)`.

**Acceptance:** branch `lane-b/plan6` exists at main HEAD; intersection probe printed zero hits (or PM was halted); toolchain results recorded in the task report; Alk jar verified runnable with checksum.

> **PM RESOLUTION NOTE (2026-06-12, after the first Task-0 run halted):** Steps 1–3 + the investigation are DONE and verified (commit `83f153d` on `lane-b/plan6`; branch at `bab5d3c`; baselines green: vitest 875, `:check` BUILD SUCCESSFUL; intersection 0/0; toolchain table recorded — R at `C:\Program Files\R\R-4.5.3\bin\Rscript.exe`, not on PATH). Both HALTs are RULED: see the §0.5 R-6-Q2 ruling row + the PM-amended §0.9-C Alk contract. Also note: `main` has since gained the PM commit `dc12c9f` (playwright.config.ts, SHARED/PM-only) — do NOT rebase; the merge handles it.
>
> **PM RESOLUTION NOTE 2 (2026-06-13, after the second Task-0 halt — the contradiction was a PM-amendment defect, owned):** Note 1 authorized tracked wrappers without listing them in this task's Files + §0.8 — now FIXED above (both lists carry `alki.cmd`/`alki.sh`). Ruling = **Option A, amended for portability:** the wrappers are STATIC TRACKED files, written PORTABLY (`%~dp0v4.3\bin` on Windows; `"$(cd "$(dirname "$0")" && pwd)/v4.3/bin"` on POSIX — never an absolute machine path), and **`fetch-alk.mjs` does NOT emit them** (it only downloads the pinned zip, verifies sha256, extracts to git-ignored `tools/alk/v4.3/`). Commit `bce072c` is RATIFIED as landed; the remaining work: (1) one follow-up commit rewriting both wrappers portable + stripping the emit logic from `fetch-alk.mjs`; (2) README "Tracked files" section updated to: fetch script + README + 2 portable wrappers tracked, everything under `tools/alk/v4.3/` + zip git-ignored; (3) acceptance re-run: `node tools/fetch-alk.mjs` → checksum match, then `tools\alk\alki.cmd <temp>\probe.alk` prints the expected output. Do NOT re-run baseline suites; do NOT re-investigate.
>
> **PM RESOLUTION NOTE 3 (2026-06-13, third halt — `.gitignore` literalism):** Ruling = the escalation's Option A. `bce072c`'s `.gitignore` edit (Alk fetch-only ignore entries) is RATIFIED — it was required by the R-6-Q2 ruling; `.gitignore` is now in this task's Files list + §0.8, and the Execution-protocol PM clause makes ratified commits pass the Files-list check by definition. **Task 0 closure check, in order:** (1) verify the portable-wrapper rewrite + fetch-script emit-strip from Note 2 LANDED (wrappers contain `%~dp0`/`dirname` forms, no absolute machine path; `fetch-alk.mjs` has no wrapper-emit logic); (2) verify acceptance: `node tools/fetch-alk.mjs` checksum-clean + wrapper runs the probe; (3) if both hold, Task 0 is COMPLETE — report status=done with the evidence, create NO new commits; if (1) is missing, land exactly that one commit then re-verify.

---

## Task 1 — Misconception taxonomy mechanism (R-6-Q4, E18; REQ-27/28)

Replaces the hardcoded 3-statistics-code prompt enum with a data-driven per-subject registry, hand-seeded with the PA flipped-reduction flagship + the existing 3 stats codes. Mechanism-now / mining-later: Plan 5 mines R-ERRORS breadth into the same registry. TDD.

**Files:**
- Create: `src/main/resources/grader/misconception-codes.json`, `src/main/kotlin/jarvis/tutor/MisconceptionTaxonomy.kt`, `src/test/kotlin/jarvis/tutor/MisconceptionTaxonomyTest.kt`
- Modify: `src/main/kotlin/jarvis/tutor/DrillGrader.kt`

- [ ] **Step 1 (RED):** `MisconceptionTaxonomyTest` — asserts: `codesFor("PS")` returns the 3 stats codes + OTHER; `codesFor("PA")` contains `REDUCTION_DIRECTION_FLIPPED` + OTHER; `codesFor("UNKNOWN")` returns `[OTHER]`; every code is UPPER_SNAKE; every entry has non-blank `label_ro` and `judge_hint_en`; the registry file parses.
- [ ] **Step 2 (GREEN):** registry JSON per §0.9-D (seeds: PS×3 + PA flagship + OTHER) + `MisconceptionTaxonomy` loader (classpath resource, parsed once, `ignoreUnknownKeys`).
- [ ] **Step 3:** `DrillGrader` prompt parameterization — `grade(...)` gains a `subject: String?` parameter (default null → OTHER-only, preserving every existing call-site compile); `GRADE_PROMPT_TEXT`/`GRADE_PROMPT_CODE` become builders that render the misconception-code block from `MisconceptionTaxonomy.codesFor(subject)`. The wire/`GradeResult` shapes are UNCHANGED (the `misconception` scalar already carries free-text codes — `GradeTeachingPayload.kt:106` `matchByGraderCode` confirms). Existing DrillGrader tests updated only where they pin the old prompt text.
- [ ] **Step 4:** full suite green; commit: `feat(plan6): data-driven misconception taxonomy + per-subject judge prompts (E18, R-6-Q4)`.

**Acceptance:** taxonomy test green; grep proves the literal `L2_ESTIMATOR_CONFUSION` no longer lives in a prompt string constant (it lives in the registry JSON); `:check` + `npm test` green.

---

## Task 2 — `ProblemsRepo` + real-corpus problem seeds with provenance (R-6-Q3; REQ-9)

Builds the repository over the three Plan-2 problem-bank tables and hand-seeds ≥1 REAL-corpus problem per subject where real material exists (PA/ALO/PS minimum). Provenance trust-net style: source doc + verbatim quote anchors. **Agents extract, the system verifies — NEVER Alex (no-oracle-inversion). Never ask Alex to pick/vet a problem.** Subjects without locatable corpus problems stay PENDING (named in INV-6.1's pending set, Task 3). TDD.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/ProblemsRepo.kt`, `src/main/kotlin/jarvis/tutor/ProblemSeed.kt`, `src/test/kotlin/jarvis/tutor/ProblemsRepoTest.kt`, `content/PA/problems/pa-prob-001.yaml` (+ siblings), `content/ALO/problems/alo-prob-001.yaml`, `content/PS/problems/ps-prob-001.yaml`, source extracts under `content/ALO/_sources/` + `content/PS/_sources/` as needed, `content/POO/problems/*.yaml` + extracts under `content/POO/_sources/` ONLY if Step 3's POO search finds real material

- [ ] **Step 1 (RED):** `ProblemsRepoTest` over an in-memory/scratch SQLite: insert/find by id, list by subject, list rubric items ordered by position, KC links round-trip, `countBySubject` (the INV-6.1 pending probe).
- [ ] **Step 2 (GREEN):** `ProblemsRepo` (constructor takes `Database`, mirrors existing repo style e.g. `TaskRepo`) over `ProblemsTable`/`ProblemRubricItemsTable`/`ProblemKcLinksTable`.
- [ ] **Step 3 — locate real material (search exhaustively BEFORE declaring absence):** PA: `content/PA/_sources/pa-lecture-01.md` is in-repo (algorithm/correctness material) — additionally probe Desktop for `NP_Complete_Course_Notes.md` (spec §10.2). ALO: probe Desktop + Downloads + any course dirs for `exercitii*.pdf` / ALO course chapters / `Fituica_Metode_Numerice` (spec §10.2 names them). PS: probe for `ps*.pdf` / PS seminar sheets. **POO: probe Desktop + Downloads + any course dirs for POO lab specs / GitHub-lab assignment docs (R-6-Q10 NAMES "corpus course docs with provenance (POO lab specs etc.)" as deliverable data sources — this search feeds Task 10's deliverable seeding AND any POO problem; the in-repo `content/POO/` being empty is a plan-write observation, NOT a finding — absence must be an EXECUTION-TIME finding from this search).** Full recursive search (the search-exhaustively rule) before reporting a subject has no material. **If a subject's real material is genuinely absent on disk → that subject stays PENDING (record it in the task report; INV-6.1 pending set in Task 3 reflects it; Task 10's POO empty state inherits THIS search's outcome) — do NOT fabricate a problem and do NOT ask Alex to supply/pick one.**
- [ ] **Step 4 — author the problem YAMLs** (`content/{subject}/problems/*.yaml`, NEW files — zero overlap with Lane A's `content/PA/kcs/*.yaml` per the lane table). Schema mirrors `ProblemsTable` 1:1 (id, subject, archetype, statement_ro, parameter_slots, solution_present, solution, exam_language per §3.4 R-LANG: PA=`alk`, PS=`r`, rubric items G1..Gn with points/AP-WP/all_or_nothing, kc_links, source: `{doc, page?, quote}` — the quote is a VERBATIM span from the source extract, trust-net style) **with the per-archetype statement/solution sub-schemas per §0.9-K (proof → `proof_frame` + named substeps; trace → `trace_steps` with expected values; code → `reference_source` + `expected_stdout`).** **Coverage is keyed per surface archetype-class, NOT just per subject:** across the seeded subjects where material exists, author ≥1 PROOF-archetype problem, ≥1 TRACE-archetype problem (**≥3 trace steps** — Task 13's step-3 pin depends on it), and ≥1 CODE-archetype problem. A surface archetype-class with NO locatable real-corpus problem anywhere is NAMED pending exactly like the subject pending set — never silently green (a single PA code problem does NOT make the proof and trace surfaces covered). For non-in-repo sources, commit a minimal legible extract under `content/{subject}/_sources/` (text, with origin path/URL recorded in its header) so the quote anchor resolves against a committed artifact.
- [ ] **Step 5:** `ProblemSeed.seed(db)` — idempotent (upsert by id) loader of `content/*/problems/*.yaml` via `ProblemsRepo`; used by tests + the Task 13 e2e server + the Task 15 production seed. A verification pass asserts every seeded problem's `source.quote` is a substring of its committed source doc (system verifies, not Alex) — a failed anchor = seed REFUSED, loud.
- [ ] **Step 6:** full suite green; commit (explicit paths): `feat(plan6): ProblemsRepo + real-corpus problem seeds w/ verbatim provenance (R-6-Q3)`.

**Acceptance:** ≥1 problem per subject with locatable real material AND ≥1 problem per surface archetype-class (proof / trace / code) across those subjects, the trace seed carrying ≥3 steps (PA expected from in-repo material; ALO/PS/POO per Step 3's findings — pending SUBJECTS and pending ARCHETYPE-CLASSES both NAMED, never silently green); every seed's quote anchor machine-verified; `ProblemsRepoTest` green; full suite green.

---

## Task 3 — Grader chain core + routing table + INV-6.1/INV-6.3 (REQ-22/23/26, E16; R-6-Q8)

The ordered chain with type-level enforcement (first leg non-LLM; LLM never alone), the numeric-oracle leg (E16), the structural rubric leg (independent of LLM-emitted booleans), and the data-driven routing table (subject × archetype-class → chain). TDD.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/grader/GraderChain.kt`, `GraderRouting.kt`, `NumericOracleGrader.kt`, `RubricGrader.kt`; `src/test/kotlin/jarvis/tutor/grader/GraderChainTest.kt`, `NumericOracleGraderTest.kt`, `RubricGraderTest.kt`, `GraderRoutingTotalityTest.kt`, `LlmNeverAloneTest.kt`

- [ ] **Step 1 (RED) chain laws:** `GraderChainTest` — `GraderChain.of(listOf(llmLeg))` THROWS (INV-6.3); `of(listOf(llmLeg, rubricLeg))` THROWS (first leg LLM, INV-6.1); `of(emptyList())` THROWS; a chain whose first leg defers (not applicable / disabled) falls through to the next and records the degradation; the deciding leg is reported in `decidedBy`.
- [ ] **Step 2 (GREEN):** implement §0.9-A verbatim (private ctor + `of` builder with the three `require`s).
- [ ] **Step 3 (RED) oracle:** `NumericOracleGraderTest` — `matches(2.5, 2.5000000001, null)` false at default tol? (no: `1e-9` → false since diff `1e-10 ≤ 1e-9` is TRUE — pick test values deliberately: assert the ≤ boundary semantics exactly); tolerance honored when given; non-numeric attempt → leg defers (not decided), never throws; symbolic `expr:`-prefixed expected routes through the SymPy subprocess bridge (test gated on python availability — STRICT on PC/CI per §0.9-C); grading a numeric problem produces `decidedBy=NUMERIC_ORACLE` with RO feedback from `practiceStrings`-equivalent server copy.
- [ ] **Step 4 (GREEN):** `NumericOracleGrader` per §0.9-B — including the single-source `matches()` that Task 4 folds everything onto. Reuses the `SympyTool.kt` subprocess pattern for the symbolic case only.
- [ ] **Step 5 (RED) rubric leg:** `RubricGraderTest` — **the leg consumes rubric items from EITHER of two sources through ONE common item model: (a) `ProblemRubricItemsTable` rows via `ProblemsRepo` (bank problems), and (b) request/task_prep-derived rubric items (the DOMINANT live `/drill/grade` traffic — `drillGrader.ts:82/:108` `rubricItems` ride the request with NO problem-bank row).** Tests cover both sources: per-item points summed; AP/WP split reported on the `ItemVerdict`s; `allOrNothing` item = full points or zero; penalty rules applied once (e.g. wrong-sign penalized once, not per-line — encode the penalty JSON contract); produces per-G-item `ItemVerdict` list (REQ-16/17 consumers). The rubric leg's per-item verdicts come from STRUCTURAL checks (presence/shape matchers declared per rubric item in `penaltyRulesJson`/matcher config — request-derived items get the same matcher treatment), NOT from LLM-emitted booleans for EITHER source — where an item has no machine-checkable matcher, the leg defers that item to the LLM-judge leg pairing (REQ-26) and says so (the deferral is recorded, surfacing in `degradedLegs`/`decided_by` honesty).
- [ ] **Step 6 (GREEN):** `RubricGrader` over `ProblemsRepo` rubric rows AND request-supplied rubric items (one leg, two item sources, one `RubricItem` model).
- [ ] **Step 7 routing table:** `GraderRouting.chainFor(subject, examLanguage, archetypeClass)` — data table, not branching logic. **Explicit row (R-6-Q8): `(SO, bash|posix-c, code)` → `[RUBRIC, LLM_JUDGE]` — rubric IS the first leg; there is NO bash runner; no WSL.** Default code rows → `[EXECUTION, RUBRIC, LLM_JUDGE]`; numeric rows → `[NUMERIC_ORACLE, LLM_JUDGE]`; proof/prose rows → `[RUBRIC, LLM_JUDGE]`.
- [ ] **Step 8 INV-6.1 (pending-not-green):** `GraderRoutingTotalityTest` — loads the seeded problem bank (Task 2 `ProblemSeed` into a scratch DB); for EVERY (subject × archetype) row present: resolve the chain, assert first element non-LLM (rubric counts as a valid first leg for SO code archetypes). PLUS the pending honesty: the test declares the EXPECTED pending set — BOTH subjects with zero seeded problems AND surface archetype-classes (proof/trace/code) with zero seeded problems, from Task 2's outcome — and asserts `countBySubject` + the per-archetype-class counts match it exactly — a subject or archetype-class gaining its first problem flips the expectation LOUDLY (the fix-claim-discipline shape of "pending-not-green": named, asserted, impossible to forget).
- [ ] **Step 9 INV-6.3:** `LlmNeverAloneTest` — iterates every chain the routing table can produce (all table rows) + the builder-throw cases; asserts none is LLM-only. (Type-level + table-level double cover.)
- [ ] **Step 10:** full suite green; commit: `feat(plan6): grader chain + numeric oracle + rubric leg + routing table; INV-6.1 pending + INV-6.3 (E16)`.

**Acceptance:** all five new test classes green; INV-6.1 prints/asserts the named pending set; `:check` green.

---

## Task 4 — Numeric-ATTEMPT tolerance fold → single source (carried follow-up #1) [lock §NEW-L check]

Folds every numeric comparison onto `NumericOracleGrader.matches` and replaces the lesson ATTEMPT placeholder with a real numeric grade path, exactly as the in-code carried follow-up demands (`QueueMasteryCalibrationRoutes.kt:647-649`). Lane B exclusively owns `QueueMasteryCalibrationRoutes.kt` + `LessonBeatGradeRouteTest.kt` (rulings lane table).

**Files:**
- Modify: `src/main/kotlin/jarvis/content/KcBeats.kt` (additive: `BeatAttempt.numeric_answer: String? = null`, `numeric_tolerance: Double? = null` — mirrors `BeatCheck:125-126`), `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (ATTEMPT branch + `ApiBeatAttempt` DTO additive fields; CHECK branch delegates to the single source), `src/main/kotlin/jarvis/tutor/GradeScoring.kt` (`answerMatches` delegates to `NumericOracleGrader.matches`), `src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt`

- [ ] **Step 1 — Signatures-lock check (REQUIRED):** read lock §NEW-L. **The containment chain makes this an ADDITIVE AMENDMENT to a FROZEN reply, not a non-frozen edit:** `ApiBeatAttempt` (`QueueMasteryCalibrationRoutes.kt:187`) is a member of `ApiLessonBeats` (`:172-176`), which is the `beats` field (`:160`) of `ApiLessonReply` — frozen VERBATIM in lock §NEW-L (`:595-628`; `:616` pins `beats` as the 12th field, "carries the BeatSelector-chosen plan + the beat content"). Adding `numeric_answer`/`numeric_tolerance` to `ApiBeatAttempt` therefore additively changes the frozen `GET /api/v1/lesson/{kcId}` wire. A grep for `ApiBeatAttempt|numeric_answer` finding no DIRECT pin is NOT sufficient clearance — the containment chain governs. Additive-nullable IS the sanctioned style (the lock §O AMENDMENT precedent: `source: SourceRef?` + `provenance`), and the §NEW-L DURABLE RULE demands the lock amendment ride the same commit/merge — so this step RECORDS the explicit §NEW-L additive-amendment entry (the two optional fields, null defaults, legacy payloads decode unchanged) for `signatures-lock-plan6.patch` (Task 14 Step 2 packages it; the PM applies it in the merge commit — same-merge, per this plan's lock-amendment note). **If anything beyond the additive-nullable two-field amendment would be needed (rename/retype/required field): BLOCKED → PM.**
- [ ] **Step 2 (RED):** `LessonBeatGradeRouteTest` new cases pinning: (a) numeric ATTEMPT with `numeric_answer="3.14"`, `numeric_tolerance=0.01`, learner `free_input="3.141"` → correct; (b) outside tolerance → incorrect with the existing RO feedback; (c) legacy beat with NO `numeric_answer` → the old placeholder behavior preserved byte-for-byte (exact-string vs trace-final — the honest legacy fallback, still dead on served choice KCs); (d) CHECK branch behavior unchanged (existing tolerance semantics `tol ?: 0.0` — regression pin).
- [ ] **Step 3 (GREEN):** implement: ATTEMPT no-choices branch prefers `numeric_answer` when present → `NumericOracleGrader.matches(expected, got, tol ?: 0.0)`; CHECK branch + `GradeScoring.answerMatches` rewired to delegate to the same `matches` (per-site defaults preserved per §0.9-B). Delete the resolved CARRIED FOLLOW-UP comment block, replacing it with a one-line pointer to this plan.
- [ ] **Step 4:** full suite green (this touches the lesson grade route — run the WHOLE backend suite, the lesson tests are the blast radius); commit: `feat(plan6): numeric-ATTEMPT tolerance via single oracle source + route pin tests (carried follow-up)`.

**Acceptance:** the 4 pin cases green; grep shows exactly ONE numeric-tolerance comparator implementation in the codebase (`NumericOracleGrader.matches`); full suite green.

---

## Task 5 — Execution grader: subprocess sandbox + runners + INV-6.2 + execution goldens (R-6-Q1; REQ-24/25, E17)

The sandbox per §0.9-C verbatim: fresh temp dir, hard timeout, bounded capture, honest no-network limitation note, boot-time toolchain self-detection with honest leg-disable. Runners: R / Python / C++ (g++/MinGW) / Alk (Task 0 jar). bash/POSIX deliberately ABSENT (Task 3 routing covers it). TDD; STRICT on PC+CI.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/grader/ExecutionGrader.kt`, `src/main/kotlin/jarvis/tutor/grader/LanguageRunners.kt`, `src/test/kotlin/jarvis/tutor/grader/ExecutionGraderSandboxTest.kt`, `src/test/resources/fixtures/grader-golden/{PA,ALO,PS,POO}/execution-grader/*.json`
- Modify: `src/test/resources/fixtures/grader-golden/_README.md` (document the execution shape; un-reserve the dir)

- [ ] **Step 1 (RED) sandbox laws:** tests for — temp dir created fresh and DELETED even on timeout/crash; wall-clock timeout kills the process tree (a sleep-forever program → `timed_out=true` within the limit); output bounded at 64 KiB with truncation marker; compile error → honest `compiled=false` + stderr excerpt; the KDoc carries the verbatim no-network limitation sentence (grep-asserted — the documented-limitation requirement is itself machine-checked).
- [ ] **Step 2 (GREEN):** `ExecutionGrader` + `LanguageRunners` per §0.9-C. `ToolchainProbe` resolves binaries once per process (R: `Rscript`, Python: `python`→`python3`, C++: `g++`, Alk: `java` + the Task-0 jar path — env override `JARVIS_ALK_JAR` for deploy); a missing toolchain → `RunnerAvailability(available=false, reason)` and the chain leg DEFERS with a recorded degradation (never throws, never silently passes).
- [ ] **Step 3 — INV-6.2 fixtures from REAL corpus problems:** for each language, a known-good reference solution → pass AND a known-bad mutant → fail, derived from the Task-2 seeded problems: Alk ← the PA problem (exam language `alk`), R ← the PS problem (`r`), Python ← the same PS computation expressed in Python, C++ ← the ALO numeric problem expressed in C++ (POO has no corpus problem yet). Each fixture carries an honest `corpus_note` naming its source problem (the §0.9-E shape) — where the language≠subject-exam-language, the note says so explicitly (the runner is under test, not the routing; routing truth is Task 3's).
- [ ] **Step 4 (RED→GREEN):** `ExecutionGraderSandboxTest` — parameterized over the execution-grader fixtures; known-good → pass, mutant → fail, per R/Python/C++/Alk (INV-6.2 verbatim). STRICT: missing toolchain = RED on PC/CI; `JARVIS_EXEC_GRADER_OPTIONAL=1` downgrades to skip-with-log (VPS only — never set in CI). **Seeded-red proof:** flip one fixture's `expected.pass`, run, confirm RED, revert (fix-claim discipline, the 4a Task-5 pattern).
- [ ] **Step 5:** full suite green; commit: `feat(plan6): execution grader — subprocess sandbox, 4 runners, INV-6.2 vs real corpus problems (E17, R-6-Q1)`.

**Acceptance:** INV-6.2 green for all 4 languages on the PC (R contingent on Task 0 Step 3's resolution); seeded-red proven + reverted; temp-dir hygiene + timeout tests green; full suite green.

---

## Task 6 — `RetryingLlm` decorator (carried follow-up #2) + recorded-replay LLM-judge goldens (R-6-Q5; INV-6.4)

Two deliverables: (1) the bounded retry/backoff decorator closing the relay false-negative class for `RelayLlm` + `FreeLlmApiLlm` — wrapped at their REAL seams per §0 #8 (`FreeLlmApiLlm` in `productionGraderResolver`; `RelayLlm` at `drillCriticLlmFactory` — `RelayLlm` is NOT a resolver member, the resolver constructs `OpenRouterChatLlm | ClaudeMaxLlm | FreeLlmApiLlm`); (2) the LLM-judge golden leg — recorded raw LLM outputs replayed through the REAL `parseGradeJson` + `GradeScoring` (deterministic, zero live LLM calls in CI, no-paid-APIs compliant — RATIFIED R-6-Q5). TDD.

**Files:**
- Create: `src/main/kotlin/jarvis/RetryingLlm.kt`, `src/test/kotlin/jarvis/RetryingLlmTest.kt`, `src/test/resources/fixtures/grader-golden/{PA,ALO,PS}/llm-judge/*.json` (≥2 per subject: 1 clean parse + 1 adversarial — fenced/preamble-wrapped/missing-field)
- Modify: `src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt` (add the llm-judge leg + per-leg discovery guards), `src/test/resources/fixtures/grader-golden/_README.md`, `src/main/kotlin/jarvis/web/TutorRoutes.kt` (TWO surgical edits per §0 #8: `productionGraderResolver` wraps `FreeLlmApiLlm`, `drillCriticLlmFactory` wraps `RelayLlm` — bundled here to keep Task 7's diff clean)

- [ ] **Step 1 (RED):** `RetryingLlmTest` with a scripted fake `Llm` — transport failure then success → one retry, success returned; persistent failure → exhausts `maxRetries` then rethrows (bounded — INV-9.2-style boundedness assert: total attempts == maxRetries+1); malformed-but-successful completion → NO retry (returned as-is); backoff invoked between attempts (injectable clock/sleeper — no real sleeps in tests); `close()` delegates.
- [ ] **Step 2 (GREEN):** §0.9-J implementation; TWO surgical `TutorRoutes.kt` edits: `productionGraderResolver` (`:189-194`) wraps its `FreeLlmApiLlm()` member in `RetryingLlm`, and `drillCriticLlmFactory` (`:198`) wraps its `RelayLlm()`. OpenRouter keeps its own 429 handling; `ClaudeMaxLlm` (CLI subprocess) stays unwrapped — outside the two named clients of the carried follow-up.
- [ ] **Step 3 — record the goldens (LOCAL, once, not in CI):** run the real grader prompts (Task 1's subject-parameterized prompts) against the relay (claude-max) or OpenRouter `:free` for the seeded problems; capture the RAW model text verbatim into the fixture `input.raw_llm_output`. Compute `expected` by running the REAL `parseGradeJson` + `GradeScoring.scoreFromRubric/correctFromRubric/isConfident` over each recorded output ONCE and freezing the result (the golden pins parser+scorer behavior, not model behavior). Include the PA flipped-reduction case: a recorded output whose `misconception` is `REDUCTION_DIRECTION_FLIPPED`.
- [ ] **Step 4 (RED→GREEN):** extend `GraderGoldenHarnessTest` with the `llm-judge` leg (filter `parentFile.name == "llm-judge"`; replay through `parseGradeJson` + scoring; compare with 1e-9 epsilon; assert `confident` against `isConfident`). Per-leg discovery guards: `grade-scoring >= 12` (existing), `llm-judge >= 6`. **Seeded-red proof** on one llm-judge golden (flip `expected.correct`, RED, revert).
- [ ] **Step 5:** INV-6.4 statement check: both golden legs (+ Task 5's sandbox suite) run inside `:check`, which CI runs on every merge → "goldens green per grader per subject before any grader change merges" is structurally satisfied. Note it in the README.
- [ ] **Step 6:** full suite green; commit: `feat(plan6): RetryingLlm (relay/freellmapi) + recorded-replay llm-judge goldens (R-6-Q5, INV-6.4)`.

**Acceptance:** retry boundedness proven; ≥6 llm-judge goldens green + seeded-red proven; zero network calls in the harness (no `Llm` instance constructed in the golden path); full suite green.

---

## Task 7 — `/drill/grade` chain integration + additive reply fields + TS mirror [lock §O check]

The surgical `TutorRoutes.kt` edit: the `/drill/grade` handler resolves the chain via `GraderRouting` (using the server-resolved problem's subject/examLanguage/archetype where available) and serves the §0.9-F additive fields. **The no-bank-problem fallback chains are EXPLICIT (the dominant live traffic is task_prep drills whose rubric items ride the request — `drillGrader.ts:82/:108` — with NO problem-bank row):** (a) drill WITH rubric items (bank-resolved OR request/task_prep-derived) → `[RUBRIC, LLM_JUDGE]`, the rubric leg consuming the request-derived items through Task 3's two-source `RubricGrader` (structural matchers — machine-checkable items are decided structurally, never by LLM-emitted booleans); (b) prose drill with NO rubric items and no bank problem → STILL `[RUBRIC, LLM_JUDGE]` — the rubric leg finds zero machine-checkable items, DEFERS, and the deferral is recorded in `degradedLegs` so the reply honestly says the LLM decided (`decided_by="llm-judge"` + the degraded RO copy). The builder NEVER sees `[LLM_JUDGE]` alone — INV-6.3 holds non-vacuously on real traffic, not just over the seeded bank. The existing UNGRADED degraded path and every frozen reply field survive byte-identical.

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (the `/drill/grade` handler + `ApiDrillGradeReply` additive fields), `src/main/kotlin/jarvis/tutor/GradeTeachingPayload.kt` (house the shared `ItemVerdict` wire type), `tutor-web/src/lib/drillGrader.ts` (mirror additively)
- Create: route-level tests appended to the existing drill-grade test class(es) (locate via `grep -rl "drill/grade" src/test/kotlin` — extend in place, do not fork a parallel suite)

- [ ] **Step 1 — Signatures-lock check (REQUIRED):** read lock §O verbatim. Assert after the diff: `misconception: String?` (scalar) untouched; `misconception_payload`/`ladder_rungs`/`self_explanation_prompt` untouched; new fields are additive with defaults (`decided_by: String? = null`, `degraded_legs_ro: List<String> = emptyList()`, `item_verdicts: List<ItemVerdict> = emptyList()`); snake_case wire names. **Any forced rename/retype → BLOCKED → PM.** The additive fields are recorded in `signatures-lock-plan6.patch` (Task 14 packages it).
- [ ] **Step 2 (RED):** route tests — (a) a numeric problem grades through the oracle leg: reply `decided_by="numeric-oracle"`, NO LLM constructed (assert via a throwing fake resolver — proves the oracle path never touches the LLM); (b) LLM-failure on a prose problem → the existing UNGRADED reply still produced (regression pin); (c) reply JSON contains the three additive fields with correct shapes; (d) a chain that degraded (execution unavailable via a disabled-toolchain fake) serves non-empty `degraded_legs_ro` whose copy is RO (diacritic-bearing string asserted — the R-6-Q1 "grade reply says which leg decided" requirement); (e) **the dominant live shape: a task_prep drill whose `rubricItems` ride the REQUEST (no problem-bank row) — machine-checkable items grade through the structural rubric leg (`decided_by != "llm-judge"` when the rubric leg decides), and where NO item is machine-checkable the reply is the honest degraded shape naming the limitation (rubric deferral in `degraded_legs_ro`, `decided_by="llm-judge"`) — the anti-vacuity pin proving REQ-26/INV-6.3 on real traffic.**
- [ ] **Step 3 (GREEN):** implement; the LLM leg consumes `productionGraderResolver` UNCHANGED (already retry-wrapped by Task 6).
- [ ] **Step 4:** `drillGrader.ts` mirrors the additive fields (optional, snake_case 1:1 — the existing convention); its type test (`drillGrader.types.test.ts`) extended.
- [ ] **Step 5:** full suite green (backend + frontend); commit: `feat(plan6): /drill/grade chain integration — oracle/execution/rubric before LLM; additive decided_by/degraded/item_verdicts (lock §O additive)`.

**Acceptance:** the five route tests green (incl. the (e) request-rubric anti-vacuity pin); lock-check step documented in the task report with the grep evidence; full suite green.

---

## Task 8 — `PracticeRoutes.kt` + `practiceApi.ts` [lock §13/§N check]

The five practice endpoint groups per §0.9-G, with the attempt gate SERVER-enforced: the problems list payload never contains a reference solution; only `code/{id}/grade` replies carry it. Keeps `TutorRoutes.kt` clean — registration is one line in `WebMain.kt` (Lane B owns it per the lane table).

**Files:**
- Create: `src/main/kotlin/jarvis/web/PracticeRoutes.kt`, `src/test/kotlin/jarvis/web/PracticeRoutesTest.kt`, `tutor-web/src/lib/practiceApi.ts`
- Modify: `src/main/kotlin/jarvis/web/WebMain.kt` (register `installPracticeRoutes()` alongside the existing installs)

- [ ] **Step 1 — Signatures-lock check (REQUIRED):** all five endpoint groups are NEW additive routes; grep the lock for `practice` — zero frozen contracts touched (lock §13: additive endpoints are the sanctioned extension path; §N quick-index untouched). New wire shapes are frozen in §0.9-G and ride `signatures-lock-plan6.patch`. **Deviation → BLOCKED → PM.**
- [ ] **Step 2 (RED):** `PracticeRoutesTest` (ktor test-host over a scratch DB seeded by `ProblemSeed`): 401 without session on every endpoint; problems list returns the seeded problems for `subject=PA` and **its JSON string contains NO solution text** (assert the serialized body does not contain the seeded solution's distinctive substring — the INV-6.6 server half); proof grade returns per-substep `item_verdicts`; trace step grade catches a wrong step-3 value at step 3 (REQ-5 pin); code run returns bounded outputs; code GRADE reply carries `reference_solution_ro`; deliverables endpoint returns the honest empty list (test written here against an empty set; re-asserted with seeds in Task 10).
- [ ] **Step 3 (GREEN):** implement over `ProblemsRepo` + `GraderRouting`/chain legs; grades on practice surfaces reuse the chain; RO feedback strings server-side. **The deliverables handler ships HERE as the honest-empty STUB only — the `TasksTable.deliverable_json` column does not exist until Task 10 (`Tasks.kt` is NOT in this task's Files list; do not reference the column in this task). Task 10 rewires the handler to read `deliverable_json` — `PracticeRoutes.kt` is in Task 10's Modify list for exactly that edit.**
- [ ] **Step 4:** `practiceApi.ts` — typed client wrapper mirroring §0.9-G snake_case (the `drillGrader.ts` pattern: one fetch site, typed replies).
- [ ] **Step 5:** full suite green; commit: `feat(plan6): PracticeRoutes (proof/trace/code/deliverables) — server-enforced attempt gate + practiceApi client`.

**Acceptance:** route tests green incl. the no-solution-leak assert; `WebMain.kt` diff is the one-line registration; full suite green.

---

## Task 9 — Surfaces I: `practiceStrings.ts` + `ProofDrill` + `StepTraceDrill` + mounts (REQ-2..6)

The first two practice surfaces, with ALL learner-visible copy in the new RO strings module (the Lane-B strings file per the rulings lane table — Lane A's `chromeStrings.ts` is a SEPARATE file; INV-8.3's grep accepts both; no shared strings file). Each component task includes its MOUNT (the ghost-component lesson: a built component is not shipped until it paints on a route).

**Files:**
- Create: `tutor-web/src/lib/practiceStrings.ts`, `tutor-web/src/components/practice/ProofDrill.tsx` + `.test.tsx`, `tutor-web/src/components/practice/StepTraceDrill.tsx` + `.test.tsx`
- Modify: `tutor-web/src/main.tsx` (mount the practice routes — follow the existing mount pattern Plan 3 used for `LessonScreen`/`OggiScreen`; delta-recon the pattern in-file before editing)

- [ ] **Step 1:** `practiceStrings.ts` — exported const map of ALL RO strings for both surfaces (labels, button copy, verdict copy, empty states). Identifiers EN, values RO.
- [ ] **Step 2 (tests-with):** `ProofDrill` — renders the fill-in frame from a problem's proof template (`proof-drill-frame`), the named sub-step list (`proof-drill-substeps`), submits via `practiceApi`, renders per-sub-step results (`proof-drill-substep-score`) — REQ-2/3/4 selectors exactly as spec'd. Component test: all three testids present; per-substep verdicts render from a mocked reply; no EN learner copy (assert strings come from `practiceStrings`).
- [ ] **Step 3 (tests-with):** `StepTraceDrill` — dominant skeleton table (`trace-drill-skeleton`), current-step input (`trace-drill-step-input`), per-step verdict (`trace-drill-step-verdict`); a wrong step blocks advance with RO feedback (REQ-5/6).
- [ ] **Step 4 — MOUNT:** add the practice routes/entries in `main.tsx` wiring both components to real `practiceApi` data (exact JSX in the diff; the reviewer asserts each new component name appears in `main.tsx`'s import + element tree — the plan self-review rule).
- [ ] **Step 5:** full frontend suite green; commit: `feat(plan6): ProofDrill + StepTraceDrill surfaces, practiceStrings (RO), mounted (REQ-2..6)`.

**Acceptance:** component tests green; testids match the spec strings byte-for-byte; mounts present in `main.tsx`; `npm test` green.

---

## Task 10 — Surfaces II: `CodePractice` + `DeliverableTracker` + deliverable seeds (REQ-7..10, 18..21; R-6-Q9/Q10)

Code practice with the PLAIN TEXTAREA (R-6-Q9 — DEFERRED editor; the spec selectors are satisfied by `code-practice-editor` on the textarea + the visible `code-practice-language-badge`), and the deliverable tracker over honest verified data (R-6-Q10).

**Files:**
- Create: `tutor-web/src/components/practice/CodePractice.tsx` + `.test.tsx`, `tutor-web/src/components/practice/DeliverableTracker.tsx` + `.test.tsx`
- Modify: `tutor-web/src/main.tsx` (mounts), `src/main/kotlin/jarvis/tutor/Tasks.kt` (additive `deliverable_json` nullable column + DTO field per §0.9-I), `src/main/kotlin/jarvis/web/PracticeRoutes.kt` (rewire the deliverables handler from the Task-8 honest-empty stub to actually read `deliverable_json`), `src/main/kotlin/jarvis/tutor/TaskPrep.kt` (only if prep-drill links need a slot beyond `deliverable_json` — prefer NOT touching it; if untouched, say so in the task report), `src/main/kotlin/jarvis/tutor/ProblemSeed.kt` (deliverable seeds), `src/test/kotlin/jarvis/web/PracticeRoutesTest.kt` (deliverables re-assert with seeds)
- Modify: `tutor-web/src/lib/practiceStrings.ts` (the surfaces' RO copy)

- [ ] **Step 1 (tests-with):** `CodePractice` — `<textarea data-testid="code-practice-editor">`, `code-practice-language-badge` rendering the problem's exam-language constraint VISIBLY (REQ-7: Alk for PA, R for PS, C++ for POO, bash/POSIX for SO), `code-practice-run` (→ `/run`), `code-practice-verdict`, and `code-practice-reference` rendered ONLY from the grade reply (component never fetches/holds the reference pre-attempt — INV-6.6 client half; the server half is Task 8). RO copy incl. the degraded-leg banner (`degraded_legs_ro` passthrough).
- [ ] **Step 2 — deliverable data (R-6-Q10):** additive `deliverable_json` on `TasksTable` (§0.9-I; column auto-created — `Migration.kt` untouched, assert via a schema test that boots a scratch DB and finds the column). Seeds: ALO Teme T1–T5 (points from the verified-sweep grade-model registry rows; `source_doc` = the sweep doc id + quote), PS Teme A–D (live-page contract bucket). **POO: seed-if-found per Task 2 Step 3's execution-time exhaustive POO-lab-spec search (NOT a plan-write assumption — R-6-Q10 names "POO lab specs etc." as deliverable sources): a located lab spec → a deliverable entry with provenance (extract committed under `content/POO/_sources/`, quote anchor machine-verified, REQ-20); genuinely absent after that search → honest empty state, the absence NAMED in the task report as an execution-time finding.** NEVER fabricate deadlines: absent → null → UI "necunoscut".
- [ ] **Step 3 (tests-with):** `DeliverableTracker` — `deliverable-card` / `deliverable-points` (per-sub-problem) / `deliverable-prep-drills` (linked drills; honest empty state copy when none) / `deliverable-deadline` (REQ-21); the REQ-19 honesty line rendered verbatim in RO ("Aplicația te pregătește pentru predare — nu notează predările reale; profesorul o face."), asserted in the component test.
- [ ] **Step 4 — MOUNT** both in `main.tsx`; rewire the deliverables handler in `PracticeRoutes.kt` to read `deliverable_json` (replacing the Task-8 honest-empty stub); re-assert the deliverables endpoint test with seeds.
- [ ] **Step 5:** full suites green; commit: `feat(plan6): CodePractice (textarea, R-6-Q9) + DeliverableTracker + verified deliverable seeds (R-6-Q10)`.

**Acceptance:** spec selectors exact; reference absent from component state pre-attempt; deliverable seeds trace to verified sources; both mounted; suites green.

---

## Task 11 — Mock-exam ADDITIVE semantics (REQ-11..17; R-6-Q7) [lock check + regression pin]

Lands §6.2.4 ADDITIVELY on the EXISTING `MockExamRoutes.kt` + `MockExamShell.tsx` (live since before this plan — they must not break). Step 1 is the execution-time delta-recon the ruling demands.

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/MockExamTable.kt` (5 additive nullable columns per §0.9-H), `src/main/kotlin/jarvis/web/MockExamRoutes.kt` (additive request/reply fields + `POST /{id}/phase`), `tutor-web/src/components/MockExamShell.tsx` + `.test.tsx` (timer/phase/ordering/rubric-result/synthetic-tag UI)
- Create: `src/main/resources/practice/mock-exam-formats.json`, `src/test/kotlin/jarvis/web/MockExamAdditiveRouteTest.kt`

- [ ] **Step 1 — DELTA-RECON (R-6-Q7, REQUIRED FIRST):** read the live `MockExamRoutes.kt` DTOs (`:84-141` at plan-write) + `MockExamShell.tsx` props/fetch contract + `MockExamsTable` columns. Compare against §0 #5 and §0.9-H. **Also pin the in-code NON-NEGOTIABLE freeze at `MockExamRoutes.kt:37`: mock-exam is SYNC, 200-ONLY — submit ALWAYS returns 200, NO 202, NO poll/status route, NO `mock_exam_jobs` table; open questions DEGRADE to UNCERTAIN rather than going async. Every additive endpoint/field in Steps 4–5 must preserve it: the new `POST /{id}/phase` is also sync-200, and the rubric-leg grading inside submit stays synchronous.** Any mismatch with this plan's assumptions (a DTO renamed, an endpoint moved, the shell fetching a different shape) → BLOCKED → PM with the diff. Do not adapt silently.
- [ ] **Step 2 — regression pin (RED-proof first):** `MockExamAdditiveRouteTest` opens with LEGACY-shape pins: start/submit/result called with pre-plan payloads return pre-plan-shaped replies (no new required field, no behavior change). These pins are written BEFORE the additive edits and must stay green through the task (live-endpoints-must-not-break, R-6-Q7).
- [ ] **Step 3 — Signatures-lock check (REQUIRED):** grep the lock for `mock-exam|MockExam` — **TWO pins EXIST (verified at plan-fix; finding them is EXPECTED, not a surprise and not a BLOCK):** (1) lock `:236` wire-consistency — `mock-exam kc_results` is one of the ONE-enum `verification_status` serialization sites: this task does NOT touch `kc_results` serialization (additive columns/fields only) — assert that pin survives byte-identical; (2) lock `:237` **P2-5** (council fix 2026-06-08) — the question `kind:"deterministic"|"open"` field is FROZEN as a wire shape, the runtime only ever emits `"open"`, and the documented unlock for deterministic grading is an authored `KnowledgeConcept.canonical_answer` scored via `GradeScoring.answerMatches` — never via `invariant` (canonical code: `MockExamRoutes.toQuestion`). **Reconciliation (why Step 4 does NOT re-violate the P2-5 wrong-oracle rule):** the rubric-leg grading applies ONLY to G-item rubric-scored items sourced from BANK problems with AUTHORED rubrics (`format_json`-driven, Task-2 seeds) — never to KC-invariant-derived questions; the `kind` wire field stays untouched (KC questions still emit only `"open"`); `kc_results`/`verification_status` serialization is unchanged. This reconciliation note rides `signatures-lock-plan6.patch` (Task 14 Step 2 records the P2-5 interaction). Any OTHER lock hit, or a forced change to either named pin → BLOCKED → PM.
- [ ] **Step 4 (RED→GREEN) additive semantics:** formats data file per §0.9-H (REQ-13/14 structures verbatim — PA brackets + 0.5 partial; POO MCQ + execution-trace reveal; SO 18-item grid w/ R-MULTISELECT "bifați TOATE" items **(spec 6.2.4/REQ-13 says R-MULTISELECT lives here AND in drills — this plan lands ONLY the mock-exam grid half; the drill-side multiselect variant is NOT built here and is a NAMED re-carry in Task 14's manifest, never a silent drop)**; SO TP / POO T1/T2 rubric SELF-assessment with all-or-nothing + score-halving and the honest "the app cannot auto-grade these" RO copy; PS 15-min mini-test, erratum E3 honored — no gap-record; ALO docs-allowed phase). Routes: start accepts optional `format_id` → stamps `format_json`/`started_at`/`synthetic_tag`; `POST /{id}/phase` advances `phase_index` (REQ-15); submit grades rubric-scored items through the chain's rubric leg → `rubric_results_json`; result reply ADDS `timer`, `phase`, `rubric_result` (per-G-item `ItemVerdict`s), `common_errors_ro` (taxonomy codes fired — REQ-16), `synthetic_tag`. Items built from bank problems where they exist; everything not from a real past paper is synthetic-tagged (REQ-12 honesty; the seeds are lecture/seminar-derived → synthetic-tagged in mock-exam use).
- [ ] **Step 5 — UI additive:** `MockExamShell` renders `mock-exam-timer`, `mock-exam-phase`, `mock-exam-question` (sub-question ordering visible — b-uses-a ordering from `format_json`), `mock-exam-rubric-result` (per-G-item post-submit), `mock-exam-synthetic-tag` ("Subiect generat — nu provine dintr-un examen real") — REQ-17 selectors exact; legacy mock-exam flow renders unchanged when the new fields are absent (component test pins both modes).
- [ ] **Step 6:** full suites green; commit: `feat(plan6): mock-exam additive — timer/phases/G-item rubric/synthetic tag + formats data; legacy contract pinned (R-6-Q7)`.

**Acceptance:** legacy pins green BEFORE and AFTER the additive diff; REQ-17 selectors exact; formats file encodes all five REQ-13/14 structures; suites green.

---

## Task 12 — DrillStack CHECK card → real graded input (REQ-29, E8) + chrome strings → `practiceStrings.ts`

Closes audit E8: the read-only CHECK card (`DrillStack.tsx:375-391`) becomes a graded input wired into the chain via the existing `/drill/grade` (now chain-backed by Task 7). Also moves DrillStack's own chrome strings into `practiceStrings.ts` (rulings lane table: DrillStack does its OWN string move; Lane A's RO sweep EXCLUDES DrillStack).

**Files:**
- Modify: `tutor-web/src/components/DrillStack.tsx`, the affected `DrillStack.*.test.tsx` siblings, `tutor-web/src/lib/practiceStrings.ts`, `tutor-web/e2e/phase5-core-loop.spec.ts` (the check-done flow assertions)

- [ ] **Step 1 (RED):** DrillStack tests — the CHECK card renders a `check-attempt-input` textarea + `check-submit-btn`; "MARK CHECK DONE" no longer exists as a no-engagement path; `phase → check-done` ONLY after a graded attempt (mocked `/drill/grade` reply) or an explicit give-up (`check-giveup-btn`, honest); the graded verdict renders (`check-verdict`).
- [ ] **Step 2 (GREEN):** implement — submit posts the check item through the existing `gradeDrill()` client (the CHECK stem as `problemStatement`); RO copy from `practiceStrings.ts`; all pre-existing DrillStack chrome strings (card titles, button copy currently inline) moved to `practiceStrings.ts` (INV-8.3 grep accepts this file).
- [ ] **Step 3:** delta-recon `phase5-core-loop.spec.ts`'s ACTUAL interactions (per §0 #7, verified at plan-fix: the spec has NO check-done flow — only the `check: "Trace fib(4)"` fixture string at `:49` and a `drill-check-btn` click at `:101`, the drill ANSWER-grade button, a different control). Update ONLY what the CHECK-card change actually breaks: if the spec's flow now reaches a CHECK card that requires a graded attempt before phase advance, drive it through `check-attempt-input`/`check-submit-btn` with the spec's existing `page.route` stub pattern; if nothing breaks, record that as the finding — do not invent edits. **Then RUN the spec in THIS task** (it is stub-based against the Vite webServer — no backend needed): `npx playwright test e2e/phase5-core-loop.spec.ts` from the worktree's `tutor-web` → green before commit. Then the affected sibling tests, then the FULL frontend suite.
- [ ] **Step 4:** commit: `feat(plan6): DrillStack CHECK card = real graded input via grader chain; chrome strings → practiceStrings (E8/REQ-29)`.

**Acceptance:** no-engagement completion is impossible (test-proven); old `MARK CHECK DONE` string gone from the component (lives only in git history); full `npm test` green AND `phase5-core-loop.spec.ts` RUN green in THIS task (it is also re-run in the Task 13 Step 3 and Task 14 Step 1 gates — the edited spec never ships unexecuted).

---

## Task 13 — Practice e2e: INV-6.5 + INV-6.6 + interaction smoke, against the seeded real-corpus server + CI patch

Playwright suites for all five surfaces against a REAL booted backend over a scratch DB seeded with the Task-2 real-corpus problems (INV-6.5 "against real corpus content"). Self-contained server bootstrap inside the spec helpers — NO `playwright.config.ts` edit (shared file).

**Files:**
- Create: `tutor-web/e2e/practice/helpers/practiceServer.ts` (spawns the backend with `JARVIS_TUTOR_DB=<scratch seeded db>` + the locally-built frontend served by Ktor; waits healthz; kills in teardown), `tutor-web/e2e/practice/helpers/smoke.ts` (the §6.2 interaction-smoke contract: collect 4xx/5xx via `page.on('response')`, error-text regex `/404|HTTP \d{3}|not found|eroare|error/i` assert), `tutor-web/e2e/practice/{proof-drill,step-trace,code-practice,mock-exam,deliverable-tracker}.spec.ts`, `build-review/tmp/lane-b-plan6-patches/test-yml-practice.patch`

- [ ] **Step 1:** `practiceServer.ts` — builds the worktree frontend (`npm run build` → Ktor-served `tutor-dist`; **the rebuilt `tutor-dist` artifacts are NEVER committed by this lane** — PM rebuilds at merge; commits in this task use explicit spec/helper paths only), boots the jar/Gradle-run backend on a free port with the scratch DB pre-seeded via `ProblemSeed` (a tiny Kotlin main or the test-host seeding script — executor picks the lightest mechanism that already exists; if none exists, a `--seed-practice` dev flag on the server main is in-scope for `WebMain.kt`). **ENV-GATE (the Plan-4b env-skip precedent — REQUIRED):** every `e2e/practice/*.spec.ts` opens with `test.skip(process.env.JARVIS_PRACTICE_E2E !== "1", "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1")` — the EXISTING node-only CI frontend job (`npm run e2e` = `playwright test` over the same `testDir: "./e2e"`, no JDK/Gradle) WILL collect these specs; the gate makes them self-skip there (named, visible in the report), never boot-and-redden merged main. The env is set ONLY in the new CI `practice-e2e` job (Step 4) and the local Task 13/14 commands. **baseURL strategy:** practice specs navigate ABSOLUTE URLs to the helper's Ktor port (`http://127.0.0.1:<port>/…`), never the config `baseURL`/Vite `:5173`; the shared config's `webServer` may still boot Vite during a practice run — tolerated and unused (no `playwright.config.ts` edit).
- [ ] **Step 2:** the five specs — each asserts its spec-section testids on first paint (REQ-4/6/10/17/21 selector lists verbatim), the interaction-smoke contract on paint + every listed click. **Pending honesty:** a surface whose archetype-class is in Task 2's NAMED pending set (zero seeded real-corpus problems for proof/trace/code respectively) asserts the honest empty-state render + smoke INSTEAD of the full grade flow, with the pending NAMED in the spec title and the task report — never a fabricated problem, never silently green (with Task 2's per-archetype-class seeding, PA in-repo material is expected to cover proof/trace/code; pending here means the material genuinely does not exist on disk). And:
  - `code-practice.spec.ts` — **INV-6.6:** `[data-testid="code-practice-reference"]` ABSENT from the DOM pre-attempt; submit a graded attempt; PRESENT post-attempt. Plus the network half: the problems-list response body contains no reference text.
  - `step-trace.spec.ts` — wrong value at step 3 → verdict at step 3 (REQ-5 live).
  - `mock-exam.spec.ts` — timer ticks, phase advances via the phase control, per-G-item result renders post-submit, synthetic tag visible (REQ-17).
  - `proof-drill.spec.ts` / `deliverable-tracker.spec.ts` — selectors + smoke (+ the REQ-19 honesty line visible).
- [ ] **Step 3:** run the suite locally with the gate env set (PowerShell: `$env:JARVIS_PRACTICE_E2E='1'`): `npx playwright test e2e/practice e2e/phase5-core-loop.spec.ts --config playwright.config.ts` from the worktree's `tutor-web` — all five practice specs green AND the Task-12-modified `phase5-core-loop.spec.ts` green (the lane never ships an unexecuted e2e gate; phase5 is stub-based and ungated by the env).
- [ ] **Step 4:** CI patch `test-yml-practice.patch` (PM applies at merge — Lane B NEVER edits `test.yml`): adds (a) R toolchain (`apt-get install -y r-base-core`) + the Alk fetch (`node tools/fetch-alk.mjs`) to the backend test job so INV-6.2 is green on ubuntu, (b) a `practice-e2e` job (WITH JDK/Gradle setup — it boots the real backend) running the five specs via the same self-contained server helper, **with `JARVIS_PRACTICE_E2E=1` set in that job's env — the ONLY place CI sets it.** (c) The EXISTING node-only `Playwright e2e` step is NOT edited by the patch — the Step-1 env-gate makes the five practice specs self-skip there (named skips), so the existing job stays green without path filtering. Verify `git apply --check` clean.
- [ ] **Step 5:** commit (specs + helpers + patch; NOT tutor-dist): `test(plan6): practice e2e — INV-6.5 five surfaces vs seeded real corpus, INV-6.6 attempt gate; CI patch (PM-applied)`.

**Acceptance:** five specs green locally against the seeded server (any pending archetype-class surface = honest empty-state assert, NAMED) + `phase5-core-loop.spec.ts` green in the same run; the env-gate verified BOTH ways (specs run with `JARVIS_PRACTICE_E2E=1`, self-skip without it — run once ungated to prove the skip); INV-6.6 proven both halves; patch applies clean; no shared file edited.

---

## Task 14 — Lane full-suite gate + merge manifest

The whole-branch gate in the worktree, then the PM merge package. **Lane B does NOT merge and does NOT deploy.**

**Files:**
- Create: `build-review/tmp/lane-b-plan6-patches/MERGE-MANIFEST.md`, `build-review/tmp/lane-b-plan6-patches/signatures-lock-plan6.patch`
- Read-only: `build-review/tmp/lane-b-plan6-patches/test-yml-practice.patch`

- [ ] **Step 1:** clean tree check; then the FULL gate in the worktree, never piped through `| tail`:
  - `gradle --no-daemon -p ../jarvis-kotlin-lane-b :check` → BUILD SUCCESSFUL (incl. GraderRoutingTotalityTest w/ named pending set, LlmNeverAloneTest, ExecutionGraderSandboxTest, extended GraderGoldenHarnessTest, LessonBeatGradeRouteTest pins, MockExamAdditiveRouteTest, PracticeRoutesTest). StateCache flake: name-and-rerun, never absorb.
  - `npm --prefix ../jarvis-kotlin-lane-b/tutor-web test` → vitest green.
  - `npx playwright test e2e/practice e2e/phase5-core-loop.spec.ts` (worktree, with `JARVIS_PRACTICE_E2E=1` set) → 5 practice suites green AND the Task-12-modified `phase5-core-loop.spec.ts` green (it lives at `e2e/` root — a bare `e2e/practice` filter would exclude it and merge a never-executed modified gate).
- [ ] **Step 2:** write `signatures-lock-plan6.patch` — the ADDITIVE lock section (§NEW-P: practice endpoints §0.9-G, `/drill/grade` additive fields §0.9-F, mock-exam additive fields §0.9-H, `ItemVerdict`) **PLUS (a) the explicit §NEW-L AMENDMENT entry for the Task-4 `ApiBeatAttempt` additive fields (`numeric_answer`/`numeric_tolerance` — served inside the frozen `ApiLessonReply.beats`, lock `:616`; §O amendment precedent) and (b) the Task-11 P2-5 reconciliation note (rubric-leg grading = bank-problem G-items only; `kind` wire + `kc_results` `verification_status` serialization untouched; lock `:236`/`:237` pins preserved)** — as a `git apply`-able patch against the lock doc; `git apply --check` clean.
- [ ] **Step 3:** merge-time zero-intersection re-check vs Lane A's post-cut commits on `main` (the 4a Task-6 Step-5(b) recipe verbatim; shared-baseline exclusions: none expected — package.json untouched this plan). **STOP on collision** → PM.
- [ ] **Step 4:** write `MERGE-MANIFEST.md`: branch/commits/changed-files; gate results; the TWO patches the PM applies (`test-yml-practice.patch`, `signatures-lock-plan6.patch`); the PM merge recipe (merge --no-ff → apply patches → rebuild frontend → FULL gate on merged main → push); **re-carry lines (explicit, per the rulings final-disposition):** cross-language schema-hash CI test · Linux visual-baseline regeneration · FSRS re-seed wart · **CodeMirror editor (R-6-Q9 deferral — named follow-up)** · **REQ-1 queue/arc mastery movement (spec 6.1 — re-scoped OUT of Plan 6 at plan-fix: no task here touches queue ordering/arc stage/mastery progression; goes to the tracking plan, never claimed shipped by this manifest)** · **R-MULTISELECT drill-side variant (spec 6.2.4/REQ-13 "lives here and in drills" — only the mock-exam grid half lands in this plan; the drills half is a named follow-up, not a silent drop)** · plus anything Task 2 left pending (subjects AND surface archetype-classes without corpus problems) and Task 0's R-toolchain outcome. Also: Task 15 is the PM-gated post-merge deploy — the manifest links it and states **no feature-shipped claim before Task 15's live probe**.
- [ ] **Step 5:** commit the manifest + lock patch; report: "Lane B plan6 green; manifest at `build-review/tmp/lane-b-plan6-patches/MERGE-MANIFEST.md`; ready for PM merge. NOT shipped until Task 15's live probe."

---

## Task 15 — POST-MERGE (PM-gated): VPS deploy + provisioning probe + production seed + live probe (R-6-Q1)

Runs AFTER the PM merges `lane-b/plan6` to `main`, from the MAIN tree, with PM authorization. This is the explicit deploy task R-6-Q1 demands; **no feature-shipped claim for ANY Plan-6 surface before this task's live probe passes.**

**Files:** none new in-repo (deploy + runbook execution; findings go in the task report / `build-review/`)

- [ ] **Step 1 — production problem seed (PC + VPS), behind the INV-3.1 backup gate:** same-day off-box dump verified for EACH DB before `ProblemSeed` + deliverable seeds run against it (the Plan-1 backup runner; the migration-side additive columns also land on first boot — `createMissingTablesAndColumns`). VPS operator note applies: ALWAYS pass explicit `JARVIS_TUTOR_DB` (the 871-vs-0 wrong-DB incident).
- [ ] **Step 2 — VPS toolchain provisioning probe:** on the VPS, probe `Rscript`/`python3`/`g++`/`java` + run `node tools/fetch-alk.mjs`; record which execution legs are live. Missing tools → the leg self-disables at boot (Task 5 behavior) — VERIFY the honest degradation live: submit a code-practice attempt for a disabled language and confirm the reply carries the RO degraded copy and a non-LLM-first fallback verdict (R-6-Q1 honest-degradation, proven in production, not assumed). Provision what is cheaply provisionable (`apt-get install r-base-core` — free); Alk jar via the fetch script.
- [ ] **Step 3 — live interaction smoke (feature-shipped rule):** against the live URL (and PC localhost), authenticated: open each of the five surfaces' routes; assert the spec testids paint, zero 4xx/5xx on paint + listed clicks, no error-text regex; run the INV-6.6 attempt-gate check live; submit one real graded attempt per chain leg class (numeric / code-on-an-available-runner / rubric-routed bash archetype if seeded / prose-LLM) and eyeball the verdicts. Render-before-claim-done: screenshot evidence in the task report.
- [ ] **Step 4:** report SHIPPED only if Steps 1–3 all green; otherwise report the honest partial state (which legs live where, which subjects pending) — the same honesty the chain itself serves.

---

*Plan written 2026-06-12 by the Lane-B plan-writer from `build-review/2026-06-12-plan6-recon.md` + `build-review/2026-06-12-plan4b6-pm-rulings.md` (binding) + spec §6/§3.4/§3.5; all §0 code anchors re-verified against the live tree at plan-write. Format per `2026-06-12-plan4a-gate-tooling.md`. Two-stage subagent execution per task; PM merges; Task 15 gates the shipped claim.*
