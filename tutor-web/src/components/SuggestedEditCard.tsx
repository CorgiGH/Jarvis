import { useState } from "react";
import { applyClipboard, type SuggestedEdit } from "../lib/suggestedEdit";
import { jarvisFetch } from "../lib/api";

export interface SuggestedEditCardProps {
  edit: SuggestedEdit;
  onStatusChange?: (id: string, status: SuggestedEdit["status"]) => void;
  /** Layer B0 — when true, APPLY/COPY is disabled with a tooltip
   *  pointing at the read-only badge. REJECT remains available so
   *  the user can dismiss without enabling effectors. */
  readOnly?: boolean;
  readOnlyReason?: string;
}

/**
 * Tutor Layer B0 — visible "I have a suggestion" card inline in chat.
 *
 * Council R3: "preserves generation effect" → APPLY puts the edit in
 * the user's clipboard so they have to consciously paste it; we
 * never type into the user's editor in B0. Daemon path (B1) will add
 * APPLY → keystroke injection but only behind an explicit trust grant.
 */
export function SuggestedEditCard({ edit, onStatusChange, readOnly, readOnlyReason }: SuggestedEditCardProps) {
  const [status, setStatus] = useState(edit.status);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  async function postStatus(s: "applied" | "rejected" | "failed") {
    try {
      const r = await jarvisFetch(`/api/v1/edit/${encodeURIComponent(edit.id)}/status`, {
        method: "POST",
        body: JSON.stringify({ status: s.toUpperCase() }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
    } catch (e) {
      // Non-blocking — local apply already happened. Log into existing
      // error surface so the user knows the audit log didn't take.
      setError(prev => prev ?? `status sync failed: ${(e as Error).message}`);
    }
  }

  async function apply() {
    setError(null);
    if (readOnly) {
      setStatus("failed");
      setError(`READ-ONLY MODE — ${readOnlyReason ?? "effectors disabled"}`);
      onStatusChange?.(edit.id, "failed");
      postStatus("failed");
      return;
    }
    try {
      if (edit.type === "clipboard") {
        await applyClipboard(edit);
        setStatus("applied");
        onStatusChange?.(edit.id, "applied");
        postStatus("applied");
      } else {
        setStatus("failed");
        setError(`type ${edit.type} not supported in B0 (daemon path arrives in B1)`);
        onStatusChange?.(edit.id, "failed");
        postStatus("failed");
      }
    } catch (e) {
      setStatus("failed");
      setError((e as Error).message);
      onStatusChange?.(edit.id, "failed");
      postStatus("failed");
    }
  }

  function reject() {
    setStatus("rejected");
    onStatusChange?.(edit.id, "rejected");
    postStatus("rejected");
  }

  const TRUNCATE = 320;
  const isLong = edit.payload.length > TRUNCATE;
  const previewText = !isLong || expanded ? edit.payload : edit.payload.slice(0, TRUNCATE) + "…";

  return (
    <div data-testid="suggested-edit-card" data-edit-id={edit.id}
         className="border-l-4 border-accent-hover bg-accent-soft p-3 my-2">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs font-bold tracking-widest text-page-fg/70">
          SUGGESTED · {edit.type.toUpperCase()}
          {edit.label ? ` · ${edit.label}` : ""}
        </div>
        <div data-testid="suggested-edit-status" className="text-xs text-page-fg/60">
          {status}
        </div>
      </div>
      <pre tabIndex={0}
           aria-label="Suggested edit preview"
           className="text-xs whitespace-pre-wrap font-mono bg-page-bg px-2 py-1 border border-page-fg/10 max-h-48 overflow-auto">
        {previewText}
      </pre>
      {isLong && !expanded && (
        <button onClick={() => setExpanded(true)}
                data-testid="suggested-edit-show-more"
                className="text-xs underline text-page-fg/60 mt-1">show more</button>
      )}
      {error && (
        <div data-testid="suggested-edit-error" className="text-xs text-danger-text mt-1">
          {error}
        </div>
      )}
      {status === "pending" && (
        <div className="flex gap-2 mt-2">
          <button
            onClick={apply}
            data-testid="suggested-edit-apply"
            disabled={readOnly}
            title={readOnly ? `READ-ONLY MODE: ${readOnlyReason}` : undefined}
            className={`text-xs font-bold tracking-widest px-3 py-2 sm:py-1 ${
              readOnly ? "bg-disabled-bg text-disabled-fg cursor-not-allowed" : "bg-panel-dark-bg text-panel-dark-fg"
            }`}
          >
            {edit.type === "clipboard" ? "COPY" : "APPLY"}
          </button>
          <button onClick={reject} data-testid="suggested-edit-reject"
                  className="text-xs font-bold tracking-widest bg-page-bg text-page-fg px-3 py-2 sm:py-1 border border-border-strong">
            REJECT
          </button>
        </div>
      )}
    </div>
  );
}
