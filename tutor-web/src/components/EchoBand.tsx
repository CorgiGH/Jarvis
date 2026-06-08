/**
 * EchoBand — surface 0d.
 * Displays the student's own just-submitted answer in a 2px accent-rule quoted strip.
 * Renders nothing when text is empty/blank.
 *
 * testids: echo-band · echo-band-quote
 */

interface EchoBandProps {
  /** The text to echo back (the student's own answer or the source quote). */
  text: string;
}

export function EchoBand({ text }: EchoBandProps) {
  if (!text || !text.trim()) return null;

  return (
    <div
      data-testid="echo-band"
      style={{
        borderLeft: "2px solid var(--color-accent, #e5c04a)",
        paddingLeft: "12px",
        margin: "8px 0",
      }}
      className="bg-panel-dark/40"
    >
      <blockquote
        data-testid="echo-band-quote"
        className="font-mono text-xs text-page-fg/80 tracking-wide leading-relaxed italic"
      >
        {text}
      </blockquote>
    </div>
  );
}
