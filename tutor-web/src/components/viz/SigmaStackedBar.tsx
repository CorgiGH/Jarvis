export interface SigmaStackedBarProps { data: number[]; mu: number; }

const SEGMENT_COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#ef4444", "#8b5cf6", "#06b6d4", "#f97316"];

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
        gap: "0.4rem",
        fontFamily: "var(--font-mono, monospace)",
      }}
    >
      <div
        style={{
          display: "flex",
          height: "28px",
          width: "100%",
          borderRadius: "4px",
          overflow: "hidden",
          background: "var(--color-panel-bg, #f1f5f9)",
        }}
      >
        {deviations.map((dev, i) => {
          const pct = total > 0 ? (dev / total) * 100 : 0;
          return (
            <div
              key={i}
              data-testid={`sigma-seg-${i}`}
              data-value={dev}
              style={{
                width: `${pct}%`,
                background: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
                transition: prefersReduced ? "none" : "width 120ms ease-out",
                height: "100%",
                minWidth: dev > 0 ? "2px" : "0px",
              }}
              title={`|x${i + 1} − μ| = ${dev.toFixed(2)}`}
            />
          );
        })}
      </div>

      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
        {deviations.map((dev, i) => (
          <span
            key={i}
            style={{
              fontSize: "0.7rem",
              color: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
              fontWeight: 600,
            }}
          >
            |x<sub>{i + 1}</sub>&minus;&mu;|={dev.toFixed(1)}
          </span>
        ))}
      </div>

      <div style={{ fontSize: "0.8rem", fontWeight: 700, textAlign: "right" }}>
        &Sigma; ={" "}
        <span
          data-testid="sigma-sum"
          style={{ color: "var(--color-accent, #3b82f6)" }}
        >
          {total.toFixed(1)}
        </span>
      </div>
    </div>
  );
}
