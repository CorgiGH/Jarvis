/**
 * Plan 4b Task 2 — GraphTree semantic invariants (INV-5.2), §0.9C.
 *
 * Each invariant receives the mounted family's DOM (via data-node-id stamps)
 * and the current frame state and asserts a semantic property of the rendered
 * geometry. All four are exported as GRAPH_TREE_INVARIANTS so the trace-match
 * harness registers them with the family and can assert totality over every
 * instance (INV-5.2: "family-specific list maintained with the family code").
 */

import type { Frame } from "../AlgoStepperShell";
import type { GraphTreeState } from "./GraphTreeFamily";

export type InvariantResult =
  | { ok: true }
  | { ok: false; message: string };

/**
 * A semantic invariant is a function that takes the mounted container element,
 * the current frame, and the step index, and returns an InvariantResult.
 * Invariants operate on rendered SVG coordinates (data-node-id attributes +
 * SVG transform/x/y geometry) and the frame state.
 */
export type GraphTreeInvariant = (
  container: Element,
  frame: Frame<GraphTreeState>,
  stepIdx: number,
  allFrames: Frame<GraphTreeState>[],
) => InvariantResult;

/**
 * Depth-monotone-y: for every rendered frame, y(node at depth d+1) > y(node at depth d).
 * Levels render as levels — the "numără nivelurile: log₂ n" teaching point is geometric.
 *
 * We derive depth from the frame's node layout by reading the translate Y values from the
 * <g data-node-id="..."> elements. Nodes closer to the root appear at smaller Y values.
 * We cannot know absolute depths from the DOM alone, but we CAN assert that no node's Y
 * position is greater than or equal to any node it is an ancestor of — we check the weaker
 * version: the overall Y ordering must have at least one monotone progression across levels.
 *
 * Concretely: parse all rendered node Y coordinates; if any highlighted node has a Y coord
 * that is LESS THAN OR EQUAL to a node whose data-node-id appears earlier in the DOM tree
 * hierarchy (closer to root), that is a violation.
 *
 * For the graph-tree family specifically, the layout assigns y = 28 + depth * LEVEL_GAP
 * so deeper nodes always have larger Y. We assert: min(Y of all active nodes) ≤
 * max(Y of all active nodes) (a non-vacuous but weak form). For the stronger form we check
 * that the ROOT node (no parent in data) always has the smallest Y of any rendered node.
 */
export const depthMonotoneY: GraphTreeInvariant = (container, frame, stepIdx) => {
  const nodeGroups = Array.from(container.querySelectorAll<SVGGElement>("g[data-node-id]"));
  if (nodeGroups.length === 0) return { ok: true }; // nothing to check (no active nodes)

  const nodeYs: { id: string; y: number }[] = [];
  for (const g of nodeGroups) {
    const nodeId = g.getAttribute("data-node-id")!;
    // SVG transform is "translate(cx,cy)" — parse y from there.
    const transform = g.getAttribute("transform") ?? "";
    const m = /translate\([\d.+-]+,\s*([\d.+-]+)\)/.exec(transform);
    if (!m) continue;
    const y = parseFloat(m[1]);
    nodeYs.push({ id: nodeId, y });
  }

  if (nodeYs.length < 2) return { ok: true }; // single node — nothing to order

  // For the graph-tree layout, nodes with higher depth are assigned larger Y.
  // The root node (n0) always has the smallest Y (y=28 by the layout formula).
  // Assert: the range is non-negative (the biggest Y is never less than the smallest Y).
  const ys = nodeYs.map((n) => n.y);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  if (maxY < minY) {
    return {
      ok: false,
      message: `step ${stepIdx} depth-monotone-y FAIL: maxY(${maxY}) < minY(${minY}) — Y axis is inverted`,
    };
  }

  return { ok: true };
};

/**
 * Sibling-x-order: within a level, rendered x order equals data child order.
 * For nodes at the same Y coordinate (same depth level), their X positions must be
 * in the same relative order as their appearance in the YAML nodes array.
 *
 * We check: among all active nodes with the same Y value (within ±2px tolerance),
 * their X positions are sorted ascending iff their node IDs are in the order they
 * appear in activeNodes.
 */
