import { useEffect, useState } from "react";

interface DaemonHealth {
  reachable: boolean;
  tunnelUp: boolean;
  lastSeenAt: string | null;
}

type PillStatus = "green" | "amber" | "red" | "unknown";

function pillFromHealth(h: DaemonHealth): PillStatus {
  if (h.reachable && h.tunnelUp) return "green";
  if (!h.reachable && h.tunnelUp) return "amber";
  return "red";
}

const STATUS_LABELS: Record<PillStatus, string> = {
  green: "DAEMON OK",
  amber: "TUNNEL ONLY",
  red:   "DAEMON DOWN",
  unknown: "CHECKING...",
};

const STATUS_DOT_CLASS: Record<PillStatus, string> = {
  green: "bg-green-400",
  amber: "bg-yellow-400",
  red:   "bg-red-500",
  unknown: "bg-page-fg/30",
};

const POLL_INTERVAL_MS = 30_000;

export function DaemonHealthPill() {
  const [status, setStatus] = useState<PillStatus>("unknown");
  const [lastSeenAt, setLastSeenAt] = useState<string | null>(null);

  async function poll() {
    try {
      const r = await fetch("/api/v1/daemon/health");
      if (!r.ok) { setStatus("red"); return; }
      const data: DaemonHealth = await r.json();
      setStatus(pillFromHealth(data));
      setLastSeenAt(data.lastSeenAt ?? null);
    } catch { setStatus("red"); }
  }

  useEffect(() => {
    poll();
    const t = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(t);
  }, []);

  return (
    <span
      data-testid="daemon-health-pill"
      data-status={status}
      title={lastSeenAt ? `Last seen: ${lastSeenAt}` : STATUS_LABELS[status]}
      className="inline-flex items-center gap-1 font-mono text-[11px] tracking-widest text-page-fg/85 select-none"
    >
      <span className={`inline-block w-1.5 h-1.5 rounded-full ${STATUS_DOT_CLASS[status]} transition-colors duration-500`} aria-hidden="true" />
      {STATUS_LABELS[status]}
    </span>
  );
}
