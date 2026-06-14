# Remaining Work to 100% Spec — jarvis tutor

> **This is the single handoff ledger.** A fresh session reads `BRIDGE-HEAD.md` first, then this doc, to get the WHOLE remaining picture in one place — no thread lost across sessions. Authored 2026-06-13 (SESSION-68) at `main @ 09f5add`. **Updated 2026-06-14 (SESSION-70):** added the GOVERNING PRINCIPLES + the honest "100%" definition + the `council-1781435843` compliance-harness reshape (§B governance group). Binding spec = `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md`; roadmap = `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`.

## What "100%" means (spec §10.2)
A real user **drops a file/URL → it digests into faithful content → teaches (lesson) → practices → tracks**, across the real corpus, with every gate alive. The proof is the §10.2 **11-artifact proof run** + bulk-upload of the 140 inventoried sources.

### Governing principles (LOCKED — Alex, SESSION-70, 2026-06-14 — the law the tasks answer to)
- **P1 — NO-SPEC-CUT.** Highest quality in EVERY aspect (UX · UI · pedagogy · visuals · faithfulness). Nothing is trimmed to ship. A spec requirement is either **PROVEN** (a decidable machine gate is green = a proof) or **ESTIMATOR + human-checkpoint gated** with an explicit `neverificat / verifică sursa` flag until it genuinely passes. A requirement is NEVER silently dropped, watered down, or made to wear a fake green. *No-fake, not no-human.*
- **P2 — EASY ⇒ GATED authoring.** Every content type ships with a `typed input → renderer/template verified ONCE → auto-gates ride for free` path — the constrained-figure-family pattern, generalized to lessons (5-beat template), depth artifacts, practice (archetype/slots), KCs, and whole subjects (Plan-5 digestion = the ultimate easy-add: drop a file → gated lesson out). **No bespoke un-gated content, ever.** Low friction is mandatory; it must never become a back door around the gates.

### What "100% compliant" concretely asserts (council-1781435843, FLAWED→reshaped — a single GREEN/RED bit is FORBIDDEN)
The board is a **per-gate MATRIX**, never one collapsed bit (the spec already mandates this: §1.5 `checkpoint-gate-summary` = the per-gate green list, each gate named). An artifact is "100% to spec" iff:
1. **Gate-coverage:** every binding spec clause / INV-* maps to ≥1 live gate (the coverage invariant; an un-gated clause is RED — the machine form of NO-SPEC-CUT, and the defense against the *missing-requirement* class that burned us twice).
2. **All PROOF gates green.** Each check is tagged `PROOF` (decidable: cardinality, monotonicity, trace-match, 4xx-free, bbox-overlap, lint) or `ESTIMATOR` (semantic: faithful, depth, beauty, pedagogy).
3. **Every ESTIMATOR green-or-flagged** (`neverificat` is an acceptable honest state; a faked pass is not).
4. **SKIP / fail-open / cross-platform-unverified each render RED**, never GREEN.
5. The **§10 user checkpoint + the 11-artifact proof run pass.**

