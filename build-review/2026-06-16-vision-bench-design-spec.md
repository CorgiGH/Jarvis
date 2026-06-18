# Build-Spec — Figure Vision-Judge Precision/Recall Bench (v1, MEASURE-only)

**Status:** ready to hand to a build workflow (just-in-time plan derivable from the task list in §10).
**Standing constraint (non-negotiable):** the vision leg stays **ADVISORY / SHADOW** regardless of any number this bench produces. The deterministic floor (frame-conjunction-gate + svg-overlap-gate) remains the only hard-blocking gate. This bench **measures** the vision leg's precision/recall; it does **not** graduate it to a blocker.
**Verified against the repo (2026-06-16, read-only):** all line/route/testid claims below were checked against `tutor-web/` source. Where a critique was wrong, it is corrected here with the grounded fact.

---

## 0. What the critiques forced to change (resolution ledger)

Every blocker/major from the three adversarial lenses is resolved below or explicitly deferred with reason. Grounded facts that overturned a critique are flagged **[CORRECTED]**.

| # | Finding (lens) | Severity | Resolution |
|---|---|---|---|
| R1 | `detGateBlind` is asserted in the taxonomy, not measured; gate is hardwired to SortMerge routes only (verified: `frame-conjunction-gate.mjs:43,57-72` imports `MERGE_STEPS`, navigates only `/tutor/lectie-mergesort` + `/merge-compare`). | blocker | **`detGateBlind` becomes an EMPIRICAL OUTPUT, never a catalog input.** §5 defines `det-probe.mjs`: run the actual two gates against each captured BAD frame's real route and record RED/GREEN. A catalog `expectedDetGateBlind` field exists ONLY as a documented expectation; the scorer asserts `catalog.expectedDetGateBlind === empirical.detGateBlind` and **fails the run loud on mismatch** (or quarantines + flags). See §5, §6. |
| R2 | 5 cross-family "frozen" defects labelled `det-gate-catchable` though the gate cannot load those routes. | blocker | Direct consequence of R1. For all four non-SortMerge families the gate **cannot run** today, so empirically every defect is `detGateBlind=TRUE` (gate GREEN-by-absence). The probe distinguishes **"gate ran → GREEN"** from **"gate could not run (unwired)"**; the latter is reported as `gateCoverage:"unwired"`, never silently folded into blindness. Only the two real SortMerge catchables (`divide-ignores-runs`, `scrubber-frozen`) stay `det-gate-catchable` because the gate genuinely runs there. |
| R3 | 3 (claimed) families have no mountable/steppable route; capture path is SortMerge-shaped. | blocker | **[CORRECTED]** All 5 families ARE steppable. Every family renders through `AlgoStepperShell` (`AlgoStepperShell.tsx:408-423`) which emits `<input type="range" data-testid="{prefix}-scrubber">`. On `/viz-demo` the tiles carry roots `seq-array-root`, `mg-root`, `mgg-root`, `graph-tree-root`, `chart-dist-root`, each with its own `{prefix}-scrubber`. seq-array + sort-merge also have dedicated lectie routes. §3 generalizes capture over `(route, rootTestid, scrubberTestid, scrubberIndex, predictGate)`. The capture path is real; only the harness's hardcoded `capture-one.mjs` (at `tools/__fixtures__/`) needs generalizing. |
| R4 | No GOOD-frame corpus; every catalogued item is BAD ⇒ precision degenerates to 1.0. | blocker | §7 mandates a **balanced GOOD corpus**: for every BAD frame, capture the CLEAN render of the SAME (route, frameIndex) as the paired GOOD, PLUS per-family **borderline GOODs** (correct-but-tempting states). Stated N and 1:1+ BAD:GOOD ratio in §7. No precision is reported without negatives. |
| R5 | Rubrics are 1:1 isomorphic to the defect list ⇒ judge handed the answer key. | blocker | §4 rubrics are **rewritten as defect-blind POSITIVE specifications** ("what a correct figure looks like"), authored from the family `renders` description ONLY. A mechanical independence check (§4.1) requires a reviewer holding the rubric but NOT the catalog to be unable to reconstruct the defect list. The per-defect checklist is retained only as an internal QA artifact, never shown to the judge. |
| R6 | MatrixGrid/GraphTree rubrics feed the judge a per-instance ground-truth oracle line (expected values/active cell) ⇒ a label-reader passes. | blocker | §4.2: the judge prompt carries **only the figure's declared INPUT** (the array / grid-kind / reveal beat — what the renderer itself consumes), **never** the correctness oracle (expected value-at-(r,c), expected active cell, expected highlight set). The scorer holds ground truth out-of-band and compares the judge's *reported pixel observation* to it. Checks that cannot be evaluated from pixels+input alone are dropped (§4.3). |
| R7 | Ground-truth labels wrong on real frames: `pivot:null` (correct = 0 highlights), `min===i` (no swap to read), plausible "wrong probability". | blocker | §8 **label-audit pass**: every (defect, frameIndex) pair is audited against the SHIPPED instance data (the exact `data_json` in `VizDemoPage.tsx` / the demo routes). Vacuous/ambiguous frames are excluded; conditional-correct frames (pivot:null ⇒ 0 highlights is GOOD; min===i ⇒ no destination accent) are encoded as such. "Wrong probability" defect is injected **grossly** wrong (e.g. 0.05 over ~total coverage) so a faithful judge MUST fail it, else dropped. A one-time human/second-judge adjudication confirms BAD-looks-wrong / GOOD-looks-right before scoring. |
| R8 | Two-matrix arithmetic mixes a subset numerator with a whole-corpus FP pool ⇒ Matrix-1 "precision" is mechanically depressed and meaningless. | major | §6: **Matrix-1 reports RECALL (coverage) as its headline**, with precision computed against the SAME evaluation population (GOOD frames scoped to the families in the subset) or omitted. A single corpus-wide precision is reserved for Matrix-2. The shared GOOD/FP rows are documented explicitly. |
| R9 | K unspecified; two contradictory reduction rules ("majority" vs asymmetric council bar); abstention dropped from denominator can flip labels. | major | §6.3: **K=3 fixed (odd).** ONE reduction rule: per-frame decision = **majority of parsed verdicts**. UNPARSEABLE counts **FAIL (conservative)** and stays IN the denominator (safety-biased: a malformed reply never silently passes a BAD frame). A **sensitivity row** reports how precision/recall move if abstentions are instead dropped — if the headline moves, the number is flagged fragile. The asymmetric "council bar" is reported as a SEPARATE advisory column, not the scorer's decision rule. |
| R10 | Prop-injection fidelity validated for only the 2 seed defects, extrapolated to ~30. | major | §3.3: prop-mode (`?defect=`) is validated by **md5 byte-equality vs the proven source-edit path for a REPRESENTATIVE sample per family** (≥1 paint-role, ≥1 geometry, ≥1 value defect per family), not just the 2 seeds. The report states the fidelity-validated fraction; any defect whose prop/source PNGs differ is captured via source-mode only. |
| R11 | Callout-crop is a SortMerge heuristic; chart annotation sits mid-plot (clamped `[PLOT_Y1,PLOT_Y0]`, verified `ChartDistributionFamily.tsx:180-181`) so the P-value answer leaks into pixels. | major | **[CONFIRMED real]** §3.4: a **per-family crop spec** + a hard **answer-leak assert** (OCR/text-scan the final bare PNG; fail capture if any answer-bearing token survives). For chart-dist the P-value annotation box is **explicitly cropped/masked** before the judge sees it; `CHECK_ANNOT_PLAUSIBLE` becomes "estimate area-fraction from the shaded pixels alone" with the number removed, or is dropped. MatrixGrid `rowOp` bottom rail likewise masked. |
| R12 | `value-dup-missing` (sort-merge) injection targets `indexOf` line (DOM stamp), not the painted glyph ⇒ no visible duplicate. | major | **[CONFIRMED real]** Verified `SortMergeFamily.tsx:932` paints `values[id]`; `:933` only computes the `data-cell-index` stamp. §2 re-points the injection to `values[id]` / `buildIdOfValue` so the displayed digit actually duplicates. |
| R13 | GraphTree grouping rubric may be unsatisfiable from one edgeless sparse frame ⇒ inflated false-negatives. | major | §4.4: GraphTree grouping/membership checks are evaluated only on frames where **≥2 sibling boxes of one parent are co-visible** (the "complete level" clause), or with an adjacent-frame pair. Frames where membership is underdetermined are excluded from those checks (kept for value/highlight checks). |
| R14 | MatrixGrid transpose variant crashes on 1×(n+1) dp-fill (`grid[w.col]` undefined when rows=1, verified `MatrixGridFamily` parse forces dp-fill rows=1). | minor | §2: drop the transpose variant for dp-fill; use the **off-by-one spill** (`grid[w.row][w.col+1]`) as primary, and restrict transpose to gauss-elim (rows≥2). Note in catalog that the spill also shifts `data-cell-value` (so it is detectable by attribute checks too — not pixel-only). |
| R15 | Chart `shade-fills-wrong-interval` first injection (`{a:xMin,b}`) is contrived. | minor | §2: lead with a realistic desync (shade reads `Math.round(a)/Math.round(b)` or an off-by-one sample bound while `mark-a`/`mark-b` use exact `data.interval`). |
| R16 | Missing borderline-GOOD variations ⇒ precision is an optimistic upper bound. | major | Folded into R4 (§7). Borderline GOODs enumerated per family. |
| R17 | N-per-family (6-11) too small for trustworthy per-family rates; no CI acknowledged. | blocker | §6.4: every reported rate carries a **Wilson 95% interval**; cells below a minimum-N threshold are reported as **"insufficient sample,"** not a point estimate. The build multiplies N by capturing each defect across **all its discriminating frame indices and both skins where applicable** (§7). The HEADLINE is the OVERALL and Matrix-2 numbers with CIs; per-family numbers are CI-qualified. |
| R18 | No human gold check on labels. | minor | Folded into R7 (§8 adjudication pass). |

