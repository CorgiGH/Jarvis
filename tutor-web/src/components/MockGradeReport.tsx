import { MockScoreSparkline } from "./MockScoreSparkline";

export interface MockQuestionResult {
  index: number;
  question: string;
  answer: string;
  correct: boolean;
  /** UNCERTAIN = graded but confidence is low (SYNC 200-only, H13 contract). */
  uncertain: boolean;
}

interface MockGradeReportProps {
  subject: string;
  /** Score 0..1 from the sync 200 reply. */
  score: number;
  /** Previous score for delta sparkline, null if no prior. */
  previousScore: number | null;
  results: MockQuestionResult[];
  onRedoWrong: () => void;
}

export function MockGradeReport({
  subject,
  score,
  previousScore,
  results,
  onRedoWrong,
}: MockGradeReportProps) {
  const pct = Math.round(score * 100);
  const wrongCount = results.filter((r) => !r.correct).length;

  return (
    <div
      data-testid="mock-grade-report"
      className="flex flex-col h-full font-mono bg-page-bg text-page-fg"
    >
      {/* Header */}
      <header className="bg-page-bg border-b-4 border-border-strong px-4 py-3 flex flex-col gap-3">
        <span className="text-xs tracking-widest text-page-fg/70 uppercase">
          Rezultat examen simulare — {subject}
        </span>

        {/* Score plate */}
        <div
          data-testid="mock-score-plate"
          className="flex items-center gap-6"
        >
          <span className="text-4xl font-bold tracking-widest text-accent">
            {pct}%
          </span>
          <MockScoreSparkline score={score} previousScore={previousScore} />
        </div>
      </header>

      {/* Per-question results table */}
      <main className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-2 p-4">
        {results.map((r) => (
          <div
            key={r.index}
            data-testid={`mock-question-result-${r.index}`}
            data-uncertain={String(r.uncertain)}
            className={`border-2 p-3 flex flex-col gap-1 ${
              r.correct
                ? "border-accent/50 bg-accent/5"
                : "border-danger-text/40 bg-danger-text/5"
            }`}
          >
            <div className="flex items-start gap-2">
              <span className="text-[10px] tracking-widest text-page-fg/60 shrink-0 pt-0.5">
                Q{r.index + 1}
              </span>
              <p className="text-xs text-page-fg flex-1">{r.question}</p>
              <div className="flex items-center gap-1 shrink-0">
                {r.uncertain && (
                  <span className="text-[10px] tracking-widest font-bold border border-border-strong px-1 text-page-fg/60">
                    INCERT
                  </span>
                )}
                <span
                  className={`text-[10px] tracking-widest font-bold ${
                    r.correct ? "text-accent" : "text-danger-text"
                  }`}
                >
                  {r.correct ? "CORECT" : "GREȘIT"}
                </span>
              </div>
            </div>
            <p className="text-xs text-page-fg/60 pl-6 line-clamp-2">
              {r.answer}
            </p>
          </div>
        ))}
      </main>

      {/* Footer CTA */}
      {wrongCount > 0 && (
        <footer className="border-t-4 border-border-strong px-4 py-3">
          <button
            data-testid="mock-redo-btn"
            onClick={onRedoWrong}
            className="px-4 py-2 text-xs font-bold tracking-widest border-2 border-accent text-accent hover:bg-accent hover:text-page-fg transition-colors"
          >
            REPETĂ {wrongCount} GREȘITE
          </button>
        </footer>
      )}
      {wrongCount === 0 && (
        <footer className="border-t-4 border-border-strong px-4 py-3">
          <button
            data-testid="mock-redo-btn"
            onClick={onRedoWrong}
            className="px-4 py-2 text-xs font-bold tracking-widest border-2 border-border-strong text-page-fg/60 hover:bg-accent hover:border-accent hover:text-page-fg transition-colors"
          >
            REPETĂ DIN NOU
          </button>
        </footer>
      )}
    </div>
  );
}
