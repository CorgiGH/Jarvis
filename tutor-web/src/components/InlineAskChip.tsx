import type { SidekickEnvelope } from "../lib/inlineAsk";

interface InlineAskChipProps {
  selectionRect: DOMRect;
  envelope: SidekickEnvelope;
  onAsk: (env: SidekickEnvelope) => void;
}

export function InlineAskChip({ selectionRect, envelope, onAsk }: InlineAskChipProps) {
  const top = selectionRect.top + window.scrollY - 8;
  const left = selectionRect.left + window.scrollX;

  return (
    <button
      className="ask-chip-fade-in"
      style={{
        position: "fixed",
        top: `${top}px`,
        left: `${left}px`,
        transform: "translateY(-100%)",
        zIndex: 9999,
        display: "inline-flex",
        alignItems: "center",
        gap: "4px",
        padding: "3px 8px",
        background: "var(--color-accent, #ffcc00)",
        color: "var(--color-page-fg, #0a0a0a)",
        fontFamily: "monospace",
        fontSize: "11px",
        fontWeight: 700,
        letterSpacing: "0.08em",
        border: "2px solid var(--color-border-strong, #0a0a0a)",
        borderRadius: 0,
        cursor: "pointer",
        userSelect: "none",
        whiteSpace: "nowrap",
      }}
      aria-label="✨ ASK sidekick about selection"
      onMouseDown={(e) => e.preventDefault()}
      onClick={() => onAsk(envelope)}
    >
      ✨ ASK
    </button>
  );
}
