import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface SigmaStackedBarProps { data: number[]; mu: number; }

const segmentFor = (i: number): { fill: string; opacity: number } => {
  const cycle = i % 4;
  if (cycle === 0) return { fill: INK, opacity: 1 };
  if (cycle === 1) return { fill: ACCENT, opacity: 1 };
  if (cycle === 2) return { fill: INK, opacity: 0.55 };
  return { fill: ACCENT, opacity: 0.55 };
};

export function SigmaStackedBar({ data, mu }: SigmaStackedBarProps) {
  const prefersReduced =
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const deviations = data.map((x) => Math.abs(x - mu));
  const total = deviations.reduce((a, b) => a + b, 0);

  return (
    <div
      data-testid="sigma-stacked-bar"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <div
        style={{
          display: "flex",
          height: "28px",
          width: "100%",
          borderRadius: 0,
          overflow: "hidden",
          background: PAPER,
          border: `2px solid ${INK}`,
        }}
      >
        {deviations.map((dev, i) => {
          const pct = total > 0 ? (dev / total) * 100 : 0;
          const { fill, opacity } = segmentFor(i);
          return (
            <div
              key={i}
              data-testid={`sigma-seg-${i}`}
              data-value={dev}
              style={{
                width: `${pct}%`,
                background: fill,
                opacity,
                transition: prefersReduced ? "none" : "width 120ms ease-out",
                height: "100%",
                minWidth: dev > 0 ? "2px" : "0px",
                borderRight: i === deviations.length - 1 ? "none" : `1px solid ${INK}`,
              }}
              title={`|x${i + 1} − μ| = ${dev.toFixed(2)}`}
            />
          );
        })}
      </div>

      <div style={{ display: "flex", gap: "0.6rem", flexWrap: "wrap", fontSize: "0.7rem", fontWeight: 700 }}>
        {deviations.map((dev, i) => (
          <span
            key={i}
            style={{ color: INK, opacity: 0.8 }}
          >
            |x<sub>{i + 1}</sub>−μ|={dev.toFixed(1)}
          </span>
        ))}
      </div>

      <div style={{ fontSize: "0.8rem", fontWeight: 900, textAlign: "right" }}>
        Σ = <span data-testid="sigma-sum" style={{ color: INK, background: ACCENT, padding: "0 0.3em" }}>{total.toFixed(1)}</span>
      </div>
    </div>
  );
}
