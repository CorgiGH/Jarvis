import { useCallback, useMemo, type ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "../AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

// ── Typed slots (mirrors GraphTreeFamily §0.9G) ────────────────────────────
// A CHART/DISTRIBUTION instance is a sampled PDF curve plus a marked interval [a,b].
// The figure reveals in cumulative beats: (1) axes+curve, (2) interval marks,
// (3) shaded area P(a≤X≤b), (4) the probability annotation.
export type CdPoint = { x: number; y: number };
export type CdInterval = { a: number; b: number };
export type CdStep = {
  /** cumulative reveal level after this step: 1=curve 2=interval 3=shade 4=annotate. */
  reveal: 1 | 2 | 3 | 4;
  callout: string;
};
export type ChartDistributionData = {
  /** sampled curve, strictly x-ascending; y ≥ 0 (a density). */
  points: CdPoint[];
  /** the highlighted interval; a < b, both within the points' x-range. */
  interval: CdInterval;
  /** P(a≤X≤b) for the annotation beat (instance-supplied so the family never
   *  invents a number; the trace oracle re-derives it from `points` and checks). */
  probability: number;
  /** x-axis label (RO). */
  xLabel: string;
  /** ordered reveal beats. */
  steps: CdStep[];
};

// What one rendered step exposes to the trace-match oracle + renderFrame.
export type ChartDistributionState = {
  reveal: 1 | 2 | 3 | 4;
  callout: string;
};

/**
 * Hand-rolled validation guard (zod-free, mirrors parseGraphTreeData). On any structural fault it
 * throws a load error naming the instance id AND the offending field, so a broken instance fails
 * admission loud (INV-5.5) instead of rendering an empty/garbled figure.
 */
export function parseChartData(dataJson: string, instanceId: string): ChartDistributionData {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`chart-dist instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;

  if (!Array.isArray(obj.points))
    throw new Error(`chart-dist instance '${instanceId}': missing/invalid field 'points' (expected array)`);
  if (obj.points.length < 2)
    throw new Error(`chart-dist instance '${instanceId}': field 'points' must have ≥2 samples`);
  const points: CdPoint[] = obj.points.map((p, i) => {
    const pt = p as Record<string, unknown>;
    if (typeof pt.x !== "number" || !Number.isFinite(pt.x))
      throw new Error(`chart-dist instance '${instanceId}': points[${i}] missing/invalid field 'x' (finite number)`);
    if (typeof pt.y !== "number" || !Number.isFinite(pt.y))
      throw new Error(`chart-dist instance '${instanceId}': points[${i}] missing/invalid field 'y' (finite number)`);
    if (pt.y < 0)
      throw new Error(`chart-dist instance '${instanceId}': points[${i}].y is negative (${pt.y}) — a density is ≥0`);
    return { x: pt.x, y: pt.y };
  });
  for (let i = 1; i < points.length; i++) {
    if (!(points[i].x > points[i - 1].x))
      throw new Error(
        `chart-dist instance '${instanceId}': points must be strictly x-ascending — points[${i}].x (${points[i].x}) ≤ points[${i - 1}].x (${points[i - 1].x})`,
      );
  }

  const iv = obj.interval as Record<string, unknown> | undefined;
  if (!iv || typeof iv.a !== "number" || typeof iv.b !== "number")
    throw new Error(`chart-dist instance '${instanceId}': missing/invalid field 'interval' (expected {a:number,b:number})`);
  if (!(iv.a < iv.b))
    throw new Error(`chart-dist instance '${instanceId}': interval.a (${iv.a}) must be < interval.b (${iv.b})`);
  const xMin = points[0].x;
  const xMax = points[points.length - 1].x;
  if (iv.a < xMin || iv.b > xMax)
    throw new Error(
      `chart-dist instance '${instanceId}': interval [${iv.a},${iv.b}] is outside the curve's x-range [${xMin},${xMax}]`,
    );

  if (typeof obj.probability !== "number" || !Number.isFinite(obj.probability))
    throw new Error(`chart-dist instance '${instanceId}': missing/invalid field 'probability' (finite number)`);
  if (obj.probability < 0 || obj.probability > 1)
    throw new Error(`chart-dist instance '${instanceId}': field 'probability' (${obj.probability}) must be in [0,1]`);

  if (typeof obj.xLabel !== "string")
    throw new Error(`chart-dist instance '${instanceId}': missing/invalid field 'xLabel' (string)`);

  if (!Array.isArray(obj.steps))
    throw new Error(`chart-dist instance '${instanceId}': missing/invalid field 'steps' (expected array)`);
  if (obj.steps.length === 0)
    throw new Error(`chart-dist instance '${instanceId}': field 'steps' must be non-empty`);
  const steps: CdStep[] = obj.steps.map((s, i) => {
    const step = s as Record<string, unknown>;
    if (step.reveal !== 1 && step.reveal !== 2 && step.reveal !== 3 && step.reveal !== 4)
      throw new Error(`chart-dist instance '${instanceId}': steps[${i}].reveal must be one of 1|2|3|4 (got ${String(step.reveal)})`);
    if (typeof step.callout !== "string")
      throw new Error(`chart-dist instance '${instanceId}': steps[${i}] missing field 'callout' (string)`);
    return { reveal: step.reveal, callout: step.callout };
  });

  return {
    points,
    interval: { a: iv.a, b: iv.b },
    probability: obj.probability,
    xLabel: obj.xLabel,
    steps,
  };
}

