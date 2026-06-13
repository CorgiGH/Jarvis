import { useEffect, useState } from "react";
import { listDeliverables } from "../../lib/practiceApi";
import type { Deliverable, DeliverablesReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

/**
 * Plan-6 Task 10 — Deliverable tracker surface (REQ-18..21, spec §6.2.5, R-6-Q10).
 *
 * Renders course deliverables (teme) from GET /api/v1/practice/deliverables.
 * All learner-visible copy from practiceStrings (INV-8.3).
 *
 * Honesty rules:
 *  - REQ-19: always shows the honesty line "Aplicația te pregătește..." (never grades real work).
 *  - REQ-21: deadline null → "necunoscut".
 *  - Prep drills: empty list → honest empty state copy.
 *  - POO lab deliverables absent (honest empty state — Task-2 exhaustive search found no POO
 *    lab spec files on disk; absence named in Task 2 report; no fabricated entry).
 *
 * Testids: deliverable-card, deliverable-points, deliverable-prep-drills,
 *          deliverable-deadline, deliverable-honesty-line.
 */

export function DeliverableTracker() {
  const [reply, setReply] = useState<DeliverablesReply | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listDeliverables()
      .then((r) => { if (!cancelled) setReply(r); })
      .catch((e) => { if (!cancelled) { setError(String(e)); setReply(null); } });
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="flex flex-col gap-4 p-4">
      <h2 className="text-sm font-semibold text-page-fg/70">
        {practiceStrings.deliverableTrackerTitle}
      </h2>

      {/* REQ-19 honesty line — always visible */}
      <p
        data-testid="deliverable-honesty-line"
        className="text-xs text-page-fg/50 italic border-l-2 border-accent/30 pl-2"
      >
        {practiceStrings.deliverableHonestyLine}
      </p>

      {reply === undefined && !error && (
        <p className="text-sm text-page-fg/50">{practiceStrings.loading}</p>
      )}

      {error && (
        <p className="text-xs text-red-600">{error}</p>
      )}

      {reply !== null && reply !== undefined && reply.deliverables.length === 0 && (
        <p className="text-sm text-page-fg/50">{practiceStrings.noProblems}</p>
      )}

      {reply !== null && reply !== undefined && reply.deliverables.map((d) => (
        <DeliverableCard key={d.id} deliverable={d} />
      ))}
    </div>
  );
}

/** Single deliverable card. */
function DeliverableCard({ deliverable: d }: { deliverable: Deliverable }) {
  return (
    <article
      data-testid="deliverable-card"
      className="rounded border border-border-strong p-4 flex flex-col gap-2"
    >
      <header className="flex items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold text-page-fg">{d.title_ro}</h3>
        <span className="text-xs font-mono text-page-fg/50">{d.subject}</span>
      </header>

      {/* Deadline (REQ-21: null → "necunoscut") */}
      <p data-testid="deliverable-deadline" className="text-xs text-page-fg/60">
        Termen:{" "}
        <span className="font-semibold">
          {d.deadline ?? practiceStrings.deliverableDeadlineUnknown}
        </span>
      </p>

      {/* Per-sub-problem points */}
      {d.sub_problems.length > 0 && (
        <ul
          data-testid="deliverable-points"
          className="flex flex-col gap-0.5 text-xs text-page-fg/70"
        >
          {d.sub_problems.map((sp, i) => (
            <li key={i} className="flex justify-between">
              <span>{sp.label_ro}</span>
              <span className="font-mono font-semibold">{sp.points} pt</span>
            </li>
          ))}
        </ul>
      )}

      {/* Prep drills (REQ-18: links) */}
      <div data-testid="deliverable-prep-drills" className="text-xs text-page-fg/60">
        <span className="font-semibold">{practiceStrings.deliverablePrepDrillsLabel}</span>
        {d.prep_drill_ids.length === 0 ? (
          <span className="ml-1">{practiceStrings.deliverableNoPrepDrills}</span>
        ) : (
          <ul className="mt-0.5 list-disc pl-4">
            {d.prep_drill_ids.map((id) => (
              <li key={id}>
                <a
                  href={`/tutor/practice/proof/${id}`}
                  className="underline text-accent hover:text-accent/70"
                >
                  {id}
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </article>
  );
}
