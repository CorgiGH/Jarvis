import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame, type PredictionGate } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "./theme";
import {
  AnimatePresence,
  FadeText,
  PopIn,
  TweenText,
  motion,
} from "./motion-helpers";

type Vec2 = [number, number];
type Mat2 = [[number, number], [number, number]];

interface Eigenvector {
  v: Vec2;
  lambda: number;
}

interface MatrixTransformProps {
  target: Mat2;
  eigenvectors: Eigenvector[];
  w: Vec2;
  testRayCount?: number;
  predictionGate?: PredictionGate;
  /** @deprecated Shell owns ARIA live region; this prop is ignored. */
  liveRegionId?: string;
}

type MatrixState = { t: number };

const FRAME_COUNT = 21; // t = 0, 0.05, ..., 1.0

const CX = 240;
const CY = 180;
const UNIT = 70;

const TWEEN = { type: "tween" as const, duration: 0.5, ease: "easeInOut" as const };

function apply(M: Mat2, v: Vec2): Vec2 {
  return [M[0][0] * v[0] + M[0][1] * v[1], M[1][0] * v[0] + M[1][1] * v[1]];
}

function lerpMat(M: Mat2, t: number): Mat2 {
  return [
    [1 + t * (M[0][0] - 1), t * M[0][1]],
    [t * M[1][0], 1 + t * (M[1][1] - 1)],
  ];
}

function toSvg(v: Vec2): Vec2 {
  return [CX + v[0] * UNIT, CY - v[1] * UNIT];
}

function buildTestRays(count: number): Vec2[] {
  const arr: Vec2[] = [];
  for (let i = 0; i < count; i++) {
    const angle = (2 * Math.PI * i) / count;
    arr.push([Math.cos(angle), Math.sin(angle)]);
  }
  return arr;
}

function ariaFor(t: number): string {
  if (t < 0.05) return "Identity — no transformation applied.";
  if (t > 0.95)
    return "Full matrix applied. Yellow eigenvectors stayed parallel, scaled by their eigenvalues. Non-eigenvector w rotated.";
  return `Matrix interpolated at t = ${t.toFixed(2)}.`;
}

function buildFrames(): Frame<MatrixState>[] {
  return Array.from({ length: FRAME_COUNT }, (_, i) => {
    const t = i / (FRAME_COUNT - 1);
    return { state: { t }, aria: ariaFor(t) };
  });
}

const VOICE_FRAME_INDICES = [0, 5, 10, 15, 20];
const VOICE_THRESHOLDS = [0, 25, 50, 75, 100];

function buildVoiceMap(): Record<number, string> {
  const base =
    (typeof import.meta !== "undefined" &&
      (import.meta as { env?: { BASE_URL?: string } }).env?.BASE_URL) ||
    "/";
  const map: Record<number, string> = {};
  VOICE_FRAME_INDICES.forEach((idx, i) => {
    map[idx] = `${base}audio/viz/eigenvector/eigenvector_t${VOICE_THRESHOLDS[i]}.mp3`;
  });
  return map;
}

const FRAMES = buildFrames();
const VOICE_MAP = buildVoiceMap();

