# One-Pass Digestion Teaching Engine — Binding Design Specification

**Date:** 2026-06-11
**Status:** BINDING — the implementation plan is derived from this document. Where this spec and any earlier plan disagree, this spec wins, except for `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, which remains canonical-on-conflict for already-frozen signatures (see §13). Adversarially reviewed 2026-06-11 (3-agent approval-diff + claim spot-check); this revision incorporates its fixes.
**Audience:** the implementation planner and future sessions.
**Language of this document:** English (jarvis-meta). All learner-facing content the system produces is in the learner's language (§8).

This document contains no open questions. Every section ends with acceptance criteria stated as machine-checkable invariants that run against the real corpus and the real database — never fixtures-only. Exam dates appear in this document only as data the scheduler consumes.

---

## 1. System Overview

### 1.1 The one loop

The tutor is a single pipeline applied identically to every piece of course material:

1. **Upload** — the user drops a file or pastes a URL. Nothing else is asked of them.
2. **Classify** — by content, never by filename, into one of 9 artifact kinds (§2.2), tagged with subject and exam component.
3. **Digest** — the artifact is decomposed into: knowledge components (KCs), per-beat teaching content for each KC, problems with parameter slots, rubrics, grade-model facts, and dates. All generated content is produced in the learner's language (§8).
4. **Verify** — machine-only gates: the trust-net faithful-check (admission gate) plus the semantic and rendered viz gates (§9). No human verifies truth.
5. **Serve** — 5-beat lessons (§4), drills, code practice, and mock exams (§6), delivered through the priority queue (§7.4).
6. **Track** — every learner interaction writes unified mastery + FSRS state (§7.1); exam dates shape queue priority.
7. **Revise** — forgetting triggers a compressed re-lesson with fresh parameter values drawn from the problem archetype's slots (§7.3).

There is exactly one path from raw material to the learner. There are no side doors (no hand-authored lesson bypass, no "just this once" direct DB insert of teaching content). Anything that wants to reach the learner goes through the loop.

### 1.2 Invariants (binding)

**(a) The user never iterates on CORRECTNESS** — never debugs output, never verifies truth, never fixes content; gates pass, retry, or park. A hard failure becomes a **gap-record** that asks the user for **FILES, never judgments** — "upload the seminar sheet for week 6" is legal; "is this proof correct?" is forbidden. This is the no-oracle-inversion rule: the tutor exists because the user cannot vet content; asking him to do so is a design violation, not a fallback. The §1.4 experience checkpoint is the single, deliberate exception: an approve/reject on how it TEACHES, bounded by the §9.1 rejection budget — it is not iteration on correctness.

**(b) Nothing unverified reaches the learner.** The trust-net is admission-only (content must pass faithful-check to enter the servable set) *and* there is an always-on serve-side refusal: the serving layer re-checks verification status at read time and refuses to render unverified content, so a bad migration or a bug that flips a flag cannot leak content. (The refusal half already exists in code — the lesson endpoint requires `faithful` status and 404s otherwise, `QueueMasteryCalibrationRoutes.kt:400-403`; this design keeps it and widens what it covers.) Both halves are extended beyond KC text to **generated teaching content (beat fields) and figures** — a beat or a figure that has not passed its gates is exactly as unservable as an unverified KC. (Decision D-RF2, see §12.)

### 1.3 Existing assets absorbed, not discarded

- **The 828 live cards** are repaired in place. Their audited state: ~89% English (the seeding prompt at `FsrsSeedMain.kt:249-257` carried no Romanian directive — final-audit L1) and 100% `kc_id = NULL` (`FsrsCards.kt:53` — final-audit T1, so the only working surface feeds nothing back). The repair = faithful-checked RO translation (§8) + `fsrs_cards.kc_id` backfill (§3.6), so the user's existing review history counts toward the unified mastery state. This absorbs the "Plan-B" the final audit noted was absent from every plan document.
- **The 24 existing viz components** become **family seeds** (§5.5): their render logic is refactored into the ~6 verified families; their hardcoded content is re-expressed as instance data. (Treat "24" as approximate per the audit's own reconciliation — enumerations ranged across clusters with ~25–27 files in `viz/`; the exact structural fact is that exactly one is registered.)
- **The trust-net turns on as-is** after migration (backup → migrate 3 columns → `verifyContent` over the corpus → D9 PC→VPS sync). It is built and sealed; the design here does not reopen it, it extends its coverage (§9.2 gate 2).

### 1.4 USER AMENDMENT (binding): per-artifact sequential processing with a user acceptance checkpoint

Artifacts are processed **one at a time, sequentially**. For each artifact:

1. All machine gates run to green (or the artifact parks as a gap-record).
2. The pipeline **stops** and presents the rendered result — the actual lesson route, real data, real theme — on the **checkpoint review screen** (§1.5).
3. The user clicks through the lesson exactly as a learner would.
4. The user **approves** or **rejects with a one-line note**.
   - **Reject:** the one-liner is fed back into the generation prompt and generation re-runs for that artifact. The user's note is experience feedback ("too dense", "figure confusing"), never a truth judgment — truth remains machine-verified. Rejection counts are bounded by the unified retry table in §9.1: one re-generation per first rejection; a second rejection on the same artifact routes to design review.
   - **Approve:** the content goes live, and the approved artifact's shape (beat plan, figure choices, density) becomes the **reference pattern** for the next artifact of the same kind × subject, so quality converges instead of resetting.
5. Only then does the next artifact enter the pipeline.

**Division of labor, stated once and binding:** machines verify **truth** (faithfulness to source, trace correctness, render correctness); the user approves **experience** (does this teach well, does it feel right). Neither party does the other's job.

**Checkpoint FORM follows the artifact kind:** teaching kinds present the rendered lesson; analytics-only kinds (grade-ledger) and registry kinds (grading-doc) present the extracted data summary (registry rows, difficulty seeds) for the same approve/reject. Student-notes go through the full lesson checkpoint.

**Spot-check mode:** per subject, the user may explicitly choose to relax the checkpoint from every-artifact to sampled spot-checks. This is unlockable **by user choice only** — the system never auto-graduates a subject out of full review, never suggests it as a default, and records who flipped the switch and when.

**Rationale for one-by-one:** bounded blast radius. A prompt regression, a bad extraction, or a family bug poisons at most one artifact before a human sees it, instead of a 140-item batch.

### 1.5 Checkpoint review screen — what the user MUST see

Route: the checkpoint surface mounts inside the existing tutor shell. On first paint, Playwright asserts visible:

- `[data-testid="checkpoint-artifact-header"]` — artifact name, detected kind, subject, exam-component tag.
- `[data-testid="checkpoint-gate-summary"]` — the per-gate green list (extraction, faithful, figure, rendered, language), each gate named.
- `[data-testid="checkpoint-lesson-frame"]` — the embedded real lesson route (not a mock, not a screenshot).
- `[data-testid="checkpoint-approve"]` and `[data-testid="checkpoint-reject"]` — the two actions.
- `[data-testid="checkpoint-reject-note"]` — the one-line note input, enabled only when reject is selected.
- `[data-testid="checkpoint-kind-selector"]` — the detected-kind control allowing one-click re-classification (§2.2).

Interaction smoke: clicking through every beat inside `checkpoint-lesson-frame`, then clicking approve, produces zero 4xx/5xx network responses and no on-screen text matching `/404|HTTP \d{3}|not found|error/i`. Reject with a note re-enqueues the artifact and the screen shows `[data-testid="checkpoint-requeued"]` (first rejection only; a second rejection routes to design review and shows `[data-testid="checkpoint-design-review"]`). Changing the kind via `checkpoint-kind-selector` re-runs classification-downstream stages and the header updates, zero 4xx/5xx.

### 1.6 Acceptance criteria (machine-checkable, real DB)

- **INV-1.1:** For every row in the servable content set (KCs, beat fields, figures, problems), verification status = passed. CI query against the production schema: `SELECT COUNT(*) FROM <servable view> WHERE verification_status != 'VERIFIED'` returns 0. Runs against the real DB, not a fixture.
- **INV-1.2:** Serve-side refusal test: flip one row to unverified in a transaction against a copy of the real DB, request it through the serving API, assert refusal (structured "not available" response, never the content), roll back.
- **INV-1.3:** Every artifact in `artifacts` with state `live` has exactly one `approved` checkpoint event row of the kind-appropriate form (§1.4: lesson form for teaching kinds, data-summary form for grade-ledger/grading-doc) — or its subject is in user-enabled spot-check mode and the artifact has a `sampled-pass` or `auto-after-spot-check-grant` event. Zero artifacts are `live` with no checkpoint event.
- **INV-1.4:** Every gap-record's user-facing request text matches the FILES-only grammar (a lintable template: request names an artifact kind + subject + week/topic; contains no question mark directed at content correctness).

---

## 2. Ingestion & Classification

### 2.1 Inputs

Two input forms, one pipeline: **drop a file** (PDF, HTML, image-scan PDF, source-code archive, plain text) or **paste a URL**. The **140 inventoried course-site resources** ingest through the URL path exactly like a pasted link — there is no separate "bulk import" code path, only a loop over the same single-artifact pipeline (sequential, per §1.4; bulk mode is §10.4). The 140 (all URLs confirmed live 2026-06-11, enumerated in `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md` §4): ALO 25 (13 lecture PDFs + 7 seminar sets + 5 tema PDFs), PS 59 (26 lectures RO+EN, 12 lab sheets, 8 homework specs, 8 data files, 3 fise, 2 grade sources), SORC 18 (6 RC lectures, 10 lab PDFs, new-format lab page, the SO+RC fisa PDF), PA 4 (two fise, BSc plan, course-site hub; plus the Alk interpreter repo), POO 32 (13 course PDFs incl. `cpp_to_rust.pdf`, 19 lab HTML pages), and the IA12 schedule page.

### 2.2 Classification: by CONTENT, never filename, into 9 kinds

The classifier reads extracted text (post §2.3 gate) and assigns one of the nine kinds below. (The taxonomy doc's §1 table enumerates exactly these nine; its prose summary says "eight" — a self-miscount, since its three discovered additions — cheat-sheet, student-notes, grade-ledger — extend the original six-kind input vocabulary to nine. This spec is binding: **nine kinds**.)

1. **lecture** — expository course material; primary KC source.
2. **lab** — practical sheets. *Kind diverges per subject and the classifier must carry that divergence:* for PS/SO/RC a lab is a worked-example sheet (theory blurb → solved example → numbered unsolved exercises; digests to step-trace drills and code practice); for POO a lab is a graded code deliverable (GitHub-submitted implementation spec with banned-API constraints; digests to a deliverable-tracker entry + code-practice prep, §6.2.5). Hard rule from the taxonomy: **worked examples are NOT solutions to the proposed exercises** (RC, PS) — the pipeline never conflates them (R-SOLPRESENT).
3. **seminar** — exercise sheets; exist only for PA/ALO/PS. A "seminar" classification for any other subject is a classifier error and must be rejected to manual-kind selection at the checkpoint, not silently accepted.
4. **homework** — assigned problems with deadlines; feeds problem bank + deliverable tracker.
5. **past-exam** — real exam papers; feeds mock-exam mode with structure, ordering, and permitted-materials metadata.
6. **grading-doc** — grade-model statements (formulas, weights, minimum gates); feeds the grade-model registry (§3.5). Registry entries from this kind carry their source URL.
7. **cheat-sheet** — permitted-materials summaries / formula sheets; feeds proof templates (§6.2.1) and permitted-materials phases of mock exams.
8. **student-notes** — non-professor material; ingestible but provenance-tiered below professor sources (§2.5).
9. **grade-ledger** — raw score rosters (PA partial-test per-sub-question ledger, SO grade overview) and the user's own standing. Analytics input only, never user-facing teaching content: per-sub-question mean/zero-rate seeds initial FSRS difficulty at card creation (R-DIFFCAL, targeting `fsrs_cards` — `FsrsCards.kt:38`); ledger column headers reconstruct rubrics for papers whose stems are missing; the user's own standing feeds the readiness dashboard's points-at-stake arithmetic.

Every classification carries: **subject**, **exam-component tag** (which grade component this material serves), and for SORC items a **SO-vs-RC domain tag** (the engine's `subjects.yaml` treats SO-RC as one subject while the grade model examines two domains — the audit flagged every authoring decision colliding with this; the domain tag is the resolution, R-DOMAINSPLIT). SO additionally splits internally: POSIX-practical material (the lecture corpus on hand) vs the OS-theory MCQ domain (no lecture corpus identified) — items tag which exam component + domain they feed.

Classification confidence below threshold → the artifact still proceeds, but the checkpoint screen (§1.5) shows the detected kind prominently and the user's approve implicitly confirms it; a rejected kind is a one-click re-classify, which is an experience judgment (what is this document) not a content-truth judgment, and is therefore legal under §1.2(a).

### 2.3 Extraction-legibility gate (FIRST gate, before everything)

Garbled extraction is the root failure that poisons every downstream stage, so it is checked before classification. The existing `ContentValidator.checkExtractionLegibility` (`ContentValidator.kt:167`) is **promoted from dead code to the front gate** — the final audit (C3) confirmed it is built but fires only when KCs reference docs, and four subjects have zero KCs, so it is dead in practice. The garble it must catch, measured in the current corpus: PS — all 10 prior course extracts are watermark noise, unquotable; ALO — 3/10 courses are slide-title dumps, c01/c02 formulas Unicode-garbled; SO — every diacritic systematically split ("fis, ierele"); POO c3 — OCR corruption interleaving assembly mid-sentence. Gate behavior:

- Pass → continue.
- Garble detected → automatically try the alternate extraction route (different PDF text extractor, then OCR for scan-PDFs).
- Still garbled → **park as a gap-record**: "extraction failed for `<artifact>`; please upload a text-readable version or the original source file." (FILES, not judgments.)

### 2.4 Per-kind extraction

Each kind has its own extraction recipe per the artifact-type taxonomy (`docs/superpowers/findings/2026-06-11-artifact-type-taxonomy.md` §1): lectures → sectioned text + figures + worked examples + pre-baked predict prompts where they exist (SO slides carry "Efectul acestei comenzi: ?"); seminar/homework sheets → enumerated problems with sub-parts preserved; past exams → problem structure + point values + permitted-materials text + ordering; grading-docs → formula spans quoted verbatim with location; code labs → file tree + spec text + pseudocode blocks kept intact (homework pseudocode is itself reusable lesson material); cheat-sheets → template frames with blanks identified.

Cross-cutting extraction obligations (taxonomy §3, the R-* register — full mapping in Appendix A.2):
- **archetype + parameter slots** on recurring problem families (R-ARCHETYPE — PS homework recurs year-to-year with parameter swaps);
- **rubric rows as first-class data** (R-RUBRIC: G1..Gn, AP/WP tags, printed point brackets, all-or-nothing flags, global penalty rules such as SO's score-halving);
- **`solution_present` on every problem item** with the worked-example ≠ exercise-solution rule and answer-key splitting (PA "cu-rasp" papers split into stems + answer-key content) (R-SOLPRESENT);
- **data-file dependencies** as a first-class field (R-DATAFILES — e.g. PS lab4's `unemploy2012.csv`; bundle or flag);
- **prerequisite cross-refs** ("din cursul N", lab-demo back-references) extracted as KC-graph edges (R-PREREQ);
- **multi-select items** ("bifați TOATE") flagged for the multi-select drill variant (R-MULTISELECT);
- **"greșeli frecvente" / error lists** mined as misconception corpora feeding distractors + scorer checklists (R-ERRORS).

### 2.5 Dedup and provenance

- **Content-hash dedup:** the same bytes (or same normalized extracted text) uploaded twice is recognized; re-upload of an already-live artifact is a no-op with a visible "already digested" notice, never a duplicate digest. Dedup also performs canonical-path resolution for known duplicate locations (PA seminar sheets exist in both `labs/` and `_fii/_gdrive/`) and survives off-by-one file naming (RC lab numbering vs PDF titles) (R-DEDUP).
- **Provenance tiers:** `professor` > `student-notes` > `tutor-generated`. The tier feeds trust-net strictness (§9.2 gate 2): professor material is the grounding source; student notes are admitted with stricter faithful-checking and can never override a professor source on conflict; tutor-generated content (pedagogical instances, fresh parameter values) is exempt from verbatim-quote checks but bound by method-consistency and family trace-match (§3.3).

### 2.6 Corpus-gap ledger — what the user MUST see

Gap-records are not log lines; they are **visible upload-request cards in-app**, mounted on the main shell where the queue lives. On first paint of the surface that hosts them, Playwright asserts:

- `[data-testid="gap-ledger"]` — the ledger container (visible whenever ≥1 open gap exists).
- `[data-testid="gap-card"]` — at least one card when open gaps exist, each containing:
  - `[data-testid="gap-card-request"]` — the FILES-only request text (subject + kind + topic/week).
  - `[data-testid="gap-card-reason"]` — the machine diagnostic in one line ("extraction garbled twice", "no past papers exist for ALO").
  - `[data-testid="gap-card-upload"]` — a direct upload affordance that feeds §2.1.

Interaction smoke: clicking `gap-card-upload` and submitting a file routes into the standard ingest pipeline with the gap pre-linked; zero 4xx/5xx; resolving a gap removes the card without page reload errors.

### 2.7 Acceptance criteria

- **INV-2.1:** Classifier never reads filenames: CI test feeds the real proof-set artifacts (§10.2) with randomized/garbage filenames and asserts identical kind assignment.
- **INV-2.2:** `checkExtractionLegibility` is invoked on the ingest path for 100% of artifacts: CI asserts the ingest service's pipeline graph includes the gate before classification (structural test on the pipeline definition), and an intentionally garbled PDF from the real corpus parks as a gap-record, never reaches digestion.
- **INV-2.3:** Dedup: ingesting the same real artifact twice yields one artifact row; second ingest returns the existing ID.
- **INV-2.4:** Every artifact row in the real DB has non-null subject, kind, exam-component tag; every SORC row has a SO/RC domain tag. `SELECT COUNT(*)` of violations = 0.
- **INV-2.5:** Seminar kind only ever co-occurs with subjects PA/ALO/PS in the real DB (0 rows otherwise).

---

## 3. Knowledge Model & Schema

### 3.1 The KC stays the atom; it gains `concept_type`

The knowledge component remains the unit of mastery, scheduling, and verification. New field: **`concept_type`**, enum:

`procedure` · `proof` · `definition-taxonomy` · `code-trace` · `timing` · `probabilistic` · `comparison` · `formula-application`

`concept_type` drives two things: **beat-variant selection** in the BeatSelector (§4.2–4.3) and **viz-family selection** (§5.2). It is assigned at digestion and is verifiable: the faithful-check prompt includes the type and the verifier flags type/content mismatch (a proof typed as `procedure` fails admission).

### 3.2 Per-beat teaching fields, LANGUAGE-KEYED

Each KC carries generated teaching content for the 5 beats, stored language-keyed (§3.7). This closes the audit's schema blockers head-on: today `ContentSchema.kt:38-102` has **no field for any beat** (A1/A2/E2/E5 — no predict prompt/options/callback, no attempt sub-task, reveal is the single prose blob `worked_example_ro` at `ContentSchema.kt:101`, `definition_ro` is hardcoded null at the lesson endpoint). Field classes per beat:

- **PREDICT (①):** prompt; 3–4 options; per-option **callback text** (echoed at reveal: "you predicted X — here is where that holds/breaks").
- **ATTEMPT (②):** sub-task statement; choices (or input schema for the numerical variant); **both-path feedback** (text for correct AND for each wrong path — the wrong-path text teaches, never just "incorrect"). Numerical variant stores **skeleton rows + trace steps** (the per-click formula-line data of §4.3).
- **REVEAL (③):** ordered step list; per-step callouts (the explanation FUSED to the step, never a detached paragraph); **figure binding** — a reference to a viz-family instance (family ID + typed instance data, §5.3).
- **NAME (④):** the formal definition; the invariant statement; a "why this matters / where it breaks" callout.
- **CHECK (⑤):** a different-instance item (same concept, different surface values) with grading data.

### 3.3 `pedagogical_instance` — a defined field class

Generated examples (fresh numbers for a trace, a new tiny graph for a lesson) cannot be verbatim-grounded in the source — the source doesn't contain them. They are a distinct field class with distinct verification:

- **Exempt** from the verbatim-quote requirement of the faithful-check.
- **Verified for method-consistency:** the verifier checks the instance is solved by the same method the source teaches (same algorithm, same formula form, same sign conventions).
- **Must pass family trace-match** (§5.4): the instance's reference execution is computed and every rendered step is asserted against it.

This is the formal answer to "how can generated content ever be admitted" — it can, under a different, equally machine-checked contract. It resolves audit A5 (the verbatim-grounding vs invented-instance tension the schema left structurally unresolved: beat ① requires concrete instances lectures don't contain, while the NLI faithful-check would refute invented numerics) and audit-limitation #8 (trust-net × beat-content interaction unspecified). Verification re-runs on any edit to a teaching field — staleness is a re-admission event, not a grandfather clause.

### 3.4 Problem bank — separate from KCs

Problems are first-class rows, not KC appendages:

- **archetype** — the problem family ("Dijkstra on weighted digraph, find shortest path + predecessor table").
- **parameter slots** — the values that can be re-rolled to produce a fresh instance (graph weights, array contents, λ for a Poisson). Re-lessons (§7.3) and drills draw fresh values from these slots.
- **solution_present** (bool) — whether the source contains a worked solution. Problems without one are still usable for practice but their grading leans on the structural graders, and mock-exam items built from them are synthetic-tagged (§6.2.4).
- **rubric rows** — `G1..Gn` items with per-item points; `AP/WP` (answer-points vs working-points) classification; all-or-nothing flags; penalty rules (e.g. wrong sign penalized once, not per-line). Mirrors real grading-doc structure.
- **exam-language constraint** — the language/dialect the exam demands for this problem class (R-LANG registry): Alk for PA (corpus-evidence tier, §3.5 — present in every past paper, absent from the fise), R for PS, C++ with banned-STL/banned-function constraints for POO, bash/POSIX C for SO. Code practice for the problem is constrained to it (§6.2.3, §8.1).
- **data-file dependencies** — CSV/txt files the problem names (R-DATAFILES); bundled with the problem or flagged as a gap.
- **KC links** — many-to-many; a problem can exercise several KCs; mastery writes fan out across links. Prerequisite edges extracted per R-PREREQ live on the KC graph and gate availability (§7.4).

### 3.5 Course-meta

- **Grade-model registry** (R-GRADEMODEL): one verified row per subject per component — formula, weights, minimum gates, **evidence tier** (`official-site` vs `corpus-evidence`), and **source URL** — seeded exclusively from the 2026-06-11 verified sweep (`docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md`), **never from the uncorrected taxonomy** (the sweep carries 15 errata against it; additionally, the taxonomy's §4 dashboard row for POO — 30/30/30/18, summing to 108 — is itself wrong and superseded by the official-site model here; treat it as an unrecorded erratum). The verified anchors:
  - **ALO:** `Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris` (verbatim from the course page); max total = **800** (the taxonomy's 1000 is CONTRADICTED — erratum E1); pass = exam ≥ 3/10 AND total ≥ 360; Gaussian curve over passers; temas T1–T5 = 50/60/60/60/70 pts with early-submission bonus.
  - **SORC:** `NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2`; pass = RC2 ≥ 5 AND NF ≥ 5; RC2 retakeable. SO1/SO2/SO3 definitions UNKNOWN (SO sub-site HTTP 401, gap G7) → gap-records, not guesses; the corpus-only T.SO claims (week 8, 30 pts, 12-pt gate) carry `corpus-evidence` tier (erratum E9).
  - **POO:** T1 (wk 8) 30 + T2 (wk 14/15) 30 + final 30 + lab activity 10; pass = **total ≥ 45** only. The taxonomy's "≥20 combined lab gate" and "attendance ≥ 10 gate" are NOT STATED on the official site (errata E13/E14) and are **excluded** from the registry.
  - **PA:** pass ≥ 45/100; 2024-25 RO fisa = teste scrise 70% + seminar 30%; the 2025-26 EN fisa restructures to 65% continuous + 35% final written (erratum E10; RO cross-check missing = gap G11 — both shapes stored, flagged unreconciled). "Alk mandatory", "2 × 35 pts", retake format = `corpus-evidence` tier (errata E11/E12).
  - **PS:** Verificare — no sit-down exam, **no re-exam on any component** (failing a gate = course failed); live-page contract = seminars 60 (6 × 10-pt 15-min mini-tests) + labs 60 (Teme A–D 20 + class exercises 20 + final-week 20-min statistics test 20); gates = both buckets ≥ 30/60. The fisa's 50/17/17/16 split is unreconciled with the 60+60 bucketing (gap G4): registry stores both, live page = the user-facing contract.

  Registry rows are admitted through the same trust-net (grading-docs are quotable sources). Schema home: `grading_model` + `grade_component` are **new additive tables** — the taxonomy's DB sweep (source-verified 2026-06-11) confirms no current table owns weights/gates/curves; `exam_dates` (`Phase1Tables.kt:104` — id, user_id, subject, start_at) is a schedule pointer only.
- **`exam_dates` table:** seeded with the **12 IA12 schedule rows** (source: `orar_IA12.html`, session 01.06.2026–28.06.2026), including restanță rows; PS has zero rows, consistent with Verificare. The ambiguous "Reverse Engineering" restanță row (22.06) is stored as-is and **not** mapped to RC (gap G13) — RC2 is examined inside the SORC slot. Dates may be exact or vague ("session week"); the scheduler consumes both (§7.4). Dates are data; nothing in the system editorializes about them.
- **Deliverables** absorbed into the existing `tasks` (`Tasks.kt:69` — deadline, `rubric_ref_json`, `grade_json` already give rubric linkage + points-at-stake a slot) / `task_prep` (`TaskPrep.kt:14` — the generated prep bundle: skeleton drills, practice problems) structures: deadline, points-at-stake, sub-problem breakdown, prep-drill links (§6.2.5). `mock_exams` (`MockExamTable.kt:18`) is the existing seat for mock-exam sessions, gaining the §6.2.4 semantics additively.
- **Per-subject EN↔RO glossary:** term-pair rows built automatically where the lecture language differs from the exam or learner language (§8.1); referenced by generation prompts and by the language validator's exemption list (an EN technical term present in the glossary does not fail the RO language gate).

### 3.6 Migrations (ordered, backup-first — locked decision)

1. **BACKUP FIRST.** The live DB (`~/.jarvis/tutor.db`, 828 cards, irreplaceable user history) gets an **off-box dump before any mutation**. This is a locked decision (§12); a migration script that does not begin by producing and verifying the backup is non-compliant. The backup tooling itself is hardened as part of this step: the final audit (limitation #12) found `tools/db-backup.py` has no flag parsing — `--help` executed a live backup into a directory named `--help`. A restore drill (backup → restore to scratch → row-count + schema-hash equality) is part of step 1, not optional.
2. `kc_verification_status` **+3 columns** — the live DB is 3 columns behind the sealed trust-net code; this migration closes that gap and is the prerequisite for turning the trust-net on (§9.2 gate 2).
3. `report_wrong` gains `resolved_by` + `resolved_at` NOW while the table is empty (council D-RF3, build-review/2026-06-05-trustnet-refreeze-decisions.md — provenance is un-backfillable once rows exist).
4. **`fsrs_cards.kc_id` backfill** — links the 828 cards to KCs so existing review history feeds unified mastery (§7.1). Cards that cannot be confidently linked stay null-linked and keep scheduling independently; no forced guesses.
5. **Beat fields** — the §3.2 per-beat, language-keyed columns/tables, additive only.
6. **Problem bank + course-meta tables** — §3.4/§3.5, additive only.

### 3.7 Language keying

- `learner_language` is a **per-user setting** (current sole user: `ro`).
- Teaching content is stored **per language version**; each version is **separately faithful-checked** (a verified EN beat says nothing about its RO sibling).
- **RO-only generation now.** The schema is keyed and ready for more languages; the pipeline generates exactly one version today. No speculative multi-language generation work is in scope.

### 3.8 Acceptance criteria

- **INV-3.1:** Backup invariant: the migration runner refuses to run any mutating step unless a same-day off-box dump exists and its integrity check passes (row-count + schema hash match). CI tests the refusal path.
- **INV-3.2:** Post-migration, against the real DB: every KC has a `concept_type` in the enum; every served KC has all 5 beats' minimum fields in the learner's language; 0 rows violate.
- **INV-3.3:** All 828 pre-existing cards still exist post-migration (count equality with the backup) and ≥1 card has a non-null `kc_id` per subject that has KCs; no card lost its review history.
- **INV-3.4:** Every grade-model registry row has a non-empty source URL; every `exam_dates` row traces to the verified sweep doc; counts match the sweep (12 schedule rows).
- **INV-3.5:** All new tables/columns are additive: CI diff against the interface-signatures-lock shows no modified or removed frozen signature (§13).

---

## 4. Lesson Engine

### 4.1 BeatOrchestrator (client)

A React component that **replaces** the current 3-step `LessonScreen`. The replaced surface's audited state (E1–E4, E12): a read-echo-recall with `Step = 'entry'|'term'|'retrieval'|'done'` (`LessonScreen.tsx:31`), predict served as hardcoded `emptyList()` (`QueueMasteryCalibrationRoutes.kt:419`) so the gate silently bypasses, no attempt, no reveal, the learner's prediction discarded as a dead `_option` prop, and **zero completion writes**. The BeatOrchestrator consumes a `beats[]` array from the lesson payload and ports the demo's gating state machine (`build-review/viz-fork-demo/lectie.html` — vanilla JS+D3, so this is a rewrite-port, not an extraction, per audit E11):

- **Next is disabled** until the beat's gate clears: predict committed (①), animation watched to final step at least once (③), check answered (⑤), attempt submitted (②).
- **Pips** show beat progress (k of N), each pip labeled with its beat glyph.
- **Predict-callback echo:** at reveal, the learner's own prediction is echoed with the stored per-option callback text (§3.2).
- **Reading-time dwell** on text-bearing beats: minimum dwell in ms = `min(5500, max(1400, 900 + words × 320))` (the demo's constants, `lectie.html:185-186`) — the Next gate also waits on this, so a wall of text cannot be skipped in 200 ms. Dwell is a floor, never a ceiling: all reveals are learner-paced via the scrubber (§4.6), which is the mitigation for the audit's P4 concern (auto-advance too fast for formula-heavy Romanian).

### 4.2 BeatSelector (server)

Server-side selection of the beat plan: **`concept_type` × mastery phase → beat plan**. The beat **vocabulary is FIXED** (locked, council-1781052957): the five beats ① PREDICT ② ATTEMPT ③ REVEAL ④ NAME ⑤ CHECK; a legal plan contains at minimum ① ② ③ ⑤ for FIRST ENCOUNTERS — compressed plans only per the §4.5 closed table — beats are **never reordered**, and no sixth beat kind may be invented by a prompt. The selector chooses *variants* and *compression*, not new vocabulary.

### 4.3 Beat variants per `concept_type`

- **procedure / formula-application (numerical):** ②③ = **skeleton trace** (locked, council-1781097621): one formula-line revealed per click; the skeleton (the table/recurrence structure) is **visually dominant** over prose; the sign/decision step gets its own **named row**; geometry appears only as a **1-screen anchor before the arithmetic**, never interleaved with it. Skeleton row count is **instance data, not a hardcoded 4** — ALO's conjugate-gradient trace carries 7 named per-iteration quantities and must fit (audit `alo-multiiteration-exceeds-4-row-skeleton`).
- **proof:** ② = structured sub-step attempt (the learner supplies the next named sub-step from choices); ③ = proof-skeleton reveal (named sub-steps fill in one per click).
- **definition-taxonomy:** ① = **classify-an-example-first** predict (instance before definition); ③ reveals the taxonomy with the predicted example placed in it.
- **code-trace:** ① = predict-the-output; ③ = line-by-line execution stepper reveal (state table advances per click, current line highlighted).
- **timing / concurrency:** ① = predict "can this break?"; ③ = **contrast reveal** — one clean interleaving stepped through, then one that breaks, same scenario.
- **probabilistic:** numerical skeleton-trace variant with the distribution/sample-space figure as the 1-screen anchor.
- **comparison:** ① = predict which of two named things applies to an instance; ③ = side-by-side stepped contrast.

These variants are the design answer to the audit's three template-fit blockers: **P-PROOF** (proofs had no beat mapping — the structured sub-step attempt + skeleton reveal is the mapping), **P-DEF** ("nothing to predict before you have vocabulary" broke ① for definition-heavy material in all six subjects — the classify-an-example-first variant predicts on a concrete instance, not on vocabulary), and **P-TIMING** (no single "first step" exists over all interleavings — ② asks "can this break?", ③ contrasts a clean interleaving with a breaking one). The audit's template-fit verdict ("demonstrated only for 3 algorithm-trace types, under a third of the real corpus" — P2) is closed by this table plus the §10.2 proof set, which deliberately spans the previously-untested types.

### 4.4 Everything is graded; everything writes

Every beat with learner input is graded (predict correctness, attempt path, check correctness). Lesson completion writes: **attempt rows carrying `beat_type`** (additive columns — `AttemptsTable` at `Phase1Tables.kt:34-51` currently has no beat_type/prediction columns, audit T3), **EWMA mastery update**, **FSRS scheduling**, and the **first-encounter flag** on first contact with a KC. This closes audit E4 (today completion writes nothing — "a learner can finish 100 lessons and the engine re-serves the same KCs at intro forever"). The lesson→drill handoff is wired: the `/oggi` **Începe** button transitions from a completed lesson into the drill queue for the same KCs (audit E10: `OggiScreen.tsx:49-52` is a no-op with the comment "Future: navigate to drill" — this spec makes the handoff a contract).

### 4.5 Mastery-conditional compression

Revisits of mastered KCs skip the predict gate (① becomes optional/elided per the selector); the compressed re-lesson trigger (§7.3) serves ③+⑤ only, with fresh parameter values. Compression is the selector's decision from mastery state — never a user-facing "skip" button that defeats the gating.

The legal beat plans are a CLOSED set:

| Plan | Beats | Legal when |
|---|---|---|
| FULL | ①②③④⑤ | any encounter |
| STANDARD | ①②③⑤ | the minimum for first encounters |
| MASTERED-REVISIT | ②③⑤ | revisit of a mastered KC (predict elided) |
| RE-LESSON | ③⑤ | forgetting trigger (§7.3) only, never a first encounter |

No other plan is legal; the BeatSelector chooses among these four.

### 4.6 Scrubber requirement (machine-checked)

Every animated reveal ships the **full scrubber**: back / play / forward / reset, with a "pas k/N" step counter. A one-shot animation (plays once, no way back) is a **gate violation** caught by the rendered interaction test (§9.2 gate 4), not a style preference. The canonical violation this kills: the Dijkstra demo's beat-③ one-shot ~17 s run whose play button is destroyed on completion (`lectie-dijkstra.html:188` — rendered-audit DIJ-ONESHOT: replay impossible, revisits show a dead figure). The MergeSort demo's scrubber (back/play/forward/restart + `pas N/9`) is the reference implementation.

### 4.7 Lesson route — what the user MUST see

On first paint of the lesson route with a real verified KC:

- `[data-testid="lesson-beat-pips"]` — the pip strip, N pips matching the payload's beat count.
- `[data-testid="lesson-beat-active"]` — exactly one active beat container.
- `[data-testid="beat-predict-options"]` — on a ① beat: 3–4 options rendered.
- `[data-testid="beat-next-gate"]` — the Next control, asserted `disabled` before the beat gate clears and enabled after.
- `[data-testid="beat-figure-scrubber"]` — on any ③ beat with a figure: back/play/forward/reset controls and `[data-testid="scrubber-step-counter"]` showing "pas k/N".
- `[data-testid="lesson-complete-handoff"]` — on the completion screen: the drill handoff control.

Interaction smoke: complete all beats by clicking; zero 4xx/5xx during the whole traversal; zero console errors; `beat-next-gate` is verifiably disabled before each gate condition and enabled after; clicking `lesson-complete-handoff` lands in the drill surface with the same KC visible.

### 4.8 Acceptance criteria

- **INV-4.1:** Beat-plan legality: CI property test over the BeatSelector for every (`concept_type` × mastery phase) pair asserts: output is exactly one of the four plans in the §4.5 closed table (FULL / STANDARD / MASTERED-REVISIT / RE-LESSON), first encounters receive only FULL or STANDARD, order strictly increasing.
- **INV-4.2:** Dwell formula unit-tested at boundary word counts (0, 2, 14, 1000 words → 1400, 1400+ , …, 5500 capped).
- **INV-4.3:** Against the real DB after a real lesson run-through: attempt rows exist with `beat_type` populated for each graded beat; EWMA and FSRS rows updated; first-encounter flag set exactly once per KC.
- **INV-4.4:** Playwright suite (§4.7 selectors + interaction smoke) runs on the real lesson route against the real corpus in CI; the one-shot-animation check asserts the scrubber's back control exists and functions on every animated ③.

---

## 5. Viz-Family Architecture (resolves G4)

### 5.1 Principle

**Render logic is verified once per FAMILY; the pipeline supplies typed DATA only; agents never draw free-form.** The historical failure mode — every new figure is bespoke code that must be individually reviewed and individually breaks — dies here. A figure in a lesson is `(family_id, instance_data)`, nothing else.

### 5.2 The families (~6)

1. **graph/tree** — nodes, edges, traversals, spanning/search trees, heaps-as-trees (d3-hierarchy layouts).
2. **sequence/array** — arrays, lists, pointer walks, sorting states, sliding windows.
3. **matrix/grid** — DP tables, adjacency matrices, grids/boards, memory layouts.
4. **chart/distribution** — distributions, histograms, function curves, convergence plots.
5. **timeline/protocol** — process/thread timelines, network protocol exchanges, scheduling Gantt strips.
6. **state-machine/code-trace** — automata, code execution state (variables table + highlighted line), Markov chains.

A family may gain sub-layouts; a *new family* is a design-level event requiring its own verification harness before first use.

### 5.3 Family contract (every family implements all of this)

- **Typed slot schema:** nodes / edges / cell values / labels / **step-deltas** (what changes at step k) / **callout-per-step** (the fused explanation text anchored to an element).
- **Deterministic layout that measures its own labels:** layout computes label bounding boxes and reserves space — **no-clip by construction**, not by post-hoc screenshot checking.
- **Callouts anchored to elements:** the explanation is visually fused to the node/cell/edge it explains. A detached footer paragraph is a contract violation (audit V5: all 24 current primitives use a detached 9pt footer band; the shell's renderFrame contract doesn't support anchoring — the family contract does). Callout text references elements by **anchor, never by hardcoded color name** — rendered-audit QR-WARM-GALBEN ("galben" labeling a coral arrow under the warm theme) is the bug class this kills; captions are instance data, language-keyed and theme-agnostic.
- **Step/event model plugged into the `AlgoStepperShell` scrubber** (§4.6) — families don't implement their own playback.

### 5.4 Verification per family

- **Trace-match harness:** for each instance, a **reference execution** is computed from the instance data (run the actual algorithm); every rendered step's state is asserted equal to the reference state. A figure that animates something the algorithm doesn't do cannot pass.
- **Semantic invariant asserts:** per-family domain invariants checked **in rendered pixels/coordinates**, e.g. the reflection family asserts `len(Hx) ≈ len(x)` in rendered px. The motivating defect (rendered-audit QR-GEO-1, the audit's single worst pedagogical finding): the QR demo's beat-1 reflection rendered x=[1,2,2] at ~60 px and Hx=[−3,0,0] at ~142 px — a visible **2.39× stretch** in an animation whose stated teaching point was length preservation ("reflexia pastreaza lungimea"). A lint-passing, legible, plausible figure that contradicts its own invariant is exactly the class only this gate catches. Council-1781132987 (First Principles + judge, concurring): the lint/baseline gates cover geometry/CSS only — **this architecture is the named semantic-correctness mechanism**, and the G4 fork is resolved in its favor.

### 5.5 Migration of the 24 existing primitives

The 24 existing viz components (~24, exact count reconciled at migration — see §1.3 note) are **seeds**: their rendering ideas are refactored into the families; their hardcoded one-off content is re-expressed as instance data (audit V3: 17 of 24 are zero-prop hardcoded scenarios — BayesTree's P(D)=0.01 disease, SchedulerGantt's fixed 4-job batch, OsiEncap's fixed MTU — making different concrete instances structurally impossible without code changes; this is what instance-data re-expression fixes). Components with **no corpus anchor** (TcpCwnd's full Reno, Tls0Rtt — audit) yield family render logic only; their content cannot become instances because instances are content rows and content requires a source. The per-component registry bottleneck **dies**: today registering one component requires lockstep changes across vizRegistry + viz-ids.yaml + Kotlin `checkVizReferences` + an exact-match test (audit V1 + limitation #14); under families, the family registers once and instances are data. The three independent production breaks (V1: 1 of ~24 registered; V2: zero KCs carry viz_id; E6: no viz field on the lesson wire) are removed by one mechanism: the §3.2 figure binding. End state: zero lesson-facing figures outside the family system.

### 5.6 SVG-only policy (council-approved, locked)

- **BLOCK** WebGL / WebGPU / Three.js / canvas-based figures, and scroll-driven or mouse-reactive decoration, **everywhere** — enforced by lint/CI (import bans + selector audit), not convention.
- **Intrinsically-3D exception:** human-decided case-by-case, hand-authored off-pipeline, screenshot-diff gated, and each instance carries a written justification of why 2D is insufficient. The exception path is expected to be rare-to-never for this corpus.

Three council-verified reasons, each independently sufficient (tooling-eval §2): **machine-verifiability** — every gate operates on the DOM/SVG tree; canvas collapses it to opaque pixels and gates degrade to false-passing screenshot diffs; **pedagogy** — scroll/mouse decoration directly opposes the locked learner-paced sequential form and the ADHD profile; **cost** — Three.js/WebGPU adds ~600 KB of JS the bundle doesn't have. The repo's own data point: the hand-coded 3D Householder lesson (`qr-3d.html`) confused the learner and is replaced by 2D. The policy text is mirrored verbatim into `.claude/active-constraints.md` as a standing constraint.

### 5.7 Stack

SVG figures at **480×360** inside `AlgoStepperShell`, animated with **Framer Motion**, tree/graph layout via **d3-hierarchy**. No additional rendering dependencies without a Decision Log entry.

### 5.8 Acceptance criteria

- **INV-5.1:** Per family, the trace-match harness runs in CI over every live instance in the real DB (not sample fixtures): rendered step states equal reference execution states, 100%.
- **INV-5.2:** Per family, semantic invariant asserts pass on every live instance (family-specific list maintained with the family code).
- **INV-5.3:** No-clip: rendered DOM audit at 2+ viewport heights asserts zero overlapping/clipped label boxes on every live instance (the standing viz no-clip gate, now per-family and CI-blocking).
- **INV-5.4:** Import ban: CI greps the lesson-facing frontend for `three`, `webgl`, `webgpu`, canvas-figure patterns → 0 hits outside the documented 3D-exception directory.
- **INV-5.5:** Zero free-form figures: every figure reference in served lessons resolves to a registered family + typed instance row; a dangling or inline figure fails admission.

---

## 6. Practice Surfaces + Graders

### 6.1 The arc

All practice follows **I-do → we-do → you-do**: the lesson's reveal is I-do; structured/step-trace drills with scaffolding are we-do; free-form code practice and mock exams are you-do. The queue (§7.4) moves a KC along the arc as mastery rises.

### 6.2 Five surfaces

Each surface lists its "what the user MUST see" selectors; all share the interaction-smoke contract: zero 4xx/5xx on paint and on every listed click, no error-text regex match.

**6.2.1 Structured proof drill.** Named sub-steps scored separately (not all-or-nothing on the whole proof). Cheat-sheet proof templates become **fill-in frames**: the template's structure is given, the learner supplies the named pieces.
MUST see: `[data-testid="proof-drill-substeps"]` (the named sub-step list), `[data-testid="proof-drill-frame"]` (the fill-in frame), `[data-testid="proof-drill-substep-score"]` (per-sub-step result after submit).

**6.2.2 Step-trace numeric drill.** Every intermediate state is checked, not just the final answer — a sign error at step 3 is caught at step 3.
MUST see: `[data-testid="trace-drill-skeleton"]` (the dominant skeleton table), `[data-testid="trace-drill-step-input"]` (current step's input), `[data-testid="trace-drill-step-verdict"]` (per-step correctness).

**6.2.3 Code-practice session.** Skeleton-fill or free code **in the exam's language** (§3.4 exam-language constraint): Alk for PA, R for PS, C++ for POO, bash/POSIX for SO. Reference solution is **attempt-gated**: visible only after a graded attempt. This is the corpus's biggest gap — 5 of 6 subjects have an exam code component with no current practice surface.
MUST see: `[data-testid="code-practice-editor"]`, `[data-testid="code-practice-language-badge"]` (the constrained language, visible), `[data-testid="code-practice-run"]`, `[data-testid="code-practice-verdict"]`, and only post-attempt `[data-testid="code-practice-reference"]`.

**6.2.4 Mock-exam mode.** Timed; mirrors the real paper's structure: ordered dependent sub-questions (b uses a's result), **permitted-materials phases** (e.g. ALO's 30-minute docs-allowed phase modeled as a phase switch), rubric-scored via §3.4 rubric rows with a **common-error checklist** surfaced in the result. Where no real past paper exists, items are **synthetic-tagged** and the UI says so (honesty over simulation). Real-paper structures the mode reproduces (taxonomy §2): PA — 3 questions (Greedy/DP/NP) × ordered a–d sub-parts with printed point brackets, 0.5-pt partial credit; POO course exam — MCQ "ce se întâmplă la execuția următorului program?" with the barem's step-by-step execution trace as the reveal; SO theory — 18-item grids mixing 0.5-pt radio and 1-pt "bifați TOATE" checkbox items (the **multi-select variant**, R-MULTISELECT, lives here and in drills); practical coding tests (SO TP, POO T1/T2) — timed **rubric self-assessment** with all-or-nothing sub-items and score-halving penalty rules (the app cannot auto-grade these; the value is format familiarity + rubric awareness). PS has no sit-down exam: its "mock" is a **15-minute mini-test simulation** matching the live-page format — the taxonomy's "Test teoretic, format unknown, ask the user" framing is corrected by sweep erratum E3 (format is known: 6 × 15-min in-seminar mini-tests; no gap-record exists for it).
MUST see: `[data-testid="mock-exam-timer"]`, `[data-testid="mock-exam-phase"]` (current permitted-materials phase), `[data-testid="mock-exam-question"]` with sub-question ordering visible, `[data-testid="mock-exam-rubric-result"]` (per-G-item breakdown post-submit), `[data-testid="mock-exam-synthetic-tag"]` on synthetic papers.

**6.2.5 Deliverable tracker.** Per deliverable: deadline (data), **points-at-stake per sub-problem**, prep drills generated from the spec's pseudocode. The app **prepares** the user for the deliverable; it **never grades real submissions** (the professor does that; pretending otherwise is a trust violation).
MUST see: `[data-testid="deliverable-card"]` with `[data-testid="deliverable-points"]` (per-sub-problem points), `[data-testid="deliverable-prep-drills"]` (linked drills), `[data-testid="deliverable-deadline"]`.

### 6.3 Grader stack — ordered by trust, LLM LAST

1. **Numeric oracle** — SymPy / tolerance comparison; local; free; first choice for anything with a computable answer. (Closes audit E16: `DrillGrader.kt:43-61` has no oracle path and `grader_rules` is never read at runtime — ALO numerics, PS fractions, RC subnetting were all LLM-judged.)
2. **Execution grader** — run the learner's code locally: **R**, **Python**, **C++ via MinGW**, **Alk via the alk-language/java-semantics interpreter** (`github.com/alk-language/java-semantics`, URL verified in the sweep); **bash/POSIX is constrained** (no safe local execution contract) → graded by rubric instead. (Closes audit E17: `GRADE_PROMPT_CODE` at `DrillGrader.kt:68-85` says "You do NOT execute the code" — missing exactly the UB/aliasing/dangling bug classes POO teaches.)
3. **Rubric grader** — structural `G1..Gn` item-by-item scoring against §3.4 rubric rows (presence/shape of required elements, AP/WP split, penalty rules).
4. **LLM-judge** — **prose only**, and **always paired with a structural grader** (rubric or oracle) so the LLM never solely decides a score; prompts carry **per-subject misconception codes** mined from the corpus's "greșeli frecvente" lists (R-ERRORS), so wrong answers map to named misconceptions that feed remediation (§7.3). (Closes audit E18: today's taxonomy is 3 hardcoded statistics codes — L2_ESTIMATOR / MINIMAX / MODE_CONFUSION — so every PA/POO/SO/RC error files as OTHER. The PA flagship case the codes must catch: the flipped reduction direction, "students flip the arrow constantly", which an LLM fluency judge passes.)

The existing drill CHECK card becomes a **real graded input** wired into this stack (audit E8: `DrillStack.tsx:376-391` is read-only text whose "MARK CHECK DONE" fires without engagement).

### 6.4 Acceptance criteria

- **INV-6.1:** Grader routing table is total: CI asserts every (subject × problem-archetype) in the real problem bank resolves to a grader chain whose first element is non-LLM. Meaningful only once the problem bank is non-empty; CI marks it pending-not-green until the proof run populates ≥1 row per subject.
- **INV-6.2:** Execution-grader sandbox test per language against real corpus problems: known-good reference solution → pass; known-bad mutant → fail; for each of R/Python/C++/Alk.
- **INV-6.3:** LLM-judge is never alone: CI asserts no grader chain consists of LLM-judge only.
- **INV-6.4:** Golden answer sets (§9.2 gate 7) green per grader per subject before any grader change merges.
- **INV-6.5:** Playwright suites for all five surfaces (§6.2 selectors + smoke) run against real corpus content.
- **INV-6.6:** Attempt-gating: Playwright asserts `code-practice-reference` is absent from the DOM pre-attempt and present post-attempt.

---

## 7. Tracking & Adaptivity

### 7.1 One per-KC state

FSRS and EWMA unify into **one per-KC learner state** (retention schedule + mastery estimate, stored together, updated together). **All surfaces write it** — lessons (per beat), drills, code practice, mock exams (closing audit T6: today a 0/10 mock exam leaves EWMA and phase untouched). The 828 cards' kc_id linkage (§3.6) means existing review history counts from day one (closing T1). This ends the audited dual-incoherence (T2): the selector currently sorts by EWMA only (`NextKcSelector.kt:95`) while `dueConcepts()` is called from nowhere in the tutor stack (only chat-era `ChatTools.kt:252`), so a forgotten retrieval-phase KC ranks below a brand-new intro KC.

### 7.2 Beat telemetry

Per beat per KC: predict accuracy, attempt accuracy, replay count, give-ups. This feeds the BeatSelector (§4.2) — e.g. consistently failed predicts on a `definition-taxonomy` KC bias the selector toward the classify-first variant with an easier instance.

### 7.3 Two new triggers

- **Forgetting** (FSRS retention drops below threshold) → a **compressed re-LESSON** (③ reveal + ⑤ check) with **fresh parameter values** drawn from the linked problem archetype's slots — re-seeing the identical example teaches recognition, not the skill.
- **Misconception fired** (LLM-judge or rubric grader maps an error to a misconception code) → a **targeted remediation drill** for that code is re-queued ahead of new material for that KC.

### 7.4 Timeline layer

**Queue priority = exam proximity × component weight × readiness gap × prerequisite order**, where: exam proximity comes from `exam_dates` (vague dates use the vague-window midpoint); component weight from the grade-model registry; readiness gap = target mastery minus current per-component mastery; prerequisite order from the KC graph. Deliverable deadlines enter the same scheduler as dated items.

**Readiness dashboard** — per grade component: current readiness vs the component's **minimum gates** with explicit warnings when below. Verified gate values (sweep §1): ALO exam ≥ 3/10 AND total ≥ 360 of 800; SORC RC2 ≥ 5 AND NF ≥ 5; PS seminar bucket ≥ 30/60 AND lab bucket ≥ 30/60 (no re-exam on any PS component — a failed gate is the course failed, and the dashboard states that plainly); PA total ≥ 45/100; POO total ≥ 45/100. The registry, not this paragraph, is canonical at runtime; gates the official sites do not state (POO attendance, per-SO-component gates) are **absent from the registry**, never guessed.

**What the user MUST see** (first paint): `[data-testid="readiness-dashboard"]`; per component `[data-testid="readiness-component"]` containing `[data-testid="readiness-score"]`, `[data-testid="readiness-min-gate"]` (the gate value), and `[data-testid="readiness-warning"]` visible iff below gate; `[data-testid="readiness-exam-chip"]` showing the exam date (data, no urgency copy). Interaction smoke: clicking a component drills into its KC list; zero 4xx/5xx.

### 7.5 ADHD session shape

- **Session caps** with break prompts (the system proposes the break; it never guilt-trips). Closes audit P5: today the queue loads 50 cards with no cap (`FsrsDueQueue.kt:14`) and MotionToggle is the only accommodation anywhere.
- **Cross-subject interleave cap** — the queue will not thrash across more than the configured number of subjects per session, and conversely will not serve one subject indefinitely during a multi-exam season (audit P7: the current cap is mode-level only, `NextKcSelector.kt:107-113`).
- **Calibration surface mounted** — the existing calibration component gets a real route and feeds confidence-vs-correctness data into telemetry (audit T7: computed today, mounted nowhere).
- **Placement rebuilt** — placement probes **subject knowledge** (a few real items per component) and writes **`entry_phase`** per KC-cluster. Audit T5: the current `PlacementShell.tsx` asks 8 hardcoded MCQs about EWMA/spaced-repetition/interleaving — the tutor's own internals, not the subject — and never calls the submit API, so the correct server-side `entry_phase` write path is unreachable.

### 7.6 Acceptance criteria

- **INV-7.1:** Single-writer invariant: every surface's submit path calls the one mastery-update service; CI greps for direct FSRS/EWMA writes outside it → 0.
- **INV-7.2:** Against the real DB: after one graded interaction on each surface, the per-KC state row's `updated_at` advances and both mastery and schedule fields change consistently.
- **INV-7.3:** Priority function is pure + property-tested: closer exam, higher weight, larger gap, satisfied prereqs each monotonically increase priority, on real registry + real exam_dates rows.
- **INV-7.4:** Forgetting trigger: simulated time-advance on a copy of the real DB causes a compressed re-lesson (③⑤ only) to enqueue with parameter values ≠ the original lesson's values.
- **INV-7.5:** Readiness dashboard Playwright suite (§7.4 selectors) green against real registry data.

---

## 8. Language Layer

### 8.1 Policy (user-approved)

- **Everything the tutor SAYS is in `learner_language`** (RO for the current user) — *generated in* RO, not translated-after or quoted-in-English.
- **Quoted source spans stay in their original language**, embedded inside RO framing ("cursul spune: ‹…EN quote…› — adică …").
- **Practice mirrors the EXAM's language:** POO practice uses C++ with EN identifiers because the exam does; PA uses Alk; the tutor's *explanations around* that code are RO.
- **Glossary auto-built** (§3.5) wherever lecture language ≠ exam language.
- **Both-language sources** (PS publishes RO+EN): the **RO version is the grounding source** for faithful-checks.

### 8.2 Enforcement

- **Language validator as an admission gate** on every learner-facing field: diacritics presence + stopword-distribution heuristic flags EN text in RO fields; glossary terms exempt. Runs in the §9.2 gate chain, not as advice. (Closes audit L7: `ContentValidator.kt:112-128` currently checks non-blank only — English-authored teaching fields pass silently; the `_ro` suffix is a naming convention, not an enforcement, audit L8.)
- **LLM prompts carry an explicit language directive** and **lead with `name_ro`**. (Closes audit L2/L3: `DrillGenerator.kt:20-30` and the grader prompts carry zero RO directive and lead with `name_en`, and `PracticeSubsystem.kt:45` says "match the input language" — guaranteeing EN feedback on EN drills. The fixed pattern exists: `MockExamRoutes.kt:350` was repaired to `name_ro`; the placement fallback `kc.stem_template ?: kc.name_en` at `SessionPlacementExamRoutes.kt:265` is the same bug class awaiting the same fix.)
- **UI chrome i18n sweep:** the audit's 14-finding language catalogue (L1–L14) plus the rendered audit's VIZ-01/VIZ-02 (100% of the 20 gallery components' captions/labels/legends/aria-live + the AlgoStepperShell chrome — FRAME/RESET/SHARE/VOICE OFF/PREDICT — in English) enumerate every EN surface. The chrome subset (DrillStack chrome incl. DEFINITELY/MAYBE/GUESS/IDK, Scratchpad, TaskQuickStart, ConceptDrawer/KnowledgeLedger, Sidekick/ChatPane, TrustSettings, stepper shell controls) moves to a strings file; new chrome strings go through it by construction.
- **Viz captions are instance data** (§5.3) and therefore language-keyed like all teaching content.
- **The 828-card repair** (§1.3) includes a faithful-checked RO translation pass — each translated card re-verified against its source, per §3.7's per-version checking.

### 8.3 Acceptance criteria

- **INV-8.1:** Language validator runs on 100% of admitted learner-facing fields: real-DB query for served fields lacking a language-check record → 0 rows.
- **INV-8.2:** Seeded EN-in-RO regression set (taken from actual catalogued violations: the QR demo's "skeleton"/"skeletonul" leak — rendered-audit QR-EN-LEAK — plus EN drill stems and EN grader feedback from the SESSION-55/56/57 class) all fail the validator in CI.
- **INV-8.3:** UI chrome: CI greps lesson/practice/dashboard component sources for hardcoded EN learner-visible literals outside the strings file → 0 (allowlist: data-testids, code identifiers).
- **INV-8.4:** Playwright language gate (§9.2 gate 4) asserts rendered lesson text is RO (diacritic/stopword heuristic) on the real proof-set lessons.

---

## 9. Quality Gates & Trust

### 9.1 Gate-chain model

Per artifact: gates run in order; **any red → bounded self-retry (~3 attempts) with the failure data fed back** into the failing stage; still red → **parked gap-record with diagnostics attached**. Broken output never ships; the user never debugs. The retry budget is a constant in config, not a vibe.

**Retry semantics (the only three loops that exist):**

| Loop | Trigger | Bound |
|---|---|---|
| (a) Machine gate red | any gate in the chain fails | ≤3 self-retries with the failure data fed back into the failing stage; still red → park as gap-record |
| (b) User REJECT #1 at the checkpoint | first rejection of an artifact | one re-generation incorporating the one-line note |
| (c) User REJECT #2 on the same artifact | second rejection of the same artifact | STOP — pipeline design review (the §10.3 rule); never a third regeneration |

### 9.2 The chain

1. **Extraction gate** (§2.3).
2. **Trust-net faithful-check, turned ON** — operational sequence: **backup → migrate 3 columns → run `verifyContent` over the corpus → D9 PC→VPS sync**. Coverage extended to: generated beat content (per-field), `pedagogical_instance` method-consistency (§3.3), provenance-tiered strictness (§2.5). **CI fix folded in:** missing verifier dependencies = **loud red, never silent UNCERTAIN** (the failure mode where an absent dep made everything "uncertain-pass" is closed at the CI level). The pre-flip zero-row guard is scoped to flip time only (D-RF1: content_hash rows = 0 before the v2 re-key); it is NOT a standing "kc_verification_status must be empty" check — re-audits over populated tables are normal operation and must remain runnable, keyed by audit_run_id.
3. **Semantic figure gates** — per-family trace-match + invariant asserts (§5.4).
4. **Rendered gates via Playwright on the real lesson route:** (a) **no-clip/overlap promoted into the @playwright/test suite FIRST** (council ordering — this lands before the other rendered gates and before any baseline exists; today the no-clip check lives only in the ad-hoc `shoot.qrqa2.mjs`, not in CI — audit V10); (b) per-element legibility/contrast — the concrete floor: the QR payoff zeros rendered at ~1.75:1 against a 4.5:1 requirement (rendered-audit QR-CONTRAST-ZERO), and truncation that hides meaning (the TLS "Finished" ellipsis, VIZ-05) fails; (c) interaction test — all beats clickable, gates actually lock, zero console errors, zero 4xx/5xx; (d) language gate (§8.3). The rendered audit proves this gate set is satisfiable: the three demo lessons pass zero-clip / zero-overlap / zero-truncation / gates-genuinely-lock in all 12 theme×viewport passes; its 23 findings (0 blocker / 9 high / 10 medium / 4 low: 7 clip-overlap, 8 legibility, 3 EN-leak, 1 broken-interaction) all fall inside what gates 3–4 catch going forward.
5. **Impeccable** (design-rule linting) **with the council's amendment (a):** calibrate on this repo first (one `detect --json` run, catalogue every rule hit) → enable only the applicable-rules subset → **pin the version** (explicit `npx impeccable@<ver>`, never floating) → fail-open (warn, don't block) until calibrated AND pinned → only then blocking; `DESIGN.md` is auto-generated from `index.css` tokens, never a hand-maintained second source of truth.
6. **Visual baselines ONLY for the fixed shell + 2 theme reference pages**, environment-locked (headless only, `deviceScaleFactor: 1`, `--workers=1`, pinned Playwright + Node versions, single reference machine), updated only by human re-commit — the pipeline never re-commits a baseline autonomously. **Generated lessons are gated structurally (gates 3–4), never by per-lesson screenshots** — per-lesson baselines on generated content recreate the manual-approval queue the pipeline exists to kill (council-1781132987, convergent finding of 4 of 5 agents).
7. **Grader eval harness** — golden answer sets per grader per subject; any grader change must keep them green (§6.4).

After the chain: the **user experience checkpoint** (§1.4) — the only human step, and it judges experience, not truth.

### 9.3 Every gate is a CI-enforced invariant

Per the fix-claim discipline: a gate that exists as a script someone remembers to run does not exist. Each of the seven gates has a CI job; "this gate is implemented" is claimable only when the CI job exists, runs on the real corpus/DB, and a seeded violation turns it red.

### 9.4 Tooling adoption (per the tooling eval + council-1781132987)

- **Adopt:** Impeccable; **self-hosted fonts** (Fraunces/Nunito/JetBrains Mono woff2 under `tutor-web/public/fonts/`, CDN `<link>`s removed — kills the silent system-font fallback in headless renders, plus offline/exam-day safety); **Playwright extensions** (`toHaveScreenshot` for the fixed shell, `assertNoOverflow` in the @playwright/test suite, `trace`/`video: 'retain-on-failure'`, and the one-line vitest-axe import fix that un-breaks 8 silently-dead axe tests).
- **The council's 5 amendments** (council-1781132987 judge — verdict FLAWED → adopt-with-amendments, all 11 tooling choices upheld (5 council agents: 1 APPROVE / 4 CONDITIONAL / 0 REJECT), confidence 8):
  (a) Impeccable: calibrate → applicable subset → version-pin → fail-open until both → only then gate; DESIGN.md auto-generated from `index.css` tokens (folded into §9.2 gate 5);
  (b) screenshot baselines for the fixed shell + 2 theme reference pages ONLY, environment-locked, human-recommitted — never for generated lessons (folded into gate 6);
  (c) `assertNoOverflow` lands in the test suite BEFORE any baseline exists — "a baseline of a clipped layout is worse than no baseline" (folded into gate 4a's ordering);
  (d) every adopt-later item carries a named unlock event: **Taste-brutalist** → G4 resolved + a token-collision diff vs DESIGN.md (and only the brutalist variant — top-level v2 bans Fraunces, the locked warm display font); **VoltAgent format** → pipeline spec locked, then author project-specific `viz-quality-gate` / `lesson-beat-validator` agents in its format; **Stitch** → a real new-surface scaffolding need, via a vetted wrapper (the existing MCP wrapper is 0-star/1-commit and untrusted);
  (e) the lint + baseline gates cover **geometry/CSS only** — the viz-family architecture (§5) is the named semantic-correctness mechanism; citing gates 5–6 as closing the one-pass loop is explicitly false.
- **Skip:** SkillUI (would scrape a second, conflicting DESIGN.md), UI/UX Pro Max (fights the locked design system), 21st.dev ($20/month after 5 requests — hard no-paid-APIs violation), webgpu-skill (trains agents toward the blocked rendering layer).

### 9.5 Acceptance criteria

- **INV-9.1:** Gate-chain totality: every artifact row in the real DB is in state `live`, `in-pipeline`, or `parked` with a gap-record; no fourth state, no NULLs.
- **INV-9.2:** Retry boundedness: pipeline logs show ≤ retry-budget attempts per gate per artifact; exceeding parks.
- **INV-9.3:** Verifier-dep check: CI job that removes/hides a verifier dependency asserts the run goes RED, not UNCERTAIN.
- **INV-9.4:** Seeded-violation drill per gate (garbled PDF, unfaithful KC, wrong-trace figure, clipped label, EN-in-RO field, broken golden answer): each turns its gate red in CI. This is the proof the gates are alive.
- **INV-9.5:** Baseline scope: CI asserts the visual-baseline directory contains only the shell + 2 theme reference pages; a per-lesson screenshot baseline appearing anywhere fails CI.

---

## 10. Proof-of-Pipeline Acceptance

### 10.1 Build order (dependency order only — no schedule semantics)

1. **Trust-net ON** (backup → migrate → verifyContent → D9 sync) **and the template-contract doc** — these two are independent and proceed in parallel.
2. **Schema migrations** (§3.6, backup-first) — depends on step 1's backup+restore-drill runner existing and green — the same runner gates every subsequent mutating migration; no separate backup deliverable exists.
3. **BeatOrchestrator + the first viz family (graph/tree, seeded from the MergeSort port)** — depends on schema (beat fields).
4. **Gate chain wired** (§9.2) — depends on the things it gates existing.
5. **Proof run** (§10.2).

### 10.2 Proof set — one artifact per kind × concept-type coverage

| Artifact | Exercises |
|---|---|
| PA current-year lecture (from the course-site hub) | full digest → 5-beat lessons (procedure + code-trace types) |
| ALO course chapter (live-site `ALO curs NN.pdf`) | numerical skeleton-trace variant |
| PS course chapter (live-site `psN.pdf`, RO version as grounding) | probabilistic variant **+ the garble path** — the prior PS extracts were 100% watermark noise (audit C3); the extraction gate must demonstrably fire on a garbled input and recover via re-extraction or park |
| PA/ALO seminar sheet (ALO `exercitii N.pdf` — the week-7 test pool, per sweep erratum E2 these are seminar sets, not past exams) | proof drill + step-trace drill digestion |
| POO lab (`labs/labNN.html`) | code-practice session generation (C++ execution grader live, banned-API constraints honored) |
| POO past exam (user corpus, 2023–24 PNG scans) | mock-exam items with rubric scoring **+ the OCR extraction route exercised** |
| SORC fișă (grading-doc; `Fisa-SO+RC_ro.pdf` — the sweep could not parse it remotely, so the local pdftotext path is exercised) | grade-model registry row + readiness dashboard wiring |
| ALO Tema PDF (live-site `Tema N.pdf`) | homework kind: deliverable-tracker entry + skeleton prep drills |
| ALO "Foaie de referință" or `Fituica_Metode_Numerice` (Desktop) | cheat-sheet kind: reference index + proof templates + permitted-materials metadata |
| PA `NP_Complete_Course_Notes.md` (Desktop) | student-notes kind: lecture-equivalent digestion at student-notes provenance tier (stricter faithful-check exercised) |
| PA partial-test grade ledger (user corpus) | grade-ledger kind: FSRS difficulty seeding + rubric reconstruction; checkpoint shows the extracted analytics summary (§1.4 data-summary form) |

Each proof artifact runs the full loop: **upload → all gates green → user clicks through at the checkpoint → approve/reject**.

### 10.3 "Proven" — defined

The pipeline is proven when, and only when:

1. **Every artifact kind has ≥1 end-to-end user-approved exemplar** — the proof set (one per artifact kind, 11 artifacts, §10.2) supplies them.
2. **Zero garbage reached the user checkpoint** — every defect in the proof run was caught by a machine gate, not by the user's eyes. A defect the user catches is a missing gate, and the gate gets built before "proven" is claimable.
3. **Every gate exists as a CI invariant** (§9.3, INV-9.4 seeded drills green).
4. **The same artifact failing the checkpoint twice = a pipeline design bug**, owned by the system, not the user — it triggers a design review of the failing stage, never a third "try again". This is loop (c) of the §9.1 retry table; §1.5's `checkpoint-design-review` state is its surface.

### 10.4 Then: bulk upload

The 140 inventoried site URLs + the user's files, **sequential** (§1.4), with the checkpoint at every artifact until the user — and only the user — flips a subject to spot-check mode. No parallel batch processing of unproven kinds.

---

## 11. Residual Edges (verbatim honesty — these are real and not designed away)

1. **Missing source material.** Zero past/sample exam papers exist on **any official site for any subject** (sweep G15 — confirmed by enumeration, not merely not-found). ALO, PS, and RC have zero past papers anywhere including the user's files; PA's papers (2015–2018) and POO's (2023–24 PNG scans) exist only in the user corpus at `corpus-evidence` tier. The SORC SO sub-site is behind HTTP 401 (SO1/SO2/SO3 component definitions unknown — gap G7); the POO final-exam format is unpublished (gap G12, still-open list); PS's remaining genuine gaps are the probability-half seminar/lab sheets and sample mini-tests (gaps G3/G5). The system stays compliant by **synthetic-tagging** mock content for those components (§6.2.4) and raising **gap-records** (§2.6) that ask for files. It does not fabricate "real exam" claims.
2. **Content vintage.** Non-PA materials in the prior corpus are partly 2019–20 vintage (the prior PA lecture-01 extract is itself headed 2019/2020 — audit limitation #4: the trust-net would faithfully certify an outdated syllabus). The pipeline **prefers live-site current files** when both exist — the 140-item sweep enumerates current-year URLs for every subject precisely so digestion grounds on them. If a professor changes course content without publishing, the system is **faithful-to-source, not faithful-to-this-week** — that is the honest limit of a document-grounded tutor, and the trust badge's "corespunde cursului" copy (§12) states exactly that and no more.
3. **Free-LLM dependency.** Generation and prose-grading run on free-tier LLM access (no-paid-APIs is locked, §12) and **pause on rate-limit**. Mitigation by architecture: content is **pre-generated at digestion, never on the request path** — serving, numeric grading, and execution grading are local and keep working when the LLM is rate-limited. A rate-limited pipeline delays new digestion; it never breaks the learner's session.

---

## 12. Decision Log (locked decisions this spec builds on — none are reopened here)

| Decision | Source | Content |
|---|---|---|
| Lesson form: gated 5-beat step-through | council-1781052957 | Fixed beat vocabulary ①–⑤, min ①②③⑤, never reordered; un-skippable gates; reveal fused with explanation |
| Numerical lesson form | council-1781097621 | Skeleton-dominant active trace; one formula-line per click; named sign-step row; geometry = 1-screen anchor before arithmetic only |
| SVG-only + tooling verdicts | council-1781132987 (verdict FLAWED→adopt-with-amendments; all 11 tooling choices upheld — 5 council agents: 1 APPROVE / 4 CONDITIONAL / 0 REJECT) | Block WebGL/WebGPU/Three.js/canvas + scroll/mouse decoration; adopt Impeccable/self-host-fonts/Playwright-extensions **with the 5 amendments (a)–(e) of §9.4** (calibrate-subset-pin-fail-open + auto-DESIGN.md; shell-only environment-locked baselines; assertNoOverflow first; named unlock events; lint/baseline = geometry-CSS only with §5 as the semantic mechanism); adopt-later Taste-brutalist/VoltAgent/Stitch with those unlocks; skip SkillUI/UI-UX-Pro-Max/21st.dev/webgpu-skill |
| D-RF2: admission-only gate + always-on serve-side refusal | trust-net refreeze decisions (build-review/2026-06-05-trustnet-refreeze-decisions.md) | Verification gates admission; serving independently refuses unverified content at read time |
| D-RF3: `report_wrong` resolution-provenance columns | trust-net refreeze decisions (build-review/2026-06-05-trustnet-refreeze-decisions.md) | `report_wrong` gains `resolved_by` + `resolved_at` while the table is empty — provenance is un-backfillable once rows exist (§3.6 step 3) |
| No paid LLM APIs | feedback_no_paid_apis (standing) | OpenRouter `:free` or the claude-max relay only |
| Trust badge copy | SESSION lang fixes (`9825005`) | "corespunde cursului", never "corect" — the badge claims source-faithfulness, not truth |
| Live-DB backup before mutation | locked decision (this design cycle) | Off-box dump verified before any migration step touches `~/.jarvis/tutor.db` |
| Per-artifact sequential + user experience checkpoint | user amendment, 2026-06-11 (this spec §1.4) | Machines verify truth; user approves experience; spot-check unlock is user-only |

---

## 13. Interface compatibility — ADDITIVE contract

All new endpoints, tables, columns, and payload fields introduced by this design are **additive** with respect to `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, which remains **canonical-on-conflict**: if an implementation detail in this spec would require changing a frozen signature, the frozen signature wins and the implementation plan must route around it additively (new endpoint version, new nullable column, new payload field with default). INV-3.5 enforces this in CI. The lock's §NEW-L (added SESSION-63) is included in the frozen set.