**Deferred (with reason):**
- **D1 — Porting the deterministic gates to the other 4 families is OUT OF SCOPE for this bench.** The bench measures the vision leg against the floor *as it exists today*. Porting is a separate work item; until then those families are `gateCoverage:"unwired"` and every defect there is `detGateBlind=TRUE`. (Recorded so a future plan can flip them once the gate is wired.)
- **D2 — Worktree-isolated parallel source-mode capture (hazard fix layer 3)** is built only if prop-mode + serialized source-mode throughput proves insufficient; not needed for v1 corpus size. (Reason: prop-mode covers most of the catalog; source-mode residue is small and serial is fast enough.)

---

## 1. Scope

- **Families (all 5):** `SequenceArrayFamily` (seq-array, selection sort), `sort-merge` (SortMergeFamily, merge), `MatrixGridFamily` (matrix-grid: dp-fill + gauss-elim), `GraphTreeFamily` (graph-tree, divide tree), `ChartDistributionFamily` (chart-dist, density+area).
- **Defects:** BOTH `det-gate-blind` AND `det-gate-catchable` are in the corpus, reported as **TWO separate confusion matrices** per Alex's ruling (§6).
- **Routes/mounts (verified):**
  - seq-array: `/tutor/lectie-selectsort` (dedicated, predict-gated) + `/tutor/viz-demo` tile (`seq-array-root`, ungated).
  - sort-merge: `/tutor/lectie-mergesort` (boxes, predict-gated, scrubberIdx 0) + `/tutor/merge-compare` (bars, scrubberIdx 1, ungated).
  - matrix-grid: `/tutor/viz-demo` tiles `mg-root` (dp-fib) + `mgg-root` (gauss), ungated.
  - graph-tree: `/tutor/viz-demo` tile `graph-tree-root`, ungated.
  - chart-dist: `/tutor/viz-demo` tile `chart-dist-root`, ungated.
