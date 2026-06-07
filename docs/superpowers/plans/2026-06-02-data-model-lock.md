# Phase 1 — Data Model (keystone): DETAILED TDD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (or `superpowers:executing-plans`) to implement this plan task-by-task. Every task is strict TDD: (a) write a FAILING test first, (b) minimal impl, (c) green, (d) refactor. Steps use checkbox (`- [ ]`) syntax. Do NOT batch tasks; each task ends green before the next starts.

**Status:** build-time detail plan for Phase 1 of the program plan. NO code in this doc — it sequences and names tests + impls.
**Date authored:** 2026-06-03 · **Repo HEAD at authoring:** `main @ b3656d2`.
**RE-FREEZE 2026-06-05 (RF1/RF3, APPROVED):** Task-12 `kc_verification_status` column set re-frozen — full 6-col enumeration (drift fix: doc froze 4, live = 6) + `source_span_hash` added + `content_hash` v2 formula pinned + audit-column allowlist (RF1). Task-12 `report_wrong` gains `resolved_by`/`resolved_at` (RF3). Memo: `build-review/2026-06-05-trustnet-refreeze-decisions.md`. Shapes cross-ref `interface-signatures-lock.md` §I.1/§I2. (No live re-key here — separate human-gated checkpoint.)
**Parent / scope source:** `docs/superpowers/plans/2026-06-02-master-impl-plan-v2.md` §1, §2.1 (CHANGE 1–10), §2.3, §2.4, §3 (Phase-1 row), §4 Area A. That doc defines WHAT Phase 1 ships; this doc defines HOW, test-first, against real `file:line`.
**Canonical-on-conflict:** `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` (frozen Kotlin signatures: PhaseModel §A, QueueItem §C, recordIn §G, upsertRubricCriterion §H, VerificationStatus §I, VerificationGate §I2, etc.). **Where the master plan's prose disagrees with the signatures-lock or with real code, the signatures-lock + real code WIN.** Conflicts found are listed in "§0. Conflicts resolved" below.
**Card-cull ID source:** `docs/superpowers/findings/2026-06-03-card-cull-audit.md` (a SEPARATE grounded audit; **as of authoring this file does NOT yet exist — it is a hard input to Task 2 and MUST be produced before Task 2 runs**). The cull DELETE id-list comes from that audit, not from this plan and not from the stale "16 junk + 6 dupes" prose.

---

## 0. Conflicts resolved (master plan vs. real code / signatures-lock — real code + lock WIN)

Verified against the live source tree and the live DB (`~/.jarvis/tutor.db`, 871 cards) at authoring time:

1. **Column name `source`, NOT `source_type`.** Master plan §2.1 backfill prose writes `... WHERE source_type='RUBRIC_CRITERION'` and the CHANGE-1 dedup-key prose says `(userId, sourceType, kcId)`. **Real DB column = `source`** (`FsrsCards.kt:31` declares `val sourceType = varchar("source", 24)` — the Kotlin *property* is `sourceType`, the *DB column* is `source`; live `PRAGMA table_info(fsrs_cards)` confirms column ordinal 2 = `source VARCHAR(24)`). **Resolution:** every SQL in this plan uses the real column `source`; every Kotlin `Op`/index expression uses the property `FsrsCardsTable.sourceType` (which maps to column `source`). The Exposed index named `(userId, source, kcId)` in §H is built from `FsrsCardsTable.userId, FsrsCardsTable.sourceType, <new kcId col>`.

2. **The "871 → 849, dedupe 6 exact dupes, cull 16 junk" figures are STALE.** Live DB facts at authoring:
   - **871 total cards; ALL 871 have `source='MANUAL'`.** ZERO `RUBRIC_CRITERION` and ZERO `GAP_PROMOTION` rows exist.
   - **ZERO exact `(front,back)` duplicate groups exist** (`SELECT COUNT(*) FROM (SELECT front,back,COUNT(*) c FROM fsrs_cards GROUP BY front,back HAVING c>1)` = 0).
   **Resolution:** the cull DELETE id-list is the SEPARATE grounded audit (`findings/2026-06-03-card-cull-audit.md`), NOT the stale "16+6". Frame the count invariant as a parameter, not the literal 849 (Task 2 + §EXIT GATE). The "849" figure is an **ESTIMATE only**, never asserted. **KEEP-MOST.**

3. **CHANGE-1 `kc_id` backfill matches 0 rows TODAY.** Its `WHERE source='RUBRIC_CRITERION'` predicate matches 0 live rows (there are none). **Still author it** — it is additive + future-proof (RUBRIC_CRITERION cards arrive in Phase 3 via `upsertRubricCriterion`). State this in the task; the test seeds a RUBRIC_CRITERION fixture row to exercise the UPDATE, then asserts 0 production rows are touched on the real corpus shape.

4. **CHANGE-2 `kc_mastery` backfill matches 0 rows TODAY.** The live DB has **no `kc_mastery` table at all** (it is created at server boot by `TutorRoutes.kt:2622`, but the live DB was seeded by `FsrsSeedMain` which only creates `Users/Sessions/FsrsCards` — `FsrsSeedMain.kt:89`). So the phase backfill UPDATE matches 0 rows on first migration. **Still author it** (additive; rows arrive once grading runs). The idempotency CI uses a fixture DB with seeded `kc_mastery` rows to exercise the replay.

5. **`Problem` FQN is `jarvis.tutor.Problem` — there is exactly ONE `Problem` class.** `grep "(data class|class) Problem"` over all `.kt` returns a single hit: `PdfProblemExtractor.kt:18`, a TOP-LEVEL class in package `jarvis.tutor`. So `jarvis.tutor.Problem` and "the class defined in PdfProblemExtractor.kt" are the SAME type; there is **no** nested `PdfProblemExtractor.Problem`. The master plan's "FQN `jarvis.tutor.Problem`, NOT `PdfProblemExtractor.Problem`" is a warning against a nested-class misread, not two competing classes. **Resolution:** widen `jarvis.tutor.Problem` (file `PdfProblemExtractor.kt:18`). It is serialized into `task_prep.problems_json` (`TaskPrep.kt:18` column `problems_json`; written at `TutorRoutes.kt:1375/1423/1502`, read at `:1937`). Serializer = `jarvis.tutor.TutorTypes.tutorJson` (`TutorTypes.kt:75`) which already sets `ignoreUnknownKeys = true` + `encodeDefaults = true` — back-compat decode is covered, NO DDL.

6. **`VerificationStatus.transition` holder name.** Signatures-lock §I declares the pure transition on an `object VerificationStatus_` (trailing underscore) alongside the `enum class VerificationStatus`. The master plan writes it as `VerificationStatus.transition(...)`. **Resolution (lock wins):** the enum is `VerificationStatus { unverified, pending, faithful, uncertain, failed }`; the pure fn lives on the companion-style holder per §I. Name the holder per the lock; if the builder prefers a companion object on the enum, that is an internal call-site choice as long as the signature `transition(from: VerificationStatus, outcome: AuditOutcome): VerificationStatus` and the 5 literals are exact. The enum has **5** literals (`failed` is runtime-only; the YAML/`ContentValidator` authored subset is the **4** `{unverified, pending, faithful, uncertain}`).

7. **`record()` is self-transacting today and is called in a `forEach` loop, non-atomically.** `KcMastery.kt:60` `record(...) = transaction(db) { ... }`; the grade route calls it per-KC in a loop at `TutorRoutes.kt:2027` (`masteryKcs.forEach { kcId -> repo.record(userId, kcId, deterministicScore) }`). This is the B3 split site. **Resolution:** Task 8 adds `recordIn(tx,…)` (txn-less, signatures-lock §G) and re-points `record()` to a thin `transaction(db){ recordIn(this,…) }` wrapper. Phase 1 does NOT rewrite the grade route loop into one txn (that is Phase 3 / B1); Phase 1 only ships the split + the wrapper so callers keep compiling. Existing callers (`TutorRoutes.kt:2027`, `KcMasteryTest.kt:24/32/33/40/41/42`) must stay green against the wrapper.

8. **`me/delete` cascade** (single `transaction(ctx.db){ ... }`, child-tables-first, then Sessions, then Users; `AuditLinesTable` intentionally excluded). The five new user-scoped tables must be appended **child-first, BEFORE `SessionsTable`/`UsersTable`**. `PRAGMA foreign_keys=ON` is confirmed at `TutorDb.kt:15`. **(2026-06-07 P0-1 refactor — see the Task-13 amendment: the cascade is now the constant `ME_DELETE_CASCADE_TABLES` looped by the route, with a CI invariant over `ALL_TABLES` asserting every user-FK table is covered; the old `:2168-2183` line ref is stale.)**

---

## 1. Phase-1 goal & invariants (one screen)

