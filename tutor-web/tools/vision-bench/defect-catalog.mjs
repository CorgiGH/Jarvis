/**
 * defect-catalog.mjs — the static defect/GOOD corpus for the vision-judge bench (spec §2, §3.0 #1, T5).
 *
 * NOT mutating code — pure data. One entry per defect/GOOD frame-source. The capturer (captureMany)
 * consumes the route/root/scrubber/frameIndices fields; the scorer consumes label/family/
 * expectedDetGateBlind/gateCoverage/cropProfile/rubricKey; the injector consumes injection{}.
 *
 * `expectedDetGateBlind` is a DOCUMENTED PRIOR only — the empirical value from det-probe.mjs (§5)
 * wins, and scorer.mjs (§6.5) asserts catalog === empirical and fails loud on mismatch. Per D1, the
 * deterministic floor (frame-conjunction-gate + svg-overlap-gate) is WIRED ONLY to sort-merge, so for
 * the other four families gateCoverage:"unwired" ⇒ expectedDetGateBlind MUST be true (an unwired
 * family cannot be "catchable").
 *
 * ── INJECTION LINE-NUMBER DRIFT (verified against CURRENT source 2026-06-17, vs spec's 2026-06-16) ──
 * The §2 injection tables cite source lines from 2026-06-16. Re-verified line-by-line this session
 * against the live family .tsx files. FINDING: the cited lines are still ACCURATE for seq-array,
 * matrix-grid, graph-tree, chart-dist, and sort-merge — the families have NOT drifted since the spec
 * was written (the 5 bench family files are git-clean at HEAD e060659). The two notable spec citations
 * that read as "drift" are actually correct on inspection:
 *   • sort-merge HEAD_LIFT — the constant `HEAD_LIFT = 7` is DEFINED at :269 (the spec's ":1011" points
 *     at the chevron RENDER block that READS HEAD_LIFT, ~:1001-1014; both are valid injection targets).
 *   • sort-merge chip-occludes-label — the cluster `runLabelY` is at :766 (spec ":766" exact).
 * The R12/R14/R15 corrections are CONFIRMED against source (see per-entry `injectionVerified`):
 *   • R12: SortMergeFamily.tsx:932 is `const value = values[id];` (painted glyph) and :933 is
 *     `const pos = st.array.indexOf(value);` (data-cell-index DOM stamp only) — confirmed exact.
 *   • R14: MatrixGridFamily dp-fib forces rows=1, so transpose crashes; primary = off-by-one spill
 *     `grid[w.row][w.col]` write at :246 (transpose restricted to gauss rows≥2).
 *   • R15: ChartDistributionFamily mark-a/mark-b at :468-469 use exact aPx/bPx; shade path build at
 *     :330-337 — lead the shade-desync defect with a realistic round/off-by-one read, not {a:xMin,b}.
 *
 * Live frame ranges (verified this session via the scrubber max attr):
 *   seq-array 0-17 · mg dp-fib 0-5 · mgg gauss 0-6 · graph-tree 0-7 · chart-dist 0-3.
 * Each /viz-demo family root paints TWICE (dark-section copy = scrubberIndex 0, standalone tile = 1);
 * the bench captures the standalone tile (scrubberIndex 1), matching frame-capturer's note.
 */

