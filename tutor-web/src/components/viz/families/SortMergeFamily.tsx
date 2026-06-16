import { useCallback, useMemo, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "../AlgoStepperShell";
import { FONT_FAMILY } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

/**
 * Plan-V family `sort-merge` — the PREMIUM ANIMATED merge-sort figure. The legacy
 * `viz-pa-mergesort-001.yaml` (family_id graph-tree) is a STATIC divide-tree whose "merge" steps only
 * RELABEL node strings — the array never visibly sorts. THIS family fixes that defect: two sorted runs
 * physically CONVERGE into one sorted output row, element by element, with stable token identity (each
 * seed number keeps ONE motion node from the divide row, through every run band and output slot, to the
 * final sorted row), front-pointer chevrons on the run heads, and a growing ✓ INTERCLASAT baseline.
 *
 * It MIRRORS the seq-array dark skin's proven premium mechanisms (SequenceArrayFamily.tsx) — stable
 * id `<motion.g>` slide, `measureLabelWidth` no-clip callout chip, growing baseline rule, marker
 * chevrons, the #0e0e0e/#fde047 palette — but with merge's OWN geometry (three vertically-stacked
 * bands) and its OWN array-state oracle (arrayMergeTrace.ts). Dark by default; the family OWNS the
 * look (no per-lesson CSS).
 *
 * Contract parity with seq-array: one `<g data-cell-index="pos">` per token stamps the token's CURRENT
 * logical position so the colour-blind invariants (final-state / multiset / merge-prefix /
 * front-pointer-in-bounds / sorted-span-monotone) read truth from pixels; each front pointer emits a
 * zero-area `<g data-pointer data-pointer-index>`.
 */

// ── Motion grammar (lectie-grade) — mirrors SequenceArrayFamily.tsx:13-17 ──────────────────────────
const SWAP_EASE: [number, number, number, number] = [0.34, 1.06, 0.5, 1]; // soft settle, barely-there overshoot
const SLIDE_MS = 460; // a token glides between bands / slots
const RING_MS = 320; // ring / glow scale+opacity in
const CHEVRON_MS = 300; // front-pointer chevrons slide to a new column

// ── Theme palette (identical to the seq-array dark skin) ───────────────────────────────────────────
const DARK = {
  bg: "#0e0e0e",
  ink: "#000000",
  paper: "#ffffff",
  accent: "#fde047", // yellow-300 — THE accent
  accentDim: "#c9a227",
  muted: "#9a9a9a",
  faint: "#6f6f6f",
  rule: "#333333",
  leftTint: "#3a3a3a", // left-run resting fill
  rightTint: "#2a2a2a", // right-run resting fill (a hair darker so the two runs read as distinct)
  dimInk: "#cfcfcf",
} as const;

// ── Typed slots ────────────────────────────────────────────────────────────────────────────────────
export type MergeRun = { lo: number; hi: number };

export type MergeStep = {
  /** the FULL array contents this frame (a permutation of `values`). final step === sorted(values). */
  array: number[];
  /** disjoint half-open spans being merged this frame, left→right. */
  runs: MergeRun[];
  /** front pointer (index into `array`) on each active run; runs[k] consumes from frontOf[k]. */
  frontOf: number[];
  /** output band: how many output slots are filled this frame (the locked sorted prefix of the merge). */
  outFilled: number;
  /** which array index was JUST taken into the output this frame (glow / slide highlight); -1 otherwise. */
  tookFrom: number;
  /** the array span finalized-sorted at the END of this frame ([0,0) when nothing finalized this frame). */
  sortedSpan: MergeRun;
  /** the phase that drives layout + callout anchoring. */
  phase: "divide" | "compare" | "take" | "drain" | "final";
  /** RO callout, element-anchored, no hardcoded colour names. */
  callout: string;
};

export type MergeData = {
  /** the seed array — distinct integers; the oracle re-runs merge sort from THIS. */
  values: number[];
  steps: MergeStep[];
};

// What one rendered step exposes BOTH to the trace-match oracle AND to the renderer / invariants.
export type MergeState = {
  array: number[];
  runs: MergeRun[];
  frontOf: number[];
  outFilled: number;
  tookFrom: number;
  sortedSpan: MergeRun;
  phase: "divide" | "compare" | "take" | "drain" | "final";
  callout: string;
};

const PHASES = new Set(["divide", "compare", "take", "drain", "final"]);

/**
 * Hand-rolled validation guard (zod-free, mirrors parseSeqArrayData). On any structural fault it throws
 * a load error naming the instance id AND the offending field/index, so a broken instance fails
 * admission loud (INV-5.5) instead of rendering a garbled figure.
 */
export function parseMergeData(dataJson: string, instanceId: string): MergeData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`sort-merge instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;

  if (!Array.isArray(obj.values))
    throw new Error(`sort-merge instance '${instanceId}': missing/invalid field 'values' (expected array)`);
  if (obj.values.length < 2)
    throw new Error(`sort-merge instance '${instanceId}': field 'values' must have ≥2 elements`);
  const values: number[] = obj.values.map((v, i) => {
    if (typeof v !== "number" || !Number.isFinite(v) || !Number.isInteger(v))
      throw new Error(`sort-merge instance '${instanceId}': values[${i}] must be a finite integer (got ${String(v)})`);
    return v;
  });
  if (new Set(values).size !== values.length)
    throw new Error(`sort-merge instance '${instanceId}': field 'values' must contain DISTINCT integers (got [${values.join(",")}])`);
  const n = values.length;

  if (!Array.isArray(obj.steps))
    throw new Error(`sort-merge instance '${instanceId}': missing/invalid field 'steps' (expected array)`);
  if (obj.steps.length === 0)
    throw new Error(`sort-merge instance '${instanceId}': field 'steps' must be non-empty`);

  const checkSpan = (s: unknown, where: string): MergeRun => {
    const r = s as Record<string, unknown>;
    if (typeof r.lo !== "number" || !Number.isInteger(r.lo) || r.lo < 0 || r.lo > n)
      throw new Error(`sort-merge instance '${instanceId}': ${where}.lo must be an integer in [0,${n}] (got ${String(r.lo)})`);
    if (typeof r.hi !== "number" || !Number.isInteger(r.hi) || r.hi < r.lo || r.hi > n)
      throw new Error(`sort-merge instance '${instanceId}': ${where}.hi must be an integer in [${r.lo},${n}] (got ${String(r.hi)})`);
    return { lo: r.lo, hi: r.hi };
  };

  const steps: MergeStep[] = obj.steps.map((s, si) => {
    const step = s as Record<string, unknown>;
    if (!Array.isArray(step.array))
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}] missing/invalid field 'array' (expected array)`);
    if (step.array.length !== n)
      throw new Error(
        `sort-merge instance '${instanceId}': steps[${si}].array length ${step.array.length} ≠ values length ${n} (cardinality must be preserved every step)`,
      );
    const array: number[] = step.array.map((v, vi) => {
      if (typeof v !== "number" || !Number.isFinite(v) || !Number.isInteger(v))
        throw new Error(`sort-merge instance '${instanceId}': steps[${si}].array[${vi}] must be a finite integer (got ${String(v)})`);
      return v;
    });
    if (!Array.isArray(step.runs))
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}] missing/invalid field 'runs' (expected array)`);
    const runs: MergeRun[] = step.runs.map((r, ri) => checkSpan(r, `steps[${si}].runs[${ri}]`));
    if (!Array.isArray(step.frontOf))
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}] missing/invalid field 'frontOf' (expected array)`);
    const frontOf: number[] = step.frontOf.map((p, pi) => {
      if (typeof p !== "number" || !Number.isInteger(p) || p < 0 || p >= n)
        throw new Error(`sort-merge instance '${instanceId}': steps[${si}].frontOf[${pi}] (${String(p)}) out of bounds — must be an index in [0,${n - 1}]`);
      return p;
    });
    if (frontOf.length !== runs.length)
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}] frontOf length ${frontOf.length} ≠ runs length ${runs.length}`);
    // each front must lie within its run
    runs.forEach((r, ri) => {
      if (frontOf[ri] < r.lo || frontOf[ri] >= r.hi)
        throw new Error(`sort-merge instance '${instanceId}': steps[${si}].frontOf[${ri}]=${frontOf[ri]} is not inside run [${r.lo},${r.hi})`);
    });
    if (typeof step.outFilled !== "number" || !Number.isInteger(step.outFilled) || step.outFilled < 0 || step.outFilled > n)
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}].outFilled must be an integer in [0,${n}] (got ${String(step.outFilled)})`);
    if (typeof step.tookFrom !== "number" || !Number.isInteger(step.tookFrom) || step.tookFrom < -1 || step.tookFrom >= n)
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}].tookFrom must be -1 or an index in [0,${n - 1}] (got ${String(step.tookFrom)})`);
    const sortedSpan = checkSpan(step.sortedSpan, `steps[${si}].sortedSpan`);
    if (typeof step.phase !== "string" || !PHASES.has(step.phase))
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}].phase must be one of divide|compare|take|drain|final (got ${String(step.phase)})`);
    if (typeof step.callout !== "string")
      throw new Error(`sort-merge instance '${instanceId}': steps[${si}] missing field 'callout' (string)`);
    return {
      array,
      runs,
      frontOf,
      outFilled: step.outFilled,
      tookFrom: step.tookFrom,
      sortedSpan,
      phase: step.phase as MergeStep["phase"],
      callout: step.callout,
    };
  });

  // last step must be the final sorted state (admission-time guarantee of the figure's whole point).
  const last = steps[steps.length - 1];
  if (last.phase !== "final")
    throw new Error(`sort-merge instance '${instanceId}': the LAST step must have phase 'final' (got '${last.phase}')`);
  const sortedSeed = [...values].sort((x, y) => x - y);
  if (JSON.stringify(last.array) !== JSON.stringify(sortedSeed))
    throw new Error(
      `sort-merge instance '${instanceId}': the final step's array [${last.array.join(",")}] is not the sorted seed [${sortedSeed.join(",")}]`,
    );

  return { values, steps };
}

