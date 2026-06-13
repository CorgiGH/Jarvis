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

describe("DrillStack CHECK card (REQ-29, E8)", () => {
  /** Helper: get the drill through to unlocked state so the CHECK card activates. */
  async function unlockDrillStack() {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("check-attempt-input")).toBeInTheDocument());
  }

  it("renders check-attempt-input textarea when the CHECK card is unlocked", async () => {
    await unlockDrillStack();
    expect(screen.getByTestId("check-attempt-input")).toBeInTheDocument();
  });

  it("renders check-submit-btn when the CHECK card is unlocked", async () => {
    await unlockDrillStack();
    expect(screen.getByTestId("check-submit-btn")).toBeInTheDocument();
  });

  it("renders check-giveup-btn when the CHECK card is unlocked", async () => {
    await unlockDrillStack();
    expect(screen.getByTestId("check-giveup-btn")).toBeInTheDocument();
  });

  it("MARK CHECK DONE button no longer exists (no-engagement path removed)", async () => {
    await unlockDrillStack();
    expect(screen.queryByText(/MARK CHECK DONE/i)).not.toBeInTheDocument();
  });

  it("phase → check-done ONLY after a graded check attempt", async () => {
    const onComplete = vi.fn();
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={onComplete} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("check-attempt-input")).toBeInTheDocument());
    // onComplete should NOT fire yet (just unlocked, no check attempt submitted)
    expect(onComplete).not.toHaveBeenCalled();
    // Submit the check attempt
    fireEvent.change(screen.getByTestId("check-attempt-input"), { target: { value: "3" } });
    fireEvent.click(screen.getByTestId("check-submit-btn"));
    await waitFor(() => expect(onComplete).toHaveBeenCalledWith("p1"));
  });

  it("check verdict renders after graded check attempt", async () => {
    await unlockDrillStack();
    fireEvent.change(screen.getByTestId("check-attempt-input"), { target: { value: "3" } });
    fireEvent.click(screen.getByTestId("check-submit-btn"));
    await waitFor(() => expect(screen.getByTestId("check-verdict")).toBeInTheDocument());
  });

  it("check-giveup-btn transitions to check-done without grading", async () => {
    const onComplete = vi.fn();
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={onComplete} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("check-giveup-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("check-giveup-btn"));
    await waitFor(() => expect(onComplete).toHaveBeenCalledWith("p1"));
  });

  it("check submission posts the CHECK stem as problemStatement via gradeDrill", async () => {
    await unlockDrillStack();
    fireEvent.change(screen.getByTestId("check-attempt-input"), { target: { value: "3" } });
    fireEvent.click(screen.getByTestId("check-submit-btn"));
    await waitFor(() => expect(lastBody?.problemStatement).toBe("c"));
  });

  it("check submit button is disabled when check-attempt-input is empty", async () => {
    await unlockDrillStack();
    expect(screen.getByTestId("check-submit-btn")).toBeDisabled();
  });
});
