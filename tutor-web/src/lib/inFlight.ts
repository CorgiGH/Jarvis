import { useEffect, useRef, useState } from "react";

/**
 * Returns [inFlight, showSpinner, run] tuple.
 *  - inFlight: true while the async fn is running (set immediately on call)
 *  - showSpinner: true only after the 400ms threshold per wiki
 *    [[Feedback & Response Time]] — avoids spinner flash on fast nets.
 *  - run: wraps an async fn so callers don't have to thread the state.
 */
export function useInFlight() {
  const [inFlight, setInFlight] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timerRef.current != null) clearTimeout(timerRef.current);
  }, []);

  async function run<T>(fn: () => Promise<T>): Promise<T> {
    setInFlight(true);
    timerRef.current = setTimeout(() => setShowSpinner(true), 400);
    try {
      return await fn();
    } finally {
      if (timerRef.current != null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      setShowSpinner(false);
      setInFlight(false);
    }
  }
  return { inFlight, showSpinner, run };
}
