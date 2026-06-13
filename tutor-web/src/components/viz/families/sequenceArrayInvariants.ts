/**
 * Plan-V family 2 — SequenceArray semantic invariants (INV-5.2), mirrors graphTreeInvariants.ts /
 * chartDistributionInvariants.ts.
 *
 * Each invariant receives the MOUNTED family's DOM (via data-cell-index + transform stamps) plus the
 * current frame state and asserts a semantic property of the RENDERED SVG geometry — read truth back
 * from the pixels, not from the model. All are exported as SEQ_ARRAY_INVARIANTS so the trace-match
 * harness registers them with the family (INV-5.2: the family-specific list lives with the family
 * code).
 *
 * The geometry the renderer stamps:
 *   <g data-cell-index="k" transform="translate(cx,cy)"><rect/><text>value</text></g>   one per cell
 *   <g data-pointer="i|j|min" data-pointer-index="k">…</g>                                pointer markers
 *
 * The 5 from recon §3 (Family 2 — sequence/array):
 *   1 index-monotone-x      (cell k+1 rendered right of cell k)
 *   2 cardinality-preserved (rendered cell count == reference length EVERY frame)
 *   3 pointer-in-bounds     (each rendered pointer marker sits over an existing cell index)
 *   4 final-state           (final rendered cells == sorted reference output)
 *   5 swap-conservation     (multiset of rendered cell values constant across frames)
 */

import type { Frame } from "../AlgoStepperShell";
import type { SeqArrayState } from "./SequenceArrayFamily";

export type InvariantResult = { ok: true } | { ok: false; message: string };

export type SeqArrayInvariant = (
  container: Element,
  frame: Frame<SeqArrayState>,
  stepIdx: number,
  allFrames: Frame<SeqArrayState>[],
) => InvariantResult;

// ── DOM-geometry helpers — read truth back from the rendered SVG, never the model ───────────────
type RenderedCell = { index: number; x: number; y: number; value: number };

/** Parse every rendered cell back out of the mounted DOM (data-cell-index + transform + <text>). */
function readCells(container: Element): RenderedCell[] {
  const groups = Array.from(container.querySelectorAll<SVGGElement>("g[data-cell-index]"));
  const cells: RenderedCell[] = [];
  for (const g of groups) {
    const idxAttr = g.getAttribute("data-cell-index");
    if (idxAttr == null) continue;
    const index = parseInt(idxAttr, 10);
    const transform = g.getAttribute("transform") ?? "";
    const m = /translate\(([-\d.]+),\s*([-\d.]+)\)/.exec(transform);
    if (!m) continue;
    const x = parseFloat(m[1]);
    const y = parseFloat(m[2]);
    const txt = g.querySelector("text");
    const value = parseInt(txt?.textContent ?? "NaN", 10);
    cells.push({ index, x, y, value });
  }
  // order by the stamped data-cell-index (the logical array order)
  cells.sort((p, q) => p.index - q.index);
  return cells;
}

/**
 * INV-1 index-monotone-x: cell k+1 is rendered strictly to the RIGHT of cell k. The array reads
 * left→right; if the renderer ever placed a higher-index cell at a smaller x the whole figure lies
 * about ordering. Vacuous if <2 cells.
 */
export const indexMonotoneX: SeqArrayInvariant = (container, _frame, stepIdx) => {
  const cells = readCells(container);
  if (cells.length < 2) return { ok: true };
  for (let k = 1; k < cells.length; k++) {
    if (!(cells[k].x > cells[k - 1].x)) {
      return {
        ok: false,
        message: `step ${stepIdx} index-monotone-x FAIL: cell ${cells[k].index} (x=${cells[k].x}) is not right of cell ${cells[k - 1].index} (x=${cells[k - 1].x})`,
      };
    }
  }
  return { ok: true };
};

/**
 * INV-2 cardinality-preserved: the rendered cell COUNT equals the reference array length every
 * frame. Selection sort permutes in place — it never adds or drops an element. We read the length
 * from the frame's own array (the trace harness has already asserted that equals the oracle) and
 * compare to the rendered count.
 */
