# THE END-TO-END MASTER PLAN — Tutor: LOCAL→100%-SPEC→DEPLOY (v2, PRISTINE)

**Supersedes** `2026-06-15-end-to-end-master-plan-to-100.md` (v1). v1's structure is preserved (oracle-first/breadth-last phase order, the per-dimension acceptance matrix, KEEP/RETIRE, the named risks); every defect in the adversarial audit `build-review/2026-06-15-master-plan-audit.md` is applied here — all 37 confirmed findings closed via its §6 12-step delta, the 5 §7 residuals named on the board. **Binding direction:** council JUDGE (`build-review/2026-06-15-teaching-pipeline-council-JUDGE.md`, confidence 9/10). **Binding spec:** `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md`.

---

## 0. THE LOCKED CONCEPTUAL FOUNDATION (Alex-ratified — the framing every phase below answers to)

**"100% to spec" = FOUR things, together. None substitutes for another; the board prints 100% only when all four hold.**

1. **The SYSTEM is fully up to spec.** Every pipeline / teaching / UI-UX / gate capability the spec defines is built AND gated. (Dimensions 1–12, 15; the gate substrate, Phase 0.)
2. **FULL COURSE COVERAGE.** *Every* concept in the courses is taught, not a curated subset. (Dimension 16; Phase F.)
3. **LAYER-1 — THE TRUTH — is 100% PURE / faithful.** The course material itself (definitions, theorems, methods, derivations, complexity, code) is exactly what the course says — never hallucinated, never wrong. **HARD-GATED as a PROOF lane** (trust-net faithfulness + depth-correctness). The bar is purity. An unverified course-FACT is the **RARE exception**, and the entire investment goes into making that exception ~never occur. When it does: a **visible, non-dismissible `verifică sursa` flag** — never hidden, never wearing a verified badge, **counted on the board**.
4. **LAYER-2 — THE TEACHING — must SERVE the truth, never distort it.** The scaffolding on top of the pure truth (intuition-first framing, visuals, analogies, plain words, predict→reveal) is generated/creative material that makes the truth digestible. *"Does this scaffolding actually teach?"* is the **HONEST ESTIMATOR** — the one thing no machine can fully prove without a live learner. It is **named as a residual** (§HONEST RESIDUALS), never dressed as proof.

### 0.1 The two-layer separation is the KEY architectural fix (it routes the audit's two demands to the right places)

The audit's two pressures pull in opposite directions, and v1 conflated them. v2 separates them **explicitly**, so each dimension gets the right KIND of gate:

| | **LAYER 1 — TRUTH** | **LAYER 2 — TEACHING** |
|---|---|---|
| What it is | course facts: defn · theorem · method · derivation · complexity · code | scaffolding: framing · figure · analogy · predict→reveal · plain words |
| Gate KIND | **PROOF-DEMANDED** (decidable, hard) | **ESTIMATOR-ACCEPTABLE** (proxy + named residual) |
| Audit demand it answers | F27/F30/F8 purity rigor: zero-unverified target, flagged-and-counted exceptions | F7/F41/F43 honesty: it's only an estimator — *name it*, never proof-label it |
| Fallback when machine can't prove | **`verifică sursa`** non-dismissible flag, excluded from mastery, **counted** (Alex policy) | serves `neverificat`; the residual is named on the board; experience-checkpoint is the human leg |
| "Done" definition | Layer-1 aims for **zero unverified**; any flagged exception is **visible + counted**, never hidden, never a verified badge | a **named estimator residual** — green means "every estimator green-or-`neverificat`-flagged AND every residual displayed", never "teaching proven" |

*This table is the TYPICAL pairing, NOT a bijection.* **LAYER (1/2/SYS) and TAG (PROOF/ESTIMATOR) are TWO independent stamps** carried per-clause; the TAG is derived per-clause from the spec text, NOT mechanically read off the LAYER. The live counter-examples are in the §2 matrix: dim-12 is **L2 · PROOF** (decidable RO-ness — a Layer-2 clause that IS hard-gated), dim-13 **L1 · split**, dim-15 **SYS · mixed**. Read the "Gate KIND" row as the default, and the per-clause TAG column in §2 as authoritative on conflict.

*On the flag STRINGS (`verifică sursa` / `neverificat`):* the spec uses these as ONE paired flag across both layers (spec line 611 the pair; line 631 the pair on a Layer-1 stub; line 656 `neverificat` alone on a Layer-1 readiness item), and the shipped, copy-pinned `TrustBadge.tsx` renders literal `neverificat` for any unverified status. This document leans `verifică sursa` toward Layer-1 truth and `neverificat` toward Layer-2 teaching as a *readability* convention only — **it is NOT a reserved per-layer string contract** (the spec uses them interchangeably; the real distinction is the per-clause PROOF/ESTIMATOR tag, not which half of the pair appears). Treat the pair as one spec flag distinguished by the layer/TAG, never by the string.

**The PROOF-vs-ESTIMATOR tagging principle (binding for the whole document):** every spec clause / INV-* in the coverage registry (§Phase 0) carries a tag — **PROOF-DEMANDED** (a Layer-1 truth clause; a hard decidable gate must back it; an estimator backing it = RED on coverage, audit F43) or **ESTIMATOR-ACCEPTABLE** (a Layer-2 teaching clause; an estimator + `neverificat` fallback is legitimate, but the gate may never wear a PROOF/"provably"/"GATE-OF-GATES" label). The matrix prints each clause's tag beside its status. **This is the meta-finding of the audit (§5 CLASS 1) closed:** the plan's honesty machinery (P1: PROOF-gate OR estimator+`neverificat`+checkpoint) is now wired *through* the two oracle dimensions (depth = Layer 1; pedagogy = Layer 2) it exists to protect.

### 0.2 Alex's two policy decisions (applied verbatim everywhere downstream)

- **Unverified depth/derivation (a Layer-1 truth item the machine couldn't prove):** the goal is it **NEVER gets there** — invest to make Layer-1 provable ~always. The RARE fallback is **RENDER BEHIND A NON-DISMISSIBLE `verifică sursa` WARNING** — *not* withhold (the learner needs the material taught), *not* badge-strip-but-serve-silently. Excluded from mastery; the warning is **DOM-asserted**. Content is never silently withheld and never silently served-as-truth. (Closes audit F30 with Alex's chosen horn (b).)
- **"100%" is NOT zero-content purity-theatre.** Served content may carry a visible flag in the rare unverified case, but it is **COUNTED + DISPLAYED, never hidden**. The v1 formula "neverificat green-or-flagged = 100%" is **DELETED**. Replaced by: **Layer-1 truth = zero-unverified target, flagged exceptions visible+counted; Layer-2 teaching-effectiveness = a named estimator residual.** (Closes audit F27.)

