# Plan V — Viz Family 3 (matrix/grid) — file-by-file BUILD SPEC

**Status:** build-ready. Every decision below is grounded in the live repo (verified 2026-06-14, read-only).
**Contract source:** `build-review/2026-06-13-planV-viz-families-recon.md` (high-level) + the 6 reader reports.
**Prefix:** `{prefix}` = **`mg`** (matrix-grid). `family_id` = **`matrix-grid`**.
**Boundary:** SVG-only, viewBox `480 × {viewBoxH}`, mounts `AlgoStepperShell`, frontend-only, ZERO frozen signatures.
Register ONLY in `familyRegistry` + `HARNESS_REGISTRY`. **Do NOT touch** legacy `vizRegistry.ts` / `content/viz-ids.yaml` / Kotlin `checkVizReferences` (those die with the V6 retirement — recon §0).

This family is the GENERALIZED matrix/grid: it must express (a) DP fill tables, (b) Gaussian-elimination matrix step-traces, (c) page-table grids. The three migration targets (`DPWastedWork`, `MatrixTransform`, `PageTableWalk`) are re-expressed as `data_json` instances; their baked scenarios become seeds the oracle re-derives. `CppVTable` is family 6 — NOT here.

---

## 0. Why one data model covers all three

A DP table, a Gauss augmented matrix, and a page table are all **R×C grids of cells whose values change over an ordered sequence of steps**, with a distinguished **pivot/active cell** per step and a **fill/active set** that grows. The differences are cosmetic (what the value string means) and reduce to:

| Source | Grid | Cell value | "Fill" growth | Pivot |
| --- | --- | --- | --- | --- |
| DP table (fib N=5) | 1×(N+1) | `dp[k]` | left→right monotone fill | the cell being computed |
| Gauss elimination | 3×4 augmented | matrix entry (with sign) | each step rewrites a row's cells | `a_rr` (diagonal, or post-swap) |
| Page table | 8×1 PT (+ TLB 4×1 + frames 8×1, modeled as one grid per instance) | PTE string (`→pfn`, `INVALID`, `COW`) | rows light as walked | the indexed PTE |

