import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";

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
  const [filter, setFilter] = useState<"all" | "open" | "resolved">("all");

  useEffect(() => {
    jarvisFetch("/api/v1/gaps")
      .then(r => r.ok ? r.json() : { gaps: [] })
      .then((d: { gaps: Gap[] }) => setGaps(d.gaps ?? []))
      .catch(() => setGaps([]))
      .finally(() => setLoaded(true));
  }, []);

  const filtered = gaps.filter(g => {
    if (filter === "all") return true;
    if (filter === "open") return g.resolvedBy == null;
    return g.resolvedBy != null;
  });

  return (
    <div data-testid="knowledge-ledger"
         role="dialog"
         aria-label="Knowledge ledger"
         className="fixed top-0 right-0 h-full w-96 bg-page-bg border-l-4 border-border-strong p-4 font-mono text-xs overflow-auto z-20">
      <div className="flex justify-between items-center mb-3">
        <div className="font-bold tracking-widest">KNOWLEDGE LEDGER</div>
        <button onClick={onClose}
                aria-label="Close ledger"
                className="bg-accent text-page-fg px-2 py-2 sm:py-1">×</button>
      </div>
      <div className="flex gap-1 mb-3">
        {(["all", "open", "resolved"] as const).map(f => (
          <button key={f} data-testid={`ledger-filter-${f}`}
                  onClick={() => setFilter(f)}
                  className={`px-2 py-2 sm:py-1 border ${filter === f ? "bg-accent text-page-fg" : "bg-page-bg text-page-fg/70 border-border-thin"}`}>
            {f.toUpperCase()}
          </button>
        ))}
      </div>
      {!loaded ? (
        <div className="text-page-fg/60">loading…</div>
      ) : filtered.length === 0 ? (
        <div data-testid="ledger-empty" className="text-page-fg/60">no gaps yet</div>
      ) : (
        <ul role="list" className="space-y-2">
          {filtered.map(g => (
            <li key={g.id} data-testid="ledger-row">
              <div className="font-bold">{g.topic}</div>
              <div className="text-page-fg/60">
                {g.type} · reused {g.reusedCount}× · {g.resolvedBy ?? "open"}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
