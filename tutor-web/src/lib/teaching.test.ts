import { describe, it, expect, vi, beforeEach } from "vitest";
import { getVerifyStatus, getTeaching } from "./teaching";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
});

describe("teaching lib", () => {
  it("getVerifyStatus GETs /api/v1/verify/{kcId}/status and returns the reply", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        verification_status: "faithful", badge_text: "matches your lecture",
        claims: [], honest_floor: "FAITHFUL_TO_SOURCE",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const r = await getVerifyStatus("kc-1");
    expect(calls[0]).toContain("/api/v1/verify/kc-1/status");
    expect(r?.badge_text).toBe("matches your lecture");
  });

  it("getTeaching returns null on 404 (FAIL-LOUD non-faithful gate)", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));
    const r = await getTeaching("kc-x");
    expect(r).toBeNull();
  });

  it("getTeaching returns the faithful teaching payload on 200", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        kcId: "kc-1", name_ro: "Recursie",
        explanation_ro: "O funcție definită prin ea însăși.",
        worked_example_ro: "fib(5)=5",
        provenance: { type: "authored", hasBeenFaithfulChecked: true },
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const r = await getTeaching("kc-1");
    expect(calls[0]).toContain("/api/v1/teaching/kc-1");
    expect(r?.explanation_ro).toBe("O funcție definită prin ea însăși.");
    expect(r?.provenance.hasBeenFaithfulChecked).toBe(true);
  });
});
