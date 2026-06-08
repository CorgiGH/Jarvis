/** LedgerDrawer — right-edge 480px DARK slide-in drawer (surface 0h).
 *
 *  Wraps KnowledgeLedger-style gap list with the new typed LedgerRow component.
 *  Fetches GET /api/v1/gaps. Filters: all / open / resolved.
 *  Dismisses on Escape or backdrop click.
 *
 *  testids:
 *   ledger-drawer
 *   ledger-drawer-backdrop
 *   ledger-row-{id}              — from LedgerRow
 *   ledger-row-status-{id}       — from LedgerRow
 *   ledger-drawer-empty          — no gaps
 *   ledger-drawer-error          — fetch failed
 */
import { useEffect, useRef, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { LedgerRow } from "./LedgerRow";
import type { LedgerGap } from "./LedgerRow";

interface LedgerDrawerProps {
  onClose: () => void;
}

const LEDGER_CAP = 50;

export function LedgerDrawer({ onClose }: LedgerDrawerProps) {
  const [gaps, setGaps] = useState<LedgerGap[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "open" | "resolved">("all");
  const closeBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let cancelled = false;
    function fetchGaps() {
      setLoadError(null);
      jarvisFetch("/api/v1/gaps")
        .then((r) =>
          r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)),
        )
        .then((d: { gaps: LedgerGap[] }) => {
          if (!cancelled) setGaps(d.gaps ?? []);
        })
        .catch((e: Error) => {
          if (!cancelled) {
            setGaps([]);
            setLoadError(e.message);
          }
        })
        .finally(() => {
          if (!cancelled) setLoaded(true);
        });
    }
    fetchGaps();
    function onGapEvent() {
      fetchGaps();
    }
    window.addEventListener("jarvis:gap-created", onGapEvent);
    window.addEventListener("jarvis:gap-resolved", onGapEvent);
    return () => {
      cancelled = true;
      window.removeEventListener("jarvis:gap-created", onGapEvent);
      window.removeEventListener("jarvis:gap-resolved", onGapEvent);
    };
  }, []);

  // Focus trap + Escape dismiss
  useEffect(() => {
    closeBtnRef.current?.focus();
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const filtered = gaps
    .filter((g) => {
      if (filter === "open") return g.resolvedBy == null;
      if (filter === "resolved") return g.resolvedBy != null;
      return true;
    })
    .sort((a, b) => b.reusedCount - a.reusedCount)
    .slice(0, LEDGER_CAP);

  return (
    <>
      {/* Backdrop */}
      <div
        data-testid="ledger-drawer-backdrop"
        onClick={onClose}
        aria-hidden="true"
        className="fixed inset-0 bg-page-fg/20 z-[19]"
      />

      {/* Drawer panel — 480px DARK */}
      <div
        data-testid="ledger-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ledger-drawer-heading"
        className="fixed top-0 right-0 h-full w-[480px] bg-panel-dark-bg border-l-4 border-accent p-4 font-mono text-xs overflow-auto z-20 text-panel-dark-fg"
      >
        {/* Header */}
        <div className="flex justify-between items-center mb-3">
          <h2
            id="ledger-drawer-heading"
            className="font-bold tracking-widest text-xs uppercase"
          >
            Knowledge Ledger
          </h2>
          <button
            ref={closeBtnRef}
            onClick={onClose}
            aria-label="Închide ledger-ul"
            className="bg-accent text-page-fg px-2 py-1 text-xs"
          >
            ×
          </button>
        </div>

        {/* Filter tabs */}
        <div className="flex gap-1 mb-3">
          {(["all", "open", "resolved"] as const).map((f) => (
            <button
              key={f}
              data-testid={`ledger-filter-${f}`}
              onClick={() => setFilter(f)}
              aria-pressed={filter === f}
              className={`px-2 py-1 border text-[10px] tracking-widest uppercase ${
                filter === f
                  ? "bg-accent text-page-fg border-accent"
                  : "bg-transparent text-panel-dark-fg/70 border-border-thin"
              }`}
            >
              {f}
            </button>
          ))}
        </div>

        {/* Content */}
        {!loaded ? (
          <div className="text-panel-dark-fg/80 tracking-widest">
            Se încarcă…
          </div>
        ) : loadError ? (
          <div
            data-testid="ledger-drawer-error"
            className="text-red-400 tracking-widest"
          >
            Nu s-au putut încărca — {loadError}
          </div>
        ) : filtered.length === 0 ? (
          <div
            data-testid="ledger-drawer-empty"
            className="text-panel-dark-fg/60 tracking-widest"
          >
            Nicio lacună înregistrată.
          </div>
        ) : (
          <ul role="list" className="space-y-0">
            {filtered.map((g) => (
              <LedgerRow key={g.id} gap={g} onClose={onClose} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