/** Build one Frame per step. Pure; the aria carries the callout. State is a straight echo of the step. */
export function framesFromMergeData(data: MergeData): Frame<MergeState>[] {
  return data.steps.map((step) => ({
    state: {
      array: [...step.array],
      runs: step.runs.map((r) => ({ ...r })),
      frontOf: [...step.frontOf],
      outFilled: step.outFilled,
      tookFrom: step.tookFrom,
      sortedSpan: { ...step.sortedSpan },
      phase: step.phase,
      callout: step.callout,
    },
    aria: step.callout,
  }));
}

// ── Label measurement (reserve space ⇒ no-clip by construction) — verbatim from SequenceArrayFamily ──
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
  return Math.ceil(text.length * fontPx * 0.6);
}

// ── Geometry — three vertically-stacked bands packed into a SHORT viewBox (no internal void) ─────────
const SVG_W = 480;
// Forced viewBox height. SHORTENED (was 336) and the merge bands re-packed below, mirroring the
// seq-array dark skin's "short box ⇒ one composition fills it, no 4:3 void" trick. At 336 the SINGLE-row
// frames (initial / final / output-only) floated a small mid-canvas row with ~40% dead height; a shorter
// box + scaled single row + index strip / ✓ rule beneath it now fill every frame edge-to-edge. The merge
// frames stay packed: callout(top) → run band → output band → ✓ rule+caption → pending strip → bottom.
const MERGE_VBH = 288;

const CELL_FONT = 19;
const CELL_H = 46;
const CELL_W_MIN = 40;
const CELL_PAD_X = 13;
const CELL_GAP = 12;
const PAD_SIDE = 22;

// Four DISJOINT vertical bands (no two text/cell classes ever share a y):
//   [callout]                            top chip
//   RUN_BAND_CY     — two sorted runs converging (left block + gap + right block, centered as a unit)
//   OUTPUT_BAND_CY  — the output building left→right (centered as a MERGE-SPAN-width block under the runs)
//   PENDING_STRIP_CY— the elements OUTSIDE the active merge, dimmed + smaller ("waiting", never competes)
// On divide/final there is no merge, so the whole array sits as ONE centered row at ROW_CY.
// Merge-frame bands packed into [0, MERGE_VBH=288] (was spread over 336): callout ≈[6,58], run-band label
// ≈72 + run cells centered at 112 (≈[89,135]), output cells centered at 190 (≈[167,213]), ✓ rule ≈222 +
// caption ≈238, pending strip at 262 (scaled, ≈[248,276]) → bottom. The stack fills the short box with no
// internal void, same as the single-row frames below.
const RUN_BAND_CY = 112;
const OUTPUT_BAND_CY = 190;
const PENDING_STRIP_CY = 262;
// GENEROUS gap between the left run and the right run so the two sorted lists read as TWO SEPARATE
// clusters, never one row (the developer's core complaint: "all the numbers are together"). Each
// cluster also gets its own "LISTĂ SORTATĂ" label centered beneath it (in the [run-cells, output]
// void) so a learner SEES two distinct sorted lists. Widened 34 → 60 (max run unit at 6 cells =
// 6·40 + 5·12 + 60 = 360 ≤ usable 436, no clip).
const RUN_GAP = 60; // horizontal gap between the left run and the right run in the run band
// Per-cluster "LISTĂ SORTATĂ" label baseline — in the void between the run cells (bottom ≈135) and the
// output band cells (top ≈167). Sits clear of both.
const RUN_LABEL_DY = 23; // below RUN_BAND_CY's cell bottom (halfH) → label baseline
// The FRONT (head) of each run lifts UP this many px so it protrudes above its cluster's body cells —
// the "candidate sticking out" read. Modest so it never reaches the callout/band-label zone above.
const HEAD_LIFT = 7;
const HEAD_LIFT_BARS = 10; // bars: lift the head BAR's whole group up so its top clears its neighbours

// ── Single-row frames (divide / final / output-only) — fill the SAME short box, no void ────────────
// A merge frame stacks callout→runs→output→rule→pending across the whole MERGE_VBH. A divide/final frame
// has only ONE row, so a 1× row parked at box-center left ~40% of the height dead above+below (the
// measured defect). Fix (layout-only, the seq-array recipe): the single row is SCALED UP (cells as big as
// the seq-array dark skin's, not a small mid-canvas row) AND a ✓ SORTAT rule + caption sits beneath it,
// so the composition (callout top → big row → rule+caption) spans the short box top-to-bottom and fills
// edge-to-edge like every merge frame. ROW_CY centers the big row in the content area below the callout.
const ROW_CY = 156; // divide/final single-row center — the big row + index strip / ✓ rule fill the box top-to-bottom
const SINGLE_ROW_SCALE = 1.5; // blow the single row up to seq-array's airier dark rhythm so it dominates the frame (width-clamped at render)
// DIVIDE split — the unscaled horizontal gap opened at every run-group boundary so the array visibly
// SPLITS into its `runs` groups (1 row → 2 halves → 4 → 6 singletons across the four divide frames).
// Reserved into the row's width budget so the gapped row stays width-clamped (no clip); the scale falls
// as more groups appear, so even the 6-singleton frame fits the box. A light bracket underlines each group.
const DIVIDE_GROUP_GAP = 26; // boxes-skin inter-group gap (unscaled SVG px)
const DIVIDE_GROUP_GAP_BARS = 24; // bars-skin inter-group gap (unscaled SVG px)

// Pending strip is drawn smaller so it reads as background context, not a third equal row.
const PENDING_SCALE = 0.62;

// ── BARS variant geometry (additive — height∝value skin; the boxes variant above is byte-unchanged) ──
// Each token is a BAR whose HEIGHT encodes its value (smallest → a short stub, largest → tall), reusing
// the seq-array bars math (heightOf below). The two ACTIVE sorted lists + the output dominate; the
// out-of-active-merge "pending" elements are demoted to a FAINT THIN CONTEXT STRIP (small fixed-height
// ticks, NOT value-scaled equal bars), so the figure no longer screams "everything at once" and the
// whole stack packs into a MUCH SHORTER viewBox. The bands, top→bottom:
//   callout chip → per-cluster "LISTĂ SORTATĂ" labels above each run → run band (two clusters
//   converging, GENEROUS BARS_RUN_GAP, head bar lifted) → ✓ INTERCLASAT rule + caption → output band →
//   faint thin pending context strip.
// VBH SHRUNK 518 → 392 (≈24% shorter): a tall-narrow SVG in a height-limited column scaled DOWN tiny;
// the shorter box lets the bars FILL the column at a readable size at 1536×730. The big win is the
// pending demotion — a full-height value-scaled pending row forced PENDING_BASE to 496; a thin context
// strip needs only ~10px so the whole stack lifts. Packing (measured live, both viewports):
//   chip[6,44] → cluster labels≈y58 (top≈50) → run-head chevron-top≈68 → run bars[ (184−88−10)=86 ,184]
//   → run-value≤205 → ✓caption-top≈231 (ruleY=OUTPUT_BAND_BASE−MAX_H−14=246) → output-bars[260,348] →
//   output-value≤369 → faint pending strip≈[378,388] ≤ VBH 392.
const MERGE_BARS_VBH = 392;
const BARS_W = 38; // bar column width (a hair narrower than seq-array's 44 so 6 run bars + gap fit at SVG_W=480)
const BARS_GAP = 12; // gap between bars within a run/output block
// GENEROUS gap between the left-run block and the right-run block so the two sorted lists read as TWO
// SEPARATE clusters (the core complaint). Widened 30 → 56; max run unit (6 bars) =
// 6·38 + 5·12 + 56 = 344 ≤ usable 436 (no clip).
const BARS_RUN_GAP = 56;
const BARS_MIN_H = 22; // shortest bar (the minimum value) — a visible stub, never collapses to 0
const BARS_MAX_H = 88; // tallest bar (the maximum value) — trimmed from 116 so the shorter box stays no-clip
const BARS_VALUE_FONT = 16; // the value number under each bar
const BARS_VALUE_GAP = 5; // gap below a band baseline to its value number row
// Faint thin pending context strip: a row of small FIXED-height ticks (NOT value bars), so the
// out-of-active-merge elements read as background context that does not compete with the two lists.
const BARS_PENDING_TICK_H = 9; // fixed pending-tick height (px) — context, not data
const BARS_PENDING_W = 14; // pending tick width (narrow)
const BARS_PENDING_GAP = 7; // gap between pending ticks
// Band baselines (the y each band's bars STAND on; bars grow UP from here). Disjoint slabs.
// Run bars stand here. Run-head chevron tip rides HEAD_LIFT_BARS above the head bar top; with the
// trimmed MAX_H=88 and lift=10, the tallest head's chevron top ≈ 184−88−10−16 = 70, below the cluster
// label row (~62). Run value row ≈ 184 + 5 + 16 = 205.
const RUN_BAND_BASE = 184;
// Output bars stand here. ✓ caption top ≈ OUTPUT_BAND_BASE − MAX_H − 21 ≈ 239 clears the cluster-label /
// run-value rows above. Output bar top (max) ≈ 348−88 = 260 stays below the rule (ruleY=348−102=246).
// Output value row hangs to ≈ 348+21 = 369.
const OUTPUT_BAND_BASE = 348;
// Faint pending strip baseline — just below the output value row; the thin ticks occupy
// [PENDING_BASE − BARS_PENDING_TICK_H, PENDING_BASE] = [378,387] ≤ VBH 392 (no clip).
const PENDING_BASE = 387;

