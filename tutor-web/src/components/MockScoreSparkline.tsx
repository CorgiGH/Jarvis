import { MasterySparkline } from "./MasterySparkline";

interface MockScoreSparklineProps {
  /** Current exam score, 0..1. Passed to MasterySparkline as ewmaScore. */
  score: number;
  /** Previous session score, 0..1, or null if no prior data. Used to compute delta. */
  previousScore: number | null;
}

/** Delta-mode wrapper around MasterySparkline for exam grade reports. */
export function MockScoreSparkline({ score, previousScore }: MockScoreSparklineProps) {
  const delta =
    previousScore !== null
      ? Math.round((score - previousScore) * 100)
      : null;

  return (
    <div
      data-testid="mock-score-sparkline"
      className="flex items-center gap-3 font-mono"
    >
      <MasterySparkline ewmaScore={score} />
      {delta !== null && (
        <span
          className={`text-xs font-bold tracking-widest ${
            delta >= 0 ? "text-accent" : "text-danger-text"
          }`}
        >
          {delta >= 0 ? `+${delta}%` : `${delta}%`}
        </span>
      )}
    </div>
  );
}
