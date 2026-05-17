import { useEffect, useRef } from "react";

export type ScratchpadSaveStatus = "idle" | "saving" | "saved" | "error";

/** Mirror of the server-side cap in TutorRoutes.kt PUT /api/v1/tasks/{id}/scratchpad. */
export const SCRATCHPAD_MAX_CHARS = 50_000;

export interface ScratchpadProps {
  value: string;
  onChange: (text: string) => void;
  status?: ScratchpadSaveStatus;
  errorMessage?: string | null;
}

/**
 * Tutor Layer B0 — user's scratchpad slot.
 *
 * Collapsible drawer pinned to the bottom of the workspace. The user
 * uses it to draft answers / paste suggested edits / pre-write code
 * before committing it to their real editor. Knowledge-gap cards
 * INSERT into here, never into the user's editor (preserves the
 * generation effect: the user has to manually move from scratchpad to
 * project).
 *
 * Persisted client-side via localStorage; server persistence (per
 * task) lands in Layer B1 once the task slot exists.
 */
export function Scratchpad({ value, onChange, status, errorMessage }: ScratchpadProps) {
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    // Auto-scroll to bottom when value grows from outside (e.g. an
    // INSERT-from-gap-card), so the user sees the appended snippet.
    if (ref.current) {
      ref.current.scrollTop = ref.current.scrollHeight;
    }
  }, [value]);

  return (
    <div data-testid="scratchpad" className="border-t-4 border-border-strong bg-page-bg">
      <div className="flex items-center justify-between bg-panel-dark-bg text-panel-dark-fg px-3 py-1 text-xs tracking-widest font-bold">
        <h2 id="scratchpad-heading" className="text-xs">SCRATCHPAD</h2>
        {status && status !== "idle" && (
          <span
            data-testid="scratchpad-status"
            data-status={status}
            aria-live="polite"
            title={status === "error" ? (errorMessage ?? "save failed") : undefined}
            className={
              status === "saving" ? "text-page-bg/70 font-normal"
              : status === "saved" ? "text-page-bg/70 font-normal"
              : "text-danger-text font-bold"
            }
          >
            {status === "saving" ? "saving…" : status === "saved" ? "saved" : "save failed"}
          </span>
        )}
      </div>
      <textarea
        ref={ref}
        data-testid="scratchpad-input"
        aria-labelledby="scratchpad-heading"
        aria-label="Task scratchpad — local notes; auto-saved per browser"
        maxLength={SCRATCHPAD_MAX_CHARS}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder="draft answers / inserted snippets land here…"
        className="w-full min-h-[6rem] max-h-[40vh] p-3 outline-none text-sm font-mono resize-y"
      />
      <div data-testid="scratchpad-counter"
           className={`px-3 py-1 text-[10px] font-mono text-right ${value.length > SCRATCHPAD_MAX_CHARS * 0.9 ? "text-danger-text font-bold" : "text-page-fg/80"}`}>
        {value.length} / {SCRATCHPAD_MAX_CHARS}
      </div>
    </div>
  );
}
