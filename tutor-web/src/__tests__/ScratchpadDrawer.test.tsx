/**
 * ScratchpadDrawer.test.tsx
 *
 * Covers the contract introduced when ResourceRail's SCRATCHPAD case was
 * extracted into a real component (Phase 3 [3] backlog close):
 *   - GET /scratchpad fires once on mount, and never re-fires on clear
 *     (hydration ref prevents the prior race where re-render with empty
 *     scratch overwrote the user's intentional clear with stale text).
 *   - PUT /scratchpad is debounced (500ms) — a flood of keystrokes
 *     produces 1 PUT.
 *   - Save status pill transitions: idle → saving → saved (auto-fades to
 *     idle after a short banner window) on success; idle → saving → error
 *     on failure, with the HTTP error message surfaced.
 */
import { render, screen, waitFor, act, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ScratchpadDrawer } from "../components/ScratchpadDrawer";

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});
afterEach(() => {
  vi.useRealTimers();
});

function mockFetch(handler: (url: string, init?: RequestInit) => Promise<Response>) {
  return vi.fn(handler) as unknown as typeof fetch;
}

test("hydrates from server on mount, displays returned text", async () => {
  const fetchImpl = mockFetch(async (url) => {
    if (url.includes("/scratchpad")) {
      return new Response(JSON.stringify({ text: "hello from server" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  });
  render(<ScratchpadDrawer taskId="T1" fetchImpl={fetchImpl as any} />);
  await waitFor(() => {
    expect((screen.getByTestId("scratchpad-input") as HTMLTextAreaElement).value).toBe("hello from server");
  });
  // Only one GET fired.
  expect((fetchImpl as any).mock.calls.filter((c: any[]) => !c[1] || c[1].method !== "PUT").length).toBe(1);
});

test("debounces PUT — 3 keystrokes within 500ms produce 1 PUT carrying the latest text", async () => {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fetchImpl = mockFetch(async (url, init) => {
    calls.push({ url, init });
    if ((init?.method ?? "GET") === "GET") {
      return new Response(JSON.stringify({ text: "" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  });
  render(<ScratchpadDrawer taskId="T1" fetchImpl={fetchImpl as any} />);
  await waitFor(() => screen.getByTestId("scratchpad-input"));
  const ta = screen.getByTestId("scratchpad-input") as HTMLTextAreaElement;

  fireEvent.change(ta, { target: { value: "a" } });
  fireEvent.change(ta, { target: { value: "ab" } });
  fireEvent.change(ta, { target: { value: "abc" } });

  // Status shows "saving" while debounce window is open.
  expect(screen.getByTestId("scratchpad-status")).toHaveAttribute("data-status", "saving");

  // Advance past debounce window.
  await act(async () => {
    vi.advanceTimersByTime(600);
  });

  const puts = calls.filter(c => c.init?.method === "PUT");
  expect(puts.length).toBe(1);
  expect(JSON.parse(puts[0].init!.body as string)).toEqual({ text: "abc" });
});

test("PUT failure sets status=error with message; success transitions saving → saved", async () => {
  let putShouldFail = false;
  const fetchImpl = mockFetch(async (url, init) => {
    if ((init?.method ?? "GET") === "GET") {
      return new Response(JSON.stringify({ text: "" }), { status: 200 });
    }
    if (putShouldFail) {
      return new Response("nope", { status: 500 });
    }
    return new Response("{}", { status: 200 });
  });
  render(<ScratchpadDrawer taskId="T1" fetchImpl={fetchImpl as any} />);
  await waitFor(() => screen.getByTestId("scratchpad-input"));
  const ta = screen.getByTestId("scratchpad-input") as HTMLTextAreaElement;

  // First change: success path.
  fireEvent.change(ta, { target: { value: "ok" } });
  await act(async () => { vi.advanceTimersByTime(600); });
  await waitFor(() => {
    expect(screen.getByTestId("scratchpad-status")).toHaveAttribute("data-status", "saved");
  });

  // Second change: failure path.
  putShouldFail = true;
  fireEvent.change(ta, { target: { value: "fail-next" } });
  await act(async () => { vi.advanceTimersByTime(600); });
  await waitFor(() => {
    const pill = screen.getByTestId("scratchpad-status");
    expect(pill).toHaveAttribute("data-status", "error");
    expect(pill).toHaveAttribute("title", expect.stringContaining("HTTP 500"));
  });
});

test("does NOT re-fetch when scratch becomes empty (hydration ref guards against clobber race)", async () => {
  const calls: string[] = [];
  const fetchImpl = mockFetch(async (url, init) => {
    if ((init?.method ?? "GET") === "GET" && url.includes("/scratchpad")) {
      calls.push(url);
      return new Response(JSON.stringify({ text: "initial" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  });
  render(<ScratchpadDrawer taskId="T1" fetchImpl={fetchImpl as any} />);
  await waitFor(() => {
    expect((screen.getByTestId("scratchpad-input") as HTMLTextAreaElement).value).toBe("initial");
  });
  expect(calls.length).toBe(1);

  // User clears the field intentionally. Pre-fix, this re-fired GET and
  // overwrote the clear. Post-fix, hydratedRef.current is true → no
  // second GET.
  const ta = screen.getByTestId("scratchpad-input") as HTMLTextAreaElement;
  fireEvent.change(ta, { target: { value: "" } });
  await act(async () => { vi.advanceTimersByTime(600); });

  expect(calls.length).toBe(1);  // still one GET only
  expect(ta.value).toBe("");      // user's clear preserved
});
