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
          <span key={i} style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{seg.text}</span>
        ) : seg.display ? (
          // Display math can be very wide. Allow horizontal scroll within the
          // block so the parent column doesn't grow beyond viewport width.
          <span
            key={i}
            data-testid="math-display"
            className="block overflow-x-auto max-w-full my-1"
            dangerouslySetInnerHTML={{ __html: renderMath(seg.tex, true) }}
          />
        ) : (
          <span
            key={i}
            data-testid="math-inline"
            className="inline-block max-w-full overflow-x-auto align-middle"
            dangerouslySetInnerHTML={{ __html: renderMath(seg.tex, false) }}
          />
        ),
      )}
    </div>
  );
}
