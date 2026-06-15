import { useCallback, useMemo, type ReactNode } from "react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "../AlgoStepperShell";
import {
  ACCENT,
  FONT_FAMILY,
  HATCH_LIGHT,
  INK,
  PAPER,
  STROKE_DEFAULT,
  STROKE_FOCUS,
  FONT_SIZE_LABEL,
} from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Render skins ──────────────────────────────────────────────────────────
// ONE geometry + ONE set of data-* stamps, TWO paint skins (mirrors SequenceArrayFamily). Colours are
// baked as LITERAL hex into the SVG presentation attributes (NOT var() — unreliable in SVG attributes),
// so the colour-blind invariants (data-cell-row/col/value/fill + data-pivot) stay green across skins.
//   • "light" — original brutalist-on-paper (gallery + e2e + trace harness, UNCHANGED).
//   • "dark"  — lectie lesson surface: grid floats on the dark stage, light ink, accent pivot, filled
//               cells get a dim-accent wash (the light skin's black HATCH_LIGHT is invisible on dark).
export type MgVariant = "light" | "dark";

const DARK = {
  bg: "#0e0e0e",        // panel-dark-bg (DESIGN.md DARK surface)
  emptyFill: "#161616", // an unfilled cell — card on the dark stage
  filledFill: "#2a2510",// a filled/computed cell — dim-yellow wash (replaces the black hatch)
  pivotFill: "#fde047", // the pivot/active cell — solid accent
  ink: "#f4f4f4",       // value in a normal cell (off-white)
  pivotInk: "#000000",  // value in the pivot cell
  rule: "#3a3a3a",      // cell hairline
  pivotRule: "#fde047", // pivot ring
  header: "#fde047",    // row/col header text (yellow)
  callout: "#fde047",   // callout text (yellow)
  rowOp: "#cfcfcf",     // rowOp rail (quiet light)
} as const;

// ── A cell coordinate (0-based, row-major) ───────────────────────────────────────────────────────
export type CellCoord = { row: number; col: number };

// ── One cell WRITE within a step: set cell (row,col) to `value` ────────────────────────────────────
export type CellWrite = {
  row: number;
  col: number;
  /** the new display string for that cell (e.g. "5", "-8", "→pfn2", "INVALID", "∞", ""). */
  value: string;
};

// ── A MATRIX/GRID step. State = direct mirror of Step (no transformation). ─────────────────────────
export type MatrixGridStep = {
  /** cells WRITTEN this step (cumulative model is rebuilt by framesFrom…). Each coord must be
   *  in-bounds [0,rows)×[0,cols). May be empty (a pure-highlight/pivot beat). */
  writes: CellWrite[];
  /** cells that become FILLED (enter the active/computed set) this step. Monotone: a fill never
   *  un-fills (INV-3). Coords in-bounds. May be empty. Cells already filled may reappear (idempotent). */
  fills: CellCoord[];
  /** the pivot/active cell for this step (the one the callout is anchored to). null = no pivot beat
   *  (e.g. the initial "here is the empty table" frame). In-bounds when present. */
  pivot: CellCoord | null;
  /** optional row-operation annotation for elimination traces — purely descriptive (rendered as a
   *  side rail label), NOT read by invariants. e.g. "E2 ← E2 + (−3)·E1" or "swap E2 ↔ E3". */
  rowOp?: string;
  /** RO callout for this beat, element-anchored to `pivot` (never a hardcoded color name). */
  callout: string;
};

// ── A MATRIX/GRID instance: the grid shape + the seed that drives the oracle + the step trace. ─────
export type MatrixGridData = {
  /** grid dimensions. rows≥1, cols≥1. */
  rows: number;
  cols: number;
  /** the algorithm KIND — selects which reference oracle re-derives the table (§6). */
  kind: "dp-fill" | "gauss-elim" | "page-table";
  /** the SEED the oracle re-runs from. Shape depends on `kind` (validated per-kind in §2). */
  seed: Record<string, unknown>;
  /** optional fixed column headers. length==cols when present. */
  colHeaders?: string[];
  /** optional fixed row headers. length==rows when present. */
  rowHeaders?: string[];
  /** ordered per-step trace. The family NEVER invents the trace — the oracle (matrixGridTrace.ts)
   *  re-derives the reference table per step from `seed`+`kind`, and the harness asserts equality. */
  steps: MatrixGridStep[];
};

