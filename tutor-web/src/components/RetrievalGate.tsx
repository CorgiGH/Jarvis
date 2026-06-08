/**
 * RetrievalGate — surface 0e.
 * DARK door-language closed-book recall:
 *   monumental prompt → answer plate → submit.
 * Used inside LessonScreen (T8-7).
 *
 * testids: retrieval-gate · retrieval-prompt · retrieval-answer-input · retrieval-submit
 */
import { useState } from "react";

interface RetrievalGateProps {
  /** The closed-book retrieval prompt (RO). */
  prompt: string;
  /** Called with the student's answer when submitted. */
  onSubmit: (answer: string) => void;
}

export function RetrievalGate({ prompt, onSubmit }: RetrievalGateProps) {
  const [answer, setAnswer] = useState("");

  const canSubmit = answer.trim().length > 0;

  function handleSubmit() {
    if (!canSubmit) return;
    onSubmit(answer);
  }

  return (
    <div
      data-testid="retrieval-gate"
      className="flex flex-col gap-4 bg-page-bg p-6 font-mono"
    >
      {/* Monumental prompt */}
      <p
        data-testid="retrieval-prompt"
        className="text-sm font-bold tracking-widest text-page-fg leading-relaxed uppercase"
      >
        {prompt}
      </p>

      {/* Answer plate */}
      <textarea
        data-testid="retrieval-answer-input"
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        rows={4}
        placeholder="Răspunsul tău…"
        className="w-full bg-panel-dark border-2 border-border-strong text-page-fg font-mono text-xs tracking-wide p-3 resize-none focus:border-accent focus:outline-none"
      />

      {/* Submit */}
      <button
        data-testid="retrieval-submit"
        onClick={handleSubmit}
        disabled={!canSubmit}
        className="self-start border-2 border-accent bg-accent text-black font-mono font-bold text-xs tracking-widest uppercase px-4 py-2 disabled:opacity-30 disabled:cursor-not-allowed hover:enabled:bg-accent-hover transition-colors"
      >
        Trimite
      </button>
    </div>
  );
}
