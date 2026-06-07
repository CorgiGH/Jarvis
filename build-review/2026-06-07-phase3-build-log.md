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
| G6 | P3-GEN generator + P3-HONESTY spot-check | DONE (suite 1231✓, review SHIP) | d00d43a |
| G7 | P3-MISC-SERVE + P3-LADDER-SERVE + P3-GHOST-FIELDS (+ far-transfer wire fix) | DONE (suite ✓, review SHIP after 1 fix) | (this commit) |

**PHASE 3 (Area C — Teaching Engine) — all 7 groups built + committed, then COUNCIL-HARDENED.**

## Council round (2026-06-07/08) — adversarial decision-council found 6 flaws; ALL fixed + re-verified

An owner-requested adversarial council (6 lenses + chair, grounded in `git diff a4980af..6bad609` + the locks) found the per-group "SHIP" reviews missed cross-group escapes. All 6 closed, TDD, full suite green (1278 tests, 0 fail, 2 env-skip), no trust carve-out invented, locks amended in-place:

| # | Flaw | Sev | Fix |
|---|------|-----|-----|
| P0-1 | GDPR `/me/delete` FK-throws — `kc_mastery` + `user_provider_config` user-FK tables missing from the cascade (the G5 "B6 fix" only closed mock_exams = the fix-claim sibling trap) | HIGH | added both child-first; extracted `ME_DELETE_CASCADE_TABLES` SSOT; **class-killer reflection invariant** asserts every `ALL_TABLES` user-FK table is covered |
| P0-2 | served misconception/ladder bypassed the P2-RULE8 / §Q:518 CitationGuard chokepoint → refutation shipped un-cited | HIGH | `MisconceptionPayload.fromCited` routes through `CitationGuard.attach` carrying `SourceRef`, fail-LOUD on none; §O amended (additive `source`) |
| P1-3 | Area-C goal INERT (placement stuck at intro, ScaffoldPlanner zero callers, no KC authored teaching fields) yet marked COMPLETE | HIGH | placement seeds practice off a strong answer; ScaffoldPlanner wired into the queue serve path; teaching fields authored on pa-kc-001..004 (system-derived); mechanical read-site gate test |
| P1-4 | canonical locks left un-amended while code diverged (misconception_payload, DTO shapes) | MED | §O/§2.2/spine reconciled; new §R freezes the DTO shapes; master-plan:205/269 false-PDF claim corrected; durable "deviation amends the lock same-commit" rule |
| P2-5 | mock-exam graded free-text vs `kc.invariant` (a math seed, not an answer key) → every deterministic Q scored 0 | MED | all questions now OPEN/degrade-to-UNCERTAIN; future fix (a real `canonical_answer` field) recorded in §6 Deferred |
| P2-6 | honesty test `assumeTrue`-skipped on clean CI (proved nothing) + weak assertions | MED | checked-in fixture (runs in CI); wrong-answer round-trip through the full grader, RED-proven non-vacuous |

10 council "watch" items: the actionable ones folded into the fixes (mixed-quarantine grade test, resolveStatus fail-closed canary, total_due faithfulness filter); the rest are Phase-5/6 forward-contract notes. Detailed per-fix decisions below (P1-3, P1-4/P2-6 sections) + in the F1/F2/F4 workflow transcripts.

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

### G7 — serve wiring (DONE, SHIP after 1 fix, suite ✓)

Files: GradeTeachingPayload.kt (NEW — MisconceptionPayload + LadderRung + NextPhaseAction enum), FeedbackLadderBuilder.kt (NEW — pure L0-L4 renderer), NextPhaseResolver.kt (NEW — advance/hold/remediate), DrillGenerator.kt (far-transfer branch), TutorRoutes.kt (7 additive served fields + far-transfer route wiring), DrillGeneratorTest/GradeTeachingPayloadTest/DrillGradeServeWiringTest/GenerateDrillsRouteTest.

7 served fields on /drill/grade, each populated from stored content + named Phase-5 consumer (H16 no-ghost, RED-proven by neutralizing population): misconception_payload (refutation+figure_spec → MisconceptionRibbon), ladder_rungs (L0-L4 → FeedbackLadder), self_explanation_prompt (→ DrillStack rung), verification_status (honest B8 → TrustBadge), phase, next_phase_action, cross_checked.

