import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
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

const DEFAULT_AUTOPLAY_MS = 800;

function readReducedMotion(): boolean {
  if (typeof window === "undefined") return false;
  if (typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    autoplayMsPerFrame = DEFAULT_AUTOPLAY_MS,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);
  const [idx, setIdx] = useState(0);
  const [playing, setPlaying] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reducedMotion = readReducedMotion();

  const stepBy = useCallback(
    (delta: number) => {
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + delta)));
    },
    [lastIdx]
  );

  const reset = useCallback(() => {
    setIdx(0);
    setPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (reducedMotion) {
      stepBy(1);
      return;
    }
    setPlaying((p) => !p);
  }, [reducedMotion, stepBy]);

  useEffect(() => {
    if (!playing || reducedMotion) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }
    intervalRef.current = setInterval(() => {
      setIdx((prev) => {
        const next = prev + 1;
        if (next > lastIdx) {
          setPlaying(false);
          return lastIdx;
        }
        return next;
      });
    }, autoplayMsPerFrame);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [playing, reducedMotion, autoplayMsPerFrame, lastIdx]);

  const onKey = (e: ReactKeyboardEvent<SVGSVGElement>) => {
    const k = e.key.toLowerCase();
    if (e.key === "ArrowRight" || k === "k") {
      e.preventDefault();
      stepBy(1);
    } else if (e.key === "ArrowLeft" || k === "j") {
      e.preventDefault();
      stepBy(-1);
    } else if (e.key === " ") {
      e.preventDefault();
      togglePlay();
    } else if (k === "r") {
      e.preventDefault();
      reset();
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
        onKeyDown={onKey}
        style={{
          background: "#fff",
          outline: `2px solid transparent`,
          outlineOffset: 2,
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
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
              setIdx(
                Math.max(0, Math.min(lastIdx, Number(e.target.value)))
              )
            }
            aria-label="Frame scrubber"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
          <button
            onClick={togglePlay}
            data-testid={`${testIdPrefix}-play`}
            style={brutalistBtn(playing, false)}
          >
            {playing ? "⏸ pause" : "▶ play"}
          </button>
          <button
            onClick={() => stepBy(-1)}
            disabled={idx === 0}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false, idx === 0)}
          >
            ◀
          </button>
          <button
            onClick={() => stepBy(1)}
            disabled={idx === lastIdx}
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false, idx === lastIdx)}
          >
            ▶
          </button>
          <button
            onClick={reset}
            data-testid={`${testIdPrefix}-reset`}
            style={brutalistBtn(false, false)}
          >
            reset
          </button>
        </div>
        {reducedMotion && (
          <div
            data-testid={`${testIdPrefix}-reduced-motion-hint`}
            style={{ fontSize: 10, opacity: 0.6, lineHeight: 1.4 }}
          >
            Reduced-motion on · play steps once per click.
          </div>
        )}
      </div>
    </div>
  );
}

function brutalistBtn(active: boolean, disabled: boolean): CSSProperties {
  return {
    background: disabled ? PAPER : active ? INK : PAPER,
    color: disabled ? INK : active ? ACCENT : INK,
    border: `2px solid ${INK}`,
    padding: "6px 10px",
    fontFamily: FONT_FAMILY,
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.4 : 1,
  };
}
