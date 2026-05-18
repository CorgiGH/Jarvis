import { useRef, useEffect } from "react";
import { ACCENT, INK } from "./theme";

export interface SumPlotTrackerProps {
  data: number[];
  mu: number;
  range?: [number, number];
}

const SVG_W = 480, SVG_H = 200;
const PAD_L = 48, PAD_R = 16, PAD_T = 16, PAD_B = 32;
const USABLE_W = SVG_W - PAD_L - PAD_R;
const USABLE_H = SVG_H - PAD_T - PAD_B;
const STEPS = 240;

function sumAbsDev(mu: number, data: number[]): number {
  return data.reduce((acc, x) => acc + Math.abs(x - mu), 0);
}

interface CurvePoint { svgX: number; svgY: number; mu: number; }

function buildCurve(data: number[], lo: number, hi: number): CurvePoint[] {
  const pts: CurvePoint[] = [];
  const yValues: number[] = [];
  for (let i = 0; i <= STEPS; i++) yValues.push(sumAbsDev(lo + (i / STEPS) * (hi - lo), data));
  const yMin = Math.min(...yValues), yMax = Math.max(...yValues);
  const yRange = yMax - yMin || 1;
  for (let i = 0; i <= STEPS; i++) {
    const mu = lo + (i / STEPS) * (hi - lo);
    pts.push({
      svgX: PAD_L + (i / STEPS) * USABLE_W,
      svgY: PAD_T + USABLE_H - ((yValues[i] - yMin) / yRange) * USABLE_H,
      mu,
    });
  }
  return pts;
}

function pathD(pts: CurvePoint[]): string {
  if (pts.length === 0) return "";
  return pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.svgX.toFixed(2)},${p.svgY.toFixed(2)}`).join(" ");
}

function interpolateMarker(mu: number, pts: CurvePoint[]): { x: number; y: number } {
  if (pts.length === 0) return { x: 0, y: 0 };
  if (mu <= pts[0].mu) return { x: pts[0].svgX, y: pts[0].svgY };
  if (mu >= pts[pts.length - 1].mu) return { x: pts[pts.length - 1].svgX, y: pts[pts.length - 1].svgY };
  let lo = 0, hi = pts.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (pts[mid].mu <= mu) lo = mid; else hi = mid;
  }
  const t = (mu - pts[lo].mu) / (pts[hi].mu - pts[lo].mu);
  return {
    x: pts[lo].svgX + t * (pts[hi].svgX - pts[lo].svgX),
    y: pts[lo].svgY + t * (pts[hi].svgY - pts[lo].svgY),
  };
}

export function SumPlotTracker({ data, mu, range }: SumPlotTrackerProps) {
  const lo = range?.[0] ?? Math.min(...data) - 1;
  const hi = range?.[1] ?? Math.max(...data) + 1;
  const curve = buildCurve(data, lo, hi);
  const pathStr = pathD(curve);

  const markerRef = useRef<SVGCircleElement>(null);
  const muRef = useRef(mu);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const prevMu = muRef.current;
    muRef.current = mu;
    if (Math.abs(mu - prevMu) < 0.001) return;
    cancelAnimationFrame(rafId.current);
    rafId.current = requestAnimationFrame(() => {
      if (!markerRef.current) return;
      const pos = interpolateMarker(mu, curve);
      markerRef.current.setAttribute("cx", pos.x.toFixed(2));
      markerRef.current.setAttribute("cy", pos.y.toFixed(2));
    });
    return () => cancelAnimationFrame(rafId.current);
  }, [mu, curve]);

  const initialPos = interpolateMarker(mu, curve);

  return (
    <svg width={SVG_W} height={SVG_H} viewBox={`0 0 ${SVG_W} ${SVG_H}`} data-testid="sum-plot-tracker">
      <line x1={PAD_L} y1={PAD_T} x2={PAD_L} y2={PAD_T + USABLE_H} stroke={INK} strokeWidth={1} opacity={0.3} />
      <line x1={PAD_L} y1={PAD_T + USABLE_H} x2={PAD_L + USABLE_W} y2={PAD_T + USABLE_H} stroke={INK} strokeWidth={1} opacity={0.3} />
      <text data-testid="sum-axis-label" x={10} y={PAD_T + USABLE_H / 2} fontSize={10} textAnchor="middle" transform={`rotate(-90, 10, ${PAD_T + USABLE_H / 2})`} fill={INK} opacity={0.6}>Σ|xᵢ − μ|</text>
      <path data-testid="sum-curve" d={pathStr} fill="none" stroke={INK} strokeWidth={2} />
      <circle ref={markerRef} data-testid="sum-marker" cx={initialPos.x} cy={initialPos.y} r={6} fill={ACCENT} stroke={INK} strokeWidth={2} />
    </svg>
  );
}
