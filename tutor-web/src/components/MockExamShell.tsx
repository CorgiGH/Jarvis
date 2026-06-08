import { useState, useCallback } from "react";
import { MockTimer } from "./MockTimer";
import { MockQuestionBlock } from "./MockQuestionBlock";
import { MockQuestionNav } from "./MockQuestionNav";

interface MockExamShellProps {
  subject: string;
  questions: string[];
  timeLimitSeconds: number;
  onSubmit: (answers: string[]) => void;
}

export function MockExamShell({
  subject,
  questions,
  timeLimitSeconds,
  onSubmit,
}: MockExamShellProps) {
  const [answers, setAnswers] = useState<string[]>(() =>
    Array(questions.length).fill("")
  );
  const [activeIndex, setActiveIndex] = useState(0);
  const [timeExpired, setTimeExpired] = useState(false);

  const allAnswered = answers.every((a) => a.trim().length > 0);

  const handleAnswer = useCallback((index: number, value: string) => {
    setAnswers((prev) => {
      const next = [...prev];
      next[index] = value;
      return next;
    });
  }, []);

  const handleExpire = useCallback(() => {
    setTimeExpired(true);
  }, []);

  const handleSubmit = useCallback(() => {
    onSubmit(answers);
  }, [answers, onSubmit]);

  return (
    <div
      data-testid="mock-exam-shell"
      className="flex flex-col h-full font-mono bg-page-bg text-page-fg"
    >
      {/* Sticky header: title + timer + nav */}
      <header className="sticky top-0 z-10 bg-page-bg border-b-4 border-border-strong px-4 py-2 flex flex-col gap-2">
        <div className="flex items-center justify-between gap-4">
          <span className="text-xs tracking-widest font-bold text-page-fg/70 uppercase">
            Examen simulare — {subject}
          </span>
          <MockTimer
            totalSeconds={timeLimitSeconds}
            onExpire={handleExpire}
          />
        </div>
        <div className="flex items-center gap-3">
          <MockQuestionNav
            count={questions.length}
            answers={answers}
            activeIndex={activeIndex}
            onSelect={setActiveIndex}
          />
          <button
            data-testid="mock-submit-btn"
            disabled={!allAnswered || timeExpired}
            onClick={handleSubmit}
            className="ml-auto px-4 py-1 text-xs font-bold tracking-widest border-2 border-border-strong bg-page-bg text-page-fg hover:bg-accent hover:border-accent hover:text-page-fg disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            TRIMITE
          </button>
        </div>
        {timeExpired && (
          <p className="text-danger-text text-xs tracking-widest">
            Timp expirat — răspunsurile au fost blocate.
          </p>
        )}
      </header>

      {/* Question blocks */}
      <main className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-4 p-4">
        {questions.map((q, i) => (
          <MockQuestionBlock
            key={i}
            index={i}
            question={q}
            answer={answers[i] ?? ""}
            onAnswer={handleAnswer}
            isActive={i === activeIndex}
          />
        ))}
      </main>
    </div>
  );
}