- **Deterministic floor as-built:** runs ONLY on sort-merge (the two routes above). For all other families `gateCoverage="unwired"`.
- **Output:** a dated markdown report + `results.json` under `build-review/`. No source/seed overwrite.

---

## 2. Final per-family defect list (with categories)

Category key: `blind` = `expectedDetGateBlind:true` (vision-only, today); `catchable` = `expectedDetGateBlind:false` (the floor genuinely catches it — **only valid where the gate is wired**). The **empirical** flag from `det-probe.mjs` (§5) is authoritative; `expected*` is the documented prior that the scorer asserts against.

### 2.1 SequenceArrayFamily (selection sort) — 8 defects
| id | category (expected) | injection layer (verified line) | corpus note |
|---|---|---|---|
| missing-sorted-prefix-paint | blind | `fillForCell` drop `if(index<sortedCount)return INK` (:448) / dark `:587` / bars `:921` | discriminating only where `sortedCount>0` |
| swapped-min-paint-on-swap-source | blind | white accent branch `:449` / dark swap-source guard `:596` | swap frames with `min≠i` only |
| bars-height-value-inversion | blind | `heightOf` invert (:412-413) or wrong arg `:1001` | BARS skin only |
| duplicated-value-token | blind | `valueOfId` wrong id (dark `:615` / bars `:943`) or `computeIdentity` `:204` | distinct-array frames |
| sorted-rule-overshoot | blind | `ruleX2` → `cxOf(sortedCount)`/`cxOf(n-1)` (dark `:627` / bars `:950`) | `sortedCount≥1` |
| chevron-on-wrong-column | blind | marker tick `cxFn(index+1)` (dark `:870` / bars `:1172`); keep `data-pointer-index` correct | scan/swap frames |
| callout-anchored-to-wrong-column | blind | `anchorIndex` wrong (white `:460`/dark `:617`/bars `:945`) | **note:** standard crop removes callout band → this is a CROP-EXEMPT check; capture an UNCROPPED variant for this defect only, or drop (see §4.3) |
| frozen-figure-over-live-state | **catchable IF wired** → empirically **blind today** (seq-array unwired) | pin render to `frames[0].state` (`:1234`) | the floor would own it once ported (D1) |

### 2.2 sort-merge (merge) — 10 defects
| id | category (expected) | injection (verified/corrected) |
|---|---|---|
| divide-ignores-runs | **catchable (REAL — gate wired)** | divide branch forces `divideRuns` empty (`460-472`) |
| scrubber-frozen | **catchable (REAL — gate wired)** | `renderFrame` ignores `frame.state`, always frame 0 (`:619`) |
| wrong-grouping | blind | corrupt `divideGroupOf` indices (`463-467`) |
| runs-collapsed | blind | `RUN_GAP 0` (`:263`) / `BARS_RUN_GAP 0` (`:313`) |
| output-not-accent | blind | `paintFor` output `accent→leftTint` (`:594`) |
| head-not-marked | blind | `paintFor` heads→body, `HEAD_LIFT 0`, null chevron (`:597,:1011`) |
| value-dup-missing | blind | **[CORRECTED]** target painted glyph `values[id]` (`:932`) or `buildIdOfValue` (`418-422`), **NOT** `indexOf` `:933` (DOM stamp only) |
| output-reversed | blind | reverse output slots (`503-507`) or flip `ruleX` (`740-741`) |
| chip-occludes-label | **catchable (svg-overlap-gate, wired)** | label y not below `calloutBottom` (`:766`) + long callout |
| final-not-sorted | blind | final branch role `row` not `rowSorted` (`469-472`) |

*(`chip-occludes-label` is the second known seed catchable — owned by `svg-overlap-gate`, which IS wired to sort-merge. Confirm empirically in §5.)*

