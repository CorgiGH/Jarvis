import { useEffect, useRef, useState } from "react";
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const closeBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    jarvisFetch(`/api/v1/gaps`)
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
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
      .catch((e: Error) => {
        if (!cancelled) {
          setHits([]);
          setLoadError(e.message);
        }
      });
    return () => { cancelled = true; };
  }, [concept]);

  // Modal-dialog contract: focus close button on open, dismiss on Escape.
  // Backdrop overlay handles click-outside-to-dismiss (see JSX below).
  useEffect(() => {
    closeBtnRef.current?.focus();
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <>
      <div data-testid="concept-drawer-backdrop"
           onClick={onClose}
           aria-hidden="true"
           className="fixed inset-0 bg-page-fg/20 z-[29]" />
      <div data-testid="concept-drawer"
           role="dialog"
           aria-modal="true"
           aria-labelledby="concept-drawer-heading"
           aria-label={`Concept reference: ${concept}`}
           className="fixed top-0 right-0 h-full w-80 bg-page-bg border-l-4 border-border-strong p-4 font-mono text-xs overflow-auto z-30">
      <div className="flex justify-between items-center mb-3">
        <h2 id="concept-drawer-heading" className="font-bold tracking-widest text-xs">CONCEPT · {concept}</h2>
        <button ref={closeBtnRef}
                onClick={onClose}
                aria-label="Close concept drawer"
                className="bg-accent text-page-fg px-2 py-2 sm:py-1">×</button>
      </div>
      {hits == null ? (
        <div className="text-page-fg/80">loading…</div>
      ) : loadError ? (
        <div data-testid="concept-drawer-load-error" className="text-danger-text">
          couldn't load references — {loadError}
        </div>
      ) : hits.length === 0 ? (
        <div className="text-page-fg/80">no past gaps mention this concept yet</div>
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
    </>
  );
}
