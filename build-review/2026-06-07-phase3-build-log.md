# Phase 3 (Area C — Teaching Engine) — autonomous build log

Owner (Alex) authorized autonomous Phase-3 build 2026-06-07 ("just go do them, get work started on phase 3, I'm going out, keep track of any decisions, get as much done as possible"). Plan-first satisfied: Area C is specced in `docs/superpowers/plans/2026-06-02-master-impl-plan-v2.md:201-209` + frozen signatures in `interface-signatures-lock.md` + `data-model-lock.md`. This file = the running decision + progress log for the unattended run.

## Standing decisions (made autonomously)

- **D-AUTO-1 — per-group selective commit.** Each group commits ONLY its own Phase-3 files (explicit `git add <paths>`, NEVER `-A`/`clean`/branch-switch). Rationale: checkpoint unattended progress; the door landmine (`tutor-web/src/door/`, `main.tsx`, `palettes`) is uncommitted+unbacked-up — a stray `-A` would entangle it. Not pushed (owner didn't ask to push).
- **D-AUTO-2 — build order (dependency chain, master-plan:201-209 + 287):** G1 selection layer (ScaffoldPlanner, PrereqGraph, NextKcSelector) → G2 atomic grade (B1/B2/B3, faithful-gated, 409-if-not-ACTIVE) → G3 read routes (queue/today, mastery, calibration) → G4 session/close + placement + entry_phase + exam_dates → G5 mock-exam SYNC-200 → G6 generator P3-GEN + honesty P3-HONESTY → G7 serve wiring P3-MISC/LADDER/GHOST. PFA/IRT/Thompson + gold-set GATE = DEFERRED (decision-record only, §6.1/§7).
- **D-AUTO-3 — do not rebuild Phase-1 pieces.** `PhaseModel` (PhaseModel.kt), `kc_mastery` phase/entry_phase columns, `attempts` table, `upsertRubricCriterion` (B2) already shipped in Phase 1. Phase 3 builds logic/routes ON them.
- **D-AUTO-4 — TDD + fix-claim discipline per group.** Each task: failing test first → impl → green; class-killing assertions on the dangerous direction (atomicity, faithful-gating, 409). Full Kotlin suite green per group + independent adversarial review before commit.
- **D-AUTO-5 — frozen locks win.** Every signature taken from `interface-signatures-lock.md` (canonical-on-conflict). No wire-shape invention; serve fields paired with a named Phase-5/6 consumer (H16, no ghosts).

## Progress

| Group | Scope | Status | Commit |
|---|---|---|---|
| G1 | ScaffoldPlanner + PrereqGraph + NextKcSelector (+QueueItem/QueueMode forced by frozen sig) | DONE (suite 1189✓, review SHIP) | d905022 |
| G2 | atomic grade (B1/B2/B3, faithful-gated, 409) | DONE (suite 1195✓, review SHIP) | 2318d71 |
| G3 | queue/today + mastery + calibration | DONE (suite ✓, review SHIP) | 06ca1a5 |
| G4 | session/close + placement + entry_phase + exam_dates | DONE (suite 1219✓, review SHIP) | 5dcfdd4 |
| G5 | mock-exam SYNC-200 (+ B6 cascade fix) | DONE (suite 1228✓, review SHIP, +PM cascade fix) | 8a182c2 |
| G6 | P3-GEN generator + P3-HONESTY spot-check | DONE (suite 1231✓, review SHIP) | (this commit) |
| G7 | P3-MISC-SERVE + P3-LADDER-SERVE + P3-GHOST-FIELDS | pending | — |

## Decision/event detail (appended as the run proceeds)

### G1 — selection layer (DONE, SHIP, suite 1189/0)

New files: PrereqGraph.kt, ScaffoldPlanner.kt, NextKcSelector.kt, QueueItem.kt (+3 tests). Phase-1 types reused, not rebuilt (PhaseModel, KcMastery, phase_plan, PrereqEdge, EdgesFile).

Decisions made by the builder (all forced by frozen signatures, no wire-surface widening):
- **D-G1-1:** Created `QueueItem`+`QueueMode` now (nominally Group-3 wire types) because the FROZEN `NextKcSelector.select` returns `QueueItem?` (lock §C/§D). Built verbatim from lock §C.
- **D-G1-2:** `PrereqGraph` injected into `LockedNextKcSelector` via CONSTRUCTOR (frozen `select(...)` carries no graph param; lock §D says graph memoized in TutorContext).
- **D-G1-3:** `worked_example_first` resolved server-side = `kc.worked_example_first || phase==intro` (QueueItem field non-nullable ⇒ a value is mandatory; interprets data-model-lock "degrade to true for novices" as intro-phase).
- **D-G1-4:** interleave-cap (lock §D 3rd scoring step) uses resolved `QueueMode.name` as the same-shape key (KcCandidate has no shape field); INTERLEAVE_CAP=3, deterministic id tie-break, falls through when no differently-shaped candidate. Class-killers remain on prereq-gating + 0-KC degrade.
- **D-G1-5:** `ScaffoldPlanner` fully-mastered KC ⇒ `emptyList()` (lock §F:147); entry_phase floor trims phases rank<floor; null mastery/entry_phase ⇒ intro floor; unknown phase_plan literal dropped via runCatching.
- **D-G1-6:** `PrereqGraph` cycle-safety = iterative DFS + visited set; self-loops/cycle-backs stripped; cyclic closure degrades deterministically to "all other reachable nodes" (no hang).

### G2 — atomic grade (DONE, SHIP, suite 1195/0)

Files: TutorRoutes.kt (+151/-9), FsrsCards.kt (+16, findRubricCriterionCard for 409 pre-check), DrillGradeAtomicTest.kt (NEW, 6 tests) + 5 existing E1/E2/E3 grade tests reseeded.

Contract delivered: LLM grade resolved OUTSIDE the txn (H4) → ONE `transaction(ctx.db)` loops gated-faithful kcIds doing recordIn (B3) + attempts insert (H1) + card upsert (B1 fault point) + upsertRubricCriterion (B2); all-or-nothing rollback; 1:N kcIds; faithful-gated; 409 not-ACTIVE. Review verified all 9 points against code incl. a REAL fault-injection rollback test.

Decisions:
- **D-G2-1:** Faithful-gate = `VerificationGate.gate` in the per-KC loop (status from `resolveStatus` + `hasOpenReportWrong`). This is the D-RF2 owner-ratified ADMISSION caller ("B1 upsert" site) — **VerificationGate was a zero-caller ghost (completeness-audit F2); now wired in production here.** Not a serve-filter, not flag-gated.
- **D-G2-2:** Non-faithful KC ⇒ skip writes, `recorded=false`, `kc_quarantined=true`, **HTTP 200** (master-plan:113). 409 reserved for the not-ACTIVE card only. KC absent from corpus ⇒ DENY (fail-closed; resolveStatus needs a live kc for content_hash).
- **D-G2-3:** **Closed a real prior gap** — pre-existing E1/E2 grade tests recorded mastery over UNVERIFIED KCs (now forbidden by the gate). Per frozen-locks-WIN, updated the TESTS to seed faithful B8 rows (preserving recording-intent over a now-faithful KC), did NOT weaken the route.
- **D-G2-4:** Added internal no-op `drillCardUpsertHook` B1 fault-seam (fires before the LAST in-txn write) for the rollback class-killer; does not bypass the gate.
- **D-G2-5 (scope):** H15 reply fields (`verification_status`/`phase`/`next_phase_action`/`cross_checked`) + inline `misconception{}`/`ladder_rungs[]`/`self_explanation_prompt` serve fields deliberately NOT built here — they are G7 serve-wiring (master-plan:113/206/207). Attempts already STORE student_confidence/scaffold_level/is_far_transfer/self_explanation (H1 inputs wired). No ghost: the fields are stored+consumed-by-grade, serve-exposure is the named G7 task.

### G3 — read routes (DONE, SHIP, suite ✓)

Files: QueueMasteryCalibrationRoutes.kt (NEW — 3 routes + frozen DTOs), TutorRoutes.kt (1-line registration), QueueMasteryCalibrationRoutesTest.kt (NEW, 12 tests). Read-only; zero schema writes. RED-proven class-killers (broke 2 spots → exactly 2 tests failed).

Decisions:
- **D-G3-1 (the queue trust-gate question, resolved):** `queue/today` **EXCLUDES** non-faithful KCs — does NOT surface-with-degraded-badge. Authority: route-table:109 "Quarantined/non-faithful OMITTED", QueueItem KDoc, KcCandidate §D, D-RF2 owner-ratification ("queue/today filter calls hasOpenReportWrong"). Implemented via `VerifyAdmin.resolveStatus` (folds D8 staleness + D3 dispute) keeping only faithful, PLUS the shared `hasOpenReportWrong` belt-and-braces. `verification_status` is carried per surviving item.
- **D-G3-2:** queue new-KC = ONE item from `LockedNextKcSelector` (frozen single-item `select`); `total_due` = count of FSRS-due ACTIVE cards. No invented multi-item ranking.
- **D-G3-3:** calibration `?subject=X` resolves the subject's KC ids from the corpus + filters attempts by kc_id membership (KC ids lowercase `pa-kc-001` vs subject `PA` ⇒ a prefix heuristic would silently return nothing).
- **D-G3-4:** calibration excludes NULL `student_confidence` (no predicted point on the reliability curve); bucket order DEFINITELY>MAYBE>GUESS>IDK; per-user scoped.
- **D-G3-5:** mastery = §M BAND (ewma_score + observations, NO history series); bilingual subject_name_ro/_en from the corpus (not hardcoded); cold-degrade ewma 0.0 / obs 0 / last_graded_at null.
- **D-G3-6:** read-only discipline — private `readMastery` helper rather than widening `KcMasteryRepo`'s public API.

### G4 — session/close + placement + exam_dates (DONE, SHIP, suite 1219/0)

Files: SessionPlacementExamRoutes.kt (NEW — 5 endpoints), TutorRoutes.kt (1-line mount), SessionPlacementExamRoutesTest.kt (NEW, 11 tests). All writes per-user scoped + transactional. Phase-1 tables confirmed present (SessionSummariesTable/AttemptsTable/ExamDatesTable) — no invented migration.

Decisions:
- **D-G4-1:** Placement cluster source — live corpus has only **2** distinct clusters (both PA), not 8. Route is corpus-driven: one representative KC per DISTINCT cluster present (deterministic, first-by-id). The "8" was the PA-cluster expectation, not a hard count.
- **D-G4-2:** `question_id = "plq-<kcId>"` (deterministic/reversible) so submit maps answer→KC with no server-side question store.
- **D-G4-3:** Placement grading deterministic, **NO LLM on serve** (no-paid): non-blank ⇒ 1.0, blank ⇒ 0.0; entry_phase via PhaseModel.transition (one observation reaches at most intro — placement raises the floor, never claims mastery off one item).
- **D-G4-4:** entry_phase write = `max(existing, candidate)` by Phase.ordinal — NEVER regresses a higher phase (the monotonicity class-killer).
- **D-G4-5:** session/close deltas SERVER-recomputed from this user's attempts in window [session_start_at, now] (client sends none, L1); writes one session_summaries row; empty/zero-width window ⇒ empty deltas.
- **D-G4-6:** exam-dates = GET/PUT `/api/v1/me/exam-dates` (CHANGE-9:97 canonical; route-table omits it). One row per (user,subject), PUT upserts (no dup); inherits the /me/ auth whitelist.
- **D-G4-7 (downstream flag):** §2.2 left the `mastery_deltas` inner shape + exam-dates GET envelope unspecified → chose server-authoritative `ApiMasteryDelta{attempts,correct,ewma_after,phase}` + `ApiExamDatesReply{exam_dates:[...]}`. Outer wire keys are verbatim from the lock. **Phase-6 SessionWrapPane/DayOf/SettingsMe must align to these inner names (or adjust there).**

### G5 — mock-exam SYNC-200 (DONE, SHIP, suite 1228/0)

Files: MockExamRoutes.kt (NEW — start/submit/result), MockExamTable.kt (NEW — `mock_exams`, result-of-record NOT `mock_exam_jobs`), Migration.kt (register), TutorRoutes.kt (mount + cascade fix), MockExamRoutesTest.kt (NEW, 9 tests), MeDeleteCascadeTest.kt + MeRoutesTest.kt (cascade fix).

Decisions:
- **D-G5-1:** Honored the H13/F1 freeze: submit ALWAYS 200; no 202, no poll route, no `mock_exam_jobs`. New `mock_exams` table is the SYNC result-of-record (questions at start; score+kc_results+narrative stamped inline at submit; no job status/poll/202). Review verified no async surface.
- **D-G5-2:** Question kind = `deterministic` iff the KC has a non-blank `invariant` (checkable), else `open` (LLM-graded, resolve-outside-txn via the G2 seam). Degrade-to-UNCERTAIN: a degraded open question → `verification_status=uncertain`, correct=false, score=0 (NO second enum). A confident grade carries the KC's REAL resolved trust status.
- **D-G5-3:** ids `meq-<kcId>`; aggregate score = mean of per-question scores; empty exam → 200 empty. mock-exam attempts are graded but NOT fed into attempts/kc_mastery (scoped to assemble→grade→return; §2.2 reply is score/kc_results/narrative only) — flagged as a possible follow-up.
- **D-G5-4 (PM-caught B6 bug — fix-claim):** `MockExamsTable` has a `userId` FK but the builder did NOT add it to the me/delete cascade (its KDoc claimed it did) — a real GDPR-erasure FK-throw (the B6 risk class). The green suite missed it (MeDeleteCascadeTest didn't seed a mock_exams row; the adversarial review didn't check the cascade). **Fix (TDD, RED-proven):** seeded a mock_exams row in MeDeleteCascadeTest (now fails without the fix), added `MockExamsTable.deleteWhere` to the cascade (child-first, before Users), and added the table to MeRoutesTest's schema. Lesson folded forward: future groups that add a user-FK table MUST wire the me/delete cascade + seed the cascade test.

### G6 — P3-GEN + P3-HONESTY (DONE, SHIP, suite 1231/0)

Files: DrillGenerator.kt (modelTag wiring), DrillGeneratorTest.kt (modelTag class-killer), P3GenServeNoRelayTest.kt (NEW), P3HonestyGraderSpotCheckTest.kt (NEW). One main file changed.

Decisions:
- **D-G6-1 (P3-GEN):** `Problem.modelTag` now set from the GENERATOR LLM's returned model id (the WRITER per D1, DrillGenerator's first `complete()`), NOT the self-solve/critic calls, NEVER null, NEVER the `criticUsed="relay/claude"` reply-envelope literal at TutorRoutes:1546 (that field is the reply envelope, not the Problem — left untouched per data-model-lock:485).
- **D-G6-2 (serve zero-relay):** P3GenServeNoRelayTest proves it with a TRIPWIRE — after generate, both LLM factories are swapped for throw-on-construct + call-counters; serve GET returns 200 with the persisted modelTag and ZERO new relay calls. Tests inject a FAKE relay (no network, no-paid); the live relay test (E3RealRelayProofTest) stays the only relay-guarded/skipped one.
- **D-G6-3 (P3-HONESTY scoped honestly):** the spot-check checks the grader's LLM-INDEPENDENT leg (GradeScoring) against keys SYSTEM-DERIVED from a real on-disk PDF (operands extracted verbatim from `ALO/labs/alo_sem1.pdf` → p-norms computed by forced math; never via Alex). RED-proven by mutating `answerMatches→true`. 5 deterministic items checked (not skipped); irrational L2 + open proof items excluded. Trust badge UNCHANGED (matches-your-lecture); gold-set GATE stays §7-deferred.
- **⚠️ FINDING-G6 (plan inaccuracy — flag for Alex):** master-plan:205 claims prof-authored SOLVED-exercise/exam-solution PDFs exist on disk (the PA/local_extras + ALO/hw + ALO/labs paths). **They do NOT** — independently confirmed: those are course-evaluation policy, a grade-results table, and UNSOLVED problem statements. NO worked answer keys exist offline. P3-HONESTY did the strongest offline-checkable thing (system-derived deterministic keys). The §7 full gold-set honesty GATE will need real keys sourced when it is built; the plan text at :205 should be corrected.
