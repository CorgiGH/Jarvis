/** PlacementQuestion — single question in the placement exam (surface 10).
 *
 *  DARK door-language. Shows a prompt and 2-4 multiple-choice options.
 *  Calls onAnswer(optionIndex) when an option is selected (unless disabled).
 *
 *  testids: placement-question
 */
export interface PlacementQuestionData {
  id: string;
  promptRo: string;
  options: string[];
}

interface Props {
  question: PlacementQuestionData;
  onAnswer: (optionIndex: number) => void;
  disabled: boolean;
}

export function PlacementQuestion({ question, onAnswer, disabled }: Props) {
  return (
    <div
      data-testid="placement-question"
      className="flex flex-col gap-5 font-mono"
    >
      {/* Prompt */}
      <p className="text-sm font-bold tracking-wide text-panel-dark-fg leading-snug">
        {question.promptRo}
      </p>

      {/* Options */}
      <div className="flex flex-col gap-3">
        {question.options.map((opt, i) => (
          <button
            key={`${question.id}-opt-${i}`}
            type="button"
            onClick={() => !disabled && onAnswer(i)}
            disabled={disabled}
            className={
              "text-left text-xs tracking-wide px-4 py-3 border transition-colors " +
              (disabled
                ? "border-panel-dark-fg/20 text-panel-dark-fg/40 cursor-not-allowed"
                : "border-panel-dark-fg/40 text-panel-dark-fg/80 hover:border-accent hover:text-panel-dark-fg cursor-pointer")
            }
          >
            <span className="font-bold text-panel-dark-fg/40 mr-2">
              {String.fromCharCode(65 + i)}.
            </span>
            {opt}
          </button>
        ))}
      </div>
    </div>
  );
}
