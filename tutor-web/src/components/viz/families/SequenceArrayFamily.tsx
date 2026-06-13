import { useCallback, useMemo, type ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

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
  rowY: number; // cell-box top
  cellCY: number; // cell-box vertical center (the translate cy stamped on each <g>)
  cxOf: (index: number) => number; // cell-box center x (the translate cx)
  indexY: number; // baseline for the 0..n-1 index labels (below the row)
  pointerY: number; // baseline for the pointer markers (above the row)
};

function computeLayout(data: SeqArrayData): CellLayout {
  const n = data.values.length;
  // widest rendered token across ALL steps (values + pointer glyphs feed the cell width).
  const widest = Math.max(
    ...data.steps.flatMap((s) => s.array.map((v) => measureLabelWidth(String(v), CELL_FONT))),
    measureLabelWidth("0", CELL_FONT),
  );
  const cellW = widest + CELL_PAD_X * 2;
  const totalW = n * cellW + (n - 1) * CELL_GAP;
  // center the row; if it is wider than the usable area, anchor at the side gutter (degrades, never clips).
  const usable = SVG_W - CELL_PAD_SIDE * 2;
  const startX = totalW <= usable ? (SVG_W - totalW) / 2 : CELL_PAD_SIDE;
  const cxOf = (index: number) => startX + index * (cellW + CELL_GAP) + cellW / 2;

  const rowY = CALLOUT_BAND_H + 60; // leave room for the pointer band above the row
  const cellCY = rowY + CELL_H / 2;
  return {
    cellW,
    rowY,
    cellCY,
    cxOf,
    indexY: rowY + CELL_H + INDEX_FONT + 6,
    pointerY: rowY - POINTER_LABEL_GAP,
  };
}

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

function renderFrame(layout: CellLayout) {
  return (frame: Frame<SeqArrayState>): ReactNode => {
    const st = frame.state;
    const n = st.array.length;
    const { cellW, rowY, cellCY, cxOf, indexY, pointerY } = layout;

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

        {/* ── POINTER MARKERS anchored ABOVE their cell, stacked on sub-rows so two pointers over the
            same cell never overlap. ──
            SCAN: i / j / min — `min` correctly tracks the running-minimum cell while scanning.
            SWAP: the scan is over; `min` (the model value) is now the SOURCE index, whose cell holds
            the swapped-OUT (larger) value — so we must NOT paint a "min" label there. Instead we show
            the EXCHANGE: a "minim" marker over the DESTINATION (index i, which now holds the placed
            minimum) and a "↔ schimb" marker over the SOURCE (index min). When the swap is a no-op
            (min === i, the minimum was already in place) there is nothing to exchange, so we render
            only the single "minim aici" marker over that cell. Every data-pointer-index stays
            in-bounds (i and min are both validated indices), keeping INV-3 pointer-in-bounds green. */}
        {st.phase === "scan" ? (
          <>
            {renderPointer("i", st.i, cxOf, pointerY - POINTER_FONT * 2 - 6, "i →")}
            {renderPointer("j", st.j, cxOf, pointerY - POINTER_FONT - 3, "j")}
            {renderPointer("min", st.min, cxOf, pointerY, "min")}
          </>
        ) : st.min === st.i ? (
          // no-op swap: the minimum was already at position i — one marker, no exchange arrow.
          <>{renderPointer("min", st.i, cxOf, pointerY, "minim aici")}</>
        ) : (
          <>
            {renderPointer("min", st.i, cxOf, pointerY - POINTER_FONT - 3, "✓ minim")}
            {renderPointer("swap", st.min, cxOf, pointerY, "↔ schimb")}
          </>
        )}

        {/* ── CALLOUT — anchored to the active cell's column, measured → wrapped → clamped (no-clip).
            Lives in the TOP band [0, CALLOUT_BAND_H]; can never enter the cell row by construction. ── */}
        {(() => {
          const { lines, font, maxLineW } = layoutCallout(st.callout);
          const half = maxLineW / 2;
          const loBound = CALLOUT_PAD + half;
          const hiBound = SVG_W - CALLOUT_PAD - half;
          const cx = Math.max(loBound, Math.min(hiBound, anchorCx));
          const blockH = (lines.length - 1) * CALLOUT_LINE_H;
          const firstWant = 14;
          const top = Math.min(firstWant, CALLOUT_BAND_H - 4 - blockH);
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

    function renderPointer(
      kind: string,
      index: number,
      cxFn: (i: number) => number,
      y: number,
      glyph: string,
    ): ReactNode {
      // Anchor over the cell, then clamp center-x by HALF the MEASURED glyph width so a wider label
      // (e.g. "↔ schimb") over an edge cell can never spill past the frame — no-clip-by-construction
      // (§5.3), same measureLabelWidth pattern as the callout. data-pointer-index still records the
      // true cell index (INV-3 reads that, not the clamped x).
      const half = measureLabelWidth(glyph, POINTER_FONT) / 2;
      const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, cxFn(index)));
      return (
        <g key={`ptr-${kind}`} data-pointer={kind} data-pointer-index={index}>
          <text
            x={cx}
            y={y}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={POINTER_FONT}
            fontWeight={700}
            fill={INK}
          >
            {glyph}
          </text>
        </g>
      );
    }
  };
}

export function SequenceArrayFamily({ instanceId, dataJson, language, labels, onStep }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseSeqArrayData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromSeqArrayData(data), [data]);
  const layout = useMemo(() => computeLayout(data), [data]);
  const lastIdx = Math.max(0, frames.length - 1);
  // STABLE onStep (useCallback) — see GraphTreeFamily.tsx:288: a fresh arrow every render re-fires
  // the shell's onStep effect → setState → re-render → new arrow → infinite render loop.
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  return (
    <AlgoStepperShell<SeqArrayState>
      title={`Sequence/array · ${instanceId}`}
      desc={
        language === "ro"
          ? "Sortare prin selecție; scanează minimul și mută-l la stânga, pas cu pas."
          : "Selection sort; scan for the minimum and move it left, step by step."
      }
      frames={frames}
      renderFrame={renderFrame(layout)}
      testIdPrefix="seq-array"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
    />
  );
}