// ── What ONE rendered step exposes — BOTH the trace-compared values (the full materialized grid +
//    the filled set + pivot) AND the DOM-lookup ids (every cell stamps data-cell-row/col/value/fill).
export type MatrixGridState = {
  /** the FULL materialized grid AFTER this step's writes (rows×cols of display strings). */
  grid: string[][];
  /** the set of filled coords AFTER this step (cumulative), serialized as "r,c" keys. */
  filled: string[];
  /** the pivot coord for this step (null when none). */
  pivot: CellCoord | null;
  /** the row-op annotation for this step (undefined when none). */
  rowOp?: string;
  /** the callout text. */
  callout: string;
  /** grid dims echoed so invariants/layout don't re-parse. */
  rows: number;
  cols: number;
};

/**
 * Hand-rolled validation guard (zod-free, mirrors parseSeqArrayData). Throws naming `instanceId` +
 * the offending field/index on EVERY fault (INV-5.5: a broken instance fails admission loud, never
 * renders garbled).
 */
export function parseMatrixGridData(dataJson: string, instanceId: string): MatrixGridData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`matrix-grid instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;

  // ── rows / cols ──
  if (typeof obj.rows !== "number" || !Number.isInteger(obj.rows) || obj.rows < 1)
    throw new Error(`matrix-grid instance '${instanceId}': field 'rows' must be an integer ≥1 (got ${String(obj.rows)})`);
  if (typeof obj.cols !== "number" || !Number.isInteger(obj.cols) || obj.cols < 1)
    throw new Error(`matrix-grid instance '${instanceId}': field 'cols' must be an integer ≥1 (got ${String(obj.cols)})`);
  const rows = obj.rows, cols = obj.cols;

  // ── kind ──
  const KINDS = ["dp-fill", "gauss-elim", "page-table"];
  if (typeof obj.kind !== "string" || !KINDS.includes(obj.kind))
    throw new Error(`matrix-grid instance '${instanceId}': field 'kind' must be one of ${KINDS.join("|")} (got ${String(obj.kind)})`);
  const kind = obj.kind as MatrixGridData["kind"];

  // ── seed (per-kind structural check; the oracle narrows further) ──
  if (typeof obj.seed !== "object" || obj.seed === null || Array.isArray(obj.seed))
    throw new Error(`matrix-grid instance '${instanceId}': field 'seed' must be an object`);
  const seed = obj.seed as Record<string, unknown>;
  if (kind === "dp-fill") {
    if (typeof seed.n !== "number" || !Number.isInteger(seed.n) || seed.n < 0)
      throw new Error(`matrix-grid instance '${instanceId}': seed.n must be an integer ≥0 for kind 'dp-fill' (got ${String(seed.n)})`);
    if (cols !== seed.n + 1 || rows !== 1)
      throw new Error(`matrix-grid instance '${instanceId}': dp-fill grid must be 1×(n+1)=1×${(seed.n as number) + 1}, got ${rows}×${cols}`);
  } else if (kind === "gauss-elim") {
    if (!Array.isArray(seed.matrix) || seed.matrix.length !== rows)
      throw new Error(`matrix-grid instance '${instanceId}': seed.matrix must be a ${rows}-row array for kind 'gauss-elim'`);
    (seed.matrix as unknown[]).forEach((rw, r) => {
      if (!Array.isArray(rw) || rw.length !== cols)
        throw new Error(`matrix-grid instance '${instanceId}': seed.matrix[${r}] must have ${cols} entries (got ${Array.isArray(rw) ? rw.length : String(rw)})`);
      (rw as unknown[]).forEach((v, c) => {
        if (typeof v !== "number" || !Number.isFinite(v))
          throw new Error(`matrix-grid instance '${instanceId}': seed.matrix[${r}][${c}] must be a finite number (got ${String(v)})`);
      });
    });
  } else { // page-table
    if (!Array.isArray(seed.pte) || seed.pte.length !== rows)
      throw new Error(`matrix-grid instance '${instanceId}': seed.pte must be a ${rows}-entry array for kind 'page-table'`);
    (seed.pte as unknown[]).forEach((v, i) => {
      if (v !== null && (typeof v !== "number" || !Number.isInteger(v) || v < 0))
        throw new Error(`matrix-grid instance '${instanceId}': seed.pte[${i}] must be a non-negative integer or null (got ${String(v)})`);
    });
  }

  // ── optional headers (length must match dims) ──
  let colHeaders: string[] | undefined;
  if (obj.colHeaders !== undefined) {
    if (!Array.isArray(obj.colHeaders) || obj.colHeaders.length !== cols)
      throw new Error(`matrix-grid instance '${instanceId}': 'colHeaders' must be a string[] of length ${cols}`);
    colHeaders = obj.colHeaders.map((h, c) => {
      if (typeof h !== "string") throw new Error(`matrix-grid instance '${instanceId}': colHeaders[${c}] must be a string (got ${String(h)})`);
      return h;
    });
  }
  let rowHeaders: string[] | undefined;
  if (obj.rowHeaders !== undefined) {
    if (!Array.isArray(obj.rowHeaders) || obj.rowHeaders.length !== rows)
      throw new Error(`matrix-grid instance '${instanceId}': 'rowHeaders' must be a string[] of length ${rows}`);
    rowHeaders = obj.rowHeaders.map((h, r) => {
      if (typeof h !== "string") throw new Error(`matrix-grid instance '${instanceId}': rowHeaders[${r}] must be a string (got ${String(h)})`);
      return h;
    });
  }

  // ── steps ──
  if (!Array.isArray(obj.steps) || obj.steps.length < 1)
    throw new Error(`matrix-grid instance '${instanceId}': field 'steps' must be a non-empty array`);

  const inBounds = (r: unknown, c: unknown): boolean =>
    typeof r === "number" && typeof c === "number" &&
    Number.isInteger(r) && Number.isInteger(c) && r >= 0 && r < rows && c >= 0 && c < cols;

  const steps: MatrixGridStep[] = obj.steps.map((s, si) => {
    const st = s as Record<string, unknown>;

    // writes
    if (!Array.isArray(st.writes))
      throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].writes must be an array`);
    const writes: CellWrite[] = (st.writes as unknown[]).map((w, wi) => {
      const wo = w as Record<string, unknown>;
      if (!inBounds(wo.row, wo.col))
        throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].writes[${wi}] coord (${String(wo.row)},${String(wo.col)}) out of bounds for ${rows}×${cols} grid`);
      if (typeof wo.value !== "string")
        throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].writes[${wi}].value must be a string (got ${String(wo.value)})`);
      return { row: wo.row as number, col: wo.col as number, value: wo.value as string };
    });

    // fills
    if (!Array.isArray(st.fills))
      throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].fills must be an array`);
    const fills: CellCoord[] = (st.fills as unknown[]).map((f, fi) => {
      const fo = f as Record<string, unknown>;
      if (!inBounds(fo.row, fo.col))
        throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].fills[${fi}] coord (${String(fo.row)},${String(fo.col)}) out of bounds for ${rows}×${cols} grid`);
      return { row: fo.row as number, col: fo.col as number };
    });

    // pivot (nullable)
    let pivot: CellCoord | null = null;
    if (st.pivot !== null && st.pivot !== undefined) {
      const po = st.pivot as Record<string, unknown>;
      if (!inBounds(po.row, po.col))
        throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].pivot coord (${String(po.row)},${String(po.col)}) out of bounds for ${rows}×${cols} grid`);
      pivot = { row: po.row as number, col: po.col as number };
    }

    if (st.rowOp !== undefined && typeof st.rowOp !== "string")
      throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].rowOp must be a string when present (got ${String(st.rowOp)})`);
    if (typeof st.callout !== "string" || st.callout.length === 0)
      throw new Error(`matrix-grid instance '${instanceId}': steps[${si}].callout must be a non-empty string`);

    return { writes, fills, pivot, rowOp: st.rowOp as string | undefined, callout: st.callout as string };
  });

  // Fills are MONOTONE across steps by construction (the frame builder only grows the filled set — no
  // unfill API exists), and INV-3 re-verifies monotonicity from the DOM. No parse-time guard needed.

  return { rows, cols, kind, seed, colHeaders, rowHeaders, steps };
}