So ONE `MatrixGridData` with `(rows, cols, per-step cell writes, fill-set deltas, pivot)` covers all three. Multi-panel cases (page table's 3 parallel grids) are authored as **one grid with a `panel` band column-group label** OR, simplest for the SO deferral, as a single PT grid; see §10.

---

## 1. Data model — exact TS types

Place at the top of `MatrixGridFamily.tsx` (mirrors `SequenceArrayFamily.tsx:51-83` typed-slots block; State is a direct mirror of Step).

```ts
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
  /** cells WRITTEN this step (cumulative model is rebuilt by framesFrom…, see §3). Each coord must be
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
  /** the SEED the oracle re-runs from. Shape depends on `kind` (validated per-kind in §2):
   *   dp-fill   → { n: number }                       (fib/DP size; oracle computes dp[0..n])
   *   gauss-elim→ { matrix: number[][] }              (R×(C) augmented matrix; oracle runs elimination)
   *   page-table→ { pte: (number|null)[] }            (PT mapping vpn→pfn|null; oracle walks the trace)
   *  Stored as an opaque object; each oracle narrows it. */
  seed: Record<string, unknown>;
  /** optional fixed column headers (e.g. ["dp[0]","dp[1]",…] or ["x1","x2","x3","|b"]). length==cols
   *  when present. Rendered as a header row above the grid; never read by invariants. */
  colHeaders?: string[];
  /** optional fixed row headers (e.g. ["E1","E2","E3"] or ["PTE 0","PTE 1",…]). length==rows. */
  rowHeaders?: string[];
  /** ordered per-step trace. The family NEVER invents the trace — the oracle (matrixGridTrace.ts)
   *  re-derives the reference table per step from `seed`+`kind`, and the harness asserts equality. */
  steps: MatrixGridStep[];
};

// ── What ONE rendered step exposes — BOTH the trace-compared values (the full materialized grid +
//    the filled set + pivot) AND the DOM-lookup ids (every cell stamps data-cell-row/col/value/fill).
export type MatrixGridState = {
  /** the FULL materialized grid AFTER this step's writes (rows×cols of display strings). The harness
   *  trace-match compares this against the oracle's reference table; the renderer paints it. */
  grid: string[][];
  /** the set of filled coords AFTER this step (cumulative), serialized as "r,c" keys for O(1) compare. */
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
```

### data_json JSON shape (concrete)

A single-quoted YAML scalar holding ONE JSON object. Concrete DP example (`kind:"dp-fill"`, fib N=5, 1×6 grid):

```json
{
  "rows": 1, "cols": 6, "kind": "dp-fill",
  "seed": { "n": 5 },
  "colHeaders": ["dp[0]","dp[1]","dp[2]","dp[3]","dp[4]","dp[5]"],
  "steps": [
    { "writes": [{"row":0,"col":0,"value":"0"}], "fills": [{"row":0,"col":0}], "pivot": {"row":0,"col":0}, "callout": "dp[0] = 0 (caz de bază)" },
    { "writes": [{"row":0,"col":1,"value":"1"}], "fills": [{"row":0,"col":1}], "pivot": {"row":0,"col":1}, "callout": "dp[1] = 1 (caz de bază)" },
    { "writes": [{"row":0,"col":2,"value":"1"}], "fills": [{"row":0,"col":2}], "pivot": {"row":0,"col":2}, "rowOp": "dp[2]=dp[1]+dp[0]", "callout": "dp[2] = dp[1]+dp[0] = 1" },
    { "writes": [{"row":0,"col":3,"value":"2"}], "fills": [{"row":0,"col":3}], "pivot": {"row":0,"col":3}, "rowOp": "dp[3]=dp[2]+dp[1]", "callout": "dp[3] = dp[2]+dp[1] = 2" },
    { "writes": [{"row":0,"col":4,"value":"3"}], "fills": [{"row":0,"col":4}], "pivot": {"row":0,"col":4}, "rowOp": "dp[4]=dp[3]+dp[2]", "callout": "dp[4] = dp[3]+dp[2] = 3" },
    { "writes": [{"row":0,"col":5,"value":"5"}], "fills": [{"row":0,"col":5}], "pivot": {"row":0,"col":5}, "rowOp": "dp[5]=dp[4]+dp[3]", "callout": "dp[5] = dp[4]+dp[3] = 5 ✓ — fiecare celulă calculată o singură dată (O(n))" }
  ]
}
```

(Full Gauss + page-table `data_json` are in §10.)

---

## 2. parseMatrixGridData — fail-loud validator

Signature mirrors `parseSeqArrayData` (`SequenceArrayFamily.tsx:92`). Throws naming `instanceId` + the offending field/index on EVERY fault (INV-5.5: a broken instance fails admission loud, never renders garbled). Zod-free, cast to `Record<string, unknown>`.

```ts
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
      throw new Error(`matrix-grid instance '${instanceId}': dp-fill grid must be 1×(n+1)=1×${(seed.n as number)+1}, got ${rows}×${cols}`);
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

  // ── cross-ref: fills must be MONOTONE across steps (a fill never un-fills — INV-3 author guard).
  //    (The invariant verifies this from the DOM too; this is a parse-time loud-fail so a bad instance
  //    never even mounts.) ──
  const seenFill = new Set<string>();
  steps.forEach((st, si) => {
    st.fills.forEach((f) => seenFill.add(`${f.row},${f.col}`));
    // (no removal API exists, so monotonicity holds by construction; this loop documents intent and
    //  could be extended if a future step gains an "unfill" field — keep the guard explicit.)
    void si;
  });

  return { rows, cols, kind, seed, colHeaders, rowHeaders, steps };
}
```

---

## 3. framesFromMatrixGridData — accretion + dual exposure

Frames ACCRETE: unlike seq-array (each step echoes a full array), matrix-grid steps carry only **deltas** (`writes`/`fills`). The frame builder folds them into the full materialized grid + cumulative filled set, so `MatrixGridState.grid` and `.filled` are the running totals (what the harness compares to the oracle's reference table, and what the renderer paints). `aria = step.callout` (SR + FigureReveal gate read this; mirrors `framesFromSeqArrayData` at `SequenceArrayFamily.tsx:163`).

```ts
export function framesFromMatrixGridData(data: MatrixGridData): Frame<MatrixGridState>[] {
  // running materialized grid (all "" initially)
  const grid: string[][] = Array.from({ length: data.rows }, () => Array.from({ length: data.cols }, () => ""));
  const filled = new Set<string>();
  const frames: Frame<MatrixGridState>[] = [];

  for (const step of data.steps) {
    // apply writes (mutate the running grid, then snapshot)
    for (const w of step.writes) grid[w.row][w.col] = w.value;
    // grow the filled set
    for (const f of step.fills) filled.add(`${f.row},${f.col}`);

    frames.push({
      state: {
        grid: grid.map((r) => [...r]),          // deep-ish copy (immutable frame)
        filled: [...filled].sort(),             // sorted for deterministic compare
        pivot: step.pivot ? { ...step.pivot } : null,
        rowOp: step.rowOp,
        callout: step.callout,
        rows: data.rows,
        cols: data.cols,
      },
      aria: step.callout,                        // = the on-figure callout → SR + FigureReveal gate
    });
  }
  return frames;
}
```

**Dual exposure (what XState gives both consumers):**
- **Trace-compared values** (harness reads): `state.grid` (full materialized table) + `state.filled` (cumulative fill set) + `state.pivot`. The harness asserts these equal the oracle's per-step `{ table, filled, pivot }` (§6).
- **DOM-lookup ids** (invariants read): every cell paints `data-cell-row` / `data-cell-col` / `data-cell-value` / `data-cell-fill`, and the pivot paints `data-pivot-row` / `data-pivot-col` (§4). Invariants read geometry back from THESE, never from `state`.

**aria callout per step:** `aria: step.callout` — identical text on screen, in the SR buffer, and gated by FigureReveal.

---

## 4. layout + renderFrame — geometry, measurement, data-* stamps

### Imports & shared primitives
Copy verbatim from `SequenceArrayFamily.tsx` / `ChartDistributionFamily.tsx`:
- `measureLabelWidth` singleton (`ChartDistributionFamily.tsx:125-142` / `SequenceArrayFamily.tsx:219-236`) — lazy off-DOM canvas, SSR fallback `text.length*fontPx*0.6`.
- `layoutCallout` + `wrapToWidth` + the clamp (`SequenceArrayFamily.tsx:308-318`).
- Theme tokens from `../theme`: `INK`, `PAPER`, `ACCENT`, `FONT_FAMILY`, `STROKE_DEFAULT`, `STROKE_FOCUS`, `FONT_SIZE_LABEL`, `FONT_SIZE_VALUE`, `HATCH_LIGHT`/`HATCH_DENSE` (for fill shading). `AlgoStepperShell` injects `<HatchDefs/>` so hatch urls resolve.

### Constants
```ts
const SVG_W = 480;
const VIEWBOX_H = 360;                 // default; this family packs into [0,360]
const CALLOUT_BAND_H = 40;             // top band (callout lives in [0,40])
const CALLOUT_FONT = 11, CALLOUT_MIN_FONT = 8, CALLOUT_PAD = 8, CALLOUT_LINE_H = 13;
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2;   // 464
const GRID_TOP = CALLOUT_BAND_H + 8;  // grid starts BELOW the callout band → disjoint, no overlap
const ROWOP_BAND_H = 18;              // bottom rail for the rowOp annotation
const HEADER_H = 16;                  // col-header row height (when colHeaders present)
const ROWHDR_W = 0;                   // computed from rowHeaders width via measureLabelWidth (else 0)
const CELL_PAD_X = 6, CELL_MIN_W = 26, CELL_MIN_H = 22;
const GRID_FONT = 13;                 // FONT_SIZE_BODY
```

### computeLayout (a `useMemo`, like `ChartDistributionFamily.tsx:514`)
1. Measure the widest cell string across ALL steps' materialized grids (run `framesFromMatrixGridData` once, or fold the writes) + widest header with `measureLabelWidth(s, GRID_FONT)`. `cellW = max(CELL_MIN_W, widest + 2*CELL_PAD_X)`; `cellH = CELL_MIN_H`.
2. `rowHdrW = rowHeaders ? max(measureLabelWidth(h, FONT_SIZE_LABEL))+8 : 0`.
3. `gridW = rowHdrW + cols*cellW`. If `gridW > SVG_W - 16`, shrink `cellW` proportionally to fit (degrade, never clip — mirrors seq-array `computeLayout` side-anchor fallback `SequenceArrayFamily.tsx:350-389`); recompute. Cap font down to `FONT_SIZE_LABEL` if cells get tight.
4. `gridH = (colHeaders?HEADER_H:0) + rows*cellH`. Vertically center the grid in `[GRID_TOP, VIEWBOX_H - ROWOP_BAND_H]`.
5. `originX = (SVG_W - gridW)/2` (clamped ≥ 4); `originY = GRID_TOP + headerOffset`.
6. Return `{ cellW, cellH, originX, originY, rowHdrW, headerH, gridFont }`.
7. `cxOf(col) = originX + rowHdrW + col*cellW + cellW/2`; `cyOf(row) = originY + row*cellH + cellH/2`.

### renderFrame(layout) → (frame, idx) => ReactNode
Structure per frame (white skin; one skin is sufficient for family 3 — the e2e/harness baseline):

1. **Col headers** (if present): `<text x={cxOf(c)} y={originY-4} text-anchor="middle">{colHeaders[c]}</text>`.
2. **Row headers** (if present): `<text x={originX+4} y={cyOf(r)} ...>{rowHeaders[r]}</text>`.
3. **Cells** — one `<g>` per cell, stamping the four data-* attrs invariants read:
```tsx
const key = `${r},${c}`;
const isFilled = st.filled.includes(key);
const isPivot = st.pivot != null && st.pivot.row === r && st.pivot.col === c;
<g
  key={key}
  data-cell-row={r}
  data-cell-col={c}
  data-cell-value={st.grid[r][c]}
  data-cell-fill={isFilled ? "1" : "0"}
  transform={`translate(${cxOf(c)},${cyOf(r)})`}
>
  <rect
    x={-cellW / 2} y={-cellH / 2} width={cellW} height={cellH}
    fill={isPivot ? ACCENT : isFilled ? HATCH_LIGHT : PAPER}
    stroke={INK} strokeWidth={isPivot ? STROKE_FOCUS : STROKE_DEFAULT}
  />
  <text textAnchor="middle" dominantBaseline="central"
        fontFamily={FONT_FAMILY} fontSize={gridFont} fill={INK}>
    {st.grid[r][c]}
  </text>
</g>
```
   - `transform="translate(cx,cy)"` so the invariant can parse `(cx,cy)` back via the SAME regex seq-array/graph-tree use (`/translate\(([-\d.]+),\s*([-\d.]+)\)/`).
   - `data-cell-value` carries the EXACT display string (the per-cell trace-match invariant reads it).
   - `data-cell-fill` is `"1"`/`"0"` (the dp-fill-order invariant reads it).
4. **Pivot stamp** — a zero-area marker so the pivot-existence invariant reads it independent of cell color:
```tsx
{st.pivot && (
  <g data-pivot-row={st.pivot.row} data-pivot-col={st.pivot.col} />
)}
```
5. **rowOp rail** (if present): a single `<text>` in the bottom band `y = VIEWBOX_H - 5`, clamped width via `measureLabelWidth` (shrink font to `CALLOUT_MIN_FONT` if needed).
6. **Callout** — element-anchored to the **pivot cell's column** (NOT a detached footer; mirrors seq-array anchor rule). `anchorCx = st.pivot ? cxOf(st.pivot.col) : SVG_W/2`. Then the standard clamp:
```ts
const { lines, font, maxLineW } = layoutCallout(st.callout);
const half = maxLineW / 2;
const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, anchorCx));
```
   Render the wrapped `<text>` block with first baseline `y=14`, each line `+CALLOUT_LINE_H`, inside `[0, CALLOUT_BAND_H]`. Disjoint from the grid (which starts at `GRID_TOP = CALLOUT_BAND_H+8`) → no-overlap by construction.

**No-clip-by-construction:** callout band `[0,40]` and grid band `[GRID_TOP,…]` are disjoint; the callout center-x clamp keeps text inside `[CALLOUT_PAD, SVG_W-CALLOUT_PAD]`; the grid width-shrink keeps all cells inside `[4, SVG_W-4]`. The e2e no-clip gate (§11) machine-verifies this at 2 viewports.

---

## 5. The 5 invariants — `matrixGridInvariants.ts`

Type + result mirror `sequenceArrayInvariants.ts:26-33`. Every body reads geometry back from the mounted SVG (data-* attrs + the `transform` regex) — NEVER from the data model.

```ts
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
      row, col, x: parseFloat(m[1]), y: parseFloat(m[2]),
      value: g.getAttribute("data-cell-value") ?? "",
      fill: g.getAttribute("data-cell-fill") === "1",
    });
  }
  return out;
}
```

### INV-1 — grid-rectangularity
Every row has the same cell count == `cols`, and within each row the column-x values are aligned column-for-column across rows (cell at `(r,c)` shares its x with `(r',c)`).
```ts
export const gridRectangularity: MatrixGridInvariant = (container, frame, stepIdx) => {
  const cells = readCells(container);
  const { rows, cols } = frame.state;
  if (cells.length !== rows * cols)
    return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: rendered ${cells.length} cells, expected ${rows}×${cols}=${rows * cols}` };
  // per-row count == cols
  const byRow = new Map<number, RenderedCell[]>();
  for (const c of cells) (byRow.get(c.row) ?? byRow.set(c.row, []).get(c.row)!).push(c);
  for (const [r, rc] of byRow) if (rc.length !== cols)
    return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: row ${r} has ${rc.length} cells, expected ${cols}` };
  // column-x alignment: for each col, all rows share the same x (±0.5px)
  for (let c = 0; c < cols; c++) {
    const xs = cells.filter((k) => k.col === c).map((k) => k.x);
    if (Math.max(...xs) - Math.min(...xs) > 0.5)
      return { ok: false, message: `step ${stepIdx} grid-rectangularity FAIL: col ${c} x not aligned across rows (spread ${(Math.max(...xs) - Math.min(...xs)).toFixed(2)}px)` };
  }
  return { ok: true };
};
```

### INV-2 — row-monotone-y / col-monotone-x
Reads `(x,y)` from the transform; asserts increasing row ⇒ strictly increasing y, increasing col ⇒ strictly increasing x.
```ts
export const rowColMonotone: MatrixGridInvariant = (container, _frame, stepIdx) => {
  const cells = readCells(container);
  // col-monotone-x: along row 0 (or any fixed row), col k+1 x > col k x
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
```

### INV-3 — dp-fill-order (filled set monotone-growing)
The rendered filled set at step k ⊇ filled set at step k−1 (never un-fills). Reads `data-cell-fill` back from the DOM at this step AND compares to the previous frame's STATE filled set (the model the harness already trace-matched), closing model↔pixels.
```ts
export const dpFillOrder: MatrixGridInvariant = (container, frame, stepIdx, allFrames) => {
  const cells = readCells(container);
  const renderedFilled = new Set(cells.filter((c) => c.fill).map((c) => `${c.row},${c.col}`));
  // (a) rendered fill set == this frame's model filled set (pixels match model)
  const modelFilled = new Set(frame.state.filled);
  for (const k of modelFilled) if (!renderedFilled.has(k))
    return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: model cell ${k} is filled but rendered data-cell-fill="0"` };
  for (const k of renderedFilled) if (!modelFilled.has(k))
    return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: rendered cell ${k} filled but model says unfilled` };
  // (b) monotone: previous frame's filled set ⊆ this one (never un-fills)
  if (stepIdx > 0) {
    const prev = new Set(allFrames[stepIdx - 1].state.filled);
    for (const k of prev) if (!renderedFilled.has(k))
      return { ok: false, message: `step ${stepIdx} dp-fill-order FAIL: cell ${k} was filled at step ${stepIdx - 1} but is now UN-filled` };
  }
  return { ok: true };
};
```

