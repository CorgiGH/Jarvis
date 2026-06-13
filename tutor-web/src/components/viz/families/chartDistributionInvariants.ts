/**
 * Plan-V family 4 — ChartDistribution semantic invariants (INV-5.2), mirrors graphTreeInvariants.ts.
 *
 * Each invariant receives the MOUNTED family's DOM (via data-cd / data-cd-xtick stamps) plus the
 * current frame state and asserts a semantic property of the RENDERED SVG geometry — read truth
 * back from the pixels, not from the model. All are exported as CHART_DIST_INVARIANTS so the
 * trace-match harness registers them with the family (INV-5.2: the family-specific list lives with
 * the family code).
 *
 * The geometry the renderer stamps:
 *   <path data-cd="curve" d="M.. L..">           the sampled PDF polyline (ink stroke)
 *   <path data-cd="shade" d="M.. L.. Z">          the shaded area (reveal ≥ 3 only)
 *   <line data-cd="axis-x"> / data-cd="axis-y">   the two axes
 *   <g data-cd-xtick="<value>"><line/><text/></g> each kept x tick (value attr = the data value)
 */

import type { Frame } from "../AlgoStepperShell";
import type { ChartDistributionState } from "./ChartDistributionFamily";

export type InvariantResult = { ok: true } | { ok: false; message: string };

export type ChartDistributionInvariant = (
  container: Element,
  frame: Frame<ChartDistributionState>,
  stepIdx: number,
  allFrames: Frame<ChartDistributionState>[],
) => InvariantResult;

// ── path-d parsing helpers ────────────────────────────────────────────────
/** Pull every (x,y) coordinate pair out of an SVG path `d` (M/L commands, comma or space sep). */
function pathPoints(d: string): { x: number; y: number }[] {
  const nums = d.match(/-?\d+(?:\.\d+)?/g);
  if (!nums) return [];
  const out: { x: number; y: number }[] = [];
  for (let i = 0; i + 1 < nums.length; i += 2) {
    out.push({ x: parseFloat(nums[i]), y: parseFloat(nums[i + 1]) });
  }
  return out;
}

function getCurve(container: Element): { x: number; y: number }[] {
  const el = container.querySelector('path[data-cd="curve"]');
  if (!el) return [];
  return pathPoints(el.getAttribute("d") ?? "");
}

/**
 * INV-1 axis-monotone-x: the curve polyline's rendered x coordinates are strictly increasing
 * left→right. If the family ever drew the curve out of order (x not monotone) the area-under-curve
 * reading would be meaningless. Vacuous if <2 sampled points are rendered.
 */
export const axisMonotoneX: ChartDistributionInvariant = (container, _frame, stepIdx) => {
  const pts = getCurve(container);
  if (pts.length < 2) return { ok: true };
  for (let i = 1; i < pts.length; i++) {
    if (!(pts[i].x > pts[i - 1].x)) {
      return {
        ok: false,
        message: `step ${stepIdx} axis-monotone-x FAIL: curve x not strictly increasing at vertex ${i} (${pts[i - 1].x} → ${pts[i].x})`,
      };
    }
  }
  return { ok: true };
};

/**
 * INV-2 point-on-curve: every rendered curve vertex sits within the plot box bounded by the two
 * axes — i.e. x ∈ [axisY.x, axisX.x2] and y ∈ [axisX.y (baseline), axisY.y2 (top)]. A vertex that
 * escapes the axes means the y-scaling clipped or overflowed (the no-clip-by-construction promise
 * for the data itself). Read the axis geometry back from the DOM (not constants).
 */
export const pointOnCurve: ChartDistributionInvariant = (container, _frame, stepIdx) => {
  const pts = getCurve(container);
  if (pts.length === 0) return { ok: true };
  const ax = container.querySelector('line[data-cd="axis-x"]');
  const ay = container.querySelector('line[data-cd="axis-y"]');
  if (!ax || !ay) return { ok: false, message: `step ${stepIdx} point-on-curve FAIL: axes not rendered` };
  const x0 = parseFloat(ay.getAttribute("x1") ?? "NaN"); // y-axis vertical line x
  const xRight = parseFloat(ax.getAttribute("x2") ?? "NaN"); // x-axis right end
  const yBase = parseFloat(ax.getAttribute("y1") ?? "NaN"); // baseline (y=0)
  const yTop = parseFloat(ay.getAttribute("y2") ?? "NaN"); // top of y-axis (y=max)
  const EPS = 1.5;
  for (const p of pts) {
    if (p.x < x0 - EPS || p.x > xRight + EPS) {
      return { ok: false, message: `step ${stepIdx} point-on-curve FAIL: vertex x=${p.x} outside plot x-range [${x0},${xRight}]` };
    }
    // SVG y grows downward: baseline (yBase) is the LARGEST y, top (yTop) the smallest.
    if (p.y > yBase + EPS || p.y < yTop - EPS) {
      return { ok: false, message: `step ${stepIdx} point-on-curve FAIL: vertex y=${p.y} outside plot y-range [${yTop},${yBase}]` };
    }
  }
  return { ok: true };
};

