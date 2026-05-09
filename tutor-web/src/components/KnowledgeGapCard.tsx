import { useState } from "react";
import type { KnowledgeGap, GapResolved } from "../lib/knowledgeGap";

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

  function insert() {
    onInsertScratchpad?.(gap);
  }
  function markResolved() {
    setResolved("USER_MARKED_DONE");
    onResolve?.(gap.id, "USER_MARKED_DONE");
  }
  function dismiss() {
    setResolved("USER_DISMISSED");
    onResolve?.(gap.id, "USER_DISMISSED");
  }

  const previewCode = gap.exampleCode && gap.exampleCode.length > 320
    ? gap.exampleCode.slice(0, 320) + "…"
    : gap.exampleCode;

  return (
    <div data-testid="knowledge-gap-card" data-gap-id={gap.id}
         className="border-l-4 border-yellow-500 bg-yellow-50 p-3 my-2">
      <div className="flex items-center justify-between mb-1">
        <div className="text-xs font-bold tracking-widest text-black/70">
          GAP · {gap.type} · {gap.topic}
          {gap.language ? ` · ${gap.language}` : ""}
        </div>
        <div data-testid="knowledge-gap-status" className="text-xs text-black/60">
          {resolved ?? "open"}
        </div>
      </div>
      {gap.sourceCitation && (
        <div data-testid="knowledge-gap-citation" className="text-xs italic text-black/60 mb-2">
          source: {gap.sourceCitation}
        </div>
      )}
      <div className="text-sm leading-relaxed mb-2 whitespace-pre-wrap">
        {gap.content}
      </div>
      {previewCode && (
        <pre data-testid="knowledge-gap-code"
             className="text-xs whitespace-pre-wrap font-mono bg-white px-2 py-1 border border-black/10 max-h-48 overflow-auto">
          {previewCode}
        </pre>
      )}
      {!resolved && (
        <div className="flex gap-2 mt-2 flex-wrap">
          <button onClick={insert} data-testid="knowledge-gap-insert"
                  className="text-xs font-bold tracking-widest bg-black text-yellow-300 px-3 py-1">
            INSERT → SCRATCHPAD
          </button>
          <button onClick={markResolved} data-testid="knowledge-gap-resolve"
                  className="text-xs font-bold tracking-widest bg-white text-black px-3 py-1 border border-black">
            MARK RESOLVED
          </button>
          <button onClick={dismiss} data-testid="knowledge-gap-dismiss"
                  className="text-xs tracking-widest bg-white text-black/60 px-3 py-1 border border-black/30">
            FLAG WRONG
          </button>
        </div>
      )}
    </div>
  );
}
