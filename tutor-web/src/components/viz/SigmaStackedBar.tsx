import { ACCENT, FONT_FAMILY, INK, PAPER, STROKE_DEFAULT } from "./theme";

export interface SigmaStackedBarProps {
  data: number[];
  mu: number;
  /** Optional index of the focused/hovered segment — that segment alone may use ACCENT. */
  focusedIndex?: number;
}

// SVG layout constants
const BAR_HEIGHT = 28;
const BAR_WIDTH = 480;
const LABEL_ROW_Y = BAR_HEIGHT + 20;
const SUM_ROW_Y = BAR_HEIGHT + 40;
const SVG_HEIGHT = BAR_HEIGHT + 56;

const TITLE_ID = "sigma-stacked-bar-title";
const DESC_ID = "sigma-stacked-bar-desc";

export function SigmaStackedBar({ data, mu, focusedIndex }: SigmaStackedBarProps) {
  const deviations = data.map((x) => Math.abs(x - mu));
  const total = deviations.reduce((a, b) => a + b, 0);

  // Compute pixel-width of each segment inside the bar.
  // Use a 1px gap (PAPER-coloured gap simulated via stroke) to delineate adjacent segments
  // without relying on color or opacity.
  const segWidths = deviations.map((dev) =>
    total > 0 ? (dev / total) * BAR_WIDTH : 0
  );

  // Build cumulative x offsets for each rect.
  const xOffsets: number[] = [];
  let cursor = 0;
  for (const w of segWidths) {
    xOffsets.push(cursor);
    cursor += w;
  }

  return (
    <div
      data-testid="sigma-stacked-bar"
      style={{ fontFamily: FONT_FAMILY, color: INK }}
    >
      <svg
        viewBox={`0 0 ${BAR_WIDTH} ${SVG_HEIGHT}`}
        width="100%"
        role="img"
        aria-labelledby={`${TITLE_ID} ${DESC_ID}`}
        style={{ display: "block", overflow: "visible" }}
      >
        <title id={TITLE_ID}>Stacked absolute deviations — Σ|xᵢ − μ|</title>
        <desc id={DESC_ID}>
          {`Bar showing ${data.length} deviation terms: ${deviations
            .map((d, i) => `|x${i + 1}−μ|=${d.toFixed(2)}`)
            .join(", ")}. Total Σ = ${total.toFixed(2)}.`}
        </desc>

        {/* Background / border */}
        <rect
          x={0}
          y={0}
          width={BAR_WIDTH}
          height={BAR_HEIGHT}
          fill={PAPER}
          stroke={INK}
          strokeWidth={2}
        />

        {/* Segment rects — all INK fill; ACCENT reserved for focused segment only */}
        {deviations.map((dev, i) => {
          const isFocused = focusedIndex === i;
          const fill = isFocused ? ACCENT : INK;
          const segW = segWidths[i];
          const x = xOffsets[i];

          return (
            <rect
              key={i}
              data-testid={`sigma-seg-${i}`}
              data-value={dev}
              x={x}
              y={0}
              width={segW}
              height={BAR_HEIGHT}
              fill={fill}
              stroke={PAPER}
              strokeWidth={segW > 0 ? STROKE_DEFAULT : 0}
            >
              <title>{`|x${i + 1} − μ| = ${dev.toFixed(2)}`}</title>
            </rect>
          );
        })}

        {/* Label row: deviation terms as SVG text */}
        {deviations.map((dev, i) => (
          <text
            key={i}
            x={xOffsets[i] + segWidths[i] / 2}
            y={LABEL_ROW_Y}
            textAnchor="middle"
            fontSize={10}
            fontWeight={700}
            fontFamily={FONT_FAMILY}
            fill={INK}
          >
            {`|x${i + 1}−μ|=${dev.toFixed(1)}`}
          </text>
        ))}

        {/* Sum row */}
        <text
          x={BAR_WIDTH}
          y={SUM_ROW_Y}
          textAnchor="end"
          fontSize={12}
          fontWeight={900}
          fontFamily={FONT_FAMILY}
          fill={INK}
        >
          {`Σ = `}
          <tspan data-testid="sigma-sum" fill={INK}>
            {total.toFixed(1)}
          </tspan>
        </text>
      </svg>
    </div>
  );
}