**THE SERVABLE-VIEW SEAM — single coherent definition (binding for INV-1.1 everywhere downstream; reconciles Phase F line 224 ⇄ Phase I line 256).** The spec's literal INV-1.1 query (spec §1.6 line 75: `SELECT COUNT(*) FROM <servable view> WHERE verification_status != 'VERIFIED'` returns 0, real DB) and Alex's horn-(b) "render the rare unverified row behind a `verifică sursa` warning" only coexist if `<servable view>` is scoped — the spec's own model already implies the split (§15.2 line 631: a flagged stub is a *placeholder in the served graph*; INV-EC4.3 line 656: per-goal readiness counts only "VERIFIED-servable KCs (INV-1.1)" and surfaces "unverified KCs … as `neverificat`" *separately*). Wired here as ONE definition:
  - `<servable view>` (the INV-1.1 query's domain) = the **admission-gated VERIFIED set ONLY** — rows that passed faithful-check (spec §1.2(b)). The literal `WHERE verification_status != 'VERIFIED'` query runs over THIS view and **stays = 0** (a true `0 == 0` decidable gate, never relaxed).
  - A `verifică sursa`-flagged-rendered row (horn (b)) lives on a **DISTINCT `flagged-servable` surface that the INV-1.1 query EXCLUDES.** It is rendered (the learner needs it taught), DOM-asserted with the non-dismissible warning, excluded from mastery — and is **NOT** inside `<servable view>`, so it never reds INV-1.1 and never wears VERIFIED.
  - **NET-NEW separate invariant** `INV-1.1b`: `COUNT(flagged-servable rows) <= the displayed per-dimension budget` (Layer-1 truth target = **0**; the budget is itself printed on the board). Flagged ≠ done: a flagged-servable row is RED-on-coverage for Layer-1, and the board prints 100% for Layer-1 only when this count is 0 OR at/below the displayed budget.

### 0.3 Governing law (carried from v1, unchanged)

**P1 — NO-SPEC-CUT:** every requirement is PROVEN by a green decidable gate OR an ESTIMATOR + the §1.4 human experience-checkpoint with an explicit `neverificat` flag — never silently dropped, never fake-green. **P2 — EASY⇒GATED:** every content type ships a `typed input → renderer/template verified once → auto-gates ride free` path. **The learner cannot vet content** (no-oracle-inversion) → every quality claim is machine-checkable without Alex; the experience-checkpoint judges *experience*, never *truth*.

---

## 1. WHERE WE ARE (honest, grounded against committed HEAD + live DB)

**Ground truth, re-verified this pass (audit F14/F19/F17/F24/F25/F37 corrections applied):**

- **Committed HEAD = `9c48d1d`** (1040 commits). The live VPS serves the **legacy SESSION-66 bundle**; HEAD is ~140 commits ahead and **entirely undeployed** — push ≠ deploy; nothing built since SESSION-66 is visible to a real user.
- **Built + CI-green on `main` (the KEEP spine the council ratifies):** Plan 1 (trust-net ON: backup gate, restore drill — live DB read-only this pass shows **8 `kc_verification_status` rows: 4 `faithful`, 4 `uncertain`**), Plan 2 (7 schema tables + `concept_type` enum + beat schema; **828 fsrs_cards, 0 with non-null `kc_id`** — the backfill is NOT done), Plan 3 (BeatOrchestrator 5-beat deck + closed BeatSelector + per-beat grading), Plan 4a/4b (Impeccable fail-open + self-hosted fonts + rendered legibility/interaction/language gates, caught 2 real defects), Plan 6 (5 practice surfaces + the 4-leg grader stack including R/Python/C++/Alk execution graders — **already built + CI-green**).
- **Viz families — HONEST committed state (audit F14):** at committed HEAD **only 3 families are tracked + green** — `GraphTreeFamily`, `ChartDistributionFamily`, `SequenceArrayFamily` (with trace-match harness + seeded-wrong fixtures). `MatrixGridFamily.tsx`, `SortMergeFamily.tsx`, their tests, the trace/invariant helper modules (`matrixGridTrace.ts` / `arrayMergeTrace.ts` / `matrixGridInvariants.ts` / `mergeInvariants.ts`) + seeded-wrong fixture, the `familyRegistry.ts` edit, and `SequenceArrayFamily.tsx`'s **+872/−53** (verified `git diff --stat` this pass) are **uncommitted WIP** (untracked or modified). Four `Lectie*Demo.tsx` files are **untracked**. This is **WIP, unverified** — not "well underway", not "5 families landed."
- **Content census (audit F19):** **7 real KCs across 2 of the 5 subjects** — PA `pa-kc-001..006` (6) + ALO `alo-kc-gauss-elim` (1); plus 2 PA fixture KCs. **POO / PS / SO-RC have ZERO KCs** (`.gitkeep` only). `subjects.yaml` lists exactly 5 subjects. Zero existing content carries any depth / discriminating-CHECK / misconception-delta field.

**The structural gap (council verdict NO):** the entire digestion/generation half (spec §2–3, ~50% of spec) is **zero-code**; there is **no machine oracle for "does this lesson teach"** (every gate measures the artifact — geometry / no-clip / faithful-to-source / language — none the learned effect; the spec-§6 mergesort-that-never-sorts passed *every* gate); the **DEPTH artifact (Layer-1 truth depth) is unnamed/unschema'd/ungated/structurally homeless**; and the human checkpoint is held by a learner who by definition cannot fill the truth role. **The spine is good; the firehose and its two oracles do not exist; nothing is live.**

---

## 2. DEFINITION OF 100% — the per-dimension acceptance contract, LAYER- and TAG-stamped

Each row: *done = 100%* + the decidable GATE that proves it + its **LAYER** (1=truth / 2=teaching / SYS=system-capability) + its **TAG** (PROOF-DEMANDED / ESTIMATOR-ACCEPTABLE). The board prints a clause green only when its gate is green AND (if ESTIMATOR-ACCEPTABLE) every residual is displayed; an ESTIMATOR backing a PROOF-DEMANDED clause = **RED on coverage** (audit F43).

| # | Dimension | Layer · Tag | done = 100% | GATE that proves it (decidable) |
|---|---|---|---|---|
| **1** | Upload / Ingest + Extraction-legibility | SYS · PROOF | File+URL drop; 140 URLs ride one sequential pipeline; legibility FIRST, garble→alt-extractor→OCR→park | INV-2.2, INV-2.3, INV-1.4; **INV-9.2 retry-boundedness** (F5) |
| **2** | Classify (9 kinds, subject, exam-tag, SO/RC split) | SYS · PROOF | By content never filename; every artifact carries subject+kind+exam-tag; SORC domain tag; seminar⇒PA/ALO/PS | INV-2.1, INV-2.4, INV-2.5 |
| **3** | Digest → KCs + beats + problems + rubrics + dates | SYS · PROOF | Every KC has `concept_type`∈enum + 5 beats RO; problem bank + rubrics + grade-registry + exam_dates; backup-first migration; **`fsrs_cards.kc_id` backfilled (0/828→linked)** (F25) | INV-3.1, INV-3.2, INV-3.3, INV-3.4, INV-3.5; **identity/consent migration-survival** (F32) |
| **4** | 5-beat form + concept_type→variant + I/we/you-do | SYS · PROOF | BeatOrchestrator replaces 3-step; 4 legal plans only; dwell-floor; variants per concept_type; completion writes attempt+EWMA+FSRS+first-encounter; drill handoff | INV-4.1, INV-4.2, INV-4.3, INV-4.4 |
| **5** | Figures (6 families, SEMANTIC gates, SVG-only) | L1 · PROOF | Figure = (family, instance); render verified once/family; **generated FROM algorithm execution**; fitness fail-closed | INV-5.1–5.5 + **semantic-state-delta frame gates** (F6/F13, §Phase A) |
| **6** | Trust-net / faithfulness (Layer-1 admission) | L1 · PROOF | Admission-gate + always-on serve-side refusal; coverage→beat fields + figures + instances; missing dep = RED; INV-1.1 query scoped to `<servable view>`=VERIFIED-set-only (§0.2), flagged rows on distinct `flagged-servable` surface | INV-1.1 (over `<servable view>`), **INV-1.1b** (flagged-servable ≤ budget, NET-NEW), INV-1.2, INV-9.3 |
| **7** | Guidance (priority, readiness, ADHD, recovery) | SYS · PROOF | Priority = proximity×weight×gap×prereq; readiness vs verified gates; caps; recovery; placement probes SUBJECT | INV-7.3, INV-7.5, INV-EC3.1/.2, INV-EC4.3; **placement-`entry_phase` + calibration-telemetry test (INV-7.6/7.7 — NET-NEW, spec §7.6 defines only 7.1–7.5; registered PLANNED in the coverage registry)** (F4) |
| **8** | One human experience-checkpoint | L2 · ESTIMATOR | Per-artifact sequential; machine-green→STOP→render real lesson→approve/reject; approve→reference-pattern | INV-1.3 + checkpoint Playwright (§1.5) — **judges experience, NOT truth (residual #5)** |
| **9** | Practice (drills, code-exec grading, mock+OCR) | SYS · PROOF | 5 surfaces; grader LLM-LAST-never-alone; exec graders R/Py/C++/Alk **wired to populated bank**; mock mirrors real papers | INV-6.1, INV-6.2 (on real corpus), INV-6.3, INV-6.4, INV-6.5, INV-6.6 |
| **10** | Tracking (unified FSRS+EWMA, triggers) | SYS · PROOF | One per-KC state; single writer; forgetting→re-lesson fresh params; misconception→remediation requeue | INV-7.1, INV-7.2, **INV-7.4** (re-lesson trigger — replaces phantom INV-4.5, F3) |
| **11** | UI/UX (premium-by-default, ADHD, no-clip, **a11y**) | SYS · PROOF | Cinematic shell IS production; scrubber on every ③; no-clip @648px viewport; Impeccable blocking; **keyboard-traversal + RO-aria-live** (F35) | INV-4.4 + INV-5.3 + §9.2 gate-4 + INV-9.5 + **@648px VIEWPORT screenshot** + **a11y gate** (Phase D) |
| **12** | Language (RO content, EN meta) | L2 · PROOF | All teaching generated in RO; quoted spans in-source; validator = admission gate; 828-card RO re-verify | INV-8.1, INV-8.2, INV-8.3, INV-8.4 *(decidable RO-ness — PROOF even though Layer-2)* |
| **13** | **Depth artifact (Layer-1 TRUTH depth — first-class)** | **L1 · split** | Separate concept-keyed artifact (invariant+why · complexity DERIVATION · correctness · edges · code); NOT a figure skin; own oracle | **Phase-B split gate (F8):** PROOF leg = code compiles+tests; ESTIMATOR legs = complexity-soundness / invariant-rederivability / edge-completeness → `verifică sursa` until grounded; per-leg mastery suppression |
| **14** | **Pedagogical quality (Layer-2 TEACHING oracle)** | **L2 · ESTIMATOR** | KC carries pre-misconception→target-conception SET + discriminating CHECK items; instructional invariants hold | **Phase-C split gate (F7):** PROOF legs = code-runs N/A; distractor↔misconception-id binding, non-paraphrase, ATTEMPT-precedes-answer; ESTIMATOR leg = "separates models" → `neverificat`. **NOT a PROOF, NOT the GATE-OF-GATES** |
| **15** | EC1–EC5 (experience completeness) | SYS · mixed | Live-help; cold-start completeness; recovery; goal/intent; provider health | INV-EC1.1–.3, EC2.1/.2, EC3.1/.2, EC4.1/.2/.3, EC5.1/.2/.3 |
| **16** | Content coverage (all 5 subjects) + Deploy | L1+SYS · PROOF | 9 kinds × concept-types in proof-set; 140 URLs through checkpoint; **synthetic-tag fraction quantified per subject**; ONE verified VPS deploy w/ live probe | INV-9.1, proof-run green on real corpus, **deploy live-probe** |

**Two cross-cutting compliance gates** (govern all of the above):
- **gate-COVERAGE invariant** — every spec clause / INV-* is **REGISTERED with a status** (LIVE-GREEN / PLANNED-in-phase-X / RED-unmapped) **and a PROOF/ESTIMATOR tag** (audit F26 + F43); **zero UNMAPPED**; a PROOF-DEMANDED clause backed only by an estimator/`neverificat` lane = RED.
- **gate-self-test** (INV-9.4) — every gate fed a known-bad fixture asserts RED; **requires ≥1 NATURALLY-occurring seed per gate** (e.g. the un-rebound mergesort as the live never-sorts seed), and the Phase-0 exit explicitly states self-test-green proves **liveness, not coverage** (audit F10).

The **per-gate MATRIX** (named board; LAYER + PROOF/ESTIMATOR tags per clause; SKIP / fail-open / cross-platform = RED) replaces any single GREEN/RED bit.

---

## 3. THE PHASES (oracle-first, deploy-last)

Hard ordering (JUDGE): **Phase 0 (gate substrate) → A (figure-correctness oracle) → B (depth/Layer-1-truth artifact + gate) → C (pedagogy/Layer-2 estimator) → [SPIKE: generator capability] → D (UI collapse) → E (digestion engine) → F (content across subjects) → G (guidance/practice/exam) → H (EC1–5) → I (hardening) → J (one deploy).** Plan 5 (breadth's engine) MUST NOT start until A+B+C are green **at their EARLY gate** (§3 line 89 — the EARLY half is the generation-precondition; LATE fires after a first E/F slice) **and the capability spike has measured the generator**.

**The circular dependency, BROKEN (audit F2):** each oracle phase (B, C) ships in TWO gated halves — **EARLY** (oracle BUILT + self-test green on seeded fixtures + green on the ≤7 real KCs **once their depth/CHECK bank is authored IN-SCOPE in that phase**) and **LATE** (oracle green on the full spec-§10.2 proof-set, fired **after a first E/F slice produces it**). The content the oracle judges no longer has to exist before the oracle. Current Phase E is relabeled **E-bulk** to make this explicit.

---

### PHASE 0 — Foundation-defect repair + compliance harness (UNBLOCKER)

**Goal:** make the gate substrate trustworthy so every later "green" means something. **Closes:** the foundation defects + the matrix machinery.

**Deliverables:**
- **Impeccable → blocking** (re-calibrate on real lint hits, pin version, flip the `|| true` fail-open guard).
- **Cross-language schema-hash CI parity** (python `db-backup.py` vs Kotlin `MigrationBackupGate.liveSchemaHash`).
- **Relay retry/backoff** on the VerificationRunner relay path (closes the `pa-kc-006` false-negative class).
- **StateCache per-test isolation** (kills the ~1-in-2 concurrent flake).
- **Linux visual-baseline regeneration** (baselines were Windows-only; CI runs Linux).
- **Per-gate MATRIX rollup** (named board; LAYER + PROOF/ESTIMATOR tags) + **gate-COVERAGE invariant as a STATUS-TAGGED REGISTRY** (F26): every spec clause/INV is registered with status (LIVE-GREEN / PLANNED-in-phase-X / RED-unmapped) **and a PROOF-DEMANDED vs ESTIMATOR-ACCEPTABLE tag derived from the spec text** (F43); zero UNMAPPED; a PROOF clause backed only by an estimator reds.
- **gate-self-test** (INV-9.4) with **≥1 natural seed per gate** + the "liveness not coverage" exit wording (F10).
- **Standing adversarial red-team step** ("same defect-CLASS escapes twice = pipeline design bug").
- **Security note (F33):** the deployed auth surface (magic-link tokens, `jarvis_session` cookies, CSRF, consent gating) is **ungated-by-PLAN** — spec §A.3 #17 scopes auth out, so the coverage invariant must **flag it as security-debt, not launder it green**. A substantial CI security suite already exists (`MagicLinkRepoTest`, `CsrfTest`, `AuthVerifyRouteTest`, `CrossTenantIsolationTest`); the residual is **CSRF-totality + cookie Secure/SameSite + session-fixation** tests. Register this as a flagged RED-unmapped cell with a named owner, never absorbed into 100%.

**EXIT GATE:** Impeccable blocking-green; schema-hash CI test green cross-platform; matrix prints all-green with zero SKIP/fail-open; the coverage **registry** is complete (zero UNMAPPED, every clause status+tag stamped); every existing gate passes its own self-test on a natural seed. The registry is the deliverable here — **NOT full coverage** (most subsystems are PLANNED-in-phase-X at this point).

---

### PHASE A — Figure-correctness loop (the Layer-1 figure oracle)

**Goal:** figures are a deterministic function of algorithm execution, gated fail-closed on the **semantic algorithm state**, not pixels. **Closes:** dimension 5 (+ the never-sorts class). **Decomposed into A1–A4 (audit F22).**

**A0 — commit + green-gate the WIP (audit F14, MUST precede the rest):** commit the FULL untracked + modified set (verified `git status --porcelain` this pass), not a partial slice — a partial commit leaves dangling imports the A0 vitest gate would red:
  - **New family modules (untracked):** `tutor-web/src/components/viz/families/MatrixGridFamily.tsx`, `SortMergeFamily.tsx`.
  - **Untracked oracle/invariant helper modules:** `families/matrixGridInvariants.ts`, `families/mergeInvariants.ts`, `families/__tests__/matrixGridTrace.ts`, `families/__tests__/arrayMergeTrace.ts`, the test `families/__tests__/arrayMergeTrace.test.ts`, `families/__tests__/seededWrongMatrixGrid.test.tsx`, and the seeded-wrong fixture `families/__tests__/fixtures/seeded-wrong-matrix-grid.yaml`.
  - **Modified (already tracked):** the `SequenceArrayFamily.tsx` delta (**+872/−53** per `git diff --stat` this pass — corrects the v1/audit "+823"/"+876" slips), the `familyRegistry.ts` edit, plus modified `GraphTreeFamily.tsx` / `ChartDistributionFamily.tsx` / `AlgoStepperShell.tsx` / `VizDemoPage.tsx` and the modified tests `__tests__/GraphTreeFamily.test.tsx` / `__tests__/traceMatchHarness.test.tsx`.
  - **Untracked demo files** `families/LectieMergeSortDemo.tsx` / `LectieSelectSortDemo.tsx` / `LectieSelectSortBarsDemo.tsx` / `LectieSelectSortHelpDemo.tsx` are deleted in Phase D (demo-vs-prod collapse), NOT committed as production here.

  Run the full vitest viz suite **foreground + streamed to a log** (never `| tail`); treat them as a foundation ONLY once tracked + green. (BRIDGE-HEAD warns "never `git clean` / branch-switch on main" — untracked families are one `clean` from deletion.)

- **A1 — Trace→keyframe engine + Kotlin→React typed-event serialization contract.** `run(algorithm, input) → typed state/event stream → keyframes`. The serialization contract (the BRIEF "Open space") is net-new design, named here. **RETIRE hand-authored per-step STATE/keyframe JSON.** *Scope honesty (F16):* algorithm execution generates the STATE/keyframe geometry; the **per-step callout prose remains generated content** that must pass the language gate + the Phase-C pedagogy oracle (NOT verified by trace-match). Callout text is a gated field, not algorithm-derived.
- **A2 — Build the two genuinely-missing families to the full contract:** **timeline/protocol** + **state-machine/code-trace** (spec §5.2; from-scratch §5.3/§5.4 work).
- **A3 — Retrofit generate-from-algorithm + the frame gates across the existing families** (graph/tree, seq/array, chart/dist, matrix/grid — all hand-authored-instance today). **Sort/merge (F18) — DETERMINATE:** **fold sort/merge into the `sequence/array` family** — spec §5.2 assigns "sorting states" to sequence/array, so this is the single spec-faithful in-scope directive (a standalone sort/merge family is an off-spec 7th family). *(Out-of-scope escape hatch only: if a spec-owner later decides §5.2 should carve sort/merge into its own family, that is a PM/Decision-Log amendment to §5.2 — NOT an executor choice and NOT an alternative this plan offers; the executor is READ-ONLY and folds.)* Surface timeline/protocol + state-machine/code-trace as the two genuinely-missing families with their own build+gate tasks.
- **A4 — Migrate the 24 primitives** (17 zero-prop re-expressed as instance data; 2 anchorless = render-logic-only; RecursionTree→graph/tree, exit vizRegistry).

**Named CI frame gates (audit F6/F13 — SEMANTIC, not perceptual):**
- **`every-frame-advances` over the typed algorithm-STATE delta** (NOT a perceptual hash): assert each **non-hold step mutates the state vector**. (Closes the divide-frames-frozen leak: spec-§6 mergesort has four `phase:'divide'` frames where the array is byte-identical and only brackets move — a perceptual hash PASSES while sorting state is frozen.)
- **Structural-only frames tagged `hold:true`** under a **max-consecutive-holds cap** (e.g. `phase=='divide'` partition frames).
- **`final==end-state`** (final frame reaches the taught end-state, e.g. `final==sorted(input)`).
- **`no-regression` = distinct-frame-count AND per-family invariant-coverage must NOT decrease vs the committed incumbent** (F13 — perceptual-diff is secondary, never the sole signal).
- **Blocking per-family visual gate** (council-1781391707): zero pairwise text-on-text overlap + nothing under fixed chrome, at 1536×648 AND ×730, theme-token conformance; pin `lectie.html` as visual reference. **Name the 3 stale 1280-viewport specs** (`family-no-clip`, `seq-array-no-clip`, `chart-dist-no-clip`) as **fail-the-build-until-migrated** (F9) so old green specs can't mask the gap.

**concept_type → family FITNESS gate (fail-CLOSED):** family must express the concept's defining dynamic AND the final frame reaches the taught end-state; no fit → HALT + "needs new family" backlog (never silent fallback).

**Mergesort re-bind (audit F12 — ATOMIC):** re-bind `pa-kc-002` off `graph-tree` onto an animated stepped seq-array/merge instance whose **final frame == sorted(input)**, as one atomic edit: update `content/PA/kcs/pa-kc-002.yaml` (currently binds `viz-pa-mergesort-001`/`graph-tree`) + author/re-target the new viz instance + **delete the wrong-binding test** `FigureBindingNonVacuityTest.kt` lines **72–89** (the `pa-kc-002 has the mergesort figure binding` test — corrected cite, v1 said 72-87). Keep graph-tree only for the "count log n levels" complexity beat. Add **"pa-kc-002 serves a sorting figure whose final frame == sorted(input)"** to the Phase-A exit gate.

**EXIT GATE:** A0 committed + green; INV-5.1–5.5 green on **every live instance in the served corpus (`content/*/viz/` loaded by ContentRepo — there is no viz table in `tutor.db`, audit F37)**; semantic frame gates red a pixels-moved-but-state-frozen frame (self-test on the divide-frames seed); fitness gate fail-closed verified; mergesort re-bound atomically + wrong-binding test deleted; all 6 families pass trace-match + no-clip + visual-overlap. **No agent/PM "verified" carries ship authority — only green CI gates** (eyeball additive). Carve viz work out of the token-frugality budget (strong model + self-see loop).

---

### PHASE B — Depth artifact: first-class Layer-1 TRUTH, schema'd, split-gated

**Goal:** make "is the depth real" a machine-decidable Layer-1 object. **Closes:** dimension 13. Net-new (no prior plan hosted it).

**Deliverables:**
- **Depth artifact = concept-keyed, schema'd lesson content type** with its OWN authoring/generation budget, **NEVER inferred from the figure** (depth is prose/proof/code, not a zoom level of a picture): invariant + why · complexity DERIVATION · correctness argument · edge cases · code/pseudocode. **Depth schema additive vs frozen signatures** (INV-3.5).
- **EARLY authoring (breaks F2):** **author the depth bank for the ~7 existing real KCs IN-SCOPE here** (PA `pa-kc-001..006` + ALO `alo-kc-gauss-elim`) before the EARLY exit gate fires — named as in-scope effort, not a free rider on the Phase-E firehose.
- **Split correctness oracle (audit F8 — Layer-1 truth gets PROOF where decidable, `verifică sursa` where not):**
  - **PROOF leg (decidable):** the **code compiles + passes tests** (rides the already-built Phase-6/G execution graders, INV-6.2). This proves the CODE runs — *nothing more*; a test-passing bubble sort can ship under a depth artifact whose prose claims O(n log n), so this leg alone is insufficient.
  - **ESTIMATOR legs (undecidable without authored source):** complexity-derivation soundness · invariant-rederivability · edge-completeness. These **stay `verifică sursa` until grounded** against an authored source OR a **second-independent-derivation cross-check produced by a SECOND independent LLM family (never the authoring model — same anti-self-grading rigor as Phase C's responders)**; agreement between two *independent-family* derivations is the grounding signal, identical-family agreement is not (two same-family models can be identically wrong on a derivation). (Per Alex policy: invest so this is ~never `verifică sursa`; the rare exception renders flagged.)
  - **Per-leg mastery suppression (not all-or-nothing):** the depth artifact contributes to mastery only on legs that are PROOF-green or source-grounded; any `verifică sursa` leg renders its content **behind the non-dismissible warning, DOM-asserted, excluded from mastery** (Alex's horn (b), audit F30) — **the body is RENDERED (the learner needs it taught), never silently badge-stripped-and-served, never withheld.**

**EARLY EXIT GATE:** depth oracle BUILT + self-test green on seeded fixtures + green on the 7 real KCs' authored depth bank; the PROOF leg (code compiles/tests) green where code exists; ESTIMATOR legs either source-grounded-green or rendering a DOM-asserted `verifică sursa` warning + excluded from mastery (test flips one leg, asserts the warning renders AND mastery is suppressed). **LATE EXIT GATE (after first E/F slice):** depth oracle green on the full §10.2 proof-set.

---

### PHASE C — Pedagogical-quality ESTIMATOR: the Layer-2 teaching oracle (NOT a proof)

**Goal:** a machine ESTIMATE of *teaching* (not the artifact), computable without Alex. **Closes:** dimension 14. Net-new. **Explicitly an ESTIMATOR lane** (audit F7) — relabeled from v1's "decidable PROOF / GATE OF GATES / provably." It **BOUNDS** wrong-teaching; it does not eliminate it.

**Schema deliverable (audit F40 — the data objects do not exist yet; named BEFORE C claims to gate anything):** additive vs frozen sigs (INV-3.5) — a **per-distractor `misconception_id` on `BeatCheck`**, a **`discriminates` assertion field**, and a **misconception-SET per KC** (plural, mining the spec's R-ERRORS — not a single "delta"). (`BeatCheck` has no `misconception_id` today; `Misconception` has no `discriminates`; only PA has misconception files, the other 4 subjects are `.gitkeep`.)

**Split gate (audit F7 — PROOF legs vs ESTIMATOR leg):**
- **PROOF legs (decidable, may be hard-gated):** distractor ↔ named-misconception-id binding ON THIS KC; CHECK targets the *stated* misconception (non-paraphrase, decidable); **ATTEMPT precedes any answer-bearing content** (beat-order legality, already INV-4.1 — audit F42: "intuition-before-name" was a tautology already enforced by the closed BeatSelector; renamed to **beat-order legality**, OR a decidable intuition assertion: REVEAL step-1 must precede any formal symbol; predict ① answerable from prior KCs only).
- **ESTIMATOR leg (undecidable without a live learner):** "the CHECK items provably separate wrong-model from right-model." With no live learner this is an LLM judging LLM-authored items against LLM-simulated models — circular self-grounding. It **serves `neverificat`** when machine-uncertain, never fake-green. **Require a SECOND independent LLM family** for the wrong/right-model responders and the judge (no single-model self-grading), grounded in mined R-ERRORS.
- **No `mastery`/`faithful` stamping on Phase-C-only evidence** until N real-learner miss-data points confirm discrimination.

**Residual wiring — DROP the false mitigation (audit F41):** v1 claimed "FSRS already captures" the CHECK-discrimination signal. **FALSE** — `attempts` records correct/incorrect + chosen option, NOT which wrong model a miss implies (distractors aren't tagged to misconceptions), and at n=1 you cannot estimate item discrimination at all (needs a population). **Demote the empirical-feedback loop to net-new POST-DEPLOY work** (requires distractor→misconception tagging on `attempts` + served lessons [E] + single-writer FSRS state [G] + a real learner population). Until then, **Phase C ships as a static authoring-time ESTIMATOR only** — and that limit is named as residual #1.

**EARLY EXIT GATE:** Phase-C schema additive-merged; estimator BUILT + self-test on seeded fixtures + run on the 7 real KCs' authored CHECK bank; PROOF legs green (binding, non-paraphrase, ATTEMPT-precedes-answer); the "separates models" leg green-via-second-family OR serving `neverificat`; no grader chain is LLM-judge-only (INV-6.3). **LATE EXIT GATE (after first E/F slice):** estimator run on the full §10.2 proof-set. **This BOUNDS wrong-teaching — it is the precondition for opening generation in the sense that the firehose is born behind it, NOT a proof that teaching is correct.**

---

### SPIKE — Generator-capability measurement (between C and D; audit F34)

**Goal:** answer the empirical question the whole breadth plan rests on — *can the available free/relay model actually clear the new Layer-1 depth oracle and Layer-2 pedagogy estimator?* — BEFORE committing to Phase F.

**Deliverables:** run the **actual free-tier / claude-max-relay model** (the in-repo `FreeLlmApiLlm` defaults to `localhost:8080`; the FreeLLMAPI **service deploy is deferred — slot it here explicitly**) against the proof-set depth+pedagogy oracles; **record the pass rate**; **name the fallback** if it can't clear the bar (claude-max relay budget under the no-paid-APIs floor; semi-manual authoring of the ~7 real-subject KCs). No paid APIs — OpenRouter `:free` or the claude-max relay only.

**EXIT GATE:** a recorded pass-rate number per oracle; a named fallback path if below bar. **If the model can't clear it**, Phase F's honest output is "100% of what cleared the oracle; the rest renders `verifică sursa`-flagged + counted" — surfaced as residual #3, never hidden.

---

### PHASE D — Collapse the two UI surfaces (premium-by-default, structural)

**Goal:** premium is a structural property every generated lesson inherits, not per-lesson CSS or a human's taste. **Closes:** dimension 11, parts of 4. **Maps to:** JUDGE Stage D + Plan W (warm theme).

**Deliverables:**
- **Promote the cinematic Lectie shell INTO production** `BeatOrchestrator`/`LessonFigureShell` (masthead, trust-badge, premium pips, dark figure stage, generous figure height). **Delete the demo-vs-prod split** (`/tutor/lectie-selectsort` + the untracked `Lectie*Demo.tsx` files).
- **Lift the 272px figure clamp;** verify at Alex's real **648px content height with VIEWPORT screenshots (not fullPage)** at 1536×864 (his resolution).
- **Experience-felt gate:** visible progress, monotone motion-toward-end-state, no dead beats (rides the Phase-A semantic frame gates).
- **Accessibility gate (audit F35):** a **keyboard-traversal + RO-aria-live correctness gate** in the rendered suite (no a11y gate exists today; the 5 axe tests disable color-contrast). For an unmedicated-ADHD keyboard learner this is load-bearing UX. Named in the dimension-11 acceptance row. (Best-practice, not a spec clause — registered, not laundered as spec-compliance.)
- **Plan W (warm theme) here** so dual-skin baselines capture all 6 families at once (never re-baseline; W after the families are final).

**EXIT GATE:** zero demo-vs-prod split (grep: no `Lectie*Demo` route reachable in prod); no-clip + legibility green at 648px viewport-screenshot on real lessons; **keyboard-traversal + RO-aria-live gate green**; INV-9.5 (baseline = shell + 2 themes only); dual-skin (cool+warm) baselines green across all 6 families.

> **GATE OF GATES:** Phases A+B+C **at their EARLY gate** (oracle built + self-test green + green on the 7 hand-authored real KCs, per §3 line 89 — NOT the LATE gate, which fires *after* a first E/F slice and therefore cannot precondition E) **AND the capability spike measured** is the **hard precondition** for Phase E. Until then every generated lesson is potentially pedagogically wrong and undetectable — breadth is forbidden. (This is the *sequencing* gate; it is NOT a claim that the pedagogy estimator *proves* teaching — see residual #1.)

---

### PHASE E (E-bulk) — Digestion / generation pipeline, sub-gated E1–E7 (audit F20)

**Goal:** build the firehose to emit TYPED specs that ride the A–C gates (P2 EASY⇒GATED), behind the oracle. **Closes:** dimensions 1, 2, 3, 8. ~50% of spec, currently zero-code — decomposed into seven sub-phases each with its own exit gate sized against its spec section:

- **E1 — Ingest + legibility + OCR.** Upload door (file/URL) → **extraction-legibility gate FIRST** (promote `ContentValidator.checkExtractionLegibility` from dead code). **OCR + alt-extractor subsystem (audit F15 — net-new, does NOT exist):** stand up a **local/offline OCR engine (tesseract + Romanian language data) + a second alt-extractor**, each with a **probe-availability gate** (like `LanguageRunners.probe()`). No OCR exists today (only `pdfbox:2.0.30`; `VisionLlm.kt` is wired to the screenshot sensor on a PAID model — collides with no-paid-APIs). Until the offline engine exists, the **POO PNG-scan proof-set artifact is parked / `verifică sursa`**, not implied as wiring-only. **Name the machine self-retry loop (a) as an E1 deliverable + add INV-9.2 retry-boundedness to E1's exit gate** (audit F5).
- **E2 — 9-kind content classifier** (never filename).
- **E3 — the full 19-item R-* register** (archetype, rubric, solution_present, datafiles, prereq, multiselect, errors, … — enumerate all 19 from spec Appendix A.2).
- **E4 — beat-gen per `concept_type`** (5-beat templates, RO).
- **E5 — figure/depth/CHECK co-generation.** Generation emits the co-manufactured learner-state objects: typed figure spec (A) + **depth artifact (B)** + **misconception-SET + discriminating CHECK items (C)**. Generation that can't pass A/B/C → flagged (`verifică sursa` for Layer-1 truth legs / `neverificat` for the Layer-2 estimator leg), never fake-green.
- **E6 — checkpoint review screen** (spec §1.5): real lesson route embedded, per-gate green summary, approve/reject + one-line note, kind re-classify, requeue on reject. **Add the §2.6 gap-ledger first-paint + click-to-ingest interaction smoke** (audit F1 — register §2.6's 5 `data-testid` selectors + upload-affordance smoke; the ghost-component / PDF-404 class).
- **E7 — migrations** (backup-first). **Verify each spec-§3.6 step against the live DB (audit F25):** the 3-column add is already live and `report_wrong.resolved_by/resolved_at` + `attempts.beat_type/prediction/first_encounter` are **already applied** — drop them from "remaining." Genuinely-pending: beat fields, problem-bank/course-meta additive tables, **and the omitted `fsrs_cards.kc_id` backfill (0/828 non-null today)**. **Add the identity/consent migration-survival invariant (audit F32):** extend the backup floor + a row-count-equality invariant to `users`/`sessions`/`magic_link_tokens`/`consent_log`/`trust_grants` (today only `fsrs_cards` is guarded).

**Near-term pragmatism (JUDGE):** semi-digest Alex's ~5 real subjects through the now-gated templates first; treat universal PDF→KC digestion as a gated stretch goal.

**EXIT GATE (per sub-phase + rollup):** INV-2.1–2.5, INV-3.1–3.5, INV-1.3, INV-1.4, INV-9.2 green on real corpus; OCR/alt-extractor probe-gates green or the POO-scan artifact parked-flagged; checkpoint Playwright (§1.5) + gap-ledger smoke (§2.6) green; **every generated artifact rides A/B/C or carries its layer-appropriate flag** — zero fake-green; INV-9.1 (artifact-state total).

---

### PHASE F — Content across all 5 subjects: F1 proof-set, then F2 the 140 URLs (audit F23)

**Goal:** real verified content for every subject, born behind the oracle. **Closes:** dimension 16 (coverage). Each artifact traverses the ENTIRE E+A+B+C pipeline on concept-types/extraction-routes with **zero working examples today** (POO/PS/SO-RC = 0 KCs).

- **F1 — the spec-§10.2 proof-set (11 artifacts), gated on E+A+B+C GENERALIZING beyond PA.** One artifact per kind × concept-type (PA lecture, ALO chapter skeleton-trace, PS probabilistic + garble-recovery, seminar proof/step-trace, POO lab code-practice, POO past-exam mock+**OCR** [parked until E1's OCR exists], SORC fișă→registry, ALO Tema→deliverable, cheat-sheet, student-notes stricter-tier, grade-ledger) through full loop → checkpoint approval. **Each non-PA concept_type/extraction-route is named a FIRST-TIME exercise** (POO/PS/SO-RC have never produced a KC). This is where the LATE depth/pedagogy oracle gates (Phase B/C) fire on the real proof-set.
- **F2 — the 140 inventoried URLs** (ALO 25 · PS 59 · SORC 18 · PA 4 · POO 32), **sized as up to 140 sequential per-artifact checkpoint CYCLES** on the E6 checkpoint screen (bounded blast radius). **Quantify the synthetic-tag fraction PER SUBJECT** (spec §11.1: ALO/PS/RC have zero past papers anywhere) so dimension-16 coverage is **never read as all-real** — the synthetic fraction is displayed on the board (residual #4).
- **828-card RO re-verify pass** (each translated card re-faithful-checked per spec §3.7) — and the `kc_id` backfill from E7 makes existing review history count.

**EXIT GATE:** INV-1.1 = **0 over `<servable view>`** (the admission-gated VERIFIED set only, per the §0.2 servable-view definition — a literal `0 == 0` decidable gate) AND INV-1.1b (`COUNT(flagged-servable) <= displayed budget`, target 0) — a `verifică sursa`-flagged row is on the distinct `flagged-servable` surface the INV-1.1 query EXCLUDES, never inside `<servable view>`; INV-1.2 (serve-side refusal); every proof-set artifact has an `approved` checkpoint event; problem bank ≥1 row/subject (INV-6.1 flips green); figure-coverage INV-EC2.2; language INV-8.1–8.4 green on real served content; **per-subject synthetic-tag fraction displayed**.

---

### PHASE G — Guidance + practice + exam completion

**Goal:** finish the surfaces that consume the now-real content. **Closes:** dimensions 7, 9, 10.

**Deliverables:**
- **Unified FSRS+EWMA single-writer state** (all surfaces write via one service); forgetting→compressed re-lesson w/ fresh params; misconception→remediation requeue.
- **Priority queue** (proximity×weight×gap×prereq) + **readiness dashboard** vs verified grade-model gates.
- **ADHD session shape** (caps, break prompts, interleave cap, recovery mode); **calibration mounted**; **placement rebuilt to probe SUBJECT** (not tutor internals) — **add a placement-`entry_phase` DB assertion + a calibration-telemetry-write test (INV-7.6/7.7)** as Phase-G exit clauses (audit F4 — these ship with no decidable gate in v1).
- **Practice carry-overs:** REQ-1 (queue moves mastery), R-MULTISELECT drills, CodeMirror editor. **The execution graders R/Python/C++/Alk are ALREADY built + CI-green (audit F24)** — the real gap is **wiring them to the populated problem bank and turning INV-6.2 green on the real corpus** (problems table = 0 rows today), not building graders.

**EXIT GATE:** INV-7.1–7.7 (**INV-7.6/7.7 are NET-NEW — spec §7.6 defines only INV-7.1–7.5; registered PLANNED in the coverage registry, not spec clauses**), INV-6.1–6.6, INV-4.3, **INV-7.4** (re-lesson trigger — replaces phantom INV-4.5, audit F3), INV-EC3.1/.2 green on real DB; readiness + 5 practice surfaces Playwright green against real corpus.

---

### PHASE H — EC1–EC5 (experience completeness)

**Goal:** the experience-completeness layer atop real content. **Closes:** dimension 15.
**Deliverables:** EC1 live-help (ask-in-lesson + prereq-peek, reuses sidekick, drill-self-paste guard); EC2 cold-start completeness gate (prereq-path to entry KC or `verifică sursa` stub + figure-coverage); EC3 recovery mode; EC4 goal/intent (decompose→re-weight, can't starve real deadline); EC5 provider health surface (admin-only, learner zero-setup).
**EXIT GATE:** INV-EC1.1–.3, EC2.1–.2, EC3.1–.2, EC4.1–.3, EC5.1–.3 green; EC1 affordances ride the Phase-A/D visual + a11y gates.

---

### PHASE I — Final hardening (whole-build adversarial council + full matrix)

**Goal:** prove honest 100% before deploy.
**Deliverables:** full per-gate matrix run on real DB; INV-9.1–9.5 + gate-coverage + gate-self-test (natural seeds) green; **whole-build adversarial council** (per-chunk reviews miss cross-chunk flaws — MANDATORY after a long autonomous run); seeded-violation drill per gate.

**EXIT GATE — the HONEST 100% definition (audit F27, two-layer, with "green-or-flagged=100%" DELETED):**
- **Layer-1 TRUTH:** per the §0.2 servable-view definition, INV-1.1 (`SELECT COUNT(*) FROM <servable view> WHERE verification_status != 'VERIFIED'` = **0**) runs over the **admission-gated VERIFIED set only** and is a true `0 == 0` decidable gate. Any `verifică sursa`-flagged row lives on the **distinct `flagged-servable` surface the INV-1.1 query EXCLUDES** — **visible + counted + DOM-asserted + excluded from mastery**, never inside `<servable view>`, never wearing VERIFIED. INV-1.1b governs that surface: **the board prints 100% for Layer-1 ONLY if `COUNT(flagged-servable) = 0, or at/below the tiny Alex-ratified per-dimension budget that is itself DISPLAYED on the board.`** flagged ≠ done — a flagged-servable row is RED-on-coverage for Layer-1; INV-1.2 (serve-side refusal) re-asserted. (Alex: invest so this count is ~always 0.)
- **Layer-2 TEACHING:** every ESTIMATOR clause is green-or-`neverificat`-flagged AND **every §HONEST RESIDUAL is displayed on the board.** "Green" here means the estimators ran and their limits are named — explicitly **NOT** "teaching is proven."
- **All PROOF-DEMANDED clauses green;** the §10 user-checkpoint passed (an **experience** approval, residual #5) — explicitly **NOT** "no human ever sees a defect." Zero SKIP / fail-open / cross-platform amber.

---

### PHASE J — ONE verified VPS deploy with live probe (DEPLOY-LAST)

**Goal:** the single, final, verified deploy. **Closes:** dimension 16 (deploy).
**Deliverables:** backup→D9 PC→VPS sync of verified state; deploy the ~140-commit-ahead bundle; run **live-probe** on the real URL. **Provision `RESEND_API_KEY` on the VPS + an end-user login round-trip probe** (audit F31 — the authenticated SPA smoke already runs via the server log when the key is unset; the residual is a real end-user who can't read the log can't self-serve email login). **Scripted DB-restore-from-dump path in the rollback (audit F36)** — rollback currently swaps dist dirs only; a mid-`ALTER` failure has only manual unscripted recovery; `db-backup.py` exposes `--restore-drill` to a scratch DB — wire a documented scripted restore into Phase J's rollback path. (`deploy.sh` SPA smoke + the applied migrations are **already done** — audit F17/F25; verify still-green, do not re-list as pending.)

**EXIT GATE (feature-shipped, post-Slice-1/1.5 lessons):** open the live URL and confirm the user *sees* the feature — bundle-hash + tests-green ≠ shipped. All spec-listed `data-testid` selectors paint on first load; **zero 4xx/5xx during first paint**; click every interactive element → no `/404|HTTP \d{3}|not found|error/i` text, no new 4xx/5xx; authenticated SPA smoke green; end-user login round-trip green; scripted restore drill green. Only then is any feature "shipped."

---

## 4. WHAT THIS SUPERSEDES IN THE EXISTING LEDGER

- **The headline re-sequence:** the ledger's `V → 5 → 7` is **REPLACED** by **0 → A → B → C → [spike] → D → E → F → G → H → I → J**. Plan 5 (E-bulk) is moved behind three oracle phases + the capability spike and may not start until A+B+C are green and the spike has measured the generator.
- **Two net-new first-class stages + a spike inserted:** **Phase B** (depth = Layer-1 truth, split-gated PROOF/`verifică sursa`); **Phase C** (pedagogy = Layer-2 teaching, explicitly an ESTIMATOR, NOT the GATE-OF-GATES); the **generator-capability SPIKE** (audit F34).
- **Plan V is reframed** to the Stage-A figure-**correctness** loop (A0 commit-WIP → A1 serialization engine → A2 two new families → A3 retrofit + semantic frame gates → A4 24 primitives). Honest committed state: only 3 families tracked+green; matrix-grid + sort-merge are uncommitted WIP (audit F14).
- **Plan W moves into Phase D** so dual-skin baselines are captured once over the final family set.
- **Phase 0 pulled to the FRONT** — foundation defects + the coverage **registry** (status+tag-stamped) + gate-self-test (natural seeds) must exist before any later gate carries authority.
- **Deploy (Task 15) stays LAST and singular** — one verified deploy with a live probe after honest-100% is proven locally.
- **Retired (supplements only, never primary):** the spec-§10.3 "caught-defect → new gate" accretion loop (presumes a catcher Alex can't be); the delegate-to-Opus-then-spot-check "verified" loop; CSS-reskin as quality-lift; agent/PM "verified" as ship authority (only green CI gates ship).

---

## 5. THE FEW HARD RISKS + mitigation

1. **Silent-wrong-teaching is the BASE RATE of LLM generation** — landing hardest on the Layer-1 depth layer (invariants, complexity), carrying a false `mastery` into an exam. **Mitigation:** Phase B splits the depth gate (PROOF leg = code-runs; ESTIMATOR legs = `verifică sursa` until source-grounded, per-leg mastery suppression, rendered-with-warning never withheld); Phase C's ESTIMATOR bounds (does not prove) wrong-teaching with a second-independent-LLM-family discrimination check; no lesson ships `mastery`/`faithful` on Phase-C-only evidence.
2. **Breadth-before-oracle trap** — scaling generation across 5 subjects before the oracles exist multiplies the never-sorts failure. **Mitigation:** the hard sequencing rule + the capability spike; per-artifact sequential checkpoint (INV-1.3) bounds blast radius to ≤1 artifact even after launch.
3. **Depth-artifact homelessness** — the figure architecture structurally cannot host prose/proof/code. **Mitigation:** Phase B makes depth a separate first-class schema'd Layer-1 stage with its OWN oracle, inheriting zero figure verification.
4. **The deploy gap** — live serves SESSION-66; HEAD ~140 commits ahead; push ≠ deploy; bundle-hash + green ≠ shipped. **Mitigation:** Phase J is one verified deploy after honest-100% locally, with a live-probe that opens the real URL and asserts paint + zero 4xx/5xx on paint and every click.
5. **Free-tier generator capability is unknown until measured** (audit F34) — if the free model can't clear the depth/pedagogy bars, Phase F yields a mostly-flagged servable set. **Mitigation:** the capability spike measures it before Phase F and names the relay/semi-manual fallback; the flagged fraction is displayed, never hidden (residual #3).

---

## HONEST RESIDUALS (named, never folded silently into green — audit §7)

Real limits of what ANY version of this plan can machine-prove. Each is stamped on the matrix as an **ACCEPTED, FLAGGED limitation** carrying its layer-appropriate flag + a human-checkpoint record:

1. **Teaching effect without a live learner is not measurable (Layer-2).** Spec §A.3.3 scopes learning-gain out. The Phase-C discriminating-CHECK gate is the closest computable proxy — it **BOUNDS** wrong-teaching, does not eliminate it; with no real student population it judges LLM-simulated models. This is the load-bearing residual (JUDGE's strongest dissent). The HONEST ESTIMATOR of §0 item 4 — never proof-labeled.
2. **Depth-derivation soundness (complexity, invariant) is undecidable without authored-source grounding (Layer-1).** "Code compiles" is decidable; "this O(n log n) prose derivation is correct" is not, absent an authored reference. Until a second-independent-derivation or authored-source cross-check exists, the depth ESTIMATOR legs render `verifică sursa` + excluded from mastery — never proof.
3. **Free-tier generator capability against the new oracles is unknown until the spike measures it.** "100% coverage" may legitimately resolve to "100% of what cleared the oracle; the rest `verifică sursa`-flagged + counted." The flagged fraction is displayed.
4. **Synthetic-tagged exam coverage is not real-paper coverage.** Spec §11.1: ALO/PS/RC have zero past papers anywhere. Dimension-16 "all 5 subjects" is partly synthetic; the synthetic fraction PER SUBJECT is displayed, never absorbed into an all-real reading.
5. **The human checkpoint judges EXPERIENCE, not TRUTH — and the checkpoint-holder cannot vet content.** "§10 user-checkpoint passed" is a real compliance leg but an EXPERIENCE approval, not a teaching-truth oracle. "100%" = "every machine gate green + experience-approved," explicitly NOT "no human ever sees a defect." No future edit may quietly upgrade the checkpoint into a quality oracle.

A "100%" that names these five as accepted, flagged residuals is honest. A "100%" that hides them is the downsized-spec-wearing-a-green-label this plan exists to prevent.

---

**Key file paths (all absolute):**
- Binding council JUDGE: `C:\Users\User\jarvis-kotlin\build-review\2026-06-15-teaching-pipeline-council-JUDGE.md`
- The audit this v2 applies: `C:\Users\User\jarvis-kotlin\build-review\2026-06-15-master-plan-audit.md`
- v1 (superseded): `C:\Users\User\jarvis-kotlin\docs\superpowers\plans\2026-06-15-end-to-end-master-plan-to-100.md`
- Binding spec: `C:\Users\User\jarvis-kotlin\docs\superpowers\specs\2026-06-11-one-pass-digestion-teaching-engine-design.md`
- Frozen signatures (canonical-on-conflict): `C:\Users\User\jarvis-kotlin\docs\superpowers\plans\2026-06-02-interface-signatures-lock.md`
- Mergesort wrong-binding test to delete atomically (Phase A): `C:\Users\User\jarvis-kotlin\src\test\kotlin\jarvis\content\FigureBindingNonVacuityTest.kt` (lines **72–89**, the `pa-kc-002 has the mergesort figure binding` test; pa-kc-002.yaml + a new seq-array/merge viz instance edited in the same atomic change)
- Viz families — 3 tracked+green, matrix-grid/sort-merge uncommitted WIP (Phase A0): `C:\Users\User\jarvis-kotlin\tutor-web\src\components\viz\families\`
- Live DB (read-only verified this pass): `~/.jarvis/tutor.db` — 8 `kc_verification_status` rows (4 faithful / 4 uncertain), 828 fsrs_cards, **0** non-null `kc_id`

**Reality grounding (re-verified vs committed HEAD `9c48d1d` + live DB this pass):** 1040 commits; only 3 viz families tracked+green (matrix-grid + sort-merge are uncommitted WIP); 7 real KCs across 2 of 5 subjects (PA 6 + ALO 1; POO/PS/SO-RC = 0); 828 cards 0-linked; 8 verification rows. The phases above account for this (Phase A *commits + completes* the family set; Phase E *creates* the digestion half from zero; the depth/pedagogy oracles author their banks for the 7 real KCs in-scope before their EARLY gates fire).
