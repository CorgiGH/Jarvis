# Jarvis Tutor — Master Implementation Plan: Pre-Build ISSUE LEDGER

**Plan audited:** `docs/superpowers/plans/2026-06-02-master-impl-plan.md` (HEAD `b970585`, branch `door-compare`).
**Audit date:** 2026-06-02. **Working branch:** `main` @ `a2dd257`, 10 ahead of `origin/main`.
**Method:** 9 slice-auditors + completeness critic + synthesis (workflow `wf_0a9ae177-999`, 11 agents, ~1.27M tokens). Every cited `file:line` re-verified against the live repo. 99 raw → 43 after dedup/false-positive drop. The 4 prior-council issues (K1–K4) excluded except where new depth was found.

---

## 1. Summary

| Severity | Count | |
|---|---|---|
| **BLOCKER** | 8 | Plan is internally false or unbuildable as written; build would stall or corrupt the live DB. |
| **HIGH** | 16 | Real contract gap / sequencing trap that forces a mid-build refactor if not folded in now. |
| **MEDIUM** | 14 | Under-specified seam; builder will guess and likely guess wrong. |
| **LOW** | 5 | Dead fields / cosmetic / one-liners. |
| **Total new** | **43** | |

**Verdict:** Strategy (freeze contracts → phase-by-phase) is sound and ~80% of the contracts are right. But NOT freeze-ready. Two fatal-to-premise classes: (a) the migration-safety mechanism is **factually wrong** — Exposed `.default()` doesn't emit a DDL DEFAULT, so the headline "every legacy card stays ACTIVE" backfill silently NULLs all 871 rows and `WHERE status='ACTIVE'` drops the whole corpus (B1); (b) the central "atomic B1 grade" + "upsert FsrsCard" contracts are **unbuildable against existing code** (`record()` self-transacts; `FsrsCardRepo` has no upsert). Fold the 8 BLOCKERs + 16 HIGHs (most are doc-only edits) + get 3 Alex decisions → freeze-ready. None of the 3 decisions block backend Phases 1–3.

---

## 2. Issue Ledger (severity-ordered)

### BLOCKERs

