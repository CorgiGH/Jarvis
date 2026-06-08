import { describe, it, expect, vi, beforeEach } from "vitest";
import { getQueueToday } from "./taskPrep";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
});

describe("getQueueToday", () => {
  it("GETs /api/v1/queue/today and returns the envelope", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        items: [{
          kc_id: "kc-1", kc_name_ro: "Recursie", kc_name_en: "Recursion",
          subject: "PA", phase: "practice", mastery_ewma: 0.4, fsrs_card_id: null,
          verification_status: "faithful", worked_example_first: true, mode: "drill",
        }],
        total_due: 1, day: "2026-06-08",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const q = await getQueueToday();
    expect(calls[0]).toContain("/api/v1/queue/today");
    expect(q?.items[0].kc_id).toBe("kc-1");
    expect(q?.items[0].mode).toBe("drill");
  });

  it("returns null on 401 (auth gate)", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 401 })));
    expect(await getQueueToday()).toBeNull();
  });
});
