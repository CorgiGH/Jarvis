import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { recordTelemetry, readTelemetryCounter } from "../lib/telemetry";

describe("recordTelemetry", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 204 })));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("bumps localStorage counter + sets last_ts", () => {
    recordTelemetry("ledger.opened");
    const { count, lastTs } = readTelemetryCounter("ledger.opened");
    expect(count).toBe(1);
    expect(lastTs).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  test("multiple calls increment counter", () => {
    recordTelemetry("ledger.opened");
    recordTelemetry("ledger.opened");
    recordTelemetry("ledger.opened");
    expect(readTelemetryCounter("ledger.opened").count).toBe(3);
  });

  test("POSTs to /api/v1/sensor/telemetry with name + ts + payload", () => {
    const fetchMock = vi.mocked(global.fetch);
    recordTelemetry("ledger.opened", { from: "header" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toContain("/api/v1/sensor/telemetry");
    expect(init?.method).toBe("POST");
    const body = JSON.parse(init?.body as string);
    expect(body.name).toBe("ledger.opened");
    expect(body.ts).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    expect(body.payload).toEqual({ from: "header" });
  });

  test("swallows fetch errors silently", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("network"); }));
    // Should not throw.
    expect(() => recordTelemetry("ledger.opened")).not.toThrow();
    // localStorage still bumped despite fetch failure.
    expect(readTelemetryCounter("ledger.opened").count).toBe(1);
  });

  test("readTelemetryCounter returns zeros for unknown event", () => {
    expect(readTelemetryCounter("does.not.exist")).toEqual({ count: 0, lastTs: null });
  });
});
