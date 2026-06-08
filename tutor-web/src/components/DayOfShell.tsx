/** DayOfShell — single-col DARK exam-day surface (surface 3).
 *
 *  Fetches GET /api/v1/me/exam-dates.
 *  If any exam is within 24h, renders:
 *    - DayOfCountdown (monumental timer)
 *    - day-of-review-strip (last 3 due KCs)
 *    - DayOfChecklist (FII checklist)
 *
 *  testids: day-of-shell · day-of-countdown (delegated) · day-of-checklist (delegated)
 *           day-of-review-strip
 */
import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { DayOfCountdown } from "./DayOfCountdown";
import { DayOfChecklist } from "./DayOfChecklist";

interface ApiExamDate {
  subject: string;
  start_at: string; // ISO
}

const TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;

function findImminent(dates: ApiExamDate[]): ApiExamDate | null {
  const now = Date.now();
  return (
    dates.find((d) => {
      const delta = new Date(d.start_at).getTime() - now;
      return delta >= 0 && delta <= TWENTY_FOUR_HOURS_MS;
    }) ?? null
  );
}

export function DayOfShell() {
  const [imminent, setImminent] = useState<ApiExamDate | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    jarvisFetch("/api/v1/me/exam-dates")
      .then((r) => (r.ok ? r.json() : null))
      .then((data: { exam_dates: ApiExamDate[] } | null) => {
        if (cancelled) return;
        const found = data ? findImminent(data.exam_dates) : null;
        setImminent(found);
        setLoaded(true);
      })
      .catch(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <div
      data-testid="day-of-shell"
      className="min-h-screen bg-panel-dark-bg text-panel-dark-fg font-mono flex flex-col items-center px-4 py-8 gap-8"
    >
      {!loaded && (
        <p className="text-xs tracking-widest text-panel-dark-fg/40 mt-16">
          Se încarcă…
        </p>
      )}

      {loaded && imminent && (
        <>
          <DayOfCountdown subject={imminent.subject} startAt={imminent.start_at} />

          {/* Last-review strip — shows the subject for light review */}
          <div
            data-testid="day-of-review-strip"
            className="w-full max-w-sm border border-panel-dark-fg/20 px-4 py-3"
          >
            <p className="text-[10px] font-bold tracking-widest uppercase text-panel-dark-fg/50 mb-2">
              Recapitulare rapidă — {imminent.subject}
            </p>
            <p className="text-xs text-panel-dark-fg/60 tracking-wide">
              Trece în revistă conceptele cheie. Nu mai memora — reamintește.
            </p>
          </div>

          <DayOfChecklist />
        </>
      )}

      {loaded && !imminent && (
        <div className="flex flex-col items-center gap-3 mt-16">
          <p className="text-xs font-bold tracking-widest uppercase text-panel-dark-fg/40">
            Niciun examen în următoarele 24h
          </p>
          <p className="text-[11px] text-panel-dark-fg/30 tracking-wide">
            Continuă pregătirea din ecranul Azi.
          </p>
        </div>
      )}
    </div>
  );
}
