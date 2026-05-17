import { jarvisFetch } from "./api";

/**
 * 2026-05-17 hot-work #4: feature-usage telemetry ping.
 *
 * Fires a best-effort POST to /api/v1/sensor/telemetry that appends a
 * TutorEvent of type "ui_telemetry" carrying the event name + optional
 * payload. Also bumps a localStorage counter for offline / DevTools-visible
 * inspection so the council 1778988899 carry-over (decide on Option B
 * KnowledgeLedger deletion at the 2026-05-31 window) has dual signals.
 *
 * Failure is silent — telemetry must never break the calling UI flow.
 */
export function recordTelemetry(
  name: string,
  payload?: Record<string, unknown>,
): void {
  const ts = new Date().toISOString();
  try {
    const key = `jarvis.telemetry.${name}.count`;
    const tsKey = `jarvis.telemetry.${name}.last_ts`;
    const prev = Number(localStorage.getItem(key) ?? "0");
    localStorage.setItem(key, String(prev + 1));
    localStorage.setItem(tsKey, ts);
  } catch {
    // localStorage may be unavailable (private mode, quota); ignore.
  }
  const body = JSON.stringify({ name, ts, payload: payload ?? null });
  jarvisFetch("/api/v1/sensor/telemetry", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  }).catch(() => {
    // Best-effort — backend may be down or rate-limiting; UI continues.
  });
}

/**
 * Read the local counter + last timestamp for a given telemetry event.
 * Returns 0/null if storage is empty or unavailable. Useful in DevTools:
 *   readTelemetryCounter("ledger.opened")
 */
export function readTelemetryCounter(name: string): {
  count: number;
  lastTs: string | null;
} {
  try {
    const count = Number(localStorage.getItem(`jarvis.telemetry.${name}.count`) ?? "0");
    const lastTs = localStorage.getItem(`jarvis.telemetry.${name}.last_ts`);
    return { count, lastTs };
  } catch {
    return { count: 0, lastTs: null };
  }
}
