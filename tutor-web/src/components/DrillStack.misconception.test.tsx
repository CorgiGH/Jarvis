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
        correct: false, score: 0.1, rubric: {}, misconception: "OFF_BY_ONE",
        elaboratedFeedback: "Aproape.",
        misconception_payload: {
          id: "OFF_BY_ONE", refutation: "Cazul de bază oprește la n<=1.",
          figure_spec: null, self_explanation_prompt: null,
          source: { doc: "curs3.pdf", page: 12, span: null, quote: "caz" },
        },
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts MisconceptionRibbon", () => {
  it("paints the ribbon with the cited refutation on an incorrect grade", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "4" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("misconception-ribbon")).toBeInTheDocument());
    expect(screen.getByTestId("misconception-ribbon-citation")).toHaveTextContent("curs3.pdf");
  });
});
