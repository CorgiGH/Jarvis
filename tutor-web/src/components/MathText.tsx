import { useMemo } from "react";
import { splitMath, renderMath } from "../lib/mathText";

/**
 * Renders chat-message text with `$...$` and `$$...$$` LaTeX fragments
 * passed through KaTeX. Non-math text falls through with whitespace-pre-wrap
 * preserved. Math segments are rendered via `katex.renderToString` and
 * inserted via `dangerouslySetInnerHTML` — KaTeX escapes its own input.
 */
export function MathText({ text, className }: { text: string; className?: string }) {
  const segments = useMemo(() => splitMath(text), [text]);
  return (
    <div className={className} data-testid="math-text">
      {segments.map((seg, i) =>
        seg.type === "text" ? (
          <span key={i} style={{ whiteSpace: "pre-wrap" }}>{seg.text}</span>
        ) : (
          <span
            key={i}
            data-testid={seg.display ? "math-display" : "math-inline"}
            dangerouslySetInnerHTML={{ __html: renderMath(seg.tex, seg.display) }}
          />
        ),
      )}
    </div>
  );
}
