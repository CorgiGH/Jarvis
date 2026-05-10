import { vi, beforeEach, afterEach, describe, test, expect } from "vitest";
import { getDue, gradeCard, getForecast } from "../lib/fsrsClient";

beforeEach(() => {
  Object.defineProperty(document, "cookie", {
    value: "csrf=testcsrf",
    configurable: true,
    writable: true,
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("getDue", () => {
  test("calls /api/v1/fsrs/due and returns card array", async () => {
    const payload = {
      cards: [
        {
          id: "c1",
          front: "Q1",
          back: "A1",
          sourceTaskId: "t1",
          difficulty: 2.1,
          stability: 4.0,
          retrievability: 0.9,
          dueAt: "2026-05-10T10:00:00Z",
          lapses: 0,
        },
      ],
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const cards = await getDue(10);
    expect(cards).toHaveLength(1);
    expect(cards[0].id).toBe("c1");
    expect(cards[0].front).toBe("Q1");
    expect(cards[0].lapses).toBe(0);
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain("/api/v1/fsrs/due");
    expect(call[0]).toContain("limit=10");
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Unauthorized", { status: 401 })),
    );
    await expect(getDue()).rejects.toThrow("401");
  });
});

describe("gradeCard", () => {
  test("posts grade to /api/v1/fsrs/{id}/grade and returns reply", async () => {
    const payload = {
      cardId: "c1",
      nextDueAt: "2026-05-14T10:00:00Z",
      newDifficulty: 2.2,
      newStability: 4.5,
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const reply = await gradeCard("c1", 3);
    expect(reply.cardId).toBe("c1");
    expect(reply.newStability).toBe(4.5);
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain("/api/v1/fsrs/c1/grade");
    expect(call[1].method).toBe("POST");
    expect(JSON.parse(call[1].body)).toEqual({ grade: 3 });
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Not Found", { status: 404 })),
    );
    await expect(gradeCard("missing", 2)).rejects.toThrow("404");
  });
});

describe("getForecast", () => {
  test("calls /api/v1/fsrs/forecast and returns counts", async () => {
    const payload = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const f = await getForecast();
    expect(f.tomorrow).toBe(4);
    expect(f.thisWeek).toBe(18);
    expect(f.thisMonth).toBe(41);
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Server Error", { status: 500 })),
    );
    await expect(getForecast()).rejects.toThrow("500");
  });
});
