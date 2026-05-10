import { useEffect, useState } from "react";

/**
 * Mockup-gap closer: bottom status bar.
 *
 * The brutalist-mono v4 mockup ends with a 30px black strip:
 *   "● READY · CTRL+ENTER · CORGFLIX.DUCKDNS.ORG    21:53 · BUC"
 *
 * Implementation matches the brutalist palette (panel-dark-bg / yellow
 * pulse) and shows Europe/Bucharest local time live-updated each minute.
 * Pure cosmetic; no interactive surfaces here so it stays out of focus
 * order — `aria-hidden` keeps screen readers from announcing the clock
 * every minute.
 */
export function StatusBar({ hostname = "corgflix.duckdns.org" }: { hostname?: string }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);
  const timeStr = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit", minute: "2-digit", hour12: false,
    timeZone: "Europe/Bucharest",
  }).format(now);
  return (
    <div data-testid="status-bar"
         aria-hidden="true"
         className="bg-panel-dark-bg text-panel-dark-fg text-[10px] tracking-widest font-bold px-4 py-1 flex items-center justify-between border-t-2 border-border-strong">
      <span className="flex items-center gap-2">
        <span data-testid="status-bar-pulse"
              className="inline-block w-1.5 h-1.5 bg-accent" />
        READY · CTRL+ENTER · {hostname.toUpperCase()}
      </span>
      <span data-testid="status-bar-clock">{timeStr} · BUC</span>
    </div>
  );
}
