import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockGradeReport } from "./MockGradeReport";

const REPORT = {
  subject: "PA",
  score: 0.75,
  previousScore: 0.6,
  results: [
    { index: 0, question: "Ce este un estimator consistent?", answer: "Un estimator...", correct: true, uncertain: false },
    { index: 1, question: "Convergenta in probabilitate.", answer: "...", correct: false, uncertain: true },
    { index: 2, question: "Legea numerelor mari.", answer: "LMN...", correct: true, uncertain: false },
  ],
};

describe("MockGradeReport", () => {
  it("renders mock-grade-report", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-grade-report")).toBeInTheDocument();
  });

  it("renders mock-score-plate with score", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-score-plate")).toBeInTheDocument();
    expect(screen.getByTestId("mock-score-plate")).toHaveTextContent("75%");
  });

  it("renders mock-score-sparkline", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-score-sparkline")).toBeInTheDocument();
  });

  it("renders a result row per question", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-question-result-0")).toBeInTheDocument();
    expect(screen.getByTestId("mock-question-result-1")).toBeInTheDocument();
    expect(screen.getByTestId("mock-question-result-2")).toBeInTheDocument();
  });

  it("shows UNCERTAIN badge on uncertain results", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    const row1 = screen.getByTestId("mock-question-result-1");
    expect(row1).toHaveAttribute("data-uncertain", "true");
  });

  it("does not show UNCERTAIN on certain results", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    const row0 = screen.getByTestId("mock-question-result-0");
    expect(row0).toHaveAttribute("data-uncertain", "false");
  });

  it("renders redo CTA button", () => {
    render(<MockGradeReport {...REPORT} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-redo-btn")).toBeInTheDocument();
  });

  it("calls onRedoWrong when redo CTA is clicked", () => {
    const onRedoWrong = vi.fn();
    render(<MockGradeReport {...REPORT} onRedoWrong={onRedoWrong} />);
    fireEvent.click(screen.getByTestId("mock-redo-btn"));
    expect(onRedoWrong).toHaveBeenCalledOnce();
  });

  it("200 reply scenario: score plate renders with score", () => {
    render(<MockGradeReport {...REPORT} score={1.0} previousScore={null} onRedoWrong={() => {}} />);
    expect(screen.getByTestId("mock-score-plate")).toHaveTextContent("100%");
  });
});
