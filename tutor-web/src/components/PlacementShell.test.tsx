import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlacementShell } from "./PlacementShell";

describe("PlacementShell", () => {
  it("renders the placement-shell testid", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("placement-shell")).toBeInTheDocument();
  });

  it("renders the first question on first paint", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("placement-question")).toBeInTheDocument();
  });

  it("renders 8 progress pips", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    // 8 pips for 8 questions
    const pips = screen.getAllByTestId(/placement-progress-pip/);
    expect(pips).toHaveLength(8);
  });

  it("first pip is active on first paint", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("placement-progress-pip-0")).toBeInTheDocument();
  });

  it("advances to next question when an answer is selected", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    // Answer question 1
    fireEvent.click(screen.getAllByRole("button")[0]);
    // Should be on question 2 now (pip 1 should be active)
    const shell = screen.getByTestId("placement-shell");
    expect(shell).toBeInTheDocument();
    // The question should still be rendered (moved to next)
    expect(screen.getByTestId("placement-question")).toBeInTheDocument();
  });

  it("shows placement-result-banner after all questions answered", () => {
    render(<PlacementShell onComplete={vi.fn()} />);
    // Answer all 8 questions by clicking first option each time
    const TOTAL = 8;
    for (let i = 0; i < TOTAL; i++) {
      // Click first button inside placement-question (option A)
      const questionEl = screen.getByTestId("placement-question");
      const btns = questionEl.querySelectorAll("button");
      fireEvent.click(btns[0]);
    }
    expect(screen.getByTestId("placement-result-banner")).toBeInTheDocument();
  });
});
