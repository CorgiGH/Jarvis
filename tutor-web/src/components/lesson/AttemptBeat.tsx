import type { ApiBeatAttempt } from "../../lib/lesson";

interface AttemptBeatProps {
  attempt: ApiBeatAttempt;
  committedIndex: number | null;
  onCommitChoice: (index: number) => void;
}

/**
 * ATTEMPT beat — choice variant (data-testid="attempt-choice" per option) OR a read-only numerical
 * skeleton trace (skeleton_rows present). The 4 faithful KCs (Task 7) are choice-variant; the
 * numerical render path ships for formula-application/procedure/probabilistic KCs (DTO-carried).
 */
export function AttemptBeat({ attempt, committedIndex, onCommitChoice }: AttemptBeatProps) {
  const numerical = attempt.skeleton_rows.length > 0;
  return (
    <div className="flex flex-col gap-5 font-mono">
      <p className="text-lg sm:text-xl font-bold tracking-tight text-page-fg leading-snug">{attempt.statement}</p>

      {numerical ? (
        <table data-testid="attempt-skeleton" className="border-2 border-border-strong text-sm text-page-fg">
          <tbody>
            {attempt.skeleton_rows.map((row, i) => (
              <tr key={i} className={row.is_decision_row ? "border-2 border-accent" : "border-b border-border-strong"}>
                <td className="px-3 py-2 font-bold tracking-wide">{row.label}</td>
                <td className="px-3 py-2 font-mono text-page-fg/80">{row.formula ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="flex flex-col gap-2.5">
          {attempt.choices.map((c, i) => {
            const committed = committedIndex === i;
            return (
              <button
                key={i}
                data-testid="attempt-choice"
                data-attempt-index={i}
                disabled={committedIndex !== null}
                onClick={() => onCommitChoice(i)}
                className={
                  "border-2 px-4 py-3.5 text-left text-sm tracking-wide transition-colors " +
                  (committed
                    ? "border-accent bg-accent text-black font-bold shadow-hard"
                    : "border-border-strong text-page-fg hover:border-accent hover:text-accent hover:bg-accent-soft disabled:opacity-40")
                }
              >
                {c.text}
              </button>
            );
          })}
        </div>
      )}

      {/* Both-path feedback shown after commit (choice variant). */}
      {!numerical && committedIndex !== null && (
        <p data-testid="attempt-feedback" className="border-l-4 border-accent pl-3 text-sm text-page-fg/80 leading-relaxed">
          {attempt.choices[committedIndex].feedback}
        </p>
      )}
    </div>
  );
}
