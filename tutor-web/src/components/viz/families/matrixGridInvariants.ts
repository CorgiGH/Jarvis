/**
 * Plan-V family 3 — MatrixGrid semantic invariants (INV-5.2), mirrors sequenceArrayInvariants.ts.
 *
 * Each invariant receives the MOUNTED family's DOM (via data-cell-row/col/value/fill + transform
 * stamps, plus the data-pivot-row/col stamp) plus the current frame state and asserts a semantic
 * property of the RENDERED SVG geometry — read truth back from the pixels, not from the model. All
 * are exported as MATRIX_GRID_INVARIANTS so the trace-match harness registers them with the family.
 *
 * The geometry the renderer stamps:
 *   <g data-cell-row="r" data-cell-col="c" data-cell-value="…" data-cell-fill="0|1"
 *      transform="translate(cx,cy)"><rect/><text/></g>   one per cell
 *   <g data-pivot-row="r" data-pivot-col="c" />                                pivot marker (zero-area)
 *
 * The 5 from recon §3 (Family 3 — matrix/grid):
 *   1 grid-rectangularity (every row has `cols` cells; columns x-aligned across rows)
 *   2 row-monotone-y / col-monotone-x (increasing row ⇒ ↑y, increasing col ⇒ ↑x)
 *   3 dp-fill-order (filled set monotone-growing; rendered fill == model fill)
 *   4 per-cell trace-match (each rendered cell value == reference table value)
 *   5 pivot-existence (pivot stamp present iff model pivot, naming a real cell)
 */

import type { Frame } from "../AlgoStepperShell";
import type { MatrixGridState } from "./MatrixGridFamily";

export type InvariantResult = { ok: true } | { ok: false; message: string };
export type MatrixGridInvariant = (
  container: Element,
  frame: Frame<MatrixGridState>,
  stepIdx: number,
  allFrames: Frame<MatrixGridState>[],
) => InvariantResult;

type RenderedCell = { row: number; col: number; x: number; y: number; value: string; fill: boolean };

function readCells(container: Element): RenderedCell[] {
  const groups = Array.from(container.querySelectorAll<SVGGElement>("g[data-cell-row][data-cell-col]"));
  const out: RenderedCell[] = [];
  for (const g of groups) {
    const row = parseInt(g.getAttribute("data-cell-row") ?? "NaN", 10);
    const col = parseInt(g.getAttribute("data-cell-col") ?? "NaN", 10);
    const m = /translate\(([-\d.]+),\s*([-\d.]+)\)/.exec(g.getAttribute("transform") ?? "");
    if (!m || Number.isNaN(row) || Number.isNaN(col)) continue;
    out.push({
      row,
      col,
      x: parseFloat(m[1]),
      y: parseFloat(m[2]),
      value: g.getAttribute("data-cell-value") ?? "",
      fill: g.getAttribute("data-cell-fill") === "1",
    });
  }
  return out;
}

