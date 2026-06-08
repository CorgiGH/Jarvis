import { useReducedMotion } from "../theme/ThemeProvider";

interface ProgressTier {
  done: number;
  total: number;
}

interface ProgressStripProps {
  outer: ProgressTier;
  inner: ProgressTier;
  currentProblemLabel: string;
}

/**
 * Two-tier progress strip.
 *
 * Outer tier: N/N problems completed. Dots use anim #4 (outer-dot-flash).
 * Inner tier: N/4 cards completed for current problem. Dots use anim #3
 *   (radial scale 0→1, 160ms ease-out). Both tiers have aria-valuenow
 *   for screen reader compatibility (Slice 2 full a11y pass).
 *
 * Animation classes `animate-dot-fill` and `animate-outer-dot-fill` must
 * be defined in tailwind.config (keyframe: scale 0→1, 160ms/200ms ease-out).
 * Under prefers-reduced-motion, no animation class is applied.
 */
export function ProgressStrip({
  outer,
  inner,
  currentProblemLabel,
}: ProgressStripProps) {
  const reduced = useReducedMotion();

  return (
    <div
      data-testid="progress-strip"
      className="flex flex-col gap-1.5 px-4 py-2 border-b-4 border-border-strong bg-page-bg font-mono text-xs"
    >
      {/* Outer tier — problems N/N */}
      <div
        data-testid="outer-progress"
        role="progressbar"
        aria-valuenow={outer.done}
        aria-valuemin={0}
        aria-valuemax={outer.total}
        aria-label={`${outer.done} of ${outer.total} problems complete`}
        className="flex items-center gap-2"
      >
        <div className="flex items-center gap-1">
          {Array.from({ length: outer.total }, (_, i) => {
            const filled = i < outer.done;
            return (
              <span
                key={i}
                data-testid="outer-dot"
                data-filled={filled ? "true" : "false"}
                aria-hidden="true"
                className={`inline-block w-3 h-3 border-2 border-border-strong transition-transform ${
                  filled
                    ? `bg-accent ${!reduced ? "animate-outer-dot-fill" : ""}`
                    : "bg-transparent"
                }`}
              />
            );
          })}
        </div>
        <span
          data-testid="outer-label"
          className="text-page-fg/70 tracking-widest"
        >
          {outer.done} / {outer.total} problems
        </span>
      </div>

      {/* Inner tier — cards N/4 for current problem */}
      <div
        data-testid="inner-progress"
        role="progressbar"
        aria-valuenow={inner.done}
        aria-valuemin={0}
        aria-valuemax={inner.total}
        aria-label={`${inner.done} of ${inner.total} cards complete (${currentProblemLabel})`}
        className="flex items-center gap-2"
      >
        <div className="flex items-center gap-1">
          {Array.from({ length: inner.total }, (_, i) => {
            const filled = i < inner.done;
            return (
              <span
                key={i}
                data-testid="inner-dot"
                data-filled={filled ? "true" : "false"}
                aria-hidden="true"
                className={`inline-block w-2 h-2 border-2 border-border-strong transition-transform ${
                  filled
                    ? `bg-accent ${!reduced ? "animate-dot-fill" : ""}`
                    : "bg-transparent"
                }`}
              />
            );
          })}
        </div>
        <span
          data-testid="inner-label"
          className="text-page-fg/70 tracking-widest"
        >
          {inner.done} / {inner.total} cards ({currentProblemLabel})
        </span>
      </div>
    </div>
  );
}
