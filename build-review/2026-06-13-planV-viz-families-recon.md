# Plan V — Viz Family Build-Out: Recon + Cold-Start Handoff

**Purpose of this doc:** the artifact a FRESH session reads to pick up Plan V cold — no prior context required. Plan V is the unfinished G4 visual work: build the 5 missing viz families + migrate the 24 hand-coded primitives into the family system.

**Repo state at recon (2026-06-13, verified against repo + read-only):**
- `main` @ `09f5add` (pushed, 0/0 vs origin).
- `familyRegistry.ts` — ONE entry: `"graph-tree": GraphTreeFamily`. Five family_ids missing.
- `vizRegistry.ts` (legacy E3) — ONE entry: `"recursion-tree": RecursionTree`.
- `content/*/viz/` — ONE instance: `content/PA/viz/viz-pa-mergesort-001.yaml`.
- 24 primitive components + infra (`AlgoStepperShell`, harness trio, `assertNoClip`) all present on disk (verified).
- Plan-index row V already points here; sequencing locked V → 5 → 7, W folded after V.

**Binding spec:** `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` §5 (§5.2 families L313–322, §5.3 contract, §5.4 verification, §5.5 migration), §624 subject-findings family mapping. **Plan-index:** `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` row V. **Reference family (copy this):** `tutor-web/src/components/viz/families/GraphTreeFamily.tsx` + `graphTreeInvariants.ts`.

---

## 1. WHY (the load-bearing gap)

G4 (the "every figure is bespoke code that individually breaks" failure mode) was resolved in favor of the **family architecture** (spec §5, council-1781132987 concurring): a figure in a lesson is `(family_id, instance_data)` — typed data, not code. Render logic is verified ONCE per family; the pipeline supplies typed DATA only; agents never draw free-form. The spec names **~6 families** (§5.2). **Only 1 is built** — graph/tree, shipped by Plan 3 (MergeSort port). The other **5 families + the 24-primitive migration are spec'd (§5.2, §5.5) but live in NO numbered plan** — they slipped between Plans 5/7/W. That is Plan V. It is load-bearing because **Plan 5 generates figure *instances* and can only emit figures for families that exist** — without families 2–6, PS/ALO/POO/SO concepts get no real figure (the sparse-figure symptom Alex surfaced 2026-06-13). The 6 are grounded in the real subject-findings scan (spec line 624: DP tables · pruning/centrality trees+graphs · continuous-PDF shading · fork-descriptor timelines · instruction steppers), **not invented**.

---

## 2. CURRENT STATE — inventory (existing components → target family)

**Infrastructure (NOT migration targets):** `AlgoStepperShell.tsx` (playback shell — frames/scrubber/gates/a11y/voice; contract below), `AlgoStepperShellSmoke.tsx` (CI smoke only), `HatchDefs.tsx` (shared SVG `<defs>`), `motion-helpers.tsx` (`TweenText`/`FadeText`/`DrawLine`/`DrawPath`/`PopIn`), `theme.ts` (tokens), `families/familyRegistry.ts` (production renderer registry), `families/GraphTreeFamily.tsx` + `graphTreeInvariants.ts` (the one built family), `vizRegistry.ts` (legacy E3 — dies with V6 retirement), `VizDemoPage.tsx` (the `/viz` gallery, review surface only — NOT a lesson surface).

**24 primitives → target families.** `Anchor` = CORPUS ANCHOR (a real lesson/subject/instance backs it); `Anchorless` = render logic salvageable but no content source; `0prop` = zero-prop hardcoded scenario (must be re-expressed as typed JSON before any instance can exist); `props` = already has a real parametric prop interface.

