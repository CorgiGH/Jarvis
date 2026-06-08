import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";
import { ThemeProvider } from "../theme/ThemeProvider";

vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(async () => ({ ok: true, json: async () => ({}) })),
}));

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=cc", configurable: true, writable: true });
  try { localStorage.clear(); } catch (_) {}
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/daemon/health")) {
      return new Response(JSON.stringify({ reachable: true, tunnelUp: true, lastSeenAt: null }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("App ?debug=1 toggle", () => {
  it("hides DaemonHealthPill when debug query absent (default)", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1"]}>
        <ThemeProvider><App /></ThemeProvider>
      </MemoryRouter>
    );
    expect(screen.queryByTestId("daemon-health-pill")).toBeNull();
  });

  it("shows DaemonHealthPill when debug=1 query present", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1&debug=1"]}>
        <ThemeProvider><App /></ThemeProvider>
      </MemoryRouter>
    );
    expect(screen.queryByTestId("daemon-health-pill")).not.toBeNull();
  });

  it("renders domain footer as 'READY' by default (no domain-footer testid)", () => {
    render(
      <MemoryRouter initialEntries={["/?taskId=t1"]}>
        <ThemeProvider><App /></ThemeProvider>
      </MemoryRouter>
    );
    expect(screen.queryByTestId("domain-footer")).toBeNull();
    expect(screen.getByText(/^READY$/)).toBeTruthy();
  });
});
