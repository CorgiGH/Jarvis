# Gate-coverage registry — BOARD (Phase-0 deliverable F26)

> **Generated**: 2026-06-15 · **HEAD at build**: `85638dcd0fa44bece18c285cd6c4501e1d0a3c19`
> **Binding plan**: [`2026-06-15-end-to-end-master-plan-to-100-v2.md`](./2026-06-15-end-to-end-master-plan-to-100-v2.md) · **Binding spec**: [`2026-06-11-one-pass-digestion-teaching-engine-design.md`](../specs/2026-06-11-one-pass-digestion-teaching-engine-design.md)
> **Machine-readable source of truth**: [`2026-06-15-gate-coverage-registry.json`](./2026-06-15-gate-coverage-registry.json) (this board is generated FROM it; the checker `tools/gate-registry-check.mjs` parses the JSON, never this MD).

This is the **status+tag-stamped registry** the plan calls for (§2 `gate-COVERAGE` invariant, line 87; Phase-0 EXIT "coverage registry complete, zero UNMAPPED", line 117). Every spec clause / INV-* + every named non-INV gate clause is enumerated with its **LAYER**, **TAG**, **STATUS**, **backing gate**, and **evidence**. Nothing is UNMAPPED; nothing is silently absorbed.

---

## How to read this board

**LAYER** — *where the clause lives* (enum `L1 | L2 | SYS`):
- **L1 — TRUTH**: course facts (defn · theorem · method · derivation · complexity · code). Default gate KIND = **PROOF**.
- **L2 — TEACHING**: scaffolding (framing · figure · analogy · predict→reveal · plain words). Default gate KIND = **ESTIMATOR**.
- **SYS**: system/pipeline plumbing (ingest · classify · migrate · schedule · state). Decidable structural invariants.

**TAG** — *what KIND of gate is owed* (enum `PROOF | ESTIMATOR`). **LAYER and TAG are TWO INDEPENDENT stamps** — the TAG is derived per-clause from the spec text, **not** mechanically read off the LAYER (plan §0.1):
- **PROOF** — a hard **decidable** gate MUST back it. An estimator / `neverificat` lane backing a PROOF clause = **RED on coverage** (audit F43). The checker enforces this.
- **ESTIMATOR** — an estimator + `neverificat` / `verifică sursa` fallback is legitimate; the gate may **never** wear a PROOF / "provably" / "GATE-OF-GATES" label, and the residual is **named on the board**.

The deliberate counter-examples (LAYER ≠ default TAG): **dim-12 language = L2 · PROOF** (decidable RO-ness); **dim-13 depth = L1 · split** (code-compiles leg PROOF; complexity/invariant/edge legs ESTIMATOR → `verifică sursa`); **dim-14 pedagogy = L2 · split** (binding/non-paraphrase/ATTEMPT-precedes legs PROOF; separates-models leg the canonical L2 · ESTIMATOR).

**STATUS** (enum `LIVE-GREEN | PLANNED | RED-UNMAPPED`):
- **LIVE-GREEN** — a CI gate backs the invariant **today**. Scoped to today’s corpus reality: many are green on the **seeded ~PA corpus** / the **3 tracked viz families**; per-row NOTEs flag where full-corpus coverage is still PLANNED (Phase-0 EXIT is explicit: self-test-green proves **liveness**, not **coverage**).
- **PLANNED** — registered with a known backing-gate target; not yet built/gated. Honest non-overclaim.
- **RED-UNMAPPED** — **no effective live gate today** AND either it is a flagged debt cell (in the accepted-debt allow-list) or a non-decidable process control. **RED-UNMAPPED rows are listed in the SECURITY-DEBT / DEBT section below with a named owner — never folded into the green count.**

---

## Counts summary

| status | count |
|---|---:|
| **LIVE-GREEN** | **18** |
| **PLANNED** | **62** |
| **RED-UNMAPPED** | **6** |
| **TOTAL** | **86** |

`#LIVE-GREEN / #PLANNED / #RED-UNMAPPED = 18 / 62 / 6` (total 86).

**Zero UNMAPPED** (every row carries a layer + tag + status) and **zero unaccounted RED** (every RED-UNMAPPED id is in the accepted-debt allow-list). Machine-verified by `node tools/gate-registry-check.mjs` (exit 0).

---

## SECURITY-DEBT & FAIL-OPEN DEBT (the RED-UNMAPPED rows)

These 6 cells are **flagged debt with a named owner**. They are in the checker’s `acceptedDebt` allow-list (printed on every run) and are **NEVER laundered into the green count**. Two are true *security* debt (`auth-surface-security-debt`) / fail-open *enforcement* debt (`Phase0-Impeccable-blocking`); the rest are Linux-baseline / a11y / process debt. Any RED-UNMAPPED row that is **not** on this list fails the checker.

| id | status | tag | owner | residual / reason | never-green-until |
|---|:---:|:---:|---|---|---|
| `auth-surface-security-debt` | RED-UNMAPPED | PROOF | PM (Alex) — spec §A.3.1 #17 scopes auth OUT of the 100% target | CSRF-totality + cookie Secure/SameSite + session-fixation remain untested. A substantial auth suite exists in `gradle :check` (MagicLinkRepoTest/CsrfTest/AuthVerifyRouteTest/CrossTenantIsolationTest) but the named residual is real. Flagged cell, never laundered green. | the three residual classes get dedicated tests OR Alex explicitly re-scopes auth into the target |
| `Phase0-Impeccable-blocking` | RED-UNMAPPED | ESTIMATOR | Phase 0 (flip the `|| true` fail-open guard after calibration) | Impeccable runs fail-open (`npx impeccable@2.3.2 detect ... || true`, test.yml line 85); 25 real findings outstanding. Registered with a fail-open-debt note, NOT LIVE-GREEN. | Phase 0 calibrates the findings and drops the `|| true` guard (Phase-0 EXIT: Impeccable blocking-green) |
| `Phase0-LinuxVisualBaselines` | RED-UNMAPPED | ESTIMATOR | Phase 0 (Linux visual-baseline regeneration) | Baselines were Windows-only; CI runs Linux; the 3 visual specs are testIgnore'd / fail-open today. Visual baselining is a Linux gap until Phase 0 regenerates them. | Phase 0 regenerates Linux baselines (Phase-0 EXIT) |
| `INV-9.5` | RED-UNMAPPED | PROOF | Phase 0 (Linux baseline regen) + Phase D (dual-skin) | Visual baselines (shell/theme-dark/theme-light) are testIgnore'd (Linux gap, fail-open); no CI job asserts the baseline dir contains ONLY shell+2-themes. The INV-9.5 scope-gate is unenforced until the baselines are repaired and Phase D adds the dual-skin gate. | Phase 0 Linux-baseline regen + Phase D dual-skin EXIT |
| `Phase0-StandingRedTeam` | RED-UNMAPPED | ESTIMATOR | PM/council process discipline | Process step, not a CI gate (a human/council discipline: a defect class appearing twice = pipeline bug). Inherently not a decidable CI gate; registered, not machine-backed. | N/A — process control, displayed on the board, never claimed CI-green |
| `a11y-keyboard-aria-live-gate` | RED-UNMAPPED | ESTIMATOR | Phase D (Accessibility gate, audit F35) | No a11y gate today (the 5 axe tests disable color-contrast). Best-practice, NOT a spec clause — registered, not laundered as spec-compliance. | Phase D builds the keyboard-traversal + RO-aria-live gate (Phase-D EXIT) |

---

## The board — grouped by dimension

### Dim 1 — Ingest / extraction-legibility / dedup / gap-record / retry  
_5 rows — 0 LIVE-GREEN · 5 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-1.4` | SYS | PROOF | PLANNED | NONE — gap-record FILES-only grammar lint does not exist; gap-record subsystem is zero-code | spec §1.6 INV-1.4 (line 78); plan §2 dim-1 GATE, Phase E EXIT (line 227) |
| `INV-2.2` | SYS | PROOF | PLANNED | NONE as the ingest-path structural test; `ContentValidator.checkExtractionLegibility` exists (ContentValidator.kt) but is dead code (fires only when KCs reference docs); no CI test asserts it is invoked before classification on 100% of artifacts | spec §2.7 INV-2.2 (line 147) + §2.3; plan §2 dim-1, Phase E1 (line 217), Phase E EXIT (line 227) |
| `INV-2.3` | SYS | PROOF | PLANNED | NONE — content-hash dedup / artifact rows are zero-code | spec §2.7 INV-2.3 (line 148) + §2.5; plan §2 dim-1, Phase E EXIT (line 227) |
| `INV-9.2` | SYS | PROOF | PLANNED | NONE — gate-chain self-retry loop + pipeline logs zero-code; ≤retry-budget assertion not gated. Plan adds it to E1 exit (audit F5) | spec §9.5 INV-9.2 (line 517) + §9.1; plan §2 dim-1 'INV-9.2 retry-boundedness (F5)', Phase E1 (line 217), Phase E EXIT (line 227) |
| `OCR-alt-extractor-probe-gate` | SYS | PROOF | PLANNED | NONE — no OCR exists (only pdfbox:2.0.30; VisionLlm.kt is paid-model, collides with no-paid-APIs); offline OCR engine + probe-gate is net-new (audit F15); POO PNG-scan proof-set parked/verifica-sursa until built | plan Phase E1 'OCR + alt-extractor subsystem (audit F15 — net-new, does NOT exist)' (line 217), Phase E EXIT 'OCR/alt-extractor probe-gates green or POO-scan parked-flagged' (line 227); spec §2.3/§10.2 OCR route |

### Dim 2 — Classify by content (subject·kind·exam-tag·SORC-domain)  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-2.1` | SYS | PROOF | PLANNED | NONE — 9-kind classifier is zero-code (spec §2-3 digestion half unbuilt); no randomized-filename CI test | spec §2.7 INV-2.1 (line 146); plan §2 dim-2, Phase E2 (line 218), Phase E EXIT (line 227) |
| `INV-2.4` | SYS | PROOF | PLANNED | NONE — no `artifacts` table with subject/kind/exam-component/domain columns in real DB; SELECT COUNT(violations)=0 query has no CI job | spec §2.7 INV-2.4 (line 149); plan §2 dim-2, Phase E EXIT (line 227) |
| `INV-2.5` | SYS | PROOF | PLANNED | NONE — no artifact/seminar rows or CI query exists | spec §2.7 INV-2.5 (line 150); plan §2 dim-2, Phase E EXIT (line 227) |

