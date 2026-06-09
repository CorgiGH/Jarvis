# Remaining Tutor Implementation Plan
**Date:** 2026-06-08  
**HEAD:** `dd45f09` (main, in sync with origin/main)  
**Author:** plan-writer agent (claude-sonnet-4-6)  
**Purpose:** sequence ALL remaining work from the design spine that is NOT yet built.

---

## What is DONE (do NOT re-plan)

Phase 0–5 are fully built and green on `main @ dd45f09`:

| Phase | Built |
|---|---|
| 0 | Data model (CHANGES 1–10), FSRS-5-lite, auth/GDPR stack |
| 1 | Content reconcile + validator, PA corpus, FSRS card seed |
| 2 | Trust-net: two-hash content_hash v2 + source_span_hash; serve gate D8; report_wrong; anyRefuted⇒never-faithful |
| 3 | Atomic faithful-gated grade; queue/today (backend + `getQueueToday` lib at `tutor-web/src/lib/taskPrep.ts:53`); mock-exam sync |
| 4 | Theme system app-wide: ThemeProvider, AppShell, ThemePicker, palettes |
| 5 | Grounded-teaching backend (T0–T9) + `GET /api/v1/teaching/{kcId}` + frontend core-loop slice (T1–T15): FeedbackLadder, MisconceptionRibbon, TrustBadge, GroundedExplanationCard, ProvenanceBadge, confidence row — all mounted in DrillStack; Playwright e2e gate green |

**Grader LLM wiring (verified):** `drillGraderLlmFactory` at `src/main/kotlin/jarvis/web/TutorRoutes.kt:166` already defaults to `OpenRouterChatLlm()` with free-tier model (`meta-llama/llama-3.3-70b-instruct:free` at `src/main/kotlin/jarvis/OpenRouterChatLlm.kt:73`). The grader already uses a free provider — no swap needed. Task D-a below is a verification-only task.

---

## Hard rules (encoded in EVERY task)

- **TDD:** write failing test first, implement, confirm green.
- **BUILD→MOUNT:** every new component task is followed immediately by a MOUNT task naming the exact parent file + import + JSX diff. No ghost components.
- **INTERACTION-SMOKE:** every new user-facing route gets a Playwright assertion: listed `data-testid` selectors paint + zero 4xx/5xx on first paint + click-through no 404/error text.
- **Faithful-serve gate:** any content endpoint is ALWAYS-ON (404 if not faithful/disputed/unknown).
- **Lock amendments:** any deviation from `interface-signatures-lock.md` is amended in the SAME commit.
- **No paid APIs:** free OpenRouter `:free` or claude-max relay only.
- **Language split:** content in Romanian, code/UI-chrome in English.

---

## Phase 6 — Queue-first home screen (B)

**Dependency:** `getQueueToday` lib already landed (`tutor-web/src/lib/taskPrep.ts:53`). NOT yet mounted to any route. Gate: mount the screen and add `/oggi` route.

### T6-1 — Build `LearnerQueueList` component
**Goal:** render a paginated list of `QueueItem`s with subject pill, KC title, phase badge, mastery band.  
**Files:**
- `tutor-web/src/components/LearnerQueueList.tsx` (new)
- `tutor-web/src/components/LearnerQueueList.test.tsx` (new)

**Test first:**
```
data-testid="learner-queue-list"           — the container
data-testid="queue-item-{kc_id}"           — each row
data-testid="queue-item-phase-{kc_id}"     — phase badge per row
data-testid="queue-item-mastery-{kc_id}"   — mastery band per row
```
Assert: 3-item fixture renders 3 rows; CTRL+ENTER on first item fires `onSelect`; empty list renders `data-testid="queue-empty"`.

**Frozen signatures:** `QueueItem` shape from `interface-signatures-lock.md §C`.

### T6-2 — Build `MasterySparkline` (shared primitive)
**Goal:** 28px hand-SVG mastery band (accent ≥0.8 / muted 0.3–0.8 / dark <0.3), shared across 0a/0b/5/0h/15.  
**Files:**
- `tutor-web/src/components/MasterySparkline.tsx` (new)
- `tutor-web/src/components/MasterySparkline.test.tsx` (new)