/**
 * INV-3 shading-within-curve: at every step where the shade is rendered (reveal ≥ 3), the shaded
 * region's bbox lies BETWEEN the curve and the baseline — i.e. its bottom equals the baseline (the
 * area is anchored to y=0) and its top is ≥ the highest curve point inside the interval (it never
 * rises above the curve). Also asserts the shade's x-span lies within the curve's x-span. Vacuous
 * before reveal 3 (no shade element rendered then).
 */
export const shadingWithinCurve: ChartDistributionInvariant = (container, frame, stepIdx) => {
  const shadeEl = container.querySelector('path[data-cd="shade"]');
  if (frame.state.reveal < 3) {
    // shade must NOT be present before its reveal beat
    if (shadeEl) {
      return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: shade rendered at reveal ${frame.state.reveal} (<3)` };
    }
    return { ok: true };
  }
  if (!shadeEl) return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: reveal ${frame.state.reveal} but no shade path` };

  const ax = container.querySelector('line[data-cd="axis-x"]');
  const baseline = parseFloat(ax?.getAttribute("y1") ?? "NaN");
  const shade = pathPoints(shadeEl.getAttribute("d") ?? "");
  if (shade.length < 3) return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: shade has <3 vertices` };

  const sxs = shade.map((p) => p.x);
  const sys = shade.map((p) => p.y);
  const sx0 = Math.min(...sxs), sx1 = Math.max(...sxs);
  const sBottom = Math.max(...sys); // largest y = lowest on screen
  const sTop = Math.min(...sys);

  const EPS = 1.5;
  // (a) anchored to the baseline (the area runs down to y=0)
  if (Math.abs(sBottom - baseline) > EPS) {
    return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: shade bottom y=${sBottom} ≠ baseline y=${baseline}` };
  }

  // (b) shade x-span ⊆ curve x-span
  const curve = getCurve(container);
  if (curve.length >= 2) {
    const cx0 = curve[0].x, cx1 = curve[curve.length - 1].x;
    if (sx0 < cx0 - EPS || sx1 > cx1 + EPS) {
      return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: shade x-span [${sx0},${sx1}] not within curve [${cx0},${cx1}]` };
    }
    // (c) shade top never rises above the curve at the overlapping x-range.
    // The minimal curve y (highest point) over [sx0,sx1] is the lowest the shade top may go.
    const curveInSpan = curve.filter((p) => p.x >= sx0 - EPS && p.x <= sx1 + EPS);
    if (curveInSpan.length > 0) {
      const curveTopMin = Math.min(...curveInSpan.map((p) => p.y));
      // shade top must be ≥ curveTopMin - EPS (shade does not go HIGHER on screen than the curve peak)
      if (sTop < curveTopMin - EPS) {
        return { ok: false, message: `step ${stepIdx} shading-within-curve FAIL: shade top y=${sTop} rises above curve peak y=${curveTopMin}` };
      }
    }
  }
  return { ok: true };
};

/**
 * INV-4 tick-no-overlap (the bar/region-proportionality slot, repurposed for this family's
 * proportional axis): every pair of rendered x-tick label boxes is horizontally disjoint. Reads
 * each <g data-cd-xtick> <text> back and asserts no two text boxes' x-extents intersect — the
 * machine guarantee that the "~4 labels stacked on top of each other" failure is impossible.
 * Uses each tick's rendered x and its measured-width proxy (text length × font) since jsdom does
 * not lay out SVG text; the renderer already thinned by MEASURED width, so this catches a renderer
 * regression that stops thinning.
 */
export const tickNoOverlap: ChartDistributionInvariant = (container, _frame, stepIdx) => {
  const ticks = Array.from(container.querySelectorAll("g[data-cd-xtick]"));
  if (ticks.length < 2) return { ok: true };
  const boxes: { x: number; halfW: number; text: string }[] = [];
  for (const g of ticks) {
    const txt = g.querySelector("text");
    if (!txt) continue;
    const x = parseFloat(txt.getAttribute("x") ?? "NaN");
    const fontPx = parseFloat(txt.getAttribute("font-size") ?? "9");
    const text = txt.textContent ?? "";
    // monospace proxy width (jsdom has no SVG text metrics); matches the renderer's 0.6em fallback.
    const halfW = (text.length * fontPx * 0.6) / 2;
    boxes.push({ x, halfW, text });
  }
  boxes.sort((p, q) => p.x - q.x);
  for (let i = 1; i < boxes.length; i++) {
    const prevRight = boxes[i - 1].x + boxes[i - 1].halfW;
    const curLeft = boxes[i].x - boxes[i].halfW;
    if (curLeft < prevRight) {
      return {
        ok: false,
        message: `step ${stepIdx} tick-no-overlap FAIL: x-tick "${boxes[i - 1].text}" (right ${prevRight.toFixed(1)}) overlaps "${boxes[i].text}" (left ${curLeft.toFixed(1)})`,
      };
    }
  }
  return { ok: true };
};

/**
 * The canonical invariant list for the chart-dist family (INV-5.2). Exported with the family so the
 * harness can register them keyed by "chart-dist" and enumerate them at totality check time.
 */
export const CHART_DIST_INVARIANTS: ChartDistributionInvariant[] = [
  axisMonotoneX,
  pointOnCurve,
  shadingWithinCurve,
  tickNoOverlap,
];
