Ground truth established. Reality vs. inventory: HEAD `9c48d1d` (1040 commits, ~140 ahead of the legacy live bundle); families graph-tree + sequence-array + chart-distribution + matrix-grid + sort-merge exist on disk (Plan V is well underway, not "pending recon"); the `pa-kc-002→graph-tree` mergesort binding test is real and is the retire-target; 8 verification rows (status column = `status`), 828 cards with 0 confidently-linked kc_id. The council JUDGE binds the A→B→C→D→E oracle-first resequence.

I have everything needed. Writing the master plan now.

---

# THE END-TO-END MASTER PLAN — Tutor: LOCAL→100%-SPEC→DEPLOY

**Binding direction:** council JUDGE (`build-review/2026-06-15-teaching-pipeline-council-JUDGE.md`, confidence 9/10) — oracle-first, breadth-last. **Governing law:** P1 (NO-SPEC-CUT: every requirement is PROVEN by a green decidable gate OR an ESTIMATOR + human-checkpoint with an explicit `neverificat` flag — never silently dropped, never fake-green) and P2 (EASY⇒GATED: every content type ships a `typed input → renderer/template verified once → auto-gates ride free` path). **The learner cannot vet content → every quality claim is machine-checkable without Alex.**

---

## 1. WHERE WE ARE (honest, one paragraph)

The **live VPS serves the legacy SESSION-66 bundle**; local `main @ 9c48d1d` is **~140 commits ahead and entirely undeployed** — push ≠ deploy, and no feature built since SESSION-66 is visible to a real user. **Built + CI-green on main:** the serving/tracking spine the council says to KEEP — Plan 1 (trust-net ON: backup gate, restore drill, 8 `kc_verification_status` rows, 4 faithful PA KCs serving), Plan 2 (7 schema tables + `concept_type` enum + beat schema + 828-card backfill, 0 confidently linked), Plan 3 (BeatOrchestrator 5-beat deck + BeatSelector closed table + per-beat grading + graph/tree family), Plan 4a/4b (Impeccable fail-open + self-hosted fonts + rendered legibility/interaction/language gates, caught 2 real defects), Plan 6 (5 practice surfaces + 4-leg grader stack), and **Plan V partially landed** (sequence/array, chart/distribution, matrix/grid, sort/merge families now exist alongside graph/tree — further than the ledger records). **The structural gap (council verdict "NO"):** the entire digestion/generation half (§2–3, ~50% of spec) is zero-code; **there is no machine oracle for "does this lesson teach"** (every gate measures the artifact — geometry/no-clip/faithful-to-source/language — none measures the learned effect; the §6 mergesort-that-never-sorts passed *every* gate); the **DEPTH artifact is unnamed/unschema'd/ungated/structurally homeless**; and the human checkpoint is held by a learner who by definition cannot fill it. Only ~5 KCs across 1 of 6 subjects have real content. Honest status: **the spine is good; the firehose and its two oracles do not yet exist; nothing is live.**

---

## 2. DEFINITION OF 100% (per dimension — the acceptance contract)

Each line: *done = 100%* + *the gate that proves it*. Every "done" is a green gate, never an opinion. Dimensions map to the 16-dimension inventory.

