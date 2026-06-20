import { useCallback, useMemo, type ReactNode } from "react";
import { hierarchy, tree as d3tree } from "d3-hierarchy";
import { AlgoStepperShell, type Frame, type ShellLabels, type ShellLayout } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Render skins ──────────────────────────────────────────────────────────
// ONE geometry + ONE set of data-* stamps, TWO paint skins (mirrors SequenceArrayFamily). Colours are
// baked as LITERAL hex into the SVG presentation attributes at render time (NOT var() — var() in an SVG
// fill/stroke attribute is unreliable across engines), so a skin swap is pure paint and the colour-blind
// invariants (data-node-id + <text> label) stay green.
//   • "light" — the original brutalist-on-paper look (demo gallery + e2e + the trace harness, UNCHANGED).
//   • "dark"  — the lectie lesson surface: nodes float on the dark stage, light ink, accent-yellow frontier.
export type GtVariant = "light" | "dark";

const DARK = {
  bg: "#0e0e0e",       // panel-dark-bg (DESIGN.md DARK surface)
  paper: "#161616",    // unhighlighted node fill — a card on the dark stage, not stark white
  ink: "#f4f4f4",      // node label on an unhighlighted node (off-white)
  accent: "#fde047",   // yellow-300 — THE accent (highlighted frontier node)
  accentInk: "#000000",// label on an accent node
  rule: "#3a3a3a",     // hairline on an unhighlighted node
  callout: "#fde047",  // callout text (yellow, like the lectie callout chip)
} as const;

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

/**
 * Content-driven viewBox height: the LOWEST-painted-pixel y across EVERY frame (the deepest node box
 * bottom, and the deepest frame's callout last-line baseline + descender), plus a small bottom margin
 * mirroring the top gutter (y0 = 28 - BOX_H/2 ≈ 15). Taking the MAX across all frames keeps the box
 * STABLE while stepping (it never resizes per-frame). The viewBox HUGS the real content height in BOTH
 * directions — it SHRINKS to hug a short tree's void AND GROWS to fit a deep tree (depth ≥5 reaches y >
 * 360). The previous Math.min(SVG_H,…) cap CLIPPED a deep tree's deepest rows off the bottom of the
 * frame (the figure-noclip-gate caught the depth-6 fixture cropping +65u past a 360 viewBox); removing
 * the cap is the fix — SVG_H is now only the DEFAULT/short-tree fallback, never a ceiling. GATE-SAFE: no
 * node/callout x/y moves — only the viewBox bottom edge tracks where content actually ends; the callout
 * vertical clamp uses this same height so its text can never land below the frame.
 */
function computeContentHeight(
  layout: Map<string, Laid>,
  frames: Frame<GraphTreeState>[],
): number {
  // deepest node box bottom (node center y + half box height).
  let bottom = 0;
  for (const laid of layout.values()) bottom = Math.max(bottom, laid.y + BOX_H / 2);
  // deepest per-frame callout bottom. The callout anchors below the FIRST highlighted node and wraps to
  // ≥1 line; replicate the renderer's wantTop + block height (worst case = no down-clamp). The exact
  // wrap/font is recomputed by layoutCallout, matching the renderer one-for-one.
  for (const f of frames) {
    const firstHi = f.state.highlightIds[0];
    if (!firstHi) continue;
    const anchor = layout.get(firstHi);
    if (!anchor) continue;
    const { lines, font } = layoutCallout(f.state.callout);
    const wantTop = anchor.y + BOX_H / 2 + 14; // first-line baseline
    const blockH = (lines.length - 1) * CALLOUT_LINE_H;
    // last-line baseline + descender (~font*0.25) ≈ the callout's lowest painted pixel.
    bottom = Math.max(bottom, wantTop + blockH + font * 0.25);
  }
  const BOTTOM_MARGIN = 14; // mirrors the ~15px top gutter above the root node box
  // HUG the real content height in BOTH directions — no Math.min(SVG_H,…) ceiling. The old cap clipped a
  // deep tree (depth ≥5 reaches y > 360) to 360, cropping its deepest rows; a short tree already hugged to
  // its own (sub-360) content via the old min, so dropping the cap only FIXES the deep case and leaves the
  // short case unchanged. SVG_H remains the AlgoStepperShell default for non-self-sizing mounts, not a cap here.
  return Math.ceil(bottom + BOTTOM_MARGIN);
}

function renderFrame(layout: Map<string, Laid>, labelOf: Map<string, string>, variant: GtVariant = "light", contentH: number = SVG_H) {
  const dark = variant === "dark";
  // Per-skin paint. Literal hex (never var()) so the SVG attributes resolve in every engine.
  const nodeFill = (isHi: boolean) => (dark ? (isHi ? DARK.accent : DARK.paper) : isHi ? ACCENT : "#fff");
  const nodeStroke = (isHi: boolean) => (dark ? (isHi ? DARK.accent : DARK.rule) : INK);
  const nodeInk = (isHi: boolean) => (dark ? (isHi ? DARK.accentInk : DARK.ink) : INK);
  const calloutInk = dark ? DARK.callout : INK;
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
                rx={dark ? 3 : 0}
                fill={nodeFill(isHi)}
                stroke={nodeStroke(isHi)}
                strokeWidth={isHi ? 2 : 1}
              />
              <text
                x={0}
                y={4}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={LABEL_FONT}
                fontWeight={700}
                fill={nodeInk(isHi)}
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
          // Vertical: start below the node box; clamp the LAST line within [, contentH - PAD]. The clamp
          // bound is the CONTENT-DRIVEN height (not the fixed 360), so a hugged short-tree box still keeps
          // the callout inside its own frame; on a tree whose content reaches 360 contentH === SVG_H.
          const wantTop = calloutAnchor.y + BOX_H / 2 + 14;
          const blockH = (lines.length - 1) * CALLOUT_LINE_H;
          const top = Math.min(wantTop, contentH - CALLOUT_PAD - blockH);
          return (
            <text
              x={cx}
              y={top}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={font}
              fill={calloutInk}
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

/** ADDITIVE props over the family contract — `variant` selects the render skin (default "light", the
 *  demo/e2e/harness baseline) and `layout` lets the lesson surface drop the white box + side controls
 *  so the figure floats on a themed dark page. Both are omitted by the gallery + harness, unchanged. */
export type GraphTreeFamilyProps = FamilyRendererProps & {
  variant?: GtVariant;
  layout?: ShellLayout;
};

export function GraphTreeFamily({ instanceId, dataJson, language, labels, onStep, variant = "light", layout: shellLayout }: GraphTreeFamilyProps): ReactNode {
  const data = useMemo(() => parseGraphTreeData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromGraphTree(data), [data]);
  const layout = useMemo(() => layoutGraphTree(data), [data]);
  // Content-driven viewBox height (hugs a short tree; ===360 for a tree that already fills the box).
  const contentH = useMemo(() => computeContentHeight(layout, frames), [layout, frames]);
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
  // Force the content-driven viewBox height so the shell's hard frame HUGS the tree (a shallow tree no
  // longer floats in the 360 box). Spread keeps the family value last so it wins over a caller's pass.
  const effectiveLayout: ShellLayout = { ...shellLayout, viewBoxH: contentH };
  return (
    <AlgoStepperShell<GraphTreeState>
      title={`Graph/tree · ${instanceId}`}
      desc={language === "ro" ? "Arbore de descompunere; pas cu pas." : "Decomposition tree; step by step."}
      frames={frames}
      renderFrame={renderFrame(layout, labelOf, variant, contentH)}
      testIdPrefix="graph-tree"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
      layout={effectiveLayout}
    />
  );
}
