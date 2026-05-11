import { useEffect, useState } from "react";
import { askSidekick } from "../lib/sidekickContext";
import type { Citation } from "../lib/sidekickContext";
import type { SidekickEnvelope } from "../lib/inlineAsk";
import { CitationPill } from "./CitationPill";

interface SidekickProps {
  envelope?: SidekickEnvelope;
  onCitationClick?: (citation: Citation) => void;
}

type FetchState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "ok"; text: string; quotedContext: string | null; citations: Citation[] }
  | { status: "error" };

export function Sidekick({ envelope, onCitationClick }: SidekickProps) {
  const [expanded, setExpanded] = useState(false);
  const [fetchState, setFetchState] = useState<FetchState>({ status: "idle" });

  useEffect(() => {
    if (!envelope) return;
    setExpanded(true);
    setFetchState({ status: "loading" });

    let cancelled = false;
    askSidekick(envelope)
      .then((reply) => {
        if (cancelled) return;
        setFetchState({ status: "ok", text: reply.text, quotedContext: reply.quotedContext, citations: reply.citations ?? [] });
      })
      .catch(() => {
        if (!cancelled) setFetchState({ status: "error" });
      });

    return () => { cancelled = true; };
  }, [envelope]);

  const chevron = expanded ? "▲" : "▼";

  return (
    <div
      data-testid="sidekick-panel"
      data-expanded={String(expanded)}
      style={{
        borderTop: "4px solid var(--color-border-strong, #0a0a0a)",
        background: "var(--color-panel-dark-bg, #1a1a1a)",
        color: "var(--color-panel-dark-fg, #f5f5f5)",
        fontFamily: "monospace",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "6px 12px", borderBottom: expanded ? "2px solid var(--color-border-strong, #0a0a0a)" : "none" }}>
        <span style={{ fontSize: "11px", fontWeight: 700, letterSpacing: "0.1em" }}>SIDEKICK</span>
        <button
          aria-label={expanded ? "Collapse sidekick" : "Expand sidekick"}
          onClick={() => setExpanded((v) => !v)}
          style={{ background: "none", border: "none", color: "inherit", fontFamily: "monospace", fontSize: "12px", cursor: "pointer", padding: "0 4px" }}
        >
          {chevron}
        </button>
      </div>
      <div style={{ overflow: "hidden", maxHeight: expanded ? "600px" : "0", transition: "max-height 200ms ease-out" }}>
        <div style={{ padding: "10px 12px", fontSize: "13px", lineHeight: 1.6 }}>
          {fetchState.status === "idle" && <span style={{ opacity: 0.5 }}>Select text or click ? to ask the sidekick.</span>}
          {fetchState.status === "loading" && <span style={{ opacity: 0.7 }}>thinking…</span>}
          {fetchState.status === "error" && <span style={{ color: "var(--color-accent, #ffcc00)", opacity: 0.9 }}>(LLM unavailable; rate-limited?)</span>}
          {fetchState.status === "ok" && (
            <>
              {fetchState.quotedContext && (
                <div
                  data-testid="sidekick-quote"
                  className="sidekick-quote-pop-in"
                  style={{ borderLeft: "3px solid var(--color-accent, #ffcc00)", paddingLeft: "10px", marginBottom: "10px", fontSize: "12px", opacity: 0.85, fontStyle: "italic" }}
                >
                  {`> quoted: "${fetchState.quotedContext}"`}
                </div>
              )}
              <div data-testid="sidekick-reply" style={{ whiteSpace: "pre-wrap" }}>{fetchState.text}</div>
              {fetchState.citations.length > 0 && (
                <div
                  data-testid="sidekick-citations-strip"
                  style={{ marginTop: "10px", display: "flex", flexWrap: "wrap" }}
                >
                  {fetchState.citations.map((c, i) => (
                    <CitationPill key={i} citation={c} onClick={(cit) => onCitationClick?.(cit)} />
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
