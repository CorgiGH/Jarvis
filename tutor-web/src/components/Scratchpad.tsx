import { useEffect, useRef } from "react";

export interface ScratchpadProps {
  value: string;
  onChange: (text: string) => void;
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
export function Scratchpad({ value, onChange }: ScratchpadProps) {
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
      <div className="bg-panel-dark-bg text-panel-dark-fg px-3 py-1 text-xs tracking-widest font-bold">
        SCRATCHPAD
      </div>
      <textarea
        ref={ref}
        data-testid="scratchpad-input"
        aria-label="Task scratchpad — local notes; auto-saved per browser"
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder="draft answers / inserted snippets land here…"
        className="w-full min-h-[6rem] max-h-[40vh] p-3 outline-none text-sm font-mono resize-y"
      />
    </div>
  );
}
