import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: false, score: 0.2, rubric: {}, misconception: "OFF_BY_ONE",
        elaboratedFeedback: "Aproape.",
        ladder_rungs: [
          { level: 0, text: "Uită-te din nou." },
          { level: 1, text: "Explică-ți." },
        ],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts FeedbackLadder after grading", () => {
  it("paints the ladder with served rungs on an incorrect grade", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "4" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("drill-feedback-ladder")).toBeInTheDocument());
    expect(screen.getByTestId("feedback-rung-0")).toHaveTextContent("Uită-te din nou.");
  });
});
