import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { jarvisFetch } from "../lib/api";
import { formatEnum } from "../lib/formatEnum";

interface Gap {
  id: string;
  topic: string;
  taskId: string | null;
  type: string;
  reusedCount: number;
  resolvedBy: string | null;
}

/**
 * Phase 7.2: Knowledge Ledger drawer. Right-edge slide-in lists every
 * gap recorded for the current user with status badges + reuse count.
 * Filter by status (all / open / resolved). Per wiki [[Recognition
 * Over Recall]] + [[F-Pattern]]: dominant column = topic title.
 */
export function KnowledgeLedger({ onClose }: { onClose: () => void }) {
  const [gaps, setGaps] = useState<Gap[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "open" | "resolved">("all");
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    function fetchGaps() {
      setLoadError(null);
      jarvisFetch("/api/v1/gaps")
        .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
        .then((d: { gaps: Gap[] }) => { if (!cancelled) setGaps(d.gaps ?? []); })
        .catch((e: Error) => {
          if (!cancelled) {
            setGaps([]);
            setLoadError(e.message);
          }
        })
        .finally(() => { if (!cancelled) setLoaded(true); });
    }
    fetchGaps();
    function onGapEvent() { fetchGaps(); }
    window.addEventListener("jarvis:gap-created", onGapEvent);
    window.addEventListener("jarvis:gap-resolved", onGapEvent);
    return () => {
      cancelled = true;
      window.removeEventListener("jarvis:gap-created", onGapEvent);
      window.removeEventListener("jarvis:gap-resolved", onGapEvent);
    };
  }, []);

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

  const filtered = gaps
    .filter(g => {
      if (filter === "all") return true;
      if (filter === "open") return g.resolvedBy == null;
      return g.resolvedBy != null;
    })
    // Top by reusedCount (DESC). Cap rendered count at LEDGER_VISIBLE_CAP to
    // honor [[Chunking]] / [[Hick's Law]] on dense workspaces.
    .sort((a, b) => b.reusedCount - a.reusedCount);

  const LEDGER_VISIBLE_CAP = 50;
  const visible = filtered.slice(0, LEDGER_VISIBLE_CAP);
  const hiddenCount = filtered.length - visible.length;

  return (
    <>
      <div data-testid="knowledge-ledger-backdrop"
           onClick={onClose}
           aria-hidden="true"
           className="fixed inset-0 bg-page-fg/20 z-[19]" />
      <div data-testid="knowledge-ledger"
           role="dialog"
           aria-modal="true"
           aria-labelledby="ledger-heading"
           className="fixed top-0 right-0 h-full w-96 bg-page-bg border-l-4 border-border-strong p-4 font-mono text-xs overflow-auto z-20">
      <div className="flex justify-between items-center mb-3">
        <h2 id="ledger-heading" className="font-bold tracking-widest text-xs">KNOWLEDGE LEDGER</h2>
        <button ref={closeBtnRef}
                onClick={onClose}
                aria-label="Close ledger"
                className="bg-accent text-page-fg px-2 py-2 sm:py-1">×</button>
      </div>
      <div className="flex gap-1 mb-3">
        {(["all", "open", "resolved"] as const).map(f => (
          <button key={f} data-testid={`ledger-filter-${f}`}
                  onClick={() => setFilter(f)}
                  aria-pressed={filter === f}
                  className={`px-2 py-2 sm:py-1 border ${filter === f ? "bg-accent text-page-fg" : "bg-page-bg text-page-fg/70 border-border-thin"}`}>
            {f.toUpperCase()}
          </button>
        ))}
      </div>
      {!loaded ? (
        <div className="text-page-fg/60">loading…</div>
      ) : loadError ? (
        <div data-testid="ledger-load-error" className="text-danger-text">
          couldn't load gaps — {loadError}
        </div>
      ) : filtered.length === 0 ? (
        <div data-testid="ledger-empty" className="text-page-fg/60">no gaps yet</div>
      ) : (
        <>
          <ul role="list" className="space-y-2">
            {visible.map(g => {
              const inner = (
                <>
                  <div className="font-bold">{g.topic}</div>
                  <div className="text-page-fg/60">
                    {formatEnum(g.type)} · reused {g.reusedCount}× · {g.resolvedBy ? formatEnum(g.resolvedBy) : "open"}
                  </div>
                </>
              );
              return (
                <li key={g.id} data-testid="ledger-row">
                  {g.taskId ? (
                    <button
                      data-testid="ledger-row-open"
                      onClick={() => { navigate(`/?taskId=${g.taskId}`); onClose(); }}
                      aria-label={`Open source task for gap ${g.topic}`}
                      className="w-full text-left p-2 hover:bg-accent-soft border border-border-thin"
                    >
                      {inner}
                    </button>
                  ) : inner}
                </li>
              );
            })}
          </ul>
          {hiddenCount > 0 && (
            <div data-testid="ledger-cap-notice" className="mt-3 text-page-fg/60 italic">
              showing top {LEDGER_VISIBLE_CAP} of {filtered.length} by reuse count · {hiddenCount} more hidden
            </div>
          )}
        </>
      )}
      </div>
    </>
  );
}
