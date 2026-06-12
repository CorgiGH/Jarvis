import {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { MotionConfig } from "motion/react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import { HatchDefs } from "./HatchDefs";

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

/** Chrome/geometry is INJECTABLE so a viz is not trapped in a fixed box.
 *  Transport (frames, scrubber, gates, a11y, voice) is unchanged; only the
 *  shell's size/border/controls-placement vary. Defaults reproduce the
 *  original boxed look exactly, so existing mounts are unaffected. */
export interface ShellLayout {
  /** drop max-width + border + padding; fill the parent (e.g. a full-screen door) */
  fullBleed?: boolean;
  /** max content width when not fullBleed (default 1100) */
  maxWidth?: number;
  /** where the scrubber/buttons sit relative to the canvas (default "side") */
  controls?: "side" | "bottom" | "none";
  /** canvas background (default white); set transparent to sit on a themed page */
  canvasBg?: string;
}

/** Plan-3 §8.2 — chrome labels, ADDITIVE. Omitted fields fall back to the current EN literals so
 *  the demo gallery is unchanged; the lesson surface passes RO via lessonStrings. */
export interface ShellLabels {
  frame?: string;   // "Frame"
  reset?: string;   // "reset"
  share?: string;   // "🔗 share"
  voiceOn?: string;  // "🔊 voice on"
  voiceOff?: string; // "🔇 voice off"
  predict?: string;  // "⚡ Predict"
}

export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGates?: Map<number, PredictionGate>;
  voiceMap?: Record<number, string>;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
  /** Plan-3 ADDITIVE — chrome labels; omitted → current EN defaults (demos unchanged). */
  labels?: ShellLabels;
  initialStep?: number;
  /** chrome/geometry overrides; omitted → original boxed look */
  layout?: ShellLayout;
  /** Called on every frame change, including the initial mount — so the parent
   *  learns the actual (gate-clamped) starting frame. Wrap in useCallback to
   *  avoid spurious re-fires when the parent re-renders. */
  onStep?: (idx: number) => void;
}

function parseHashIdx(prefix: string): number | null {
  if (typeof window === "undefined") return null;
  const m = window.location.hash.match(
    new RegExp(`#${prefix}-idx-(\\d+)`)
  );
  if (!m) return null;
  return Number(m[1]);
}

