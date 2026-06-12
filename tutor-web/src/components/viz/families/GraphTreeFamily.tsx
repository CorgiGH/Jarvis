import { useMemo, type ReactNode } from "react";
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
            <g key={n.id} transform={`translate(${pos.x},${pos.y})`}>
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
        {/* Callout ANCHORED to the first highlighted node's box (§5.3 — never a detached footer). */}
        {calloutAnchor && (
          <text
            x={Math.max(8, Math.min(SVG_W - 8, calloutAnchor.x))}
            y={Math.min(SVG_H - 10, calloutAnchor.y + BOX_H / 2 + 16)}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={10}
            fill={INK}
          >
            {frame.state.callout}
          </text>
        )}
      </>
    );

    function labelWidthFor(t: string): number {
      return measureLabelWidth(t, LABEL_FONT) + BOX_PAD_X * 2;
    }
  };
}

export function GraphTreeFamily({ instanceId, dataJson, language, labels }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseGraphTreeData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromGraphTree(data), [data]);
  const layout = useMemo(() => layoutGraphTree(data), [data]);
  // labelOf evolves with deltas; recompute the final-per-node map so renderFrame shows live labels.
  const labelOf = useMemo(() => {
    const m = new Map(data.nodes.map((n) => [n.id, n.label]));
    return m;
  }, [data]);
  return (
    <AlgoStepperShell<GraphTreeState>
      title={`Graph/tree · ${instanceId}`}
      desc={language === "ro" ? "Arbore de descompunere; pas cu pas." : "Decomposition tree; step by step."}
      frames={frames}
      renderFrame={renderFrame(layout, labelOf)}
      testIdPrefix="graph-tree"
      labels={labels}
    />
  );
}