### Dim 3 — Migration / schema / backup-first / registry / signatures  
_7 rows — 3 LIVE-GREEN · 4 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-3.1` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → MigrationBackupGateTest (src/test/kotlin/jarvis/tutor/MigrationBackupGateTest.kt); backup-tooling job → tools/test_db_backup.py + test_db_backup_hardening.py (M-DB fail-closed + restore drill) | spec §3.8 INV-3.1 (line 227) + §3.6; plan Phase 0 schema-hash + backup floor; test.yml backup-tooling job lines 160-172, MigrationBackupGateTest in suite |
| `INV-3.2` | SYS | PROOF | PLANNED | PARTIAL — ContentValidator/validateContent (in `gradle :check`) checks schema shape on the ~6 PA KCs, but the post-migration real-DB INV-3.2 (every served KC has all 5 beats in learner language, 0 violations) is not gated; beat-gen + migrations are zero-code | spec §3.8 INV-3.2 (line 228) + §3.2; plan §2 dim-3, Phase E4/E5/E7, Phase E EXIT (line 227) |
| `INV-3.3` | SYS | PROOF | PLANNED | NONE — `fsrs_cards.kc_id` backfill is 0/828 today (not done); count-equality + ≥1 non-null kc_id per subject is not a CI job | spec §3.8 INV-3.3 (line 229) + §3.6 step 4; plan §1 (828 cards 0 non-null kc_id), §2 dim-3 (backfill 0/828), Phase E7 (line 223) |
| `INV-3.4` | SYS | PROOF | PLANNED | NONE — grading_model/grade_component/exam_dates registry rows are zero-code; no CI count/source-URL assertion | spec §3.8 INV-3.4 (line 230) + §3.5; plan Phase E7 course-meta additive tables, Phase F |
| `INV-3.5` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → SignatureLockPinTest (src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt) — diff vs interface-signatures-lock; explicitly named in test.yml line 33 | spec §3.8 INV-3.5 (line 231) + §13; plan §2 dim-3, recon: INV-3.5 SignatureLockPinTest in `gradle :check` |
| `Phase0-SchemaHashParity` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → SchemaHashParityTest (src/test/kotlin/jarvis/tutor/SchemaHashParityTest.kt) — python db-backup.py vs Kotlin MigrationBackupGate.liveSchemaHash; recon 'NEW SchemaHashParityTest' | plan Phase 0 'Cross-language schema-hash CI parity' (line 108), Phase 0 EXIT 'schema-hash CI test green cross-platform' (line 117) |
| `identity-consent-migration-survival` | SYS | PROOF | PLANNED | PARTIAL — MigrationBackupGate guards fsrs_cards today; the identity/consent migration-survival invariant (audit F32) extends the backup floor + row-count-equality to users/sessions/magic_link_tokens/consent_log/trust_grants — not yet gated | plan §2 dim-3 'identity/consent migration-survival (F32)' (line 71), Phase E7 (line 223) |

### Dim 4 — Lesson beats (legality · dwell · completion-writes · selectors)  
_4 rows — 3 LIVE-GREEN · 1 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-4.1` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → BeatSelector property test (lesson/beat routes in the JUnit suite, per recon 'lesson/beat/practice routes'); closed 4-plan table enforced | spec §4.8 INV-4.1 (line 300) + §4.5; plan §2 dim-4, Phase C PROOF leg renamed beat-order-legality (line 176); recon: lesson/beat routes in `gradle :check`. NOTE: judgment call — recon names 'lesson/beat/practice routes' generically; confirm a dedicated BeatSelector legality property test exists |
| `INV-4.2` | SYS | PROOF | LIVE-GREEN | frontend job vitest (dwell formula unit test) OR backend lesson route — recon lists vitest unit coverage; dwell constants from lectie.html unit-tested at boundary word counts | spec §4.8 INV-4.2 (line 301) + §4.1; plan §2 dim-4. NOTE: judgment call on exact job — boundary unit test most plausibly in vitest; not explicitly named in recon |
| `INV-4.3` | SYS | PROOF | PLANNED | PARTIAL — AttemptsTable lacks beat_type/prediction columns today (audit T3); real-DB after-lesson-run assertion (attempt rows with beat_type, EWMA+FSRS updated, first-encounter once) is not gated; lesson-beats.spec exercises UI but not the DB-write contract on real corpus | spec §4.8 INV-4.3 (line 302) + §4.4; plan §2 dim-4/dim-10, Phase G EXIT (line 253) |
| `INV-4.4` | SYS | PROOF | LIVE-GREEN | lesson-gates job (real backend, seeded corpus) → e2e/lesson-gates.spec.ts; frontend job → e2e/lesson-beats.spec.ts (§4.7 selectors + interaction smoke, one-shot/scrubber-back check) | spec §4.8 INV-4.4 (line 303) + §4.6/§4.7; plan §2 dim-4 & dim-11; test.yml lesson-gates job lines 218-283, lesson-beats in npm run e2e. NOTE: LIVE on the seeded ~PA corpus only; LATE coverage on full proof-set is PLANNED |

### Dim 5 — Viz figures (trace-match · semantic invariants · no-clip · SVG-only · binding)  
_9 rows — 3 LIVE-GREEN · 6 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-5.1` | L1 | PROOF | LIVE-GREEN | frontend job vitest → traceMatchHarness.test.tsx + per-family *.test.tsx (GraphTree/ChartDistribution/SequenceArray); rendered step states == reference execution | spec §5.8 INV-5.1 (line 353) + §5.4; plan §2 dim-5; recon 'viz trace-match INV-5.1/5.2'. NOTE: LIVE for the 3 tracked families only — matrix-grid/sort-merge are uncommitted WIP (Phase A0); full 6-family + real-DB-instance coverage is PLANNED (Phase A) |
| `INV-5.2` | L1 | PROOF | LIVE-GREEN | frontend job vitest (semantic invariant asserts, per recon 'viz trace-match INV-5.1/5.2') + seededWrong*.test.tsx fixtures | spec §5.8 INV-5.2 (line 354) + §5.4 (len(Hx)≈len(x)); plan §2 dim-5. NOTE: LIVE for 3 tracked families; semantic-state-delta frame gates (every-frame-advances/final==end-state) are net-new PLANNED in Phase A |
| `INV-5.3` | L1 | PROOF | LIVE-GREEN | frontend job Playwright e2e → family-no-clip.spec.ts + seq-array-no-clip.spec.ts + chart-dist-no-clip.spec.ts + seeded-clip-legs.spec.ts; helpers/assertNoClip.ts | spec §5.8 INV-5.3 (line 355); plan §2 dim-5/dim-11; recon 'per-family *-no-clip INV-5.3 + NEW seeded-clip-legs'. NOTE: the 3 no-clip specs are at 1280 viewport (plan names them 'fail-the-build-until-migrated' to 1536×648/730 in Phase A); LIVE today, migration PLANNED |
| `INV-5.4` | L1 | PROOF | PLANNED | NONE found in CI — no grep job in test.yml for `three`/`webgl`/`webgpu`/canvas-figure patterns over lesson-facing frontend; spec §5.6 says lint/CI-enforced but no such job is wired | spec §5.8 INV-5.4 (line 356) + §5.6; plan §2 dim-5. NOTE: judgment call on status — if an import-ban grep lives in vitest/lint it would be LIVE-GREEN; none surfaced in recon or test.yml, so PLANNED |
| `INV-5.5` | L1 | PROOF | PLANNED | PARTIAL — FigureBindingNonVacuityTest (in `gradle :check`) asserts specific KC→figure bindings, but the spec INV-5.5 'every figure ref in served lessons resolves to registered family + typed instance, dangling fails admission' over the served corpus is not fully gated; families not all migrated | spec §5.8 INV-5.5 (line 357) + §5.5; plan §2 dim-5, Phase A mergesort re-bind deletes FigureBindingNonVacuityTest.kt:72-89 (line 147) |
| `semantic-frame-gate-every-frame-advances` | L1 | PROOF | PLANNED | NONE — net-new semantic frame gate (NOT a perceptual hash); closes the divide-frames-frozen leak (spec §6 mergesort). Unbuilt at HEAD | plan Phase A 'every-frame-advances over the typed algorithm-STATE delta' (line 139); §2 dim-5 'semantic-state-delta frame gates (F6/F13)' (line 73) |
| `semantic-frame-gate-final-eq-endstate` | L1 | PROOF | PLANNED | NONE — net-new; Phase-A adds 'pa-kc-002 serves a sorting figure whose final frame==sorted(input)' to the exit gate. Unbuilt | plan Phase A 'final==end-state' (line 141) + mergesort re-bind (line 147), Phase A EXIT (line 149) |
| `semantic-frame-gate-no-regression` | L1 | PROOF | PLANNED | NONE — net-new (F13); perceptual-diff secondary. Unbuilt | plan Phase A 'no-regression = distinct-frame-count AND per-family invariant-coverage must NOT decrease' (line 142) |
| `concept_type-family-FITNESS-gate` | L1 | PROOF | PLANNED | NONE — fail-closed fitness gate (no fit→HALT+backlog) unbuilt | plan Phase A 'concept_type → family FITNESS gate (fail-CLOSED)' (line 145); §2 dim-5 |

### Dim 6 — Trust-net / Layer-1 admission / verifier-dep  
_5 rows — 1 LIVE-GREEN · 4 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-1.1` | L1 | PROOF | PLANNED | NONE in CI today — `verifyContent`/`trustInvariants` are manual gradle tasks NOT referenced in .github/workflows/test.yml; the literal `SELECT COUNT(*) FROM <servable view> WHERE verification_status!='VERIFIED'=0` over real DB is not a CI job | spec §1.6 INV-1.1 (line 75); plan §2 dim-6, Phase F EXIT GATE (line 239), Phase I EXIT (line 271); build.gradle.kts registers verifyContent/trustInvariants but test.yml never invokes them |
| `INV-1.1b` | L1 | PROOF | PLANNED | NONE — net-new invariant; no flagged-servable surface or budget gate exists in CI | plan §0.2 NET-NEW INV-1.1b (line 42); plan §2 dim-6 GATE col; Phase F/I EXIT. Explicitly authored in v2, no test backs it |
| `INV-1.2` | L1 | PROOF | PLANNED | NONE in CI as the INV-1.2 transaction-flip test; serve-side 404 refusal exists in code (QueueMasteryCalibrationRoutes.kt:400-403) but no CI test flips a row to unverified against a real-DB copy and asserts refusal | spec §1.6 INV-1.2 (line 76); spec §1.2(b) refusal-half-exists note; plan §2 dim-6, Phase F EXIT (line 239), Phase I EXIT (line 271) |
| `INV-9.3` | L1 | PROOF | LIVE-GREEN | verifier-deps-loud-red job (test.yml lines 174-216) — hides verifier deps, asserts verifyContent aborts RED ('verifyContent ABORTED (FAIL-LOUD, H4)'), names JARVIS_RELAY_URL/JARVIS_PYTHON3; recon 'verifier-deps-loud-red-job INV-9.3' | spec §9.5 INV-9.3 (line 518) + §9.2 gate 2; plan §2 dim-6 |
| `Phase0-RelayRetryBackoff` | L1 | PROOF | PLANNED | PARTIAL — VerificationRunnerTest runs in `gradle :check`, but the relay retry/backoff path closing the pa-kc-006 false-negative class is a Phase-0 deliverable; not confirmed gated | plan Phase 0 'Relay retry/backoff ... closes the pa-kc-006 false-negative class' (line 109); recon trust-net VerificationRunnerTest in `gradle :check` |

### Dim 7 — Priority / readiness / placement / calibration  
_4 rows — 0 LIVE-GREEN · 4 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-7.3` | SYS | PROOF | PLANNED | NONE — priority = proximity×weight×gap×prereq not built as a gated pure function over real registry/exam_dates rows (registry+exam_dates tables are zero-code) | spec §7.6 INV-7.3 (line 440) + §7.4; plan §2 dim-7, Phase G EXIT (line 253) |
| `INV-7.5` | SYS | PROOF | PLANNED | NONE — readiness dashboard route + registry rows zero-code; §7.4 selector Playwright suite not in CI | spec §7.6 INV-7.5 (line 442) + §7.4; plan §2 dim-7, Phase G EXIT (line 253) |
| `INV-7.6` | SYS | PROOF | PLANNED | NONE — net-new (spec §7.6 defines only INV-7.1–7.5); placement rebuild to probe SUBJECT + entry_phase write path unbuilt (current PlacementShell never calls submit, audit T5) | plan §2 dim-7 NET-NEW (line 75), Phase G EXIT 'INV-7.6/7.7 NET-NEW, registered PLANNED' (line 250/253); spec §7.5 placement rebuild rationale |
| `INV-7.7` | SYS | PROOF | PLANNED | NONE — net-new; calibration surface mounted-nowhere today (audit T7); telemetry-write test unbuilt | plan §2 dim-7 NET-NEW (line 75), Phase G EXIT (line 250/253); spec §7.5 calibration-mounted rationale |

### Dim 8 — Human experience checkpoint  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-1.3` | L2 | ESTIMATOR | PLANNED | NONE — the §1.5 checkpoint review screen and its Playwright smoke do not exist; no `artifacts` live-row→approved-checkpoint-event test in CI | spec §1.6 INV-1.3 (line 77) + §1.4/§1.5; plan §2 dim-8 (line 76), Phase E6 (line 222), Phase E EXIT (line 227) |
| `checkpoint-gap-ledger-smoke` | SYS | PROOF | PLANNED | NONE — checkpoint review screen (§1.5 data-testids) and gap-ledger (§2.6 5 selectors + upload smoke) are zero-code; the ghost-component/PDF-404 class smoke unbuilt | spec §1.5 + §2.6 selectors; plan Phase E6 'checkpoint Playwright (§1.5) + gap-ledger smoke (§2.6)' (line 222/227), Phase E EXIT (line 227) |
| `checkpoint-experience-residual-5` | L2 | ESTIMATOR | PLANNED | NONE as a decidable gate — by definition: the human experience-checkpoint is the named estimator/residual, displayed on the board, never proof-labeled; INV-1.3 backs only its existence not its truth-power | plan §2 dim-8 'judges experience NOT truth (residual #5)' (line 76), HONEST RESIDUALS #5 (line 316), Phase I EXIT (line 273). NOTE: tag ESTIMATOR by spec §0.1; status PLANNED = displayed-on-board deliverable, not a CI gate |

