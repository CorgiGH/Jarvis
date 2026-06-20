import { useCallback, useMemo, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Motion grammar (lectie-grade) ────────────────────────────────────────────────────────────────
// The dark lesson surface MOVES between frames; only the white skin (gallery/e2e baseline) snaps.
// Reference (lectie.html) eases cells with `transform .8s cubic-bezier(.34,1.2,.5,1)` (a gentle
// overshoot) + `fill .5s ease`. We mirror that feel with motion/react tweens at premium durations.
// MotionConfig reducedMotion="user" (in the shell) zeroes every duration for prefers-reduced-motion,
// so these are honored automatically — no manual guard needed.
const SWAP_EASE: [number, number, number, number] = [0.34, 1.06, 0.5, 1]; // soft settle, barely-there overshoot
const CELL_SLIDE_MS = 420; // cell glides to its new column on a swap
const RING_MS = 320; // champion ring scale/opacity in
const CHEVRON_MS = 300; // pointer chevrons slide to target column
const CALLOUT_FADE_MS = 280; // callout chip cross-fade on change

// ── Theme palette ───────────────────────────────────────────────────────────────────────────────
// Two render skins share ONE geometry + ONE set of DOM stamps (data-cell-index / data-pointer-index /
// the <text> value), so every correctness invariant (index-monotone-x, cardinality, pointer-in-bounds,
// final-state, swap-conservation) is colour-blind and stays green across skins.
//   • "white" — the original brutalist-on-paper look (demo gallery + e2e baselines, UNCHANGED).
//   • "dark"  — the lectie lesson-surface look: cells on a dark canvas, accent-yellow for the placed /
//               sorted prefix, an under-row marker rail replacing the stacked i/j/min text labels.
//   • "bars"  — the 3Blue1Brown INTUITION skin: each element is a BAR whose HEIGHT ∝ its VALUE, so
//               "the smallest" is the SHORTEST bar — visual, not a digit you have to compare. Same
//               geometry contract (data-cell-index + value <text>), same stable-id slide on a swap;
//               champion bar glows, sorted-prefix bars lock to accent. Lives on the dark lectie skin.
type SeqVariant = "white" | "dark" | "bars";

const DARK = {
  bg: "#0e0e0e", // panel-dark-bg (DESIGN.md DARK surface)
  ink: "#000000",
  paper: "#ffffff",
  accent: "#fde047", // yellow-300 — THE accent
  accentDim: "#c9a227", // gate/dim yellow (lectie --gate)
  muted: "#9a9a9a",
  faint: "#6f6f6f",
  rule: "#333333",
  dimInk: "#cfcfcf", // bars skin: the value number on an unsorted (slate) bar — readable but quiet
} as const;

// ── Typed slots (mirrors ChartDistributionFamily / GraphTreeFamily §0.9G) ──────────────────────
// A SEQUENCE/ARRAY instance is an array of distinct integers being SELECTION-SORTED, plus a
// per-step pointer trace. Per outer i: scan the suffix [i..n-1] for the minimum (pointer j walks,
// `min` marks the running minimum index), then swap the min into position i. Left of i = the
// sorted prefix (recolored). The data_json carries the seed `values` AND every step's snapshot, so
// the family never INVENTS the trace — the oracle (selectionSortTrace.ts) re-runs the real
// algorithm from `values` and the harness asserts frames == oracle.
export type SeqArrayStep = {
  /** the array CONTENTS at this step (a permutation of `values` — a swap permutes, never edits). */
  array: number[];
  /** the sorted-prefix boundary: indices [0, sortedCount) are finalized (recolored). */
  sortedCount: number;
  /** pointer indices into `array`. i = current placement boundary, j = scan cursor, min = running
   *  minimum index of the suffix. All three must be in-bounds [0, array.length). */
  i: number;
  j: number;
  min: number;
  /** which beat this is — drives the callout anchor (the j cell while scanning, else the i cell). */
  phase: "scan" | "swap";
  /** RO callout for this beat (element-anchored, never a hardcoded color name). */
  callout: string;
};
export type SeqArrayData = {
  /** the seed array — distinct integers; the oracle re-runs selection sort from THIS. */
  values: number[];
  /** ordered per-step snapshots. */
  steps: SeqArrayStep[];
};

// What one rendered step exposes BOTH to the trace-match oracle (array + pointers + sortedCount)
// AND to the renderer / invariants (those same values become DOM via data-cell-index stamps).
export type SeqArrayState = {
  array: number[];
  sortedCount: number;
  i: number;
  j: number;
  min: number;
  phase: "scan" | "swap";
  callout: string;
};

/**
 * Hand-rolled validation guard (zod-free, mirrors parseChartData/parseGraphTreeData). On any
 * structural fault it throws a load error naming the instance id AND the offending field/index, so a
 * broken instance fails admission loud (INV-5.5) instead of rendering a garbled figure.
 * Cross-reference integrity (every pointer index in-bounds of the step's array length, every step
 * array the same length as `values`) is part of parsing.
 */
export function parseSeqArrayData(dataJson: string, instanceId: string): SeqArrayData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`seq-array instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;

  if (!Array.isArray(obj.values))
    throw new Error(`seq-array instance '${instanceId}': missing/invalid field 'values' (expected array)`);
  if (obj.values.length < 2)
    throw new Error(`seq-array instance '${instanceId}': field 'values' must have ≥2 elements`);
  const values: number[] = obj.values.map((v, i) => {
    if (typeof v !== "number" || !Number.isFinite(v) || !Number.isInteger(v))
      throw new Error(`seq-array instance '${instanceId}': values[${i}] must be a finite integer (got ${String(v)})`);
    return v;
  });
  const seenVals = new Set(values);
  if (seenVals.size !== values.length)
    throw new Error(`seq-array instance '${instanceId}': field 'values' must contain DISTINCT integers (got [${values.join(",")}])`);

  const n = values.length;

  if (!Array.isArray(obj.steps))
    throw new Error(`seq-array instance '${instanceId}': missing/invalid field 'steps' (expected array)`);
  if (obj.steps.length === 0)
    throw new Error(`seq-array instance '${instanceId}': field 'steps' must be non-empty`);

  const steps: SeqArrayStep[] = obj.steps.map((s, si) => {
    const step = s as Record<string, unknown>;
    if (!Array.isArray(step.array))
      throw new Error(`seq-array instance '${instanceId}': steps[${si}] missing/invalid field 'array' (expected array)`);
    if (step.array.length !== n)
      throw new Error(
        `seq-array instance '${instanceId}': steps[${si}].array length ${step.array.length} ≠ values length ${n} (cardinality must be preserved every step)`,
      );
    const array: number[] = step.array.map((v, vi) => {
      if (typeof v !== "number" || !Number.isFinite(v) || !Number.isInteger(v))
        throw new Error(`seq-array instance '${instanceId}': steps[${si}].array[${vi}] must be a finite integer (got ${String(v)})`);
      return v;
    });
    if (typeof step.sortedCount !== "number" || !Number.isInteger(step.sortedCount) || step.sortedCount < 0 || step.sortedCount > n)
      throw new Error(`seq-array instance '${instanceId}': steps[${si}].sortedCount must be an integer in [0,${n}] (got ${String(step.sortedCount)})`);
    const checkPtr = (name: "i" | "j" | "min"): number => {
      const p = step[name];
      if (typeof p !== "number" || !Number.isInteger(p) || p < 0 || p >= n)
        throw new Error(
          `seq-array instance '${instanceId}': steps[${si}].${name} (${String(p)}) is out of bounds — must be an integer index in [0,${n - 1}]`,
        );
      return p;
    };
    const i = checkPtr("i");
    const j = checkPtr("j");
    const min = checkPtr("min");
    if (step.phase !== "scan" && step.phase !== "swap")
      throw new Error(`seq-array instance '${instanceId}': steps[${si}].phase must be 'scan' | 'swap' (got ${String(step.phase)})`);
    if (typeof step.callout !== "string")
      throw new Error(`seq-array instance '${instanceId}': steps[${si}] missing field 'callout' (string)`);
    return { array, sortedCount: step.sortedCount, i, j, min, phase: step.phase, callout: step.callout };
  });

  return { values, steps };
}

/**
 * Build one Frame per step. Pure. The aria string carries the step callout (so screen readers and
 * the FigureReveal gate see the same teaching beat the figure shows). The state is a straight echo
 * of the step (array + pointers + sortedCount) — those are exactly the values the trace-harness
 * compares AND the renderer stamps to DOM.
 */
export function framesFromSeqArrayData(data: SeqArrayData): Frame<SeqArrayState>[] {
  return data.steps.map((step) => ({
    state: {
      array: [...step.array],
      sortedCount: step.sortedCount,
      i: step.i,
      j: step.j,
      min: step.min,
      phase: step.phase,
      callout: step.callout,
    },
    aria: step.callout,
  }));
}

// ── Stable element identity (the lectie "same node slides" trick) ──────────────────────────────
// A swap PERMUTES the array — the same physical token moves to a new column. To animate that move
// (instead of snapping a positional cell to a new value), every element needs a STABLE id carried
// across frames so the SAME motion node keys through the whole trace and just re-targets its x.
//
// Selection sort keeps the multiset of DISTINCT integers invariant (parse already enforces distinct
// `values`), so value↔id is a clean bijection: id := the element's ORIGINAL index in `values`. At any
// step, the element with stable id `s` sits at the position where `values[s]` currently lives. We
// precompute, per step, BOTH directions:
//   • idAtPos[pos]   = which stable id occupies column `pos` this step (for value/paint lookups)
//   • posOfId[id]    = which column stable id `id` currently sits in (the motion target x)
// data-cell-index stays = the element's CURRENT POSITION (semantic invariants read position); the
// stable id is ONLY the motion key, never a DOM stamp the invariants see.
export type SeqIdentityStep = {
  /** stable id (= seed index) occupying each current column. idAtPos.length === n. */
  idAtPos: number[];
  /** current column of each stable id. posOfId.length === n. */
  posOfId: number[];
};

export function computeIdentity(data: SeqArrayData): SeqIdentityStep[] {
  const n = data.values.length;
  // stable id := seed index; value → id from the seed (values are distinct, so this is a bijection).
  const idOfValue = new Map<number, number>();
  data.values.forEach((v, i) => idOfValue.set(v, i));
  return data.steps.map((step) => {
    const idAtPos = step.array.map((v) => {
      const id = idOfValue.get(v);
      // A faithful trace only ever permutes the seed; a value absent from the seed is a parse-class
      // fault that parseSeqArrayData/the trace harness already reject. Fall back to position so the
      // renderer never throws on an out-of-contract value (defensive — should be unreachable).
      return id ?? -1;
    });
    const posOfId = new Array<number>(n).fill(0);
    idAtPos.forEach((id, pos) => {
      if (id >= 0) posOfId[id] = pos;
    });
    return { idAtPos, posOfId };
  });
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────────────────────────
// Off-DOM 2D context ONLY for text extents (NOT a canvas figure; the figure is pure SVG, §5.6).
let measureCanvas: { ctx: CanvasRenderingContext2D | null } | null = null;
function measureLabelWidth(text: string, fontPx: number): number {
  if (typeof document !== "undefined") {
    if (!measureCanvas) {
      const c = document.createElement("canvas");
      measureCanvas = { ctx: c.getContext("2d") };
    }
    const ctx = measureCanvas.ctx;
    if (ctx) {
      ctx.font = `${fontPx}px ${FONT_FAMILY}`;
      return Math.ceil(ctx.measureText(text).width);
    }
  }
  // SSR / no-canvas fallback: monospace ≈ 0.6em per glyph.
  return Math.ceil(text.length * fontPx * 0.6);
}

const SVG_W = 480;
const SVG_H = 360;
// DARK skin uses a SHORTER viewBox so a wide-short figure (one array row + callout + rail) fills the
// frame with NO internal 4:3 void — the lectie lesson surface reads tight, not floating. The dark
// geometry below packs all content into [0, DARK_VBH]; the family forces this viewBox height on the
// shell whenever variant="dark" (the white skin + demo gallery keep the original 360, unchanged).
const DARK_VBH = 218;
// BARS skin uses a TALLER viewBox so the height∝value encoding has vertical room to read clearly (the
// tallest bar ≈ value 8, the shortest ≈ value 1, and the eye must see the gap at a glance). The bars
// geometry packs callout (top) → MULTI-LANE marker rail (labels stack onto separate lines so two never
// collide, see barsMarkers) → chevron row → bar field → value labels + baseline → SORTAT caption into
// [0, BARS_VBH]; the family forces this viewBox height on the shell when variant="bars". The viewBox
// grew (300 → 348) to give the rail its own multi-lane band BELOW the callout and ABOVE the bars, so
// rail labels never overlap each other, the callout, the bars, the value numbers, or the beat title.
const BARS_VBH = 348;

// Frame bands (disjoint by construction — each text class owns its own vertical band so two text
// boxes can NEVER overlap vertically; horizontal overlap is killed per-class by measurement):
//   [0, CALLOUT_BAND_H]   → the step callout (top gutter, anchored over the active cell's column)
//   [POINTER_Y_TOP, …]    → pointer markers (i / j / min) sit ABOVE the cell row
//   cell row centered      → the index-labeled cells (data-cell-index + transform stamps)
//   below the row          → the index labels (0..n-1)
const CALLOUT_BAND_H = 56; // top band reserved for a ≤3-line callout
const CELL_FONT = 16;
const CELL_PAD_X = 10;
const CELL_H = 44;
const CELL_GAP = 12;
const CELL_PAD_SIDE = 24; // left/right gutter so the widest cell can never clip
const INDEX_FONT = 10;
const POINTER_FONT = 11;
const POINTER_LABEL_GAP = 4;
// DARK skin cell rhythm (the lectie lesson-surface proportions — bigger, airier).
const CELL_FONT_DARK = 19;
const CELL_PAD_X_DARK = 14;
const CELL_H_DARK = 50;
const CELL_GAP_DARK = 14;

// ── BARS skin rhythm (3Blue1Brown intuition) ───────────────────────────────────────────────────
// The whole point: HEIGHT ∝ VALUE, so "smallest" is the SHORTEST bar (read at a glance, never a digit
// compare). A bar's height = BARS_MIN_H + (value - minValue) / (maxValue - minValue) * (BARS_MAX_H -
// BARS_MIN_H). minValue gets BARS_MIN_H (a visible stub, never zero) and maxValue gets BARS_MAX_H, so
// the encoding fills the available band with maximum contrast regardless of the actual value range.
const BARS_W = 44; // bar column width
const BARS_GAP = 18; // gap between bars
const BARS_MIN_H = 30; // the shortest bar (the minimum value) — a visible stub, never collapses to 0
const BARS_MAX_H = 168; // the tallest bar (the maximum value)
const BARS_VALUE_FONT = 17; // the value number under each bar
const BARS_BASELINE_Y = 270; // y of the shared baseline all bars stand on (bars grow UP from here)
const BARS_VALUE_GAP = 6; // gap below the baseline to the value number

// ── BARS marker-rail band (multi-lane, no-overlap-by-construction) ─────────────────────────────────
// The rail labels (început / ne uităm aici / cel mai mic …) are WIDER than the column pitch, so two
// labels over adjacent columns WOULD collide on a single line. We give the rail its own vertical band
// between the callout chip and the top of the tallest bar, and STACK colliding labels onto separate
// lanes (rows): a label is placed in the lowest lane whose existing labels it does not horizontally
// overlap. Same-lane labels are non-overlapping by the placement test; different-lane labels are at
// different y. ⇒ ZERO label-label overlap on every frame, by construction. Each label keeps its
// chevron tick at the TRUE column (so it still points at its bar) with a thin vertical connector up
// to the lane the label landed on.
const BARS_RAIL_CHEVRON_TIP_Y = 96; // chevron tip y (just ABOVE the tallest bar top = baseline - MAX_H)

// ── Callout geometry — VERBATIM no-clip-by-construction logic from ChartDistributionFamily ──────
// Measure → wrap to ≥2 lines → shrink to CALLOUT_MIN_FONT → clamp center-x by HALF the widest line
// so the FULL text stays inside [CALLOUT_PAD, SVG_W - CALLOUT_PAD] (§5.3 no-clip contract).
const CALLOUT_FONT = 11;
const CALLOUT_MIN_FONT = 8;
const CALLOUT_PAD = 8;
const CALLOUT_LINE_H = 13;
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2;

function layoutCallout(text: string): { lines: string[]; font: number; maxLineW: number } {
  for (let font = CALLOUT_FONT; font >= CALLOUT_MIN_FONT; font--) {
    const lines = wrapToWidth(text, CALLOUT_MAX_W, font);
    const maxLineW = Math.max(...lines.map((l) => measureLabelWidth(l, font)), 1);
    if (maxLineW <= CALLOUT_MAX_W || font === CALLOUT_MIN_FONT) {
      return { lines, font, maxLineW };
    }
  }
  const lines = wrapToWidth(text, CALLOUT_MAX_W, CALLOUT_MIN_FONT);
  return { lines, font: CALLOUT_MIN_FONT, maxLineW: Math.max(...lines.map((l) => measureLabelWidth(l, CALLOUT_MIN_FONT)), 1) };
}

function wrapToWidth(text: string, maxW: number, font: number): string[] {
  const words = text.split(/\s+/).filter(Boolean);
  if (words.length === 0) return [""];
  const lines: string[] = [];
  let cur = words[0];
  for (let i = 1; i < words.length; i++) {
    const cand = `${cur} ${words[i]}`;
    if (measureLabelWidth(cand, font) <= maxW) {
      cur = cand;
    } else {
      lines.push(cur);
      cur = words[i];
    }
  }
  lines.push(cur);
  return lines;
}

// ── Cell layout — MEASURE every value, reserve the WIDEST cell width for all cells (uniform grid),
// then center the row. The widest cell + the side gutter guarantee no cell clips the frame edge. ──
type CellLayout = {
  cellW: number;
  cellH: number; // cell-box height (variant-dependent)
  rowY: number; // cell-box top
  cellCY: number; // cell-box vertical center (the translate cy stamped on each <g>)
  cxOf: (index: number) => number; // cell-box center x (the translate cx)
  indexY: number; // baseline for the 0..n-1 index labels (below the row)
  pointerY: number; // baseline for the pointer markers (above the row)
  /** WHITE-skin content-driven viewBox height. The lowest-painted-pixel y of the figure (the index-
   *  label row baseline + a small bottom margin matching the top gutter), so the shell's hard frame
   *  HUGS the figure instead of the 360 default floating it in a 4:3 void. Dark/bars force their own
   *  short/tall viewBox; only the white skin reads this (the demo gallery + e2e drive the white skin). */
  viewBoxH: number;
};

function computeLayout(data: SeqArrayData, variant: SeqVariant = "white"): CellLayout {
  const n = data.values.length;
  const dark = variant === "dark";
  // Dark skin breathes: slightly wider, taller cells + a wider gap (the lectie cell rhythm).
  const cellFont = dark ? CELL_FONT_DARK : CELL_FONT;
  const cellH = dark ? CELL_H_DARK : CELL_H;
  const cellGap = dark ? CELL_GAP_DARK : CELL_GAP;
  const cellPadX = dark ? CELL_PAD_X_DARK : CELL_PAD_X;
  // widest rendered token across ALL steps (values + pointer glyphs feed the cell width).
  const widest = Math.max(
    ...data.steps.flatMap((s) => s.array.map((v) => measureLabelWidth(String(v), cellFont))),
    measureLabelWidth("0", cellFont),
  );
  const cellW = Math.max(widest + cellPadX * 2, dark ? 46 : 0);
  const totalW = n * cellW + (n - 1) * cellGap;
  // center the row; if it is wider than the usable area, anchor at the side gutter (degrades, never clips).
  const usable = SVG_W - CELL_PAD_SIDE * 2;
  const startX = totalW <= usable ? (SVG_W - totalW) / 2 : CELL_PAD_SIDE;
  const cxOf = (index: number) => startX + index * (cellW + cellGap) + cellW / 2;

  // Vertically center the whole composition (marker rail + row + index labels + sorted rule) in the
  // viewBox below the callout band, so the figure never floats in a dead void. The dark skin packs the
  // callout chip (top), a SHORT gap, the marker rail, the cell row, then index labels + the SORTAT rule
  // — into the SHORT DARK_VBH (240) viewBox so the used band fills the frame (no big internal void).
  // rowY=108 keeps the callout-to-rail gap tight (was ~110) and centers the full composition (callout
  // y≈6 → SORTAT caption y≈200) in [0, DARK_VBH=218] so the SVG paints edge-to-edge with no void.
  const rowY = dark ? 108 : CALLOUT_BAND_H + 60;
  const cellCY = rowY + cellH / 2;
  const indexY = rowY + cellH + (dark ? 22 : INDEX_FONT + 6);
  // WHITE-skin content-driven viewBox height: the LOWEST-painted-pixel y is the index-label row
  // (baseline `indexY`, an INDEX_FONT-tall digit). `indexY + 12` reserves the digit's descender + a
  // bottom margin matching the top callout gutter, so the hard frame HUGS the figure (no 360 floor /
  // ~50% bottom void). The pointer markers + callout live ABOVE the row, so the index row is the true
  // bottom. Dark/bars ignore this (they force DARK_VBH/BARS_VBH on the shell).
  const WHITE_BOTTOM_MARGIN = 12;
  const viewBoxH = indexY + WHITE_BOTTOM_MARGIN;
  return {
    cellW,
    cellH,
    rowY,
    cellCY,
    cxOf,
    // DARK band order under the row (disjoint, no overlap): cells bottom → SORTAT rule (hugs cells,
    // +8) → index labels (+22, clears the 3px rule) → SORTAT caption (indexY+20). Each owns its band.
    indexY,
    pointerY: dark ? rowY - 10 : rowY - POINTER_LABEL_GAP,
    viewBoxH,
  };
}

// ── BARS layout (the 3Blue1Brown height∝value field) ────────────────────────────────────────────
// One bar per array column. Height encodes value (smallest→shortest, largest→tallest); the value
// number sits BELOW the shared baseline. The marker rail (i/scanează/minim) sits ABOVE the bar field;
// the SORTAT rule + caption sit BELOW the value numbers. All bands disjoint, packed into BARS_VBH.
type BarsLayout = {
  barW: number;
  cxOf: (index: number) => number; // bar column center x
  baselineY: number; // shared baseline (bars grow UP from here)
  heightOf: (value: number) => number; // value → bar height (smallest = BARS_MIN_H, largest = BARS_MAX_H)
  topOf: (value: number) => number; // baselineY - heightOf(value) — the bar's top edge y
  valueLabelY: number; // baseline for the value numbers (below the baseline)
  pointerY: number; // baseline for the marker-rail ticks (above the tallest bar)
  ruleY: number; // SORTAT baseline rule y (below the value numbers)
  captionY: number; // SORTAT caption y (below the rule)
};

function computeBarsLayout(data: SeqArrayData): BarsLayout {
  const n = data.values.length;
  const minV = Math.min(...data.values);
  const maxV = Math.max(...data.values);
  const span = maxV - minV || 1; // guard equal-values (parse forbids duplicates, but be safe)
  const heightOf = (value: number) =>
    BARS_MIN_H + ((value - minV) / span) * (BARS_MAX_H - BARS_MIN_H);
  const baselineY = BARS_BASELINE_Y;
  const topOf = (value: number) => baselineY - heightOf(value);

  const barW = BARS_W;
  const totalW = n * barW + (n - 1) * BARS_GAP;
  const usable = SVG_W - CELL_PAD_SIDE * 2;
  const startX = totalW <= usable ? (SVG_W - totalW) / 2 : CELL_PAD_SIDE;
  const cxOf = (index: number) => startX + index * (barW + BARS_GAP) + barW / 2;

  return {
    barW,
    cxOf,
    baselineY,
    heightOf,
    topOf,
    // value numbers hang just below the baseline; SORTAT rule below them; caption below the rule.
    valueLabelY: baselineY + BARS_VALUE_GAP + BARS_VALUE_FONT,
    // the marker rail's chevron tip sits ABOVE the tallest bar top (baseline − BARS_MAX_H) with a gap;
    // its stacked label lanes live above that (see BARS_RAIL_* + barsMarkers). Fixed y (not value-derived)
    // so the chevron row never dips into a bar.
    pointerY: BARS_RAIL_CHEVRON_TIP_Y,
    ruleY: baselineY + BARS_VALUE_GAP + BARS_VALUE_FONT + 10,
    captionY: baselineY + BARS_VALUE_GAP + BARS_VALUE_FONT + 28,
  };
}

// ── WHITE skin (original brutalist-on-paper) ─────────────────────────────────────────────────────
// Cell fill. The sorted prefix (incl. the just-placed minimum at index i on a swap frame, since
// sortedCount = i+1 there) is INK. During SCAN the running-min cell + the scan cursor are ACCENT.
// On a SWAP frame we DO NOT accent the source cell (index === state.min): after the exchange that
// cell holds the swapped-OUT (larger) value, so accenting/labeling it "min" would teach that the
// larger value is the minimum (the defect). The destination (index i) already wins via the sorted
// branch above; the source is left a neutral unsorted cell and is instead marked by the swap glyph.
function fillForCell(state: SeqArrayState, index: number): string {
  if (index < state.sortedCount) return INK; // sorted prefix — finalized (incl. placed min on swap)
  if (state.phase === "scan" && (index === state.j || index === state.min)) return ACCENT;
  return "#fff";
}

function renderWhiteFrame(layout: CellLayout) {
  return (frame: Frame<SeqArrayState>): ReactNode => {
    const st = frame.state;
    const { cellW, rowY: _rowY, cellCY, cxOf, indexY, pointerY } = layout;

    // The callout anchors to the ACTIVE cell's column (the j cell while scanning, the i cell while
    // swapping) — element-anchored, never a detached footer, never a color name.
    const anchorIndex = st.phase === "scan" ? st.j : st.i;
    const anchorCx = cxOf(anchorIndex);

    return (
      <>
        {/* ── CELLS — index-labeled boxes in a row. Each <g> stamps data-cell-index + a translate
            transform so the invariants can read geometry (NOT the model). ── */}
        {st.array.map((value, index) => {
          const cx = cxOf(index);
          const isSorted = index < st.sortedCount;
          // Thick stroke = "in play" this beat. SCAN: the scan cursor + running-min cell. SWAP: the
          // two EXCHANGED cells (destination i + source min) so the swap reads as a pair — but the
          // source carries a "schimb" glyph below, never a "min" label (see the pointer block).
          const isActive =
            (st.phase === "scan" && (index === st.j || index === st.min)) ||
            (st.phase === "swap" && (index === st.i || index === st.min));
          return (
            <g key={index} data-cell-index={index} transform={`translate(${cx},${cellCY})`}>
              <rect
                x={-cellW / 2}
                y={-CELL_H / 2}
                width={cellW}
                height={CELL_H}
                fill={fillForCell(st, index)}
                stroke={INK}
                strokeWidth={isActive ? 2.5 : 1}
              />
              <text
                x={0}
                y={CELL_FONT / 2 - 2}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={CELL_FONT}
                fontWeight={700}
                fill={isSorted ? "#fff" : INK}
              >
                {value}
              </text>
            </g>
          );
        })}

        {/* ── INDEX LABELS (0..n-1) below the row — fixed small font, centered per cell. ── */}
        {st.array.map((_, index) => (
          <text
            key={`idx-${index}`}
            x={cxOf(index)}
            y={indexY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={INDEX_FONT}
            fill={INK}
            opacity={0.6}
          >
            {index}
          </text>
        ))}

        {/* ── POINTER MARKERS anchored ABOVE their cell (stacked sub-rows). See dark-skin notes for the
            scan/swap semantics; identical index logic, identical data-pointer stamps. ── */}
        {st.phase === "scan" ? (
          <>
            {renderWhitePointer("i", st.i, cxOf, pointerY - POINTER_FONT * 2 - 6, "i →")}
            {renderWhitePointer("j", st.j, cxOf, pointerY - POINTER_FONT - 3, "j")}
            {renderWhitePointer("min", st.min, cxOf, pointerY, "min")}
          </>
        ) : st.min === st.i ? (
          <>{renderWhitePointer("min", st.i, cxOf, pointerY, "minim aici")}</>
        ) : (
          <>
            {renderWhitePointer("min", st.i, cxOf, pointerY - POINTER_FONT - 3, "✓ minim")}
            {renderWhitePointer("swap", st.min, cxOf, pointerY, "↔ schimb")}
          </>
        )}

        {/* ── CALLOUT — anchored to the active cell's column, measured → wrapped → clamped (no-clip). ── */}
        {(() => {
          const { lines, font, maxLineW } = layoutCallout(st.callout);
          const half = maxLineW / 2;
          const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, anchorCx));
          const blockH = (lines.length - 1) * CALLOUT_LINE_H;
          const top = Math.min(14, CALLOUT_BAND_H - 4 - blockH);
          return (
            <text x={cx} y={top} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={font} fill={INK}>
              {lines.map((ln, i) => (
                <tspan key={i} x={cx} dy={i === 0 ? 0 : CALLOUT_LINE_H}>
                  {ln}
                </tspan>
              ))}
            </text>
          );
        })()}
      </>
    );

    function renderWhitePointer(
      kind: string,
      index: number,
      cxFn: (i: number) => number,
      y: number,
      glyph: string,
    ): ReactNode {
      const half = measureLabelWidth(glyph, POINTER_FONT) / 2;
      const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, cxFn(index)));
      return (
        <g key={`ptr-${kind}`} data-pointer={kind} data-pointer-index={index}>
          <text x={cx} y={y} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={POINTER_FONT} fontWeight={700} fill={INK}>
            {glyph}
          </text>
        </g>
      );
    }
  };
}

// ── DARK skin (the lectie lesson surface) ────────────────────────────────────────────────────────
// SAME geometry, SAME data-cell-index / data-pointer / <text>value stamps — only paint changes, so the
// colour-blind invariants stay green. The teaching reads at a glance from cell PAINT (no stacked text):
//   • sorted prefix (index < sortedCount)  → solid accent fill, black ink  = "locked in place"
//   • running-min cell (scan) / placed-min (swap-dest) → accent ring + soft glow = "the champion"
//   • scan cursor j (scan)                 → white fill + accent ring        = "currently comparing"
//   • everything else                      → white fill, faint hairline      = "still in play"
// The i / j / min pointers become a clean MARKER RAIL beneath the row: a chevron tick under the cell +
// one short label, instead of three stacked monospace words. A faint baseline rule under the sorted
// prefix gives the "wall of sorted" the eye can track growing left→right.
function darkCellPaint(st: SeqArrayState, index: number): { fill: string; ink: string; ring: string; ringW: number; glow: boolean } {
  const sorted = index < st.sortedCount;
  if (sorted) {
    // the just-placed minimum (swap dest = i, since sortedCount = i+1) gets a glow halo this beat.
    const justPlaced = st.phase === "swap" && index === st.i;
    return { fill: DARK.accent, ink: DARK.ink, ring: DARK.accent, ringW: justPlaced ? 3 : 0, glow: justPlaced };
  }
  if (st.phase === "scan") {
    if (index === st.min) return { fill: DARK.paper, ink: DARK.ink, ring: DARK.accent, ringW: 3, glow: true }; // champion
    if (index === st.j) return { fill: DARK.paper, ink: DARK.ink, ring: DARK.accent, ringW: 2, glow: false }; // comparing
  }
  if (st.phase === "swap" && st.min !== st.i && index === st.min) {
    // the source cell of a real swap — neutral ring, marked by the ↔ rail, never accented as "min".
    return { fill: DARK.paper, ink: DARK.ink, ring: DARK.muted, ringW: 2, glow: false };
  }
  return { fill: DARK.paper, ink: DARK.ink, ring: DARK.rule, ringW: 1, glow: false };
}

function renderDarkFrame(layout: CellLayout, identity: SeqIdentityStep[]) {
  return (frame: Frame<SeqArrayState>, idx: number): ReactNode => {
    const st = frame.state;
    const { cellW, cellH, cellCY, cxOf, indexY, pointerY } = layout;
    const halfW = cellW / 2;
    const halfH = cellH / 2;
    const n = st.array.length;
    // Stable-identity model for THIS step: which column each stable id currently sits in (= the
    // motion target x). Guard idx against the identity array so a transient render never throws.
    const idStep = identity[idx] ?? identity[identity.length - 1];
    const posOfId = idStep.posOfId;
    // value displayed by stable id `s` is the seed value at index s; current column = posOfId[s].
    const valueOfId = (s: number) => st.array[posOfId[s]];

    const anchorIndex = st.phase === "scan" ? st.j : st.i;
    const anchorCx = cxOf(anchorIndex);

    // sorted-prefix baseline rule (the growing "wall of sorted"). Drawn from the left edge of cell 0
    // to the right edge of the last sorted cell, hugging the cells; the "SORTAT" caption sits BELOW
    // the index labels so the bands never overlap. The rule's right end + the caption SLIDE as the
    // wall grows (motion), instead of jumping a column each round. Absent when nothing is sorted yet.
    const ruleY = cellCY + halfH + 8;
    const captionY = indexY + 20;
    const ruleX1 = cxOf(0) - halfW;
    const ruleX2 = st.sortedCount > 0 ? cxOf(st.sortedCount - 1) + halfW : ruleX1;
    const captionX = (ruleX1 + ruleX2) / 2;
    const sortedRule =
      st.sortedCount > 0 ? (
        <g>
          <motion.line
            x1={ruleX1}
            y1={ruleY}
            y2={ruleY}
            stroke={DARK.accent}
            strokeWidth={3}
            strokeLinecap="round"
            initial={false}
            animate={{ x2: ruleX2 }}
            transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
          />
          <motion.text
            y={captionY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={10}
            fontWeight={700}
            letterSpacing="0.18em"
            fill={DARK.accentDim}
            initial={false}
            animate={{ x: captionX }}
            transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
          >
            ✓ SORTAT
          </motion.text>
        </g>
      ) : null;

    return (
      <>
        {sortedRule}

        {/* ── CELLS ── one motion.g PER STABLE ID (id := seed index), keyed by id so the SAME node
            survives every frame and SLIDES (animate x/y) to its new column on a swap — the lectie
            "same token moves" feel. data-cell-index = the element's CURRENT POSITION (posOfId[id])
            so the position-reading invariants stay correct; the stable id is only the motion key.
            Paint + the value <text> are looked up by current position, so colour still tracks role. */}
        {Array.from({ length: n }, (_, id) => {
          const pos = posOfId[id];
          const cx = cxOf(pos);
          const value = valueOfId(id);
          const p = darkCellPaint(st, pos);
          return (
            <motion.g
              key={`cell-${id}`}
              data-cell-index={pos}
              initial={false}
              animate={{ x: cx, y: cellCY }}
              transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
            >
              {/* champion glow halo — scales+fades IN when this cell becomes the running/placed min */}
              <AnimatePresence>
                {p.glow && (
                  <motion.rect
                    key="glow"
                    x={-halfW - 4}
                    y={-halfH - 4}
                    width={cellW + 8}
                    height={cellH + 8}
                    rx={3}
                    fill="none"
                    stroke={DARK.accent}
                    strokeWidth={1}
                    initial={{ opacity: 0, scale: 0.82 }}
                    animate={{ opacity: 0.4, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.82 }}
                    transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
                    style={{ transformOrigin: "center", transformBox: "fill-box" }}
                  />
                )}
              </AnimatePresence>
              {/* the cell box — fill + ring colour cross-fade (mirrors lectie `fill .5s ease`); the
                  ring WIDTH animates so the champion ring grows in rather than snapping thick. */}
              <motion.rect
                x={-halfW}
                y={-halfH}
                width={cellW}
                height={cellH}
                rx={3}
                initial={false}
                animate={{
                  fill: p.fill,
                  stroke: p.ringW > 0 ? p.ring : DARK.rule,
                  strokeWidth: p.ringW > 0 ? p.ringW : 1,
                }}
                transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
              />
              <text
                x={0}
                y={CELL_FONT_DARK / 2 - 2}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={CELL_FONT_DARK}
                fontWeight={700}
                fill={p.ink}
              >
                {value}
              </text>
            </motion.g>
          );
        })}

        {/* ── INDEX LABELS (0..n-1) below the row — FIXED columns (positions don't move), so these
            are static text, never animated; only the cells above them slide. ── */}
        {st.array.map((_, index) => (
          <text
            key={`idx-${index}`}
            x={cxOf(index)}
            y={indexY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={11}
            fill={DARK.faint}
          >
            {index}
          </text>
        ))}

        {/* ── MARKER RAIL — the i/j/min/swap chevrons sit ABOVE the row, each a downward tick + a short
            label (replaces the old stacked monospace i/j/min text). Co-located pointers are MERGED into
            one marker so two chevrons never crowd the same column. Every marker keeps its own
            data-pointer / data-pointer-index <g> (INV-3 reads the index, not the merged glyph). The
            chevron groups SLIDE between columns (keyed by merged kind), so the pointers glide. ── */}
        {darkMarkers(st, cxOf, pointerY)}

        {/* ── CALLOUT CARD — a bordered chip in the top band, anchored over the active column, measured
            → wrapped → clamped so the full text always sits inside the frame (no-clip by construction).
            The whole chip CROSS-FADES when the callout text changes (keyed by the text): the outgoing
            sentence fades out while the incoming one fades in, so the teaching beat swaps softly
            instead of snapping. Anchored over the active column on the way in. ── */}
        {(() => {
          const { lines, font, maxLineW } = layoutCallout(st.callout);
          const padX = 9;
          const padY = 6;
          const boxW = maxLineW + padX * 2;
          const half = boxW / 2;
          const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, anchorCx));
          const lineH = CALLOUT_LINE_H;
          const textBlockH = (lines.length - 1) * lineH + font;
          const boxH = textBlockH + padY * 2;
          const boxTop = 6;
          const firstBaseline = boxTop + padY + font - 2;
          return (
            <AnimatePresence mode="popLayout" initial={false}>
              <motion.g
                key={`callout-${st.callout}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: CALLOUT_FADE_MS / 1000, ease: "easeInOut" }}
              >
                <rect
                  x={cx - half}
                  y={boxTop}
                  width={boxW}
                  height={boxH}
                  rx={3}
                  fill="#161616"
                  stroke={DARK.accent}
                  strokeWidth={1.5}
                />
                <text
                  x={cx}
                  y={firstBaseline}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={font}
                  fontWeight={700}
                  fill={DARK.accent}
                >
                  {lines.map((ln, i) => (
                    <tspan key={i} x={cx} dy={i === 0 ? 0 : lineH}>
                      {ln}
                    </tspan>
                  ))}
                </text>
              </motion.g>
            </AnimatePresence>
          );
        })()}
      </>
    );

    /** Build the marker rail for THIS frame. Each logical pointer (i/j/min, or min/swap) is an entry;
     *  entries over the SAME column are MERGED into one chevron + a single combined label, so the rail
     *  never crowds. Every logical pointer still emits its own zero-area <g data-pointer> stamp so
     *  INV-3 pointer-in-bounds sees exactly the i/j/min indices it expects. */
    function darkMarkers(
      state: SeqArrayState,
      cxFn: (i: number) => number,
      baseY: number,
    ): ReactNode {
      type P = { kind: string; index: number; label: string; color: string; prio: number };
      const ptrs: P[] = [];
      if (state.phase === "scan") {
        ptrs.push({ kind: "i", index: state.i, label: "i", color: DARK.muted, prio: 0 });
        ptrs.push({ kind: "j", index: state.j, label: "scanează", color: DARK.paper, prio: 1 });
        ptrs.push({ kind: "min", index: state.min, label: "minim", color: DARK.accent, prio: 2 });
      } else if (state.min === state.i) {
        ptrs.push({ kind: "min", index: state.i, label: "deja minim", color: DARK.accent, prio: 2 });
      } else {
        ptrs.push({ kind: "min", index: state.i, label: "✓ plasat", color: DARK.accent, prio: 2 });
        ptrs.push({ kind: "swap", index: state.min, label: "↔", color: DARK.muted, prio: 1 });
      }

      // group by column index
      const byCol = new Map<number, P[]>();
      for (const p of ptrs) {
        const g = byCol.get(p.index) ?? [];
        g.push(p);
        byCol.set(p.index, g);
      }

      const labelFont = 10;
      const tickH = 7;
      const yTickTip = baseY;
      const yTickTop = yTickTip - tickH;
      const yLabel = yTickTop - 4;

      const out: ReactNode[] = [];
      for (const [index, group] of byCol) {
        // pick the highest-priority entry to colour the chevron; merge labels (champion-first)
        const sorted = [...group].sort((a, b) => b.prio - a.prio);
        const chevColor = sorted[0].color;
        const mergedLabel = sorted.map((p) => p.label).join(" · ");
        const half = Math.max(measureLabelWidth(mergedLabel, labelFont) / 2, 6);
        const tickCx = cxFn(index);
        const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, tickCx));
        // Key the marker by its KIND-SET (not the column) so a marker carrying the SAME pointer roles
        // through consecutive frames is the SAME motion node and SLIDES to the new column. The tick
        // (at tickCx) and the clamped label (at cx) are drawn RELATIVE to a group translated by the
        // shared left datum (tickCx) and animate that translate; their internal offsets stay constant.
        const kindKey = sorted.map((p) => p.kind).join("+");
        const labelDx = cx - tickCx; // clamp offset, applied inside the group so x stays the tick column
        out.push(
          <motion.g
            key={`mk-${kindKey}`}
            initial={false}
            animate={{ x: tickCx }}
            transition={{ duration: CHEVRON_MS / 1000, ease: SWAP_EASE }}
          >
            <motion.path
              d={`M ${-5} ${yTickTop} L 0 ${yTickTip} L 5 ${yTickTop}`}
              fill="none"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              initial={false}
              animate={{ stroke: chevColor }}
              transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
            />
            <motion.text
              x={labelDx}
              y={yLabel}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={labelFont}
              fontWeight={700}
              letterSpacing="0.04em"
              initial={false}
              animate={{ fill: chevColor }}
              transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
            >
              {mergedLabel}
            </motion.text>
            {/* zero-area data-pointer stamps — one per logical pointer (INV-3 reads these indices). */}
            {group.map((p) => (
              <g key={`ptr-${p.kind}`} data-pointer={p.kind} data-pointer-index={p.index} />
            ))}
          </motion.g>,
        );
      }
      return <>{out}</>;
    }
  };
}

