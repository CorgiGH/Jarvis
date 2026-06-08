/** LearnerQueueList — paginated list of QueueItems for the /oggi home screen.
 *
 *  Each row: subject pill + KC name (Romanian) + phase badge + MasterySparkline.
 *  Empty state: data-testid="queue-empty".
 *  Keyboard: CTRL+ENTER on a row calls onSelect(item).
 */
import type { QueueItem } from "../lib/taskPrep";
import { MasterySparkline } from "./MasterySparkline";

const PAGE_SIZE = 10;

interface LearnerQueueListProps {
  items: QueueItem[];
  onSelect: (item: QueueItem) => void;
  page?: number; // 0-indexed; omit for page 0
}

const PHASE_LABELS: Record<QueueItem["phase"], string> = {
  intro: "intro",
  practice: "practice",
  retrieval: "retrieval",
  mastered: "mastered",
};

export function LearnerQueueList({ items, onSelect, page = 0 }: LearnerQueueListProps) {
  if (items.length === 0) {
    return (
      <div data-testid="queue-empty" className="p-6 font-mono text-sm text-page-fg/60 tracking-widest">
        Nimic programat azi.
      </div>
    );
  }

  const start = page * PAGE_SIZE;
  const slice = items.slice(start, start + PAGE_SIZE);

  return (
    <ol
      data-testid="learner-queue-list"
      className="list-none m-0 p-0 flex flex-col gap-1"
    >
      {slice.map((item) => (
        <li
          key={item.kc_id}
          data-testid={`queue-item-${item.kc_id}`}
          tabIndex={0}
          role="button"
          aria-label={item.kc_name_ro}
          onClick={() => onSelect(item)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && e.ctrlKey) {
              e.preventDefault();
              onSelect(item);
            }
          }}
          className="flex items-center gap-3 px-3 py-2 font-mono text-xs tracking-widest cursor-pointer hover:bg-panel-bg focus:outline-none focus:bg-panel-bg border border-transparent hover:border-border-strong"
        >
          {/* Subject pill */}
          <span className="shrink-0 bg-accent text-page-fg px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider">
            {item.subject}
          </span>

          {/* KC name (Romanian) */}
          <span className="flex-1 truncate text-page-fg">
            {item.kc_name_ro}
          </span>

          {/* Phase badge */}
          <span
            data-testid={`queue-item-phase-${item.kc_id}`}
            className="shrink-0 text-page-fg/60 text-[10px] uppercase tracking-wider border border-border-strong px-1"
          >
            {PHASE_LABELS[item.phase]}
          </span>

          {/* Mastery band */}
          <span
            data-testid={`queue-item-mastery-${item.kc_id}`}
            className="shrink-0"
          >
            <MasterySparkline ewmaScore={item.mastery_ewma} />
          </span>
        </li>
      ))}
    </ol>
  );
}
