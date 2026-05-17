import { useEffect, useState, useCallback } from "react";
import {
  getDue, getForecast, gradeCard,
  type FsrsCardView, type FsrsForecastReply,
} from "../lib/fsrsClient";

const FLIP_STYLE = `
.fsrs-scene { perspective: 900px; }
.fsrs-card-flip {
  position: relative; width: 100%;
  transform-style: preserve-3d;
  transition: transform 400ms cubic-bezier(.4,0,.2,1);
}
.fsrs-card-flip.is-flipped { transform: rotateY(180deg); }
.fsrs-card-face {
  position: absolute; width: 100%;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
}
.fsrs-card-face.fsrs-card-back { transform: rotateY(180deg); }
.fsrs-card-flip-container { position: relative; }
@media (prefers-reduced-motion: reduce) {
  .fsrs-card-flip { transition: none; }
}
`;

interface Props { streak: number; }
type Grade = 1 | 2 | 3 | 4;

const GRADE_LABELS: Record<Grade, string> = {
  1: "AGAIN ~10m",
  2: "HARD ~1d",
  3: "GOOD ~4d",
  4: "EASY ~12d",
};

export function FsrsReview({ streak }: Props) {
  const [cards, setCards] = useState<FsrsCardView[]>([]);
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [forecast, setForecast] = useState<FsrsForecastReply | null>(null);
  const [loading, setLoading] = useState(true);
  const [grading, setGrading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([getDue(50), getForecast()])
      .then(([dueCards, fc]) => {
        if (cancelled) return;
        setCards(dueCards);
        setForecast(fc);
      })
      .catch((err: Error) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const currentCard: FsrsCardView | undefined = cards[index];
  const totalDue = cards.length;
  const done = !loading && cards.length > 0 && index >= cards.length;
  const empty = !loading && cards.length === 0;

  const handleShowAnswer = useCallback(() => setFlipped(true), []);

  const handleGrade = useCallback(async (grade: Grade) => {
    if (!currentCard || grading) return;
    setGrading(true);
    try {
      await gradeCard(currentCard.id, grade);
      setIndex(prev => prev + 1);
      setFlipped(false);
    } catch (_) {
      // silent — user can retry
    } finally {
      setGrading(false);
    }
  }, [currentCard, grading]);

  return (
    <>
      <style>{FLIP_STYLE}</style>
      <div className="flex flex-col h-full font-mono bg-page-bg text-page-fg">
        <header
          data-testid="fsrs-header"
          className="bg-panel-dark-bg text-panel-dark-fg px-4 py-3 flex items-center gap-4 border-b-4 border-accent tracking-widest font-bold text-sm"
        >
          <span>JARVIS · REVIEW</span>
          {!loading && !error && (
            <>
              <span className="text-accent">{totalDue} DUE</span>
              {streak > 0 && <span className="text-orange-400">🔥 {streak}-DAY STREAK</span>}
            </>
          )}
        </header>

        <main className="flex-1 min-h-0 overflow-y-auto flex flex-col items-center justify-start p-6 gap-6">
          {loading && <p className="text-page-fg/80 tracking-widest text-sm">loading review queue…</p>}
          {error && <p className="text-danger-text tracking-widest text-sm" role="alert">(can't reach review queue — {error})</p>}

          {(empty || done) && !loading && !error && (
            <div data-testid="fsrs-empty" className="text-center space-y-2">
              <p className="text-2xl font-bold tracking-widest">ALL DONE</p>
              <p className="text-page-fg/80 text-sm tracking-widest">no cards due right now — check back later</p>
            </div>
          )}

          {currentCard && !done && !loading && (
            <>
              <p className="text-xs text-page-fg/80 tracking-widest self-start">CARD {index + 1} OF {totalDue}</p>

              <div className="fsrs-scene w-full max-w-xl">
                <div className="fsrs-card-flip-container" style={{ minHeight: "180px" }}>
                  <div
                    data-testid="card-flip-wrapper"
                    className={`fsrs-card-flip ${flipped ? "is-flipped" : ""}`}
                    style={{ minHeight: "180px" }}
                  >
                    <div className="fsrs-card-face border-4 border-border-strong bg-accent-soft p-6 flex flex-col gap-4" style={{ minHeight: "180px" }}>
                      <p className="text-[10px] tracking-widest text-page-fg/80 font-bold">FRONT · click to flip</p>
                      <p data-testid="card-front" className="text-base leading-relaxed">{currentCard.front}</p>
                      <div className="flex gap-3 mt-auto">
                        <button data-testid="show-answer-btn" onClick={handleShowAnswer} className="px-4 py-2 bg-accent hover:bg-accent-hover text-page-fg font-bold tracking-widest text-xs">SHOW ANSWER</button>
                      </div>
                    </div>
                    <div className="fsrs-card-face fsrs-card-back border-4 border-accent bg-page-bg p-6 flex flex-col gap-4" style={{ minHeight: "180px" }}>
                      <p className="text-[10px] tracking-widest text-page-fg/80 font-bold">ANSWER</p>
                      <p data-testid="card-back" className="text-base leading-relaxed">{currentCard.back}</p>
                    </div>
                  </div>
                </div>
              </div>

              {flipped && (
                <div data-testid="grade-buttons" className="flex flex-wrap gap-3 justify-center">
                  {([1, 2, 3, 4] as Grade[]).map(g => (
                    <button
                      key={g}
                      data-testid={`grade-btn-${g}`}
                      onClick={() => handleGrade(g)}
                      disabled={grading}
                      className={`px-4 py-2 font-bold tracking-widest text-xs border-2 border-border-strong hover:bg-accent hover:text-page-fg disabled:opacity-50 ${g === 3 ? "bg-accent text-page-fg" : "bg-page-bg text-page-fg"}`}
                    >
                      {GRADE_LABELS[g]}
                    </button>
                  ))}
                </div>
              )}
            </>
          )}

          {forecast && (
            <div data-testid="fsrs-forecast" className="mt-auto border-t border-border-thin pt-4 w-full max-w-xl flex gap-6 text-xs tracking-widest text-page-fg/70">
              <span>FORECAST · tomorrow <strong className="text-page-fg">{forecast.tomorrow}</strong> · week <strong className="text-page-fg">{forecast.thisWeek}</strong> · month <strong className="text-page-fg">{forecast.thisMonth}</strong></span>
            </div>
          )}
        </main>
      </div>
    </>
  );
}
