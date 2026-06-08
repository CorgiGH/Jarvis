/**
 * TermLanding — surface 0d.
 * One new term highlighted (accent bg / black ink) + RO/EN gloss inline.
 * Renders nothing when termRo is empty/blank.
 *
 * testids: term-landing · term-landing-term · term-landing-gloss
 */

interface TermLandingProps {
  /** Romanian label for the new term (primary). */
  termRo: string;
  /** English gloss (secondary, shown only when non-empty). */
  termEn: string;
}

export function TermLanding({ termRo, termEn }: TermLandingProps) {
  if (!termRo || !termRo.trim()) return null;

  return (
    <div data-testid="term-landing" className="flex flex-col gap-2 py-4">
      {/* The new term — highlighted */}
      <span
        data-testid="term-landing-term"
        className="bg-accent text-black inline-block px-3 py-1 font-mono font-bold tracking-widest text-base uppercase"
      >
        {termRo}
      </span>

      {/* EN gloss — secondary, muted */}
      {termEn && termEn.trim() && (
        <span
          data-testid="term-landing-gloss"
          className="font-mono text-xs text-page-fg/50 tracking-wider"
        >
          {termEn}
        </span>
      )}
    </div>
  );
}
