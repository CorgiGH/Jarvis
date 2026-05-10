import type { ReactNode } from "react";
import { buildSidekickEnvelope } from "../lib/inlineAsk";
import type { SidekickEnvelope } from "../lib/inlineAsk";

interface AskGutterProps {
  paragraphText: string;
  context: Omit<SidekickEnvelope, "user_question" | "anchor_text">;
  onAsk: (env: SidekickEnvelope) => void;
  children: ReactNode;
}

export function AskGutter({ paragraphText, context, onAsk, children }: AskGutterProps) {
  function handleAsk() {
    const env = buildSidekickEnvelope({
      taskId: context.task_id,
      problemId: context.problem_id,
      cardId: context.card_id,
      cardTitle: context.card_title,
      anchorId: context.anchor_id,
      anchorText: paragraphText,
      selection: context.selection,
      userQuestion: "",
    });
    onAsk(env);
  }

  return (
    <div className="askable" style={{ position: "relative", display: "flex", alignItems: "flex-start", gap: 0 }}>
      <button
        className="ask-gutter-btn"
        aria-label="Ask sidekick about this paragraph"
        onClick={handleAsk}
        style={{
          position: "absolute",
          left: "-28px",
          top: "2px",
          width: "22px",
          height: "22px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--color-accent-soft, #fffae6)",
          border: "2px solid var(--color-border-strong, #0a0a0a)",
          borderRadius: 0,
          fontFamily: "monospace",
          fontSize: "12px",
          fontWeight: 700,
          cursor: "pointer",
          opacity: 0,
          transition: "opacity 120ms ease-out",
          flexShrink: 0,
        }}
      >
        ?
      </button>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
}
