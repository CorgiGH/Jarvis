import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";

interface DrawerHit { filename: string; snippet: string; }

/**
 * Phase 7.1: side drawer rendered when user clicks a <ConceptInline>.
 * Shows past gaps that mention the concept (proxy for "what does the
 * tutor remember about this term"). A richer corpus retriever path
 * would dispatch /api/v1/gap/{id}/search-docs but that requires a
 * gapId; concept clicks don't have one. Phase 7+ can add a dedicated
 * concept-search route if richer results are needed.
 */
export function ConceptDrawer({ concept, onClose }: { concept: string; onClose: () => void }) {
  const [hits, setHits] = useState<DrawerHit[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    jarvisFetch(`/api/v1/gaps`)
      .then(r => r.ok ? r.json() : { gaps: [] })
      .then((d: { gaps: any[] }) => {
        if (cancelled) return;
        const matches = (d.gaps ?? [])
          .filter(g => g.topic && g.topic.toLowerCase().includes(concept.toLowerCase()))
          .slice(0, 5);
        setHits(matches.map(g => ({
          filename: `gap:${g.id}`,
          snippet: g.content ?? "",
        })));
      })
      .catch(() => { if (!cancelled) setHits([]); });
    return () => { cancelled = true; };
  }, [concept]);

  return (
    <div data-testid="concept-drawer"
         role="dialog"
         aria-modal="true"
         aria-label={`Concept reference: ${concept}`}
         className="fixed top-0 right-0 h-full w-80 bg-page-bg border-l-4 border-border-strong p-4 font-mono text-xs overflow-auto z-30">
      <div className="flex justify-between items-center mb-3">
        <div className="font-bold tracking-widest">CONCEPT · {concept}</div>
        <button onClick={onClose}
                aria-label="Close concept drawer"
                className="bg-accent text-page-fg px-2 py-2 sm:py-1">×</button>
      </div>
      {hits == null ? (
        <div className="text-page-fg/60">loading…</div>
      ) : hits.length === 0 ? (
        <div className="text-page-fg/60">no past gaps mention this concept yet</div>
      ) : (
        <ul role="list" className="space-y-2">
          {hits.map((h, i) => (
            <li key={`${h.filename}-${i}`}>
              <div className="font-bold">{h.filename}</div>
              <div className="text-page-fg/70 whitespace-pre-wrap">{h.snippet}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
