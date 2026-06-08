import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

function makeContent(p: DrillContent["provenance"]): DrillContent {
  return {
    drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
    check: "Trace fib(4).", expectedAnswerHint: "5", provenance: p,
  };
}

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null,
        elaboratedFeedback: "ok", verification_status: "faithful",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack provenance rendering boundary", () => {
  it("generated content shows the provenance badge, NOT the faithful trust badge", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "generated", hasBeenFaithfulChecked: false })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("provenance-badge")).toBeInTheDocument());
    // The faithful badge must NOT span generated content.
    expect(screen.queryByTestId("trust-badge")).not.toBeInTheDocument();
  });

  it("authored content shows the faithful trust badge, NOT the provenance badge", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "authored", hasBeenFaithfulChecked: true })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("trust-badge")).toBeInTheDocument());
    expect(screen.queryByTestId("provenance-badge")).not.toBeInTheDocument();
  });

  it("INVARIANT: no [data-content-block] ever contains both faithful + generated badges", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "generated", hasBeenFaithfulChecked: false })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("provenance-badge")).toBeInTheDocument());
    document.querySelectorAll("[data-content-block]").forEach((block) => {
      const hasFaithful = block.querySelector('[data-faithful="true"]');
      const hasGenerated = block.querySelector('[data-provenance-type="generated"]');
      expect(hasFaithful && hasGenerated).toBeFalsy();
    });
  });
});
