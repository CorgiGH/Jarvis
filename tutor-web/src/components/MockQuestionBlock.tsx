interface MockQuestionBlockProps {
  index: number;
  question: string;
  answer: string;
  onAnswer: (index: number, value: string) => void;
  isActive: boolean;
}

export function MockQuestionBlock({
  index,
  question,
  answer,
  onAnswer,
  isActive,
}: MockQuestionBlockProps) {
  const answered = answer.trim().length > 0;

  return (
    <div
      data-testid={`mock-question-${index}`}
      data-answered={String(answered)}
      className={`border-2 p-4 font-mono flex flex-col gap-3 ${
        isActive
          ? "border-accent bg-accent-soft"
          : "border-border-strong bg-page-bg"
      }`}
    >
      <div className="flex items-start gap-2">
        <span className="text-xs tracking-widest text-page-fg/60 shrink-0 pt-0.5">
          Q{index + 1}
        </span>
        <p className="text-sm text-page-fg leading-relaxed">{question}</p>
        {answered && (
          <span className="ml-auto shrink-0 text-[10px] tracking-widest text-accent font-bold">
            COMPLETAT
          </span>
        )}
      </div>
      <textarea
        className="w-full min-h-[80px] bg-page-bg border-2 border-border-strong text-page-fg font-mono text-sm p-2 resize-none focus:outline-none focus:border-accent"
        value={answer}
        onChange={(e) => onAnswer(index, e.target.value)}
        placeholder="Răspunsul tău…"
        aria-label={`Răspuns la întrebarea ${index + 1}`}
      />
    </div>
  );
}
