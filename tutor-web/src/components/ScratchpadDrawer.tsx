import { useEffect, useRef, useState } from "react";
import { Scratchpad, type ScratchpadSaveStatus } from "./Scratchpad";
import { jarvisFetch } from "../lib/api";

const DEBOUNCE_MS = 500;
const SAVED_BANNER_MS = 1500;

interface Props {
  taskId: string;
  /** Override for tests — defaults to global jarvisFetch. */
  fetchImpl?: typeof jarvisFetch;
}

/**
 * Owns scratchpad state for the ResourceRail SCRATCHPAD drawer:
 *   - One-shot server hydration on mount (hydratedRef gates re-fetch so a
 *     user-cleared field never gets clobbered by a stale server response).
 *   - Debounced PUT (500ms) instead of per-keystroke flood.
 *   - Save-status pill rendered inside Scratchpad header
 *     ("saving…" / "saved" / "save failed").
 *   - Surface fetch errors instead of swallowing them silently.
 *
 * Closes Phase 3 [3] state-visibility + feedback-response-time backlog
 * entries — the prior inline implementation in ResourceRail PUT on every
 * keystroke, swallowed errors, and re-fetched on every render where
 * scratch was empty (clobber race when the user intentionally cleared).
 */
export function ScratchpadDrawer({ taskId, fetchImpl = jarvisFetch }: Props) {
  const [scratch, setScratch] = useState<string>("");
  const [status, setStatus] = useState<ScratchpadSaveStatus>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hydratedRef = useRef(false);
  const debounceHandleRef = useRef<number | null>(null);
  const savedBannerHandleRef = useRef<number | null>(null);

  // Hydrate once per mount. If hydration fails (network / 4xx), keep the
  // scratchpad usable — surface the error in the pill.
  useEffect(() => {
    if (hydratedRef.current || !taskId) return;
    hydratedRef.current = true;
    fetchImpl(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`)
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data: { text?: string } | null) => {
        if (data?.text != null) setScratch(data.text);
      })
      .catch((e: Error) => {
        setStatus("error");
        setErrorMessage(`load failed: ${e.message}`);
      });
  }, [taskId, fetchImpl]);

  useEffect(() => {
    return () => {
      if (debounceHandleRef.current != null) window.clearTimeout(debounceHandleRef.current);
      if (savedBannerHandleRef.current != null) window.clearTimeout(savedBannerHandleRef.current);
    };
  }, []);

  function handleChange(next: string) {
    setScratch(next);
    if (debounceHandleRef.current != null) window.clearTimeout(debounceHandleRef.current);
    if (savedBannerHandleRef.current != null) window.clearTimeout(savedBannerHandleRef.current);
    setStatus("saving");
    setErrorMessage(null);
    debounceHandleRef.current = window.setTimeout(() => {
      debounceHandleRef.current = null;
      fetchImpl(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`, {
        method: "PUT",
        body: JSON.stringify({ text: next }),
      })
        .then(r => {
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          setStatus("saved");
          // Banner auto-fades after a short visible window so the pill
          // doesn't stay "saved" forever; status returns to idle.
          savedBannerHandleRef.current = window.setTimeout(() => {
            savedBannerHandleRef.current = null;
            setStatus("idle");
          }, SAVED_BANNER_MS);
        })
        .catch((e: Error) => {
          setStatus("error");
          setErrorMessage(`save failed: ${e.message}`);
        });
    }, DEBOUNCE_MS);
  }

  return (
    <Scratchpad
      value={scratch}
      onChange={handleChange}
      status={status}
      errorMessage={errorMessage}
    />
  );
}