**Test first:**
```
data-testid="mastery-sparkline"
data-mastery-value="{ewma}"         — numeric, 0..1
data-mastery-band="high|mid|low"    — "high" ≥0.8, "mid" 0.3..0.8, "low" <0.3
```
Assert: value=0.9 → band=high, accent fill; value=0.5 → band=mid; value=0.1 → band=low.  
`band` render (NOT time-series) — the only shape buildable from `/mastery` wire (one scalar `ewma_score`; `interface-signatures-lock.md §M` confirms no `ewma_history` column).

**Mount site:** embedded inside `LearnerQueueList` queue-item rows (T6-1, same commit).

### T6-3 — Build `OggiScreen` (queue-first home)
**Goal:** `/oggi` route screen — calls `getQueueToday`, shows `LearnerQueueList` (7fr) + next-KC mini-door panel (3fr); CTRL+ENTER begins the top item.  
**Files:**
- `tutor-web/src/components/OggiScreen.tsx` (new)
- `tutor-web/src/components/OggiScreen.test.tsx` (new)

**Test first:**
```
data-testid="oggi-screen"
data-testid="oggi-queue-panel"
data-testid="oggi-next-kc-panel"
data-testid="oggi-empty"            — when queue returns []
data-testid="oggi-error"            — when getQueueToday throws
```
Assert: mocked `getQueueToday` returning 3 items → `oggi-queue-panel` + `oggi-next-kc-panel` render; empty → `oggi-empty`; network error → `oggi-error`.

### T6-4 — MOUNT `OggiScreen` at `/oggi` + add nav link
**Goal:** wire the screen into the router and AppShell nav.  
**Files touched (exact):**
- `tutor-web/src/main.tsx` — add `<Route path="/oggi" element={<OggiScreen />} />`
- `tutor-web/src/App.tsx` — add nav link `oggi` (beside workspace/tasks)

**Interaction-smoke (Playwright):**
```
data-testid selectors that must paint: oggi-screen, oggi-queue-panel
Zero 4xx/5xx on first paint of /tutor/oggi
Click first queue-item → no 404/error text
```

---

## Phase 7 — "Meet a new term" lesson-serve backend (A)

**Dependency:** Phase 6 (queue path confirmed working). Phase 7 provides the endpoint Phases 8–9 consume.

### T7-1 — `GET /api/v1/lesson/{kcId}` (net-new, faithful-gated first-encounter endpoint)

**Goal:** serve the first-encounter lesson payload (question stem + EchoBand source + PredictionGate options) for a KC that is faithful. Non-faithful / disputed / unknown → 404 (FAIL-LOUD, same gate as `/api/v1/teaching/{kcId}`).

**Proposed frozen endpoint signature (amend `interface-signatures-lock.md §NEW-L` in the same commit):**

```kotlin
/** GET /api/v1/lesson/{kcId}
 *  Faithful-gated first-encounter lesson. Mirrors the teaching gate (resolveStatus + hasOpenReportWrong).
 *  Non-faithful / disputed / unknown → 404 (FAIL-LOUD). */
@Serializable
data class ApiLessonReply(
    val kcId: String,
    val kc_name_ro: String,
    val kc_name_en: String,
    val concrete_question_ro: String?,   // stem for Lesson Entry (0c) — from stem_template or null
    val echo_source_ro: String?,         // span quote for EchoBand (0d) — from kc.source[0].quote or null
    val prediction_options: List<String>, // 2-4 RO options for PredictionGate (0d) — empty = gate disabled
    val term_ro: String,                 // the one new term (kc_name_ro)
    val definition_ro: String?,          // authored definition — from kc.definition or null
    val explanation_ro: String?,         // authored explanation (from grounded-teaching fields)
    val worked_example_ro: String?,      // authored worked example (from grounded-teaching fields)
    val provenance: DrillProvenanceDto,  // {authored, hasBeenFaithfulChecked=true} — gate guarantees it
)
```