**Goal:** lock the foundational schema and do the cull + migration + explicit backfill over the live 871-card DB right the first time, behind a verified off-box backup, idempotently, with a partial-failure abort+restore path. Ship the pure model functions (`PhaseModel`, `VerificationStatus.transition`) and the two txn-less repo seams (`recordIn`, `upsertRubricCriterion`) that Phases 2–3 build the atomic grade on.

**Load-bearing migration mechanism (memorize):**
- **Exposed `.default()` is a CLIENT-SIDE insert default only.** It does NOT emit a SQL `DEFAULT` clause. `SchemaUtils.createMissingTablesAndColumns(...)` emits `ALTER TABLE … ADD COLUMN … <type>` with **no** SQL default ⇒ on SQLite every existing row gets the new column = **NULL**.
- Therefore **every non-null-intent new column needs an explicit post-ALTER `UPDATE … SET col=<val> WHERE col IS NULL`** in the **same boot transaction**, immediately after `createMissingTablesAndColumns`.
- **SQLite `ALTER TABLE` is NOT transactional.** A multi-column add that fails midway leaves the DB half-migrated and cannot be rolled back by the surrounding `transaction{}`. ⇒ wrap the migration in try/catch: on a failed ALTER, log the failing column + trigger an auto off-box dump + abort non-zero. **Recovery = restore-from-backup** (M-PARTIAL).
- **Schema is NEVER ALTERed before a verified off-box `tools/db-backup.py` dump that asserts `MIN_EXPECTED_CARDS=800`** (M-DB). `db-backup.py:27` already enforces this floor fail-closed; Task 1 wires it as a precondition.

**Enums-as-VARCHAR, never native enum.** All new enum columns are `varchar(16)` (or `varchar(24)`/`varchar(96)` where noted). Wire/stored literal == enum `name`.

**New-table client-side defaults are fine without a backfill.** A brand-new table (CHANGE 4–10) has no pre-existing rows, so `varchar.default('…')` at INSERT time is sufficient — no ALTER, no post-create UPDATE needed (only ALTER-on-existing-rows needs the explicit UPDATE).

---

## 2. Dependency-ordered task list (overview)

Strict order — each depends on all the ones above it:

1. **Task 1 — M-DB backup precondition wiring** (no schema change; gate only).
2. **Task 2 — Card cull SQL** (references the audit doc; runs BEFORE any ALTER).
3. **Task 3 — Migration runner skeleton + M-PARTIAL** (try/catch + auto-backup + abort; the shell every ALTER runs inside).
4. **Task 4 — CHANGE 1: `fsrs_cards` ADD `kc_id`/`status`/`paused_at` + post-ALTER backfill `status='ACTIVE'`** + `M-SEED` (`FsrsSeedMain` sets ACTIVE) + `WHERE status='ACTIVE'` reads.
5. **Task 5 — `PhaseModel.transition` (pure, §A).**
6. **Task 6 — CHANGE 2: `kc_mastery` ADD `phase`/`entry_phase` + post-ALTER `PhaseModel.transition` replay backfill.**
7. **Task 7 — `VerificationStatus` enum + `transition` (§I, §2.4 state machine).**
8. **Task 8 — `KcMasteryRepo.recordIn(tx,…)` + `record()` wrapper split (B3, §G).**
9. **Task 9 — `FsrsCardRepo.upsertRubricCriterion(tx,…)` + `(userId, source, kcId)` index (B2, §H).**
10. **Task 10 — CHANGE 3: `KnowledgeConcept` +4/teaching fields & `Misconception` +3 fields, Kotlin-defaulted; deserialize over all 8 YAMLs (B4).**
11. **Task 11 — `ContentValidator` strict rule, all-errors-together (H11) + 4-literal enum check; run over all 8 YAMLs.**
12. **Task 12 — Register new tables (CHANGE 4–10: `session_summaries`, `attempts`, `verification_audit`, `report_wrong`, `exam_dates`, `kc_verification_status`).**
13. **Task 13 — `me/delete` cascade append child-first (B6).**
14. **Task 14 — `Problem` widen `sourceRefs` + `modelTag`, no-DDL decode back-compat (L4).**
15. **Task 15 — Migration-idempotency CI (M-IDEMP): seed legacy fixture → migrate twice → survival + `status='ACTIVE'`.**
16. **Task 16 — Phase-1 EXIT GATE assertions (all green before Phase 1 done).**

Tasks 5, 7, 10, 14 are pure/leaf and may be done in any order among themselves, but are listed where their consumers need them (6 needs 5; 11 needs 10). Tasks 3→4→6 are a strict chain (the runner, then the column adds that run inside it).

---

## Task 1 — M-DB backup precondition wiring

**Why:** no ALTER may run over the live corpus without a verified off-box dump that proves the corpus is not truncated. `tools/db-backup.py` already implements the fail-closed `MIN_EXPECTED_CARDS=800` floor (`db-backup.py:27`, `:69`); this task makes "backup passed" a hard precondition of the migration runner (Task 3) and asserts the floor behavior in a test so a future edit can't silently lower it.

