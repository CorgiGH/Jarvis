/** LangToggle — RO / EN language switcher for onboarding.
 *
 *  Controlled: caller owns the lang state.
 *  testids: lang-toggle
 */
export type Lang = "ro" | "en";

interface Props {
  lang: Lang;
  onToggle: (next: Lang) => void;
}

export function LangToggle({ lang, onToggle }: Props) {
  return (
    <div
      data-testid="lang-toggle"
      className="flex items-center gap-0 font-mono border border-border-strong self-start"
      role="group"
      aria-label="Limbă / Language"
    >
      {(["ro", "en"] as Lang[]).map((l) => (
        <button
          key={l}
          type="button"
          onClick={() => onToggle(l)}
          aria-pressed={lang === l}
          className={
            "px-3 py-1 text-xs font-bold tracking-widest uppercase transition-colors " +
            (lang === l
              ? "bg-accent text-page-fg"
              : "bg-transparent text-page-fg/50 hover:text-page-fg")
          }
        >
          {l.toUpperCase()}
        </button>
      ))}
    </div>
  );
}
