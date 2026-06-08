/** LedgerRow — one gap row for LedgerDrawer (surface 0h).
 *
 *  testids:
 *   ledger-row-{id}
 *   ledger-row-status-{id}  — "open" | "resolved"
 */
import { useNavigate } from "react-router-dom";
import { formatEnum } from "../lib/formatEnum";

export interface LedgerGap {
  id: string;
  topic: string;
  taskId: string | null;
  type: string;
  reusedCount: number;
  resolvedBy: string | null;
}

interface LedgerRowProps {
  gap: LedgerGap;
  onClose: () => void;
}

export function LedgerRow({ gap, onClose }: LedgerRowProps) {
  const navigate = useNavigate();
  const isResolved = gap.resolvedBy !== null;

  const inner = (
    <>
      <div className="font-bold text-page-fg truncate">{gap.topic}</div>
      <div className="flex items-center gap-2 text-page-fg/70 mt-0.5">
        <span
          data-testid={`ledger-row-status-${gap.id}`}
          className={`uppercase text-[9px] tracking-widest border px-1 ${
            isResolved
              ? "border-border-thin text-page-fg/50"
              : "border-accent text-accent"
          }`}
        >
          {isResolved ? "resolved" : "open"}
        </span>
        <span className="text-[10px]">{formatEnum(gap.type)}</span>
        <span className="text-[10px]">· reused {gap.reusedCount}×</span>
      </div>
    </>
  );

  return (
    <li
      data-testid={`ledger-row-${gap.id}`}
      className="border-b border-border-thin last:border-0"
    >
      {gap.taskId ? (
        <button
          onClick={() => {
            navigate(`/?taskId=${gap.taskId}`);
            onClose();
          }}
          aria-label={`Deschide task-ul pentru ${gap.topic}`}
          className="w-full text-left px-3 py-2 hover:bg-accent-soft focus:outline-none focus:bg-accent-soft"
        >
          {inner}
        </button>
      ) : (
        <div className="px-3 py-2">{inner}</div>
      )}
    </li>
  );
}
