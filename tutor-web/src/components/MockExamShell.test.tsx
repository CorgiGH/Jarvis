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
});