/**
 * Build one Frame per step. Frames ACCRETE: steps carry only deltas (writes/fills); the builder folds
 * them into the full materialized grid + cumulative filled set, so `MatrixGridState.grid` and `.filled`
 * are running totals (what the harness compares + the renderer paints). aria = step.callout.
 */
export function framesFromMatrixGridData(data: MatrixGridData): Frame<MatrixGridState>[] {
  const grid: string[][] = Array.from({ length: data.rows }, () => Array.from({ length: data.cols }, () => ""));
  const filled = new Set<string>();
  const frames: Frame<MatrixGridState>[] = [];

  for (const step of data.steps) {
    for (const w of step.writes) grid[w.row][w.col] = w.value;
    for (const f of step.fills) filled.add(`${f.row},${f.col}`);

    frames.push({
      state: {
        grid: grid.map((r) => [...r]),
        filled: [...filled].sort(),
        pivot: step.pivot ? { ...step.pivot } : null,
        rowOp: step.rowOp,
        callout: step.callout,
        rows: data.rows,
        cols: data.cols,
      },
      aria: step.callout,
    });
  }
  return frames;
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────────────────────────────
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

// ── Constants ──────────────────────────────────────────────────────────────────────────────────
const SVG_W = 480;
const VIEWBOX_H = 360;                 // default; this family packs into [0,360]
const CALLOUT_BAND_H = 40;             // top band (callout lives in [0,40])
const CALLOUT_FONT = 11, CALLOUT_MIN_FONT = 8, CALLOUT_PAD = 8, CALLOUT_LINE_H = 13;
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2;   // 464
const GRID_TOP = CALLOUT_BAND_H + 8;  // grid starts BELOW the callout band → disjoint, no overlap
const ROWOP_BAND_H = 18;              // bottom rail for the rowOp annotation
const HEADER_H = 16;                  // col-header row height (when colHeaders present)
const CELL_PAD_X = 6, CELL_MIN_W = 26, CELL_MIN_H = 22;
const GRID_FONT = 13;                 // FONT_SIZE_BODY

// ── Callout geometry — VERBATIM no-clip-by-construction logic (measure → wrap → shrink → clamp). ──
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

// ── Layout — measure every cell + header, reserve the widest cell width, center the grid; shrink to
//    fit, never clip. Returns geometry helpers cxOf/cyOf the renderer + invariants share. ──
type MatrixGridLayout = {
  cellW: number;
  cellH: number;
  originX: number;
  originY: number;
  rowHdrW: number;
  headerH: number;
  gridFont: number;
  rows: number;
  cols: number;
  cxOf: (col: number) => number;
  cyOf: (row: number) => number;
};

function computeLayout(data: MatrixGridData): MatrixGridLayout {
  const { rows, cols, colHeaders, rowHeaders } = data;
  // 1. widest cell string across ALL materialized grids (fold the writes) + widest header.
  const frames = framesFromMatrixGridData(data);
  let widest = measureLabelWidth("0", GRID_FONT);
  for (const f of frames) {
    for (const r of f.state.grid) {
      for (const v of r) widest = Math.max(widest, measureLabelWidth(v, GRID_FONT));
    }
  }
  if (colHeaders) for (const h of colHeaders) widest = Math.max(widest, measureLabelWidth(h, GRID_FONT));
  let cellW = Math.max(CELL_MIN_W, widest + 2 * CELL_PAD_X);
  const cellH = CELL_MIN_H;

  // 2. row-header gutter width.
  const rowHdrW = rowHeaders
    ? Math.max(...rowHeaders.map((h) => measureLabelWidth(h, FONT_SIZE_LABEL))) + 8
    : 0;

  // 3. fit the grid into [4, SVG_W-4]; shrink cellW proportionally if too wide (degrade, never clip).
  let gridW = rowHdrW + cols * cellW;
  const maxGridW = SVG_W - 16;
  if (gridW > maxGridW) {
    cellW = Math.max(CELL_MIN_W / 2, (maxGridW - rowHdrW) / cols);
    gridW = rowHdrW + cols * cellW;
  }
  // cap font down to FONT_SIZE_LABEL if cells got tight.
  const gridFont = cellW < CELL_MIN_W ? FONT_SIZE_LABEL : GRID_FONT;

  // 4. vertical placement: grid (+ header band) centered in [GRID_TOP, VIEWBOX_H - ROWOP_BAND_H].
  const headerH = colHeaders ? HEADER_H : 0;
  const gridH = headerH + rows * cellH;
  const availTop = GRID_TOP;
  const availBot = VIEWBOX_H - ROWOP_BAND_H;
  const originYBase = availTop + Math.max(0, (availBot - availTop - gridH) / 2);
  const originY = originYBase + headerH; // cells start below the header band

  // 5. horizontal: center the grid; clamp originX ≥ 4.
  const originX = Math.max(4, (SVG_W - gridW) / 2);

  const cxOf = (col: number) => originX + rowHdrW + col * cellW + cellW / 2;
  const cyOf = (row: number) => originY + row * cellH + cellH / 2;

  return { cellW, cellH, originX, originY, rowHdrW, headerH, gridFont, rows, cols, cxOf, cyOf };
}

// ── renderFrame — geometry + measurement + data-* stamps (light/dark skins). ─────────────────────
function renderFrame(layout: MatrixGridLayout, data: MatrixGridData, variant: MgVariant = "light") {
  const { cellW, cellH, originX, originY, gridFont, rows, cols, cxOf, cyOf } = layout;
  const { colHeaders, rowHeaders } = data;
  const dark = variant === "dark";
  // Per-skin paint (literal hex, never var()).
  const headerInk = dark ? DARK.header : INK;
  const calloutInk = dark ? DARK.callout : INK;
  const rowOpInk = dark ? DARK.rowOp : INK;
  const cellInk = (isPivot: boolean) => (dark ? (isPivot ? DARK.pivotInk : DARK.ink) : INK);
  const cellFill = (isPivot: boolean, isFilled: boolean) =>
    dark
      ? isPivot
        ? DARK.pivotFill
        : isFilled
          ? DARK.filledFill
          : DARK.emptyFill
      : isPivot
        ? ACCENT
        : isFilled
          ? HATCH_LIGHT
          : PAPER;
  const cellStroke = (isPivot: boolean) => (dark ? (isPivot ? DARK.pivotRule : DARK.rule) : INK);
  return (frame: Frame<MatrixGridState>): ReactNode => {
    const st = frame.state;

    // callout anchored to the pivot cell's column (NOT a detached footer).
    const anchorCx = st.pivot ? cxOf(st.pivot.col) : SVG_W / 2;
    const { lines, font, maxLineW } = layoutCallout(st.callout);
    const half = maxLineW / 2;
    const calloutCx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, anchorCx));

    return (
      <>
        {/* ── COL HEADERS (above the grid) ── */}
        {colHeaders &&
          colHeaders.map((h, c) => (
            <text
              key={`colh-${c}`}
              x={cxOf(c)}
              y={originY - 4}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={Math.min(gridFont, FONT_SIZE_LABEL)}
              fontWeight={700}
              fill={headerInk}
            >
              {h}
            </text>
          ))}

        {/* ── ROW HEADERS (left of the grid) ── */}
        {rowHeaders &&
          rowHeaders.map((h, r) => (
            <text
              key={`rowh-${r}`}
              x={originX + 4}
              y={cyOf(r)}
              textAnchor="start"
              dominantBaseline="central"
              fontFamily={FONT_FAMILY}
              fontSize={FONT_SIZE_LABEL}
              fontWeight={700}
              fill={headerInk}
            >
              {h}
            </text>
          ))}

        {/* ── CELLS — one <g> per cell, stamping the four data-* attrs invariants read. ── */}
        {Array.from({ length: rows }, (_, r) =>
          Array.from({ length: cols }, (_, c) => {
            const key = `${r},${c}`;
            const isFilled = st.filled.includes(key);
            const isPivot = st.pivot != null && st.pivot.row === r && st.pivot.col === c;
            return (
              <g
                key={key}
                data-cell-row={r}
                data-cell-col={c}
                data-cell-value={st.grid[r][c]}
                data-cell-fill={isFilled ? "1" : "0"}
                transform={`translate(${cxOf(c)},${cyOf(r)})`}
              >
                <rect
                  x={-cellW / 2}
                  y={-cellH / 2}
                  width={cellW}
                  height={cellH}
                  fill={cellFill(isPivot, isFilled)}
                  stroke={cellStroke(isPivot)}
                  strokeWidth={isPivot ? STROKE_FOCUS : STROKE_DEFAULT}
                />
                <text
                  textAnchor="middle"
                  dominantBaseline="central"
                  fontFamily={FONT_FAMILY}
                  fontSize={gridFont}
                  fill={cellInk(isPivot)}
                >
                  {st.grid[r][c]}
                </text>
              </g>
            );
          }),
        )}

        {/* ── PIVOT STAMP — a zero-area marker so the pivot-existence invariant reads it
            independent of cell color. ── */}
        {st.pivot && <g data-pivot-row={st.pivot.row} data-pivot-col={st.pivot.col} />}

        {/* ── rowOp RAIL (bottom band) ── */}
        {st.rowOp &&
          (() => {
            const w = measureLabelWidth(st.rowOp, FONT_SIZE_LABEL);
            let f = FONT_SIZE_LABEL;
            if (w > CALLOUT_MAX_W) f = Math.max(CALLOUT_MIN_FONT, Math.floor((FONT_SIZE_LABEL * CALLOUT_MAX_W) / w));
            const hw = measureLabelWidth(st.rowOp, f) / 2;
            const rx = Math.max(CALLOUT_PAD + hw, Math.min(SVG_W - CALLOUT_PAD - hw, SVG_W / 2));
            return (
              <text
                x={rx}
                y={VIEWBOX_H - 5}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={f}
                fontWeight={700}
                fill={rowOpInk}
                opacity={0.75}
              >
                {st.rowOp}
              </text>
            );
          })()}

        {/* ── CALLOUT — element-anchored to the pivot column, measured → wrapped → clamped (no-clip).
            Lives in [0, CALLOUT_BAND_H], disjoint from the grid band → no overlap by construction. ── */}
        <text
          x={calloutCx}
          y={14}
          textAnchor="middle"
          fontFamily={FONT_FAMILY}
          fontSize={font}
          fill={calloutInk}
        >
          {lines.map((ln, i) => (
            <tspan key={i} x={calloutCx} dy={i === 0 ? 0 : CALLOUT_LINE_H}>
              {ln}
            </tspan>
          ))}
        </text>
      </>
    );
  };
}