// Callout chip geometry (measure → wrap → clamp; no-clip by construction).
const CALLOUT_FONT = 12;
const CALLOUT_MIN_FONT = 9;
const CALLOUT_PAD = 8;
const CALLOUT_LINE_H = 14;
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2;

function layoutCallout(text: string): { lines: string[]; font: number; maxLineW: number } {
  for (let font = CALLOUT_FONT; font >= CALLOUT_MIN_FONT; font--) {
    const lines = wrapToWidth(text, CALLOUT_MAX_W, font);
    const maxLineW = Math.max(...lines.map((l) => measureLabelWidth(l, font)), 1);
    if (maxLineW <= CALLOUT_MAX_W || font === CALLOUT_MIN_FONT) return { lines, font, maxLineW };
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
    if (measureLabelWidth(cand, font) <= maxW) cur = cand;
    else {
      lines.push(cur);
      cur = words[i];
    }
  }
  lines.push(cur);
  return lines;
}

// ── Per-step token placement ───────────────────────────────────────────────────────────────────────
// Each token has a STABLE id = its seed index (values are distinct, so value↔id is a bijection). Per
// frame we compute, for every id, its target band + slot + role, so the renderer can SLIDE the SAME
// motion node between bands. Bands:
//   • "row"     — one centered row (divide + final frames): all n tokens, no merge in progress.
//   • "left"    — the left sorted run in the run band (run-relative slot 0..leftLen-1).
//   • "right"   — the right sorted run in the run band.
//   • "output"  — the output band, building left→right (slot 0..outFilled-1), locked accent.
//   • "rest"    — a token OUTSIDE the active merge span: it rests dimmed at its array position on the
//                 output-band baseline (so the eye still sees the whole array; only the active span
//                 lifts into the run/output choreography).
type TokenPlace =
  // `group` = which divide-run group this row token belongs to (0-based, left→right). On a divide
  // frame it drives a visible inter-group GAP so the array SPLITS as the runs subdivide (1→2→4→6
  // groups across the divide frames); on a final frame every token is group 0 (one solid row).
  | { band: "row"; slot: number; group: number }
  | { band: "left"; slot: number; runLen: number; leftLen: number; rightLen: number }
  | { band: "right"; slot: number; runLen: number; leftLen: number; rightLen: number }
  | { band: "output"; slot: number }
  | { band: "rest"; pos: number };

type FramePlacement = {
  placeOf: Map<number, TokenPlace>; // stable id → placement
  roleOf: Map<number, TokenRole>; // stable id → paint role
  mergeLo: number; // active merge span (–1 on divide/final)
  mergeHi: number;
  leftLen: number;
  rightLen: number;
  // DIVIDE-frame run grouping (read by the renderer to draw the split). Empty/`[]` off a divide frame.
  // `divideRuns` = the runs as the frame carries them (disjoint half-open spans covering [0,n)); the
  // renderer lays each group with a gap between groups + a per-group bracket so successive divide frames
  // visibly differ (1 group → 2 → 4 → 6 singletons). `divideGroupOf` maps a row slot → its group index.
  divideRuns: MergeRun[];
  divideGroupOf: number[]; // length n on a divide frame, [] otherwise (index by row slot = array pos)
};

type TokenRole =
  | "row" // neutral row token (divide)
  | "rowSorted" // final row (all accent)
  | "leftHead" // left run's front (candidate)
  | "leftBody"
  | "rightHead"
  | "rightBody"
  | "output" // committed output (accent locked)
  | "outputJustTaken" // the token just dropped into output this frame (glow)
  | "rest"; // dimmed, outside the active merge span

/** The value→id bijection (id := seed index). */
function buildIdOfValue(values: number[]): Map<number, number> {
  const m = new Map<number, number>();
  values.forEach((v, i) => m.set(v, i));
  return m;
}

/** Recover the active merge span [mergeLo, mergeHi) for a merge frame. */
function activeSpan(st: MergeState): { lo: number; hi: number } {
  if (st.runs.length > 0) {
    const lo = st.runs[0].lo - st.outFilled;
    const hi = st.runs[st.runs.length - 1].hi;
    return { lo, hi };
  }
  // runs empty (a drain just completed): the whole span is the finalized sortedSpan.
  return { lo: st.sortedSpan.lo, hi: st.sortedSpan.hi };
}

/**
 * Compute every token's band/slot/role for one frame. The renderer turns these into (x,y) targets.
 * For a merge frame we need to know the FULL left/right run lengths (not just the remaining tails) so
 * the run band keeps a stable left-block / right-block geometry while heads are consumed. We recover
 * them from the array contents of the active span at this frame: the committed output prefix +
 * remaining left tail = the left run; the rest = the right run. But the simplest faithful source is:
 * leftLen = (mid - lo), rightLen = (hi - mid) where mid splits the span the way the oracle did. We
 * derive mid from the run structure: when both runs are present, the original split is recoverable as
 * leftConsumed = outFilled - rightConsumed; rather than reconstruct, we track run *capacity* from the
 * span + the count of output already taken from each side is not in the step — so instead we render the
 * run band as the REMAINING tails only (runs[]) plus the output band, which is exact and needs no
 * reconstruction. leftLen/rightLen below are the REMAINING tail lengths (what's still in the run band).
 */
function computePlacement(values: number[], st: MergeState, idOfValue: Map<number, number>): FramePlacement {
  const placeOf = new Map<number, TokenPlace>();
  const roleOf = new Map<number, TokenRole>();
  const idAt = (pos: number) => idOfValue.get(st.array[pos])!;

  if (st.phase === "divide" || st.phase === "final") {
    const sortedRow = st.phase === "final";
    // Map each array position to its DIVIDE run-group index. On a final frame there is no split, so
    // every token is group 0 (one solid sorted row). On a divide frame the frame's `runs` ARE the
    // groups (disjoint half-open spans the parse guard already validated); a position's group = the
    // index of the run that contains it. This is what makes the four divide frames differ: as `runs`
    // subdivides 1→2→4→6, the group count rises and the renderer opens a gap at each group boundary.
    const divideRuns: MergeRun[] = sortedRow ? [] : st.runs.map((r) => ({ ...r }));
    const divideGroupOf: number[] = new Array(values.length).fill(0);
    if (!sortedRow) {
      for (let pos = 0; pos < values.length; pos++) {
        let g = divideRuns.findIndex((r) => pos >= r.lo && pos < r.hi);
        if (g < 0) g = 0; // defensive — runs always cover [0,n) on a divide frame
        divideGroupOf[pos] = g;
      }
    }
    for (let pos = 0; pos < values.length; pos++) {
      const id = idAt(pos);
      placeOf.set(id, { band: "row", slot: pos, group: divideGroupOf[pos] });
      roleOf.set(id, sortedRow ? "rowSorted" : "row");
    }
    return { placeOf, roleOf, mergeLo: -1, mergeHi: -1, leftLen: 0, rightLen: 0, divideRuns, divideGroupOf };
  }

  // ── merge frame (compare / take / drain) ──
  const { lo: mergeLo, hi: mergeHi } = activeSpan(st);
  const leftRun = st.runs[0]; // may be undefined (left fully drained)
  const rightRun = st.runs.length > 1 ? st.runs[1] : st.runs.length === 1 ? undefined : undefined;
  // Determine which of the (≤2) runs is the LEFT vs RIGHT block by position: the lower-lo run is left.
  let leftSpan: MergeRun | undefined;
  let rightSpan: MergeRun | undefined;
  if (st.runs.length === 2) {
    leftSpan = st.runs[0];
    rightSpan = st.runs[1];
  } else if (st.runs.length === 1) {
    // one run remains — is it the left tail or the right tail? It is left iff its lo == mergeLo + outFilled
    const r = st.runs[0];
    if (r.lo === mergeLo + st.outFilled && rightRunWasConsumed(st)) {
      leftSpan = r; // remaining left tail
    } else {
      // it's the right tail draining
      rightSpan = r;
    }
  }
  void leftRun;
  void rightRun;
  const leftLen = leftSpan ? leftSpan.hi - leftSpan.lo : 0;
  const rightLen = rightSpan ? rightSpan.hi - rightSpan.lo : 0;

  // output tokens: positions [mergeLo, mergeLo + outFilled)
  for (let k = 0; k < st.outFilled; k++) {
    const pos = mergeLo + k;
    const id = idAt(pos);
    placeOf.set(id, { band: "output", slot: k });
    const isJustTaken = st.tookFrom === pos || (st.tookFrom >= 0 && pos === mergeLo + st.outFilled - 1);
    roleOf.set(id, isJustTaken ? "outputJustTaken" : "output");
  }
  // left run tokens
  if (leftSpan) {
    for (let pos = leftSpan.lo; pos < leftSpan.hi; pos++) {
      const id = idAt(pos);
      const slot = pos - leftSpan.lo;
      placeOf.set(id, { band: "left", slot, runLen: leftLen, leftLen, rightLen });
      roleOf.set(id, slot === 0 ? "leftHead" : "leftBody");
    }
  }
  // right run tokens
  if (rightSpan) {
    for (let pos = rightSpan.lo; pos < rightSpan.hi; pos++) {
      const id = idAt(pos);
      const slot = pos - rightSpan.lo;
      placeOf.set(id, { band: "right", slot, runLen: rightLen, leftLen, rightLen });
      roleOf.set(id, slot === 0 ? "rightHead" : "rightBody");
    }
  }
  // everything OUTSIDE the active merge span rests dimmed at its array position
  for (let pos = 0; pos < values.length; pos++) {
    if (pos >= mergeLo && pos < mergeHi) continue;
    const id = idAt(pos);
    placeOf.set(id, { band: "rest", pos });
    roleOf.set(id, "rest");
  }

  return { placeOf, roleOf, mergeLo, mergeHi, leftLen, rightLen, divideRuns: [], divideGroupOf: [] };
}

