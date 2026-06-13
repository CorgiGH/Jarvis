import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { StepTraceDrill } from "./StepTraceDrill";
import type { PracticeProblem, TraceStepReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

// Mock practiceApi so no real network calls happen in tests.
vi.mock("../../lib/practiceApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/practiceApi")>();
  return {
    ...actual,
    gradeTraceStep: vi.fn(),
  };
});

import { gradeTraceStep } from "../../lib/practiceApi";
const mockGradeTraceStep = gradeTraceStep as ReturnType<typeof vi.fn>;

/** A PA trace problem with ≥3 steps (required: Task 13 step-3 pin REQ-5). */
const TRACE_PROBLEM: PracticeProblem = {
  id: "pa-prob-trace-001",
  subject: "PA",
  archetype: "algorithm-trace",
  surface: "trace",
  statement_ro: "Urmăriți execuția algoritmului Fibonacci pentru fib(4).",
  trace_steps: [
    { index: 0, label_ro: "fib(1)" },
    { index: 1, label_ro: "fib(2)" },
    { index: 2, label_ro: "fib(3)" },
    { index: 3, label_ro: "fib(4)" },
  ],
};

const STEP_REPLY_CORRECT: TraceStepReply = {
  verdict: {
    id: "0",
    label: "fib(1)",
    passed: true,
    points_earned: 1,
    points_max: 1,
  },
  feedback_ro: "Pas corect — continuă.",
};

const STEP_REPLY_WRONG: TraceStepReply = {
  verdict: {
    id: "2",
    label: "fib(3)",
    passed: false,
    points_earned: 0,
    points_max: 1,
  },
  feedback_ro: "Valoare incorectă — încearcă din nou.",
};

describe("StepTraceDrill", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders trace-drill-skeleton testid", () => {
    render(<StepTraceDrill problem={TRACE_PROBLEM} />);
    expect(screen.getByTestId("trace-drill-skeleton")).toBeInTheDocument();
  });

  it("renders trace-drill-step-input testid for the current step", () => {
    render(<StepTraceDrill problem={TRACE_PROBLEM} />);
    expect(screen.getByTestId("trace-drill-step-input")).toBeInTheDocument();
  });

  it("does not show trace-drill-step-verdict before any submission", () => {
    render(<StepTraceDrill problem={TRACE_PROBLEM} />);
    expect(screen.queryByTestId("trace-drill-step-verdict")).not.toBeInTheDocument();
  });

  it("shows trace-drill-step-verdict after a correct step submission", async () => {
    mockGradeTraceStep.mockResolvedValue(STEP_REPLY_CORRECT);
    render(<StepTraceDrill problem={TRACE_PROBLEM} />);

    fireEvent.change(screen.getByTestId("trace-drill-step-input"), {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("trace-drill-submit-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("trace-drill-step-verdict")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("trace-drill-step-verdict")).toHaveTextContent(
      practiceStrings.traceDrillStepCorrect,
    );
  });

  it("blocks advance on wrong step — verdict shows at the wrong step (REQ-5)", async () => {
    // Simulate wrong answer at step 3 (index 2, the 3rd step — one-based)
    // Steps 0 and 1 pass, step 2 fails — still on step 2 after failure
    mockGradeTraceStep
      .mockResolvedValueOnce({
        verdict: { id: "0", label: "fib(1)", passed: true, points_earned: 1, points_max: 1 },
        feedback_ro: "Pas corect — continuă.",
      })
      .mockResolvedValueOnce({
        verdict: { id: "1", label: "fib(2)", passed: true, points_earned: 1, points_max: 1 },
        feedback_ro: "Pas corect — continuă.",
      })
      .mockResolvedValueOnce(STEP_REPLY_WRONG);

    render(<StepTraceDrill problem={TRACE_PROBLEM} />);

    // Step 0
    fireEvent.change(screen.getByTestId("trace-drill-step-input"), { target: { value: "1" } });
    fireEvent.click(screen.getByTestId("trace-drill-submit-btn"));
    await waitFor(() => expect(mockGradeTraceStep).toHaveBeenCalledTimes(1));

    // Step 1
    fireEvent.change(screen.getByTestId("trace-drill-step-input"), { target: { value: "1" } });
    fireEvent.click(screen.getByTestId("trace-drill-submit-btn"));
    await waitFor(() => expect(mockGradeTraceStep).toHaveBeenCalledTimes(2));

    // Step 2 — wrong answer
    fireEvent.change(screen.getByTestId("trace-drill-step-input"), { target: { value: "99" } });
    fireEvent.click(screen.getByTestId("trace-drill-submit-btn"));
    await waitFor(() => expect(mockGradeTraceStep).toHaveBeenCalledTimes(3));

    // Verdict appears at step 2 (wrong), user is NOT advanced
    expect(screen.getByTestId("trace-drill-step-verdict")).toHaveTextContent(
      practiceStrings.traceDrillStepWrong,
    );
    // The step input is still visible — user is NOT past step 2
    expect(screen.getByTestId("trace-drill-step-input")).toBeInTheDocument();
  });

  it("uses only practiceStrings for learner copy — no hardcoded RO text", () => {
    render(<StepTraceDrill problem={TRACE_PROBLEM} />);
    expect(
      screen.getByTestId("trace-drill-submit-btn"),
    ).toHaveTextContent(practiceStrings.traceDrillSubmitStep);
  });
});