// ── BARS skin (the 3Blue1Brown intuition surface) ──────────────────────────────────────────────────
// HEIGHT ∝ VALUE is the whole teaching: the SHORTEST bar IS the smallest number, no digit-compare. SAME
// stable-id model as dark (a bar SLIDES to its new column on a swap), SAME data-cell-index + value
// <text> stamps so the family's correctness contract holds. Paint reads role from current position:
//   • sorted prefix (index < sortedCount)  → solid accent fill, black number, locked on the baseline
//   • running-min bar (scan) / placed-min (swap-dest) → accent fill + glow halo = "the champion / shortest"
//   • scan cursor j (scan)                 → bright fill + accent outline = "currently looking at this one"
//   • swap source bar (real swap)          → muted outline = "this one moves out"
//   • everything else (unsorted)           → soft slate fill = "still in play"
// The "smallest = shortest" reads instantly because the champion is BOTH glowing AND the lowest bar.
function barsPaint(st: SeqArrayState, index: number): { fill: string; numInk: string; outline: string; outlineW: number; glow: boolean } {
  const sorted = index < st.sortedCount;
  if (sorted) {
    const justPlaced = st.phase === "swap" && index === st.i;
    return { fill: DARK.accent, numInk: DARK.ink, outline: DARK.accent, outlineW: justPlaced ? 3 : 0, glow: justPlaced };
  }
  if (st.phase === "scan") {
    if (index === st.min) return { fill: DARK.accent, numInk: DARK.ink, outline: DARK.accent, outlineW: 3, glow: true }; // champion = shortest
    if (index === st.j) return { fill: "#3a3a3a", numInk: DARK.paper, outline: DARK.accent, outlineW: 2, glow: false }; // comparing
  }
  if (st.phase === "swap" && st.min !== st.i && index === st.min) {
    return { fill: "#2a2a2a", numInk: DARK.paper, outline: DARK.muted, outlineW: 2, glow: false }; // source moving out
  }
  return { fill: "#262626", numInk: DARK.dimInk, outline: DARK.rule, outlineW: 1, glow: false }; // still in play
}

