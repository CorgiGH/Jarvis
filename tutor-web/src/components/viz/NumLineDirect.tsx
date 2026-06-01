import { useRef, useCallback } from "react";
import { ACCENT, INK } from "./theme";

const TITLE_ID = "num-line-direct-title";
const DESC_ID = "num-line-direct-desc";

export interface NumLineDirectProps {
  data: number[];
  mu: number;
  onMu: (v: number) => void;
  min?: number;
  max?: number;
}

const SVG_W = 480;
const SVG_H = 80;
const PAD = 24;
const USABLE = SVG_W - PAD * 2;
const TICK_H = 14;
const AXIS_Y = 50;
const MARKER_R = 9;

function toSvgX(v: number, lo: number, hi: number): number {
  return PAD + ((v - lo) / (hi - lo)) * USABLE;
}
function fromSvgX(x: number, lo: number, hi: number): number {
  const clamped = Math.max(PAD, Math.min(SVG_W - PAD, x));
  return lo + ((clamped - PAD) / USABLE) * (hi - lo);
}

export function NumLineDirect({ data, mu, onMu, min, max }: NumLineDirectProps) {
  const lo = min ?? Math.min(...data) - 2;
  const hi = max ?? Math.max(...data) + 2;
  const muRef = useRef(mu);
  muRef.current = mu;
  const dragging = useRef(false);
  const rafId = useRef<number>(0);
  const pendingX = useRef<number | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  const prefersReduced = typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // NOTE: No imperative setAttribute path. Both the circle cx and the μ label x
  // derive purely from the `mu` prop via React render, so they always stay in sync.
  // The old useEffect that called markerRef.current.setAttribute("cx", ...) has
  // been removed — it caused the label to trail the circle during RAF-throttled drags.

  const flushRAF = useCallback(() => {
    if (pendingX.current === null) return;
    const svgEl = svgRef.current;
    const rect = svgEl ? svgEl.getBoundingClientRect() : { left: 0 };
    const svgX = pendingX.current - rect.left;
    const newMu = parseFloat(fromSvgX(svgX, lo, hi).toFixed(3));
    pendingX.current = null;
    if (Math.abs(newMu - muRef.current) >= 0.05) onMu(newMu);
  }, [lo, hi, onMu]);

  const onPointerDown = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragging.current = true;
  }, []);

  const onPointerMove = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    if (!dragging.current) return;
    const svgEl = svgRef.current;
    const rect = svgEl ? svgEl.getBoundingClientRect() : { left: 0 };
    const rawSvgX = e.clientX - rect.left;
    const currentX = toSvgX(muRef.current, lo, hi);
    if (Math.abs(rawSvgX - currentX) < 1) return;
    pendingX.current = e.clientX;
    if (prefersReduced) flushRAF();
    else {
      cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(flushRAF);
    }
  }, [flushRAF, lo, prefersReduced]);

  const onPointerUp = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.releasePointerCapture(e.pointerId);
    dragging.current = false;
    cancelAnimationFrame(rafId.current);
    if (pendingX.current !== null) flushRAF();
  }, [flushRAF]);

  return (
    <div style={{ position: "relative" }}>
      <svg
        ref={svgRef}
        width={SVG_W}
        height={SVG_H}
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        data-testid="num-line-direct"
        role="img"
        aria-labelledby={`${TITLE_ID} ${DESC_ID}`}
        style={{ userSelect: "none", touchAction: "none" }}
      >
        <title id={TITLE_ID}>Number line — mean (μ) adjuster</title>
        <desc id={DESC_ID}>
          {`A number line from ${lo.toFixed(1)} to ${hi.toFixed(1)} with ${data.length} sample point${data.length !== 1 ? "s" : ""}. Drag the μ marker to set the mean.`}
        </desc>
        <line x1={PAD} y1={AXIS_Y} x2={SVG_W - PAD} y2={AXIS_Y} stroke={INK} strokeWidth={1.5} opacity={0.35} />
        {data.map((v, i) => (
          <line
            key={i}
            data-testid="sample-tick"
            x1={toSvgX(v, lo, hi)} y1={AXIS_Y - TICK_H / 2}
            x2={toSvgX(v, lo, hi)} y2={AXIS_Y + TICK_H / 2}
            stroke={INK} strokeWidth={2} opacity={0.6}
          />
        ))}
        <circle
          data-testid="mu-marker"
          cx={toSvgX(mu, lo, hi)} cy={AXIS_Y} r={MARKER_R}
          fill={ACCENT} stroke={INK} strokeWidth={2}
          style={{ cursor: "ew-resize" }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
        />
        <text data-testid="mu-label" x={toSvgX(mu, lo, hi)} y={AXIS_Y - MARKER_R - 4} textAnchor="middle" fontSize={11} fill={INK} fontWeight="bold">μ</text>
      </svg>
      <div
        aria-live="polite"
        role="status"
        data-testid="num-line-direct-live"
        style={{ position: "absolute", left: -9999 }}
      >
        {`μ = ${mu}`}
      </div>
    </div>
  );
}
