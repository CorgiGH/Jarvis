/**
 * CalibrationTable — tabular view of confidence calibration (Surface 6).
 * Each row shows the confidence band, attempt count, accuracy, and a
 * OVER / UNDER / CALIBRATED classification.
 *
 * Classification thresholds:
 *   OVER = confidence_expected > accuracy + 0.15  (over-confident)
 *   UNDER = accuracy > confidence_expected + 0.15 (under-confident)
 *   CALIBRATED = otherwise
 *
 * Expected accuracies (rough priors for each confidence level):
 *   DEFINITELY → 0.95, MAYBE → 0.75, GUESS → 0.50, IDK → 0.25
 */
import type { CalibrationBucket } from "./CalibrationPlot";

type Band = "OVER" | "UNDER" | "CALIBRATED";

const EXPECTED_ACCURACY: Record<string, number> = {
  DEFINITELY: 0.95,
  MAYBE: 0.75,
  GUESS: 0.50,
  IDK: 0.25,
};

const CONF_LABEL: Record<string, string> = {
  DEFINITELY: "Sigur",
  MAYBE: "Poate",
  GUESS: "Ghicit",
  IDK: "Nu știu",
};

const DISPLAY_ORDER = ["DEFINITELY", "MAYBE", "GUESS", "IDK"];

function classify(conf: string, accuracy: number): Band {
  const expected = EXPECTED_ACCURACY[conf] ?? 0.5;
  if (expected > accuracy + 0.15) return "OVER";
  if (accuracy > expected + 0.15) return "UNDER";
  return "CALIBRATED";
}

const BAND_STYLE: Record<Band, string> = {
  OVER:        "text-danger-text",
  UNDER:       "text-accent",
  CALIBRATED:  "text-page-fg/70",
};

const BAND_LABEL: Record<Band, string> = {
  OVER:        "SUPRA",
  UNDER:       "SUB",
  CALIBRATED:  "OK",
};

interface CalibrationTableProps {
  buckets: CalibrationBucket[];
  totalAttempts: number;
}

export function CalibrationTable({ buckets, totalAttempts }: CalibrationTableProps) {
  const byConf = Object.fromEntries(buckets.map((b) => [b.student_confidence, b]));
  const orderedBuckets = DISPLAY_ORDER
    .map((conf) => byConf[conf as CalibrationBucket["student_confidence"]])
    .filter(Boolean) as CalibrationBucket[];

  return (
    <div
      data-testid="calibration-table"
      className="font-mono text-xs flex flex-col gap-1"
    >
      <div className="flex items-center justify-between text-[10px] tracking-widest text-page-fg/50 uppercase border-b border-border-thin pb-1">
        <span>Calibrare predicții</span>
        <span>{totalAttempts} încercări</span>
      </div>

      {orderedBuckets.length === 0 && (
        <p className="text-page-fg/40 tracking-widest text-[10px] py-2">
          — niciun răspuns cu predicție încă
        </p>
      )}

      {orderedBuckets.map((bucket) => {
        const band = classify(bucket.student_confidence, bucket.accuracy);
        const pct = Math.round(bucket.accuracy * 100);
        return (
          <div
            key={bucket.student_confidence}
            data-testid={`calibration-row-${bucket.student_confidence}`}
            data-band={band}
            className="flex items-center gap-3 py-1 border-b border-border-thin last:border-b-0"
          >
            <span className="w-16 shrink-0 text-page-fg tracking-widest">
              {CONF_LABEL[bucket.student_confidence] ?? bucket.student_confidence}
            </span>
            <span className="w-8 text-right text-page-fg/60">
              {bucket.attempts}
            </span>
            <div className="flex-1 bg-page-bg border border-border-thin h-2 relative">
              <div
                className="absolute inset-y-0 left-0 bg-accent/60"
                style={{ width: `${pct}%` }}
              />
            </div>
            <span className="w-8 text-right text-page-fg">
              {pct}%
            </span>
            <span className={`w-12 text-right text-[10px] font-bold tracking-widest ${BAND_STYLE[band]}`}>
              {BAND_LABEL[band]}
            </span>
          </div>
        );
      })}
    </div>
  );
}
