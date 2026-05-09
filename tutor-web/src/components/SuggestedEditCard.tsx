import { useState } from "react";
import { applyClipboard, type SuggestedEdit } from "../lib/suggestedEdit";

export interface SuggestedEditCardProps {
  edit: SuggestedEdit;
  onStatusChange?: (id: string, status: SuggestedEdit["status"]) => void;
}

/**
 * Tutor Layer B0 — visible "I have a suggestion" card inline in chat.
 *
 * Council R3: "preserves generation effect" → APPLY puts the edit in
 * the user's clipboard so they have to consciously paste it; we
 * never type into the user's editor in B0. Daemon path (B1) will add
 * APPLY → keystroke injection but only behind an explicit trust grant.
 */
export function SuggestedEditCard({ edit, onStatusChange }: SuggestedEditCardProps) {
  const [status, setStatus] = useState(edit.status);
  const [error, setError] = useState<string | null>(null);

  async function apply() {
    setError(null);
    try {
      if (edit.type === "clipboard") {
        await applyClipboard(edit);
        setStatus("applied");
        onStatusChange?.(edit.id, "applied");
      } else {
        setStatus("failed");
        setError(`type ${edit.type} not supported in B0 (daemon path arrives in B1)`);
        onStatusChange?.(edit.id, "failed");
      }
    } catch (e) {
      setStatus("failed");
      setError((e as Error).message);
      onStatusChange?.(edit.id, "failed");
    }
  }

  function reject() {
    setStatus("rejected");
    onStatusChange?.(edit.id, "rejected");
  }

  const previewText = edit.payload.length > 240
    ? edit.payload.slice(0, 240) + "…"
    : edit.payload;

  return (
    <div data-testid="suggested-edit-card" data-edit-id={edit.id}
         className="border-l-4 border-yellow-400 bg-yellow-50 p-3 my-2">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs font-bold tracking-widest text-black/70">
          SUGGESTED · {edit.type.toUpperCase()}
          {edit.label ? ` · ${edit.label}` : ""}
        </div>
        <div data-testid="suggested-edit-status" className="text-xs text-black/60">
          {status}
        </div>
      </div>
      <pre className="text-xs whitespace-pre-wrap font-mono bg-white px-2 py-1 border border-black/10 max-h-48 overflow-auto">
        {previewText}
      </pre>
      {error && (
        <div data-testid="suggested-edit-error" className="text-xs text-red-700 mt-1">
          {error}
        </div>
      )}
      {status === "pending" && (
        <div className="flex gap-2 mt-2">
          <button onClick={apply} data-testid="suggested-edit-apply"
                  className="text-xs font-bold tracking-widest bg-black text-yellow-300 px-3 py-1">
            {edit.type === "clipboard" ? "COPY" : "APPLY"}
          </button>
          <button onClick={reject} data-testid="suggested-edit-reject"
                  className="text-xs font-bold tracking-widest bg-white text-black px-3 py-1 border border-black">
            REJECT
          </button>
        </div>
      )}
    </div>
  );
}
