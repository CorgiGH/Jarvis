/** MasterySparkline — 28px band gauge showing a single EWMA score.
 *
 *  Render mode: BAND (not time-series). /mastery returns one scalar ewma_score
 *  per KC — no history array exists. Fills a 0..1 band; threshold tick at 0.8.
 *
 *  Bands:
 *    high  ≥ 0.8   (accent fill)
 *    mid   0.3..0.8
 *    low   < 0.3
 */

interface MasterySparklineProps {
  ewmaScore: number; // 0..1
}

type Band = "high" | "mid" | "low";

function band(score: number): Band {
  if (score >= 0.8) return "high";
  if (score >= 0.3) return "mid";
  return "low";
}

const FILL: Record<Band, string> = {
  high: "var(--color-accent, #e5c04a)",
  mid: "var(--color-panel-dark-fg, #a0a0a0)",
  low: "var(--color-border-strong, #555)",
};

const W = 80;
const H = 28;
const THRESHOLD = 0.8;
const TICK_X = Math.round(THRESHOLD * W);

export function MasterySparkline({ ewmaScore }: MasterySparklineProps) {
  const b = band(ewmaScore);
  const fillWidth = Math.round(Math.max(0, Math.min(1, ewmaScore)) * W);

  return (
    <svg
      data-testid="mastery-sparkline"
      data-mastery-value={String(ewmaScore)}
      data-mastery-band={b}
      width={W}
      height={H}
      aria-label={`Mastery ${Math.round(ewmaScore * 100)}%`}
      role="img"
      style={{ display: "block" }}
    >
      {/* Track */}
      <rect x={0} y={8} width={W} height={12} rx={2}
        fill="var(--color-panel-bg, #222)" />
      {/* Fill */}
      {fillWidth > 0 && (
        <rect x={0} y={8} width={fillWidth} height={12} rx={2}
          fill={FILL[b]} />
      )}
      {/* Threshold tick at 0.8 */}
      <line
        x1={TICK_X} y1={4} x2={TICK_X} y2={H - 4}
        stroke="var(--color-page-fg, #fff)"
        strokeWidth={1.5}
        strokeOpacity={0.5}
      />
    </svg>
  );
}
