import { useState } from "react";
import type { LadderRung } from "../lib/drillGrader";
import { MathText } from "./MathText";

interface FeedbackLadderProps {
  /** Ordered L0..L4 rungs, served verbatim by /drill/grade. */
  rungs: LadderRung[];
  /** Notified with the new visible level (1..n) on each escalate. */
  onEscalate?: (level: number) => void;
}

/**
 * KEYSTONE surface (render-queue #1). PURE rendering of server-served scaffold
 * rungs — NO client-side re-derivation. Least nudge first (L0), escalate to the
 * full solution (L4) one rung at a time. Data source: gradeResult.ladder_rungs.
 */
export function FeedbackLadder({ rungs, onEscalate }: FeedbackLadderProps) {
  const [revealed, setRevealed] = useState(0); // index of the highest revealed rung
  if (!rungs || rungs.length === 0) return null;

  const last = rungs.length - 1;
  const atTop = revealed >= last;

  function escalate() {
    if (atTop) return;
    const next = revealed + 1;
    setRevealed(next);
    onEscalate?.(next);
  }

  return (
    <section
      data-testid="drill-feedback-ladder"
      className="mt-3 border-4 border-border-strong bg-page-bg font-mono"
    >
      {/* 5-pip rail */}
      <div
        data-testid="feedback-rung-rail"
        className="flex gap-1.5 px-3 py-2 border-b-4 border-border-strong bg-panel-dark-bg"
      >
        {rungs.map((r, i) => (
          <span
            key={r.level}
            data-testid={i === revealed ? "feedback-rung-live-pip" : undefined}
            aria-label={`rung L${r.level}`}
            className={`h-3 w-3 border-2 border-panel-dark-fg ${
              i <= revealed ? "bg-accent" : "bg-transparent"
            }`}
          />
        ))}
      </div>

      {/* Revealed rung panels (cumulative) */}
      <ol className="px-4 py-3 flex flex-col gap-2">
        {rungs.slice(0, revealed + 1).map((r) => (
          <li
            key={r.level}
            data-testid={`feedback-rung-${r.level}`}
            data-rung-level={r.level}
            className={`text-xs leading-relaxed text-page-fg ${
              r.level === 1 ? "border-l-2 border-accent-rule pl-2" : ""
            }`}
          >
            <span className="mr-2 text-[10px] tracking-widest text-page-fg/60">
              L{r.level}
            </span>
            <MathText text={r.text} className="inline" />
          </li>
        ))}
      </ol>

      <div className="px-4 pb-3">
        <button
          type="button"
          data-testid="feedback-rung-escalate-button"
          onClick={escalate}
          disabled={atTop}
          className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-40 transition-all duration-[280ms] ease-in-out active:scale-95"
        >
          {atTop ? "AM RENUNȚAT — VEZI SOLUȚIA" : "ARATĂ-MI MAI MULT →"}
        </button>
      </div>
    </section>
  );
}
