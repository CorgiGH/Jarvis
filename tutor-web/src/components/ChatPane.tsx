import { useEffect, useRef, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { ScreenshotCapture, type ScreenshotEvent } from "./ScreenshotCapture";
import { SuggestedEditCard } from "./SuggestedEditCard";
import { parseSuggestedEdits, type SuggestedEdit } from "../lib/suggestedEdit";
import { KnowledgeGapCard } from "./KnowledgeGapCard";
import { parseKnowledgeGaps, type KnowledgeGap } from "../lib/knowledgeGap";
import { MathText } from "./MathText";
import { ChipRow } from "./ChipRow";
import { parseChips, type QuickChip } from "../lib/chip";
import { parseConcepts, type ConceptRef } from "../lib/conceptEnvelope";
import { ConceptInline } from "./ConceptInline";
import { parsePlotly, type PlotlyBlock } from "../lib/plotlyParse";
import { PlotlyEmbed } from "./PlotlyEmbed";
import { chatPane as S } from "../lib/chromeStrings";

interface Msg {
  role: "you" | "jarvis" | "sensor";
  text: string;
  edits?: SuggestedEdit[];
  gaps?: KnowledgeGap[];
  chips?: QuickChip[];
  concepts?: ConceptRef[];
  plots?: PlotlyBlock[];
}

export interface ChatPaneProps {
  taskId: string;
  onScratchpadInsert?: (text: string) => void;
}

export function ChatPane({ taskId, onScratchpadInsert }: ChatPaneProps) {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [historicalGaps, setHistoricalGaps] = useState<KnowledgeGap[]>([]);
  const [historicalGapsRefreshing, setHistoricalGapsRefreshing] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  // Phase 4.1c: GET historical gaps for the active task on mount + on
  // jarvis:gap-created event (fired by PdfPane selection or anything
  // else that POSTs a gap directly without going through this chat).
  useEffect(() => {
    let cancelled = false;
    function fetchGaps() {
      setHistoricalGapsRefreshing(true);
      jarvisFetch(`/api/v1/gaps?taskId=${encodeURIComponent(taskId)}`)
        .then(r => r.ok ? r.json() : { gaps: [] })
        .then((data: { gaps: any[] }) => {
          if (cancelled) return;
          setHistoricalGaps((data.gaps ?? []).map(g => ({
            id: g.id, topic: g.topic, language: g.language, type: g.type,
            trigger: g.trigger, content: g.content, exampleCode: g.exampleCode,
            sourceCitation: g.sourceCitation,
          })));
        })
        .catch(() => {})
        .finally(() => { if (!cancelled) setHistoricalGapsRefreshing(false); });
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
  }, [taskId]);

  function pickChip(prompt: string) {
    setInput(prompt);
    inputRef.current?.focus();
  }

  // Render assistant prose with CONCEPT/PLOTLY sentinels expanded into
  // their components. Text parts pipe through MathText so KaTeX still
  // renders. Concept + plotly sentinels never coexist with math inside
  // the same word, so split-on-pattern is safe.
  function renderJarvisProse(text: string, concepts: ConceptRef[] = [], plots: PlotlyBlock[] = []) {
    const parts = text.split(/(CONCEPT\d+|PLOTLY\d+)/);
    return parts.map((p, i) => {
      const cm = /^CONCEPT(\d+)$/.exec(p);
      if (cm) {
        const c = concepts[parseInt(cm[1], 10)];
        return c ? <ConceptInline key={`c-${i}`} name={c.name} /> : null;
      }
      const pm = /^PLOTLY(\d+)$/.exec(p);
      if (pm) {
        const idx = parseInt(pm[1], 10);
        const pl = plots[idx];
        return pl ? <PlotlyEmbed key={`p-${i}`} figure={pl.json} indexLabel={`FIG ${idx + 1}`} /> : null;
      }
      if (!p) return null;
      return <MathText key={`t-${i}`} text={p} className="text-sm leading-relaxed mt-1 break-words" />;
    });
  }
  // Layer B0 read-only mode latches on the most recent screenshot's
  // server-side classification. Until the next screenshot reclassifies
  // (presumably user is back in their editor), suggested-edit APPLY
  // stays disabled. This is a soft latch — REJECT still works.
  const [readOnly, setReadOnly] = useState(false);
  const [readOnlyReason, setReadOnlyReason] = useState<string>("");

  function onScreenshot(e: ScreenshotEvent) {
    setReadOnly(Boolean(e.readOnlyMode));
    setReadOnlyReason(e.readOnlyReason ?? "");
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
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    try {
      const res = await jarvisFetch("/api/chat", {
        method: "POST",
        body: JSON.stringify({ msg: userMsg, taskId }),
        signal: ac.signal,
      });
      if (!res.ok) {
        const bodyPreview = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status}${bodyPreview ? `: ${bodyPreview.slice(0, 200)}` : ""}`);
      }
      const data = await res.json();
      const raw = data.reply ?? "(no reply)";
      // Run both extractors. parseSuggestedEdits strips <edit> envelopes
      // first; parseKnowledgeGaps then strips <gap> envelopes from what's
      // left. Both produce plain prose body for the message text.
      const editParsed = parseSuggestedEdits(raw);
      const gapParsed = parseKnowledgeGaps(editParsed.body);
      const chipParsed = parseChips(gapParsed.body);
      const conceptParsed = parseConcepts(chipParsed.body);
      const plotlyParsed = parsePlotly(conceptParsed.body);
      setMessages(m => [...m, {
        role: "jarvis",
        text: plotlyParsed.body,
        edits: editParsed.edits,
        gaps: gapParsed.gaps,
        chips: chipParsed.chips,
        concepts: conceptParsed.concepts,
        plots: plotlyParsed.plots,
      }]);
      // Phase 4.1c: persist each parsed gap to the server. Best-effort —
      // local card already renders so a network failure doesn't block UX.
      gapParsed.gaps.forEach(g => {
        jarvisFetch("/api/v1/gap", {
          method: "POST",
          body: JSON.stringify({
            topic: g.topic,
            language: g.language,
            type: g.type,
            trigger: g.trigger,
            content: g.content,
            exampleCode: g.exampleCode,
            sourceCitation: g.sourceCitation,
            taskId,
          }),
        }).catch(() => {});
      });
    } catch (e) {
      if ((e as Error).name === "AbortError") return;
      setMessages(m => [...m, { role: "jarvis", text: `(error: ${(e as Error).message})` }]);
    } finally {
      if (abortRef.current === ac) {
        setSending(false);
        abortRef.current = null;
      }
    }
  }

  return (
    <div data-testid="chat-pane" className="h-full flex flex-col bg-page-bg font-mono min-w-0">
      <div className="bg-panel-dark-bg text-panel-dark-fg px-4 py-2 text-sm tracking-widest font-bold flex items-center justify-between">
        <span>{S.headingPrefix} {taskId}</span>
        {readOnly && (
          <span data-testid="read-only-badge"
                className="bg-danger-bg text-danger-fg px-2 py-0.5 text-xs"
                title={readOnlyReason}>
            {S.readOnlyBadge}
          </span>
        )}
      </div>
      <ScreenshotCapture taskId={taskId} onResult={onScreenshot} />
      <div className="prose-clamp flex-1 min-h-0 overflow-auto p-4 space-y-3"
           tabIndex={0}
           role="log"
           aria-label="Chat messages">
        {(() => {
          const openGaps = historicalGaps.filter(g => g.resolvedBy == null);
          return openGaps.length > 0 && (
            <div data-testid="historical-gaps" className={`mb-2 ${historicalGapsRefreshing ? "opacity-60" : ""}`}>
              <div className="text-xs font-bold tracking-widest text-page-fg/80 mb-1 flex items-center gap-2">
                <span>{S.previouslyFlagged(openGaps.length)}</span>
                {historicalGapsRefreshing && (
                  <span data-testid="historical-gaps-refreshing" className="text-page-fg/80 font-normal" aria-live="polite">{S.refreshing}</span>
                )}
              </div>
              {openGaps.map(g => (
                <KnowledgeGapCard key={g.id} gap={g}
                  onInsertScratchpad={gg => {
                    const text = gg.exampleCode
                      ? `// ${gg.topic}\n${gg.exampleCode}`
                      : `// ${gg.topic}\n${gg.content}`;
                    onScratchpadInsert?.(text);
                  }} />
              ))}
            </div>
          );
        })()}
        {messages.map((m, i) => (
          <div key={i} className="min-w-0">
            <div className={`inline-block px-2 py-0.5 text-xs font-bold tracking-widest ${
              m.role === "you" ? "bg-accent text-page-fg"
              : m.role === "sensor" ? "bg-info-bg text-page-fg"
              : "bg-panel-dark-bg text-page-bg"
            }`}>
              {m.role.toUpperCase()}
            </div>
            {m.role === "jarvis"
              ? <div className="mt-1">{renderJarvisProse(m.text, m.concepts, m.plots)}</div>
              : <div className="text-sm leading-relaxed mt-1 whitespace-pre-wrap break-words">{m.text}</div>}
            {m.edits?.map(edit => (
              <SuggestedEditCard
                key={edit.id}
                edit={edit}
                readOnly={readOnly}
                readOnlyReason={readOnlyReason}
              />
            ))}
            {m.gaps?.map(gap => (
              <KnowledgeGapCard
                key={gap.id}
                gap={gap}
                onInsertScratchpad={g => {
                  const text = g.exampleCode
                    ? `// ${g.topic}\n${g.exampleCode}`
                    : `// ${g.topic}\n${g.content}`;
                  onScratchpadInsert?.(text);
                }}
              />
            ))}
            {m.chips && <ChipRow chips={m.chips} onPick={pickChip} />}
          </div>
        ))}
      </div>
      <div className="flex border-t-4 border-border-strong">
        <label htmlFor="chat-input" className="sr-only">Message Jarvis</label>
        <input
          id="chat-input"
          ref={inputRef}
          className="flex-1 px-3 py-2 outline-none text-sm font-mono"
          placeholder={S.inputPlaceholder}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.ctrlKey && e.key === "Enter") send(); }}
        />
        <button
          className="bg-accent px-6 font-bold tracking-widest text-sm disabled:opacity-50 border-l-4 border-border-strong"
          onClick={send}
          disabled={sending}
        >
          {S.sendButton}
        </button>
      </div>
    </div>
  );
}