### 2.3 MatrixGridFamily — 6 defects (all empirically `blind` today; gate unwired → D1)
| id | category (expected) | injection (verified, with R14 fix) |
|---|---|---|
| pivot-highlight-wrong-cell | blind | isPivot test `st.pivot.col+1` (`:463`) or frame emit `col+1` (`:253`) |
| filled-wash-missing-or-spurious | blind | `isFilled=false` / invert (`:462`) or skip `filled.add` (`:247`) |
| swapped-or-duplicated-cell-values | blind | **[R14 fix]** primary = off-by-one spill `grid[w.row][w.col+1]` (`:246`); transpose variant gauss-only (rows≥2) |
| missing-row-or-column-headers | blind | short-circuit header guards (`424-456`) |
| mis-colored-pivot-blends-into-grid | blind | neutralize pivot branch in `cellFill`/`strokeWidth` (`399-411,478-480`); keep `data-pivot` stamp |
| frozen-grid-region-no-motion | **catchable IF wired** → **blind today** | pin to `frames[0].state` snapshot (`:251`) |

### 2.4 GraphTreeFamily — 9 defects (all empirically `blind` today; gate unwired → D1)
| id | category (expected) | injection (verified) | corpus note |
|---|---|---|---|
| swapped-or-stale-label-on-node | blind | skip/mis-apply delta `.set` (`:92`); render reads `:254` | |
| duplicated-value-across-nodes | blind | collide two `labelOf` (`:92`) or author data | |
| wrong-grouping-bracket-membership | blind | corrupt `parent` pointers (`:27`); layout `:197` | **[R13]** evaluate only on ≥2-co-visible-sibling frames |
| mis-colored-highlight-frontier | blind | corrupt/invert `hi.has(n.id)` (`:99-100,:231`) | |
| missing-frontier-node-not-painted | blind | drop id from `step.highlight` (`:93,:228`) | |
| callout-anchored-to-wrong-or-detached-node | blind | reorder `highlightIds[0]` (`:100,:224`) | crop-exempt (callout band) → §4.3 |
| overlapping-or-colliding-node-boxes | **catchable IF svg-overlap wired** → **blind today** (GraphTree uses `data-node-id`, not `data-cell-index`; svg-overlap-gate keys on `data-cell-index` → does NOT cover it) | shrink `nodeSize`/`LEVEL_GAP` (`:200,:134`) | |
| clipped-node-box-or-label-at-viewport-edge | blind | `margin=0` (`:207-208`) | |
| frozen-figure-over-live-state | **catchable IF wired** → **blind today** | always render `frames[0]` activeNodes | |

### 2.5 ChartDistributionFamily — 11 defects (all empirically `blind` today; gate unwired → D1)
| id | category (expected) | injection (verified, with R15 fix) | corpus note |
|---|---|---|---|
| shade-fills-wrong-interval | blind | **[R15]** shade reads `Math.round(a)/round(b)` / off-by-one bound while `mark-a/b` use exact (`316,330-337`) | |
| interval-marks-missing-on-reveal2 | **catchable IF wired** → **blind today** | `showInterval=false` (`:381`) | reveal 1→2 pair |
| interval-drops-both-at-same-x | blind | `bPx=aPx` (`:365`) | reveal≥2 |
| probability-number-wrong-or-stale | blind | **[R7]** inject GROSSLY wrong value (`:386`) so faithful judge must fail; P-box **masked** from judge (§3.4) | reveal=4 |
| shade-detached-floats-above-baseline | blind | `PLOT_Y0→PLOT_Y0-40` in shade anchors (`335-337`) | reveal≥3 |
| curve-points-shuffled-or-duplicated | blind | reverse/concat `data.points` at render (`310-312`) | any reveal |
| ab-badges-swapped | blind | swap `x={aPx}`/`x={bPx}` glyph bindings (`472-473`) | reveal≥2 |
| shade-mis-colored-blends-into-curve | blind | `shadeFill=axisInk` / `fillOpacity 0.02` (`373,459`) | reveal≥3 |
| y-axis-or-zero-tick-missing | blind | `buildYTicks→[]` or drop `axis-y` line (`281-287,400-408`) | any reveal |
| frozen-figure-noop-renderframe | **catchable IF wired** → **blind today** | clamp `reveal=1` (`:380`) | per the seed crop |
| **(borderline-GOOD)** zero-width-looking interval (a≈b but distinct) | GOOD-borderline | clean render, edge state | tests false-positive |

---

## 3. Harness component breakdown + concurrency resolution

