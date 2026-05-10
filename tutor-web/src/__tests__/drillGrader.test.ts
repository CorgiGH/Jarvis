import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { gradeDrill } from "../lib/drillGrader";
import type { GradeResult } from "../lib/drillGrader";

const MOCK_GRADE_RESULT: GradeResult = {
  correct: true,
  score: 1.0,
  rubric: { numeric: true, mechanism: true, justification: true },
  misconception: null,
  elaboratedFeedback:
    "✓ correct. Mechanism cited (median). Reasoning: Σ|x_i − μ| minimized at sample median.",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", {
    value: "csrf=abc123",
    configurable: true,
    writable: true,
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe("gradeDrill", () => {
  test("POSTs to /api/v1/drill/grade with correct body and CSRF header", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(MOCK_GRADE_RESULT), {
          status: 200,
          headers: { "content-type": "application/json" },
        })
      )
    );

    const result = await gradeDrill({
      taskId: "task-01",
      problemId: "A3",
      problemStatement: "Sample x=(3,7,8,9,14). What is μ̂ MLE for Laplace?",
      userAttempt: "μ̂ = 8 because median",
      expectedAnswerHint: "median equals 8",
    });

    const fetchMock = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(fetchMock[0]).toBe("/api/v1/drill/grade");
    expect(fetchMock[1].method).toBe("POST");
    expect(fetchMock[1].headers["X-CSRF-Token"]).toBe("abc123");
    const body = JSON.parse(fetchMock[1].body as string);
    expect(body.taskId).toBe("task-01");
    expect(body.problemId).toBe("A3");
    expect(body.userAttempt).toBe("μ̂ = 8 because median");

    expect(result.correct).toBe(true);
    expect(result.score).toBe(1.0);
    expect(result.misconception).toBeNull();
    expect(result.elaboratedFeedback).toContain("median");
  });

  test("returns GradeResult with misconception on incorrect attempt", async () => {
    const misconceptionResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: "L2_ESTIMATOR_CONFUSION",
      elaboratedFeedback: "you computed the mean (L2 MLE), not the Laplace median MLE",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(misconceptionResult), {
          status: 200,
          headers: { "content-type": "application/json" },
        })
      )
    );

    const result = await gradeDrill({
      taskId: "task-01",
      problemId: "A3",
      problemStatement: "What is μ̂?",
      userAttempt: "μ̂ = 8.2 (sum/n)",
      expectedAnswerHint: "median equals 8",
    });

    expect(result.correct).toBe(false);
    expect(result.misconception).toBe("L2_ESTIMATOR_CONFUSION");
    expect(result.rubric.numeric).toBe(false);
  });

  test("throws on non-OK response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response("LLM unavailable", { status: 502 })
      )
    );

    await expect(
      gradeDrill({
        taskId: "t1",
        problemId: "A1",
        problemStatement: "p",
        userAttempt: "a",
        expectedAnswerHint: "h",
      })
    ).rejects.toThrow(/502/);
  });
});