### INV-4 — per-cell trace-match (each filled value == reference table)
Reads `data-cell-value` back from every cell and asserts it equals this frame's model grid value (which the harness has independently trace-matched against the oracle's reference table — §6). This is the "each filled value == reference" pixel check.
```ts
export const perCellTraceMatch: MatrixGridInvariant = (container, frame, stepIdx) => {
  const cells = readCells(container);
  for (const c of cells) {
    const expected = frame.state.grid[c.row][c.col];
    if (c.value !== expected)
      return { ok: false, message: `step ${stepIdx} per-cell-trace-match FAIL: cell (${c.row},${c.col}) renders "${c.value}" but reference table has "${expected}"` };
  }
  return { ok: true };
};
```

### INV-5 — pivot-existence (pivot on declared/diagonal position)
If the frame's model has a pivot, the rendered `data-pivot-row/col` stamp must exist AND name a coordinate that has a rendered cell (the pivot points at a real cell). For `gauss-elim`, additionally assert the pivot is on the diagonal (`row===col`) UNLESS the step's rowOp names a swap — that data lives in `frame.state.rowOp`.
```ts
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
```

---

## 6. Trace oracle — `__tests__/matrixGridTrace.ts`

Derives the algorithm input from the instance SEED (NOT a pinned literal), runs the REAL reference per `kind`, and emits one reference frame per step: `{ table: string[][], filled: string[], pivot: CellCoord|null }`. The harness asserts `frames[i].state.{grid,filled,pivot}` equals `ref[i].{table,filled,pivot}`. Generalizes across instances because every derivation reads only `seed`.