---

## 14. Glossary

**The 9 artifact kinds:** lecture · lab · seminar · homework · past-exam · grading-doc · cheat-sheet · student-notes · grade-ledger. (§2.2)

**The beat vocabulary (fixed):** ① PREDICT — commit to an expectation before seeing; ② ATTEMPT — do a scaffolded sub-task; ③ REVEAL — stepped, scrubber-equipped disclosure fused with explanation; ④ NAME — formal definition + invariant + why-callout; ⑤ CHECK — a different-instance graded item. (§4.2)

**The 6 viz families:** graph/tree · sequence/array · matrix/grid · chart/distribution · timeline/protocol · state-machine/code-trace. (§5.2)

**Other terms:** *gap-record* — a parked artifact's user-visible request for files (§2.6). *pedagogical_instance* — generated example content verified by method-consistency + trace-match instead of verbatim grounding (§3.3). *checkpoint* — the per-artifact user experience-approval step (§1.4). *grade-model registry* — verified per-subject grading formulas with source URLs (§3.5). *synthetic-tagged* — practice content honestly labeled as not derived from a real past paper (§6.2.4).

---

## 15. Amendment — 2026-06-14 · experience-completeness additions (EC1–EC5)

Surfaced in the SESSION-72 experience review (Alex), each verified against this spec before adoption. They EXTEND named sections; none reopens a frozen signature (`interface-signatures-lock.md`) or the trust-net. Governed by the to-100 ledger's **P1 (NO-SPEC-CUT)** + **P2 (EASY ⇒ GATED)**: where a requirement is machine-decidable it ships an INV; where it is not, it ships an ESTIMATOR + the §1.5 checkpoint flag `neverificat / verifică sursa`, never a faked pass.

