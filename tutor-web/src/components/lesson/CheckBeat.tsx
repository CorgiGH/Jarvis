import { useState } from "react";
import type { ApiBeatCheck } from "../../lib/lesson";
import { lessonStrings } from "../../lib/lessonStrings";

interface CheckBeatProps {
  check: ApiBeatCheck;
  /** Disable inputs once submitted (gate clears via the POST in the orchestrator). */
  submitted: boolean;
  /** Server feedback to render after grading. */
  feedbackRo: string | null;
  /** Choice variant. */
  onSubmitChoice: (index: number) => void;
  /** Numeric variant. */
  onSubmitNumeric: (value: string) => void;
}

/** CHECK beat — different-instance check item. Choice variant or numeric input. */
export function CheckBeat({ check, submitted, feedbackRo, onSubmitChoice, onSubmitNumeric }: CheckBeatProps) {
  const numeric = check.choices.length === 0 && check.numeric_answer != null;
  const [value, setValue] = useState("");

  return (
    <div className="flex flex-col gap-5 font-mono">
      <p className="text-lg sm:text-xl font-bold tracking-tight text-page-fg leading-snug">{check.item_stem}</p>

      {numeric ? (
        <div className="flex items-center gap-2">
          <input
            data-testid="check-numeric-input"
            value={value}
            disabled={submitted}
            onChange={(e) => setValue(e.target.value)}
            className="bg-panel-dark-bg border-2 border-border-strong text-page-fg text-sm p-2.5 w-40 focus:border-accent focus:outline-none"
          />
          <button
            data-testid="check-submit"
            disabled={submitted || value.trim().length === 0}
            onClick={() => onSubmitNumeric(value.trim())}
            className="border-2 border-accent bg-accent text-black font-bold text-xs tracking-widest uppercase px-5 py-2.5 disabled:opacity-30"
          >
            {lessonStrings.submit}
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-2.5">
          {check.choices.map((c, i) => (
            <button
              key={i}
              data-testid="check-choice"
              data-check-index={i}
              disabled={submitted}
              onClick={() => onSubmitChoice(i)}
              className="border-2 border-border-strong text-page-fg text-sm tracking-wide px-4 py-3.5 text-left hover:border-accent hover:text-accent hover:bg-accent-soft disabled:opacity-40 transition-colors"
            >
              {c.text}
            </button>
          ))}
        </div>
      )}

      {submitted && feedbackRo && (
        <p data-testid="check-feedback" className="border-l-4 border-accent pl-3 text-sm text-page-fg/80 leading-relaxed">
          {feedbackRo}
        </p>
      )}
    </div>
  );
}
