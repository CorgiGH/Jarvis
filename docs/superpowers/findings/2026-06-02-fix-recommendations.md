# Master-Plan Fix Recommendations (council-reviewed)

**Council review of the 43-issue audit ledger** (`2026-06-02-master-plan-audit-ledger.md`), workflow `wf_32f96235-336` (6 agents).
**Counts:** AGREE (fix right as-is) + REFINE (right direction, adjusted) — **0 WRONG, 0 DISPUTED**. **None need an Alex decision** — all are concrete builder actions. (Mock-exam = SYNC, already decided; Subject-Map columns = parked to render; brand = brutalist default.)

Each line: **id · title → verdict → DO:** final action.

---

## BLOCKERS

**B1 · fsrs_cards.status backfill** → AGREE → **DO:** same boot txn after `createMissingTablesAndColumns`, run `UPDATE fsrs_cards SET status='ACTIVE' WHERE status IS NULL` (+ phase backfill); migration test that a pre-migration row survives `due()`/queue; fix §2.1 wording to "Exposed client-side default + explicit post-ALTER backfill". Gate w/ M-DB (≥800).

**B2 · FsrsCardRepo upsert + finder** → REFINE → **DO:** `(userId,kcId)` finder + txn-less `upsertRubricCriterion(tx,…)` taking the caller's txn (composes in B1/B3) + `(userId,sourceType,kcId)` index; front=stem_template/name_en, back=canonical answer. Needs kcId column first; 1:N is M-B1-CARD.

**B3 · KcMasteryRepo txn split** → AGREE → **DO:** split into txn-less `recordIn(tx,…)` + thin `record()` wrapper; B1 grade opens ONE `transaction{}` calling recordIn per KC + attempts + card upsert. Fold H4 (LLM legs resolve OUTSIDE the txn).

**B4 · CHANGE-3 schema fields** → REFINE → **DO:** add CHANGE-3 fields to `KnowledgeConcept`/`Misconception` Kotlin-defaulted (esp. `verification_status="unverified"` so kaml deserializes existing YAMLs); ContentValidatorTest over all **8** KC YAMLs (6 real + 2 fixtures).

**B5 · stage0 exit-gate doc** → REFINE → **DO:** write `2026-06-02-stage0-exit-gate.md` gating Phase 1 on: react-pdf/DOMMatrix specs green, `git push origin main`→CI green, `./gradlew check` green, off-box dump ≥800. Don't hard-code "9 failing".

**B6 · me/delete FK cascade** → REFINE → **DO:** in me/delete (`TutorRoutes.kt:2156-2183`) append child-first before Users: session_summaries, attempts, report_wrong (+ exam_dates per H14); register tables; test (rows→delete→zero leftover, no FK throw).

**B7 · Phase-4 T5 merge GATE** → AGREE → **DO:** make Phase-4 T5 a hard prerequisite before any Phase-5/6 task: merge door-compare→main, wrap router in ThemeProvider, replace `App.tsx` inline header (~235) with `<AppShell>`. List explicitly.

**B8 · kc_verification_status table** → AGREE → **DO:** add `kc_verification_status` table (kc_id PK, status default 'unverified', last_audit_run_id, updated_at) = SOLE runtime source of truth; YAML field = seed only, never written back; reads resolve from it.

---

## HIGH

**H1 · student_confidence rename** → AGREE → rename request field `student_confidence` (DEFINITELY|MAYBE|GUESS|IDK|null); keep reply `confidence: HIGH|LOW`; attempts col `student_confidence`.
**H2 · keep KCs server-resolved** → AGREE → no client `kc_id` in grade request; iterate `serverProblem.kcIds`.
**H3 · collapse = same family** → AGREE → collapse via `RELAY|OPENROUTER|NONLLM` enum per leg, not model-string; drop the blank-id wording.
**H4 · LLM-leg failure mapping** → AGREE → try/catch every leg → throw=UNCERTAIN / B1 skip-write; resolve grade+cross-check BEFORE the write txn; no LLM inside an open txn.
**H5 · verification offline** → AGREE → owner/Gradle offline batch off the request path; FAIL LOUD (never-ran vs disagreed); queue reads last-persisted B8 status.
**H6 · verifyContent NOT on check** → AGREE → owner/manual offline only; not `dependsOn(check)`; abort if family env missing.
**H7 · folded→raw span map** → AGREE → LiveSourceLocator builds folded-index→raw-index map; store RAW span; test w/ pa-kc-005 `\n  ` quote.
**H8 · pa-kc-005/006 need authoring** → AGREE → correct §5; author invariant+grader_rules+vision span in Phase 2; acceptance = ≥1 KC FAITHFUL end-to-end.
**H9 · domain-scope grader_rules** → AGREE → PA=SymPy; PS/SO-RC/POO/ALO = NONE→UNCERTAIN floor until Stage-2+; HUMAN_GOLD system-derived, never Alex.
**H10 · curate-tutor Stage 9** → AGREE → after validateContent set `verification_status: pending` + content-hash claim_ids, re-validate; idempotent.
**H11 · strict = ALL of** → REFINE → `grounding_tier:strict` = anchored span + vision-confirmed + invariant!=null + grader_rules non-empty; validator emits all errors together. (errors additive, not "double")
**H12 · 7 endpoint JSON shapes** → AGREE → add concrete req/resp+status to §2.2 for mock-exam/placement/lab before any build task.
**H13 · mock-exam SYNC 200** → AGREE → submit always `200 {score, kc_results, narrative}`; drop 202+poll; open-ended degrades to UNCERTAIN.
**H14 · exam_dates table+routes** → AGREE → CHANGE 9: `exam_dates(id,user_id,subject,start_at,created_at)` + `GET/PUT /me/exam-dates`; in B6 cascade; picker in onboarding S5 + SettingsMe.
**H15 · grade reply + FsrsCardView fields** → AGREE → extend `ApiDrillGradeReply` (verification_status/kc_quarantined/phase/next_phase_action/cross_checked) + `verify/{kcId}/status` + `kc_id?` on FsrsCardView + misconception-resolve; pair each w/ mount-site.
**H16 · StackPhase + KC-field consumers** → AGREE → Phase-5 T0: extend `StackPhase` (currentRung/prediction/studentConfidence) before consumers; name a READ site for each CHANGE-3 teaching field or mark DEFERRED (no ghosts).