// Shared mount descriptors (verified live). cropProfile === family per spec §3.4.
const M = {
  "seq-array": { family: "seq-array", route: "/tutor/viz-demo", rootTestid: "seq-array-root", scrubberTestid: "seq-array-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "unwired", cropProfile: "seq-array", maxFrame: 17 },
  // sort-merge BOXES skin lives on /merge-compare scrubberIndex 1 (the bars tile) AND /lectie-mergesort
  // (boxes, predict-gated, scrubberIndex 0). The deterministic floor is WIRED here → gateCoverage:"wired".
  "sort-merge-bars": { family: "sort-merge", skin: "bars", route: "/tutor/merge-compare", rootTestid: "sort-merge-root", scrubberTestid: "sort-merge-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "wired", cropProfile: "sort-merge", maxFrame: 7 },
  "sort-merge-boxes": { family: "sort-merge", skin: "boxes", route: "/tutor/lectie-mergesort", rootTestid: "sort-merge-root", scrubberTestid: "sort-merge-scrubber", scrubberIndex: 0, predictGate: true, gateCoverage: "wired", cropProfile: "sort-merge", maxFrame: 7 },
  "matrix-grid-dp": { family: "matrix-grid", route: "/tutor/viz-demo", rootTestid: "mg-root", scrubberTestid: "mg-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "unwired", cropProfile: "matrix-grid", maxFrame: 5 },
  "matrix-grid-gauss": { family: "matrix-grid", route: "/tutor/viz-demo", rootTestid: "mgg-root", scrubberTestid: "mgg-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "unwired", cropProfile: "matrix-grid", maxFrame: 6 },
  "graph-tree": { family: "graph-tree", route: "/tutor/viz-demo", rootTestid: "graph-tree-root", scrubberTestid: "graph-tree-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "unwired", cropProfile: "graph-tree", maxFrame: 7 },
  "chart-dist": { family: "chart-dist", route: "/tutor/viz-demo", rootTestid: "chart-dist-root", scrubberTestid: "chart-dist-scrubber", scrubberIndex: 1, predictGate: false, gateCoverage: "unwired", cropProfile: "chart-dist", maxFrame: 3 },
};

const mk = (mountKey, entry) => {
  const m = M[mountKey];
  const e = {
    family: m.family,
    route: m.route,
    rootTestid: m.rootTestid,
    scrubberTestid: m.scrubberTestid,
    scrubberIndex: m.scrubberIndex,
    predictGate: m.predictGate,
    gateCoverage: m.gateCoverage,
    cropProfile: m.cropProfile,
    skin: m.skin ?? null,
    rubricKey: m.family,
    label: entry.label || "BAD",
    expectedDetGateBlind: entry.expectedDetGateBlind ?? (m.gateCoverage === "unwired" ? true : undefined),
    injection: entry.injection || { mode: "source" },
    ...entry,
  };
  // §6.5 invariant: an unwired family can never be catchable. Applies to BAD frames only — GOOD
  // (clean / borderline) frames carry expectedDetGateBlind:null (no det-gate expectation by design).
  if (e.label === "BAD" && e.gateCoverage === "unwired" && e.expectedDetGateBlind !== true) {
    throw new Error(`[defect-catalog] ${e.id}: unwired BAD defect must have expectedDetGateBlind:true (got ${e.expectedDetGateBlind})`);
  }
  return e;
};

// ── 2.1 SequenceArrayFamily (selection sort) — 8 BAD defects ────────────────────────────────────────
const SEQ_ARRAY_BAD = [
  mk("seq-array", {
    id: "missing-sorted-prefix-paint",
    expectedDetGateBlind: true,
    // discriminating only where sortedCount>0 (swap-result frames). frames 5,10,14,17 are post-swap.
    frameIndices: [5, 10, 14],
    corpusNote: "discriminating only where sortedCount>0 (post-swap frames)",
    injectionVerified: "fillForCell sorted branch SequenceArrayFamily.tsx:448 (white) / darkCellPaint :587 / bars :921 region",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":448/:587/:921" } },
  }),
  mk("seq-array", {
    id: "swapped-min-paint-on-swap-source",
    expectedDetGateBlind: true,
    frameIndices: [5, 14, 17],
    corpusNote: "swap frames with min≠i only (frames 5,14,17 are swaps where min was elsewhere)",
    injectionVerified: "white accent branch :449 / dark swap-source guard :596",
    injection: { mode: "source", patch: null, source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":449/:596" } },
  }),
  mk("seq-array", {
    id: "bars-height-value-inversion",
    expectedDetGateBlind: true,
    skin: "bars",
    frameIndices: [1, 5],
    corpusNote: "BARS skin only — height∝value inverted",
    injectionVerified: "heightOf invert :412-413 or wrong arg :1001",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":412-413/:1001" } },
  }),
  mk("seq-array", {
    id: "duplicated-value-token",
    expectedDetGateBlind: true,
    frameIndices: [0, 3, 7],
    corpusNote: "distinct-array frames (every frame; values are distinct)",
    injectionVerified: "painted value <text> via valueOfId (dark :615 / bars :943) or computeIdentity :204",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":615/:943/:204" } },
  }),
  mk("seq-array", {
    id: "sorted-rule-overshoot",
    expectedDetGateBlind: true,
    frameIndices: [10, 14, 17],
    corpusNote: "sortedCount≥1 (the SORTAT rule overshoots its boundary)",
    injectionVerified: "ruleX2 → cxOf(sortedCount)/cxOf(n-1) (dark :627 / bars :950)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":627/:950" } },
  }),
  mk("seq-array", {
    id: "chevron-on-wrong-column",
    expectedDetGateBlind: true,
    frameIndices: [1, 3, 12],
    corpusNote: "scan/swap frames; data-pointer-index kept correct so it is pixel-only",
    injectionVerified: "marker tick cxFn(index+1) (dark :870 / bars :1172); data-pointer-index unchanged",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":870/:1172" } },
  }),
  mk("seq-array", {
    id: "callout-anchored-to-wrong-column",
    expectedDetGateBlind: true,
    frameIndices: [3],
    corpusNote: "CROP-EXEMPT (callout band) → §4.3: needs an UNCROPPED variant or DROP. Flagged cropExempt.",
    cropExempt: true,
    injectionVerified: "anchorIndex wrong (white :460 / dark :617 / bars :945)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":460/:617/:945" } },
  }),
  mk("seq-array", {
    id: "frozen-figure-over-live-state",
    // catchable IF wired → empirically blind today (seq-array unwired, D1).
    expectedDetGateBlind: true,
    frameIndices: [5, 10],
    corpusNote: "the floor would own it once ported (D1); blind today because seq-array is unwired",
    injectionVerified: "pin render to frames[0].state (render selection :1229-1234)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx", target: ":1234" } },
  }),
];