### 15.1 EC1 — Live-help layer (extends §4)

A learner lost mid-lesson must get help WITHOUT leaving the beat. Two affordances, both `(kc_id, beat)`-anchored:

- **Ask-in-lesson.** A low-profile entry inside the BeatOrchestrator surface opens a grounded ask panel anchored to the current KC + beat. Reuses the built sidekick path (`POST /api/v1/sidekick/ask`, `SidekickEnvelope` with `card_id`/`anchor`/`selection`, `inlineAsk.ts`). Answer is citation-backed; the **drill-self-paste guard** refuses to hand over a gated drill's solution. RO framing per §8.1. A "suggestions" affordance offers 2–3 KC-scoped starter questions so a stuck learner who cannot phrase the question still has an entry.
- **Prereq-peek.** Any term the KC declares as a prerequisite (R-PREREQ edge) carries an on-demand recap affordance → a compact card serving the prereq KC's **compressed reveal** (③ + NAME-level) from its stored beat fields (§3.2), with provenance. It does NOT advance the lesson and does NOT mutate the host KC's mastery (a peek is not a graded interaction).

No new generation on the request path — server reuses the sidekick endpoint + the prereq KC's stored beats.

- **INV-EC1.1 (anchoring):** every ask issued from a lesson carries the live `kc_id` + `beat_type`; a request with neither fails validation.
- **INV-EC1.2 (no-spoiler):** a test pastes the active drill statement and asserts the `drill_statement` Jaccard ≥ 0.7 short-circuit fires — the LLM call is NOT made.
- **INV-EC1.3 (peek is read-only):** a prereq-peek interaction writes zero rows to the host KC's mastery/FSRS state (DB assertion).
- **Visual:** both affordances ride the §9.2 gate-4 visual gate (no clip/overlap at the learner viewport, viewport-not-fullPage, nothing under fixed chrome).

