import { useEffect, useState } from "react";
import { PdfPane } from "./PdfPane";
import { ChatPane } from "./ChatPane";
import { Scratchpad } from "./Scratchpad";
import { Sidebar } from "./Sidebar";

const SCRATCHPAD_KEY = "jarvis.scratchpad";

export function TutorWorkspace({ pdfUrl, taskId, dedupedNotice = false }: { pdfUrl: string; taskId: string; dedupedNotice?: boolean }) {
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

  function appendToScratchpad(text: string) {
    setScratch(prev => prev.length === 0 ? text : `${prev}\n\n${text}`);
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      {dedupedNotice && (
        <div data-testid="deduped-notice"
             className="bg-yellow-300 border-b-4 border-black text-black font-mono text-xs font-bold tracking-widest px-4 py-1.5">
          OPENED EXISTING TASK · same subject + title already on file
        </div>
      )}
      <div className="flex flex-1 min-h-0">
      <Sidebar activeTaskId={taskId} />
      <div className="flex h-full min-h-0 flex-1 flex-col sm:flex-row">
        <div className="flex flex-col h-full min-h-0 flex-1 min-w-0 sm:w-1/2 border-b-4 sm:border-b-0 sm:border-r-4 border-black">
          <div className="flex-1 min-h-0 overflow-hidden">
            <PdfPane url={pdfUrl} />
          </div>
          <Scratchpad value={scratch} onChange={setScratch} />
        </div>
        <div className="flex-1 min-w-0 min-h-0 sm:w-1/2 h-full">
          <ChatPane taskId={taskId} onScratchpadInsert={appendToScratchpad} />
        </div>
      </div>
      </div>
    </div>
  );
}
