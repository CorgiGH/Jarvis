/**
 * LessonEntryBand — surface 0c.
 * The dark→light transitional accent-rule band (Lesson Entry).
 * Shows the subject pill + new KC name to signal "a new term is starting."
 *
 * testids: lesson-entry-band
 */

interface LessonEntryBandProps {
  /** Subject identifier (RO). */
  subject: string;
  /** Romanian KC name (the new term). */
  kcNameRo: string;
}

export function LessonEntryBand({ subject, kcNameRo }: LessonEntryBandProps) {
  return (
    <div
      data-testid="lesson-entry-band"
      style={{
        borderBottom: "2px solid var(--color-accent, #e5c04a)",
        background: "var(--color-page-bg, #111)",
      }}
      className="flex flex-col gap-2 px-4 py-4 font-mono"
    >
      {/* Subject pill */}
      <span className="self-start bg-accent text-black px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider">
        {subject}
      </span>

      {/* KC name as the "new term" heading */}
      <p className="text-lg font-bold tracking-widest text-page-fg uppercase leading-tight">
        {kcNameRo}
      </p>

      {/* Entry label */}
      <span className="text-[10px] font-bold uppercase tracking-widest text-accent">
        Concept nou
      </span>
    </div>
  );
}
