/**
 * Layer B0 Task 3 — browser screenshot capture via getDisplayMedia.
 *
 * The user picks the screen/window/tab to share once per capture; we
 * grab a single frame onto an off-screen canvas, encode as PNG, then
 * stop the stream immediately so no recording continues.
 *
 * getDisplayMedia requires a user gesture (click / keypress), so this
 * is wired to the Ctrl+Shift+J hotkey + an explicit camera button —
 * both satisfy the gesture requirement.
 */
export interface ScreenshotResult {
  /** base64-encoded PNG bytes (no data: prefix). */
  imageBase64: string;
  /** capture timestamp ISO string. */
  capturedAt: string;
}

export async function captureScreenshot(): Promise<ScreenshotResult> {
  if (typeof navigator === "undefined" || !navigator.mediaDevices?.getDisplayMedia) {
    throw new Error("getDisplayMedia not supported in this browser");
  }
  // video:true is required by spec; audio:false keeps the picker
  // dialog from prompting for mic capture too.
  const stream = await navigator.mediaDevices.getDisplayMedia({
    video: true,
    audio: false,
  });
  try {
    const track = stream.getVideoTracks()[0];
    if (!track) throw new Error("no video track in display media stream");

    const settings = track.getSettings();
    const width = settings.width ?? 1920;
    const height = settings.height ?? 1080;

    // Use ImageCapture if available (cleaner single-frame grab); fall
    // back to a canvas-from-video pipeline that works in Firefox.
    const ic = (window as unknown as { ImageCapture?: new (track: MediaStreamTrack) => { grabFrame(): Promise<ImageBitmap> } }).ImageCapture;
    let bitmap: ImageBitmap | null = null;
    if (ic) {
      try { bitmap = await new ic(track).grabFrame(); } catch { bitmap = null; }
    }

    let canvas: HTMLCanvasElement | OffscreenCanvas;
    if (bitmap) {
      canvas = typeof OffscreenCanvas !== "undefined"
        ? new OffscreenCanvas(bitmap.width, bitmap.height)
        : Object.assign(document.createElement("canvas"), { width: bitmap.width, height: bitmap.height });
      const ctx = canvas.getContext("2d");
      if (!ctx) throw new Error("2d context unavailable");
      // @ts-expect-error — both OffscreenCanvasRenderingContext2D and
      // CanvasRenderingContext2D accept ImageBitmap.
      ctx.drawImage(bitmap, 0, 0);
    } else {
      // Firefox fallback: pipe stream into an HTMLVideoElement, wait
      // one frame, draw onto a canvas.
      const video = document.createElement("video");
      video.muted = true;
      video.srcObject = stream;
      await video.play();
      // Wait one rAF so the first frame is decoded.
      await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
      canvas = Object.assign(document.createElement("canvas"), {
        width: video.videoWidth || width,
        height: video.videoHeight || height,
      });
      const ctx = (canvas as HTMLCanvasElement).getContext("2d");
      if (!ctx) throw new Error("2d context unavailable");
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      video.pause();
      video.srcObject = null;
    }

    const blob = await canvasToBlob(canvas);
    const buffer = await blob.arrayBuffer();
    return {
      imageBase64: bytesToBase64(new Uint8Array(buffer)),
      capturedAt: new Date().toISOString(),
    };
  } finally {
    stream.getTracks().forEach(t => t.stop());
  }
}

async function canvasToBlob(canvas: HTMLCanvasElement | OffscreenCanvas): Promise<Blob> {
  if (canvas instanceof OffscreenCanvas) {
    return canvas.convertToBlob({ type: "image/png" });
  }
  return new Promise((resolve, reject) => {
    canvas.toBlob(b => b ? resolve(b) : reject(new Error("toBlob returned null")), "image/png");
  });
}

/** Browser-friendly base64 encode for Uint8Array; chunk to avoid
 *  argument-length limits on large screenshots. */
export function bytesToBase64(bytes: Uint8Array): string {
  const CHUNK = 0x8000;
  let bin = "";
  for (let i = 0; i < bytes.length; i += CHUNK) {
    bin += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + CHUNK)));
  }
  return btoa(bin);
}