// ── 2.2 sort-merge (merge) — 10 BAD defects (gate WIRED) ────────────────────────────────────────────
const SORT_MERGE_BAD = [
  mk("sort-merge-bars", {
    id: "divide-ignores-runs",
    expectedDetGateBlind: false, // catchable — gate genuinely runs (frame-conjunction)
    frameIndices: [1, 2, 3],
    corpusNote: "REAL catchable: frame-conjunction-gate is wired to this route",
    injectionVerified: "divide branch forces divideRuns empty SortMergeFamily.tsx:460-472",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":460-472" } },
  }),
  mk("sort-merge-bars", {
    id: "scrubber-frozen",
    expectedDetGateBlind: false, // catchable
    frameIndices: [3, 5, 7],
    corpusNote: "REAL catchable: renderFrame ignores frame.state → frame-conjunction-gate fires",
    injectionVerified: "renderFrame reads frame.state at :619; pin to frame 0",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":619" } },
  }),
  mk("sort-merge-bars", {
    id: "wrong-grouping",
    expectedDetGateBlind: true,
    frameIndices: [1, 2, 3],
    corpusNote: "blind — divideGroupOf indices corrupted (grouping wrong but runs present)",
    injectionVerified: "corrupt divideGroupOf indices :463-467",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":463-467" } },
  }),
  mk("sort-merge-bars", {
    id: "runs-collapsed",
    expectedDetGateBlind: true,
    frameIndices: [2, 3],
    corpusNote: "blind — RUN_GAP collapsed so the two runs touch",
    injectionVerified: "RUN_GAP 0 :263 / BARS_RUN_GAP 0 :313",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":263/:313" } },
  }),
  mk("sort-merge-bars", {
    id: "output-not-accent",
    expectedDetGateBlind: true,
    // discriminating on the merge-in-progress output frames (5,6 carry #fde047 output cells). Frame 7
    // (bars final row) uses the #3a3a3a/#2a2a2a final palette, not accent — that is final-not-sorted's
    // frame (verified live via __probe-fills.mjs); excluded here so the prop transform is not a no-op.
    frameIndices: [5, 6],
    corpusNote: "blind — output cells lose their accent (read as plain body); discriminating on frames 5,6",
    injectionVerified: "paintFor output case accent→leftTint :593-594",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":594" } },
  }),
  mk("sort-merge-bars", {
    id: "head-not-marked",
    expectedDetGateBlind: true,
    frameIndices: [5, 6],
    corpusNote: "blind — run heads not ringed/lifted, null chevron",
    injectionVerified: "paintFor heads→body :597-599; HEAD_LIFT 0 :269; chevron block :1001-1014",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":597/:269/:1014" } },
  }),
  mk("sort-merge-bars", {
    id: "value-dup-missing",
    expectedDetGateBlind: true,
    frameIndices: [0, 5, 7],
    corpusNote: "blind — [R12 CORRECTED] target the PAINTED glyph, not the DOM stamp",
    injectionVerified: "R12: paint values[id] at :932 (NOT :933 indexOf DOM stamp) / buildIdOfValue :418-422",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":932/:418-422" } },
  }),
  mk("sort-merge-bars", {
    id: "output-reversed",
    expectedDetGateBlind: true,
    frameIndices: [7],
    corpusNote: "blind — output slots reversed (descending output)",
    injectionVerified: "reverse output slots :503-507 or flip ruleX :740-741",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":503-507/:740-741" } },
  }),
  mk("sort-merge-bars", {
    id: "chip-occludes-label",
    expectedDetGateBlind: false, // catchable via svg-overlap-gate (wired to sort-merge)
    frameIndices: [5, 6],
    corpusNote: "catchable IF svg-overlap fires — confirm empirically (§5). Cluster label y vs calloutBottom.",
    injectionVerified: "runLabelY not below calloutBottom :766 (calloutBottom :638 boxes / :1114 bars) + long callout",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":766/:638" } },
  }),
  mk("sort-merge-bars", {
    id: "final-not-sorted",
    expectedDetGateBlind: true,
    frameIndices: [7],
    corpusNote: "blind — final row role 'row' not 'rowSorted' (not all-accent ascending)",
    injectionVerified: "final branch role row not rowSorted :469-472",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/SortMergeFamily.tsx", target: ":469-472" } },
  }),
];

