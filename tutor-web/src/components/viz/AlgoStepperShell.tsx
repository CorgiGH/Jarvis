import {
  useMemo,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export type Frame<S> = {
  state: S;
  aria: string;
  meta?: Record<string, unknown>;
};

export interface PredictionGate {
  question: string;
  answers: { label: string; isCorrect: boolean }[];
  onAnswered?: (correct: boolean) => void;
}

export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGate?: PredictionGate;
  voiceMap?: Record<number, string>;
  autoplayMsPerFrame?: number;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
}

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);
  const [idx, setIdx] = useState(0);

  const onKey = (e: React.KeyboardEvent<SVGSVGElement>) => {
    const k = e.key.toLowerCase();
    if (e.key === "ArrowRight" || k === "k") {
      e.preventDefault();
      setIdx((prev) => Math.min(lastIdx, prev + 1));
    } else if (e.key === "ArrowLeft" || k === "j") {
      e.preventDefault();
      setIdx((prev) => Math.max(0, prev - 1));
    }
  };

  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = `${testIdPrefix}-title`;
  const descId = `${testIdPrefix}-desc`;

  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-testid={`${testIdPrefix}-root`}
      style={{
        background: PAPER,
        border: `2px solid ${INK}`,
        padding: 20,
        display: "grid",
        gridTemplateColumns: "1fr 260px",
        gap: 24,
        fontFamily: FONT_FAMILY,
        color: INK,
        maxWidth: 1100,
      }}
    >
      <svg
        viewBox="0 0 480 360"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-labelledby={`${titleId} ${descId}`}
        tabIndex={0}
        style={{
          background: "#fff",
          outline: `2px solid transparent`,
          outlineOffset: 2,
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
        onKeyDown={onKey}
        onFocus={(e) => {
          e.currentTarget.style.outline = `2px solid ${ACCENT}`;
        }}
        onBlur={(e) => {
          e.currentTarget.style.outline = `2px solid transparent`;
        }}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{desc}</desc>
        {currentFrame && renderFrame(currentFrame, idx)}
      </svg>
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14 }}
      >
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.08em",
              fontWeight: 700,
              textTransform: "uppercase",
              marginBottom: 4,
            }}
          >
            Frame
          </div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>
            {idx + 1} / {materializedFrames.length}
          </div>
          <input
            type="range"
            min={0}
            max={lastIdx}
            step={1}
            value={idx}
            onChange={(e) =>
              setIdx(Math.max(0, Math.min(lastIdx, Number(e.target.value))))
            }
            aria-label="Frame scrubber"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4 }}>
          <button
            onClick={() =>
              setIdx((prev) => Math.max(0, prev - 1))
            }
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false)}
          >
            ◀ back
          </button>
          <button
            onClick={() =>
              setIdx((prev) => Math.min(lastIdx, prev + 1))
            }
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false)}
          >
            fwd ▶
          </button>
        </div>
      </div>
    </div>
  );
}

function brutalistBtn(active: boolean): CSSProperties {
  return {
    background: active ? INK : PAPER,
    color: active ? ACCENT : INK,
    border: `2px solid ${INK}`,
    padding: "6px 10px",
    fontFamily: FONT_FAMILY,
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    cursor: "pointer",
  };
}
