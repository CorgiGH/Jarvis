interface ForecastDotPlotProps {
  tomorrow: number;
  thisWeek: number;
  thisMonth: number;
}

interface BarDef {
  key: "tomorrow" | "week" | "month";
  label: string;
  count: number;
}

/**
 * Hand-SVG dot-plot for FSRS review forecast.
 * Shows three bars (MÂINE / SĂPTĂMÂNA / LUNA) proportional to the
 * FsrsForecastReply values. Sits in the right panel of FsrsReview.
 */
export function ForecastDotPlot({ tomorrow, thisWeek, thisMonth }: ForecastDotPlotProps) {
  const max = Math.max(tomorrow, thisWeek, thisMonth, 1); // avoid /0

  const bars: BarDef[] = [
    { key: "tomorrow", label: "MÂINE", count: tomorrow },
    { key: "week",     label: "SĂPT.", count: thisWeek },
    { key: "month",    label: "LUNA",  count: thisMonth },
  ];

  const BAR_W = 100; // max SVG width for bars

  return (
    <div
      data-testid="fsrs-forecast-plot"
      className="flex flex-col gap-2 font-mono text-xs text-page-fg/70 w-full"
    >
      <p className="text-[10px] tracking-widest font-bold text-page-fg/50 uppercase">
        Previziune recenzii
      </p>
      {bars.map(({ key, label, count }) => {
        const pct = count / max;
        const barPx = Math.round(pct * BAR_W);

        return (
          <div key={key} className="flex items-center gap-2">
            <span className="w-14 text-[10px] tracking-widest text-page-fg/50 shrink-0">
              {label}
            </span>
            <svg
              width={BAR_W + 4}
              height={14}
              aria-hidden="true"
              style={{ display: "block" }}
            >
              {/* Track */}
              <rect x={0} y={3} width={BAR_W} height={8} rx={2}
                fill="var(--color-panel-bg, #222)" />
              {/* Fill */}
              {barPx > 0 && (
                <rect
                  data-testid={`forecast-bar-${key}`}
                  data-bar-pct={String(pct)}
                  x={0} y={3} width={barPx} height={8} rx={2}
                  fill="var(--color-accent, #e5c04a)"
                />
              )}
              {/* Invisible full-width sentinel when 0 */}
              {barPx === 0 && (
                <rect
                  data-testid={`forecast-bar-${key}`}
                  data-bar-pct="0"
                  x={0} y={3} width={0} height={8} rx={2}
                  fill="transparent"
                />
              )}
            </svg>
            <span className="text-[10px] tracking-widest text-page-fg">
              {count}
            </span>
          </div>
        );
      })}
    </div>
  );
}