---

## MEDIUM

**M-DB** → AGREE → `MIN_EXPECTED_CARDS=800` in db-backup.py; exit-0 = the Phase-0 gate.
**M-IDEMP** → AGREE → CI test: fixture DB w/ legacy rows → migrate → assert survival + ACTIVE; dry-run on prod-DB copy before deploy.
**M-PARTIAL** → AGREE → wrap migration in try/catch → log failing column + off-box dump + abort non-zero; recovery = restore-from-backup (SQLite ALTER not transactional).
**M-SEED** → AGREE → list FsrsSeedMain (line 89) as a CHANGE-1 site; seeded card status='ACTIVE'; assert in test.
**M-FK** → AGREE → folded into B6 (confirm the 3 tables + exam_dates child-first).
**M-CLAIM** → AGREE → claim_id = content-hash `{kc_id}:{kind}:{sha256_8}` + optional `ref_id` on SourceRef (reorder-stable).
**M-PAGE** → AGREE → verification_audit.page nullable + `page_anchor_status (LIVE|DEGRADED|NONE)`; DEGRADED still FAITHFUL-eligible; SourceRef.page default 0→null.
**M-GATE-PATHS** → AGREE → `status='ACTIVE'` on findDueForUser + forecast; grade route 409 if not ACTIVE; FsrsSeedMain exempt; GapPromotion badges 'unverified'; test grade-a-QUARANTINED→409.
**M-B1-CARD** → AGREE → 1:N: one card per kcId in Problem.kcIds, each gated independently; partial-record allowed; attempts records all kcIds.
**M-NEXTKC** → AGREE → `PrereqGraph` transitive-closure at load; memoize subjects in TutorContext; 0-KC subject → select() returns empty (no crash); ContentRepo singleton.
**M-MISC-ENUM** → AGREE → remove hardcoded PS misconception codes from DrillGrader prompt; pass the graded KC's authored Misconception ids at call time.
**M-MASTERY-SHAPE** → REFINE → `last_graded_at` non-null ISO (nullable only at 0 obs); add subject_name_ro/_en; Surface-6 reads /calibration; session/close returns server deltas; decide MasterySparkline series-vs-band before Phase-5 T1.
**M-CSCHEMA** → AGREE → curate-tutor Stage-1: extract()=='' is an ERROR; strict KC needs ≥1 form-feed; verifyContent preflights pdftotext on PATH.
**M-FIXTURE** → REFINE → in `checkExamWeights` filter `exam_weight>0.0` before summing (skip the is_fixture flag); mixed test; note in Stage-6.

---

## LOW

**L1** → AGREE → drop dead `mastery_deltas` from session/close request (server recomputes).
**L2** → REFINE → verify/status uses nested `span:{start,end}|null` to match SourceRef; audit DB cols stay flat.
**L3** → AGREE → add `dueNow:number` to `FsrsForecastReply`; drop the App.tsx inline cast.
**L4** → AGREE → reword CHANGE 8: FQN `jarvis.tutor.Problem`, JSON blob in `task_prep.problems_json` (no DDL), defaults handle back-compat.
**L5** → REFINE → Phase-4 T4.5: ThemeProvider `motionPreference` (localStorage) + `MotionToggle` in AppShell + switch motion-helpers to it. Don't defer (spine locks it; 6+ animated components depend).