| # | Title | Refactor? | Problem (verified) | Evidence | Fix |
|---|---|---|---|---|---|
| **B1** | Exposed `.default()` is client-side, NOT a DDL DEFAULT — ALTER on the 871-row table leaves `status`/`phase` NULL, `WHERE status='ACTIVE'` drops the whole corpus | Yes | Plan §2.1 says "DB-level DEFAULT … legacy 871 → status='ACTIVE'." Exposed 0.55 `.default()` only fills via INSERT DSL; `createMissingTablesAndColumns` emits `ALTER TABLE ADD COLUMN … VARCHAR(16)` with no SQL DEFAULT → SQLite fills existing rows NULL → every legacy card vanishes from due()/forecast()/queue. | `build.gradle.kts:48`; `.default()` only `UserPreferences.kt:15`+`Users.kt:32`; `FsrsCards.kt:28` | After `createMissingTablesAndColumns`, run explicit `UPDATE … SET status='ACTIVE' WHERE status IS NULL` (+ phase) in the same txn. Migration test: pre-migration row survives due()/queue. Correct the plan's "DB-level DEFAULT" wording. |
| **B2** | `FsrsCardRepo` has no `upsert` / no `(userId,kc_id)` finder — "upsert FsrsCard" reduces to insert → duplicate card every confident grade | Yes | Repo exposes only insert/findDueForUser/findById. Grade loop runs per submit → same KC re-cards every attempt → count explosion. Plan never says the card's front/back text. | `FsrsCards.kt:45-93`; loop `TutorRoutes.kt:2024-2030` | Add `upsertRubricCriterion(userId,kcId,front,back,state)` (SELECT then UPDATE-or-INSERT, inside caller txn). Index `(userId,sourceType,kcId)`. Specify front/back (stem_template/name → front; canonical answer → back). |
| **B3** | B1 "ONE txn" impossible — `KcMasteryRepo.record()` opens its own `transaction(db){}` and is called in a per-KC loop | Yes | A 2-KC problem already runs 2 separate txns; can't enclose them + attempt + card atomically without splitting record(). Builder gets "mastery moved, card not written" on mid-seq failure. | `KcMastery.kt:60-83`; loop `TutorRoutes.kt:2024-2030` | Split into txn-less `recordIn(tx,…)` + thin `record()` wrapper. B1 opens ONE `transaction{}` → loops recordIn + attempts + card upsert. Phase-1 task. |
| **B4** | CHANGE 3 fields absent from `KnowledgeConcept` class AND all 6 YAML KCs — gate reads `kc.verification_status` → won't compile; content-ghost concrete | Yes | None of the 8 new fields exist today. Class can't compile until extended; kaml needs a default for non-nullable `verification_status` or the 6 YAMLs fail to deserialize. | `ContentSchema.kt:38-58`/`:81-90`; grep content/ = 0 | Add fields nullable/defaulted before Phase 2; backfill 6 YAMLs `verification_status: unverified`; ContentValidatorTest for strict + enum. |
| **B5** | `2026-06-02-stage0-exit-gate.md` does not exist — Phase 0 (hard dep of Phase 1) has no task list / no fix for the 9 failing tests / no push step | Yes | Plan L127 names it; file not found. Phase 0 gates the live-DB migration. | `ls docs/superpowers/plans/`; plan L127 | Write it: fix the 9 react-pdf/DOMMatrix failures; `git push origin main` (10 unpushed) → CI; `./gradlew check` green; db-backup card-count ≥800. Gate Phase 1 on all four. |
| **B6** | New user-FK tables absent from GDPR me/delete cascade + `PRAGMA foreign_keys=ON` → me/delete **throws FK-violation and aborts** → Art-17 erasure crashes | No | me/delete hard-codes 13 tables then Users, child-first. The 3 new user-FK tables (session_summaries, attempts, report_wrong) aren't in it. FK enforcement ON → delete crashes, not just leftover PII. | me/delete `TutorRoutes.kt:2156-2183`; `TutorDb.kt:15` PRAGMA on | Phase-1: append the 3 (+ exam_dates, H14) child-first before Users. Test: user with rows in each new table → me/delete → zero leftover + no FK exception. |
| **B7** | Frontend AppShell/ThemeProvider/door exist only on `door-compare`, not `main` — every frontend BLOCKER is contingent on the Phase-4-T5 merge the plan treats as one line | Yes | `git ls-files`(main) finds none; `App.tsx:235` still inline header; `main.tsx:15` wraps only BrowserRouter. Phase 5/6 mount assumptions depend on a clean merge that doesn't exist on the build branch. | `git ls-tree door-compare` vs `git ls-files`; `App.tsx:235`; `main.tsx:15` | Make Phase-4 T5 a hard gate before any Phase-5/6 task: commit unstaged main.tsx + untracked door/; merge door-compare; wrap ThemeProvider; replace inline header with AppShell. Explicit prerequisite row. |
| **B8** | KC `verification_status` has **no runtime persistence store** — YAML-only field, but report-wrong/admin-verify/the gate mutate it per-event (new depth on K4) | Yes | KCs have no DB row; CHANGE 6 audit is per-claim history, not the single resolved status. Both implementable paths broken: write back to git YAML on every action (races git, stale reads) or discover mid-Phase-2 a status table is needed. | plan §2.1 L59/§2.2 L93/§2.4; `ContentSchema.kt` (YAML, no table) | Add `kc_verification_status` table (kc_id PK, status default 'unverified', last_audit_run_id, updated_at) as the sole runtime source of truth. YAML field = authored seed only, never written back at runtime. Gate + report-wrong + reads resolve from this table. Register in installTutorContext. |

### HIGHs (condensed — full detail per row)