**Files:**
- `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (add route + reply DTO)
- `src/test/kotlin/jarvis/web/LessonServeRouteTest.kt` (new — 5 tests mirroring `TeachingServeRouteTest`)
- `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` (amend §NEW-L in same commit)

**Test first:** faithful KC → 200 with `provenance.hasBeenFaithfulChecked=true`; non-faithful → 404; open report_wrong → 404; unknown kcId → 404; no session → 401.

**Lock:** add `§NEW-L` to `interface-signatures-lock.md` in the same commit (new section, not a deviation).

---

## Phase 8 — Lesson-entry frontend surfaces (A)

**Dependency:** T7-1 (backend lesson endpoint). Build components, then mount.

### T8-1 — Build `EchoBand`
**Goal:** display the student's own just-submitted answer in a 2px accent-rule quoted strip.  
**Files:** `tutor-web/src/components/EchoBand.tsx` + `EchoBand.test.tsx`  
**Test testids:** `data-testid="echo-band"`, `data-testid="echo-band-quote"`  
Assert: renders the quoted text verbatim; renders nothing when text is empty.

### T8-2 — Build `TermLanding`
**Goal:** one new term highlighted (accent bg / black ink) + RO/EN gloss inline.  
**Files:** `tutor-web/src/components/TermLanding.tsx` + `TermLanding.test.tsx`  
**Test testids:** `data-testid="term-landing"`, `data-testid="term-landing-term"`, `data-testid="term-landing-gloss"`  
Assert: renders term with accent bg; renders EN gloss when provided; renders nothing when term is empty.

### T8-3 — Build `PredictionGate`
**Goal:** two hard-bordered buttons; student must commit a prediction before seeing the next step; gate locks if prediction not submitted.  
**Files:** `tutor-web/src/components/PredictionGate.tsx` + `PredictionGate.test.tsx`  
**Test testids:** `data-testid="prediction-gate"`, `data-testid="prediction-option-{i}"`, `data-testid="prediction-submitted"`  
Assert: 2 options render; clicking one calls `onPredict(option)`; after selection renders committed state; renders nothing when options empty.

### T8-4 — Build `RetrievalGate`
**Goal:** DARK door-language closed-book recall; monumental prompt → answer plate → `ConfidenceRow`.  
**Files:** `tutor-web/src/components/RetrievalGate.tsx` + `RetrievalGate.test.tsx`  
**Test testids:** `data-testid="retrieval-gate"`, `data-testid="retrieval-prompt"`, `data-testid="retrieval-answer-input"`, `data-testid="retrieval-submit"`  
Assert: prompt renders; submit disabled when answer empty; calls `onSubmit(answer)` with the text.

**Mount site:** `RetrievalGate` is used inside `LessonScreen` (T8-7).

### T8-5 — Build `InvariantRule`
**Goal:** 4px accent blockquote for the invariant statement.  
**Files:** `tutor-web/src/components/InvariantRule.tsx` + `InvariantRule.test.tsx`  
**Test testids:** `data-testid="invariant-rule"`, `data-testid="invariant-rule-body"`  
Assert: renders body text; renders nothing when text is null/empty.

### T8-6 — Build `LessonEntryBand` + `ConcreteQuestionBlock`
**Goal:** the dark→light transitional accent-rule band (Lesson Entry, surface 0c) + the concrete opening question block.  
**Files:** `tutor-web/src/components/LessonEntryBand.tsx` + `LessonEntryBand.test.tsx`, `ConcreteQuestionBlock.tsx` + `ConcreteQuestionBlock.test.tsx`  
**Test testids:** `data-testid="lesson-entry-band"`, `data-testid="concrete-question-block"`, `data-testid="concrete-question-input"`, `data-testid="concrete-question-submit"`  
Assert: band renders with accent-rule; question renders; submit fires `onAnswer(text)`.

### T8-7 — Build `LessonScreen` (0c + 0d + 0e surfaces composed)
**Goal:** full lesson flow: Entry (0c) → TermLanding (0d) → RetrievalGate (0e). Calls `GET /api/v1/lesson/{kcId}` to fetch the payload; 404 → `LessonUnavailable` state.  
**Files:** `tutor-web/src/components/LessonScreen.tsx` + `LessonScreen.test.tsx`

**Test testids (all must paint on the relevant step):**
```
data-testid="lesson-screen"
data-testid="lesson-step-entry"        — step 0c (first paint)
data-testid="lesson-step-term"         — step 0d (after entry submit)
data-testid="lesson-step-retrieval"    — step 0e (after prediction)
data-testid="lesson-unavailable"       — when /lesson returns 404
```
Interaction-smoke:
- First paint: `lesson-screen` + `lesson-step-entry` visible; zero 4xx/5xx.
- Submit entry answer → `lesson-step-term` renders; no error text.
- Submit prediction → `lesson-step-retrieval` renders.
- Mock 404 → `lesson-unavailable` renders.

**Faithful-serve gate:** if `/lesson/{kcId}` returns 404, render `lesson-unavailable` ("KC nu este încă verificat — revin mai târziu."); NEVER fall back to unverified content.

### T8-8 — MOUNT `LessonScreen` at `/lesson/:kcId` + nav wiring
**Files touched:**
- `tutor-web/src/main.tsx` — add `<Route path="/lesson/:kcId" element={<LessonScreen />} />`
- `tutor-web/src/components/OggiScreen.tsx` — wire queue-item "mode=worked" click → `/lesson/{kcId}`

**Playwright interaction-smoke (full):**
```
/tutor/lesson/pa-kc-001 (mocked faithful fixture)
→ lesson-screen, lesson-step-entry paint
→ zero 4xx/5xx on first paint
→ fill + submit entry answer: lesson-step-term paints, no error text
→ click prediction option: lesson-step-retrieval paints
→ no /404|error/i text at any step
```

---

## Phase 9 — Remaining spine surfaces (C)

Built in order of shared-primitive dependency. Grouped by sub-theme.

### Phase 9A — High-stakes: Mock Exam shell

### T9A-1 — Build `MockExamShell` + `MockTimer` + `MockQuestionBlock` + `MockQuestionNav`
**Goal:** View A of mock exam (surface 0g) — timed exam with numbered question blocks, sticky timer + nav + SUBMIT.  
**Files:** `tutor-web/src/components/MockExamShell.tsx` + `.test.tsx`; `MockTimer.tsx` + `.test.tsx`; `MockQuestionBlock.tsx` + `.test.tsx`; `MockQuestionNav.tsx` + `.test.tsx`

**Test testids:**
```
data-testid="mock-exam-shell"
data-testid="mock-timer"
data-testid="mock-question-{i}"         — each question block
data-testid="mock-question-nav"
data-testid="mock-submit-btn"
```
Assert: timer counts down; question nav highlights active; SUBMIT disabled until all answered.

### T9A-2 — Build `MockGradeReport` + `MockScoreSparkline`
**Goal:** View B — score plate + per-question table + sparkline + redo-wrong CTA (surface 9).  
**Grading contract (LOCKED):** SYNC 200-only (H13). `MockScoreSparkline` renders `MasterySparkline` in delta-mode.  
**Files:** `MockGradeReport.tsx` + `.test.tsx`; `MockScoreSparkline.tsx` + `.test.tsx`

**Test testids:**
```
data-testid="mock-grade-report"
data-testid="mock-score-plate"
data-testid="mock-question-result-{i}"
data-testid="mock-redo-btn"
data-testid="mock-score-sparkline"
```
Assert: 200 reply → score plate renders with score; UNCERTAIN question → badge renders; redo CTA fires `onRedoWrong`.

### T9A-3 — MOUNT exam shell at `/exam/:subject`
**Files touched:** `tutor-web/src/main.tsx` (new Route), `tutor-web/src/App.tsx` (nav link or remove existing placeholder).

**Playwright interaction-smoke:**
```
/tutor/exam/PA (mocked fixture, 3 questions)
→ mock-exam-shell, mock-timer, mock-question-0, mock-question-nav paint
→ zero 4xx/5xx
→ answer all questions → mock-submit-btn enabled → click → mock-grade-report paints
→ no /404|error/i text
```

### Phase 9B — Review/memory: FSRS upgrade + calibration

### T9B-1 — Upgrade `FsrsReview` (opacity cross-fade + grade buttons + `ForecastDotPlot`)
**Goal:** drop the rotateY flip; add opacity cross-fade; GOOD = one accent; right panel `ForecastDotPlot`.  
**Files:** `tutor-web/src/components/FsrsReview.tsx` (modify existing), `ForecastDotPlot.tsx` (new) + `.test.tsx`

**Test testids (new/changed):**
```
data-testid="fsrs-card-front"
data-testid="fsrs-card-back"         — cross-fades in (no rotateY)
data-testid="fsrs-grade-GOOD"        — accent
data-testid="fsrs-forecast-plot"
```
`ProgressStrip` `rounded-full` → square pips fix (R3 compliance) in same commit.

### T9B-2 — Build `CalibrationPlot` + `CalibrationTable` (surface 6)
**Goal:** confidence calibration scatter (hand-SVG, single-col 4-bucket).  
**Files:** `tutor-web/src/components/CalibrationPlot.tsx` + `.test.tsx`; `CalibrationTable.tsx` + `.test.tsx`

**Test testids:** `data-testid="calibration-plot"`, `data-testid="calibration-table"`, `data-testid="calibration-row-{band}"` (OVER/UNDER/CALIBRATED).

**Backend contract needed:** `GET /api/v1/calibration` — verify it exists at `QueueMasteryCalibrationRoutes.kt` (already noted in spine §6). If missing, T9B-2 adds it.

### Phase 9C — Session wrap

### T9C-1 — Build `SessionWrapPane` (surface 5)
**Goal:** end-of-session overlay with monumental N-CARDS + `MasterySparkline` delta-mode + narrative + DONE CTA.  
**Files:** `tutor-web/src/components/SessionWrapPane.tsx` + `.test.tsx`

**Test testids:**
```
data-testid="session-wrap-pane"
data-testid="session-wrap-cards-count"
data-testid="session-wrap-mastery-sparkline"
data-testid="session-wrap-done-btn"
```

**Backend contract needed:** `POST /api/v1/session/close` — verify exists at `SessionPlacementExamRoutes.kt:156`. If the reply does not include `session_summary`, plan a minimal additive response extension.

### T9C-2 — MOUNT `SessionWrapPane` trigger in `App.tsx`
**Files touched:** `tutor-web/src/App.tsx` — on session close event, render `SessionWrapPane` overlay.

### Phase 9D — Meta-nav surfaces

### T9D-1 — Build `SubjectCard` + `RetentionGapBadge` + `SubjectMap` screen (surface 0b)
**Goal:** 5-equal-column subject map (R6 exception — equal-peer, **Alex sign-off required before build**).  
**Files:** `SubjectCard.tsx` + `.test.tsx`; `RetentionGapBadge.tsx` + `.test.tsx`; `SubjectMap.tsx` + `.test.tsx`

**Test testids:** `data-testid="subject-map"`, `data-testid="subject-card-{subjectId}"`, `data-testid="retention-gap-badge-{subjectId}"`.  
**Mount site:** route `/subjects`, nav link `materie`.

### T9D-2 — Build `LedgerRow` + wire `KnowledgeLedger` into `LedgerDrawer` (surface 0h)
**Goal:** right-edge 480px DARK slide-in drawer.  
**Files:** `tutor-web/src/components/LedgerRow.tsx` + `.test.tsx`; `LedgerDrawer.tsx` (new wrapper, if not already a component) + `.test.tsx`

**Test testids:** `data-testid="ledger-drawer"`, `data-testid="ledger-row-{id}"`, `data-testid="ledger-row-status-{id}"`.  
**Mount site:** `App.tsx` — `header-ledger-btn` click → opens `LedgerDrawer`.

### T9D-3 — Build `BottomTabBar` (surface 0i, mobile <768px)
**Goal:** fixed 56px DARK strip with 4 cells AZI/MATERIE/JURNAL/EU; active = accent bg.  
**Files:** `tutor-web/src/components/BottomTabBar.tsx` + `.test.tsx`

**Test testids:** `data-testid="bottom-tab-bar"`, `data-testid="tab-{name}"` (azi/materie/jurnal/eu), `data-testid="tab-active"`.  
**Mount site:** `AppShell` or `App.tsx` — rendered only at `width < 768px`.

### Phase 9E — Foundation surfaces

### T9E-1 — Build `DayOfShell` + `DayOfCountdown` + `DayOfChecklist` (surface 3)
**Goal:** single-col DARK — monumental countdown + reappraisal sentence + review strip + FII checklist.  
**Files:** `DayOfShell.tsx` + `.test.tsx`; `DayOfCountdown.tsx` + `.test.tsx`; `DayOfChecklist.tsx` + `.test.tsx`

**Backend contract:** `GET /api/v1/me/exam-dates` — verify at `SessionPlacementExamRoutes.kt`; if missing, add it (exam_dates table CHANGE 9 is already in schema).

**Test testids:** `data-testid="day-of-shell"`, `data-testid="day-of-countdown"`, `data-testid="day-of-checklist"`, `data-testid="day-of-review-strip"`.  
**Mount site:** route `/day-of`, rendered when exam_dates.start_at is within 24h.

### T9E-2 — Build `OnboardingShell` + step components (surface 4)
**Goal:** 5-step full-page DARK onboarding (AI literacy, ToS/privacy, placement intent, profile+LangToggle, notifications). Each step gated by the previous. `LangToggle` + `BilingualText` are new primitives; `VoxButton` tombstone ships here (disabled).

**Files:** `OnboardingShell.tsx` + `.test.tsx`; `LangToggle.tsx` + `.test.tsx`; `BilingualText.tsx` + `.test.tsx`; `VoxButton.tsx` + `.test.tsx` (disabled tombstone only, no audio).

**Test testids:** `data-testid="onboarding-shell"`, `data-testid="onboarding-step-{n}"` (1..5), `data-testid="lang-toggle"`, `data-testid="vox-btn-disabled"`.  
**Mount site:** route `/welcome` (replace/extend existing `/welcome/ai-literacy`); step 1 = AI literacy gate (existing `AiLiteracyGate` reused).

### T9E-3 — Build `PlacementShell` + `PlacementQuestion` + `PlacementResultBanner` (surface 10)
**Goal:** DARK door-language, 8 pips, one question at a time; post-submit node-graph via `--fig-*` Figure system.

**Files:** `PlacementShell.tsx` + `.test.tsx`; `PlacementQuestion.tsx` + `.test.tsx`; `PlacementResultBanner.tsx` + `.test.tsx`

**Test testids:** `data-testid="placement-shell"`, `data-testid="placement-question"`, `data-testid="placement-result-banner"`, `data-testid="placement-progress-pip-{n}"`.  
**Mount site:** route `/placement`.

### T9E-4 — Build shared error/empty components (surfaces 13 + 14)
**Goal:** `EmptyState` (3 variants), `InlineErrorCard`, `DegradedBanner`, `FatalErrorPage`.

**Files:** `EmptyState.tsx` + `.test.tsx`; `InlineErrorCard.tsx` + `.test.tsx`; `DegradedBanner.tsx` + `.test.tsx`; `FatalErrorPage.tsx` + `.test.tsx`

**Test testids:** `data-testid="empty-state"`, `data-testid="empty-state-variant-{v}"`, `data-testid="inline-error-card"`, `data-testid="degraded-banner"`, `data-testid="fatal-error-page"`.

### T9E-5 — `RightsSidebar` in `SettingsMe` (surface 12)
**Goal:** DARK right panel in `/me` — GDPR Art 15/17/18/22 + AI Act; export + pause + delete. `SettingsMe` exists; add the sidebar.

**Files:** `tutor-web/src/components/RightsSidebar.tsx` + `.test.tsx`; `tutor-web/src/components/SettingsMe.tsx` (modify)

**Test testids:** `data-testid="rights-sidebar"`, `data-testid="gdpr-export-btn"`, `data-testid="gdpr-delete-btn"`.

### Phase 9F — Lab sandbox (surface 7) — DEFERRED flag

> **Flag for owner:** the lab sandbox (V86 + xterm.js) requires a non-trivial runtime dependency (V86 WASM, ~4MB) and a first-lab assertion DSL that has no spec yet (spine §4.6 open decisions: "V86 warm/cold · assertion DSL · first lab"). This phase is described but **gated on Alex specifying the first lab topic and the assertion format** before build.  
> If Alex unlocks it, the tasks are: `LabShell`, `LabGradeReadout`, `LabObjectiveCard` + `/lab/:labId` route + Playwright gate.

---

## Phase D — Data-safety gated re-audit

### D-a — Verify grader provider (informational, no code change required)
**Goal:** confirm the drill grader already uses a free provider so no tutor usage burns the Claude-Max pool.  
**Verification (no commit):** grep `drillGraderLlmFactory` at `src/main/kotlin/jarvis/web/TutorRoutes.kt:166` — confirmed it is `OpenRouterChatLlm()` with default model `meta-llama/llama-3.3-70b-instruct:free`. The grader is already free. No swap needed. Close this item.

### D-b — Live content re-audit (faithful-stamp the live DB) — **GATED-ON-OWNER-GO**

**Pre-condition (from council 1780762845 — ALL three gates must pass before any DB write):**

1. **Off-box DB dump first:** run `tools/db-backup` asserting `MIN_EXPECTED_CARDS=800` against the live `~/.jarvis/tutor.db`. Do NOT proceed if the dump does not complete successfully or the card count is below 800.
2. **Zero-row abort guard:** before running the audit batch, query `SELECT COUNT(*) FROM kc_verification_status`. If the count is > 0, STOP and surface the existing rows to the owner for review — do NOT overwrite existing verification data without explicit authorization.
3. **Post-flip parity check:** after the audit batch runs, verify that the total number of `kc_verification_status` rows equals the number of KCs in the corpus; spot-check ≥3 `faithful` rows against the source PDFs manually.

**Explicit owner-go required.** Do not execute this phase without Alex's explicit "go" on each gate.

**If go is given, the tasks are:**
- Run `./gradlew run --args="verify-content"` (or the equivalent CLI) over all subjects.
- Confirm `kc_verification_status` is populated.
- Re-run the post-flip parity check.
- Do NOT touch `~/.jarvis/tutor.db` schema or data in any other way.

---

## Open decisions before build (owner must resolve)

1. **0b Subject Map R6 exception** — 5-equal-column layout breaks R6 (asymmetry); Alex sign-off required before T9D-1. Without it, T9D-1 is blocked.
2. **Lab first-lab topic + assertion DSL** — gates Phase 9F entirely.
3. **`prediction_options` authoring** — the `ApiLessonReply.prediction_options` field needs authored Romanian options per KC. Phase 8 can build behind mocked options and degrade gracefully when empty; but the UX is hollow until authored. Flag as content-authoring debt.
4. **`ConcreteQuestionBlock` stem source** — if `KnowledgeConcept.stem_template` is null for most KCs, the lesson entry falls back to the KC name as the question prompt (honest degraded mode). No blocker, but note the degraded-mode copy needs to be in Romanian.
5. **`GET /api/v1/calibration` wire shape** — T9B-2 needs the calibration endpoint's exact reply shape. Verify at `QueueMasteryCalibrationRoutes.kt` before building the component.

---

## Phase / task count summary

| Phase | Description | Tasks |
|---|---|---|
| 6 | Queue-first home screen | 4 |
| 7 | Lesson-serve backend | 1 |
| 8 | Lesson-entry frontend | 8 |
| 9A | Mock exam | 3 |
| 9B | FSRS upgrade + calibration | 2 |
| 9C | Session wrap | 2 |
| 9D | Meta-nav (subject map, ledger, mobile tabs) | 3 |
| 9E | Foundation (day-of, onboarding, placement, error states, rights sidebar) | 5 |
| 9F | Lab sandbox (DEFERRED) | — |
| D | Data-safety gated re-audit | 2 (1 verify-only, 1 gated) |
| **Total** | | **30 tasks** |

**Build order:** 6 → 7 → 8 → 9A → 9B → 9C → 9D → 9E → D  
(Phase 7 backend must land before Phase 8 frontend; Phase 6 queue-path must land before Phase 8 mount wiring; everything else in 9x is parallel.)

---

## Next-session adds (surfaced during the 2026-06-09 demo eval)

**G1 — Content-quality guardrail (do BEFORE curating a real lecture).** The demo surfaced an English-name leak ("Ce este The notion of an algorithm?") because a KC had no Romanian `stem_template` and the stem fell back to `name_en`. Fixed the two fallbacks (MockExamRoutes + demo seed → `name_ro`), but the systemic guarantee is missing. Add:
  - `curate-tutor` MUST author a Romanian `stem_template` per drillable KC — never rely on the name fallback for a practice question.
  - Extend `ContentValidator` (currently only checks name_ro/name_en non-blank) to FAIL/flag: (a) any learner-facing string that is not Romanian (heuristic: ASCII-only / English stopwords in name_ro / stem), (b) any drillable KC missing a `stem_template`. A bad KC must not be servable.

**G2 — Wire the viz/animation system INTO the lesson/drill surfaces.** The viz gallery (`/viz-demo`) works but is "not yet on Shell" — animations don't render inside lessons/drills. The lesson endpoint (`ApiLessonReply`) has NO viz/figure field. Add a `figure_spec`/`viz_id` to the served lesson + drill + misconception payloads and render it via the existing `vizRegistry` in `LessonScreen`/`DrillStack`. This is the #1 quality gap from the demo eval.

(See also the 12-item spec-coverage punch-list from the 2026-06-09 audit: HintControlRow missing, CalibrationPlot/error-states built-but-unmounted, mock-exam stub questions, Scratchpad thin, MasterySparkline not in FsrsReview masthead, /welcome routing overlap, VoxButton not in masthead.)

**G3 — Viz library quality pass (own session).** The `/viz-demo` gallery is the EXISTING viz primitives from prior sessions; the owner reviewed them 2026-06-09 and they are rough — "everything except the recursion tree looks like the old primitives, each with a lot of small things to fix." Confirmed sample: `SigmaStackedBar` (PS basis) shows per-item rounded deviations 1+1+2+3+4+6+9 (=26) but Σ = 25.8 — precision/consistency bug. The `SO-3 · Race condition + mutex` set also flagged (each animation has small issues). Run as a dedicated viz-audit-and-fix WORKFLOW using the trace-match + self-see correctness gate (one render→check-math→fix→re-render loop per viz), owner flags as the input list. Pairs with G2 (wire the polished viz into lessons/drills). NOT to be ground out in a long-context session — fresh session, cheaper per fix.

**G3 correction (owner, 2026-06-09):** the flagged issues are VISUAL, NOT numeric. For `SigmaStackedBar` the font was WAY too large for the figure, and the bar rendered ALL-BLACK — no distinct ink segments, no hairline seams, no accent-on-focus (contradicting its own caption). So the viz pass targets visual fidelity: typography scale per figure, segment/color rendering, focus + accent states, layout/proportion. Treat the numbers as correct unless a specific figure is independently shown wrong.

**G4 — Free-layout / "Track B" viz architecture (HEADLINE viz decision, owner-flagged 2026-06-09).** Owner's core viz vision = "elements roam freely anywhere on the page without visual hiccups (overlap/breakage)." Verified: this was SPECCED as "Track B" in `docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md` §6b (declarative viz-spec + ELK auto-layout → zero-overlap + agent-emits-intent-not-coordinates + Motion choreography) but DEFERRED, gated behind "prove hand-coding is the bottleneck." NOT implemented. What exists instead = 24 hand-coded fixed-geometry SVG primitives (each bakes its own x/y, e.g. `figures.tsx` W=480/H=360), wrapped in `AlgoStepperShell` (frame scrubber); the `--fig-*` "Figure system" is CSS color theming ONLY, no layout engine; only 1/24 wired to a student route (`vizRegistry.ts:9`). The owner's primitives-are-rough + free-roaming-missing feedback IS the deferral trigger. Decision needed: commit to building Track B (declarative spec + ELK/elkjs auto-layout + Motion) as the viz foundation — a from-scratch architecture effort, its own session — vs continue hand-coding primitives. G2/G3 (wire-into-shell, per-viz polish) are downstream of this choice: polishing 24 hand-coded primitives is wasted if Track B replaces the approach. RESOLVE G4 BEFORE G2/G3.