### 3.0 Components
1. **`defect-catalog.mjs`** — static data file (NOT mutating code), one entry per defect/GOOD frame: `{ id, family, label:'BAD'|'GOOD'|'GOOD-borderline', skin, route, rootTestid, scrubberTestid, scrubberIndex, frameIndices:[...], predictGate:bool, expectedDetGateBlind:bool, gateCoverage:'wired'|'unwired', injection:{mode:'prop'|'source', ...}, rubricKey, cropProfile }`. `expectedDetGateBlind` is a documented prior only; the empirical value wins (§5/§6).
2. **`defect-injector.mjs`** — prop-first (default), source-edit fallback. Prop = additive dev-only `?defect=<id>` query, `import.meta.env.DEV`-gated, applied as a render-time transform; source mode = write patch to working-tree copy → HMR settle → capture → `git checkout -- <single file>` → assert porcelain clean **for that path only** (never `git checkout .` / `git clean` — the tree has ~180 untracked door/demo files; R-MUTABLE-TREE landmine). Returns `{framePngPaths, label, restored:true}`.
3. **`frame-capturer.mjs`** — **generalized** `captureBareFigure({route, rootTestid, scrubberTestid, scrubberIndex, frameIndex, predictGate, cropProfile, query})`. Lifts the proven pipeline from `tools/__fixtures__/capture-one.mjs` (viewport 1536×730, DPR 1, reducedMotion 'reduce', SETTLE_MS 650, screenshot `svg.algo-stepper-shell-svg` clipped to bbox) but **parameterized** over family root/scrubber instead of the hardcoded merge-compare constants. Predict-gate unlock (`[data-testid^="predict-"]` + `lectie-next`) applied only when `predictGate:true`. Writes labeled PNGs + a `frames.json` side manifest mapping path → `{label, family, defectId, expectedDetGateBlind, route, frameIndex}`. Label lives in the manifest, **never burned into pixels**.
4. **`crop-profiles.mjs`** — per-family crop spec + answer-leak assert (§3.4).
5. **`det-probe.mjs`** — runs the two real gates against captured frames to produce the EMPIRICAL `detGateBlind` (§5).
6. **`vision-judge.mjs`** — `claude --print --output-format text`, ANTHROPIC_API_KEY unset (free Claude Max relay), K=3 fresh subprocesses per frame, image delivered by appending the absolute path into the prompt (`[Image attached for analysis — open and view this file: <abspath>]` — the CLI has no `--image` flag, grounded in `ClaudeMaxLlm.kt`). Prompt = the defect-blind per-family rubric (text, from `rubrics/<family>.txt`) + the figure's declared INPUT only (NO oracle) + output contract. Returns `{perRun, majority, kPass, kFail, parseErrors}`.
7. **`verdict-parser.mjs`** — pure function (unit-testable like `frame-conjunction-core.mjs`): stdout → `{verdict:'PASS'|'FAIL'|'UNPARSEABLE', checks, raw}`. Enforces "any CHECK FAIL ⇒ VERDICT FAIL". UNPARSEABLE distinct from FAIL.
8. **`scorer.mjs`** — joins `frames.json` + empirical `detGateBlind` + majority verdicts → the two matrices, precision/recall with Wilson CIs, K-agreement, abstention rate, the `expected vs empirical detGateBlind` reconciliation assert (§6).
9. **`report-emitter.mjs`** — markdown + `results.json` to `build-review/<date>-vision-judge-bench.md`. Carries the honest-caveats section (floor stays authoritative; leg stays advisory).
10. **`orchestrator.mjs`** — two-phase driver (capture serial / lock-guarded; judge parallel pool of 3-4); idempotent clean scratch dir per run; probes the single :5173 server fail-loud.

### 3.1 Two-phase split (the concurrency fix)
- **Phase 1 CAPTURE** — bounded-serial. Prop-mode defects share the ONE :5173 server but step one navigation at a time. Source-mode defects take an exclusive file-lock (write → HMR settle → capture → targeted revert → assert clean → release). Never two source edits in flight; never a prop capture interleaved with a source-edit window.
- **Phase 2 JUDGE** — safely parallel. Reads only static PNGs; spawns isolated `claude --print` subprocesses (fresh context per call by construction). No server/source contention. Worker pool bounded (3-4) for free-tier throughput.

### 3.2 Concurrency hazard (the :5173 / shared-source collision) — resolution
Three layers, priority order: **(1) prop/flag injection as default** (additive `?defect=`, DEV-gated; source + served bundle never change ⇒ unlimited captures from one server, parallel-safe). **(2) serialized source-mode** behind a global mutex for the residual structural defects, with targeted single-file revert. **(3) worktree isolation** = DEFERRED (D2) unless throughput demands it.

### 3.3 Prop-injection fidelity (R10)
Prop-mode is trusted only after **md5 byte-equality vs the source-edit path for a representative sample per family** (≥1 paint-role, ≥1 geometry, ≥1 value defect/family) + the 2 seeds. Any defect whose prop and source PNGs differ is captured source-mode only. Report states the fidelity-validated fraction.

### 3.4 Crop + answer-leak (R11) — per-family `cropProfile`
- **sort-merge / seq-array:** existing top-band callout crop (chip-rect measure + 4px), as the seed gate does.
- **chart-dist:** top callout crop **AND** mask/crop the **annotation P-box** (clamped to `[PLOT_Y1,PLOT_Y0]` = mid-plot, verified — the standard crop does NOT remove it). `probability-number` defect: P-box masked; `CHECK_ANNOT_PLAUSIBLE` becomes "estimate area-fraction from shaded pixels; the printed number is removed" — or the check is dropped (§4.3).
- **matrix-grid:** top callout crop AND mask the `rowOp` bottom rail (carries the operation text).
- **graph-tree:** top callout crop.
- **HARD ASSERT per frame:** after crop, run a text-scan (OCR or DOM-text-bbox intersection) over the saved PNG region; **fail the capture** if any answer-bearing token (PASS/FAIL/probability digits in the masked zone/phase words) survives. Bare-frame validity is load-bearing; this assert defends it.

