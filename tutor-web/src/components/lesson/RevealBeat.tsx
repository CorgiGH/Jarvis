import { useState, useEffect, useCallback } from "react";
import type { ApiBeatReveal, ApiPredictOption } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";
import { readMs } from "../../lib/dwell";
import { prefersReducedMotionNow } from "../../theme/applyTheme";

interface RevealBeatProps {
  reveal: ApiBeatReveal;
  /** The learner's committed predict option (for the echo banner), or null. */
  predictedOption: ApiPredictOption | null;
  /** Fires once the reveal gate clears: stepped to final step AND its dwell floor met. */
  onGateClear: () => void;
}

/**
 * REVEAL beat — stepped reveal with back/forward + "pas k/N". Figure path (Task 8) when reveal.figure
 * is set; the 4 authored KCs carry NO figure, so the stepped-TEXT path is what ships + is tested here.
 * Gate clears when the learner reached the FINAL step at least once AND that step's dwell floor elapsed.
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

  // Figure path: Task 8 fills FigureReveal. Until then the authored KCs never bind a figure, so this
  // branch is unreachable for served content; the seam is typed, not a 404 stub.
  if (reveal.figure) {
    return (
      <div data-testid="beat-figure-scrubber" className="font-mono text-xs text-page-fg/60">
        {/* Task 8: <FigureReveal figure={reveal.figure} onGateClear={onGateClear} /> */}
        <span data-testid="scrubber-step-counter">{lessonStrings.step} 1/1</span>
      </div>
    );
  }

  const step = steps[idx];
  return (
    <div className="flex flex-col gap-3 font-mono">
      {predictedOption && (
        <p data-testid="reveal-echo" className="border-l-4 border-accent pl-3 text-xs text-page-fg/70 leading-relaxed">
          <span className="font-bold tracking-widest uppercase text-[10px] text-accent block mb-1">
            {lessonStrings.echoPrefix}
          </span>
          {predictedOption.callback}
        </p>
      )}

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
