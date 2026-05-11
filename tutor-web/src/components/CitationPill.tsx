import type { Citation } from "../lib/sidekickContext";

interface CitationPillProps {
  citation: Citation;
  onClick: (citation: Citation) => void;
}

/** Brutalist yellow-on-dark pill showing the basename of a citation path. */
export function CitationPill({ citation, onClick }: CitationPillProps) {
  const basename = citation.path.split("/").pop() ?? citation.path;

  return (
    <button
      data-testid="citation-pill"
      onClick={() => onClick(citation)}
      style={{
        display: "inline-block",
        padding: "2px 8px",
        marginRight: "4px",
        marginBottom: "4px",
        fontSize: "10px",
        letterSpacing: "0.08em",
        fontFamily: "monospace",
        fontWeight: 700,
        background: "transparent",
        color: "var(--color-accent, #ffcc00)",
        border: "1px solid var(--color-accent, #ffcc00)",
        cursor: "pointer",
        lineHeight: 1.6,
      }}
      title={citation.snippet}
    >
      {basename}
    </button>
  );
}
