import { useCallback, useMemo, type ReactNode } from "react";
import { hierarchy, tree as d3tree } from "d3-hierarchy";
import { AlgoStepperShell, type Frame, type ShellLabels } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Typed slots (plan core §0.9G) ─────────────────────────────────────────
export type GtNode = { id: string; label: string; parent?: string };
export type GtDelta = { node: string; label?: string };
export type GtStep = { highlight: string[]; deltas: GtDelta[]; callout: string };
export type GraphTreeData = { nodes: GtNode[]; steps: GtStep[] };

// What a single rendered step exposes to the trace-match test + renderFrame.
export type ActiveNode = { id: string; label: string };
export type GraphTreeState = {
  activeNodes: ActiveNode[];   // the occupied frontier at this step (by current label)
  highlight: string[];         // current LABELS of highlighted nodes at this step (for invariant tests)
  highlightIds: string[];      // node IDs of highlighted nodes (for layout/render lookup)
  callout: string;
};

/**
 * Hand-rolled validation guard (zod-free per plan core §0.9G). On any structural fault it throws a
 * load error naming the instance id AND the offending field, so a broken instance fails admission
 * loud (INV-5.5) instead of rendering an empty/garbled figure.
 */
export function parseGraphTreeData(dataJson: string, instanceId: string): GraphTreeData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`graph-tree instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;
  if (!Array.isArray(obj.nodes))
    throw new Error(`graph-tree instance '${instanceId}': missing/invalid field 'nodes' (expected array)`);
  if (!Array.isArray(obj.steps))
    throw new Error(`graph-tree instance '${instanceId}': missing/invalid field 'steps' (expected array)`);
  const ids = new Set<string>();
  obj.nodes.forEach((n, i) => {
    const node = n as Record<string, unknown>;
    if (typeof node.id !== "string")
      throw new Error(`graph-tree instance '${instanceId}': nodes[${i}] missing field 'id' (string)`);
    if (typeof node.label !== "string")
      throw new Error(`graph-tree instance '${instanceId}': nodes[${i}] missing field 'label' (string)`);
    ids.add(node.id);
  });
  obj.steps.forEach((s, i) => {
    const step = s as Record<string, unknown>;
    if (!Array.isArray(step.highlight))
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'highlight' (string[])`);
    if (!Array.isArray(step.deltas))
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'deltas' (array)`);
    if (typeof step.callout !== "string")
      throw new Error(`graph-tree instance '${instanceId}': steps[${i}] missing field 'callout' (string)`);
    for (const h of step.highlight as unknown[]) {
      if (typeof h !== "string" || !ids.has(h))
        throw new Error(`graph-tree instance '${instanceId}': steps[${i}].highlight references unknown node '${String(h)}'`);
    }
  });
  return obj as unknown as GraphTreeData;
}

/**
 * Build one Frame per step. The label of a node mutates as `deltas` rename it (merge sorts a group);
 * a node is "active" at step k iff it is in that step's highlight set (the frontier). activeNodes
 * carries the CURRENT label (post-applied deltas up to and including step k).
 */
export function framesFromGraphTree(data: GraphTreeData): Frame<GraphTreeState>[] {
  const labelOf = new Map(data.nodes.map((n) => [n.id, n.label]));
  const frames: Frame<GraphTreeState>[] = [];
  for (const step of data.steps) {
    for (const d of step.deltas) if (d.label != null) labelOf.set(d.node, d.label);
    const activeNodes: ActiveNode[] = step.highlight.map((id) => ({ id, label: labelOf.get(id)! }));
    // highlight = current labels (for trace-match / semantic invariant tests, per plan §0.9G).
    // highlightIds = node IDs (for render layout lookup).
    frames.push({
      state: {
        activeNodes,
        highlight: step.highlight.map((id) => labelOf.get(id)!),
        highlightIds: [...step.highlight],
        callout: step.callout,
      },
      aria: step.callout,
    });
  }
  return frames;
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────
let measureCanvas: { ctx: CanvasRenderingContext2D | null } | null = null;
function measureLabelWidth(text: string, fontPx: number): number {
  // Use an off-DOM 2D context ONLY to measure text extents. This is NOT a canvas FIGURE
  // (the figure is pure SVG); measurement does not violate the SVG-only render policy (§5.6).
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
const LABEL_FONT = 11;
const BOX_PAD_X = 8;
const BOX_H = 26;
const LEVEL_GAP = 64;

// ── Callout geometry (Plan-4b Task 9 PM amendment — no-clip-by-construction on the REAL figure) ──
// The callout `<text>` is `textAnchor="middle"`, so its rendered box spans [cx - w/2, cx + w/2].
// The old code clamped only cx into [8, SVG_W-8], IGNORING the text's own width — a wide callout
// anchored to a left-side node overflowed to NEGATIVE x (the assertNoClip viewport-overflow red:
// "↓ ÎMPARTE …" rendered left=-27). The fix below measures each callout, wraps the over-wide ones
// to ≥2 lines (and shrinks font as a last resort), then clamps the center-x by HALF the widest
// line so the FULL text stays inside [CALLOUT_PAD, SVG_W - CALLOUT_PAD] (§5.3 no-clip contract).
const CALLOUT_FONT = 10;
const CALLOUT_MIN_FONT = 8; // legibility floor (gate 4b still applies — never shrink below this)
const CALLOUT_PAD = 8;
const CALLOUT_LINE_H = 12; // line advance for wrapped tspans
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2; // the widest a single callout line may render

/**
 * Lay out a callout so it never clips: word-wrap to lines each ≤ [CALLOUT_MAX_W] at the largest font
 * (down to [CALLOUT_MIN_FONT]) that fits, then return the lines + the chosen font + the widest line's
 * width. A single un-splittable token wider than the box at the min font is left on its own line
 * (degrades gracefully; the clamp below still keeps its center honest — it cannot be made narrower).
 */
function layoutCallout(text: string): { lines: string[]; font: number; maxLineW: number } {
  for (let font = CALLOUT_FONT; font >= CALLOUT_MIN_FONT; font--) {
    const lines = wrapToWidth(text, CALLOUT_MAX_W, font);
    const maxLineW = Math.max(...lines.map((l) => measureLabelWidth(l, font)), 1);
    // Accept the first font where every line fits, OR the min font (best effort at the floor).
    if (maxLineW <= CALLOUT_MAX_W || font === CALLOUT_MIN_FONT) {
      return { lines, font, maxLineW };
    }
  }
  // Unreachable (the loop returns at CALLOUT_MIN_FONT), but keep the type total.
  const lines = wrapToWidth(text, CALLOUT_MAX_W, CALLOUT_MIN_FONT);
  return { lines, font: CALLOUT_MIN_FONT, maxLineW: Math.max(...lines.map((l) => measureLabelWidth(l, CALLOUT_MIN_FONT)), 1) };
}

/** Greedy word-wrap on whitespace so each line's measured width stays ≤ [maxW] (best effort). */
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

type Laid = { id: string; x: number; y: number; w: number };

function layoutGraphTree(data: GraphTreeData): Map<string, Laid> {
  const byId = new Map(data.nodes.map((n) => [n.id, n]));
  const root = data.nodes.find((n) => !n.parent);
  if (!root) return new Map();
  const widthOf = (id: string) => measureLabelWidth(byId.get(id)!.label, LABEL_FONT) + BOX_PAD_X * 2;
  const built = hierarchy<GtNode>(root, (n) =>
    data.nodes.filter((c) => c.parent === n.id),
  );
  // nodeSize x = the widest label across the tree (reserves space so boxes never overlap).
  const maxW = Math.max(...data.nodes.map((n) => widthOf(n.id)), 1);
  const laidRoot = d3tree<GtNode>().nodeSize([maxW + 12, LEVEL_GAP])(built);
  const xs = laidRoot.descendants().map((d) => d.x);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const span = Math.max(maxX - minX, 1);
  const out = new Map<string, Laid>();
  for (const d of laidRoot.descendants()) {
    // normalize x into the canvas with a margin so the widest box at the edge cannot clip.
    const margin = maxW / 2 + 12;
    const usable = SVG_W - margin * 2;
    const nx = margin + ((d.x - minX) / span) * usable;
    out.set(d.data.id, { id: d.data.id, x: nx, y: 28 + d.depth * LEVEL_GAP, w: widthOf(d.data.id) });
  }
  return out;
}

function renderFrame(layout: Map<string, Laid>, labelOf: Map<string, string>) {
  return (frame: Frame<GraphTreeState>): ReactNode => {
    const hi = new Set(frame.state.highlightIds);
    const firstHi = frame.state.highlightIds[0];
    const calloutAnchor = firstHi ? layout.get(firstHi) : undefined;
    return (
      <>
        {frame.state.activeNodes.map((n) => {
          const pos = layout.get(n.id);
          if (!pos) return null;
          const isHi = hi.has(n.id);
          const w = labelWidthFor(labelOf.get(n.id) ?? n.label);
          return (
            <g key={n.id} data-node-id={n.id} transform={`translate(${pos.x},${pos.y})`}>
              <rect
                x={-w / 2}
                y={-BOX_H / 2}
                width={w}
                height={BOX_H}
                fill={isHi ? ACCENT : "#fff"}
                stroke={INK}
                strokeWidth={isHi ? 2 : 1}
              />
              <text
                x={0}
                y={4}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={LABEL_FONT}
                fontWeight={700}
                fill={INK}
              >
                {labelOf.get(n.id) ?? n.label}
              </text>
            </g>
          );
        })}
        {/* Callout ANCHORED to the first highlighted node's box (§5.3 — never a detached footer).
            No-clip by construction (Plan-4b Task 9 PM amendment): measure → wrap/shrink → clamp the
            center-x by HALF the widest line so the FULL text stays inside [CALLOUT_PAD, SVG_W-PAD]. */}
        {calloutAnchor && (() => {
          const { lines, font, maxLineW } = layoutCallout(frame.state.callout);
          // Clamp center-x so [cx - maxLineW/2, cx + maxLineW/2] ⊆ [CALLOUT_PAD, SVG_W - CALLOUT_PAD].
          // Each line is ≤ CALLOUT_MAX_W = SVG_W - 2·PAD, so this range is always non-empty.
          const half = maxLineW / 2;
          const loBound = CALLOUT_PAD + half;
          const hiBound = SVG_W - CALLOUT_PAD - half;
          const cx = Math.max(loBound, Math.min(hiBound, calloutAnchor.x));
          // Vertical: start below the node box; clamp the LAST line within [, SVG_H - PAD].
          const wantTop = calloutAnchor.y + BOX_H / 2 + 14;
          const blockH = (lines.length - 1) * CALLOUT_LINE_H;
          const top = Math.min(wantTop, SVG_H - CALLOUT_PAD - blockH);
          return (
            <text
              x={cx}
              y={top}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={font}
              fill={INK}
            >
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

    function labelWidthFor(t: string): number {
      return measureLabelWidth(t, LABEL_FONT) + BOX_PAD_X * 2;
    }
  };
}

export function GraphTreeFamily({ instanceId, dataJson, language, labels, onStep }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseGraphTreeData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromGraphTree(data), [data]);
  const layout = useMemo(() => layoutGraphTree(data), [data]);
  const lastIdx = Math.max(0, frames.length - 1);
  // labelOf evolves with deltas; recompute the final-per-node map so renderFrame shows live labels.
  const labelOf = useMemo(() => {
    const m = new Map(data.nodes.map((n) => [n.id, n.label]));
    return m;
  }, [data]);
  // Plan-4b Task 4 — thread onStep: the shell's idx callback becomes (idx, lastIdx) for the parent.
  // MUST be a STABLE reference (useCallback): the shell's onStep effect deps on props.onStep, so a
  // fresh arrow every render would re-fire the effect on every render → setStepDisplay → re-render →
  // new arrow → infinite render loop (the busy-loop). Stable ref breaks the cycle.
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  return (
    <AlgoStepperShell<GraphTreeState>
      title={`Graph/tree · ${instanceId}`}
      desc={language === "ro" ? "Arbore de descompunere; pas cu pas." : "Decomposition tree; step by step."}
      frames={frames}
      renderFrame={renderFrame(layout, labelOf)}
      testIdPrefix="graph-tree"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
    />
  );
}
