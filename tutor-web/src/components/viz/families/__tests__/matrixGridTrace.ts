/**
 * Plan-V family 3 (INV-5.1 / INV-5.2) — an INDEPENDENT matrix/grid reference oracle, used by the
 * trace-match harness to recompute the expected per-step rendered table from the instance's SEED.
 * This is the ORACLE: the harness asserts the family's frames equal what this computes, so the family
 * cannot animate a table the algorithm never produces.
 *
 * Every derivation reads only `seed` (+ `kind`), NOT a pinned literal, so it generalizes across
 * instances. One reference frame per step: `{ table, filled, pivot }`.
 */

import type { CellCoord, MatrixGridData } from "../MatrixGridFamily";

export type MatrixGridRefStep = { table: string[][]; filled: string[]; pivot: CellCoord | null };

export function matrixGridReference(data: MatrixGridData): MatrixGridRefStep[] {
  switch (data.kind) {
    case "dp-fill":
      return dpFillReference(data);
    case "gauss-elim":
      return gaussElimReference(data);
    case "page-table":
      return pageTableReference(data);
  }
}

// ── dp-fill oracle (fib DP) ──────────────────────────────────────────────────────────────────────
// Runs the real bottom-up fib recurrence from `seed.n`, filling dp[k] one cell per step left→right.
function dpFillReference(data: MatrixGridData): MatrixGridRefStep[] {
  const n = (data.seed as { n: number }).n;
  const dp: number[] = [];
  const table = Array.from({ length: data.rows }, () => Array.from({ length: data.cols }, () => ""));
  const filled = new Set<string>();
  const out: MatrixGridRefStep[] = [];
  for (let k = 0; k <= n; k++) {
    dp[k] = k < 2 ? k : dp[k - 1] + dp[k - 2]; // REAL fib recurrence
    table[0][k] = String(dp[k]);
    filled.add(`0,${k}`);
    out.push({ table: table.map((r) => [...r]), filled: [...filled].sort(), pivot: { row: 0, col: k } });
  }
  return out;
}

// ── gauss-elim oracle (forward elimination with partial pivoting) ──────────────────────────────────
// Runs the algorithm from alo-study-guide-gauss.md on `seed.matrix` (R×C augmented). Emits a reference
// frame per pivot/elimination/swap step. The number of emitted steps + their cell writes are
// deterministic from the matrix, so the authored `steps` MUST match.
function gaussElimReference(data: MatrixGridData): MatrixGridRefStep[] {
  const M = (data.seed as { matrix: number[][] }).matrix.map((r) => [...r]);
  const R = data.rows, C = data.cols;
  const fmt = (x: number) => (Number.isInteger(x) ? String(x) : String(+x.toFixed(4)));
  const table = M.map((r) => r.map(fmt));
  const filled = new Set<string>();
  const out: MatrixGridRefStep[] = [];
  const snap = (pivot: CellCoord | null) =>
    out.push({ table: table.map((r) => [...r]), filled: [...filled].sort(), pivot });
  // initial frame: whole matrix shown, nothing eliminated yet
  for (let r = 0; r < R; r++) for (let c = 0; c < C; c++) filled.add(`${r},${c}`);
  snap(null);
  for (let r = 0; r < R - 1; r++) {
    // partial pivot: if M[r][r]==0 swap with a lower row having a nonzero col-r
    if (M[r][r] === 0) {
      let s = -1;
      for (let k = r + 1; k < R; k++) if (M[k][r] !== 0) { s = k; break; }
      if (s >= 0) {
        [M[r], M[s]] = [M[s], M[r]];
        for (let c = 0; c < C; c++) { table[r][c] = fmt(M[r][c]); table[s][c] = fmt(M[s][c]); }
        snap({ row: r, col: r });
      }
    }
    snap({ row: r, col: r }); // pivot-select beat
    for (let i = r + 1; i < R; i++) {
      const f = -M[i][r] / M[r][r];
      for (let c = r; c < C; c++) { M[i][c] = M[i][c] + f * M[r][c]; table[i][c] = fmt(M[i][c]); }
      snap({ row: r, col: r }); // elimination beat (row i updated)
    }
  }
  return out;
}

// ── page-table oracle (DEFERRED — no SO-RC corpus/KC exists yet) ───────────────────────────────────
function pageTableReference(_data: MatrixGridData): MatrixGridRefStep[] {
  // DEFERRED: no SO-RC corpus/KC exists yet (content/SO-RC/kcs/ empty). When authored, walk seed.pte
  // for the declared vpn sequence, lighting PT rows as visited, value "→pfnX" | "INVALID".
  throw new Error("page-table reference not yet implemented (SO-RC corpus deferred)");
}