---

## 4. Per-family rubrics (defect-blind positive specifications)

**Authoring rule (R5):** each rubric describes ONLY what a CORRECT figure of that family looks like, derived from the `renders` description. No CHECK names a specific sabotage. The judge gets the rubric text + the figure's declared INPUT + the output contract — **never the catalog, never a correctness oracle**.

**Output contract (all families):** `VERDICT: PASS|FAIL`, then one line per CHECK (`PASS|FAIL` + a pixel-grounded reason naming the concrete cell/bar/column/box/color observed). Any single CHECK FAIL ⇒ VERDICT FAIL. A verdict that only restates a caption is itself invalid (frames carry no answer caption).

### 4.1 Independence check (mechanical)
Before the bench runs, a reviewer is given each `rubrics/<family>.txt` but NOT `defect-catalog.mjs` and must be **unable to enumerate the defect list** from the CHECKs. If they can, the rubric is too defect-shaped and is rewritten. Record pass/fail of this check in the report.

### 4.2 Judge input contract (R6)
Judge receives: (a) the bare cropped PNG; (b) the figure's **declared input** — for seq-array the `array`+`phase`+pointers state (the renderer's own input), for chart-dist the reveal beat, for matrix-grid the grid-kind, for graph-tree the declared frontier-ids — i.e. exactly what the component consumes. Judge does **NOT** receive: expected value-at-(r,c), expected active cell, expected highlight set, expected probability, or any "should be" oracle.

### 4.3 Checks that cannot be pixel-evaluated from input alone → DROP or RESTRUCTURE
- Arithmetic correctness of a DP/Gauss value ("is dp[5]=5 right") — **dropped** (not a vision lift; the trace harness owns it).
- chart `CHECK_ANNOT_PLAUSIBLE` with the number visible — **restructured** to area-fraction estimation with the number masked, or dropped.
- callout-anchoring checks where the callout band is cropped out — **either** capture a per-defect UNCROPPED variant scoped to that check **or** drop the check (do not let an answer leak in to keep a check alive).

### 4.4 Per-family rubric skeletons (defect-blind)
The detailed positive-spec CHECK text is authored in the build (Task R), but the SHAPE is fixed here so independence is auditable:

- **seq-array:** sorted-prefix is a contiguous recolored left block; exactly one champion highlight in the scanned suffix; (bars) height monotonic with the printed digit; all visible digits distinct; sorted-wall right edge aligns with the colored boundary; pointer chevrons sit over the cell whose paint-role matches the label; live-figure cross-check on an adjacent pair. **Conditional encodings (R7):** when `min===i` expect no swap-destination accent; absent rule when `sortedCount=0` is correct.
- **sort-merge:** two clusters separated by a clear gap; output cells solid accent distinct from grey body; each front ringed+chevroned; visible values = exact multiset, no dup/missing; output grows left→right ascending; final row all-accent ascending under a SORTAT rule; brackets one-contiguous-run at correct boundaries; chip overlaps nothing; live-figure cross-check.
- **matrix-grid:** exactly one visually-distinguished active cell (when the step has one — **pivot:null ⇒ zero highlights is CORRECT**, R7); filled/computed region carries the distinct wash, empty cells plain; values render inside their own cell, no overflow/occlusion; axis headers present when the content type requires them; live-figure cross-check (callout band excluded).
- **graph-tree:** every box's digits read correctly and in non-decreasing order where merged; value multiset conserved; **(R13)** membership checked only where ≥2 co-visible siblings; frontier count/identity matches the declared frontier; exactly the active node(s) highlighted; callout has a box above it; no overlap/clip; step-specificity cross-check.
- **chart-dist:** x+y axes present; y-ticks at 0 and max; single monotone-x curve; two separated dashed drops with a-left/b-right badges (reveal≥2); shade is warm accent, bounds align with the drops, bottom rests on baseline (reveal≥3); annotation box present+unclipped (reveal≥4) — **plausibility check only on masked-number area-fraction** (§4.3); progression cross-check.

---

## 5. Empirical `detGateBlind` (R1/R2/R7-gate) — `det-probe.mjs`

For every captured **BAD** frame:
1. Determine the frame's real route + family.
2. If the family is **sort-merge** → run `frame-conjunction-gate.mjs` and `svg-overlap-gate.mjs` against that route/defect; record RED (caught) or GREEN (missed). `detGateBlind = (both GREEN)`.
3. If the family is **non-sort-merge** → the gates cannot load the route. Record `gateCoverage:"unwired"`, `detGateBlind=true`, and a `gateRan:false` flag. **"Could not run" is reported distinctly from "ran→GREEN"** — never silently treated as evidence of the floor's blindness vs. as a coverage gap.
4. **Reveal-1→2 near-threshold check (chart, R-classification minor):** for the chart interval beat, empirically measure `changedPx` on the cropped pair to confirm the marks-only delta is comfortably >MEANINGFUL_PX=80 (so the "blind" marks defects are stable 'live') AND that suppressing marks drops it <80 (so `interval-marks-missing` would be 'fail' if wired). Record the measured delta; if it sits near 80, flag the beat's classification unsafe.

Output: `detgate.json` mapping defectId → `{detGateBlind, gateRan, gateCoverage, verdict:'RED'|'GREEN'|'N/A'}`.