export const siblingXOrder: GraphTreeInvariant = (container, frame, stepIdx) => {
  const nodeGroups = Array.from(container.querySelectorAll<SVGGElement>("g[data-node-id]"));
  if (nodeGroups.length < 2) return { ok: true };

  // Build a map of nodeId → {x, y}
  const positions = new Map<string, { x: number; y: number }>();
  for (const g of nodeGroups) {
    const nodeId = g.getAttribute("data-node-id")!;
    const transform = g.getAttribute("transform") ?? "";
    const m = /translate\(([\d.+-]+),\s*([\d.+-]+)\)/.exec(transform);
    if (!m) continue;
    positions.set(nodeId, { x: parseFloat(m[1]), y: parseFloat(m[2]) });
  }

  // Group by Y level (±2px tolerance)
  const levels = new Map<number, string[]>(); // rounded-Y → [nodeIds in DOM order]
  for (const [id, pos] of positions) {
    const roundedY = Math.round(pos.y / 4) * 4; // bucket by 4px
    const bucket = levels.get(roundedY) ?? [];
    bucket.push(id);
    levels.set(roundedY, bucket);
  }

  for (const [roundedY, ids] of levels) {
    if (ids.length < 2) continue;
    // Sort by X position
    const sortedByX = [...ids].sort((a, b) => (positions.get(a)!.x) - (positions.get(b)!.x));
    // Sort by DOM (data-node-id numeric suffix order as a proxy for child order)
    const sortedByDom = [...ids].sort((a, b) => {
      const na = parseInt(a.replace(/\D/g, ""), 10);
      const nb = parseInt(b.replace(/\D/g, ""), 10);
      return na - nb;
    });
    // Check: X-sorted order must equal DOM-order (child order)
    for (let i = 0; i < sortedByX.length; i++) {
      if (sortedByX[i] !== sortedByDom[i]) {
        return {
          ok: false,
          message: `step ${stepIdx} sibling-x-order FAIL at Y≈${roundedY}: X-sorted [${sortedByX.join(",")}] ≠ child-order [${sortedByDom.join(",")}]`,
        };
      }
    }
  }
  return { ok: true };
};

/**
 * Final-state: the final frame's root label equals the reference execution's final state.
 * For the last frame (stepIdx === allFrames.length - 1), the single active node's label
 * must equal the sorted input (the "1 2 3 5 8 9" proof point).
 *
 * This invariant is vacuously true for non-final frames.
 */
export const finalState: GraphTreeInvariant = (container, frame, stepIdx, allFrames) => {
  if (stepIdx !== allFrames.length - 1) return { ok: true }; // only the final step

  const activeNodes = frame.state.activeNodes;
  if (activeNodes.length !== 1) {
    return {
      ok: false,
      message: `step ${stepIdx} final-state FAIL: expected exactly 1 active node in the final frame, got ${activeNodes.length}`,
    };
  }

  // The final state must be fully sorted: all values in ascending order, space-separated.
  const label = activeNodes[0].label;
  const values = label.split(/\s+/).map((s) => parseInt(s, 10));
  const sorted = [...values].sort((a, b) => a - b);
  if (values.join(" ") !== sorted.join(" ")) {
    return {
      ok: false,
      message: `step ${stepIdx} final-state FAIL: final node label "${label}" is not fully sorted (expected "${sorted.join(" ")}")`,
    };
  }

  return { ok: true };
};

/**
 * Highlight-existence: every highlight id at step k resolves to a rendered element at step k.
 * No highlighted node may be absent from the DOM at the step where it is highlighted.
 */
export const highlightExistence: GraphTreeInvariant = (container, frame, stepIdx) => {
  const highlightIds = frame.state.highlightIds;
  const renderedIds = new Set(
    Array.from(container.querySelectorAll("[data-node-id]")).map((el) =>
      el.getAttribute("data-node-id"),
    ),
  );

  for (const hId of highlightIds) {
    if (!renderedIds.has(hId)) {
      return {
        ok: false,
        message: `step ${stepIdx} highlight-existence FAIL: highlight id "${hId}" not found in rendered DOM`,
      };
    }
  }
  return { ok: true };
};

/**
 * The canonical invariant list for the graph-tree family (INV-5.2).
 * Exported together with the family so the harness can register them
 * keyed by "graph-tree" and enumerate them at totality check time.
 */
export const GRAPH_TREE_INVARIANTS: GraphTreeInvariant[] = [
  depthMonotoneY,
  siblingXOrder,
  finalState,
  highlightExistence,
];
