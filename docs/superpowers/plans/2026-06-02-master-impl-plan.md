# Jarvis Tutor — Master Implementation Plan

> ⛔ **SUPERSEDED by `2026-06-02-master-impl-plan-v2.md` — do NOT build from this file.** v2 folded all 43 audit fixes + the 5 locked decisions + the frozen `interface-signatures-lock.md`; this v1 carries the factually-wrong Exposed `.default()` migration + the unbuildable atomic-upsert contract that v2 corrected.

**Status:** contract-locking artifact. NO code here.
**Date:** 2026-06-02 · **HEAD:** `b970585` (branch `door-compare`) · **Algorithm header verified:** FSRS-5-lite (`Fsrs.kt`)
**Source spec:** `docs/superpowers/plans/2026-06-02-tutor-ui-design-spine.md` (§2.1–§2.5, §4.1) + `2026-06-01-staged-tutor-buildout.md`
**Produced by:** workflow `wf_2ede1bfe-f26` (8 agents, repo-grounded).

---

## 1. Purpose & Rule

**The rule Alex demanded:** plan everything → freeze the cross-cutting contracts ONCE → then build phase-by-phase against frozen seams. No mid-build redesign, no schema-churn, no shape-mismatch. This doc sequences and contracts. Each phase gets its OWN detailed TDD plan (like the existing `foundation-theme-shell.md`) written at build time.

**Why frozen contracts first:** every UI surface and pipeline reads the same data model + the same API envelopes + the same KC schema. If those are not locked before anything builds against them, each surface invents its own shape (the ghost-component / phase-recomputed-everywhere anti-patterns). Changing a frozen contract later = migration + API version bump + every consumer patched.

### Already built vs new

| State | What |
|---|---|
| **Built (backend)** | FSRS-5-lite (`Fsrs.kt`), `FsrsCardRepo`/`fsrs_cards`, `KcMastery`/`kc_mastery` (flat EWMA), `GapPromotion`, `DrillGrader`/`GradeScoring`, `DrillGenerator`, `ContentSchema`/`ContentValidator`, `SympyTool`, full auth+GDPR stack, 6 PA KCs + 2 misconceptions |
| **Built (frontend)** | Door theme system + `palettes.ts` (6 palettes) + `figures.tsx`; `ThemeProvider`/`applyTheme`; `AppShell` masthead (nav slot **empty**); ~24 viz (registry = only `recursion-tree`); ~40 UI components incl. `DrillStack`/`FsrsReview`/`Scratchpad`/`KnowledgeLedger`/`SettingsMe`. All 4 Stage-0 viz bugs fixed |
| **New (this plan)** | 4 schema migrations + new tables (cross-cutting); correctness/verification engine; phase-gated teaching loop; new API endpoints; AppShell nav wiring; ~50 new frontend components across 29 surfaces |

### Decisive verdicts (locked, do not re-litigate)
- **Persistence:** stay SQLite single-user. Every new column nullable-or-DB-defaulted; enums as VARCHAR (never native pg enum); `SchemaUtils.createMissingTablesAndColumns` is the only migration path. Postgres-survivable by construction.
- **Mastery model:** keep flat EWMA + add a stored `phase` column. NO PFA/IRT now (no per-item params, single-user). PFA parked behind the `PhaseModel`/`NextKcSelector` interfaces as a Stage-2+ swap.
- **Scheduler:** keep `Fsrs.kt` FSRS-5-lite as-is. The "FSRS-4.5/FSRS-6" labels elsewhere are WRONG vs the real header. No scheduler rewrite — the scheduling work is the B1 join + report-wrong pause.
- **Selection:** deterministic scorer (prereq-gated → lowest-mastery → interleave-cap). NOT Thompson sampling (no reward priors). Same `NextKcSelector.select(...)` interface a future `ThompsonSelector` implements.
- **Trust language:** badge ALWAYS reads "matches your lecture / faithful to your source" for FAITHFUL. NEVER "verified" or "correct."

---

## 2. Locked Cross-Cutting Contracts (the anti-refactor core — FROZEN before any build)

### 2.1 Data model — every new table/column