Decisions:
- **D-G7-1 (field-name deviation, justified):** lock §O froze the inline misconception field as `misconception` (object), but the LIVE frontend already binds `misconception: String?` (grader code) at DrillStack.tsx:248. Serving an object there would break the live frontend. Resolution: keep `misconception:String?`, add the structured payload as **`misconception_payload`** (additive). Lock canonical on SHAPE; the live wire field is a real additive constraint — preserves both.
- **D-G7-2 (NextPhaseAction created):** the enum didn't exist (only a comment ref) — created it (advance/hold/remediate) per §B, resolved by NextPhaseResolver from phase-before/after + deterministic-correct.
- **D-G7-3 (ladder source):** no L0-L4 text is STORED (schema stores teaching FIELDS, ladder is RENDERED) → FeedbackLadderBuilder composes rungs from stored content (L0 nudge / L1 self_explanation_prompt / L2 misconception.trigger / L3 refutation / L4 grader feedback); rungs with absent backing are omitted (no ghost).
- **D-G7-4 (far_transfer WIRED, not demoted — PM-caught half-ghost fix):** the G7 build added `DrillGenerator.farTransfer` but left it with ZERO production callers (a silent half-ghost — the Slice-1 anti-pattern; review caught it, blocked SHIP). **Fix:** the /generate-drills route now AUTO-fires farTransfer for any resolved KC whose far_transfer_stem is non-blank, persists the far-transfer Bundle (shape="far-transfer", modelTag=relay id) into problems_json, served by the prep route. Route-level test (RED-proven) asserts a persisted+served far-transfer Problem; a no-stem KC persists none (no ghost). Auto-fires off the authored field (no new request flag). Then review = SHIP.

## P1-3 — adversarial-council fix: "the Area-C teaching loop is wired but INERT" (4 sub-fixes, TDD)

Council found 6 flaws; P1-3 = the teaching loop is present but does nothing end-to-end. Fixed all four sub-parts, each RED-then-GREEN. Suite green for the targeted classes; final verify = full suite.

- **D-P1-3a (placement no-op closed):** `POST /placement/submit` routed its one-shot answer score through `PhaseModel.transition(observations=1)`, which is observation-gated and ALWAYS returned `intro` (obs < `PRACTICE_OBS_MIN`=2) — so a strong placement answer never seeded practice+; the score was a no-op. **Fix:** placement now derives entry_phase DIRECTLY from answer strength via the RECORDED mapping `placementEntryPhase(score)` — **correct (non-blank) ⇒ `practice`, blank/incorrect ⇒ `intro`** (a one-shot floor seed is NOT an FSRS observation; `mastered`/`retrieval` still require the audited MIN_OBSERVATIONS=3 history, never claimable off one item). The monotonic no-regress `maxPhase(existing, candidate)` is KEPT (monotonicity class-killer test still green). Fixed the false MIN_OBSERVATIONS/PRACTICE_OBS_MIN comment at the old lines 311-312. RED-proven: new test asserts a strong answer ⇒ `practice` (was `intro`), a blank answer ⇒ `intro`. File: `SessionPlacementExamRoutes.kt` (+ `SessionPlacementExamRoutesTest`). No lock amended — the score→phase mapping was never frozen; only the route shape + PhaseModel monotonicity are locked (both untouched).
- **D-P1-3b (ScaffoldPlanner ghost wired on the serve path):** `ScaffoldPlanner.planFor` had ZERO production callers (only its def + a KDoc) — a real ghost (the Slice-1 anti-pattern). **Fix:** wired it into `LockedNextKcSelector` (the ONE serve-path resolver of `mode`/`worked_example_first`, called by `GET /queue/today`). `servePhase()` = the FIRST phase of `planFor(kc, mastery)` (= `phase_plan ∩ not-yet-mastered`, trimmed to the `entry_phase` floor) AT OR ABOVE the learner's current resolved `KcCandidate.phase`. So an authored `phase_plan` that skips `intro` and a placement-seeded floor BOTH actually shape what is served; the learner is never regressed below graded progression; `mode`/`worked_example_first` stay the single source of truth (no second resolver). RED-proven at TWO layers: unit (`NextKcSelectorTest` — `phase_plan=[practice,retrieval]` cold ⇒ served `practice`/`drill`, not `intro`/`worked`) AND route (`QueueMasteryCalibrationRoutesTest` — production `queue/today` caller consumes planFor). Two pre-existing selector tests updated to the composed-phase contract (current-progression respected). File: `NextKcSelector.kt`.
- **D-P1-3c (teaching content authored — loop precondition met):** NO KC authored the teaching fields (grep content/ = none; master-plan:209 made this a loop precondition). **Fix:** authored all four fields (`phase_plan` + `worked_example_first` + `far_transfer_stem` + `self_explanation_prompt`) on the four faithful PA lecture-01 KCs `pa-kc-001..004`, each SYSTEM-DERIVED from that KC's own committed source quotes (no oracle inversion, no invented subject fact — every prompt restates/uses ONLY what the KC's `source` already states; RO learner-facing per the language-split rule). `validateContent` GREEN (0 errors; source spans untouched — fields appended after the source block). RED-proven: `ContentSchemaDefaultsTest` updated (mirrors the existing `authoredStrictKcs` precedent) + new `AuthoredTeachingKcsTest` pins the authored shape, phase_plan parse/order, planFor non-empty, and verbatim_source still passing.
- **D-P1-3d (mechanical read-site gate — anti-ghost class-killer):** new `Phase3TeachingFieldReadSiteGateTest` walks `src/main/kotlin` (comment/KDoc lines stripped first, so a KDoc mention CANNOT satisfy it) and FAILS if any gated symbol (`ScaffoldPlanner.planFor`, `.phase_plan`, `.far_transfer_stem`, `.self_explanation_prompt`) has no real code read-site OUTSIDE its home/definition file. RED-PROVEN: temporarily stubbed out the planFor call → gate went RED → restored. Plus a non-vacuous self-check (a bogus symbol is reported missing). This would have caught the original ScaffoldPlanner ghost and re-ghosting any teaching field.

