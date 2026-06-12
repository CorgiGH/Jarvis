# Plan 3: Lesson Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §4 + §5-first-family — BeatOrchestrator (replaces the 3-step LessonScreen), server BeatSelector over the closed plan table, per-beat server grading + completion writes (attempts w/ beat_type, EWMA, FSRS seed, first-encounter), lesson→drill handoff, RO beat content for the 4 faithful PA KCs, the graph/tree viz family with the MergeSort instance verified by trace-match, and the no-clip Playwright gate landing in the e2e suite FIRST.

**Architecture:** Server stays truth: beats live in KC YAML (Plan 2 schema), the lesson handler serves a new ADDITIVE `beats` payload only when `kc.beats["ro"].isCompleteFor(concept_type)` and grades every beat submission server-side via a new POST; the client BeatOrchestrator is a gated state machine ported (rewrite-port, audit E11) from `build-review/viz-fork-demo/lectie.html`. The graph/tree family renders `(family_id, instance_data)` only; MergeSort ships as the family's verification vehicle (trace-match + no-clip), NOT bound into faithful-KC beats (their content doesn't cover MergeSort — binding it would be a trust violation; figures are optional on beat ③ and the 4 authored beat sets carry none).

**Tech Stack:** Kotlin/Ktor/Exposed; React 19 + `motion` v12 (`motion/react` — NOT framer-motion, recon-verified) + d3-hierarchy; vitest + @testing-library; @playwright/test (stub-backend pattern); SQLite behind the INV-3.1 gate.

---

## Section 0 — Verified ground truth (recon 2026-06-12, workflow wf_00e9fe95; all file:lines re-verified against HEAD `dfe7d5c`; docs-only commit chain: `dfe7d5c` recon → `0c05807` plan file → `99a6916` Task-0 step-1 fix (docs-only, violated read-only contract — escalated) → subsequent review-fix of same (docs-only); no src/test/content files changed in any commit in the chain, all §0 anchors valid; Step 1 uses diff-based check against `dfe7d5c` — SHA-stable regardless of further docs-only commits)

### 0.1 The surface being replaced (audited dead ends — what "done" must kill)

- `LessonScreen.tsx:31-174`: `Step = 'entry'|'term'|'retrieval'|'done'`; `handlePredict(_option)` + `handleRetrieval(_answer)` DROP the learner's answers (dead `_` params, :101/:105); `done` renders static "Lecție completă. Bine făcut!" — **zero writes, zero navigation** (:168). The component is deleted at the end of Task 10 (rewrite-port complete).
- Route `/lesson/:kcId` (`main.tsx:51`, under `basename="/tutor"`) is **orphaned** — nothing navigates to it; it renders OUTSIDE the App shell (no nav back).
- `OggiScreen.tsx:49-52`: `handleSelect` = `setSelected(item)` + comment "Future: navigate to drill" — the `Începe` button (:150-156) is a no-op (audit E10).
- Server `GET /api/v1/lesson/{kcId}` (`QueueMasteryCalibrationRoutes.kt:386-433`): faithful-gate (resolveStatus + hasOpenReportWrong → 404) then a flat field-map; `prediction_options=emptyList()`, `definition_ro=null`, `userId` bound to `_` — **reads neither `kc.concept_type` nor `kc.beats`, writes nothing**.

### 0.2 What Plan 2 left ready

- `KnowledgeConcept.concept_type` (wire literal, all 8 PA KCs backfilled: 001/002/003 `definition-taxonomy`, 004 `proof`, 005/006 `formula-application`, fixture-recursion `code-trace`, fixture-compute `formula-application`) + `KnowledgeConcept.beats: Map<String, KcBeats> = emptyMap()` (`ContentSchema.kt:103-106`). **No KC YAML has a `beats:` key yet.**
- `KcBeats.isCompleteFor(conceptType)` (`KcBeats.kt:19-38`): ①②③⑤ minimum + numerical variant (skeleton_rows+trace_steps) for PROCEDURE/FORMULA_APPLICATION/PROBABILISTIC, choice variant otherwise; beat ④ optional. INV-3.2b (`TrustInvariantsCli.kt:50-68`): empty beats = skip; authored-but-incomplete = loud red.
- **Hash safety (recon-confirmed):** `claimsFor` (`ContentReconcile.kt:71-140`) reads ONLY `source/invariant/invariant_statement/grader_rules/explanation_ro/worked_example_ro`. `beats` and `concept_type` are NOT read ⇒ **authoring beats does NOT change content_hash** — the 4 faithful badges stay lit. Per-beat faithful-checking is gate-2 coverage extension (spec §9.2), sequenced in Plans 4/5 by the spec's own coverage map — Plan 3 serves beats under the existing KC-level faithful badge + structural completeness (INV-3.2b). Machine-checked here: Task 7 re-runs the Plan-2 hash-stability test after authoring.
- `VizInstance`/`PedagogicalInstance` (`VizInstance.kt`, `data_json` string + parse-validated loader `ContentRepo.loadVizInstances:56-73`). **No `viz/` dir exists under any subject yet.**

### 0.3 Server write paths (the §4.4 completion contract plugs into these)

- `KcMasteryRepo.recordIn(tx, userId, kcId, score, now)` — FROZEN lock §G; called first inside the drill/grade atomic txn (`TutorRoutes.kt:2222-2225`).
- `AttemptsTable` (`Phase1Tables.kt:34-51`): **no `beat_type`, no `prediction` columns** — Plan 3 adds both (nullable, additive). Insert pattern verbatim at `TutorRoutes.kt:2227-2242` (ULID via `TutorTypes.ulid()`, same-txn as recordIn).
- FSRS: drill/grade seeds via `upsertRubricCriterion(tx, …, Fsrs.initial(...))` (`TutorRoutes.kt:2246-2256`); schedule-forward is the separate `POST /api/v1/fsrs/{id}/grade` (raw update `TutorRoutes.kt:2001-2011`). **Known wart (NOT Plan 3's to fix): every drill re-grade clobbers the card back to initial** — Plan 3's completion mirrors the existing seed-if-absent call; the re-seed wart is carried to the plan index.
- First-encounter: NO stored flag; derivable as `existing == null` in `recordIn`'s INSERT branch (`KcMastery.kt:96-102`). Plan 3 stores it on the attempt row (`first_encounter` bool col, additive) — computed in the same txn BEFORE recordIn flips the row into existence.
- Phase: `readMastery(db,userId,kcId)?.phase ?: entryPhase ?: Phase.intro` (`QueueMasteryCalibrationRoutes.kt:439-453`, `resolvePhase:192`).

### 0.4 Frozen-signature constraints (lock canonical-on-conflict; DURABLE RULE = amend lock + pin test in the SAME commit)

- `ApiLessonReply` §NEW-L: 11 fields pinned IN ORDER by `SignatureLockPinTest.kt:101-112` — **appending a field breaks the pin; Task 3 amends the lock doc §NEW-L + the pin test in the same commit** (the lock's own DURABLE RULE procedure, not a violation).
- **Population legality (recon-verified verbatim):** lock says `prediction_options` "populate from a KC options source when one exists" → `beats["ro"].predict.options` IS that source — population legal when beats complete. `definition_ro` "no dedicated KC definition field" → `beats["ro"].name.definition` IS a dedicated field distinct from explanation_ro — population legal when non-blank. Pre-beats KCs keep `emptyList()`/`null` (honest-degraded).
- `VerificationGate.gate` ADMISSION-ONLY (§I.2 RF2 FROZEN) — the beats-completeness serve guard is an INLINE check in the lesson handler after the faithful gate (recon-located slot: after `QueueMasteryCalibrationRoutes.kt:399`, before the respond), never a gate-signature change.
- `QueueItem` (§C, 10 fields pinned) — Plan 3 does NOT touch it (lesson launch uses `kc_id` already present).
- `Phase`/`VerificationStatus` enums frozen; `KcMasteryRepo.recordIn` frozen; `recordIn`-only Phase writes.

### 0.5 Frontend infra facts

- Animation = **`motion` ^12 (`motion/react`)**; d3-hierarchy ^3.1.2 present. React 19, Tailwind v4 tokens (`bg-accent`/`text-page-fg`/`border-border-strong`/`shadow-hard`; radius locked 0; never hardcode hex; check `prefersReducedMotionNow()`).
- `AlgoStepperShell` (`viz/AlgoStepperShell.tsx:16-59`) ALREADY has the full scrubber (range + step-back/fwd/reset + counter, testids `{prefix}-scrubber/-step-back/-step-fwd/-reset`) and keyboard nav. Plan 3 plugs the family into it (families never implement their own playback, §5.3) and adds an ADDITIVE `labels` prop (RO on the lesson surface; defaults keep current EN so demos/gallery don't churn — full chrome sweep is Plan 4/§8.2).
- Playwright: `e2e/` dir, baseURL `http://localhost:5173`, `webServer: npm run dev`, locale ro-RO, `reducedMotion: 'reduce'`; CI frontend job has NO JVM → e2e uses the **stub-backend pattern** (`tutor-shell-api-contract.spec.ts:7-32` is the repaired reference: navigate `/tutor/...`, `route.fulfill()` stubs, zero-4xx/5xx gate that provably reds).
- **No-clip:** NO helper exists in the suite; the geometry logic lives in ad-hoc `audit.viz.mjs:76-125` (viewport overflow, scroll-vs-client clip +2px tolerance, interactive-overlap area >16px²). Task 9 ports it into `e2e/helpers/assertNoClip.ts` — **the standing gate becomes suite-real in this plan, before any baseline exists** (council amendment (c)).
- vitest pattern: `LessonScreen.test.tsx` (vi.mock the lib module) + `DrillStack.test.tsx` (matchMedia stub, fetch stub, MemoryRouter). e2e excluded from vitest by include-glob.
- Old viz registry lockstep (`vizRegistry.ts` ⟷ `content/viz-ids.yaml` ⟷ `checkVizReferences`) keyed on `kc.viz_id` — only `recursion-tree` registered. **The family system is a NEW channel** (`FigureBinding`), so Plan 3 adds a separate `familyRegistry.ts` and does NOT touch the legacy lockstep (it dies with the V6 retirement in later plans).
- Strings: no i18n layer exists. Plan 3 ships `tutor-web/src/lib/lessonStrings.ts` (RO learner-facing strings for the new surface only; the global sweep is Plan 4).

### 0.6 Locked decisions + deviations (binding on every task)

1. **MergeSort instance = family verification vehicle, NOT lesson content.** The 4 faithful KCs (lecture-01 fundamentals) get NO figure binding — their beat ③ reveals are stepped text (figure nullable by schema). Binding a MergeSort figure to pa-kc-001..004 would attach content their source doesn't teach = trust violation. The family ships verified (trace-match + semantic invariants + no-clip) against the MergeSort instance at `content/PA/viz/viz-pa-mergesort-001.yaml`; first SERVED binding arrives with digestion content (Plan 5).
2. **Beat authoring derives ONLY from each KC's existing YAML** (name/source quotes/explanation_ro/worked_example_ro/self_explanation_prompt/far_transfer_stem) — zero new subject facts; same rule as the existing Phase-3 scaffolding comments. All learner strings Romanian (diacritic-bearing); EN technical terms only when the source quote is EN, framed RO (§8.1). Dual-writer + adversarial-review protocol; Alex NEVER asked to vet (no-oracle-inversion).
3. **Beats-incomplete ⇒ lesson 404** `{"error":"beats not complete"}` (inline guard after the faithful gate). The only currently-servable lessons are the 4 faithful PA KCs and all 4 get complete beats in Task 7 — no served surface regresses. LessonScreen + its test are DELETED in Task 10 (the BeatOrchestrator replaces, never coexists — spec §4.1 "replaces").
4. **Server is truth for grading:** the client never self-grades; every gated beat posts to `POST /api/v1/lesson/{kcId}/beat` and the server grades against the KC's own beats data, writes the attempt row (beat_type, prediction, first_encounter), and on CHECK runs the completion writes (recordIn + FSRS seed) in ONE atomic txn mirroring `TutorRoutes.kt:2222-2256`.
5. **RE_LESSON plan exists in the closed table but is NEVER produced in Plan 3** (forgetting trigger = Plan 7); the selector emits it only behind an explicit `reLesson=true` parameter that no Plan-3 call site passes. INV-4.1 property test covers it as table-legality, not as a served path.
6. **No `claimsFor`/`kcContentHashOf`/`TrustSync`/`VerificationGate` edits** — same hard line as Plan 2 (§0.8 #3 there). Diff touching them = stop work.
7. **Standing:** never `git add -A`/`git clean` on main; live-DB mutation only behind INV-3.1 with fresh off-box dumps (AttemptsTable gains 3 columns ⇒ the gate FIRES on both live DBs); badge text never "corect"; explicit DB path on VPS; gradle daemon allowed (omit `--no-daemon`).

---

## Section 0.9 — Canonical types & contracts (SINGLE SOURCE OF TRUTH; copy verbatim, divergence = bug)

### A. BeatPlan — new file `src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt`

```kotlin
package jarvis.tutor.lesson

import jarvis.content.ConceptType
import jarvis.tutor.Phase

/** Spec §4.5 — the CLOSED set of legal beat plans. No fifth plan may ever be added by a prompt. */
enum class BeatPlan(val beats: List<BeatType>) {
    FULL(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK)),
    STANDARD(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    MASTERED_REVISIT(listOf(BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    RE_LESSON(listOf(BeatType.REVEAL, BeatType.CHECK)),
}

/** Wire literals are lowercase names; beats are NEVER reordered (council-1781052957). */
enum class BeatType { PREDICT, ATTEMPT, REVEAL, NAME, CHECK }

/**
 * Spec §4.2 — concept_type x mastery phase -> plan, vocabulary FIXED. The selector chooses
 * compression, never new vocabulary. First encounters (no mastery row / observations == 0)
 * receive only FULL or STANDARD (INV-4.1). RE_LESSON only via the §7.3 forgetting trigger
 * (Plan 7) — reLesson=true is passed by NO Plan-3 call site.
 */
object BeatSelector {
    fun planFor(conceptType: ConceptType, phase: Phase?, isFirstEncounter: Boolean, reLesson: Boolean = false): BeatPlan = when {
        reLesson && !isFirstEncounter -> BeatPlan.RE_LESSON
        isFirstEncounter -> BeatPlan.FULL          // first contact always carries ④ NAME
        phase == Phase.mastered -> BeatPlan.MASTERED_REVISIT
        else -> BeatPlan.STANDARD
    }
}
```

### B. Lesson wire DTOs — extend `QueueMasteryCalibrationRoutes.kt` (ADDITIVE; lock §NEW-L + pin test amended in the SAME commit, Task 3)

```kotlin
/** ADDITIVE field on ApiLessonReply (12th field, appended LAST): */
val beats: ApiLessonBeats? = null,   // null ⇒ legacy payload (never served in practice post-Task 3: incomplete beats 404)

@Serializable
data class ApiLessonBeats(
    val plan: List<String>,                  // BeatType lowercase names, in served order
    val concept_type: String,                // the KC's wire literal
    val predict: ApiBeatPredict? = null,     // present iff plan contains "predict"
    val attempt: ApiBeatAttempt? = null,
    val reveal: ApiBeatReveal? = null,
    val name: ApiBeatName? = null,           // present iff plan contains "name"
    val check: ApiBeatCheck? = null,
)
@Serializable data class ApiPredictOption(val text: String, val callback: String, val correct: Boolean)
@Serializable data class ApiBeatPredict(val prompt: String, val options: List<ApiPredictOption>)
@Serializable data class ApiAttemptChoice(val text: String, val correct: Boolean, val feedback: String)
@Serializable data class ApiSkeletonRow(val label: String, val formula: String?, val is_decision_row: Boolean)
@Serializable data class ApiTraceStep(val row_index: Int, val value: String, val callout: String?)
@Serializable data class ApiBeatAttempt(
    val statement: String,
    val choices: List<ApiAttemptChoice> = emptyList(),
    val skeleton_rows: List<ApiSkeletonRow> = emptyList(),
    val trace_steps: List<ApiTraceStep> = emptyList(),
    val input_schema: String? = null,
    val feedback_correct: String,
)
@Serializable data class ApiRevealStep(val text: String, val callout: String)
@Serializable data class ApiFigureBinding(val family_id: String, val instance_id: String)
@Serializable data class ApiBeatReveal(val steps: List<ApiRevealStep>, val figure: ApiFigureBinding? = null)
@Serializable data class ApiBeatName(val definition: String, val invariant_statement: String, val why_matters: String)
@Serializable data class ApiBeatCheck(val item_stem: String, val choices: List<ApiAttemptChoice> = emptyList(), val numeric_answer: String? = null, val numeric_tolerance: Double? = null)
```

(`correct` flags ride to the client for the gate/echo UX; the SERVER remains grading truth via the POST below — the client reply is never trusted for writes.)

### C. Beat grade endpoint — `POST /api/v1/lesson/{kcId}/beat` (new, same file)

```kotlin
@Serializable
data class ApiBeatGradeRequest(
    val beat_type: String,            // "predict" | "attempt" | "check" (gated beats with learner input)
    val selected_index: Int? = null,  // choice beats: index into the served options/choices
    val free_input: String? = null,   // numerical attempt / numeric check
    val prediction_text: String? = null, // predict beats: the chosen option text (stored on the attempt row)
)
@Serializable
data class ApiBeatGradeReply(
    val correct: Boolean,
    val score: Double,                // predict/attempt: 1.0/0.0 informational; check: feeds EWMA
    val feedback_ro: String,          // both-path feedback / option callback (RO)
    val beat_type: String,
    val lesson_complete: Boolean,     // true on the graded CHECK
    val first_encounter: Boolean,
    val phase: Phase? = null,         // post-write phase (CHECK only)
    val verification_status: VerificationStatus? = null,
)
```

Server behavior (Task 4): faithful+beats gate identical to GET; grade from the KC's beats data (selected_index → option.correct; numeric → tolerance compare); EVERY graded beat writes ONE attempt row (`beat_type` col = lowercase literal, `prediction` col = prediction_text for predict beats, `first_encounter` computed as `readMastery(...) == null && no prior lesson attempt rows for (user,kc)`); on `beat_type=="check"`: same-txn `recordIn(score)` + `upsertRubricCriterion` seed (mirrors `TutorRoutes.kt:2222-2256`) + `lesson_complete=true`.

### D. AttemptsTable additive columns (Task 1; INV-3.1 fires on live DBs)

```kotlin
    /** Plan-3 §4.4 (audit T3) — which lesson beat produced this attempt; NULL for drill attempts. */
    val beatType = varchar("beat_type", 12).nullable()
    /** Plan-3 §4.4 — the learner's predict-beat commitment, echoed at reveal; NULL otherwise. */
    val prediction = text("prediction").nullable()
    /** Plan-3 §4.4 — true iff this attempt was the user's FIRST contact with the KC. */
    val firstEncounter = bool("first_encounter").nullable()
```

### E. Dwell — new file `tutor-web/src/lib/dwell.ts` (demo constants verbatim, `lectie.html:185-186`)

```ts
/** min(5500, max(1400, round(900 + words*320))) ms — a FLOOR, never a ceiling (spec §4.1). */
export function readMs(text: string): number {
  const w = text.trim().split(/\s+/).filter(Boolean).length;
  return Math.min(5500, Math.max(1400, Math.round(900 + w * 320)));
}
```

INV-4.2 boundary tests: 0 words→1400, 2→1540, 14→5380, 15→5500 (capped), 1000→5500.

### F. BeatOrchestrator — new files `tutor-web/src/components/lesson/BeatOrchestrator.tsx` + per-beat subcomponents (`PredictBeat.tsx`, `AttemptBeat.tsx`, `RevealBeat.tsx`, `NameBeat.tsx`, `CheckBeat.tsx`) + `lessonStrings.ts`

Contract (spec §4.1/§4.7): consumes `lesson.beats.plan[]`; ONE active beat; **Next (`data-testid="beat-next-gate"`) disabled until the beat's gate clears** — predict committed (POST returned), attempt submitted (POST returned), reveal stepped to final at least once AND dwell floor met per step, check answered (POST returned); pips `data-testid="lesson-beat-pips"` (N pips, glyph-labeled ①②③④⑤); predict-callback echo at reveal start (the served option's `callback` for the learner's stored choice); reveal stepping reuses the AlgoStepperShell scrubber WHEN a figure is bound, else a stepped-text list with back/forward (same gate semantics, `data-testid="beat-figure-scrubber"` + `"scrubber-step-counter"` "pas k/N" present whenever stepping exists); completion screen `data-testid="lesson-complete-handoff"` navigates to the drill surface with the same KC visible (Task 10 verifies the concrete target route against the codebase and wires it; fallback contract = `/oggi` with `queue-item-{kcId}` visible). All learner-visible strings from `lessonStrings.ts`, Romanian.

### G. Graph/tree family — new files `tutor-web/src/components/viz/families/GraphTreeFamily.tsx` + `familyRegistry.ts` + instance `content/PA/viz/viz-pa-mergesort-001.yaml`

- Typed slots (parsed from `VizInstance.instance.data_json`): `{ nodes: [{id, label, parent?}], steps: [{ highlight: string[], deltas: [{node, label?}], callout: string }] }` — schema validated at parse (zod-free: hand-rolled guard, load error names the instance).
- Deterministic d3-hierarchy layout that MEASURES labels (`getBBox`/canvas-measure) and reserves space — no-clip by construction (§5.3); callouts anchored to the highlighted node, never a detached footer; caption/callout text = instance data (language-keyed by the instance's `language`).
- Playback EXCLUSIVELY through `AlgoStepperShell` (frames built from steps; `labels` additive prop, RO on lesson surface).
- MergeSort instance data ports the demo's steps for its REAL input `[5,2,8,1,9,3]` (`lectie.html:142,148`) — **8 family steps** (4 divide incl. depth-0 + 1 pause + 2 merge + 1 final; the live demo's "pas N/9" counts a play-reset the family does not). The trace-match test asserts `frames.length === reference.length` (self-consistent, red on drift) — never a hardcoded count.
- Trace-match harness (vitest, INV-5.1): an actual mergesort reference implementation runs in the test over the instance's input; every step's rendered state (highlight set + node labels) asserted equal to the reference trace. Semantic invariants (INV-5.2): leaf count == input length at every step; merged node label == sorted concat of children; final step label == fully sorted array.
- `familyRegistry.ts`: `export const familyRegistry: Record<string, FamilyRenderer> = { "graph-tree": GraphTreeFamily }` — NEW channel; legacy vizRegistry/viz-ids.yaml UNTOUCHED.

### H. No-clip e2e helper — new file `tutor-web/e2e/helpers/assertNoClip.ts` (ported from `audit.viz.mjs:76-125` verbatim logic)

`assertNoClip(page, scopeTestId)` asserts: zero viewport-width overflow (>1px), zero text clipping (scroll vs client +2px), zero interactive-element overlaps (intersection area >16px²). Used at TWO viewport heights (e.g. 900 and 620) in every lesson/family spec (INV-5.3 + the standing cardinal-sin gate). Lands in the suite in Task 9 BEFORE any visual baseline exists (council amendment (c)).

### I. Acceptance criteria mapped

| INV | Where |
|---|---|
| INV-4.1 plan legality property test (8 types × phases × first-encounter × reLesson) | Task 2 |
| INV-4.2 dwell boundaries | Task 5 |
| INV-4.3 real-DB run-through: attempt rows w/ beat_type + EWMA + FSRS + first-encounter exactly once | Task 12 (on a live-DB COPY — never pollute Alex's real mastery with the PM's run-through) |
| INV-4.4 Playwright §4.7 selectors + interaction smoke + scrubber-back + zero 4xx/5xx/console | Task 9 (stub-backend in CI, the repaired-contract pattern; full real-backend pass in Task 12) |
| INV-5.1 trace-match over live instances | Task 8 |
| INV-5.2 semantic invariants | Task 8 |
| INV-5.3 no-clip 2 viewports | Tasks 8/9 |
| INV-5.4 import ban (three/webgl/webgpu/canvas-figure) | Task 9 (CI grep test) |
| INV-5.5 figure bindings resolve (family registered + instance exists) | Task 7 (ContentValidator additive check; vacuous-true for the 4 figure-less beat sets, red on dangling binding) |

---

## Task list

| # | Task |
|---|---|
| 0 | Preconditions probe (read-only) |
| 1 | AttemptsTable +3 additive cols + migration/pin tests |
| 2 | BeatSelector + BeatPlan closed table + INV-4.1 property test |
| 3 | ApiLessonBeats DTOs + lesson GET: beats guard + payload + legal population of prediction_options/definition_ro + lock §NEW-L + SignatureLockPinTest amended SAME commit |
| 4 | POST /lesson/{kcId}/beat: server grading + attempt/completion writes + tests |
| 5 | dwell.ts + INV-4.2 tests |
| 6 | BeatOrchestrator + beat subcomponents + lessonStrings.ts + vitest |
| 7 | RO beat authoring for pa-kc-001..004 (dual-writer + adversarial review; source-derived only) + INV-3.2b green + hash-stability re-proof + INV-5.5 validator check |
| 8 | GraphTreeFamily + familyRegistry + MergeSort instance + trace-match + semantic invariants + family no-clip |
| 9 | assertNoClip helper + lesson-route Playwright suite (§4.7 + interaction smoke + import-ban test) |
| 10 | Wires: Oggi Începe→lesson; lesson-complete-handoff→drill surface (verify target); DELETE LessonScreen(+test); shell `labels` prop RO |
| 11 | deploy.sh smoke fix (healthz + /login title grep — carried follow-up) |
| 12 | LIVE PC ops: gated migrate + real lesson run-through on a live-DB copy (INV-4.3) + trustInvariants |
| 13 | VPS: backup → deploy → migrate → asserts → full-page screenshots for the morning eyeball packet |
| 14 | Whole-suite gate + plan-index update + push |

## Task 0 — Preconditions probe (read-only, no commit)

Verify the working tree, the live PC DB, the corpus state, and the baseline backend suite are exactly what Plan 3 builds on **before** any edit. Every check is a command + the expected output + a STOP instruction. This task makes no edits and no commit. Plan 2 is already committed on `main` (`ConceptType`, `KcBeats`, the 7 Plan-2 tables, `SignatureLockPinTest`) — Plan 3 is additive on top of the docs-only commit chain descending from recon SHA `dfe7d5c` (chain: `dfe7d5c` recon → `0c05807` plan file → `99a6916` Task-0 step fix → subsequent review-fix; all §0 file:line anchors remain valid because every commit in the chain touched only `docs/superpowers/plans/2026-06-12-plan3-lesson-engine.md`).

**Files:** none (read-only).

- [ ] **Step 1: Confirm the branch + HEAD + clean-enough git state.** From repo root `C:\Users\User\jarvis-kotlin`:

```powershell
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
git status --short
```

Expected: branch `main`; HEAD is any commit in the docs-only chain descending from `dfe7d5c`; the recon anchors in §0 were verified against `dfe7d5c` — every commit above it touched only `docs/superpowers/plans/2026-06-12-plan3-lesson-engine.md`, so all §0 file:line anchors remain valid. **Verify the chain is docs-only** (run `git diff dfe7d5c..HEAD -- src/main/kotlin/ src/test/kotlin/ tutor-web/src/ tutor-web/e2e/ content/PA/kcs/` — expected: empty output). `git status --short` shows ONLY the known working-tree noise: door demos under `src/main/resources/tutor-dist/` (`doors-v2.html`, `doors-v3.html`, `doors-lab/`, `roadmap.html`, `work-so-far.html`, `jarvis-roadmap*`, `work/`), the deleted/rebuilt `index-*.js/.css` bundle assets + `index.html`, `build-review/` artifacts (incl. `build-review/tmp/`), `.claude/council-cache/council-*.md`, `.claude/active-constraints.md`, `*.json` probe files (`door-probe-*`, `probe-*`, `trust-verdicts.json`), `tools/audit_kc_quotes.py`, `tools/build_rc_ghid.py`, `tutor-web/audit.viz*.mjs`, `tutor-web/*.html`, `tutor-web/public/doors-*`. Note: `docs/superpowers/plans/2026-06-12-plan3-lesson-engine.md` is now **tracked** (committed at HEAD — no longer in the untracked noise list). There must be **no staged changes** and **no modifications** to any file under `src/main/kotlin/`, `src/test/kotlin/`, `tutor-web/src/`, `tutor-web/e2e/`, or any `content/PA/kcs/*.yaml`. **STOP if** the diff `dfe7d5c..HEAD` for src/main/kotlin/, src/test/kotlin/, tutor-web/src/, tutor-web/e2e/, content/PA/kcs/ is non-empty (recon anchors displaced — re-run recon before proceeding), or any source/test/content file is already modified/staged (a prior task did not finish — re-assess). Do **not** `git add -A`, `git clean`, switch branches, or merge `door-compare` (Section 0.6 #7 — the untracked door files must stay untouched).

- [ ] **Step 2: Confirm the new Plan-3 files do NOT exist yet** (each is created by a later task; a pre-existing one means another task already ran):

```powershell
Test-Path src\main\kotlin\jarvis\tutor\lesson\BeatSelector.kt
Test-Path tutor-web\src\lib\dwell.ts
Test-Path tutor-web\src\components\lesson\BeatOrchestrator.tsx
Test-Path tutor-web\src\components\viz\families\GraphTreeFamily.tsx
Test-Path tutor-web\e2e\helpers\assertNoClip.ts
```

Expected: `False` for all five. **STOP if** any returns `True` — Plan 3 introduces these paths; a pre-existing one means a downstream task already ran and the tree is mid-flight.

- [ ] **Step 3: Probe the live PC `attempts` table read-only — confirm it lacks the 3 Plan-3 columns and is empty.** This is the Task-1 migration baseline:

```powershell
python -c "import sqlite3,pathlib; db=(pathlib.Path.home()/'.jarvis'/'tutor.db').resolve(); con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True); print('attempts cols:', [c[1] for c in con.execute('PRAGMA table_info(attempts)')]); print('attempts rows:', con.execute('SELECT COUNT(*) FROM attempts').fetchone()[0])"
```

Expected output (exactly):

```
attempts cols: ['id', 'user_id', 'kc_id', 'task_id', 'problem_id', 'phase', 'student_confidence', 'correct', 'score', 'scaffold_level', 'is_far_transfer', 'self_explanation', 'recorded', 'graded_at']
attempts rows: 0
```

**STOP if** `beat_type`, `prediction`, or `first_encounter` is ALREADY present (a prior Task-1 apply ran against the live DB — the INV-3.1 gate state must be re-assessed before re-applying), or if the 14-column set differs otherwise (the live `attempts` shape drifted from `Phase1Tables.kt:34-51`). Record the `attempts rows` number here (expected 0) — Task 12's INV-4.3 run-through asserts the row count GREW by exactly the lesson's graded-beat count on the live-DB COPY.

- [ ] **Step 4: Confirm the corpus + verdict baseline** (the 828-card store, the 8 verdict rows / 4 faithful, all 8 KCs typed, zero beats authored). The 4 faithful KCs are the only servable lessons; Task 7 authors complete beats for exactly pa-kc-001..004:

```powershell
python -c "import sqlite3,pathlib; db=(pathlib.Path.home()/'.jarvis'/'tutor.db').resolve(); con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True); print('cards:', con.execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0]); print('kvs rows:', con.execute('SELECT COUNT(*) FROM kc_verification_status').fetchone()[0]); print('kvs faithful:', con.execute(\"SELECT COUNT(*) FROM kc_verification_status WHERE status='faithful'\").fetchone()[0])"
(Get-ChildItem content\PA\kcs\*.yaml | Measure-Object).Count
(Select-String -Path content\PA\kcs\*.yaml -Pattern '^concept_type:' | Measure-Object).Count
(Select-String -Path content\PA\kcs\*.yaml -Pattern '^beats:' | Measure-Object).Count
```

Expected output (exactly):

```
cards: 828
kvs rows: 8
kvs faithful: 4
8
8
0
```

**STOP if** `cards != 828` (the irreplaceable corpus changed — do not build over it), `kvs rows != 8` or `kvs faithful != 4` (the trust state drifted — Plan 3 serves only faithful KCs), the YAML count is not 8 (the 8-KC corpus Task 7 authors against changed), the `concept_type` count is not 8 (Plan 2's backfill regressed — Task 2's selector + Task 7's beats key off `concept_type`), or the `beats:` count is not 0 (a Task-7 authoring pass already ran — re-assess the served-lesson set before continuing).

- [ ] **Step 5: Confirm `KcBeats.isCompleteFor` exists** (Plan 2 shipped it; Task 4's serve guard + Task 7's authoring depend on it being present and unchanged):

```powershell
Test-Path src\main\kotlin\jarvis\content\KcBeats.kt
Select-String -Path src\main\kotlin\jarvis\content\KcBeats.kt -Pattern 'fun isCompleteFor'
```

Expected: `True`, and one match line for `fun isCompleteFor(conceptType: ConceptType): Boolean`. **STOP if** the file is missing or the function signature differs — Plan 3 consumes this Plan-2 contract verbatim.

- [ ] **Step 6: Confirm the baseline backend suite is green before changing anything.** Run gradle directly (never pipe through `| tail` / `| head` — review-workflow rule):

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`. **STOP and report if it is not green** — do not build on red. (`:check` includes `validateContent`, which loads the real corpus through `ContentValidator.validate`, and `SignatureLockPinTest`; Tasks 1 and 3 extend those exact paths, so a clean baseline here is the regression net.)

- [ ] **Step 7: Confirm the frontend baseline is green.** From `tutor-web`:

```powershell
npm test
```

Expected: vitest reports all suites pass. (Known: the `tutor-shell-api-contract.spec.ts` Playwright e2e is excluded from the vitest include-glob, so it does NOT run here; a separate `npm run e2e` red is pre-existing and is NOT Plan 3's to fix unless a Plan-3 spec touches it — Section 0.6.) **STOP if** any vitest suite is red — Tasks 5 and 6 add vitest suites to a clean baseline.

---

## Task 1 — `AttemptsTable` +3 additive nullable columns + migration/pin tests

Adds the three additive nullable columns the lesson-completion contract (spec §4.4, audit T3) writes onto each beat attempt: `beat_type`, `prediction`, `first_encounter`. All three are nullable (drill attempts leave them NULL), so `SchemaUtils.createMissingTablesAndColumns` adds them as bare `ALTER … ADD COLUMN` with no backfill (the live `attempts` table has 0 rows — Task 0 Step 3). The 3 columns are copied VERBATIM from plan core §0.9D. **INV-3.1 note:** adding these columns is pending DDL ⇒ the migration backup gate FIRES on the two live DBs (≥800 `fsrs_cards`). This task does NOT apply to any live DB — operational apply is Tasks 12 (PC) / 13 (VPS). The migration test below uses fresh **temp** DBs through `TutorMigration.migrate`, where the gate self-skips (0 protected rows), exactly as `LiveShapeColumnsMigrationTest` does.

**SignatureLockPinTest note (verified at recon):** `SignatureLockPinTest.kt` does NOT pin the `attempts` column SET — it only lists `"attempts"` in the frozen-table-NAME set (`:142`) for the additivity collision check (Plan-2 tables must not collide). Adding columns to `attempts` does NOT break that name set, so **no pin amendment is required** for this task. The `AttemptsTable` lives in `Phase1Tables.kt`, which is NOT in the §NEW-L / `ApiLessonReply` lock scope (that is Task 3). Therefore this task touches the lock doc only with the optional `attempts`-columns note below; no `SignatureLockPinTest` edit is part of this commit.

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Phase1Tables.kt` (3 additive columns on `AttemptsTable`)
- Create: `src/test/kotlin/jarvis/tutor/AttemptsBeatColumnsMigrationTest.kt`
- Modify (note only): `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` (one §-note recording the 3 additive `attempts` columns + that they are NOT pinned by `SignatureLockPinTest`)

- [ ] **Step 1: Write the failing migration test.** Model it on `LiveShapeColumnsMigrationTest.kt:25-65` (full-migrate a fresh temp DB → DROP the new columns to reproduce the live shape → re-migrate → assert the exact delta + idempotence + nullability + that the other `attempts` columns are unchanged). Create `src/test/kotlin/jarvis/tutor/AttemptsBeatColumnsMigrationTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-3 Task 1 — pins the live-DB column delta the lesson-completion migration adds to `attempts`
 * (spec §4.4, audit T3): the 3 additive nullable columns beat_type / prediction / first_encounter.
 *
 * Live `attempts` shape captured READ-ONLY from ~/.jarvis/tutor.db on 2026-06-12 (Task 0 Step 3),
 * 14 columns, 0 rows:
 *   [id, user_id, kc_id, task_id, problem_id, phase, student_confidence, correct, score,
 *    scaffold_level, is_far_transfer, self_explanation, recorded, graded_at]
 * Code (Phase1Tables.kt) additionally defines beat_type / prediction / first_encounter, all nullable.
 *
 * Replica technique (same as LiveShapeColumnsMigrationTest): full-migrate a fresh temp DB, DROP the
 * 3 new columns (sqlite-jdbc bundles SQLite >= 3.35 ⇒ ALTER TABLE … DROP COLUMN) to reproduce the
 * exact live shape, then re-migrate and assert the deltas. The INV-3.1 backup gate self-skips on a
 * fresh DB (0 protected rows) — this test NEVER touches a live DB.
 */
class AttemptsBeatColumnsMigrationTest {

    private val liveShape = listOf(
        "id", "user_id", "kc_id", "task_id", "problem_id", "phase", "student_confidence",
        "correct", "score", "scaffold_level", "is_far_transfer", "self_explanation",
        "recorded", "graded_at",
    )

    private fun liveShapeDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("attempts-shape.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        transaction(db) {
            for (c in listOf("beat_type", "prediction", "first_encounter")) {
                exec("ALTER TABLE attempts DROP COLUMN $c")
            }
        }
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `attempts gains exactly beat_type prediction first_encounter`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp)
        assertEquals(
            liveShape,
            columnsOf(db, "attempts"),
            "replica must match the live 14-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            (liveShape + listOf("beat_type", "prediction", "first_encounter")).toSet(),
            columnsOf(db, "attempts").toSet(),
        )

        // the 14 pre-existing columns are untouched (no rename / drop of an existing column)
        val after = columnsOf(db, "attempts").toSet()
        for (c in liveShape) assertEquals(true, c in after, "pre-existing column '$c' must survive")

        // idempotent (M-IDEMP): a second run is a no-op
        TutorMigration.migrate(db)
        assertEquals(17, columnsOf(db, "attempts").size)
    }

    @Test
    fun `the 3 new columns are all nullable (additive, no backfill on the empty live table)`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp)
        TutorMigration.migrate(db)
        transaction(db) {
            exec("PRAGMA table_info(attempts)") { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    if (name in setOf("beat_type", "prediction", "first_encounter")) {
                        assertEquals(0, rs.getInt("notnull"), "$name must be nullable")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run — expect RED** (the replica DROP fails because the columns do not exist yet, OR the post-migrate assertion fails because the columns are never re-added):

```powershell
gradle :test --tests "jarvis.tutor.AttemptsBeatColumnsMigrationTest"
```

Expected: `> Task :test FAILED`. The first run reds in `liveShapeDb` at `ALTER TABLE attempts DROP COLUMN beat_type` (`no such column: beat_type`) — because `AttemptsTable` does not yet declare the column, so the fresh migrate never created it to drop. This is the correct RED for the not-yet-added column.

- [ ] **Step 3: Add the 3 additive columns to `AttemptsTable`.** In `src/main/kotlin/jarvis/tutor/Phase1Tables.kt`, the `AttemptsTable` object ends with the `gradedAt` column, then `primaryKey`, then the `init { index(...) }` block. Insert the 3 columns (VERBATIM from plan core §0.9D) immediately after the `gradedAt` line, before `override val primaryKey`.

Find:

```kotlin
    val recorded = bool("recorded")
    val gradedAt = timestamp("graded_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, kcId, gradedAt) }
}
```

Replace with:

```kotlin
    val recorded = bool("recorded")
    val gradedAt = timestamp("graded_at")
    /** Plan-3 §4.4 (audit T3) — which lesson beat produced this attempt; NULL for drill attempts. */
    val beatType = varchar("beat_type", 12).nullable()
    /** Plan-3 §4.4 — the learner's predict-beat commitment, echoed at reveal; NULL otherwise. */
    val prediction = text("prediction").nullable()
    /** Plan-3 §4.4 — true iff this attempt was the user's FIRST contact with the KC. */
    val firstEncounter = bool("first_encounter").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, kcId, gradedAt) }
}
```

- [ ] **Step 4: Run the migration test — expect GREEN:**

```powershell
gradle :test --tests "jarvis.tutor.AttemptsBeatColumnsMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, both tests pass.

- [ ] **Step 5: Record the additive columns in the interface-signatures-lock as a §-note** (not a pin amendment — `SignatureLockPinTest` does not pin the `attempts` column set; this note documents the additive change + why no pin moves). In `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, locate the §G mastery/write-path section (the `KcMasteryRepo.recordIn` lock referenced in plan core §0.3). Append a note paragraph at the end of that section:

```markdown
**Note (Plan 3, 2026-06-12, additive — NOT a new pin):** `AttemptsTable` (`Phase1Tables.kt`) gains 3
additive nullable columns for the lesson-completion contract (spec §4.4, audit T3): `beat_type`
varchar(12), `prediction` text, `first_encounter` bool — all nullable (drill attempts write NULL).
These are NOT in the frozen-pin set: `SignatureLockPinTest` lists `attempts` only in its
frozen-table-NAME additivity set (no column-set pin on `attempts`), so adding columns breaks no pin.
The 3-column delta is pinned by `AttemptsBeatColumnsMigrationTest` (the live-shape replica test),
mirroring `LiveShapeColumnsMigrationTest`. INV-3.1: this is pending DDL ⇒ the backup gate FIRES on
both live DBs; operational apply is Plan-3 Tasks 12 (PC) / 13 (VPS), never a unit test.
```

- [ ] **Step 6: Run the full backend suite** (the new column changes the migration path `:check` exercises; `SignatureLockPinTest` must still be green — confirms no pin was disturbed; never trust a partial run — review-workflow rule):

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL` (includes `AttemptsBeatColumnsMigrationTest`, `SignatureLockPinTest`, `LiveShapeColumnsMigrationTest`, and `validateContent`).

- [ ] **Step 7: Commit explicit paths only** (never `git add -A` — Section 0.6 #7):

```powershell
git add src/main/kotlin/jarvis/tutor/Phase1Tables.kt src/test/kotlin/jarvis/tutor/AttemptsBeatColumnsMigrationTest.kt docs/superpowers/plans/2026-06-02-interface-signatures-lock.md
git commit -m "feat(lesson): AttemptsTable +beat_type/prediction/first_encounter (additive, INV-3.1)"
```

---

## Task 2 — `BeatSelector` + `BeatPlan` closed table + `BeatType` + INV-4.1 property test

Adds the server-side beat-plan selector (spec §4.2/§4.5): `concept_type × mastery phase → BeatPlan`, over the CLOSED set of four legal plans (FULL / STANDARD / MASTERED_REVISIT / RE_LESSON). The vocabulary is FIXED (council-1781052957): beats are PREDICT ① ATTEMPT ② REVEAL ③ NAME ④ CHECK ⑤, **never reordered**, and no fifth plan / sixth beat kind may be added by a prompt. All three types (`BeatPlan`, `BeatType`, `BeatSelector`) are copied VERBATIM from plan core §0.9A into one new file. The INV-4.1 property test (spec §4.8) exhausts every `ConceptType` × phase (null + all 4 Phase values) × `isFirstEncounter` × `reLesson` combination and asserts the legality invariants. **RE_LESSON is table-legal only** — it is emitted solely behind `reLesson=true && !isFirstEncounter`, a parameter no Plan-3 call site passes (Section 0.6 #5; the forgetting trigger is Plan 7).

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt`
- Create: `src/test/kotlin/jarvis/tutor/lesson/BeatSelectorTest.kt`

- [ ] **Step 1: Write the failing INV-4.1 property test.** Create `src/test/kotlin/jarvis/tutor/lesson/BeatSelectorTest.kt`:

```kotlin
package jarvis.tutor.lesson

import jarvis.content.ConceptType
import jarvis.tutor.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 2 — INV-4.1 (spec §4.8): beat-plan legality property test over the BeatSelector.
 *
 * Exhausts EVERY (ConceptType × phase{null + all 4 Phase} × isFirstEncounter{T,F} × reLesson{T,F})
 * tuple — 8 × 5 × 2 × 2 = 160 cases — and asserts:
 *   1. output is exactly one of the FOUR plans in the §4.5 closed table;
 *   2. first encounters receive only FULL or STANDARD (and FULL carries ④ NAME);
 *   3. reLesson && !first ⇒ RE_LESSON; mastered && !first && !reLesson ⇒ MASTERED_REVISIT;
 *   4. every plan's beats follow the fixed order PREDICT<ATTEMPT<REVEAL<NAME<CHECK (index-monotonic);
 *   5. STANDARD lacks NAME; the closed table is exactly the 4 plans (no 5th plan exists).
 *
 * The selector chooses compression, never new vocabulary (council-1781052957).
 */
class BeatSelectorTest {

    /** The fixed beat order (§4.5, never reordered). Index in this list = the beat's legal position. */
    private val fixedOrder = listOf(
        BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK,
    )

    private val phases: List<Phase?> = listOf(null) + Phase.entries

    @Test
    fun `every tuple yields one of the four closed-table plans`() {
        for (ct in ConceptType.entries) for (phase in phases)
            for (first in listOf(true, false)) for (re in listOf(true, false)) {
                val plan = BeatSelector.planFor(ct, phase, first, re)
                assertTrue(
                    plan in BeatPlan.entries,
                    "($ct, $phase, first=$first, re=$re) -> $plan must be a closed-table plan",
                )
            }
    }

    @Test
    fun `first encounters receive only FULL or STANDARD and FULL carries NAME`() {
        for (ct in ConceptType.entries) for (phase in phases)
            for (re in listOf(true, false)) {
                val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = true, reLesson = re)
                assertTrue(
                    plan == BeatPlan.FULL || plan == BeatPlan.STANDARD,
                    "first encounter ($ct, $phase, re=$re) -> $plan must be FULL or STANDARD",
                )
            }
        // first encounter ALWAYS carries ④ NAME (the selector returns FULL — never STANDARD —
        // for a first encounter per §0.9A; STANDARD's "minimum for first encounters" is the
        // schema floor, but the live selector promotes first contact to FULL).
        for (ct in ConceptType.entries) for (phase in phases) {
            val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = true, reLesson = false)
            assertEquals(BeatPlan.FULL, plan, "first contact ($ct, $phase) carries ④ NAME ⇒ FULL")
            assertTrue(BeatType.NAME in plan.beats, "$plan must contain NAME")
        }
    }

    @Test
    fun `reLesson on a non-first encounter yields RE_LESSON`() {
        for (ct in ConceptType.entries) for (phase in phases) {
            val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = false, reLesson = true)
            assertEquals(BeatPlan.RE_LESSON, plan, "reLesson+!first ($ct, $phase) -> RE_LESSON")
        }
    }

    @Test
    fun `mastered non-first non-reLesson yields MASTERED_REVISIT`() {
        for (ct in ConceptType.entries) {
            val plan = BeatSelector.planFor(
                ct, Phase.mastered, isFirstEncounter = false, reLesson = false,
            )
            assertEquals(BeatPlan.MASTERED_REVISIT, plan, "mastered+!first+!re ($ct) -> MASTERED_REVISIT")
        }
    }

    @Test
    fun `non-mastered non-first non-reLesson yields STANDARD`() {
        for (ct in ConceptType.entries)
            for (phase in listOf<Phase?>(null, Phase.intro, Phase.practice, Phase.retrieval)) {
                val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = false, reLesson = false)
                assertEquals(BeatPlan.STANDARD, plan, "non-mastered ($ct, $phase) -> STANDARD")
            }
    }

    @Test
    fun `every plan's beats follow the fixed order strictly increasing`() {
        for (plan in BeatPlan.entries) {
            val indices = plan.beats.map { fixedOrder.indexOf(it) }
            assertTrue(
                indices.all { it >= 0 },
                "$plan contains a beat outside the fixed vocabulary: ${plan.beats}",
            )
            assertTrue(
                indices.zipWithNext().all { (a, b) -> a < b },
                "$plan beats must be strictly increasing in the fixed order: $indices",
            )
        }
    }

    @Test
    fun `STANDARD lacks NAME and the closed table is exactly four plans`() {
        assertTrue(BeatType.NAME !in BeatPlan.STANDARD.beats, "STANDARD elides ④ NAME")
        assertEquals(
            setOf(BeatPlan.FULL, BeatPlan.STANDARD, BeatPlan.MASTERED_REVISIT, BeatPlan.RE_LESSON),
            BeatPlan.entries.toSet(),
            "the legal plan set is CLOSED at exactly four (no 5th plan may be added)",
        )
        assertEquals(4, BeatPlan.entries.size)
    }

    @Test
    fun `BeatType vocabulary is exactly the five fixed beats in order`() {
        assertEquals(
            listOf(
                BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK,
            ),
            BeatType.entries,
            "the beat vocabulary is FIXED at 5, never reordered (council-1781052957)",
        )
    }
}
```

- [ ] **Step 2: Run — expect RED** (compile failure: `BeatSelector`, `BeatPlan`, `BeatType` unresolved):

```powershell
gradle :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: BeatSelector` / `BeatPlan` / `BeatType`.

- [ ] **Step 3: Create the selector** — `src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt` (VERBATIM from plan core §0.9A — do not rename, reorder, or add a fifth plan):

```kotlin
package jarvis.tutor.lesson

import jarvis.content.ConceptType
import jarvis.tutor.Phase

/** Spec §4.5 — the CLOSED set of legal beat plans. No fifth plan may ever be added by a prompt. */
enum class BeatPlan(val beats: List<BeatType>) {
    FULL(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK)),
    STANDARD(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    MASTERED_REVISIT(listOf(BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    RE_LESSON(listOf(BeatType.REVEAL, BeatType.CHECK)),
}

/** Wire literals are lowercase names; beats are NEVER reordered (council-1781052957). */
enum class BeatType { PREDICT, ATTEMPT, REVEAL, NAME, CHECK }

/**
 * Spec §4.2 — concept_type x mastery phase -> plan, vocabulary FIXED. The selector chooses
 * compression, never new vocabulary. First encounters (no mastery row / observations == 0)
 * receive only FULL or STANDARD (INV-4.1). RE_LESSON only via the §7.3 forgetting trigger
 * (Plan 7) — reLesson=true is passed by NO Plan-3 call site.
 */
object BeatSelector {
    fun planFor(conceptType: ConceptType, phase: Phase?, isFirstEncounter: Boolean, reLesson: Boolean = false): BeatPlan = when {
        reLesson && !isFirstEncounter -> BeatPlan.RE_LESSON
        isFirstEncounter -> BeatPlan.FULL          // first contact always carries ④ NAME
        phase == Phase.mastered -> BeatPlan.MASTERED_REVISIT
        else -> BeatPlan.STANDARD
    }
}
```

- [ ] **Step 4: Run the property test — expect GREEN:**

```powershell
gradle :test --tests "jarvis.tutor.lesson.BeatSelectorTest"
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 5: Run the full backend suite** (never trust a partial run — review-workflow rule):

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit explicit paths only** (never `git add -A`):

```powershell
git add src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt src/test/kotlin/jarvis/tutor/lesson/BeatSelectorTest.kt
git commit -m "feat(lesson): BeatSelector + closed BeatPlan table + INV-4.1 property test"
```

---

## Task 5 — `dwell.ts` + INV-4.2 boundary tests

> **NOTE — appears out of numeric order on disk.** This section sits physically between Task 2 and Task 3 (disk order: 0, 1, 2, **5**, 3, 4, 6…), while the task-list table (top of plan) lists tasks 0–14 numerically. Task 5 has NO dependency — it is runnable any time after Task 0. Process tasks by NUMBER (the dependency notes assume numeric order: Task 6 needs Task 5 done first; Task 3 needs Tasks 1+2), not by document position. The disk order is cosmetic (PM-ruled) and dependency-safe either way; this note removes the table-vs-body discrepancy a fresh executor would otherwise hit.

Adds the reading-time dwell floor (spec §4.1) the BeatOrchestrator's Next gate waits on so a wall of Romanian text cannot be skipped in 200 ms. The formula `min(5500, max(1400, round(900 + words × 320)))` ms is copied VERBATIM from plan core §0.9E, which ports the demo constants at `build-review/viz-fork-demo/lectie.html:185-186` (`readMs=t=>{const w=(''+t).trim().split(/\s+/).filter(Boolean).length;return Math.min(5500,Math.max(1400,Math.round(900+w*320)));}`). Dwell is a FLOOR, never a ceiling — all reveals stay learner-paced via the scrubber (§4.6). The INV-4.2 boundary tests (spec §4.8) pin the floor at 1400, the cap at 5500, and the per-word slope at the documented word counts.

**Files:**
- Create: `tutor-web/src/lib/dwell.ts`
- Create: `tutor-web/src/lib/dwell.test.ts`

- [ ] **Step 1: Write the failing INV-4.2 boundary test.** Vitest co-located test (`*.test.ts` next to the source, the repo convention — e.g. `queueToday.test.ts`). Create `tutor-web/src/lib/dwell.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { readMs } from "./dwell";

/**
 * Plan-3 Task 5 — INV-4.2 (spec §4.8): the reading-time dwell FLOOR, ported verbatim from the demo
 * constants at build-review/viz-fork-demo/lectie.html:185-186. Formula:
 *   min(5500, max(1400, round(900 + words * 320)))
 * Boundaries from plan core §0.9E: 0 words -> 1400 (floor), 2 -> 1540, 14 -> 5380, 15 -> 5500 (cap),
 * 1000 -> 5500 (cap). Dwell is a FLOOR, never a ceiling (§4.1) — the scrubber stays learner-paced.
 */
describe("readMs (INV-4.2 dwell floor)", () => {
  it("0 words clamps to the 1400 ms floor", () => {
    expect(readMs("")).toBe(1400);
    expect(readMs("   ")).toBe(1400); // whitespace-only -> 0 words after filter(Boolean)
  });

  it("2 words -> 900 + 2*320 = 1540 ms (above the floor)", () => {
    expect(readMs("două cuvinte")).toBe(1540);
  });

  it("14 words -> 900 + 14*320 = 5380 ms (below the cap)", () => {
    const text = Array.from({ length: 14 }, (_, i) => `cuvânt${i}`).join(" ");
    expect(readMs(text)).toBe(5380);
  });

  it("15 words -> 900 + 15*320 = 5700 -> clamped to the 5500 ms cap", () => {
    const text = Array.from({ length: 15 }, (_, i) => `cuvânt${i}`).join(" ");
    expect(readMs(text)).toBe(5500);
  });

  it("1000 words clamps to the 5500 ms cap", () => {
    const text = Array.from({ length: 1000 }, (_, i) => `c${i}`).join(" ");
    expect(readMs(text)).toBe(5500);
  });

  it("collapses runs of whitespace to single word boundaries", () => {
    // two words separated by mixed whitespace -> still 2 words -> 1540
    expect(readMs("două \n\t  cuvinte")).toBe(1540);
  });
});
```

- [ ] **Step 2: Run — expect RED** (the module does not exist). From `tutor-web`:

```powershell
npx vitest run src/lib/dwell.test.ts
```

Expected: the run fails to resolve the import — `Failed to load url ./dwell` / `Cannot find module './dwell'`.

- [ ] **Step 3: Create the module** — `tutor-web/src/lib/dwell.ts` (VERBATIM from plan core §0.9E):

```ts
/** min(5500, max(1400, round(900 + words*320))) ms — a FLOOR, never a ceiling (spec §4.1). */
export function readMs(text: string): number {
  const w = text.trim().split(/\s+/).filter(Boolean).length;
  return Math.min(5500, Math.max(1400, Math.round(900 + w * 320)));
}
```

- [ ] **Step 4: Run the boundary test — expect GREEN.** From `tutor-web`:

```powershell
npx vitest run src/lib/dwell.test.ts
```

Expected: all 6 tests pass.

- [ ] **Step 5: Run the full frontend suite** (the new test joins the vitest include-glob; never trust a partial run — review-workflow rule). From `tutor-web`:

```powershell
npm test
```

Expected: vitest reports all suites pass (including `dwell.test.ts`).

- [ ] **Step 6: Commit explicit paths only** (never `git add -A`):

```powershell
git add tutor-web/src/lib/dwell.ts tutor-web/src/lib/dwell.test.ts
git commit -m "feat(lesson): dwell.ts reading-time floor + INV-4.2 boundary tests"
```

## Task 3 — `ApiLessonBeats` DTOs (§0.9B verbatim) + lesson GET beats guard/payload + lock §NEW-L + pin test amended (SAME commit)

Extends `GET /api/v1/lesson/{kcId}` ADDITIVELY: after the existing faithful + dispute gates, a **beats guard** (RO beats null OR not `isCompleteFor(conceptType)` ⇒ 404 `{"error":"beats not complete"}`; an unparseable `concept_type` ⇒ 404 `{"error":"concept_type invalid"}`). When the guard passes, assemble `ApiLessonBeats` from `BeatSelector.planFor(conceptType, resolvePhase(mastery), isFirstEncounter = mastery == null)` (Task 2) + the KC's `beats["ro"]` content, mapping ONLY the beats the served plan contains. `prediction_options` is populated from the predict beat's `options.map { it.text }`; `definition_ro` from the name beat's `definition` — **but ONLY when the served plan contains `name` AND the definition is non-blank** (STANDARD/MASTERED-REVISIT/RE-LESSON omit ④ ⇒ `definition_ro` stays `null`). The legality is what lock §NEW-L now says: `BeatPredict.options` IS the KC options source; `BeatName.definition` IS the dedicated definition field. **Lock amendment + `SignatureLockPinTest` update land in the SAME commit** (the lock's own DURABLE RULE, not a violation). `beats` is the 12th `ApiLessonReply` field, appended LAST.

This is a server-only task (DTOs + route extension + lock + pin test). The frontend `BeatOrchestrator` consumes this payload in Task 6 — Task 3 produces no UI.

**Files:**
- Modify: `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (new DTOs after `ApiLessonReply`; `beats` field on `ApiLessonReply`; beats guard + payload assembly in the GET handler)
- Modify: `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` (§NEW-L: `beats` field + populated-fields legality note)
- Modify: `src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt` (the `ApiLessonReply` pin → 12-field ordered list)
- Modify: `src/test/kotlin/jarvis/web/LessonServeRouteTest.kt` (new beats fixture + 5 new tests)

> Depends on Task 1 (`AttemptsTable` cols — not touched here but the schema must compile) and Task 2 (`BeatSelector`, `BeatPlan`, `BeatType` in `jarvis.tutor.lesson`). The §0.9B DTOs reference `ConceptType` (Plan 2, `jarvis.content`) and `Phase` (`jarvis.tutor`). Confirm both compile before starting (`gradle :compileKotlin`).

- [ ] **Step 1: Write the failing route tests.** Append a COMPLETE beats fixture + 5 tests to `src/test/kotlin/jarvis/web/LessonServeRouteTest.kt`. Add these imports at the top (alongside the existing ones):

```kotlin
import io.ktor.client.statement.HttpResponse
```

> **Confirm the `seedMastery` helper's symbols are imported.** `seedMastery` (below) references `jarvis.tutor.KcMasteryTable`, `jarvis.tutor.Phase`, `org.jetbrains.exposed.sql.insert`, and `java.time.Instant`. Confirm `LessonServeRouteTest.kt` already imports `jarvis.tutor.KcMasteryTable` and `java.time.Instant` (add them if absent — `KcMasteryTable.insert { … }` and `Instant.now()` will not compile otherwise). The robust check: run `gradle :compileTestKotlin` after pasting and add whatever symbol the compiler names unresolved (the plan applies this same compile-then-import discipline elsewhere).

Add this helper that writes a KC YAML with COMPLETE `ro` beats for a `definition-taxonomy` KC (the choice variant — NOT numerical, so `attempt.choices` is the required path), plus the existing `pa-kc-001` shape. Insert it after the existing `seedContent` function:

```kotlin
    /**
     * Plan-3 Task 3 — a PA KC with COMPLETE `ro` beats for the definition-taxonomy (choice) variant.
     * isCompleteFor(DEFINITION_TAXONOMY) needs: predict (3-4 options, ≥1 correct, callbacks non-blank),
     * attempt (statement + feedback_correct + choices each with non-blank feedback — the NON-numerical
     * branch), reveal (≥1 step, text+callout non-blank), check (item_stem + ≥1 correct choice). Beat ④
     * name is present so FIRST-encounter (FULL plan) can populate definition_ro.
     */
    private fun seedBeatsContent(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a well-ordered collection of unambiguous and effectively computable operations."
                page: 4
                provenance: located
            version: 1
            concept_type: definition-taxonomy
            explanation_ro: "Un algoritm este o colecție bine ordonată de operații."
            worked_example_ro: "Exemplu: pașii adunării a două numere."
            beats:
              ro:
                predict:
                  prompt: "Care dintre următoarele este un algoritm?"
                  options:
                    - text: "O rețetă de prăjitură cu pași clari"
                      callback: "Corect — pașii sunt neambigui și se termină."
                      correct: true
                    - text: "Instrucțiunea „fii fericit”"
                      callback: "Nu — nu este efectiv calculabilă."
                      correct: false
                    - text: "O buclă care nu se oprește niciodată"
                      callback: "Nu — un algoritm trebuie să se oprească în timp finit."
                      correct: false
                attempt:
                  statement: "Clasifică exemplul: este sau nu un algoritm?"
                  feedback_correct: "Da — îndeplinește toate cele patru proprietăți."
                  choices:
                    - text: "Este un algoritm"
                      correct: true
                      feedback: "Corect: neambiguu, finit, efectiv, produce rezultat."
                    - text: "Nu este un algoritm"
                      correct: false
                      feedback: "Reanalizează: pașii sunt neambigui și se opresc."
                reveal:
                  steps:
                    - text: "Un algoritm are operații neambigue."
                      callout: "Fiecare pas are un singur înțeles."
                    - text: "Un algoritm se oprește în timp finit."
                      callout: "Nu există bucle infinite."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile care se opresc în timp finit."
                  invariant_statement: "Orice algoritm se termină în timp finit."
                  why_matters: "Distinge un algoritm de o procedură care nu se termină."
                check:
                  item_stem: "Este „adună două numere” un algoritm?"
                  choices:
                    - text: "Da"
                      correct: true
                      feedback: "Corect — pași finiți, neambigui."
                    - text: "Nu"
                      correct: false
                      feedback: "Greșit — îndeplinește toate proprietățile."
            """.trimIndent(),
        )
    }

    /** A copy of seedBeatsContent with the `name:` beat block removed → beats INCOMPLETE only if a
     *  required beat is missing. Here all of ①②③⑤ stay; ④ stays optional so this is still COMPLETE.
     *  For the incomplete case we drop `check:` (a required beat) below. */
    private fun seedIncompleteBeatsContent(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            version: 1
            concept_type: definition-taxonomy
            beats:
              ro:
                predict:
                  prompt: "Care este un algoritm?"
                  options:
                    - text: "O rețetă"
                      callback: "Corect."
                      correct: true
                    - text: "„Fii fericit”"
                      callback: "Nu."
                      correct: false
                    - text: "Buclă infinită"
                      callback: "Nu."
                      correct: false
                attempt:
                  statement: "Clasifică."
                  feedback_correct: "Da."
                  choices:
                    - text: "Este"
                      correct: true
                      feedback: "Corect."
                reveal:
                  steps:
                    - text: "Pași neambigui."
                      callout: "Un singur înțeles."
            """.trimIndent(),
        )
    }
```

Now add the 5 tests (the existing `seedB8` + `seedUser` + `installRoutes` helpers are reused verbatim). Add a small mastery-seed helper near `openReport`:

```kotlin
    /** Seed a kc_mastery row at a given phase + observation count (drives the served plan). */
    private fun seedMastery(
        db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String,
        phase: jarvis.tutor.Phase, observations: Int,
    ) = transaction(db) {
        KcMasteryTable.insert {
            it[KcMasteryTable.userId] = userId
            it[KcMasteryTable.kcId] = kcId
            it[ewmaScore] = 0.5
            it[KcMasteryTable.observations] = observations
            it[lastGradedAt] = Instant.now()
            it[KcMasteryTable.phase] = phase.name
        }
    }
```

```kotlin
    @Test fun `complete beats serve a 200 with the beats payload and populated prediction_options`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        // beats payload present; concept_type echoed; plan carries the predict beat.
        assertTrue(body.contains("\"concept_type\":\"definition-taxonomy\""), body)
        assertTrue(body.contains("\"predict\""), body)
        // prediction_options populated from beats predict options (NOT empty list).
        assertTrue(body.contains("O rețetă de prăjitură cu pași clari"), "prediction_options populated: $body")
        assertFalse(body.contains("\"prediction_options\":[]"), "must not be the honest-empty list: $body")
    }

    @Test fun `incomplete beats are 404 beats-not-complete`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedIncompleteBeatsContent(content, "pa-kc-001")   // missing the required `check:` beat
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
        assertTrue(resp.bodyAsText().contains("beats not complete"), resp.bodyAsText())
    }

    @Test fun `non-faithful KC remains 404 even with complete beats`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `STANDARD plan (practice phase) omits name and leaves definition_ro null`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        // mastery row present + practice phase ⇒ NOT first encounter, NOT mastered ⇒ STANDARD (①②③⑤).
        seedMastery(db, uid, "pa-kc-001", jarvis.tutor.Phase.practice, observations = 2)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        // STANDARD has no ④ NAME beat in the plan ⇒ no name block + definition_ro null.
        assertTrue(body.contains("\"definition_ro\":null"), "STANDARD ⇒ definition_ro null: $body")
        assertFalse(body.contains("\"name\":{"), "STANDARD plan must not carry the name beat: $body")
    }

    @Test fun `first encounter serves FULL with the name beat and populated definition_ro`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        // NO mastery row ⇒ first encounter ⇒ FULL (①②③④⑤).
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"name\":{"), "FULL plan carries the name beat: $body")
        assertTrue(body.contains("se opresc în timp finit"), "definition_ro populated from beats name: $body")
        assertFalse(body.contains("\"definition_ro\":null"), "first encounter ⇒ definition_ro populated: $body")
    }
```

Add the missing imports at the top of the test file if not already present:

```kotlin
import kotlin.test.assertFalse
```

- [ ] **Step 2: Run — expect RED** (compile failure: `ApiLessonBeats`/`beats` field unresolved; populated-options assertion will fail once it compiles):

```powershell
gradle :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: ApiLessonBeats` (or, after a partial impl, the new tests RED on the assertions).

- [ ] **Step 3: Add the `ApiLessonBeats` DTO block (§0.9B VERBATIM).** In `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt`, insert immediately AFTER the `ApiLessonReply` data class (after its closing `)` at line 154), before `fun Route.installQueueMasteryCalibrationRoutes()`:

```kotlin
/**
 * Plan-3 §0.9B — the ADDITIVE beats payload on the lesson reply (spec §4). Present only when the KC's
 * `beats["ro"]` is structurally complete for its concept_type (KcBeats.isCompleteFor). `plan` is the
 * served BeatSelector plan as lowercase BeatType names, in order; a beat sub-object is present iff the
 * plan contains that beat. `correct`/`callback`/`feedback` flags ride to the client for the gate/echo
 * UX, but the SERVER is grading truth via POST /api/v1/lesson/{kcId}/beat — the reply is never trusted
 * for writes (Task 4).
 */
@Serializable
data class ApiLessonBeats(
    val plan: List<String>,                  // BeatType lowercase names, in served order
    val concept_type: String,                // the KC's wire literal
    val predict: ApiBeatPredict? = null,     // present iff plan contains "predict"
    val attempt: ApiBeatAttempt? = null,
    val reveal: ApiBeatReveal? = null,
    val name: ApiBeatName? = null,           // present iff plan contains "name"
    val check: ApiBeatCheck? = null,
)

@Serializable data class ApiPredictOption(val text: String, val callback: String, val correct: Boolean)
@Serializable data class ApiBeatPredict(val prompt: String, val options: List<ApiPredictOption>)
@Serializable data class ApiAttemptChoice(val text: String, val correct: Boolean, val feedback: String)
@Serializable data class ApiSkeletonRow(val label: String, val formula: String?, val is_decision_row: Boolean)
@Serializable data class ApiTraceStep(val row_index: Int, val value: String, val callout: String?)
@Serializable data class ApiBeatAttempt(
    val statement: String,
    val choices: List<ApiAttemptChoice> = emptyList(),
    val skeleton_rows: List<ApiSkeletonRow> = emptyList(),
    val trace_steps: List<ApiTraceStep> = emptyList(),
    val input_schema: String? = null,
    val feedback_correct: String,
)
@Serializable data class ApiRevealStep(val text: String, val callout: String)
@Serializable data class ApiFigureBinding(val family_id: String, val instance_id: String)
@Serializable data class ApiBeatReveal(val steps: List<ApiRevealStep>, val figure: ApiFigureBinding? = null)
@Serializable data class ApiBeatName(val definition: String, val invariant_statement: String, val why_matters: String)
@Serializable data class ApiBeatCheck(val item_stem: String, val choices: List<ApiAttemptChoice> = emptyList(), val numeric_answer: String? = null, val numeric_tolerance: Double? = null)
```

- [ ] **Step 4: Add the `beats` field to `ApiLessonReply` (12th, appended LAST).** In the same file, find the end of the `ApiLessonReply` data class:

Find:

```kotlin
    val worked_example_ro: String?,
    val provenance: jarvis.tutor.DrillProvenanceDto, // {authored, hasBeenFaithfulChecked=true}
)
```

Replace with:

```kotlin
    val worked_example_ro: String?,
    val provenance: jarvis.tutor.DrillProvenanceDto, // {authored, hasBeenFaithfulChecked=true}
    // ===== Plan 3 §0.9B (spec §4) — ADDITIVE 12th field, appended LAST; lock §NEW-L + pin amended SAME commit.
    // null ⇒ legacy payload (never served in practice post-Task 3: incomplete beats are 404). =====
    val beats: ApiLessonBeats? = null,
)
```

- [ ] **Step 5: Insert the beats guard + payload assembly in the GET handler.** In `get("/api/v1/lesson/{kcId}")`, the current handler ends with a single `call.respond(HttpStatusCode.OK, ApiLessonReply(...))`. Replace the existing terminal `call.respond(...)` block (lines 408-431, from `call.respond(` through the closing `)`) with the gate + assembly + a SINGLE respond. The faithful gate above (lines 399-406) is UNTOUCHED.

Find:

```kotlin
            call.respond(
                HttpStatusCode.OK,
                ApiLessonReply(
                    kcId = kc.id,
                    kc_name_ro = kc.name_ro,
                    kc_name_en = kc.name_en,
                    // stem_template → concrete question stem (null if not authored yet)
                    concrete_question_ro = kc.stem_template?.takeIf { it.isNotBlank() },
                    // First source quote → echo anchor (null if no source refs)
                    echo_source_ro = kc.source.firstOrNull()?.quote?.takeIf { it.isNotBlank() },
                    // Honest degraded: no option source on KC yet — empty list, DO NOT fabricate
                    prediction_options = emptyList(),
                    // term = name_ro (primary Romanian label)
                    term_ro = kc.name_ro,
                    // definition_ro: no dedicated KC field exists today. Honest-null rather than
                    // duplicating explanation_ro (trust-first: never imply a definition was authored
                    // when none was). Field kept nullable for forward-compat if a source is added.
                    definition_ro = null,
                    explanation_ro = kc.explanation_ro?.takeIf { it.isNotBlank() },
                    worked_example_ro = kc.worked_example_ro?.takeIf { it.isNotBlank() },
                    // Served only behind the faithful gate ⇒ honest authored+checked provenance.
                    provenance = jarvis.tutor.DrillProvenanceDto(type = "authored", hasBeenFaithfulChecked = true),
                ),
            )
```

Replace with:

```kotlin
            // ── Plan-3 beats guard (spec §4.1/§4.5) — INLINE after the faithful gate, NOT a gate-signature
            //    change (§I.2 RF2 FROZEN). concept_type must parse; beats["ro"] must be structurally
            //    complete for that type (KcBeats.isCompleteFor). Either failing ⇒ 404 (OMIT, fail-loud).
            val conceptType = kc.concept_type?.let { jarvis.content.ConceptType.fromWire(it) }
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"concept_type invalid"}"""); return@requireUser }
            val roBeats = kc.beats["ro"]
            if (roBeats == null || !roBeats.isCompleteFor(conceptType)) {
                call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@requireUser
            }

            // The served plan: BeatSelector chooses compression from concept_type + mastery (Task 2).
            // No mastery row ⇒ first encounter ⇒ FULL/STANDARD only (INV-4.1). reLesson is never passed.
            val mastery = readMastery(db, userId, kc.id)
            val plan = jarvis.tutor.lesson.BeatSelector.planFor(
                conceptType = conceptType,
                phase = resolvePhase(mastery),
                isFirstEncounter = mastery == null,
            )
            val planBeats = plan.beats.map { it.name.lowercase() }  // ["predict","attempt",...]
            val carriesName = jarvis.tutor.lesson.BeatType.NAME in plan.beats

            // Map ONLY the beats the served plan contains (a compressed plan omits ① and/or ④).
            val apiBeats = ApiLessonBeats(
                plan = planBeats,
                concept_type = jarvis.content.ConceptType.wireOf(conceptType),
                predict = if (jarvis.tutor.lesson.BeatType.PREDICT in plan.beats) roBeats.predict?.let { p ->
                    ApiBeatPredict(
                        prompt = p.prompt,
                        options = p.options.map { ApiPredictOption(it.text, it.callback, it.correct) },
                    )
                } else null,
                attempt = if (jarvis.tutor.lesson.BeatType.ATTEMPT in plan.beats) roBeats.attempt?.let { a ->
                    ApiBeatAttempt(
                        statement = a.statement,
                        choices = a.choices.map { ApiAttemptChoice(it.text, it.correct, it.feedback) },
                        skeleton_rows = a.skeleton_rows.map { ApiSkeletonRow(it.label, it.formula, it.is_decision_row) },
                        trace_steps = a.trace_steps.map { ApiTraceStep(it.row_index, it.value, it.callout) },
                        input_schema = a.input_schema,
                        feedback_correct = a.feedback_correct,
                    )
                } else null,
                reveal = if (jarvis.tutor.lesson.BeatType.REVEAL in plan.beats) roBeats.reveal?.let { r ->
                    ApiBeatReveal(
                        steps = r.steps.map { ApiRevealStep(it.text, it.callout) },
                        figure = r.figure?.let { ApiFigureBinding(it.family_id, it.instance_id) },
                    )
                } else null,
                name = if (carriesName) roBeats.name?.let { n ->
                    ApiBeatName(n.definition, n.invariant_statement, n.why_matters)
                } else null,
                check = if (jarvis.tutor.lesson.BeatType.CHECK in plan.beats) roBeats.check?.let { c ->
                    ApiBeatCheck(
                        item_stem = c.item_stem,
                        choices = c.choices.map { ApiAttemptChoice(it.text, it.correct, it.feedback) },
                        numeric_answer = c.numeric_answer,
                        numeric_tolerance = c.numeric_tolerance,
                    )
                } else null,
            )

            call.respond(
                HttpStatusCode.OK,
                ApiLessonReply(
                    kcId = kc.id,
                    kc_name_ro = kc.name_ro,
                    kc_name_en = kc.name_en,
                    // stem_template → concrete question stem (null if not authored yet)
                    concrete_question_ro = kc.stem_template?.takeIf { it.isNotBlank() },
                    // First source quote → echo anchor (null if no source refs)
                    echo_source_ro = kc.source.firstOrNull()?.quote?.takeIf { it.isNotBlank() },
                    // Plan-3 (lock §NEW-L amended): BeatPredict.options IS the KC options source. Populated
                    // ONLY when the served plan carries ① PREDICT; a plan without it keeps the honest-empty
                    // list (no fabrication). beats complete is already proven by the guard above.
                    prediction_options = apiBeats.predict?.options?.map { it.text } ?: emptyList(),
                    // term = name_ro (primary Romanian label)
                    term_ro = kc.name_ro,
                    // Plan-3 (lock §NEW-L amended): BeatName.definition IS the dedicated definition field.
                    // Populated ONLY when the served plan carries ④ NAME and the definition is non-blank —
                    // STANDARD/MASTERED-REVISIT/RE-LESSON omit ④, so definition_ro stays null (the served
                    // plan carries no ④; still NOT a duplicate of explanation_ro, trust-first).
                    definition_ro = apiBeats.name?.definition?.takeIf { it.isNotBlank() },
                    explanation_ro = kc.explanation_ro?.takeIf { it.isNotBlank() },
                    worked_example_ro = kc.worked_example_ro?.takeIf { it.isNotBlank() },
                    // Served only behind the faithful gate ⇒ honest authored+checked provenance.
                    provenance = jarvis.tutor.DrillProvenanceDto(type = "authored", hasBeenFaithfulChecked = true),
                    beats = apiBeats,
                ),
            )
```

> Note: the handler's `requireUser { _ ->` binds the user id to `_` today. The beats-plan read needs the real `userId` (for `readMastery`). Change the lambda header from `requireUser { _ ->` to `requireUser { userId ->` in this one handler (line 387). `userId` is now USED (no underscore-dead-prop smell).

Find (anchored on the lesson GET opening line so the match is UNIQUE — `requireUser { _ ->` is NON-unique: it also appears in the `/teaching/{kcId}` handler at line 345; editing the wrong one would bind `userId` unused in /teaching AND leave the lesson handler on `_`, so `readMastery(db, userId, kc.id)` would not compile). `get("/api/v1/lesson/{kcId}")` is the only such route in the file, so the two-line anchor is unique:

```kotlin
    get("/api/v1/lesson/{kcId}") {
        requireUser { _ ->
```

Replace:

```kotlin
    get("/api/v1/lesson/{kcId}") {
        requireUser { userId ->
```

- [ ] **Step 6: Amend lock §NEW-L IN THE SAME COMMIT (DURABLE RULE).** In `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, update the §NEW-L reply shape and field-mapping notes.

6a. In the `ApiLessonReply` code block under §NEW-L, find:

```kotlin
    val worked_example_ro: String?,        // KnowledgeConcept.worked_example_ro
    val provenance: DrillProvenanceDto,    // always {type="authored", hasBeenFaithfulChecked=true}
)
```

Replace with:

```kotlin
    val worked_example_ro: String?,        // KnowledgeConcept.worked_example_ro
    val provenance: DrillProvenanceDto,    // always {type="authored", hasBeenFaithfulChecked=true}
    val beats: ApiLessonBeats? = null,     // Plan-3 ADDITIVE (spec §4): the served beat plan + content; null ⇒ legacy
)
```

6b. Replace the two stale field-mapping bullets. Find:

```
- `definition_ro` is ALWAYS `null` today — `KnowledgeConcept` has no dedicated `definition` field, and we do NOT duplicate `explanation_ro` into it (trust-first: never imply an authored definition that doesn't exist). Field kept nullable for forward-compat if a definition source is added.
- `prediction_options` is always `emptyList()` — honest-degraded; DO NOT fabricate. Future: populate from a KC options source when one exists.
```

Replace with:

```
- **Plan-3 amendment (2026-06-12):** `prediction_options` is populated from the served beats predict beat — `beats["ro"].predict.options[*].text`. `BeatPredict.options` IS the KC options source the original note anticipated ("populate from a KC options source when one exists"). Populated ONLY when the served BeatSelector plan carries ① PREDICT; a plan without ① keeps `emptyList()` (no fabrication).
- **Plan-3 amendment (2026-06-12):** `definition_ro` is populated from `beats["ro"].name.definition` when the served plan carries ④ NAME and the definition is non-blank. `BeatName.definition` IS a dedicated definition field distinct from `explanation_ro` — populating it is honest, not a duplicate. STANDARD / MASTERED-REVISIT / RE-LESSON omit ④, so `definition_ro` stays `null` for those plans (the served plan carries no ④).
- The 12th field `beats` carries the BeatSelector-chosen plan + the beat content; present only when `beats["ro"].isCompleteFor(concept_type)` (else the route is 404 `beats not complete`). The pre-beats honest-degraded shape (`prediction_options=[]`, `definition_ro=null`, `beats=null`) is still the legal legacy payload for any KC without complete beats — but in practice every served KC has complete beats post-Task 3 (incomplete ⇒ 404).
```

- [ ] **Step 7: Amend the `SignatureLockPinTest` to the 12-field ordered list IN THE SAME COMMIT.** In `src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt`, find:

```kotlin
    /** lock §NEW-L — the lesson reply field names IN ORDER (kotlinx-serialization descriptor). */
    @Test
    fun `ApiLessonReply wire field names match the NEW-L list in order`() {
        assertEquals(
            listOf(
                "kcId", "kc_name_ro", "kc_name_en", "concrete_question_ro", "echo_source_ro",
                "prediction_options", "term_ro", "definition_ro", "explanation_ro",
                "worked_example_ro", "provenance",
            ),
            wireFieldNames<ApiLessonReply>(),
        )
    }
```

Replace with:

```kotlin
    /** lock §NEW-L (Plan-3 amended) — the lesson reply field names IN ORDER; `beats` appended 12th. */
    @Test
    fun `ApiLessonReply wire field names match the NEW-L list in order`() {
        assertEquals(
            listOf(
                "kcId", "kc_name_ro", "kc_name_en", "concrete_question_ro", "echo_source_ro",
                "prediction_options", "term_ro", "definition_ro", "explanation_ro",
                "worked_example_ro", "provenance", "beats",
            ),
            wireFieldNames<ApiLessonReply>(),
        )
    }
```

> Confirm the test imports `jarvis.web.ApiLessonReply` (it already does — the existing test references it). The new DTOs need no import addition there.

- [ ] **Step 8: Run the route + pin tests — expect GREEN:**

```powershell
gradle :test --tests "jarvis.web.LessonServeRouteTest" --tests "jarvis.tutor.SignatureLockPinTest"
```

Expected: `BUILD SUCCESSFUL` — all LessonServeRouteTest tests (the 5 original gate tests + the 5 new beats tests) and the amended `ApiLessonReply` pin pass.

- [ ] **Step 9: Run the full backend suite** (the lesson route is on the corpus-load + serve path; never trust a partial run — review-workflow rule):

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`. **STOP and report if not green.**

- [ ] **Step 10: Commit (explicit paths ONLY — never `-A`; lock + pin in the SAME commit as the code).**

```powershell
git add src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt docs/superpowers/plans/2026-06-02-interface-signatures-lock.md src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt src/test/kotlin/jarvis/web/LessonServeRouteTest.kt
git commit -m "feat(lesson): serve beats payload on GET /lesson + amend lock §NEW-L (Plan 3 Task 3)

ADDITIVE 12th ApiLessonReply field `beats`: BeatSelector plan + per-beat
content, served only when beats[\"ro\"].isCompleteFor(concept_type) (else
404 beats-not-complete). prediction_options now populated from the predict
beat's options; definition_ro from the name beat (FULL plan only). Lock
§NEW-L + SignatureLockPinTest amended in this commit (DURABLE RULE).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 4 — `POST /api/v1/lesson/{kcId}/beat`: server grading + attempt/completion writes (§0.9C VERBATIM)

Adds the per-beat grade endpoint. Same faithful + dispute + beats-complete gate as the GET. The server grades each gated beat from the KC's OWN beats data (choice → served option `correct` flag; numeric → tolerance compare), and **every graded beat writes ONE `attempts` row** carrying `beat_type` (lowercase literal), `prediction` (the predict beat's chosen text), and `first_encounter` (computed BEFORE any write). On `beat_type=="check"` the server ALSO runs the completion writes — `recordIn(score)` (EWMA + phase) + `upsertRubricCriterion` FSRS seed — in ONE atomic txn mirroring `TutorRoutes.kt:2222-2256`, and replies `lesson_complete=true` with the post-write `phase` + `verification_status`. A `beat_type` outside `predict|attempt|check` ⇒ 400.

The new `attempts` columns (`beat_type`, `prediction`, `first_encounter`) are added in Task 1 (§0.9D); this task USES them. The grade endpoint is POST + CSRF-gated (mirror `post("/api/v1/session/close")`).

**Files:**
- Modify: `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (request/reply DTOs + the new POST route inside `installQueueMasteryCalibrationRoutes`)
- Test: `src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt` (new)

> Depends on Task 1 (`AttemptsTable.beatType / prediction / firstEncounter`), Task 2 (`BeatSelector`), Task 3 (the gate + beats DTOs + `readMastery`/`resolvePhase` helpers already in this file). Confirm `gradle :compileKotlin` is green before starting.

- [ ] **Step 1: Write the failing route tests.** Create `src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt`. It reuses the same fixtures + gate seeds as `LessonServeRouteTest` (copy the `seedBeatsContent` choice-variant KC + a numeric-variant KC for the numeric-tolerance tests).

```kotlin
package jarvis.web

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.AttemptsTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import jarvis.tutor.VerificationAuditTable
import jarvis.tutor.VerificationStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan-3 Task 4 — POST /api/v1/lesson/{kcId}/beat. Server-side grading + attempt/completion writes.
 *
 * Class-killers:
 *  - every graded beat (predict/attempt/check) writes ONE attempts row with the right beat_type;
 *  - predict stores prediction_text on the row;
 *  - check flips mastery (recordIn → kc_mastery row appears w/ observations 1) + seeds the FSRS card;
 *  - first_encounter is true exactly once across two lesson runs;
 *  - a wrong choice grades incorrect with the served wrong-path feedback;
 *  - numeric tolerance pass/fail;
 *  - non-faithful ⇒ 404; incomplete beats ⇒ 404; bad beat_type ⇒ 400.
 */
class LessonBeatGradeRouteTest {

    @AfterTest fun reset() { System.clearProperty("JARVIS_CONTENT_DIR") }

    private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installTutorRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, FsrsCardsTable, KcMasteryTable,
                AttemptsTable, ReportWrongTable, KcVerificationStatusTable, VerificationAuditTable,
            )
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    private fun seedB8(db: org.jetbrains.exposed.sql.Database, content: Path, kcId: String, status: VerificationStatus) =
        transaction(db) {
            val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == kcId }
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status.name
                it[contentHash] = if (status == VerificationStatus.faithful)
                    jarvis.content.ContentReconcile.kcContentHash(kc) else null
                it[sourceSpanHash] = if (status == VerificationStatus.faithful) "seedspanhash" else null
                it[updatedAt] = Instant.now()
            }
        }

    /** definition-taxonomy KC with complete choice-variant beats (option[0] correct in every choice set). */
    private fun seedChoiceKc(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            version: 1
            concept_type: definition-taxonomy
            stem_template: "Este X un algoritm?"
            beats:
              ro:
                predict:
                  prompt: "Care este un algoritm?"
                  options:
                    - text: "O rețetă clară"
                      callback: "Corect — pași neambigui, finiți."
                      correct: true
                    - text: "„Fii fericit”"
                      callback: "Nu — nu este efectiv calculabilă."
                      correct: false
                    - text: "Buclă infinită"
                      callback: "Nu — nu se termină."
                      correct: false
                attempt:
                  statement: "Clasifică exemplul."
                  feedback_correct: "Da — îndeplinește toate proprietățile."
                  choices:
                    - text: "Este un algoritm"
                      correct: true
                      feedback: "Corect: neambiguu, finit."
                    - text: "Nu este un algoritm"
                      correct: false
                      feedback: "Greșit — reanalizează proprietățile."
                reveal:
                  steps:
                    - text: "Operații neambigue."
                      callout: "Un singur înțeles per pas."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații."
                  invariant_statement: "Se termină în timp finit."
                  why_matters: "Distinge un algoritm de o procedură infinită."
                check:
                  item_stem: "Este „adună două numere” un algoritm?"
                  choices:
                    - text: "Da"
                      correct: true
                      feedback: "Corect."
                    - text: "Nu"
                      correct: false
                      feedback: "Greșit."
            """.trimIndent(),
        )
    }

    /** formula-application KC with complete numeric-variant beats (skeleton + trace + numeric check). */
    private fun seedNumericKc(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Costul de timp"
            name_en: "Time cost"
            cluster: "Eficiența algoritmilor"
            bloom_level: apply
            difficulty: 3
            time_minutes: 30
            exam_weight: 0.13
            tier: 3
            version: 1
            concept_type: formula-application
            beats:
              ro:
                predict:
                  prompt: "Care este costul total?"
                  options:
                    - text: "3*t"
                      callback: "Corect — trei operații de cost t."
                      correct: true
                    - text: "t"
                      callback: "Nu — sunt trei operații."
                      correct: false
                    - text: "0"
                      callback: "Nu — fiecare operație costă t."
                      correct: false
                attempt:
                  statement: "Calculează costul însumând rândurile."
                  feedback_correct: "Corect — 3*t."
                  input_schema: "{\"type\":\"number\"}"
                  skeleton_rows:
                    - label: "op 1"
                      formula: "t"
                      is_decision_row: false
                    - label: "op 2"
                      formula: "t"
                      is_decision_row: false
                    - label: "total"
                      formula: "3*t"
                      is_decision_row: true
                  trace_steps:
                    - row_index: 0
                      value: "t"
                      callout: "Prima operație."
                    - row_index: 2
                      value: "3*t"
                      callout: "Suma."
                reveal:
                  steps:
                    - text: "Fiecare operație costă t."
                      callout: "Trei operații → 3*t."
                name:
                  definition: "Costul de timp este suma costurilor operațiilor."
                  invariant_statement: "t + t + t = 3*t."
                  why_matters: "Modelează eficiența."
                check:
                  item_stem: "Dacă t=2, care este costul total?"
                  numeric_answer: "6"
                  numeric_tolerance: 0.01
            """.trimIndent(),
        )
    }

    private fun csrf() = arrayOf("Cookie", "jarvis_session=", "X-CSRF-Token", "c")

    private fun attemptsFor(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String) =
        transaction(db) {
            AttemptsTable.selectAll()
                .where { (AttemptsTable.userId eq userId) and (AttemptsTable.kcId eq kcId) }
                .map { row ->
                    Triple(
                        row[AttemptsTable.beatType],
                        row[AttemptsTable.prediction],
                        row[AttemptsTable.firstEncounter],
                    )
                }
        }

    @Test fun `predict beat writes a row with beat_type predict and the prediction text`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"predict","selected_index":0,"prediction_text":"O rețetă clară"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("\"correct\":true"), r.bodyAsText())
        val rows = attemptsFor(db, uid, "pa-kc-001")
        assertEquals(1, rows.size)
        assertEquals("predict", rows.single().first)
        assertEquals("O rețetă clară", rows.single().second, "prediction text stored on the row")
    }

    @Test fun `attempt beat writes a row with beat_type attempt`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"attempt","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("\"correct\":true"), r.bodyAsText())
        assertEquals("attempt", attemptsFor(db, uid, "pa-kc-001").single().first)
    }

    @Test fun `check beat flips mastery, seeds the FSRS card, and completes the lesson`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val body = r.bodyAsText()
        assertTrue(body.contains("\"lesson_complete\":true"), body)
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("\"phase\":"), body)
        // recordIn ran: a kc_mastery row exists with observations 1.
        val obs = transaction(db) {
            KcMasteryTable.selectAll()
                .where { (KcMasteryTable.userId eq uid) and (KcMasteryTable.kcId eq "pa-kc-001") }
                .map { it[KcMasteryTable.observations] }.singleOrNull()
        }
        assertEquals(1, obs, "recordIn flipped mastery into existence")
        // FSRS card seeded.
        val cards = transaction(db) {
            FsrsCardsTable.selectAll()
                .where { (FsrsCardsTable.userId eq uid) and (FsrsCardsTable.kcId eq "pa-kc-001") }
                .count()
        }
        assertTrue(cards >= 1, "upsertRubricCriterion seeded an FSRS card")
    }

    @Test fun `first_encounter is true exactly once across two lesson runs`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        // Run 1: predict then check (first contact).
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"predict","selected_index":0,"prediction_text":"O rețetă clară"}""")
        }
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        // Run 2: another check (no longer first contact).
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        val firstEncounterTrue = attemptsFor(db, uid, "pa-kc-001").count { it.third == true }
        assertEquals(1, firstEncounterTrue, "first_encounter set on exactly one attempt row")
    }

    @Test fun `a wrong choice grades incorrect with the served wrong-path feedback`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"attempt","selected_index":1}""")   // the wrong choice
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val body = r.bodyAsText()
        assertTrue(body.contains("\"correct\":false"), body)
        assertTrue(body.contains("reanalizează"), "served wrong-path feedback echoed (teaches, not just 'incorrect'): $body")
    }

    @Test fun `numeric check within tolerance grades correct, outside tolerance grades incorrect`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedNumericKc(content, "pa-kc-006")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-006", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val pass = client.post("/api/v1/lesson/pa-kc-006/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","free_input":"6.001"}""")   // within 0.01 of 6
        }
        assertEquals(HttpStatusCode.OK, pass.status, pass.bodyAsText())
        assertTrue(pass.bodyAsText().contains("\"correct\":true"), pass.bodyAsText())

        val fail = client.post("/api/v1/lesson/pa-kc-006/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","free_input":"7"}""")        // outside tolerance
        }
        assertEquals(HttpStatusCode.OK, fail.status, fail.bodyAsText())
        assertTrue(fail.bodyAsText().contains("\"correct\":false"), fail.bodyAsText())
    }

    @Test fun `non-faithful KC is 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
    }

    @Test fun `incomplete beats are 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        // Write a KC with concept_type but NO beats key (incomplete).
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"PA\"\n    name_en: \"PA\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"x\"\nname_en: \"x\"\ncluster: c\nbloom_level: understand\n" +
                "difficulty: 1\ntime_minutes: 10\nexam_weight: 0.0\ntier: 1\nversion: 1\nconcept_type: definition-taxonomy\n",
        )
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("beats not complete"), r.bodyAsText())
    }

    @Test fun `a beat_type outside the gated set is 400`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"reveal","selected_index":0}""")   // reveal is not gradable
        }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
    }
}
```

- [ ] **Step 2: Run — expect RED** (route 404/405 — the POST does not exist yet, so all OK-expecting tests fail):

```powershell
gradle :test --tests "jarvis.web.LessonBeatGradeRouteTest"
```

Expected: failures (the route is absent; Ktor returns 404/405 for the unknown POST).

- [ ] **Step 3: Add the request/reply DTOs (§0.9C VERBATIM).** In `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt`, insert immediately AFTER the `ApiBeatCheck` line added in Task 3 (the last of the `ApiLessonBeats` sub-DTOs), before `fun Route.installQueueMasteryCalibrationRoutes()`:

```kotlin
/**
 * Plan-3 §0.9C — the per-beat grade request. Sent for the gated beats with learner input
 * (predict / attempt / check). The server grades from the KC's OWN beats data; the client reply
 * is never trusted for writes.
 */
@Serializable
data class ApiBeatGradeRequest(
    val beat_type: String,                // "predict" | "attempt" | "check"
    val selected_index: Int? = null,      // choice beats: index into the served options/choices
    val free_input: String? = null,       // numerical attempt / numeric check
    val prediction_text: String? = null,  // predict beats: the chosen option text (stored on the attempt row)
)

@Serializable
data class ApiBeatGradeReply(
    val correct: Boolean,
    val score: Double,                                // predict/attempt: 1.0/0.0 informational; check: feeds EWMA
    val feedback_ro: String,                          // both-path feedback / option callback (RO)
    val beat_type: String,
    val lesson_complete: Boolean,                     // true on the graded CHECK
    val first_encounter: Boolean,
    val phase: jarvis.tutor.Phase? = null,            // post-write phase (CHECK only)
    val verification_status: jarvis.tutor.VerificationStatus? = null,
)

/** Local JSON for beat-grade request decode (matches the server CN config: ignore unknown, encode defaults). */
private val beatGradeJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

- [ ] **Step 4: Add the POST route.** Inside `fun Route.installQueueMasteryCalibrationRoutes()`, append a new route AFTER the `get("/api/v1/lesson/{kcId}")` handler (after its closing `}`), before the closing `}` of the function:

```kotlin
    // ── POST /api/v1/lesson/{kcId}/beat ───────────────────────────────────────────────────────────
    // Server-side beat grading + the §4.4 completion-writes contract (spec §4.4). Same faithful + dispute
    // + beats-complete gate as the GET. EVERY graded beat writes ONE attempts row (beat_type, prediction,
    // first_encounter). On beat_type=="check": same-txn recordIn (EWMA+phase) + upsertRubricCriterion FSRS
    // seed, mirroring TutorRoutes.kt:2222-2256, and lesson_complete=true. CSRF-gated (POST).
    post("/api/v1/lesson/{kcId}/beat") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"kcId required"}"""); return@csrfProtect }
                val req = try {
                    beatGradeJson.decodeFromString(ApiBeatGradeRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, """{"error":"bad body"}"""); return@csrfProtect
                }

                // beat_type must be one of the three gradable beats; anything else (reveal/name/garbage) ⇒ 400.
                val beatType = req.beat_type.lowercase()
                if (beatType !in setOf("predict", "attempt", "check")) {
                    call.respond(HttpStatusCode.BadRequest, """{"error":"beat_type not gradable"}"""); return@csrfProtect
                }

                val repo = ContentRepo(groupThreeContentDir())
                val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"unknown kc"}"""); return@csrfProtect }

                // Identical faithful gate as the GET.
                val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
                if (resolved != VerificationStatus.faithful) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"not faithful"}"""); return@csrfProtect
                }
                if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"disputed"}"""); return@csrfProtect
                }

                // Identical beats guard as the GET (concept_type parse + structural completeness).
                val conceptType = kc.concept_type?.let { jarvis.content.ConceptType.fromWire(it) }
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"concept_type invalid"}"""); return@csrfProtect }
                val roBeats = kc.beats["ro"]
                if (roBeats == null || !roBeats.isCompleteFor(conceptType)) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect
                }

                // ── Grade from the KC's OWN beats data. correct flag + RO feedback.
                var correct = false
                var feedbackRo = ""
                when (beatType) {
                    "predict" -> {
                        val opts = roBeats.predict?.options ?: emptyList()
                        val sel = req.selected_index?.takeIf { it in opts.indices }
                            ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                        correct = opts[sel].correct
                        feedbackRo = opts[sel].callback   // the option callback IS the both-path feedback (§3.2 ①)
                    }
                    "attempt" -> {
                        val a = roBeats.attempt
                            ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect }
                        if (a.choices.isNotEmpty()) {
                            val sel = req.selected_index?.takeIf { it in a.choices.indices }
                                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                            correct = a.choices[sel].correct
                            feedbackRo = if (correct) a.feedback_correct else a.choices[sel].feedback
                        } else {
                            // Numeric attempt variant: compare free_input to the trace's final value.
                            // DELIBERATE PLACEHOLDER (consistency#5 — PM-accepted mechanism, NOT served in
                            // Plan 3). The 4 faithful KCs Plan 3 serves (pa-kc-001..004) are ALL choice-variant
                            // (a.choices non-empty), so this branch is DEAD on the served corpus. trace_steps[].value
                            // is INSTANCE/teaching data (e.g. a formula string like "3*t"), NOT the numeric a learner
                            // would type, and the last trace step is not guaranteed to be the answer — so exact-string
                            // match here would essentially always grade a real numeric answer incorrect. It exists only
                            // so the code compiles for FORMULA_APPLICATION/PROCEDURE/PROBABILISTIC numerical variants.
                            // CARRIED FOLLOW-UP: when a numerical-variant KC is actually served, grade numeric ATTEMPT
                            // against a real expected value (add an attempt.numeric_answer + tolerance, mirror the
                            // numeric CHECK path below) and add a LessonBeatGradeRouteTest case pinning the match.
                            val expected = a.trace_steps.lastOrNull()?.value
                            val got = req.free_input?.trim()
                            correct = expected != null && got != null && got == expected
                            feedbackRo = if (correct) a.feedback_correct else "Reanalizează pașii din schelet."
                        }
                    }
                    "check" -> {
                        val c = roBeats.check
                            ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect }
                        if (c.choices.isNotEmpty()) {
                            val sel = req.selected_index?.takeIf { it in c.choices.indices }
                                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                            correct = c.choices[sel].correct
                            feedbackRo = c.choices[sel].feedback
                        } else {
                            // Numeric check: parse free_input as double, compare to numeric_answer within tolerance.
                            val answer = c.numeric_answer?.toDoubleOrNull()
                            val tol = c.numeric_tolerance ?: 0.0
                            val got = req.free_input?.trim()?.toDoubleOrNull()
                            correct = answer != null && got != null && kotlin.math.abs(got - answer) <= tol
                            feedbackRo = if (correct) "Corect." else "Reanalizează: răspunsul numeric nu se potrivește."
                        }
                    }
                }
                val score = if (correct) 1.0 else 0.0

                // ── first_encounter: computed BEFORE any write (§0.9C). True iff no mastery row AND zero prior
                //    lesson attempt rows for (user, kc). taskId="lesson" marks an attempt as lesson-originated.
                val firstEncounter = transaction(db) {
                    val noMastery = KcMasteryTable.selectAll()
                        .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kc.id) }
                        .empty()
                    val noPriorLessonAttempts = AttemptsTable.selectAll()
                        .where {
                            (AttemptsTable.userId eq userId) and
                                (AttemptsTable.kcId eq kc.id) and
                                (AttemptsTable.taskId eq "lesson")
                        }
                        .empty()
                    noMastery && noPriorLessonAttempts
                }

                val now = Instant.now()
                var postPhase: Phase? = null
                if (beatType == "check") {
                    // ── CHECK: completion writes in ONE atomic txn (mirror TutorRoutes.kt:2222-2256).
                    //    recordIn (EWMA + phase) → attempts row → upsertRubricCriterion FSRS seed.
                    val masteryRepo = jarvis.tutor.KcMasteryRepo(db)
                    val cardRepo = jarvis.tutor.FsrsCardRepo(db)
                    val initial = jarvis.Fsrs.initial(if (correct) 3 else 2)
                    // front derivation mirrors TutorRoutes:2218 (server problem statement → fallback). The lesson
                    // has no server problem; use the KC stem_template, else name_en (a stable rubric front).
                    val front = kc.stem_template?.takeIf { it.isNotBlank() } ?: kc.name_en
                    val back = roBeats.name?.definition?.takeIf { it.isNotBlank() } ?: kc.explanation_ro ?: ""
                    try {
                        transaction(db) {
                            val m = masteryRepo.recordIn(this, userId, kc.id, score, now)
                            postPhase = m.phase
                            AttemptsTable.insert {
                                it[id] = jarvis.tutor.TutorTypes.ulid()
                                it[AttemptsTable.userId] = userId
                                it[AttemptsTable.kcId] = kc.id
                                it[taskId] = "lesson"
                                it[problemId] = kc.id + ":" + beatType
                                it[phase] = m.phase?.name ?: Phase.intro.name
                                it[correct] = correct
                                it[score] = score
                                it[scaffoldLevel] = 0
                                it[AttemptsTable.recorded] = true
                                it[gradedAt] = now
                                it[beatType] = beatType
                                it[prediction] = req.prediction_text
                                it[firstEncounter] = firstEncounter
                            }
                            cardRepo.upsertRubricCriterion(
                                this, userId, kc.id, front, back,
                                jarvis.tutor.FsrsState(
                                    difficulty = initial.difficulty,
                                    stability = initial.stability,
                                    retrievability = 1.0,
                                    dueAt = now.plus(java.time.Duration.ofDays(1)),
                                    lastReviewedAt = now,
                                    lapses = 0,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        System.err.println("[lesson-beat] atomic completion txn FAILED (rolled back) for kc=${kc.id}: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"completion write failed"}"""); return@csrfProtect
                    }
                } else {
                    // ── predict / attempt: ONE attempt row only, no mastery/FSRS write. Phase from mastery (or intro).
                    val phaseNow = resolvePhase(readMastery(db, userId, kc.id))
                    transaction(db) {
                        AttemptsTable.insert {
                            it[id] = jarvis.tutor.TutorTypes.ulid()
                            it[AttemptsTable.userId] = userId
                            it[AttemptsTable.kcId] = kc.id
                            it[taskId] = "lesson"
                            it[problemId] = kc.id + ":" + beatType
                            it[phase] = phaseNow.name
                            it[correct] = correct
                            it[score] = score
                            it[scaffoldLevel] = 0
                            it[AttemptsTable.recorded] = true
                            it[gradedAt] = now
                            it[beatType] = beatType
                            it[prediction] = req.prediction_text
                            it[firstEncounter] = firstEncounter
                        }
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiBeatGradeReply(
                        correct = correct,
                        score = score,
                        feedback_ro = feedbackRo,
                        beat_type = beatType,
                        lesson_complete = beatType == "check",
                        first_encounter = firstEncounter,
                        phase = if (beatType == "check") postPhase else null,
                        verification_status = if (beatType == "check") resolved else null,
                    ),
                )
            }
        }
    }
```

> The new route needs these imports at the top of `QueueMasteryCalibrationRoutes.kt` (add only the ones not already present):
> ```kotlin
> import io.ktor.server.request.receiveText
> import io.ktor.server.routing.post
> import jarvis.tutor.csrfProtect
> import org.jetbrains.exposed.sql.insert
> ```
> `requireUser`, `ContentRepo`, `VerifyAdmin`, `ReportWrongQuery`, `Phase`, `VerificationStatus`, `Instant`, `transaction`, `selectAll`, `and`, `eq` are already imported (used by the existing handlers). `KcMasteryTable` and `AttemptsTable` are already imported. Verify with `gradle :compileKotlin` after the edit; add any genuinely-missing import the compiler names.

- [ ] **Step 5: Run the new tests — expect GREEN:**

```powershell
gradle :test --tests "jarvis.web.LessonBeatGradeRouteTest"
```

Expected: `BUILD SUCCESSFUL`, all 10 tests pass. If `recordIn`/`upsertRubricCriterion`/`Fsrs.initial`/`FsrsState` argument shapes differ from the mirrored `TutorRoutes.kt:2217-2256` block, re-read that block and match it verbatim (do NOT invent a different state shape).

- [ ] **Step 6: Run the full backend suite** (the POST shares the corpus-load + serve + write path; never trust a partial run):

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`. **STOP and report if not green.**

- [ ] **Step 7: Commit (explicit paths ONLY — never `-A`).**

```powershell
git add src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt
git commit -m "feat(lesson): POST /lesson/{kcId}/beat — server grading + completion writes (Plan 3 Task 4)

Grades each gated beat from the KC's own beats data (choice → option.correct;
numeric → tolerance compare). Every graded beat writes ONE attempts row
(beat_type, prediction, first_encounter computed pre-write). CHECK additionally
runs recordIn (EWMA+phase) + FSRS seed in one atomic txn (mirrors the drill
grade txn) and replies lesson_complete + phase + verification_status. Same
faithful + dispute + beats-complete gate as the GET; bad beat_type ⇒ 400.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Task 6 — BeatOrchestrator + per-beat subcomponents + `lessonStrings.ts` + the beats client type + route flip + vitest

Builds the gated client state machine that consumes `lesson.beats` (the additive payload Task 3 added to the GET reply) and **replaces** the dead 3-step `LessonScreen` flow (audit E1–E4/E11/E12). One active beat at a time; the **Next gate** (`data-testid="beat-next-gate"`) stays `disabled` until the active beat's gate clears (predict committed → POST resolved; attempt submitted → POST resolved; reveal stepped to final at least once AND the per-step dwell floor met; check answered → POST resolved); pips strip (`data-testid="lesson-beat-pips"`, glyph-labeled ①②③④⑤); the predict-callback echo at reveal start; completion screen with `data-testid="lesson-complete-handoff"` calling an `onComplete` prop (Task 10 wires the concrete navigation target). The route `/lesson/:kcId` flips to `BeatOrchestratorRoute` HERE — the `LessonScreen.tsx` file stays on disk until Task 10 deletes it (the ROUTE switches now, the file dies later). Server stays grading truth: every gated beat POSTs to `/api/v1/lesson/{kcId}/beat` (Task 4) and the reply drives the gate — the client never self-grades writes. Animation imports are from `motion/react`, NEVER `framer-motion`; theme = Tailwind tokens only (`bg-accent`/`text-page-fg`/`border-border-strong`/`shadow-hard`, radius 0); all learner-visible strings come from `lessonStrings.ts` (Romanian).

> **Spec/contract anchors (read, do not re-derive):** plan core §0.9F (BeatOrchestrator contract + the full testid list), §0.9B (the wire DTO field names — `ApiLessonBeats` etc. — copy the TS mirror VERBATIM from the names there), §0.9C (the POST request/reply DTOs), §0.9E (`dwell.ts` — Task 5 ships it; Task 6 imports `readMs`), spec §4.1/§4.5/§4.7. The reveal scrubber for figure-bound ③ beats is wired in Task 8 (GraphTreeFamily + `AlgoStepperShell`); the 4 authored KCs (Task 7) carry NO figure, so Task 6's ③ path that ships and is vitest-covered here is the **stepped-text** path. Both render paths are implemented (the DTO carries `figure?`), but the figure path delegates to a `<FigureReveal>` placeholder that Task 8 fills — Task 6 leaves a typed seam, never a stub that 404s.

**Files:**
- Create: `tutor-web/src/lib/lessonStrings.ts`
- Create: `tutor-web/src/lib/beatGrade.ts` (the POST client — `postBeatGrade`)
- Create: `tutor-web/src/components/lesson/BeatOrchestrator.tsx`
- Create: `tutor-web/src/components/lesson/PredictBeat.tsx`
- Create: `tutor-web/src/components/lesson/AttemptBeat.tsx`
- Create: `tutor-web/src/components/lesson/RevealBeat.tsx`
- Create: `tutor-web/src/components/lesson/NameBeat.tsx`
- Create: `tutor-web/src/components/lesson/CheckBeat.tsx`
- Create: `tutor-web/src/components/lesson/BeatOrchestrator.test.tsx`
- Modify: `tutor-web/src/lib/lesson.ts` (add the `beats?: ApiLessonBeats` field + the beat DTO TS types, mirroring core §0.9B)
- Modify: `tutor-web/src/main.tsx` (flip the `/lesson/:kcId` route element to `BeatOrchestratorRoute`; keep the `LessonScreen` import until Task 10)

> **Pre-task dependency check.** Task 6 imports `readMs` from `tutor-web/src/lib/dwell.ts` (Task 5) and POSTs against the shape Task 4 grades. If executing Task 6 before 4/5 are merged, the vitest fetch stub still makes the orchestrator's tests pass (the server is stubbed), but `dwell.ts` MUST exist — if `tutor-web/src/lib/dwell.ts` is absent, STOP and do Task 5 first (it is a 1-file/1-test task).

- [ ] **Step 1: Extend the `ApiLessonReply` TS type with the beats DTOs** (mirror core §0.9B verbatim — same field names, same optionality). In `tutor-web/src/lib/lesson.ts`, find:

```ts
  /** Always {type:"authored", hasBeenFaithfulChecked:true} — gate guarantees faithfulness. */
  provenance: DrillProvenance;
}
```

Replace with:

```ts
  /** Always {type:"authored", hasBeenFaithfulChecked:true} — gate guarantees faithfulness. */
  provenance: DrillProvenance;
  /**
   * Plan-3 §0.9B — the ADDITIVE beats payload. Present iff the KC has complete beats for its
   * concept_type (server inline guard, Task 3); null ⇒ legacy payload (never served post-Task-3:
   * incomplete beats 404). Field names mirror Kotlin ApiLessonBeats EXACTLY (divergence = wire bug).
   */
  beats?: ApiLessonBeats | null;
}

/** Mirror of Kotlin ApiLessonBeats (core §0.9B). BeatType wire literals are lowercase. */
export interface ApiLessonBeats {
  /** BeatType lowercase names, in served order, e.g. ["predict","attempt","reveal","name","check"]. */
  plan: string[];
  /** The KC's concept_type wire literal. */
  concept_type: string;
  /** Present iff plan contains "predict". */
  predict?: ApiBeatPredict | null;
  attempt?: ApiBeatAttempt | null;
  reveal?: ApiBeatReveal | null;
  /** Present iff plan contains "name". */
  name?: ApiBeatName | null;
  check?: ApiBeatCheck | null;
}

export interface ApiPredictOption {
  text: string;
  callback: string;
  correct: boolean;
}
export interface ApiBeatPredict {
  prompt: string;
  options: ApiPredictOption[];
}
export interface ApiAttemptChoice {
  text: string;
  correct: boolean;
  feedback: string;
}
export interface ApiSkeletonRow {
  label: string;
  formula: string | null;
  is_decision_row: boolean;
}
export interface ApiTraceStep {
  row_index: number;
  value: string;
  callout: string | null;
}
export interface ApiBeatAttempt {
  statement: string;
  choices: ApiAttemptChoice[];
  skeleton_rows: ApiSkeletonRow[];
  trace_steps: ApiTraceStep[];
  input_schema: string | null;
  feedback_correct: string;
}
export interface ApiRevealStep {
  text: string;
  callout: string;
}
export interface ApiFigureBinding {
  family_id: string;
  instance_id: string;
}
export interface ApiBeatReveal {
  steps: ApiRevealStep[];
  figure?: ApiFigureBinding | null;
}
export interface ApiBeatName {
  definition: string;
  invariant_statement: string;
  why_matters: string;
}
export interface ApiBeatCheck {
  item_stem: string;
  choices: ApiAttemptChoice[];
  numeric_answer: string | null;
  numeric_tolerance: number | null;
}
```

- [ ] **Step 2: Create `tutor-web/src/lib/lessonStrings.ts`** — ALL learner-visible chrome strings for the new surface, Romanian (the spec §8.2 strings-file pattern; the global sweep is Plan 4). Code/identifiers stay EN:

```ts
/**
 * Plan-3 Task 6 — Romanian learner-facing chrome strings for the BeatOrchestrator surface only
 * (spec §8.2; the app-wide sweep is Plan 4). Every string the learner SEES on the lesson route
 * lives here so the §8.3 INV-8.3 chrome grep (no hardcoded EN learner literals outside the strings
 * file) stays green for this surface. Beat CONTENT (prompts/options/callouts) is per-KC data and
 * comes from the wire payload, NOT from here.
 */
export const lessonStrings = {
  /** Next/continue control (the gate button). */
  next: "Continuă",
  /** Final-beat label on the Next button. */
  finish: "Termină lecția",
  /** Back control. */
  back: "Înapoi",
  /** Gate-blocked message when predict not yet committed. */
  gateAnswer: "Răspunde ca să continui",
  /** Gate-blocked message on a reveal not yet stepped to the end / dwell not met. */
  gateWatch: "Parcurge toți pașii ca să continui",
  /** Submit control on attempt/check. */
  submit: "Trimite",
  /** Step counter prefix, rendered "pas k/N". */
  step: "pas",
  /** Beat-section eyebrow labels (paired with the glyphs). */
  predictLabel: "PREZICE",
  attemptLabel: "ÎNCEARCĂ",
  revealLabel: "PRIVEȘTE",
  nameLabel: "ACUM ARE UN NUME",
  checkLabel: "VERIFICĂ",
  /** Reveal echo banner prefix: "Tu ai prezis: …". */
  echoPrefix: "Tu ai prezis:",
  /** NameBeat callout sub-labels. */
  definitionLabel: "Definiție",
  invariantLabel: "Invariant",
  whyLabel: "De ce contează",
  /** Loading + completion. */
  loading: "Se încarcă…",
  complete: "Lecție completă",
  completeBody: "Bine făcut. Hai să exersezi acum acest concept.",
  /** The drill-handoff control on the completion screen. */
  handoff: "Începe exercițiile",
  /** Honest-degraded fallback when the KC has no servable beats / 404. */
  unavailable: "Această lecție nu este încă disponibilă — revin mai târziu.",
} as const;

/** The five beat glyphs, indexed by beat ordinal in the served plan (NOT by beat kind). */
export const BEAT_GLYPHS = ["①", "②", "③", "④", "⑤"] as const;

/** Beat-kind → eyebrow label (RO). */
export function beatKindLabel(kind: string): string {
  switch (kind) {
    case "predict": return lessonStrings.predictLabel;
    case "attempt": return lessonStrings.attemptLabel;
    case "reveal": return lessonStrings.revealLabel;
    case "name": return lessonStrings.nameLabel;
    case "check": return lessonStrings.checkLabel;
    default: return kind;
  }
}
```

- [ ] **Step 3: Create the POST client `tutor-web/src/lib/beatGrade.ts`** (mirrors core §0.9C request/reply; uses the existing `jarvisFetch` CSRF helper):

```ts
import { jarvisFetch } from "./api";

/** Request to POST /api/v1/lesson/{kcId}/beat — mirror of Kotlin ApiBeatGradeRequest (core §0.9C). */
export interface ApiBeatGradeRequest {
  beat_type: string;                 // "predict" | "attempt" | "check"
  selected_index?: number | null;    // choice beats
  free_input?: string | null;        // numerical attempt / numeric check
  prediction_text?: string | null;   // predict beats — stored on the attempt row
}

/** Reply — mirror of Kotlin ApiBeatGradeReply (core §0.9C). */
export interface ApiBeatGradeReply {
  correct: boolean;
  score: number;
  feedback_ro: string;
  beat_type: string;
  lesson_complete: boolean;
  first_encounter: boolean;
  phase?: string | null;
  verification_status?: string | null;
}

/**
 * POST a gated beat to the server (the SERVER grades + writes; the client never self-grades).
 * Throws on non-2xx so the orchestrator's gate stays closed on failure (the learner retries).
 */
export async function postBeatGrade(
  kcId: string,
  req: ApiBeatGradeRequest,
): Promise<ApiBeatGradeReply> {
  const res = await jarvisFetch(`/api/v1/lesson/${encodeURIComponent(kcId)}/beat`, {
    method: "POST",
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    throw new Error(`postBeatGrade ${res.status}: ${await res.text().catch(() => "")}`);
  }
  return res.json() as Promise<ApiBeatGradeReply>;
}
```

- [ ] **Step 4: Write the failing orchestrator test FIRST** — create `tutor-web/src/components/lesson/BeatOrchestrator.test.tsx`. This drives the whole component contract (gating, pips count, echo, dwell, check POST + completion, full FULL-plan traversal). It uses `vi.useFakeTimers()` for dwell and a fetch stub keyed on the beat endpoint:

```tsx
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BeatOrchestrator } from "./BeatOrchestrator";
import type { ApiLessonReply } from "../../lib/lesson";
import type { ApiBeatGradeReply } from "../../lib/beatGrade";

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

/** A FULL-plan (①②③④⑤) definition-taxonomy lesson fixture — choice variant, no figure. */
const FULL_LESSON: ApiLessonReply = {
  kcId: "pa-kc-001",
  kc_name_ro: "Noțiunea de algoritm",
  kc_name_en: "The notion of an algorithm",
  concrete_question_ro: null,
  echo_source_ro: null,
  prediction_options: [],
  term_ro: "Noțiunea de algoritm",
  definition_ro: null,
  explanation_ro: null,
  worked_example_ro: null,
  provenance: { type: "authored", hasBeenFaithfulChecked: true },
  beats: {
    plan: ["predict", "attempt", "reveal", "name", "check"],
    concept_type: "definition-taxonomy",
    predict: {
      prompt: "Care dintre acestea este un algoritm?",
      options: [
        { text: "O rețetă cu pași neambigui care se oprește", callback: "Exact — pași clari, finit.", correct: true },
        { text: "„Fă-l să fie frumos”", callback: "Prea vag — nu e neambiguu.", correct: false },
        { text: "Un calcul care nu se oprește niciodată", callback: "Nu se termină în timp finit.", correct: false },
      ],
    },
    attempt: {
      statement: "Clasifică: „repetă la nesfârșit”.",
      choices: [
        { text: "Algoritm", correct: false, feedback: "Nu se oprește în timp finit." },
        { text: "Nu e algoritm", correct: true, feedback: "Corect — încalcă condiția de terminare." },
      ],
      skeleton_rows: [],
      trace_steps: [],
      input_schema: null,
      feedback_correct: "Bun — ai prins condiția de terminare.",
    },
    reveal: {
      steps: [
        { text: "Un algoritm este o colecție bine ordonată de operații.", callout: "„well-ordered collection of operations”" },
        { text: "Operațiile sunt neambigue și efectiv calculabile.", callout: "fiecare pas e clar și executabil" },
        { text: "Execuția produce un rezultat și se oprește în timp finit.", callout: "terminarea e obligatorie" },
      ],
      figure: null,
    },
    name: {
      definition: "Algoritm = colecție bine ordonată de operații neambigue, efectiv calculabile, care produc un rezultat și se opresc în timp finit.",
      invariant_statement: "Dacă execuția nu se termină, nu e algoritm.",
      why_matters: "Fără terminare nu poți garanta un rezultat.",
    },
    check: {
      item_stem: "Este „adună a și b, afișează suma, oprește” un algoritm?",
      choices: [
        { text: "Da", correct: true, feedback: "Corect — pași neambigui, finit." },
        { text: "Nu", correct: false, feedback: "Ba da — îndeplinește toate condițiile." },
      ],
      numeric_answer: null,
      numeric_tolerance: null,
    },
  },
};

function gradeReply(over: Partial<ApiBeatGradeReply> = {}): ApiBeatGradeReply {
  return {
    correct: true,
    score: 1.0,
    feedback_ro: "Corect.",
    beat_type: "predict",
    lesson_complete: false,
    first_encounter: true,
    phase: null,
    verification_status: null,
    ...over,
  };
}

function stubFetch(handler?: (url: string, body: any) => ApiBeatGradeReply) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/lesson/") && url.endsWith("/beat")) {
      const body = init?.body ? JSON.parse(init.body as string) : {};
      const reply = handler ? handler(url, body) : gradeReply({ beat_type: body.beat_type });
      return new Response(JSON.stringify(reply), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", { value: "csrf=test", configurable: true, writable: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function renderOrch(onComplete = vi.fn()) {
  return render(
    <MemoryRouter>
      <BeatOrchestrator kcId="pa-kc-001" lesson={FULL_LESSON} onComplete={onComplete} />
    </MemoryRouter>,
  );
}

describe("BeatOrchestrator first paint", () => {
  it("renders pips matching the plan length, one active beat, and a disabled Next gate", () => {
    stubFetch();
    renderOrch();
    expect(screen.getByTestId("lesson-beat-pips").querySelectorAll("[data-pip]")).toHaveLength(5);
    expect(screen.getByTestId("lesson-beat-active")).toBeInTheDocument();
    expect(screen.getByTestId("beat-predict-options")).toBeInTheDocument();
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
  });
});

describe("BeatOrchestrator predict gate", () => {
  it("Next stays disabled until the predict POST resolves, then enables", async () => {
    const fetchMock = stubFetch();
    renderOrch();
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    const calls = fetchMock.mock.calls.filter((c) => String(c[0]).endsWith("/beat"));
    expect(calls).toHaveLength(1);
    expect(JSON.parse(calls[0][1].body as string).beat_type).toBe("predict");
  });
});

describe("BeatOrchestrator reveal echo + dwell", () => {
  it("echoes the chosen predict option's callback at reveal start", async () => {
    stubFetch();
    renderOrch();
    // commit predict (option 0)
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate")); // → attempt
    // submit attempt (correct choice index 1)
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate")); // → reveal
    // echo banner shows the predict option 0 callback
    expect(screen.getByTestId("reveal-echo")).toHaveTextContent("Exact — pași clari, finit.");
  });

  it("dwell blocks Next until the per-step timer elapses on the final step", async () => {
    vi.useFakeTimers();
    stubFetch();
    renderOrch();
    // advance to reveal
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await act(async () => { await vi.runOnlyPendingTimersAsync(); });
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await act(async () => { await vi.runOnlyPendingTimersAsync(); });
    fireEvent.click(screen.getByTestId("beat-next-gate")); // → reveal, step 1 of 3
    // step to the final reveal step
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 3/3");
    // dwell not yet met → still disabled
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
    // elapse the dwell floor
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(screen.getByTestId("beat-next-gate")).toBeEnabled();
  });
});

describe("BeatOrchestrator full traversal + completion", () => {
  it("clicks through all 5 beats, fires the check POST, and renders the completion handoff", async () => {
    const onComplete = vi.fn();
    const fetchMock = stubFetch((_url, body) =>
      gradeReply({
        beat_type: body.beat_type,
        lesson_complete: body.beat_type === "check",
        phase: body.beat_type === "check" ? "practice" : null,
      }),
    );
    renderOrch(onComplete);

    // ① predict
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // ② attempt
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // ③ reveal — step to the end (3 steps; start at 1, advance twice)
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // ④ name — text-only beat, no gate-input → Next enabled after dwell
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId ? screen.getByTestId("beat-next-gate") : screen.getByTestId("beat-next-gate"));
    // ⑤ check
    fireEvent.click(screen.getAllByTestId("check-choice")[0]);
    await waitFor(() => expect(screen.getByTestId("lesson-complete-handoff")).toBeInTheDocument());

    const beatCalls = fetchMock.mock.calls.filter((c) => String(c[0]).endsWith("/beat"));
    expect(beatCalls.map((c) => JSON.parse(c[1].body as string).beat_type)).toEqual([
      "predict", "attempt", "check",
    ]); // reveal + name are not graded (no POST)
    fireEvent.click(screen.getByTestId("lesson-complete-handoff"));
    expect(onComplete).toHaveBeenCalledWith("pa-kc-001");
  });
});
```

> **Note on the ④ name step in the traversal test:** the `getByTestId ? … : …` ternary above is dead-simple defensive noise — replace it with a plain `fireEvent.click(screen.getByTestId("beat-next-gate"))` when pasting (it is written verbose only to make the step boundary unmistakable; clean it to the single call). The name beat is text-only: its only gate is the dwell floor, which under fake timers you elapse, and under real timers (this test) resolves because the name beat's combined text is short enough that `readMs` floors at 1400ms — but this assertion runs synchronously after the reveal `waitFor`, so to keep it deterministic, the NameBeat dwell uses `prefersReducedMotionNow()` to collapse to 0 when reduced-motion is set; this test sets reduced-motion false, so **wrap the ④→⑤ advance in `vi.useFakeTimers()` OR set `mockReducedMotion(true)` for this one test**. Use `mockReducedMotion(true)` at the top of THIS test body (reduced motion zeroes every dwell — see Step 6) so the traversal is timer-free and deterministic.

- [ ] **Step 5: Run — expect RED** (the orchestrator + subcomponents do not exist):

```powershell
npm --prefix tutor-web run test -- --run src/components/lesson/BeatOrchestrator.test.tsx
```

Expected: vitest fails to resolve `./BeatOrchestrator` (module not found) / the per-beat imports.

- [ ] **Step 6: Implement the per-beat subcomponents.** Each is a presentational beat body; the orchestrator owns gate state. Create:

`tutor-web/src/components/lesson/PredictBeat.tsx`:

```tsx
import type { ApiBeatPredict } from "../../lib/lesson";

interface PredictBeatProps {
  predict: ApiBeatPredict;
  /** Index of the committed option, or null before commit. */
  committedIndex: number | null;
  /** Called when the learner picks an option (orchestrator POSTs + stores for echo). */
  onCommit: (index: number) => void;
}

/** ① PREDICT — classify-an-example-first. Options data-testid="beat-predict-options". */
export function PredictBeat({ predict, committedIndex, onCommit }: PredictBeatProps) {
  return (
    <div className="flex flex-col gap-3 font-mono">
      <p className="text-sm font-bold tracking-wide text-page-fg leading-relaxed">{predict.prompt}</p>
      <div data-testid="beat-predict-options" className="flex flex-col gap-2">
        {predict.options.map((opt, i) => {
          const committed = committedIndex === i;
          return (
            <button
              key={i}
              data-predict-index={i}
              disabled={committedIndex !== null}
              onClick={() => onCommit(i)}
              className={
                "border-2 px-4 py-3 text-left text-xs tracking-wide transition-colors " +
                (committed
                  ? "border-accent bg-accent text-black font-bold shadow-hard"
                  : "border-page-fg text-page-fg hover:border-accent hover:text-accent disabled:opacity-40")
              }
            >
              {opt.text}
            </button>
          );
        })}
      </div>
    </div>
  );
}
```

`tutor-web/src/components/lesson/AttemptBeat.tsx` — implements BOTH render paths (choice variant + numerical skeleton-trace), per the DTO; the 4 authored KCs are choice-variant, but the numerical path ships because the DTO carries `skeleton_rows`/`trace_steps`:

```tsx
import type { ApiBeatAttempt } from "../../lib/lesson";

interface AttemptBeatProps {
  attempt: ApiBeatAttempt;
  committedIndex: number | null;
  onCommitChoice: (index: number) => void;
}

/**
 * ② ÎNCEARCĂ — choice variant (data-testid="attempt-choice" per option) OR a read-only numerical
 * skeleton trace (skeleton_rows present). The 4 faithful KCs (Task 7) are choice-variant; the
 * numerical render path ships for formula-application/procedure/probabilistic KCs (DTO-carried).
 */
export function AttemptBeat({ attempt, committedIndex, onCommitChoice }: AttemptBeatProps) {
  const numerical = attempt.skeleton_rows.length > 0;
  return (
    <div className="flex flex-col gap-3 font-mono">
      <p className="text-sm font-bold tracking-wide text-page-fg leading-relaxed">{attempt.statement}</p>

      {numerical ? (
        <table data-testid="attempt-skeleton" className="border-2 border-border-strong text-xs text-page-fg">
          <tbody>
            {attempt.skeleton_rows.map((row, i) => (
              <tr key={i} className={row.is_decision_row ? "border-2 border-accent" : "border-b border-border-strong"}>
                <td className="px-3 py-2 font-bold tracking-wide">{row.label}</td>
                <td className="px-3 py-2 font-mono text-page-fg/80">{row.formula ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="flex flex-col gap-2">
          {attempt.choices.map((c, i) => {
            const committed = committedIndex === i;
            return (
              <button
                key={i}
                data-testid="attempt-choice"
                data-attempt-index={i}
                disabled={committedIndex !== null}
                onClick={() => onCommitChoice(i)}
                className={
                  "border-2 px-4 py-3 text-left text-xs tracking-wide transition-colors " +
                  (committed
                    ? "border-accent bg-accent text-black font-bold shadow-hard"
                    : "border-page-fg text-page-fg hover:border-accent hover:text-accent disabled:opacity-40")
                }
              >
                {c.text}
              </button>
            );
          })}
        </div>
      )}

      {/* Both-path feedback shown after commit (choice variant). */}
      {!numerical && committedIndex !== null && (
        <p data-testid="attempt-feedback" className="border-l-4 border-accent pl-3 text-xs text-page-fg/80 leading-relaxed">
          {attempt.choices[committedIndex].feedback}
        </p>
      )}
    </div>
  );
}
```

`tutor-web/src/components/lesson/RevealBeat.tsx` — stepped reveal. When `figure` is bound it delegates to `<FigureReveal>` (Task 8); otherwise the stepped-text path renders a scrubber-shaped control (`beat-figure-scrubber` + `scrubber-step-counter`) so the SAME gate semantics ("stepped to final + dwell met") apply, per core §0.9F:

```tsx
import { useState, useEffect, useCallback } from "react";
import type { ApiBeatReveal, ApiPredictOption } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";
import { readMs } from "../../lib/dwell";
import { prefersReducedMotionNow } from "../../theme/applyTheme";

interface RevealBeatProps {
  reveal: ApiBeatReveal;
  /** The learner's committed predict option (for the echo banner), or null. */
  predictedOption: ApiPredictOption | null;
  /** Fires once the reveal gate clears: stepped to final step AND its dwell floor met. */
  onGateClear: () => void;
}

/**
 * ③ PRIVEȘTE — stepped reveal with back/forward + "pas k/N". Figure path (Task 8) when reveal.figure
 * is set; the 4 authored KCs carry NO figure, so the stepped-TEXT path is what ships + is tested here.
 * Gate clears when the learner reached the FINAL step at least once AND that step's dwell floor elapsed.
 */
export function RevealBeat({ reveal, predictedOption, onGateClear }: RevealBeatProps) {
  const steps = reveal.steps;
  const n = steps.length;
  const [idx, setIdx] = useState(0);
  const [reachedEnd, setReachedEnd] = useState(n === 1);
  const [dwellMet, setDwellMet] = useState(false);
  const reduced = prefersReducedMotionNow();

  // Per-step dwell floor: a wall of text can't be skipped in 200ms (spec §4.1). Reduced motion → 0.
  useEffect(() => {
    setDwellMet(false);
    if (reduced) { setDwellMet(true); return; }
    const t = setTimeout(() => setDwellMet(true), readMs(steps[idx].text + " " + steps[idx].callout));
    return () => clearTimeout(t);
  }, [idx, reduced, steps]);

  useEffect(() => {
    if (idx === n - 1) setReachedEnd(true);
  }, [idx, n]);

  const gateClear = reachedEnd && dwellMet;
  useEffect(() => {
    if (gateClear) onGateClear();
  }, [gateClear, onGateClear]);

  const back = useCallback(() => setIdx((i) => Math.max(0, i - 1)), []);
  const fwd = useCallback(() => setIdx((i) => Math.min(n - 1, i + 1)), [n]);

  // Figure path: Task 8 fills FigureReveal. Until then the authored KCs never bind a figure, so this
  // branch is unreachable for served content; the seam is typed, not a 404 stub.
  if (reveal.figure) {
    return (
      <div data-testid="beat-figure-scrubber" className="font-mono text-xs text-page-fg/60">
        {/* Task 8: <FigureReveal figure={reveal.figure} onGateClear={onGateClear} /> */}
        <span data-testid="scrubber-step-counter">{lessonStrings.step} 1/1</span>
      </div>
    );
  }

  const step = steps[idx];
  return (
    <div className="flex flex-col gap-3 font-mono">
      {predictedOption && (
        <p data-testid="reveal-echo" className="border-l-4 border-accent pl-3 text-xs text-page-fg/70 leading-relaxed">
          <span className="font-bold tracking-widest uppercase text-[10px] text-accent block mb-1">
            {lessonStrings.echoPrefix}
          </span>
          {predictedOption.callback}
        </p>
      )}

      <div className="border-2 border-border-strong p-4 flex flex-col gap-2 shadow-hard">
        <p className="text-sm text-page-fg leading-relaxed">{step.text}</p>
        <p className="border-l-4 border-accent pl-3 text-xs text-page-fg/80 leading-relaxed">{step.callout}</p>
      </div>

      <div data-testid="beat-figure-scrubber" className="flex items-center gap-3">
        <button
          data-step-back
          onClick={back}
          disabled={idx === 0}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          ‹ {lessonStrings.back}
        </button>
        <span data-testid="scrubber-step-counter" className="text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.step} {idx + 1}/{n}
        </span>
        <button
          data-step-fwd
          onClick={fwd}
          disabled={idx === n - 1}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          {lessonStrings.next} ›
        </button>
      </div>
    </div>
  );
}
```

`tutor-web/src/components/lesson/NameBeat.tsx`:

```tsx
import { useEffect } from "react";
import type { ApiBeatName } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";
import { readMs } from "../../lib/dwell";
import { prefersReducedMotionNow } from "../../theme/applyTheme";

interface NameBeatProps {
  name: ApiBeatName;
  /** Fires once the name beat's dwell floor elapses (text-only beat: dwell is the only gate). */
  onGateClear: () => void;
}

/** ④ ACUM ARE UN NUME — definition + invariant + why-it-matters callout. */
export function NameBeat({ name, onGateClear }: NameBeatProps) {
  const reduced = prefersReducedMotionNow();
  useEffect(() => {
    if (reduced) { onGateClear(); return; }
    const t = setTimeout(onGateClear, readMs(name.definition + " " + name.invariant_statement + " " + name.why_matters));
    return () => clearTimeout(t);
  }, [reduced, name, onGateClear]);

  return (
    <div className="flex flex-col gap-3 font-mono">
      <div className="border-2 border-accent bg-accent text-black p-4 shadow-hard">
        <span className="font-bold uppercase tracking-widest text-[10px] block mb-1">{lessonStrings.definitionLabel}</span>
        <p className="text-sm leading-relaxed">{name.definition}</p>
      </div>
      <div className="border-2 border-border-strong p-3">
        <span className="font-bold uppercase tracking-widest text-[10px] text-accent block mb-1">{lessonStrings.invariantLabel}</span>
        <p className="text-xs text-page-fg leading-relaxed">{name.invariant_statement}</p>
      </div>
      <div className="border-l-4 border-accent pl-3">
        <span className="font-bold uppercase tracking-widest text-[10px] text-page-fg/50 block mb-1">{lessonStrings.whyLabel}</span>
        <p className="text-xs text-page-fg/80 leading-relaxed">{name.why_matters}</p>
      </div>
    </div>
  );
}
```

`tutor-web/src/components/lesson/CheckBeat.tsx` — choices OR numeric input; POSTs on submit, shows `feedback_ro`:

```tsx
import { useState } from "react";
import type { ApiBeatCheck } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";

interface CheckBeatProps {
  check: ApiBeatCheck;
  /** Disable inputs once submitted (gate clears via the POST in the orchestrator). */
  submitted: boolean;
  /** Server feedback to render after grading. */
  feedbackRo: string | null;
  /** Choice variant. */
  onSubmitChoice: (index: number) => void;
  /** Numeric variant. */
  onSubmitNumeric: (value: string) => void;
}

/** ⑤ VERIFICĂ — different-instance check item. Choice variant or numeric input. */
export function CheckBeat({ check, submitted, feedbackRo, onSubmitChoice, onSubmitNumeric }: CheckBeatProps) {
  const numeric = check.choices.length === 0 && check.numeric_answer != null;
  const [value, setValue] = useState("");

  return (
    <div className="flex flex-col gap-3 font-mono">
      <p className="text-sm font-bold tracking-wide text-page-fg leading-relaxed">{check.item_stem}</p>

      {numeric ? (
        <div className="flex items-center gap-2">
          <input
            data-testid="check-numeric-input"
            value={value}
            disabled={submitted}
            onChange={(e) => setValue(e.target.value)}
            className="bg-panel-dark border-2 border-border-strong text-page-fg text-xs p-2 w-40 focus:border-accent focus:outline-none"
          />
          <button
            data-testid="check-submit"
            disabled={submitted || value.trim().length === 0}
            onClick={() => onSubmitNumeric(value.trim())}
            className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-4 py-2 disabled:opacity-30"
          >
            {lessonStrings.submit}
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {check.choices.map((c, i) => (
            <button
              key={i}
              data-testid="check-choice"
              data-check-index={i}
              disabled={submitted}
              onClick={() => onSubmitChoice(i)}
              className="border-2 border-page-fg text-page-fg text-xs tracking-wide px-4 py-3 text-left hover:border-accent hover:text-accent disabled:opacity-40 transition-colors"
            >
              {c.text}
            </button>
          ))}
        </div>
      )}

      {submitted && feedbackRo && (
        <p data-testid="check-feedback" className="border-l-4 border-accent pl-3 text-xs text-page-fg/80 leading-relaxed">
          {feedbackRo}
        </p>
      )}
    </div>
  );
}
```

- [ ] **Step 7: Implement the orchestrator** — `tutor-web/src/components/lesson/BeatOrchestrator.tsx`. It owns the active index, per-beat gate-cleared flags, the stored predict choice (for echo), and posts gated beats; the completion screen calls `onComplete(kcId)` (Task 10 wires navigation):

```tsx
import { useCallback, useMemo, useState } from "react";
import type { ApiLessonReply, ApiPredictOption } from "../../lib/lesson";
import { postBeatGrade } from "../../lib/beatGrade";
import { lessonStrings, BEAT_GLYPHS, beatKindLabel } from "../../lib/lessonStrings";
import { PredictBeat } from "./PredictBeat";
import { AttemptBeat } from "./AttemptBeat";
import { RevealBeat } from "./RevealBeat";
import { NameBeat } from "./NameBeat";
import { CheckBeat } from "./CheckBeat";

interface BeatOrchestratorProps {
  kcId: string;
  lesson: ApiLessonReply;
  /** Called with the kcId when the learner clicks the drill handoff (Task 10 wires the route). */
  onComplete: (kcId: string) => void;
}

/**
 * Plan-3 §0.9F — the gated lesson state machine. Replaces LessonScreen's 3-step flow (audit E1–E4).
 * ONE active beat; Next (beat-next-gate) disabled until the active beat's gate clears. Server grades
 * every gated beat via POST; the client never self-grades writes (spec §4.4).
 */
export function BeatOrchestrator({ kcId, lesson, onComplete }: BeatOrchestratorProps) {
  const beats = lesson.beats;
  const plan = beats?.plan ?? [];

  const [activeIdx, setActiveIdx] = useState(0);
  const [cleared, setCleared] = useState<Record<number, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [predictIndex, setPredictIndex] = useState<number | null>(null);
  const [attemptIndex, setAttemptIndex] = useState<number | null>(null);
  const [checkSubmitted, setCheckSubmitted] = useState(false);
  const [checkFeedback, setCheckFeedback] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const predictedOption: ApiPredictOption | null = useMemo(() => {
    if (predictIndex == null || !beats?.predict) return null;
    return beats.predict.options[predictIndex] ?? null;
  }, [predictIndex, beats]);

  const markCleared = useCallback((idx: number) => {
    setCleared((c) => (c[idx] ? c : { ...c, [idx]: true }));
  }, []);

  if (!beats || plan.length === 0) {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono">
        <div data-testid="lesson-unavailable" className="border-2 border-border-strong p-4 text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.unavailable}
        </div>
      </div>
    );
  }

  const kind = plan[activeIdx];
  const isLast = activeIdx === plan.length - 1;
  const gateOpen = !!cleared[activeIdx] && !busy;

  async function commitPredict(index: number) {
    if (busy || predictIndex != null) return;
    setBusy(true);
    setPredictIndex(index);
    try {
      await postBeatGrade(kcId, {
        beat_type: "predict",
        selected_index: index,
        prediction_text: beats!.predict?.options[index]?.text ?? null,
      });
      markCleared(activeIdx);
    } finally {
      setBusy(false);
    }
  }

  async function commitAttempt(index: number) {
    if (busy || attemptIndex != null) return;
    setBusy(true);
    setAttemptIndex(index);
    try {
      await postBeatGrade(kcId, { beat_type: "attempt", selected_index: index });
      markCleared(activeIdx);
    } finally {
      setBusy(false);
    }
  }

  async function submitCheck(req: { selected_index?: number; free_input?: string }) {
    if (busy || checkSubmitted) return;
    setBusy(true);
    setCheckSubmitted(true);
    try {
      const reply = await postBeatGrade(kcId, { beat_type: "check", ...req });
      setCheckFeedback(reply.feedback_ro);
      markCleared(activeIdx);
      if (reply.lesson_complete) setDone(true);
    } finally {
      setBusy(false);
    }
  }

  function onNext() {
    if (!gateOpen) return;
    if (isLast) {
      setDone(true);
      return;
    }
    setActiveIdx((i) => i + 1);
  }

  if (done) {
    return (
      <div data-testid="lesson-complete" className="flex flex-col flex-1 items-center justify-center gap-4 p-8 font-mono">
        <h2 className="text-lg font-bold tracking-widest uppercase text-page-fg">{lessonStrings.complete}</h2>
        <p className="text-xs text-page-fg/70 tracking-wide text-center max-w-md leading-relaxed">{lessonStrings.completeBody}</p>
        <button
          data-testid="lesson-complete-handoff"
          onClick={() => onComplete(kcId)}
          className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-6 py-3 shadow-hard hover:bg-accent-hover transition-colors"
        >
          {lessonStrings.handoff}
        </button>
      </div>
    );
  }

  return (
    <div data-testid="lesson-screen" className="flex flex-col h-full overflow-y-auto font-mono">
      {/* Pips */}
      <div data-testid="lesson-beat-pips" className="flex items-center gap-2 p-4 border-b border-border-strong">
        {plan.map((k, i) => (
          <span
            key={i}
            data-pip
            aria-label={beatKindLabel(k)}
            className={
              "w-7 h-7 flex items-center justify-center border-2 text-xs " +
              (i === activeIdx
                ? "border-accent bg-accent text-black font-bold shadow-hard"
                : i < activeIdx
                  ? "border-accent text-accent"
                  : "border-border-strong text-page-fg/40")
            }
          >
            {BEAT_GLYPHS[i] ?? i + 1}
          </span>
        ))}
      </div>

      {/* Active beat */}
      <div data-testid="lesson-beat-active" className="flex flex-col gap-4 p-4 flex-1">
        <span className="font-bold uppercase tracking-widest text-[10px] text-page-fg/50">
          {BEAT_GLYPHS[activeIdx]} {beatKindLabel(kind)}
        </span>

        {kind === "predict" && beats.predict && (
          <PredictBeat predict={beats.predict} committedIndex={predictIndex} onCommit={commitPredict} />
        )}
        {kind === "attempt" && beats.attempt && (
          <AttemptBeat attempt={beats.attempt} committedIndex={attemptIndex} onCommitChoice={commitAttempt} />
        )}
        {kind === "reveal" && beats.reveal && (
          <RevealBeat reveal={beats.reveal} predictedOption={predictedOption} onGateClear={() => markCleared(activeIdx)} />
        )}
        {kind === "name" && beats.name && (
          <NameBeat name={beats.name} onGateClear={() => markCleared(activeIdx)} />
        )}
        {kind === "check" && beats.check && (
          <CheckBeat
            check={beats.check}
            submitted={checkSubmitted}
            feedbackRo={checkFeedback}
            onSubmitChoice={(i) => submitCheck({ selected_index: i })}
            onSubmitNumeric={(v) => submitCheck({ free_input: v })}
          />
        )}
      </div>

      {/* Gate controls */}
      <div className="flex items-center justify-between gap-4 p-4 border-t border-border-strong">
        <button
          onClick={() => setActiveIdx((i) => Math.max(0, i - 1))}
          disabled={activeIdx === 0}
          className="border-2 border-page-fg px-4 py-2 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          ‹ {lessonStrings.back}
        </button>
        <span className="text-xs text-page-fg/50 tracking-wide">
          {gateOpen ? "" : (kind === "reveal" ? lessonStrings.gateWatch : lessonStrings.gateAnswer)}
        </span>
        <button
          data-testid="beat-next-gate"
          onClick={onNext}
          disabled={!gateOpen}
          className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-6 py-2 shadow-hard disabled:opacity-30 disabled:cursor-not-allowed hover:enabled:bg-accent-hover transition-colors"
        >
          {isLast ? lessonStrings.finish : lessonStrings.next}
        </button>
      </div>
    </div>
  );
}
```

> **Gate-semantics note for the executor:** `cleared[idx]` is set true by each beat's own clear path — predict/attempt/check via the POST resolving, reveal/name via `onGateClear` (stepped-to-end + dwell, or dwell-only). The `busy` flag keeps Next disabled WHILE a POST is in flight (so the test's "disabled until POST resolves" assertion holds). `markCleared` is idempotent so re-entry (back then forward) keeps an already-cleared beat open.

- [ ] **Step 8: Flip the route in `tutor-web/src/main.tsx`.** Add the wrapper + a loader (the orchestrator needs the lesson payload; reuse `getLesson`). Keep the `LessonScreen` import (Task 10 deletes it). Add the import near the other component imports:

Find:

```tsx
import { LessonScreen } from "./components/LessonScreen";
```

Replace with:

```tsx
import { LessonScreen } from "./components/LessonScreen"; // deleted in Plan-3 Task 10
import { BeatOrchestrator } from "./components/lesson/BeatOrchestrator";
import { getLesson } from "./lib/lesson";
import type { ApiLessonReply } from "./lib/lesson";
```

Then add `useEffect` + `useState` to the EXISTING `react` import rather than a second `from "react"` statement (main.tsx already imports `StrictMode` from "react" at line 3 — a duplicate `from "react"` is redundant and trips `import/no-duplicates`). Find:

```tsx
import { StrictMode } from "react";
```

Replace with:

```tsx
import { StrictMode, useEffect, useState } from "react";
```

Then replace the `LessonScreenRoute` function:

Find:

```tsx
function LessonScreenRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  return <LessonScreen kcId={kcId ?? ""} />;
}
```

Replace with:

```tsx
/**
 * Plan-3 Task 6 — the lesson route now mounts the BeatOrchestrator. It loads the beats payload
 * (GET /api/v1/lesson/{kcId}); null/404/beats-absent → honest-degraded unavailable message.
 * The concrete drill-handoff navigation target is wired in Task 10 (fallback: /tutor/oggi).
 */
function BeatOrchestratorRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  const id = kcId ?? "";
  const [lesson, setLesson] = useState<ApiLessonReply | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    setLesson(undefined);
    getLesson(id)
      .then((r) => { if (!cancelled) setLesson(r); })
      .catch(() => { if (!cancelled) setLesson(null); });
    return () => { cancelled = true; };
  }, [id]);

  if (lesson === undefined) {
    return <div data-testid="lesson-screen" className="flex-1 p-6 font-mono text-xs text-page-fg/50 tracking-widest">Se încarcă…</div>;
  }
  if (lesson === null || !lesson.beats) {
    return (
      <div data-testid="lesson-screen" className="flex-1 p-6 font-mono">
        <div data-testid="lesson-unavailable" className="border-2 border-border-strong p-4 text-xs text-page-fg/60 tracking-wide">
          KC nu este încă verificat — revin mai târziu.
        </div>
      </div>
    );
  }
  // Task 10 replaces the fallback nav with the verified drill route.
  return <BeatOrchestrator kcId={id} lesson={lesson} onComplete={() => { window.location.href = "/tutor/oggi"; }} />;
}
```

Then point the route element at it. Find:

```tsx
          <Route path="/lesson/:kcId" element={<LessonScreenRoute />} />
```

Replace with:

```tsx
          <Route path="/lesson/:kcId" element={<BeatOrchestratorRoute />} />
```

- [ ] **Step 9: Apply the test cleanup noted in Step 4.** In `BeatOrchestrator.test.tsx`, in the "full traversal" test, (a) add `mockReducedMotion(true);` as the FIRST line of the test body (zeroes every dwell → deterministic, timer-free), and (b) replace the verbose `getByTestId ? … : …` ternary with the single line `fireEvent.click(screen.getByTestId("beat-next-gate"));`. With reduced motion the reveal step's dwell is 0, so after stepping to `pas 3/3` the Next gate enables synchronously (still requires reaching the last step — the gate is NOT bypassed, only the dwell floor collapses).

- [ ] **Step 10: Run the orchestrator suite — expect GREEN:**

```powershell
npm --prefix tutor-web run test -- --run src/components/lesson/BeatOrchestrator.test.tsx
```

Expected: all describe blocks pass — first-paint (pips=5, one active beat, predict options, disabled Next), predict gate (disabled→enabled on POST resolve, 1 predict POST), reveal echo (callback text), dwell (disabled at pas 3/3 until 6s elapse), full traversal (3 graded POSTs `[predict,attempt,check]`, handoff renders, `onComplete("pa-kc-001")`).

- [ ] **Step 11: Typecheck + the full frontend suite** (the new types touch `lesson.ts`; never trust a partial run — review-workflow rule):

```powershell
npm --prefix tutor-web run build
npm --prefix tutor-web run test -- --run
```

Expected: `tsc` clean (build succeeds), all vitest green. (The pre-existing `tutor-shell-api-contract.spec.ts` Playwright e2e is NOT in the vitest include-glob — if vitest reds, it is yours.) The old `LessonScreen.test.tsx` still passes here — the file is alive until Task 10; do NOT delete it in this task.

- [ ] **Step 12: Commit (explicit paths only — never `git add -A`; the untracked door demos must stay untouched, §0.6 #7):**

```powershell
git add tutor-web/src/lib/lessonStrings.ts tutor-web/src/lib/beatGrade.ts tutor-web/src/lib/lesson.ts tutor-web/src/components/lesson/BeatOrchestrator.tsx tutor-web/src/components/lesson/PredictBeat.tsx tutor-web/src/components/lesson/AttemptBeat.tsx tutor-web/src/components/lesson/RevealBeat.tsx tutor-web/src/components/lesson/NameBeat.tsx tutor-web/src/components/lesson/CheckBeat.tsx tutor-web/src/components/lesson/BeatOrchestrator.test.tsx tutor-web/src/main.tsx
git commit -m @'
feat(lesson): BeatOrchestrator gated state machine + per-beat subcomponents (Plan 3 Task 6)

Replaces LessonScreen's dead 3-step flow (audit E1-E4). Consumes lesson.beats;
ONE active beat, Next gate (beat-next-gate) disabled until the beat's gate clears
(predict/attempt/check via server POST, reveal/name via stepped-end + dwell floor).
Pips ①②③④⑤, predict-callback echo at reveal, completion handoff (onComplete prop;
Task 10 wires the drill route). Server stays grading truth via POST /lesson/{kcId}/beat.
lessonStrings.ts holds all RO chrome. Route /lesson/:kcId flips to BeatOrchestratorRoute;
LessonScreen file stays alive until Task 10. motion/react only; Tailwind tokens, radius 0.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## Task 7 — RO beat authoring for pa-kc-001..004 (dual-writer + adversarial review; source-derived only) + INV-3.2b green + hash-stability re-proof + INV-5.5 validator check

Authors the `beats: { ro: … }` block into the 4 faithful PA KC YAMLs so the lesson handler can serve them (Task 3 serves only when `beats["ro"].isCompleteFor(concept_type)`; Plan-2 INV-3.2b loud-reds any authored-but-incomplete beats). **Every learner string is Romanian with diacritics; every factual claim is traceable to the KC's OWN YAML fields or its source quotes** — zero new subject facts (the no-oracle-inversion + grounded-teaching rule: Alex never vets content). The author is YOU (this task body carries the actual authored content); the executor pastes it and runs an adversarial-review step (a reviewer subagent re-derives each claim's provenance — a mismatch is a STOP). Three render variants apply: **definition-taxonomy** (001/002/003: ① classify-an-example-first, ② choice attempt with both-path teaching feedback, ③ stepped reveal each fused with a callout, ④ formal definition + invariant + why, ⑤ different-instance classify) and **proof** (004: ① classify which-step-first on the argument, ② supply-the-next-named-sub-step from choices, ③ proof-skeleton steps `need-formal-def → models-introduced → models-equivalent → conclusion`, ④ name, ⑤ different-instance application). None of the 4 carries a figure (locked decision §0.6 #1 — binding MergeSort to these KCs would attach content their source doesn't teach = trust violation), so INV-5.5's figure-binding check is vacuous-green on them.

> **Hash safety (core §0.2/§0.3, recon-confirmed):** `ContentReconcile.claimsFor` reads ONLY `source/invariant/invariant_statement/grader_rules/explanation_ro/worked_example_ro` — it does NOT read `beats`. Authoring beats therefore does NOT change any KC's `content_hash`; the 4 faithful badges stay lit. This task re-proves it machine-checked (Step 5) via the Plan-2 `ConceptTypeHashStabilityTest`.

> **The ONLY permitted fact source for authoring is `content/PA/_sources/pa-lecture-01.md` and each KC's own YAML `source[*].quote` / `name_ro` / `explanation_ro` / `worked_example_ro` / `self_explanation_prompt` / `far_transfer_stem`.** Every beat below carries a YAML `#` comment citing the field/quote it derives from. EN source spans are framed in RO per spec §8.1 ("cursul spune: ‹…EN…› — adică …"), never left bare.

**Files:**
- Modify: `content/PA/kcs/pa-kc-001.yaml` (append `beats:`)
- Modify: `content/PA/kcs/pa-kc-002.yaml` (append `beats:`)
- Modify: `content/PA/kcs/pa-kc-003.yaml` (append `beats:`)
- Modify: `content/PA/kcs/pa-kc-004.yaml` (append `beats:`)
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt` (additive INV-5.5 figure-binding check)
- Create: `src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt`
- Create: `src/test/kotlin/jarvis/content/AuthoredBeatsCompletenessTest.kt` (unit-level INV-3.2b over the loaded real corpus)

- [ ] **Step 1: Author `beats` into `pa-kc-001.yaml`** (definition-taxonomy). Append this block to the END of the file (after `worked_example_ro:`), exactly as written. Every string derives from this KC's source quotes (the page-4 definition: "well-ordered collection of unambiguous and effectively computable operations that when executed produces a result and halts in a finite amount of time"; "guaranteed to solve a specific problem") and its `explanation_ro` / `worked_example_ro` — cited per beat:

```yaml
# ─────────────────────────────────────────────────────────────────────────────
# Plan-3 Task 7 — 5-beat teaching content (RO). concept_type=definition-taxonomy.
# PROVENANCE: every claim derives from THIS KC's source quotes (pa-lecture-01 p.4:
#   "An algorithm is a well-ordered collection of unambiguous and effectively computable
#    operations that when executed produces a result and halts in a finite amount of time."
#   + "guaranteed to solve a specific problem") and its own explanation_ro / worked_example_ro.
# ZERO new subject facts. EN spans framed RO per spec §8.1. (Reviewer: re-derive each claim.)
# ─────────────────────────────────────────────────────────────────────────────
beats:
  ro:
    predict:
      # ① classify-an-example-first (def-taxonomy variant) — the three options test the THREE
      #    definitional clauses from the p.4 quote: neambiguu+efectiv calculabil, terminare în
      #    timp finit, produce un rezultat. Correct = the one meeting all clauses.
      prompt: "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?"
      options:
        - text: "„Citește a și b, calculează s = a + b, afișează s și oprește-te.”"
          callback: "Exact — pașii sunt neambigui și efectiv calculabili, produce un rezultat și se oprește în timp finit (definiția p.4)."
          correct: true
        - text: "„Fă-l să arate bine.”"
          callback: "Nu — pasul nu este neambiguu; definiția cere operații neambigue și efectiv calculabile."
          correct: false
        - text: "„Repetă pasul la nesfârșit, fără condiție de oprire.”"
          callback: "Nu — încalcă cerința ca execuția să se oprească într-un timp finit."
          correct: false
    attempt:
      # ② choice attempt, both-path teaching feedback. Statement reuses the worked_example_ro
      #    add-two-numbers example; wrong path TEACHES the violated clause (never just „greșit”).
      statement: "Secvența „cât timp x > 0, afișează x” (fără ca x să se modifice) este un algoritm?"
      choices:
        - text: "Da, este un algoritm"
          correct: false
          feedback: "Nu chiar: dacă x nu se schimbă, execuția nu se oprește niciodată — se încalcă cerința de terminare în timp finit din definiția p.4."
        - text: "Nu, nu este un algoritm"
          correct: true
          feedback: "Corect — fără terminare în timp finit nu îndeplinește definiția din curs, oricât de neambigui ar fi pașii."
      feedback_correct: "Bun — ai folosit chiar condiția de terminare din definiția p.4."
    reveal:
      # ③ stepped reveal — each step restates ONE clause of the p.4 definition, callout fuses the
      #    EN source span framed RO (§8.1). Mirrors explanation_ro almost verbatim.
      steps:
        - text: "Un algoritm este o colecție bine ordonată de operații."
          callout: "Cursul spune: «a well-ordered collection of ... operations» — adică pașii au o ordine clară."
        - text: "Operațiile sunt neambigue și efectiv calculabile."
          callout: "«unambiguous and effectively computable operations» — fiecare pas e clar și poate fi efectiv executat."
        - text: "Când este executat, produce un rezultat și se oprește într-un timp finit."
          callout: "«produces a result and halts in a finite amount of time» — execuția nu continuă la nesfârșit."
      figure: null
    name:
      # ④ formal definition (verbatim restatement of explanation_ro) + invariant + why.
      definition: "Algoritm = o colecție bine ordonată de operații neambigue și efectiv calculabile care, atunci când sunt executate, produc un rezultat și se opresc într-un timp finit."
      invariant_statement: "Dacă execuția nu se oprește într-un timp finit, secvența nu este un algoritm — oricât de clari ar fi pașii."
      why_matters: "Fără terminare nu poți garanta că obții vreodată un rezultat, deci nu poți spune că ai rezolvat problema."
    check:
      # ⑤ different-instance classify (NOT the attempt instance). Uses the far_transfer recipe idea.
      item_stem: "O rețetă de bucătărie cu pași clari care se termină după ultimul pas îndeplinește definiția algoritmului din curs?"
      choices:
        - text: "Da — pași neambigui, efectiv executabili, se termină în timp finit"
          correct: true
          feedback: "Corect — îndeplinește toate cele trei condiții din definiția p.4."
        - text: "Nu — o rețetă nu poate fi niciodată un algoritm"
          correct: false
          feedback: "Ba da: dacă pașii sunt neambigui, efectiv executabili și se termină în timp finit, definiția din curs este îndeplinită."
      numeric_answer: null
      numeric_tolerance: null
```

- [ ] **Step 2: Author `beats` into `pa-kc-002.yaml`** (definition-taxonomy). Derives from this KC's quotes (p.7: "computation model"; "An algorithm must solve a problem given by a pair (input,output), where the input is represented in the start configuration and the output in the final configuration") and `self_explanation_prompt` / `far_transfer_stem`. Append to the END of the file:

```yaml
# ─────────────────────────────────────────────────────────────────────────────
# Plan-3 Task 7 — 5-beat teaching content (RO). concept_type=definition-taxonomy.
# PROVENANCE: pa-lecture-01 p.7 — "computation model" + "An algorithm must solve a problem
#   given by a pair (input,output), where the input is represented in the start configuration
#   and the output in the final configuration." + this KC's self_explanation_prompt / far_transfer_stem.
# ZERO new subject facts. EN spans framed RO per §8.1. (Reviewer: re-derive each claim.)
# ─────────────────────────────────────────────────────────────────────────────
beats:
  ro:
    predict:
      # ① classify-an-example-first: which framing matches the (intrare, ieșire) pair from p.7?
      prompt: "O problemă este dată, conform cursului, ca o pereche (intrare, ieșire). Care descriere se potrivește acestei perechi?"
      options:
        - text: "Intrarea = lista nesortată din configurația de start; ieșirea = lista sortată din configurația finală."
          callback: "Exact — intrarea apare în configurația de start, ieșirea în configurația finală (p.7)."
          correct: true
        - text: "Intrarea și ieșirea sunt același lucru, nu contează ordinea."
          callback: "Nu — cursul distinge configurația de start (intrarea) de cea finală (ieșirea)."
          correct: false
        - text: "O problemă nu are nicio legătură cu un model de calcul."
          callback: "Nu — perechea (intrare, ieșire) se reprezintă tocmai în configurațiile modelului de calcul."
          correct: false
    attempt:
      # ② choice attempt: map a concrete problem to start/final configuration (both-path feedback).
      statement: "Pentru problema „dat un număr n, calculează n!”, ce reprezintă configurația de start?"
      choices:
        - text: "Valoarea n (intrarea)"
          correct: true
          feedback: "Corect — intrarea (n) este reprezentată în configurația de start, conform p.7."
        - text: "Valoarea n! (rezultatul)"
          correct: false
          feedback: "Nu — n! este ieșirea, reprezentată în configurația finală; configurația de start conține intrarea n."
      feedback_correct: "Bun — ai legat intrarea de configurația de start exact ca în curs."
    reveal:
      # ③ stepped reveal — computation model parts + the (input,output) pairing; callouts frame EN spans.
      steps:
        - text: "Descrierea formală generală a procesării datelor este modelul de calcul."
          callout: "Cursul spune: «The general formal description of this is that of computation model»."
        - text: "Un algoritm trebuie să rezolve o problemă dată ca pereche (intrare, ieșire)."
          callout: "«An algorithm must solve a problem given by a pair (input,output)»."
        - text: "Intrarea se reprezintă în configurația de start, iar ieșirea în configurația finală."
          callout: "«the input is represented in the start configuration and the output in the final configuration»."
      figure: null
    name:
      definition: "O problemă (pentru un algoritm) este dată de perechea (intrare, ieșire), unde intrarea se reprezintă în configurația de start a modelului de calcul, iar ieșirea în configurația finală."
      invariant_statement: "Intrarea trăiește în configurația de start; ieșirea în configurația finală — niciodată invers."
      why_matters: "Fără să fixezi ce e intrarea și ce e ieșirea, nu poți spune precis ce trebuie să calculeze algoritmul."
    check:
      # ⑤ different-instance: sorting (the far_transfer example), classify start vs final config.
      item_stem: "Pentru problema „sortează o listă de numere”, ce conține configurația finală?"
      choices:
        - text: "Lista sortată (ieșirea)"
          correct: true
          feedback: "Corect — ieșirea (lista sortată) se reprezintă în configurația finală, conform p.7."
        - text: "Lista nesortată inițială (intrarea)"
          correct: false
          feedback: "Nu — lista nesortată este intrarea, în configurația de start; configurația finală conține ieșirea sortată."
      numeric_answer: null
      numeric_tolerance: null
```

- [ ] **Step 3: Author `beats` into `pa-kc-003.yaml`** (definition-taxonomy). Derives from this KC's quotes (p.8: "There are various ways to describe an algorithm:"; "pseudo-code, combines a formal notation with a informal one") and `self_explanation_prompt` / `far_transfer_stem`. The lecture p.8 lists the modes (informal: natural language; formal: mathematical notation / programming languages; semiformal: pseudo-code / graphical notation) — those category names are in the source and may be used. Append to the END of the file:

```yaml
# ─────────────────────────────────────────────────────────────────────────────
# Plan-3 Task 7 — 5-beat teaching content (RO). concept_type=definition-taxonomy.
# PROVENANCE: pa-lecture-01 p.8 — "There are various ways to describe an algorithm:" with the
#   listed modes (informal: natural language; formal: mathematical notation / programming
#   languages; semiformal: pseudo-code «combines a formal notation with a informal one»,
#   graphical notation) + this KC's self_explanation_prompt / far_transfer_stem.
# ZERO new subject facts (the mode names are listed verbatim on p.8). EN spans framed RO §8.1.
# ─────────────────────────────────────────────────────────────────────────────
beats:
  ro:
    predict:
      # ① classify-an-example-first: place a concrete description into the p.8 taxonomy.
      prompt: "Cursul enumeră mai multe moduri de a descrie un algoritm. În ce categorie intră o descriere în limbaj natural („mai întâi citești numărul, apoi îl afișezi”)?"
      options:
        - text: "Mod informal (limbaj natural)"
          callback: "Exact — p.8 listează limbajul natural drept modul informal."
          correct: true
        - text: "Mod formal (notație matematică sau limbaj de programare)"
          callback: "Nu — formal înseamnă notație matematică (mașini Turing, lambda-calcul) sau limbaje de programare cu sintaxă și semantică formale."
          correct: false
        - text: "Nu este niciun mod valid de descriere"
          callback: "Ba da — limbajul natural este explicit modul informal din p.8."
          correct: false
    attempt:
      # ② choice attempt: classify pseudo-code, both-path feedback from the p.8 pseudo-code quote.
      statement: "Pseudo-codul, care „combină o notație formală cu una informală”, în ce categorie din curs intră?"
      choices:
        - text: "Semiformal"
          correct: true
          feedback: "Corect — p.8 pune pseudo-codul la categoria semiformală, fiindcă îmbină formalul cu informalul."
        - text: "Pur formal"
          correct: false
          feedback: "Nu chiar: pseudo-codul combină o notație formală CU una informală, deci este semiformal, nu pur formal."
      feedback_correct: "Bun — ai prins de ce pseudo-codul stă între formal și informal."
    reveal:
      # ③ stepped reveal — the three categories of p.8, callouts frame the EN source spans.
      steps:
        - text: "Există mai multe moduri de a descrie un algoritm."
          callout: "Cursul spune: «There are various ways to describe an algorithm»."
        - text: "Modul informal folosește limbajul natural; modul formal folosește notație matematică sau limbaje de programare cu sintaxă și semantică formale."
          callout: "p.8 listează: informal = natural language; formal = mathematical notation / programming languages."
        - text: "Pseudo-codul este semiformal: combină o notație formală cu una informală."
          callout: "«pseudo-code, combines a formal notation with a informal one» — de aceea e semiformal."
      figure: null
    name:
      definition: "Un algoritm poate fi descris în mai multe moduri: informal (limbaj natural), formal (notație matematică sau limbaje de programare cu sintaxă și semantică formale) și semiformal (pseudo-cod, notație grafică)."
      invariant_statement: "Pseudo-codul este semiformal pentru că îmbină o parte formală cu una informală — nu este nici pur formal, nici pur informal."
      why_matters: "Alegi modul de descriere după cât de precis trebuie să fii: informal pentru intuiție, formal pentru demonstrații, pseudo-cod ca echilibru."
    check:
      # ⑤ different-instance: classify a programming-language description (a different mode than ①②).
      item_stem: "O descriere a algoritmului scrisă într-un limbaj de programare cu sintaxă și semantică formale, în ce categorie din curs intră?"
      choices:
        - text: "Mod formal"
          correct: true
          feedback: "Corect — p.8 pune limbajele de programare (cu sintaxă și semantică formale) la categoria formală."
        - text: "Mod informal"
          correct: false
          feedback: "Nu — informal înseamnă limbaj natural; un limbaj de programare cu sintaxă și semantică formale este formal."
      numeric_answer: null
      numeric_tolerance: null
```

- [ ] **Step 4: Author `beats` into `pa-kc-004.yaml`** (PROOF variant). Derives from this KC's quotes (p.10: "To prove that there is no algorithm that solve this problem, we need a formal definition for algorithm!"; p.11: "The formal definition for an algorithm was introduced using various computational models:"; "All these models are computationally equivalent.") and `self_explanation_prompt` / `far_transfer_stem`. The proof skeleton is the argument chain need-formal-def → models-introduced → models-equivalent → conclusion. Append to the END of the file:

```yaml
# ─────────────────────────────────────────────────────────────────────────────
# Plan-3 Task 7 — 5-beat teaching content (RO). concept_type=proof (proof variant: ② supply
#   the next named sub-step from choices; ③ proof-skeleton reveal, one named sub-step per step).
# PROVENANCE: pa-lecture-01 p.10-11 — "To prove that there is no algorithm that solve this problem,
#   we need a formal definition for algorithm!" + "The formal definition for an algorithm was
#   introduced using various computational models:" + "All these models are computationally
#   equivalent." + this KC's self_explanation_prompt / far_transfer_stem.
# ZERO new subject facts. EN spans framed RO §8.1. (Reviewer: re-derive each claim.)
# ─────────────────────────────────────────────────────────────────────────────
beats:
  ro:
    predict:
      # ① classify which step comes FIRST in the argument (proof-variant predict on the argument).
      prompt: "Vrei să demonstrezi că NU există un algoritm care rezolvă o anumită problemă. Care este, conform cursului, primul lucru de care ai nevoie?"
      options:
        - text: "O definiție formală a algoritmului"
          callback: "Exact — cursul spune că pentru a demonstra că nu există un algoritm ai nevoie întâi de o definiție formală."
          correct: true
        - text: "Un calculator mai rapid"
          callback: "Nu — viteza nu are legătură; problema este că noțiunea intuitivă de algoritm nu permite o demonstrație."
          correct: false
        - text: "Un singur exemplu de algoritm care eșuează"
          callback: "Nu — un exemplu nu demonstrează inexistența; ai nevoie de o definiție formală pentru a raționa despre TOȚI algoritmii."
          correct: false
    attempt:
      # ② supply-the-next-named-sub-step (proof variant). Given "need a formal definition", what's next?
      statement: "După ce admiți că ai nevoie de o definiție formală a algoritmului, care este următorul pas din argumentul cursului?"
      choices:
        - text: "Definiția formală se introduce prin diverse modele de calcul (recursive, lambda-calcul, mașini Turing)."
          correct: true
          feedback: "Corect — p.11: definiția formală «was introduced using various computational models»."
        - text: "Se renunță la ideea de a demonstra ceva."
          correct: false
          feedback: "Nu — argumentul continuă: definiția formală se obține introducând modele de calcul, nu abandonând demonstrația."
      feedback_correct: "Bun — ai trecut corect de la „avem nevoie de o definiție formală” la „o introducem prin modele de calcul”."
    reveal:
      # ③ proof-skeleton reveal: need-formal-def -> models-introduced -> models-equivalent -> conclusion.
      steps:
        - text: "Pas 1 — Pentru a demonstra că NU există un algoritm care rezolvă o problemă, ai nevoie de o definiție formală a algoritmului."
          callout: "Cursul spune: «To prove that there is no algorithm ... we need a formal definition for algorithm!» (intuiția nu permite o astfel de demonstrație)."
        - text: "Pas 2 — Definiția formală a fost introdusă prin diverse modele de calcul (funcții recursive, lambda-calcul, mașini Turing)."
          callout: "«The formal definition for an algorithm was introduced using various computational models»."
        - text: "Pas 3 — Toate aceste modele sunt echivalente din punct de vedere computațional."
          callout: "«All these models are computationally equivalent» — deci nu contează pe care îl alegi."
        - text: "Concluzie — Având o definiție formală (și echivalentă între modele), poți raționa riguros despre existența sau inexistența unui algoritm."
          callout: "Echivalența modelelor face concluzia independentă de modelul ales."
      figure: null
    name:
      definition: "Pentru a demonstra că o problemă NU este rezolvabilă algoritmic, ai nevoie de o definiție formală a algoritmului; aceasta a fost introdusă prin mai multe modele de calcul, toate echivalente computațional."
      invariant_statement: "Pentru că modelele de calcul sunt echivalente, concluzia despre rezolvabilitatea algoritmică nu depinde de modelul ales."
      why_matters: "Doar cu o definiție formală poți demonstra riguros că ceva NU se poate calcula — intuiția singură nu ajunge."
    check:
      # ⑤ different-instance application of the far_transfer_stem (model choice doesn't change solvability).
      item_stem: "Dacă demonstrezi cu mașini Turing că o problemă nu are algoritm, ce poți spune despre rezolvarea ei cu lambda-calcul?"
      choices:
        - text: "Tot nu are algoritm — modelele sunt echivalente computațional"
          correct: true
          feedback: "Corect — fiindcă toate modelele sunt echivalente computațional, inexistența se transferă la orice model."
        - text: "Ar putea avea algoritm în lambda-calcul, e alt model"
          correct: false
          feedback: "Nu — cursul afirmă că toate modelele sunt echivalente computațional, deci mulțimea problemelor rezolvabile este aceeași."
      numeric_answer: null
      numeric_tolerance: null
```

- [ ] **Step 5: ADVERSARIAL REVIEW (mandatory STOP-gate before any test).** Dispatch a reviewer subagent (Sonnet) with this exact charge: *"For each of the 4 YAMLs `content/PA/kcs/pa-kc-001..004.yaml`, read ONLY that KC's `source[*].quote`, `name_ro`, `explanation_ro`, `worked_example_ro`, `self_explanation_prompt`, `far_transfer_stem`, plus `content/PA/_sources/pa-lecture-01.md`. For EVERY string under `beats.ro` (prompt, every option text+callback, attempt statement+choices+feedback, every reveal step text+callout, name definition+invariant+why, check stem+choices+feedback), point to the exact source field/quote it restates. Flag (a) any subject fact NOT present in the source, (b) any EN span left unframed, (c) any RO string missing diacritics, (d) any `correct: true` whose option contradicts the source. Output a per-string provenance table; ANY (a)/(d) = STOP."* If the reviewer flags (a) or (d), fix the YAML against the source and re-run the review — do NOT proceed to tests with an unresolved provenance gap (fix-claim discipline: an authored fact with no source is exactly the trust violation this corpus exists to prevent).

- [ ] **Step 6: Write the unit-level INV-3.2b completeness test** (the 4 authored beat sets must each pass `isCompleteFor`). Create `src/test/kotlin/jarvis/content/AuthoredBeatsCompletenessTest.kt`:

```kotlin
package jarvis.content

import jarvis.tutor.verify.TrustInvariantsCli
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 7 — the 4 faithful PA KCs (pa-kc-001..004) now carry authored ro beats; each must be
 * STRUCTURALLY COMPLETE for its concept_type (KcBeats.isCompleteFor) so the lesson handler (Task 3)
 * serves them and Plan-2 INV-3.2b stays green. Runs the SAME pure leg the trustInvariants CLI runs
 * (TrustInvariantsCli.checkKcs) over the loaded REAL corpus — not a fixture (phase-done = acceptance
 * on the real corpus, fix_claim_discipline). The other 4 PA KCs (005/006 + 2 fixtures) have NO beats
 * yet, which is allowed (empty beats skip the completeness check, Plan-2 §0.8 #5).
 */
class AuthoredBeatsCompletenessTest {

    private val repo = ContentRepo(Path.of("content"))
    private val paKcs by lazy { repo.loadSubject("PA").kcs.associateBy { it.id } }

    @Test fun `pa-kc-001 to 004 each carry complete ro beats for their concept_type`() {
        for (id in listOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")) {
            val kc = paKcs[id] ?: error("$id missing from content/PA/kcs")
            val type = ConceptType.fromWire(kc.concept_type ?: "")
                ?: error("$id has missing/invalid concept_type=${kc.concept_type}")
            val ro = kc.beats["ro"] ?: error("$id has no ro beats")
            assertTrue(ro.isCompleteFor(type), "$id ro beats incomplete for $type")
        }
    }

    @Test fun `the full PA corpus passes the INV-3_2b authored-beats leg`() {
        // checkKcs runs INV-3.2a (concept_type valid) + INV-3.2b (authored beats complete) — DB-free.
        val failures = TrustInvariantsCli.checkKcs(repo.loadSubject("PA").kcs)
        assertEquals(
            emptyList(), failures,
            "INV-3.2a/3.2b failures over the real PA corpus: $failures",
        )
    }
}
```

- [ ] **Step 7: Write the additive INV-5.5 figure-binding validation test FIRST** (red), then implement the check. Create `src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt`:

```kotlin
package jarvis.content

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 7 (spec §5.5 / INV-5.5) — every figure binding in a served beat reveal must resolve to
 * an existing viz instance row. Additive author/audit check: vacuous-GREEN for the 4 Task-7 beat sets
 * (they bind NO figure, locked decision §0.6 #1), loud-RED on a dangling family_id/instance_id. The
 * family REGISTRY check (family_id registered) lands with the family registry in Task 8; this check
 * covers the instance-existence half (instance_id resolves to a content/{subject}/viz row).
 */
class FigureBindingValidationTest {

    private fun kcWithFigure(id: String, fb: FigureBinding?) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro", name_en = "en", cluster = "c",
        bloom_level = "understand", difficulty = 1, time_minutes = 10, exam_weight = 0.0,
        tier = 1, source = emptyList(), version = 1, concept_type = "definition-taxonomy",
        beats = mapOf(
            "ro" to KcBeats(
                reveal = BeatReveal(steps = listOf(RevealStep("t", "c")), figure = fb),
            ),
        ),
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs = kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    @Test fun `a KC with no figure binding is vacuously valid`() {
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", null))) { emptySet() }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a figure binding to an existing instance passes`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-pa-mergesort-001")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            setOf("viz-pa-mergesort-001")
        }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a dangling instance_id is an error naming the KC and the missing instance`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-does-not-exist")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            setOf("viz-pa-mergesort-001")
        }
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("error", it.severity)
        assertEquals("figure_binding", it.rule)
        assertTrue(it.detail.contains("k1"), "names the KC: ${it.detail}")
        assertTrue(it.detail.contains("viz-does-not-exist"), "names the missing instance: ${it.detail}")
    }

    @Test fun `the 4 authored PA beat sets resolve (vacuous-green on the real corpus)`() {
        val repo = ContentRepo(Path.of("content"))
        val sub = repo.loadSubject("PA")
        // The real viz instance ids for PA (empty today; the MergeSort instance lands in Task 8).
        val knownInstances = repo.loadVizInstances("PA").map { it.id }.toSet()
        val issues = ContentValidator.checkFigureBindings(sub) { knownInstances }
        assertTrue(issues.isEmpty(), "Task-7 beat sets bind no figure → must be vacuous-green: $issues")
    }
}
```

- [ ] **Step 8: Run — expect RED** (compile failure: `checkFigureBindings` unresolved):

```powershell
gradle :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: checkFigureBindings`.

- [ ] **Step 9: Implement the additive INV-5.5 check in `ContentValidator.kt`.** Add the function (parameterized on a `knownInstanceIds` lookup so it unit-tests without disk, and is fed the real viz instances by the caller). Insert it after `checkConceptTypeEnum` (added Plan-2 Task 1):

```kotlin
    /**
     * Plan-3 Task 7 (spec §5.5 / INV-5.5) — every FigureBinding in a beat reveal must resolve to a
     * known viz instance id. ADDITIVE author/audit check; vacuous-true when no beat binds a figure
     * (the 4 Task-7 faithful KCs). [knownInstanceIds] returns the set of loaded viz instance ids for
     * the subject (caller passes ContentRepo.loadVizInstances). The family-REGISTRY half (family_id
     * registered in the frontend familyRegistry) is checked at the frontend layer (Task 8) — this is
     * the content-side instance-existence leg. ZERO serve/sync/hash ripple (beats don't feed claimsFor).
     */
    fun checkFigureBindings(
        sub: LoadedSubject,
        knownInstanceIds: (String) -> Set<String>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val known = knownInstanceIds(sub.subject)
        for (kc in sub.kcs) {
            for ((lang, beats) in kc.beats) {
                val fb = beats.reveal?.figure ?: continue
                if (fb.instance_id !in known) {
                    issues += ValidationIssue(
                        severity = "error",
                        rule = "figure_binding",
                        subject = sub.subject,
                        detail = "KC '${kc.id}' (beats[$lang]) binds figure family='${fb.family_id}' " +
                            "instance_id='${fb.instance_id}', which is not a known viz instance " +
                            "(known: ${known.sorted().joinToString(", ").ifEmpty { "<none>" }})",
                    )
                }
            }
        }
        return issues
    }
```

> **Wire-in note:** the corpus-level `validate(...)` entry does NOT call `checkFigureBindings` in this task (it needs the viz-instance set, which `validate`'s current signature doesn't carry — extending `validate`'s signature is out of scope here and risks the `:check`/`validateContent` path). The check ships as a directly-callable audit function exercised by `FigureBindingValidationTest` + the Task-8 family wiring. Folding it into `validate` (with the instance set threaded through) is a Task-8/Plan-4 follow-up; carry it to the plan index.

- [ ] **Step 10: Run the new tests — expect GREEN:**

```powershell
gradle :test --tests "jarvis.content.FigureBindingValidationTest" --tests "jarvis.content.AuthoredBeatsCompletenessTest"
```

Expected: `BUILD SUCCESSFUL`; FigureBindingValidationTest (4) + AuthoredBeatsCompletenessTest (2) all pass.

- [ ] **Step 11: Re-prove hash stability — the Plan-2 `ConceptTypeHashStabilityTest` must STILL pass** (beats do NOT feed `claimsFor`, core §0.2/§0.3; this is the machine proof the 4 faithful badges survive authoring):

```powershell
gradle :test --tests "jarvis.content.ConceptTypeHashStabilityTest"
```

Expected: `BUILD SUCCESSFUL` — all 6 production content_hashes (`ca05c671 / 56156563 / d5363220 / 817aaf44 / 1b67adce / 6af8f795`) byte-identical after adding `beats`. **STOP if it reds** — a hash drift means `claimsFor` was somehow touched or the YAML edit corrupted a `claimsFor`-read field (source/explanation_ro/worked_example_ro); revert the YAML change and re-author additively.

- [ ] **Step 12: Run the unit-level INV-3.2b over the real corpus exactly as the CLI does, and confirm the YAML parses** (the 4 KCs load + isCompleteFor green is the acceptance-on-real-corpus gate). The full `:check` exercises `validateContent` over the same corpus:

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`. (`:check` loads the real corpus through `ContentValidator.validate` + runs every test incl. the three above. A YAML syntax error in any of the 4 files surfaces here as a kaml decode failure naming the file.)

- [ ] **Step 13: Commit (explicit paths only — never `git add -A`; door demos stay untouched, §0.6 #7):**

```powershell
git add content/PA/kcs/pa-kc-001.yaml content/PA/kcs/pa-kc-002.yaml content/PA/kcs/pa-kc-003.yaml content/PA/kcs/pa-kc-004.yaml src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt src/test/kotlin/jarvis/content/AuthoredBeatsCompletenessTest.kt
git commit -m @'
feat(content): author RO 5-beat teaching content for pa-kc-001..004 (Plan 3 Task 7)

001/002/003 definition-taxonomy (classify-first predict, both-path attempt, fused
stepped reveal, formal name, different-instance check); 004 proof (which-step-first
predict, supply-next-sub-step attempt, proof-skeleton reveal need-formal-def→models→
equivalent→conclusion). Every string RO + diacritics; every claim traced to the KC's
own source quotes (pa-lecture-01) — zero new subject facts, adversarially reviewed.
INV-3.2b green on the real corpus; ConceptTypeHashStabilityTest still green (beats do
NOT feed claimsFor — 4 faithful badges survive). Adds INV-5.5 figure-binding validator
(vacuous-green: the 4 beat sets bind no figure).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

## Task 8 — `GraphTreeFamily` + `familyRegistry` + MergeSort instance + trace-match + semantic invariants + family no-clip

Build the **graph/tree** viz family (spec §5.1-5.4, plan core §0.9G): a `FamilyRenderer` type, a `familyRegistry.ts` with a single `"graph-tree"` entry, and `GraphTreeFamily.tsx` which parses a typed `data_json` payload, lays nodes out deterministically with d3-hierarchy **measuring its own labels** (no-clip by construction, §5.3), anchors the per-step callout to the first highlighted node, and feeds frames into the existing `AlgoStepperShell` (families never implement their own playback, §5.3). Ship the MergeSort instance YAML as the family's verification vehicle (NOT lesson content — locked decision §0.6 #1) and prove it with a vitest trace-match (INV-5.1) + semantic invariants (INV-5.2). This task also adds the ADDITIVE `labels` prop to `AlgoStepperShell` so the lesson surface can pass RO chrome strings while demos keep the current EN defaults.

**Files:**
- Create: `tutor-web/src/components/viz/families/familyRegistry.ts`
- Create: `tutor-web/src/components/viz/families/GraphTreeFamily.tsx`
- Create: `tutor-web/src/components/viz/families/__tests__/GraphTreeFamily.test.tsx`
- Create: `tutor-web/src/components/viz/families/__tests__/mergesortTrace.ts` (test-only reference helper)
- Create: `content/PA/viz/viz-pa-mergesort-001.yaml`
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx` (additive `labels` prop + apply to the 6 chrome literals)
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx` (REQUIRED gallery card mounting `GraphTreeFamily` — the family's user-facing surface; bundle-green ≠ visible, feature-shipped rule)

> **Locked input (recon-verified against `build-review/viz-fork-demo/lectie.html:142,148-173`):** the demo's MergeSort input is the array **`[5,2,8,1,9,3]`** (6 elements; `lectie.html:142` and `:148`), and its `maxDepth` is **3** (`log₂6 ≈ 2.58 → tree height 3`, matching the `:147` check feedback "log₂8 = 3 niveluri" framing on the count step). The SHIPPED family step list is: 4 DIVIDE states (`d=0,1,2,3`), 1 PAUSE/count state (`divideState(maxDepth)` with the "numără nivelurile" callout), 2 MERGE states (`m=2,1`), 1 FINAL/root-merge state (`mergeState(0)`) = **8 steps total** (PM-LOCKED — see the Step-6 reconciliation note). The m=0 root merge IS the final step, not a separate merge; the live demo's `pas N/9` counts a play-button reset the family does NOT count. The plan-core §0.9G says "**8 family steps**" (canonical); any older paraphrase mentioning "9 steps" or "`[5,3,8,1]` · 3 divide + pause + 4 merge + final" is SUPERSEDED by this 8-step lock. The trace-match test reads the shipped YAML's `data_json` and asserts `frames.length === mergesortReference(input).length` (= 8) so the count cannot silently drift (consistency#3). Every callout string below is copied verbatim from `lectie.html:170-173` (Romanian, diacritics preserved).

- [ ] **Step 1: Write the failing family + trace-match + semantic-invariant test.** First create the test-only reference helper `tutor-web/src/components/viz/families/__tests__/mergesortTrace.ts` (an INDEPENDENT mergesort that recomputes the expected per-step state from the input — the test must NOT import the instance's baked frames as its own oracle):

```ts
/**
 * Plan-3 Task 8 (INV-5.1 / INV-5.2) — an INDEPENDENT mergesort reference, used by the
 * trace-match test to recompute the expected per-step rendered state from the raw input
 * array. This is the ORACLE: the test asserts the family's frames equal what this computes,
 * so the family cannot animate something mergesort does not actually do.
 *
 * The model mirrors the demo's divide-then-merge sweep (lectie.html:167-173):
 *   - DIVIDE level d: every leaf value sits under its depth-min(d, leafDepth) ancestor,
 *     shown sorted iff leafDepth <= d.
 *   - PAUSE: same as DIVIDE at maxDepth (the "count the levels" beat).
 *   - MERGE level m: every value sits under its depth-min(m, leafDepth) ancestor, in SORTED
 *     order, sorted-styled.
 * A "node label" in the rendered figure is the space-joined values currently grouped at a
 * tree node; the family renders one group-box per occupied node. The reference returns, per
 * step, the set of node labels (sorted concat strings) and which are highlighted.
 */
export type RefStep = { kind: "divide" | "pause" | "merge" | "final"; labels: string[]; highlight: string[] };

function buildTree(a: number[]): { cells: number[]; sorted: number[]; children?: [any, any]; depth: number }[] {
  // flat list of nodes with depth; mirrors bld() in lectie.html:149
  const out: any[] = [];
  function bld(arr: number[], depth: number): any {
    const node: any = { cells: arr.slice(), sorted: arr.slice().sort((x, y) => x - y), depth };
    out.push(node);
    if (arr.length > 1) {
      const m = Math.floor(arr.length / 2);
      node.children = [bld(arr.slice(0, m), depth + 1), bld(arr.slice(m), depth + 1)];
    }
    return node;
  }
  bld(a, 0);
  return out;
}

/** All distinct groupings (node labels) present at a given divide/merge level. */
function groupsAtLevel(nodes: any[], level: number, sorted: boolean): string[] {
  const seen = new Set<string>();
  for (const n of nodes) {
    if (n.depth !== Math.min(level, n.depth)) continue;
    // a node is "occupied" at this level iff it is at exactly depth==min(level, its depth)
    if (n.depth !== level && n.children) continue; // only the cut frontier shows
    const vals = sorted ? n.sorted : n.cells;
    seen.add(vals.join(" "));
  }
  return [...seen];
}

export function mergesortReference(input: number[]): RefStep[] {
  const nodes = buildTree(input);
  const maxDepth = Math.max(...nodes.map((n) => n.depth));
  const steps: RefStep[] = [];
  // DIVIDE d=0..maxDepth (4 steps for [5,2,8,1,9,3], maxDepth=3).
  for (let d = 0; d <= maxDepth; d++) {
    const labels = frontierLabels(nodes, d, false);
    steps.push({ kind: "divide", labels, highlight: labels });
  }
  // PAUSE (count the levels) — same frontier as the deepest divide (1 step).
  steps.push({ kind: "pause", labels: frontierLabels(nodes, maxDepth, false), highlight: frontierLabels(nodes, maxDepth, false) });
  // MERGE m=maxDepth-1 .. 1 (2 steps for maxDepth=3: m=2, m=1). The m=0 ROOT merge is the FINAL
  // step below — NOT a separate merge — so the total is 8, matching the SHIPPED YAML (Step-6
  // reconciliation / PM-locked count). Emitting m=0 here too would re-create the redundant
  // 9th step that drifted from the YAML (consistency#3).
  for (let m = maxDepth - 1; m >= 1; m--) {
    const labels = frontierLabels(nodes, m, true);
    steps.push({ kind: "merge", labels, highlight: labels });
  }
  // FINAL = the m=0 root merge (the whole array, sorted). 4 + 1 + 2 + 1 = 8 steps total.
  const finalLabels = frontierLabels(nodes, 0, true);
  steps.push({ kind: "final", labels: finalLabels, highlight: finalLabels });
  return steps;
}

/** The visible frontier at a level: each leaf collapses to its depth-min(level, leafDepth) ancestor. */
function frontierLabels(nodes: any[], level: number, sorted: boolean): string[] {
  const leaves = nodes.filter((n) => !n.children);
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const leaf of leaves) {
    // walk up to the ancestor at depth == min(level, leaf.depth)
    let anc = leaf;
    while (anc.depth > Math.min(level, leaf.depth)) anc = parentOf(nodes, anc);
    const key = (sorted ? anc.sorted : anc.cells).join(" ");
    if (!seen.has(key)) { seen.add(key); ordered.push(key); }
  }
  return ordered;
}

function parentOf(nodes: any[], child: any): any {
  return nodes.find((n) => n.children && (n.children[0] === child || n.children[1] === child));
}
```

Then the test `tutor-web/src/components/viz/families/__tests__/GraphTreeFamily.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { parse as parseYaml } from "yaml";
import { GraphTreeFamily, parseGraphTreeData, framesFromGraphTree } from "../GraphTreeFamily";
import { familyRegistry } from "../familyRegistry";
import { mergesortReference } from "./mergesortTrace";
// Raw-import the SHIPPED instance YAML so the test reads the ACTUAL bytes on disk — NOT a
// regenerated copy. A YAML edit that drifts from the family contract now goes red here (the
// previous self-regenerated `buildMergesortData()` copy never touched the YAML, so 8-vs-9 drift
// was invisible — consistency#3). The `?raw` query is Vite's raw-string loader.
import mergesortYamlRaw from "../../../../../content/PA/viz/viz-pa-mergesort-001.yaml?raw";

// Pull the single-quoted data_json string straight out of the shipped instance YAML.
const MERGESORT_INPUT = [5, 2, 8, 1, 9, 3];
const MERGESORT_DATA_JSON: string = (() => {
  const doc = parseYaml(mergesortYamlRaw) as { instance: { data_json: string } };
  const dj = doc?.instance?.data_json;
  if (typeof dj !== "string" || dj.length === 0)
    throw new Error("viz-pa-mergesort-001.yaml: instance.data_json missing or not a string");
  return dj;
})();

describe("familyRegistry", () => {
  it("registers ONLY graph-tree (the one family Plan 3 ships)", () => {
    expect(Object.keys(familyRegistry)).toEqual(["graph-tree"]);
  });
});

describe("GraphTreeFamily — parse guard (INV-5.5 load-error shape)", () => {
  it("parses the SHIPPED well-formed payload (8 steps — locked count, §0.6 / Step-6 reconciliation)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    expect(parsed.nodes.length).toBeGreaterThan(0);
    // 8 steps = 4 divide (d=0..3) + 1 pause + 2 merge (m=2,m=1) + 1 final (m=0 root merge).
    // This pins the SHIPPED YAML's step count; if the YAML drifts, this goes red.
    expect(parsed.steps.length).toBe(8);
  });

  it("a missing nodes field throws naming the instance id and the bad field", () => {
    expect(() => parseGraphTreeData(JSON.stringify({ steps: [] }), "viz-bad-001"))
      .toThrow(/viz-bad-001.*nodes/s);
  });

  it("a step missing highlight throws naming the instance id and the bad field", () => {
    const bad = JSON.stringify({ nodes: [{ id: "a", label: "1" }], steps: [{ deltas: [], callout: "x" }] });
    expect(() => parseGraphTreeData(bad, "viz-bad-002")).toThrow(/viz-bad-002.*highlight/s);
  });
});

describe("GraphTreeFamily — trace-match (INV-5.1) over the MergeSort instance", () => {
  it("every step's highlight + node-label set equals the independent mergesort reference", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    const ref = mergesortReference(MERGESORT_INPUT);
    expect(frames.length).toBe(ref.length);
    for (let i = 0; i < ref.length; i++) {
      const renderedLabels = frames[i].state.activeNodes.map((n) => n.label).sort();
      const renderedHi = [...frames[i].state.highlight].sort();
      expect(renderedLabels, `step ${i} labels`).toEqual([...ref[i].labels].sort());
      expect(renderedHi, `step ${i} highlight (by label)`).toEqual([...ref[i].highlight].sort());
    }
  });
});

describe("GraphTreeFamily — semantic invariants (INV-5.2)", () => {
  it("leaf count is preserved at every step (== input length)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    for (let i = 0; i < frames.length; i++) {
      const totalVals = frames[i].state.activeNodes
        .reduce((acc, n) => acc + n.label.split(/\s+/).filter(Boolean).length, 0);
      expect(totalVals, `step ${i} total values`).toBe(MERGESORT_INPUT.length);
    }
  });

  it("every merged node label equals sorted(its concatenated values)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    // the final step's single node must equal sorted(input)
    const finalNodes = frames[frames.length - 1].state.activeNodes;
    expect(finalNodes.length).toBe(1);
    expect(finalNodes[0].label).toBe([...MERGESORT_INPUT].sort((a, b) => a - b).join(" "));
  });

  it("the final step label is the fully sorted array", () => {
    const ref = mergesortReference(MERGESORT_INPUT);
    expect(ref[ref.length - 1].labels).toEqual([[...MERGESORT_INPUT].sort((a, b) => a - b).join(" ")]);
  });
});

describe("GraphTreeFamily — renders inside the shell without throwing", () => {
  it("mounts via the registry renderer with no runtime error", () => {
    const Renderer = familyRegistry["graph-tree"];
    const { getByTestId } = render(
      <Renderer instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />,
    );
    // AlgoStepperShell root paints with the family's testId prefix
    expect(getByTestId("graph-tree-root")).toBeTruthy();
  });
});
```

> **consistency#3 — the YAML is now the single source of truth for the test.** `MERGESORT_DATA_JSON` is read from the SHIPPED `content/PA/viz/viz-pa-mergesort-001.yaml` (raw import + `yaml.parse` → `instance.data_json`), NOT regenerated. The old self-regenerating `buildMergesortData()` closed loop is DELETED — it never read the YAML, so its 9-step output silently diverged from the YAML's 8 steps. `mergesortReference` (the INDEPENDENT oracle in `mergesortTrace.ts`) is reconciled to 8 steps (m=0 is the FINAL, not a separate merge), and `framesFromGraphTree(parsed)` builds frames from the shipped YAML — so `expect(frames.length).toBe(ref.length)` now genuinely pins YAML ⇄ family ⇄ oracle. A YAML step-count drift goes RED in BOTH the parse test (`toBe(8)`) and the trace-match test.
>
> **Vite raw + yaml deps:** the `?raw` import needs no plugin (Vite/Vitest built-in). The `yaml` package is already a tutor-web dep (used by the curator/content tooling); if `npx vitest` reports `Cannot find module 'yaml'`, add it (`npm --prefix tutor-web i -D yaml`) — but confirm it is absent first (`Select-String -Path tutor-web\package.json -Pattern '"yaml"'`).

- [ ] **Step 2: Run — expect RED** (the family module + its exports don't exist):

```powershell
cd tutor-web ; npx vitest run src/components/viz/families ; cd ..
```

Expected: FAIL with `Cannot find module '../GraphTreeFamily'` / `'../familyRegistry'`.

- [ ] **Step 3: Create the `FamilyRenderer` type + registry** — `tutor-web/src/components/viz/families/familyRegistry.ts`:

```ts
import type { ReactNode } from "react";
import { GraphTreeFamily } from "./GraphTreeFamily";

/**
 * Plan-3 §5.1/§5.3 — a family renders (family_id, instance_data) and NOTHING else. It receives the
 * instance id (for error messages), the raw data_json (the family owns parsing/validation), and the
 * instance language (callouts are instance data, language-keyed). Playback is the shell's, not the
 * family's (§5.3) — the family only supplies frames + a renderFrame.
 *
 * This is a NEW channel (FigureBinding, spec §3.2). The legacy vizRegistry.ts / content/viz-ids.yaml /
 * Kotlin checkVizReferences lockstep is UNTOUCHED (plan core §0.5) — it dies with the V6 retirement.
 */
export type FamilyRendererProps = {
  instanceId: string;
  dataJson: string;
  language: string;
  /** RO chrome strings on the lesson surface; omitted → the shell's EN demo defaults. */
  labels?: import("../AlgoStepperShell").ShellLabels;
};
export type FamilyRenderer = (props: FamilyRendererProps) => ReactNode;

export const familyRegistry: Record<string, FamilyRenderer> = {
  "graph-tree": GraphTreeFamily,
};
```

- [ ] **Step 4: Create `GraphTreeFamily.tsx`.** The parse guard names the instance id + bad field; d3-hierarchy lays out the FULL tree once, label widths measured via a canvas `measureText` (off-DOM, SSR-safe fallback), reserving horizontal space so sibling group-boxes never overlap (no-clip by construction); each step's frame carries the active (frontier) nodes + the highlight set + the callout; the callout is anchored to the FIRST highlighted node's box. Frames feed `AlgoStepperShell`.

`tutor-web/src/components/viz/families/GraphTreeFamily.tsx`:

```tsx
import { useMemo, type ReactNode } from "react";
import { hierarchy, tree as d3tree } from "d3-hierarchy";
import { AlgoStepperShell, type Frame, type ShellLabels } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Typed slots (plan core §0.9G) ─────────────────────────────────────────
export type GtNode = { id: string; label: string; parent?: string };
export type GtDelta = { node: string; label?: string };
export type GtStep = { highlight: string[]; deltas: GtDelta[]; callout: string };
export type GraphTreeData = { nodes: GtNode[]; steps: GtStep[] };

// What a single rendered step exposes to the trace-match test + renderFrame.
export type ActiveNode = { id: string; label: string };
export type GraphTreeState = {
  activeNodes: ActiveNode[];   // the occupied frontier at this step (by current label)
  highlight: string[];         // current LABELS of highlighted nodes (trace-match compares visible
                               // state by label — PM ruling 2026-06-12, Task-8 blocker-2 Option A)
  highlightIds: string[];      // node IDs of highlighted nodes (layout/render lookup only)
  callout: string;
};

/**
 * Hand-rolled validation guard (zod-free per plan core §0.9G). On any structural fault it throws a
 * load error naming the instance id AND the offending field, so a broken instance fails admission
 * loud (INV-5.5) instead of rendering an empty/garbled figure.
 */
export function parseGraphTreeData(dataJson: string, instanceId: string): GraphTreeData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`graph-tree instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;
  if (!Array.isArray(obj.nodes))
    throw new Error(`graph-tree instance '${instanceId}': missing/invalid field 'nodes' (expected array)`);
  if (!Array.isArray(obj.steps))
    throw new Error(`graph-tree instance '${instanceId}': missing/invalid field 'steps' (expected array)`);
  const ids = new Set<string>();
  obj.nodes.forEach((n, i) => {
    const node = n as Record<string, unknown>;
    if (typeof node.id !== "string")
      throw new Error(`graph-tree instance '${instanceId}': nodes[${i}] missing field 'id' (string)`);
    if (typeof node.label !== "string")
      throw new Error(`graph-tree instance '${instanceId}': nodes[${i}] missing field 'label' (string)`);
    ids.add(node.id);
  });
  obj.steps.forEach((s, i) => {
    const step = s as Record<string, unknown>;
    if (!Array.isArray(step.highlight))
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'highlight' (string[])`);
    if (!Array.isArray(step.deltas))
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'deltas' (array)`);
    if (typeof step.callout !== "string")
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'callout' (string)`);
    for (const h of step.highlight as unknown[]) {
      if (typeof h !== "string" || !ids.has(h))
        throw new Error(`graph-tree instance '${instanceId}': steps[${i}].highlight references unknown node '${String(h)}'`);
    }
  });
  return obj as unknown as GraphTreeData;
}

/**
 * Build one Frame per step. The label of a node mutates as `deltas` rename it (merge sorts a group);
 * a node is "active" at step k iff it is in that step's highlight set (the frontier). activeNodes
 * carries the CURRENT label (post-applied deltas up to and including step k).
 */
export function framesFromGraphTree(data: GraphTreeData): Frame<GraphTreeState>[] {
  const labelOf = new Map(data.nodes.map((n) => [n.id, n.label]));
  const frames: Frame<GraphTreeState>[] = [];
  for (const step of data.steps) {
    for (const d of step.deltas) if (d.label != null) labelOf.set(d.node, d.label);
    const activeNodes: ActiveNode[] = step.highlight.map((id) => ({ id, label: labelOf.get(id)! }));
    frames.push({
      state: { activeNodes, highlight: [...step.highlight], callout: step.callout },
      aria: step.callout,
    });
  }
  return frames;
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────
let measureCanvas: { ctx: CanvasRenderingContext2D | null } | null = null;
function measureLabelWidth(text: string, fontPx: number): number {
  // Use an off-DOM 2D context ONLY to measure text extents. This is NOT a canvas FIGURE
  // (the figure is pure SVG); measurement does not violate the SVG-only render policy (§5.6).
  if (typeof document !== "undefined") {
    if (!measureCanvas) {
      const c = document.createElement("canvas");
      measureCanvas = { ctx: c.getContext("2d") };
    }
    const ctx = measureCanvas.ctx;
    if (ctx) {
      ctx.font = `${fontPx}px ${FONT_FAMILY}`;
      return Math.ceil(ctx.measureText(text).width);
    }
  }
  // SSR / no-canvas fallback: monospace ≈ 0.6em per glyph.
  return Math.ceil(text.length * fontPx * 0.6);
}

const SVG_W = 480;
const SVG_H = 360;
const LABEL_FONT = 11;
const BOX_PAD_X = 8;
const BOX_H = 26;
const LEVEL_GAP = 64;

type Laid = { id: string; x: number; y: number; w: number };

function layoutGraphTree(data: GraphTreeData): Map<string, Laid> {
  const byId = new Map(data.nodes.map((n) => [n.id, n]));
  const root = data.nodes.find((n) => !n.parent);
  if (!root) return new Map();
  const widthOf = (id: string) => measureLabelWidth(byId.get(id)!.label, LABEL_FONT) + BOX_PAD_X * 2;
  const built = hierarchy<GtNode>(root, (n) =>
    data.nodes.filter((c) => c.parent === n.id),
  );
  // nodeSize x = the widest label across the tree (reserves space so boxes never overlap).
  const maxW = Math.max(...data.nodes.map((n) => widthOf(n.id)), 1);
  const laidRoot = d3tree<GtNode>().nodeSize([maxW + 12, LEVEL_GAP])(built);
  const xs = laidRoot.descendants().map((d) => d.x);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const span = Math.max(maxX - minX, 1);
  const out = new Map<string, Laid>();
  for (const d of laidRoot.descendants()) {
    // normalize x into the canvas with a margin so the widest box at the edge cannot clip.
    const margin = maxW / 2 + 12;
    const usable = SVG_W - margin * 2;
    const nx = margin + ((d.x - minX) / span) * usable;
    out.set(d.data.id, { id: d.data.id, x: nx, y: 28 + d.depth * LEVEL_GAP, w: widthOf(d.data.id) });
  }
  return out;
}

function renderFrame(layout: Map<string, Laid>, labelOf: Map<string, string>) {
  return (frame: Frame<GraphTreeState>): ReactNode => {
    const hi = new Set(frame.state.highlight);
    const firstHi = frame.state.highlight[0];
    const calloutAnchor = firstHi ? layout.get(firstHi) : undefined;
    return (
      <>
        {frame.state.activeNodes.map((n) => {
          const pos = layout.get(n.id);
          if (!pos) return null;
          const isHi = hi.has(n.id);
          const w = labelWidthFor(labelOf.get(n.id) ?? n.label);
          return (
            <g key={n.id} transform={`translate(${pos.x},${pos.y})`}>
              <rect
                x={-w / 2}
                y={-BOX_H / 2}
                width={w}
                height={BOX_H}
                fill={isHi ? ACCENT : "#fff"}
                stroke={INK}
                strokeWidth={isHi ? 2 : 1}
              />
              <text
                x={0}
                y={4}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={LABEL_FONT}
                fontWeight={700}
                fill={INK}
              >
                {labelOf.get(n.id) ?? n.label}
              </text>
            </g>
          );
        })}
        {/* Callout ANCHORED to the first highlighted node's box (§5.3 — never a detached footer). */}
        {calloutAnchor && (
          <text
            x={Math.max(8, Math.min(SVG_W - 8, calloutAnchor.x))}
            y={Math.min(SVG_H - 10, calloutAnchor.y + BOX_H / 2 + 16)}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={10}
            fill={INK}
          >
            {frame.state.callout}
          </text>
        )}
      </>
    );

    function labelWidthFor(t: string): number {
      return measureLabelWidth(t, LABEL_FONT) + BOX_PAD_X * 2;
    }
  };
}

export function GraphTreeFamily({ instanceId, dataJson, language, labels }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseGraphTreeData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromGraphTree(data), [data]);
  const layout = useMemo(() => layoutGraphTree(data), [data]);
  // labelOf evolves with deltas; recompute the final-per-node map so renderFrame shows live labels.
  const labelOf = useMemo(() => {
    const m = new Map(data.nodes.map((n) => [n.id, n.label]));
    return m;
  }, [data]);
  return (
    <AlgoStepperShell<GraphTreeState>
      title={`Graph/tree · ${instanceId}`}
      desc={language === "ro" ? "Arbore de descompunere; pas cu pas." : "Decomposition tree; step by step."}
      frames={frames}
      renderFrame={renderFrame(layout, labelOf)}
      testIdPrefix="graph-tree"
      labels={labels}
    />
  );
}
```

> Note: `renderFrame` re-derives live labels per-frame from `data` via the closed-over `labelOf` only as a fallback; the authoritative per-step label is already in `frame.state.activeNodes[].label` (built by `framesFromGraphTree`, which applies deltas cumulatively). The test asserts against `frame.state`, which is the contract surface.

- [ ] **Step 5: Add the additive `labels` prop to `AlgoStepperShell`.** This is ADDITIVE — defaults reproduce the current EN literals so the demo/gallery mounts (`RecursionTree`, `MatrixTransform`, `VizDemoPage`) do not churn. Show the exact hunks.

5a. Add the `ShellLabels` type + `labels` prop. In `tutor-web/src/components/viz/AlgoStepperShell.tsx`, find:

```tsx
export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGates?: Map<number, PredictionGate>;
  voiceMap?: Record<number, string>;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
  initialStep?: number;
```

Replace with:

```tsx
/** Plan-3 §8.2 — chrome labels, ADDITIVE. Omitted fields fall back to the current EN literals so
 *  the demo gallery is unchanged; the lesson surface passes RO via lessonStrings. */
export interface ShellLabels {
  frame?: string;   // "Frame"
  reset?: string;   // "reset"
  share?: string;   // "🔗 share"
  voiceOn?: string;  // "🔊 voice on"
  voiceOff?: string; // "🔇 voice off"
  predict?: string;  // "⚡ Predict"
}

export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGates?: Map<number, PredictionGate>;
  voiceMap?: Record<number, string>;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
  /** Plan-3 ADDITIVE — chrome labels; omitted → current EN defaults (demos unchanged). */
  labels?: ShellLabels;
  initialStep?: number;
```

5b. Resolve the labels once inside the component. Find:

```tsx
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;
```

Replace with:

```tsx
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;
  const L = {
    frame: props.labels?.frame ?? "Frame",
    reset: props.labels?.reset ?? "reset",
    share: props.labels?.share ?? "🔗 share",
    voiceOn: props.labels?.voiceOn ?? "🔊 voice on",
    voiceOff: props.labels?.voiceOff ?? "🔇 voice off",
    predict: props.labels?.predict ?? "⚡ Predict",
  };
```

5c. Apply `L` to the 6 chrome literals. Make these exact replacements:

- The `Frame` heading — find `            Frame\n          </div>` and replace `Frame` with `{L.frame}`.
- The reset button label — find `            reset\n          </button>` and replace `reset` with `{L.reset}`.
- The share button label — find `            🔗 share\n          </button>` and replace `🔗 share` with `{L.share}`.
- The voice toggle — find `            {voiceOn ? "🔊 voice on" : "🔇 voice off"}` and replace with `            {voiceOn ? L.voiceOn : L.voiceOff}`.
- The predict heading — find `              ⚡ Predict\n            </div>` and replace `⚡ Predict` with `{L.predict}`.

(The exact surrounding indentation matches `AlgoStepperShell.tsx:321` "Frame", `:371` "reset", `:379` "🔗 share", `:386` voice ternary, `:403` "⚡ Predict" — recon-verified against HEAD `dfe7d5c`.)

- [ ] **Step 6: Create the MergeSort instance YAML.** `content/PA/viz/viz-pa-mergesort-001.yaml`. The `data_json` is a SINGLE-QUOTED JSON string (kaml binds `PedagogicalInstance.data_json` as a String per plan core §0.9D / `VizInstance.kt:14-21`). `method_kc_id` anchors to a real PA KC (`pa-kc-fixture-recursion`, the only `code-trace` KC — this is the verification vehicle's method anchor, NOT a served lesson binding, §0.6 #1).

```yaml
id: viz-pa-mergesort-001
subject: PA
family_id: graph-tree
language: ro
instance:
  method_kc_id: pa-kc-fixture-recursion
  data_json: '{"nodes":[{"id":"n0","label":"5 2 8 1 9 3"},{"id":"n1","label":"5 2 8","parent":"n0"},{"id":"n2","label":"5","parent":"n1"},{"id":"n3","label":"2 8","parent":"n1"},{"id":"n4","label":"2","parent":"n3"},{"id":"n5","label":"8","parent":"n3"},{"id":"n6","label":"1 9 3","parent":"n0"},{"id":"n7","label":"1","parent":"n6"},{"id":"n8","label":"9 3","parent":"n6"},{"id":"n9","label":"9","parent":"n8"},{"id":"n10","label":"3","parent":"n8"}],"steps":[{"highlight":["n0"],"deltas":[],"callout":"vectorul întreg — încă nesortat"},{"highlight":["n1","n6"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n3","n7","n8"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"✋ numără nivelurile: 3 = log₂(6) — de-aici vine „log n\""},{"highlight":["n2","n3","n7","n8"],"deltas":[{"node":"n3","label":"2 8"},{"node":"n8","label":"3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n1","n6"],"deltas":[{"node":"n1","label":"2 5 8"},{"node":"n6","label":"1 3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n0"],"deltas":[{"node":"n0","label":"1 2 3 5 8 9"}],"callout":"3 niveluri × O(n) = O(n log n) ✓ → [1 2 3 5 8 9]"}]}'
```

> **Step-count reconciliation (PM-LOCKED at 8):** the demo array `[5,2,8,1,9,3]` has tree height **3**, so the frontier-per-level sweep is: divide d=0 (1 node) · d=1 (2 nodes) · d=2 (4 nodes, the depth-2 frontier `5 / 2 8 / 1 / 9 3`) · d=3 (6 leaves) · PAUSE · merge m=2 (`2 8` and `9 3` sort) · merge m=1 (`5 2 8`→`2 5 8`, `1 9 3`→`1 3 9`) · final/root merge m=0 (`1 2 3 5 8 9`) = **8 steps**, matching the YAML above (the m=0 root merge IS the final step, not a separate merge; the live demo's `pas N/9` counts the play-button reset as a step, which the family does NOT). This is the LOCKED count: the parse test asserts `parsed.steps.length === 8` AND the trace-match test reads the SHIPPED YAML's `data_json` (raw import, NOT a regenerated copy — consistency#3) and asserts `frames.length === mergesortReference(input).length` where `mergesortReference` is reconciled to emit exactly these 8 steps (m=0 = final). Any future YAML edit that drifts the count goes RED in both tests. The earlier §0.9G prose paraphrase that read "9 steps" is superseded by this 8-step lock (see the §0.9G correction).

- [ ] **Step 7: Mount the family in the demo gallery (REQUIRED — the family's user-facing surface).** Add a `graph-tree` card to `tutor-web/src/components/viz/VizDemoPage.tsx` (the existing `/tutor/viz-demo` gallery route, `main.tsx:48`). This is the ONLY surface where the family actually paints for a human (feature-shipped rule: bundle-green + tests-green ≠ visible — the 2026-05-11 ghost-component class this plan exists to prevent), and it is the mount the Task-9 `family-no-clip.spec.ts` drives. NOT optional / NOT a `test.skip` fallback.

7a. Add the imports. In `VizDemoPage.tsx`, find:

```tsx
import { MatrixTransform } from "./MatrixTransform";
```

Replace with:

```tsx
import { MatrixTransform } from "./MatrixTransform";
import { GraphTreeFamily } from "./families/GraphTreeFamily";
```

7b. Add the data_json constant + the gallery card. The `dataJson` MUST be the SAME single-quoted string shipped in `content/PA/viz/viz-pa-mergesort-001.yaml` (Step 6) — keep them byte-identical (the `family-no-clip.spec.ts` exercises this exact instance). After the `subheadingStyle` const block (before `export function VizDemoPage()`), add:

```tsx
// The viz-pa-mergesort-001 instance data_json (verbatim from content/PA/viz/viz-pa-mergesort-001.yaml).
// Keep byte-identical to the shipped YAML — the family-no-clip e2e drives THIS mount.
const MERGESORT_DATA_JSON =
  '{"nodes":[{"id":"n0","label":"5 2 8 1 9 3"},{"id":"n1","label":"5 2 8","parent":"n0"},{"id":"n2","label":"5","parent":"n1"},{"id":"n3","label":"2 8","parent":"n1"},{"id":"n4","label":"2","parent":"n3"},{"id":"n5","label":"8","parent":"n3"},{"id":"n6","label":"1 9 3","parent":"n0"},{"id":"n7","label":"1","parent":"n6"},{"id":"n8","label":"9 3","parent":"n6"},{"id":"n9","label":"9","parent":"n8"},{"id":"n10","label":"3","parent":"n8"}],"steps":[{"highlight":["n0"],"deltas":[],"callout":"vectorul întreg — încă nesortat"},{"highlight":["n1","n6"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n3","n7","n8"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"↓ ÎMPARTE — fiecare rând nou taie în jumătate"},{"highlight":["n2","n4","n5","n7","n9","n10"],"deltas":[],"callout":"✋ numără nivelurile: 3 = log₂(6) — de-aici vine „log n\\""},{"highlight":["n2","n3","n7","n8"],"deltas":[{"node":"n3","label":"2 8"},{"node":"n8","label":"3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n1","n6"],"deltas":[{"node":"n1","label":"2 5 8"},{"node":"n6","label":"1 3 9"}],"callout":"↑ INTERCLASEAZĂ — atinge toate cele 6 elemente pe nivel → O(n)"},{"highlight":["n0"],"deltas":[{"node":"n0","label":"1 2 3 5 8 9"}],"callout":"3 niveluri × O(n) = O(n log n) ✓ → [1 2 3 5 8 9]"}]}';
```

> **JSON-in-TS-string escaping note:** inside the JSON, the `„log n"` callout ends with an ASCII `"` that must be `\"` in the JSON string AND that backslash must survive into the JS string literal — so it is written `\\"` in the `.ts` source (JS unescapes `\\"`→`\"`, then `JSON.parse` unescapes `\"`→`"`). The YAML (Step 6) is single-quoted so it stores the literal `\"` directly. After pasting, sanity-check: `JSON.parse(MERGESORT_DATA_JSON).steps.length === 8` in a node REPL, or rely on the family's parse guard at render.

Then, inside `VizDemoPage`'s returned JSX, add the card as the FIRST `<section>` after the `<header>` (before the existing `viz-demo-matrix` section). Find:

```tsx
      <section data-testid="viz-demo-matrix" style={tileStyle}>
```

Replace with:

```tsx
      <section data-testid="viz-demo-graph-tree" style={tileStyle}>
        <h2 style={headingStyle}>PA · MergeSort graph-tree family (viz-pa-mergesort-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content, §0.6 #1). d3-hierarchy layout · 8 steps · no-clip by construction.
        </p>
        <GraphTreeFamily instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />
      </section>

      <section data-testid="viz-demo-matrix" style={tileStyle}>
```

> The family renders inside `AlgoStepperShell` with `testIdPrefix="graph-tree"`, so the card exposes `graph-tree-root` / `graph-tree-frame-counter` / `graph-tree-step-fwd` / `graph-tree-step-back` (the testids the `family-no-clip.spec.ts` asserts). `data-testid="viz-demo-graph-tree"` is the section wrapper (consistent with the gallery's other `viz-demo-*` sections).

- [ ] **Step 8: Run the family tests — expect GREEN:**

```powershell
cd tutor-web ; npx vitest run src/components/viz/families ; cd ..
```

Expected: all tests pass (registry, parse guard ×3, trace-match, 3 semantic invariants, render-smoke).

- [ ] **Step 9: Confirm the loader still loads the new instance** (Plan-2 `ContentRepo.loadVizInstances` parse-validates `data_json` — a malformed string would error at load). Run the backend content suite:

```powershell
gradle :test --tests "jarvis.content.*"
```

Expected: `BUILD SUCCESSFUL`. (The Plan-2 `loadVizInstances` test, if present, now also sees a real `content/PA/viz/` directory with one well-formed instance — no longer the empty case.)

- [ ] **Step 10: Full frontend vitest + tsc — never trust a partial run** (review-workflow rule):

```powershell
cd tutor-web ; npx tsc --noEmit ; npm test ; cd ..
```

Expected: `tsc` clean; vitest GREEN except the known pre-existing `tutor-shell-api-contract.spec.ts` e2e red (plan core §0.6 — NOT yours, do not claim it). If any NON-e2e spec is red, STOP and fix.

- [ ] **Step 11: Commit (explicit paths only — NEVER `git add -A`; door files on `main` must not be swept):**

```powershell
git add tutor-web/src/components/viz/families/familyRegistry.ts tutor-web/src/components/viz/families/GraphTreeFamily.tsx tutor-web/src/components/viz/families/__tests__/GraphTreeFamily.test.tsx tutor-web/src/components/viz/families/__tests__/mergesortTrace.ts content/PA/viz/viz-pa-mergesort-001.yaml tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/VizDemoPage.tsx tutor-web/package.json tutor-web/package-lock.json tutor-web/src/vite-raw.d.ts tutor-web/vite.config.ts
# (last 4 PM-ratified 2026-06-12, Task-8 blocker-1: the Step-1 cross-root `?raw` YAML import
#  functionally requires vite server.fs.allow + the ?raw TS declaration + the yaml devDependency)
git commit -m @'
feat(viz): graph-tree family + MergeSort instance + trace-match/semantic invariants (INV-5.1/5.2)

GraphTreeFamily renders (family_id, instance_data) only: hand-rolled data_json
guard (load error names instance + bad field), d3-hierarchy layout that measures
label widths and reserves space (no-clip by construction), callout anchored to the
first highlighted node, frames fed to AlgoStepperShell. familyRegistry = the NEW
FigureBinding channel ("graph-tree" only); legacy vizRegistry/viz-ids.yaml untouched.
viz-pa-mergesort-001 = the family verification vehicle (NOT lesson content, §0.6 #1):
ports the demo's [5,2,8,1,9,3] sweep verbatim. Trace-match (INV-5.1) recomputes the
expected per-step state from an INDEPENDENT mergesort and asserts equality; semantic
invariants (INV-5.2) = leaf-count preservation + merged-label == sorted(children) +
final == sorted(input). AlgoStepperShell gains an ADDITIVE labels prop (EN defaults
keep demos unchanged; RO on the lesson surface). VizDemoPage gains a graph-tree
gallery card mounting the family at /tutor/viz-demo — the family's user-facing
surface (feature-shipped rule; the family-no-clip e2e drives this mount).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## Task 9 — `assertNoClip` helper + lesson-route Playwright suite (§4.7 + interaction smoke + import-ban test)

Land the **no-clip e2e helper** (ported VERBATIM-logic from `audit.viz.mjs:76-125`, plan core §0.9H) into the suite — the standing cardinal-sin gate becomes suite-real here, BEFORE any visual baseline exists (council amendment (c)). Then the lesson-route spec (stub-backend pattern from `tutor-shell-api-contract.spec.ts`) asserts every §4.7 selector + interaction smoke + scrubber-back, the family-no-clip spec renders `GraphTreeFamily` at two viewport heights with the scrubber exercised, and the import-ban vitest (INV-5.4) greps the lesson-facing dirs for `three`/`webgl`/`webgpu`/canvas-figure imports → 0 hits.

**Files:**
- Create: `tutor-web/e2e/helpers/assertNoClip.ts`
- Create: `tutor-web/e2e/lesson-beats.spec.ts`
- Create: `tutor-web/e2e/fixtures/pa-kc-001-beats.json` (VERBATIM from the Task-7 authored `pa-kc-001.yaml` beats)
- Create: `tutor-web/e2e/family-no-clip.spec.ts`
- Create: `tutor-web/src/__tests__/importBan.test.ts` (INV-5.4)

> **Mount dependency (Task 8, not Task 9):** `family-no-clip.spec.ts` drives the `graph-tree` gallery card. That mount lives in `tutor-web/src/components/viz/VizDemoPage.tsx` and is added + committed by **Task 8 Step 7/Step 11** (REQUIRED there) — Task 9 does NOT modify or stage `VizDemoPage.tsx`. If executing Task 9 before Task 8 lands, `family-no-clip.spec.ts` is authored here but its green pass depends on Task 8's mount being on the branch (same cross-task pattern as `lesson-beats.spec.ts` depending on Tasks 6/10).

> **Sequencing note:** the §4.7 lesson selectors are emitted by `BeatOrchestrator` (Task 6) and the route is wired (Task 10). This task's `lesson-beats.spec.ts` is written here but its full pass depends on Tasks 6/10 being on the branch; the `assertNoClip` helper, `family-no-clip.spec.ts` (depends only on Task 8), and `importBan.test.ts` are independently runnable now. If executing strictly task-by-task, the lesson-beats spec is authored here and goes green once Task 10 lands (the executing-plans review checkpoint catches the cross-task dependency — note it in the task PR).

- [ ] **Step 1: Create the no-clip helper** — `tutor-web/e2e/helpers/assertNoClip.ts`. The three checks + thresholds are the SAME as `audit.viz.mjs:76-125` (viewport-width overflow >1px; text clip scroll-vs-client +2px; interactive-overlap area >16px²). It runs `page.evaluate` scoped to a testid, then `expect`s zero findings:

```ts
import { expect, type Page } from "@playwright/test";

/**
 * Plan-3 §0.9H / INV-5.3 — the standing no-clip gate, ported VERBATIM-logic from
 * audit.viz.mjs:76-125 (the three checks + exact thresholds):
 *   1. viewport-width overflow  : element.right > vw+1 OR element.left < -1
 *   2. text clipping            : a clipping element whose scroll{W,H} > client{W,H}+2 and has text
 *   3. interactive overlap      : two non-nested interactives whose intersection area > 16 px²
 *
 * Scoped to a testid subtree. Call at >=2 viewport heights per the cardinal-sin gate
 * (machine-checked: every viz/lesson workflow asserts no clip/overlap, 2+ viewport heights).
 */
export async function assertNoClip(page: Page, scopeTestId: string): Promise<void> {
  const findings = await page.evaluate((tid) => {
    const sec = document.querySelector(`[data-testid="${tid}"]`);
    if (!sec) return { missing: true, viewportOverflow: [], textClips: [], overlaps: [] };
    const out = { missing: false, viewportOverflow: [] as unknown[], textClips: [] as unknown[], overlaps: [] as unknown[] };
    const vw = document.documentElement.clientWidth;
    const visible = (el: Element) => {
      const r = el.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) return false;
      const cs = getComputedStyle(el);
      return cs.display !== "none" && cs.visibility !== "hidden" && parseFloat(cs.opacity || "1") > 0.05;
    };
    const all = Array.from(sec.querySelectorAll("*"));
    // 1. viewport-width overflow (audit.viz.mjs:76-88)
    for (const el of all) {
      if (!visible(el)) continue;
      const r = el.getBoundingClientRect();
      if (r.right > vw + 1 || r.left < -1) {
        out.viewportOverflow.push({
          tag: el.tagName, cls: String(el.getAttribute("class") || "").slice(0, 60),
          left: Math.round(r.left), right: Math.round(r.right), vw,
          text: (el.textContent || "").trim().slice(0, 60),
        });
        if (out.viewportOverflow.length > 8) break;
      }
    }
    // 2. text clipping (audit.viz.mjs:89-106)
    for (const el of all) {
      if (el.namespaceURI && el.namespaceURI.includes("svg")) continue;
      if (!visible(el)) continue;
      const cs = getComputedStyle(el);
      const clipsX = ["hidden", "clip", "scroll", "auto"].includes(cs.overflowX);
      const clipsY = ["hidden", "clip", "scroll", "auto"].includes(cs.overflowY);
      const hasText = Array.from(el.childNodes).some((n) => n.nodeType === 3 && (n.textContent || "").trim());
      if (!hasText) continue;
      if ((clipsX && el.scrollWidth > el.clientWidth + 2) || (clipsY && el.scrollHeight > el.clientHeight + 2)) {
        out.textClips.push({
          tag: el.tagName, text: (el.textContent || "").trim().slice(0, 80),
          sw: el.scrollWidth, cw: el.clientWidth, sh: el.scrollHeight, ch: el.clientHeight,
        });
        if (out.textClips.length > 8) break;
      }
    }
    // 3. interactive-element overlaps (audit.viz.mjs:107-125)
    const inter = Array.from(sec.querySelectorAll('button, a, input, select, [role="button"], [role="slider"]')).filter(visible);
    for (let i = 0; i < inter.length; i++) {
      for (let j = i + 1; j < inter.length; j++) {
        const a = inter[i], c = inter[j];
        if (a.contains(c) || c.contains(a)) continue;
        const ra = a.getBoundingClientRect(), rc = c.getBoundingClientRect();
        const w = Math.min(ra.right, rc.right) - Math.max(ra.left, rc.left);
        const h = Math.min(ra.bottom, rc.bottom) - Math.max(ra.top, rc.top);
        if (w > 2 && h > 2 && w * h > 16) {
          out.overlaps.push({
            a: `${a.tagName}:${(a.getAttribute("data-testid") || a.textContent || "").trim().slice(0, 40)}`,
            b: `${c.tagName}:${(c.getAttribute("data-testid") || c.textContent || "").trim().slice(0, 40)}`,
            area: Math.round(w * h),
          });
          if (out.overlaps.length > 6) break;
        }
      }
    }
    return out;
  }, scopeTestId);

  expect(findings.missing, `no-clip scope '${scopeTestId}' not found in DOM`).toBe(false);
  expect(findings.viewportOverflow, `viewport overflow in '${scopeTestId}':\n${JSON.stringify(findings.viewportOverflow, null, 2)}`).toEqual([]);
  expect(findings.textClips, `text clipping in '${scopeTestId}':\n${JSON.stringify(findings.textClips, null, 2)}`).toEqual([]);
  expect(findings.overlaps, `interactive overlap in '${scopeTestId}':\n${JSON.stringify(findings.overlaps, null, 2)}`).toEqual([]);
}
```

- [ ] **Step 2: Create the beats fixture.** `tutor-web/e2e/fixtures/pa-kc-001-beats.json` is the COMPLETE `ApiLessonReply` (with `beats`) that the Task-4 server would serve for `pa-kc-001` — definition-taxonomy → FULL plan ①②③④⑤, choices variant, no figure. The `beats` sub-object below is **copied VERBATIM from the Task-7 authored `content/PA/kcs/pa-kc-001.yaml` `beats.ro` block** (predict/attempt/reveal/name/check strings byte-identical to Task 7 Step 1). The top-level mapped fields are derived from the SAME authored source per the Task-4 server mapping: `prediction_options` = the predict beat's `options[].text`; `definition_ro` = the name beat's `definition`; `echo_source_ro` = the KC's p.4 source quote (`source[0].quote`); `concrete_question_ro` = the predict prompt; `explanation_ro` / `worked_example_ro` = the KC's pre-existing authored fields.

> **VERBATIM-PIN (drift guard, replaces the old false provenance claim).** This fixture's `beats` strings MUST equal the Task-7 `pa-kc-001.yaml` `beats.ro` strings byte-for-byte. The previous version of this fixture CLAIMED "Verbatim RO content from Task 7" while shipping entirely different invented content (predict "Care dintre acestea descrie o succesiune de pași?" with options recipe/number/colour vs. Task-7's "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?") — a provenance lie in a trust-net repo, AND a fixture that exercised content the production server would never serve. To keep them locked, add a one-time check step: after authoring, diff the fixture's `beats` against the YAML (`Select-String`/manual compare of the 5 beats), OR add the assertion noted in Step 3 (`fixture.beats.predict.prompt` === the exact Task-7 prompt). If Task 7 edits the authored beats, this fixture MUST be re-synced in the same change.

```json
{
  "kcId": "pa-kc-001",
  "kc_name_ro": "Noțiunea de algoritm",
  "kc_name_en": "The notion of an algorithm",
  "concrete_question_ro": "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?",
  "echo_source_ro": "An algorithm is a well-ordered collection of unambiguous and effectively computable operations that when executed produces a result and halts in a finite amount of time.",
  "prediction_options": [
    "„Citește a și b, calculează s = a + b, afișează s și oprește-te.”",
    "„Fă-l să arate bine.”",
    "„Repetă pasul la nesfârșit, fără condiție de oprire.”"
  ],
  "term_ro": "Algoritm",
  "definition_ro": "Algoritm = o colecție bine ordonată de operații neambigue și efectiv calculabile care, atunci când sunt executate, produc un rezultat și se opresc într-un timp finit.",
  "explanation_ro": "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile care, executate, produc un rezultat și se opresc în timp finit.",
  "worked_example_ro": "Citește a și b, calculează s = a + b, afișează s și oprește-te — pași neambigui, efectiv calculabili, care se termină.",
  "provenance": { "type": "authored", "hasBeenFaithfulChecked": true },
  "beats": {
    "plan": ["predict", "attempt", "reveal", "name", "check"],
    "concept_type": "definition-taxonomy",
    "predict": {
      "prompt": "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?",
      "options": [
        { "text": "„Citește a și b, calculează s = a + b, afișează s și oprește-te.”", "callback": "Exact — pașii sunt neambigui și efectiv calculabili, produce un rezultat și se oprește în timp finit (definiția p.4).", "correct": true },
        { "text": "„Fă-l să arate bine.”", "callback": "Nu — pasul nu este neambiguu; definiția cere operații neambigue și efectiv calculabile.", "correct": false },
        { "text": "„Repetă pasul la nesfârșit, fără condiție de oprire.”", "callback": "Nu — încalcă cerința ca execuția să se oprească într-un timp finit.", "correct": false }
      ]
    },
    "attempt": {
      "statement": "Secvența „cât timp x > 0, afișează x” (fără ca x să se modifice) este un algoritm?",
      "choices": [
        { "text": "Da, este un algoritm", "correct": false, "feedback": "Nu chiar: dacă x nu se schimbă, execuția nu se oprește niciodată — se încalcă cerința de terminare în timp finit din definiția p.4." },
        { "text": "Nu, nu este un algoritm", "correct": true, "feedback": "Corect — fără terminare în timp finit nu îndeplinește definiția din curs, oricât de neambigui ar fi pașii." }
      ],
      "feedback_correct": "Bun — ai folosit chiar condiția de terminare din definiția p.4."
    },
    "reveal": {
      "steps": [
        { "text": "Un algoritm este o colecție bine ordonată de operații.", "callout": "Cursul spune: «a well-ordered collection of ... operations» — adică pașii au o ordine clară." },
        { "text": "Operațiile sunt neambigue și efectiv calculabile.", "callout": "«unambiguous and effectively computable operations» — fiecare pas e clar și poate fi efectiv executat." },
        { "text": "Când este executat, produce un rezultat și se oprește într-un timp finit.", "callout": "«produces a result and halts in a finite amount of time» — execuția nu continuă la nesfârșit." }
      ]
    },
    "name": {
      "definition": "Algoritm = o colecție bine ordonată de operații neambigue și efectiv calculabile care, atunci când sunt executate, produc un rezultat și se opresc într-un timp finit.",
      "invariant_statement": "Dacă execuția nu se oprește într-un timp finit, secvența nu este un algoritm — oricât de clari ar fi pașii.",
      "why_matters": "Fără terminare nu poți garanta că obții vreodată un rezultat, deci nu poți spune că ai rezolvat problema."
    },
    "check": {
      "item_stem": "O rețetă de bucătărie cu pași clari care se termină după ultimul pas îndeplinește definiția algoritmului din curs?",
      "choices": [
        { "text": "Da — pași neambigui, efectiv executabili, se termină în timp finit", "correct": true, "feedback": "Corect — îndeplinește toate cele trei condiții din definiția p.4." },
        { "text": "Nu — o rețetă nu poate fi niciodată un algoritm", "correct": false, "feedback": "Ba da: dacă pașii sunt neambigui, efectiv executabili și se termină în timp finit, definiția din curs este îndeplinită." }
      ]
    }
  }
}
```

- [ ] **Step 3: Create `lesson-beats.spec.ts`** (stub-backend pattern — `route.fulfill` GET `/api/v1/lesson/*` with the fixture; POST `.../beat` stub returning graded replies). Asserts §4.7: pip count, single active beat, predict 3-4 options, next-gate disabled→enabled per gate, scrubber "pas k/N" + BACK works on the stepped reveal, lesson-complete-handoff at end; interaction smoke (zero 4xx/5xx, zero console errors, full click-through); `assertNoClip` at 1280×900 + 1280×620 on every beat screen.

```ts
import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";
import fixture from "./fixtures/pa-kc-001-beats.json";

// VERBATIM-PIN (specTrust#3): the fixture's beats MUST be byte-identical to the Task-7 authored
// content/PA/kcs/pa-kc-001.yaml beats.ro. If Task 7 edits the authored beats, re-sync this fixture.
// These three anchors catch the common drift (prompt/first-option/check-stem) so the fixture cannot
// silently revert to invented content while claiming source-provenance.
test("fixture is the Task-7 authored pa-kc-001 content (verbatim pin)", () => {
  expect(fixture.beats.predict.prompt).toBe(
    "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?",
  );
  expect(fixture.beats.predict.options[0].text).toBe(
    "„Citește a și b, calculează s = a + b, afișează s și oprește-te.”",
  );
  expect(fixture.beats.check.item_stem).toBe(
    "O rețetă de bucătărie cu pași clari care se termină după ultimul pas îndeplinește definiția algoritmului din curs?",
  );
});

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// A graded reply for any beat POST. predict/attempt: correct=true (so the gate clears);
// check: lesson_complete=true (so the handoff renders).
function gradeReply(beatType: string) {
  return {
    correct: true,
    score: beatType === "check" ? 1.0 : 1.0,
    feedback_ro: "corect",
    beat_type: beatType,
    lesson_complete: beatType === "check",
    first_encounter: true,
    phase: beatType === "check" ? "practice" : null,
    verification_status: beatType === "check" ? "faithful" : null,
  };
}

test("lesson route: §4.7 beats render, gate, scrub-back, complete — zero 4xx/5xx + zero console errors", async ({ page }) => {
  const bad: string[] = [];
  const consoleErrors: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text().slice(0, 200)); });
  page.on("pageerror", (e) => consoleErrors.push(String(e.message).slice(0, 200)));

  await page.route("**/api/v1/lesson/pa-kc-001/beat", (r) => {
    const body = r.request().postDataJSON() as { beat_type: string };
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(gradeReply(body.beat_type)) });
  });
  await page.route("**/api/v1/lesson/pa-kc-001", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(fixture) }),
  );

  await page.goto("/tutor/lesson/pa-kc-001");

  // (1) pips: N == plan length; exactly one active beat
  await expect(page.getByTestId("lesson-beat-pips")).toBeVisible({ timeout: 10000 });
  const pips = page.getByTestId("lesson-beat-pips").locator("[data-pip]");
  await expect(pips).toHaveCount(fixture.beats.plan.length);
  await expect(page.getByTestId("lesson-beat-active")).toHaveCount(1);

  // first paint no-clip at both viewports
  for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "lesson-beat-active"); }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  // (2) ① PREDICT: 3-4 options; next-gate disabled before commit, enabled after
  await expect(page.getByTestId("beat-predict-options").locator("button")).toHaveCount(3);
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  await page.getByTestId("beat-predict-options").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // (3) ② ATTEMPT: next disabled until submitted, then enabled
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // (4) ③ REVEAL: stepped text with a scrubber "pas k/N" + a functioning BACK (one-shot violation gate)
  await expect(page.getByTestId("scrubber-step-counter")).toBeVisible();
  await expect(page.getByTestId("scrubber-step-counter")).toContainText(/pas\s+\d+\/\d+/);
  // step to the final reveal step (forward), then verify BACK actually moves the counter.
  // Locate by the [data-step-fwd]/[data-step-back] DATA ATTRIBUTES, NOT by accessible name:
  // the stepped-text RevealBeat's forward button renders "{lessonStrings.next} ›" = "Continuă ›"
  // (and back = "‹ Înapoi") with NO aria-label — "Continuă" matches none of /înainte|forward|▶/,
  // so a name-regex locator hangs (consistency#2). The data attributes are on both the stepped-text
  // buttons (Task 6 RevealBeat) AND match how BeatOrchestrator.test.tsx / the Task-12/13 mjs drivers
  // locate the scrubber, so this is the robust, drift-proof anchor.
  const fwd = page.getByTestId("beat-figure-scrubber").locator("[data-step-fwd]").first();
  const back = page.getByTestId("beat-figure-scrubber").locator("[data-step-back]").first();
  await fwd.click();
  const counterAfterFwd = await page.getByTestId("scrubber-step-counter").textContent();
  await back.click();
  const counterAfterBack = await page.getByTestId("scrubber-step-counter").textContent();
  expect(counterAfterBack, "BACK must move the step counter (one-shot animation is a gate violation)").not.toBe(counterAfterFwd);
  // step to the final to clear the reveal gate, then advance
  await fwd.click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // ④ NAME: text-only beat, dwell-floored; next enables after the floor
  await expect(page.getByTestId("lesson-beat-active")).toBeVisible();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 7000 });
  await page.getByTestId("beat-next-gate").click();

  // (5) ⑤ CHECK: answer, then lesson-complete-handoff appears
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("lesson-complete-handoff")).toBeVisible({ timeout: 10000 });

  // no-clip on the completion screen, both viewports. Scope to the completion WRAPPER
  // (`lesson-complete`, Task 6) so the heading + body + handoff are all covered. The old
  // `assertNoClip(page,"lesson-beat-active").catch(()=>{})` swallowed a guaranteed "scope not
  // found" (there is NO lesson-beat-active on the completion screen — it only exists mid-lesson),
  // making it a dead assertion; scoping the real check to the handoff BUTTON alone was near-vacuous
  // (a button has no descendants). `lesson-complete` covers the actual completion layout (specTrust#6).
  for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "lesson-complete"); }

  // smoke totals
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx during traversal:\n${bad.join("\n")}`).toEqual([]);
  expect(consoleErrors, `console errors:\n${consoleErrors.join("\n")}`).toEqual([]);
});
```

> The selector names (`lesson-beat-pips` with per-pip `[data-pip]`, `lesson-beat-active`, `beat-predict-options`, `beat-next-gate`, `beat-figure-scrubber`, `scrubber-step-counter`, `lesson-complete-handoff`) are the §4.7 contract (plan core §0.9F). Task 6's `BeatOrchestrator` MUST emit exactly these — if a name drifts, this spec is the enforcement. The scrubber buttons are located by the `[data-step-fwd]` / `[data-step-back]` DATA ATTRIBUTES (Task 6 `RevealBeat`), NOT by accessible name — the stepped-text reveal's RO chrome is "Continuă ›" / "‹ Înapoi" (from `lessonStrings`, no aria-label), which would not match a `forward`/`▶` name regex. Task 6 carries `data-step-fwd`/`data-step-back` on the stepped-text buttons; if the served reveal ever becomes figure-bound (`AlgoStepperShell` scrubber), the same data attributes must be added to the shell's `step-fwd`/`step-back` buttons so this spec keeps matching either path.

- [ ] **Step 4: Create `family-no-clip.spec.ts`** — renders `GraphTreeFamily` with the MergeSort instance at both viewports, scrubber back/forward exercised, `assertNoClip` on the family root. It drives the `/tutor/viz-demo` gallery, where Task 8 Step 7 mounts the `graph-tree` card (`data-testid="viz-demo-graph-tree"` → `GraphTreeFamily` → `graph-tree-root`). The mount is a REQUIRED Task-8 step (not optional), so this spec is UNCONDITIONAL — no `test.skip` fallback. Use the component test-id contract directly:

```ts
import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// The family renders inside AlgoStepperShell with testIdPrefix="graph-tree". The 4 faithful KCs
// carry no figure (§0.6 #1), so the lesson route never shows a figure-bound reveal; the family's
// real user-facing surface is the /tutor/viz-demo gallery card (Task 8 Step 7, REQUIRED mount).
// Route: /tutor/viz-demo paints the gallery; the graph-tree card carries data-testid="graph-tree-root".
test("graph-tree family: no clip at 2 viewports, scrubber back/forward works", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  await page.goto("/tutor/viz-demo");
  await expect(page.getByTestId("graph-tree-root")).toBeVisible({ timeout: 10000 });

  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "graph-tree-root");
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  // scrubber forward then back must change the frame counter (no one-shot)
  const counter = page.getByTestId("graph-tree-frame-counter");
  const start = await counter.textContent();
  await page.getByTestId("graph-tree-step-fwd").click();
  const afterFwd = await counter.textContent();
  expect(afterFwd).not.toBe(start);
  await page.getByTestId("graph-tree-step-back").click();
  expect(await counter.textContent()).toBe(start);

  // re-check no-clip after stepping (layout must not drift into a clip mid-animation)
  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "graph-tree-root");
  }

  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
});
```

> **Mount dependency (RESOLVED in Task 8 — no skip):** this spec needs the MergeSort family visible at a real URL. The `graph-tree` gallery card is mounted by **Task 8 Step 7** (REQUIRED) in `VizDemoPage.tsx` (`/tutor/viz-demo`, `main.tsx:48`) rendering `<GraphTreeFamily instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />` — the family's user-facing surface (feature-shipped rule: bundle-green ≠ visible). The mount is NOT deferred and NOT optional, so this spec is UNCONDITIONALLY enforced — there is NO `test.skip` fallback. If the `graph-tree-root` selector is not visible at `/tutor/viz-demo`, the family was never mounted → the spec fails LOUD (the ghost-component class this plan exists to prevent). Do not add a skip; fix the mount.

- [ ] **Step 5: Create the import-ban vitest (INV-5.4)** — `tutor-web/src/__tests__/importBan.test.ts`. It scans ONLY the lesson-facing dirs (`src/components/lesson/`, `src/components/viz/families/`) for IMPORT specifiers of `three`/`webgl`/`webgpu` and for canvas-figure use (`getContext(`, `<canvas`), NOT the bare English word "three" (which appears in many unrelated comments — recon-verified, e.g. `ForecastDotPlot.tsx`). The two legacy non-lesson canvas-measurement files are allowlisted by EXACT path:

```ts
import { describe, it, expect } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * Plan-3 INV-5.4 (spec §5.6) — SVG-only policy, machine-enforced over the LESSON-FACING dirs.
 * BLOCK three.js / WebGL / WebGPU / canvas-based FIGURES everywhere a learner sees a figure.
 *
 * Scope = lesson + family render code only (the bare word "three"/"canvas" appears in unrelated
 * comments across src/, recon-verified — so we match IMPORT specifiers + canvas-figure APIs, not words).
 * Allowlist = two legacy non-lesson primitives that use an off-DOM canvas for measurement ONLY
 * (not a rendered figure); they are outside the lesson path and predate the family system.
 */
const LESSON_DIRS = [
  "src/components/lesson",
  "src/components/viz/families",
];

// Exact-path allowlist (legacy, non-lesson; SVG figures, canvas used for text measurement only).
const ALLOWLIST = new Set<string>([
  "src/components/viz/SumPlotTracker.tsx",
  "src/components/viz/NumLineDirect.tsx",
]);

// import specifiers that pull a GPU/canvas renderer:
const BANNED_IMPORT = /\bfrom\s+['"](three|three\/.*|@react-three\/.*|.*webgl.*|.*webgpu.*|regl|pixi\.js)['"]/i;
// canvas-FIGURE APIs (rendering, not off-DOM measureText):
const BANNED_CANVAS = /<canvas[\s>]|getContext\(\s*['"](webgl|webgl2|webgpu)['"]/i;

function walk(dir: string, acc: string[] = []): string[] {
  let entries: string[] = [];
  try { entries = readdirSync(dir); } catch { return acc; }
  for (const e of entries) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (/\.(ts|tsx)$/.test(e) && !/\.(test|spec)\.tsx?$/.test(e)) acc.push(p);
  }
  return acc;
}

describe("INV-5.4 import ban (lesson-facing SVG-only)", () => {
  it("no lesson/family source imports three/webgl/webgpu or uses a canvas figure", () => {
    const offenders: string[] = [];
    for (const d of LESSON_DIRS) {
      for (const file of walk(d)) {
        const rel = file.replace(/\\/g, "/");
        if (ALLOWLIST.has(rel)) continue;
        const src = readFileSync(file, "utf8");
        if (BANNED_IMPORT.test(src)) offenders.push(`${rel}: banned renderer import`);
        if (BANNED_CANVAS.test(src)) offenders.push(`${rel}: canvas-figure API`);
      }
    }
    expect(offenders, `SVG-only policy violations:\n${offenders.join("\n")}`).toEqual([]);
  });

  it("the allowlisted files exist (a stale allowlist entry is itself a smell)", () => {
    for (const p of ALLOWLIST) {
      expect(() => statSync(p), `${p} (allowlist entry) must exist`).not.toThrow();
    }
  });
});
```

> **Note on GraphTreeFamily's measure-canvas:** `GraphTreeFamily.tsx` (in `families/`, NOT allowlisted) calls `getContext("2d")` for off-DOM `measureText`. The `BANNED_CANVAS` regex matches `getContext('webgl'|'webgl2'|'webgpu')` only — `getContext("2d")` for measurement is NOT banned (it produces no figure; the figure is SVG). This is deliberate: the policy blocks canvas FIGURES, not text metrics. Verified: the regex does not match `getContext("2d")`.

- [ ] **Step 6: Run the import-ban vitest — expect GREEN:**

```powershell
cd tutor-web ; npx vitest run src/__tests__/importBan.test.ts ; cd ..
```

Expected: 2 tests pass (zero offenders; both allowlist files exist).

- [ ] **Step 7: Run the e2e suite that does NOT depend on Tasks 6/10** (the family-no-clip spec depends only on Task 8; lesson-beats needs Task 10 wired — run it after Task 10):

```powershell
cd tutor-web ; npx playwright test family-no-clip.spec.ts ; cd ..
```

Expected: PASS. (After Task 10 lands, run `npx playwright test lesson-beats.spec.ts` and expect PASS — the full §4.7 gate.)

- [ ] **Step 8: Commit (explicit paths):**

```powershell
git add tutor-web/e2e/helpers/assertNoClip.ts tutor-web/e2e/lesson-beats.spec.ts tutor-web/e2e/fixtures/pa-kc-001-beats.json tutor-web/e2e/family-no-clip.spec.ts tutor-web/src/__tests__/importBan.test.ts
git commit -m @'
test(e2e): no-clip helper + lesson-beats §4.7 gate + family-no-clip + import-ban (INV-5.3/5.4)

assertNoClip ports audit.viz.mjs:76-125 verbatim-logic (viewport overflow / text
clip +2px / interactive overlap >16px²) into the suite — the standing cardinal-sin
gate is now suite-real, before any baseline. lesson-beats.spec.ts (stub-backend
pattern, fixture exported from the Task-7 pa-kc-001 beats) asserts §4.7: pip count,
single active beat, predict 3-4 options, next-gate disabled→enabled per gate,
scrubber "pas k/N" + functioning BACK (one-shot violation gate), complete-handoff;
zero 4xx/5xx + zero console errors; assertNoClip at 1280x900 + 1280x620 on every
beat. family-no-clip.spec.ts exercises GraphTreeFamily at both viewports with
scrubber back/forward. importBan.test.ts (INV-5.4) scopes the three/webgl/webgpu/
canvas-figure ban to lesson-facing dirs (allowlists SumPlotTracker + NumLineDirect
by exact path — legacy non-lesson, measure-only).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## Task 10 — Wires: Oggi `Începe` → lesson · `lesson-complete-handoff` → drill surface (verified) · DELETE `LessonScreen` · shell `labels` prop RO

Wire the two audited dead ends (E10 + E4 handoff) and remove the replaced surface. (1) `OggiScreen`'s `Începe` navigates to `/lesson/{kc_id}` for the selected (or top) queue item. (2) `BeatOrchestrator`'s completion handoff navigates to the drill surface for the same KC — **VERIFIED against the codebase**: there is NO per-KC drill route reachable without a taskId, so the plan-core §0.9F fallback applies. (3) DELETE `LessonScreen.tsx` + its test + the dead route/import (the BeatOrchestrator replaces it — spec §4.1 "replaces", never coexists). Update `new-surfaces-smoke.spec.ts` lesson expectations to the new testids.

**Files:**
- Modify: `tutor-web/src/components/OggiScreen.tsx` (Începe → navigate)
- Modify: `tutor-web/src/main.tsx` (route `/lesson/:kcId` → BeatOrchestrator; drop the `LessonScreen` import)
- Modify: `tutor-web/src/components/lesson/BeatOrchestrator.tsx` (completion handoff → navigate; Task 6 created the component, Task 10 wires the target)
- Delete: `tutor-web/src/components/LessonScreen.tsx`
- Delete: `tutor-web/src/components/LessonScreen.test.tsx`
- Modify: `tutor-web/e2e/new-surfaces-smoke.spec.ts` (lesson test → new testids)

> **Drill-surface target verification (recon-verified against HEAD `dfe7d5c`, do NOT re-derive — this is the ruling):**
> - There is **no per-KC drill route**. The drill surface is `TutorWorkspace`, reached ONLY via a `taskId` (a PDF-backed task): `App.tsx:382` renders `<TutorWorkspace pdfUrl={…/tasks/${taskId}/pdf} taskId={taskId} />` as the dashboard fallback — keyed on a task, not a KC.
> - `/review` renders `FsrsReview` (`App.tsx:367`) — the whole-queue FSRS review, with NO per-KC deep-link parameter.
> - `/oggi` (`OggiScreen` → `LearnerQueueList`) emits per-KC items with `data-testid="queue-item-${kc_id}"` (`LearnerQueueList.tsx:45`, recon-verified).
> - **Conclusion:** the §4.7 "same KC visible" requirement is satisfied by the plan-core §0.9F FALLBACK: the handoff navigates to `/oggi` and the e2e asserts `queue-item-{kcId}` is visible. A dedicated per-KC drill deep-link does NOT exist and is NOT invented in this plan (§0.6 — no new drill surface). Add a follow-up line for the dedicated deep-link.

- [ ] **Step 1: Wire `OggiScreen`'s `Începe`.** The button (`OggiScreen.tsx:150-156`) calls `onBegin`, which is currently `handleSelect` (a no-op tracker, the E10 dead end). Re-point the handoff to navigate to the lesson route for the chosen item's `kc_id`. Use `react-router-dom`'s `useNavigate` under the `/tutor` basename (the route is `/lesson/:kcId`, `main.tsx:51`).

1a. Add the import. Find:

```tsx
import { useEffect, useState } from "react";
import { getQueueToday } from "../lib/taskPrep";
```

Replace with:

```tsx
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getQueueToday } from "../lib/taskPrep";
```

1b. Get `navigate` and route the begin handler to the SELECTED (or top) item. Find:

```tsx
export function OggiScreen() {
  const [state, setState] = useState<State>({ status: "loading" });
  const [selected, setSelected] = useState<QueueItem | null>(null);
```

Replace with:

```tsx
export function OggiScreen() {
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ status: "loading" });
  const [selected, setSelected] = useState<QueueItem | null>(null);
```

1c. Add a begin handler that navigates, and pass it to the panel. Find:

```tsx
  function handleSelect(item: QueueItem) {
    setSelected(item);
    // Future: navigate to drill for this KC. For now just track selection.
  }
```

Replace with:

```tsx
  function handleSelect(item: QueueItem) {
    setSelected(item);
  }

  /** E10 fix: the Începe button launches the gated lesson for this KC. The route is
   *  /lesson/:kcId under the /tutor basename; useNavigate keeps SPA history intact. */
  function handleBegin(item: QueueItem) {
    navigate(`/lesson/${encodeURIComponent(item.kc_id)}`);
  }
```

1d. Re-point the panel's `onBegin` from the no-op `handleSelect` to `handleBegin`. Find:

```tsx
            {(selected ?? topItem) && (
              <NextKcPanel
                item={(selected ?? topItem)!}
                onBegin={() => handleSelect((selected ?? topItem)!)}
              />
            )}
```

Replace with:

```tsx
            {(selected ?? topItem) && (
              <NextKcPanel
                item={(selected ?? topItem)!}
                onBegin={() => handleBegin((selected ?? topItem)!)}
              />
            )}
```

> **Caveat — basename:** `/tutor/lesson/:kcId` originally rendered OUTSIDE the App shell (`main.tsx:51` `LessonScreenRoute`, plan core §0.1 — orphaned, no nav back). Task 6 Step 8 ALREADY re-pointed that route to `BeatOrchestratorRoute` (still standalone for this plan; the in-shell mount is a Plan-4/§8.2 chrome sweep). Task 10's lesson-complete-handoff (Step 3) returns the learner to `/oggi` (inside the App shell), closing the no-nav-back trap for the completion path. Task 10 does NOT re-point the route again — it only drops the dead `LessonScreen` import (Step 4) and switches the wrapper's handoff from hard-nav to router nav (Step 3).

- [ ] **Step 2: Verify there is no per-KC drill route to deep-link** (the ruling above is recon-verified; this step re-confirms before wiring the fallback so a future executor does not silently invent one). Read-only:

```powershell
Select-String -Path tutor-web\src\App.tsx -Pattern 'pathname === "/review"|pathname === "/oggi"|TutorWorkspace|FsrsReview'
Select-String -Path tutor-web\src\components\LearnerQueueList.tsx -Pattern 'data-testid=.\`queue-item-'
```

Expected: `App.tsx` shows `/review` → `FsrsReview` and `/oggi` → `OggiScreen` and `TutorWorkspace` only behind `taskId`; `LearnerQueueList` shows `queue-item-${item.kc_id}`. **STOP if** a new `/drill/:kcId` (or similar per-KC route) now exists — then the fallback is wrong and the handoff should target the real route instead; re-assess.

- [ ] **Step 3: Wire the completion handoff at the route wrapper `BeatOrchestratorRoute` (fallback contract).** Task 6 (Step 8) created `BeatOrchestratorRoute` in `main.tsx` and mounts `<BeatOrchestrator kcId={id} lesson={lesson} onComplete={() => { window.location.href = "/tutor/oggi"; }} />`. The `onComplete` callback IS the handoff seam (§0.9F — `onComplete` is a REQUIRED prop; the Task-6 `lesson-complete-handoff` button calls `onComplete(kcId)` on click; DO NOT move navigation inside `BeatOrchestrator` or the required-prop contract is broken). Task 10 only replaces the wrapper's `window.location.href` hard-nav with a router `navigate("/oggi")` so the handoff is an in-SPA transition (the same KC is visible as `queue-item-{kcId}` at `/oggi`).

3a. Add `useNavigate` to the existing react-router-dom import in `main.tsx` (it already imports `useParams` from there). Find (the real line — recon-verified `main.tsx:5`):

```tsx
import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom";
```

Replace with:

```tsx
import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate } from "react-router-dom";
```

> Do NOT add a second `import … from "react-router-dom"` statement (import/no-duplicates) — append `useNavigate` to the existing line above.

3b. Replace the wrapper's hard-nav `onComplete` with the router navigate. In `BeatOrchestratorRoute` (created by Task 6 Step 8), find:

```tsx
function BeatOrchestratorRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  const id = kcId ?? "";
```

Replace with:

```tsx
function BeatOrchestratorRoute() {
  const { kcId } = useParams<{ kcId: string }>();
  const navigate = useNavigate();
  const id = kcId ?? "";
```

Then find the wrapper's mount line (Task 6 Step 8):

```tsx
  return <BeatOrchestrator kcId={id} lesson={lesson} onComplete={() => { window.location.href = "/tutor/oggi"; }} />;
```

Replace with:

```tsx
  return <BeatOrchestrator kcId={id} lesson={lesson} onComplete={() => navigate("/oggi")} />;
```

> Why `/oggi` and not a drill route: §0.9F fallback contract — no per-KC drill deep-link exists (Step 2). The §4.7 acceptance "lands in the drill surface with the same KC visible" is satisfied by `/oggi` showing `queue-item-{kcId}` (the lesson-beats.spec.ts in Task 9 can be extended, or new-surfaces-smoke covers the handoff). The dedicated per-KC drill deep-link is a CARRIED FOLLOW-UP (Step 7), not built here. The Task-6 `lesson-complete-handoff` button keeps calling `onComplete(kcId)` UNCHANGED — Task 10 changes only what `onComplete` does (hard-nav → router nav), never the button or `BeatOrchestrator` itself.

- [ ] **Step 4: Drop the now-dead `LessonScreen` import from `main.tsx`.** The `/lesson/:kcId` route was ALREADY re-pointed to `BeatOrchestratorRoute` in Task 6 Step 8 — the route element is `element={<BeatOrchestratorRoute />}` and `BeatOrchestrator` is already imported there. Do NOT re-create a `LessonRoute` or emit `<BeatOrchestrator kcId=.../>` without its REQUIRED `lesson`+`onComplete` props (that is a tsc error: `BeatOrchestratorProps` declares all three required, Task 6). Task 10's ONLY `main.tsx` route change is removing the dead `LessonScreen` import that Task 6 deliberately left with a "deleted in Plan-3 Task 10" marker (so it stayed compilable until the Step-5 delete). Find:

```tsx
import { LessonScreen } from "./components/LessonScreen"; // deleted in Plan-3 Task 10
```

Replace with (delete the line entirely):

```tsx
```

> After this delete + the Step-5 `git rm`, no `LessonScreen` reference remains in `main.tsx` (Task 6 left only the import + comment; the function/route were already converted to `BeatOrchestratorRoute`). Step 7's `tsc --noEmit` confirms zero dangling `LessonScreen` references. The route stays mounted at `BeatOrchestratorRoute` (the lesson-fetch wrapper that supplies `lesson` + `onComplete`) — Task 10 never touches the route element or the wrapper's prop shape, only its `onComplete` action (Step 3).

- [ ] **Step 5: DELETE `LessonScreen.tsx` + `LessonScreen.test.tsx`.** They are the replaced surface (plan core §0.1, §0.6 #3). After Step 4, `LessonScreen` has zero importers (recon-verified: only `main.tsx` imported it).

```powershell
git rm tutor-web/src/components/LessonScreen.tsx tutor-web/src/components/LessonScreen.test.tsx
```

> **PredictionGate / RetrievalGate decision (recon-verified):** `tutor-web/src/components/PredictionGate.tsx` + `RetrievalGate.tsx` were imported ONLY by `LessonScreen.tsx` (production). Task 6's new beat subcomponents (`PredictBeat.tsx`, etc.) are a fresh rewrite-port and do NOT reuse them. So after this delete they become dead (their own `.test.tsx` files keep them compiling). Per the task scope ("PredictionGate/RetrievalGate stay if BeatOrchestrator subcomponents reuse them — check") and the verified non-reuse: **leave them in place this task** (deleting them is out of the "DELETE LessonScreen" scope and risks an unrelated diff), and add a CARRIED FOLLOW-UP to remove the now-orphaned `PredictionGate`/`RetrievalGate` (+ their tests) once Task 6 is confirmed not to reuse them. Note: there is ALSO a `PredictionGate` *interface* exported by `AlgoStepperShell.tsx` — that is a DIFFERENT, in-use type; do not touch it.

- [ ] **Step 6: Update `new-surfaces-smoke.spec.ts` lesson expectations to the new testids.** The current lesson test (`new-surfaces-smoke.spec.ts:79-130`) asserts `lesson-screen` + `lesson-step-entry` (the deleted surface) and stubs the OLD flat lesson payload. Replace that single test with one that drives the BeatOrchestrator: stub the beats payload (reuse the Task-9 fixture shape) and assert the new §4.7 first-paint testids + handoff-to-`/oggi`.

6a. Replace the whole `// ── /lesson/:kcId — LessonScreen ──` test block (`new-surfaces-smoke.spec.ts:78-130`) with:

```ts
// ── /lesson/:kcId — BeatOrchestrator (replaces LessonScreen) ──────────────
test("Phase-6 /lesson/:kcId: beat-orchestrator paints + handoff → /oggi (same KC visible)", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  // Beats payload (minimal STANDARD plan ①②③⑤ choice-variant; enough for first-paint + handoff).
  await page.route("**/api/v1/lesson/pa-kc-001/beat", (r) => {
    const t = (r.request().postDataJSON() as { beat_type: string }).beat_type;
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      correct: true, score: 1.0, feedback_ro: "corect", beat_type: t,
      lesson_complete: t === "check", first_encounter: true,
      phase: t === "check" ? "practice" : null, verification_status: t === "check" ? "faithful" : null,
    }) });
  });
  await page.route("**/api/v1/lesson/pa-kc-001", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      kcId: "pa-kc-001", kc_name_ro: "Noțiunea de algoritm", kc_name_en: "Algorithm",
      concrete_question_ro: "?", echo_source_ro: "?", prediction_options: [],
      term_ro: "Algoritm", definition_ro: "def", explanation_ro: "expl", worked_example_ro: "ex",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
      beats: {
        plan: ["predict", "attempt", "reveal", "check"],
        concept_type: "definition-taxonomy",
        predict: { prompt: "Care e un algoritm?", options: [
          { text: "Rețetă", callback: "Da.", correct: true },
          { text: "Număr", callback: "Nu.", correct: false },
          { text: "Culoare", callback: "Nu.", correct: false } ] },
        attempt: { statement: "Alege.", choices: [
          { text: "Pași de legat șireturi", correct: true, feedback: "Corect." },
          { text: "„pantof”", correct: false, feedback: "Nu." } ], feedback_correct: "Da." },
        reveal: { steps: [
          { text: "Pas 1.", callout: "Intrarea." },
          { text: "Pas 2.", callout: "Pași neambigui." } ] },
        check: { item_stem: "Care e un algoritm?", choices: [
          { text: "Pași de căutare", correct: true, feedback: "Da." },
          { text: "42", correct: false, feedback: "Nu." } ] },
      },
    }) }),
  );
  // /oggi (the handoff target) needs its queue stub so queue-item-pa-kc-001 paints.
  await stubShell(page);
  await page.route("**/api/v1/queue/today", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      day: "2026-06-12", total_due: 1, items: [{
        kc_id: "pa-kc-001", kc_name_ro: "Noțiunea de algoritm", kc_name_en: "Algorithm",
        subject: "PA", phase: "intro", mastery_ewma: 0.2, fsrs_card_id: null,
        verification_status: "faithful", worked_example_first: false, mode: "worked" }] }) }),
  );

  await page.goto("/tutor/lesson/pa-kc-001");

  // (1) §4.7 first-paint testids
  await expect(page.getByTestId("lesson-beat-pips")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("lesson-beat-active")).toHaveCount(1);
  await expect(page.getByTestId("beat-predict-options")).toBeVisible();
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();

  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);
});
```

> If `stubShell` is not already imported in this file, it is the module-scope helper defined at `new-surfaces-smoke.spec.ts:28` — same file, no import needed. The queue route literal (`/api/v1/queue/today`) matches the `getQueueToday` client call (`taskPrep.ts`) — confirm the exact path in `taskPrep.ts` and align the stub glob if it differs.

- [ ] **Step 7: `tsc --noEmit` + full vitest green** (the delete + rewire touches the type graph; never trust a partial run — review-workflow rule):

```powershell
cd tutor-web ; npx tsc --noEmit ; npm test ; cd ..
```

Expected: `tsc` clean (no dangling `LessonScreen` reference; `BeatOrchestrator`/`OggiScreen` typecheck); vitest GREEN except the known pre-existing `tutor-shell-api-contract.spec.ts` e2e red (NOT yours). A `Cannot find module './components/LessonScreen'` means an importer was missed — grep `LessonScreen` and fix before commit. If a vitest NON-e2e spec is red (e.g. an `OggiScreen` test that asserted the old no-op begin), update it to the new navigate behavior.

- [ ] **Step 8: Commit (explicit paths; the deletes ride in the same commit):**

```powershell
git add tutor-web/src/components/OggiScreen.tsx tutor-web/src/main.tsx tutor-web/e2e/new-surfaces-smoke.spec.ts
git rm tutor-web/src/components/LessonScreen.tsx tutor-web/src/components/LessonScreen.test.tsx
git commit -m @'
feat(lesson): wire Oggi Începe→/lesson, complete-handoff→/oggi; DELETE LessonScreen (E10/E4)

OggiScreen Începe now navigates to /lesson/{kc_id} for the selected (or top) queue
item (E10 dead end closed — was a no-op "Future: navigate to drill" tracker).
The BeatOrchestratorRoute wrapper's lesson-complete-handoff navigates to /oggi (the §0.9F fallback:
no per-KC drill deep-link exists — recon-verified: TutorWorkspace is task-keyed,
/review is whole-queue FsrsReview; /oggi shows queue-item-{kcId}, the "same KC
visible" satisfaction). /lesson/:kcId route re-pointed from LessonScreen to
BeatOrchestrator; LessonScreen.tsx + its test DELETED (spec §4.1 "replaces").
new-surfaces-smoke lesson test updated to the §4.7 beat testids + handoff.
Carried follow-up: dedicated per-KC drill deep-link; remove orphaned
PredictionGate/RetrievalGate (LessonScreen-only, not reused by beat subcomponents).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

## Task 11 — `deploy.sh` smoke fix (healthz 200 + `/login` title grep — carried follow-up)

The deploy smoke at `tools/deploy.sh:122` greps `https://corgflix.duckdns.org/` for `<div id="root"`. That is BROKEN: the app's root `/` is NOT the SPA — it 302-redirects unauthenticated requests to `/login` (`WebMain.kt:124`), and `curl -fsS` does not follow redirects, so the body is empty and the grep fails (or, if it ever followed, the `/login` HTML has no `<div id="root">`). Replace it with two assertions over the REAL anonymous-reachable surfaces: `/healthz` returns 200, and `/login` serves the real login page (`<title>Jarvis · login`). Both are explicitly public (`WebMain.kt:84`).

**Files:**
- Modify: `tools/deploy.sh` (the `=== VERIFY ===` block, line ~122)

> **Recon-verified anonymous-reachable surfaces (against HEAD `dfe7d5c`):**
> - `/` (and any non-`/tutor`, non-`/login`, non-`/api` path) → `respondRedirect("/login")` 302 (`WebMain.kt:117-124`). The old grep target is a redirect, not the SPA — this is the bug.
> - `/healthz` → `respondText("ok")` 200, listed PUBLIC at `WebMain.kt:84` + `:130-131`.
> - `/login` → `respondText(LOGIN_HTML)` 200, PUBLIC at `WebMain.kt:84` + `:134-135`; `LOGIN_HTML` carries `<title>Jarvis · login</title>` (`WebMain.kt:811`).
> - `/tutor/` → the SPA `index.html` (title `Jarvis Tutor`, `<div id="root">`), also public (`WebMain.kt:85`), but it is the client bundle that self-authenticates after load — a weaker smoke than `/login` (a served-but-broken bundle still has the root div). `/login` is the strongest anonymous server-rendered assertion. (Note: `HEALTH_URL` is already `…/healthz`, `deploy.sh:24`.)

- [ ] **Step 1: Read the current VERIFY block** (so the Edit matches verbatim). `tools/deploy.sh:119-123`:

```bash
# === VERIFY ===
echo "[deploy] verifying $HEALTH_URL"
curl -s -m 10 "$HEALTH_URL" && echo
curl -fsS "https://corgflix.duckdns.org/" | grep -q '<div id="root"' || { echo "SMOKE FAIL: SPA index did not serve"; exit 1; }
ssh "$VPS" "tail -25 /var/log/jarvis.log | grep -Ev '^SLF4J|^Picked up|^Jarvis web|^Auth required' | head -20"
```

- [ ] **Step 2: Replace the broken smoke line with the two-assertion smoke.** Apply this exact edit to `tools/deploy.sh`:

Find:

```bash
# === VERIFY ===
echo "[deploy] verifying $HEALTH_URL"
curl -s -m 10 "$HEALTH_URL" && echo
curl -fsS "https://corgflix.duckdns.org/" | grep -q '<div id="root"' || { echo "SMOKE FAIL: SPA index did not serve"; exit 1; }
```

Replace with:

```bash
# === VERIFY ===
# Smoke over the REAL anonymous-reachable surfaces only. The old check curl'd the
# app root `/` for `<div id="root"` — but `/` 302-redirects unauthenticated requests
# to `/login` (WebMain.kt: the auth intercept), so curl (no -L) saw an empty 302 body
# and the SPA-index assumption never held. The two public, server-rendered surfaces are
# `/healthz` (returns the literal "ok") and `/login` (LOGIN_HTML, <title>Jarvis · login).
# Plan-2 Task 12 confirmed both reachable anonymously; assets and `/` are auth-gated.
echo "[deploy] verifying $HEALTH_URL"
HEALTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$HEALTH_URL")"
[[ "$HEALTH_CODE" == "200" ]] || { echo "SMOKE FAIL: healthz returned $HEALTH_CODE (expected 200)"; exit 1; }
echo "[deploy] healthz 200 OK"
LOGIN_URL="${JARVIS_LOGIN:-https://corgflix.duckdns.org/login}"
curl -fsS -m 10 "$LOGIN_URL" | grep -q '<title>Jarvis · login' || { echo "SMOKE FAIL: /login did not serve the login page (expected <title>Jarvis · login)"; exit 1; }
echo "[deploy] /login page OK"
```

> The dry explanation comment is part of the edit (it documents WHY `/` was wrong, so a future operator does not "fix" it back). `HEALTH_URL` is already defined (`deploy.sh:24`, defaults to `…/healthz`); `LOGIN_URL` derives from the same host with a `JARVIS_LOGIN` override for symmetry. The `<title>Jarvis · login` literal is byte-exact from `WebMain.kt:811` (note the `·` U+00B7 middle dot — copy it, do not retype as a hyphen).

- [ ] **Step 3: Bash-lint the script** (catch a quoting/heredoc slip before it runs on the VPS):

```powershell
bash -n tools/deploy.sh
```

Expected: no output (exit 0). A syntax error here means the edit broke quoting — fix before commit.

- [ ] **Step 4: Confirm the edit landed** (read-only sanity; the Edit tool already errors if the old string was absent):

```powershell
Select-String -Path tools/deploy.sh -Pattern 'Jarvis · login|healthz returned'
```

Expected: two match lines (the `/login` title grep + the healthz failure message).

- [ ] **Step 5: Commit (explicit path):**

```powershell
git add tools/deploy.sh
git commit -m @'
fix(deploy): smoke over real anonymous surfaces — healthz 200 + /login title (carried)

The old smoke curl'd app-root `/` for `<div id="root"`, but `/` 302-redirects
unauthenticated requests to `/login` (curl without -L saw an empty 302 body), so
the SPA-index assertion never held. Replace with the two server-rendered public
surfaces: healthz returns 200, and /login serves <title>Jarvis · login (WebMain.kt
public routes). Keeps a dry explanation comment so the broken `/` check is not
reintroduced. This was the Plan-2 carried follow-up.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

> No live deploy runs in this task — it edits the script only. The actual VPS smoke fires in Task 13 (VPS deploy). If Task 13 runs before this commit, the broken smoke would false-fail the deploy; sequence Task 11 before Task 13.

## Task 12 — LIVE PC ops: gated migrate + a real lesson run-through on a live-DB COPY (INV-4.3) + trustInvariants

This is Plan-3's first task that mutates real PC data. Plan-3 added **3 additive columns** to `AttemptsTable` (`beat_type`, `prediction`, `first_encounter` — plan core §0.9D, Task 1) ⇒ pending DDL over the 828-card DB ⇒ the **INV-3.1 backup gate FIRES** on the live PC DB exactly as in Plan-2 Task 11. The sequence is the same in spirit as Plan-2 Task 11, with one critical addition: an **INV-4.3 lesson run-through driven through the REAL backend** (login → all 5 beats → completion writes), which is done on a **THROW-AWAY COPY of the live DB — NEVER the real DB**. The PM is the only user; if the run-through wrote to the real DB it would pollute Alex's mastery/FSRS with the PM's clicks (plan core §0.6 #2 / §0.9 I row INV-4.3). The real DB only ever gets the additive schema migrate (no lesson writes).

Hard facts (verified read-only 2026-06-11/12; the Plan-2 Task-11 backup/migrate/trustInvariants commands ran green on 2026-06-12 — reuse those exact idioms):
- Live PC DB: `$env:USERPROFILE\.jarvis\tutor.db` = `C:\Users\User\.jarvis\tutor.db`, **828 `fsrs_cards`**, 8 rows in `kc_verification_status` ({faithful:4, uncertain:4}). The 4 faithful hashes are `pa-kc-001 ca05c671` / `pa-kc-002 56156563` / `pa-kc-003 d5363220` / `pa-kc-004 817aaf44`. kvs has 8 rows.
- `jarvis migrate` reads the **positional path first** (`Main.kt:48-52`), then `JARVIS_DB`; `jarvis web` reads `JARVIS_TUTOR_DB` for its DB (`Config.kt:38`) and `JARVIS_PORT` (default 8080, `WebMain.kt:45-60`). This task ALWAYS passes the explicit path — never relies on `$HOME` resolution.
- The lesson route is auth-gated (`GET /api/v1/lesson/{kcId}` → `requireUser`, `QueueMasteryCalibrationRoutes.kt:386-387`; `POST /api/v1/lesson/{kcId}/beat` likewise, Task 4). Local dev auth = the magic link: with no `RESEND_API_KEY` the server uses `LoggingMailer`, which prints `[MagicLink] (dev, no RESEND_API_KEY) link for <email> -> <link>` to stdout (`MagicLinkMailer.kt:43-49`). The verify link is built from `JARVIS_PUBLIC_BASE_URL` (`TutorRoutes.kt:370`) — set it to `http://localhost:8080` locally so the link points at the local server. `secure=true` cookies (`TutorRoutes.kt:405`) ARE accepted by Chromium over `http://localhost` (localhost is a secure context) — the `shoot.journey-auth.mjs:7-18` pattern proves it.
- `pa-kc-001` is faithful (servable) and gets complete `beats["ro"]` in Task 7 ⇒ it is the canonical run-through KC.
- After Task 4, every graded beat writes ONE `AttemptsTable` row with `taskId="lesson"`, `beat_type` ∈ {predict,attempt,reveal,name,check} (only learner-input beats post — predict/attempt/check, plan core §0.9C), `prediction` non-null on the predict row, `first_encounter` true exactly once; the graded CHECK additionally runs `recordIn` (kc_mastery EWMA) + `upsertRubricCriterion` (one `fsrs_cards` row, source `RUBRIC_CRITERION`) in one atomic txn (`TutorRoutes.kt:2222-2256` is the mirror).
- No `claimsFor()` / content-hash / D9 / VerificationGate code is touched — the 4 faithful badges must stay lit with **unchanged** `content_hash` (asserted at the end of 12a).

### 12a — The real apply (additive migrate only; NO lesson writes to the real DB)

- [ ] **Step 1: Stop any local process holding the live DB.** Do NOT stop the relay on :9999 (standing infra, unrelated):

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Confirm:$false }
```

Expected: no output (nothing was listening) or the process is stopped. Either is fine.

- [ ] **Step 2: Take the real off-box backup of the LIVE DB + push it off-box to the VPS** (a fresh same-day dump is the INV-3.1 gate's precondition; the off-box copy is the recovery point):

```powershell
$bk = "$env:USERPROFILE\jarvis-backups\$(Get-Date -Format yyyy-MM-dd)"
New-Item -ItemType Directory -Force $bk | Out-Null
python tools/db-backup.py $bk
```

Expected (three PASS lines, cards 828):

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 828==828, schema_hash match)
[db-backup] backed up 828 fsrs_cards -> ...\jarvis-backups\2026-06-12\jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

Then copy the dump + manifest off-box to the VPS:

```powershell
ssh root@46.247.109.91 "mkdir -p /opt/jarvis/backups/pc"
Get-ChildItem $bk | ForEach-Object { scp $_.FullName root@46.247.109.91:/opt/jarvis/backups/pc/ }
ssh root@46.247.109.91 "ls -la /opt/jarvis/backups/pc/"   # confirm the dated dump + .manifest.json landed
```

Expected: the new `.sql.gz` and `.sql.gz.manifest.json` appear in the VPS listing.

- [ ] **Step 3: Run the gated migration on the LIVE DB (same day as the backup).** The INV-3.1 gate reads `$bk` and must find the fresh same-day manifest with `integrity=PASS`, `fsrs_cards==828`, `schema_hash==live`. The 3 new attempts columns are pending DDL ⇒ the gate fires:

```powershell
$env:JARVIS_BACKUP_DIR = $bk
gradle run --args="migrate $env:USERPROFILE\.jarvis\tutor.db"
```

Expected: `[migrate] SUCCESS — fsrs_cards before=828 after=828`

**What refusal looks like and what to do:** if instead you see `[TutorMigration] migration REFUSED (INV-3.1 backup gate): ...` (no manifest / stale date / card-count or schema-hash mismatch), the migration did NOT run — **refusal = STOP. Fix the backup (re-run Step 2 so a fresh same-day manifest exists in `$bk`), then retry Step 3. NEVER set any bypass env var, NEVER point `JARVIS_BACKUP_DIR` at a stale manifest, NEVER edit the gate.** The gate refusing is the system working.

- [ ] **Step 4: Assert the 3 new columns landed + the corpus survived + kvs untouched (read-only against the REAL DB).** This proves the additive migrate did exactly what it should and nothing else:

```powershell
python - "$env:USERPROFILE\.jarvis\tutor.db" << 'PY'
import sqlite3, sys
con = sqlite3.connect("file:" + sys.argv[1].replace("\\","/") + "?mode=ro", uri=True)
acols = {c[1] for c in con.execute("PRAGMA table_info(attempts)")}
new = {"beat_type","prediction","first_encounter"}
print("attempts new cols present:", new.issubset(acols), sorted(new - acols) or "(all 3 present)")
print("fsrs_cards:", con.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0])
print("kvs rows:", con.execute("SELECT COUNT(*) FROM kc_verification_status").fetchone()[0])
print("kvs faithful hashes:", con.execute("SELECT kc_id||':'||content_hash FROM kc_verification_status WHERE status='faithful' ORDER BY kc_id").fetchall())
PY
```

Expected EXACTLY:

```
attempts new cols present: True (all 3 present)
fsrs_cards: 828
kvs rows: 8
kvs faithful hashes: [('pa-kc-001:ca05c671',), ('pa-kc-002:56156563',), ('pa-kc-003:d5363220',), ('pa-kc-004:817aaf44',)]
```

If `attempts new cols present` is not `True`, or `fsrs_cards != 828`, or any faithful hash differs, STOP — the migrate is wrong or a content-hash cascade fired; do not proceed.

### 12b — INV-4.3 lesson run-through on a COPY (never the real DB)

- [ ] **Step 1: Make the rehearsal copy of the (now-migrated) live DB.** The COPY already carries the 3 new columns — the run-through writes ONLY to this copy:

```powershell
$reh = "$env:USERPROFILE\plan3-rehearsal"
New-Item -ItemType Directory -Force $reh | Out-Null
Copy-Item "$env:USERPROFILE\.jarvis\tutor.db" "$reh\tutor-copy.db" -Force
python -c "import sqlite3,sys; print('copy cards:', sqlite3.connect(sys.argv[1]).execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0])" "$reh\tutor-copy.db"
```

Expected: `copy cards: 828`

- [ ] **Step 2: Start the local server against the COPY (background) — DB = the copy, dev mailer (no Resend key), local base URL.** `JARVIS_TUTOR_DB` points `web` at the copy; clearing `RESEND_API_KEY` forces `LoggingMailer` (token printed to stdout); `JARVIS_PUBLIC_BASE_URL` makes the verify link point at the local server. Redirect stdout+stderr to a log file so the magic-link line can be fished:

```powershell
$reh = "$env:USERPROFILE\plan3-rehearsal"
$env:JARVIS_TUTOR_DB = "$reh\tutor-copy.db"
$env:JARVIS_PORT = "8080"
$env:JARVIS_PUBLIC_BASE_URL = "http://localhost:8080"
Remove-Item Env:\RESEND_API_KEY -ErrorAction SilentlyContinue   # force LoggingMailer (dev link to stdout)
$srv = Start-Process -FilePath "gradle" -ArgumentList 'run','--args=web' `
  -RedirectStandardOutput "$reh\server.out.log" -RedirectStandardError "$reh\server.err.log" `
  -PassThru -WindowStyle Hidden
Set-Content "$reh\server.pid" $srv.Id
```

Wait for the server to come up (poll healthz; do NOT blind-sleep):

```powershell
$ok = $false
foreach ($i in 1..40) {
  try { if ((Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://localhost:8080/healthz).StatusCode -eq 200) { $ok = $true; break } } catch {}
  Start-Sleep -Milliseconds 1500
}
"healthz up: $ok"
```

Expected: `healthz up: True`. **STOP if** it never comes up — inspect `$reh\server.err.log` for a boot-migrate refusal (the copy is already migrated, so this should not happen) or a port clash.

- [ ] **Step 3: Issue a magic link for the owner email and fish the dev link from the server log.** POST `/auth/request-link` (always 200, no auth needed — `TutorRoutes.kt:357-377`), then read the `[MagicLink] (dev...)` line from the stdout log:

```powershell
$reh = "$env:USERPROFILE\plan3-rehearsal"
Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/request-link `
  -ContentType 'application/json' -Body '{"email":"amoalexandru5@gmail.com","lang":"ro"}'
# the LoggingMailer prints: [MagicLink] (dev, no RESEND_API_KEY) link for amoalexandru5@gmail.com -> http://localhost:8080/auth/verify?token=<RAW>
$line = (Select-String -Path "$reh\server.out.log" -Pattern '\[MagicLink\].*-> (http\S+)' | Select-Object -Last 1)
$verifyUrl = $line.Matches.Groups[1].Value
$mlToken = ([uri]$verifyUrl).Query.TrimStart('?').Split('=')[1]
"verifyUrl: $verifyUrl"
"token len: $($mlToken.Length)"
```

Expected: `{ok=True}` from the POST; `verifyUrl` is a `http://localhost:8080/auth/verify?token=...` URL; a non-empty token. **STOP if** no `[MagicLink]` line appears — that means `RESEND_API_KEY` leaked into the env (the link was emailed, not logged); unset it and retry from Step 2.

- [ ] **Step 4: Write the Playwright run-through driver** that signs in via the dev verify link (sets the session+csrf cookies on the browser context), then drives ONE full lesson for `pa-kc-001` through the REAL backend by clicking the BeatOrchestrator UI (the same surface a learner sees), capturing zero-4xx/5xx + zero console errors during the traversal. Create `tutor-web/plan3-runthrough.mjs`:

```js
// INV-4.3 full lesson run-through against the REAL backend (a live-DB COPY).
// Signs in via the dev magic link, drives pa-kc-001 through all beats by clicking
// the BeatOrchestrator UI, gating on the Next control each beat. READ-WRITE to the
// COPY only. Fails loud on any 4xx/5xx or console error during the traversal.
import { chromium } from "playwright-core";

const VERIFY = process.env.ML_VERIFY_URL;                 // http://localhost:8080/auth/verify?token=...
const BASE = "http://localhost:8080/tutor";
const KC = "pa-kc-001";
if (!VERIFY) { console.error("ML_VERIFY_URL not set"); process.exit(2); }

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1366, height: 1000 }, locale: "ro-RO", reducedMotion: "reduce" });
const page = await ctx.newPage();
const bad = [];
const errs = [];
page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url().replace(/^https?:\/\/[^/]+/, "")}`); });
page.on("console", (m) => { if (m.type() === "error") errs.push(m.text().slice(0, 160)); });
page.on("pageerror", (e) => errs.push(e.message.slice(0, 160)));

// 1. Sign in — /auth/verify sets jarvis_session + csrf cookies, 302 -> /tutor/.
await page.goto(VERIFY, { waitUntil: "networkidle", timeout: 25000 });
// 2. AI-literacy confirm (gate before LLM/lesson surfaces — csrf cookie is non-httpOnly, readable).
await page.evaluate(async () => {
  const csrf = document.cookie.split("; ").find((c) => c.startsWith("csrf="))?.split("=")[1] ?? "";
  await fetch("/api/v1/me/ai-literacy/confirm", {
    method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
    body: JSON.stringify({ lang: "ro" }), credentials: "include",
  });
});

// 3. Open the lesson route for pa-kc-001.
await page.goto(`${BASE}/lesson/${KC}`, { waitUntil: "networkidle", timeout: 25000 });
await page.waitForSelector('[data-testid="lesson-beat-pips"]', { timeout: 15000 });

// 4. Walk every beat. The Next gate (beat-next-gate) is disabled until the beat's gate
//    clears; satisfy each gate by interacting, then click Next, until the completion
//    handoff appears. The active beat is [data-testid="lesson-beat-active"].
const MAX_BEATS = 8; // FULL plan is 5; cap as a loop safety
for (let i = 0; i < MAX_BEATS; i++) {
  if (await page.locator('[data-testid="lesson-complete-handoff"]').count()) break;
  // predict: click the first option (the gate fires the POST and stores the prediction)
  const predict = page.locator('[data-testid="beat-predict-options"] button, [data-testid="beat-predict-options"] [role="button"]');
  if (await predict.count()) await predict.first().click();
  // attempt: click the first choice if a choice-variant attempt is showing
  const choice = page.locator('[data-testid="lesson-beat-active"] [data-testid^="beat-attempt-choice"]');
  if (await choice.count()) await choice.first().click();
  // reveal: step a figure/text scrubber to its final step if present (gate = stepped to final once)
  const fwd = page.locator('[data-testid="beat-figure-scrubber"] [data-testid$="-step-fwd"], [data-testid="beat-figure-scrubber"] [data-testid="scrubber-step-fwd"]');
  if (await fwd.count()) { for (let s = 0; s < 12; s++) { if (await fwd.first().isDisabled().catch(() => false)) break; await fwd.first().click(); } }
  // check: answer the first choice if a check is showing
  const check = page.locator('[data-testid="lesson-beat-active"] [data-testid^="beat-check-choice"]');
  if (await check.count()) await check.first().click();
  // wait for the Next gate to enable (dwell floor + gate cleared), then advance
  const next = page.locator('[data-testid="beat-next-gate"]');
  await next.waitFor({ state: "visible", timeout: 15000 });
  for (let w = 0; w < 12; w++) { if (!(await next.isDisabled())) break; await page.waitForTimeout(700); }
  if (await next.isDisabled()) { console.error(`beat ${i}: Next never enabled`); break; }
  await next.click();
  await page.waitForTimeout(400);
}

const completed = (await page.locator('[data-testid="lesson-complete-handoff"]').count()) > 0;
console.log(JSON.stringify({ completed, bad, errs }, null, 2));
await browser.close();
process.exit(completed && bad.length === 0 && errs.length === 0 ? 0 : 1);
```

- [ ] **Step 5: Run the driver against the COPY-backed server.** It must complete the lesson with zero 4xx/5xx and zero console errors:

```powershell
$env:ML_VERIFY_URL = $verifyUrl
node tutor-web/plan3-runthrough.mjs
"driver exit: $LASTEXITCODE"
```

Expected: a JSON blob with `"completed": true`, `"bad": []`, `"errs": []`, and `driver exit: 0`. **STOP if** `completed` is false, or `bad`/`errs` are non-empty — a 4xx/5xx or a console error during the real traversal is an INV-4.4-class failure surfaced here against the real backend (plan core §0.9 I, INV-4.4 "full real-backend pass in Task 12"). Inspect `$reh\server.err.log` and the JSON before claiming anything.

- [ ] **Step 6: Assert the completion writes landed on the COPY (read-only).** This is the literal INV-4.3 acceptance check — attempt rows with `beat_type`, prediction on the predict row, first_encounter exactly once, an EWMA mastery row, and a `+1 RUBRIC_CRITERION` FSRS card for `(owner, pa-kc-001)`. Resolve the owner id at runtime (single user; never hardcode):

```powershell
python - "$env:USERPROFILE\plan3-rehearsal\tutor-copy.db" << 'PY'
import sqlite3, sys
con = sqlite3.connect("file:" + sys.argv[1].replace("\\","/") + "?mode=ro", uri=True)
owner = con.execute("SELECT id FROM users ORDER BY created_at LIMIT 1").fetchone()[0]
kc = "pa-kc-001"
rows = con.execute(
  "SELECT beat_type, prediction, first_encounter FROM attempts "
  "WHERE user_id=? AND kc_id=? AND task_id='lesson'", (owner, kc)).fetchall()
beats = [r[0] for r in rows]
print("lesson attempt rows:", len(rows), ">= 3:", len(rows) >= 3)
print("beat_types present:", sorted(set(beats)),
      "covers predict/attempt/check:", {"predict","attempt","check"}.issubset(set(beats)))
predict_rows = [r for r in rows if r[0] == "predict"]
print("prediction non-null on predict row:", all(r[1] for r in predict_rows) and len(predict_rows) >= 1)
fe = sum(1 for r in rows if r[2] in (1, True))
print("first_encounter set exactly once:", fe == 1, "(count=%d)" % fe)
m = con.execute("SELECT observations FROM kc_mastery WHERE user_id=? AND kc_id=?", (owner, kc)).fetchone()
print("kc_mastery row present, observations>=1:", bool(m) and m[0] >= 1)
fc = con.execute("SELECT COUNT(*) FROM fsrs_cards WHERE user_id=? AND kc_id=? AND source='RUBRIC_CRITERION'", (owner, kc)).fetchone()[0]
print("fsrs_cards RUBRIC_CRITERION for (owner,kc):", fc, ">= 1:", fc >= 1)
PY
```

Expected EXACTLY (each line ends `True`):

```
lesson attempt rows: 3 >= 3: True
beat_types present: ['attempt', 'check', 'predict'] covers predict/attempt/check: True
prediction non-null on predict row: True
first_encounter set exactly once: True (count=1)
kc_mastery row present, observations>=1: True
fsrs_cards RUBRIC_CRITERION for (owner,kc): 1 >= 1: True
```

(Row count is ≥3 because only the three learner-input beats — predict/attempt/check — post attempt rows; reveal/name carry no learner input and write nothing, plan core §0.9C.) **STOP if** any line ends `False` — the completion contract (Task 4) regressed; do not claim INV-4.3.

- [ ] **Step 7: Kill the local server** (the copy + its log are scratch; the migrated REAL DB is the artifact this task delivers):

```powershell
$reh = "$env:USERPROFILE\plan3-rehearsal"
$pid = Get-Content "$reh\server.pid" -ErrorAction SilentlyContinue
if ($pid) { Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue }
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -Confirm:$false -ErrorAction SilentlyContinue }
Remove-Item Env:\JARVIS_TUTOR_DB, Env:\JARVIS_PUBLIC_BASE_URL, Env:\ML_VERIFY_URL -ErrorAction SilentlyContinue
```

Expected: the process is stopped; nothing remains listening on 8080.

### 12c — trustInvariants over the REAL DB

- [ ] **Step 1: Run trustInvariants over the REAL DB (all blocks PASS, including INV-3.2b green with the 4 authored beat sets from Task 7).** `args[0]` is the CONTENT DIR (`TrustInvariantsCli.kt:116-124`); the DB path comes ONLY from `JARVIS_TUTOR_DB` — NEVER pass the `.db` path positionally:

```powershell
$env:JARVIS_TUTOR_DB = "$env:USERPROFILE\.jarvis\tutor.db"
$env:JARVIS_BACKUP_DIR = $bk
gradle trustInvariants "-PinvariantsArgs=content"
```

Expected: `[trustInvariants] ALL PASS (db=..., content=content, floor=800)` — including:
- INV-3.2b (every KC with non-empty `beats` passes `isCompleteFor` — now **4 authored beat sets** for pa-kc-001..004 from Task 7; the other 4 KCs have empty beats and are skipped, `TrustInvariantsCli.kt:50-66`);
- INV-3.3 (828 == backup manifest, no card lost its history);
- FLIP (≥1 faithful — still 4) and PARITY (each faithful hash recomputes identically from `content/` — proves authoring beats did NOT change `content_hash`, plan core §0.2 hash safety).

**STOP if** any line FAILs. If INV-3.2b fails, an authored beat set in Task 7 is structurally incomplete — fix the YAML in Task 7, do not force this green. If PARITY fails, a Plan-3 field leaked into `claimsFor()` (forbidden, plan core §0.6 #6) — STOP and escalate; do NOT edit content to force green.

- [ ] **Step 2: Badge sanity — the 4 faithful rows are still faithful with UNCHANGED content_hash** (the proof Plan 3 caused no re-audit cascade):

```powershell
python -c "import sqlite3,pathlib; con=sqlite3.connect((pathlib.Path.home()/'.jarvis'/'tutor.db').as_uri()+'?mode=ro',uri=True); [print(r) for r in con.execute('SELECT kc_id,status,content_hash FROM kc_verification_status ORDER BY kc_id')]"
```

Expected EXACTLY (8 rows, the 4 faithful hashes byte-identical to the pre-migrate values):

```
('pa-kc-001', 'faithful', 'ca05c671')
('pa-kc-002', 'faithful', '56156563')
('pa-kc-003', 'faithful', 'd5363220')
('pa-kc-004', 'faithful', '817aaf44')
('pa-kc-005', 'uncertain', '1b67adce')
('pa-kc-006', 'uncertain', '6af8f795')
('pa-kc-fixture-compute', 'uncertain', '1a004870')
('pa-kc-fixture-recursion', 'uncertain', '1a004870')
```

Any faithful row flipped or any `content_hash` changed = a hash cascade fired — STOP and escalate.

No repo commit (this task changes runtime state only — the DB is not git-tracked). The rehearsal copy + scratch logs under `$env:USERPROFILE\plan3-rehearsal` can be deleted afterward; the dated `$bk` dump is KEPT (it is the recovery point). `tutor-web/plan3-runthrough.mjs` is a scratch driver, not committed (it is not part of the e2e suite — that lands in Task 9).

---

## Task 13 — VPS: backup → deploy (boot-migrate gated) → migrate confirm → asserts → healthz → full-page screenshot packet for the morning eyeball

The VPS runs the tutor as a systemd service (`User=jarvis`, `WorkingDirectory=/opt/jarvis`, `Environment=HOME=/opt/jarvis`, `ExecStart=…/jarvis-kotlin web`). On `web` boot the service runs `TutorMigration.migrate` against `Config.tutorDbPath`, which with `HOME=/opt/jarvis` and no `JARVIS_TUTOR_DB` set resolves to `/opt/jarvis/.jarvis/tutor.db` — the REAL service DB (**871 cards**, verified 2026-06-11). The 3-new-attempts-columns DDL is pending there too, so the INV-3.1 gate fires on the boot-migrate; `JARVIS_BACKUP_DIR=/opt/jarvis/backups` is already in `/opt/jarvis/.env` (verified), so a same-day manifest must exist there BEFORE the deploy-restart. Task 11's deploy.sh smoke fix (healthz + `/login` title grep) must now PASS.

**Critical DB-path rule (the 871-vs-0 incident):** any CLI run over `ssh root@46.247.109.91` WITHOUT the service's HOME has `user.home`=`/root` → it targets `/root/.jarvis/tutor.db` — a junk byproduct DB (286 KB, root-owned), NEVER the service DB. Every CLI invocation below passes the explicit path `/opt/jarvis/.jarvis/tutor.db` (and every backup uses `--db /opt/jarvis/.jarvis/tutor.db`). NEVER target `/root/.jarvis/tutor.db`.

**PowerShell ssh quoting idiom (from Plan-1/Plan-2 ops):** single-quote the PowerShell string so `$` reaches the REMOTE bash untouched; the remote `set -a; . /opt/jarvis/.env; set +a` sources the env on the VPS side.

- [ ] **Step 1: Ship the hardened backup tool + back up the VPS DB (explicit `--db`), then pull the dump to the PC (the VPS's off-box copy):**

```powershell
scp tools/db-backup.py root@46.247.109.91:/opt/jarvis/db-backup.py
ssh root@46.247.109.91 'mkdir -p /opt/jarvis/backups; python3 /opt/jarvis/db-backup.py --db /opt/jarvis/.jarvis/tutor.db /opt/jarvis/backups'
```

Expected (three PASS lines, **871** cards — the VPS has its own history):

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 871==871, schema_hash match)
[db-backup] backed up 871 fsrs_cards -> /opt/jarvis/backups/jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

Pull the off-box copy to the PC:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\jarvis-backups\vps" | Out-Null
scp "root@46.247.109.91:/opt/jarvis/backups/*.sql.gz*" "$env:USERPROFILE\jarvis-backups\vps\"
```

Expected: the new `.sql.gz` + `.sql.gz.manifest.json` land under `...\jarvis-backups\vps\`.

- [ ] **Step 2: Deploy the new jar + corpus (boot-migrate runs the schema; the Task-11-fixed smoke must PASS).** `bash tools/deploy.sh` builds (`gradle :test :installDist :android:assembleDebug`), scps the dist + the `content/` corpus, restarts the service (which migrates on boot over the 871-card DB), and smoke-checks healthz + the `/login` title grep (Task 11's fix):

```powershell
bash tools/deploy.sh
```

Expected: deploy completes; the final lines show `active` from `systemctl is-active jarvis`, a healthz body, and the Task-11 smoke passing (no `SMOKE FAIL`). The boot-migrate over the 871-card DB needs the same-day manifest in `/opt/jarvis/backups` (taken in Step 1) — if the service fails to start with `[TutorMigration] migration REFUSED (INV-3.1 backup gate)` in `/var/log/jarvis.log`, the manifest is missing/stale: re-run Step 1 (same day), then `ssh root@46.247.109.91 "systemctl restart jarvis && sleep 4 && systemctl is-active jarvis"`. **Refusal = STOP + fix the backup; never bypass.** If the smoke fails on the `/login` grep, Task 11's fix did not deploy — re-check Task 11 landed before claiming this done.

- [ ] **Step 3: Explicit no-op migrate confirm against the REAL service DB** (belt-and-braces — proves the correct DB was migrated, not the `/root` junk DB; boot-migrate already ran, so this is a no-op confirming `before=871 after=871`):

```powershell
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin migrate /opt/jarvis/.jarvis/tutor.db'
```

Expected: `[migrate] SUCCESS — fsrs_cards before=871 after=871`. If `before=0` appears, the wrong DB was targeted (the `/root` junk DB) — STOP, re-check the explicit path.

- [ ] **Step 4: Remote read-only asserts (3 new attempts cols present, counts unchanged, faithful 4 + hashes byte-identical to the PC).** Run a python `-c` over the explicit path (the `\x27` is a single-quote escaped through the PowerShell→ssh→bash layers):

```powershell
ssh root@46.247.109.91 'python3 -c "import sqlite3; con=sqlite3.connect(\"file:/opt/jarvis/.jarvis/tutor.db?mode=ro\",uri=True); ac={c[1] for c in con.execute(\"PRAGMA table_info(attempts)\")}; need={\"beat_type\",\"prediction\",\"first_encounter\"}; print(\"attempts new cols present:\", need.issubset(ac)); print(\"fsrs_cards:\", con.execute(\"SELECT COUNT(*) FROM fsrs_cards\").fetchone()[0]); print(\"kvs rows:\", con.execute(\"SELECT COUNT(*) FROM kc_verification_status\").fetchone()[0]); print(\"faithful:\", con.execute(\"SELECT COUNT(*) FROM kc_verification_status WHERE status=\x27faithful\x27\").fetchone()[0]); print(\"faithful hashes:\", con.execute(\"SELECT kc_id||\x27:\x27||content_hash FROM kc_verification_status WHERE status=\x27faithful\x27 ORDER BY kc_id\").fetchall())"'
```

Expected EXACTLY:

```
attempts new cols present: True
fsrs_cards: 871
kvs rows: 8
faithful: 4
faithful hashes: [('pa-kc-001:ca05c671',), ('pa-kc-002:56156563',), ('pa-kc-003:d5363220',), ('pa-kc-004:817aaf44',)]
```

(If the PowerShell→ssh quoting fights you, `ssh root@46.247.109.91` interactively and run the python block directly — the assertion is what matters, not the wrapper. The faithful set + hashes must be byte-identical to the PC's `ca05c671 / 56156563 / d5363220 / 817aaf44`.) Any deviation (missing column, cards ≠ 871, a flipped/changed faithful row) = STOP and escalate.

- [ ] **Step 5: healthz 200 confirmation** (the service is serving after the boot-migrate):

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -m 10 https://corgflix.duckdns.org/healthz
```

Expected: `200`. Anything else: `ssh root@46.247.109.91 "tail -30 /var/log/jarvis.log"` for the boot-migrate refusal or a startup error before claiming done.

### 13b — Screenshot packet for the morning eyeball (render-before-claim, WHOLE artifact)

The PM REVIEWS these pixels personally before claiming Plan 3 done (render-before-claim, [[feedback_render_before_claim_done]]). Capture the lesson at every beat screen on the LIVE VPS surface, AFTER auth.

- [ ] **Step 1: Mint an owner session token on the VPS and fish the verify link from the log.** The documented pattern is the magic link (`shoot.journey-auth.mjs`). POST `/auth/request-link` for the owner email, then read the `[MagicLink]` line from `/var/log/jarvis.log` (LoggingMailer prints the link there when `RESEND_API_KEY` is unset). On the VPS, if Resend IS configured the link is emailed, not logged — in that case set `RESEND_API_KEY=""` for one request by running the request through the running service is not possible without restart, so the robust path is to read the most recent issued link from the log; if absent, fall back to interactively pasting a link Alex generates. First try the log:

```powershell
curl.exe -s -X POST https://corgflix.duckdns.org/auth/request-link -H "Content-Type: application/json" -d '{\"email\":\"amoalexandru5@gmail.com\",\"lang\":\"ro\"}'
ssh root@46.247.109.91 "grep -a '\[MagicLink\]' /var/log/jarvis.log | tail -1"
```

Expected: `{"ok":true}` from the POST; the grep prints a `[MagicLink] (dev, no RESEND_API_KEY) link for amoalexandru5@gmail.com -> https://corgflix.duckdns.org/auth/verify?token=<RAW>` line. Extract that URL into `$verifyUrl`:

```powershell
$line = ssh root@46.247.109.91 "grep -a '\[MagicLink\]' /var/log/jarvis.log | tail -1"
$verifyUrl = ([regex]'-> (https\S+)').Match($line).Groups[1].Value
"verifyUrl: $verifyUrl"
```

**If the grep prints nothing** (Resend is live on the VPS, so the link was emailed not logged): STOP the screenshot automation and ask Alex to paste a fresh sign-in link (he receives it by email) — do NOT disable Resend on the live service. Set `$verifyUrl` to the pasted link and continue.

- [ ] **Step 2: Write the screenshot-packet driver** that signs in via the verify link, stores the authed storage state, navigates the lesson route, and full-page screenshots each beat screen as it advances (predict → attempt → reveal → name → check → complete) into `build-review/plan3-eyeball/`. Create `tutor-web/plan3-eyeball.mjs`:

```js
// Full-page screenshot packet of the LIVE VPS lesson for the morning eyeball.
// Signs in via the dev/owner magic link, stores auth state, then walks pa-kc-001
// beat-by-beat capturing each screen. Read-only intent (do NOT submit the final
// graded check — capture the check screen, then the completion screen after one
// answer). Output: build-review/plan3-eyeball/*.png + auth-state.json.
import { chromium } from "playwright-core";
import { mkdirSync } from "node:fs";

const VERIFY = process.env.ML_VERIFY_URL;                 // https://corgflix.duckdns.org/auth/verify?token=...
const BASE = "https://corgflix.duckdns.org/tutor";
const KC = "pa-kc-001";
const OUT = "C:\\Users\\User\\jarvis-kotlin\\build-review\\plan3-eyeball";
if (!VERIFY) { console.error("ML_VERIFY_URL not set"); process.exit(2); }
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1366, height: 1000 }, locale: "ro-RO", reducedMotion: "reduce" });
const page = await ctx.newPage();

// 1. Sign in; persist the authed storage state for re-use / debugging.
await page.goto(VERIFY, { waitUntil: "networkidle", timeout: 25000 });
await page.evaluate(async () => {
  const csrf = document.cookie.split("; ").find((c) => c.startsWith("csrf="))?.split("=")[1] ?? "";
  await fetch("/api/v1/me/ai-literacy/confirm", {
    method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
    body: JSON.stringify({ lang: "ro" }), credentials: "include",
  });
});
await ctx.storageState({ path: `${OUT}\\auth-state.json` });

// 2. Open the lesson; capture each beat as we advance.
await page.goto(`${BASE}/lesson/${KC}`, { waitUntil: "networkidle", timeout: 25000 });
await page.waitForSelector('[data-testid="lesson-beat-pips"]', { timeout: 15000 });

const shot = async (name) => { await page.waitForTimeout(900); await page.screenshot({ path: `${OUT}\\${name}.png`, fullPage: true }); };
const NAMES = ["1-predict", "2-attempt", "3-reveal", "4-name", "5-check", "6-complete"];
let n = 0;
for (let i = 0; i < NAMES.length; i++) {
  await shot(NAMES[n++]);
  if (await page.locator('[data-testid="lesson-complete-handoff"]').count()) break;
  // satisfy the current beat's gate, then advance
  const predict = page.locator('[data-testid="beat-predict-options"] button, [data-testid="beat-predict-options"] [role="button"]');
  if (await predict.count()) await predict.first().click();
  const choice = page.locator('[data-testid="lesson-beat-active"] [data-testid^="beat-attempt-choice"]');
  if (await choice.count()) await choice.first().click();
  const fwd = page.locator('[data-testid="beat-figure-scrubber"] [data-testid$="-step-fwd"], [data-testid="beat-figure-scrubber"] [data-testid="scrubber-step-fwd"]');
  if (await fwd.count()) { for (let s = 0; s < 12; s++) { if (await fwd.first().isDisabled().catch(() => false)) break; await fwd.first().click(); } }
  const check = page.locator('[data-testid="lesson-beat-active"] [data-testid^="beat-check-choice"]');
  if (await check.count()) await check.first().click();
  const next = page.locator('[data-testid="beat-next-gate"]');
  await next.waitFor({ state: "visible", timeout: 15000 });
  for (let w = 0; w < 12; w++) { if (!(await next.isDisabled())) break; await page.waitForTimeout(700); }
  if (await next.isDisabled()) { console.error(`beat ${i}: Next never enabled`); break; }
  await next.click();
  await page.waitForTimeout(400);
}
console.log(JSON.stringify({ captured: NAMES.slice(0, n) }, null, 2));
await browser.close();
```

- [ ] **Step 3: Run the packet driver against the LIVE VPS.** This writes the morning-eyeball PNGs:

```powershell
$env:ML_VERIFY_URL = $verifyUrl
node tutor-web/plan3-eyeball.mjs
Remove-Item Env:\ML_VERIFY_URL -ErrorAction SilentlyContinue
```

Expected: a JSON blob listing the captured screens; the files below exist on disk.

- [ ] **Step 4: List the exact files produced and confirm all 6 beat screens + auth state are present:**

```powershell
Get-ChildItem "$env:USERPROFILE\jarvis-kotlin\build-review\plan3-eyeball\*.png" | Select-Object Name, Length
Test-Path "$env:USERPROFILE\jarvis-kotlin\build-review\plan3-eyeball\auth-state.json"
```

Expected: exactly these PNGs (each non-zero length), plus `auth-state.json` present (`True`):

```
build-review/plan3-eyeball/1-predict.png
build-review/plan3-eyeball/2-attempt.png
build-review/plan3-eyeball/3-reveal.png
build-review/plan3-eyeball/4-name.png
build-review/plan3-eyeball/5-check.png
build-review/plan3-eyeball/6-complete.png
build-review/plan3-eyeball/auth-state.json
```

(pa-kc-001 is concept_type `definition-taxonomy` — a choice-variant lesson, so its beat ③ reveal is stepped TEXT, not a figure-scrubber; the `3-reveal.png` shows the stepped-text reveal and the figure-scrubber selector is correctly absent for these 4 faithful KCs, plan core §0.6 #1. If a beat screen failed to capture because a gate never enabled, that is a real defect — STOP and fix the orchestrator/server before claiming the packet complete.)

- [ ] **Step 5: PM eyeball (human, blocking).** The PM opens all 6 PNGs and confirms, for the WHOLE lesson: every beat screen renders with no clipping/overlap, the pips show the right count, the predict callback echo appears at reveal, the Next gate is visually present, and the completion handoff control is visible. **This is the render-before-claim gate for Plan 3 — do not mark Plan 3 done in Task 14 until the PM has personally reviewed these pixels.** This is a render check, NOT a content-judgement ask (never ask Alex to vet the lesson's correctness — no-oracle-inversion; the faithful badge already attests the content).

No repo commit (runtime ops + scratch drivers). `tutor-web/plan3-eyeball.mjs` and the PNGs are eyeball artifacts under `build-review/` (gitignored-class working-tree files), not committed by this task.

---

## Task 14 — Whole-suite final gate + plan-index update (Plan 3 DONE) + carried follow-ups + push + 0/0 verify

Plan-3's per-task commits are already on `main` (Tasks 1–11). This task runs the full review-workflow gate (backend `:check` + frontend vitest + Playwright e2e + python suites — each run in full, NEVER piped through `tail`/`head`), flips the plan-index Plan 3 row to DONE with the verified live-state summary, appends the carried follow-ups, commits the docs update on an explicit path, pushes, and verifies `0/0 vs origin`.

**Files:**
- Modify: `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`

- [ ] **Step 1: Full backend suite — run in full, review the whole output (NEVER pipe through `tail`/`head`; the review-workflow rule):**

```powershell
gradle :check
```

Expected: `BUILD SUCCESSFUL`. This runs every backend test including the new Plan-3 tests (BeatSelector / BeatPlan legality INV-4.1, ApiLessonBeats DTOs + lesson GET beats guard, the `POST /lesson/{kcId}/beat` grading + completion-write tests, the AttemptsTable migration/pin tests for the 3 new columns, and the amended `SignatureLockPinTest` for `ApiLessonReply` §NEW-L). If anything is RED, STOP and fix — do not mark the plan done.

- [ ] **Step 2: Frontend vitest:**

```powershell
cd tutor-web ; npm test ; cd ..
```

Expected: vitest GREEN (all unit/component specs pass — including the Plan-3 BeatOrchestrator/beat-subcomponent specs, the dwell INV-4.2 boundary tests, and the GraphTreeFamily trace-match INV-5.1 + semantic-invariant INV-5.2 specs). The Plan-3 lesson route e2e + family no-clip specs run under Playwright (Step 3), not vitest (e2e is excluded by the vitest include-glob, plan core §0.5). If vitest's non-e2e specs go red, that IS yours — STOP and fix.

- [ ] **Step 3: Playwright e2e suite** (the §4.7 lesson selectors + interaction smoke + scrubber-back + no-clip + import-ban tests landed in Task 9 under the stub-backend pattern; CI frontend job has no JVM):

```powershell
cd tutor-web ; npx playwright test ; cd ..
```

Expected: the Plan-3 specs added in Task 9 (lesson route + family no-clip + INV-5.4 import-ban grep) pass. **The pre-existing `tutor-shell-api-contract.spec.ts` red, if still red, is NOT introduced by Plan 3** (carried in the plan-index follow-up list, plan core §0.5) — do NOT claim it green, do NOT fix it here. If a Plan-3 spec goes red, that IS yours — STOP and fix.

- [ ] **Step 4: Python backup-tooling suites (run both, full output):**

```powershell
python tools/test_db_backup.py
python tools/test_db_backup_hardening.py
```

Expected: each prints `OK` (the unittest pass line). A failure here means the backup tool regressed — STOP and fix before claiming done.

- [ ] **Step 5: Update the plan index — flip the Plan 3 row to DONE and append the carried follow-ups.** Read the current Plan 3 row first (it currently reads `next` / `just-in-time`). Apply this exact edit to `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`:

The Plan 3 row's `Status` + `Detailed plan doc` cells currently read (the `§4-5` and the trailing cells):

```
| §4-5 | next | just-in-time |
```

Replace with:

```
| §4-5 | **DONE 2026-06-12** (BeatOrchestrator replaces LessonScreen; server BeatSelector + per-beat grading/completion writes live; 3 additive attempts cols migrated on PC+VPS behind INV-3.1; 828/871 cards intact; 4 faithful badges unchanged; INV-4.3 run-through green on a live-DB copy; graph/tree family + MergeSort trace-match shipped; no-clip + lesson e2e in the suite) | `2026-06-12-plan3-lesson-engine.md` |
```

Then append the carried follow-ups to the existing "Carried follow-ups" line (do NOT delete existing entries; append after the last `·`-separated item). The fixed Plan-3 carry candidates (always append these two):

- `FSRS re-seed wart (plan core §0.3): every drill re-grade clobbers the card back to Fsrs.initial — Plan-3 lesson completion mirrors the same seed-if-absent call and does NOT fix it; schedule-forward correctness is a later plan`
- `AlgoStepperShell full RO chrome sweep (the additive labels prop ships RO on the lesson surface only; demos/gallery keep EN defaults) = Plan 4 / spec §8.2`
- `Numeric ATTEMPT grading (consistency#5): the no-choices attempt branch is a deliberate placeholder (exact-string match vs trace_steps.last().value, a formula string — would grade a real numeric answer wrong). DEAD on the 4 faithful choice-variant KCs Plan 3 serves. When a numerical-variant KC (FORMULA_APPLICATION/PROCEDURE/PROBABILISTIC) is served, add attempt.numeric_answer + tolerance (mirror the numeric CHECK path) and a LessonBeatGradeRouteTest case pinning the match`

Then append, ONLY IF the corresponding condition held during execution, the run-discovered items (mark each `<FILL-AT-EXECUTION>` and drop it entirely if the condition did not hold):

- `<FILL-AT-EXECUTION: IF Task 10 wired the lesson-complete-handoff to the /oggi fallback (queue-item-{kcId} visible) rather than a dedicated drill route, append: "lesson→drill handoff currently lands on /oggi with queue-item-{kcId} (the §0.9F fallback contract) — re-point to the concrete drill route when the drill surface gains a deep-link target">`
- `<FILL-AT-EXECUTION: IF any other genuine load-bearing ambiguity surfaced during Tasks 12/13 (e.g. VPS Resend masked the dev MagicLink log so the screenshot packet needed a hand-pasted link), append it verbatim as one ·-separated item>`

If execution surfaced a NEW ambiguity not covered above, append it verbatim too rather than silently dropping it.

- [ ] **Step 6: Verify the edit rendered (read-only sanity):**

```powershell
Select-String -Path docs/superpowers/plans/2026-06-11-one-pass-plan-index.md -Pattern 'DONE 2026-06-12.*BeatOrchestrator'
```

Expected: one match line showing the updated Plan 3 row. (This is the one place a `Select-String` confirms the doc edit landed — the Edit tool already errors if the old string was not found.)

- [ ] **Step 7: Commit the docs update (explicit path only — NEVER `git add -A`; untracked door demos on `main` must not be swept, plan core §0.6 #7):**

```powershell
git add docs/superpowers/plans/2026-06-11-one-pass-plan-index.md
git commit -m @'
docs(plan): Plan 3 (lesson engine) DONE — BeatOrchestrator + server grading live PC+VPS, 3 attempts cols migrated, family trace-match shipped

Plan 3 complete on main: BeatOrchestrator replaces the 3-step LessonScreen;
server BeatSelector over the closed plan table + per-beat grading + atomic
completion writes (attempts beat_type/prediction/first_encounter, EWMA, FSRS
seed). 3 additive attempts columns migrated on both live DBs behind the
INV-3.1 gate (828/871 cards intact, 4 faithful badges unchanged — claimsFor()
untouched). INV-4.3 real-backend run-through green on a live-DB copy; the
graph/tree family + MergeSort instance ship verified (trace-match + semantic
invariants + no-clip); the lesson e2e + no-clip gate land in the suite. Index
flipped to DONE + carried follow-ups appended (FSRS re-seed wart, RO chrome
sweep -> Plan 4).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

- [ ] **Step 8: Push and verify `0/0 vs origin`:**

```powershell
git push origin main
git status
git log -1 --oneline
```

Expected: `git status` shows `Your branch is up to date with 'origin/main'.` (0 ahead / 0 behind), and `git log -1` shows the docs(plan) commit at HEAD. If `git status` shows "ahead by 1", the push did not land — re-run `git push origin main` and re-check before claiming the plan done.

**Done means:** `:check` green, frontend vitest green, the Plan-3 Playwright specs green (the one pre-existing e2e red is named + not yours), both python suites `OK`, the PM has personally eyeballed the Task-13 screenshot packet (render-before-claim), the plan index marks Plan 3 DONE with the verified live-state summary + carried follow-ups, and `main` is `0/0` against `origin`.

