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
    autoplayMsPerFrame = DEFAULT_AUTOPLAY_MS,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);

  const initialIdx = useMemo(() => {
    const fromHash = parseHashIdx(testIdPrefix);
    if (fromHash === null) return 0;
    return Math.max(0, Math.min(lastIdx, fromHash));
  }, [testIdPrefix, lastIdx]);

  const [idx, setIdx] = useState(initialIdx);
  const [playing, setPlaying] = useState(false);
  const [predictionLocked, setPredictionLocked] = useState(!!props.predictionGate);
  const [voiceOn, setVoiceOn] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const pendingNextSrcRef = useRef<string | null>(null);
  const voiceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reducedMotion = readReducedMotion();

  const stepBy = useCallback(
    (delta: number) => {
      if (predictionLocked) return;
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + delta)));
    },
    [lastIdx, predictionLocked]
  );

  const reset = useCallback(() => {
    setIdx(0);
    setPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (predictionLocked) return;
    if (reducedMotion) {
      stepBy(1);
      return;
    }
    setPlaying((p) => !p);
  }, [reducedMotion, stepBy, predictionLocked]);

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

  useEffect(() => {
    writeHashIdx(testIdPrefix, idx);
  }, [idx, testIdPrefix]);

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
      setPredictionLocked(false);
      props.predictionGate?.onAnswered?.(isCorrect);
    },
    [props.predictionGate]
  );

  const onShareClick = useCallback(() => {
    props.onShare?.(`${testIdPrefix}-idx-${idx}`);
  }, [props.onShare, testIdPrefix, idx]);

  const onKey = (e: ReactKeyboardEvent<SVGSVGElement>) => {
    if (predictionLocked) return;
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
            disabled={predictionLocked}
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
            disabled={predictionLocked}
            data-testid={`${testIdPrefix}-play`}
            style={brutalistBtn(playing, predictionLocked)}
          >
            {playing ? "⏸ pause" : "▶ play"}
          </button>
          <button
            onClick={() => stepBy(-1)}
            disabled={predictionLocked || idx === 0}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false, predictionLocked || idx === 0)}
          >
            ◀
          </button>
          <button
            onClick={() => stepBy(1)}
            disabled={predictionLocked || idx === lastIdx}
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false, predictionLocked || idx === lastIdx)}
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
          <button
            onClick={onShareClick}
            data-testid={`${testIdPrefix}-share`}
            style={brutalistBtn(false, false)}
          >
            🔗 share
          </button>
          <button
            onClick={() => setVoiceOn((v) => !v)}
            data-testid={`${testIdPrefix}-voice`}
            style={brutalistBtn(voiceOn, false)}
          >
            {voiceOn ? "🔊 voice on" : "🔇 voice off"}
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
        {props.predictionGate && predictionLocked && (
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
              ⚡ Predict
            </div>
            <div style={{ fontSize: 11, lineHeight: 1.5 }}>
              {props.predictionGate.question}
            </div>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 4,
                marginTop: 8,
              }}
            >
              {props.predictionGate.answers.map((a, i) => (
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
