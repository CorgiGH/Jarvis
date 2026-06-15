/**
 * Plan-V family (sort-merge) — semantic invariants (INV-5.2), mirrors sequenceArrayInvariants.ts.
 *
 * Each invariant receives the MOUNTED family's DOM (via data-cell-index + transform stamps + the
 * <text> value) plus the current frame state and asserts a semantic property of the RENDERED SVG —
 * truth read back from the pixels, not from the model. Exported as MERGE_INVARIANTS so the trace-match
 * harness registers them with the family.
 *
 * The geometry the renderer stamps:
 *   <g data-cell-index="k" transform="translate(cx,cy)"><rect/><text>value</text></g>   one per token
 *   <g data-pointer="front-r" data-pointer-index="k">…</g>                                front pointers
 *
 * The load-bearing ones (the merge figure's "it teaches" contract):
 *   1 final-state             — the LAST frame's rendered array (by data-cell-index order) == sorted(values).
 *                               This is the invariant whose absence is the legacy graph-tree figure's defect.
 *   2 multiset-conserved      — every frame's rendered array is a permutation of values (same multiset).
 *   3 merge-prefix-correct    — every take/drain frame's output filled-prefix is non-decreasing (the
 *                               formal "we always take the smaller front"); on the final frame the whole
 *                               array is non-decreasing.
 *   4 front-pointer-in-bounds — every rendered front pointer sits over an existing token index.
 *   5 sorted-span-monotone    — index-monotone-x within each band (tokens laid left→right by slot).
 */

import type { Frame } from "../AlgoStepperShell";
import type { MergeState } from "./SortMergeFamily";

export type InvariantResult = { ok: true } | { ok: false; message: string };

export type MergeInvariant = (
  container: Element,
  frame: Frame<MergeState>,
  stepIdx: number,
  allFrames: Frame<MergeState>[],
) => InvariantResult;

// ── DOM helpers — read truth back from the rendered SVG ─────────────────────────────────────────────
type RenderedCell = { index: number; x: number; y: number; value: number };

function readCells(container: Element): RenderedCell[] {
  const groups = Array.from(container.querySelectorAll<SVGGElement>("g[data-cell-index]"));
  const cells: RenderedCell[] = [];
  for (const g of groups) {
    const idxAttr = g.getAttribute("data-cell-index");
    if (idxAttr == null) continue;
    const index = parseInt(idxAttr, 10);
    // POSITION SOURCE OF TRUTH = the renderer's static data-cx / data-cy attributes (the current
    // frame's TARGET position). motion/react animates style.transform toward (x,y), but in jsdom the
    // RAF tween never runs so style.transform is stale — reading data-cx/cy gives the real per-frame
    // geometry. Fall back to style.transform (translateX/Y) then a transform="translate()" attribute
    // for any non-motion render path.
    const cxAttr = g.getAttribute("data-cx");
    const cyAttr = g.getAttribute("data-cy");
    const styleT = (g as unknown as { style?: { transform?: string } }).style?.transform ?? "";
    const attrT = g.getAttribute("transform") ?? "";
    const tx = /translateX\(([-\d.]+)px\)/.exec(styleT);
    const ty = /translateY\(([-\d.]+)px\)/.exec(styleT);
    const attrM = /translate\(([-\d.]+)[ ,]+([-\d.]+)\)/.exec(attrT);
    const x = cxAttr != null ? parseFloat(cxAttr) : tx ? parseFloat(tx[1]) : attrM ? parseFloat(attrM[1]) : NaN;
    const y = cyAttr != null ? parseFloat(cyAttr) : ty ? parseFloat(ty[1]) : attrM ? parseFloat(attrM[2]) : NaN;
    const txt = g.querySelector("text");
    const value = parseInt(txt?.textContent ?? "NaN", 10);
    cells.push({ index, x, y, value });
  }
  cells.sort((p, q) => p.index - q.index);
  return cells;
}

/** Read the rendered array values in data-cell-index order. */
function readArray(container: Element): number[] {
  return readCells(container).map((c) => c.value);
}

/**
 * INV-1 final-state: on the LAST frame the rendered cell values (in index order) form the fully sorted
 * ascending sequence — the proof that merge sort terminated correctly. Vacuous for non-final frames.
 */
export const finalState: MergeInvariant = (container, _frame, stepIdx, allFrames) => {
  if (stepIdx !== allFrames.length - 1) return { ok: true };
  const rendered = readArray(container);
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
 * INV-2 multiset-conserved: this frame's rendered value multiset equals the first frame's — merge sort
 * permutes, it never invents or drops a number. Anchors on the frame's own array (the trace harness has
 * already matched that to the oracle) AND re-asserts the rendered <text> values equal it.
 */