| # | Dimension | done = 100% to spec | GATE that proves it (decidable) |
|---|---|---|---|
| **1** | **Upload / Ingest + Extraction-legibility** | File+URL drop; 140 URLs ride one sequential pipeline; legibility gate runs FIRST, garble→alt-extractor→OCR→park-as-gap | INV-2.2 (gate on 100% of ingest, garbled real PDF parks), INV-2.3 (dedup), INV-1.4 (gap-record FILES-only grammar lint) |
| **2** | **Classify (9 kinds, subject, exam-component, SO/RC split)** | By content never filename; every artifact carries subject+kind+exam-tag; SORC carries domain tag; seminar⇒PA/ALO/PS only | INV-2.1 (random-filename proof-set→identical kind), INV-2.4 (non-null tags real-DB), INV-2.5 (seminar↔subject) |
| **3** | **Digest → KCs + beats + problems + rubrics + dates** | Every KC has `concept_type`∈enum + 5 beats in RO; problem bank + rubrics + grade-model registry + exam_dates populated; backup-first migration | INV-3.1 (refuse-mutate-without-dump), INV-3.2 (concept_type + 5 beats), INV-3.3 (828 cards survive), INV-3.4 (registry source-URLs, 12 exam rows), INV-3.5 (additive-only vs frozen sigs) |
| **4** | **5-beat form + concept_type→variant + I-do/we-do/you-do** | BeatOrchestrator replaces 3-step; 4 legal plans only; dwell-floor; variants per concept_type; completion writes attempt+EWMA+FSRS+first-encounter; drill handoff | INV-4.1 (closed plan table), INV-4.2 (dwell boundaries), INV-4.3 (completion writes), INV-4.4 (Playwright §4.7 selectors + scrubber-back) |
| **5** | **Figures (6 families, semantic gates, SVG-only)** | Every figure = (family, instance); render verified once/family; **figure generated FROM algorithm execution**; fitness gate fail-closed | INV-5.1 (trace-match real-DB), INV-5.2 (semantic invariants), INV-5.3 (no-clip 2 viewports), INV-5.4 (SVG-only grep), INV-5.5 (zero free-form), **+ fitness/every-frame-advances/final==end-state/monotone** (Stage A) |
| **6** | **Trust-net / faithfulness** | Admission-gate + always-on serve-side refusal; coverage extends to beat fields + figures + pedagogical instances; missing dep = RED not UNCERTAIN | INV-1.1 (every servable row VERIFIED), INV-1.2 (serve-side refusal), INV-9.3 (dep-removal→RED) |
| **7** | **Guidance (priority queue, readiness, ADHD, recovery)** | Priority = proximity×weight×gap×prereq; readiness dashboard vs verified gates; session caps; recovery mode; placement probes SUBJECT | INV-7.3 (monotone priority), INV-7.5 (dashboard selectors), INV-EC3.1/.2 (recovery), INV-EC4.3 (`neverificat` not silent-0) |
| **8** | **One human experience-checkpoint** | Per-artifact sequential; machine-green→STOP→render real lesson→approve/reject; approve→reference-pattern; reject→one regen; spot-check user-only | INV-1.3 (every `live` artifact has 1 approved checkpoint event), checkpoint Playwright (§1.5 selectors + smoke) |
| **9** | **Practice (drills, code-exec grading, mock+OCR)** | 5 surfaces; grader stack LLM-LAST-never-alone; exec graders R/Python/C++/Alk; attempt-gated reference; mock mirrors real papers | INV-6.1 (routing total, non-LLM first), INV-6.2 (exec sandbox good/bad mutant), INV-6.3 (never LLM-alone), INV-6.4 (goldens), INV-6.5 (5 surfaces Playwright), INV-6.6 (attempt-gating) |
| **10** | **Tracking (unified FSRS+EWMA, triggers)** | One per-KC state; all surfaces write it via single writer; forgetting→re-lesson w/ fresh params; misconception→remediation requeue | INV-7.1 (single-writer grep), INV-7.2 (state advances all surfaces), INV-7.4 (re-lesson fresh params), INV-4.5 (RE-LESSON legal only on forget) |
| **11** | **UI/UX (premium-by-default, ADHD pacing, no-clip)** | Cinematic shell IS production (demo/prod collapsed); scrubber on every ③; no-clip at 648px; Impeccable blocking | INV-4.4 + INV-5.3 + §9.2 gate-4 (no-clip/legibility/interaction/language) + INV-9.5 (baseline = shell+2 themes only); **+ Stage-D viewport-screenshot @648px** |
| **12** | **Language (RO content, EN meta)** | All teaching generated in RO; quoted spans in-source; validator is admission gate; chrome i18n in strings file; 828-card RO re-verify | INV-8.1 (validator 100% of fields), INV-8.2 (EN-in-RO regression fails), INV-8.3 (no hardcoded EN grep), INV-8.4 (Playwright RO gate) |
| **13** | **Depth artifact (NEW first-class layer)** | Separate concept-keyed artifact (invariant+why · complexity DERIVATION · correctness · edge cases · code); NOT a figure skin; own oracle; `neverificat` until passed | **Stage-B depth gate**: invariant re-derivable, complexity sound, code compiles+tests, edges enumerated; excluded from `mastery`/`faithful` UI until green |
| **14** | **Pedagogical quality (NEW load-bearing oracle)** | Each KC carries pre-misconception→target-conception delta + discriminating CHECK items; instructional invariants hold | **Stage-C discriminating-CHECK gate**: CHECK items provably separate wrong-model from right-model learner (else `neverificat`) + ATTEMPT-precedes-answer + intuition-before-name + depth-present + EC2-prereq-complete |
| **15** | **EC1–EC5 (experience completeness)** | Live-help (ask-in-lesson + prereq-peek); cold-start completeness; recovery; goal/intent; provider health surface | INV-EC1.1/.2/.3, INV-EC2.1/.2, INV-EC3.1/.2, INV-EC4.1/.2/.3, INV-EC5.1/.2/.3 |
| **16** | **Content coverage (all 5 subjects) + Deploy** | 9 kinds × concept-types in proof-set; 140 URLs through checkpoint; synthetic-tag where no real papers; ONE verified VPS deploy w/ live probe | INV-9.1 (artifact-state total), proof-run green on real corpus, **deploy live-probe** (user sees feature on live URL, zero 4xx/5xx, all spec'd `data-testid` paint) |

**Two cross-cutting compliance gates** (govern all of the above): **gate-COVERAGE invariant** — every spec clause/INV-* maps to ≥1 live gate; un-gated clause = RED (machine enforcement of P1). **gate-self-test** — every gate fed a known-bad fixture asserts RED (INV-9.4); a gate that never proves it can say RED is not a gate. The **per-gate MATRIX** (named board, PROOF vs ESTIMATOR tags; SKIP/fail-open/cross-platform = RED) replaces any single GREEN/RED bit.

---

## 3. THE PHASES (ordered: oracle-first, deploy-last)

Hard ordering rule from the JUDGE: **foundation-defects + figure-correctness oracle (A) → depth artifact + gate (B) → pedagogical-quality oracle (C) → UI collapse / premium-by-default (D) → THEN digestion/generation (E/Plan 5) → content across subjects → guidance/practice/exam completion → EC1–5 → final hardening → ONE VPS deploy.** Plan 5 (breadth) MUST NOT start until A+B+C are green.

---

### PHASE 0 — Foundation-defect repair + compliance harness (UNBLOCKER; before any gate carries authority)

**Goal:** make the gate substrate trustworthy so every later "green" means something. **Closes:** the §B foundation defects + the matrix.
**Deliverables:**
- **Impeccable → blocking** (re-calibrate on real lint hits, pin version, flip the `|| true` fail-open guard).
- **Cross-language schema-hash CI parity** (python `db-backup.py` vs Kotlin `MigrationBackupGate.liveSchemaHash`).
- **Relay retry/backoff** on the VerificationRunner relay path (closes `pa-kc-006` false-negative class).
- **StateCache per-test isolation** (kills the ~1-in-2 concurrent flake).
- **Linux visual-baseline regeneration** (baselines captured Windows-only; CI runs Linux).
- **deploy.sh SPA smoke** replaced (greps `/` for `<div id=root>` but `/` 302s to `/login` → healthz + authenticated probe).
- **Per-gate MATRIX rollup** (named board, PROOF/ESTIMATOR tags) + **gate-COVERAGE invariant** (every spec clause→≥1 gate) + **gate-self-test** (every gate→known-bad→RED).
- **Standing adversarial red-team step** ("same defect-CLASS escapes twice = pipeline design bug").

**EXIT GATE:** Impeccable blocking-green; schema-hash CI test green cross-platform; matrix prints all-green with zero SKIP/fail-open; coverage invariant green (every current INV maps to a gate); every existing gate passes its own self-test. **Maps to:** §B of the to-100 ledger + foundation defects; supersedes the "build the matrix late as a meta-layer" approach.

---

### PHASE A — Figure-correctness loop (Stage A; the figure oracle)

**Goal:** figures are a deterministic function of algorithm execution, gated fail-closed. **Closes dimensions:** 5 (+ the §6 never-sorts class). **Maps to:** Plan V + council-1781391707 (visual gate) + the two prior figure councils; supersedes "hand-author per-step JSON."
**Deliverables:**
- **Generate-from-algorithm:** `run(algorithm, input) → typed state/event stream → keyframes`. **RETIRE hand-authored per-step figure JSON.**
- **concept_type → family FITNESS gate (fail-CLOSED):** family must express the concept's defining dynamic AND final frame reaches taught end-state; no fit → HALT + "needs new family" backlog (never silent fallback).
- **Re-bind mergesort** off `graph-tree` onto an animated stepped seq-array/merge family; **delete the test pinning the wrong binding** (`FigureBindingNonVacuityTest.kt:72`, verified present: pins `pa-kc-002→viz-pa-mergesort-001` on `graph-tree`). Keep graph-tree only for the "count log n levels" complexity beat.
- **Finish the 6 families** (graph/tree, seq/array, matrix/grid, chart/distribution **already on disk**; complete timeline/protocol + state-machine/code-trace; reconcile sort/merge into the set) + migrate the 24 primitives (17 zero-prop re-expressed as instance data; 2 anchorless = render-logic-only; RecursionTree→graph/tree, exit vizRegistry).
- **Named CI gates from RENDERED frames:** every-frame-advances (perceptual-hash dead-frame detector; legit holds = explicit `hold:true`), final==end-state, monotone-progress, no-regression perceptual-diff. **Blocking per-family visual gate** (council-1781391707): zero pairwise text-on-text overlap + nothing under fixed chrome, at 1536×648 AND ×730, theme-token conformance; pin `lectie.html` as visual reference.

**EXIT GATE:** INV-5.1–5.5 green on every live instance in real DB; fitness gate fail-closed verified by self-test; mergesort re-bound and the wrong-binding test deleted; all 6 families pass trace-match + no-clip + visual-overlap; **no agent/PM "verified" carries ship authority — only green CI gates** (eyeball additive). **Carve viz work out of the token-frugality budget** (route to strong model + self-see loop).

---

### PHASE B — Depth artifact: first-class, schema'd, separately gated (Stage B)

**Goal:** make "is the depth real" a machine-decidable object. **Closes dimension:** 13 (the structurally-homeless layer). **Maps to:** council-1781433195; net-new (no prior plan hosted it).
**Deliverables:**
- **Depth artifact = concept-keyed, schema'd lesson content type** with its OWN authoring/generation budget, **NEVER inferred from the figure** (depth is prose/proof/code, not a zoom level of a picture): invariant + why · complexity DERIVATION · correctness argument · edge cases · code/pseudocode.
- **Own correctness oracle:** invariant re-derivable from first principles; complexity derivation sound (no hand-waving); **code compiles + passes tests** (rides the Phase-G execution graders); edge cases enumerated and correct.
- **`neverificat` flag** until it passes its gate, and **excluded from every `mastery`/`faithful` UI signal** so an ungated depth artifact can never *look* trustworthy to a learner who can't tell.

**EXIT GATE:** depth gate green on the proof-set KCs (invariant re-derivable + complexity sound + code compiles/tests + edges enumerated); ungated depth provably carries `neverificat` and is suppressed from mastery/faithful UI (test flips one, asserts suppression); depth schema additive vs frozen signatures (INV-3.5).

---

### PHASE C — Pedagogical-quality estimator: the load-bearing teaching oracle (Stage C)

**Goal:** a machine test of *teaching* (not the artifact), computable without Alex. **Closes dimension:** 14 (the one oracle the whole stack lacks). **Maps to:** JUDGE Stage C; net-new.
**Deliverables:**
- Each KC carries an explicit **pre-misconception → target-conception delta** + a small bank of **discriminating CHECK items** (questions a wrong-model learner FAILS and a right-model learner PASSES).
- **Discriminating-CHECK gate (terminal):** asserts CHECK items actually separate wrong-model from right-model; don't separate → `neverificat`. *This is the pedagogy oracle geometry gates structurally cannot be.*
- **Checkable instructional invariants:** ATTEMPT precedes any answer-bearing content; CHECK targets the *stated* misconception (not a paraphrase); intuition-before-name; depth present-and-gated (Phase B); EC2 prereq-path complete.
- **LLM-last-never-alone** for any judgment; machine-uncertain serves `neverificat`, never fake-green.
- **Residual-risk wiring (JUDGE's strongest dissent):** real CHECK-beat miss-data (FSRS layer already captures it) feeds back as the empirical signal — the proxy is acknowledged as a proxy, not silently treated as ground truth.

**EXIT GATE:** discriminating-CHECK gate green on proof-set KCs (items provably discriminate); all instructional invariants green; no grader chain is LLM-judge-only (INV-6.3); a lesson cannot ship `faithful`/`mastery` unless its CHECK items provably separate models. **This gate is the precondition for opening generation.**

---

### PHASE D — Collapse the two UI surfaces (premium-by-default, structural) (Stage D)

**Goal:** premium is a structural property every generated lesson inherits, not per-lesson CSS or a human's taste. **Closes dimensions:** 11, parts of 4. **Maps to:** JUDGE Stage D + Plan W (warm theme).
**Deliverables:**
- **Promote the cinematic Lectie shell INTO production** `BeatOrchestrator`/`LessonFigureShell` (masthead, trust-badge, premium pips, dark figure stage, generous figure height). **Delete the demo-vs-prod split** (`/tutor/lectie-selectsort` + the `Lectie*Demo.tsx` files on disk).
- **Lift the 272px figure clamp;** verify at Alex's real **648px content height with VIEWPORT screenshots (not fullPage)** at 1536×864.
- **Experience-felt gate:** visible progress, monotone motion-toward-end-state, no dead beats (rides Phase-A frame gates).
- **Plan W (warm theme) here** so dual-skin baselines capture all 6 families at once (never re-baseline; W after the families are final).

**EXIT GATE:** zero demo-vs-prod split (grep: no `Lectie*Demo` route reachable in prod); no-clip + legibility green at 648px viewport-screenshot on real lessons; INV-9.5 (baseline = shell + 2 themes only); dual-skin (cool+warm) baselines green across all 6 families.

> **GATE OF GATES:** Phases A+B+C+D all green is the **hard precondition** for Phase E. Until then every generated lesson is potentially pedagogically wrong and undetectable — breadth is forbidden.

---

### PHASE E — Digestion / generation pipeline (Stage E; Plan 5 — breadth's engine, NOT breadth itself)

**Goal:** build the firehose to emit TYPED specs that ride the A–C gates (P2 EASY⇒GATED), behind the oracle. **Closes dimensions:** 1, 2, 3, 8. **Maps to:** Plan 5.
**Deliverables:**
- Upload door (file/URL) → **extraction-legibility gate FIRST** (promote `ContentValidator.checkExtractionLegibility` from dead code; garble→alt-extractor→OCR→park-as-gap) → **9-kind content classifier** (never filename) → per-kind extraction (R-* register: archetype, rubric, solution_present, datafiles, prereq, multiselect, errors) → dedup/provenance → **gap-ledger** (FILES-only grammar).
- **Generation emits co-manufactured learner-state objects:** beat content (5-beat templates per concept_type, RO) + typed figure spec (Phase A) + **depth artifact (Phase B)** + **misconception delta + discriminating CHECK items (Phase C)**. Generation that can't pass A/B/C → `neverificat`, never fake-green.
- **Checkpoint review screen** (§1.5): real lesson route embedded, per-gate green summary, approve/reject + one-line note, kind re-classify, requeue on reject.
- **Backup-first migration** (the remaining §3.6 steps: 3-column add already live; complete report_wrong provenance cols, beat fields, problem-bank/course-meta additive tables).
- **Near-term pragmatism (JUDGE):** semi-digest Alex's ~5 real subjects through the now-gated templates first; treat universal PDF→KC digestion as a gated stretch goal.

**EXIT GATE:** INV-2.1–2.5, INV-3.1–3.5, INV-1.3, INV-1.4 green on real corpus; checkpoint Playwright (§1.5 selectors + smoke) green; **every generated artifact rides A/B/C gates or carries `neverificat`** — zero fake-green; INV-9.1 (artifact-state total).

---

### PHASE F — Content across all 5 subjects (proof-set, THEN 140 URLs)

**Goal:** real verified content for every subject, born behind the oracle. **Closes dimensions:** 16, 15 (coverage). **Maps to:** Plan 7 proof-run + bulk upload.
**Deliverables:**
- **§10.2 proof-set:** one artifact per kind × concept-type (PA lecture, ALO chapter skeleton-trace, PS probabilistic + garble-recovery, seminar proof/step-trace, POO lab code-practice, POO past-exam mock+OCR, SORC fișă→registry, ALO Tema→deliverable, cheat-sheet, student-notes stricter-tier, grade-ledger) through full loop → checkpoint approval.
- **140 inventoried URLs** (ALO 25 · PS 59 · SORC 18 · PA 4 · POO 32) processed **sequentially through the per-artifact checkpoint** (bounded blast radius); synthetic-tag where no real past paper exists.
- **828-card RO re-verify pass** (each translated card re-faithful-checked per §3.7).

**EXIT GATE:** INV-1.1 (every servable row VERIFIED), INV-1.2 (serve-side refusal), every proof-set artifact has an `approved` checkpoint event; problem bank ≥1 row/subject (INV-6.1 flips green); figure-coverage INV-EC2.2; language INV-8.1–8.4 green on real served content.

---

### PHASE G — Guidance + practice + exam completion (the remaining serving surfaces)

**Goal:** finish the surfaces that consume the now-real content. **Closes dimensions:** 7, 9, 10. **Maps to:** Plans 6 (carry-overs) + 7.
**Deliverables:**
- **Unified FSRS+EWMA single-writer state** (all surfaces write via one service); forgetting→compressed re-lesson w/ fresh params; misconception→remediation requeue.
- **Priority queue** (proximity×weight×gap×prereq) + **readiness dashboard** vs verified grade-model gates.
- **ADHD session shape** (caps, break prompts, interleave cap, recovery mode); **calibration mounted**; **placement rebuilt to probe SUBJECT** (not tutor internals).
- **Practice carry-overs:** REQ-1 (queue moves mastery), R-MULTISELECT drills, execution graders R/Python/C++/Alk, CodeMirror editor.

**EXIT GATE:** INV-7.1–7.5, INV-6.1–6.6, INV-4.3/4.5, INV-EC3.1/.2 green on real DB; readiness + 5 practice surfaces Playwright green against real corpus.

---

### PHASE H — EC1–EC5 (experience completeness)

**Goal:** the experience-completeness layer atop real content. **Closes dimension:** 15. **Maps to:** §F of the ledger.
**Deliverables:** EC1 live-help (ask-in-lesson + prereq-peek, reuses sidekick, drill-self-paste guard); EC2 cold-start completeness gate (prereq-path to entry KC or `neverificat` stub + figure-coverage); EC3 recovery mode; EC4 goal/intent (decompose→re-weight, can't starve real deadline); EC5 provider health surface (admin-only, learner zero-setup).
**EXIT GATE:** INV-EC1.1–.3, EC2.1–.2, EC3.1–.2, EC4.1–.3, EC5.1–.3 green; EC1 affordances ride the Phase-A/D visual gate.

---

### PHASE I — Final hardening (whole-build adversarial council + full matrix)

**Goal:** prove 100% honestly before deploy. **Maps to:** §B matrix at full authority + mandatory whole-build council.
**Deliverables:** full per-gate matrix run on real DB (all-green or every amber carries `neverificat` + human-checkpoint record); INV-9.1–9.5 + gate-coverage + gate-self-test green; **whole-build adversarial council** (per-chunk reviews miss cross-chunk flaws — MANDATORY after a long autonomous run); seeded-violation drill per gate (INV-9.4).
**EXIT GATE:** matrix prints **honest 100%** = every spec clause gated · all PROOF green · every ESTIMATOR green-or-`neverificat`-flagged · §10 user-checkpoint passed — explicitly **NOT** "no human ever sees a defect." Zero SKIP/fail-open/cross-platform amber.

---

### PHASE J — ONE verified VPS deploy with live probe (DEPLOY-LAST)

**Goal:** the single, final, verified deploy. **Closes dimension:** 16 (deploy). **Maps to:** Plan-6 Task 15 (PM-gated).
**Deliverables:** backup→D9 PC→VPS sync of verified state; deploy the ~140-commit-ahead bundle; run **live-probe** on the real URL.
**EXIT GATE (feature-shipped, post-Slice-1/1.5 lessons):** open the live URL and confirm the user *sees* the feature — bundle-hash + tests-green ≠ shipped. All spec-listed `data-testid` selectors paint on first load; **zero 4xx/5xx during first paint**; click every interactive element → no `/404|HTTP \d{3}|not found|error/i` text, no new 4xx/5xx; authenticated SPA smoke green. Only then is any feature "shipped."

---

## 4. WHAT THIS SUPERSEDES IN THE EXISTING LEDGER

- **The headline re-sequence:** the ledger's `V → 5 → 7` (figures → digestion → tracking) is **REPLACED** by the JUDGE's **A → B → C → D → E → F → G → H → I → J** (figure-oracle → depth → pedagogy-oracle → UI-collapse → digestion → content → serving → EC → hardening → deploy). **Plan 5 is moved behind three new oracle phases** and may not start until A+B+C are green.
- **Two net-new first-class stages inserted** that the old ledger never named as gated pipeline stages: **Phase B (depth artifact)** — was "folded into Plan 5 as a content type"; now its own schema'd, separately-gated stage with its own oracle, suppressed from mastery/faithful UI until green. **Phase C (pedagogical-quality estimator)** — *did not exist anywhere*; it is the load-bearing teaching oracle the whole stack lacked.
- **Plan V is reframed** from "build 5 families" to "Stage A figure-**correctness** loop": generate-from-algorithm replaces hand-authored JSON; the fitness gate + frame gates + mergesort re-bind (delete `FigureBindingNonVacuityTest.kt:72`) are now in-scope. Reality note: families graph/tree, seq/array, chart/dist, matrix/grid already exist on disk — Plan V is mid-flight, not "pending recon."
- **Plan W (warm theme) moves into Phase D** (UI collapse) so dual-skin baselines are captured once over the final family set.
- **Phase 0 (foundation + compliance matrix) is pulled to the FRONT** — the §B foundation defects (Impeccable→blocking, schema-hash parity, relay backoff, StateCache, Linux baselines, deploy.sh smoke) + the matrix/coverage/self-test gates must exist *before* any later gate carries authority. The old "build the matrix late as a meta-layer" is rejected.
- **Deploy (Task 15) stays LAST and singular** — confirmed not incremental; one verified deploy with a live probe after honest-100% is proven locally.
- **Retired mechanisms (carry forward as supplements only, never primary):** the §10.3 "caught-defect → new gate" accretion loop (presumes a catcher Alex can't be); the delegate-to-Opus-then-spot-check "verified" loop (replaced by generate→gate→eyeball-once); CSS-reskin as the quality-lift strategy (families own premium by construction); agent/PM "verified" as ship authority (only green CI gates ship).

---

## 5. THE FEW HARD RISKS + mitigation

1. **Silent-wrong-teaching is the BASE RATE of LLM generation, not the exception** (risk-analyst's framing) — and it lands hardest on the depth layer (invariants, complexity), carrying a false `mastery` straight into an exam. **Mitigation:** Phase C's discriminating-CHECK gate is a machine test of teaching-effect computed without Alex; depth (Phase B) carries `neverificat` and is excluded from any mastery/faithful UI until its own oracle passes — an unverified artifact can never *look* trustworthy. No lesson ships `faithful`/`mastery` unless CHECK items provably separate wrong-model from right-model.

2. **Breadth-before-oracle trap** — scaling Plan 5 across 6 subjects before the oracles exist multiplies the §6 never-sorts failure 6×, retrofitting impossible after wrong lessons ship. **Mitigation:** the hard sequencing rule — Phase E (digestion) is gated on A+B+C green; the firehose is *born behind* the oracle. Per-artifact sequential checkpoint (INV-1.3) bounds blast radius to ≤1 artifact even after launch.

3. **Depth-artifact homelessness** — the figure-family architecture structurally cannot host depth (prose/proof/code is not a zoom level of a picture); folding it into figures would re-create the §6 class at the depth layer. **Mitigation:** Phase B makes depth a separate first-class schema'd stage with its OWN oracle and authoring budget, inheriting *zero* figure verification.

4. **The deploy gap** — live serves SESSION-66; local is ~140 commits ahead and undeployed; push ≠ deploy; bundle-hash + green tests ≠ shipped (the Slice-1 ghost-component + Slice-1.5 PDF-404 lessons). **Mitigation:** Phase J is one verified deploy *after* honest-100% locally, with a live-probe that opens the real URL, asserts every spec'd `data-testid` paints, asserts zero 4xx/5xx on paint and on every interactive click. No feature is "shipped" until the user sees it live.

5. **Residual risk the JUDGE flags explicitly (strongest dissent, worth holding):** even a passing pedagogical estimator measures a *proxy for the artifact's discriminating power*, not the actual belief change — a check item could rubber-stamp "renders perfectly, teaches a falsehood." **Mitigation:** Stage C is built on the *discriminating-check* formulation (closest computable proxy to "the belief moved") rather than a generic LLM-judge score; real CHECK-beat miss-data from the FSRS layer feeds back as the empirical signal. This is named as a known residual, not closed — and it is categorically better than the artifact-only stack that exists today.

---

**Key file paths (all absolute):**
- Binding council: `C:\Users\User\jarvis-kotlin\build-review\2026-06-15-teaching-pipeline-council-JUDGE.md`
- Single remaining-work ledger (this plan supersedes its sequence): `C:\Users\User\jarvis-kotlin\docs\superpowers\plans\2026-06-13-remaining-work-to-100.md`
- Plan index: `C:\Users\User\jarvis-kotlin\docs\superpowers\plans\2026-06-11-one-pass-plan-index.md`
- Binding spec: `C:\Users\User\jarvis-kotlin\docs\superpowers\specs\2026-06-11-one-pass-digestion-teaching-engine-design.md`
- Frozen signatures (canonical-on-conflict): `C:\Users\User\jarvis-kotlin\docs\superpowers\plans\2026-06-02-interface-signatures-lock.md`
- Mergesort wrong-binding test to delete (Phase A): `C:\Users\User\jarvis-kotlin\src\test\kotlin\jarvis\content\FigureBindingNonVacuityTest.kt` (lines 72–87 pin `pa-kc-002 → viz-pa-mergesort-001` on `graph-tree`)
- Viz families on disk (Phase A): `C:\Users\User\jarvis-kotlin\tutor-web\src\components\viz\families\` (GraphTree, SequenceArray, ChartDistribution, MatrixGrid, SortMerge + `familyRegistry.ts`; `Lectie*Demo.tsx` = Phase-D collapse targets)
- Live DB (read-only verified this pass): `~/.jarvis/tutor.db` — 8 `kc_verification_status` rows (status column = `status`), 828 fsrs_cards, 0 confidently kc_id-linked

**Reality correction vs inventory:** HEAD is `9c48d1d` (1040 commits), not `09f5add`/1035 — 5 commits ahead; Plan V is mid-flight (4 families already built), not "pending recon." The phase plan above accounts for this (Phase A *completes* the family set + adds the generate-from-algorithm/fitness/frame gates rather than starting from family 1).