| # | Title | Fix |
|---|---|---|
| **H1** | `confidence` wire-name overloaded (request student-confidence vs existing reply grader-confidence `HIGH\|LOW` at `TutorRoutes.kt:2551`) | Rename request field → `student_confidence`; lock both in §2.2 + attempts column. |
| **H2** | drill/grade EXTEND re-introduces a client-trusted `kc_id` that E2 removed (`TutorRoutes.kt:2536-2539`) | Drop `kc_id` from request; gate/attempts/FSRS iterate server-resolved `Problem.kcIds`. |
| **H3** | "blank model id ⇒ collapsed ⇒ UNCERTAIN" never fires — both clients substitute a non-blank id (`RelayLlm.kt:108`, `OpenRouterChatLlm.kt:198`) | Collapse = same configured **family** (RELAY\|OPENROUTER\|NONLLM enum per leg), not a model-string compare. |
| **H4** | 429/relay failure **throws** (not mapped to UNCERTAIN); inside B1 it throws mid-txn → fail-UNSAFE | try/catch every LLM leg → throw=collapse→UNCERTAIN / B1 don't-record. Run grade+cross-check OUTSIDE the write txn; open the atomic txn only after both resolve. Never call an LLM in an open txn. |
| **H5** | Two-family verifier hard-needs BOTH RelayLlm(claude-max, throws if env unset) AND OpenRouter live → when the relay PC sleeps the whole corpus pins UNCERTAIN, queue empties | Make the audit an offline owner/Gradle batch off the request path; FAIL LOUD when a family is unprovisioned (distinguish "disagreed" from "never ran"); relay preflight; queue uses last-persisted status (B8). |
| **H6** | `verifyContent` Gradle task risks hanging off `check` (like validateContent) → CI offline → poisons corpus UNCERTAIN every run | State explicitly: `verifyContent` = owner/manual offline batch, NOT on the `check` graph; aborts if a family env is missing. |
| **H7** | `LiveSourceLocator` offset coordinate mismatch — fuzzy matcher folds whitespace but spans must index RAW source; `SourceOfRecord.slice` uses raw offsets | Build folded-index→raw-index map; locate folded, translate range to raw for the stored span. Test with pa-kc-005's `\n  ` quote. |
| **H8** | Definitional-KC-with-no-gold-span is the **universal** case — 0 strict, 0 spans, 0 invariant/grader_rules in the whole corpus → every KC UNCERTAIN, nothing FAITHFUL (§5's "005/006 have a span" is FALSE) | Correct §5: 005/006 must be **authored** (invariant+grader_rules+vision-confirmed span) from the real PDF in Phase 2 before any FAITHFUL KC. Phase-2 acceptance: ≥1 KC reaches FAITHFUL end-to-end. |
| **H9** | `grader_rules` SymPy-only — unrunnable for PS/SO-RC/POO (proof/trace/structural) | Domain-scope `grader_rules`: PA=SymPy; others=NONE until Stage-2+ → NonLlmLeg NONE → UNCERTAIN floor (explicit). HUMAN_GOLD never routes through Alex. |
| **H10** | `curate-tutor` SKILL.md emits no verification_status / claim_ids / PENDING — "reconcile curate-tutor" is unplanned | Concrete Stage 9: after validateContent, set `verification_status: pending` + stable claim_ids + re-validate; idempotent (never regress faithful→pending). Before Phase 2. |
| **H11** | New strict rule (invariant+grader_rules) collides with existing strict (span+vision-provenance) → double-error on any strict KC | Split `grading_tier: algebraic\|textual` OR document strict = BOTH + update curate-tutor. Pick one in Phase-1. |
| **H12** | mock-exam/placement/lab endpoints have **no wire shapes** (prose only) — breaks "freeze contracts once" | Add concrete req/resp JSON for all 7 before build (job-id, status enum, Retry-After for 202). |
| **H13** | mock-exam 200-vs-202 undefined — Ktor never used `Accepted`, no job table, no poll route | Decide: (A) `mock_exam_jobs` table + poll + 202, or (B) sync-only 200. Document. **(Alex decision — §3)** |
| **H14** | Day-Of needs an `exam_dates` contract §2.1/§2.2 don't provide (no table, no endpoint) | CHANGE 9: `exam_dates(id,user_id,subject,start_at,created_at)` + `GET/PUT /me/exam-dates` + onboarding/Settings picker; add to me/delete (B6). |
| **H15** | TrustBadge/MisconceptionRibbon/FeedbackLadder have no backend data — `verify/{kcId}/status` absent; grade reply missing the 5 promised fields; FsrsCardView has no kc_id | Phase-3 grade task adds the 5 reply fields + ships `verify/{kcId}/status` (B8 table) + misconception-resolve. Badge degrades to "unverified" when kc_id null. |
| **H16** | DrillStack `StackPhase` extension named as a dep but has no task; teaching KC fields (phase_plan/far_transfer/self_explanation/stem_template/worked_example_first) stored but **no consumer** in DrillGenerator/Grader | Phase-5 T0: extend StackPhase (currentRung/prediction/confidence) before any consumer mounts. Pair each KC field with a named READ site or mark it deferred — not "frozen contract." |

### MEDIUMs (titles + one-line fix)

- **M-DB** db-backup never asserts the 871 minimum → add `MIN_EXPECTED_CARDS=800` guard; exit-0 IS the migration gate.
- **M-IDEMP** migration runs only at VPS boot vs an empty-schema CI → CI idempotency test must seed a fixture DB with legacy rows; dry-run on a prod-DB copy.
- **M-PARTIAL** one boot-time batch adds 4 tables+5 cols; a failed ALTER aborts server start after partial mutation → try/catch + auto off-box backup + auto-rollback + runbook.
- **M-SEED** `FsrsSeedMain` has its own 3-table create + kc-less insert → list as CHANGE-1 site; assert seeded card status='ACTIVE'.
- **M-FK** me/delete child-first ordering explicit (folded into B6).
- **M-CLAIM** `claim_id` ordinal unstable (positional YAML) → content-hash claim_id + optional `ref_id` on SourceRef.
- **M-PAGE** corpus has 0 form-feeds/0 spans → page always 1 → make audit page/span nullable + `page_anchor_status (LIVE\|DEGRADED\|NONE)`; DEGRADED+offset/fuzzy pass ⇒ FAITHFUL-eligible.
- **M-GATE-PATHS** gate list omits FsrsSeedMain (un-gated 871 corpus), GapPromotion early-return, `fsrs/{id}/grade` (no status filter) → exempt legacy/kc-less cards + badge unverified; add status guard to grade route (409 if not ACTIVE); enumerate ALL reads needing `status='ACTIVE'`.
- **M-B1-CARD** B1 card upsert iterates `kcIds` (1:N) but card kc_id is scalar → lock "one card per (user,kc_id) in Problem.kcIds, each gated independently; partial-record allowed."
- **M-NEXTKC** NextKcSelector needs a transitive-closure PrereqGraph + cached cross-subject load (neither exists; ContentRepo loads per-request) → add PrereqGraph helper + memoize content in TutorContext; degrade when a subject has 0 KCs.
- **M-MISC-ENUM** DrillGrader misconception enum hardcoded to PS codes → make per-KC-derived (pass authored Misconception ids into the prompt).
- **M-MASTERY-SHAPE** `/mastery` single shape can't serve Surface 5 (delta) or 6 (calibration); `subject_name` monolingual vs bilingual KCs; `last_graded_at|null` over-promises → drop Surface 6 from consumers; session/close returns deltas; `subject_name_ro`+`_en`; decide MasterySparkline time-series-vs-band before T1 freezes the prop.
- **M-CSCHEMA** curate-tutor Stage-1 assumes a pre-extracted `.md`; a real PDF with no `pdftotext` writes empty source silently (validator WARNING) → Stage-1 gate: assert extract() non-empty + ≥1 form-feed; promote to ERROR for strict; `verifyContent` preflight checks pdftotext on PATH.
- **M-FIXTURE** `checkExamWeights` sums fixture KCs (0.0); Stage 6 has no fixture-exclusion → add `is_fixture` (or exclude weight==0.0) + update Stage 6 + mixed test.

### LOWs

- **L1** session/close `mastery_deltas` is dead input (server recomputes) → remove from request.
- **L2** verify/status uses flat span_start/end but producer nests `span:{start,end}` → match producer or state the flatten mapping (page==0⇒null).
- **L3** `FsrsForecastReply` omits `dueNow` though backend returns it (`fsrsClient.ts:26-27`) → add `dueNow:number`; fix App.tsx inline-type bypass.
- **L4** CHANGE 8 framing error: `Problem` is a serialized blob not a table; FQN is `jarvis.tutor.Problem` not `PdfProblemExtractor.Problem` → reword (no DDL; ignoreUnknownKeys covers back-compat) + decode test.
- **L5** spine's localStorage motion toggle (ADHD accommodation) has no plan task → add `MotionToggle` + `motionPreference` in ThemeProvider, or explicitly defer.

---

## 3. Fixes that need an ALEX decision (none block backend Phases 1–3)

| Decision | Issue | Default if no answer |
|---|---|---|
| **Brand/theme §2.1** (brutalist-yellow default + warm opt-in + picker scope) | gates B7 / Phase-4 T5 | Brutalist default, warm deferred (current stance). |
| **Subject Map 0b 5-equal-column R6 exception** | Phase-6 SubjectCard | SubjectCard redesigns to 4-col if rejected. |
| **Mock-exam sync (200) vs async (202+poll)** | H13 | Recommend sync-only 200 for Stage-1 (no job infra) — confirm. |

**NOT an Alex decision (no-oracle-inversion holds):** computational-KC authoring (H8), the never-translate glossary, HUMAN_GOLD spans (H9) are all system-derived from the PDF/corpus, never handed to Alex to vet.

---

## 4. What the audit confirmed SOUND (validated, not just holes)

- **Strategy right** — freeze contracts → phase-by-phase; keystone order (data model → correctness → teaching → frontend) is a correct dependency chain.
- **Decisive verdicts (§2.4) well-grounded** — SQLite single-user; flat EWMA + stored phase; FSRS-5-lite as-is (the "FSRS-4.5/6" labels ARE wrong vs the real header); deterministic NextKcSelector not Thompson; "faithful to your source" never "verified/correct."
- **Interface locks (§2.3) are the right seams** — PhaseModel/NextKcSelector/ScaffoldPlanner/VerificationStatus.transition; PFA/Thompson swap won't refactor callers (only gap = B3 record() txn boundary).
- **Verification state machine (§2.4) conceptually sound** — no FAITHFUL without non-LLM-pass AND families-agree; no auto-clear from attempts; REPORT-WRONG→PENDING. Flaws are in firing conditions (H3/H4/H5/H6), not topology.
- **3-value status enum** (ACTIVE\|QUARANTINED\|PAUSED not boolean) is right.
- **due()/forecast() status filter** is additive+correct (the bug is the NULL backfill B1 + missing filter on findById/queue M-GATE-PATHS).
- **CHANGE 8 back-compat genuinely safe** — `tutorJson` ignoreUnknownKeys+encodeDefaults (only L4's framing wrong).
- **Deferrals (§6)** correctly scoped (warm app-wide, voice, PFA/Thompson, Postgres/cohort, Lab V86, PS vision).

**Bottom line:** contracts ~80% freeze-ready. The 8 BLOCKERs are mostly *mechanical truths the plan asserts incorrectly* — fixable as doc + named-task edits without changing the architecture. Fold BLOCKERs + HIGHs + the 3 Alex decisions → freeze.