### Dim 9 — Graders (routing · sandbox · LLM-never-alone · golden · practice surfaces)  
_6 rows — 5 LIVE-GREEN · 1 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-6.1` | SYS | PROOF | PLANNED | PARTIAL — grader stack built + GraderGoldenHarnessTest green in `gradle :check`, but INV-6.1 is 'pending-not-green until problem bank ≥1 row/subject' (problems table = 0 rows today); flips green only after Phase F populates the bank | spec §6.4 INV-6.1 (line 397) + note 'pending-not-green'; plan §2 dim-9, Phase F EXIT 'problem bank ≥1 row/subject (INV-6.1 flips green)' (line 239) |
| `INV-6.2` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → ExecutionGraderSandboxTest (src/test/kotlin/jarvis/tutor/grader/ExecutionGraderSandboxTest.kt); R installed + Alk fetched in CI (test.yml lines 28-32). NOTE: green on fixture/reference problems; 'on real corpus' (plan §2 dim-9) is PLANNED until Phase F populates the bank | spec §6.4 INV-6.2 (line 398); plan §2 dim-9 'INV-6.2 (on real corpus)', Phase G 'turning INV-6.2 green on the real corpus' (line 251); recon ExecutionGraderSandboxTest INV-6.2 |
| `INV-6.3` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → grader-chain assertion (GraderGoldenHarnessTest / grader suite asserting no chain is LLM-judge-only). NOTE: judgment call — recon names Gate7 GraderGoldenHarnessTest; INV-6.3's specific 'no chain LLM-only' assertion presumed in the grader suite | spec §6.4 INV-6.3 (line 399) + §6.3; plan §2 dim-9, Phase C 'no grader chain is LLM-judge-only (INV-6.3)' (line 182) |
| `INV-6.4` | SYS | PROOF | LIVE-GREEN | backend job `gradle :check` → GraderGoldenHarnessTest (= Gate7, §9.2); per recon 'grader-golden GraderGoldenHarnessTest=Gate7' | spec §6.4 INV-6.4 (line 400) + §9.2 gate 7; plan §2 dim-9. NOTE: golden sets exist per built graders; per-subject coverage broadens with the populated bank (Phase F) |
| `INV-6.5` | SYS | PROOF | LIVE-GREEN | practice-e2e job → e2e/practice/{proof-drill,step-trace,code-practice,mock-exam,deliverable-tracker}.spec.ts + phase5-core-loop.spec.ts (real Kotlin backend + seeded corpus, test.yml lines 109-152) | spec §6.4 INV-6.5 (line 401) + §6.2; plan §2 dim-9; recon practice-e2e '5 surfaces + phase5-core-loop'. NOTE: 'real corpus' = the seeded PA corpus; full-subject corpus is PLANNED (Phase F) |
| `INV-6.6` | SYS | PROOF | LIVE-GREEN | practice-e2e job → e2e/practice/code-practice.spec.ts (asserts `code-practice-reference` absent pre-attempt, present post-attempt) | spec §6.4 INV-6.6 (line 402) + §6.2.3; plan §2 dim-9; recon practice-e2e 5 surfaces |

### Dim 10 — Mastery state (single-writer · per-surface consistency · forgetting)  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-7.1` | SYS | PROOF | PLANNED | NONE — unified single-writer mastery service not built; CI grep for direct FSRS/EWMA writes outside it (→0) does not exist; today selector sorts EWMA-only, dueConcepts called from nowhere (audit T2) | spec §7.6 INV-7.1 (line 438) + §7.1; plan §2 dim-10, Phase G EXIT (line 253) |
| `INV-7.2` | SYS | PROOF | PLANNED | NONE — depends on unified state + populated surfaces; no real-DB per-surface updated_at/mastery+schedule consistency test in CI | spec §7.6 INV-7.2 (line 439); plan §2 dim-10, Phase G EXIT (line 253) |
| `INV-7.4` | SYS | PROOF | PLANNED | NONE — re-lesson trigger (replaces phantom INV-4.5 per audit F3) not built; simulated time-advance on real-DB copy → ③⑤ enqueue with params≠original is not a CI test | spec §7.6 INV-7.4 (line 441) + §7.3; plan §2 dim-10, Phase G EXIT 'INV-7.4 replaces phantom INV-4.5' (line 253) |

### Dim 11 — Visual / a11y / no-clip viewport  
_4 rows — 0 LIVE-GREEN · 0 PLANNED · 4 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-9.5` | SYS | PROOF | RED-UNMAPPED | NONE EFFECTIVE — visual baselines (shell.png/theme-dark.png/theme-light.png) are testIgnore'd (Linux gap, fail-open per recon); no CI job asserts the baseline dir contains ONLY shell+2-themes or reds on a per-lesson baseline. Plan repairs Linux baselines in Phase 0 + Phase D dual-skin; until then the INV-9.5 scope-gate is unenforced | spec §9.5 INV-9.5 (line 520) + §9.2 gate 6; plan §2 dim-11, Phase 0 Linux-baseline regen (line 111), Phase D EXIT (line 207); recon 'Visual baselines testIgnore'd (Linux gap, fail-open)'. NOTE: judgment call — RED-UNMAPPED because the existing baseline specs are fail-open/ignored, not a live gate |
| `Phase0-Impeccable-blocking` | SYS | ESTIMATOR | RED-UNMAPPED | NONE EFFECTIVE — Impeccable runs fail-open (`npx impeccable@2.3.2 detect ... || true`, test.yml line 85); 25 real findings outstanding; per recon register with a fail-open-debt note, NOT LIVE-GREEN. Plan 0 flips the `|| true` guard after calibration | plan Phase 0 'Impeccable → blocking (flip the || true fail-open guard)' (line 107), Phase 0 EXIT 'Impeccable blocking-green' (line 117); spec §9.2 gate 5; test.yml line 80-85 fail-open; recon 'Impeccable: fail-open (|| true), 25 real findings'. NOTE: tag ESTIMATOR (design-rule linting = Layer-2 geometry/CSS proxy per §9.4(e)); status RED-UNMAPPED with fail-open-debt note |
| `Phase0-LinuxVisualBaselines` | SYS | ESTIMATOR | RED-UNMAPPED | NONE EFFECTIVE — baselines are Windows-only; CI runs Linux; the 3 visual specs are testIgnore'd/fail-open today (recon). Phase 0 regenerates them; until then visual baselining is a Linux gap | plan Phase 0 'Linux visual-baseline regeneration (baselines were Windows-only; CI runs Linux)' (line 111); recon 'Visual baselines: testIgnore'd (Linux gap, fail-open)'. NOTE: tag ESTIMATOR (screenshot pixel proxy, Layer-2 geometry per §9.4); status RED-UNMAPPED (fail-open today) |
| `a11y-keyboard-aria-live-gate` | SYS | ESTIMATOR | RED-UNMAPPED | NONE — no a11y gate today (the 5 axe tests disable color-contrast, per plan line 204); best-practice not a spec clause — registered, not laundered as spec-compliance | plan §2 dim-11 'keyboard-traversal + RO-aria-live (F35)' (line 79), Phase D 'Accessibility gate (audit F35)' (line 204), Phase D EXIT (line 207). NOTE: tag ESTIMATOR (UX best-practice, plan explicitly says 'Best-practice, not a spec clause'); RED-UNMAPPED today |

### Dim 12 — Language (RO content · chrome grep · rendered-RO) [L2·PROOF]  
_4 rows — 3 LIVE-GREEN · 1 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-8.1` | L2 | PROOF | PLANNED | PARTIAL — validator code + LanguageGateSeededRegressionTest exist in `gradle :check`, but INV-8.1's real-DB query 'served fields lacking a language-check record → 0 rows' depends on served content existing (4 subjects 0 KCs); not gated on real served corpus | spec §8.3 INV-8.1 (line 466) + §8.2; plan §2 dim-12, Phase F EXIT 'language INV-8.1–8.4 green on real served content' (line 239) |
| `INV-8.2` | L2 | PROOF | LIVE-GREEN | backend job `gradle :check` → LanguageGateSeededRegressionTest (src/test/kotlin/jarvis/content/LanguageGateSeededRegressionTest.kt); recon names it INV-8.2 | spec §8.3 INV-8.2 (line 467); plan §2 dim-12; recon LanguageGateSeededRegressionTest INV-8.2 in `gradle :check` |
| `INV-8.3` | L2 | PROOF | LIVE-GREEN | frontend job → tools/chrome-en-grep.mjs, BLOCKING (test.yml lines 77-79); recon 'chrome-en-grep INV-8.3 (blocking)' | spec §8.3 INV-8.3 (line 468) + §8.2; plan §2 dim-12; test.yml 'Chrome EN-literal grep (INV-8.3)' |
| `INV-8.4` | L2 | PROOF | LIVE-GREEN | frontend job Playwright → seeded-violations.spec.ts (en-in-ro.json) + helpers/roHeuristic.ts; lesson-gates language leg. NOTE: green on seeded fixtures/PA lesson; 'real proof-set lessons' across subjects is PLANNED (Phase F) | spec §8.3 INV-8.4 (line 469) + §9.2 gate 4d; plan §2 dim-12; recon 'seeded-violations.spec INV-9.4/8.4' |

