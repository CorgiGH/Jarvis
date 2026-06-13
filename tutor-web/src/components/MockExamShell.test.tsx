import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { MockExamShell } from "./MockExamShell";

const THREE_QUESTIONS = [
  "Ce este un estimator consistent?",
  "Definiti convergenta in probabilitate.",
  "Enuntati legea numerelor mari.",
];

describe("MockExamShell", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it("renders mock-exam-shell", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-exam-shell")).toBeInTheDocument();
  });

  it("renders mock-timer", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-timer")).toBeInTheDocument();
  });

  it("renders all question blocks", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-question-0")).toBeInTheDocument();
    expect(screen.getByTestId("mock-question-1")).toBeInTheDocument();
    expect(screen.getByTestId("mock-question-2")).toBeInTheDocument();
  });

  it("renders mock-question-nav", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-question-nav")).toBeInTheDocument();
  });

  it("renders mock-submit-btn", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-submit-btn")).toBeInTheDocument();
  });

  it("submit button is disabled when not all questions answered", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    expect(screen.getByTestId("mock-submit-btn")).toBeDisabled();
  });

  it("submit button enables when all questions answered", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    const textareas = screen.getAllByRole("textbox");
    textareas.forEach((ta) => {
      fireEvent.change(ta, { target: { value: "raspuns" } });
    });
    expect(screen.getByTestId("mock-submit-btn")).not.toBeDisabled();
  });

  it("calls onSubmit with answers when submitted", () => {
    const onSubmit = vi.fn();
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={onSubmit} />);
    const textareas = screen.getAllByRole("textbox");
    textareas.forEach((ta, i) => {
      fireEvent.change(ta, { target: { value: `raspuns ${i}` } });
    });
    fireEvent.click(screen.getByTestId("mock-submit-btn"));
    expect(onSubmit).toHaveBeenCalledWith(["raspuns 0", "raspuns 1", "raspuns 2"]);
  });

  it("nav highlights the clicked question", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    const navBtns = screen.getAllByRole("button");
    fireEvent.click(navBtns[1]);
    expect(navBtns[1]).toHaveAttribute("data-active", "true");
  });

  // ── ADDITIVE mode (Plan-6 Task 11, REQ-11..17) — legacy mode unchanged, additive selectors present ──

  it("LEGACY mode: additive selectors are ABSENT when the additive props are omitted", () => {
    render(<MockExamShell subject="PA" questions={THREE_QUESTIONS} timeLimitSeconds={300} onSubmit={() => {}} />);
    // The phase / synthetic-tag / rubric-result selectors only render with additive props.
    expect(screen.queryByTestId("mock-exam-phase")).not.toBeInTheDocument();
    expect(screen.queryByTestId("mock-exam-synthetic-tag")).not.toBeInTheDocument();
    expect(screen.queryByTestId("mock-exam-rubric-result")).not.toBeInTheDocument();
    // Legacy selectors still present.
    expect(screen.getByTestId("mock-timer")).toBeInTheDocument();
    expect(screen.getByTestId("mock-submit-btn")).toBeInTheDocument();
  });

  it("ADDITIVE mode: renders mock-exam-timer, mock-exam-phase, mock-exam-question, synthetic-tag", () => {
    render(
      <MockExamShell
        subject="PA"
        questions={THREE_QUESTIONS}
        timeLimitSeconds={300}
        onSubmit={() => {}}
        phase={{ phaseIndex: 0, labelRo: "Fără materiale", materialsAllowedRo: "Niciun material permis", phaseCount: 2 }}
        syntheticTag={true}
      />
    );
    expect(screen.getByTestId("mock-exam-timer")).toBeInTheDocument();
    expect(screen.getByTestId("mock-exam-phase")).toBeInTheDocument();
    expect(screen.getByTestId("mock-exam-question")).toBeInTheDocument();
    expect(screen.getByTestId("mock-exam-synthetic-tag")).toBeInTheDocument();
    // The synthetic-tag copy is the RO honesty line (from practiceStrings, no EN learner copy).
    expect(screen.getByTestId("mock-exam-synthetic-tag").textContent).toMatch(/Subiect generat/);
  });

  it("ADDITIVE mode: phase advance button fires onAdvancePhase when not on the last phase", () => {
    const onAdvancePhase = vi.fn();
    render(
      <MockExamShell
        subject="ALO"
        questions={THREE_QUESTIONS}
        timeLimitSeconds={300}
        onSubmit={() => {}}
        phase={{ phaseIndex: 0, labelRo: "Fără materiale", materialsAllowedRo: "Niciun material permis", phaseCount: 2 }}
        onAdvancePhase={onAdvancePhase}
      />
    );
    fireEvent.click(screen.getByTestId("mock-exam-phase-advance"));
    expect(onAdvancePhase).toHaveBeenCalledTimes(1);
  });

  it("ADDITIVE mode: no phase-advance button on the last phase", () => {
    render(
      <MockExamShell
        subject="PA"
        questions={THREE_QUESTIONS}
        timeLimitSeconds={300}
        onSubmit={() => {}}
        phase={{ phaseIndex: 1, labelRo: "Materiale permise", materialsAllowedRo: "Documentație", phaseCount: 2 }}
        onAdvancePhase={() => {}}
      />
    );
    expect(screen.queryByTestId("mock-exam-phase-advance")).not.toBeInTheDocument();
  });

  it("ADDITIVE mode: renders mock-exam-rubric-result with per-G-item breakdown", () => {
    render(
      <MockExamShell
        subject="PA"
        questions={THREE_QUESTIONS}
        timeLimitSeconds={300}
        onSubmit={() => {}}
        rubricResult={[
          { id: "G1", label: "G1 reducere", passed: true, points_earned: 1, points_max: 1 },
          { id: "G2", label: "G2 corect", passed: false, points_earned: 0, points_max: 1 },
        ]}
      />
    );
    expect(screen.getByTestId("mock-exam-rubric-result")).toBeInTheDocument();
    expect(screen.getByTestId("mock-exam-rubric-item-G1")).toBeInTheDocument();
    expect(screen.getByTestId("mock-exam-rubric-item-G2")).toBeInTheDocument();
  });
});
