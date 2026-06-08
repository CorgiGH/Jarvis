/** OggiScreen — /oggi home: queue-first study entry point.
 *
 *  Layout: 7fr queue list | 3fr next-KC mini-door panel.
 *  Calls getQueueToday on mount. CTRL+ENTER on the top item begins that KC.
 *
 *  testids: oggi-screen · oggi-queue-panel · oggi-next-kc-panel ·
 *           oggi-empty (empty queue) · oggi-error (getQueueToday threw)
 */
import { useEffect, useState } from "react";
import { getQueueToday } from "../lib/taskPrep";
import type { QueueItem } from "../lib/taskPrep";
import { LearnerQueueList } from "./LearnerQueueList";
import { MasterySparkline } from "./MasterySparkline";

type State =
  | { status: "loading" }
  | { status: "ok"; items: QueueItem[]; day: string }
  | { status: "empty" }
  | { status: "error"; message: string };

export function OggiScreen() {
  const [state, setState] = useState<State>({ status: "loading" });
  const [selected, setSelected] = useState<QueueItem | null>(null);

  useEffect(() => {
    let cancelled = false;
    getQueueToday()
      .then((res) => {
        if (cancelled) return;
        if (!res || res.items.length === 0) {
          setState({ status: "empty" });
        } else {
          setState({ status: "ok", items: res.items, day: res.day });
          setSelected(res.items[0]);
        }
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setState({
          status: "error",
          message: err instanceof Error ? err.message : String(err),
        });
      });
    return () => { cancelled = true; };
  }, []);

  const topItem = state.status === "ok" ? state.items[0] : null;

  function handleSelect(item: QueueItem) {
    setSelected(item);
    // Future: navigate to drill for this KC. For now just track selection.
  }

  return (
    <div data-testid="oggi-screen" className="flex flex-col h-full font-mono">
      {/* Header */}
      <div className="px-4 pt-4 pb-2 border-b border-border-strong flex items-baseline gap-3">
        <span className="text-xs font-bold tracking-widest uppercase text-page-fg/80">
          Azi
        </span>
        {state.status === "ok" && (
          <span className="text-[11px] text-page-fg/50 tracking-wider">
            {state.items.length} de studiat
          </span>
        )}
      </div>

      {/* Body */}
      {state.status === "loading" && (
        <div className="flex-1 p-6 text-xs text-page-fg/50 tracking-widest">
          Se încarcă…
        </div>
      )}

      {state.status === "empty" && (
        <div
          data-testid="oggi-empty"
          className="flex-1 p-6 text-xs text-page-fg/60 tracking-widest"
        >
          Ai terminat tot pentru azi.
        </div>
      )}

      {state.status === "error" && (
        <div
          data-testid="oggi-error"
          className="flex-1 p-6 text-xs text-red-400 tracking-widest"
        >
          Eroare la încărcare: {state.message}
        </div>
      )}

      {state.status === "ok" && (
        <div className="flex-1 flex overflow-hidden">
          {/* 7fr queue panel */}
          <div
            data-testid="oggi-queue-panel"
            className="overflow-y-auto border-r border-border-strong"
            style={{ flex: "7 1 0" }}
          >
            <LearnerQueueList
              items={state.items}
              onSelect={handleSelect}
            />
          </div>

          {/* 3fr next-KC mini-door panel */}
          <div
            data-testid="oggi-next-kc-panel"
            className="flex flex-col gap-3 p-4 overflow-y-auto"
            style={{ flex: "3 1 0" }}
          >
            {(selected ?? topItem) && (
              <NextKcPanel
                item={(selected ?? topItem)!}
                onBegin={() => handleSelect((selected ?? topItem)!)}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** Mini-door panel showing details about the next/selected KC. */
function NextKcPanel({ item, onBegin }: { item: QueueItem; onBegin: () => void }) {
  return (
    <div className="flex flex-col gap-3">
      {/* Subject pill */}
      <span className="self-start bg-accent text-page-fg px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider">
        {item.subject}
      </span>

      {/* KC name */}
      <p className="text-sm font-bold tracking-widest text-page-fg leading-tight">
        {item.kc_name_ro}
      </p>

      {/* Phase + mode */}
      <div className="flex items-center gap-2 text-[10px] tracking-wider text-page-fg/60 uppercase">
        <span className="border border-border-strong px-1">{item.phase}</span>
        <span className="border border-border-strong px-1">{item.mode}</span>
      </div>

      {/* Mastery band */}
      <MasterySparkline ewmaScore={item.mastery_ewma} />

      {/* Begin button */}
      <button
        onClick={onBegin}
        title="CTRL+ENTER"
        className="mt-2 bg-accent text-page-fg text-xs font-bold tracking-widest px-3 py-2 hover:bg-accent-hover uppercase"
      >
        Începe
      </button>
    </div>
  );
}