export const multisetConserved: MergeInvariant = (container, frame, stepIdx, allFrames) => {
  const rendered = readArray(container).sort((a, b) => a - b);
  const frameSorted = [...frame.state.array].sort((a, b) => a - b);
  if (JSON.stringify(rendered) !== JSON.stringify(frameSorted)) {
    return {
      ok: false,
      message: `step ${stepIdx} multiset-conserved FAIL: rendered values [${rendered.join(",")}] ≠ frame array multiset [${frameSorted.join(",")}]`,
    };
  }
  const firstSorted = [...allFrames[0].state.array].sort((a, b) => a - b);
  if (JSON.stringify(frameSorted) !== JSON.stringify(firstSorted)) {
    return {
      ok: false,
      message: `step ${stepIdx} multiset-conserved FAIL: value multiset [${frameSorted.join(",")}] drifted from initial [${firstSorted.join(",")}]`,
    };
  }
  return { ok: true };
};

/**
 * INV-3 merge-prefix-correct: the output filled-prefix is always non-decreasing — the formal statement
 * of "we always take the smaller front". On take/drain frames the committed output occupies array
 * positions [mergeLo, mergeLo+outFilled); those values must be non-decreasing. On the final frame the
 * WHOLE array is non-decreasing. Read from the frame's array (positions are exact there); the rendered
 * values are independently checked by multiset-conserved + final-state, so this reads the committed
 * prefix from the model's positions and asserts the ordering property the figure must teach.
 */
export const mergePrefixCorrect: MergeInvariant = (_container, frame, stepIdx) => {
  const st = frame.state;
  const nonDecreasing = (xs: number[], label: string): InvariantResult => {
    for (let k = 1; k < xs.length; k++) {
      if (xs[k] < xs[k - 1]) {
        return {
          ok: false,
          message: `step ${stepIdx} merge-prefix-correct FAIL: ${label} [${xs.join(",")}] is not non-decreasing at position ${k}`,
        };
      }
    }
    return { ok: true };
  };
  if (st.phase === "final") {
    return nonDecreasing(st.array, "final array");
  }
  if (st.phase === "take" || st.phase === "drain") {
    // recover the active merge span lo from the runs/sortedSpan (same logic the renderer uses)
    const mergeLo = st.runs.length > 0 ? st.runs[0].lo - st.outFilled : st.sortedSpan.lo;
    const prefix = st.array.slice(mergeLo, mergeLo + st.outFilled);
    return nonDecreasing(prefix, "output prefix");
  }
  return { ok: true };
};

/**
 * INV-4 front-pointer-in-bounds: every rendered front-pointer marker sits over an EXISTING token
 * index. A pointer past the array end (or negative) would mislead about which run head is active.
 */
export const frontPointerInBounds: MergeInvariant = (container, _frame, stepIdx) => {
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
        message: `step ${stepIdx} front-pointer-in-bounds FAIL: pointer '${kind}' points at index ${idxAttr} which has no rendered token`,
      };
    }
  }
  return { ok: true };
};

/**
 * INV-5 sorted-span-monotone: within the OUTPUT band (and the single divide/final row) tokens are laid
 * out left→right by slot — a higher output slot is rendered to the RIGHT of a lower one. Reads the
 * rendered x for the committed output positions and asserts strict left→right order. Vacuous when the
 * transform x is unavailable (jsdom sometimes drops the animated transform) or <2 output tokens.
 */
export const sortedSpanMonotone: MergeInvariant = (container, frame, stepIdx) => {
  const st = frame.state;
  const cells = readCells(container);
  const byIndex = new Map(cells.map((c) => [c.index, c]));
  // pick the positions that form a contiguous left→right block this frame: the output prefix (merge
  // frames) or the whole row (divide/final).
  let positions: number[];
  if (st.phase === "divide" || st.phase === "final") {
    positions = st.array.map((_, i) => i);
  } else if (st.outFilled > 0) {
    const mergeLo = st.runs.length > 0 ? st.runs[0].lo - st.outFilled : st.sortedSpan.lo;
    positions = Array.from({ length: st.outFilled }, (_, k) => mergeLo + k);
  } else {
    return { ok: true };
  }
  let prevX = -Infinity;
  for (const pos of positions) {
    const c = byIndex.get(pos);
    if (!c || Number.isNaN(c.x)) continue; // transform x unavailable — skip (not a failure)
    if (!(c.x > prevX)) {
      return {
        ok: false,
        message: `step ${stepIdx} sorted-span-monotone FAIL: token at index ${pos} (x=${c.x}) is not right of the previous (x=${prevX})`,
      };
    }
    prevX = c.x;
  }
  return { ok: true };
};

/** The canonical invariant list for the sort-merge family (INV-5.2). */
export const MERGE_INVARIANTS: MergeInvariant[] = [
  finalState,
  multisetConserved,
  mergePrefixCorrect,
  frontPointerInBounds,
  sortedSpanMonotone,
];