## P1-4 + P2-6 — adversarial-council fix: locks not reconciled to disk + a weak honesty test (doc + test only, NO production-behavior change)

Council flaws P1-4 (canonical locks silently contradict the shipped Phase-3 code) and P2-6 (the honesty spot-check skipped on clean CI, proving nothing while counting green). Doc edits + one test rewrite + one checked-in fixture. No production Kotlin/TS behavior changed.

- **P1-4(a) — locks reconciled to code reality (the DURABLE RULE now codified):**
  - **Misconception serve field reconciled (D-G7-1):** `interface-signatures-lock.md` §O (the served-fields list + the §N table row + the consumers line + a header one-liner), `master-impl-plan-v2.md` §2.2 (the `/drill/grade` row + `TASK P3-MISC-SERVE`), and `tutor-ui-design-spine.md` §0f all froze the inline field as `misconception:{…}` (object). **CODE IS REALITY:** the live reply DTO carries `misconception_payload: MisconceptionPayload?` (object) PLUS a distinct coexisting scalar `misconception: String?` (the grader CODE bound by `DrillStack.tsx:248`). Renaming the scalar would break the live frontend, so the object rides under `misconception_payload`. All four docs amended to name `misconception_payload` as the wire field + `MisconceptionRibbon` as the Phase-5 read-site.
  - **Autonomously-frozen shapes PROMOTED into the lock (new §R):** `ApiMasteryDelta{kc_id, attempts, correct, ewma_after, phase}` (session/close `mastery_deltas[]` element) + `ApiExamDatesReply{exam_dates:[{subject, start_at}]}` (D-G4-7/D-G4-6) + the `worked_example_first` server formula `kc.worked_example_first || servePhase==intro` (D-G1-3, refined by P1-3b) — all were frozen in the build-log journal but never in a canonical lock. §R freezes them verbatim from the shipped code and cross-links the build-log decision ids.
  - **False on-disk-PDF claim CORRECTED (FINDING-G6):** `master-impl-plan-v2.md:205` + `:269` claimed prof-authored SOLVED-exercise / exam-solution PDFs sit at named PA/ALO paths. They do NOT — those are eval-policy / grade-table / UNSOLVED statement files. Survey: the ONLY answer keys on disk at all are ~2 exams' worth of `barem`/`rezolvare` files buried in `_fii/_gdrive/` (e.g. `ALO/.../Rezolvare TEMA {1,3} Notion.pdf`, POO/SO exam `barem*.pdf`), not the curated paths and not vetted for verification. Both lines corrected to the truth + pointer to the SYSTEM-DERIVED P3-HONESTY approach.
  - **DURABLE RULE added:** a header one-liner + a fuller §R statement — any forced deviation from a lock amends the lock IN THE SAME COMMIT; a deviation that would weaken a Phase-2 trust guarantee STOPS and escalates to the owner (no silent trust carve-out). Build-logs are a journal, not a lock.
- **P2-6 — `P3HonestyGraderSpotCheckTest` hardened to run + assert on clean CI (TDD):**
  - **RED captured:** `git ls-files tmp-secondbrain-scrape/` = 0 (the source dir is UNTRACKED). With the PDF hidden (clean-CI simulation) the OLD test `assumeTrue(pdf.isFile)`-SKIPPED → `BUILD SUCCESSFUL`, asserting nothing while counting green.
  - **Fix 1 (CI-runnable):** checked in `src/test/resources/fixtures/alo_sem1_operands.txt` — the verbatim `pdftotext` extraction of the real PDF's two clean integer operand vectors `(-2,2,1)` / `(2,-1,3,4)`. The test now reads operands from the fixture (always on the classpath) and RUNS its assertions unconditionally; the live PDF, when present, is used ONLY as a drift check (fixture-vs-source-of-record), never as a skip-gate. Verified: with the PDF absent the test still runs both methods (markers `(live PDF absent — fixture only)` + the round-trip print fire).
  - **Fix 2 (wrong-answer round-trip through the FULL grader):** new test drives the REAL `DrillGrader.grade(...)` against an injected CREDULOUS fake LLM (always says correct=true / all-pass rubric — no network, no paid relay) and applies the EXACT serve-path deterministic composition (`answerMatch = answerMatches(key, attempt); correct = answerMatch ?: rubricCorrect`, TutorRoutes.kt:2122-2125). Asserts: the system-derived correct answer (`5` = L1 of (-2,2,1)) grades CORRECT, and plausible-but-wrong answers (`1` = forgot the |·|, plus `4/6/signed/word`) grade INCORRECT against the PDF-derived key EVEN THOUGH the LLM blessed them — the deterministic key VETOES the credulous grader. **RED-proven non-vacuous:** swapping `correct = answerMatch` → `correct = rubricCorrect` (trust the credulous LLM) made the test FAIL; restored to GREEN.