"100%" therefore = *every clause gated · all proofs hold · every estimate green-or-flagged · user checkpoint passed* — it does NOT mean "no human ever sees a defect" (impossible; collides with the spec's own §10 human step).

## DONE + CI-green (run `09f5add`, all 7 jobs)
Plan 1 (trust-net ON) · Plan 2 (knowledge schema) · Plan 3 (lesson engine + viz family **1 of 6**) · Plan 4a (gate tooling) · Plan 4b (rendered gates 3+4) · Plan 6 (practice + 4-leg grader chain). Trust-net LIVE: 4 faithful KCs (`pa-kc-001..004`) serving.

---

## A. Build plans (sequence: V → 5 → 7; W after V)

| Plan | What | State | Doc |
|---|---|---|---|
| **V** | **Viz families 2–6** (sequence/array · matrix/grid · chart/distribution · timeline/protocol · state-machine/code-trace) + migrate the ~24 hand-coded primitives into the family system. Only family 1 (graph/tree) is built. Frontend-only; reuses 4b's per-family harness. **Load-bearing — Plan 5 can only emit figures for families that exist; this is the sparse-figure fix.** **Fold in the §E `council-1781391707` machine visual/overlap gate.** | **NEXT, recon'd** | `build-review/2026-06-13-planV-viz-families-recon.md` |
| **5** | Digestion/upload pipeline — upload door (file/URL) → 9-kind classifier → per-kind extraction → dedup/provenance → gap-ledger → generate beat content + viz instances → checkpoint review. The "drop a PDF, get a lesson" engine half. **Must include the §E `council-1781433195` DEPTH layer (separate gated artifact, NOT a figure skin).** | pending | spec §1.4-1.5,§2 |
| **7** | Tracking/timeline — unified FSRS+EWMA state, beat telemetry, forgetting→re-lesson, misconception→re-queue, exam-aware queue priority, readiness dashboard, ADHD session shape, placement rebuild + the §10.2 **proof run**. | pending | spec §7,§10 |
| **W** | Warm second theme — switchable app-wide skin; every surface gated in BOTH skins, baselines double. **AFTER V** (so dual-skin baselines cover all 6 families at once; never run V ∥ W — viz file intersection). | ratified | spec-external; `DoorWarm.tsx`/`warmVars`/`palettes.ts` exist |

## B. Gate hardening / loose threads (tracked, unslotted — fold into the plan that touches the area)

**Compliance-harness governance (`council-1781435843` — the "gate the gates" layer; build the matrix early, do the 3 foundation defects FIRST within/before Plan V):**
- **Per-gate MATRIX rollup (NET-NEW; NOT a single bit).** A cheap CI/CLI aggregator over the existing gates that prints a per-gate named board with PROOF/ESTIMATOR tags; SKIP / fail-open / cross-platform-unverified each render RED. Aligns with spec §1.5 `checkpoint-gate-summary` (which Plan 5 builds as the user-facing screen). Cheap; build early so every later plan reports into it. A single collapsed GREEN/RED is FORBIDDEN (it launders false-green with higher trust — the reshaped finding).
- **Gate-COVERAGE invariant (NET-NEW).** A CI check that every binding spec clause / INV-* maps to ≥1 live gate; an un-gated clause is RED. Machine enforcement of P1 (NO-SPEC-CUT) + the defense against the *missing-requirement* class. Needs a spec-clause→gate registry.
- **Adversarial red-team round (NET-NEW, recurring process).** Each build cycle, one adversarial pass whose sole job is to find ONE spec-true defect the current gate set passes; the defect becomes a new gate ("same defect-CLASS escapes twice = pipeline design bug" tripwire). Partly overlaps the existing whole-build-council review — make it a STANDING step, not ad-hoc.
- **Gate-self-test (extend existing).** The seeded-red pattern already exists (4a's 12 grader goldens + each viz family's `seededWrongTrace`); extend so EVERY gate is fed a known-bad fixture and asserted RED — a gate that can't prove it ever says RED is not a gate.

**Foundation defects (already tracked below; `council-1781435843`-confirmed these gate the harness's TRUST — fix BEFORE the matrix carries authority):**
- **Impeccable → blocking.** Built in Plan 4a, running in CI **fail-open** (`|| true`). Locked sequence = calibrate→subset→pin→fail-open→**then blocking**; stuck at fail-open. Promotion to a real blocking gate needs a re-calibrate against more true-positives + flip the `|| true`. **Fold into Plan V** (V is the big viz/design push impeccable lints) or a dedicated gate-hardening pass. Files: `tools/impeccable-rules.json`, `tools/impeccable-filter.mjs`, `build-review/impeccable-calibration-2026-06-12.json`.
- **cross-language schema-hash CI test** — python `db-backup.py` vs Kotlin `MigrationBackupGate.liveSchemaHash`; now more load-bearing (4b duplicated the §0.9E RO-heuristic constants TS↔Kotlin). → Plan 5.
- **relay retry/backoff** — `RelayLlm`/`FreeLlmApiLlm` got `RetryingLlm` in Plan 6; the VerificationRunner relay path (pa-kc-006 false-negative class) still bare. → verify path.
- **StateCache flake** (`stateCacheConcurrentPersistNeverTearsJson`) — pre-existing concurrency race, ~1-in-2 under `--rerun-tasks`; standing carve-out, name-don't-chase. A real fix (isolate the cache per test) is unslotted.
- **Linux visual-baseline regeneration** — baselines were captured on Windows; CI runs Linux. Unslotted (no visual job blocks on them yet).
- **deploy.sh SPA smoke broken** — greps `/` for `<div id="root">` but `/` 302s to `/login`; replace with healthz + an authenticated probe. → fold into Task 15 / deploy.
- **~24 tsc baseline errors** — pre-existing; CI runs vitest not tsc.

## C. Per-plan re-carries (small, named — not lost)
- **REQ-1** — queue actually moves a concept along the predict→practice arc as mastery rises (spec §6.1/§7.4). → Plan 7.
- **R-MULTISELECT drills** — the "bifați toate" multi-select variant on the DRILL side (Plan 6 shipped only the mock-exam grid half). → Plan 7 or a practice follow-up.
- **ALO-proof + POO problem seeds** — Plan 6 seeded PA/ALO/PS; ALO-proof archetype + POO have no locatable corpus problem yet (honest pending). → arrives with content (Plan 5/7).
- **CodeMirror editor** — code-practice uses a plain textarea (R-6-Q9 deferral); real syntax editor is a named follow-up. → practice polish.
- **lessonStrings / chromeStrings / practiceStrings consolidation** — 3 RO strings files exist by design this cycle; revisit/merge. → tidy.
- **FSRS re-seed wart** · **pa-kc-006 re-audit** (pending relay re-auth) · **Resend key unset** (magic-link via log) — small ops items.
- Resolved already (don't re-carry): BeatOrchestrator error boundary (done in 4b), numeric-ATTEMPT tolerance single-source (done in 6), `tutor-shell-api-contract` red (resolved `5fbfaaf`), rtk (rejected w/ evidence).

## D. Ops / "make it real" (non-build, but required for a usable site)
- **Deploy — Task 15 (PM-gated).** Nothing from SESSION-68 is live; the VPS serves the SESSION-66 bundle. VPS deploy + toolchain provisioning probe + production seed behind the INV-3.1 backup gate + live interaction smoke. **No feature-shipped claim for ANY 4b/6 surface until its live probe passes.** Do this after each plan lands (or batch).
- **Content (the 140 sources).** ALO 25 · PS 59 · SORC 18 · PA 4 · POO 32. The site is nearly empty until these ingest — and ingestion needs Plan 5 (digest) + the Plan 7 proof run. The CP-3 `LanguageCheckTable` live migration also fires when content flows.
- **Trust-net flip — DONE** (corrects the long-standing stale "never run" note): 4 PA KCs are faithful + serving on PC+VPS.

---

## E. Council rulings to fold (SESSION-70, 2026-06-14) — CITE, don't re-litigate
Two councils ran this session; both bind future viz/lesson work.

- **`council-1781391707` — keep G4, ADD a machine VISUAL gate (VERDICT: FLAWED, fix clear).** The family system gates TRUTH (trace-match) + clip, but is BLIND to visual quality + text overlap — proven this session: two subagents reported "zero overlap", a manual 18-frame sweep found 9; the cardinal no-overlap sin slipped the existing gate. Fold into **Plan V** + the per-family build checklist:
  - A BLOCKING per-family visual gate alongside no-clip/trace-match: step EVERY frame at the user's REAL viewport (**1536×648 AND ×730** — his panel is 1080p at 125% scaling, so logical 1536×864; **NEVER fullPage screenshots** — fullPage hides header/footer clipping), assert **ZERO pairwise text-on-text overlap** + nothing hidden under the fixed header/footer + golden-master/perceptual-diff vs a committed reference + theme-token conformance (no hardcoded `#fff`). See [[reference_alex_screen_resolution]].
  - Pin `lectie.html` as the committed visual reference ("lectie-parity"). Polish each family ONCE (amortized); back-apply to families already built (1/2/4).
  - Carve viz work OUT of token-frugality; route it to a strong model + self-see loop. Orchestrator discipline rides ON TOP of the machine gate, not instead of it.

- **`council-1781433195` — "intuition + detail = two render-skins of one figure" is WRONG (VERDICT: WRONG APPROACH).** Two skins (bars↔trace) is an intuition↔TRACE **visual zoom**, NOT depth. Genuine technical depth = a SEPARATE, concept-keyed, NON-figural artifact: loop invariant + justification · complexity **DERIVATION** (not the O() answer) · correctness argument · edge cases · readable code/pseudocode (line-synced to the figure, VisuAlgo-style) · tradeoffs. The locked figure-family CANNOT host it (verifies pixels, not proofs). Fold into **Plan 5** (lesson model):
  - Depth is a first-class lesson content type with its OWN authoring budget (does NOT amortize over the family like the intuition skin).
  - **CRITICAL:** depth content inherits NONE of the figure's verification → it needs its OWN correctness gate (complexity/invariant/code checks, or authored-source grounding); until gated, flag "neverificat / verifică sursa" — never wear the figure's authority. Default honest-thin-with-pointer over looks-complete-but-unverified.
  - The 2026-06-14 selectsort demos prove only the VISUAL layer; the depth layer is unbuilt. See [[feedback_lesson_layered_depth]].

- **`council-1781435843` — the "pipeline to 100% spec compliance" approach (VERDICT: FLAWED, fix clear; 1 REJECT + 4 CONDITIONAL, 0 flagged).** Direction is right (acceptance-criteria-as-code + a gated build pipeline), but a **single GREEN/RED verdict is the single-aggregated-pass/fail anti-pattern** — it would launder false-GREEN (fail-open lint, Windows-baselines-on-Linux, undecidable depth) with HIGHER PM trust, re-creating the exact false-green incident this effort exists to kill. Reshaped contract (CITE, don't re-litigate):
  - **Matrix, not a bit** — see the §"What 100% means" reframe above (per-gate named board, PROOF vs ESTIMATOR, SKIP/fail-open/cross-platform-unverified = RED). The aggregate is a derived rollup, never the primitive, never prints "100%".
  - **Gate the decomposition itself** — the new **gate-coverage invariant** + a recurring **adversarial red-team round** (both §B). The harness only ever proves compliance to the checklist you wrote; the *missing-requirement* class is what burned us twice and needs its own gate.
  - **Fix the foundation BEFORE the matrix carries authority** — the **3 foundation defects: impeccable fail-open→fail-closed · regenerate visual baselines in CI's container (env-parity) · quarantine the StateCache flake**; + a **gate-self-test** (feed each gate a known-bad fixture, assert RED). All §B; do within/before Plan V.
  - **Build the 2 new gates INLINE, not as a meta-layer up-front** — visual/overlap = **bbox-geometry assertions** in Plan V (decidable → PROOF); depth = a `neverificat` **ESTIMATOR + human-checkpoint lane** in Plan 5/7 that rides trust-net for grounding and NEVER fakes a machine pass (the no-third-regen rule would otherwise *brick* artifacts).
  - **Honest "100%"** = every clause gated · all PROOF green · every ESTIMATOR green-or-flagged · §10 user checkpoint passed — not "no human ever sees a defect." Confidence 8; full transcript `.claude/council-cache/council-1781435843.md`.

---

## Recommended sequence (one line)
**Plan V** (+ the §B compliance-harness governance — matrix rollup · gate-coverage invariant · adversarial red-team round · gate-self-test · the 3 foundation fixes incl. impeccable→blocking) → deploy → **Plan 5** (digestion + the per-gate checkpoint screen §1.5 + the depth ESTIMATOR lane) → **Plan 7** (incl. the proof run + content) → **Plan W** after V whenever. Two-lane contract applies if two non-intersecting plans run parallel.

## How a new session uses this
1. Read `BRIDGE-HEAD.md` (current state + the V→5→7 pointer).
2. Read this ledger (the full remaining map).
3. For the active plan, open its recon/plan doc (Plan V → the recon above) and execute just-in-time per the build workflow.
