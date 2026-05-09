import { useState } from "react";
import { jarvisFetch } from "../lib/api";

interface Msg { role: "you" | "jarvis"; text: string; }

export function ChatPane({ taskId }: { taskId: string }) {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);

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
      setMessages(m => [...m, { role: "jarvis", text: data.reply ?? "(no reply)" }]);
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
      <div className="flex-1 overflow-auto p-4 space-y-3">
        {messages.map((m, i) => (
          <div key={i}>
            <div className={`inline-block px-2 py-0.5 text-xs font-bold tracking-widest ${m.role === "you" ? "bg-yellow-300 text-black" : "bg-black text-white"}`}>
              {m.role.toUpperCase()}
            </div>
            <div className="text-sm leading-relaxed mt-1">{m.text}</div>
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
