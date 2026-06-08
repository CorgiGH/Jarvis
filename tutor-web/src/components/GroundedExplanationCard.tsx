import { useEffect, useState } from "react";
import { getTeaching } from "../lib/teaching";
import type { ApiTeachingReply } from "../lib/teaching";
import { MathText } from "./MathText";

interface GroundedExplanationCardProps {
  /** KC to fetch grounded teaching for. Sourced from QueueItem.kc_id on the
   *  queue/today path. UNDEFINED on the legacy task-prep path (the prep blob
   *  carries no kc id) → no fetch, renders nothing (honest no-op). */
  kcId: string | undefined;
}

/**
 * Faithful grounded teaching card. Fetches GET /api/v1/teaching/{kcId} on-demand.
 * Renders explanation_ro / worked_example_ro ONLY when the reply is present AND
 * provenance.hasBeenFaithfulChecked === true (double-gate: the backend already
 * 404s non-faithful KCs, the client re-checks the marker before painting).
 *
 * kcId SOURCE: the queue/today path (QueueItem.kc_id). The task-prep path carries
 * NO kc id, so on that path kcId is undefined and the card renders NOTHING — a
 * documented honest no-op, NOT a bug. (This is why grounded-explanation-card is a
 * queue-path Visual-Acceptance surface, asserted in the Task-10 unit test, not the
 * task-prep e2e.)
 */
export function GroundedExplanationCard({ kcId }: GroundedExplanationCardProps) {
  const [reply, setReply] = useState<ApiTeachingReply | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!kcId) { setReply(null); return; }
    getTeaching(kcId)
      .then((r) => { if (!cancelled) setReply(r); })
      .catch(() => { if (!cancelled) setReply(null); });
    return () => { cancelled = true; };
  }, [kcId]);

  if (!reply || !reply.provenance.hasBeenFaithfulChecked) return null;

  return (
    <section
      data-testid="grounded-explanation-card"
      data-content-block="grounded"
      data-faithful="true"
      className="mt-3 border-4 border-accent bg-page-bg font-mono"
    >
      <div className="px-3 py-1.5 border-b-4 border-accent bg-accent text-page-fg text-[10px] tracking-widest font-bold">
        EXPLICAȚIE · matches your lecture
      </div>
      <div className="px-4 py-3 flex flex-col gap-3">
        {reply.explanation_ro && (
          <MathText text={reply.explanation_ro} className="text-xs leading-relaxed" />
        )}
        {reply.worked_example_ro && (
          <div className="border-l-2 border-accent-rule pl-3">
            <div className="mb-1 text-[10px] tracking-widest text-page-fg/60">EXEMPLU REZOLVAT</div>
            <MathText text={reply.worked_example_ro} className="text-xs leading-relaxed" />
          </div>
        )}
      </div>
    </section>
  );
}