/** ADDITIVE props — `variant` selects the render skin (default "light", the demo/e2e/harness baseline)
 *  and `layout` lets the lesson surface drop the white box + side controls so the figure floats on the
 *  dark page. Both omitted by the gallery + harness, unchanged. `testIdPrefix` is the pre-existing carry. */
export type MatrixGridFamilyProps = FamilyRendererProps & {
  testIdPrefix?: string;
  variant?: MgVariant;
  layout?: ShellLayout;
};

export function MatrixGridFamily({
  instanceId,
  dataJson,
  language,
  labels,
  onStep,
  testIdPrefix = "mg",
  variant = "light",
  layout: shellLayout,
}: MatrixGridFamilyProps): ReactNode {
  const data = useMemo(() => parseMatrixGridData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromMatrixGridData(data), [data]);
  const layout = useMemo(() => computeLayout(data), [data]);
  const render = useMemo(() => renderFrame(layout, data, variant), [layout, data, variant]);
  const lastIdx = Math.max(0, frames.length - 1);
  // STABLE onStep (useCallback) — a fresh arrow every render re-fires the shell's onStep effect →
  // setState → re-render → new arrow → infinite render loop (GraphTreeFamily.tsx:288).
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  return (
    <AlgoStepperShell<MatrixGridState>
      title={`Matrix/grid · ${instanceId}`}
      desc={language === "ro" ? "Tabel/matrice pas cu pas" : "Step-through grid"}
      frames={frames}
      renderFrame={render}
      testIdPrefix={testIdPrefix}
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
      layout={shellLayout}
    />
  );
}