function writeHashIdx(prefix: string, idx: number): void {
  if (typeof window === "undefined") return;
  window.location.hash = `${prefix}-idx-${idx}`;
}

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;
  const L = {
    frame: props.labels?.frame ?? "Frame",
    reset: props.labels?.reset ?? "reset",
    share: props.labels?.share ?? "🔗 share",
    voiceOn: props.labels?.voiceOn ?? "🔊 voice on",
    voiceOff: props.labels?.voiceOff ?? "🔇 voice off",
    predict: props.labels?.predict ?? "⚡ Predict",
  };

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);

  const initialIdx = useMemo(() => {
    const fromHash = parseHashIdx(testIdPrefix);
    const seed = fromHash ?? props.initialStep ?? 0;
    // On first mount no gates are answered yet — clamp the seed frame to the
    // lowest prediction-gate frame so a stale hash OR an initialStep cannot
    // deep-link past an unanswered prediction gate.
    const gateFrames = props.predictionGates
      ? [...props.predictionGates.keys()].sort((a, b) => a - b)
      : [];
    const ceiling = Math.min(gateFrames[0] ?? lastIdx, lastIdx);
    return Math.max(0, Math.min(ceiling, seed));
  }, [testIdPrefix, lastIdx, props.initialStep, props.predictionGates]);

  const [idx, setIdx] = useState(initialIdx);
  const [answeredGates, setAnsweredGates] = useState<Set<number>>(new Set());
  const [voiceOn, setVoiceOn] = useState(false);

  const maxReachable = useMemo(() => {
    if (!props.predictionGates) return lastIdx;
    const gateFrames = [...props.predictionGates.keys()].sort((a, b) => a - b);
    for (const g of gateFrames) {
      if (!answeredGates.has(g)) return Math.min(g, lastIdx);
    }
    return lastIdx;
  }, [props.predictionGates, answeredGates, lastIdx]);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const pendingNextSrcRef = useRef<string | null>(null);
  const voiceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const gateLocked = !!props.predictionGates?.has(idx) && !answeredGates.has(idx);
  const activeGate = props.predictionGates?.get(idx);

  const stepBy = useCallback(
    (delta: number) => {
      setIdx((prev) => Math.max(0, Math.min(maxReachable, prev + delta)));
    },
    [maxReachable]
  );

  const reset = useCallback(() => {
    setIdx(0);
  }, []);

  useEffect(() => {
    writeHashIdx(testIdPrefix, idx);
    props.onStep?.(idx);
  }, [idx, testIdPrefix, props.onStep]);

  const playSrc = useCallback((src: string) => {
    if (!src) return;
    let a = audioRef.current;
    if (!a) {
      a = new Audio();
      a.onended = () => {
        if (pendingNextSrcRef.current) {
          const next = pendingNextSrcRef.current;
          pendingNextSrcRef.current = null;
          a!.src = next;
          a!.play().catch(() => {});
        }
      };
      audioRef.current = a;
    }
    if (a.paused) {
      a.src = src;
      a.play().catch(() => {});
    } else {
      pendingNextSrcRef.current = src;
    }
  }, []);

  useEffect(() => {
    if (!voiceOn) {
      if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
      return;
    }
    const src = props.voiceMap?.[idx];
    if (!src) return;
    if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
    voiceTimerRef.current = setTimeout(() => playSrc(src), 200);
    return () => {
      if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
    };
  }, [voiceOn, idx, props.voiceMap, playSrc]);

  useEffect(() => {
    if (!voiceOn && audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
      pendingNextSrcRef.current = null;
    }
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
    };
  }, [voiceOn]);

  const onPredict = useCallback(
    (isCorrect: boolean) => {
      setAnsweredGates((prev) => new Set(prev).add(idx));
      activeGate?.onAnswered?.(isCorrect);
    },
    [idx, activeGate]
  );

  const onShareClick = useCallback(() => {
    props.onShare?.(`${testIdPrefix}-idx-${idx}`);
  }, [props.onShare, testIdPrefix, idx]);

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
      stepBy(1);
    } else if (k === "r") {
      e.preventDefault();
      reset();
    } else if (e.key === "Home") {
      e.preventDefault();
      setIdx(0);
    } else if (e.key === "End") {
      e.preventDefault();
      setIdx(maxReachable);
    }
  };

  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = `${testIdPrefix}-title`;
  const descId = `${testIdPrefix}-desc`;

  // --- injectable chrome/geometry (defaults = original boxed look) ---
  const { fullBleed = false, maxWidth = 1100, controls = "side", canvasBg = "#fff" } =
    props.layout ?? {};
  const sideControls = controls === "side";
  const wrapperStyle: CSSProperties = {
    background: fullBleed ? "transparent" : PAPER,
    border: fullBleed ? "none" : `2px solid ${INK}`,
    padding: fullBleed ? 0 : 20,
    display: "grid",
    gridTemplateColumns: sideControls ? "1fr 260px" : "1fr",
    gap: sideControls ? 24 : 12,
    fontFamily: FONT_FAMILY,
    color: INK,
    maxWidth: fullBleed ? "none" : maxWidth,
    width: "100%",
    height: fullBleed ? "100%" : undefined,
  };

  return (
    <Fragment>
      <style>{`
        @media (max-width: 720px) {
          .algo-stepper-shell-wrapper {
            grid-template-columns: 1fr !important;
          }
        }
        @keyframes algoStepperFadeIn {
          from { opacity: 0; }
          to { opacity: var(--algo-stepper-target-opacity, 1); }
        }
        .algo-stepper-shell-svg * {
          animation: algoStepperFadeIn 350ms ease-out;
        }
        @media (prefers-reduced-motion: reduce) {
          .algo-stepper-shell-svg * {
            animation: none;
          }
        }
      `}</style>
      <div
        role="group"
        aria-labelledby={titleId}
        data-testid={`${testIdPrefix}-root`}
        className="algo-stepper-shell-wrapper"
        style={wrapperStyle}
      >
        <MotionConfig reducedMotion="user">
          <svg
            viewBox="0 0 480 360"
            preserveAspectRatio="xMidYMid meet"
            role="img"
            aria-labelledby={`${titleId} ${descId}`}
            tabIndex={0}
            onKeyDown={onKey}
            className="algo-stepper-shell-svg"
            style={{
              background: canvasBg,
              outline: `2px solid transparent`,
              outlineOffset: 2,
              width: "100%",
              height: fullBleed ? "100%" : "auto",
              minHeight: 0,
              border: fullBleed ? "none" : `1px solid ${INK}`,
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
            <HatchDefs />
            {currentFrame && renderFrame(currentFrame, idx)}
          </svg>
        </MotionConfig>
      {controls !== "none" && (
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14, position: "relative" }}
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
            {L.frame}
          </div>
          <div
            data-testid={`${testIdPrefix}-frame-counter`}
            style={{ fontSize: 18, fontWeight: 700 }}
          >
            {idx + 1} / {materializedFrames.length}
          </div>
          <input
            type="range"
            min={0}
            max={maxReachable}
            step={1}
            value={idx}
            disabled={gateLocked}
            onChange={(e) =>
              setIdx(
                Math.max(0, Math.min(maxReachable, Number(e.target.value)))
              )
            }
            aria-label="Frame scrubber"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
          <button
            onClick={() => stepBy(-1)}
            disabled={idx === 0}
            data-testid={`${testIdPrefix}-step-back`}
            aria-label="Step back one frame"
            style={brutalistBtn(false, idx === 0)}
          >
            ◀
          </button>
          <button
            onClick={() => stepBy(1)}
            disabled={idx >= maxReachable}
            data-testid={`${testIdPrefix}-step-fwd`}
            aria-label="Step forward one frame"
            style={brutalistBtn(false, idx >= maxReachable)}
          >
            ▶
          </button>
          <button
            onClick={reset}
            data-testid={`${testIdPrefix}-reset`}
            aria-label="Reset to first frame"
            style={brutalistBtn(false, false)}
          >
            {L.reset}
          </button>
          <button
            onClick={onShareClick}
            data-testid={`${testIdPrefix}-share`}
            aria-label="Copy share link to current frame"
            style={brutalistBtn(false, false)}
          >
            {L.share}
          </button>
          <button
            onClick={() => setVoiceOn((v) => !v)}
            data-testid={`${testIdPrefix}-voice`}
            style={brutalistBtn(voiceOn, false)}
          >
            {voiceOn ? L.voiceOn : L.voiceOff}
          </button>
        </div>
        {gateLocked && activeGate && (
          <div
            data-testid="predict-gate"
            style={{ border: `2px solid ${INK}`, padding: 10, background: PAPER }}
          >
            <div
              style={{
                fontSize: 11,
                letterSpacing: "0.08em",
                fontWeight: 700,
                textTransform: "uppercase",
                marginBottom: 6,
              }}
            >
              {L.predict}
            </div>
            <div style={{ fontSize: 11, lineHeight: 1.5 }}>
              {activeGate.question}
            </div>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 4,
                marginTop: 8,
              }}
            >
              {activeGate.answers.map((a, i) => (
                <button
                  key={i}
                  onClick={() => onPredict(a.isCorrect)}
                  style={{
                    background: PAPER,
                    color: INK,
                    border: `1px solid ${INK}`,
                    padding: "4px 8px",
                    fontFamily: FONT_FAMILY,
                    fontSize: 10,
                    letterSpacing: "0.06em",
                    textTransform: "uppercase",
                    cursor: "pointer",
                  }}
                >
                  {a.label}
                </button>
              ))}
            </div>
          </div>
        )}
        <div
          aria-live="polite"
          role="status"
          data-testid={`${testIdPrefix}-live`}
          style={{ position: "absolute", left: -9999 }}
        >
          {currentFrame?.aria ?? ""}
        </div>
      </div>
      )}
      </div>
    </Fragment>
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
