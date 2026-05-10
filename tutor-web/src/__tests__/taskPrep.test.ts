import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { getTaskPrep, submitTask } from "../lib/taskPrep";
import type { TaskPrepReply, RailItem } from "../lib/taskPrep";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("getTaskPrep", () => {
  test("returns null on 404", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("no prep", { status: 404 })));
    const result = await getTaskPrep("task-01");
    expect(result).toBeNull();
  });

  test("returns parsed shape on 200", async () => {
    const payload: TaskPrepReply = {
      taskId: "task-01",
      generatedAt: "2026-05-11T00:00:00Z",
      version: 1,
      problemsJson: '[{"problem_id":"A1","page":4,"statement":"x"}]',
      drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
      railJson: '[{"type":"PDF","label":"Tema_A.pdf","action":"OPEN_DRAWER","payload":{"path":"x"}}]',
    };
    vi.stubGlobal("fetch", vi.fn(async () =>
      new Response(JSON.stringify(payload), { status: 200, headers: { "content-type": "application/json" } })));
    const result = await getTaskPrep("task-01");
    expect(result).not.toBeNull();
    expect(result!.taskId).toBe("task-01");
    expect(result!.railJson).toContain("PDF");
  });

  test("throws on non-2xx non-404 status", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));
    await expect(getTaskPrep("task-01")).rejects.toThrow(/500/);
  });
});

describe("submitTask", () => {
  test("POSTs to /tasks/{id}/submit and returns reply", async () => {
    vi.stubGlobal("fetch", vi.fn(async () =>
      new Response(JSON.stringify({ taskId: "t1", status: "SUBMITTED", submittedAt: "2026-05-11T00:00:00Z" }),
        { status: 200, headers: { "content-type": "application/json" } })));
    const r = await submitTask("t1", "my note");
    expect(r.status).toBe("SUBMITTED");
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain("/api/v1/tasks/t1/submit");
    expect(call[1].method).toBe("POST");
    expect(JSON.parse(call[1].body)).toEqual({ note: "my note" });
  });

  test("submitTask throws on non-2xx", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("bad", { status: 403 })));
    await expect(submitTask("t1")).rejects.toThrow(/403/);
  });
});

describe("RailItem type", () => {
  test("RailItem JSON shape parses", () => {
    const raw = '[{"type":"PDF","label":"x","action":"OPEN_DRAWER","payload":{"path":"y"}}]';
    const parsed: RailItem[] = JSON.parse(raw);
    expect(parsed[0].type).toBe("PDF");
    expect(parsed[0].action).toBe("OPEN_DRAWER");
  });
});