### 15.2 EC2 — Cold-start completeness gate (extends §2.4 / §7.4; ledger §B)

No advanced KC may be reachable without a prerequisite path down to a foundational/entry KC — or the missing rung is made explicit, never silently taught-on-top-of. This is the machine form of P1 for the teaching ladder.

- At digestion, each servable KC's prerequisite chain is walked. A KC whose chain references a concept with no KC in the corpus gets an **assumed-knowledge stub**: a flagged placeholder carrying `neverificat / verifică sursa` + a gap-record (§2.6) requesting the foundational material.
- An **entry KC** = a KC with no unmet prerequisites (reachable from zero per §7.5 placement / `entry_phase`).

- **INV-EC2.1 (reachability):** CI graph check over the production KC graph — every `servable` KC has a directed prerequisite path to ≥1 entry KC; any failure is either flagged `neverificat` or carries a linked assumed-knowledge stub. Silent-orphan count = 0, against the real DB.
- **INV-EC2.2 (figure coverage):** every served REVEAL whose `concept_type` maps to a viz family (§5.2) has a bound figure instance OR a logged `figure-deferred` flag; silent-figureless count = 0 (the sparse-figure coverage check).

### 15.3 EC3 — Recovery-session mode (extends §7.5)

Per-KC re-lessons (§7.3) do not protect a learner who returns broadly decayed. A session-level response sits beside the §7.5 caps:

- The selector detects **broad decay** — the session's due/decayed set exceeds a configured threshold (count or fraction of planned KCs), OR N consecutive in-session CHECK failures.
- On trigger the session enters **recovery mode**: NEW first-encounter KCs are suppressed; the queue serves only RE-LESSONs (§4.5) over the decayed prerequisite cluster, prereq-ordered, lighter load; the surface states plainly "azi recapitulăm" (data, no guilt-trip, per §7.5). New material resumes the next session once the cluster recovers above threshold.

- **INV-EC3.1 (suppression):** a recovery-triggered session on a copy of the real DB serves zero first-encounter (FULL/STANDARD) beat-plans — only RE-LESSON / MASTERED-REVISIT (property test).
- **INV-EC3.2 (trigger purity):** the trigger is a pure function of (due-set size, planned-set size, in-session check failures), property-tested monotone (more decay ⇒ recovery no later).

### 15.4 EC4 — Goal/intent layer (extends §7.4)

The queue is curriculum-driven (exam dates × weights). A learner must also set an explicit target and have the system orient to it.

- A **goal** = a learner-declared target: a specific past paper, a topic/KC-cluster, a deliverable, or a date. Stored per user.
- On set, the system **decomposes** it into KCs (a past paper → the KCs its items exercise; a topic → its cluster), computes **readiness for that target** (the §7.4 readiness-gap arithmetic restricted to the target's KCs), and **re-weights queue priority** toward closing that gap — WITHOUT silencing exam-proximity safety (a near official exam still floors priority; the goal re-weights within, never overrides a real deadline). The readiness dashboard gains a per-goal view ("ești X% pregătit pentru «lucrarea 2023»").

- **INV-EC4.1 (decomposition):** setting a past-paper goal yields a non-empty KC set from that paper's item→KC links; a goal with zero resolvable KCs is rejected at set-time with a gap-record, never silently accepted.
- **INV-EC4.2 (priority safety):** property test — an official exam within its proximity window still out-prioritizes a goal KC with a later horizon (a goal cannot starve a real deadline).
- **INV-EC4.3 (readiness honesty):** per-goal readiness counts only VERIFIED-servable KCs (INV-1.1); unverified KCs in the target surface as `neverificat`, never as a silent 0 or a silent pass.

### 15.5 EC5 — Provider setup + health surface (new settings surface)

Switching the LLM provider (esp. FreeLLMAPI) is today an env-var + deploy step with no in-app guidance and no reachability signal. The learner path stays zero-setup (default `free`); this is an ADMIN surface.

- A settings panel exposes the existing plumbing: `GraderProviderSetting` (enum `free / claude / freellmapi`) + `user_provider_config` (encrypted `api_key_encrypted_ref`). Pick provider, store key (encrypted, never logged/committed), set `FREELLMAPI_BASE_URL`.
- A **connection health-check**: a non-generating probe per provider returning `reachable ✓ / rate-limited / down`, so a stalled digestion (§11.3 free-LLM-pause) has a legible cause.
- The learner-facing path mounts NONE of this. The §12 no-paid-APIs floor is unchanged — `free` / claude-max-relay only; `freellmapi` is localhost-additive.

- **INV-EC5.1 (default zero-config):** a fresh user with no `user_provider_config` row resolves to `free` and reaches a served lesson with no provider step (the `GraderProviderSetting` default, asserted).
- **INV-EC5.2 (secret hygiene):** the provider key is never written to logs or the repo; CI greps build output + tracked files for the key pattern → 0.
- **INV-EC5.3 (health honesty):** the health-check reflects a real probe; a `down` provider never renders `✓` (test with a bad base-url asserts `down`).

---

## Appendix A. Coverage map

This appendix maps the audit finding clusters and the artifact-taxonomy requirements to the spec sections that address them. Sources: `build-review/2026-06-10-final-audit-report.md` (135 deduplicated flaws: 23 blocker / 53 high / 46 medium / 13 low, from 200 adversarially-verified raw findings, 0 refuted), `build-review/2026-06-10-rendered-ux-audit.md` (23 findings: 0 blocker / 9 high / 10 medium / 4 low, 104 screenshots), and `docs/superpowers/findings/2026-06-11-artifact-type-taxonomy.md` §3 (19 R-* requirements).

### A.1 Final-audit-report clusters (finding IDs per the report's §3)

| Cluster (findings) | Addressed by |
|---|---|
| **C — Content corpus & trust-net (C1–C5)** | C1 (4/5 subjects zero KCs) → §1.1 digest stage + §10.4 bulk run over the 140 sources; C2 (trust-net never run) → §9.2 gate 2 turn-on sequence + §3.6 migration; C3 (extraction garble, gate built-but-dead) → §2.3 front-gate promotion; C4 (EN sources vs RO output contract) → §8.1 generated-not-quoted policy + §3.5 glossary; C5 (no path before finals) → its deadline framing is excluded by this spec's ground rules; its substantive half — no Plan-B for the 828 cards — is §1.3 |
| **E — Engine delivery (E1–E18)** | E1/E11 → §4.1 BeatOrchestrator (rewrite-port); E2/E12 → §3.2 predict fields + §4.1 callback echo; E3 → §4.1 + §5 (reveal + stepper on the lesson path); E4 → §4.4 completion writes; E5 → §3.2 NAME fields; E6 → §3.2 figure binding (viz on the wire); E7 → §4.2 BeatSelector; E8 → §6.3 graded CHECK; E9 → §3.2 ATTEMPT schema + §4.3 variants; E10 → §4.4 handoff contract; E13 → §4.3 geometry anchor (now schema-representable); E14 → superseded — verified families replace degrade-to-static; residual failure parks per §9.1; E16 → §6.3 numeric oracle; E17 → §6.3 execution grader; E18 → §6.3 misconception codes (R-ERRORS) |
| **A — Authoring schema (A1–A11)** | A1/A2 → §3.2 beat fields; A3 → §3.2 CHECK different-instance + INV-3.2; A4 → superseded: the curate-tutor skill is replaced by the digest stage, which produces beat fields by contract (§1.1/§3.2); A5 → §3.3 pedagogical_instance; A6/A7 → §1.1 LLM generation unblocked by gate-2 turn-on (§9.2); A8 → admission gates check teaching-field presence (INV-3.2); A9/A10 → §3.2 mandatory fields + §8.2 RO enforcement; A11 → §3.4 (canonical answers live in problem rows + ⑤ grading data) |
| **T — Tracking (T1–T9)** | T1 → §3.6 kc_id backfill; T2 → §7.1 unified state; T3 → §7.2 beat telemetry + §4.4 beat_type columns; T4 → §7.3 misconception re-queue; T5 → §7.5 placement rebuild; T6 → §7.1 all-surfaces-write (mock exams write mastery); T7 → §7.5 calibration mounted; T8 → §7.3 forgetting re-lesson; T9 (dead legacy Lessons.kt channel) → implementation cleanup under §13's additive rule, no learner-facing design needed |
| **V — Viz (V1–V11)** | V1/V2/V7 → §5.1/§5.5 (G4 resolved in favor of families; registry bottleneck dies); V3 → §5.3 typed instance data; V4 → §4.1 gating at orchestrator level; V5 → §5.3 anchored callouts; V6 → §3.2 figure binding becomes the only figure channel (dead `figure_spec` wire retired additively); V8 (Sigma all-black) → re-expressed as chart/distribution instance + INV-5.2/5.3; V9 → §5.4 trace-match over ALL live instances (vs 4/24 today); V10 → §9.2 gate 4a (no-clip into @playwright/test FIRST); V11 → §5.3 deterministic per-family layout |
| **L — Language (L1–L14)** | L1 → §1.3/§8.2 card repair; L2/L3 → §8.2 prompt directives; L4 → §8.2 name_ro lead (placement fallback); L5 → §8.2 validator covers misconception trigger/refutation text; L6, L10–L14 → §8.2 strings-file chrome sweep; L7 → §8.2 validator as admission gate; L8 → §8.1 quoted spans inside RO framing, explicitly marked as quotes; L9 (EN internal fields) → out of learner scope but flagged at §9.2 gate 2 (NLI hypothesis quality) |
| **P — Pedagogy (P1–P9, P-PROOF, P-DEF, P-TIMING)** | P1 → §1.3 repair + §6 graded surfaces (kills self-graded read-reveal); P-PROOF/P-DEF/P-TIMING → §4.3 variants (named explicitly there); P2 → §4.3 full concept-type table + §10.2 proof set spanning untested types; P3 → superseded (DrillStack 4-card form replaced by BeatOrchestrator + §6 surfaces); P4 → §4.1 dwell-as-floor + §4.6 learner-paced scrubber; P5/P7 → §7.5 caps + interleave; P6 → superseded (beat-at-a-time presentation); P8 → §4.5 mastery-conditional predict skip; P9 → §4.6 scrubber requirement |
| **Subject findings (PA 12 · ALO 9 · PS 5 · POO 11 · SO 8 · RC 11)** | Kind divergence + SO/RC domain tags → §2.2; garble/OCR routes (pa-lectures-2-8-pdf-only, ps garble, poo PNG scans) → §2.3/§2.4 + §10.2; numeric skeleton incl. CG >4 rows → §4.3; family coverage (DP tables, pruning trees, centrality graphs, continuous-PDF shading, fork-descriptor timelines, instruction steppers) → §5.2 — each named missing one-off becomes an instance of an existing family, not a new component; graders (reduction-direction trap, subnetting, R/C++/Alk execution) → §6.3; corpus absences (poo-exceptions-absent, poo-design-patterns-only-singleton, so-corpus-not-os-theory / so-no-theory-source, rc-corpus-only-3-lectures) → §2.6 gap-records + §11.1 — the system does not author content with no source; anchorless viz (TcpCwnd Reno, Tls0Rtt) → §5.5 (render logic salvaged, content requires source rows); exam_weight redistribution traps (pa-cluster-coverage-inverted-weights, ps/poo weight-underivable) → §3.5 registry weights replace per-KC guessed weights as the priority input (§7.4); ps-monte-carlo cross-subject overlap → A.3 item 4 (partially addressed) |

### A.1b Rendered-UX-audit findings (23)

| Findings | Addressed by |
|---|---|
| QR-GEO-1, QR-GEO-2 (figure contradicts its own invariant; label collision; dead guide line) | §5.4 invariant asserts in rendered px + trace-match; §5.3 measure-own-labels layout |
| QR-CONTRAST-ZERO, QR-GATA-FAINT, VIZ-03, VIZ-04, VIZ-05, VIZ-09, VIZ-10 (contrast / truncation / unreadable values / aria-only readouts) | §9.2 gate 4b per-element legibility; §5.3 no-clip-by-construction (labels are measured, zero-width segments emit no label); quantitative readouts are slot data, on-screen |
| DIJ-ONESHOT (+ audit P9) | §4.6 scrubber requirement, machine-checked by gate 4c |
| QR-EN-LEAK, VIZ-01, VIZ-02 (EN copy in lessons, all 20 components, shell chrome) | §8.2 enforcement + strings file + INV-8.2 seeded regressions |
| VIZ-06, VIZ-07, VIZ-08, VIZ-11 (clip/overlap in components) | §5.3 contract + INV-5.3 over all live instances |
| QR-DOUBLE-2 (step numbering) | §5.4 trace-match — step identity/numbering is part of the reference trace |
| QR-WARM-GALBEN (color-name vs theme) | §5.3 anchor-not-color-name callout rule |
| QR-TOPGAP, MS-DJ-TOPPOOL, VIZ-12 (composition/layout imbalance) | §9.2 gate 5 (Impeccable calibrated subset) + gate 6 shell baselines |
| FAVICON-404 | §9.2 gate 4c zero-4xx smoke (ship the asset or the gate stays red) |

### A.2 Artifact-taxonomy R-* requirements (all 19)

| R-* | Addressed by |
|---|---|
| R-NEW-SURFACE-PROOF | §6.2.1 structured proof drill |
| R-NEW-SURFACE-STEPTRACE | §6.2.2 step-trace numeric drill |
| R-NEW-SURFACE-CODE | §6.2.3 code-practice session |
| R-NEW-SURFACE-MOCK | §6.2.4 mock-exam mode |
| R-NEW-SURFACE-DELIV | §6.2.5 deliverable tracker |
| R-RUBRIC | §3.4 rubric rows + §2.4 extraction + §6.2.4 scorer |
| R-SOLPRESENT | §3.4 `solution_present` + §2.2/§2.4 worked-example ≠ solution rule + §6.2.4 synthetic tagging |
| R-GRADEMODEL | §3.5 registry (+ §7.4 dashboard) |
| R-LANG | §3.4 exam-language constraint + §6.2.3 + §8.1 |
| R-ARCHETYPE | §3.4 archetype + parameter slots + §7.3 fresh-values re-lesson |
| R-DATAFILES | §3.4 data-file dependencies + §2.4 |
| R-PREREQ | §2.4 edge extraction + §3.4 KC links + §7.4 prereq-order priority |
| R-DIFFCAL | §2.2 kind 9 (grade-ledger analytics → FSRS initial difficulty + rubric reconstruction) |
| R-GAPLEDGER | §2.6 visible upload-request cards + §11.1 |
| R-MULTISELECT | §6.2.4 multi-select variant (mock + drills), flagged at extraction §2.4 |
| R-ERRORS | §2.4 mining + §6.3 misconception codes + §7.3 remediation |
| R-DOMAINSPLIT | §2.2 SO-vs-RC + POSIX-vs-theory domain tags |
| R-DEDUP | §2.5 content-hash + canonical-path resolution |
| R-PROVENANCE | §2.5 tiers + §9.2 gate 2 tiered strictness |

**Totals: 19/19 R-* mapped; all 7 finding areas + subject findings + all 23 rendered-UX findings mapped.**

### A.3 Unaddressed or partially addressed (with reason)

1. **Multi-user/auth isolation** (audit limitation #17) — out of scope: single-user deployment; the only per-user seams this design touches are `learner_language`, checkpoint events, and spot-check grants. An auth/isolation design is a separate spec when a second user exists.
2. **Performance/bundle budget** (limitation #19) — no numeric budget is set here. Structurally mitigated (SVG-only avoids the ~600 KB Three.js class; families share layout code); a measured budget gate is deferred until a baseline measurement exists.
3. **Outcome instrumentation / learning-gain measurement** (limitation #18) — the readiness dashboard is a proxy (mastery vs verified gates), not a causal learning-gain measure. Defining "did this lesson teach" experimentally is out of scope.
4. **Cross-subject KC-level dedup** (`ps-monte-carlo-overlap-misclassification`) — partially addressed: §2.5 dedup is artifact-level (content hash); concept-level overlap across subjects (PS↔PA↔ALO randomized algorithms) has no merge mechanism in this design. Latent until multiple subjects are digested; the per-artifact checkpoint surfaces the symptom but does not resolve the duplication.
5. **Romanian register quality vs UAIC professor vocabulary** (limitation #16) — the language gate checks RO-ness, the glossary anchors term pairs, faithful-checking anchors to RO sources where they exist (PS); a dedicated mathematical-register quality measure is not designed.
6. **Voice** — standing project deferral, unchanged; the stepper's voice affordance stays off-path.
7. **Mărire (grade-improvement) session dates** (sweep G14) — data gap, not design gap: `exam_dates` accepts the rows whenever they are published.
8. **C5's deadline-feasibility computation** — excluded by this spec's own ground rule (no deadline framing anywhere); the dependency-only build order (§10.1) and the 828-card absorption (§1.3) carry its substantive content.
