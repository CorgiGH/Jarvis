# Plan V: Viz Families 2–6 Build-Out + 24-Primitive Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development`. **Plan V executes in the MAIN working tree `C:\Users\User\jarvis-kotlin`, directly on `main`.** Plan V does NOT run a parallel Lane B — five families all live in `tutor-web/src/components/viz/**`, a guaranteed file intersection (the [[project_build_workflow]] zero-file-intersection rule forbids a second lane on the same files). The five family tasks (1–5) are therefore **sequential**; Plan W is folded AFTER Plan V, never alongside. **Executors NEVER edit this plan doc** — a plan/reality mismatch is a BLOCKED task escalated to the PM verbatim (protocol rule 3).

**Goal:** Spec §5.2 names ~6 viz families; only family 1 (graph/tree) is built (Plan 3). Plan V builds families **2 sequence/array · 3 matrix/grid · 4 chart/distribution · 5 timeline/protocol · 6 state-machine/code-trace**, and migrates the 24 hand-coded primitives (recon §2 inventory) into the family system: extract each primitive's render logic into its target family, delete the hardcoded scenario, re-express the baked content as typed `data_json` instances from the REAL corpus where a corpus anchor exists. Each family is verified by the SAME three auto-gates Plan 4b shipped for graph/tree — trace-match harness (INV-5.1), semantic-invariant asserts read back from rendered SVG px (INV-5.2, spec §5.4), no-clip per-instance at 2 viewport heights (INV-5.3). Task 6 re-calibrates the Impeccable design gate and flips its CI `|| true` to blocking, runs the whole-build review council, and eyeballs all six families.

**Architecture:** **Frontend-only.** File scope = `tutor-web/src/components/viz/**` + `content/{SUBJECT}/viz/*.yaml` (the instances) + the Task-6 Impeccable tool/CI fold. **ZERO frozen-signature changes** — `family_id` and `data_json` are opaque strings end-to-end (`VizInstance.kt:21`, `VizInstanceRoutes.kt` reply, `ApiFigureBinding`); adding a family is purely additive in `familyRegistry.ts` + the harness `HARNESS_REGISTRY` (verified §0). **SVG-only, cool-skin only** (§5.6/§5.7: 480×360 SVG in `AlgoStepperShell`, Framer Motion, d3-hierarchy; no three/webgl/webgpu/canvas-figures; no warm-skin baselines — W comes after V and re-baselines all six at once). **NO legacy `vizRegistry.ts` / `viz-ids.yaml` / Kotlin `checkVizReferences` wiring** — that is the V1 bottleneck (1 of 24 ever registered); new families register ONLY in `familyRegistry` + `HARNESS_REGISTRY`. The legacy lockstep dies with V6 retirement.

**Binding spec:** `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` §5 (§5.2 families L313–322, §5.3 family contract, §5.4 verification-per-family, §5.5 24-primitive migration, §5.6 SVG-only, §5.7 stack, §5.8 INV-5.1…5.5) + line 624 subject-findings family mapping. **Recon (the spine of this plan):** `build-review/2026-06-13-planV-viz-families-recon.md` (inventory table §2, per-family invariants §3, the A–H checklist §4, the AlgoStepperShell contract §4, landmines). **Plan-index:** `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` row V. **Format template mirrored:** `docs/superpowers/plans/2026-06-12-plan4b-rendered-gates.md`.