| Component | Target family | State | Subject | Note |
|---|---|---|---|---|
| `GraphTreeFamily.tsx` | 1 graph/tree | Anchor, props | PA | ONLY fully-wired family; `viz-pa-mergesort-001` instance is live, DB-served, lesson-wired via `FigureReveal` |
| `RecursionTree.tsx` | 1 graph/tree | Anchor, ~0prop (`n=5` default) | PA | Only LEGACY-registered component (`vizRegistry` + `viz-ids.yaml`); migrate to graph/tree, exit vizRegistry |
| `ArrayStepper.tsx` | 2 sequence/array | Anchorless, props (`values?`) | PA | Most orphaned: no VizDemoPage entry, no registry, no lesson. Bar/compare/swap stepper logic salvageable |
| `DPWastedWork.tsx` | 3 matrix/grid (+tree sub-view) | Anchorless, 0prop (`N=5`) | PA | Straddles matrix/grid (DP table) + graph/tree (call tree). Primary home = matrix/grid w/ tree overlay as layout variant |
| `PageTableWalk.tsx` | 3 matrix/grid | Anchor, 0prop | SO | "SO-4 page table walk"; re-express `{virtualAddress, pageTableEntries, tlbState, physFrames}` |
| `MatrixTransform.tsx` | 3 matrix/grid | Anchor, props | ALO | "ALO-3 eigenvector"; ONLY primitive with real props but NOT on AlgoStepperShell — needs Shell migration |
| `BayesTree.tsx` | 4 chart/distribution | Anchorless, 0prop | PS | "PS-2 Bayes tree"; re-express `{priorP, sensitivity, fpr, eventLabel, testLabel}` |
| `NumLineDirect.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis primitive (parametric) |
| `SumPlotTracker.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis primitive (parametric) |
| `SlopeCounter.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis primitive (parametric) |
| `SigmaStackedBar.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis primitive; the V8 "Sigma all-black" rendered-audit defect re-expresses as a chart instance (§5.5) |
| `CompareFrames.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis primitive (parametric) |
| `NPGadget.tsx` | 4→**1 graph/tree?** | Anchorless, 0prop | PA | "PA-7 world-first"; it IS a bipartite graph — maps better to graph/tree. **PM decision needed** |
| `TcpCwnd.tsx` | 5 timeline/protocol | **Anchorless (spec-flagged §5.5)**, 0prop | RC | Full Reno; render salvaged, NO instance without RC source rows |
| `TcpHandshake.tsx` | 5 timeline/protocol | Anchor, 0prop | RC | "RC-2"; re-express `{phases: PhaseConfig[], actors: Actor[]}` |
| `OsiEncap.tsx` | 5 timeline/protocol | Anchor, 0prop | RC | "RC-1" 7-layer encap; re-express `{payloadBytes, mtu, layers}` |
| `SchedulerGantt.tsx` | 5 timeline/protocol | Anchor, 0prop | SO | "SO-2"; re-express `{jobs, quantum?, algo}` |
| `RaceMutex.tsx` | 5 timeline/protocol | Anchor, 0prop | SO | "SO-3"; simplest re-expression (one canonical instance) |
| `ProcessFSM.tsx` | 6 state-machine/code-trace | Anchor, 0prop | SO | "SO-1" process lifecycle FSM; re-express `{states, transitions, trace}` |
| `CppVTable.tsx` | 6 state-machine/code-trace | Anchor, 0prop | POO | "POO-1/4 world-first"; re-express `{classes, scenario}` |
| `Tls0RttReplay.tsx` | 6 state-machine/code-trace | **Anchorless (spec-flagged §5.5)**, 0prop | RC | Render salvaged, NO instance without RC source rows |

**Aggregate:** 17 of 24 are zero-prop hardcoded; 2 spec-flagged anchorless (TcpCwnd, Tls0Rtt — yield render logic only, never instances, RC corpus = only ~3–6 lectures); ArrayStepper fully orphaned; NPGadget + DPWastedWork have ambiguous family homes (PM calls).

---

## 3. THE 6 FAMILIES — 1 done, 5 to build

**Family 1 — graph/tree — DONE (Plan 3).** Reference implementation. `GraphTreeFamily.tsx` + `graphTreeInvariants.ts` (`depthMonotoneY`, `siblingXOrder`, `finalState`, `highlightExistence`) + `__tests__/mergesortTrace.ts` oracle + `viz-pa-mergesort-001` instance. Registered in BOTH registries; all 3 gates green. **Copy its shape for every new family.**

For each unbuilt family below: **Covers** (real corpus needs) · **Renders** (sub-layouts) · **Semantic invariants** (in rendered px — the §5.4 gate) · **Subjects** + corpus strength.

### Family 2 — sequence/array
- **Covers:** PA sorting/merge-sort traces (the `lectie.html` MergeSort demo is the spec's reference scrubber, §4.6; `ArrayStepper` + mergesort trace are seeds), sliding windows, two-pointer, greedy scans · ALO per-iteration quantity vectors (CG/secant) · PS sample arrays / lab-CSV data vectors stepped for descriptive stats · POO STL container & iterator walks, ownership-move sequences · SO fd-tables / buffer states.
- **Renders:** linear index-labeled cells; pointer/cursor markers (i, j, lo, hi, pivot); sliding-window span highlight; sort-state recoloring (compared/swapped/sorted-suffix); linked-sequence pointer walk; per-step delta-band. Callouts anchored to active cell/pointer.
- **Invariants (px):** `index-monotone-x` (cell i+1 right of cell i) · `cardinality-preserved` (rendered cell count == reference length every frame) · `pointer-in-bounds` · `final-state trace-match` (final cells == reference output, e.g. sorted) · `swap-conservation` (swap permutes, never edits the multiset).
- **Subjects:** PA (primary), ALO, PS, POO, SO. **Corpus need: STRONG** — merge-sort is the spec's own canonical reference + named "instruction steppers" in §624 + multiple seeds.

### Family 3 — matrix/grid
- **Covers:** PA DP tables (§624 names "DP tables" explicitly; `DPWastedWork` seed) + adjacency matrices · **ALO is the dominant need** — Gaussian/LU/Householder QR/Jacobi/SVD matrix states (Tema 3/4/5); §4.3 step-trace checks each intermediate matrix state; the 7-named-quantity CG skeleton (>4 rows) lives here · SO page tables / memory layouts (`PageTableWalk` seed) · POO vtable layouts (`CppVTable` seed) as a labeled slot grid.
- **Renders:** row×col grid w/ headers; per-cell value + fill state (computed/pending/pivot/eliminated); DP fill-order highlight; row-operation row-band deltas; adjacency on/off cells; memory grid w/ frame→page arrows. Callouts anchored to active cell or row-band.
- **Invariants (px):** `grid-rectangularity` (equal cell count, aligned col x per row) · `row-monotone-y` / `col-monotone-x` · `dp-fill-order` (filled set at k ⊇ k−1; never un-fills) · `per-cell trace-match` (each filled value == reference table) · `pivot-existence` (pivot cell on declared/diagonal position).
- **Subjects:** PA (DP), ALO (primary — matrix step-trace), SO, POO. **Corpus need: STRONG** — two §624 needs + ALO's whole step-trace surface + 3 seeds.

### Family 4 — chart/distribution
- **Covers:** **PS dominant** — PDF/PMF/histograms over lab CSVs; the **continuous-PDF shading** named in §624 (the `probabilistic` concept_type's §4.3 1-screen anchor); `SigmaStackedBar`/`SumPlotTracker` seeds; the V8 "Sigma all-black" defect re-expresses here · ALO convergence plots (residual-norm vs iteration; error decay) · PA complexity/growth curves (illustrative).
- **Renders:** axes w/ ticks; continuous PDF curve; shaded area-under-curve (P(a≤X≤b) — the named need); discrete histogram; stacked/grouped bars; convergence scatter+line (log-y option); running-sum marker (`SumPlotTracker` seed). Callouts anchored to shaded region or data point.
- **Invariants (px):** `axis-monotone` (tick x/y increases with value) · `shading-within-curve` (shaded bbox between curve and baseline) · `bar-height-proportionality` (rendered height ratios == data ratios — kills V8 all-bars-same-height/all-black) · `point-on-curve trace-match` · `total-mass / normalization` (PMF mass→constant baseline; CDF non-decreasing).
- **Subjects:** PS (primary), ALO, PA. **Corpus need: STRONG for PS** (named §624 + whole `probabilistic` anchor + 2 seeds); moderate ALO.

### Family 5 — timeline/protocol
- **Covers:** **RC dominant render-need** — network protocol exchanges (TCP handshake, DNS, HTTP/WebSockets; `TcpHandshake` seed), message-sequence diagrams · SO process/thread timelines + scheduling Gantt (§624 "fork-descriptor timelines"; `SchedulerGantt` seed, fixed 4-job batch → instance) + concurrency interleavings (`timing` concept_type's clean-vs-breaking contrast; `RaceMutex` seed) · PS Poisson/arrival-process event timelines.
- **Renders:** horizontal lanes (one per process/thread/host) on a shared time axis; inter-lane message arrows; Gantt blocks; single-timeline event ticks; clean-vs-breaking two-track contrast. Callouts anchored by **element, never color name** (QR-WARM-GALBEN class, §5.3).
- **Invariants (px):** `time-monotone-x` (event t+1 ≥ event t per lane) · `lane-y-stable` (constant y per lane across frames) · `arrow-endpoints-on-lanes` (+ send-before-receive: tail-x < head-x) · `non-overlapping-Gantt` (one job per lane in x-extent) · `event-order trace-match` (rendered L→R order == reference).
- **Subjects:** RC (primary), SO (Gantt/concurrency/fork), PS (arrivals). **Corpus need: MODERATE-TO-STRONG.** ⚠️ **Thinness flag:** RC corpus is the weakest (~3–6 lectures, no grading doc, no past papers — gaps G7/G8/G15), so RC *instances* will be sparse even though the *render need* is real; **the SO Gantt/concurrency content is the reliable driver**, not RC alone. (Anchorless TcpCwnd Reno falls here — render logic only.)

### Family 6 — state-machine/code-trace
- **Covers:** **POO dominant exam need** — the course MCQ "Ce se întâmplă la execuția următorului program?" (mental C++ trace → output / compile error / runtime error); barem = step-by-step execution trace = the `code-trace` concept_type's §4.3 line-by-line stepper (state table per click, current line highlighted). §624 names "instruction steppers". **Single biggest exam-prep payoff.** · SO POSIX command/syscall effect traces ("Efectul acestei comenzi: ?") · PS R-script execution traces · PA finite automata / NP-gadget state machines (`NPGadget`, `ProcessFSM` seeds), Markov chains · process-state FSMs (`ProcessFSM`).
- **Renders:** two-pane code listing (current line highlighted) + variable-state table updating per step; FSM/automaton node-edge diagram w/ active-state highlight + consumed-input cursor; Markov transition diagram w/ current-state highlight; accreting output buffer. Callouts anchored to active line or active state node.
- **Invariants (px):** `single-active-state` (exactly one line/state highlighted per frame) · `highlight-existence` (reuse graph/tree's) · `var-table trace-match` (table values at k == reference interpreter; output buffer == reference stdout prefix — the strongest, catches "animates something the code doesn't do") · `transition-edge-exists` (no teleport between unconnected states) · `monotone-program-counter` (straight-line: line index non-decreasing; on jump, target exists).
- **Subjects:** POO (primary — exam-critical), SO, PS, PA. **Corpus need: STRONG** — named §624 + the POO exam's dominant item type IS a code trace + multiple seeds.

**Cross-cutting:** all five reuse the proven graph/tree pattern — an orientation/monotonicity invariant (the `depthMonotoneY` analog), a `highlightExistence` check, and a `finalState`/per-step **trace-match** against a reference execution (§5.4). The ONLY thin corpus anchor is RC (inside family 5); every other family is backed by ≥1 named §624 need + ≥1 seed + a primary subject with substantial corpus. None of the other four is speculative.

---

## 4. FAMILY CONTRACT + reusable 4b harness

**One figure = `(family_id, instance_data)`.** Authoring writes a YAML to `content/{SUBJECT}/viz/{id}.yaml` (`id`, `subject`, `family_id`, `language`, `instance.method_kc_id`, `instance.data_json` — a JSON **string**, not a YAML map: `src/main/kotlin/jarvis/content/VizInstance.kt:21`, kaml can't bind `JsonElement`). Server loads via `ContentRepo.loadVizInstances` / `loadAllVizInstances` (`ContentRepo.kt:56,83`), serves read-only `GET /api/v1/viz/{id}` → `ApiVizInstanceReply{id,subject,family_id,language,data_json}` (`VizInstanceRoutes.kt:50`); snake_case wire, backend casing wins; 200/404/401; **unknown family_id allowed server-side** (server is family-agnostic; the frontend registry is the gate). Client `FigureReveal.tsx` fetches, checks `reply.family_id === binding.family_id` AND `familyRegistry[reply.family_id]` exists (else degrades to stepped-text, zero `console.error`), mounts the family with `{instanceId, dataJson, language, labels, onStep}`. The family renders frames into `AlgoStepperShell` — **the shell owns ALL playback** (scrubber, frame counter, play/back/fwd/reset, keyboard, hash deep-link, prediction gates). Frozen signatures touched by adding a family: **NONE** — `family_id` + `data_json` are opaque strings end-to-end; adding a family is purely additive (`VizInstanceRoutes.kt`, `ContentRepo`, `ApiFigureBinding`/`ApiVizInstanceReply` are already family-generic).

### "To add family X you implement A/B/C and register D/E" — checklist
**Implement:**
- **A. `families/XFamily.tsx`** — exported `XData`/`XStep`/`XState` types + a **zod-free fail-loud** `parseXData(dataJson, instanceId): XData` (JSON.parse then structurally validate every field; throw a plain `Error` naming **both instanceId AND the offending field/index** — INV-5.5; cross-reference integrity is part of parsing, e.g. every highlight id exists in nodes); `framesFromXData(data): Frame<XState>[]` (pure; `Frame<S> = {state, aria, meta?}`, `AlgoStepperShell.tsx:17`; XState must expose BOTH the values the trace-harness compares AND the IDs the renderer/invariants need for DOM lookup; deltas mutate state cumulatively; `aria` carries the step callout); `layoutX` using **`measureLabelWidth` for no-clip BY CONSTRUCTION** (off-DOM 2D-canvas text-extent measurement ONLY — explicitly NOT a canvas figure, has an SSR monospace fallback; reserve node-box space + the Task-9 callout treatment: wrap to ≥2 lines, shrink to `CALLOUT_MIN_FONT=8`, clamp center-x by half the widest line so `[cx−w/2, cx+w/2] ⊆ [PAD, SVG_W−PAD]`); `renderFrame` with **element-anchored callouts** (anchored to the rendered element, never a detached footer, never a hardcoded color name); the `XFamily` component mounting `AlgoStepperShell` with a **`useCallback`-stable `onStep`** (a fresh arrow each render → shell effect re-fires → setState → infinite loop — documented landmine, `GraphTreeFamily.tsx:288`). **SVG-only, 480×360.**
- **B. `families/__tests__/xTrace.ts`** — independent reference oracle that runs the REAL algorithm from the instance's seed (graph/tree derives the input array from the root node label then calls `mergesortReference`); design it to read the same seed the instance encodes so it generalizes across instances, not one pinned input.
- **C. `families/xInvariants.ts`** — `export const X_INVARIANTS: XInvariant[]`, each `(container, frame, stepIdx, allFrames) => {ok} | {ok:false,message}` reading domain truth **back from rendered SVG geometry** (parse `transform="translate(cx,cy)"` off `data-node-id`, or x/y/width attrs) — NOT from the data model. This is the §5.4 semantic-correctness gate (motivating defect QR-GEO-1: a reflection rendered with a 2.39× stretch while teaching length preservation).

**Register (then the 3 gates auto-cover EVERY instance, no per-instance code):**
- **D.** Add `"X": XFamily` to `familyRegistry.ts` (production renderer).
- **E.** Add `"X": { referenceExecutor, invariants: X_INVARIANTS }` to `HARNESS_REGISTRY` in `__tests__/traceMatchHarness.test.tsx:39` (oracle + invariants).

**Also required (not optional):**
- **F.** A wrong-trace fixture under `__tests__/fixtures/` (NEVER under `content/` — `ContentRepo` globs `content/{subject}/viz/`) + a `seededWrongTrace`-style assert-RED test proving the harness THROWS naming the bad step (proves gate-3 is alive for your oracle).
- **G.** A **REQUIRED reachable mount** painting `data-testid="{prefix}-root"` (a VizDemoPage gallery card or a lesson route) so `e2e/family-no-clip.spec.ts` can drive it; reuse `assertNoClip(page, "{prefix}-root")` as-is at 2 viewport heights.
- **H.** Author ≥1 `content/{SUBJECT}/viz/{id}.yaml` instance where a corpus anchor exists.

**The 3 auto-gates (family-agnostic, run against the REAL corpus not fixtures):**
1. **`traceMatchHarness.test.tsx`** — globs EVERY `content/*/viz/*.yaml`, asserts ≥1 found (0 → RED), and per instance: family_id in BOTH registries or REDs loudly; trace-match (frames == oracle, equal step count); mounts the real component, steps every frame clicking `{prefix}-step-fwd`, runs ALL your invariants against the mounted DOM each step.
2. **`seededWrongTrace.test.tsx`** — your wrong-trace fixture must make `assertHarnessForInstance` throw naming the offending step index.
3. **`e2e/family-no-clip.spec.ts`** + `assertNoClip.ts` — Playwright at 2 viewport heights: zero overflow / text-clip / interactive-overlap before AND after stepping, scrubber fwd→back round-trips the counter (no one-shot), zero 4xx/5xx + no error-text regex in the family subtree.

---

## 5. PROPOSED PLAN V SHAPE

- **Frontend-only.** File scope = `tutor-web/src/components/viz/**` (+ `content/{SUBJECT}/viz/*.yaml` for the instances). No Kotlin/wire changes (family_id + data_json are opaque, already family-generic — confirmed §4). Zero frozen signatures touched.
- **One family per task.** Each task = build family X (A/B/C) + register (D/E) + the wrong-trace drill (F) + the required mount (G) + author instances-from-real-content where a corpus anchor exists (H, skip anchorless content) + **eyeball a full-size screenshot** of the mounted family on its route (render-before-claim-done; viz-no-clip gate is machine-checked AND eyeballed). 5 tasks for families 2–6, in dependency-free order (any order works; suggest by corpus strength: 2, 3, 6, 4, 5 — RC-thin family 5 last).
- **Migrate the hardcoded primitives** as part of each family task: extract render logic into `XFamily.tsx`, delete the hardcoded scenario, re-express the baked content as `data_json`. RecursionTree migrates into graph/tree (family 1) and exits `vizRegistry`. Anchorless seeds (TcpCwnd, Tls0Rtt) yield render logic only — no instance.
- **SVG-only LOCKED** (§5.6): no three/webgl/webgpu/canvas-*figures*, no scroll/mouse-reactive decoration; 480×360 SVG in AlgoStepperShell, Framer/motion, d3-hierarchy. New rendering deps need a Decision Log entry.
- **Both-skin baseline note:** Plan V builds in the CURRENT (cool) skin only. Do NOT add warm-skin baselines in V — **W comes after V** precisely so its dual-skin baselines cover all 6 families in one pass (no re-baseline). Keep callouts theme-agnostic (anchor-not-color) so W is a clean overlay.
- **Do NOT wire new families through the legacy channel** (`vizRegistry.ts` / `viz-ids.yaml` / Kotlin `checkVizReferences`). That's the V1 bottleneck (1 of 24 ever registered). All new families register ONLY in `familyRegistry` + `HARNESS_REGISTRY`. The legacy lockstep dies with V6 retirement.

---

## 6. SEQUENCING

- **V before Plan 5.** Plan 5 generates figure *instances* and can only emit figures for families that EXIST. Run V first or Plan 5 produces no real figures for PS/ALO/POO/SO (the sparse-figure symptom). **Order: V → 5 → 7.**
- **W after V.** W's dual-skin (warm) baselines should cover all 6 viz families at once — building V first means W re-baselines nothing.
- **Do NOT run V parallel with W.** Both live in `tutor-web/src/components/viz/**` — guaranteed file intersection, which violates the two-lane zero-file-intersection rule (`project_build_workflow`). W is folded AFTER V, not alongside.
- This recon was produced ahead of plan-writing; the plan-index row V already records the path of this doc + the sequencing.

---

## 7. RISKS / OPEN PM QUESTIONS

1. **Thin corpus anchor — RC (family 5).** Render need is real + §624-named, but RC has only ~3–6 lectures, no grading doc, no past papers (gaps G7/G8/G15) → RC instances will be sparse. Build family 5 justified by SO scheduling/concurrency; expect few/zero RC instances. **PM: accept SO-only instances for family 5 at first ship?**
2. **24→6 migration scope.** 17/24 primitives are zero-prop and need full content re-expression to become instances. **PM: migrate ALL primitives in Plan V, or only the corpus-anchored ones (defer anchorless render-only salvage)?** Anchorless TcpCwnd + Tls0Rtt are render-only regardless.
3. **One plan vs split.** 5 families + 24-primitive migration is large. **PM: build all 5 families in one Plan V, or split (e.g. V-a = families 2/3/6 strong-corpus, V-b = 4/5)?** Two-lane is blocked by the viz file-intersection, so a split would be SEQUENTIAL plans, not parallel lanes.
4. **NPGadget family assignment.** Currently demoed under "chart/distribution" but it IS a bipartite graph → maps better to graph/tree (family 1, already built). **PM call:** add it as a graph/tree instance vs. force it into chart/distribution.
5. **DPWastedWork straddle.** Spans matrix/grid (DP table) + graph/tree (call tree). Proposed: matrix/grid primary with the call-tree as a layout variant. **PM confirm.**
6. **MatrixTransform Shell migration.** The only primitive with real props but NOT on AlgoStepperShell (own animation loop). Needs Shell migration as part of family-3 wiring — slightly more work than the zero-prop seeds.
7. **Instance authoring vs digestion.** Re-expressing baked content into `data_json` by hand vs. waiting for Plan 5's digestion to emit instances. Plan V should hand-author ≥1 instance per anchored family to drive the e2e mount + prove the family; Plan 5 then populates breadth.

---

**Cold-start TL;DR for the next session:** copy `GraphTreeFamily.tsx`/`graphTreeInvariants.ts`/`__tests__/mergesortTrace.ts`; per family implement A (`XFamily.tsx`) + B (`xTrace.ts` oracle) + C (`xInvariants.ts`), register D (`familyRegistry`) + E (`HARNESS_REGISTRY`), add F (wrong-trace fixture+test) + G (reachable `{prefix}-root` mount) + H (≥1 content instance), eyeball the render. The 3 gates then auto-cover every instance. SVG-only, cool-skin only, frontend-only, no legacy `vizRegistry` wiring. Sequence V → 5 → 7, W after.
