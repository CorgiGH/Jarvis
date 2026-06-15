import type { ApiBeatPredict } from "../../lib/lesson";

interface PredictBeatProps {
  predict: ApiBeatPredict;
  /** Index of the committed option, or null before commit. */
  committedIndex: number | null;
  /** Called when the learner picks an option (orchestrator POSTs + stores for echo). */
  onCommit: (index: number) => void;
}

/** PREDICT beat — classify-an-example-first. Options data-testid="beat-predict-options". */
export function PredictBeat({ predict, committedIndex, onCommit }: PredictBeatProps) {
  return (
    <div className="flex flex-col gap-5 font-mono">
      <p className="text-lg sm:text-xl font-bold tracking-tight text-page-fg leading-snug">{predict.prompt}</p>
      <div data-testid="beat-predict-options" className="flex flex-col gap-2.5">
        {predict.options.map((opt, i) => {
          const committed = committedIndex === i;
          return (
            <button
              key={i}
              data-predict-index={i}
              disabled={committedIndex !== null}
              onClick={() => onCommit(i)}
              className={
                "border-2 px-4 py-3.5 text-left text-sm tracking-wide transition-colors " +
                (committed
                  ? "border-accent bg-accent text-black font-bold shadow-hard"
                  : "border-border-strong text-page-fg hover:border-accent hover:text-accent hover:bg-accent-soft disabled:opacity-40")
              }
            >
              {opt.text}
            </button>
          );
        })}
      </div>
    </div>
  );
}
