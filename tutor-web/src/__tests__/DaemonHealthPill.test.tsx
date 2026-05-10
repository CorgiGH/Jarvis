import { render, screen, act } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { DaemonHealthPill } from "../components/DaemonHealthPill";

function stubHealth(payload: object) {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify(payload), { status: 200, headers: { "content-type": "application/json" } })
  ));
}

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe("DaemonHealthPill", () => {
  test("green DAEMON OK when reachable=true & tunnelUp=true", async () => {
    stubHealth({ reachable: true, tunnelUp: true, lastSeenAt: "2026-05-10T12:00:00Z" });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("green");
    expect(screen.getByText(/DAEMON OK/i)).toBeInTheDocument();
  });

  test("amber TUNNEL ONLY when reachable=false & tunnelUp=true", async () => {
    stubHealth({ reachable: false, tunnelUp: true, lastSeenAt: null });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("amber");
    expect(screen.getByText(/TUNNEL ONLY/i)).toBeInTheDocument();
  });

  test("red DAEMON DOWN when both false", async () => {
    stubHealth({ reachable: false, tunnelUp: false, lastSeenAt: null });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("red");
    expect(screen.getByText(/DAEMON DOWN/i)).toBeInTheDocument();
  });

  test("re-polls every 30s and updates", async () => {
    let callCount = 0;
    vi.stubGlobal("fetch", vi.fn(async () => {
      callCount++;
      const payload = callCount === 1
        ? { reachable: false, tunnelUp: false, lastSeenAt: null }
        : { reachable: true, tunnelUp: true, lastSeenAt: "2026-05-10T12:00:30Z" };
      return new Response(JSON.stringify(payload), { status: 200 });
    }));
    await act(async () => { render(<DaemonHealthPill />); });
    expect(screen.getByTestId("daemon-health-pill").getAttribute("data-status")).toBe("red");
    await act(async () => { vi.advanceTimersByTime(30_000); });
    expect(screen.getByTestId("daemon-health-pill").getAttribute("data-status")).toBe("green");
    expect(callCount).toBeGreaterThanOrEqual(2);
  });

  test("renders without crashing on fetch reject", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("net::ERR_FAILED"); }));
    await act(async () => { render(<DaemonHealthPill />); });
    expect(screen.getByTestId("daemon-health-pill")).toBeInTheDocument();
  });
});