**Files:**
- Verify (no edit expected): `tools/db-backup.py`.
- Test: `tools/test_db_backup.py` (pytest; the repo's only Python test surface — VERIFY AT BUILD whether a Python test harness is wired into CI; if not, add a `./gradlew`-invoked exec or a CI step. If no pytest harness exists, write this as a `tools/`-level script-test invoked from CI and note it).

- [ ] **Step 1 — Write the failing test.** `tools/test_db_backup.py::test_refuses_below_floor`: build a temp SQLite DB with `fsrs_cards` holding **799** rows, set `MIN_EXPECTED_CARDS=800` (or rely on default), run `db-backup.py <tmp_out>` with `JARVIS_DB=<tmp>`; assert **exit code 1** and stderr matches `/refusing backup/`. Add `test_passes_at_or_above_floor`: 800 rows → exit 0 + a `.sql.gz` written + integrity `PASS`.
- [ ] **Step 2 — Minimal impl.** Expected GREEN with the current `db-backup.py` (the floor + integrity check already exist at `:69` and `:94-123`). If the test fails, the only legal fix is to the test harness wiring, NOT to lower the floor.
- [ ] **Step 3 — Wire as a documented precondition.** Add to the Task-3 runner's preflight (or to the deploy runbook section of this doc) the literal command: `MIN_EXPECTED_CARDS=800 python3 tools/db-backup.py backups/` MUST exit 0 immediately before any migration boot. Record the dump path; it is the M-PARTIAL restore source.
- [ ] **Step 4 — Refactor.** None expected.

**Acceptance:** backup refuses below 800 (fail-closed), passes at/above, writes a verifiable gzip dump; the migration runner (Task 3) hard-requires a fresh passing dump.

---

## Task 2 — Card cull SQL (references the audit; runs BEFORE any ALTER)

**Why:** KEEP-MOST. Remove only the audited junk rows from the 871-card corpus BEFORE the schema ALTER, so the ALTER + backfill run over the curated set. The DELETE id-list is NOT in this plan — it comes from `docs/superpowers/findings/2026-06-03-card-cull-audit.md` (the separate grounded audit). **HARD INPUT:** that audit file must exist and enumerate exact card `id`s (or an exact, reproducible predicate) before this task runs. If it does not exist yet, STOP and produce it first.

**Count invariant (parameterized — do NOT hard-code 849):**
- Let `CULL_N` = the count of ids in the audit's DELETE list.
- Pre-cull count = **871** (live, verified).
- Post-cull count invariant = **`871 − CULL_N`**, and this MUST be **≥ `MIN_EXPECTED_CARDS` (800)**.
- The "849" in the master plan is an ESTIMATE (`871 − 22`), not an assertion. The test asserts `post == 871 − CULL_N AND post ≥ 800`, reading `CULL_N` from the audit list length — never the literal 849.

**Files:**
- New migration/cull script: `tools/card-cull.sql` (or a Kotlin one-shot `jarvis card-cull` subcommand — VERIFY AT BUILD which the team prefers; SQL file is simplest and reviewable). The DELETE targets exact `id`s from the audit.
- Test: `src/test/kotlin/jarvis/tutor/CardCullTest.kt` (Kotlin, in-memory SQLite seeded from a fixture mirroring the live corpus shape).

- [ ] **Step 1 — Write the failing test.** `CardCullTest::cull_removes_exactly_the_audited_ids_and_floor_holds`:
  - Seed an in-memory `fsrs_cards` with N rows (N = 871 or a representative fixture incl. the audited junk-shape rows + the known-good rows).
  - Load the audit DELETE id-list (read the audit file, or a test fixture mirroring it).
  - Run the cull.
  - Assert: every audited id is gone; every non-audited id survives; `final_count == N − CULL_N`; `final_count ≥ 800`.
  - Add `cull_is_idempotent`: running the cull twice leaves the same final set (re-deleting absent ids is a no-op).
- [ ] **Step 2 — Minimal impl.** Author `tools/card-cull.sql` as `DELETE FROM fsrs_cards WHERE id IN (<audited ids>);` (exact ids only — NO heuristic predicate that could over-delete). The audit is the source of truth for the id list.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor / runbook.** Document the run order in the deploy runbook section: (1) Task-1 backup passes, (2) run `tools/card-cull.sql`, (3) re-run `db-backup.py` (post-cull dump; still ≥800), (4) boot the migration runner (Task 3). The cull runs BEFORE the ALTER so the backfill never touches culled rows.

**Acceptance:** exactly the audited rows are removed; floor (≥800) holds; idempotent; runs strictly before the ALTER.

---

## Task 3 — Migration runner skeleton + M-PARTIAL (try/catch + auto-backup + abort)

**Why:** SQLite ALTER is not transactional. Every CHANGE-1/2 ALTER + its post-ALTER backfill must run inside ONE boot guard that, on any failure, logs the failing column, fires an auto off-box dump, and aborts non-zero so the operator restores from backup. This is the shell Tasks 4 and 6 plug their ALTERs + backfills into.

**Files:**
- New: `src/main/kotlin/jarvis/tutor/Migration.kt` — an object `TutorMigration` with `fun migrate(db: Database): MigrationResult` that: (1) calls `createMissingTablesAndColumns(<all tables incl. new>)`, (2) runs the explicit post-ALTER backfill UPDATEs (Tasks 4 + 6), all wrapped in try/catch.
- Wire into `installTutorContext` (`TutorRoutes.kt:2597`): replace the bare `transaction(db){ createMissingTablesAndColumns(...) }` at `:2602-2624` with `TutorMigration.migrate(db)`. (VERIFY AT BUILD: keep `installTutorContext` the single boot path; `FsrsSeedMain.kt:89` is a SEPARATE boot path that only creates 3 tables — Task 4 handles its `status='ACTIVE'` insert.)
- Test: `src/test/kotlin/jarvis/tutor/TutorMigrationTest.kt`.

- [ ] **Step 1 — Write the failing test.** `TutorMigrationTest::partial_failure_aborts_and_signals_restore`:
  - Connect an in-memory DB, seed legacy `fsrs_cards` rows.
  - Inject a forced failure mid-migration (VERIFY AT BUILD the cleanest injection: e.g. a test seam `migrate(db, failAfter = <colName>)`, or a column-name collision). Assert `migrate` returns/throws a failure result that names the failing column, and that the failure path is observable (auto-backup hook invoked, non-zero/throw).
  - `migrate_on_clean_db_succeeds`: in-memory DB → `migrate` returns success; all new columns + tables present.
- [ ] **Step 2 — Minimal impl.** `TutorMigration.migrate`: try { createMissingTablesAndColumns(...); backfills } catch (e) { log failing column from `e`; invoke an auto-backup hook (shell out to `db-backup.py` or document it as an operator step — VERIFY AT BUILD whether boot can shell out); rethrow / return failure }. On the in-memory test DB there is no off-box path; the auto-backup hook is a no-op stub there but the abort+signal path must still fire.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep `migrate` the SOLE place ALTERs + backfills live (Tasks 4 + 6 add into it). No ALTER may live outside this guard.

**Acceptance:** migration runs inside one try/catch guard; partial failure names the failing column, triggers auto-backup, aborts; clean DB migrates fully.

---

## Task 4 — CHANGE 1: `fsrs_cards` ADD `kc_id` / `status` / `paused_at` + backfill

**Why:** CHANGE 1 (`FsrsCards.kt:28`). Add the three columns nullable (Exposed ALTER adds them NULL on existing rows), then an explicit post-ALTER `UPDATE … SET status='ACTIVE' WHERE status IS NULL` in the same migration guard. `kc_id` backfill is additive (0 live RUBRIC_CRITERION rows today — Conflict §3). `M-SEED`: `FsrsSeedMain` inserts must set `status='ACTIVE'`.

**Real columns (NEW):**
| Property (Kotlin) | DB column | Type | Rule |
|---|---|---|---|
| `kcId` | `kc_id` | `varchar(64).nullable()` | Soft ref to `KnowledgeConcept.id` (KCs live in YAML, no DB FK). GAP_PROMOTION → NULL; RUBRIC_CRITERION → KC id. Do NOT reuse `source_ref varchar(32)` (too narrow). |
| `status` | `status` | `varchar(16).nullable()` | `ACTIVE \| QUARANTINED \| PAUSED` (3-value, not boolean — QUARANTINED = audit-not-cleared; PAUSED = student report-wrong). Nullable at DDL; backfilled to `ACTIVE`. |
| `pausedAt` | `paused_at` | `timestamp().nullable()` | set by REPORT WRONG (Phase 3); distinct from quarantine. |

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/FsrsCards.kt` — add the 3 columns to `FsrsCardsTable` (after `lapses`, before `primaryKey`/`init`), add `kcId`/`status`/`pausedAt` to `TutorCard` + `FsrsState` mapping as needed (status/kcId likely on `TutorCard`; `pausedAt` too — VERIFY AT BUILD the cleanest data-class placement; keep `toCard()` reading the new columns with null-safety).
- Modify: `src/main/kotlin/jarvis/tutor/Migration.kt` (Task 3) — add the post-ALTER backfill UPDATEs.
- Modify: `src/main/kotlin/jarvis/FsrsSeedMain.kt:166` (the `repo.insert(TutorCard(...))` site, and `:89` table-create) — seeded cards set `status='ACTIVE'` explicitly (M-SEED).
- Modify read paths: `FsrsCardRepo.findDueForUser` (`FsrsCards.kt:63`) + any `due()`/`forecast()`/queue-feeding query — add `AND status='ACTIVE'` (additive; NULL-safe: treat NULL as not-active only AFTER backfill, so the backfill MUST run first).
- Tests: `src/test/kotlin/jarvis/tutor/FsrsCardsMigrationTest.kt` (new), extend `src/test/kotlin/jarvis/tutor/FsrsCardsTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `FsrsCardsMigrationTest::legacy_row_gets_status_ACTIVE_after_backfill`: seed a pre-migration `fsrs_cards` row WITHOUT a status column value (simulate by inserting via raw SQL with status NULL, or via the pre-change table), run `TutorMigration.migrate`, assert the row's `status == 'ACTIVE'` and `kc_id IS NULL` and `paused_at IS NULL`.
  - `FsrsCardsMigrationTest::legacy_MANUAL_card_survives_due_and_queue_after_migration`: seed a legacy MANUAL card due now, migrate, assert `findDueForUser` still returns it (it is ACTIVE post-backfill). **This is the load-bearing "corpus doesn't disappear" test.**
  - `FsrsCardsMigrationTest::kc_id_backfill_touches_zero_rows_on_manual_only_corpus`: seed only MANUAL rows, migrate, assert no row's `kc_id` was set (matches live reality — Conflict §3). Then seed ONE RUBRIC_CRITERION fixture row whose `source_ref` is a KC id; assert the kc_id backfill sets its `kc_id` (exercises the future-proof UPDATE).
  - Extend `FsrsCardsTest`: `findDueForUser_excludes_non_ACTIVE` — a QUARANTINED/PAUSED card is NOT returned by the queue query.
  - `FsrsSeedMainTest` (VERIFY AT BUILD if one exists; else add): seeded card has `status='ACTIVE'` (M-SEED).
- [ ] **Step 2 — Minimal impl.** Add the 3 nullable columns. Add to `TutorMigration.migrate` after `createMissingTablesAndColumns`:
  ```
  UPDATE fsrs_cards SET status = 'ACTIVE' WHERE status IS NULL;
  UPDATE fsrs_cards SET kc_id = source_ref WHERE source = 'RUBRIC_CRITERION' AND kc_id IS NULL;
  ```
  (the kc_id rule: for RUBRIC_CRITERION cards, `source_ref` already holds the KC id — VERIFY AT BUILD the exact mapping when Phase 3 writes them; today it is 0-row). Add `AND status='ACTIVE'` to the queue/due reads. Set `status='ACTIVE'` in `FsrsSeedMain` insert.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Ensure `toCard()` is null-safe for the new columns; `M-GATE-PATHS` note (FsrsSeedMain + GapPromotion early-return + kc-less legacy cards are EXEMPT from the verification gate — they badge `unverified`, they are NOT blocked; that gate itself is Phase 2, this task only ensures the columns + ACTIVE default support it).

**Acceptance:** legacy MANUAL cards survive `due()`/queue (status ACTIVE post-backfill); 3 columns present; kc_id backfill 0-row on the live shape; non-ACTIVE cards excluded from the queue; seeded cards ACTIVE.

---

## Task 5 — `PhaseModel.transition` (pure, signatures-lock §A)

**Why:** SOLE writer of `kc_mastery.phase`. Pure, deterministic, unit-testable in isolation. Consumed by Task 6 (backfill replay) and Task 8 (`recordIn` in-txn). Frozen signature = §A.

**Frozen contract (§A — exact):**
```
enum class Phase { intro, practice, retrieval, mastered }   // wire literal == name (lowercase)
object PhaseModel {
    fun transition(ewma: Double, observations: Int, mastered: Boolean, current: Phase?): Phase
}
```
- Returns `Phase` (never null — a null `current` resolves to `intro`).
- `mastered` == `KcMastery.mastered` (`ewma ≥ 0.8 && obs ≥ 3`; `KcMastery.kt:31`, constants `MASTERY_THRESHOLD=0.8`, `MIN_OBSERVATIONS=3` at `:35-36`).
- `Phase.name` is the literal written to `kc_mastery.phase` and echoed in 4 wire sites (queue/today, drill/grade, mastery, entry_phase).

**Files:**
- New: `src/main/kotlin/jarvis/tutor/PhaseModel.kt`.
- Test: `src/test/kotlin/jarvis/tutor/PhaseModelTest.kt`.

- [ ] **Step 1 — Write failing tests (boundary table).** `PhaseModelTest`:
  - `null_current_resolves_to_intro`: `transition(0.0, 0, false, null)` → `intro`.
  - `mastered_true_yields_mastered`: `transition(0.85, 3, mastered=true, current=retrieval)` → `mastered`.
  - Monotone-progression cases (VERIFY AT BUILD the exact intro→practice→retrieval thresholds from the §4.1 phase model — the lock fixes the SIGNATURE, not the threshold table; derive the thresholds from the master plan §4.1 / spine and pin them here as the test's golden table). At minimum: low-obs cold KC ⇒ `intro`; mid ⇒ `practice`/`retrieval`; `mastered==true` ⇒ `mastered`; never regress below `current` unless mastery drops (decide + test the demotion rule).
  - `pure_no_side_effects`: same inputs → same output, no DB.
- [ ] **Step 2 — Minimal impl.** Pure `when`/threshold logic. No DB, no clock.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep it the ONLY place phase is computed.

**Acceptance:** pure; null→intro; mastered→mastered; deterministic golden table green.

---

## Task 6 — CHANGE 2: `kc_mastery` ADD `phase` / `entry_phase` + replay backfill

**Why:** CHANGE 2 (`KcMastery.kt:14`). Add `phase` + `entry_phase` nullable; backfill `phase` by replaying `PhaseModel.transition` over existing `(ewma_score, observations)`. `entry_phase` NULL ⇒ treat as `intro`. **0 live rows today** (Conflict §4 — `kc_mastery` table not even present in the live DB), so the backfill matches 0 rows on first migration; the idempotency CI (Task 15) seeds rows to exercise the replay.

**Real columns (NEW):**
| Property | DB column | Type | Rule |
|---|---|---|---|
| `phase` | `phase` | `varchar(16).nullable()` | `intro\|practice\|retrieval\|mastered`. STORED, not derived at call time. SOLE owner = `PhaseModel.transition` (Task 5), called in `recordIn` (Task 8) + the backfill here. |
| `entryPhase` | `entry_phase` | `varchar(16).nullable()` | phase at first KC entry from placement; drives the §4.1 phase-count gate. NULL ⇒ treat as `intro`. |

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/KcMastery.kt` — add `phase`/`entryPhase` columns to `KcMasteryTable`; add to the `KcMastery` data class + `readRow` mapping (null-safe; expose as `Phase?`).
- Modify: `src/main/kotlin/jarvis/tutor/Migration.kt` — add the replay backfill.
- Tests: `src/test/kotlin/jarvis/tutor/KcMasteryMigrationTest.kt` (new).

- [ ] **Step 1 — Write failing tests.**
  - `KcMasteryMigrationTest::phase_backfilled_by_replay`: seed a pre-migration `kc_mastery` row with `(ewma_score=0.9, observations=4, phase NULL)`, migrate, assert `phase == PhaseModel.transition(0.9, 4, mastered=true, current=null).name` (== `mastered`).
  - `entry_phase_null_treated_as_intro`: a row with `entry_phase NULL` → consumers read it as `intro` (assert via the accessor / a helper).
  - `backfill_zero_rows_on_empty_table`: empty `kc_mastery` → migrate → 0 rows touched, no error (matches live reality).
- [ ] **Step 2 — Minimal impl.** Add columns. In `TutorMigration.migrate`, after `createMissingTablesAndColumns`: SELECT all `kc_mastery` rows where `phase IS NULL`, compute `PhaseModel.transition(ewma, obs, KcMastery(...).mastered, current=null)`, `UPDATE … SET phase=<computed> WHERE userId=… AND kcId=…`. (Exposed has no SQL-side replay; iterate rows in the same txn.)
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Ensure the replay is idempotent (re-running over already-phased rows is a no-op via `WHERE phase IS NULL`).

**Acceptance:** existing mastery rows get a replayed `phase`; `entry_phase` NULL ⇒ intro; 0-row no-op on empty table; idempotent.

---

## Task 7 — `VerificationStatus` enum + `transition` (§I, §2.4)

**Why:** the KC-trust enum every wire site serializes and the pure state-machine transition. Frozen 5-literal enum (§I); `transition` encodes the §2.4 state machine. Phase 1 ships the TYPE + the pure transition only; the runtime store (`kc_verification_status` table) is registered in Task 12; the gate/runner are Phase 2.

**Frozen contract (§I — exact):**
```
enum class VerificationStatus { unverified, pending, faithful, uncertain, failed }   // wire literal == name
// pure transition (holder per §I; see Conflict §6):
fun transition(from: VerificationStatus, outcome: AuditOutcome): VerificationStatus
enum class AuditOutcome {
    ALL_AGREE_ROUNDTRIP_NONLLM_PASS,        // -> faithful
    FAMILY_COLLAPSE,                        // -> uncertain
    DEFINITIONAL_NO_GOLD_SPAN,              // -> uncertain
    NONLLM_LEG_NONE,                        // -> uncertain (floor)
    EQUATIONAL_LLM_UNCONFIRMED,             // B5r-3/D-R9: SymPy+round-trip pass, LLM family UNCLEAR -> uncertain
    PROSE_LLM_UNCONFIRMED,                  // MF-1/D-R17: prose anchor round-trips, LLM not bothSupported -> uncertain
    DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW,    // -> failed/uncertain
    REPORT_WRONG,                           // faithful -> pending
}
```
**§2.4 invariants (LOCKED):** no path reaches `faithful` without BOTH a non-LLM-leg pass AND families-agree. `FAMILY_COLLAPSE` ⇒ uncertain. A thrown leg ⇒ uncertain (never a mid-txn crash; that handling is Phase 2). No auto-clear from student attempts. FAIL-LOUD: "never ran" ≠ "disagreed".

**`AuditOutcome` post-lock additions (SESSION-57 sync):** `EQUATIONAL_LLM_UNCONFIRMED` (B5r-3/D-R9) + `PROSE_LLM_UNCONFIRMED` (MF-1/D-R17) — internal driver values added during the Phase-2 false-faithful work, BOTH mapping to `uncertain`. They keep the §2.4 invariant strict (anything short of bothSupported ⇒ not `faithful`) and do NOT widen the frozen `VerificationStatus` 5-literal wire surface. Canonical code: `VerificationStatus.kt:46-62`.

**Files:**
- New: `src/main/kotlin/jarvis/tutor/VerificationStatus.kt` (or `jarvis.content` — VERIFY AT BUILD the package the Phase-2 consumers expect; the lock places it where `VerificationGate`/`QueueItem` read it. Keep it where `KnowledgeConcept` and the trust store can both see it).
- Test: `src/test/kotlin/jarvis/tutor/VerificationStatusTest.kt`.

- [ ] **Step 1 — Write failing tests (table-driven over §2.4).**
  - `PENDING + ALL_AGREE_ROUNDTRIP_NONLLM_PASS → FAITHFUL`.
  - `PENDING + FAMILY_COLLAPSE → UNCERTAIN`.
  - `PENDING + DEFINITIONAL_NO_GOLD_SPAN → UNCERTAIN`.
  - `PENDING + NONLLM_LEG_NONE → UNCERTAIN`.
  - `PENDING + DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW → FAILED` (or UNCERTAIN per the locked split — pin the chosen mapping and test it).
  - `FAITHFUL + REPORT_WRONG → PENDING`.
  - `UNVERIFIED + (curate emits) → PENDING` (the §2.4 entry edge — VERIFY AT BUILD whether this edge is an `AuditOutcome` or a separate curate-side transition; the lock's `AuditOutcome` set does not include an explicit "curate emits" — note it: the UNVERIFIED→PENDING edge is set by curate-tutor Stage-9 directly, NOT via `transition`. Test that `transition` never silently jumps UNVERIFIED→FAITHFUL.)
  - `no_path_to_faithful_without_both_legs`: assert no `(from, outcome)` pair returns `faithful` except `ALL_AGREE_ROUNDTRIP_NONLLM_PASS`.
- [ ] **Step 2 — Minimal impl.** Pure `when(from)` × `when(outcome)`.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep `failed` reachable only at runtime (never an authored YAML literal — the validator accepts only the 4 authored literals; Task 11).

**Acceptance:** all §2.4 edges green; faithful only via the full-pass outcome; pure.

---

## Task 8 — `KcMasteryRepo.recordIn(tx,…)` + `record()` wrapper split (B3, §G)

**Why:** the grade route (`TutorRoutes.kt:2027`) calls `record()` per-KC in a loop; `record()` self-transacts (`KcMastery.kt:60`), so Phase 3's atomic grade (one txn over recordIn + attempts + upsertRubricCriterion) is unbuildable. Split now: add `recordIn(tx,…)` (txn-less, caller owns the txn), re-point `record()` to a thin wrapper. `recordIn` ALSO computes + writes `phase` via `PhaseModel.transition` (Task 5) in the same txn (signatures-lock §G side-effects).

**Frozen contract (§G — exact):**
```
fun recordIn(tx: Transaction, userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery
fun record(userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery   // = transaction(db){ recordIn(this,…) }
```
- `tx` = `org.jetbrains.exposed.sql.Transaction`.
- Returns `KcMastery` (existing shape; `record()` and `recordIn()` return identically).
- In-txn side effects: writes `ewma_score, observations, last_graded_at` (existing) PLUS `phase` (= `PhaseModel.transition(newEwma, newObs, computedMastered, current=existingPhase)`). `PhaseModel` is called ONLY here (and the Task-6 backfill).

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/KcMastery.kt` — extract the body of `record()` (`:60-83`) into `recordIn(tx, …)` that runs against the passed `Transaction` receiver (move `readRow`/`insert`/`update` calls to use the caller's txn); `record()` becomes `transaction(db) { recordIn(this, userId, kcId, score, now) }`. Add the `phase` write inside `recordIn` (read existing phase via `readRow`/a new accessor, compute via `PhaseModel.transition`, write in the same insert/update).
- No change required at `TutorRoutes.kt:2027` (it keeps calling the `record()` wrapper) — Phase 1 does NOT rewrite the grade loop into one txn (that is Phase 3 / B1). VERIFY AT BUILD that `record()`'s signature is byte-identical so the loop + `KcMasteryTest` stay green.
- Tests: extend `src/test/kotlin/jarvis/tutor/KcMasteryTest.kt`; new `src/test/kotlin/jarvis/tutor/KcMasteryRecordInTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `KcMasteryRecordInTest::recordIn_writes_phase_in_caller_txn`: open a `transaction(db){ recordIn(this, "u1","pa-kc-001", 1.0) }`, assert returned `KcMastery` + the row's `phase` == `PhaseModel.transition(...)` for those inputs.
  - `recordIn_and_two_other_writes_are_atomic`: in one `transaction{}` call `recordIn` then a second insert that throws; assert the `kc_mastery` write rolled back (proves caller-owned-txn atomicity — the whole point of B3).
  - `record_wrapper_still_works`: existing `KcMasteryTest` cases (`:24/32/33/40/41/42`) stay green via the wrapper.
- [ ] **Step 2 — Minimal impl.** Extract + re-point; add phase write.
- [ ] **Step 3 — Green** (incl. all pre-existing `KcMasteryTest` cases).
- [ ] **Step 4 — Refactor.** Ensure `readRow` (private, in-txn) is reused by `recordIn`; no double-txn.

**Acceptance:** `recordIn(tx,…)` runs in the caller's txn, writes phase, is atomic with sibling writes; `record()` wrapper preserves all existing behavior; existing tests green.

---

## Task 9 — `FsrsCardRepo.upsertRubricCriterion(tx,…)` + `(userId, source, kcId)` index (B2, §H)

**Why:** `FsrsCardRepo` has no upsert — Phase 3's confident-grade card write would duplicate a card every re-grade. Add a SELECT-then-UPDATE-or-INSERT inside the caller's txn, deduped by a `(userId, source, kcId)` index. Needs the `kc_id` column (Task 4). Phase 1 ships the method + the index; the grade-route call site is Phase 3.

**Frozen contract (§H — exact):**
```
fun upsertRubricCriterion(tx: Transaction, userId: String, kcId: String, front: String, back: String, state: FsrsState): TutorCard
```
- `tx` = `org.jetbrains.exposed.sql.Transaction` (same txn as `recordIn`).
- `state` = `FsrsState(difficulty, stability, retrievability, dueAt, lastReviewedAt, lapses)` (`FsrsCards.kt:16`).
- Returns `TutorCard` (the upserted row): `source = FsrsSource.RUBRIC_CRITERION` (`FsrsCards.kt:14`), `sourceRef` = the rubric/stem id, new `kc_id` column = `kcId`.
- Dedup key = the `(userId, source, kcId)` index (one RUBRIC_CRITERION card per (user, kc); re-grade UPDATEs, never duplicates).
- `front = stem_template ?: name_en`, `back = canonicalAnswer` (caller passes the resolved strings; the method does not read content).

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/FsrsCards.kt` — add the `(userId, sourceType, kcId)` index to `FsrsCardsTable.init` (maps to columns `user_id, source, kc_id`); add `upsertRubricCriterion(tx,…)` to `FsrsCardRepo` (txn-less; SELECT existing by `(userId, source=RUBRIC_CRITERION, kcId)` → UPDATE front/back/state, else INSERT). Reuse `toCard()`.
- Tests: extend `src/test/kotlin/jarvis/tutor/FsrsCardsTest.kt`; new `src/test/kotlin/jarvis/tutor/UpsertRubricCriterionTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `UpsertRubricCriterionTest::first_call_inserts`: `transaction(db){ upsertRubricCriterion(this, "u1","pa-kc-001","front","back", state) }` → returns a card with `source=RUBRIC_CRITERION`, `kc_id="pa-kc-001"`; exactly 1 row.
  - `second_call_same_kc_updates_not_duplicates`: call twice with different front/back → still exactly 1 row for `(u1, RUBRIC_CRITERION, pa-kc-001)`; front/back updated (the B2 bug fix).
  - `different_kc_inserts_second_row`: `(u1, pa-kc-002)` → 2 rows total.
  - `runs_in_caller_txn`: wrap in a txn that throws after the upsert → row rolled back.
  - `index_present`: assert the `(user_id, source, kc_id)` index exists (or that a duplicate raw INSERT is rejected — VERIFY AT BUILD whether the index is UNIQUE or just a lookup index; §H says "dedup key", so make it a uniqueIndex so duplicates are storage-rejected, mirroring `TasksTable.uniqueIndex` at `Tasks.kt:95`).
- [ ] **Step 2 — Minimal impl.** Add index + method.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Ensure GAP_PROMOTION cards keep `kc_id=NULL` (they never go through this method; §H note).

**Acceptance:** one RUBRIC_CRITERION card per (user, kc); re-grade updates; caller-txn-atomic; index enforces dedup.

---

## Task 10 — CHANGE 3: `KnowledgeConcept` + `Misconception` new fields, Kotlin-defaulted (B4)

**Why:** the verification engine + teaching engine read these; they must deserialize over all 8 existing YAMLs WITHOUT the fields (Kotlin defaults). `ContentSchema.kt:38` (`KnowledgeConcept`) + `:81` (`Misconception`). kaml (`Yaml.default`, `ContentRepo.kt:31`) requires Kotlin defaults for absent YAML keys.

**`KnowledgeConcept` ADD (all defaulted):**
| Field | Type | Default | Purpose |
|---|---|---|---|
| `verification_status` | `String` | `"unverified"` | authored SEED only; runtime truth = `kc_verification_status` table (Task 12). One of the 4 authored literals. |
| `invariant` | `String?` | `null` | precise invariant the 2-family + non-LLM leg check. |
| `grader_rules` | `List<String>` | `emptyList()` | machine-checkable rubric items (domain-scoped: PA⇒SymPy, else NONE). |
| `stem_template` | `String?` | `null` | canonical drill stem. |
| `phase_plan` | `List<String>?` | `null` | ordered subset of phases this KC runs; `null` ⇒ all four. Read by `ScaffoldPlanner.planFor` (Phase 3). |
| `far_transfer_stem` | `String?` | `null` | Gick-Holyoak; WIRE-or-defer in Phase 3 (`TASK P3-GHOST-FIELDS(b)`). Declared here only. |
| `self_explanation_prompt` | `String?` | `null` | Chi/Renkl; served Phase 3, read Phase 5. Declared here only. |
| `worked_example_first` | `Boolean` | `false` (VERIFY default) | SERVER decision surfaced in queue/today. Declared here only. |

**`Misconception` ADD (all defaulted):** `verification_status: String = "unverified"`, `self_explanation_prompt: String? = null`, `figure_spec: String? = null`.

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt` — add the fields to `KnowledgeConcept` (after `version`, or wherever keeps kaml happy) + `Misconception`. All defaulted.
- Tests: extend `src/test/kotlin/jarvis/content/ContentSchemaTest.kt`; new `src/test/kotlin/jarvis/content/ContentSchemaDefaultsTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `ContentSchemaDefaultsTest::all_8_real_yamls_deserialize_without_new_fields`: for each of the 8 KC YAMLs (`content/PA/kcs/pa-kc-001..006.yaml` + `pa-kc-fixture-compute.yaml` + `pa-kc-fixture-recursion.yaml`) via `ContentRepo.loadSubject("PA")`, assert decode succeeds and the new fields take defaults (`verification_status=="unverified"`, `invariant==null`, `grader_rules.isEmpty()`, `phase_plan==null`, etc.).
  - `both_misconception_yamls_deserialize_with_defaults`: `content/PA/misconceptions/001.yaml` + `002.yaml` → `figure_spec==null`, `self_explanation_prompt==null`, `verification_status=="unverified"`.
  - `a_yaml_with_the_fields_present_round_trips`: a fixture YAML that DOES set `invariant`/`grader_rules`/`phase_plan` decodes them correctly.
- [ ] **Step 2 — Minimal impl.** Add the defaulted fields.
- [ ] **Step 3 — Green** (all 8 + 2 misconception YAMLs decode unchanged).
- [ ] **Step 4 — Refactor.** No stored-but-unconsumed ghosts are introduced HERE beyond declaration; the read-site wiring (H16) is Phase 3/5 and is out of Phase-1 scope — but note each field's eventual READ site (table above) so Phase 3 can't claim "shipped" without wiring.

**Acceptance:** all 8 KC YAMLs + 2 misconception YAMLs deserialize with the new fields defaulted; explicit-value YAML round-trips.

---

## Task 11 — `ContentValidator` strict rule, all-errors-together (H11) + enum check

**Why:** `grounding_tier=='strict'` must require ALL of {anchored span ∧ vision-confirmed ∧ `invariant != null` ∧ `grader_rules` non-empty}, and `verification_status` must be one of the 4 authored literals. The validator must emit ALL failing conditions together (one report, not a separate error per overlapping rule — H11 "additive not double-error"). Run over all 8 KC YAMLs (6 real + 2 fixtures). `ContentValidator.kt:185` (`checkVerbatimSources`) already does the strict span+provenance check (`:207`, `:211`); H11 ADDS the `invariant`/`grader_rules`/enum conditions and the all-together aggregation.

**Files:**
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt` — extend the strict path in `checkVerbatimSources` (or a new `checkStrictGrounding(sub)` added to `validate` at `:155-162`) to also require `invariant != null` + `grader_rules` non-empty for strict KCs, emitting one aggregated issue listing every failed condition; add a `checkVerificationStatusEnum(sub)` asserting each KC/misconception `verification_status` ∈ `{unverified, pending, faithful, uncertain}` (the 4 AUTHORED literals; `failed` is runtime-only, Task 7 / Conflict §6).
- Tests: extend `src/test/kotlin/jarvis/content/ContentValidatorTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `strict_kc_missing_invariant_and_grader_rules_reports_both_in_one_issue`: a strict KC fixture with a good span+provenance but `invariant==null` AND `grader_rules.isEmpty()` → ONE `error` issue whose detail names BOTH missing conditions (not two separate issues).
  - `strict_kc_all_conditions_pass_is_clean`: strict KC with span+vision-confirmed+invariant+grader_rules → no strict error.
  - `verification_status_must_be_authored_literal`: a KC with `verification_status="failed"` (runtime-only) → `error` (failed is not an authored literal); a KC with `"pending"` → ok.
  - `validator_runs_over_all_8_yamls_clean`: `ContentRepo.loadSubject("PA")` → `ContentValidator.validate(...)` → assert `ok==true` for the current corpus (the 6 real KCs are `grounding_tier=standard` today, so strict rules don't fire; VERIFY AT BUILD that the 2 fixtures + 6 real KCs pass — if a real KC is strict and lacks invariant, that is a Phase-2 authoring task, NOT a Phase-1 validator bug; in that case the test asserts the EXPECTED issue set, not blanket `ok`).
- [ ] **Step 2 — Minimal impl.** Aggregate the strict conditions into one issue; add the enum check.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep `DISCLAIMER` ("structural checks only — content not groundedness-verified", `:28`) — H11 does not change that the validator proves well-FORMED, not TRUE.

**Acceptance:** strict KC emits all failing conditions together; enum check accepts only the 4 authored literals; runs over all 8 YAMLs with the expected (clean-or-known) issue set.

---

## Task 12 — Register new tables (CHANGE 4–10)

**Why:** the 6 new tables must be created in `installTutorContext`'s `createMissingTablesAndColumns(...)` (`TutorRoutes.kt:2603-2624`, now inside `TutorMigration.migrate` per Task 3). New tables need no backfill (no pre-existing rows); their client-side `.default()` at INSERT is fine.

**Exact column lists (from master plan CHANGE 4–10):**

- **`session_summaries`** (CHANGE 4): `id varchar(26) PK · user_id FK→Users · started_at ts · closed_at ts · cards_reviewed int · mastery_delta_json text · narrative text nullable`.
- **`attempts`** (CHANGE 5): `id PK · user_id FK · kc_id varchar(64) · task_id · problem_id · phase varchar(16) · student_confidence varchar(12) nullable (DEFINITELY|MAYBE|GUESS|IDK) · correct bool · score double · scaffold_level int(0..4) · is_far_transfer bool default false · self_explanation text nullable · recorded bool · graded_at ts` · INDEX `(user_id, kc_id, graded_at)`. **Column is `student_confidence`** (H1) — NOT `confidence` (that wire name is the existing grader-confidence reply `HIGH|LOW` at `TutorRoutes.kt:2551` area).
- **`verification_audit`** (CHANGE 6): `id PK · claim_id varchar(96) · kc_id · subject · claim_kind (DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM) · status · doc · page int nullable · page_anchor_status (LIVE|DEGRADED|NONE) · span_start nullable · span_end nullable · relocated_offset nullable · fuzzy_distance · family_a · family_b · nonllm_leg (SYMPY|TEST_EXEC|HUMAN_GOLD|NONE) · nonllm_result text nullable · agree bool · roundtrip_pass bool · collapsed_to_one_family bool · audited_at ts · audit_run_id · notes`. **No UI consumer** (owner-CLI/forensic only); register the table, do NOT build a surface.
- **`report_wrong`** (CHANGE 7): `id PK · user_id FK · kc_id · card_id nullable · grade_attempt_raw text · reported_at ts · resolution (OPEN|REVERIFIED_FAITHFUL|RETRACTED) nullable` **· resolved_by varchar(26) nullable · resolved_at ts nullable** (NEW, RF3/D-RF3, 2026-06-05). `user_id` is the REPORTER; `resolved_by` is the RESOLVER (the single-user admin/verify owner writes its own id — multi-user later generalizes owner→roles on the SAME edge). **Add these NOW, NULL-defaulted, while the table is empty (0 rows):** resolver provenance (who/when) is the one thing that CANNOT be backfilled later — a future multi-user corpus of historical resolutions would otherwise have no attribution. RF1 is already re-opening `report_wrong`-adjacent contracts, so this is additive + fail-safe now. The single-owner resolution assumption is recorded in `interface-signatures-lock.md` §I2 (RF3) per the load-bearing "record it in the schema, not just prose" clause. Memo: `build-review/2026-06-05-trustnet-refreeze-decisions.md` D-RF3.
- **`exam_dates`** (CHANGE 9): `id PK · user_id FK · subject varchar(64) · start_at ts · created_at ts`. (Routes + picker are Phase 3/6; the TABLE is registered here + added to me/delete in Task 13.)
- **`kc_verification_status`** (CHANGE 10): **SOLE runtime source of truth for a KC's resolved trust status.** Client-side default 'unverified' is fine (new table, INSERT-time). The YAML `verification_status` is the authored seed only, never written back at runtime. **Full column set (RE-FROZEN 2026-06-05, RF1 — DRIFT FIX: this doc previously froze only the first 4; live = 6, `Phase1Tables.kt:108-150`, and `source_span_hash` is added by RF1):**
  - `kc_id varchar(64) PK`
  - `status varchar(16) default 'unverified'`
  - `last_audit_run_id varchar(64) nullable`
  - `content_hash varchar(16) nullable` — D8/D2 staleness fingerprint AND the D9 surgical-upsert KEY. **`sha256_8`, v2 formula** `v2:kind|content|invariant|doc|page|spanStart|spanEnd` over a claimId-sorted set (vs live v1 which omits the `v2:` prefix + the raw `invariant` term — ContentReconcile.kt:285-300). NULL ⇒ fail-closed.
  - `source_span_hash varchar(16) nullable` — **NEW (RF1/D1).** PC-computed-only `sha256_8` over the round-trip's normalized located slice; MIRRORS `content_hash` width (RF1 overturned full-sha256: never ship one 64-bit + one 256-bit trust hash). Cannot ride `kcContentHash` (VPS serve-only / D7 has no `_sources` bytes); serve treats it as presence/version, NULL ⇒ fail-closed. Carried by D9 as an audit column.
  - `lecture_grounded bool nullable` — **was LIVE+served but absent from this lock (drift, now frozen).** B5r-2/D-R5/D-R6: the served "matches your lecture" badge lights on `status==faithful` OR `lecture_grounded==true`, both behind the `content_hash`+`source_span_hash` staleness gate. NULL ⇒ fail-closed.
  - `updated_at ts`
  - **Audit-column allowlist (RF1):** D9 syncs exactly `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}` (never `report_wrong`, never the whole db). The live v2 re-key + ONE re-audit while the table is empty is a SEPARATE human-gated checkpoint (RF1 ordering). Memo: `build-review/2026-06-05-trustnet-refreeze-decisions.md` D-RF1. Frozen shapes cross-ref: `interface-signatures-lock.md` §I.1.

(CHANGE 8 = `Problem` widen = Task 14, NOT a table.)

**Files:**
- New table objects (Exposed `Table`): `src/main/kotlin/jarvis/tutor/SessionSummaries.kt`, `Attempts.kt`, `VerificationAudit.kt`, `ReportWrong.kt`, `ExamDates.kt`, `KcVerificationStatus.kt` (VERIFY AT BUILD whether to group some; one file per table mirrors the existing layout, e.g. `KcMastery.kt`).
- Modify: `TutorMigration.migrate` (Task 3) / `TutorRoutes.kt:2603-2624` — add the 6 tables to `createMissingTablesAndColumns(...)`.
- Tests: `src/test/kotlin/jarvis/tutor/NewTablesMigrationTest.kt`.

- [ ] **Step 1 — Write failing tests.**
  - `NewTablesMigrationTest::all_six_tables_created`: in-memory DB → `migrate` → assert each of the 6 tables exists (query `sqlite_master` or do a trivial insert+select per table).
  - `kc_verification_status_default_unverified`: insert a row without `status` → reads back `'unverified'`.
  - `attempts_index_present`: assert the `(user_id, kc_id, graded_at)` index exists.
  - `enum_columns_are_varchar`: assert no native-enum column type (all the enum cols are varchar).
- [ ] **Step 2 — Minimal impl.** Author the 6 `Table` objects with the exact columns above; add to `createMissingTablesAndColumns`.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep FK columns referencing `UsersTable.id` (`.references(UsersTable.id)`) for the user-scoped tables (mirrors `KcMastery.kt:15`) so `PRAGMA foreign_keys=ON` is honored and the me/delete cascade (Task 13) is correct.

**Acceptance:** all 6 tables created with exact columns + indexes; enums as varchar; `kc_verification_status` defaults unverified.

---

## Task 13 — `me/delete` cascade append child-first (B6)

**Why:** GDPR Art-17 erasure must delete the user's rows in EVERY user-scoped table BEFORE deleting the user row, or `PRAGMA foreign_keys=ON` (`TutorDb.kt:15`) makes the `UsersTable` delete throw. The cascade is at `TutorRoutes.kt:2168-2183`. Append the new user-scoped tables (`session_summaries`, `attempts`, `report_wrong`, `exam_dates`) child-first, BEFORE `SessionsTable`/`UsersTable`. (`verification_audit` + `kc_verification_status` are NOT user-scoped — they key on `kc_id`, not `user_id` — so they are NOT in the cascade. VERIFY AT BUILD: confirm `verification_audit` has no `user_id` FK; if it does, add it.)

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` — inside the `transaction(ctx.db){ ... }` at `:2168-2183`, add (before `SessionsTable.deleteWhere` at `:2181`):
  ```
  SessionSummariesTable.deleteWhere { it.userId eq uid }
  AttemptsTable.deleteWhere { it.userId eq uid }
  ReportWrongTable.deleteWhere { it.userId eq uid }
  ExamDatesTable.deleteWhere { it.userId eq uid }
  ```
- Tests: new `src/test/kotlin/jarvis/tutor/MeDeleteCascadeTest.kt` (VERIFY AT BUILD if an existing me/delete test exists to extend — search `me/delete`).

- [ ] **Step 1 — Write the failing test.** `MeDeleteCascadeTest::delete_removes_rows_in_all_new_tables_no_fk_throw`:
  - Create a user; insert ≥1 row for that user in EACH new user-scoped table (`session_summaries`, `attempts`, `report_wrong`, `exam_dates`) PLUS the existing ones.
  - Call the me/delete path (or invoke the cascade transaction directly).
  - Assert: 0 leftover rows for that user in every table; the `UsersTable` row is gone; NO FK exception thrown (run under `PRAGMA foreign_keys=ON`).
- [ ] **Step 2 — Minimal impl.** Append the 4 deletes child-first.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Keep `AuditLinesTable` excluded (intentional, `:2164-2167`). Keep ordering: all child tables → `SessionsTable` → `UsersTable`.

**Acceptance:** erasure with rows in every new user-scoped table leaves 0 leftover and throws no FK error under `foreign_keys=ON`.

> **AMENDMENT 2026-06-07 (P0-1, Phase-3 adversarial council):** Task 13's original enumerated
> append covered the 4 Phase-1 tables but the council found TWO pre-existing user-FK tables STILL
> missing from the cascade: **`kc_mastery`** (`KcMastery.kt:16`, written by every graded drill) and
> **`user_provider_config`** (`ProviderConfig.kt:25`). Under `foreign_keys=ON` either one left behind
> makes the final `UsersTable.deleteWhere` FK-throw → the whole erasure rolls back → `/me/delete`
> **500s** for any user who graded a drill or set a provider. Both are now in the cascade.
> **Refactor (SSOT + class-killer, fix-claim discipline):** the cascade is no longer a hand-enumerated
> inline list. The child-table set is the single constant `ME_DELETE_CASCADE_TABLES` in
> `TutorRoutes.kt`; the route loops it (deleting by the `user_id` column) then deletes `UsersTable`
> last. Intentionally-retained user-FK tables (only `AuditLinesTable`, GDPR Art 17(3)(b)) live in
> `ME_DELETE_RETAINED_USER_FK_TABLES`. A CI invariant (`MeDeleteCascadeTest`) reflects over
> `jarvis.tutor.ALL_TABLES`, finds **every** table whose column foreign-keys `UsersTable.id`, and
> asserts each is covered — so the next user-FK table cannot be silently missed. The line refs in this
> Task (`:2168-2183`, `:2164-2167`, `:2181`) are pre-refactor and stale; the contract (child-first →
> Sessions → Users, Audit excluded, 0 leftover, no FK throw) is unchanged and now machine-enforced.
> NOTE: `consent_log` / `user_preferences` / `ai_literacy_confirmation` are user-scoped but FK-LESS
> (no `.references(UsersTable.id)`) — they were already in the cascade and cannot cause the FK-throw,
> so the FK-based invariant intentionally does not police them (they have no FK to police).

---

## Task 14 — `Problem` widen `sourceRefs` + `modelTag`, no-DDL decode back-compat (L4)

**Why:** `jarvis.tutor.Problem` (`PdfProblemExtractor.kt:18`, the ONLY `Problem` class — Conflict §5) is a serialized blob in `task_prep.problems_json` (`TaskPrep.kt:18`). Add `sourceRefs: List<SourceRef> = emptyList()` + `modelTag: String? = null`. **No DDL** — `tutorJson` (`TutorTypes.kt:75`) already has `ignoreUnknownKeys=true` + `encodeDefaults=true`, so old blobs (without the fields) decode with defaults. `modelTag`'s null default is the back-compat DECODE shape ONLY; the WRITER (Phase 3 `TASK P3-GEN`) sets it from the relay's returned model string — NEVER null, NEVER the `criticUsed="relay/claude"` literal (`TutorRoutes.kt:1520`). Phase 1 only declares the fields + the decode test.

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt:18` — add the 2 defaulted fields to `data class Problem` (import `jarvis.content.SourceRef`).
- Tests: new `src/test/kotlin/jarvis/tutor/ProblemWidenDecodeTest.kt`.

- [ ] **Step 1 — Write the failing test.** `ProblemWidenDecodeTest::old_blob_without_new_fields_decodes_with_defaults`:
  - Take an OLD `problems_json` blob (e.g. the literal used in existing tests: `[{"problem_id":"A1","page":4,"statement":"derive MLE"}]`, from `TaskPrepTest.kt:29`) and decode via `TutorTypes.tutorJson.decodeFromString(ListSerializer(Problem.serializer()), blob)`; assert `sourceRefs.isEmpty()` + `modelTag == null`, all existing fields intact.
  - `round_trips_with_new_fields`: encode a `Problem` with `sourceRefs=[SourceRef(...)]` + `modelTag="claude-..."`, decode back, assert equal.
  - `existing_problemsJson_tests_stay_green`: the existing `DrillGradeServerSideTest` / `GenerateDrillsRouteTest` / `ReprepGuardTest` decode paths (they use `Problem.serializer()` at `:74/:105/:110`) still pass.
- [ ] **Step 2 — Minimal impl.** Add the 2 defaulted fields.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** Note for Phase 3: the `modelTag` writer is `TASK P3-GEN`; Phase 1 must NOT leave any writer setting it to the `criticUsed` literal.

**Acceptance:** old blobs decode with defaults; round-trip works; no DDL; all existing `problems_json` decode tests green.

---

## Task 15 — Migration-idempotency CI (M-IDEMP)

**Why:** the migration MUST be safe to run twice (boot, re-boot, dry-run on a prod copy). Seed a legacy fixture DB → run `migrate` TWICE → assert survival + `status='ACTIVE'` + phase backfilled, and that the second run is a no-op (no double-backfill, no error).

**Files:**
- Test: `src/test/kotlin/jarvis/tutor/MigrationIdempotencyTest.kt`.
- VERIFY AT BUILD: ensure this test runs in CI (`./gradlew check`); if a "prod-DB-copy dry-run" CI step is wanted beyond the unit test, document it in the runbook (copy `~/.jarvis/tutor.db` → tmp → `migrate` → assert) — but the gating assertion is the unit test.

- [ ] **Step 1 — Write the failing test.** `MigrationIdempotencyTest::migrate_twice_is_safe`:
  - Build an in-memory DB shaped like the LEGACY schema (pre-CHANGE-1/2): `fsrs_cards` with status-less legacy rows + a `kc_mastery` row with `phase NULL`.
  - Run `TutorMigration.migrate(db)` ONCE: assert legacy card survives + `status='ACTIVE'`; kc_mastery `phase` backfilled.
  - Run `TutorMigration.migrate(db)` AGAIN: assert NO error; counts unchanged; `status` still `'ACTIVE'` (not re-written wrongly); `phase` unchanged (the `WHERE … IS NULL` guards make it a no-op).
  - Assert card count == seeded count (no rows dropped by the second run).
- [ ] **Step 2 — Minimal impl.** Should pass given Tasks 3/4/6 wrote `WHERE col IS NULL`-guarded backfills + `createMissingTablesAndColumns` is itself idempotent. Fix any non-idempotent backfill found.
- [ ] **Step 3 — Green.**
- [ ] **Step 4 — Refactor.** None.

**Acceptance:** migrate runs twice with no error, no row loss, no double-backfill; legacy rows survive ACTIVE + phased.

---

## Task 16 — Phase-1 EXIT GATE

All of the following MUST be green before Phase 1 is declared done. This is the single checklist a reviewer runs.

- [ ] **Card count invariant:** post-cull `COUNT(*) FROM fsrs_cards == 871 − CULL_N` (CULL_N read from `findings/2026-06-03-card-cull-audit.md`) AND `≥ MIN_EXPECTED_CARDS (800)`. (Never assert the literal 849.) — Task 2.
- [ ] **Legacy MANUAL card survives `due()`/queue after migration:** a pre-migration MANUAL card is `status='ACTIVE'` post-backfill and is returned by `findDueForUser` / the queue query. — Task 4.
- [ ] **Idempotency CI green:** seed legacy fixture → `migrate` TWICE → survival + `status='ACTIVE'` + `phase` backfilled + no error + no row loss. — Task 15.
- [ ] **`me/delete` with rows in every new user-scoped table → 0 leftover, no FK throw** under `PRAGMA foreign_keys=ON`. — Task 13.
- [ ] **`Problem` old blob decodes with defaults** (`sourceRefs=[]`, `modelTag=null`); round-trip works; existing `problems_json` decode tests green. — Task 14.
- [ ] **`ContentValidator` passes all 8 KC YAMLs** (6 real + 2 fixtures) for strict + enum (expected/known issue set, all-errors-together for strict). — Task 11.
- [ ] **`PhaseModel.transition` + `VerificationStatus.transition` pure golden tables green;** no path to `faithful` without the full-pass outcome. — Tasks 5, 7.
- [ ] **`recordIn(tx,…)` atomic with sibling writes; `record()` wrapper preserves all existing `KcMasteryTest` behavior.** — Task 8.
- [ ] **`upsertRubricCriterion(tx,…)` never duplicates per (user, kc); index enforces dedup.** — Task 9.
- [ ] **M-DB backup precondition:** `MIN_EXPECTED_CARDS=800 python3 tools/db-backup.py backups/` exits 0 immediately before migration; refuses below 800. — Task 1.
- [ ] **M-PARTIAL:** a forced mid-migration failure names the failing column, fires auto-backup, aborts; recovery = restore-from-backup is documented. — Task 3.
- [ ] **`./gradlew check` GREEN** (all Kotlin unit + existing route tests). VERIFY AT BUILD: also run `npm test` if any Phase-1 change touches `tutor-web/` (it should NOT — Phase 1 is backend-only; if `fsrsClient.ts`-shape changes leak in, they belong to later phases).

---

## 3. Deploy runbook (the exact live-DB order — operator steps)

This is the ONLY safe order to apply Phase 1 to the live `~/.jarvis/tutor.db`:

1. **Backup (M-DB):** `MIN_EXPECTED_CARDS=800 python3 tools/db-backup.py backups/` → must exit 0; record the dump path. (Pre-cull count = 871.)
2. **Cull (Task 2):** apply `tools/card-cull.sql` (exact audited ids from `findings/2026-06-03-card-cull-audit.md`). Verify `COUNT(*) == 871 − CULL_N ≥ 800`.
3. **Re-backup post-cull:** run `db-backup.py` again (still ≥800); this is the M-PARTIAL restore source for the migration.
4. **Migrate (Tasks 3/4/6/12):** boot via `installTutorContext` → `TutorMigration.migrate` runs `createMissingTablesAndColumns` + the explicit post-ALTER backfills (`status='ACTIVE'`, kc_id, phase replay) inside one try/catch guard. On failure: it names the failing column + auto-dumps + aborts → restore from step-3 dump.
5. **Verify EXIT GATE (Task 16)** against the migrated DB.

**Why this order:** cull BEFORE ALTER so the backfill never runs over junk rows; backup BEFORE every irreversible step (SQLite ALTER is not transactional); the post-cull backup is the restore point if the ALTER half-fails.

---

## 4. Out of Phase-1 scope (do NOT build here — named so nothing leaks in)

- The atomic grade route rewrite (one txn over recordIn + attempts + upsertRubricCriterion) — **Phase 3 / B1**. Phase 1 ships the SEAMS (`recordIn`, `upsertRubricCriterion`), not the call-site rewrite.
- `VerificationGate.gate(...)`, `VerificationRunner`, `LiveSourceLocator`, `NonLlmLeg`, report-wrong route — **Phase 2**. Phase 1 ships the `VerificationStatus` enum + transition + the `kc_verification_status` TABLE only.
- All API endpoints (`queue/today`, `mastery`, `calibration`, `session/close`, mock-exam, placement, exam-dates routes, extended `drill/grade` reply) — **Phase 3**. Phase 1 ships the TABLES they read/write, not the routes.
- `ScaffoldPlanner`, `NextKcSelector`, `PrereqGraph` — **Phase 3**.
- `Problem.modelTag` WRITER (`TASK P3-GEN`) — **Phase 3**. Phase 1 only declares the field + the decode test.
- Any `tutor-web/` component — **Phase 4/5/6**.

---

_End of Phase-1 Data Model TDD plan. Canonical-on-conflict = `2026-06-02-interface-signatures-lock.md`; scope source = `2026-06-02-master-impl-plan-v2.md` §4 Area A; cull id-source = `findings/2026-06-03-card-cull-audit.md` (must exist before Task 2)._