function renderBarsFrame(layout: BarsLayout, identity: SeqIdentityStep[]) {
  return (frame: Frame<SeqArrayState>, idx: number): ReactNode => {
    const st = frame.state;
    const { barW, cxOf, baselineY, heightOf, topOf, valueLabelY, pointerY, ruleY, captionY } = layout;
    const halfW = barW / 2;
    const n = st.array.length;
    const idStep = identity[idx] ?? identity[identity.length - 1];
    const posOfId = idStep.posOfId;
    const valueOfId = (s: number) => st.array[posOfId[s]];

    const anchorIndex = st.phase === "scan" ? st.j : st.i;
    const anchorCx = cxOf(anchorIndex);

    // ── SORTAT baseline wall — grows left→right as the sorted prefix grows; the rule + caption SLIDE. ──
    const ruleX1 = cxOf(0) - halfW;
    const ruleX2 = st.sortedCount > 0 ? cxOf(st.sortedCount - 1) + halfW : ruleX1;
    const captionX = (ruleX1 + ruleX2) / 2;
    const sortedRule =
      st.sortedCount > 0 ? (
        <g>
          <motion.line
            x1={ruleX1}
            y1={ruleY}
            y2={ruleY}
            stroke={DARK.accent}
            strokeWidth={3}
            strokeLinecap="round"
            initial={false}
            animate={{ x2: ruleX2 }}
            transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
          />
          <motion.text
            y={captionY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={10}
            fontWeight={700}
            letterSpacing="0.18em"
            fill={DARK.accentDim}
            initial={false}
            animate={{ x: captionX }}
            transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
          >
            ✓ SORTAT
          </motion.text>
        </g>
      ) : null;

    return (
      <>
        {sortedRule}

        {/* ── shared baseline the bars stand on — a faint rule so "grows up from the floor" reads. ── */}
        <line x1={ruleX1 - 4} y1={baselineY} x2={cxOf(n - 1) + halfW + 4} y2={baselineY} stroke={DARK.rule} strokeWidth={1} />

        {/* ── BARS ── one motion.g PER STABLE ID (id := seed index), keyed by id so the SAME bar
            survives every frame and SLIDES (animate x) to its new column on a swap. data-cell-index
            = the bar's CURRENT POSITION (posOfId[id]) so the position-reading invariants stay correct.
            The bar's HEIGHT animates to heightOf(value) and its y to topOf(value); on a swap the bar
            both slides across AND keeps its own height (a value's bar is the same height wherever it
            lands), so the eye tracks "the short bar moved to the front". ── */}
        {Array.from({ length: n }, (_, id) => {
          const pos = posOfId[id];
          const cx = cxOf(pos);
          const value = valueOfId(id);
          const p = barsPaint(st, pos);
          const h = heightOf(value);
          const top = topOf(value);
          return (
            <motion.g
              key={`bar-${id}`}
              data-cell-index={pos}
              initial={false}
              animate={{ x: cx }}
              transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
            >
              {/* champion glow halo — a soft accent frame around the running/placed-min bar */}
              <AnimatePresence>
                {p.glow && (
                  <motion.rect
                    key="glow"
                    x={-halfW - 4}
                    y={top - 4}
                    width={barW + 8}
                    height={h + 8}
                    rx={4}
                    fill="none"
                    stroke={DARK.accent}
                    strokeWidth={1}
                    initial={{ opacity: 0, scale: 0.86 }}
                    animate={{ opacity: 0.5, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.86 }}
                    transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
                    style={{ transformOrigin: "center", transformBox: "fill-box" }}
                  />
                )}
              </AnimatePresence>
              {/* the bar itself — height + fill + outline cross-fade between frames */}
              <motion.rect
                x={-halfW}
                rx={3}
                initial={false}
                animate={{
                  y: top,
                  height: h,
                  width: barW,
                  fill: p.fill,
                  stroke: p.outlineW > 0 ? p.outline : DARK.rule,
                  strokeWidth: p.outlineW > 0 ? p.outlineW : 1,
                }}
                transition={{ duration: CELL_SLIDE_MS / 1000, ease: SWAP_EASE }}
              />
              {/* value number — sits BELOW the shared baseline, centered under the bar (data-cell-index
                  group holds it so the invariants' <text> value read still resolves to this bar). */}
              <text
                x={0}
                y={valueLabelY}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={BARS_VALUE_FONT}
                fontWeight={700}
                fill={p.glow ? DARK.accent : p.numInk}
              >
                {value}
              </text>
            </motion.g>
          );
        })}

        {/* ── MARKER RAIL — the "scanează / minim / i" labels above the bars (no index jargon glyphs;
            plain RO words). Co-located pointers merge so two ticks never crowd a column. Each logical
            pointer keeps its own zero-area data-pointer stamp (INV-3 reads the index). ── */}
        {barsMarkers(st, cxOf, pointerY)}

        {/* ── CALLOUT CARD — measured→wrapped→clamped bordered chip, anchored over the active column.
            No-clip by construction. ONE callout text node exists at a time: an INSTANT swap (a single
            keyed <motion.g>, NO AnimatePresence). The earlier popLayout cross-fade KEPT the exiting
            <text> mounted in the SVG, so fast-stepping piled old callouts on top of the new one
            (the cardinal overlap sin). Rendering only the current callout makes the swap atomic — the
            text content simply changes on the one node, never stacks. The whole-figure 350ms CSS
            fade-in (.algo-stepper-shell-svg *) still gives a soft beat-to-beat feel. ── */}
        {(() => {
          const { lines, font, maxLineW } = layoutCallout(st.callout);
          const padX = 9;
          const padY = 6;
          const boxW = maxLineW + padX * 2;
          const half = boxW / 2;
          const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, anchorCx));
          const lineH = CALLOUT_LINE_H;
          const textBlockH = (lines.length - 1) * lineH + font;
          const boxH = textBlockH + padY * 2;
          const boxTop = 4;
          const firstBaseline = boxTop + padY + font - 2;
          return (
            <g>
              <rect
                x={cx - half}
                y={boxTop}
                width={boxW}
                height={boxH}
                rx={3}
                fill="#161616"
                stroke={DARK.accent}
                strokeWidth={1.5}
              />
              <text
                x={cx}
                y={firstBaseline}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={font}
                fontWeight={700}
                fill={DARK.accent}
              >
                {lines.map((ln, i) => (
                  <tspan key={i} x={cx} dy={i === 0 ? 0 : lineH}>
                    {ln}
                  </tspan>
                ))}
              </text>
            </g>
          );
        })()}
      </>
    );

    /** Marker rail for the bars skin — PLAIN RO words (no i/j glyphs). Two-stage no-overlap layout:
     *  (1) MERGE pointers that share a column into ONE marker (so two chevrons never crowd a column),
     *  then (2) STACK labels onto separate LANES so two labels over DIFFERENT columns can never collide
     *  either — a label is placed in the lowest lane whose already-placed labels it doesn't horizontally
     *  overlap (with BARS_RAIL_LABEL_GAP slack). Same-lane labels are non-overlapping by that test;
     *  different-lane labels sit at different y. ⇒ ZERO label-label overlap on EVERY frame, by
     *  construction. The chevron tick stays at the TRUE column (still points at its bar); a thin vertical
     *  connector links the lane-stacked label down to its chevron. Every logical pointer still emits its
     *  own zero-area data-pointer stamp (INV-3 reads the index). The marker group slides between columns
     *  (keyed by kind-set), so the rail glides. */
    function barsMarkers(
      state: SeqArrayState,
      cxFn: (i: number) => number,
      chevTipY: number,
    ): ReactNode {
      type P = { kind: string; index: number; label: string; color: string; prio: number };
      const ptrs: P[] = [];
      if (state.phase === "scan") {
        ptrs.push({ kind: "i", index: state.i, label: "început", color: DARK.muted, prio: 0 });
        ptrs.push({ kind: "j", index: state.j, label: "ne uităm aici", color: DARK.paper, prio: 1 });
        ptrs.push({ kind: "min", index: state.min, label: "cel mai mic", color: DARK.accent, prio: 2 });
      } else if (state.min === state.i) {
        ptrs.push({ kind: "min", index: state.i, label: "deja cel mai mic", color: DARK.accent, prio: 2 });
      } else {
        ptrs.push({ kind: "min", index: state.i, label: "✓ mutat în față", color: DARK.accent, prio: 2 });
        ptrs.push({ kind: "swap", index: state.min, label: "pleacă", color: DARK.muted, prio: 1 });
      }

      // Merge pointers sharing a column → ONE chevron per column (highest-prio colour wins). NO rail
      // TEXT labels: the verbose RO labels were wider than the column pitch and collided across
      // columns/lanes (two failed lane-stacking attempts). The CALLOUT carries the words and the min bar
      // GLOWS, so the rail only needs a positional chevron. Chevrons are ~10px wide and columns are far
      // apart, so they can never overlap — overlap-free BY CONSTRUCTION. data-pointer stamps preserved (INV-3).
      const byCol = new Map<number, P[]>();
      for (const p of ptrs) {
        const g = byCol.get(p.index) ?? [];
        g.push(p);
        byCol.set(p.index, g);
      }
      const tickH = 7;
      const yTickTip = chevTipY;
      const yTickTop = yTickTip - tickH;
      const out: ReactNode[] = [];
      for (const [index, group] of byCol) {
        const sorted = [...group].sort((a, b) => b.prio - a.prio);
        const color = sorted[0].color;
        const kindKey = sorted.map((p) => p.kind).join("+");
        out.push(
          <motion.g
            key={`mk-${kindKey}`}
            initial={false}
            animate={{ x: cxFn(index) }}
            transition={{ duration: CHEVRON_MS / 1000, ease: SWAP_EASE }}
          >
            {/* a DOWNWARD chevron pointing at the bar at this column; colour encodes its role
                (accent = current minimum, paper = the bar we're checking, muted = boundary / leaving). */}
            <motion.path
              d={`M ${-5} ${yTickTop} L 0 ${yTickTip} L 5 ${yTickTop}`}
              fill="none"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              initial={false}
              animate={{ stroke: color }}
              transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
            />
            {sorted.map((p) => (
              <g key={`ptr-${p.kind}`} data-pointer={p.kind} data-pointer-index={p.index} />
            ))}
          </motion.g>,
        );
      }
      return <>{out}</>;
    }
  };
}

