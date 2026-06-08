interface MockQuestionNavProps {
  count: number;
  answers: string[];
  activeIndex: number;
  onSelect: (index: number) => void;
}

export function MockQuestionNav({
  count,
  answers,
  activeIndex,
  onSelect,
}: MockQuestionNavProps) {
  return (
    <div
      data-testid="mock-question-nav"
      className="flex gap-2 items-center flex-wrap"
    >
      {Array.from({ length: count }, (_, i) => {
        const answered = (answers[i] ?? "").trim().length > 0;
        const active = i === activeIndex;
        return (
          <button
            key={i}
            role="button"
            data-active={String(active)}
            data-answered={String(answered)}
            aria-label={`Întrebarea ${i + 1}${answered ? " — completată" : ""}`}
            onClick={() => onSelect(i)}
            className={`w-7 h-7 border-2 font-mono text-xs font-bold tracking-widest transition-colors ${
              active
                ? "bg-accent border-accent text-page-fg"
                : answered
                ? "bg-accent/30 border-accent text-page-fg"
                : "bg-page-bg border-border-strong text-page-fg/60"
            }`}
          >
            {i + 1}
          </button>
        );
      })}
    </div>
  );
}