---

## 6. Metrics

**Label convention:** ground truth BAD=positive, GOOD=negative; judge majority FAIL = positive-prediction, PASS = negative. TP=BAD→FAIL (caught), FN=BAD→PASS (**dangerous miss**), FP=GOOD→FAIL (safe but noisy), TN=GOOD→PASS.

### 6.1 Matrix-2 (ALL defects) — full coverage
Positives = all BAD frames; negatives = all GOOD frames. Report `precision=TP/(TP+FP)`, `recall=TP/(TP+FN)`, per-family AND overall, each with Wilson 95% CI.

### 6.2 Matrix-1 (the MARGINAL-value subset) — RECALL-led (R8)
Positives = BAD frames whose **empirical** `detGateBlind=true` (the floor does NOT catch them today). **Headline = RECALL** ("does the vision leg cover what the floor misses?"). Precision, if reported, uses GOOD frames **scoped to the same families/population** as the subset — never the whole-corpus FP pool paired with a subset numerator. The shared GOOD/FP rows are documented. The two real sort-merge catchables (`divide-ignores-runs`, `scrubber-frozen`) + `chip-occludes-label` (if empirically RED) are EXCLUDED from Matrix-1 (the floor owns them) but INCLUDED in Matrix-2.

### 6.3 K-runs reduction (R9)
K=3 (odd). Per-frame decision = **majority of parsed verdicts**. UNPARSEABLE = **FAIL, kept in denominator** (conservative; a malformed reply never silently passes a BAD frame). Report: K-agreement (unanimous vs split count), abstention rate (UNPARSEABLE fraction), AND a **sensitivity row** showing precision/recall if abstentions were instead dropped — flag the number fragile if the headline moves. The asymmetric council bar (bad⇒FAIL≥2/3, good⇒PASS K/K) is reported as a SEPARATE advisory column, not the decision rule.

### 6.4 Confidence (R17)
Every rate carries a Wilson 95% interval. A cell with N below the minimum threshold (set in build; e.g. <8 positives) is reported **"insufficient sample — N=x"** instead of a point estimate. Headline trust sits on OVERALL + Matrix-2; per-family numbers are CI-qualified.

### 6.5 Reconciliation assert (R1/R3-classification)
`scorer.mjs` asserts `catalog.expectedDetGateBlind === empirical.detGateBlind` for every defect. On mismatch: **fail the run loud** (or quarantine that defect from BOTH matrices and surface it in the report). `gateCoverage:"unwired"` defects must have `expectedDetGateBlind:true`; any `expected:false` on an unwired family is a catalog bug that fails this assert.

---

## 7. GOOD/BAD corpus sizes (R4/R16/R17)

**Balanced, with stated N.** For every BAD frame, capture the CLEAN render of the SAME (route, frameIndex) as the paired GOOD. PLUS per-family **borderline GOODs** (correct-but-tempting states) to bound the false-positive rate honestly.

- **BAD frames:** each defect captured across **all its discriminating frameIndices** and **both skins** where the defect is skin-bearing (e.g. seq-array boxes+bars; sort-merge boxes+bars). Target ≥2 discriminating frames per defect where the trace provides them.
- **GOOD-clean:** 1:1 with each BAD frame (the un-defected same frame) → guarantees ≥N_bad negatives.
- **GOOD-borderline (≥3 per family):** e.g. seq-array: sorted-prefix of length 1; champion is 2nd-shortest because the true min already sits at i; `min===i` "deja minim" frame. chart-dist: legitimately narrow interval where a≈b but distinct; reveal=1 bare-curve. matrix-grid: pivot:null matrix-load frame (zero highlights is correct); a fully-filled final frame. graph-tree: single-node frontier; a complete level with all siblings co-visible. sort-merge: final all-accent frame; a single-run divide frame.

**Indicative corpus (exact counts finalized by the label-audit, §8):** seq-array 8 BAD-defects, sort-merge 10, matrix-grid 6, graph-tree 9, chart-dist 11 = 44 BAD-defect classes; ×~2 discriminating frames ×(skins where applicable) ⇒ ~90-120 BAD frames; matched ~90-120 GOOD-clean; +~15 GOOD-borderline. The report states the FINAL realized N per family/per matrix; under-N cells are CI-flagged (§6.4).

---

## 8. Label audit + adjudication (R7/R18)

Before scoring:
1. **Audit every (defect, frameIndex)** against the SHIPPED instance data (`VizDemoPage.tsx` / demo-route `data_json`, verbatim). Confirm the injection produces a VISIBLY wrong frame at THAT index. **Exclude no-op frames** (e.g. sorted-prefix removal on a `sortedCount=0` frame is invisible → that frame is actually GOOD-looking; drop or relabel).
2. **Encode conditional-correct frames** so the ground truth is right: pivot:null ⇒ 0 highlights = GOOD; min===i ⇒ no destination accent = GOOD; sortedCount=0 ⇒ no rule = GOOD.
3. **Plausibility defects forced gross:** chart `probability-number` injected as 0.05 over near-total coverage (or symmetric) so a faithful judge MUST fail; if a defect cannot be made unambiguously wrong, drop it.
4. **One-time adjudication:** a human (or an independent second judge) confirms BAD-looks-wrong and GOOD-looks-right on the captured corpus BEFORE the K-run scoring. Record the adjudicated-vs-asserted disagreement rate — it bounds the bench's own error floor.

