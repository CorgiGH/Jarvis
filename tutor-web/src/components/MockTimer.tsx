import { useEffect, useRef, useState } from "react";

interface MockTimerProps {
  totalSeconds: number;
  onExpire: () => void;
}

function formatTime(secs: number): string {
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export function MockTimer({ totalSeconds, onExpire }: MockTimerProps) {
  const [remaining, setRemaining] = useState(totalSeconds);
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  useEffect(() => {
    if (totalSeconds <= 0) {
      onExpireRef.current();
      return;
    }

    // Use a single interval that decrements remaining every second.
    // More testable with fake timers than chained setTimeout.
    let rem = totalSeconds;
    const id = setInterval(() => {
      rem -= 1;
      setRemaining(Math.max(0, rem));
      if (rem <= 0) {
        clearInterval(id);
        onExpireRef.current();
      }
    }, 1000);

    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [totalSeconds]);

  const warning = remaining < 60;

  return (
    <div
      data-testid="mock-timer"
      data-timer-warning={String(warning)}
      className={`font-mono font-bold tracking-widest text-sm px-3 py-1 border-2 ${
        warning
          ? "border-danger-text text-danger-text"
          : "border-border-strong text-page-fg"
      }`}
    >
      {formatTime(remaining)}
    </div>
  );
}