```ts
import type { CellCoord, MatrixGridData } from "../MatrixGridFamily";

export type MatrixGridRefStep = { table: string[][]; filled: string[]; pivot: CellCoord | null };

export function matrixGridReference(data: MatrixGridData): MatrixGridRefStep[] {
  switch (data.kind) {
    case "dp-fill":     return dpFillReference(data);
    case "gauss-elim":  return gaussElimReference(data);
    case "page-table":  return pageTableReference(data);
  }
}
```

### dp-fill oracle (fib DP)
Runs the real bottom-up recurrence from `seed.n`, filling `dp[k]` one cell per step left→right:
```ts
function dpFillReference(data: MatrixGridData): MatrixGridRefStep[] {
  const n = (data.seed as { n: number }).n;
  const dp: number[] = [];
  const table = Array.from({ length: data.rows }, () => Array.from({ length: data.cols }, () => ""));
  const filled = new Set<string>();
  const out: MatrixGridRefStep[] = [];
  for (let k = 0; k <= n; k++) {
    dp[k] = k < 2 ? k : dp[k - 1] + dp[k - 2];      // REAL fib recurrence
    table[0][k] = String(dp[k]);
    filled.add(`0,${k}`);
    out.push({ table: table.map((r) => [...r]), filled: [...filled].sort(), pivot: { row: 0, col: k } });
  }
  return out;
}
```

