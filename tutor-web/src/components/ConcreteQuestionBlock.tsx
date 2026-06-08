/**
 * ConcreteQuestionBlock — surface 0c.
 * The concrete opening question block for Lesson Entry.
 * Shows the question stem (RO) + text input + submit button.
 *
 * testids: concrete-question-block · concrete-question-input · concrete-question-submit
 */
import { useState } from "react";

interface ConcreteQuestionBlockProps {
  /** The opening question (RO). Degraded fallback: KC name when stem_template is null. */
  question: string;
  /** Called with the student's answer text when submitted. */
  onAnswer: (text: string) => void;
}

export function ConcreteQuestionBlock({ question, onAnswer }: ConcreteQuestionBlockProps) {
  const [text, setText] = useState("");

  const canSubmit = text.trim().length > 0;

  function handleSubmit() {
    if (!canSubmit) return;
    onAnswer(text);
  }

  return (
    <div
      data-testid="concrete-question-block"
      className="flex flex-col gap-4 p-4 font-mono"
    >
      {/* Question stem */}
      <p className="text-sm font-bold tracking-widest text-page-fg leading-relaxed">
        {question}
      </p>

      {/* Answer textarea */}
      <textarea
        data-testid="concrete-question-input"
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={3}
        placeholder="Răspunsul tău…"
        className="w-full bg-panel-dark border-2 border-border-strong text-page-fg font-mono text-xs tracking-wide p-3 resize-none focus:border-accent focus:outline-none"
      />

      {/* Submit */}
      <button
        data-testid="concrete-question-submit"
        onClick={handleSubmit}
        disabled={!canSubmit}
        className="self-start border-2 border-accent bg-accent text-black font-mono font-bold text-xs tracking-widest uppercase px-4 py-2 disabled:opacity-30 disabled:cursor-not-allowed hover:enabled:bg-accent-hover transition-colors"
      >
        Continuă
      </button>
    </div>
  );
}