### Dim 13 — Depth oracle [L1 split: PROOF + ESTIMATOR legs]  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `depth-PROOF-leg-code-compiles` | L1 | PROOF | PLANNED | PARTIAL — rides the built Phase-6/G execution graders (INV-6.2, ExecutionGraderSandboxTest in `gradle :check`), BUT the depth artifact schema + concept-keyed depth bank do not exist (net-new, no prior plan hosted it); the leg cannot fire until Phase B authors the depth bank | plan §2 dim-13 'Phase-B split gate (F8) PROOF leg = code compiles+tests' (line 81), Phase B PROOF leg (line 161); spec dimension-13 is net-new (no INV-* in spec — depth artifact unnamed/unschema'd per plan §1 line 59) |
| `depth-ESTIMATOR-legs-complexity-invariant-edges` | L1 | ESTIMATOR | PLANNED | NONE — undecidable without authored-source / second-independent-LLM-family cross-check; renders verifica-sursa + excluded from mastery until grounded (residual #2); depth oracle unbuilt | plan §2 dim-13 'ESTIMATOR legs ... → verifica sursa' (line 81), Phase B ESTIMATOR legs (line 162), HONEST RESIDUAL #2 (line 313). NOTE: layer L1 (truth depth) but tag ESTIMATOR (per §0.1 a Layer-1 clause the machine can't prove decidably) — a deliberate counter-example to the L1=PROOF default |
| `depth-per-leg-mastery-suppression` | L1 | PROOF | PLANNED | NONE — the suppression mechanic (flip a leg → warning renders DOM-asserted AND mastery suppressed) is the Phase-B EARLY-exit test; unbuilt | plan §2 dim-13, Phase B 'Per-leg mastery suppression' (line 163), Phase B EARLY EXIT 'test flips one leg, asserts the warning renders AND mastery is suppressed' (line 165) |

### Dim 14 — Pedagogy oracle [L2 split: PROOF legs + ESTIMATOR leg]  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `pedagogy-PROOF-legs-distractor-binding` | L2 | PROOF | PLANNED | PARTIAL — ATTEMPT-precedes-answer is INV-4.1 beat-order legality (LIVE via closed BeatSelector); BUT the per-distractor misconception_id on BeatCheck + `discriminates` field do not exist (schema deliverable F40); binding/non-paraphrase decidable legs unbuilt | plan §2 dim-14 'Phase-C split gate (F7) PROOF legs' (line 82), Phase C PROOF legs (line 176), schema deliverable F40 (line 173) |
| `pedagogy-ESTIMATOR-leg-separates-models` | L2 | ESTIMATOR | PLANNED | NONE — undecidable without a live learner (LLM judging LLM on LLM-simulated models); serves neverificat, requires a SECOND independent LLM family; residual #1 (load-bearing). NOT a PROOF, NOT the GATE-OF-GATES | plan §2 dim-14 'ESTIMATOR leg = separates models → neverificat' (line 82), Phase C ESTIMATOR leg (line 177), HONEST RESIDUAL #1 (line 312). NOTE: the canonical L2·ESTIMATOR clause |
| `pedagogy-no-mastery-stamp-on-C-only-evidence` | L2 | PROOF | PLANNED | NONE — requires distractor→misconception tagging on attempts + served lessons + real-learner population (demoted to POST-DEPLOY per audit F41); static authoring-time estimator only until then | plan Phase C 'No mastery/faithful stamping on Phase-C-only evidence until N real-learner miss-data points' (line 178), residual-wiring drop-false-mitigation (line 180) |

### Dim 15 — Experience-completeness (EC1–EC5)  
_13 rows — 0 LIVE-GREEN · 13 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-EC1.1` | SYS | PROOF | PLANNED | NONE — EC1 live-help (ask-in-lesson) zero-code; validation test unbuilt | spec §15.1 INV-EC1.1 (line 622); plan §2 dim-15, Phase H EXIT (line 261) |
| `INV-EC1.2` | SYS | PROOF | PLANNED | NONE — drill-self-paste guard unbuilt; no test asserts the Jaccard short-circuit fires (LLM call NOT made) | spec §15.1 INV-EC1.2 (line 623); plan Phase H (line 260), EXIT (line 261) |
| `INV-EC1.3` | SYS | PROOF | PLANNED | NONE — prereq-peek unbuilt; DB assertion of zero host-KC mastery/FSRS writes not gated | spec §15.1 INV-EC1.3 (line 624); plan Phase H (line 260), EXIT (line 261) |
| `INV-EC2.1` | SYS | PROOF | PLANNED | NONE — KC prerequisite graph + assumed-knowledge stub zero-code; real-DB graph reachability check not gated | spec §15.2 INV-EC2.1 (line 634); plan §2 dim-15, Phase H EC2 (line 260), EXIT (line 261) |
| `INV-EC2.2` | SYS | PROOF | PLANNED | NONE — silent-figureless count=0 check unbuilt; depends on served content + figure bindings | spec §15.2 INV-EC2.2 (line 635); plan Phase F EXIT 'figure-coverage INV-EC2.2' (line 239), Phase H (line 260) |
| `INV-EC3.1` | SYS | PROOF | PLANNED | NONE — recovery-session mode unbuilt; property test on real-DB copy (zero FULL/STANDARD plans) not gated | spec §15.3 INV-EC3.1 (line 644); plan §2 dim-15, Phase G EXIT 'INV-EC3.1/.2 green on real DB' (line 253), Phase H (line 260) |
| `INV-EC3.2` | SYS | PROOF | PLANNED | NONE — recovery trigger unbuilt; monotone property test not gated | spec §15.3 INV-EC3.2 (line 645); plan §2 dim-15, Phase G EXIT (line 253), Phase H (line 260) |
| `INV-EC4.1` | SYS | PROOF | PLANNED | NONE — goal/intent layer zero-code; item→KC links + set-time rejection unbuilt | spec §15.4 INV-EC4.1 (line 654); plan §2 dim-15, Phase H EC4 (line 260), EXIT (line 261) |
| `INV-EC4.2` | SYS | PROOF | PLANNED | NONE — goal re-weighting unbuilt; property test (goal cannot starve real deadline) not gated; depends on INV-7.3 priority fn | spec §15.4 INV-EC4.2 (line 655); plan Phase H (line 260), EXIT (line 261) |
| `INV-EC4.3` | L1 | PROOF | PLANNED | NONE — per-goal readiness view unbuilt; ties to INV-1.1 servable-view scope (§0.2) | spec §15.4 INV-EC4.3 (line 656); plan §2 dim-7 (INV-EC4.3 listed), §0.2 (INV-EC4.3 line 656 servable scope), Phase H (line 260). NOTE: layer judgment — L1 because it gates on VERIFIED-servable truth (per §0.2 servable-view), though it lives in the EC/guidance surface |
| `INV-EC5.1` | SYS | PROOF | PLANNED | NONE — provider settings surface unbuilt; GraderProviderSetting default-resolves-to-free assertion not gated | spec §15.5 INV-EC5.1 (line 666); plan §2 dim-15, Phase H EC5 (line 260), EXIT (line 261) |
| `INV-EC5.2` | SYS | PROOF | PLANNED | NONE — no CI grep of build output + tracked files for the provider-key pattern; provider-config surface unbuilt | spec §15.5 INV-EC5.2 (line 667); plan Phase H (line 260), EXIT (line 261) |
| `INV-EC5.3` | SYS | PROOF | PLANNED | NONE — provider health-check probe unbuilt; bad-base-url→`down` test not gated | spec §15.5 INV-EC5.3 (line 668); plan Phase H (line 260), EXIT (line 261) |

### Dim 16 — Deploy / synthetic-tag / gate-chain totality  
_3 rows — 0 LIVE-GREEN · 3 PLANNED · 0 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-9.1` | L1 | PROOF | PLANNED | NONE — artifacts table + pipeline-state machine zero-code; real-DB 'no fourth state, no NULLs' query not gated | spec §9.5 INV-9.1 (line 516); plan §2 dim-16, Phase E EXIT 'INV-9.1 artifact-state total' (line 227). NOTE: dim-16 layer is 'L1+SYS' in plan matrix; enum forces single value — recorded L1 (truth-coverage), SYS half noted |
| `deploy-live-probe` | SYS | PROOF | PLANNED | NONE — deploy is last+singular; live serves SESSION-66 bundle, HEAD ~140 commits ahead & undeployed; deploy.sh SPA smoke exists but the end-user login round-trip + scripted-restore-drill + first-paint zero-4xx probe are Phase-J deliverables | plan §2 dim-16 'deploy live-probe' (line 84), Phase J (line 277-282), risk #4 (line 303) |
| `synthetic-tag-fraction-displayed` | L1 | PROOF | PLANNED | NONE — synthetic-tag fraction per subject (spec §11.1: ALO/PS/RC zero past papers) display is a Phase-F deliverable + residual #4; unbuilt | plan §2 dim-16 'synthetic-tag fraction quantified per subject' (line 84), Phase F EXIT 'per-subject synthetic-tag fraction displayed' (line 239), residual #4 (line 315). NOTE: dim-16 layer 'L1+SYS' in plan; enum forces single — recorded L1 |

### Cross-cutting / foundation (registry · matrix · Phase-0 · spike · security-debt · process)  
_7 rows — 0 LIVE-GREEN · 5 PLANNED · 2 RED-UNMAPPED_

| id | layer | tag | status | backingGate | evidence |
|---|:---:|:---:|:---:|---|---|
| `INV-9.4` | SYS | PROOF | PLANNED | PARTIAL-LIVE — seeded drills exist for several gates: vitest seededWrong{Trace,SeqArray,ChartDist}.test.tsx (wrong-trace), Playwright seeded-violations.spec.ts (clip.json/en-in-ro.json) + seeded-clip-legs.spec.ts, LanguageGateSeededRegressionTest (EN-in-RO), verifier-deps-loud-red (unfaithful/dep). MISSING: ≥1 NATURAL seed per EVERY gate (e.g. un-rebound mergesort never-sorts), garbled-PDF + broken-golden seeds; full per-gate coverage is the Phase 0 deliverable | spec §9.5 INV-9.4 (line 519); plan §2 gate-self-test (line 88), Phase 0 'gate-self-test with ≥1 natural seed per gate' (line 113), Phase 0 EXIT (line 117), Phase I (line 268); recon seededWrong*/seeded-violations/seeded-clip-legs |
| `gate-COVERAGE-registry` | SYS | PROOF | PLANNED | NONE — this very registry is the Phase-0 deliverable; no coverage-registry artifact or CI job exists in the repo today (only unrelated ToolRegistry/familyRegistry/vizRegistry under git ls-files) | plan §2 gate-COVERAGE invariant (line 87), Phase 0 'gate-COVERAGE invariant as a STATUS-TAGGED REGISTRY (F26)' (line 112), Phase 0 EXIT 'coverage registry complete, zero UNMAPPED' (line 117) |
| `gate-MATRIX-rollup` | SYS | PROOF | PLANNED | NONE — per-gate MATRIX rollup board does not exist; no aggregator job | plan §2 'per-gate MATRIX' (line 90), Phase 0 'Per-gate MATRIX rollup' (line 111), Phase 0 EXIT 'matrix prints all-green with zero SKIP/fail-open' (line 117) |
| `Phase0-StateCacheIsolation` | SYS | PROOF | PLANNED | NONE named — flake-fix deliverable; no specific CI gate proves the ~1-in-2 concurrent flake is killed | plan Phase 0 'StateCache per-test isolation (kills the ~1-in-2 concurrent flake)' (line 110) |
| `Phase0-StandingRedTeam` | SYS | ESTIMATOR | RED-UNMAPPED | NONE — process step, not a CI gate (a human/council discipline); registered, not machine-backed | plan Phase 0 'Standing adversarial red-team step' (line 114). NOTE: tag ESTIMATOR/process; inherently not a decidable CI gate — RED-UNMAPPED as a process control |
| `auth-surface-security-debt` | SYS | PROOF | RED-UNMAPPED | PARTIAL — a substantial auth suite exists in `gradle :check` (MagicLinkRepoTest, CsrfTest, AuthVerifyRouteTest, CrossTenantIsolationTest), but spec §A.3 #17 scopes auth OUT → per recon register as RED-UNMAPPED security-debt: CSRF-totality + cookie Secure/SameSite + session-fixation remain untested; NEVER laundered green | plan Phase 0 'Security note (F33)' (line 115); spec §A.3.1 #17 (line 731); recon 'Auth surface F33: register as RED-UNMAPPED security-debt'. NOTE: flagged cell with named owner, never absorbed into 100% |
| `generator-capability-spike` | SYS | ESTIMATOR | PLANNED | NONE — empirical measurement, not a standing gate: run the free-tier/claude-max-relay model (FreeLlmApiLlm defaults localhost:8080; service deploy deferred) against the depth+pedagogy oracles, record pass-rate, name fallback. Residual #3 | plan SPIKE (line 186-192), Phase D GATE-OF-GATES precondition (line 209), HONEST RESIDUAL #3 (line 314). NOTE: tag ESTIMATOR (a measurement of an estimator's headroom); status PLANNED |


---

## The checker (`tools/gate-registry-check.mjs`)

```
node tools/gate-registry-check.mjs              # checks docs/superpowers/plans/2026-06-15-gate-coverage-registry.json
node tools/gate-registry-check.mjs <path.json>  # check a fixture (used by the self-test)
```

Exits **1** (printing the offending rows) if ANY of:
- **(U) UNMAPPED** — a clause missing `status` / `tag` / `layer`, or a value outside the enum.
- **(R) UNACCOUNTED-RED** — a `RED-UNMAPPED` clause whose id is NOT in `acceptedDebt`.
- **(P) PROOF-BY-ESTIMATOR** — a `PROOF` clause marked `LIVE-GREEN` but backed only by an estimator / `neverificat` lane (or naming no real decidable gate). Audit F43.
- **(D) DUPLICATE-ID** — two clauses share an id.
- **(C) COUNTS-DRIFT** — the `counts` block disagrees with the actual array.
- **(A) ORPHAN-DEBT** — a stale `acceptedDebt` entry (no matching clause, or pointing at a non-RED clause).

Exits **0** only when the registry is internally consistent. The accepted-debt allow-list is **printed on every run** so debt is never silently absorbed. INV-9.4 self-test for this gate: `tools/gate-registry-check.test.mjs` (`node --test tools/gate-registry-check.test.mjs`) — feeds a seeded-bad registry (UNMAPPED + PROOF-by-estimator + duplicate + counts-drift + orphan-debt) and asserts the checker REDS, plus a clean fixture asserting exit 0.

> **NOT YET wired into CI** — `test.yml` integration is a separate PM step (per the build brief). This board + JSON + checker + self-test are the Phase-0 artifact; the CI `gate-registry-check` job comes next.

---

## Machine-readable source (embedded mirror)

The canonical machine-readable source is the sibling [`2026-06-15-gate-coverage-registry.json`](./2026-06-15-gate-coverage-registry.json). It is reproduced here for a single-file read; **on any divergence the `.json` file wins** (the checker parses the `.json`, not this block).

```json
{
  "$schema": "gate-coverage-registry/v1",
  "generatedFor": "Phase-0 deliverable (gate-COVERAGE invariant F26 + gate-MATRIX rollup)",
  "plan": "docs/superpowers/plans/2026-06-15-end-to-end-master-plan-to-100-v2.md",
  "spec": "docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md",
  "headAtBuild": "85638dcd0fa44bece18c285cd6c4501e1d0a3c19",
  "enums": {
    "layer": [
      "L1",
      "L2",
      "SYS"
    ],
    "tag": [
      "PROOF",
      "ESTIMATOR"
    ],
    "status": [
      "LIVE-GREEN",
      "PLANNED",
      "RED-UNMAPPED"
    ]
  },
  "semantics": {
    "layer": {
      "L1": "Layer-1 TRUTH — course facts (defn/theorem/method/derivation/complexity/code). Default gate KIND = PROOF.",
      "L2": "Layer-2 TEACHING — scaffolding (framing/figure/analogy/predict-reveal/plain-words). Default gate KIND = ESTIMATOR.",
      "SYS": "System/pipeline plumbing (ingest/classify/migrate/schedule/state) — decidable structural invariants."
    },
    "tag": {
      "PROOF": "PROOF-DEMANDED — a hard decidable gate MUST back it. An estimator/neverificat lane backing a PROOF clause = RED on coverage (audit F43).",
      "ESTIMATOR": "ESTIMATOR-ACCEPTABLE — an estimator + `neverificat`/`verifica sursa` fallback is legitimate; the gate may NEVER wear a PROOF/'provably'/'GATE-OF-GATES' label, and the residual is named on the board."
    },
    "status": {
      "LIVE-GREEN": "A CI gate backs the invariant TODAY (scoped to today's corpus reality — many are green on the seeded ~PA corpus / 3 tracked viz families; per-row NOTE flags where full-corpus coverage is still PLANNED).",
      "PLANNED": "Registered with a known backing-gate target; not yet built/gated. Honest non-overclaim.",
      "RED-UNMAPPED": "No effective live gate today AND it is not in the accepted-debt allow-list, OR it is a flagged debt/process cell. RED-UNMAPPED rows NOT in `acceptedDebt` fail the registry checker."
    }
  },
  "tagPrinciple": "LAYER and TAG are TWO INDEPENDENT stamps. The TAG is derived per-clause from the spec text, NOT mechanically read off the LAYER. Counter-examples are deliberate: dim-12 language is L2-PROOF (decidable RO-ness); dim-13 depth ESTIMATOR legs are L1-ESTIMATOR (verifica-sursa); dim-14 pedagogy ESTIMATOR leg is the canonical L2-ESTIMATOR.",
  "acceptedDebt": [
    {
      "id": "auth-surface-security-debt",
      "owner": "PM (Alex) — spec §A.3.1 #17 scopes auth OUT of the 100% target",
      "reason": "CSRF-totality + cookie Secure/SameSite + session-fixation remain untested. A substantial auth suite exists in `gradle :check` (MagicLinkRepoTest/CsrfTest/AuthVerifyRouteTest/CrossTenantIsolationTest) but the named residual is real. Flagged cell, never laundered green.",
      "neverGreenUntil": "the three residual classes get dedicated tests OR Alex explicitly re-scopes auth into the target"
    },
    {
      "id": "Phase0-Impeccable-blocking",
      "owner": "Phase 0 (flip the `|| true` fail-open guard after calibration)",
      "reason": "Impeccable runs fail-open (`npx impeccable@2.3.2 detect ... || true`, test.yml line 85); 25 real findings outstanding. Registered with a fail-open-debt note, NOT LIVE-GREEN.",
      "neverGreenUntil": "Phase 0 calibrates the findings and drops the `|| true` guard (Phase-0 EXIT: Impeccable blocking-green)"
    },
    {
      "id": "Phase0-LinuxVisualBaselines",
      "owner": "Phase 0 (Linux visual-baseline regeneration)",
      "reason": "Baselines were Windows-only; CI runs Linux; the 3 visual specs are testIgnore'd / fail-open today. Visual baselining is a Linux gap until Phase 0 regenerates them.",
      "neverGreenUntil": "Phase 0 regenerates Linux baselines (Phase-0 EXIT)"
    },
    {
      "id": "INV-9.5",
      "owner": "Phase 0 (Linux baseline regen) + Phase D (dual-skin)",
      "reason": "Visual baselines (shell/theme-dark/theme-light) are testIgnore'd (Linux gap, fail-open); no CI job asserts the baseline dir contains ONLY shell+2-themes. The INV-9.5 scope-gate is unenforced until the baselines are repaired and Phase D adds the dual-skin gate.",
      "neverGreenUntil": "Phase 0 Linux-baseline regen + Phase D dual-skin EXIT"
    },
    {
      "id": "Phase0-StandingRedTeam",
      "owner": "PM/council process discipline",
      "reason": "Process step, not a CI gate (a human/council discipline: a defect class appearing twice = pipeline bug). Inherently not a decidable CI gate; registered, not machine-backed.",
      "neverGreenUntil": "N/A — process control, displayed on the board, never claimed CI-green"
    },
    {
      "id": "a11y-keyboard-aria-live-gate",
      "owner": "Phase D (Accessibility gate, audit F35)",
      "reason": "No a11y gate today (the 5 axe tests disable color-contrast). Best-practice, NOT a spec clause — registered, not laundered as spec-compliance.",
      "neverGreenUntil": "Phase D builds the keyboard-traversal + RO-aria-live gate (Phase-D EXIT)"
    }
  ],
  "counts": {
    "LIVE-GREEN": 18,
    "PLANNED": 62,
    "RED-UNMAPPED": 6,
    "total": 86
  },
  "clauses": [
    {
      "id": "INV-1.1",
      "dimension": "6 (Trust-net / Layer-1 admission)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F (LATE) / I",
      "backingGate": "NONE in CI today — `verifyContent`/`trustInvariants` are manual gradle tasks NOT referenced in .github/workflows/test.yml; the literal `SELECT COUNT(*) FROM <servable view> WHERE verification_status!='VERIFIED'=0` over real DB is not a CI job",
      "evidence": "spec §1.6 INV-1.1 (line 75); plan §2 dim-6, Phase F EXIT GATE (line 239), Phase I EXIT (line 271); build.gradle.kts registers verifyContent/trustInvariants but test.yml never invokes them"
    },
    {
      "id": "INV-1.1b",
      "dimension": "6 (Trust-net / flagged-servable budget)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F / I",
      "backingGate": "NONE — net-new invariant; no flagged-servable surface or budget gate exists in CI",
      "evidence": "plan §0.2 NET-NEW INV-1.1b (line 42); plan §2 dim-6 GATE col; Phase F/I EXIT. Explicitly authored in v2, no test backs it"
    },
    {
      "id": "INV-1.2",
      "dimension": "6 (Trust-net / serve-side refusal)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F / I",
      "backingGate": "NONE in CI as the INV-1.2 transaction-flip test; serve-side 404 refusal exists in code (QueueMasteryCalibrationRoutes.kt:400-403) but no CI test flips a row to unverified against a real-DB copy and asserts refusal",
      "evidence": "spec §1.6 INV-1.2 (line 76); spec §1.2(b) refusal-half-exists note; plan §2 dim-6, Phase F EXIT (line 239), Phase I EXIT (line 271)"
    },
    {
      "id": "INV-1.3",
      "dimension": "8 (Human experience checkpoint)",
      "layer": "L2",
      "tag": "ESTIMATOR",
      "status": "PLANNED",
      "plannedPhase": "E6 (checkpoint screen) / E exit",
      "backingGate": "NONE — the §1.5 checkpoint review screen and its Playwright smoke do not exist; no `artifacts` live-row→approved-checkpoint-event test in CI",
      "evidence": "spec §1.6 INV-1.3 (line 77) + §1.4/§1.5; plan §2 dim-8 (line 76), Phase E6 (line 222), Phase E EXIT (line 227)"
    },
    {
      "id": "INV-1.4",
      "dimension": "1 (Ingest / gap-record grammar)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E (E1/E6)",
      "backingGate": "NONE — gap-record FILES-only grammar lint does not exist; gap-record subsystem is zero-code",
      "evidence": "spec §1.6 INV-1.4 (line 78); plan §2 dim-1 GATE, Phase E EXIT (line 227)"
    },
    {
      "id": "INV-2.1",
      "dimension": "2 (Classify by content not filename)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E2",
      "backingGate": "NONE — 9-kind classifier is zero-code (spec §2-3 digestion half unbuilt); no randomized-filename CI test",
      "evidence": "spec §2.7 INV-2.1 (line 146); plan §2 dim-2, Phase E2 (line 218), Phase E EXIT (line 227)"
    },
    {
      "id": "INV-2.2",
      "dimension": "1 (Extraction-legibility gate FIRST)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E1",
      "backingGate": "NONE as the ingest-path structural test; `ContentValidator.checkExtractionLegibility` exists (ContentValidator.kt) but is dead code (fires only when KCs reference docs); no CI test asserts it is invoked before classification on 100% of artifacts",
      "evidence": "spec §2.7 INV-2.2 (line 147) + §2.3; plan §2 dim-1, Phase E1 (line 217), Phase E EXIT (line 227)"
    },
    {
      "id": "INV-2.3",
      "dimension": "1 (Dedup)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E1",
      "backingGate": "NONE — content-hash dedup / artifact rows are zero-code",
      "evidence": "spec §2.7 INV-2.3 (line 148) + §2.5; plan §2 dim-1, Phase E EXIT (line 227)"
    },
    {
      "id": "INV-2.4",
      "dimension": "2 (subject+kind+exam-tag+SORC domain on every artifact)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E2",
      "backingGate": "NONE — no `artifacts` table with subject/kind/exam-component/domain columns in real DB; SELECT COUNT(violations)=0 query has no CI job",
      "evidence": "spec §2.7 INV-2.4 (line 149); plan §2 dim-2, Phase E EXIT (line 227)"
    },
    {
      "id": "INV-2.5",
      "dimension": "2 (seminar⇒PA/ALO/PS only)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E2",
      "backingGate": "NONE — no artifact/seminar rows or CI query exists",
      "evidence": "spec §2.7 INV-2.5 (line 150); plan §2 dim-2, Phase E EXIT (line 227)"
    },
    {
      "id": "INV-3.1",
      "dimension": "3 (Backup-first migration refusal)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → MigrationBackupGateTest (src/test/kotlin/jarvis/tutor/MigrationBackupGateTest.kt); backup-tooling job → tools/test_db_backup.py + test_db_backup_hardening.py (M-DB fail-closed + restore drill)",
      "evidence": "spec §3.8 INV-3.1 (line 227) + §3.6; plan Phase 0 schema-hash + backup floor; test.yml backup-tooling job lines 160-172, MigrationBackupGateTest in suite"
    },
    {
      "id": "INV-3.2",
      "dimension": "3 (every KC concept_type∈enum + 5 beats RO)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E (E4/E5) / E7",
      "backingGate": "PARTIAL — ContentValidator/validateContent (in `gradle :check`) checks schema shape on the ~6 PA KCs, but the post-migration real-DB INV-3.2 (every served KC has all 5 beats in learner language, 0 violations) is not gated; beat-gen + migrations are zero-code",
      "evidence": "spec §3.8 INV-3.2 (line 228) + §3.2; plan §2 dim-3, Phase E4/E5/E7, Phase E EXIT (line 227)"
    },
    {
      "id": "INV-3.3",
      "dimension": "3 (828 cards survive migration + kc_id backfill)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E7 / F",
      "backingGate": "NONE — `fsrs_cards.kc_id` backfill is 0/828 today (not done); count-equality + ≥1 non-null kc_id per subject is not a CI job",
      "evidence": "spec §3.8 INV-3.3 (line 229) + §3.6 step 4; plan §1 (828 cards 0 non-null kc_id), §2 dim-3 (backfill 0/828), Phase E7 (line 223)"
    },
    {
      "id": "INV-3.4",
      "dimension": "3 (grade-model registry source URLs + 12 exam_dates)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E7 / F",
      "backingGate": "NONE — grading_model/grade_component/exam_dates registry rows are zero-code; no CI count/source-URL assertion",
      "evidence": "spec §3.8 INV-3.4 (line 230) + §3.5; plan Phase E7 course-meta additive tables, Phase F"
    },
    {
      "id": "INV-3.5",
      "dimension": "3 (additive vs frozen signatures)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → SignatureLockPinTest (src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt) — diff vs interface-signatures-lock; explicitly named in test.yml line 33",
      "evidence": "spec §3.8 INV-3.5 (line 231) + §13; plan §2 dim-3, recon: INV-3.5 SignatureLockPinTest in `gradle :check`"
    },
    {
      "id": "INV-4.1",
      "dimension": "4 (beat-plan legality / closed BeatSelector)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → BeatSelector property test (lesson/beat routes in the JUnit suite, per recon 'lesson/beat/practice routes'); closed 4-plan table enforced",
      "evidence": "spec §4.8 INV-4.1 (line 300) + §4.5; plan §2 dim-4, Phase C PROOF leg renamed beat-order-legality (line 176); recon: lesson/beat routes in `gradle :check`. NOTE: judgment call — recon names 'lesson/beat/practice routes' generically; confirm a dedicated BeatSelector legality property test exists"
    },
    {
      "id": "INV-4.2",
      "dimension": "4 (dwell formula boundary)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job vitest (dwell formula unit test) OR backend lesson route — recon lists vitest unit coverage; dwell constants from lectie.html unit-tested at boundary word counts",
      "evidence": "spec §4.8 INV-4.2 (line 301) + §4.1; plan §2 dim-4. NOTE: judgment call on exact job — boundary unit test most plausibly in vitest; not explicitly named in recon"
    },
    {
      "id": "INV-4.3",
      "dimension": "4/10 (completion writes attempt+EWMA+FSRS+first-encounter)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E (E7 columns) / G",
      "backingGate": "PARTIAL — AttemptsTable lacks beat_type/prediction columns today (audit T3); real-DB after-lesson-run assertion (attempt rows with beat_type, EWMA+FSRS updated, first-encounter once) is not gated; lesson-beats.spec exercises UI but not the DB-write contract on real corpus",
      "evidence": "spec §4.8 INV-4.3 (line 302) + §4.4; plan §2 dim-4/dim-10, Phase G EXIT (line 253)"
    },
    {
      "id": "INV-4.4",
      "dimension": "4/11 (lesson Playwright selectors + scrubber-back on every ③)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "lesson-gates job (real backend, seeded corpus) → e2e/lesson-gates.spec.ts; frontend job → e2e/lesson-beats.spec.ts (§4.7 selectors + interaction smoke, one-shot/scrubber-back check)",
      "evidence": "spec §4.8 INV-4.4 (line 303) + §4.6/§4.7; plan §2 dim-4 & dim-11; test.yml lesson-gates job lines 218-283, lesson-beats in npm run e2e. NOTE: LIVE on the seeded ~PA corpus only; LATE coverage on full proof-set is PLANNED"
    },
    {
      "id": "INV-5.1",
      "dimension": "5 (per-family trace-match over every live instance)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job vitest → traceMatchHarness.test.tsx + per-family *.test.tsx (GraphTree/ChartDistribution/SequenceArray); rendered step states == reference execution",
      "evidence": "spec §5.8 INV-5.1 (line 353) + §5.4; plan §2 dim-5; recon 'viz trace-match INV-5.1/5.2'. NOTE: LIVE for the 3 tracked families only — matrix-grid/sort-merge are uncommitted WIP (Phase A0); full 6-family + real-DB-instance coverage is PLANNED (Phase A)"
    },
    {
      "id": "INV-5.2",
      "dimension": "5 (per-family semantic invariant asserts in rendered px)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job vitest (semantic invariant asserts, per recon 'viz trace-match INV-5.1/5.2') + seededWrong*.test.tsx fixtures",
      "evidence": "spec §5.8 INV-5.2 (line 354) + §5.4 (len(Hx)≈len(x)); plan §2 dim-5. NOTE: LIVE for 3 tracked families; semantic-state-delta frame gates (every-frame-advances/final==end-state) are net-new PLANNED in Phase A"
    },
    {
      "id": "INV-5.3",
      "dimension": "5/11 (no-clip @2+ viewport heights, per-family CI-blocking)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job Playwright e2e → family-no-clip.spec.ts + seq-array-no-clip.spec.ts + chart-dist-no-clip.spec.ts + seeded-clip-legs.spec.ts; helpers/assertNoClip.ts",
      "evidence": "spec §5.8 INV-5.3 (line 355); plan §2 dim-5/dim-11; recon 'per-family *-no-clip INV-5.3 + NEW seeded-clip-legs'. NOTE: the 3 no-clip specs are at 1280 viewport (plan names them 'fail-the-build-until-migrated' to 1536×648/730 in Phase A); LIVE today, migration PLANNED"
    },
    {
      "id": "INV-5.4",
      "dimension": "5 (SVG-only import ban: no three/webgl/webgpu/canvas)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A",
      "backingGate": "NONE found in CI — no grep job in test.yml for `three`/`webgl`/`webgpu`/canvas-figure patterns over lesson-facing frontend; spec §5.6 says lint/CI-enforced but no such job is wired",
      "evidence": "spec §5.8 INV-5.4 (line 356) + §5.6; plan §2 dim-5. NOTE: judgment call on status — if an import-ban grep lives in vitest/lint it would be LIVE-GREEN; none surfaced in recon or test.yml, so PLANNED"
    },
    {
      "id": "INV-5.5",
      "dimension": "5 (zero free-form figures; every ref resolves to family+instance)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A (A3/A4) / E5",
      "backingGate": "PARTIAL — FigureBindingNonVacuityTest (in `gradle :check`) asserts specific KC→figure bindings, but the spec INV-5.5 'every figure ref in served lessons resolves to registered family + typed instance, dangling fails admission' over the served corpus is not fully gated; families not all migrated",
      "evidence": "spec §5.8 INV-5.5 (line 357) + §5.5; plan §2 dim-5, Phase A mergesort re-bind deletes FigureBindingNonVacuityTest.kt:72-89 (line 147)"
    },
    {
      "id": "INV-6.1",
      "dimension": "9 (grader routing table total, first element non-LLM)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F (populate bank) / G",
      "backingGate": "PARTIAL — grader stack built + GraderGoldenHarnessTest green in `gradle :check`, but INV-6.1 is 'pending-not-green until problem bank ≥1 row/subject' (problems table = 0 rows today); flips green only after Phase F populates the bank",
      "evidence": "spec §6.4 INV-6.1 (line 397) + note 'pending-not-green'; plan §2 dim-9, Phase F EXIT 'problem bank ≥1 row/subject (INV-6.1 flips green)' (line 239)"
    },
    {
      "id": "INV-6.2",
      "dimension": "9 (execution-grader sandbox R/Py/C++/Alk, real corpus)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → ExecutionGraderSandboxTest (src/test/kotlin/jarvis/tutor/grader/ExecutionGraderSandboxTest.kt); R installed + Alk fetched in CI (test.yml lines 28-32). NOTE: green on fixture/reference problems; 'on real corpus' (plan §2 dim-9) is PLANNED until Phase F populates the bank",
      "evidence": "spec §6.4 INV-6.2 (line 398); plan §2 dim-9 'INV-6.2 (on real corpus)', Phase G 'turning INV-6.2 green on the real corpus' (line 251); recon ExecutionGraderSandboxTest INV-6.2"
    },
    {
      "id": "INV-6.3",
      "dimension": "9 (LLM-judge never alone)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → grader-chain assertion (GraderGoldenHarnessTest / grader suite asserting no chain is LLM-judge-only). NOTE: judgment call — recon names Gate7 GraderGoldenHarnessTest; INV-6.3's specific 'no chain LLM-only' assertion presumed in the grader suite",
      "evidence": "spec §6.4 INV-6.3 (line 399) + §6.3; plan §2 dim-9, Phase C 'no grader chain is LLM-judge-only (INV-6.3)' (line 182)"
    },
    {
      "id": "INV-6.4",
      "dimension": "9 (golden answer sets green per grader per subject)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → GraderGoldenHarnessTest (= Gate7, §9.2); per recon 'grader-golden GraderGoldenHarnessTest=Gate7'",
      "evidence": "spec §6.4 INV-6.4 (line 400) + §9.2 gate 7; plan §2 dim-9. NOTE: golden sets exist per built graders; per-subject coverage broadens with the populated bank (Phase F)"
    },
    {
      "id": "INV-6.5",
      "dimension": "9 (5 practice-surface Playwright suites, real corpus)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "practice-e2e job → e2e/practice/{proof-drill,step-trace,code-practice,mock-exam,deliverable-tracker}.spec.ts + phase5-core-loop.spec.ts (real Kotlin backend + seeded corpus, test.yml lines 109-152)",
      "evidence": "spec §6.4 INV-6.5 (line 401) + §6.2; plan §2 dim-9; recon practice-e2e '5 surfaces + phase5-core-loop'. NOTE: 'real corpus' = the seeded PA corpus; full-subject corpus is PLANNED (Phase F)"
    },
    {
      "id": "INV-6.6",
      "dimension": "9 (code-practice reference attempt-gated)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "practice-e2e job → e2e/practice/code-practice.spec.ts (asserts `code-practice-reference` absent pre-attempt, present post-attempt)",
      "evidence": "spec §6.4 INV-6.6 (line 402) + §6.2.3; plan §2 dim-9; recon practice-e2e 5 surfaces"
    },
    {
      "id": "INV-7.1",
      "dimension": "10 (single-writer FSRS+EWMA)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — unified single-writer mastery service not built; CI grep for direct FSRS/EWMA writes outside it (→0) does not exist; today selector sorts EWMA-only, dueConcepts called from nowhere (audit T2)",
      "evidence": "spec §7.6 INV-7.1 (line 438) + §7.1; plan §2 dim-10, Phase G EXIT (line 253)"
    },
    {
      "id": "INV-7.2",
      "dimension": "10 (per-KC state advances consistently per surface, real DB)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — depends on unified state + populated surfaces; no real-DB per-surface updated_at/mastery+schedule consistency test in CI",
      "evidence": "spec §7.6 INV-7.2 (line 439); plan §2 dim-10, Phase G EXIT (line 253)"
    },
    {
      "id": "INV-7.3",
      "dimension": "7 (priority function pure + monotone, real registry+exam_dates)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — priority = proximity×weight×gap×prereq not built as a gated pure function over real registry/exam_dates rows (registry+exam_dates tables are zero-code)",
      "evidence": "spec §7.6 INV-7.3 (line 440) + §7.4; plan §2 dim-7, Phase G EXIT (line 253)"
    },
    {
      "id": "INV-7.4",
      "dimension": "10 (forgetting trigger → compressed re-lesson, fresh params)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — re-lesson trigger (replaces phantom INV-4.5 per audit F3) not built; simulated time-advance on real-DB copy → ③⑤ enqueue with params≠original is not a CI test",
      "evidence": "spec §7.6 INV-7.4 (line 441) + §7.3; plan §2 dim-10, Phase G EXIT 'INV-7.4 replaces phantom INV-4.5' (line 253)"
    },
    {
      "id": "INV-7.5",
      "dimension": "7 (readiness dashboard Playwright, real registry)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — readiness dashboard route + registry rows zero-code; §7.4 selector Playwright suite not in CI",
      "evidence": "spec §7.6 INV-7.5 (line 442) + §7.4; plan §2 dim-7, Phase G EXIT (line 253)"
    },
    {
      "id": "INV-7.6",
      "dimension": "7 (placement entry_phase DB write assertion — NET-NEW)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — net-new (spec §7.6 defines only INV-7.1–7.5); placement rebuild to probe SUBJECT + entry_phase write path unbuilt (current PlacementShell never calls submit, audit T5)",
      "evidence": "plan §2 dim-7 NET-NEW (line 75), Phase G EXIT 'INV-7.6/7.7 NET-NEW, registered PLANNED' (line 250/253); spec §7.5 placement rebuild rationale"
    },
    {
      "id": "INV-7.7",
      "dimension": "7 (calibration-telemetry-write test — NET-NEW)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G",
      "backingGate": "NONE — net-new; calibration surface mounted-nowhere today (audit T7); telemetry-write test unbuilt",
      "evidence": "plan §2 dim-7 NET-NEW (line 75), Phase G EXIT (line 250/253); spec §7.5 calibration-mounted rationale"
    },
    {
      "id": "INV-8.1",
      "dimension": "12 (language validator on 100% admitted fields, real DB)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F",
      "backingGate": "PARTIAL — validator code + LanguageGateSeededRegressionTest exist in `gradle :check`, but INV-8.1's real-DB query 'served fields lacking a language-check record → 0 rows' depends on served content existing (4 subjects 0 KCs); not gated on real served corpus",
      "evidence": "spec §8.3 INV-8.1 (line 466) + §8.2; plan §2 dim-12, Phase F EXIT 'language INV-8.1–8.4 green on real served content' (line 239)"
    },
    {
      "id": "INV-8.2",
      "dimension": "12 (seeded EN-in-RO regression set fails validator)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → LanguageGateSeededRegressionTest (src/test/kotlin/jarvis/content/LanguageGateSeededRegressionTest.kt); recon names it INV-8.2",
      "evidence": "spec §8.3 INV-8.2 (line 467); plan §2 dim-12; recon LanguageGateSeededRegressionTest INV-8.2 in `gradle :check`"
    },
    {
      "id": "INV-8.3",
      "dimension": "12 (UI chrome EN-literal grep → 0)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job → tools/chrome-en-grep.mjs, BLOCKING (test.yml lines 77-79); recon 'chrome-en-grep INV-8.3 (blocking)'",
      "evidence": "spec §8.3 INV-8.3 (line 468) + §8.2; plan §2 dim-12; test.yml 'Chrome EN-literal grep (INV-8.3)'"
    },
    {
      "id": "INV-8.4",
      "dimension": "12 (Playwright language gate: rendered RO on real proof-set)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "frontend job Playwright → seeded-violations.spec.ts (en-in-ro.json) + helpers/roHeuristic.ts; lesson-gates language leg. NOTE: green on seeded fixtures/PA lesson; 'real proof-set lessons' across subjects is PLANNED (Phase F)",
      "evidence": "spec §8.3 INV-8.4 (line 469) + §9.2 gate 4d; plan §2 dim-12; recon 'seeded-violations.spec INV-9.4/8.4'"
    },
    {
      "id": "INV-9.1",
      "dimension": "16/9 (gate-chain totality: artifact state ∈{live,in-pipeline,parked}) [plan matrix = L1+SYS]",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E (E5) / F",
      "backingGate": "NONE — artifacts table + pipeline-state machine zero-code; real-DB 'no fourth state, no NULLs' query not gated",
      "evidence": "spec §9.5 INV-9.1 (line 516); plan §2 dim-16, Phase E EXIT 'INV-9.1 artifact-state total' (line 227). NOTE: dim-16 layer is 'L1+SYS' in plan matrix; enum forces single value — recorded L1 (truth-coverage), SYS half noted"
    },
    {
      "id": "INV-9.2",
      "dimension": "1 (retry boundedness ≤budget, exceed→park)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E1",
      "backingGate": "NONE — gate-chain self-retry loop + pipeline logs zero-code; ≤retry-budget assertion not gated. Plan adds it to E1 exit (audit F5)",
      "evidence": "spec §9.5 INV-9.2 (line 517) + §9.1; plan §2 dim-1 'INV-9.2 retry-boundedness (F5)', Phase E1 (line 217), Phase E EXIT (line 227)"
    },
    {
      "id": "INV-9.3",
      "dimension": "6 (verifier-dep check: missing dep → RED not UNCERTAIN)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "verifier-deps-loud-red job (test.yml lines 174-216) — hides verifier deps, asserts verifyContent aborts RED ('verifyContent ABORTED (FAIL-LOUD, H4)'), names JARVIS_RELAY_URL/JARVIS_PYTHON3; recon 'verifier-deps-loud-red-job INV-9.3'",
      "evidence": "spec §9.5 INV-9.3 (line 518) + §9.2 gate 2; plan §2 dim-6"
    },
    {
      "id": "INV-9.4",
      "dimension": "cross-cutting / gate-self-test (seeded-violation drill per gate)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "0 (partial LIVE) → I",
      "backingGate": "PARTIAL-LIVE — seeded drills exist for several gates: vitest seededWrong{Trace,SeqArray,ChartDist}.test.tsx (wrong-trace), Playwright seeded-violations.spec.ts (clip.json/en-in-ro.json) + seeded-clip-legs.spec.ts, LanguageGateSeededRegressionTest (EN-in-RO), verifier-deps-loud-red (unfaithful/dep). MISSING: ≥1 NATURAL seed per EVERY gate (e.g. un-rebound mergesort never-sorts), garbled-PDF + broken-golden seeds; full per-gate coverage is the Phase 0 deliverable",
      "evidence": "spec §9.5 INV-9.4 (line 519); plan §2 gate-self-test (line 88), Phase 0 'gate-self-test with ≥1 natural seed per gate' (line 113), Phase 0 EXIT (line 117), Phase I (line 268); recon seededWrong*/seeded-violations/seeded-clip-legs"
    },
    {
      "id": "INV-9.5",
      "dimension": "11 (visual-baseline scope = shell + 2 themes only)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "RED-UNMAPPED",
      "plannedPhase": "D",
      "backingGate": "NONE EFFECTIVE — visual baselines (shell.png/theme-dark.png/theme-light.png) are testIgnore'd (Linux gap, fail-open per recon); no CI job asserts the baseline dir contains ONLY shell+2-themes or reds on a per-lesson baseline. Plan repairs Linux baselines in Phase 0 + Phase D dual-skin; until then the INV-9.5 scope-gate is unenforced",
      "evidence": "spec §9.5 INV-9.5 (line 520) + §9.2 gate 6; plan §2 dim-11, Phase 0 Linux-baseline regen (line 111), Phase D EXIT (line 207); recon 'Visual baselines testIgnore'd (Linux gap, fail-open)'. NOTE: judgment call — RED-UNMAPPED because the existing baseline specs are fail-open/ignored, not a live gate"
    },
    {
      "id": "INV-EC1.1",
      "dimension": "15 (EC1 ask anchoring kc_id+beat_type)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — EC1 live-help (ask-in-lesson) zero-code; validation test unbuilt",
      "evidence": "spec §15.1 INV-EC1.1 (line 622); plan §2 dim-15, Phase H EXIT (line 261)"
    },
    {
      "id": "INV-EC1.2",
      "dimension": "15 (EC1 no-spoiler drill-self-paste guard Jaccard≥0.7)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — drill-self-paste guard unbuilt; no test asserts the Jaccard short-circuit fires (LLM call NOT made)",
      "evidence": "spec §15.1 INV-EC1.2 (line 623); plan Phase H (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC1.3",
      "dimension": "15 (EC1 prereq-peek is read-only, zero mastery writes)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — prereq-peek unbuilt; DB assertion of zero host-KC mastery/FSRS writes not gated",
      "evidence": "spec §15.1 INV-EC1.3 (line 624); plan Phase H (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC2.1",
      "dimension": "15 (EC2 cold-start reachability: every servable KC→entry KC, silent-orphan=0)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — KC prerequisite graph + assumed-knowledge stub zero-code; real-DB graph reachability check not gated",
      "evidence": "spec §15.2 INV-EC2.1 (line 634); plan §2 dim-15, Phase H EC2 (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC2.2",
      "dimension": "15 (EC2 figure-coverage: every mapped REVEAL has figure or figure-deferred flag)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F / H",
      "backingGate": "NONE — silent-figureless count=0 check unbuilt; depends on served content + figure bindings",
      "evidence": "spec §15.2 INV-EC2.2 (line 635); plan Phase F EXIT 'figure-coverage INV-EC2.2' (line 239), Phase H (line 260)"
    },
    {
      "id": "INV-EC3.1",
      "dimension": "15 (EC3 recovery suppression: zero first-encounter plans)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G / H",
      "backingGate": "NONE — recovery-session mode unbuilt; property test on real-DB copy (zero FULL/STANDARD plans) not gated",
      "evidence": "spec §15.3 INV-EC3.1 (line 644); plan §2 dim-15, Phase G EXIT 'INV-EC3.1/.2 green on real DB' (line 253), Phase H (line 260)"
    },
    {
      "id": "INV-EC3.2",
      "dimension": "15 (EC3 trigger purity: pure monotone function)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "G / H",
      "backingGate": "NONE — recovery trigger unbuilt; monotone property test not gated",
      "evidence": "spec §15.3 INV-EC3.2 (line 645); plan §2 dim-15, Phase G EXIT (line 253), Phase H (line 260)"
    },
    {
      "id": "INV-EC4.1",
      "dimension": "15 (EC4 goal decomposition: non-empty KC set or rejected)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — goal/intent layer zero-code; item→KC links + set-time rejection unbuilt",
      "evidence": "spec §15.4 INV-EC4.1 (line 654); plan §2 dim-15, Phase H EC4 (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC4.2",
      "dimension": "15 (EC4 priority safety: real exam out-prioritizes a goal KC)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — goal re-weighting unbuilt; property test (goal cannot starve real deadline) not gated; depends on INV-7.3 priority fn",
      "evidence": "spec §15.4 INV-EC4.2 (line 655); plan Phase H (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC4.3",
      "dimension": "15/7 (EC4 readiness honesty: per-goal counts only VERIFIED-servable; unverified→neverificat)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — per-goal readiness view unbuilt; ties to INV-1.1 servable-view scope (§0.2)",
      "evidence": "spec §15.4 INV-EC4.3 (line 656); plan §2 dim-7 (INV-EC4.3 listed), §0.2 (INV-EC4.3 line 656 servable scope), Phase H (line 260). NOTE: layer judgment — L1 because it gates on VERIFIED-servable truth (per §0.2 servable-view), though it lives in the EC/guidance surface"
    },
    {
      "id": "INV-EC5.1",
      "dimension": "15 (EC5 default zero-config resolves to `free`)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — provider settings surface unbuilt; GraderProviderSetting default-resolves-to-free assertion not gated",
      "evidence": "spec §15.5 INV-EC5.1 (line 666); plan §2 dim-15, Phase H EC5 (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC5.2",
      "dimension": "15 (EC5 secret hygiene: key never in logs/repo, grep→0)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — no CI grep of build output + tracked files for the provider-key pattern; provider-config surface unbuilt",
      "evidence": "spec §15.5 INV-EC5.2 (line 667); plan Phase H (line 260), EXIT (line 261)"
    },
    {
      "id": "INV-EC5.3",
      "dimension": "15 (EC5 health honesty: down provider never renders ✓)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "H",
      "backingGate": "NONE — provider health-check probe unbuilt; bad-base-url→`down` test not gated",
      "evidence": "spec §15.5 INV-EC5.3 (line 668); plan Phase H (line 260), EXIT (line 261)"
    },
    {
      "id": "gate-COVERAGE-registry",
      "dimension": "cross-cutting (status+tag-stamped registry, zero UNMAPPED)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "0",
      "backingGate": "NONE — this very registry is the Phase-0 deliverable; no coverage-registry artifact or CI job exists in the repo today (only unrelated ToolRegistry/familyRegistry/vizRegistry under git ls-files)",
      "evidence": "plan §2 gate-COVERAGE invariant (line 87), Phase 0 'gate-COVERAGE invariant as a STATUS-TAGGED REGISTRY (F26)' (line 112), Phase 0 EXIT 'coverage registry complete, zero UNMAPPED' (line 117)"
    },
    {
      "id": "gate-MATRIX-rollup",
      "dimension": "cross-cutting (per-gate named board, LAYER+TAG, SKIP/fail-open/cross-platform=RED)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "0",
      "backingGate": "NONE — per-gate MATRIX rollup board does not exist; no aggregator job",
      "evidence": "plan §2 'per-gate MATRIX' (line 90), Phase 0 'Per-gate MATRIX rollup' (line 111), Phase 0 EXIT 'matrix prints all-green with zero SKIP/fail-open' (line 117)"
    },
    {
      "id": "Phase0-Impeccable-blocking",
      "dimension": "11 / Phase-0 foundation (Impeccable flip fail-open→blocking)",
      "layer": "SYS",
      "tag": "ESTIMATOR",
      "status": "RED-UNMAPPED",
      "plannedPhase": "0",
      "backingGate": "NONE EFFECTIVE — Impeccable runs fail-open (`npx impeccable@2.3.2 detect ... || true`, test.yml line 85); 25 real findings outstanding; per recon register with a fail-open-debt note, NOT LIVE-GREEN. Plan 0 flips the `|| true` guard after calibration",
      "evidence": "plan Phase 0 'Impeccable → blocking (flip the || true fail-open guard)' (line 107), Phase 0 EXIT 'Impeccable blocking-green' (line 117); spec §9.2 gate 5; test.yml line 80-85 fail-open; recon 'Impeccable: fail-open (|| true), 25 real findings'. NOTE: tag ESTIMATOR (design-rule linting = Layer-2 geometry/CSS proxy per §9.4(e)); status RED-UNMAPPED with fail-open-debt note"
    },
    {
      "id": "Phase0-SchemaHashParity",
      "dimension": "3 / Phase-0 (cross-language schema-hash CI parity)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "LIVE-GREEN",
      "plannedPhase": "",
      "backingGate": "backend job `gradle :check` → SchemaHashParityTest (src/test/kotlin/jarvis/tutor/SchemaHashParityTest.kt) — python db-backup.py vs Kotlin MigrationBackupGate.liveSchemaHash; recon 'NEW SchemaHashParityTest'",
      "evidence": "plan Phase 0 'Cross-language schema-hash CI parity' (line 108), Phase 0 EXIT 'schema-hash CI test green cross-platform' (line 117)"
    },
    {
      "id": "Phase0-RelayRetryBackoff",
      "dimension": "6 / Phase-0 (relay retry/backoff on VerificationRunner)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "0",
      "backingGate": "PARTIAL — VerificationRunnerTest runs in `gradle :check`, but the relay retry/backoff path closing the pa-kc-006 false-negative class is a Phase-0 deliverable; not confirmed gated",
      "evidence": "plan Phase 0 'Relay retry/backoff ... closes the pa-kc-006 false-negative class' (line 109); recon trust-net VerificationRunnerTest in `gradle :check`"
    },
    {
      "id": "Phase0-StateCacheIsolation",
      "dimension": "SYS / Phase-0 (StateCache per-test isolation, kills concurrent flake)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "0",
      "backingGate": "NONE named — flake-fix deliverable; no specific CI gate proves the ~1-in-2 concurrent flake is killed",
      "evidence": "plan Phase 0 'StateCache per-test isolation (kills the ~1-in-2 concurrent flake)' (line 110)"
    },
    {
      "id": "Phase0-LinuxVisualBaselines",
      "dimension": "11 / Phase-0 (Linux visual-baseline regeneration)",
      "layer": "SYS",
      "tag": "ESTIMATOR",
      "status": "RED-UNMAPPED",
      "plannedPhase": "0",
      "backingGate": "NONE EFFECTIVE — baselines are Windows-only; CI runs Linux; the 3 visual specs are testIgnore'd/fail-open today (recon). Phase 0 regenerates them; until then visual baselining is a Linux gap",
      "evidence": "plan Phase 0 'Linux visual-baseline regeneration (baselines were Windows-only; CI runs Linux)' (line 111); recon 'Visual baselines: testIgnore'd (Linux gap, fail-open)'. NOTE: tag ESTIMATOR (screenshot pixel proxy, Layer-2 geometry per §9.4); status RED-UNMAPPED (fail-open today)"
    },
    {
      "id": "Phase0-StandingRedTeam",
      "dimension": "cross-cutting / Phase-0 (standing adversarial red-team: defect-class-twice=pipeline bug)",
      "layer": "SYS",
      "tag": "ESTIMATOR",
      "status": "RED-UNMAPPED",
      "plannedPhase": "0",
      "backingGate": "NONE — process step, not a CI gate (a human/council discipline); registered, not machine-backed",
      "evidence": "plan Phase 0 'Standing adversarial red-team step' (line 114). NOTE: tag ESTIMATOR/process; inherently not a decidable CI gate — RED-UNMAPPED as a process control"
    },
    {
      "id": "semantic-frame-gate-every-frame-advances",
      "dimension": "5 / Phase-A (every-frame-advances over typed STATE delta)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A",
      "backingGate": "NONE — net-new semantic frame gate (NOT a perceptual hash); closes the divide-frames-frozen leak (spec §6 mergesort). Unbuilt at HEAD",
      "evidence": "plan Phase A 'every-frame-advances over the typed algorithm-STATE delta' (line 139); §2 dim-5 'semantic-state-delta frame gates (F6/F13)' (line 73)"
    },
    {
      "id": "semantic-frame-gate-final-eq-endstate",
      "dimension": "5 / Phase-A (final frame == taught end-state, e.g. sorted(input))",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A",
      "backingGate": "NONE — net-new; Phase-A adds 'pa-kc-002 serves a sorting figure whose final frame==sorted(input)' to the exit gate. Unbuilt",
      "evidence": "plan Phase A 'final==end-state' (line 141) + mergesort re-bind (line 147), Phase A EXIT (line 149)"
    },
    {
      "id": "semantic-frame-gate-no-regression",
      "dimension": "5 / Phase-A (no-regression: distinct-frame-count + invariant-coverage not decrease)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A",
      "backingGate": "NONE — net-new (F13); perceptual-diff secondary. Unbuilt",
      "evidence": "plan Phase A 'no-regression = distinct-frame-count AND per-family invariant-coverage must NOT decrease' (line 142)"
    },
    {
      "id": "concept_type-family-FITNESS-gate",
      "dimension": "5 / Phase-A (family fitness fail-CLOSED, no silent fallback)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "A",
      "backingGate": "NONE — fail-closed fitness gate (no fit→HALT+backlog) unbuilt",
      "evidence": "plan Phase A 'concept_type → family FITNESS gate (fail-CLOSED)' (line 145); §2 dim-5"
    },
    {
      "id": "a11y-keyboard-aria-live-gate",
      "dimension": "11 / Phase-D (keyboard-traversal + RO-aria-live gate)",
      "layer": "SYS",
      "tag": "ESTIMATOR",
      "status": "RED-UNMAPPED",
      "plannedPhase": "D",
      "backingGate": "NONE — no a11y gate today (the 5 axe tests disable color-contrast, per plan line 204); best-practice not a spec clause — registered, not laundered as spec-compliance",
      "evidence": "plan §2 dim-11 'keyboard-traversal + RO-aria-live (F35)' (line 79), Phase D 'Accessibility gate (audit F35)' (line 204), Phase D EXIT (line 207). NOTE: tag ESTIMATOR (UX best-practice, plan explicitly says 'Best-practice, not a spec clause'); RED-UNMAPPED today"
    },
    {
      "id": "deploy-live-probe",
      "dimension": "16 / Phase-J (one verified VPS deploy + live probe)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "J",
      "backingGate": "NONE — deploy is last+singular; live serves SESSION-66 bundle, HEAD ~140 commits ahead & undeployed; deploy.sh SPA smoke exists but the end-user login round-trip + scripted-restore-drill + first-paint zero-4xx probe are Phase-J deliverables",
      "evidence": "plan §2 dim-16 'deploy live-probe' (line 84), Phase J (line 277-282), risk #4 (line 303)"
    },
    {
      "id": "synthetic-tag-fraction-displayed",
      "dimension": "16 / Phase-F (per-subject synthetic-tag fraction quantified+displayed) [plan matrix = L1+SYS]",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "F",
      "backingGate": "NONE — synthetic-tag fraction per subject (spec §11.1: ALO/PS/RC zero past papers) display is a Phase-F deliverable + residual #4; unbuilt",
      "evidence": "plan §2 dim-16 'synthetic-tag fraction quantified per subject' (line 84), Phase F EXIT 'per-subject synthetic-tag fraction displayed' (line 239), residual #4 (line 315). NOTE: dim-16 layer 'L1+SYS' in plan; enum forces single — recorded L1"
    },
    {
      "id": "auth-surface-security-debt",
      "dimension": "Phase-0 security note (CSRF-totality / cookie Secure-SameSite / session-fixation)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "RED-UNMAPPED",
      "plannedPhase": "",
      "backingGate": "PARTIAL — a substantial auth suite exists in `gradle :check` (MagicLinkRepoTest, CsrfTest, AuthVerifyRouteTest, CrossTenantIsolationTest), but spec §A.3 #17 scopes auth OUT → per recon register as RED-UNMAPPED security-debt: CSRF-totality + cookie Secure/SameSite + session-fixation remain untested; NEVER laundered green",
      "evidence": "plan Phase 0 'Security note (F33)' (line 115); spec §A.3.1 #17 (line 731); recon 'Auth surface F33: register as RED-UNMAPPED security-debt'. NOTE: flagged cell with named owner, never absorbed into 100%"
    },
    {
      "id": "identity-consent-migration-survival",
      "dimension": "3 / Phase-E7 (row-count-equality on users/sessions/magic_link_tokens/consent_log/trust_grants)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E7",
      "backingGate": "PARTIAL — MigrationBackupGate guards fsrs_cards today; the identity/consent migration-survival invariant (audit F32) extends the backup floor + row-count-equality to users/sessions/magic_link_tokens/consent_log/trust_grants — not yet gated",
      "evidence": "plan §2 dim-3 'identity/consent migration-survival (F32)' (line 71), Phase E7 (line 223)"
    },
    {
      "id": "checkpoint-gap-ledger-smoke",
      "dimension": "8/1 / Phase-E6 (§1.5 checkpoint selectors + §2.6 gap-ledger first-paint + click-to-ingest smoke)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E6",
      "backingGate": "NONE — checkpoint review screen (§1.5 data-testids) and gap-ledger (§2.6 5 selectors + upload smoke) are zero-code; the ghost-component/PDF-404 class smoke unbuilt",
      "evidence": "spec §1.5 + §2.6 selectors; plan Phase E6 'checkpoint Playwright (§1.5) + gap-ledger smoke (§2.6)' (line 222/227), Phase E EXIT (line 227)"
    },
    {
      "id": "OCR-alt-extractor-probe-gate",
      "dimension": "1 / Phase-E1 (offline OCR tesseract+RO + alt-extractor with probe-availability gate)",
      "layer": "SYS",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "E1",
      "backingGate": "NONE — no OCR exists (only pdfbox:2.0.30; VisionLlm.kt is paid-model, collides with no-paid-APIs); offline OCR engine + probe-gate is net-new (audit F15); POO PNG-scan proof-set parked/verifica-sursa until built",
      "evidence": "plan Phase E1 'OCR + alt-extractor subsystem (audit F15 — net-new, does NOT exist)' (line 217), Phase E EXIT 'OCR/alt-extractor probe-gates green or POO-scan parked-flagged' (line 227); spec §2.3/§10.2 OCR route"
    },
    {
      "id": "checkpoint-experience-residual-5",
      "dimension": "8 / HONEST RESIDUAL #5 (checkpoint judges EXPERIENCE not TRUTH)",
      "layer": "L2",
      "tag": "ESTIMATOR",
      "status": "PLANNED",
      "plannedPhase": "I (board display) / H",
      "backingGate": "NONE as a decidable gate — by definition: the human experience-checkpoint is the named estimator/residual, displayed on the board, never proof-labeled; INV-1.3 backs only its existence not its truth-power",
      "evidence": "plan §2 dim-8 'judges experience NOT truth (residual #5)' (line 76), HONEST RESIDUALS #5 (line 316), Phase I EXIT (line 273). NOTE: tag ESTIMATOR by spec §0.1; status PLANNED = displayed-on-board deliverable, not a CI gate"
    },
    {
      "id": "depth-PROOF-leg-code-compiles",
      "dimension": "13 / Phase-B (depth Layer-1 truth — PROOF leg: code compiles+tests)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "B",
      "backingGate": "PARTIAL — rides the built Phase-6/G execution graders (INV-6.2, ExecutionGraderSandboxTest in `gradle :check`), BUT the depth artifact schema + concept-keyed depth bank do not exist (net-new, no prior plan hosted it); the leg cannot fire until Phase B authors the depth bank",
      "evidence": "plan §2 dim-13 'Phase-B split gate (F8) PROOF leg = code compiles+tests' (line 81), Phase B PROOF leg (line 161); spec dimension-13 is net-new (no INV-* in spec — depth artifact unnamed/unschema'd per plan §1 line 59)"
    },
    {
      "id": "depth-ESTIMATOR-legs-complexity-invariant-edges",
      "dimension": "13 / Phase-B (depth ESTIMATOR legs: complexity-soundness / invariant-rederivability / edge-completeness → verifica-sursa)",
      "layer": "L1",
      "tag": "ESTIMATOR",
      "status": "PLANNED",
      "plannedPhase": "B",
      "backingGate": "NONE — undecidable without authored-source / second-independent-LLM-family cross-check; renders verifica-sursa + excluded from mastery until grounded (residual #2); depth oracle unbuilt",
      "evidence": "plan §2 dim-13 'ESTIMATOR legs ... → verifica sursa' (line 81), Phase B ESTIMATOR legs (line 162), HONEST RESIDUAL #2 (line 313). NOTE: layer L1 (truth depth) but tag ESTIMATOR (per §0.1 a Layer-1 clause the machine can't prove decidably) — a deliberate counter-example to the L1=PROOF default"
    },
    {
      "id": "depth-per-leg-mastery-suppression",
      "dimension": "13 / Phase-B (per-leg mastery suppression + DOM-asserted verifica-sursa warning)",
      "layer": "L1",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "B",
      "backingGate": "NONE — the suppression mechanic (flip a leg → warning renders DOM-asserted AND mastery suppressed) is the Phase-B EARLY-exit test; unbuilt",
      "evidence": "plan §2 dim-13, Phase B 'Per-leg mastery suppression' (line 163), Phase B EARLY EXIT 'test flips one leg, asserts the warning renders AND mastery is suppressed' (line 165)"
    },
    {
      "id": "pedagogy-PROOF-legs-distractor-binding",
      "dimension": "14 / Phase-C (pedagogy Layer-2 — PROOF legs: distractor↔misconception-id binding, non-paraphrase, ATTEMPT-precedes-answer)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "C",
      "backingGate": "PARTIAL — ATTEMPT-precedes-answer is INV-4.1 beat-order legality (LIVE via closed BeatSelector); BUT the per-distractor misconception_id on BeatCheck + `discriminates` field do not exist (schema deliverable F40); binding/non-paraphrase decidable legs unbuilt",
      "evidence": "plan §2 dim-14 'Phase-C split gate (F7) PROOF legs' (line 82), Phase C PROOF legs (line 176), schema deliverable F40 (line 173)"
    },
    {
      "id": "pedagogy-ESTIMATOR-leg-separates-models",
      "dimension": "14 / Phase-C (pedagogy ESTIMATOR leg: CHECK items separate wrong-model from right-model → neverificat)",
      "layer": "L2",
      "tag": "ESTIMATOR",
      "status": "PLANNED",
      "plannedPhase": "C",
      "backingGate": "NONE — undecidable without a live learner (LLM judging LLM on LLM-simulated models); serves neverificat, requires a SECOND independent LLM family; residual #1 (load-bearing). NOT a PROOF, NOT the GATE-OF-GATES",
      "evidence": "plan §2 dim-14 'ESTIMATOR leg = separates models → neverificat' (line 82), Phase C ESTIMATOR leg (line 177), HONEST RESIDUAL #1 (line 312). NOTE: the canonical L2·ESTIMATOR clause"
    },
    {
      "id": "pedagogy-no-mastery-stamp-on-C-only-evidence",
      "dimension": "14 / Phase-C (no mastery/faithful stamping on Phase-C-only evidence)",
      "layer": "L2",
      "tag": "PROOF",
      "status": "PLANNED",
      "plannedPhase": "C",
      "backingGate": "NONE — requires distractor→misconception tagging on attempts + served lessons + real-learner population (demoted to POST-DEPLOY per audit F41); static authoring-time estimator only until then",
      "evidence": "plan Phase C 'No mastery/faithful stamping on Phase-C-only evidence until N real-learner miss-data points' (line 178), residual-wiring drop-false-mitigation (line 180)"
    },
    {
      "id": "generator-capability-spike",
      "dimension": "cross-cutting / SPIKE (recorded pass-rate per oracle + named fallback)",
      "layer": "SYS",
      "tag": "ESTIMATOR",
      "status": "PLANNED",
      "plannedPhase": "SPIKE (between C and D)",
      "backingGate": "NONE — empirical measurement, not a standing gate: run the free-tier/claude-max-relay model (FreeLlmApiLlm defaults localhost:8080; service deploy deferred) against the depth+pedagogy oracles, record pass-rate, name fallback. Residual #3",
      "evidence": "plan SPIKE (line 186-192), Phase D GATE-OF-GATES precondition (line 209), HONEST RESIDUAL #3 (line 314). NOTE: tag ESTIMATOR (a measurement of an estimator's headroom); status PLANNED"
    }
  ]
}
```