/** Heuristic: has the right run been (partly) consumed already? Used only to disambiguate a single
 *  remaining run as left-tail vs right-tail. If outFilled > leftCapacity the right side is in play. We
 *  approximate: a single remaining run starting exactly at mergeLo+outFilled that still has its head at
 *  a position whose value is ≤ everything after it is the left tail; in practice the oracle drains the
 *  shorter side first, so the single-run case is the OTHER side's tail. We treat a single run as the
 *  LEFT block when it abuts the output prefix (its lo == mergeLo + outFilled) AND there is no second
 *  run — i.e. the right side already fully drained. */
function rightRunWasConsumed(st: MergeState): boolean {
  // If only one run remains and it sits immediately after the output prefix, the side that emptied is
  // ambiguous from the step alone; visually it does not matter which block we call it — it occupies the
  // run band as a single tail. We bias to LEFT so it sits on the left side of the run band (closest to
  // the output below it reads naturally). Returning true keeps a single abutting run on the LEFT.
  return true;
}

const cellWidth = (values: number[]): number => {
  const widest = Math.max(
    ...values.map((v) => measureLabelWidth(String(v), CELL_FONT)),
    measureLabelWidth("0", CELL_FONT),
  );
  return Math.max(widest + CELL_PAD_X * 2, CELL_W_MIN);
};

/** The stable ids of tokens OUTSIDE the active merge span, in array (left→right) order — the "pending"
 *  strip. Reads the placement's mergeLo/mergeHi so it matches the renderer's active-span view exactly. */
function restPositions(
  st: MergeState,
  pl: FramePlacement,
  values: number[],
  idOfValue: Map<number, number>,
): number[] {
  void values;
  const ids: number[] = [];
  for (let pos = 0; pos < st.array.length; pos++) {
    if (pos >= pl.mergeLo && pos < pl.mergeHi) continue;
    ids.push(idOfValue.get(st.array[pos])!);
  }
  return ids;
}

// Centered row x for n cells of width w (used for divide/final + per-band blocks).
function rowStartX(count: number, cellW: number): number {
  const totalW = count * cellW + (count - 1) * CELL_GAP;
  const usable = SVG_W - PAD_SIDE * 2;
  return totalW <= usable ? (SVG_W - totalW) / 2 : PAD_SIDE;
}
function cxInRow(startX: number, slot: number, cellW: number): number {
  return startX + slot * (cellW + CELL_GAP) + cellW / 2;
}

// ── Paint per role ───────────────────────────────────────────────────────────────────────────────
function paintFor(role: TokenRole): { fill: string; ink: string; ring: string; ringW: number; glow: boolean } {
  switch (role) {
    case "rowSorted":
    case "output":
      return { fill: DARK.accent, ink: DARK.ink, ring: DARK.accent, ringW: 0, glow: false };
    case "outputJustTaken":
      return { fill: DARK.accent, ink: DARK.ink, ring: DARK.accent, ringW: 3, glow: true };
    case "leftHead":
    case "rightHead":
      return { fill: DARK.paper, ink: DARK.ink, ring: DARK.accent, ringW: 3, glow: false };
    case "leftBody":
      return { fill: DARK.leftTint, ink: DARK.paper, ring: DARK.rule, ringW: 1, glow: false };
    case "rightBody":
      return { fill: DARK.rightTint, ink: DARK.paper, ring: DARK.rule, ringW: 1, glow: false };
    case "rest":
      return { fill: "#1c1c1c", ink: DARK.faint, ring: DARK.rule, ringW: 1, glow: false };
    case "row":
    default:
      return { fill: DARK.paper, ink: DARK.ink, ring: DARK.rule, ringW: 1, glow: false };
  }
}

