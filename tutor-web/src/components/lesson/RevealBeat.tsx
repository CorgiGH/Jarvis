import { useState, useEffect, useCallback } from "react";
import type { ApiBeatReveal, ApiPredictOption } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";
import { readMs } from "../../lib/dwell";
import { prefersReducedMotionNow } from "../../theme/applyTheme";
import { FigureReveal } from "./FigureReveal";

interface RevealBeatProps {
  reveal: ApiBeatReveal;
  /** The learner's committed predict option (for the echo banner), or null. */
  predictedOption: ApiPredictOption | null;
  /** Fires once the reveal gate clears: stepped to final step AND its dwell floor met. */
  onGateClear: () => void;
}

/**
 * REVEAL beat — stepped reveal with back/forward + "pas k/N".
 * Plan-4b Task 4: when reveal.figure is set, FigureReveal IS the reveal (§0.9A).
 * The predict echo banner renders above BOTH paths (moved above the branch).
 * The stepped-text path is byte-identical to Plan-3 (its existing tests pass unchanged).
 */
export function RevealBeat({ reveal, predictedOption, onGateClear }: RevealBeatProps) {
  const steps = reveal.steps;
  const n = steps.length;
  const [idx, setIdx] = useState(0);
  const [reachedEnd, setReachedEnd] = useState(n === 1);
  const [dwellMet, setDwellMet] = useState(false);
  const reduced = prefersReducedMotionNow();

  // Per-step dwell floor: a wall of text can't be skipped in 200ms (spec §4.1). Reduced motion -> 0.
  useEffect(() => {
    setDwellMet(false);
    if (reduced) { setDwellMet(true); return; }
    const t = setTimeout(() => setDwellMet(true), readMs(steps[idx].text + " " + steps[idx].callout));
    return () => clearTimeout(t);
  }, [idx, reduced, steps]);

  useEffect(() => {
    if (idx === n - 1) setReachedEnd(true);
  }, [idx, n]);

  const gateClear = reachedEnd && dwellMet;
  useEffect(() => {
    if (gateClear) onGateClear();
  }, [gateClear, onGateClear]);

  const back = useCallback(() => setIdx((i) => Math.max(0, i - 1)), []);
  const fwd = useCallback(() => setIdx((i) => Math.min(n - 1, i + 1)), [n]);

  // --- Predict echo banner (§4.1): renders above BOTH figure and stepped-text paths ---
  const echoBanner = predictedOption && (
    <p data-testid="reveal-echo" className="border-l-4 border-accent pl-3 text-xs text-page-fg/70 leading-relaxed">
      <span className="font-bold tracking-widest uppercase text-[10px] text-accent block mb-1">
        {lessonStrings.echoPrefix}
      </span>
      {predictedOption.callback}
    </p>
  );

  // --- Figure path (Plan-4b Task 4): FigureReveal when reveal.figure is set ---
  // §0.9A: the figure IS the reveal; onGateClear fires when the final frame is reached.
  // The stepped-text path below is the fallback (boundary degrades to it on fetch/render failure).
  if (reveal.figure) {
    return (
      <div className="flex flex-col gap-3 font-mono">
        {echoBanner}
        <FigureReveal
          figure={reveal.figure}
          steps={steps}
          predictedOption={predictedOption}
          onGateClear={onGateClear}
        />
      </div>
    );
  }

  // --- Stepped-text path (byte-identical to Plan-3 behavior) ---
  const step = steps[idx];
  return (
    <div className="flex flex-col gap-3 font-mono">
      {echoBanner}

      <div className="border-2 border-border-strong p-4 flex flex-col gap-2 shadow-hard">
        <p className="text-sm text-page-fg leading-relaxed">{step.text}</p>
        <p className="border-l-4 border-accent pl-3 text-xs text-page-fg/80 leading-relaxed">{step.callout}</p>
      </div>

      <div data-testid="beat-figure-scrubber" className="flex items-center gap-3">
        <button
          data-step-back
          onClick={back}
          disabled={idx === 0}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          {lessonStrings.back}
        </button>
        <span data-testid="scrubber-step-counter" className="text-xs text-page-fg/60 tracking-wide">
          {lessonStrings.step} {idx + 1}/{n}
        </span>
        <button
          data-step-fwd
          onClick={fwd}
          disabled={idx === n - 1}
          className="border-2 border-page-fg px-3 py-1 text-xs tracking-wide text-page-fg disabled:opacity-30 hover:border-accent hover:text-accent"
        >
          {lessonStrings.next}
        </button>
      </div>
    </div>
  );
}
