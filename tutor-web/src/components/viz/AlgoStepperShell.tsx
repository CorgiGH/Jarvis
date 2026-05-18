import { useMemo, useState, type ReactNode } from "react";
import { FONT_FAMILY, INK, PAPER } from "./theme";

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

const titleIdFor = (prefix: string) => `${prefix}-title`;
const descIdFor = (prefix: string) => `${prefix}-desc`;

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") {
      return Array.from(frames());
    }
    return frames;
  }, [frames]);

  const [idx] = useState(0);
  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = titleIdFor(testIdPrefix);
  const descId = descIdFor(testIdPrefix);

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
          outline: "none",
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
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
        {/* controls land in next task */}
      </div>
    </div>
  );
}