// ── 2.3 MatrixGridFamily — 6 BAD defects (all blind today; gate unwired → D1) ───────────────────────
const MATRIX_GRID_BAD = [
  mk("matrix-grid-gauss", {
    id: "pivot-highlight-wrong-cell",
    expectedDetGateBlind: true,
    frameIndices: [1, 5, 6],
    corpusNote: "pivot ring on the wrong cell (col+1)",
    injectionVerified: "isPivot test st.pivot.col+1 :463 or frame emit col+1 :253",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":463/:253" } },
  }),
  mk("matrix-grid-dp", {
    id: "filled-wash-missing-or-spurious",
    expectedDetGateBlind: true,
    frameIndices: [2, 4, 5],
    corpusNote: "filled wash missing/inverted (computed cells not distinguished)",
    injectionVerified: "isFilled=false / invert :462 or skip filled.add :247",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":462/:247" } },
  }),
  mk("matrix-grid-dp", {
    id: "swapped-or-duplicated-cell-values",
    expectedDetGateBlind: true,
    frameIndices: [2, 4, 5],
    corpusNote: "[R14 FIX] primary = off-by-one spill grid[w.row][w.col+1] on dp-fill (rows=1, transpose crashes); transpose variant gauss-only",
    injectionVerified: "R14: spill write target grid[w.row][w.col] :246 (write col+1); transpose restricted to gauss rows≥2",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":246" } },
  }),
  mk("matrix-grid-gauss", {
    id: "missing-row-or-column-headers",
    expectedDetGateBlind: true,
    frameIndices: [0, 2],
    corpusNote: "axis headers dropped (gauss has row+col headers)",
    injectionVerified: "short-circuit header guards :424-456",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":424-456" } },
  }),
  mk("matrix-grid-gauss", {
    id: "mis-colored-pivot-blends-into-grid",
    expectedDetGateBlind: true,
    frameIndices: [1, 5, 6],
    corpusNote: "pivot stroke neutralized to grid; data-pivot stamp kept (pixel-only)",
    injectionVerified: "neutralize pivot in cellFill/strokeWidth :399-411,:478-480; keep data-pivot stamp :498",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":399-411/:480" } },
  }),
  mk("matrix-grid-dp", {
    id: "frozen-grid-region-no-motion",
    expectedDetGateBlind: true, // catchable IF wired → blind today
    frameIndices: [3, 5],
    corpusNote: "pin to frames[0].state snapshot; blind today (matrix-grid unwired, D1)",
    injectionVerified: "pin grid/filled to frame 0 (frame-build :251)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/MatrixGridFamily.tsx", target: ":251" } },
  }),
];

