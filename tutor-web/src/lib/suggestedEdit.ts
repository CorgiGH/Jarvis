/**
 * Tutor Layer B0 — suggested-edit envelope.
 *
 * The LLM emits typed JSON envelopes inside chat replies that the
 * frontend renders as actionable cards (APPLY / REJECT). The wire
 * format is intentionally tiny so future card types can be added
 * without protocol churn.
 *
 * Envelope syntax in chat reply:
 *   <edit>{"id":"...","type":"clipboard","payload":"...","status":"pending"}</edit>
 *
 * Multiple envelopes per reply are allowed. They are extracted out of
 * the visible text; the remaining prose is rendered as the chat
 * message body. Malformed envelopes are skipped silently (logged).
 *
 * v0 supports type=clipboard only (APPLY → navigator.clipboard.writeText).
 * v1 will add type=apply_edit (server forwards to daemon → keystroke
 * injection, gated by trust grant + shadow-git pre-commit).
 */

export type SuggestedEditType = "clipboard" | "apply_edit" | "run";
export type SuggestedEditStatus = "pending" | "applied" | "rejected" | "failed";

export interface SuggestedEdit {
  id: string;
  type: SuggestedEditType;
  /** Free-form payload — for clipboard, the literal text to copy.
   *  For apply_edit (Layer B1) it'll be an ApplyEditRequest. */
  payload: string;
  status: SuggestedEditStatus;
  /** Optional one-line label rendered on the card. */
  label?: string;
  /** Optional language hint for syntax-highlighting future versions. */
  lang?: string;
}

export interface ParsedReply {
  /** Chat reply text with all <edit>...</edit> envelopes removed. */
  body: string;
  /** Extracted, well-formed envelopes in document order. */
  edits: SuggestedEdit[];
}

const ENVELOPE = /<edit>\s*([\s\S]*?)\s*<\/edit>/g;

export function parseSuggestedEdits(reply: string): ParsedReply {
  const edits: SuggestedEdit[] = [];
  let body = reply;
  let m: RegExpExecArray | null;
  ENVELOPE.lastIndex = 0;
  while ((m = ENVELOPE.exec(reply)) !== null) {
    try {
      const parsed = JSON.parse(m[1]) as Partial<SuggestedEdit>;
      if (
        typeof parsed.id === "string" && parsed.id.length > 0 &&
        typeof parsed.payload === "string" &&
        (parsed.type === "clipboard" || parsed.type === "apply_edit" || parsed.type === "run")
      ) {
        edits.push({
          id: parsed.id,
          type: parsed.type,
          payload: parsed.payload,
          status: parsed.status ?? "pending",
          label: parsed.label,
          lang: parsed.lang,
        });
      }
    } catch {
      // Skip malformed envelope; leave the raw <edit>...</edit> in the body.
      continue;
    }
  }
  // Strip ALL well-formed envelopes from the body, even if some were
  // malformed (consistent rendering trumps preserving partial JSON).
  body = reply.replace(ENVELOPE, "").replace(/\n{3,}/g, "\n\n").trim();
  return { body, edits };
}

export async function applyClipboard(edit: SuggestedEdit): Promise<void> {
  if (edit.type !== "clipboard") {
    throw new Error(`applyClipboard called with type=${edit.type}`);
  }
  if (typeof navigator === "undefined" || !navigator.clipboard?.writeText) {
    throw new Error("clipboard API unavailable");
  }
  await navigator.clipboard.writeText(edit.payload);
}