// ── INV-1 — grid-rectangularity ───────────────────────────────────────────────────────────────────
export const gridRectangularity: MatrixGridInvariant = (container, frame, stepIdx) => {
  const cells = readCells(container);
  const { rows, cols } = frame.state;
  if (cells.length !== rows * cols)
    return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: rendered ${cells.length} cells, expected ${rows}×${cols}=${rows * cols}` };
  // per-row count == cols
  const byRow = new Map<number, RenderedCell[]>();
  for (const c of cells) {
    const g = byRow.get(c.row) ?? [];
    g.push(c);
    byRow.set(c.row, g);
  }
  for (const [r, rc] of byRow)
    if (rc.length !== cols)
      return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: row ${r} has ${rc.length} cells, expected ${cols}` };
  // column-x alignment: for each col, all rows share the same x (±0.5px)
  for (let c = 0; c < cols; c++) {
    const xs = cells.filter((k) => k.col === c).map((k) => k.x);
    if (xs.length > 0 && Math.max(...xs) - Math.min(...xs) > 0.5)
      return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: col ${c} x not aligned across rows (spread ${(Math.max(...xs) - Math.min(...xs)).toFixed(2)}px)` };
  }
  return { ok: true };
};

// ── INV-2 — row-monotone-y / col-monotone-x ────────────────────────────────────────────────────────
export const rowColMonotone: MatrixGridInvariant = (container, _frame, stepIdx) => {
  const cells = readCells(container);
  // col-monotone-x: along row 0, col k+1 x > col k x
  const r0 = cells.filter((c) => c.row === 0).sort((a, b) => a.col - b.col);
  for (let k = 1; k < r0.length; k++)
    if (!(r0[k].x > r0[k - 1].x))
      return { ok: false, message: `step ${stepIdx} col-monotone-x FAIL: col ${r0[k].col} (x=${r0[k].x}) not right of col ${r0[k - 1].col} (x=${r0[k - 1].x})` };
  // row-monotone-y: along col 0, row k+1 y > row k y
  const c0 = cells.filter((c) => c.col === 0).sort((a, b) => a.row - b.row);
  for (let k = 1; k < c0.length; k++)
    if (!(c0[k].y > c0[k - 1].y))
      return { ok: false, message: `step ${stepIdx} row-monotone-y FAIL: row ${c0[k].row} (y=${c0[k].y}) not below row ${c0[k - 1].row} (y=${c0[k - 1].y})` };
  return { ok: true };
};

// ── INV-3 — dp-fill-order (filled set monotone-growing) ────────────────────────────────────────────
export const dpFillOrder: MatrixGridInvariant = (container, frame, stepIdx, allFrames) => {
  const cells = readCells(container);
  const renderedFilled = new Set(cells.filter((c) => c.fill).map((c) => `${c.row},${c.col}`));
  // (a) rendered fill set == this frame's model filled set (pixels match model)
  const modelFilled = new Set(frame.state.filled);
  for (const k of modelFilled)
    if (!renderedFilled.has(k))
      return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: model cell ${k} is filled but rendered data-cell-fill="0"` };
  for (const k of renderedFilled)
    if (!modelFilled.has(k))
      return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: rendered cell ${k} filled but model says unfilled` };
  // (b) monotone: previous frame's filled set ⊆ this one (never un-fills)
  if (stepIdx > 0) {
    const prev = new Set(allFrames[stepIdx - 1].state.filled);
    for (const k of prev)
      if (!renderedFilled.has(k))
        return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: cell ${k} was filled at step ${stepIdx - 1} but is now UN-filled` };
  }
  return { ok: true };
};

// ── INV-4 — per-cell trace-match (each rendered value == reference table) ──────────────────────────
export const perCellTraceMatch: MatrixGridInvariant = (container, frame, stepIdx) => {
  const cells = readCells(container);
  for (const c of cells) {
    const expected = frame.state.grid[c.row][c.col];
    if (c.value !== expected)
      return { ok: false, message: `step ${stepIdx} per-cell-trace-match FAIL: cell (${c.row},${c.col}) renders "${c.value}" but reference table has "${expected}"` };
  }
  return { ok: true };
};

// ── INV-5 — pivot-existence (pivot stamp present iff model pivot, naming a real cell) ──────────────
export const pivotExistence: MatrixGridInvariant = (container, frame, stepIdx) => {
  const pivotEl = container.querySelector("g[data-pivot-row][data-pivot-col]");
  if (frame.state.pivot == null) {
    if (pivotEl) return { ok: false, message: `step ${stepIdx} pivot-existence FAIL: model has no pivot but a data-pivot stamp was rendered` };
    return { ok: true };
  }
  if (!pivotEl)
    return { ok: false, message: `step ${stepIdx} pivot-existence FAIL: model pivot (${frame.state.pivot.row},${frame.state.pivot.col}) but NO data-pivot stamp rendered` };
  const pr = parseInt(pivotEl.getAttribute("data-pivot-row") ?? "NaN", 10);
  const pc = parseInt(pivotEl.getAttribute("data-pivot-col") ?? "NaN", 10);
  const cells = readCells(container);
  if (!cells.some((c) => c.row === pr && c.col === pc))
    return { ok: false, message: `step ${stepIdx} pivot-existence FAIL: pivot (${pr},${pc}) has no rendered cell` };
  if (pr !== frame.state.pivot.row || pc !== frame.state.pivot.col)
    return { ok: false, message: `step ${stepIdx} pivot-existence FAIL: rendered pivot (${pr},${pc}) ≠ model pivot (${frame.state.pivot.row},${frame.state.pivot.col})` };
  return { ok: true };
};

export const MATRIX_GRID_INVARIANTS: MatrixGridInvariant[] = [
  gridRectangularity,
  rowColMonotone,
  dpFillOrder,
  perCellTraceMatch,
  pivotExistence,
];
