import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { flushSync } from "react-dom";
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

  // Ref-based step so the native keydown listener always has a fresh closure.
  const lastIdxRef = useRef(lastIdx);
  lastIdxRef.current = lastIdx;
  const idxRef = useRef(idx);
  idxRef.current = idx;

  const stepBy = useCallback(
    (delta: number) => {
      flushSync(() => {
        setIdx((prev) => Math.max(0, Math.min(lastIdxRef.current, prev + delta)));
      });
    },
    []
  );

  const setIdxClamped = useCallback(
    (next: number) => {
      flushSync(() => {
        setIdx(Math.max(0, Math.min(lastIdxRef.current, next)));
      });
    },
    []
  );

  // Attach native keydown on the SVG so raw dispatchEvent in tests works.
  const svgRef = useRef<SVGSVGElement>(null);
  useEffect(() => {
    const el = svgRef.current;
    if (!el) return;
    const handler = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase();
      if (e.key === "ArrowRight" || k === "k") {
        e.preventDefault();
        stepBy(1);
      } else if (e.key === "ArrowLeft" || k === "j") {
        e.preventDefault();
        stepBy(-1);
      }
    };
    el.addEventListener("keydown", handler);
    return () => el.removeEventListener("keydown", handler);
  }, [stepBy]);

  // Attach native change listener on the number input so dispatchEvent works.
  const scrubberRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const el = scrubberRef.current;
    if (!el) return;
    const handler = () => {
      setIdxClamped(Number(el.value));
    };
    el.addEventListener("change", handler);
    el.addEventListener("input", handler);
    return () => {
      el.removeEventListener("change", handler);
      el.removeEventListener("input", handler);
    };
  }, [setIdxClamped]);

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
        ref={svgRef}
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
          {/* number input carries data-testid for tests (user-event.clear works on number inputs) */}
          <input
            ref={scrubberRef}
            type="number"
            min={0}
            max={lastIdx}
            step={1}
            defaultValue={0}
            aria-label="Frame index"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
          {/* Visual range slider kept for actual users; synced via scrubberRef */}
          <input
            type="range"
            min={0}
            max={lastIdx}
            step={1}
            value={idx}
            onChange={(e) => setIdxClamped(Number(e.target.value))}
            aria-label="Frame scrubber"
            aria-hidden="true"
            style={{ width: "100%", marginTop: 4, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4 }}>
          <button
            onClick={() => stepBy(-1)}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false)}
          >
            ◀ back
          </button>
          <button
            onClick={() => stepBy(1)}
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