**Migration discipline:** every new column is `NULLABLE` or has a DB-level `DEFAULT`. No NOT-NULL-without-default on a populated table (Exposed can't backfill). Schema is NEVER ALTERed before a verified `tools/db-backup` off-box dump with the correct ~871-card count.

#### CHANGE 1 — `fsrs_cards` (`FsrsCards.kt:28`) — ADD
| Column | Type | Rule |
|---|---|---|
| `kc_id` | `varchar(64).nullable()` | Soft ref to `KnowledgeConcept.id` (KCs live in YAML, no DB FK). GAP_PROMOTION → NULL; RUBRIC_CRITERION → KC id. **Do NOT reuse `source_ref varchar(32)`** — too narrow, occupied. |
| `status` | `varchar(16)` default `'ACTIVE'` | Enum `ACTIVE \| QUARANTINED \| PAUSED`. 3-value (not boolean): QUARANTINED = audit-not-cleared; PAUSED = student REPORT-WRONG. §2.5 needs both distinguishable. |
| `paused_at` | `timestamp().nullable()` | Set by REPORT WRONG; distinct from quarantine. |

Backfill: legacy ~871 rows → `kc_id=NULL`, `status='ACTIVE'`. Targeted UPDATE sets `kc_id` for RUBRIC_CRITERION rows whose `source_ref` matches a KC id. **Card count unchanged (the 871 invariant).** `due()`/`forecast()` gain `WHERE status='ACTIVE'` (additive).

#### CHANGE 2 — `kc_mastery` (`KcMastery.kt:14`) — ADD
| Column | Type | Rule |
|---|---|---|
| `phase` | `varchar(16)` default `'intro'` | Enum `intro \| practice \| retrieval \| mastered`. **Stored, not derived at call time.** SOLE owner = pure fn `PhaseModel.transition(...)` called INSIDE `KcMasteryRepo.record()` in the same transaction. |
| `entry_phase` | `varchar(16).nullable()` | Phase at first KC entry from placement; drives the §4.1 phase-count gate. NULL = treat as `intro`. |

Backfill: replay `PhaseModel.transition` over existing `(ewma_score, observations)`.

#### CHANGE 3 — `KnowledgeConcept` (`ContentSchema.kt:38`, YAML-backed) — ADD nullable/defaulted fields (existing 6 PA KCs deserialize unchanged)
| Field | Default | Purpose |
|---|---|---|
| `verification_status: String` | `"unverified"` | `unverified \| pending \| faithful \| uncertain`. The per-fact status the trust badge reads. **status != faithful ⇒ MUST NOT enter SR or cold-start corpus** (§2.5). |
| `invariant: String?` | `null` | Precise math/logical invariant the two-family re-derivation + SymPy leg checks. |
| `grader_rules: List<String>` | `emptyList()` | Machine-checkable rubric items (SymPy/test assertions) the non-LLM leg runs. |
| `stem_template: String?` | `null` | Canonical drill stem; stops `DrillGenerator` hallucinating stems. |

**Teaching fields:** `phase_plan: List<String>?` (ordered subset of 5 phases this KC runs; default all), `far_transfer_stem: String?` (Gick-Holyoak), `self_explanation_prompt: String?` (Chi/Renkl).
**`Misconception` (`ContentSchema.kt:81`):** add `verification_status` (default `"unverified"`), `self_explanation_prompt: String?`, `figure_spec: String?`.
`ContentValidator` rule: `grounding_tier=='strict'` ⇒ `invariant != null` AND `grader_rules` non-empty; `verification_status` ∈ the four literals.

#### CHANGE 4 — NEW `session_summaries` (Surface 5)
`id varchar(26) PK · user_id FK · started_at ts · closed_at ts · cards_reviewed int · mastery_delta_json text · narrative text nullable`

#### CHANGE 5 — NEW `attempts` (calibration / far-transfer / self-explanation — grade route currently writes a non-queryable jsonl log only)
`id PK · user_id FK · kc_id varchar(64) · task_id · problem_id · phase varchar(16) · confidence varchar(12) nullable (DEFINITELY|MAYBE|GUESS|IDK) · correct bool · score double · scaffold_level int(0..4) · is_far_transfer bool default false · self_explanation text nullable · recorded bool · graded_at ts` · INDEX `(user_id, kc_id, graded_at)`. Subsumes a separate calibration_log.

#### CHANGE 6 — NEW `verification_audit` (audit-of-record; one row per KC-claim per run)
`id PK · claim_id varchar(96) ("{kc_id}:{claim_kind}:{ordinal}") · kc_id · subject · claim_kind (DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM) · status · doc · page · span_start · span_end · relocated_offset nullable · fuzzy_distance · family_a · family_b · nonllm_leg (SYMPY|TEST_EXEC|HUMAN_GOLD|NONE) · nonllm_result text nullable · agree bool · roundtrip_pass bool · collapsed_to_one_family bool · audited_at ts · audit_run_id · notes`

#### CHANGE 7 — NEW `report_wrong` (grading fail-safe trail)
`id PK · user_id FK · kc_id · card_id nullable · grade_attempt_raw text · reported_at ts · resolution (OPEN|REVERIFIED_FAITHFUL|RETRACTED) nullable`

#### CHANGE 8 — persisted `Problem` (`PdfProblemExtractor.Problem`) — ADD (declare only; B2 threads in Phase 3) `sourceRefs: List<SourceRef>` (default empty) + `modelTag: String?` (default null). Prevents a Problem-schema migration mid-build.

All new tables registered in `installTutorContext`'s `SchemaUtils.createMissingTablesAndColumns(...)` (`TutorRoutes.kt:2603`).

### 2.2 API surface — every endpoint + req/resp shape (wire names)

| Method · Path | State | Req | Resp |
|---|---|---|---|
| `GET /api/v1/queue/today` | NEW | `?limit=N` | `{ items:[{kc_id, kc_name_ro, kc_name_en, subject, phase, mastery_ewma, fsrs_card_id\|null, verification_status, worked_example_first:bool, mode:"worked\|drill\|retrieve"}], total_due, day }`. Server-ordered (overdue FSRS → intro KCs → interleave within subject). Quarantined/non-faithful OMITTED. Supersedes `/fsrs/due` for surface 0a. |
| `GET /api/v1/mastery` | NEW | `?subject=X` | `{ subjects:[{subject_id, subject_name, kcs:[{kc_id, kc_name_ro, kc_name_en, phase, ewma_score, observations, last_graded_at\|null, verification_status}]}] }`. **The single shape `MasterySparkline` reads** (0a/0b/5/6/0h/15). |
| `GET /api/v1/calibration` | NEW | `?subject=X` | `{ buckets:[{confidence, attempts, correct, accuracy}], total_attempts }`. Aggregated from `attempts`; min-n = server constant. |
| `POST /api/v1/session/close` | NEW | `{ reviewed_card_ids, mastery_deltas, session_start_at }` | `{ session_id, narrative, cards_reviewed, kcs_moved_to_mastered }`. **Server recomputes deltas authoritatively** (client values NOT trusted). Idempotent. |
| `POST /api/v1/drill/grade` | **EXTEND** (`TutorRoutes.kt:1917`) | ADD `kc_id`, `confidence`(enum\|null), `scaffold_level:int?`, `is_far_transfer:bool=false`, `self_explanation:string?` | ADD `verification_status`, `kc_quarantined:bool`, `phase`, `next_phase_action:"advance\|hold\|remediate"`, `cross_checked:bool`. **Atomic (B1):** ONE txn = mastery.record()+phase, insert `attempts`, upsert `FsrsCard(RUBRIC_CRITERION, kc_id)` — **only when verification_status==faithful & not quarantined**. Non-faithful ⇒ `recorded=false, kc_quarantined=true`, skip writes. Every `correct=false` cross-checked by a 2nd family; disagreement ⇒ uncertain, NOT shown wrong. |
| `POST /api/v1/fsrs/{id}/report-wrong` | NEW | `{ kc_id, reason? }` | `{ ok, paused_kc_cards:N }`. ONE txn: `status='PAUSED'` on all the user's cards for that kc_id + insert `report_wrong(OPEN)` + KC `verification_status→pending`. Never deletes/auto-clears. |
| `GET /api/v1/fsrs/forecast` | EXISTS | — | `{ dueNow, tomorrow, thisWeek, thisMonth }`. **LOCKED.** `ForecastDotPlot` consumes directly. |
| `GET /api/v1/fsrs/due` | EXISTS | — | ADD `kc_id` + `verification_status` per card (backward-compatible). |
| `POST /api/v1/admin/verify/{kcId}` | NEW (owner-only) | `{}` | per-claim audit result; `kc_status=FAITHFUL` iff ALL claims FAITHFUL. |
| `GET /api/v1/verify/{kcId}/status` | NEW (trust badge) | — | `{ verification_status, badge_text, claims:[{claim_kind, status, source:{doc,page,span_start,span_end,quote}}], honest_floor }`. `badge_text` ALWAYS "matches your lecture / faithful to your source" for FAITHFUL. |
| `POST /api/v1/mock-exam/start` · `/{id}/submit` · `GET /{id}/result` | NEW | — | start→questions; submit→**200 sync for deterministic KCs, 202+poll only for open-ended LLM grading**; result poll. |
| `GET /api/v1/placement/questions` · `POST /placement/submit` | NEW | — | 8 questions (1/cluster); submit writes `kc_mastery.entry_phase`. |
| `GET /api/v1/lab/{id}/objective` · `POST /lab/{id}/grade` | NEW | — | V86 deferred ⇒ `stdout_contains` assertions only. |

### 2.3 Internal Kotlin interfaces to LOCK (so the model swap never refactors callers)
- `PhaseModel.transition(ewma, observations, mastered, current): Phase` — pure; SOLE writer of `kc_mastery.phase`.
- `NextKcSelector.select(userId, subject?, candidates, recentShapes): QueueItem` — deterministic now; same signature a future `ThompsonSelector` implements.
- `ScaffoldPlanner.planFor(kc, mastery): List<Phase>` — `kc.phase_plan ∩ not-yet-mastered`, honoring `entry_phase` (the §4.1 gate).
- `VerificationGate.gate(kc): ALLOW|DENY` — single chokepoint for SR-entry / cold-start seed / `GapPromotion.promote` / B1 card upsert. ALLOW iff `verification_status==faithful` AND no OPEN report_wrong. **Ignores student attempt counts** (no laundering by consistency).
- `VerificationStatus.transition(from, AuditOutcome): VerificationStatus` — pure; encodes §2.5.

### 2.4 Verification state machine (§2.5, LOCKED)
```
UNVERIFIED --(curate-tutor emits claims)--> PENDING
PENDING --(all legs agree + roundtrip + non-LLM pass)--> FAITHFUL
PENDING --(legs collapse to one family / definitional w/ no gold span / no non-LLM leg)--> UNCERTAIN
PENDING --(legs disagree / roundtrip fails / span re-locate fails)--> FAILED
FAITHFUL --(REPORT WRONG)--> PENDING
```
Invariants: **no path reaches FAITHFUL without BOTH a non-LLM-leg-pass AND families-agree.** A 429-collapse (both legs same family) ⇒ UNCERTAIN. Blank/aliased model id ⇒ collapsed ⇒ UNCERTAIN. No auto-clear from student attempts.

---

## 3. Dependency-Ordered Build Phases

Keystone-first: data model → correctness engine → teaching engine → frontend foundation → core loop → rest. **Phase 4 (foundation-theme-shell) is ~70% built already** (4 of 6 tasks committed on `door-compare`).

| Phase | Area | Depends on | Ships | Detailed plan doc (to write at build time) |
|---|---|---|---|---|
| **0** | Stage-0 exit gate | — | Clean `main` baseline, verified `tools/db-backup`, `./gradlew check`+`npm test`+e2e green, 4 viz fixes (DONE), CI verify-net | `2026-06-02-stage0-exit-gate.md` |
| **1** | **Data Model (keystone)** | Phase 0 backup verified | Changes 1–8: migrations + new tables, `PhaseModel`, `VerificationStatus`, `ContentValidator` strict rule, Problem widen. Migration-idempotency CI assertion. | `2026-06-02-data-model-lock.md` |
| **2** | **Correctness & Trust Engine** | Phase 1; real Curs 1 PA.pdf (open input) | Span re-locator, `TwoFamilyDeriver`, `NonLlmLeg`(SymPy), `SpanClaimRoundTrip`, `VerificationRunner`, `VerificationGate`, report-wrong, `verifyContent` Gradle task, curate-tutor reconcile | `2026-06-02-correctness-engine.md` |
| **3** | **Teaching Engine + B1-B6** | Phases 1–2 | phase ownership in `record()`, `attempts` repo, `ScaffoldPlanner`, `NextKcSelector`, extended atomic `drill/grade`, `queue/today`+`mastery`+`calibration`+`session/close`, B2 keystone | `2026-06-02-teaching-engine.md` |
| **4** | **Frontend Foundation (theme/shell)** | Phase 0 viz; door-compare→main; Brand §2.1 confirm | **Mostly built.** Remaining: mount ThemeProvider+AppShell app-wide; nav links; token audit; self-see gate | `foundation-theme-shell.md` **(exists; extend)** |
| **5** | **Frontend Core Loop** | Phases 1–4 | `MasterySparkline`; `FeedbackLadder`; `PredictGate`; `ConfidenceRow`; `TermLanding`/`EchoBand`; `LessonEntryBand`; `RetrievalGate`; `MisconceptionRibbon`; `HintControlRow`; `TrustBadge`; FSRS opacity-flip+`ForecastDotPlot`; Playwright gate | `2026-06-02-core-loop-surfaces.md` |
| **6** | **Frontend Remaining Surfaces** | Phase 5; all read endpoints | meta-nav (0a/0b/0h/0i), review/memory (5/6/15), high-stakes (mock/Day-Of/placement), foundation (onboarding/empty/error/bilingual/GDPR), specialized (lab/scratchpad/voice tombstone) | `2026-06-02-remaining-surfaces.md` |

**Shared-primitive discipline:** `MasterySparkline` built ONCE (Phase 5 T1); 6 consumers mount it inline. Every "create component" pairs with "mount it in [exact file]" + JSX diff. Final Playwright gate per surface: all `data-testid` paint + zero 4xx/5xx on first paint + click-through with no `/404|HTTP \d{3}|not found|error/i`.

**Parallelism:** Phase 4 (frontend-only) runs alongside Phases 1–3. Phase 5 needs both 4 (shell) AND 3 (grade/queue/verification endpoints).

---

## 4. Per-Area Detail

### Area A — Data Model (Phase 1, keystone)
- **Goal:** lock the foundational schema; do the migration+backfill over the live 871-card DB right the first time.
- **Tasks:** ADR + migration-idempotency CI · backup-precondition gate · fsrs_cards (kc_id+status+paused_at) + 871-invariant test · kc_mastery phase + `PhaseModel` owned in `record()` · KC 4 fields (backward-compat deserialize test) · `ContentValidator` strict rule · `VerificationStatus.transition` table-test · `session_summaries`+`attempts` registered · Problem widen.
- **Refactor-risk if not locked:** bare-boolean status ⇒ can't split quarantine vs report-wrong later. kc_id reusing `source_ref(32)` ⇒ truncation+mismatch. phase derived-at-call ⇒ every caller drifts. NOT-NULL-without-default ⇒ hot-DB migration fails on deploy.

### Area B — Correctness & Trust Engine (Phase 2)
- **Goal:** every KC fact clears an audit (re-locate against LIVE PDF → two-family agree w/ ≥1 non-LLM leg → span↔claim round-trip) BEFORE entering SR. Closed LLM stack certifies "faithful to your source," NEVER "correct."
- **API/internals:** `admin/verify/{kcId}`, `verify/{kcId}/status`, `fsrs/{id}/report-wrong`; `VerificationGate` chokepoint; `VerificationRunner.audit` (family A = `RelayLlm` claude-max, family B = `OpenRouterChatLlm :free`).
- **Tasks:** `LiveSourceLocator` (page+offset+Levenshtein guard) · `NonLlmLeg` (SymPy/HUMAN_GOLD/TEST_EXEC stub) · `TwoFamilyDeriver` (+collapse detect) · `SpanClaimRoundTrip` (3rd, different family) · `VerificationRunner.audit` · `VerificationGate` · grading cross-check fail-safe · report-wrong+pause · reconcile curate-tutor (emits PENDING) · `verifyContent` Gradle task.
- **Refactor-risk:** two-LLM agreement alone promoting definitional claims ⇒ both free models launder the same mistaught error → **definitional KCs = UNCERTAIN-until-gold-span**. REPORT WRONG as soft flag (not a DB write that pauses+flips) ⇒ disputed KC keeps seeding corpus. `claim_id` ordinal must be stable across re-audits.
- **Degraded note:** current `pa-lecture-01.md` has 0 form-feeds (hand-typed paraphrase) ⇒ page-anchor inert; slice-1 runs offset+fuzzy only, marks page-anchor DEGRADED until the real PDF lands.

### Area C — Teaching Engine (Phase 3)
- **Goal:** turn flat-EWMA + FSRS + LLM-grader into the §4.1 mastery-gated loop (worked-example-first faded by mastery, phase-count gated by entry mastery, interleaving, far-transfer, self-explanation) WITHOUT a model rewrite.
- **Tasks:** `PhaseModel` (pure, boundary tests) · `attempts` repo + bucket aggregation · `ScaffoldPlanner` · extended grade = atomic record+attempt+card (B1, faithful-gated) · `NextKcSelector` (prereq DAG via `edges.yaml`) · `queue/today` · `mastery`+`calibration`+`session/close` · schema fields + `DrillGenParser` map · PFA/Thompson DECISION RECORD (no code) · B2–B6 blockers.
- **Refactor-risk:** B1 not-one-txn ⇒ mastery moves with no card / orphaned cards. `NextKcSelector` signature unlocked ⇒ model swap forces caller changes. `attempts` absent ⇒ calibration/session-close build on non-queryable jsonl. **Content-ghost risk:** ship schema fields AND populate `self_explanation_prompt`/`far_transfer_stem`/`faded_worked` on the Stage-1 computational KC before claiming the §4.1 loop shipped.

### Area D — Frontend Foundation (Phase 4, ~70% built)
- **Tasks:** T1–T4 **DONE** on `door-compare` (`applyTheme`, `ThemeProvider`, `ThemePicker` strip, `AppShell`). **T5 (NOT done):** merge→main, wrap router in `ThemeProvider` (`main.tsx`), replace inline `<header>` in `App.tsx` with `<AppShell nav={...}>`, move nav links+ledger+READY into nav prop. **T6:** self-see render gate (pick magenta-lime, confirm whole-app recolor+persist; Playwright app-shell paints + zero-4xx/5xx + nav click-through).
- **Refactor-risk:** `brutalistVars()` sets only 3 vars — any surface using raw hex / a non-driven token won't recolor. **Audit hardcoded-`#FDE047` surfaces BEFORE T6.** Nav must use spine names (workspace/oggi/materie/ledger/me); oggi/materie stubbed-disabled until those routes exist.

### Area E — Frontend Core Loop (Phase 5)
- **Tasks:** `MasterySparkline` (build once) · token/motion audit · AppShell nav · `FeedbackLadder` (mount in `DrillStack`) · `PredictGate` (mount in `DrillStack`) · `ConfidenceRow` (shared) · `TermLanding`+`EchoBand`+`InvariantRule`+`PredictionGate` · `LessonEntryBand`+`ConcreteQuestionBlock` · `RetrievalGate` · `MisconceptionRibbon` (mount at L4) · `HintControlRow` · `TrustBadge`+`QuarantineWarning` (mount in `FsrsReview`) · FSRS opacity-flip+`ForecastDotPlot` · Playwright gate.
- **Refactor-risk:** **`DrillStack` state machine** lacks rung-counter/prediction/confidence — extend `StackPhase` with `currentRung:0-4`, `prediction`, `confidence` BEFORE T4/T5/T11 or each re-derives local state. **`ProgressStrip` uses `rounded-full`** (R3 violation) — decide square-pip patch vs variant before mounting in 0c/0d/0e. `worked_example_first` MUST be a server decision (degrade to `true` if endpoint absent). `FsrsReview` rotateY→opacity is a DOM-structure change.

### Area F — Frontend Remaining Surfaces (Phase 6)
- **Tasks:** `LangToggle`+`BilingualText`+`LangContext` · `EmptyState` · `InlineErrorCard`/`DegradedBanner`/`FatalErrorPage` · `LearnerQueueList`+`ModePill`(0a) · `SubjectCard`+`RetentionGapBadge`(0b) · `LedgerRow`(0h) · `BottomTabBar`+`TabIcon`(0i) · FSRS upgrade · `SessionWrapPane`(5) · `CalibrationPlot`+`CalibrationTable`(6, NOT Plotly) · `OnboardingShell`+`ToggleRow`(4) · `PlacementShell`(10) · `MockExamShell`(0g+9) · `DayOfShell`(3) · `RightsSidebar`(12) · Scratchpad 4-tab(8) · `LabShell` xterm(7, V86 deferred) · `VoxButton` tombstone(11) · per-surface Playwright gates.
- **Refactor-risk:** `MasterySparkline` prop interface frozen at Phase-5 T1 — no required prop added without a codemod across 6 sites. Mock async: 200 sync deterministic / 202 only LLM. Surface 0b 5-equal-column = R6 exception, **needs Alex sign-off** (no 4-col workaround). `BilingualText` glossary = hardcoded `Set<string>` (not an API call). `@xterm/xterm` lazy-imported (code-split). DayOf needs an exam-date picker in onboarding S5/SettingsMe or it's inaccessible.

---

## 5. Open Inputs from Alex (he supplies raw files; verifies NOTHING)

| Input | Why it gates | Notes |
|---|---|---|
| **Raw lecture PDFs** — "Curs 1 PA.pdf" first, then PA, then PS/POO/ALO/SO-RC | Gates Phase 2: current `pa-lecture-01.md` is hand-typed paraphrase (0 form-feeds); the page-anchor half of the locator is inert without a real `pdftotext` extraction into `_sources/`. Schema defaults safely to `unverified` without it. | Hands over the file ONLY. No oracle inversion. |
| **Brand/theme §2.1 confirm** (one-time yes/no) | Gates Phase 4 T5 ship: brutalist-yellow default + warm opt-in + picker=palette+layout only + Concept-row demo-only. | Confirmable with zero subject knowledge. "Warm default" ⇒ AppShell token map changes + audit every inheriting surface. |
| **Voice unlock path** | Gates only Surface-11 tombstone copy. Memory says ElevenLabs $5/mo or clone Alex's voice; spine says Piper free/MIT. | No build depends on it until the tombstone. |
| **Surface 0b 5-equal-column sign-off** (R6 exception) | Gates Phase 6 T7 mount. | If rejected, `SubjectCard` redesigns before mount. |

**Self-determined (NOT Alex):** the computational-KC choice (pa-kc-005/006 with a sync-gradable canonical answer + real span, else author one) — derived from the PDF by the system.

---

## 6. Explicitly Deferred

| Deferred | Why | Unlock |
|---|---|---|
| **Warm-skin app-wide** | `DoorWarm` works on the door route only; app-wide warm needs a per-component token audit. `AppShell` passes `skin='brutalist'` hardcoded; `onSkin` is a no-op stub (`TODO(warm-skin)`). | Its own follow-on plan after Phase 6. |
| **Voice mode** | Parked 2026-05-18. Ships as a disabled `VoxButton` tombstone only. | Alex's unlock decision + a plan. |
| **PFA / IRT / Thompson** | No per-item params, single-user, no reward priors. Parked behind locked `PhaseModel`/`NextKcSelector` interfaces. | Stage-2+ interface-compatible swap. |
| **Postgres / multi-user / cohort C1-C7** | SQLite single-user is the locked path; cohort out of scope for the single-user spine. | Future migration; enums-as-VARCHAR keeps DDL portable. |
| **Lab V86 VM** | `LabShell` ships xterm.js (typed input only). Objectives = `stdout_contains` only until a real VM. | Later VM plan. |
| **PS vision/MinerU extraction** | PS lectures need vision PDF extraction; `SympyTool` is symbolic-only, no MinerU/DSPy sidecar. | Stage-2+ breadth after PA end-to-end. |

---

**Build order is a hard dependency chain:** Phase 1 (data model) → Phase 2 (correctness) → Phase 3 (teaching+B1-B6) before ANY core-loop surface (Phase 5). Phase 4 (theme/shell) runs parallel to 1–3 and must land before Phase 5. Each phase's detailed TDD plan is written at its build time, not now.