---

## 9. Honest caveats (carried verbatim into the report)
- The deterministic floor (frame-conjunction-gate + svg-overlap-gate) stays **authoritative and hard-blocking**. This bench changes nothing about that.
- The vision leg stays **ADVISORY / SHADOW** regardless of the measured precision/recall. This bench MEASURES; it does not graduate the leg.
- The floor is wired ONLY to sort-merge today; for the other 4 families the "marginal value" question is the WHOLE value (the floor catches nothing there). D1 records porting as separate future work.
- Numbers are reported with CIs; small-N cells are flagged, not point-estimated. The fidelity-validated prop fraction and the label-adjudication disagreement rate are reported so a reader can discount accordingly.

---

## 10. Build task list (just-in-time, ready for a build workflow)

Ordered by dependency. Each task is read-only-on-plan for the executor (mismatch ⇒ BLOCK → PM amends).

1. **T1 — `frame-capturer.mjs` generalization.** Lift `tools/__fixtures__/capture-one.mjs` into `captureBareFigure({route, rootTestid, scrubberTestid, scrubberIndex, frameIndex, predictGate, cropProfile, query})`. Parameterize over family root/scrubber; keep viewport/DPR/reducedMotion/SETTLE_MS. Unit-smoke: capture seq-array frame 5 on `/viz-demo` + `/lectie-selectsort`.
2. **T2 — `crop-profiles.mjs` + answer-leak assert.** Per-family crop (chart P-box + matrix rowOp masked); post-crop text-scan assert that fails capture on surviving answer tokens. Verify chart P-value is gone from the bare PNG.
3. **T3 — `defect-injector.mjs`.** Additive DEV-only `?defect=<id>` prop path in each family/demo mount (render-time transform); source-edit fallback with file-lock + targeted single-file revert + porcelain-clean assert (scoped path only — NEVER `git checkout .`/`clean`).
4. **T4 — prop-fidelity validation.** md5 prop-vs-source for the 2 seeds + a representative sample/family (paint/geometry/value). Record validated fraction; demote any mismatch to source-only.
5. **T5 — `defect-catalog.mjs`.** Author all entries per §2 with the R12/R14/R15 corrections, `expectedDetGateBlind`, `gateCoverage`, `cropProfile`, frameIndices.
6. **T6 — corpus capture (Phase 1).** Capture BAD + GOOD-clean + GOOD-borderline (§7) into a clean scratch dir; write `frames.json` manifest (labels out-of-band).
7. **T7 — label audit + adjudication (§8).** Audit each frame against shipped data; drop/relabel no-ops; encode conditionals; force plausibility defects gross; record adjudication disagreement.
8. **T8 — `det-probe.mjs` (§5).** Run the two real gates against BAD frames; emit `detgate.json` with `{detGateBlind, gateRan, gateCoverage, verdict}`; measure the chart reveal-1→2 changedPx near-threshold check.
9. **T9 — `rubrics/<family>.txt` (defect-blind, §4).** Positive specs only; run the §4.1 independence check (reviewer without catalog cannot reconstruct defect list); restructure/drop the §4.3 un-pixel-evaluable checks.
10. **T10 — `verdict-parser.mjs` + unit tests.** Pure function; any-CHECK-FAIL⇒FAIL; UNPARSEABLE distinct. Test without spawning claude (mirror `frame-conjunction-core.mjs`).
11. **T11 — `vision-judge.mjs` (Phase 2).** `claude --print` free relay, K=3 fresh subprocesses, abspath-in-prompt image delivery, defect-blind rubric + declared-input only (no oracle), worker pool 3-4.
12. **T12 — `scorer.mjs`.** Join manifest + `detgate.json` + verdicts; reconciliation assert (§6.5, fail loud on mismatch); two matrices (Matrix-1 recall-led); Wilson CIs; K-agreement; abstention rate + sensitivity row.
13. **T13 — `report-emitter.mjs`.** Markdown + `results.json` to `build-review/<date>-vision-judge-bench.md`; honest-caveats section; per-defect verdict table; independence-check + fidelity-fraction + adjudication-disagreement lines.
14. **T14 — `orchestrator.mjs`.** Two-phase driver; single :5173 probe fail-loud; serial/lock capture; parallel judge; idempotent scratch dir.
15. **T15 — whole-bench self-review council** (post-run, MANDATORY per build-workflow): re-verify the reconciliation assert fired correctly, the GOOD corpus is non-empty per family, no answer leaked into any bare PNG, and the leg is still reported ADVISORY.

---

## 11. Open-risk register (residual, surfaced not hidden)
1. Free-tier non-determinism/throughput — mitigated by K=3 + majority + bounded pool + FAIL-OPEN-counted abstention; report carries abstention + agreement.
2. Prop-fidelity extrapolation beyond the validated sample — mitigated by per-family sampling (T4); residual flagged.
3. Crop heuristic for a future family with differently-placed captions — mitigated by the per-family cropProfile + hard answer-leak assert; new families need a new profile.
4. Adjudication is a one-time human pass — its disagreement rate is reported as the bench's error floor.
5. Small N per family — CI-flagged; trust placed on OVERALL + Matrix-2.