export function MatrixTransform({
  target,
  eigenvectors,
  w,
  testRayCount = 8,
  predictionGate,
}: MatrixTransformProps): ReactNode {
  const testRays = buildTestRays(testRayCount);

  const renderFrame = (frame: Frame<MatrixState>): ReactNode => {
    const { t } = frame.state;
    const M = lerpMat(target, t);
    const renderedTestRays = testRays.map((v) => toSvg(apply(M, v)));
    const renderedEigs = eigenvectors.map((e) => ({
      end: toSvg(apply(M, e.v)),
      lambda: e.lambda,
    }));
    const renderedW = toSvg(apply(M, w));
    const wIsDashed = t < 0.05;

    return (
      <>
        <defs>
          <marker
            id="mt-ink-arrow"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill={INK} />
          </marker>
          <marker
            id="mt-yellow-arrow"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path
              d="M 0 0 L 10 5 L 0 10 z"
              fill={ACCENT}
              stroke={INK}
              strokeWidth="1"
            />
          </marker>
        </defs>

        {/* axes (static) */}
        <line x1={40} y1={CY} x2={440} y2={CY} stroke={INK} strokeWidth="1" />
        <line x1={CX} y1={20} x2={CX} y2={340} stroke={INK} strokeWidth="1" />
        <text x={446} y={CY + 4} fontFamily={FONT_FAMILY} fontSize={11} fill={INK}>
          x
        </text>
        <text x={CX + 4} y={20} fontFamily={FONT_FAMILY} fontSize={11} fill={INK}>
          y
        </text>

        {/* readout — t tweens */}
        <TweenText
          x={20}
          y={36}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fontWeight={700}
          fill={INK}
          value={t}
          formatter={(n) => `M(t) · t = ${n.toFixed(2)}`}
        />
        <text
          x={20}
          y={52}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fill={INK}
          opacity={0.6}
        >
          M(0) = I · M(1) = [[{target[0][0]},{target[0][1]}],[{target[1][0]},
          {target[1][1]}]]
        </text>

        {/* gray test rays — persistent, endpoints animate */}
        {renderedTestRays.map(([x2, y2], i) => (
          <motion.line
            key={`ray-${i}`}
            x1={CX}
            y1={CY}
            initial={false}
            animate={{ x2, y2 }}
            transition={TWEEN}
            stroke={INK}
            strokeWidth={0.8}
            opacity={0.4}
          />
        ))}

        {/* w (non-eigenvector) — dashed at t=0, pops in/out */}
        <AnimatePresence>
          {wIsDashed && (
            <PopIn key="w-dashed" durationMs={250}>
              <line
                x1={CX}
                y1={CY}
                x2={toSvg(w)[0]}
                y2={toSvg(w)[1]}
                stroke={INK}
                strokeWidth={1.5}
                strokeDasharray="4 3"
              />
            </PopIn>
          )}
        </AnimatePresence>

        {/* w (non-eigenvector) — animated endpoint */}
        <motion.line
          x1={CX}
          y1={CY}
          initial={false}
          animate={{ x2: renderedW[0], y2: renderedW[1] }}
          transition={TWEEN}
          stroke={INK}
          strokeWidth={2}
          markerEnd="url(#mt-ink-arrow)"
        />
        <motion.g
          initial={false}
          animate={{ x: renderedW[0] + 6, y: renderedW[1] - 4 }}
          transition={TWEEN}
        >
          <FadeText
            x={0}
            y={0}
            fontFamily={FONT_FAMILY}
            fontSize={11}
            fontWeight={700}
            fill={INK}
          >
            {wIsDashed ? "w" : "Mw (rotated)"}
          </FadeText>
        </motion.g>

        {/* eigenvectors — persistent, endpoints + labels animate */}
        {renderedEigs.map(({ end, lambda }, i) => {
          const [ex, ey] = end;
          const len = Math.hypot(ex - CX, ey - CY) || 1;
          const ox = ((ex - CX) / len) * 16;
          const oy = ((ey - CY) / len) * 16;
          return (
            <g key={`eig-${i}`}>
              <motion.line
                x1={CX}
                y1={CY}
                initial={false}
                animate={{ x2: ex, y2: ey }}
                transition={TWEEN}
                stroke={ACCENT}
                strokeWidth={4}
                markerEnd="url(#mt-yellow-arrow)"
              />
              <motion.text
                initial={false}
                animate={{ x: ex + ox, y: ey - oy + 4 }}
                transition={TWEEN}
                fontFamily={FONT_FAMILY}
                fontSize={11}
                fontWeight={700}
                fill={INK}
              >
                v{i + 1} · λ={lambda}
              </motion.text>
            </g>
          );
        })}
      </>
    );
  };

  return (
    <AlgoStepperShell<MatrixState>
      title="Matrix transformation"
      desc="Interactive demo of eigenvectors. Cartesian plane with test rays, yellow eigenvectors, and one non-eigenvector w. Scrub from t=0 (identity) to t=1 (full matrix). Yellow rays stay parallel; gray rays rotate."
      frames={FRAMES}
      renderFrame={renderFrame}
      predictionGate={predictionGate}
      voiceMap={VOICE_MAP}
      testIdPrefix="matrix-transform"
    />
  );
}