// ── 2.4 GraphTreeFamily — 9 BAD defects (all blind today; gate unwired → D1) ────────────────────────
const GRAPH_TREE_BAD = [
  mk("graph-tree", {
    id: "swapped-or-stale-label-on-node",
    expectedDetGateBlind: true,
    frameIndices: [5, 6, 7],
    corpusNote: "merge deltas mis-applied → stale node label (frames 5-7 carry deltas)",
    injectionVerified: "skip/mis-apply delta .set :92; render reads :254",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":92/:254" } },
  }),
  mk("graph-tree", {
    id: "duplicated-value-across-nodes",
    expectedDetGateBlind: true,
    frameIndices: [3, 5],
    corpusNote: "two nodes collide to the same label",
    injectionVerified: "collide two labelOf :92 or author data",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":92" } },
  }),
  mk("graph-tree", {
    id: "wrong-grouping-bracket-membership",
    expectedDetGateBlind: true,
    frameIndices: [2, 3],
    corpusNote: "[R13] evaluate only on ≥2-co-visible-sibling frames (frames 2,3 have full levels)",
    r13CoVisibleRequired: true,
    injectionVerified: "corrupt parent pointers :27; layout :197",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":27/:197" } },
  }),
  mk("graph-tree", {
    id: "mis-colored-highlight-frontier",
    expectedDetGateBlind: true,
    frameIndices: [1, 5],
    corpusNote: "frontier highlight inverted/dropped (wrong nodes ringed)",
    injectionVerified: "corrupt/invert hi.has(n.id) :223,:231",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":231" } },
  }),
  mk("graph-tree", {
    id: "missing-frontier-node-not-painted",
    expectedDetGateBlind: true,
    frameIndices: [1, 2],
    corpusNote: "a frontier node id dropped from the highlight set",
    injectionVerified: "drop id from step.highlight :93,:228",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":93/:228" } },
  }),
  mk("graph-tree", {
    id: "callout-anchored-to-wrong-or-detached-node",
    expectedDetGateBlind: true,
    frameIndices: [5],
    corpusNote: "CROP-EXEMPT (callout band) → §4.3. Flagged cropExempt.",
    cropExempt: true,
    injectionVerified: "reorder highlightIds[0] :224 (firstHi)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":224" } },
  }),
  mk("graph-tree", {
    id: "overlapping-or-colliding-node-boxes",
    // svg-overlap-gate keys on data-cell-index; GraphTree uses data-node-id → NOT covered → blind today
    expectedDetGateBlind: true,
    frameIndices: [3, 7],
    corpusNote: "blind: svg-overlap-gate keys on data-cell-index, GraphTree uses data-node-id (not covered)",
    injectionVerified: "shrink nodeSize/LEVEL_GAP :200,:134",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":200/:134" } },
  }),
  mk("graph-tree", {
    id: "clipped-node-box-or-label-at-viewport-edge",
    expectedDetGateBlind: true,
    frameIndices: [3, 7],
    corpusNote: "node box clipped at the canvas edge (margin removed)",
    injectionVerified: "margin=0 :207-208",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":207-208" } },
  }),
  mk("graph-tree", {
    id: "frozen-figure-over-live-state",
    expectedDetGateBlind: true, // catchable IF wired → blind today
    frameIndices: [5, 7],
    corpusNote: "always render frames[0] activeNodes; blind today (graph-tree unwired, D1)",
    injectionVerified: "pin render to frame 0 (renderFrame :215-228)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/GraphTreeFamily.tsx", target: ":215-228" } },
  }),
];

