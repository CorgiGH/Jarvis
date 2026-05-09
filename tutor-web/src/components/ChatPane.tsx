import { useState } from "react";
import { jarvisFetch } from "../lib/api";
import { ScreenshotCapture, type ScreenshotEvent } from "./ScreenshotCapture";
import { SuggestedEditCard } from "./SuggestedEditCard";
import { parseSuggestedEdits, type SuggestedEdit } from "../lib/suggestedEdit";

interface Msg {
  role: "you" | "jarvis" | "sensor";
  text: string;
  edits?: SuggestedEdit[];
}

export function ChatPane({ taskId }: { taskId: string }) {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);

  function onScreenshot(e: ScreenshotEvent) {
    const x = e.extracted;
    const lines: string[] = [`screenshot #${e.eventSeq}`];
    if (x.filePath) lines.push(`  file: ${x.filePath}`);
    if (x.cursor) lines.push(`  cursor: line ${x.cursor.line}, col ${x.cursor.col}`);
    if (x.consoleOutput) lines.push(`  console:\n${x.consoleOutput}`);
    if (x.error) lines.push(`  error:\n${x.error}`);
    if (lines.length === 1) lines.push("  (no editor / console / error visible)");
    setMessages(m => [...m, { role: "sensor", text: lines.join("\n") }]);
  }

  async function send() {
    if (!input.trim() || sending) return;
    const userMsg = input;
    setMessages(m => [...m, { role: "you", text: userMsg }]);
    setInput("");
    setSending(true);
    try {
      const res = await jarvisFetch("/api/chat", {
        method: "POST",
        body: JSON.stringify({ taskId, message: userMsg }),
      });
      const data = await res.json();
      const raw = data.reply ?? "(no reply)";
      const { body, edits } = parseSuggestedEdits(raw);
      setMessages(m => [...m, { role: "jarvis", text: body, edits }]);
    } catch (e) {
      setMessages(m => [...m, { role: "jarvis", text: `(error: ${(e as Error).message})` }]);
    } finally {
      setSending(false);
    }
  }

  return (
    <div data-testid="chat-pane" className="h-full flex flex-col bg-white font-mono">
      <div className="bg-black text-yellow-300 px-4 py-2 text-sm tracking-widest font-bold">
        JARVIS · TASK {taskId}
      </div>
      <ScreenshotCapture taskId={taskId} onResult={onScreenshot} />
      <div className="flex-1 overflow-auto p-4 space-y-3">
        {messages.map((m, i) => (
          <div key={i}>
            <div className={`inline-block px-2 py-0.5 text-xs font-bold tracking-widest ${
              m.role === "you" ? "bg-yellow-300 text-black"
              : m.role === "sensor" ? "bg-blue-200 text-black"
              : "bg-black text-white"
            }`}>
              {m.role.toUpperCase()}
            </div>
            <div className="text-sm leading-relaxed mt-1 whitespace-pre-wrap">{m.text}</div>
            {m.edits?.map(edit => (
              <SuggestedEditCard key={edit.id} edit={edit} />
            ))}
          </div>
        ))}
      </div>
      <div className="flex border-t-4 border-black">
        <input
          className="flex-1 px-3 py-2 outline-none text-sm font-mono"
          placeholder="message · ctrl+enter sends"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.ctrlKey && e.key === "Enter") send(); }}
          disabled={sending}
        />
        <button
          className="bg-yellow-300 px-6 font-bold tracking-widest text-sm disabled:opacity-50 border-l-4 border-black"
          onClick={send}
          disabled={sending}
        >
          SEND
        </button>
      </div>
    </div>
  );
}
