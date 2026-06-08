/** DayOfCountdown — monumental exam countdown + reappraisal sentence.
 *
 *  Shows hours (or minutes) remaining until exam start_at.
 *  Learner-facing copy is in Romanian.
 *
 *  testids: day-of-countdown
 */
interface Props {
  subject: string;
  startAt: string; // ISO datetime string
}

function hoursUntil(isoDate: string): number {
  const delta = new Date(isoDate).getTime() - Date.now();
  return Math.max(0, delta / (1000 * 60 * 60));
}

export function DayOfCountdown({ subject, startAt }: Props) {
  const hours = hoursUntil(startAt);
  const h = Math.floor(hours);
  const m = Math.floor((hours - h) * 60);

  const timeLabel =
    h > 0 ? `${h}h ${m}m` : `${m}m`;

  return (
    <div
      data-testid="day-of-countdown"
      className="flex flex-col items-center gap-4 py-8 font-mono"
    >
      {/* Subject name */}
      <p className="text-xs font-bold tracking-widest uppercase text-page-fg/60">
        {subject}
      </p>

      {/* Monumental time display */}
      <div
        aria-label={`${timeLabel} până la examen`}
        className="text-7xl font-black tracking-tighter text-accent leading-none"
      >
        {timeLabel}
      </div>

      {/* Reappraisal sentence */}
      <p className="text-sm text-page-fg/80 tracking-wide text-center max-w-xs">
        Ești pregătit. Examenul este o oportunitate să demonstrezi ce știi.
      </p>

      <p className="text-xs text-page-fg/40 tracking-widest">
        Mult succes!
      </p>
    </div>
  );
}
