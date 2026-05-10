export interface SlopeCounterProps { data: number[]; mu: number; }

export function SlopeCounter({ data, mu }: SlopeCounterProps) {
  const left = data.filter((x) => x < mu).length;
  const right = data.filter((x) => x > mu).length;
  const diff = left - right;

  return (
    <div data-testid="slope-counter" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "0.4rem", fontFamily: "var(--font-mono, monospace)" }}>
      <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
        <span data-testid="slope-left-chip" style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem", padding: "0.2em 0.6em", borderRadius: "9999px", background: "var(--color-left, #3b82f6)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
          LEFT: {left}
        </span>
        <span data-testid="slope-right-chip" style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem", padding: "0.2em 0.6em", borderRadius: "9999px", background: "var(--color-right, #f59e0b)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
          RIGHT: {right}
        </span>
      </div>
      <div style={{ fontSize: "0.75rem", opacity: 0.65, marginTop: "0.1rem" }}>
        slope = <span data-testid="slope-diff" style={{ fontWeight: 700, color: diff > 0 ? "var(--color-left, #3b82f6)" : diff < 0 ? "var(--color-right, #f59e0b)" : "currentColor" }}>{diff >= 0 ? `+${diff}` : `${diff}`}</span> ({left} − {right})
      </div>
    </div>
  );
}
