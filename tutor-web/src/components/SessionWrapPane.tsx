/** SessionWrapPane — end-of-session overlay (surface 5).
 *
 *  Shows: monumental N-CARDS count + MasterySparkline delta-mode
 *  list + narrative text + DONE CTA.
 *
 *  Props:
 *   reply   — ApiSessionCloseReply (§R ApiMasteryDelta inner shape)
 *   onDone  — called when user clicks "TERMINAT"
 *
 *  testids:
 *   session-wrap-pane
 *   session-wrap-cards-count
 *   session-wrap-mastery-sparkline
 *   session-wrap-done-btn
 */
import { MasterySparkline } from "./MasterySparkline";

export interface ApiMasteryDelta {
  kc_id: string;
  attempts: number;
  correct: number;
  ewma_after: number;
  phase: "intro" | "practice" | "retrieval" | "mastered";
}

export interface ApiSessionCloseReply {
  session_id: string;
  narrative: string | null;
  cards_reviewed: number;
  kcs_moved_to_mastered: string[];
  mastery_deltas: ApiMasteryDelta[];
}

interface SessionWrapPaneProps {
  reply: ApiSessionCloseReply;
  onDone: () => void;
}

export function SessionWrapPane({ reply, onDone }: SessionWrapPaneProps) {
  const { cards_reviewed, narrative, mastery_deltas, kcs_moved_to_mastered } = reply;

  return (
    <div
      data-testid="session-wrap-pane"
      className="fixed inset-0 z-50 bg-page-bg flex flex-col items-center justify-center p-8 font-mono"
    >
      {/* Monumental card count */}
      <div className="text-center mb-6">
        <span
          data-testid="session-wrap-cards-count"
          className="text-8xl font-black tracking-tight text-accent"
          aria-label={`${cards_reviewed} cărți studiate`}
        >
          {cards_reviewed}
        </span>
        <div className="text-xs uppercase tracking-widest text-page-fg/60 mt-2">
          cărți studiate azi
        </div>
      </div>

      {/* Narrative */}
      {narrative && (
        <p className="text-sm tracking-widest text-page-fg/80 mb-6 text-center max-w-md">
          {narrative}
        </p>
      )}

      {/* KCs moved to mastered */}
      {kcs_moved_to_mastered.length > 0 && (
        <div className="mb-4 text-xs tracking-wider text-accent uppercase font-bold">
          {kcs_moved_to_mastered.length === 1
            ? "1 noțiune stăpânită azi"
            : `${kcs_moved_to_mastered.length} noțiuni stăpânite azi`}
        </div>
      )}

      {/* MasterySparkline delta list */}
      {mastery_deltas.length > 0 && (
        <div
          data-testid="session-wrap-mastery-sparkline"
          className="flex flex-col gap-2 mb-8 w-full max-w-sm"
          aria-label="Progres pe noțiuni"
        >
          {mastery_deltas.map((d) => (
            <div
              key={d.kc_id}
              className="flex items-center gap-3 text-[10px] tracking-widest text-page-fg/70"
            >
              <MasterySparkline ewmaScore={d.ewma_after} />
              <span className="text-[10px] uppercase tracking-wider text-page-fg/50 border border-border-strong px-1">
                {d.phase}
              </span>
              <span className="text-[10px] text-page-fg/50 truncate">
                {d.kc_id}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Done CTA */}
      <button
        data-testid="session-wrap-done-btn"
        onClick={onDone}
        className="bg-accent text-page-fg text-sm font-bold tracking-widest px-8 py-3 uppercase hover:bg-accent-hover"
      >
        Terminat
      </button>
    </div>
  );
}
