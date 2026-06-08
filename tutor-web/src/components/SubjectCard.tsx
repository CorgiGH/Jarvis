/** SubjectCard — asymmetric layout card for one subject (surface 0b, SubjectMap).
 *
 *  Uses the ASYMMETRIC layout (plan default — R6 equal-column exception PARKED).
 *  Left: subject name + KC counts. Right: MasterySparkline + RetentionGapBadge.
 *
 *  testids:
 *   subject-card-{subjectId}
 *   retention-gap-badge-{subjectId}   — only when retentionGapCount > 0
 */
import { MasterySparkline } from "./MasterySparkline";

export interface SubjectCardProps {
  subjectId: string;
  subjectNameRo: string;
  subjectNameEn: string;
  kcCount: number;
  masteredCount: number;
  avgEwma: number; // 0..1 average EWMA across all KCs in this subject
  retentionGapCount: number; // KCs with low mastery (ewma < 0.3) that need attention
  onClick?: () => void;
}

export function SubjectCard({
  subjectId,
  subjectNameRo,
  subjectNameEn,
  kcCount,
  masteredCount,
  avgEwma,
  retentionGapCount,
  onClick,
}: SubjectCardProps) {
  return (
    <button
      data-testid={`subject-card-${subjectId}`}
      onClick={onClick}
      className="w-full text-left bg-panel-bg border border-border-strong font-mono hover:border-accent focus:outline-none focus:border-accent p-4 flex gap-4 items-start"
      aria-label={`${subjectNameRo} — ${masteredCount} din ${kcCount} noțiuni stăpânite`}
    >
      {/* Left: dominant info column (F-pattern) */}
      <div className="flex-1 min-w-0 flex flex-col gap-1">
        <span className="text-xs font-bold tracking-widest uppercase text-page-fg truncate">
          {subjectNameRo}
        </span>
        <span className="text-[10px] text-page-fg/50 tracking-wide truncate">
          {subjectNameEn}
        </span>
        <span className="text-[10px] text-page-fg/60 tracking-widest mt-1">
          {masteredCount} / {kcCount} stăpânite
        </span>
      </div>

      {/* Right: mastery band + gap badge */}
      <div className="flex flex-col items-end gap-2 shrink-0">
        <MasterySparkline ewmaScore={avgEwma} />
        {retentionGapCount > 0 && (
          <RetentionGapBadge subjectId={subjectId} count={retentionGapCount} />
        )}
      </div>
    </button>
  );
}

/** RetentionGapBadge — inline badge showing KCs that need re-practice.
 *  testid: retention-gap-badge-{subjectId}
 */
export function RetentionGapBadge({
  subjectId,
  count,
}: {
  subjectId: string;
  count: number;
}) {
  return (
    <span
      data-testid={`retention-gap-badge-${subjectId}`}
      title={`${count} noțiuni cu stăpânire scăzută`}
      className="bg-danger-text text-page-fg text-[9px] font-bold px-1.5 py-0.5 tracking-widest uppercase"
    >
      {count}
    </span>
  );
}