/** ADDITIVE props over the family contract — `variant` selects the render skin (default "white", the
 *  demo/e2e baseline) and `layout` lets the lesson surface drop the white box + side controls so the
 *  figure sits on a themed dark page. Both are omitted by the gallery + harness, so those are unchanged. */
export type SequenceArrayFamilyProps = FamilyRendererProps & {
  variant?: SeqVariant;
  layout?: ShellLayout;
};

export function SequenceArrayFamily({
  instanceId,
  dataJson,
  language,
  labels,
  onStep,
  variant = "white",
  layout: shellLayout,
}: SequenceArrayFamilyProps): ReactNode {
  const data = useMemo(() => parseSeqArrayData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromSeqArrayData(data), [data]);
  const layout = useMemo(() => computeLayout(data, variant), [data, variant]);
  // Bars layout (height∝value field) — only built for the bars skin; cheap, memoized.
  const barsLayout = useMemo(() => computeBarsLayout(data), [data]);
  // Per-step stable-identity model (dark + bars skins — the motion keying source). Cheap; memoized.
  const identity = useMemo(() => computeIdentity(data), [data]);
  const lastIdx = Math.max(0, frames.length - 1);
  // STABLE onStep (useCallback) — see GraphTreeFamily.tsx:288: a fresh arrow every render re-fires
  // the shell's onStep effect → setState → re-render → new arrow → infinite render loop.
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  const render =
    variant === "bars"
      ? renderBarsFrame(barsLayout, identity)
      : variant === "dark"
        ? renderDarkFrame(layout, identity)
        : renderWhiteFrame(layout);
  // Every skin now forces a CONTENT-DRIVEN viewBox height so the shell's hard frame HUGS the figure
  // (no 360 default void). DARK/BARS pack into their fixed short/tall boxes; WHITE uses its measured
  // content height (index-row baseline + margin) from computeLayout — previously the white skin floated
  // in the 360 4:3 box with a big bottom void. The spread keeps the family value last so it wins even
  // if a caller passed a viewBoxH.
  const effectiveLayout =
    variant === "bars"
      ? { ...shellLayout, viewBoxH: BARS_VBH }
      : variant === "dark"
        ? { ...shellLayout, viewBoxH: DARK_VBH }
        : { ...shellLayout, viewBoxH: layout.viewBoxH };
  return (
    <AlgoStepperShell<SeqArrayState>
      title={`Sequence/array · ${instanceId}`}
      desc={
        language === "ro"
          ? "Sortare prin selecție; scanează minimul și mută-l la stânga, pas cu pas."
          : "Selection sort; scan for the minimum and move it left, step by step."
      }
      frames={frames}
      renderFrame={render}
      testIdPrefix="seq-array"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
      layout={effectiveLayout}
    />
  );
}
