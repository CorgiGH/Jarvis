/**
 * CalibrationPlot — hand-SVG confidence calibration scatter (Surface 6).
 * Single-col 4-bucket chart: student_confidence (X) vs accuracy (Y).
 * Expected-calibration diagonal (perfect calibration reference line) is rendered.
 *
 * Wire shape: GET /api/v1/calibration → { buckets: CalibrationBucket[], total_attempts: Int }
 * (QueueMasteryCalibrationRoutes.kt:102 — student_confidence, attempts, correct, accuracy).
 */
export interface CalibrationBucket {
  student_confidence: "DEFINITELY" | "MAYBE" | "GUESS" | "IDK";
  attempts: number;
  correct: number;
  accuracy: number;
}

interface CalibrationPlotProps {
  buckets: CalibrationBucket[];
  /** Expected accuracy per confidence label (the "perfect calibration" reference). */
  expectedLine: Record<string, number>;
}

const CONF_ORDER: CalibrationBucket["student_confidence"][] = [
  "IDK", "GUESS", "MAYBE", "DEFINITELY",
];

const CONF_LABEL: Record<string, string> = {
  DEFINITELY: "Sigur",
  MAYBE: "Poate",
  GUESS: "Ghicit",
  IDK: "N/A",
};

const W = 200;
const H = 160;
const PADDING = { top: 16, right: 16, bottom: 32, left: 40 };

const plotW = W - PADDING.left - PADDING.right;
const plotH = H - PADDING.top - PADDING.bottom;

function xFor(confIndex: number, total: number): number {
  // Evenly spaced columns
  if (total <= 1) return plotW / 2;
  return (confIndex / (total - 1)) * plotW;
}

function yFor(accuracy: number): number {
  return plotH - accuracy * plotH;
}

export function CalibrationPlot({ buckets, expectedLine }: CalibrationPlotProps) {
  const orderedConfs = CONF_ORDER.filter((c) =>
    buckets.some((b) => b.student_confidence === c)
  );

  const allConfs = CONF_ORDER; // always show 4 columns (even if empty)
  const total = allConfs.length;

  // Build lookup
  const byConf = Object.fromEntries(buckets.map((b) => [b.student_confidence, b]));

  return (
    <div data-testid="calibration-plot" className="flex flex-col gap-1">
      <p className="font-mono text-[10px] tracking-widest text-page-fg/50 uppercase">
        Calibrare predicții
      </p>
      <svg
        width={W}
        height={H}
        style={{ display: "block", overflow: "visible" }}
        aria-label="Grafic calibrare predicții"
        role="img"
      >
        <g transform={`translate(${PADDING.left},${PADDING.top})`}>
          {/* Y-axis ticks */}
          {[0, 0.25, 0.5, 0.75, 1].map((v) => (
            <g key={v}>
              <line
                x1={-4} y1={yFor(v)} x2={plotW} y2={yFor(v)}
                stroke="var(--color-border-thin, #333)"
                strokeWidth={1}
                strokeDasharray={v === 0 || v === 1 ? undefined : "2 4"}
              />
              <text
                x={-6} y={yFor(v) + 4}
                textAnchor="end"
                fontSize={8}
                fill="var(--color-page-fg-muted, #666)"
                fontFamily="monospace"
              >
                {Math.round(v * 100)}%
              </text>
            </g>
          ))}

          {/* Perfect-calibration diagonal */}
          <line
            data-testid="calibration-diagonal"
            x1={xFor(0, total)} y1={yFor(0)}
            x2={xFor(total - 1, total)} y2={yFor(1)}
            stroke="var(--color-page-fg, #fff)"
            strokeWidth={1}
            strokeOpacity={0.2}
            strokeDasharray="3 3"
          />

          {/* Expected line dots */}
          {allConfs.map((conf, i) => {
            const exp = expectedLine[conf];
            if (exp == null) return null;
            return (
              <circle
                key={`exp-${conf}`}
                cx={xFor(i, total)}
                cy={yFor(exp)}
                r={3}
                fill="none"
                stroke="var(--color-page-fg, #fff)"
                strokeWidth={1}
                strokeOpacity={0.3}
              />
            );
          })}

          {/* Actual accuracy dots */}
          {allConfs.map((conf, i) => {
            const bucket = byConf[conf];
            if (!bucket) return null;
            const cx = xFor(i, total);
            const cy = yFor(bucket.accuracy);

            return (
              <g key={conf}>
                <circle
                  data-testid={`calibration-dot-${conf}`}
                  data-accuracy={String(bucket.accuracy)}
                  cx={cx}
                  cy={cy}
                  r={Math.max(4, Math.min(10, Math.sqrt(bucket.attempts) * 2))}
                  fill="var(--color-accent, #e5c04a)"
                  fillOpacity={0.85}
                />
              </g>
            );
          })}

          {/* X-axis labels */}
          {allConfs.map((conf, i) => (
            <text
              key={`lbl-${conf}`}
              x={xFor(i, total)}
              y={plotH + 14}
              textAnchor="middle"
              fontSize={8}
              fill="var(--color-page-fg-muted, #666)"
              fontFamily="monospace"
            >
              {CONF_LABEL[conf] ?? conf}
            </text>
          ))}

          {/* Hidden ordered conf list for test assertions */}
          {orderedConfs.length === 0 && (
            <text data-testid="calibration-empty" fontSize={8} x={plotW / 2} y={plotH / 2}
              textAnchor="middle" fill="var(--color-page-fg-muted, #666)" fontFamily="monospace">
              —
            </text>
          )}
        </g>
      </svg>
    </div>
  );
}
