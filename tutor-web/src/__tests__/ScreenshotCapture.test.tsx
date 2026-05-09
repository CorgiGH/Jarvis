import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ScreenshotCapture } from "../components/ScreenshotCapture";

const fakeImage = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=ttt", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/sensor/screenshot")) {
      return new Response(JSON.stringify({
        eventSeq: 7,
        extracted: {
          filePath: "src/foo.kt",
          cursor: { line: 12, col: 3 },
          consoleOutput: "compiled",
          error: null,
          rawReply: "{...}",
        },
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));

  // Stub captureScreenshot via the lib/screenshot module — easier than
  // wiring full getDisplayMedia in jsdom which doesn't ship MediaDevices.
  vi.doMock("../lib/screenshot", () => ({
    captureScreenshot: vi.fn(async () => ({
      imageBase64: fakeImage,
      capturedAt: new Date().toISOString(),
    })),
    bytesToBase64: (b: Uint8Array) => Buffer.from(b).toString("base64"),
  }));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.doUnmock("../lib/screenshot");
  vi.resetModules();
});

test("renders idle camera button", async () => {
  const { ScreenshotCapture: Fresh } = await import("../components/ScreenshotCapture");
  render(<Fresh taskId="T1" />);
  expect(screen.getByTestId("screenshot-capture-btn")).toBeInTheDocument();
  expect(screen.getByText(/capture/i)).toBeInTheDocument();
});

test("clicking the button POSTs to /api/v1/sensor/screenshot and emits result", async () => {
  const { ScreenshotCapture: Fresh } = await import("../components/ScreenshotCapture");
  const onResult = vi.fn();
  render(<Fresh taskId="T1" onResult={onResult} />);
  fireEvent.click(screen.getByTestId("screenshot-capture-btn"));

  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/api/v1/sensor/screenshot"));
    expect(calls.length).toBe(1);
    const init = calls[0][1];
    expect(init.method).toBe("POST");
    expect(init.headers["X-CSRF-Token"]).toBe("ttt");
    const body = JSON.parse(init.body);
    expect(body.imageBase64).toBe(fakeImage);
    expect(body.taskId).toBe("T1");
    expect(body.mediaType).toBe("image/png");
  });

  await waitFor(() => {
    expect(onResult).toHaveBeenCalledTimes(1);
    expect(onResult.mock.calls[0][0].extracted.filePath).toBe("src/foo.kt");
  });
});

test("captures on Ctrl+Shift+J hotkey", async () => {
  const { ScreenshotCapture: Fresh } = await import("../components/ScreenshotCapture");
  const onResult = vi.fn();
  render(<Fresh taskId="T2" onResult={onResult} />);
  fireEvent.keyDown(window, { key: "J", ctrlKey: true, shiftKey: true });
  await waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
});

test("shows error on backend failure", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("server error", { status: 500 })));
  const { ScreenshotCapture: Fresh } = await import("../components/ScreenshotCapture");
  render(<Fresh taskId="T3" />);
  fireEvent.click(screen.getByTestId("screenshot-capture-btn"));
  await waitFor(() => expect(screen.getByTestId("screenshot-error")).toBeInTheDocument());
  expect(screen.getByTestId("screenshot-error").textContent).toMatch(/HTTP 500/);
});