/**
 * Build one Frame per step. Pure. The aria string carries the step callout (so screen readers and
 * the FigureReveal gate see the same teaching beat the figure shows).
 */
export function framesFromChartData(data: ChartDistributionData): Frame<ChartDistributionState>[] {
  return data.steps.map((step) => ({
    state: { reveal: step.reveal, callout: step.callout },
    aria: step.callout,
  }));
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────
// Off-DOM 2D context ONLY for text extents (NOT a canvas figure; the figure is pure SVG, §5.6).
let measureCanvas: { ctx: CanvasRenderingContext2D | null } | null = null;
function measureLabelWidth(text: string, fontPx: number): number {
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

// Frame bands (disjoint by construction — each text class owns its own vertical band so two text
// boxes can NEVER overlap vertically; horizontal overlap is killed per-class by measurement):
//   [0, CALLOUT_BAND_H]                  → the step callout (top gutter)
//   [PAD_T, PLOT_Y0]                     → the plot (axes, curve, shade, interval marks, annotation)
//   [PLOT_Y0, SVG_H]                     → x-axis tick row + x-axis label row (bottom gutter)
const PAD_L = 44;            // y-axis gutter (tick labels live here)
const PAD_R = 16;
const CALLOUT_BAND_H = 40;   // top band reserved for a ≤2-line callout (lines at 14 and 27)
const PAD_T = CALLOUT_BAND_H; // plot top starts below the callout band
const PAD_B = 56;            // bottom gutter: x-axis ticks (1 row) + axis label (1 row)
const PLOT_X0 = PAD_L;
const PLOT_X1 = SVG_W - PAD_R;
const PLOT_Y0 = SVG_H - PAD_B; // baseline (y=0)
const PLOT_Y1 = PAD_T;         // top of plot (y=max)
const PLOT_W = PLOT_X1 - PLOT_X0;
const PLOT_H = PLOT_Y0 - PLOT_Y1;

const TICK_FONT = 9;
const AXIS_LABEL_FONT = 10;
const TICK_LEN = 4;
const TICK_GAP_MIN = 6; // min horizontal gap between two x-tick label boxes (no-overlap floor)

// ── Callout geometry — VERBATIM no-clip-by-construction logic from GraphTreeFamily ──
// Measure → wrap to ≥2 lines → shrink to CALLOUT_MIN_FONT → clamp center-x by HALF the widest
// line so the FULL text stays inside [CALLOUT_PAD, SVG_W - CALLOUT_PAD] (§5.3 no-clip contract).
const CALLOUT_FONT = 11;
const CALLOUT_MIN_FONT = 8;
const CALLOUT_PAD = 8;
const CALLOUT_LINE_H = 13;
const CALLOUT_MAX_W = SVG_W - CALLOUT_PAD * 2;

function layoutCallout(text: string): { lines: string[]; font: number; maxLineW: number } {
  for (let font = CALLOUT_FONT; font >= CALLOUT_MIN_FONT; font--) {
    const lines = wrapToWidth(text, CALLOUT_MAX_W, font);
    const maxLineW = Math.max(...lines.map((l) => measureLabelWidth(l, font)), 1);
    if (maxLineW <= CALLOUT_MAX_W || font === CALLOUT_MIN_FONT) {
      return { lines, font, maxLineW };
    }
  }
  const lines = wrapToWidth(text, CALLOUT_MAX_W, CALLOUT_MIN_FONT);
  return { lines, font: CALLOUT_MIN_FONT, maxLineW: Math.max(...lines.map((l) => measureLabelWidth(l, CALLOUT_MIN_FONT)), 1) };
}

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

// ── Axis layout — MEASURE every tick label, thin the set so no two boxes overlap ────────
type Tick = { value: number; px: number; text: string };

function fmtTick(v: number): string {
  // compact, locale-free: integers stay integer; else 1 decimal.
  if (Number.isInteger(v)) return String(v);
  return v.toFixed(1);
}

/**
 * Build x-axis ticks at every integer in [xMin,xMax], then THIN greedily so each tick label's
 * MEASURED box (centered on its pixel x) clears the previous kept label's box by ≥TICK_GAP_MIN.
 * Endpoints (a,b are integers in our content) survive because they fall on integer grid; thinning
 * never drops the first/last tick. This makes x-axis label overlap impossible by construction.
 */
function buildXTicks(xMin: number, xMax: number): Tick[] {
  const xToPx = (x: number) => PLOT_X0 + ((x - xMin) / (xMax - xMin)) * PLOT_W;
  const candidates: Tick[] = [];
  const start = Math.ceil(xMin);
  const end = Math.floor(xMax);
  for (let v = start; v <= end; v++) {
    const text = fmtTick(v);
    candidates.push({ value: v, px: xToPx(v), text });
  }
  if (candidates.length === 0) {
    // degenerate range: just the endpoints
    candidates.push({ value: xMin, px: xToPx(xMin), text: fmtTick(xMin) });
    candidates.push({ value: xMax, px: xToPx(xMax), text: fmtTick(xMax) });
  }
  // greedy left→right keep: only keep a tick whose left edge clears the last kept right edge.
  const kept: Tick[] = [];
  let lastRight = -Infinity;
  for (const t of candidates) {
    const halfW = measureLabelWidth(t.text, TICK_FONT) / 2;
    const left = t.px - halfW;
    const right = t.px + halfW;
    if (left >= lastRight + TICK_GAP_MIN) {
      kept.push(t);
      lastRight = right;
    }
  }
  // Always include the final candidate (right edge of the axis) if thinning dropped it AND it
  // does not collide with the last kept tick.
  const last = candidates[candidates.length - 1];
  if (kept.length === 0 || kept[kept.length - 1].value !== last.value) {
    const halfW = measureLabelWidth(last.text, TICK_FONT) / 2;
    if (last.px - halfW >= lastRight + TICK_GAP_MIN) kept.push(last);
  }
  return kept;
}

/** y-axis ticks at 0 and yMax only (2 labels, comfortably spaced vertically — no overlap risk). */
function buildYTicks(yMax: number): Tick[] {
  const yToPx = (y: number) => PLOT_Y0 - (y / yMax) * PLOT_H;
  return [
    { value: 0, px: yToPx(0), text: "0" },
    { value: yMax, px: yToPx(yMax), text: fmtTick(yMax) },
  ];
}

type Layout = {
  xMin: number;
  xMax: number;
  yMax: number;
  xToPx: (x: number) => number;
  yToPx: (y: number) => number;
  curvePath: string;
  shadePath: string;
  shadeBBox: { x0: number; x1: number; yTop: number; yBottom: number };
  xTicks: Tick[];
  yTicks: Tick[];
};

function computeLayout(data: ChartDistributionData): Layout {
  const xMin = data.points[0].x;
  const xMax = data.points[data.points.length - 1].x;
  const yMax = Math.max(...data.points.map((p) => p.y), 1e-9);
  const xToPx = (x: number) => PLOT_X0 + ((x - xMin) / (xMax - xMin)) * PLOT_W;
  const yToPx = (y: number) => PLOT_Y0 - (y / yMax) * PLOT_H;

  // Curve polyline path.
  const curvePath = data.points
    .map((p, i) => `${i === 0 ? "M" : "L"}${xToPx(p.x).toFixed(2)},${yToPx(p.y).toFixed(2)}`)
    .join(" ");

  // Shaded area under the curve between [a,b]: sample the curve at the same grid restricted to
  // [a,b], plus the exact endpoints (interpolated) so the shade hugs the curve and the baseline.
  const { a, b } = data.interval;
  const yAt = (x: number): number => {
    if (x <= data.points[0].x) return data.points[0].y;
    if (x >= data.points[data.points.length - 1].x) return data.points[data.points.length - 1].y;
    for (let i = 1; i < data.points.length; i++) {
      const p0 = data.points[i - 1];
      const p1 = data.points[i];
      if (x <= p1.x) {
        const t = (x - p0.x) / (p1.x - p0.x);
        return p0.y + t * (p1.y - p0.y);
      }
    }
    return data.points[data.points.length - 1].y;
  };
  const shadeXs: number[] = [a];
  for (const p of data.points) if (p.x > a && p.x < b) shadeXs.push(p.x);
  shadeXs.push(b);
  const top = shadeXs.map((x) => `${xToPx(x).toFixed(2)},${yToPx(yAt(x)).toFixed(2)}`);
  const shadePath =
    `M${xToPx(a).toFixed(2)},${PLOT_Y0.toFixed(2)} ` +
    `L${top.join(" L")} ` +
    `L${xToPx(b).toFixed(2)},${PLOT_Y0.toFixed(2)} Z`;

  // BBox of the shaded region (for the within-curve invariant).
  const shadeYTopPx = Math.min(...shadeXs.map((x) => yToPx(yAt(x))));
  const shadeBBox = {
    x0: xToPx(a),
    x1: xToPx(b),
    yTop: shadeYTopPx,
    yBottom: PLOT_Y0,
  };

  return {
    xMin,
    xMax,
    yMax,
    xToPx,
    yToPx,
    curvePath,
    shadePath,
    shadeBBox,
    xTicks: buildXTicks(xMin, xMax),
    yTicks: buildYTicks(yMax),
  };
}

function renderFrame(data: ChartDistributionData, layout: Layout) {
  const { a, b } = data.interval;
  const aPx = layout.xToPx(a);
  const bPx = layout.xToPx(b);

  return (frame: Frame<ChartDistributionState>): ReactNode => {
    const reveal = frame.state.reveal;
    const showInterval = reveal >= 2;
    const showShade = reveal >= 3;
    const showAnnot = reveal >= 4;

    // Probability label text (annotation beat). Measured + clamped like the callout.
    const probText = `P(${fmtTick(a)} ≤ X ≤ ${fmtTick(b)}) = ${data.probability.toFixed(2)}`;

    return (
      <>
        {/* ── AXES (always) ── */}
        <line
          data-cd="axis-x"
          x1={PLOT_X0}
          y1={PLOT_Y0}
          x2={PLOT_X1}
          y2={PLOT_Y0}
          stroke={INK}
          strokeWidth={1.5}
        />
        <line
          data-cd="axis-y"
          x1={PLOT_X0}
          y1={PLOT_Y0}
          x2={PLOT_X0}
          y2={PLOT_Y1}
          stroke={INK}
          strokeWidth={1.5}
        />

        {/* x ticks + measured/thinned labels (no overlap by construction) */}
        {layout.xTicks.map((t) => (
          <g key={`xt-${t.value}`} data-cd-xtick={t.value}>
            <line x1={t.px} y1={PLOT_Y0} x2={t.px} y2={PLOT_Y0 + TICK_LEN} stroke={INK} strokeWidth={1} />
            <text
              x={t.px}
              y={PLOT_Y0 + TICK_LEN + TICK_FONT + 2}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={TICK_FONT}
              fill={INK}
            >
              {t.text}
            </text>
          </g>
        ))}

        {/* y ticks + labels (0 and yMax only) */}
        {layout.yTicks.map((t) => (
          <g key={`yt-${t.value}`} data-cd-ytick={t.value}>
            <line x1={PLOT_X0 - TICK_LEN} y1={t.px} x2={PLOT_X0} y2={t.px} stroke={INK} strokeWidth={1} />
            <text
              x={PLOT_X0 - TICK_LEN - 2}
              y={t.px + TICK_FONT / 2 - 1}
              textAnchor="end"
              fontFamily={FONT_FAMILY}
              fontSize={TICK_FONT}
              fill={INK}
            >
              {t.text}
            </text>
          </g>
        ))}

        {/* x-axis label, centered under the plot (single short token — fits by construction) */}
        <text
          x={(PLOT_X0 + PLOT_X1) / 2}
          y={SVG_H - 6}
          textAnchor="middle"
          fontFamily={FONT_FAMILY}
          fontSize={AXIS_LABEL_FONT}
          fontWeight={700}
          fill={INK}
        >
          {data.xLabel}
        </text>

        {/* ── SHADE (reveal ≥ 3) — drawn under the curve so the curve stroke stays crisp on top ── */}
        {showShade && (
          <path data-cd="shade" d={layout.shadePath} fill={ACCENT} stroke="none" fillOpacity={0.55} />
        )}

        {/* ── CURVE (always) ── */}
        <path data-cd="curve" d={layout.curvePath} fill="none" stroke={INK} strokeWidth={2} />

        {/* ── INTERVAL marks (reveal ≥ 2): vertical drops at a and b + value labels on the axis ── */}
        {showInterval && (
          <>
            <line data-cd="mark-a" x1={aPx} y1={PLOT_Y0} x2={aPx} y2={layout.yToPx(yAtClamp(data, a))} stroke={INK} strokeWidth={1.5} strokeDasharray="4 3" />
            <line data-cd="mark-b" x1={bPx} y1={PLOT_Y0} x2={bPx} y2={layout.yToPx(yAtClamp(data, b))} stroke={INK} strokeWidth={1.5} strokeDasharray="4 3" />
            {/* a / b badges sit just above the baseline, anchored to the drop; small + measured so
                they cannot collide with the tick labels below the axis. */}
            <text x={aPx} y={PLOT_Y0 - 4} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={TICK_FONT + 1} fontWeight={700} fill={INK}>a</text>
            <text x={bPx} y={PLOT_Y0 - 4} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={TICK_FONT + 1} fontWeight={700} fill={INK}>b</text>
          </>
        )}

        {/* ── ANNOTATION (reveal ≥ 4): the probability, measured + clamped inside the frame ── */}
        {showAnnot && (() => {
          const font = 10;
          const w = measureLabelWidth(probText, font);
          const half = w / 2;
          // prefer centered over the shaded region; clamp so the full box stays in-frame.
          const want = (aPx + bPx) / 2;
          const cx = Math.max(CALLOUT_PAD + half, Math.min(SVG_W - CALLOUT_PAD - half, want));
          // The annotation box lives INSIDE the plot box [PLOT_Y1, PLOT_Y0] — never in the callout
          // band above. Prefer just above the shaded peak; clamp the box so its TOP stays ≥ PLOT_Y1
          // (no collision with the top-band callout) and its BOTTOM stays ≤ PLOT_Y0.
          const boxH = font + 6;
          const wantTop = layout.shadeBBox.yTop - 6 - boxH;
          const rectTop = Math.max(PLOT_Y1 + 2, Math.min(PLOT_Y0 - boxH - 2, wantTop));
          return (
            <g data-cd="annot">
              <rect x={cx - half - 4} y={rectTop} width={w + 8} height={boxH} fill="#fff" stroke={INK} strokeWidth={1} />
              <text x={cx} y={rectTop + font + 1} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={font} fontWeight={700} fill={INK}>
                {probText}
              </text>
            </g>
          );
        })()}

        {/* ── CALLOUT (always) — anchored to the plot, measured → wrapped → clamped (no-clip). ── */}
        {(() => {
          const { lines, font, maxLineW } = layoutCallout(frame.state.callout);
          const half = maxLineW / 2;
          const loBound = CALLOUT_PAD + half;
          const hiBound = SVG_W - CALLOUT_PAD - half;
          const cx = Math.max(loBound, Math.min(hiBound, (PLOT_X0 + PLOT_X1) / 2));
          // Callout lives in the TOP band [0, CALLOUT_BAND_H]; first line baseline at 14, each next
          // line +CALLOUT_LINE_H. Height-aware clamp: if the block (rare 3+ lines) would descend
          // past the band, shift it UP so the LAST line baseline never crosses CALLOUT_BAND_H-2 —
          // it can never enter the plot box, so it cannot overlap any plot text by construction.
          const blockH = (lines.length - 1) * CALLOUT_LINE_H;
          const firstWant = 14;
          const top = Math.min(firstWant, CALLOUT_BAND_H - 2 - blockH);
          return (
            <text x={cx} y={top} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={font} fill={INK}>
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
  };
}

/** y at x by linear interpolation on the sampled curve (clamped to the endpoints). */
function yAtClamp(data: ChartDistributionData, x: number): number {
  const pts = data.points;
  if (x <= pts[0].x) return pts[0].y;
  if (x >= pts[pts.length - 1].x) return pts[pts.length - 1].y;
  for (let i = 1; i < pts.length; i++) {
    if (x <= pts[i].x) {
      const t = (x - pts[i - 1].x) / (pts[i].x - pts[i - 1].x);
      return pts[i - 1].y + t * (pts[i].y - pts[i - 1].y);
    }
  }
  return pts[pts.length - 1].y;
}

export function ChartDistributionFamily({ instanceId, dataJson, language, labels, onStep }: FamilyRendererProps): ReactNode {
  const data = useMemo(() => parseChartData(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromChartData(data), [data]);
  const layout = useMemo(() => computeLayout(data), [data]);
  const lastIdx = Math.max(0, frames.length - 1);
  // STABLE onStep (useCallback) — see GraphTreeFamily.tsx:288: a fresh arrow every render re-fires
  // the shell's onStep effect → setState → re-render → new arrow → infinite render loop.
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  return (
    <AlgoStepperShell<ChartDistributionState>
      title={`Chart/distribution · ${instanceId}`}
      desc={
        language === "ro"
          ? "Densitate de probabilitate; aria de sub curbă, pas cu pas."
          : "Probability density; area under the curve, step by step."
      }
      frames={frames}
      renderFrame={renderFrame(data, layout)}
      testIdPrefix="chart-dist"
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
    />
  );
}