// ── Renderer ─────────────────────────────────────────────────────────────────────────────────────
function renderMergeFrame(values: number[], idOfValue: Map<number, number>, cellW: number) {
  const halfW = cellW / 2;
  const halfH = CELL_H / 2;
  const n = values.length;

  return (frame: Frame<MergeState>): ReactNode => {
    const st = frame.state;
    const pl = computePlacement(values, st, idOfValue);
    const showBands = st.phase === "compare" || st.phase === "take" || st.phase === "drain";
    const singleRow = st.phase === "divide" || st.phase === "final"; // ONE centered row, no merge bands

    // ── CALLOUT CHIP geometry, MEASURED ONCE here so the band label reserves space below the chip's
    // ACTUAL bottom (same coupling as the bars skin; boxes clears today only by luck and would lose that
    // luck if a chip ever wrapped to 3 lines). Rendered far below from these same values. ──
    const calloutLayout = layoutCallout(st.callout);
    const calloutPadX = 10;
    const calloutPadY = 6;
    const calloutBoxW = calloutLayout.maxLineW + calloutPadX * 2;
    const calloutHalf = calloutBoxW / 2;
    const calloutCx = Math.max(CALLOUT_PAD + calloutHalf, Math.min(SVG_W - CALLOUT_PAD - calloutHalf, SVG_W / 2));
    const calloutLineH = CALLOUT_LINE_H;
    const calloutTextBlockH = (calloutLayout.lines.length - 1) * calloutLineH + calloutLayout.font;
    const calloutBoxH = calloutTextBlockH + calloutPadY * 2;
    const calloutBoxTop = 6;
    const calloutFirstBaseline = calloutBoxTop + calloutPadY + calloutLayout.font - 2;
    const calloutBottom = calloutBoxTop + calloutBoxH;

    // ── Single-row layout (divide / final) — one centered row at ROW_CY, scaled up to seq-array's airier
    // dark rhythm so it fills the box, CLAMPED so the row (incl. any divide gaps) never exceeds the usable
    // width (no-clip by construction). On a DIVIDE frame the row is broken into its run GROUPS with an
    // inter-group gap, so the array visibly SPLITS as `runs` subdivides; that gap is folded into the width
    // budget here so the scale falls just enough to keep the widest (6-singleton) divide frame inside the
    // box. On a final frame there are no groups → one solid row (byte-identical to before). ──
    const rowUsable = SVG_W - PAD_SIDE * 2;
    // unscaled base-x centre per row slot, with a cumulative inter-group gap before each new group.
    const rowBaseXOf: number[] = [];
    let rowSpanLeft = Infinity;
    let rowSpanRight = -Infinity;
    if (singleRow) {
      let cursorLeft = 0; // running left edge of the next cell, in unscaled units from 0
      let prevGroup = pl.divideGroupOf.length > 0 ? pl.divideGroupOf[0] : 0;
      for (let slot = 0; slot < n; slot++) {
        const group = pl.divideGroupOf.length > 0 ? pl.divideGroupOf[slot] : 0;
        if (slot > 0) {
          cursorLeft += CELL_GAP;
          if (group !== prevGroup) cursorLeft += DIVIDE_GROUP_GAP;
        }
        const cx = cursorLeft + cellW / 2;
        rowBaseXOf.push(cx);
        rowSpanLeft = Math.min(rowSpanLeft, cursorLeft);
        rowSpanRight = Math.max(rowSpanRight, cursorLeft + cellW);
        cursorLeft += cellW;
        prevGroup = group;
      }
    }
    const rowFootprint = singleRow ? rowSpanRight - rowSpanLeft : n * cellW + (n - 1) * CELL_GAP;
    const rowScale = singleRow ? Math.min(SINGLE_ROW_SCALE, rowUsable / rowFootprint) : 1;
    const rowUnscaledMid = singleRow ? (rowSpanLeft + rowSpanRight) / 2 : 0;
    // scaled, centred-in-frame x for a slot's unscaled base centre.
    const rowScaledX = (baseCx: number) => SVG_W / 2 + (baseCx - rowUnscaledMid) * rowScale;
    // back-compat alias used by the ✓ rule / index-strip geometry below (the row's full scaled width).
    const rowTotalW = rowFootprint;

    // ── Shared block geometry (centered units so the run band + output block align vertically) ──
    // The full merge span length (≥ the runs + output), so the output block reserves the SAME footprint
    // the runs occupy and the committed prefix grows left→right inside it, directly under the runs.
    const mergeSpanLen = showBands ? Math.max(pl.mergeHi - pl.mergeLo, 1) : n;
    // run band: [left block][RUN_GAP][right block] centered as a unit.
    const runUnitW =
      (pl.leftLen + pl.rightLen) * cellW +
      Math.max(pl.leftLen + pl.rightLen - 1, 0) * CELL_GAP +
      (pl.leftLen > 0 && pl.rightLen > 0 ? RUN_GAP : 0);
    const runStartX = runUnitW <= SVG_W - PAD_SIDE * 2 ? (SVG_W - runUnitW) / 2 : PAD_SIDE;
    const rightStartX = runStartX + pl.leftLen * (cellW + CELL_GAP) + (pl.leftLen > 0 ? RUN_GAP : 0);
    // output band: a mergeSpanLen-wide block, centered, directly under the runs.
    const outBlockW = mergeSpanLen * cellW + (mergeSpanLen - 1) * CELL_GAP;
    const outStartX = outBlockW <= SVG_W - PAD_SIDE * 2 ? (SVG_W - outBlockW) / 2 : PAD_SIDE;
    // pending strip: the dimmed out-of-span elements, laid in their array order, centered + scaled.
    const restIds = st.phase === "divide" || st.phase === "final" ? [] : restPositions(st, pl, values, idOfValue);
    const restCellW = cellW * PENDING_SCALE;
    const restGap = CELL_GAP * PENDING_SCALE;
    const restUnitW = restIds.length * restCellW + Math.max(restIds.length - 1, 0) * restGap;
    const restStartX = (SVG_W - restUnitW) / 2;
    const restSlotOf = new Map<number, number>();
    restIds.forEach((id, slot) => restSlotOf.set(id, slot));

    // Resolve each token's target (x, y, scale) from its placement.
    const targetOf = (id: number, place: TokenPlace): { x: number; y: number; scale: number } => {
      switch (place.band) {
        case "row": {
          // divide / final: one centered row at ROW_CY, SCALED UP (width-clamped) so it fills the box
          // like seq-array's dark row (a 1× row floated mid-canvas — the void). On a divide frame the
          // base-x already carries the per-group gap (rowBaseXOf), so the row SPLITS into its run groups;
          // rowScaledX centres the gapped unit in the frame and applies the (gap-aware) clamp scale.
          const x = rowScaledX(rowBaseXOf[place.slot] ?? rowBaseXOf[0] ?? SVG_W / 2);
          return { x, y: ROW_CY, scale: rowScale };
        }
        case "rest": {
          const slot = restSlotOf.get(id) ?? 0;
          const x = restStartX + slot * (restCellW + restGap) + restCellW / 2;
          return { x, y: PENDING_STRIP_CY, scale: PENDING_SCALE };
        }
        case "output":
          return { x: outStartX + place.slot * (cellW + CELL_GAP) + cellW / 2, y: OUTPUT_BAND_CY, scale: 1 };
        case "left":
          // the FRONT (slot 0) lifts UP a few px so it protrudes above its cluster's body cells — the
          // candidate "sticking out" of its sorted list (reinforced by ring + chevron + the vs brace).
          return { x: cxInRow(runStartX, place.slot, cellW), y: RUN_BAND_CY - (place.slot === 0 ? HEAD_LIFT : 0), scale: 1 };
        case "right":
          return { x: cxInRow(rightStartX, place.slot, cellW), y: RUN_BAND_CY - (place.slot === 0 ? HEAD_LIFT : 0), scale: 1 };
      }
    };

    // ── OUTPUT baseline rule (✓ INTERCLASAT / ✓ SORTAT) — grows left→right with the committed block. ──
    // On a MERGE frame it hugs the output band; on a single-row (final) frame it hugs the SCALED row's
    // underside and spans the scaled row's full width, so the rule + caption sit right beneath the big
    // row (filling the lower band) instead of orphaned at the old 1× output-band y.
    const filled = st.phase === "final" ? n : st.outFilled;
    const ruleY = singleRow ? ROW_CY + halfH * rowScale + 12 : OUTPUT_BAND_CY + halfH + 9;
    let ruleX1Edge: number;
    let ruleX2: number;
    if (singleRow) {
      // scaled row: centered, width = rowTotalW * rowScale → the rule spans the row's full footprint.
      const scaledRowW = rowTotalW * rowScale;
      ruleX1Edge = (SVG_W - scaledRowW) / 2; // row left edge
      ruleX2 = (SVG_W + scaledRowW) / 2; // row right edge
    } else {
      ruleX1Edge = outStartX + cellW / 2 - halfW;
      ruleX2 = filled > 0 ? outStartX + (filled - 1) * (cellW + CELL_GAP) + cellW / 2 + halfW : ruleX1Edge;
    }
    const ruleX1 = ruleX1Edge;
    const captionX = (ruleX1Edge + ruleX2) / 2;
    const captionY = ruleY + 18;
    const ruleCaption = st.phase === "final" ? "✓ SORTAT" : "✓ INTERCLASAT";
    const showRule = filled > 0;
    void ruleX1;

    // ── Single-row position-index labels (0..n-1) beneath the row — the seq-array dark-skin furniture
    // that fills the lower band so the big row never floats over a void. On a divide frame the strip sits
    // just under the row; on a final frame it sits BELOW the ✓ SORTAT rule + caption (disjoint band), so
    // both single-row frame types fill top-to-bottom. Centered per SCALED cell (pitch matches the row). ──
    const showIndexStrip = singleRow;
    // Each index label sits under its actual (gap-aware, scaled) cell centre — rowScaledX(rowBaseXOf[i]).
    const indexStripCx = (i: number) => rowScaledX(rowBaseXOf[i] ?? 0);
    // divide: hug the row (no rule). final: drop below the rule+caption so the bands stay disjoint.
    const indexStripY = st.phase === "final" ? ROW_CY + halfH * rowScale + 56 : ROW_CY + halfH * rowScale + 26;

    // ── Per-cluster geometry — each sorted run is its OWN block; its centre x anchors a "LISTĂ
    // SORTATĂ" label beneath it, and its FRONT (slot 0) is the candidate head. The two fronts are the
    // leftmost cell of each cluster; on a compare frame a "vs" badge + brace links ONLY those two so
    // "compare these two fronts" is explicit. ──
    const leftClusterCx = pl.leftLen > 0 ? runStartX + (pl.leftLen * cellW + (pl.leftLen - 1) * CELL_GAP) / 2 : NaN;
    const rightClusterCx = pl.rightLen > 0 ? rightStartX + (pl.rightLen * cellW + (pl.rightLen - 1) * CELL_GAP) / 2 : NaN;
    const runLabelY = RUN_BAND_CY + halfH + RUN_LABEL_DY;
    const isCompare = st.phase === "compare";
    // The "vs" badge sits in the RUN_GAP void BETWEEN the two clusters, vertically centred on the run
    // cells — "left list vs right list". It occupies clear space (the inter-cluster gap), so it needs
    // no vertical connectors and never collides with the chevrons/cells. Only when BOTH fronts exist.
    const bothFronts = pl.leftLen > 0 && pl.rightLen > 0;
    // gap midpoint = between the right edge of the left cluster and the left edge of the right cluster.
    const leftClusterRight = runStartX + pl.leftLen * cellW + Math.max(pl.leftLen - 1, 0) * CELL_GAP;
    const vsCx = bothFronts ? (leftClusterRight + rightStartX) / 2 : SVG_W / 2;
    const vsCy = RUN_BAND_CY; // vertically centred with the run cells, inside the gap

    return (
      <>
        {/* ── DIVIDE single-row position-index strip (0..n-1) — fills the lower band, no void. ── */}
        {showIndexStrip && (
          <g>
            {Array.from({ length: n }, (_, i) => {
              const cx = indexStripCx(i);
              return (
                <text
                  key={`ixs-${i}`}
                  x={cx}
                  y={indexStripY}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={12}
                  fontWeight={700}
                  fill={DARK.faint}
                >
                  {i}
                </text>
              );
            })}
            {st.phase === "divide" && (
              <text
                x={SVG_W / 2}
                y={indexStripY + 22}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={10}
                fontWeight={700}
                letterSpacing="0.18em"
                fill={DARK.faint}
              >
                POZIȚII 0…{n - 1}
              </text>
            )}
          </g>
        )}

        {/* ── DIVIDE group brackets — a light underline under each run group so the SPLIT reads as
            "the array is being cut into these groups". One bracket per `runs` group; as the runs
            subdivide (1→2→4→6) the brackets multiply and the cells fan apart, so each divide frame
            differs visibly from the last. Drawn only on divide frames with ≥2 groups (a single
            whole-array group needs no cut marker). Each bracket hugs its group's scaled cell edges,
            just beneath the row. ── */}
        {st.phase === "divide" && pl.divideRuns.length > 1 && (
          <g>
            {pl.divideRuns.map((run, gi) => {
              const leftEdge = rowScaledX(rowBaseXOf[run.lo] ?? 0) - halfW * rowScale;
              const rightEdge = rowScaledX(rowBaseXOf[run.hi - 1] ?? 0) + halfW * rowScale;
              const by = ROW_CY + halfH * rowScale + 7; // just under the row
              const tick = 5; // short up-ticks at each end so it reads as a bracket, not a stray rule
              return (
                <path
                  key={`grp-${gi}`}
                  d={`M ${leftEdge} ${by - tick} L ${leftEdge} ${by} L ${rightEdge} ${by} L ${rightEdge} ${by - tick}`}
                  fill="none"
                  stroke={DARK.accentDim}
                  strokeWidth={2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              );
            })}
          </g>
        )}

        {/* ── OUTPUT baseline rule + caption ── */}
        {showRule && (
          <g>
            <motion.line
              x1={ruleX1Edge}
              y1={ruleY}
              y2={ruleY}
              stroke={DARK.accent}
              strokeWidth={3}
              strokeLinecap="round"
              initial={false}
              animate={{ x2: ruleX2 }}
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
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
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
            >
              {ruleCaption}
            </motion.text>
          </g>
        )}

        {/* ── PER-CLUSTER "LISTĂ SORTATĂ" labels — one beneath EACH run cluster, in the void between
            the run cells and the output band, so the two runs read as TWO DISTINCT sorted lists (not
            one row). Centered under each cluster, clamped into the frame. ── */}
        {showBands && pl.leftLen > 0 && (
          <text
            x={Math.min(Math.max(leftClusterCx, PAD_SIDE + 40), SVG_W - PAD_SIDE - 40)}
            y={runLabelY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            letterSpacing="0.12em"
            fill={DARK.faint}
          >
            LISTĂ SORTATĂ
          </text>
        )}
        {showBands && pl.rightLen > 0 && (
          <text
            x={Math.min(Math.max(rightClusterCx, PAD_SIDE + 40), SVG_W - PAD_SIDE - 40)}
            y={runLabelY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            letterSpacing="0.12em"
            fill={DARK.faint}
          >
            LISTĂ SORTATĂ
          </text>
        )}

        {/* ── COMPARE: a "vs" badge in the gap BETWEEN the two clusters, vertically centred on the run
            cells — "compare the two fronts". It sits in the inter-cluster void (clear of cells +
            chevrons + labels), so it reinforces "two separate lists, heads compared" without any
            connector that could cross a token. Drawn only on a compare frame with both fronts. ── */}
        {showBands && isCompare && bothFronts && (
          <text
            x={vsCx}
            y={vsCy + 5}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={13}
            fontWeight={700}
            letterSpacing="0.02em"
            fill={DARK.accent}
          >
            vs
          </text>
        )}

        {/* ── TOKENS — one motion.g PER STABLE ID, keyed by id so the SAME node survives every frame
            and SLIDES (animate x/y) between bands. data-cell-index = the token's CURRENT array
            position so the position-reading invariants stay correct. ── */}
        {Array.from({ length: n }, (_, id) => {
          const place = pl.placeOf.get(id)!;
          const role = pl.roleOf.get(id)!;
          const value = values[id];
          const pos = st.array.indexOf(value); // current array position (the data-cell-index stamp)
          const { x, y, scale } = targetOf(id, place);
          const p = paintFor(role);
          return (
            <motion.g
              key={`tok-${id}`}
              data-cell-index={pos}
              // data-cx / data-cy = the STATIC target position for THIS frame (the source of truth the
              // geometry invariants read). motion animates style.transform toward (x,y), but in jsdom
              // the RAF tween never runs, so style.transform is stale — these plain attributes always
              // reflect the current frame's target, so index-monotone / sorted-span checks read truth.
              data-cx={x}
              data-cy={y}
              initial={false}
              animate={{ x, y, scale }}
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
              style={{ transformOrigin: "center", transformBox: "fill-box" }}
            >
              {/* glow halo on the just-taken token */}
              <AnimatePresence>
                {p.glow && (
                  <motion.rect
                    key="glow"
                    x={-halfW - 4}
                    y={-halfH - 4}
                    width={cellW + 8}
                    height={CELL_H + 8}
                    rx={3}
                    fill="none"
                    stroke={DARK.accent}
                    strokeWidth={1}
                    initial={{ opacity: 0, scale: 0.82 }}
                    animate={{ opacity: 0.45, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.82 }}
                    transition={{ duration: RING_MS / 1000, ease: "easeOut" }}
                    style={{ transformOrigin: "center", transformBox: "fill-box" }}
                  />
                )}
              </AnimatePresence>
              <motion.rect
                x={-halfW}
                y={-halfH}
                width={cellW}
                height={CELL_H}
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
                y={CELL_FONT / 2 - 2}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={CELL_FONT}
                fontWeight={700}
                fill={p.ink}
              >
                {value}
              </text>
            </motion.g>
          );
        })}

        {/* ── FRONT-POINTER CHEVRONS — a down-chevron above each active run head; merged when co-located
            (they never are across two runs, but kept symmetric with seq-array). Each emits a zero-area
            data-pointer stamp so front-pointer-in-bounds reads the real index. Only during a live merge
            with run heads present. ── */}
        {showBands && st.runs.map((run, ri) => {
          void run;
          const frontPos = st.frontOf[ri];
          // x of the run head in the run band
          const headId = idOfValue.get(st.array[frontPos])!;
          const place = pl.placeOf.get(headId);
          if (!place || (place.band !== "left" && place.band !== "right")) return null;
          const { x } = targetOf(headId, place);
          // the head is lifted UP by HEAD_LIFT, so its chevron tip rides HEAD_LIFT higher too.
          const yTip = RUN_BAND_CY - HEAD_LIFT - halfH - 6;
          const yTop = yTip - 8;
          return (
            <g key={`front-${ri}`} data-pointer={`front-${ri}`} data-pointer-index={frontPos}>
              <motion.path
                d={`M ${-6} ${yTop} L 0 ${yTip} L 6 ${yTop}`}
                fill="none"
                stroke={DARK.accent}
                strokeWidth={2.5}
                strokeLinecap="round"
                strokeLinejoin="round"
                initial={false}
                animate={{ x }}
                transition={{ duration: CHEVRON_MS / 1000, ease: SWAP_EASE }}
              />
            </g>
          );
        })}

        {/* ── CALLOUT CHIP — measured → wrapped → clamped bordered chip in the top band. ONE node at a
            time (atomic swap, NO AnimatePresence cross-fade): the merge has many quick takes, so the
            bars-skin single-node pattern avoids piling old callouts on fast-step. ── */}
        {(() => {
          const { lines, font } = calloutLayout;
          return (
            <g>
              <rect x={calloutCx - calloutHalf} y={calloutBoxTop} width={calloutBoxW} height={calloutBoxH} rx={3} fill="#161616" stroke={DARK.accent} strokeWidth={1.5} />
              <text x={calloutCx} y={calloutFirstBaseline} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={font} fontWeight={700} fill={DARK.accent}>
                {lines.map((ln, i) => (
                  <tspan key={i} x={calloutCx} dy={i === 0 ? 0 : calloutLineH}>
                    {ln}
                  </tspan>
                ))}
              </text>
            </g>
          );
        })()}
      </>
    );
  };
}

// ── BARS variant paint (additive) — same ROLE→colour mapping as the boxes skin (paintFor), restated for
// bars so the box renderer above stays byte-unchanged. fill = bar body, ink = the value number colour. ──
function barPaintFor(role: TokenRole): { fill: string; ink: string; ring: string; ringW: number; glow: boolean } {
  switch (role) {
    case "rowSorted":
    case "output":
      return { fill: DARK.accent, ink: DARK.accent, ring: DARK.accent, ringW: 0, glow: false };
    case "outputJustTaken":
      return { fill: DARK.accent, ink: DARK.accent, ring: DARK.accent, ringW: 3, glow: true };
    case "leftHead":
    case "rightHead":
      return { fill: "#3a3a3a", ink: DARK.paper, ring: DARK.accent, ringW: 3, glow: false };
    case "leftBody":
      return { fill: DARK.leftTint, ink: DARK.dimInk, ring: DARK.rule, ringW: 1, glow: false };
    case "rightBody":
      return { fill: DARK.rightTint, ink: DARK.dimInk, ring: DARK.rule, ringW: 1, glow: false };
    case "rest":
      return { fill: "#1c1c1c", ink: DARK.faint, ring: DARK.rule, ringW: 1, glow: false };
    case "row":
    default:
      return { fill: "#262626", ink: DARK.dimInk, ring: DARK.rule, ringW: 1, glow: false };
  }
}

// ── BARS RENDERER (additive) — the height∝value skin. Mirrors renderMergeFrame's three-band
// choreography + stable-id <motion.g> slide + chevrons + ✓ INTERCLASAT rule, but each token is a BAR
// whose height encodes its value (seq-array math). Packed into the TALLER MERGE_BARS_VBH viewBox with
// disjoint vertical bands. The boxes renderer above is untouched. ──
function renderMergeBarsFrame(values: number[], idOfValue: Map<number, number>) {
  const n = values.length;
  const halfW = BARS_W / 2;
  const minV = Math.min(...values);
  const maxV = Math.max(...values);
  const span = maxV - minV || 1;
  // seq-array height encoding: smallest value → BARS_MIN_H (a stub), largest → BARS_MAX_H.
  const heightOf = (v: number) => BARS_MIN_H + ((v - minV) / span) * (BARS_MAX_H - BARS_MIN_H);

  return (frame: Frame<MergeState>): ReactNode => {
    const st = frame.state;
    const pl = computePlacement(values, st, idOfValue);
    const showBands = st.phase === "compare" || st.phase === "take" || st.phase === "drain";
    const singleRow = st.phase === "divide" || st.phase === "final";

    // ── CALLOUT CHIP geometry, MEASURED ONCE here so the run-band label can reserve space below the
    // chip's ACTUAL bottom (the chip grows DOWN by line count; a 2-line "take" callout would otherwise
    // drop onto the hardcoded 1-line label baseline — the documented seed defect). Rendered far below
    // from these same values. ──
    const calloutLayout = layoutCallout(st.callout);
    const calloutPadX = 10;
    const calloutPadY = 6;
    const calloutBoxW = calloutLayout.maxLineW + calloutPadX * 2;
    const calloutHalf = calloutBoxW / 2;
    const calloutCx = Math.max(CALLOUT_PAD + calloutHalf, Math.min(SVG_W - CALLOUT_PAD - calloutHalf, SVG_W / 2));
    const calloutLineH = CALLOUT_LINE_H;
    const calloutTextBlockH = (calloutLayout.lines.length - 1) * calloutLineH + calloutLayout.font;
    const calloutBoxH = calloutTextBlockH + calloutPadY * 2;
    const calloutBoxTop = 6;
    const calloutFirstBaseline = calloutBoxTop + calloutPadY + calloutLayout.font - 2;
    const calloutBottom = calloutBoxTop + calloutBoxH;

    // ── Horizontal block layouts (x only — y is the band baseline; bars grow UP from it) ──
    const pitch = BARS_W + BARS_GAP;
    const centeredStartX = (count: number, gap: number, barW: number): number => {
      const totalW = count * barW + Math.max(count - 1, 0) * gap;
      const usable = SVG_W - PAD_SIDE * 2;
      return totalW <= usable ? (SVG_W - totalW) / 2 : PAD_SIDE;
    };

    // single-row (divide / final): one centered row of n bars at the OUTPUT baseline (the array as bars).
    // On a DIVIDE frame the row is broken into its run GROUPS with an inter-group gap so the array
    // visibly SPLITS as `runs` subdivides (1→2→4→6). The gapped row is re-centred in the frame. On a
    // final frame there are no groups → one solid evenly-spaced row (byte-identical to before).
    const rowDivCx: number[] = []; // unscaled bar-centre x per slot, with cumulative group gaps
    {
      let cursorLeft = 0;
      let prevGroup = pl.divideGroupOf.length > 0 ? pl.divideGroupOf[0] : 0;
      for (let slot = 0; slot < n; slot++) {
        const group = pl.divideGroupOf.length > 0 ? pl.divideGroupOf[slot] : 0;
        if (slot > 0) {
          cursorLeft += BARS_GAP;
          if (group !== prevGroup) cursorLeft += DIVIDE_GROUP_GAP_BARS;
        }
        rowDivCx.push(cursorLeft + halfW);
        cursorLeft += BARS_W;
        prevGroup = group;
      }
    }
    const rowFootprint = rowDivCx.length > 0 ? rowDivCx[rowDivCx.length - 1] + halfW - (rowDivCx[0] - halfW) : 0;
    const rowOffset = (SVG_W - rowFootprint) / 2; // re-centre the gapped unit in the frame
    const rowCxOf = (slot: number) => rowOffset + (rowDivCx[slot] ?? 0);

    // run band: [left block][RUN_GAP][right block] centered as a unit.
    const runUnitW =
      (pl.leftLen + pl.rightLen) * BARS_W +
      Math.max(pl.leftLen + pl.rightLen - 1, 0) * BARS_GAP +
      (pl.leftLen > 0 && pl.rightLen > 0 ? BARS_RUN_GAP : 0);
    const runStartX =
      runUnitW <= SVG_W - PAD_SIDE * 2 ? (SVG_W - runUnitW) / 2 : PAD_SIDE;
    const rightStartX = runStartX + pl.leftLen * pitch + (pl.leftLen > 0 ? BARS_RUN_GAP : 0);
    const leftCxOf = (slot: number) => runStartX + slot * pitch + halfW;
    const rightCxOf = (slot: number) => rightStartX + slot * pitch + halfW;

    // output band: a mergeSpanLen-wide block, centered, directly under the runs.
    const mergeSpanLen = showBands ? Math.max(pl.mergeHi - pl.mergeLo, 1) : n;
    const outStartX = centeredStartX(mergeSpanLen, BARS_GAP, BARS_W);
    const outCxOf = (slot: number) => outStartX + slot * pitch + halfW;

    // pending strip: a FAINT THIN CONTEXT row of fixed-height ticks (NOT value bars), array order,
    // centered. Each tick is BARS_PENDING_W wide / BARS_PENDING_TICK_H tall — background context that
    // does not compete with the two active lists + output. Still carries its data-cell-index stamp.
    const restIds = singleRow ? [] : restPositions(st, pl, values, idOfValue);
    const restPitch = BARS_PENDING_W + BARS_PENDING_GAP;
    const restUnitW = restIds.length * BARS_PENDING_W + Math.max(restIds.length - 1, 0) * BARS_PENDING_GAP;
    const restStartX = (SVG_W - restUnitW) / 2;
    const restSlotOf = new Map<number, number>();
    restIds.forEach((id, slot) => restSlotOf.set(id, slot));

    // Resolve each token's target — bar center x, the band BASELINE y, and a width scale.
    // (Bars grow UP from the baseline; the motion.g translates by (x, baseline), the rect is drawn
    //  with y = -h so its bottom sits on the baseline and it rises. The head bar (slot 0 of a run)
    //  lifts UP by HEAD_LIFT_BARS so it protrudes above its cluster — the candidate "sticking out".)
    const targetOf = (
      id: number,
      place: TokenPlace,
    ): { x: number; baseY: number; scale: number } => {
      switch (place.band) {
        case "row":
          return { x: rowCxOf(place.slot), baseY: OUTPUT_BAND_BASE, scale: 1 };
        case "rest": {
          const slot = restSlotOf.get(id) ?? 0;
          return { x: restStartX + slot * restPitch + BARS_PENDING_W / 2, baseY: PENDING_BASE, scale: 1 };
        }
        case "output":
          return { x: outCxOf(place.slot), baseY: OUTPUT_BAND_BASE, scale: 1 };
        case "left":
          return { x: leftCxOf(place.slot), baseY: RUN_BAND_BASE - (place.slot === 0 ? HEAD_LIFT_BARS : 0), scale: 1 };
        case "right":
          return { x: rightCxOf(place.slot), baseY: RUN_BAND_BASE - (place.slot === 0 ? HEAD_LIFT_BARS : 0), scale: 1 };
      }
    };

    // ── Per-cluster geometry — each sorted run is its own block; a "LISTĂ SORTATĂ" label sits ABOVE
    // each cluster (in the band-label zone) and a "vs" badge sits in the gap between them on a compare
    // frame. ──
    const leftClusterCx = pl.leftLen > 0 ? runStartX + (pl.leftLen * BARS_W + (pl.leftLen - 1) * BARS_GAP) / 2 : NaN;
    const rightClusterCx = pl.rightLen > 0 ? rightStartX + (pl.rightLen * BARS_W + (pl.rightLen - 1) * BARS_GAP) / 2 : NaN;
    const isCompare = st.phase === "compare";
    const bothFronts = pl.leftLen > 0 && pl.rightLen > 0;
    const leftClusterRight = runStartX + pl.leftLen * BARS_W + Math.max(pl.leftLen - 1, 0) * BARS_GAP;
    const vsCx = bothFronts ? (leftClusterRight + rightStartX) / 2 : SVG_W / 2;
    // the "vs" badge sits in the gap, vertically near the run bars' mid-height, in clear inter-cluster
    // space (above the run value row, below the bars' tops).
    const vsCy = RUN_BAND_BASE - BARS_MIN_H - 6;
    // cluster-label baseline — BELOW the run bars + their value numbers, in the void above the ✓ rule
    // (run value row baseline ≈ RUN_BAND_BASE+21=205; ✓ caption top ≈ 231). Sits clear of both, and of
    // the chevrons (which live ABOVE the bars), so two "LISTĂ SORTATĂ" captions never collide.
    const clusterLabelY = RUN_BAND_BASE + BARS_VALUE_GAP + BARS_VALUE_FONT + 15;

    // ── ✓ INTERCLASAT / ✓ SORTAT rule — sits ABOVE the output band's bars (between the run band's value
    // row and the output bars), spanning the committed output block, growing left→right. On a single-row
    // (final) frame it spans the whole row. ──
    const filled = st.phase === "final" ? n : st.outFilled;
    const showRule = filled > 0;
    const ruleY = singleRow
      ? OUTPUT_BAND_BASE - BARS_MAX_H - 14 // above the tallest single-row bar
      : OUTPUT_BAND_BASE - BARS_MAX_H - 14; // above the tallest possible output bar
    let ruleX1: number;
    let ruleX2: number;
    if (singleRow) {
      ruleX1 = rowCxOf(0) - halfW;
      ruleX2 = rowCxOf(n - 1) + halfW;
    } else {
      ruleX1 = outCxOf(0) - halfW;
      ruleX2 = filled > 0 ? outCxOf(filled - 1) + halfW : ruleX1;
    }
    const captionX = (ruleX1 + ruleX2) / 2;
    const captionY = ruleY - 7;
    const ruleCaption = st.phase === "final" ? "✓ SORTAT" : "✓ INTERCLASAT";

    return (
      <>
        {/* ── PER-CLUSTER "LISTĂ SORTATĂ" labels — one beneath EACH run cluster (in the void between the
            run value numbers and the ✓ rule), so the two runs read as TWO DISTINCT sorted lists, not one
            row. Centered under each cluster, clamped into the frame. ── */}
        {showBands && pl.leftLen > 0 && (
          <text
            x={Math.min(Math.max(leftClusterCx, PAD_SIDE + 40), SVG_W - PAD_SIDE - 40)}
            y={clusterLabelY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            letterSpacing="0.12em"
            fill={DARK.faint}
          >
            LISTĂ SORTATĂ
          </text>
        )}
        {showBands && pl.rightLen > 0 && (
          <text
            x={Math.min(Math.max(rightClusterCx, PAD_SIDE + 40), SVG_W - PAD_SIDE - 40)}
            y={clusterLabelY}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fontWeight={700}
            letterSpacing="0.12em"
            fill={DARK.faint}
          >
            LISTĂ SORTATĂ
          </text>
        )}

        {/* ── COMPARE: a "vs" badge in the gap BETWEEN the two clusters — "compare the two fronts". It
            sits in the inter-cluster void (clear of bars + chevrons + labels). ── */}
        {showBands && isCompare && bothFronts && (
          <text
            x={vsCx}
            y={vsCy}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={13}
            fontWeight={700}
            letterSpacing="0.02em"
            fill={DARK.accent}
          >
            vs
          </text>
        )}

        {/* ── shared baselines (faint rules the bars stand on) so "grows up from the floor" reads. ── */}
        {showBands && pl.leftLen + pl.rightLen > 0 && (
          <line
            x1={runStartX - 4}
            y1={RUN_BAND_BASE}
            x2={runStartX + runUnitW + 4}
            y2={RUN_BAND_BASE}
            stroke={DARK.rule}
            strokeWidth={1}
          />
        )}
        {/* On a divide frame split into ≥2 groups the bars stand on PER-GROUP baseline segments (drawn
            below) so the split reads as separate floors; otherwise one continuous baseline. */}
        {!(st.phase === "divide" && pl.divideRuns.length > 1) && (
          <line
            x1={(singleRow ? rowCxOf(0) : outCxOf(0)) - halfW - 4}
            y1={OUTPUT_BAND_BASE}
            x2={(singleRow ? rowCxOf(n - 1) : outCxOf(mergeSpanLen - 1)) + halfW + 4}
            y2={OUTPUT_BAND_BASE}
            stroke={DARK.rule}
            strokeWidth={1}
          />
        )}

        {/* ── DIVIDE group baseline + bracket — one short floor segment per run group so the array
            visibly SPLITS into its groups; as `runs` subdivides (1→2→4→6) the segments multiply and the
            bars fan apart, so each divide frame differs from the last. Only on a divide frame with ≥2
            groups (a single whole-array group keeps the plain continuous baseline above). ── */}
        {st.phase === "divide" && pl.divideRuns.length > 1 && (
          <g>
            {pl.divideRuns.map((run, gi) => {
              const leftEdge = rowCxOf(run.lo) - halfW - 4;
              const rightEdge = rowCxOf(run.hi - 1) + halfW + 4;
              const tick = 6; // down-ticks at each end so the floor reads as a group bracket
              return (
                <path
                  key={`grp-${gi}`}
                  d={`M ${leftEdge} ${OUTPUT_BAND_BASE + tick} L ${leftEdge} ${OUTPUT_BAND_BASE} L ${rightEdge} ${OUTPUT_BAND_BASE} L ${rightEdge} ${OUTPUT_BAND_BASE + tick}`}
                  fill="none"
                  stroke={DARK.accentDim}
                  strokeWidth={2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              );
            })}
          </g>
        )}

        {/* ── ✓ INTERCLASAT rule + caption ── */}
        {showRule && (
          <g>
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
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
            >
              {ruleCaption}
            </motion.text>
            <motion.line
              x1={ruleX1}
              y1={ruleY}
              y2={ruleY}
              stroke={DARK.accent}
              strokeWidth={3}
              strokeLinecap="round"
              initial={false}
              animate={{ x2: ruleX2 }}
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
            />
          </g>
        )}

        {/* ── BARS — one motion.g PER STABLE ID, keyed by id so the SAME node survives every frame and
            SLIDES (animate x/y) between bands. data-cell-index = the token's CURRENT array position so
            the position-reading invariants stay correct. The rect is drawn UP from y=0 (the g sits on
            the band baseline); height = heightOf(value), so a value's bar keeps its height wherever it
            lands and the eye tracks "the short bar moved". ── */}
        {Array.from({ length: n }, (_, id) => {
          const place = pl.placeOf.get(id)!;
          const role = pl.roleOf.get(id)!;
          const value = values[id];
          const pos = st.array.indexOf(value);
          const { x, baseY, scale } = targetOf(id, place);
          const p = barPaintFor(role);
          const isRest = place.band === "rest";
          // ACTIVE tokens (left/right/output/row) are value-height bars; the demoted PENDING tokens are
          // faint fixed-height context ticks (narrow, short, no value number) so the two active lists +
          // output dominate the figure.
          const h = isRest ? BARS_PENDING_TICK_H : heightOf(value);
          const w = isRest ? BARS_PENDING_W : BARS_W;
          const hw = w / 2;
          return (
            <motion.g
              key={`bar-${id}`}
              data-cell-index={pos}
              data-cx={x}
              data-cy={baseY}
              initial={false}
              animate={{ x, y: baseY, scale }}
              transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
              style={{ transformOrigin: "center bottom", transformBox: "fill-box" }}
            >
              {/* glow halo on the just-taken bar */}
              <AnimatePresence>
                {p.glow && !isRest && (
                  <motion.rect
                    key="glow"
                    x={-hw - 4}
                    y={-h - 4}
                    width={w + 8}
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
              {/* the bar — grows UP from the baseline (y = -h) so its bottom hugs the band rule.
                  PENDING ticks are faint + thin (background context). */}
              <motion.rect
                x={-hw}
                width={w}
                rx={isRest ? 2 : 3}
                initial={false}
                animate={{
                  y: -h,
                  height: h,
                  fill: isRest ? "#242424" : p.fill,
                  stroke: isRest ? DARK.rule : p.ringW > 0 ? p.ring : DARK.rule,
                  strokeWidth: isRest ? 1 : p.ringW > 0 ? p.ringW : 1,
                }}
                transition={{ duration: SLIDE_MS / 1000, ease: SWAP_EASE }}
              />
              {/* value number — below the baseline, centered under the bar. Suppressed on the faint
                  pending ticks (they are context, not data) so the strip stays unobtrusive + short. */}
              {!isRest && (
                <text
                  x={0}
                  y={BARS_VALUE_GAP + BARS_VALUE_FONT}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={BARS_VALUE_FONT}
                  fontWeight={700}
                  fill={p.ink}
                >
                  {value}
                </text>
              )}
            </motion.g>
          );
        })}

        {/* ── FRONT-POINTER CHEVRONS — a down-chevron above each active run head bar. Each emits a
            zero-area data-pointer stamp so front-pointer-in-bounds reads the real index. ── */}
        {showBands && st.runs.map((run, ri) => {
          void run;
          const frontPos = st.frontOf[ri];
          const headId = idOfValue.get(st.array[frontPos])!;
          const place = pl.placeOf.get(headId);
          if (!place || (place.band !== "left" && place.band !== "right")) return null;
          const { x } = targetOf(headId, place);
          const headVal = values[headId];
          // the head bar is lifted UP by HEAD_LIFT_BARS, so its top — and the chevron above it — rise too.
          const yTip = RUN_BAND_BASE - HEAD_LIFT_BARS - heightOf(headVal) - 8; // just above the head bar's top
          const yTop = yTip - 8;
          return (
            <g key={`front-${ri}`} data-pointer={`front-${ri}`} data-pointer-index={frontPos}>
              <motion.path
                d={`M ${-6} ${yTop} L 0 ${yTip} L 6 ${yTop}`}
                fill="none"
                stroke={DARK.accent}
                strokeWidth={2.5}
                strokeLinecap="round"
                strokeLinejoin="round"
                initial={false}
                animate={{ x }}
                transition={{ duration: CHEVRON_MS / 1000, ease: SWAP_EASE }}
              />
            </g>
          );
        })}

        {/* ── CALLOUT CHIP — measured → wrapped → clamped bordered chip in the top band. Geometry is
            computed ONCE at the top of the frame (calloutLayout/calloutBoxH/calloutBottom/…) so the
            run-band label reserves space below this chip's ACTUAL bottom — they can never diverge. ── */}
        {(() => {
          const { lines, font } = calloutLayout;
          return (
            <g>
              <rect x={calloutCx - calloutHalf} y={calloutBoxTop} width={calloutBoxW} height={calloutBoxH} rx={3} fill="#161616" stroke={DARK.accent} strokeWidth={1.5} />
              <text x={calloutCx} y={calloutFirstBaseline} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={font} fontWeight={700} fill={DARK.accent}>
                {lines.map((ln, i) => (
                  <tspan key={i} x={calloutCx} dy={i === 0 ? 0 : calloutLineH}>
                    {ln}
                  </tspan>
                ))}
              </text>
            </g>
          );
        })()}
      </>
    );
  };
}

/** ADDITIVE props — `layout` lets the lesson surface drop the white box + side controls.
 *  `variant` selects the render skin: "boxes" (default = the CURRENT renderer, byte-unchanged) or
 *  "bars" (the PROTOTYPE height∝value skin in a taller viewBox). */
export type SortMergeFamilyProps = FamilyRendererProps & {
  layout?: ShellLayout;
  variant?: "boxes" | "bars";
  /** ADDITIVE — open the figure on a specific frame (passed straight to the shell). The compare
   *  prototype uses it to open BOTH skins on the same representative merge frame. */
  initialStep?: number;
};

export function SortMergeFamily({ instanceId, dataJson, language, labels, onStep, layout: shellLayout, variant = "boxes", initialStep }: SortMergeFamilyProps): ReactNode {
  const data = useMemo(() => parseMergeData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromMergeData(data), [data]);
  const idOfValue = useMemo(() => buildIdOfValue(data.values), [data]);
  const cellW = useMemo(() => cellWidth(data.values), [data]);
  const render = useMemo(
    () =>
      variant === "bars"
        ? renderMergeBarsFrame(data.values, idOfValue)
        : renderMergeFrame(data.values, idOfValue, cellW),
    [variant, data.values, idOfValue, cellW],
  );

  const lastIdx = Math.max(0, frames.length - 1);
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );

  const effectiveLayout: ShellLayout = { ...shellLayout, viewBoxH: variant === "bars" ? MERGE_BARS_VBH : MERGE_VBH };

  return (
    <AlgoStepperShell<MergeState>
      title={`Sort/merge · ${instanceId}`}
      desc={
        language === "ro"
          ? "Sortare prin interclasare; două liste sortate se contopesc într-una singură, element cu element."
          : "Merge sort; two sorted runs converge into one, element by element."
      }
      frames={frames}
      renderFrame={render}
      testIdPrefix="sort-merge"
      labels={labels}
      initialStep={initialStep}
      onStep={onStep ? shellOnStep : undefined}
      layout={effectiveLayout}
    />
  );
}
