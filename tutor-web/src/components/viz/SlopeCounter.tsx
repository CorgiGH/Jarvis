import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface SlopeCounterProps { data: number[]; mu: number; }

const chipBase: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: "0.25rem",
  padding: "0.25em 0.6em",
  border: `2px solid ${INK}`,
  borderRadius: 0,
  fontSize: "0.8rem",
  fontWeight: 700,
  fontFamily: FONT_FAMILY,
  letterSpacing: "0.06em",
  textTransform: "uppercase",
};

export function SlopeCounter({ data, mu }: SlopeCounterProps) {
  const left = data.filter((x) => x < mu).length;
  const right = data.filter((x) => x > mu).length;
  const diff = left - right;

  return (
    <div
      data-testid="slope-counter"
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "0.5rem",
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
        <span
          data-testid="slope-left-chip"
          style={{ ...chipBase, background: PAPER, color: INK }}
        >
          LEFT: {left}
        </span>
        <span
          data-testid="slope-right-chip"
          style={{ ...chipBase, background: ACCENT, color: INK }}
        >
          RIGHT: {right}
        </span>
      </div>
      <div style={{ fontSize: "0.75rem", opacity: 0.7, marginTop: "0.1rem" }}>
        slope ={" "}
        <span
          data-testid="slope-diff"
          style={{ fontWeight: 900, color: INK }}
        >
          {diff >= 0 ? `+${diff}` : `${diff}`}
        </span>{" "}
        ({left} − {right})
      </div>
    </div>
  );
}
