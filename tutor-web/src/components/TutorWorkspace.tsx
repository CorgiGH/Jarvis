import { useEffect, useRef, useState } from "react";
import { PdfPane } from "./PdfPane";
import { ChatPane } from "./ChatPane";
import { Scratchpad } from "./Scratchpad";
import { Sidebar } from "./Sidebar";
import { StatusBar } from "./StatusBar";
import { InlineAskChip } from "./InlineAskChip";
import { Sidekick } from "./Sidekick";
import { attachSelectionListener, buildSidekickEnvelope } from "../lib/inlineAsk";
import type { SidekickEnvelope } from "../lib/inlineAsk";
import { jarvisFetch } from "../lib/api";

const SCRATCHPAD_KEY = "jarvis.scratchpad";

export function TutorWorkspace({ pdfUrl, taskId, dedupedNotice = false }: { pdfUrl: string; taskId: string; dedupedNotice?: boolean }) {
  const workspaceRef = useRef<HTMLDivElement>(null);
  const [chipState, setChipState] = useState<{ rect: DOMRect; envelope: SidekickEnvelope } | null>(null);
  const [sidekickEnvelope, setSidekickEnvelope] = useState<SidekickEnvelope | undefined>(undefined);

  useEffect(() => {
    const root = workspaceRef.current;
    if (!root) return;
    const detach = attachSelectionListener(root, (selectedText, rect) => {
      const env = buildSidekickEnvelope({ taskId, selection: selectedText, userQuestion: selectedText });
      setChipState({ rect, envelope: env });
    });
    function handlePointerDown(e: PointerEvent) {
      const target = e.target as HTMLElement;
      if (!target.closest(".ask-chip-fade-in")) setChipState(null);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => {
      detach();
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [taskId]);

  // Layer B0: scratchpad state lives at the workspace level so chat-side
  // INSERT actions can append from across the divider. Persisted via
  // localStorage per browser; server-side per-task storage arrives in B1.
  const storageKey = `${SCRATCHPAD_KEY}:${taskId}`;
  const [scratch, setScratch] = useState<string>("");
  useEffect(() => {
    if (typeof localStorage === "undefined") return;
    const v = localStorage.getItem(storageKey);
    if (v != null) setScratch(v);
  }, [storageKey]);
  useEffect(() => {
    if (typeof localStorage === "undefined") return;
    localStorage.setItem(storageKey, scratch);
  }, [storageKey, scratch]);

  // Phase 3.4: server-persist. Fetch on task mount; server wins so a
  // different device's edits flow back. Subsequent local edits PUT
  // back via 500ms debounce. localStorage stays as offline cache so
  // the textarea isn't blank during the round-trip.
  useEffect(() => {
    let cancelled = false;
    jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`)
      .then(r => r.ok ? r.json() : null)
      .then((data: { text?: string } | null) => {
        if (cancelled || !data) return;
        setScratch(data.text ?? "");
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [taskId]);

  useEffect(() => {
    if (typeof scratch !== "string") return;
    const t = setTimeout(() => {
      jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`, {
        method: "PUT",
        body: JSON.stringify({ text: scratch }),
      }).catch(() => {});
    }, 500);
    return () => clearTimeout(t);
  }, [scratch, taskId]);

  function appendToScratchpad(text: string) {
    setScratch(prev => prev.length === 0 ? text : `${prev}\n\n${text}`);
  }

  // Phase 6.3b: fetch task detail to surface auto-attached materialPaths.
  const [materialPaths, setMaterialPaths] = useState<string[]>([]);
  useEffect(() => {
    let cancelled = false;
    jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}`)
      .then(r => r.ok ? r.json() : null)
      .then((d: { materialPaths?: string[] } | null) => {
        if (!cancelled && d?.materialPaths) setMaterialPaths(d.materialPaths);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [taskId]);

  // Phase 4.3: PdfPane selection-tooltip callback. POSTs gap directly + fires
  // window event so ChatPane re-fetches its historical-gaps list.
  async function emitPdfSelectionGap(selection: { text: string; page: number }) {
    try {
      await jarvisFetch("/api/v1/gap", {
        method: "POST",
        body: JSON.stringify({
          topic: selection.text,
          type: "CONCEPT",
          trigger: "EXPLICIT_ASK",
          content: selection.text,
          sourceCitation: `pdf:page=${selection.page}`,
          taskId,
        }),
      });
      window.dispatchEvent(new CustomEvent("jarvis:gap-created", { detail: { taskId } }));
    } catch (_) {}
  }

  return (
    <div ref={workspaceRef} className="flex h-full min-h-0 flex-col">
      {dedupedNotice && (
        <div data-testid="deduped-notice"
             role="status"
             aria-live="polite"
             className="bg-accent border-b-4 border-border-strong text-page-fg font-mono text-xs font-bold tracking-widest px-4 py-1.5">
          OPENED EXISTING TASK · same subject + title already on file
        </div>
      )}
      {materialPaths.length > 0 && (
        <div data-testid="reference-materials"
             className="border-b-4 border-border-strong bg-accent-soft px-4 py-2 font-mono text-xs">
          <div className="font-bold tracking-widest mb-1">REFERENCE MATERIALS ({materialPaths.length})</div>
          <ul role="list" className="space-y-0.5">
            {materialPaths.map((p, i) => (
              <li key={`${p}-${i}`} className="text-page-fg/80 truncate">{p}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="flex flex-1 min-h-0">
      <Sidebar activeTaskId={taskId} />
      <div className="flex h-full min-h-0 flex-1 flex-col md:flex-row">
        <div className="flex flex-col h-full min-h-0 flex-1 min-w-0 md:w-1/2 border-b-4 md:border-b-0 md:border-r-4 border-border-strong">
          <div className="flex-1 min-h-[50vh] md:min-h-0 overflow-hidden">
            <PdfPane url={pdfUrl} uploadUrl={pdfUrl} onPdfSelectionGap={emitPdfSelectionGap} />
          </div>
          <Scratchpad value={scratch} onChange={setScratch} />
        </div>
        <div className="flex-1 min-w-0 min-h-0 md:w-1/2 h-full flex flex-col">
          <ChatPane taskId={taskId} onScratchpadInsert={appendToScratchpad} />
          <Sidekick envelope={sidekickEnvelope} />
        </div>
      </div>
      </div>
      <StatusBar />
      {chipState && (
        <InlineAskChip
          selectionRect={chipState.rect}
          envelope={chipState.envelope}
          onAsk={(env) => { setSidekickEnvelope(env); setChipState(null); }}
        />
      )}
    </div>
  );
}