export const cardinalityPreserved: SeqArrayInvariant = (container, frame, stepIdx) => {
  const cells = readCells(container);
  const expected = frame.state.array.length;
  if (cells.length !== expected) {
    return {
      ok: false,
      message: `step ${stepIdx} cardinality-preserved FAIL: rendered ${cells.length} cells, reference length ${expected}`,
    };
  }
  return { ok: true };
};

/**
 * INV-3 pointer-in-bounds: every rendered pointer marker (i / j / min) sits over an EXISTING cell
 * index. A pointer pointing past the array end (or at a negative index) would mislead the learner
 * about which element is active. Read the pointer's data-pointer-index back and assert a cell with
 * that data-cell-index exists.
 */
export const pointerInBounds: SeqArrayInvariant = (container, _frame, stepIdx) => {
  const cells = readCells(container);
  const cellIndices = new Set(cells.map((c) => c.index));
  const pointers = Array.from(container.querySelectorAll<SVGGElement>("g[data-pointer]"));
  for (const p of pointers) {
    const kind = p.getAttribute("data-pointer") ?? "?";
    const idxAttr = p.getAttribute("data-pointer-index");
    const idx = idxAttr == null ? NaN : parseInt(idxAttr, 10);
    if (!cellIndices.has(idx)) {
      return {
        ok: false,
        message: `step ${stepIdx} pointer-in-bounds FAIL: pointer '${kind}' points at index ${idxAttr} which has no rendered cell`,
      };
    }
  }
  return { ok: true };
};

/**
 * INV-4 final-state: on the LAST frame the rendered cell values (in index order) form the fully
 * SORTED ascending sequence — the proof point that selection sort terminated correctly. Read the
 * values back from the DOM <text> nodes. Vacuous for non-final frames.
 */
export const finalState: SeqArrayInvariant = (container, _frame, stepIdx, allFrames) => {
  if (stepIdx !== allFrames.length - 1) return { ok: true };
  const cells = readCells(container);
  const rendered = cells.map((c) => c.value);
  const sorted = [...rendered].sort((a, b) => a - b);
  if (JSON.stringify(rendered) !== JSON.stringify(sorted)) {
    return {
      ok: false,
      message: `step ${stepIdx} final-state FAIL: final rendered cells [${rendered.join(",")}] are not sorted (expected [${sorted.join(",")}])`,
    };
  }
  return { ok: true };
};

/**
 * INV-5 swap-conservation: the MULTISET of rendered cell values is constant across frames — a swap
 * permutes the array, it never edits a value. We compare this frame's rendered value multiset to the
 * first frame's. (Reading allFrames[0].state.array would trust the model; instead we anchor on the
 * frame state's array multiset, which the trace harness has independently matched against the oracle
 * per step, and assert the RENDERED values equal the frame's array multiset — closing model↔pixels.)
 */
export const swapConservation: SeqArrayInvariant = (container, frame, stepIdx, allFrames) => {
  const cells = readCells(container);
  const renderedSorted = cells.map((c) => c.value).sort((a, b) => a - b);
  // (a) rendered values are a permutation of THIS frame's array (pixels match the model this step)
  const frameSorted = [...frame.state.array].sort((a, b) => a - b);
  if (JSON.stringify(renderedSorted) !== JSON.stringify(frameSorted)) {
    return {
      ok: false,
      message: `step ${stepIdx} swap-conservation FAIL: rendered values [${renderedSorted.join(",")}] ≠ frame array multiset [${frameSorted.join(",")}]`,
    };
  }
  // (b) this frame's multiset equals the FIRST frame's multiset (no element ever created/destroyed)
  const firstSorted = [...allFrames[0].state.array].sort((a, b) => a - b);
  if (JSON.stringify(frameSorted) !== JSON.stringify(firstSorted)) {
    return {
      ok: false,
      message: `step ${stepIdx} swap-conservation FAIL: value multiset [${frameSorted.join(",")}] drifted from initial [${firstSorted.join(",")}] — a swap must permute, never edit`,
    };
  }
  return { ok: true };
};

/**
 * The canonical invariant list for the seq-array family (INV-5.2). Exported with the family so the
 * harness can register them keyed by "seq-array" and enumerate them at totality check time.
 */
export const SEQ_ARRAY_INVARIANTS: SeqArrayInvariant[] = [
  indexMonotoneX,
  cardinalityPreserved,
  pointerInBounds,
  finalState,
  swapConservation,
];