// ── 2.5 ChartDistributionFamily — 11 BAD defects (all blind today; gate unwired → D1) ───────────────
const CHART_DIST_BAD = [
  mk("chart-dist", {
    id: "shade-fills-wrong-interval",
    expectedDetGateBlind: true,
    frameIndices: [2, 3],
    corpusNote: "[R15] shade reads Math.round(a)/round(b) or off-by-one bound while mark-a/b use exact",
    injectionVerified: "R15: shade path build :330-337 (read rounded a/b) while mark-a/b :468-469 exact",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":330-337" } },
  }),
  mk("chart-dist", {
    id: "interval-marks-missing-on-reveal2",
    expectedDetGateBlind: true, // catchable IF wired → blind today
    frameIndices: [1],
    corpusNote: "reveal 1→2 pair: marks suppressed on the interval beat; blind today (unwired, D1)",
    injectionVerified: "showInterval=false :381",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":381" } },
  }),
  mk("chart-dist", {
    id: "interval-drops-both-at-same-x",
    expectedDetGateBlind: true,
    frameIndices: [1, 2],
    corpusNote: "reveal≥2: both drops land at the same x (bPx=aPx)",
    injectionVerified: "bPx=aPx :365",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":365" } },
  }),
  mk("chart-dist", {
    id: "probability-number-wrong-or-stale",
    expectedDetGateBlind: true,
    frameIndices: [3],
    corpusNote: "[R7] inject GROSSLY wrong (e.g. 0.05 over near-total coverage); P-box MASKED from judge (§3.4) → this check is RESTRUCTURED to area-fraction or DROPPED (the printed number never reaches the judge)",
    cropMasked: true,
    restructuredOrDropped: true,
    injectionVerified: "probText probability :386 (data.probability.toFixed)",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":386" } },
  }),
  mk("chart-dist", {
    id: "shade-detached-floats-above-baseline",
    expectedDetGateBlind: true,
    frameIndices: [2, 3],
    corpusNote: "reveal≥3: shade baseline lifted off the axis",
    injectionVerified: "PLOT_Y0→PLOT_Y0-40 in shade anchors :335-337",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":335-337" } },
  }),
  mk("chart-dist", {
    id: "curve-points-shuffled-or-duplicated",
    expectedDetGateBlind: true,
    frameIndices: [0, 2],
    corpusNote: "any reveal: curve points reversed/concatenated (jagged/non-monotone-x curve)",
    injectionVerified: "reverse/concat data.points at render :310-312",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":310-312" } },
  }),
  mk("chart-dist", {
    id: "ab-badges-swapped",
    expectedDetGateBlind: true,
    frameIndices: [1, 2],
    corpusNote: "reveal≥2: a-badge and b-badge x bindings swapped",
    injectionVerified: "swap x={aPx}/x={bPx} glyph bindings :472-473",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":472-473" } },
  }),
  mk("chart-dist", {
    id: "shade-mis-colored-blends-into-curve",
    expectedDetGateBlind: true,
    frameIndices: [2, 3],
    corpusNote: "reveal≥3: shade fill→axis ink / fillOpacity 0.02 (vanishes into the curve)",
    injectionVerified: "shadeFill=axisInk :373 / fillOpacity 0.02 :459",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":373/:459" } },
  }),
  mk("chart-dist", {
    id: "y-axis-or-zero-tick-missing",
    expectedDetGateBlind: true,
    frameIndices: [0, 3],
    corpusNote: "any reveal: y-axis line / y-ticks dropped",
    injectionVerified: "buildYTicks→[] :281-287 or drop axis-y line :401-405",
    injection: { mode: "prop", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":281-287/:401" } },
  }),
  mk("chart-dist", {
    id: "frozen-figure-noop-renderframe",
    expectedDetGateBlind: true, // catchable IF wired → blind today
    frameIndices: [2, 3],
    corpusNote: "clamp reveal=1 (figure never advances); blind today (unwired, D1)",
    injectionVerified: "clamp reveal=1 :380",
    injection: { mode: "source", source: { file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx", target: ":380" } },
  }),
  // 11th chart-dist row in §2.5 is a borderline-GOOD (zero-width-looking interval) — placed in GOOD set.
];

// ── GOOD-CLEAN (R4): 1:1 with each BAD frame — the SAME (route, frameIndex) rendered with NO defect ──
// Built mechanically by pairing every BAD (mount, frameIndex) and dropping the injection. Deduped by
// (route, scrubberIndex, frameIndex, skin) so we capture each clean frame once.
function buildGoodClean(badEntries) {
  const seen = new Set();
  const out = [];
  for (const b of badEntries) {
    for (const fi of b.frameIndices) {
      const key = `${b.route}|${b.scrubberIndex}|${fi}|${b.skin || ""}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({
        id: `good-clean__${b.family}__f${fi}${b.skin ? "__" + b.skin : ""}`,
        family: b.family,
        route: b.route,
        rootTestid: b.rootTestid,
        scrubberTestid: b.scrubberTestid,
        scrubberIndex: b.scrubberIndex,
        predictGate: b.predictGate,
        gateCoverage: b.gateCoverage,
        cropProfile: b.cropProfile,
        skin: b.skin ?? null,
        rubricKey: b.family,
        label: "GOOD",
        expectedDetGateBlind: null, // GOOD frames carry no det-gate expectation
        frameIndices: [fi],
        injection: { mode: "none" },
        pairedFrom: b.id,
      });
    }
  }
  return out;
}

// ── GOOD-BORDERLINE (R16/§7): correct-but-tempting states, ≥3 per family ────────────────────────────
const GOOD_BORDERLINE = [
  // seq-array: sorted-prefix length 1; min===i "deja minim" frame; champion is 2nd-shortest because true min sits at i.
  mk("seq-array", { id: "gb-seq-sorted-prefix-len1", label: "GOOD-borderline", frameIndices: [5], expectedDetGateBlind: null, corpusNote: "sorted-prefix of length 1 (post first swap)", injection: { mode: "none" } }),
  mk("seq-array", { id: "gb-seq-min-equals-i", label: "GOOD-borderline", frameIndices: [10], expectedDetGateBlind: null, corpusNote: "min===i 'deja minim' — NO swap-destination accent is CORRECT (R7 conditional)", injection: { mode: "none" } }),
  mk("seq-array", { id: "gb-seq-sortedcount-zero", label: "GOOD-borderline", frameIndices: [0], expectedDetGateBlind: null, corpusNote: "sortedCount=0 ⇒ NO sorted rule is CORRECT (R7 conditional)", injection: { mode: "none" } }),
  // sort-merge: final all-accent frame; a single-run divide frame.
  mk("sort-merge-bars", { id: "gb-sm-final-all-accent", label: "GOOD-borderline", frameIndices: [7], expectedDetGateBlind: null, corpusNote: "final all-accent ascending row (correct end state)", injection: { mode: "none" } }),
  mk("sort-merge-bars", { id: "gb-sm-single-run-divide", label: "GOOD-borderline", frameIndices: [3], expectedDetGateBlind: null, corpusNote: "fully-divided 6-singleton frame (each run length 1 is correct)", injection: { mode: "none" } }),
  mk("sort-merge-bars", { id: "gb-sm-whole-vector", label: "GOOD-borderline", frameIndices: [0], expectedDetGateBlind: null, corpusNote: "whole undivided vector (one run is correct at step 0)", injection: { mode: "none" } }),
  // matrix-grid: pivot:null matrix-load frame (zero highlights correct); fully-filled final frame.
  mk("matrix-grid-gauss", { id: "gb-mg-pivot-null-load", label: "GOOD-borderline", frameIndices: [0], expectedDetGateBlind: null, corpusNote: "pivot:null load frame ⇒ ZERO highlights is CORRECT (R7 conditional)", injection: { mode: "none" } }),
  mk("matrix-grid-dp", { id: "gb-mg-final-filled", label: "GOOD-borderline", frameIndices: [5], expectedDetGateBlind: null, corpusNote: "fully-filled final dp row (all cells washed correct)", injection: { mode: "none" } }),
  mk("matrix-grid-gauss", { id: "gb-mg-gauss-final-tri", label: "GOOD-borderline", frameIndices: [6], expectedDetGateBlind: null, corpusNote: "upper-triangular final gauss frame", injection: { mode: "none" } }),
  // graph-tree: single-node frontier; a complete level with all siblings co-visible.
  mk("graph-tree", { id: "gb-gt-single-node-frontier", label: "GOOD-borderline", frameIndices: [0], expectedDetGateBlind: null, corpusNote: "single-node frontier (root only)", injection: { mode: "none" } }),
  mk("graph-tree", { id: "gb-gt-complete-level", label: "GOOD-borderline", frameIndices: [3], expectedDetGateBlind: null, corpusNote: "complete level, all 6 singleton siblings co-visible", injection: { mode: "none" } }),
  mk("graph-tree", { id: "gb-gt-merged-root", label: "GOOD-borderline", frameIndices: [7], expectedDetGateBlind: null, corpusNote: "merged sorted root (single node, correct end)", injection: { mode: "none" } }),
  // chart-dist: legitimately narrow interval where a≈b but distinct; reveal=1 bare-curve.
  mk("chart-dist", { id: "gb-cd-bare-curve-reveal1", label: "GOOD-borderline", frameIndices: [0], expectedDetGateBlind: null, corpusNote: "reveal=1 bare curve (no interval/shade/annot is correct here)", injection: { mode: "none" } }),
  mk("chart-dist", { id: "gb-cd-narrow-interval", label: "GOOD-borderline", frameIndices: [1], expectedDetGateBlind: null, corpusNote: "[§2.5 11th row] zero-width-LOOKING interval (a≈b but distinct) — tests false-positive", injection: { mode: "none" } }),
  mk("chart-dist", { id: "gb-cd-shade-on-baseline", label: "GOOD-borderline", frameIndices: [2], expectedDetGateBlind: null, corpusNote: "correct shade resting on the baseline", injection: { mode: "none" } }),
];

export const BAD_DEFECTS = [...SEQ_ARRAY_BAD, ...SORT_MERGE_BAD, ...MATRIX_GRID_BAD, ...GRAPH_TREE_BAD, ...CHART_DIST_BAD];
export const GOOD_CLEAN = buildGoodClean(BAD_DEFECTS);
export const GOOD_BORDERLINE_DEFECTS = GOOD_BORDERLINE;

// The full catalog (BAD + GOOD-clean + GOOD-borderline).
export const DEFECT_CATALOG = [...BAD_DEFECTS, ...GOOD_CLEAN, ...GOOD_BORDERLINE_DEFECTS];

// Per-family BAD-defect-class counts (spec §2: 8/10/6/9/11 = 44).
export const BAD_COUNTS = {
  "seq-array": SEQ_ARRAY_BAD.length,
  "sort-merge": SORT_MERGE_BAD.length,
  "matrix-grid": MATRIX_GRID_BAD.length,
  "graph-tree": GRAPH_TREE_BAD.length,
  "chart-dist": CHART_DIST_BAD.length,
  total: BAD_DEFECTS.length,
};

// Expand a catalog entry's frameIndices into one capture item per frame (the captureMany shape).
export function expandToCaptureItems(entries) {
  const items = [];
  for (const e of entries) {
    for (const fi of e.frameIndices) {
      items.push({
        id: e.frameIndices.length > 1 ? `${e.id}__f${fi}` : e.id,
        defectId: e.id,
        family: e.family,
        label: e.label,
        route: e.route,
        rootTestid: e.rootTestid,
        scrubberTestid: e.scrubberTestid,
        scrubberIndex: e.scrubberIndex,
        frameIndex: fi,
        predictGate: e.predictGate,
        cropProfile: e.cropProfile,
        skin: e.skin,
        expectedDetGateBlind: e.expectedDetGateBlind,
        gateCoverage: e.gateCoverage,
        injection: e.injection,
      });
    }
  }
  return items;
}

// ── Self-check (T5): run `node defect-catalog.mjs` to print the census + validate the §6.5 invariant. ──
if (process.argv[1] && process.argv[1].endsWith("defect-catalog.mjs")) {
  const blindUnwiredOk = BAD_DEFECTS.every((d) => !(d.gateCoverage === "unwired" && d.expectedDetGateBlind !== true));
  const wiredFamilies = new Set(BAD_DEFECTS.filter((d) => d.gateCoverage === "wired").map((d) => d.family));
  const catchable = BAD_DEFECTS.filter((d) => d.expectedDetGateBlind === false).map((d) => d.id);
  console.log("BAD defect-class counts:", JSON.stringify(BAD_COUNTS));
  // SPEC INCONSISTENCY (recorded as a line-drift finding): §7 sums chart-dist as 11 BAD ⇒ 44 total,
  // but §2.5's 11th row is explicitly labelled "(borderline-GOOD) zero-width-looking interval" — a
  // GOOD frame, not a BAD defect. So the true BAD-defect-class count is chart-dist=10 ⇒ 43 BAD total
  // (8+10+6+9+10), and the §2.5-row-11 borderline-GOOD lives in the GOOD-borderline set (gb-cd-narrow-interval).
  console.log("Expected per spec §2.5 enumeration: seq=8 sort-merge=10 matrix-grid=6 graph-tree=9 chart-dist=10(+1 borderline-GOOD) total=43 BAD");
  console.log("GOOD-clean frames    :", GOOD_CLEAN.length);
  console.log("GOOD-borderline      :", GOOD_BORDERLINE_DEFECTS.length);
  console.log("wired families       :", [...wiredFamilies].join(", ") || "(none)");
  console.log("expected catchable   :", catchable.join(", "));
  console.log("§6.5 invariant (unwired ⇒ blind):", blindUnwiredOk ? "OK" : "VIOLATED");
  const propCount = BAD_DEFECTS.filter((d) => d.injection.mode === "prop").length;
  console.log(`injection modes      : prop=${propCount} source=${BAD_DEFECTS.length - propCount}`);
  const ok =
    BAD_COUNTS["seq-array"] === 8 &&
    BAD_COUNTS["sort-merge"] === 10 &&
    BAD_COUNTS["matrix-grid"] === 6 &&
    BAD_COUNTS["graph-tree"] === 9 &&
    BAD_COUNTS["chart-dist"] === 10 && // §2.5 row 11 is a borderline-GOOD, not a BAD defect (see note above)
    BAD_COUNTS.total === 43 &&
    blindUnwiredOk;
  console.log(ok ? "CATALOG SELF-CHECK GREEN" : "CATALOG SELF-CHECK RED");
  process.exit(ok ? 0 : 1);
}