**Sequencing:** **V → 5 → 7.** Plan 5 generates figure *instances* and can only emit figures for families that EXIST — run V first or Plan 5 produces no real figure for PS/ALO/POO/SO (the sparse-figure symptom Alex surfaced 2026-06-13). **W after V** (W's dual-skin baselines cover all six families once). **V ∥ W forbidden** (viz file intersection). Within V, family tasks 1→5 are ordered by corpus strength; Task 6 closes.

**Tech stack:** vitest for the trace-match harness + invariants (the `traceMatchHarness.test.tsx` / `seededWrongTrace.test.tsx` pattern Plan 4b shipped), @playwright/test for `family-no-clip.spec.ts` (the `assertNoClip` helper, 2 viewport heights), no new runtime dependencies, no LLM calls anywhere in this plan, no paid APIs.

---

## Section 0 — Verified ground truth (recon 2026-06-13 + plan-write re-verification 2026-06-13, post-4b/6-merge)

Re-verified against the repo + read-only state at plan-write (every claim below was checked, not trusted from memory — [[feedback_get_it_right_first_time]], the trust-but-verify rule):

1. **Git state:** `main` @ `94b4082` (descendant of the recon's `09f5add`; Plans 4b + 6 are merged — `VizInstanceRoutes.kt`, `FigureReveal.tsx`, `traceMatchHarness.test.tsx`, `graphTreeInvariants.ts`, `seededWrongTrace.test.tsx`, the Impeccable fail-open CI step, `chromeStrings.ts` all live on main). Do NOT trust the recon's pre-merge framing of the harness trio as "to be built" — they EXIST and are the COPY targets.
2. **`familyRegistry.ts` = ONE entry:** `{ "graph-tree": GraphTreeFamily }` (verified). `FamilyRendererProps = { instanceId, dataJson, language, labels?, onStep? }` (`onStep?: (idx, lastIdx) => void`, ADDITIVE Plan-4b Task 4). Five family_ids missing: `sequence-array`, `matrix-grid`, `chart-distribution`, `timeline-protocol`, `state-machine`. **These five string ids are the canonical family_id values Plan V freezes by use** (Plan 5 will emit instances keyed by them) — name them EXACTLY as listed; a typo splits the namespace.
3. **`HARNESS_REGISTRY` = ONE entry** in `tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx:39` — `{ "graph-tree": { referenceExecutor, invariants: GRAPH_TREE_INVARIANTS } }`. The harness globs `content/*/viz/*.yaml` (`import.meta.glob`, `?raw`, eager), REDs on 0 instances, and per instance asserts: family_id in BOTH `familyRegistry` AND `HARNESS_REGISTRY` (else loud RED), trace-match (rendered frames == oracle, equal step count), then mounts the real component and runs every registered invariant against the mounted DOM at every step. `assertHarnessForInstance(yamlRaw, path)` is exported and reused by `seededWrongTrace.test.tsx`. **The harness is family-agnostic and total** — once a family is registered in BOTH registries, the 3 gates auto-cover every instance of it with ZERO per-instance code (this is the architecture's whole point).
4. **`content/*/viz/` = ONE instance on disk:** `content/PA/viz/viz-pa-mergesort-001.yaml` (`family_id: graph-tree`, `language: ro`, `instance.data_json` = a JSON **string**, not a YAML map — `VizInstance.kt:21`, kaml cannot bind `JsonElement`). This is the reference instance shape every authored instance copies.
5. **24 primitives present** (verified `ls tutor-web/src/components/viz/*.tsx`): the migration targets are the recon §2 table (reproduced below with PM rulings applied). Infra files (NOT migration targets): `AlgoStepperShell.tsx`, `AlgoStepperShellSmoke.tsx`, `HatchDefs.tsx`, `motion-helpers.tsx`, `theme.ts`, `families/*`, `vizRegistry.ts` (legacy), `VizDemoPage.tsx` (the `/viz-demo` gallery — review surface, NOT a lesson surface).
6. **Frozen signatures UNTOUCHED end-to-end:** `VizInstance.kt` (`id/subject/family_id/language/instance{method_kc_id,data_json}`), `ContentRepo.loadVizInstances`/`loadAllVizInstances` (the latter loops every subject — `ContentRepo.kt:83`), `VizInstanceRoutes.kt` (`GET /api/v1/viz/{instanceId}` → `ApiVizInstanceReply{id,subject,family_id,language,data_json}`, 200/404/401, **unknown family_id allowed server-side** — server is family-agnostic, the frontend registry is the gate). `FigureReveal.tsx` already dispatches on `familyRegistry[reply.family_id]` and degrades to stepped-text on a registry miss (zero console.error). **A new family becomes lesson-renderable the moment it is in `familyRegistry` — no wire/route/Kotlin change.** Lock check for every family task: `interface-signatures-lock.md` has NO per-family entry (verified zero hits for the five new ids) — registering a family is NOT a frozen-surface change. If a task ever finds it needs a wire/route change → STOP, PM.
7. **Layout-measurement helper is in `GraphTreeFamily.tsx`** (`measureLabelWidth`, off-DOM 2D-canvas text-extent, SSR monospace fallback, explicitly NOT a canvas figure — §5.6 compliant) + the Task-9 callout treatment (wrap to ≥2 lines, shrink to `CALLOUT_MIN_FONT=8`, clamp center-x by half the widest line so `[cx−w/2, cx+w/2] ⊆ [PAD, SVG_W−PAD]`). **Copy this helper + the callout layout verbatim into each new family** — it is the "no-clip BY CONSTRUCTION" mechanism (§5.3); do not re-invent.
8. **The `useCallback`-stable `onStep` landmine** (`GraphTreeFamily.tsx:288`): the shell's `onStep` effect deps on `props.onStep`; a fresh arrow every render re-fires the effect → setState → re-render → new arrow → infinite loop. Every family that threads `onStep` MUST wrap it in `useCallback` with a minimal dep list. Copy the comment.
9. **`MatrixTransform.tsx` ALREADY mounts `AlgoStepperShell`** (verified — recon §7 #6's "the only primitive NOT on AlgoStepperShell, needs Shell migration" is STALE: it imports `AlgoStepperShell` at line 2 and mounts it at line 276). So family-3's MatrixTransform migration is a render-logic extraction like the others, not a Shell port. **Recon-correction baked into Task 2.**
10. **The mount surface (checklist item G):** the proven, CI-driven mount is the `/viz-demo` gallery (`VizDemoPage.tsx`). The app uses `<BrowserRouter basename="/tutor">` (`main.tsx:248`) with `<Route path="/viz-demo">` (`main.tsx:257`), so the live URL is **`/tutor/viz-demo`** (this is why `family-no-clip.spec.ts:17` navigates there and is green — no discrepancy). `VizDemoPage` mounts each viz inside a `<section data-testid="viz-demo-{name}">` and the family inside paints `data-testid="{prefix}-root"` (the shell root). **Each family task adds a gallery card mounting the family on a REAL authored instance's `data_json`, and extends `family-no-clip.spec.ts` to drive `{prefix}-root` at 2 viewport heights.**
11. **Impeccable gate is fail-open:** `.github/workflows/test.yml:84-85` runs `npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true`. `tools/impeccable-rules.json` enables `["side-tab","layout-transition"]` (subset). `tools/impeccable-filter.mjs` exits 1 when ≥1 enabled finding survives; the `|| true` keeps it non-blocking. `build-review/impeccable-calibration-2026-06-12.json` is the calibration corpus. Task 6 re-calibrates over the new viz code + flips `|| true` → blocking.
12. **Working-tree landmine:** door/demo files + many `build-review/` artifacts are deliberately UNTRACKED. **Never `git add -A`, never `git clean`, never branch-switch on main, never merge `door-compare`.** Commits stage explicit paths only. Keep Kotlin test method names ASCII if any task touches Kotlin (it does not — V is frontend + content YAML only).

### The 24-primitive → target-family migration table (recon §2, PM rulings applied)

`Anchor` = a real lesson/subject backs it (author an instance); `Anchorless` = render logic salvageable, **NO instance** (TcpCwnd, Tls0Rtt — RC corpus too thin, spec-flagged §5.5); `0prop` = hardcoded scenario (re-express the baked content as `data_json` before any instance); `props` = already parametric.

| # | Component | Target family (PM-ruled) | State | Subject | Migration note |
|---|---|---|---|---|---|
| — | `GraphTreeFamily.tsx` | 1 graph/tree | Anchor, props | PA | DONE (Plan 3) — the reference; `viz-pa-mergesort-001` live |
| 1 | `RecursionTree.tsx` | **1 graph/tree** | Anchor, ~0prop (`n=5`) | PA | migrate to graph/tree as an INSTANCE; **exit `vizRegistry`** (the only legacy entry) |
| 2 | `ArrayStepper.tsx` | 2 sequence/array | Anchorless→PA-anchor, props | PA | most-orphaned; bar/compare/swap stepper logic is the family-2 seed |
| 3 | `DPWastedWork.tsx` | **3 matrix/grid (primary), call-tree = layout variant** | Anchorless→PA-anchor, 0prop | PA | PM ruling: matrix/grid PRIMARY, call-tree a layout variant (NOT split into two families) |
| 4 | `PageTableWalk.tsx` | 3 matrix/grid | Anchor, 0prop | SO | "SO-4 page table walk"; re-express `{virtualAddress, pageTableEntries, tlbState, physFrames}` |
| 5 | `MatrixTransform.tsx` | 3 matrix/grid | Anchor, props (**already on Shell, §0 #9**) | ALO | "ALO-3 eigenvector"; render-logic extract, NOT a Shell port |
| 6 | `BayesTree.tsx` | 4 chart/distribution | Anchorless→PS-anchor, 0prop | PS | "PS-2 Bayes tree"; re-express `{priorP, sensitivity, fpr, eventLabel, testLabel}` |
| 7 | `NumLineDirect.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis parametric |
| 8 | `SumPlotTracker.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis parametric (running-sum marker) |
| 9 | `SlopeCounter.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis parametric |
| 10 | `SigmaStackedBar.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis; the V8 "Sigma all-black" defect re-expresses as a chart instance (§5.5) |
| 11 | `CompareFrames.tsx` | 4 chart/distribution | Anchor, props | PS | PS-basis parametric |
| 12 | `NPGadget.tsx` | **1 graph/tree (PM ruling — it is bipartite)** | Anchorless, 0prop | PA | author as a **graph/tree (family 1) instance**, NOT chart/distribution; render-logic salvage into family 1 |
| 13 | `TcpCwnd.tsx` | 5 timeline/protocol | **Anchorless (§5.5) — render-only, NO instance** | RC | full Reno; salvage render logic into family 5, author no instance |
| 14 | `TcpHandshake.tsx` | 5 timeline/protocol | Anchor, 0prop | RC | "RC-2"; re-express `{phases, actors}` (author iff RC anchor lands — see Task 5 ruling) |
| 15 | `OsiEncap.tsx` | 5 timeline/protocol | Anchor, 0prop | RC | "RC-1" 7-layer encap; re-express `{payloadBytes, mtu, layers}` (RC, see Task 5 ruling) |
| 16 | `SchedulerGantt.tsx` | 5 timeline/protocol | Anchor, 0prop | SO | "SO-2"; re-express `{jobs, quantum?, algo}` — **SO is family-5's reliable driver** |
| 17 | `RaceMutex.tsx` | 5 timeline/protocol | Anchor, 0prop | SO | "SO-3"; simplest re-expression (one canonical SO instance) |
| 18 | `ProcessFSM.tsx` | 6 state-machine/code-trace | Anchor, 0prop | SO | "SO-1" process lifecycle FSM; re-express `{states, transitions, trace}` |
| 19 | `CppVTable.tsx` | 6 state-machine/code-trace | Anchor, 0prop | POO | "POO-1/4"; re-express `{classes, scenario}` |
| 20 | `Tls0RttReplay.tsx` | 6 state-machine/code-trace | **Anchorless (§5.5) — render-only, NO instance** | RC | salvage render logic into family 6, author no instance |

**Aggregate:** family 1 absorbs 2 more (RecursionTree + NPGadget). Family 2 = ArrayStepper. Family 3 = DPWastedWork + PageTableWalk + MatrixTransform. Family 4 = 6 PS primitives. Family 5 = 5 primitives (3 RC + 2 SO; 1 RC anchorless). Family 6 = 3 (ProcessFSM + CppVTable + Tls0Rtt-anchorless). **2 anchorless (TcpCwnd, Tls0Rtt) yield render logic only — NO instance authored.**

### PM rulings applied (BINDING — do not re-open)

| Ruling | Where applied |
|---|---|
| ONE Plan V (not split); 5 sequential family tasks (file intersection blocks parallel lanes) | Whole plan; Tasks 1–5 sequential, no Lane B |
| Migrate corpus-anchored primitives to real content instances; anchorless TcpCwnd + Tls0Rtt = render-logic salvage only (NO instance) | Tasks 5 (TcpCwnd), 3 (Tls0Rtt → Task 3 of the list is family-6 = Task 3 numbered task) — checklist H skips anchorless |
| Family 5 ships **SO-driven** (Gantt/concurrency/fork); accept **few/zero RC instances** at first ship | Task 5: ≥1 SO instance REQUIRED (SchedulerGantt or RaceMutex); RC instances best-effort, zero acceptable |
| **NPGadget → graph/tree (family 1)** instance (bipartite), NOT chart/distribution | Task 3 (numbered) — family 6 task authors the NPGadget graph/tree instance into family 1; see note |
| **DPWastedWork → matrix/grid primary, call-tree a layout variant** | Task 2 (numbered) — family 3 |

**Note on NPGadget placement:** NPGadget is a family-1 (graph/tree) instance per ruling, but graph/tree is already built — authoring its instance is just adding a `content/PA/viz/*.yaml` (graph/tree, bipartite layout) + a gallery card. The numbered family-6 task (Task 3 below) OWNS the NPGadget migration (extract the bipartite render idea, prove graph/tree renders it; if graph/tree's d3-hierarchy layout cannot express a bipartite graph without a layout extension, that is a graph/tree (family-1) layout-variant addition recorded as a Decision Log note in Task 3 — NOT a new family). Author the NPGadget instance in Task 3.

---

## Numbered task list (order by corpus strength)

| # | Task | Family | family_id |
|---|---|---|---|
| 1 | Family 2 sequence/array — migrate ArrayStepper; RecursionTree→family 1 + exit legacy `vizRegistry`; PA instance | 2 | `sequence-array` |
| 2 | Family 3 matrix/grid — migrate DPWastedWork (primary) + PageTableWalk + MatrixTransform; ALO/PA/SO instances | 3 | `matrix-grid` |
| 3 | Family 6 state-machine/code-trace — migrate ProcessFSM + CppVTable + Tls0Rtt(render-only) + NPGadget(→fam 1 instance); POO code-trace instance | 6 | `state-machine` |
| 4 | Family 4 chart/distribution — migrate BayesTree + NumLineDirect + SumPlotTracker + SlopeCounter + SigmaStackedBar + CompareFrames; PS instances | 4 | `chart-distribution` |
| 5 | Family 5 timeline/protocol — migrate TcpHandshake + OsiEncap + SchedulerGantt + RaceMutex + TcpCwnd(render-only); SO-driven instances | 5 | `timeline-protocol` |
| 6 | Impeccable → blocking (re-calibrate + flip the CI `\|\| true`) + whole-build review council + eyeball all 6 families | — | — |

---

## The A–H per-family checklist (recon §4 — every family task 1–5 satisfies ALL of it)

Each family task is **self-contained**: an executor with ONLY that task section + the repo can finish it by copying the graph/tree shape. The 8 sub-steps, with exact files:

- **A. `families/{Family}Family.tsx`** — exported `{X}Data` / `{X}Step` / `{X}State` types + a **zod-free fail-loud** `parse{X}Data(dataJson, instanceId): {X}Data` (`JSON.parse` then structurally validate EVERY field; throw a plain `Error` naming **both `instanceId` AND the offending field/index** — INV-5.5; cross-reference integrity is part of parsing, e.g. every highlight/transition id must exist in the declared element set); `framesFrom{X}Data(data): Frame<{X}State>[]` (PURE; `Frame<S> = {state, aria, meta?}` per `AlgoStepperShell.tsx:17`; `{X}State` exposes BOTH the values the trace-harness compares AND the ids the renderer/invariants need for DOM lookup; deltas mutate state cumulatively; `aria` carries the per-step callout); `layout{X}` using **`measureLabelWidth` for no-clip BY CONSTRUCTION** (copy the helper + the callout layout from `GraphTreeFamily.tsx` §0 #7 — reserve element-box space, wrap callouts to ≥2 lines, shrink to `CALLOUT_MIN_FONT=8`, clamp center-x by half the widest line); `renderFrame` with **element-anchored callouts** (anchored to the rendered element, NEVER a detached footer, NEVER a hardcoded color name — the §5.3 / QR-WARM-GALBEN rule); the `{X}Family` component mounting `AlgoStepperShell` with a **`useCallback`-stable `onStep`** (the §0 #8 infinite-loop landmine — copy the comment). **SVG-only, 480×360.** Stamp `data-{element}-id` on each rendered element group (the test hook the invariants read back — mirror graph/tree's `data-node-id`).
- **B. `families/__tests__/{x}Trace.ts`** — an INDEPENDENT reference oracle that runs the REAL algorithm/semantics from the instance's seed and GENERALIZES (reads the same seed the instance encodes, like `mergesortTrace.ts` derives the input array from the root label — NOT one pinned input). Returns per-step reference state for the trace-match.
- **C. `families/{x}Invariants.ts`** — `export const {X}_INVARIANTS: {X}Invariant[]`, each `(container, frame, stepIdx, allFrames) => {ok} | {ok:false,message}` reading domain truth **back from rendered SVG geometry** (parse `transform="translate(cx,cy)"` off `data-{element}-id`, or x/y/width attrs) — NOT from the data model. This is the §5.4 semantic-correctness gate. The SPECIFIC invariants per family are listed in each task (recon §3).
- **D. `familyRegistry.ts`** — add `"{family_id}": {X}Family` (the production renderer).
- **E. `HARNESS_REGISTRY`** in `traceMatchHarness.test.tsx:39` — add `"{family_id}": { referenceExecutor, invariants: {X}_INVARIANTS }`.
- **F. wrong-trace fixture** under `families/__tests__/fixtures/{family_id}-wrong-trace.yaml` (NEVER under `content/` — `ContentRepo` globs `content/{subject}/viz/` and would admit it) + extend `seededWrongTrace.test.tsx` to assert `assertHarnessForInstance` THROWS naming the bad step (proves gate-3 is alive for this family's oracle).
- **G. a REQUIRED reachable mount** painting `data-testid="{family_id}-root"` — add a `<section data-testid="viz-demo-{family}">` card to `VizDemoPage.tsx` mounting `{X}Family` on a REAL authored instance's `data_json` (the same way the graph-tree card mounts at `VizDemoPage.tsx:72-78`), then extend `tutor-web/e2e/family-no-clip.spec.ts` with a `{family_id}-root` block: `assertNoClip(page, "{family_id}-root")` at the 2 existing viewport heights BEFORE and AFTER stepping `{family_id}-step-fwd`→`-step-back` (round-trip the frame counter — the DIJ-ONESHOT one-shot kill), scoped error-text regex check + zero 4xx/5xx (copy the graph-tree block verbatim, swap the prefix).
- **H. ≥1 `content/{SUBJECT}/viz/{id}.yaml` instance** from the REAL corpus where an anchor exists (SKIP anchorless — TcpCwnd, Tls0Rtt get NO instance). Shape: copy `viz-pa-mergesort-001.yaml` (`id`/`subject`/`family_id`/`language: ro`/`instance.method_kc_id`/`instance.data_json` as a JSON **string**). Learning/teaching text in callouts = **ROMANIAN**; ids/keys/code = English.

**Migration mechanics (every family task):** for each migration-target primitive (table above): (1) READ it, extract its render idea (the SVG-drawing logic, the step model) into `{X}Family.tsx`'s `framesFrom{X}Data` + `renderFrame`; (2) DELETE the hardcoded scenario from the family (the family is data-driven — the scenario becomes an instance); (3) re-express the primitive's baked content as `data_json` in an authored instance (checklist H) where an anchor exists. The PRIMITIVE FILE itself stays on disk this cycle (it may still be mounted in `VizDemoPage`'s legacy cards; do NOT delete primitive `.tsx` files — that is V6 retirement, out of scope) UNLESS it is `RecursionTree` (Task 1 removes its `vizRegistry` entry — see Task 1).

**Two-stage review (every task):** (1) **Implementer subagent** executes the steps exactly, TDD where marked, commits to `main` with explicit paths only (never `git add -A` — §0 #12). (2) **Independent reviewer subagent** (fresh context, given ONLY this task section + the diff): re-runs every acceptance command ITSELF (never trusts the implementer's claimed green — the [[feedback_review_workflow]] rule), checks the diff touches ONLY this task's files, checks the lock-check line (no frozen-surface change), and adversarially compares the rendered figure to the family contract. Findings → implementer fixes → reviewer re-verifies. **Acceptance per family task = the 3 gates green + the full vitest/e2e suites green + an eyeballed full-size screenshot of the mounted family on `/tutor/viz-demo`** (render-before-claim, [[feedback_render_before_claim_done]]; the viz-no-clip gate is machine-checked AND eyeballed — [[feedback_viz_no_clip_gate]]).

---

## Task 1 — Family 2 sequence/array (`sequence-array`)

**Goal:** build the sequence/array family from the `ArrayStepper` seed; migrate `RecursionTree` into family 1 (graph/tree) as an instance and EXIT the legacy `vizRegistry`; author ≥1 PA instance. Corpus strength: STRONG (merge-sort is the spec's canonical reference, §624 "instruction steppers", ArrayStepper + RecursionTree seeds).

**Files — CREATE:** `tutor-web/src/components/viz/families/SequenceArrayFamily.tsx`, `families/__tests__/sequenceArrayTrace.ts`, `families/sequenceArrayInvariants.ts`, `families/__tests__/fixtures/sequence-array-wrong-trace.yaml`, `content/PA/viz/viz-pa-arraysort-001.yaml`, `content/PA/viz/viz-pa-recursion-001.yaml` (the RecursionTree→graph/tree instance — `family_id: graph-tree`).
**Files — MODIFY:** `families/familyRegistry.ts` (add `sequence-array`), `families/__tests__/traceMatchHarness.test.tsx` (add `sequence-array` to `HARNESS_REGISTRY`), `families/__tests__/seededWrongTrace.test.tsx` (add the family-2 wrong-trace assert), `tutor-web/src/components/viz/VizDemoPage.tsx` (add the `viz-demo-sequence-array` card), `tutor-web/e2e/family-no-clip.spec.ts` (add the `sequence-array-root` block), `tutor-web/src/components/viz/vizRegistry.ts` (**remove the `recursion-tree` entry — exit the legacy channel**), `content/viz-ids.yaml` (remove `recursion-tree` if present — keep the legacy lockstep test green; verify `vizRegistry.test.ts` passes after).

**Lock check:** `sequence-array` is not in `interface-signatures-lock.md` (verified zero hits). Adding a family is additive. STOP, PM if any wire/route change is needed.

**Family-2 SPECIFIC semantic invariants (px-read, recon §3.2 — implement ALL in `sequenceArrayInvariants.ts`):**
- `index-monotone-x` — rendered x of cell i+1 > rendered x of cell i (cells left-to-right).
- `cardinality-preserved` — rendered cell count == reference array length at EVERY frame.
- `pointer-in-bounds` — every rendered pointer/cursor marker (i, j, lo, hi, pivot) sits over a rendered cell (its x within `[minCellX, maxCellX]`).
- `final-state` trace-match — final frame's cell labels == reference output (e.g. sorted), read from the DOM.
- `swap-conservation` — a swap permutes the multiset of cell values (never edits it): the sorted multiset of rendered labels is invariant across frames.

**Migration targets:** `ArrayStepper.tsx` (bar/compare/swap stepper render logic → family 2). `RecursionTree.tsx` → graph/tree (family 1) INSTANCE (`viz-pa-recursion-001.yaml`, `family_id: graph-tree`, the recursion tree as nodes/steps) + remove its `vizRegistry` entry. **NPGadget is NOT here** (it is family 1, Task 3 owns it per the placement note).

**Instances to author (checklist H):**
- `content/PA/viz/viz-pa-arraysort-001.yaml` — `family_id: sequence-array`, a PA sorting trace (e.g. insertion/selection sort on a small array) re-expressed from ArrayStepper's scenario as `data_json`; callouts in RO. Anchor: PA sorting (the spec's canonical reference subject).
- `content/PA/viz/viz-pa-recursion-001.yaml` — `family_id: graph-tree`, the RecursionTree scenario as a graph/tree instance. Anchor: PA recursion (RecursionTree's own subject). The trace-match here reuses the EXISTING graph-tree oracle (`mergesortTrace.ts` generalizes by root-label) — confirm the recursion instance's root label is a space-separated integer list OR extend the graph-tree oracle minimally; if the recursion content is NOT a mergesort-shaped tree (different algorithm), the graph-tree oracle will mismatch → record as a BLOCK (the graph-tree oracle is mergesort-specific; a generic recursion tree may need its own family-1 oracle generalization, a Decision Log note) → escalate to PM rather than forcing a green.

**Steps:**
- [ ] Step 1 (TDD): copy `GraphTreeFamily.tsx` → `SequenceArrayFamily.tsx`; rewrite types/parse/frames/layout/render for cells+pointers; copy `measureLabelWidth` + the callout layout (§0 #7) + the `useCallback` onStep landmine comment (§0 #8). Write `sequenceArrayTrace.ts` (independent oracle reading the instance seed). Write `sequenceArrayInvariants.ts` (the 5 invariants above). Register in BOTH registries (D, E). Run `npm --prefix tutor-web test` → expect the harness to RED until the family + instance are consistent (TDD).
- [ ] Step 2: author `viz-pa-arraysort-001.yaml` (H); iterate family until trace-match + all 5 invariants green over it.
- [ ] Step 3: author `viz-pa-recursion-001.yaml` (graph/tree instance) + handle the oracle-shape question above (green or BLOCK→PM).
- [ ] Step 4: wrong-trace fixture (F) + `seededWrongTrace.test.tsx` assert-RED for family 2.
- [ ] Step 5: gallery card (G) in `VizDemoPage.tsx` mounting `SequenceArrayFamily` on `viz-pa-arraysort-001`'s `data_json`; extend `family-no-clip.spec.ts` with the `sequence-array-root` block (2 viewports, step round-trip).
- [ ] Step 6: exit legacy `vizRegistry` — remove `recursion-tree` from `vizRegistry.ts` + `content/viz-ids.yaml`; run `vizRegistry.test.ts` (the lockstep test) green.
- [ ] Step 7: FULL `npm --prefix tutor-web test` + `npm --prefix tutor-web run e2e` green (foreground, streamed to a log, never `| tail`/`Select-Object -Last N`, never 2 vitest at once — §executor-rules). Commit (explicit paths).
- [ ] Step 8: **eyeball** — open `/tutor/viz-demo` headed, screenshot the sequence-array card to `build-review/tmp/planV-sequence-array.png`; confirm cells, pointers, and BOTH-line callouts fully visible, no clip.

**Acceptance:** harness green over the new instances; all 5 family-2 invariants pass on the mounted DOM; wrong-trace fixture REDs naming the step; `sequence-array-root` no-clip green at 2 viewports + step round-trip; `vizRegistry` no longer carries `recursion-tree` (lockstep test green); full vitest + e2e green; eyeballed screenshot confirms no clip. Reviewer re-runs all gates + the wrong-trace red-path itself.

---

## Task 2 — Family 3 matrix/grid (`matrix-grid`)

**Goal:** build the matrix/grid family; migrate `DPWastedWork` (matrix/grid PRIMARY, call-tree a LAYOUT VARIANT — PM ruling), `PageTableWalk`, `MatrixTransform` (already on Shell, §0 #9 — render-extract not Shell-port); author ALO + PA + SO instances. Corpus strength: STRONG (§624 "DP tables" + ALO's whole matrix step-trace surface + 3 seeds).

**Files — CREATE:** `families/MatrixGridFamily.tsx`, `families/__tests__/matrixGridTrace.ts`, `families/matrixGridInvariants.ts`, `families/__tests__/fixtures/matrix-grid-wrong-trace.yaml`, `content/ALO/viz/viz-alo-gauss-001.yaml`, `content/PA/viz/viz-pa-dp-001.yaml`, `content/SO/viz/viz-so-pagetable-001.yaml`.
**Files — MODIFY:** `familyRegistry.ts`, `traceMatchHarness.test.tsx` (HARNESS_REGISTRY), `seededWrongTrace.test.tsx`, `VizDemoPage.tsx` (card), `family-no-clip.spec.ts` (`matrix-grid-root` block).

**Lock check:** `matrix-grid` not in the lock (verified). Additive.

**Family-3 SPECIFIC invariants (px-read, recon §3.3):**
- `grid-rectangularity` — equal cell count per row, aligned col x per row (every row's cell-x set matches the header col-x set).
- `row-monotone-y` / `col-monotone-x` — row r+1 below row r; col c+1 right of col c.
- `dp-fill-order` — the filled-cell set at step k ⊇ the set at k−1 (cells never un-fill).
- `per-cell trace-match` — each filled cell's rendered value == the reference table value (the strongest gate).
- `pivot-existence` — when the instance declares a pivot/diagonal cell, a rendered cell exists at that position.

**Migration targets:** `DPWastedWork.tsx` (DP table → matrix/grid; the call-tree view = a layout variant flag in `data_json`, e.g. `layout: "grid" | "call-tree"`, rendered by the same family — a Decision Log note that family 3 has a call-tree sub-layout, NOT a new family). `PageTableWalk.tsx` (`{virtualAddress, pageTableEntries, tlbState, physFrames}` → matrix/grid). `MatrixTransform.tsx` (eigenvector/transform matrix states → matrix/grid; extract its existing Shell-mounted render logic — it is ALREADY on the Shell, so this is the cleanest extraction).

**Instances (H):**
- `content/ALO/viz/viz-alo-gauss-001.yaml` (`matrix-grid`) — a Gaussian-elimination / LU step-trace (ALO is the dominant matrix-step-trace need, §624). Anchor: ALO Tema 3/4 matrix methods. RO callouts.
- `content/PA/viz/viz-pa-dp-001.yaml` (`matrix-grid`) — a DP-table fill (from DPWastedWork's scenario), grid layout. Anchor: PA DP (§624 "DP tables").
- `content/SO/viz/viz-so-pagetable-001.yaml` (`matrix-grid`) — a page-table walk (from PageTableWalk). Anchor: SO-4.

**Steps:**
- [ ] Step 1 (TDD): copy the graph/tree shape → `MatrixGridFamily.tsx` (cells/headers/fill-state; `measureLabelWidth` + callout layout + `useCallback` landmine copied); `matrixGridTrace.ts` (independent oracle — e.g. recompute the Gaussian/LU intermediate matrices, or the DP table, from the instance seed); `matrixGridInvariants.ts` (the 5 above). Register D + E. Run → RED (TDD).
- [ ] Step 2: author the 3 instances; iterate until trace-match + all invariants green over each. Confirm the call-tree layout variant renders DPWastedWork's tree form (Decision Log note).
- [ ] Step 3: wrong-trace fixture (F) + assert-RED.
- [ ] Step 4: gallery card (G) on `viz-alo-gauss-001`; `family-no-clip.spec.ts` `matrix-grid-root` block.
- [ ] Step 5: FULL vitest + e2e green (foreground, streamed). Commit.
- [ ] Step 6: eyeball `/tutor/viz-demo` matrix card → `build-review/tmp/planV-matrix-grid.png`; confirm grid alignment + values + callouts no-clip.

**Acceptance:** harness green over 3 instances; all 5 family-3 invariants pass; per-cell trace-match holds; wrong-trace REDs; `matrix-grid-root` no-clip + step round-trip green at 2 viewports; full suites green; eyeballed. Reviewer (Opus-class — matrix semantics are subtle) re-runs all gates + the per-cell trace-match red-path.

---

## Task 3 — Family 6 state-machine/code-trace (`state-machine`)

**Goal:** build the state-machine/code-trace family (the SINGLE biggest exam-prep payoff — POO "Ce se întâmplă la execuția acestui program?" → step-by-step execution trace, §624 "instruction steppers"); migrate `ProcessFSM`, `CppVTable`, `Tls0RttReplay` (render-only, NO instance — anchorless §5.5), and author the `NPGadget` graph/tree (family 1) instance (PM ruling — bipartite). Author a POO code-trace instance. Corpus strength: STRONG.

**Files — CREATE:** `families/StateMachineFamily.tsx`, `families/__tests__/stateMachineTrace.ts`, `families/stateMachineInvariants.ts`, `families/__tests__/fixtures/state-machine-wrong-trace.yaml`, `content/POO/viz/viz-poo-codetrace-001.yaml`, `content/SO/viz/viz-so-processfsm-001.yaml`, `content/PA/viz/viz-pa-npgadget-001.yaml` (**`family_id: graph-tree`** — family 1, bipartite, per ruling).
**Files — MODIFY:** `familyRegistry.ts`, `traceMatchHarness.test.tsx`, `seededWrongTrace.test.tsx`, `VizDemoPage.tsx`, `family-no-clip.spec.ts`.

**Lock check:** `state-machine` not in the lock (verified). Additive.

**Family-6 SPECIFIC invariants (px-read, recon §3.6):**
- `single-active-state` — EXACTLY one line/state highlighted per frame (parse the highlighted element, assert count == 1).
- `highlight-existence` — every active line/state id resolves to a rendered element (reuse graph/tree's pattern).
- `var-table trace-match` — the variable-state table values at step k == the reference interpreter's state; the accreting output buffer == the reference stdout prefix (the STRONGEST gate — catches "animates something the code doesn't do").
- `transition-edge-exists` — no teleport: every state transition at step k follows a declared edge between the from/to states (the edge element exists in the DOM).
- `monotone-program-counter` — for straight-line code, the highlighted line index is non-decreasing; on a jump, the target line exists.

**Migration targets:** `ProcessFSM.tsx` (process-lifecycle FSM → state-machine: `{states, transitions, trace}`), `CppVTable.tsx` (vtable dispatch trace → state-machine: `{classes, scenario}`), `Tls0RttReplay.tsx` (render logic salvage into state-machine — **NO instance**, anchorless §5.5). **NPGadget** → author its instance as **family 1 (graph/tree)** per ruling: extract the bipartite render idea; if d3-hierarchy cannot lay out a bipartite graph, record a graph/tree layout-variant Decision Log note (NOT a new family) and confirm the graph-tree harness accepts it OR escalate the oracle-shape question (graph-tree's oracle is mergesort-specific; NPGadget is not a mergesort tree → the trace-match may need a graph-tree oracle generalization → BLOCK→PM if it cannot pass honestly).

**Instances (H):**
- `content/POO/viz/viz-poo-codetrace-001.yaml` (`state-machine`) — a C++ MCQ-style program: two-pane (code listing + variable table + output buffer), one line highlighted per step. Anchor: POO course exam dominant item type. RO callouts; the CODE itself is C++ (English identifiers — code is meta/English).
- `content/SO/viz/viz-so-processfsm-001.yaml` (`state-machine`) — process lifecycle FSM (from ProcessFSM). Anchor: SO-1.
- `content/PA/viz/viz-pa-npgadget-001.yaml` (`graph-tree`) — NPGadget bipartite graph, family 1.

**Steps:**
- [ ] Step 1 (TDD): copy the shape → `StateMachineFamily.tsx` (two-pane code+var-table OR FSM node-edge; one active highlight; `measureLabelWidth` + callout + onStep landmine). `stateMachineTrace.ts` — an INDEPENDENT mini-interpreter that recomputes the var-table + output buffer per step from the instance's code/program seed (the oracle). `stateMachineInvariants.ts` (the 5 above). Register D + E. Run → RED.
- [ ] Step 2: author the POO code-trace + SO FSM instances; iterate until var-table trace-match + all invariants green.
- [ ] Step 3: NPGadget → family-1 instance + the oracle-shape resolution (green or BLOCK→PM, Decision Log note for the bipartite layout variant).
- [ ] Step 4: wrong-trace fixture (F) + assert-RED.
- [ ] Step 5: gallery card (G) on `viz-poo-codetrace-001`; `family-no-clip.spec.ts` `state-machine-root` block.
- [ ] Step 6: FULL vitest + e2e green. Commit.
- [ ] Step 7: eyeball `/tutor/viz-demo` code-trace card → `build-review/tmp/planV-state-machine.png`; confirm code pane + var table + output buffer + single highlight, no clip.

**Acceptance:** harness green; all 5 family-6 invariants pass (esp. var-table/output trace-match); wrong-trace REDs; `state-machine-root` no-clip + round-trip green; NPGadget family-1 instance resolved (green or escalated); Tls0Rtt render-only (no instance authored); full suites green; eyeballed. Reviewer (Opus-class — interpreter oracle correctness is load-bearing) re-runs the var-table trace-match red-path.

---

## Task 4 — Family 4 chart/distribution (`chart-distribution`)

**Goal:** build the chart/distribution family (PS-dominant: PDF/PMF/histograms, the §624 continuous-PDF shading, the V8 "Sigma all-black" defect re-expressed); migrate the 6 PS primitives (`BayesTree`, `NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`, `CompareFrames`); author PS instances. Corpus strength: STRONG for PS.

**Files — CREATE:** `families/ChartDistributionFamily.tsx`, `families/__tests__/chartDistributionTrace.ts`, `families/chartDistributionInvariants.ts`, `families/__tests__/fixtures/chart-distribution-wrong-trace.yaml`, `content/PS/viz/viz-ps-pdf-001.yaml`, `content/PS/viz/viz-ps-bayes-001.yaml`, `content/PS/viz/viz-ps-sigma-001.yaml` (the V8 defect re-expression).
**Files — MODIFY:** `familyRegistry.ts`, `traceMatchHarness.test.tsx`, `seededWrongTrace.test.tsx`, `VizDemoPage.tsx`, `family-no-clip.spec.ts`.

**Lock check:** `chart-distribution` not in the lock (verified). Additive.

**Family-4 SPECIFIC invariants (px-read, recon §3.4):**
- `axis-monotone` — tick x (and y) increases monotonically with data value (read tick element x/y).
- `shading-within-curve` — the shaded area-under-curve bbox lies between the curve and the baseline (P(a≤X≤b) shading does not spill outside the curve).
- `bar-height-proportionality` — rendered bar height ratios == data value ratios (this KILLS the V8 all-bars-same-height / all-black defect — the motivating bug).
- `point-on-curve trace-match` — each rendered data point sits on the reference-computed curve at its x.
- `total-mass / normalization` — PMF mass renders to a constant baseline; a CDF is non-decreasing (read cumulative bar tops left-to-right).

**Migration targets:** all 6 PS primitives. `SigmaStackedBar` specifically: re-express as a chart instance whose `bar-height-proportionality` invariant proves the bars are NOT all-same-height/all-black (the V8 §5.5 fix). `NumLineDirect`/`SumPlotTracker`/`SlopeCounter`/`CompareFrames`/`BayesTree` → chart/distribution render logic.

**Instances (H):**
- `content/PS/viz/viz-ps-pdf-001.yaml` (`chart-distribution`) — a continuous PDF with shaded P(a≤X≤b) area (the §624-named need, the `probabilistic` concept_type's anchor). Anchor: PS distributions. RO callouts.
- `content/PS/viz/viz-ps-bayes-001.yaml` (`chart-distribution`) — Bayes-tree probabilities re-expressed (`{priorP, sensitivity, fpr, ...}` → chart/tree). Anchor: PS-2.
- `content/PS/viz/viz-ps-sigma-001.yaml` (`chart-distribution`) — the stacked/grouped bars (SigmaStackedBar), proving bar-height proportionality. Anchor: PS, the V8 defect.

**Steps:**
- [ ] Step 1 (TDD): copy the shape → `ChartDistributionFamily.tsx` (axes/ticks/curve/shaded-area/bars; `measureLabelWidth` + callout + onStep landmine). `chartDistributionTrace.ts` — independent oracle recomputing the curve points / bar heights / cumulative mass from the instance seed. `chartDistributionInvariants.ts` (the 5 above). Register D + E. Run → RED.
- [ ] Step 2: author the 3 PS instances; iterate until trace-match + invariants green; CONFIRM `bar-height-proportionality` reds a deliberately-flat sigma fixture (the V8 regression guard, folded into the wrong-trace step).
- [ ] Step 3: wrong-trace fixture (F) + assert-RED (use a bar-height-violating fixture to pin the V8 class dead).
- [ ] Step 4: gallery card (G) on `viz-ps-pdf-001`; `family-no-clip.spec.ts` `chart-distribution-root` block.
- [ ] Step 5: FULL vitest + e2e green. Commit.
- [ ] Step 6: eyeball `/tutor/viz-demo` chart card → `build-review/tmp/planV-chart-distribution.png`; confirm axes/curve/shading/bars visible, NOT all-black, no clip.

**Acceptance:** harness green; all 5 family-4 invariants pass; bar-height-proportionality reds the flat fixture (V8 dead); shading-within-curve holds; wrong-trace REDs; `chart-distribution-root` no-clip + round-trip green; full suites green; eyeballed (bars distinct, shading correct). Reviewer re-runs the bar-height red-path.

---

## Task 5 — Family 5 timeline/protocol (`timeline-protocol`)

**Goal:** build the timeline/protocol family (RC protocol exchanges + SO scheduling Gantt/concurrency/fork-timelines, §624); migrate `TcpHandshake`, `OsiEncap`, `SchedulerGantt`, `RaceMutex`, and `TcpCwnd` (render-only, NO instance — anchorless §5.5). **SHIPS SO-DRIVEN** (PM ruling) — ≥1 SO instance REQUIRED; RC instances best-effort, **few/zero acceptable at first ship** (RC corpus is the thinnest, ~3–6 lectures, no grading doc/past papers). Corpus strength: MODERATE-TO-STRONG (SO is the reliable driver).

**Files — CREATE:** `families/TimelineProtocolFamily.tsx`, `families/__tests__/timelineProtocolTrace.ts`, `families/timelineProtocolInvariants.ts`, `families/__tests__/fixtures/timeline-protocol-wrong-trace.yaml`, `content/SO/viz/viz-so-gantt-001.yaml` (REQUIRED), `content/SO/viz/viz-so-racemutex-001.yaml` (REQUIRED-or-best-effort), and IF an RC anchor exists: `content/RC/viz/viz-rc-handshake-001.yaml` / `content/RC/viz/viz-rc-osi-001.yaml` (best-effort — author only if the RC corpus supplies a real source; zero RC instances is acceptable).
**Files — MODIFY:** `familyRegistry.ts`, `traceMatchHarness.test.tsx`, `seededWrongTrace.test.tsx`, `VizDemoPage.tsx`, `family-no-clip.spec.ts`.

**Lock check:** `timeline-protocol` not in the lock (verified). Additive.

**Family-5 SPECIFIC invariants (px-read, recon §3.5):**
- `time-monotone-x` — within a lane, event t+1's x ≥ event t's x.
- `lane-y-stable` — each lane's y is constant across all frames (read the lane element's y; it must not drift).
- `arrow-endpoints-on-lanes` — every inter-lane message arrow's tail and head sit on declared lanes; send-before-receive: tail-x < head-x.
- `non-overlapping-Gantt` — within a lane, at most one Gantt block occupies any x-extent (no overlapping job blocks).
- `event-order trace-match` — the rendered left-to-right event order == the reference execution order.

**Migration targets:** `SchedulerGantt.tsx` (`{jobs, quantum?, algo}` → timeline; SO, the reliable driver), `RaceMutex.tsx` (clean-vs-breaking two-track contrast → timeline; SO concurrency), `TcpHandshake.tsx` (`{phases, actors}` → timeline; RC), `OsiEncap.tsx` (`{payloadBytes, mtu, layers}` → timeline; RC), `TcpCwnd.tsx` (full Reno render logic salvage → timeline, **NO instance** — anchorless §5.5). Callouts anchored by **element, never color name** (§5.3 QR-WARM-GALBEN).

**Instances (H):**
- `content/SO/viz/viz-so-gantt-001.yaml` (`timeline-protocol`) — a scheduling Gantt (round-robin or SJF) from SchedulerGantt's scenario. Anchor: SO-2 (REQUIRED — the SO driver).
- `content/SO/viz/viz-so-racemutex-001.yaml` (`timeline-protocol`) — a race-vs-mutex two-track contrast from RaceMutex. Anchor: SO-3 (the simplest re-expression).
- RC instances (handshake / OSI encap) — author ONLY if the RC corpus has a real source row; zero acceptable per ruling.

**Steps:**
- [ ] Step 1 (TDD): copy the shape → `TimelineProtocolFamily.tsx` (horizontal lanes on a shared time axis + Gantt blocks + inter-lane arrows; `measureLabelWidth` + callout + onStep landmine). `timelineProtocolTrace.ts` — independent oracle recomputing the schedule/event order from the instance seed (e.g. run the round-robin scheduler). `timelineProtocolInvariants.ts` (the 5 above). Register D + E. Run → RED.
- [ ] Step 2: author the SO instances (gantt REQUIRED; racemutex required-or-best-effort); iterate until trace-match + invariants green. Best-effort RC instances if an anchor exists; if no RC source → ship SO-only (record in the commit message: "RC instances deferred — no corpus source, per PM ruling").
- [ ] Step 3: wrong-trace fixture (F, SO-based) + assert-RED.
- [ ] Step 4: gallery card (G) on `viz-so-gantt-001`; `family-no-clip.spec.ts` `timeline-protocol-root` block.
- [ ] Step 5: FULL vitest + e2e green. Commit.
- [ ] Step 6: eyeball `/tutor/viz-demo` timeline card → `build-review/tmp/planV-timeline-protocol.png`; confirm lanes/blocks/arrows + callouts no-clip; arrows go send→receive left-to-right.

**Acceptance:** harness green over ≥1 SO instance (RC zero acceptable); all 5 family-5 invariants pass; non-overlapping-Gantt + send-before-receive hold; wrong-trace REDs; `timeline-protocol-root` no-clip + round-trip green; TcpCwnd render-only (no instance); full suites green; eyeballed. Reviewer re-runs the event-order red-path + confirms RC-deferral is recorded honestly, not silently dropped.

---

## Task 6 — Impeccable → blocking + whole-build review + eyeball all 6 families

**Goal:** re-calibrate the Impeccable design-quality gate over the now-six-family viz code and FLIP its CI `|| true` to blocking; run the mandatory whole-build review council (per-task reviews miss cross-task flaws — [[feedback_review_workflow]]); eyeball all six families end-to-end (render-before-claim).

**Files — MODIFY:** `tools/impeccable-rules.json` (re-calibrated `enabled`/`disabled`), `build-review/impeccable-calibration-2026-06-12.json` (or a new `build-review/impeccable-calibration-2026-06-13.json` if the corpus materially changes — keep the dated one immutable, add a new dated file and point the rules' `$comment` at it), `.github/workflows/test.yml` (**remove the `|| true`** on the Impeccable step, line 85). `tools/impeccable-filter.mjs` only if a finding-key/shape change is discovered (unlikely — verified `antipattern` is the key).
**Note on test.yml:** `.github/workflows/test.yml` is a PM-MERGE sensitive file in the 4b/6 lane discipline. In Plan V (single lane, on main) the executor MAY edit it directly, BUT the `|| true` flip is a STANDALONE one-line change committed separately and called out to the PM in the task report (so the PM sees the gate went blocking). If the repo still treats `.github/workflows/**` as patch-only at execution → deliver it as `build-review/tmp/planV-patches/impeccable-blocking.patch` + escalate to PM to apply. Verify the convention at execution; STOP, PM on ambiguity.

**Steps:**
- [ ] Step 1 (re-calibrate): run `npx --yes impeccable@2.3.2 detect --json tutor-web/src/components/viz/ | node tools/impeccable-filter.mjs` over the new family code. For EVERY surviving enabled finding: classify true-positive (FIX the viz code — it is a real design defect) vs false-positive (add to `disabled` WITH a reason, the existing format). Re-run until the filter exits 0 over the viz code. Document the calibration deltas in the commit message (every added `disabled` entry's reason). If a finding cannot be cleanly classified → STOP, PM (no silent allowlist creep — the [[feedback_fix_claim_discipline]] rule).
- [ ] Step 2 (flip to blocking): edit `.github/workflows/test.yml` line 85, remove `|| true`. The Impeccable step is now blocking. Verify locally that the full `npx impeccable | filter` pipeline exits 0 over the WHOLE `src/` scope the CI step uses (not just viz/) — if non-viz code has pre-existing enabled findings that were masked by `|| true`, that is a scope-discovery → record + STOP, PM (do NOT silently disable non-viz findings to make the flip green; the PM decides whether to widen scope or fix).
- [ ] Step 3 (whole-build review council): convene the mandatory whole-build adversarial council over all five new families + the migration (the `claude-council-lite` / whole-build-council per [[feedback_review_workflow]]) — feed it the six family files, the six invariant files, the new instances, and the diff. The council adversarially checks for cross-family flaws the per-task reviews missed: shared-helper drift (did each family re-copy `measureLabelWidth` correctly?), oracle-shape gaps (any family whose oracle silently passes a wrong trace?), the ghost-instance class (any `data_json` that parses but renders nothing?), invariant vacuity (any invariant that returns `{ok:true}` on an empty container and is therefore dead?). Findings → fix → re-review.
- [ ] Step 4 (eyeball all 6): open `/tutor/viz-demo` headed, screenshot EACH of the six family cards (graph-tree + the five new) to `build-review/tmp/planV-eyeball-{family}.png`; confirm each paints, callouts fully visible, NO clip/overlap (the cardinal-sin gate, eyeballed not just machine-checked — [[feedback_viz_no_clip_gate]]).
- [ ] Step 5 (whole-build gate, FULL suites, foreground + streamed, never `| tail`): `npm --prefix tutor-web test` (the trace-match harness now covers all six families over every authored instance; `seededWrongTrace` reds for every family); `npm --prefix tutor-web run e2e` (`family-no-clip.spec.ts` covers all six `{family_id}-root` mounts at 2 viewports); `npm --prefix tutor-web run e2e:visual` (3 baselines unchanged — the new families do not render in the 3 self-contained baselines; STOP if any baseline drifts — human-recommit-only). Confirm the Impeccable pipeline exits 0.
- [ ] Step 6 (plan-index): flip the row-V status to DONE in `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md` (or deliver `build-review/tmp/planV-patches/plan-index-V.patch` if the index is patch-only at execution); record the six family_ids now live in `familyRegistry`, the authored-instance count, the Impeccable-now-blocking note, and the W-comes-next reminder.
- [ ] Step 7: commit (explicit paths); report to PM: all 6 families live + gated, Impeccable blocking, council findings dispositioned, six screenshots, full suites green.

**Acceptance:** Impeccable pipeline exits 0 over the viz code (and the full CI `src/` scope) with the `|| true` removed; whole-build council findings all dispositioned (fixed or escalated); all six family cards eyeballed no-clip; full vitest + e2e + e2e:visual green (baselines byte-identical); plan-index flipped. Reviewer re-runs Step 5's suites in full + re-runs the Impeccable pipeline themselves before sign-off.

---

## Executor rules (standing hard rules — carried verbatim, binding on every task)

1. **PM-delegation** ([[feedback_pm_delegation]]): executors BUILD; the PM frames/reviews/decides. A scope question, a contradiction, a ruling gap = STOP and escalate verbatim — never improvise a product decision.
2. **VERIFY green yourself** ([[feedback_review_workflow]]): never trust a subagent's "green" — re-run the suite yourself. The reviewer re-runs every acceptance command independently in a fresh context; the implementer's claim is not evidence.
3. **Process hygiene** (SESSION-68 machine-hang lesson): run test suites in the FOREGROUND with a timeout, stream output to a log; **NEVER pipe a long run through `| tail` / `Select-Object -Last N`** (buffers till exit, blinds you and can hang the box); never poll a backgrounded test task; kill any hung node/gradle before a retry; **never run two vitest processes at once**.
4. **Executors NEVER edit this plan** (protocol rule 3): a plan/reality mismatch — an anchor that doesn't exist, a frozen signature that conflicts, a corpus source that's missing, an oracle that can't pass honestly — is a BLOCKED task escalated to the PM verbatim. The PM amends the plan; the executor never self-edits.
5. **No-clip gate** ([[feedback_viz_no_clip_gate]]): every family asserts no clip/overlap in Playwright at 2+ viewport heights (checklist G) AND is eyeballed at full size (checklist acceptance). The "talked about 1000 times" cardinal sin — machine-checked AND human-seen.
6. **Language split** ([[feedback_language_split]]): learning/teaching text (callouts, figure captions, instance content) = **ROMANIAN**; code/identifiers/keys/jargon/meta (family_id values, data_json keys, test names, commit messages, this plan) = **English**. Hard line.
7. **Frozen signatures UNTOUCHED**: `family_id`/`data_json` opaque end-to-end; adding a family is additive in `familyRegistry` + `HARNESS_REGISTRY` only. NO wire/route/Kotlin change, NO legacy `vizRegistry` wiring (except Task 1's REMOVAL of the `recursion-tree` entry). A needed wire change = STOP, PM.
8. **SVG-only, cool-skin only, frontend-only** (§5.6/§5.7): 480×360 SVG in `AlgoStepperShell`, Framer Motion, d3-hierarchy; no three/webgl/webgpu/canvas-figures; no warm-skin baselines (W after V). A new rendering dep = a Decision Log entry + STOP, PM.
9. **Commit explicit paths only** (§0 #12): never `git add -A`, never `git clean`, never branch-switch on main. Door/demo + build-review artifacts stay untracked.
10. **Model routing** ([[feedback_subagent_model_routing]]): implementers default Sonnet-class; reviewers for Tasks 2 (matrix per-cell) + 3 (interpreter oracle) Opus-class (oracle correctness is load-bearing); Task 6 council per its own skill.
