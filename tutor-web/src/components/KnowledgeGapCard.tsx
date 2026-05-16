import { useState } from "react";
import type { KnowledgeGap, GapResolved } from "../lib/knowledgeGap";
import { jarvisFetch } from "../lib/api";

export interface KnowledgeGapCardProps {
  gap: KnowledgeGap;
  /** Called when the user clicks INSERT → SCRATCHPAD. Parent owns
   *  the scratchpad slot; we never write to the user's editor. */
  onInsertScratchpad?: (gap: KnowledgeGap) => void;
  /** Called when the user marks the gap resolved or dismisses it. */
  onResolve?: (id: string, by: GapResolved) => void;
}

/**
 * Layer B0 inline gap card.
 *
 * Render rules per spec §4 item 6:
 *  - yellow border (matches suggested-edit visual identity)
 *  - header w/ topic + source citation
 *  - body w/ markdown content + optional example code block
 *  - actions: INSERT → SCRATCHPAD, MARK RESOLVED, FLAG WRONG
 *
 * "INSERT → SCRATCHPAD (NOT user's editor — preserves generation
 * effect)" is the load-bearing constraint.
 */
export function KnowledgeGapCard({ gap, onInsertScratchpad, onResolve }: KnowledgeGapCardProps) {
  const [resolved, setResolved] = useState<GapResolved | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [contentExpanded, setContentExpanded] = useState(false);
  const [docsOpen, setDocsOpen] = useState(false);
  const [docs, setDocs] = useState<{ filename: string; snippet: string; lineRef: string | null }[] | null>(null);
  const [docsError, setDocsError] = useState<string | null>(null);
  const [promoted, setPromoted] = useState<string | null>(gap.fsrsCardId ?? null);
  const [promoting, setPromoting] = useState(false);

  async function promoteToFsrs() {
    if (promoted || promoting) return;
    setPromoting(true);
    try {
      const r = await jarvisFetch(`/api/v1/gap/${encodeURIComponent(gap.id)}/promote`, { method: "POST" });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const data = await r.json();
      setPromoted(data.cardId);
    } catch (e) {
      setSyncError(`promote failed: ${(e as Error).message}`);
    } finally {
      setPromoting(false);
    }
  }

  async function loadDocs() {
    if (docs != null) { setDocsOpen(o => !o); return; }
    setDocsError(null);
    try {
      const r = await jarvisFetch(`/api/v1/gap/${encodeURIComponent(gap.id)}/search-docs`, { method: "POST" });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const data = await r.json();
      setDocs(data.results ?? []);
      setDocsOpen(true);
    } catch (e) {
      setDocsError((e as Error).message);
    }
  }

  async function postStatus(status: GapResolved) {
    setSyncError(null);
    try {
      const r = await jarvisFetch(`/api/v1/gap/${encodeURIComponent(gap.id)}/status`, {
        method: "POST",
        body: JSON.stringify({ status }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      // Broadcast to any open rail (ChatPane historicalGaps, KnowledgeLedger
      // open-filter) so they can re-fetch and reflect the new status without
      // waiting for remount. Per Phase 4 [4] + Phase 7 [7] backlog.
      window.dispatchEvent(new CustomEvent("jarvis:gap-resolved", {
        detail: { id: gap.id, status },
      }));
    } catch (e) {
      setSyncError((e as Error).message);
    }
  }

  function insert() {
    onInsertScratchpad?.(gap);
  }
  function markResolved() {
    setResolved("USER_MARKED_DONE");
    onResolve?.(gap.id, "USER_MARKED_DONE");
    postStatus("USER_MARKED_DONE");
  }
  function dismiss() {
    setResolved("USER_DISMISSED");
    onResolve?.(gap.id, "USER_DISMISSED");
    postStatus("USER_DISMISSED");
  }

  const previewCode = gap.exampleCode && gap.exampleCode.length > 320
    ? gap.exampleCode.slice(0, 320) + "…"
    : gap.exampleCode;

  return (
    <div data-testid="knowledge-gap-card" data-gap-id={gap.id}
         className="border-l-4 border-accent-rule bg-accent-soft p-3 my-2">
      <div className="flex items-center justify-between mb-1">
        <div className="text-xs font-bold tracking-widest text-page-fg/70">
          GAP · {gap.type} · {gap.topic}
          {gap.language ? ` · ${gap.language}` : ""}
        </div>
        <div data-testid="knowledge-gap-status" className="text-xs text-page-fg/60">
          {resolved ?? "open"}
        </div>
      </div>
      {gap.sourceCitation && (
        <div data-testid="knowledge-gap-citation" className="text-xs italic text-page-fg/60 mb-2">
          source: {gap.sourceCitation}
        </div>
      )}
      {gap.content.length > 240 && !contentExpanded ? (
        <>
          <div className="text-sm leading-relaxed mb-1 whitespace-pre-wrap">
            {gap.content.slice(0, 240)}…
          </div>
          <button onClick={() => setContentExpanded(true)}
                  data-testid="knowledge-gap-show-more"
                  className="text-xs underline text-page-fg/60 mb-2">show more</button>
        </>
      ) : (
        <div className="text-sm leading-relaxed mb-2 whitespace-pre-wrap">
          {gap.content}
        </div>
      )}
      {previewCode && (
        <pre data-testid="knowledge-gap-code"
             tabIndex={0}
             aria-label="Example code snippet"
             className="text-xs whitespace-pre-wrap font-mono bg-page-bg px-2 py-1 border border-page-fg/10 max-h-48 overflow-auto">
          {previewCode}
        </pre>
      )}
      {!resolved && (
        <div className="flex gap-2 mt-2 flex-wrap">
          <button onClick={insert} data-testid="knowledge-gap-insert"
                  className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-2 sm:py-1">
            INSERT → SCRATCHPAD
          </button>
          <button onClick={markResolved} data-testid="knowledge-gap-resolve"
                  className="text-xs font-bold tracking-widest bg-page-bg text-page-fg px-3 py-2 sm:py-1 border border-border-strong">
            MARK RESOLVED
          </button>
          <button onClick={dismiss} data-testid="knowledge-gap-dismiss"
                  className="text-xs tracking-widest bg-page-bg text-page-fg/60 px-3 py-2 sm:py-1 border border-border-thin">
            FLAG WRONG
          </button>
          <button onClick={loadDocs} data-testid="knowledge-gap-show-docs"
                  className="text-xs tracking-widest bg-page-bg text-page-fg/80 px-3 py-2 sm:py-1 border border-border-thin">
            {docsOpen ? "HIDE DOCS" : "SHOW DOCS"}
          </button>
          {promoted ? (
            <span data-testid="knowledge-gap-promoted"
                  className="text-xs font-bold tracking-widest bg-page-bg text-page-fg/60 px-3 py-1 border border-border-thin">
              → FSRS ({promoted.slice(0, 6)})
            </span>
          ) : (
            <button onClick={promoteToFsrs} disabled={promoting}
                    data-testid="knowledge-gap-promote"
                    className="text-xs font-bold tracking-widest bg-accent text-page-fg px-3 py-2 sm:py-1 border border-border-strong disabled:opacity-50">
              {promoting ? "PROMOTING…" : "PROMOTE → FSRS"}
            </button>
          )}
        </div>
      )}
      {docsOpen && docs && (
        <div data-testid="knowledge-gap-docs" className="mt-2 border-t border-border-thin pt-2 space-y-1">
          {docs.length === 0 ? (
            <div className="text-xs text-page-fg/60">no docs found</div>
          ) : (
            docs.map((d, i) => (
              <div key={`${d.filename}-${i}`} className="text-xs">
                <div className="font-bold">{d.filename}{d.lineRef ? ` :${d.lineRef}` : ""}</div>
                <div className="text-page-fg/70 whitespace-pre-wrap">{d.snippet}</div>
              </div>
            ))
          )}
        </div>
      )}
      {docsError && (
        <div className="text-xs text-danger-text mt-1">docs load failed: {docsError}</div>
      )}
      {syncError && (
        <div data-testid="knowledge-gap-sync-error" className="text-xs text-danger-text mt-1 flex items-center gap-2 flex-wrap">
          <span>status sync failed: {syncError}</span>
          {resolved && (
            <button
              onClick={() => postStatus(resolved)}
              data-testid="knowledge-gap-sync-retry"
              className="text-xs font-bold tracking-widest bg-accent text-page-fg px-3 py-2 sm:py-1 border border-border-strong"
            >
              RETRY
            </button>
          )}
        </div>
      )}
    </div>
  );
}
