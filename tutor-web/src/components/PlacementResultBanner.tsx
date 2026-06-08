/** PlacementResultBanner — post-submit result (surface 10, view B).
 *
 *  Shows score plate + recommendation text + continue CTA.
 *  Future: node-graph via --fig-* Figure system (deferred until Figure system lands).
 *
 *  testids: placement-result-banner
 */
export interface PlacementResult {
  score: number;
  total: number;
  recommendation: string;
}

interface Props {
  result: PlacementResult;
  onContinue: () => void;
}

export function PlacementResultBanner({ result, onContinue }: Props) {
  const pct = result.total > 0 ? Math.round((result.score / result.total) * 100) : 0;

  return (
    <div
      data-testid="placement-result-banner"
      className="flex flex-col items-center gap-6 font-mono py-8"
    >
      {/* Score plate */}
      <div className="flex flex-col items-center gap-1 border border-accent px-8 py-6">
        <p className="text-[10px] font-bold tracking-widest uppercase text-panel-dark-fg/40">
          Rezultat plasare
        </p>
        <p className="text-5xl font-black text-accent tracking-tight">
          {result.score}
          <span className="text-xl text-panel-dark-fg/40 ml-1">/ {result.total}</span>
        </p>
        <p className="text-xs text-panel-dark-fg/60 tracking-widest">
          {pct}%
        </p>
      </div>

      {/* Recommendation */}
      <p className="text-sm text-panel-dark-fg/80 tracking-wide text-center max-w-sm leading-relaxed">
        {result.recommendation}
      </p>

      {/* Continue CTA */}
      <button
        type="button"
        onClick={onContinue}
        className="bg-accent text-page-fg text-xs font-bold tracking-widest uppercase px-6 py-3 hover:bg-accent-hover"
      >
        Continuă pregătirea
      </button>
    </div>
  );
}
