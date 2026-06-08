import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
  provenance: { type: "authored", hasBeenFaithfulChecked: true },
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null,
        elaboratedFeedback: "Corect!", verification_status: "faithful",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts TrustBadge for authored faithful content", () => {
  it("paints the faithful trust badge after a graded drill", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("trust-badge")).toBeInTheDocument());
    expect(screen.getByTestId("trust-badge")).toHaveAttribute("data-faithful", "true");
  });
});
