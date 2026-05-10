import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { captureScreenshot } from "../lib/screenshot";

export interface ScreenshotEvent {
  eventSeq: number;
  extracted: {
    filePath: string | null;
    cursor: { line: number; col: number } | null;
    consoleOutput: string | null;
    error: string | null;
    rawReply: string;
  };
  /** Layer B0 read-only mode: server-side classifier flagged this
   *  capture as non-code-editor (browser tab, IDE-less window, etc).
   *  Effectors should be auto-disabled while true. */
  readOnlyMode?: boolean;
  readOnlyReason?: string;
}

export interface ScreenshotCaptureProps {
  taskId: string;
  onResult?: (e: ScreenshotEvent) => void;
}

/**
 * Screenshot capture surface — provides:
 *  - a global Ctrl+Shift+J hotkey that triggers getDisplayMedia
 *  - an explicit camera button (also a user-gesture path, required by
 *    getDisplayMedia even with the hotkey)
 *  - inline status (capturing → uploading → done/failed)
 *
 * Sends the captured PNG to /api/v1/sensor/screenshot. On success
 * fires onResult so the parent can render the extraction inline in
 * chat (suggested-edit cards / context display).
 */
export function ScreenshotCapture({ taskId, onResult }: ScreenshotCaptureProps) {
  const [status, setStatus] = useState<string>("idle");
  const [error, setError] = useState<string | null>(null);

  async function trigger() {
    setStatus("capturing");
    setError(null);
    try {
      const shot = await captureScreenshot();
      setStatus("uploading");
      const res = await jarvisFetch("/api/v1/sensor/screenshot", {
        method: "POST",
        body: JSON.stringify({
          imageBase64: shot.imageBase64,
          mediaType: "image/png",
          taskId,
        }),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
      }
      const data: ScreenshotEvent = await res.json();
      setStatus("done");
      onResult?.(data);
    } catch (e) {
      setStatus("failed");
      setError((e as Error).message);
    }
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      // Ctrl+Shift+J on Windows/Linux, Cmd+Shift+J on Mac.
      const meta = e.ctrlKey || e.metaKey;
      if (meta && e.shiftKey && (e.key === "J" || e.key === "j")) {
        e.preventDefault();
        trigger();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [taskId]); // eslint-disable-line react-hooks/exhaustive-deps

  const statusText = status === "idle" ? "capture (Ctrl+Shift+J)"
    : status === "capturing" ? "capturing…"
    : status === "uploading" ? "uploading…"
    : status === "done" ? "captured"
    : "retry";
  const showCamera = status === "idle";

  return (
    <div data-testid="screenshot-capture" className="flex items-center gap-2 px-3 py-1.5 border-b border-page-fg/10 bg-page-bg">
      <button
        onClick={trigger}
        disabled={status === "capturing" || status === "uploading"}
        data-testid="screenshot-capture-btn"
        aria-label="Capture a screenshot of the current window for the tutor to read"
        className="text-xs font-bold tracking-widest bg-accent px-3 py-1 disabled:opacity-50"
      >
        {showCamera && <span aria-hidden="true">📷 </span>}
        <span>{statusText}</span>
      </button>
      {error && (
        <span data-testid="screenshot-error" className="text-xs text-danger-text">
          {error}
        </span>
      )}
    </div>
  );
}
