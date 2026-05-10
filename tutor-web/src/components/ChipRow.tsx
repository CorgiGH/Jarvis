import type { QuickChip } from "../lib/chip";

/**
 * Pill row of quick-action chips below an assistant message. Click → fill
 * the chat input with the chip's prompt + focus the input. User reviews
 * before sending (preserves agency vs auto-send).
 */
export function ChipRow({
  chips,
  onPick,
}: {
  chips: QuickChip[];
  onPick: (prompt: string) => void;
}) {
  if (chips.length === 0) return null;
  return (
    <div data-testid="chip-row" className="flex flex-wrap gap-2 mt-2">
      {chips.map((c, i) => (
        <button
          key={`${c.label}-${i}`}
          data-testid="chip-button"
          onClick={() => onPick(c.prompt)}
          className="font-mono text-xs px-2 py-1 border-2 border-border-strong bg-accent-hover hover:bg-accent tracking-wide"
          title={c.prompt}
        >
          {c.label}
        </button>
      ))}
    </div>
  );
}
