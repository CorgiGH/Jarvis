import { describe, expect, test, vi, beforeEach, afterEach } from "vitest";
import { jarvisFetch, getCsrfToken } from "../lib/api";

describe("api", () => {
  beforeEach(() => {
    Object.defineProperty(document, "cookie", {
      value: "csrf=abc123; foo=bar",
      configurable: true,
      writable: true,
    });
    vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", {
      status: 200, headers: { "content-type": "application/json" }
    })));
  });
  afterEach(() => { vi.unstubAllGlobals(); });

  test("getCsrfToken reads csrf cookie", () => {
    expect(getCsrfToken()).toBe("abc123");
  });

  test("jarvisFetch GET sends credentials and no csrf header", async () => {
    await jarvisFetch("/api/v1/health");
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].credentials).toBe("include");
    expect(call[1].headers?.["X-CSRF-Token"]).toBeUndefined();
  });

  test("jarvisFetch POST sends X-CSRF-Token header from cookie", async () => {
    await jarvisFetch("/api/v1/whatever", { method: "POST", body: JSON.stringify({}) });
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers["X-CSRF-Token"]).toBe("abc123");
  });
});