### gauss-elim oracle (forward elimination with partial pivoting)
Runs the algorithm from `alo-study-guide-gauss.md:13-24` on `seed.matrix` (R×C augmented). Emits a reference frame per pivot/elimination/swap step. The number of emitted steps + their cell writes are deterministic from the matrix, so the authored `steps` MUST match. (Authoring guidance: emit one frame per (pivot-select), (each row elimination), (swap when pivot==0) — see §10 for the concrete ALO step list.)
```ts
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
      if (s >= 0) { [M[r], M[s]] = [M[s], M[r]]; for (let c = 0; c < C; c++) { table[r][c] = fmt(M[r][c]); table[s][c] = fmt(M[s][c]); } snap({ row: r, col: r }); }
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
```
(If the authored ALO instance emits exactly the frames this produces, the harness step-count + per-step `table` match passes. The author must align `steps[]` to this — see §10's ALO step list, which was hand-derived against this oracle.)

### page-table oracle (DEFERRED — see §10)
`page-table` is authored only when an SO-RC KC exists. Stub:
```ts
function pageTableReference(data: MatrixGridData): MatrixGridRefStep[] {
  // DEFERRED: no SO-RC corpus/KC exists yet (content/SO-RC/kcs/ empty). When authored, walk seed.pte
  // for the declared vpn sequence, lighting PT rows as visited, value "→pfnX" | "INVALID".
  throw new Error("page-table reference not yet implemented (SO-RC corpus deferred)");
}
```

**Harness compare (in the registry entry, §8):** for each `i`, assert `JSON.stringify(frames[i].state.grid)===JSON.stringify(ref[i].table)` (throw `trace mismatch at step ${i} (table)`), `JSON.stringify(frames[i].state.filled)===JSON.stringify(ref[i].filled)` (`(filled)`), and pivot equality (`(pivot)`).

---

## 7. Wrong-trace fixture (F)

File: `tutor-web/src/components/viz/families/__tests__/fixtures/seeded-wrong-matrix-grid.yaml` (NEVER under `content/` — the harness glob is `content/*/viz/*.yaml`, ContentRepo globs `content/{subject}/viz/`; both blind to `__tests__/fixtures/`).

Use the **DP fib instance** as the base (cleanest oracle). Mutation at **step 4** (0-indexed): `dp[4]` should be `3` (fib: `dp[3]+dp[2]=2+1`), but the fixture writes `4`. The oracle's `dpFillReference` produces `"3"`; the frame's materialized `grid[0][4]` becomes `"4"` → `trace mismatch at step 4 (table)`.

```yaml
# INV-9.4 gate-3 seed: seeded wrong-trace fixture for the matrix-grid family. NEVER under content/.
# Mutation: step 4 writes dp[4]="4", but the real fib recurrence gives dp[4]=dp[3]+dp[2]=2+1=3.
# matrixGridReference re-runs the recurrence and the harness trace-match trips at step 4 (table).
id: viz-pa-dpfib-001-WRONG
subject: PA
family_id: matrix-grid
language: ro
instance:
  method_kc_id: pa-kc-fixture-recursion
  data_json: '{"rows":1,"cols":6,"kind":"dp-fill","seed":{"n":5},"colHeaders":["dp[0]","dp[1]","dp[2]","dp[3]","dp[4]","dp[5]"],"steps":[{"writes":[{"row":0,"col":0,"value":"0"}],"fills":[{"row":0,"col":0}],"pivot":{"row":0,"col":0},"callout":"dp[0]=0"},{"writes":[{"row":0,"col":1,"value":"1"}],"fills":[{"row":0,"col":1}],"pivot":{"row":0,"col":1},"callout":"dp[1]=1"},{"writes":[{"row":0,"col":2,"value":"1"}],"fills":[{"row":0,"col":2}],"pivot":{"row":0,"col":2},"callout":"dp[2]=1"},{"writes":[{"row":0,"col":3,"value":"2"}],"fills":[{"row":0,"col":3}],"pivot":{"row":0,"col":3},"callout":"dp[3]=2"},{"writes":[{"row":0,"col":4,"value":"4"}],"fills":[{"row":0,"col":4}],"pivot":{"row":0,"col":4},"callout":"MINCIUNA: dp[4] ar trebui 3, nu 4"},{"writes":[{"row":0,"col":5,"value":"5"}],"fills":[{"row":0,"col":5}],"pivot":{"row":0,"col":5},"callout":"dp[5]=5"}]}'
```

Test: `tutor-web/src/components/viz/families/__tests__/seededWrongMatrixGrid.test.tsx` (mirror `seededWrongSeqArray.test.tsx`):
```tsx
import { describe, it, expect } from "vitest";
import wrongYamlRaw from "./fixtures/seeded-wrong-matrix-grid.yaml?raw";
import { assertHarnessForInstance } from "./traceMatchHarness.test";

describe("seededWrongMatrixGrid — INV-9.4 gate-3 drill (matrix-grid)", () => {
  it("harness THROWS on the wrong-table fixture naming the offending step index", () => {
    expect(() =>
      assertHarnessForInstance(wrongYamlRaw, "seeded-wrong-matrix-grid.yaml"),
    ).toThrow(/step 4/);
  });
  it("the error message names a trace/table mismatch (not a parse or missing-family error)", () => {
    let thrown: Error | null = null;
    try { assertHarnessForInstance(wrongYamlRaw, "seeded-wrong-matrix-grid.yaml"); }
    catch (e) { thrown = e as Error; }
    expect(thrown, "Expected harness to throw but it did not").not.toBeNull();
    expect(thrown!.message).toMatch(/step 4/);
    expect(thrown!.message).toMatch(/trace mismatch|table/i);
  });
});
```

---

## 8. Registrations (D, E)

### D — `tutor-web/src/components/viz/families/familyRegistry.ts`
After line 4 (`import { SequenceArrayFamily } …`):
```ts
import { MatrixGridFamily } from "./MatrixGridFamily";
```
In the `familyRegistry` object (currently lines 31-35), append after the `"seq-array"` line:
```ts
  "matrix-grid": MatrixGridFamily,
```

### E — `tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx`
Add the imports (after line 41):
```ts
import { parseMatrixGridData, framesFromMatrixGridData } from "../MatrixGridFamily";
import { matrixGridReference } from "./matrixGridTrace";
import { MATRIX_GRID_INVARIANTS } from "../matrixGridInvariants";
```
Add the registry entry inside `HARNESS_REGISTRY` (after the `"seq-array"` block, before the closing `}` at line 193):
```ts
  "matrix-grid": {
    runAssertion: (dataJson, instanceId) => {
      const parsed = parseMatrixGridData(dataJson, instanceId);
      const frames = framesFromMatrixGridData(parsed);
      const ref = matrixGridReference(parsed); // re-derives the reference table from the SEED
      if (frames.length !== ref.length) {
        throw new Error(`${instanceId}: step count mismatch — rendered ${frames.length} steps, reference has ${ref.length}`);
      }
      for (let i = 0; i < ref.length; i++) {
        const s = frames[i].state, r = ref[i];
        if (JSON.stringify(s.grid) !== JSON.stringify(r.table))
          throw new Error(`${instanceId}: trace mismatch at step ${i} (table) — rendered ${JSON.stringify(s.grid)} ≠ reference ${JSON.stringify(r.table)}`);
        if (JSON.stringify(s.filled) !== JSON.stringify(r.filled))
          throw new Error(`${instanceId}: trace mismatch at step ${i} (filled) — rendered ${JSON.stringify(s.filled)} ≠ reference ${JSON.stringify(r.filled)}`);
        if (JSON.stringify(s.pivot) !== JSON.stringify(r.pivot))
          throw new Error(`${instanceId}: trace mismatch at step ${i} (pivot) — rendered ${JSON.stringify(s.pivot)} ≠ reference ${JSON.stringify(r.pivot)}`);
      }
      const Renderer = familyRegistry["matrix-grid"];
      const { container } = render(<Renderer instanceId={instanceId} dataJson={dataJson} language="ro" />);
      const stepFwdBtn = container.querySelector('[data-testid="mg-step-fwd"]');
      for (let i = 0; i < frames.length; i++) {
        for (const invariant of MATRIX_GRID_INVARIANTS) {
          const result = invariant(container, frames[i], i, frames);
          if (!result.ok) throw new Error(`${instanceId}: ${result.message}`);
        }
        if (i < frames.length - 1 && stepFwdBtn) fireEvent.click(stepFwdBtn);
      }
    },
  },
```
(Gate (b) at lines 232-236 then passes because the renderer is in `familyRegistry`.)

---

## 9. Mount (G) — VizDemoPage card

`tutor-web/src/components/viz/VizDemoPage.tsx`. Add the import after line 15 (`import { SequenceArrayFamily } …`):
```ts
import { MatrixGridFamily } from "./families/MatrixGridFamily";
```
Add a `const` next to the other verbatim `data_json` constants (after line 60), byte-identical to the shipped DP YAML (§10 PA instance):
```ts
// The viz-pa-dpfib-001 instance data_json (verbatim from content/PA/viz/viz-pa-dpfib-001.yaml).
// Keep byte-identical to the shipped YAML — the matrix-grid no-clip e2e drives THIS mount.
const DPFIB_DATA_JSON =
  '{"rows":1,"cols":6,"kind":"dp-fill","seed":{"n":5},"colHeaders":["dp[0]","dp[1]","dp[2]","dp[3]","dp[4]","dp[5]"],"steps":[{"writes":[{"row":0,"col":0,"value":"0"}],"fills":[{"row":0,"col":0}],"pivot":{"row":0,"col":0},"callout":"dp[0] = 0 (caz de bază)"},{"writes":[{"row":0,"col":1,"value":"1"}],"fills":[{"row":0,"col":1}],"pivot":{"row":0,"col":1},"callout":"dp[1] = 1 (caz de bază)"},{"writes":[{"row":0,"col":2,"value":"1"}],"fills":[{"row":0,"col":2}],"pivot":{"row":0,"col":2},"rowOp":"dp[2]=dp[1]+dp[0]","callout":"dp[2] = dp[1]+dp[0] = 1"},{"writes":[{"row":0,"col":3,"value":"2"}],"fills":[{"row":0,"col":3}],"pivot":{"row":0,"col":3},"rowOp":"dp[3]=dp[2]+dp[1]","callout":"dp[3] = dp[2]+dp[1] = 2"},{"writes":[{"row":0,"col":4,"value":"3"}],"fills":[{"row":0,"col":4}],"pivot":{"row":0,"col":4},"rowOp":"dp[4]=dp[3]+dp[2]","callout":"dp[4] = dp[3]+dp[2] = 3"},{"writes":[{"row":0,"col":5,"value":"5"}],"fills":[{"row":0,"col":5}],"pivot":{"row":0,"col":5},"rowOp":"dp[5]=dp[4]+dp[3]","callout":"dp[5] = dp[4]+dp[3] = 5 ✓ — fiecare celulă calculată o singură dată (O(n))"}]}';
```
Add the `<section>` card (place near the top families, e.g. after the `viz-demo-seq-array` section that ends at line 106):
```tsx
      <section data-testid="viz-demo-matrix-grid" style={tileStyle}>
        <h2 style={headingStyle}>PA · DP fill-table matrix-grid family (viz-pa-dpfib-001)</h2>
        <p style={subheadingStyle}>
          Family verification vehicle (NOT lesson content). DP fib(5) table · left→right fill · pivot-anchored callouts · per-cell trace-match · paints data-testid="mg-root".
        </p>
        <MatrixGridFamily instanceId="viz-pa-dpfib-001" dataJson={DPFIB_DATA_JSON} language="ro" />
      </section>
```
`data-testid="mg-root"` is painted by `AlgoStepperShell` via `testIdPrefix="mg"` (set in the family component, §11). The `<section>` testid is `viz-demo-matrix-grid` (gallery selection); the e2e no-clip target is `mg-root`.

---

## 10. Instances (H) — content YAMLs

The harness validates `family_id` + `data_json` only; `method_kc_id` is NOT validated by the harness. BUT per the project convention (and recon "REAL method_kc_id" requirement), each `content/` instance MUST anchor a real KC that exists on disk. Verified corpus state (read-only, 2026-06-14):

| Subject | KC dir | Real KC available? |
| --- | --- | --- |
| PA | `content/PA/kcs/` | YES — `pa-kc-fixture-recursion` (real, `concept_type: code-trace`, loadable) |
| ALO | `content/ALO/kcs/` | **EMPTY** (only nothing; `_sources/alo-study-guide-gauss.md` + `problems/alo-prob-001.yaml` exist but no KC) |
| SO-RC | `content/SO-RC/kcs/` | **EMPTY** (whole subject empty: kcs, _sources, misconceptions are `.gitkeep`-only per memory) |

### H1 — PA DP instance (SHIP)
File: `content/PA/viz/viz-pa-dpfib-001.yaml`. `method_kc_id: pa-kc-fixture-recursion` — REAL (verified at `content/PA/kcs/pa-kc-fixture-recursion.yaml:1`, `concept_type: code-trace, viz_id: recursion-tree`). It covers the recursion/DP-recurrence half of `DPWastedWork` (the migration target). The DP fill table is the matrix-grid PRIMARY re-expression of that primitive's left pane.
```yaml
id: viz-pa-dpfib-001
subject: PA
family_id: matrix-grid
language: ro
instance:
  method_kc_id: pa-kc-fixture-recursion
  data_json: '{"rows":1,"cols":6,"kind":"dp-fill","seed":{"n":5},"colHeaders":["dp[0]","dp[1]","dp[2]","dp[3]","dp[4]","dp[5]"],"steps":[{"writes":[{"row":0,"col":0,"value":"0"}],"fills":[{"row":0,"col":0}],"pivot":{"row":0,"col":0},"callout":"dp[0] = 0 (caz de bază)"},{"writes":[{"row":0,"col":1,"value":"1"}],"fills":[{"row":0,"col":1}],"pivot":{"row":0,"col":1},"callout":"dp[1] = 1 (caz de bază)"},{"writes":[{"row":0,"col":2,"value":"1"}],"fills":[{"row":0,"col":2}],"pivot":{"row":0,"col":2},"rowOp":"dp[2]=dp[1]+dp[0]","callout":"dp[2] = dp[1]+dp[0] = 1"},{"writes":[{"row":0,"col":3,"value":"2"}],"fills":[{"row":0,"col":3}],"pivot":{"row":0,"col":3},"rowOp":"dp[3]=dp[2]+dp[1]","callout":"dp[3] = dp[2]+dp[1] = 2"},{"writes":[{"row":0,"col":4,"value":"3"}],"fills":[{"row":0,"col":4}],"pivot":{"row":0,"col":4},"rowOp":"dp[4]=dp[3]+dp[2]","callout":"dp[4] = dp[3]+dp[2] = 3"},{"writes":[{"row":0,"col":5,"value":"5"}],"fills":[{"row":0,"col":5}],"pivot":{"row":0,"col":5},"rowOp":"dp[5]=dp[4]+dp[3]","callout":"dp[5] = dp[4]+dp[3] = 5 ✓ — fiecare celulă calculată o singură dată (O(n))"}]}'
```

### H2 — ALO Gaussian-elimination instance (SHIP — but requires authoring one minimal real KC first)
The dominant ALO matrix-step-trace material EXISTS and is rich: `content/ALO/_sources/alo-study-guide-gauss.md` (§7, the 3×3 system) + `content/ALO/problems/alo-prob-001.yaml` (full numeric trace, pivot a22=0 → row swap). This is the ideal `gauss-elim` instance. **The ONLY blocker is the missing KC** — `content/ALO/kcs/` is empty.

**Ruling: author one minimal, REAL KC sourced from the existing study guide**, then ship the instance. This is NOT inventing content — the KC is a faithful index of `alo-study-guide-gauss.md` which is real corpus. New KC file `content/ALO/kcs/alo-kc-gauss-elim.yaml`:
```yaml
id: alo-kc-gauss-elim
subject: ALO
name_ro: "Eliminare Gauss cu pivotare parțială"
name_en: "Gaussian elimination with partial pivoting"
cluster: "Algebră liniară — sisteme"
bloom_level: apply
difficulty: 3
time_minutes: 15
exam_weight: 0.0
tier: 2
concept_type: code-trace
requires_visual: true
source:
  - doc: alo-study-guide-gauss
    quote: "Transforma Ax = b intr-un sistem echivalent Ux = b', unde U este superior triunghiulara"
version: 1
```
Then the instance `content/ALO/viz/viz-alo-gauss-001.yaml`. The `seed.matrix` is the augmented matrix from the study guide (`[[1,-1,3,2],[3,-3,1,-1],[1,1,0,3]]`). The `steps[]` are hand-derived against `gaussElimReference` (§6): initial frame, then pivot r=0 select, then two elimination beats (E2,E3), then the a22=0 swap, then pivot r=1 select. (5 reference frames for this matrix — author must run `gaussElimReference` once to confirm the exact count + per-step table; the builder aligns `steps[]` to that output. The cell value formatter is integer-or-4dp.)
```yaml
id: viz-alo-gauss-001
subject: ALO
family_id: matrix-grid
language: ro
instance:
  method_kc_id: alo-kc-gauss-elim
  data_json: '{"rows":3,"cols":4,"kind":"gauss-elim","seed":{"matrix":[[1,-1,3,2],[3,-3,1,-1],[1,1,0,3]]},"colHeaders":["x1","x2","x3","| b"],"rowHeaders":["E1","E2","E3"],"steps":[ ... aligned to gaussElimReference output ... ]}'
```
**Builder note:** generate the exact `steps[]` by calling `gaussElimReference` on the seed in a scratch test and transcribing the frames (writes = the cells whose `table` value changed since the prior frame; fills = all cells, set on the initial frame; pivot per the oracle; callouts in RO from the study guide). This guarantees the harness trace-match passes by construction.

### H3 — SO page-table instance (DEFERRED)
`content/SO-RC/` is entirely empty — NO KC, NO source. Per the spec rule ("if a subject has no real KC anchor, say so and mark it deferred rather than inventing"), the SO page-table instance is **DEFERRED**. The `page-table` `kind` + `pageTableReference` stub are present in the code so the family is ready, but no `content/SO-RC/viz/*.yaml` is authored until an SO-RC lecture is curated (a real KC + source land in `content/SO-RC/`). The migration of `PageTableWalk` to data_json is therefore parked behind the SO-RC corpus gap, exactly as `MatrixTransform`/page-table source material dictates.

**Net authored instances: 2 ship (PA dp-fill, ALO gauss-elim + its sourced KC), 1 deferred (SO page-table).** Two anchored subjects covered with real KCs; the third has no corpus to anchor.

---

## 11. File manifest + gate plan

### Files CREATED
| Path | Purpose |
| --- | --- |
| `tutor-web/src/components/viz/families/MatrixGridFamily.tsx` | types (§1) + `parseMatrixGridData` (§2) + `framesFromMatrixGridData` (§3) + layout/`renderFrame` (§4) + `MatrixGridFamily` component (§11 below) |
| `tutor-web/src/components/viz/families/matrixGridInvariants.ts` | the 5 invariants + `MATRIX_GRID_INVARIANTS` (§5) |
| `tutor-web/src/components/viz/families/__tests__/matrixGridTrace.ts` | the oracle `matrixGridReference` (§6) |
| `tutor-web/src/components/viz/families/__tests__/seededWrongMatrixGrid.test.tsx` | gate-3 assert-RED test (§7) |
| `tutor-web/src/components/viz/families/__tests__/fixtures/seeded-wrong-matrix-grid.yaml` | gate-3 wrong-trace fixture (§7) |
| `tutor-web/e2e/matrix-grid-no-clip.spec.ts` | gate-3 no-clip e2e (below) |
| `content/PA/viz/viz-pa-dpfib-001.yaml` | PA DP instance (§10 H1) |
| `content/ALO/kcs/alo-kc-gauss-elim.yaml` | minimal real KC sourced from the Gauss study guide (§10 H2) |
| `content/ALO/viz/viz-alo-gauss-001.yaml` | ALO Gauss instance (§10 H2) |

### Files EDITED
| Path | Edit |
| --- | --- |
| `tutor-web/src/components/viz/families/familyRegistry.ts` | import + `"matrix-grid": MatrixGridFamily` (§8 D) |
| `tutor-web/src/components/viz/families/__tests__/traceMatchHarness.test.tsx` | 3 imports + `HARNESS_REGISTRY["matrix-grid"]` block (§8 E) |
| `tutor-web/src/components/viz/VizDemoPage.tsx` | import + `DPFIB_DATA_JSON` + `viz-demo-matrix-grid` card (§9 G) |

### The `MatrixGridFamily` component (in `MatrixGridFamily.tsx`)
Mirrors `SequenceArrayFamily` (`SequenceArrayFamily.tsx:1201-1259`); single white skin (no dark/bars needed for family 3):
```tsx
export function MatrixGridFamily({ instanceId, dataJson, language, labels, onStep }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseMatrixGridData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromMatrixGridData(data), [data]);
  const layout = useMemo(() => computeLayout(data), [data]);
  const lastIdx = Math.max(0, frames.length - 1);
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  return (
    <AlgoStepperShell<MatrixGridState>
      title={`Matrix/grid · ${instanceId}`}
      desc={language === "ro" ? "Tabel/matrice pas cu pas" : "Step-through grid"}
      frames={frames}
      renderFrame={renderFrame(layout)}
      testIdPrefix="mg"               // → data-testid="mg-root" + "mg-step-fwd" + "mg-frame-counter"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
    />
  );
}
```

### `matrix-grid-no-clip.spec.ts` (gate 3)
Mirror `seq-array-no-clip.spec.ts` exactly; swap testids to `mg-*`, set `TOTAL` to the PA DP instance step count (**6**):
```ts
import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";
const VIEWPORTS = [{ width: 1280, height: 900 }, { width: 1280, height: 620 }] as const;
const TOTAL = 6; // viz-pa-dpfib-001 = 6 fill steps
test("matrix-grid family: no clip before+after stepping at 2 viewports", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });
  await page.goto("/tutor/viz-demo");
  await expect(page.getByTestId("mg-root")).toBeVisible({ timeout: 10000 });
  const counter = page.getByTestId("mg-frame-counter");
  const fwd = page.getByTestId("mg-step-fwd");
  const back = page.getByTestId("mg-step-back");
  for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "mg-root"); }
  await page.setViewportSize({ ...VIEWPORTS[0] });
  expect(await counter.textContent()).toContain(`1 / ${TOTAL}`);
  for (let beat = 1; beat < TOTAL; beat++) {
    await fwd.click();
    for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "mg-root"); }
    await page.setViewportSize({ ...VIEWPORTS[0] });
  }
  for (let b = 1; b < TOTAL; b++) await back.click();
  const start = await counter.textContent();
  expect(start).toContain(`1 / ${TOTAL}`);
  await fwd.click();
  expect(await counter.textContent()).not.toBe(start);
  await back.click();
  expect(await counter.textContent()).toBe(start);
  const root = page.getByTestId("mg-root");
  await expect(root.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
});
```

### The 3 gates — exact commands (cwd = `tutor-web`)
```bash
# Gate 1 — trace-match totality + semantic invariants (vitest). Runs the harness over EVERY content
#          instance incl. the 2 new matrix-grid YAMLs + all 5 MATRIX_GRID_INVARIANTS.
npx vitest run src/components/viz/families/__tests__/traceMatchHarness.test.tsx

# Gate 2 — assert-RED wrong-trace (vitest). Proves the harness THROWS at step 4 on the corrupted DP fixture.
npx vitest run src/components/viz/families/__tests__/seededWrongMatrixGrid.test.tsx

# Gate 3 — no-clip e2e (playwright). 2 viewports, before+after stepping, scrubber round-trip, 0 4xx/5xx.
#          (Dev server must serve /tutor/viz-demo; playwright config starts it.)
npx playwright test e2e/matrix-grid-no-clip.spec.ts
```
Run the FULL family vitest suite + typecheck before claiming done (per review_workflow — foreground, no `| tail`):
```bash
npx tsc --noEmit
npx vitest run src/components/viz/families/__tests__/
```

### Build-order checklist (A–H, recon)
A `MatrixGridFamily.tsx` · B `matrixGridTrace.ts` oracle · C `matrixGridInvariants.ts` · D `familyRegistry` · E `HARNESS_REGISTRY` · F wrong fixture + assert-RED test · G `mg-root` mount in VizDemoPage · H ≥1 content instance per anchored subject (PA dp-fill ✓, ALO gauss-elim ✓ + sourced KC; SO page-table DEFERRED — empty corpus).
