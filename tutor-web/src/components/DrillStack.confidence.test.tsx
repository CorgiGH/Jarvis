import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "w", definition: "d", check: "c", expectedAnswerHint: "5",
};

let lastBody: any = null;
beforeEach(() => {
  lastBody = null;
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      lastBody = JSON.parse(String(init?.body ?? "{}"));
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null, elaboratedFeedback: "ok",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack confidence row (H16)", () => {
  it("renders the four-value confidence row pre-grade", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    expect(screen.getByTestId("drill-confidence-row")).toBeInTheDocument();
  });

  it("passes the selected confidence to gradeDrill", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.click(screen.getByTestId("confidence-MAYBE"));
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(lastBody?.studentConfidence).toBe("MAYBE"));
  });
});
