import katex from "katex";

/**
 * Mockup-gap bridge step 1: KaTeX math rendering in chat replies.
 *
 * The LLM already emits `$inline$` and `$$display$$` LaTeX fragments
 * (council R5 + 2026-05-08 mockup `tutor-mockup-v4.html` section 4).
 * Until this lib landed, those fragments rendered as raw text with
 * dollar signs. This splits a message into a list of plain-text and
 * math segments so the renderer can hand math to KaTeX without
 * blanket-trusting the LLM's output for HTML injection.
 *
 * Delimiter precedence: `$$...$$` (display) is checked BEFORE `$...$`
 * (inline) so `$$x^2$$` is one display block, not two empty-inline
 * fragments separated by `x^2`. Fragments must NOT cross a newline —
 * a stray `$` deep in code samples shouldn't accidentally swallow
 * paragraphs of text.
 *
 * `katex.renderToString({throwOnError: false})` returns escaped HTML
 * with class hooks defined in `katex/dist/katex.min.css` (already
 * imported in main.tsx). Errors render as red `\error` styled HTML —
 * we don't surface a JS exception that would blow up the whole
 * message render.
 */

export type MathSegment =
  | { type: "text"; text: string }
  | { type: "math"; tex: string; display: boolean };

const DISPLAY = /\$\$([^\n][^]*?[^\n])\$\$/g;
const INLINE = /\$([^$\n]+?)\$/g;

export function splitMath(input: string): MathSegment[] {
  if (!input) return [];
  // First pass: peel off display blocks.
  const segments: MathSegment[] = [];
  let cursor = 0;
  for (const m of input.matchAll(DISPLAY)) {
    const idx = m.index ?? 0;
    if (idx > cursor) {
      // Run inline pass on the text between display blocks.
      segments.push(...splitInline(input.slice(cursor, idx)));
    }
    segments.push({ type: "math", tex: m[1].trim(), display: true });
    cursor = idx + m[0].length;
  }
  if (cursor < input.length) {
    segments.push(...splitInline(input.slice(cursor)));
  }
  return segments;
}

function splitInline(input: string): MathSegment[] {
  if (!input) return [];
  const out: MathSegment[] = [];
  let cursor = 0;
  for (const m of input.matchAll(INLINE)) {
    const idx = m.index ?? 0;
    if (idx > cursor) out.push({ type: "text", text: input.slice(cursor, idx) });
    out.push({ type: "math", tex: m[1].trim(), display: false });
    cursor = idx + m[0].length;
  }
  if (cursor < input.length) out.push({ type: "text", text: input.slice(cursor) });
  return out;
}

export function renderMath(tex: string, display: boolean): string {
  return katex.renderToString(tex, {
    throwOnError: false,
    displayMode: display,
    output: "html",
  });
}
