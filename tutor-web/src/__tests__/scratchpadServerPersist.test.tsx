import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

test("workspace GETs scratchpad on mount + PUTs after 500ms debounce on change", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/tasks/T1/scratchpad") && (!init || (init.method ?? "GET") === "GET")) {
      return new Response(JSON.stringify({ text: "loaded from server" }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tasks/T1/scratchpad") && init?.method === "PUT") {
      return new Response(JSON.stringify({ text: "ack" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));

  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);

  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/scratchpad") && (!c[1] || (c[1].method ?? "GET") === "GET"));
    expect(calls.length).toBeGreaterThan(0);
  });

  vi.useFakeTimers({ shouldAdvanceTime: true });
  const textarea = screen.getByTestId("scratchpad-input") as HTMLTextAreaElement;
  fireEvent.change(textarea, { target: { value: "user wrote" } });
  const beforePut = (globalThis.fetch as any).mock.calls.filter((c: any) =>
    typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/scratchpad") && c[1]?.method === "PUT").length;
  vi.advanceTimersByTime(550);
  await vi.waitFor(() => {
    const afterPut = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/scratchpad") && c[1]?.method === "PUT").length;
    expect(afterPut).toBeGreaterThan(beforePut);
  });
});
