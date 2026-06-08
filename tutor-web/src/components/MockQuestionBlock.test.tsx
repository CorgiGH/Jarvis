import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockQuestionBlock } from "./MockQuestionBlock";

const baseProps = {
  index: 0,
  question: "Ce este un estimator consistent?",
  answer: "",
  onAnswer: vi.fn(),
  isActive: true,
};

describe("MockQuestionBlock", () => {
  it("renders with correct testid", () => {
    render(<MockQuestionBlock {...baseProps} />);
    expect(screen.getByTestId("mock-question-0")).toBeInTheDocument();
  });

  it("renders the question text", () => {
    render(<MockQuestionBlock {...baseProps} />);
    expect(screen.getByTestId("mock-question-0")).toHaveTextContent(
      "Ce este un estimator consistent?"
    );
  });

  it("calls onAnswer when user types in textarea", () => {
    const onAnswer = vi.fn();
    render(<MockQuestionBlock {...baseProps} onAnswer={onAnswer} />);
    const textarea = screen.getByRole("textbox");
    fireEvent.change(textarea, { target: { value: "raspuns" } });
    expect(onAnswer).toHaveBeenCalledWith(0, "raspuns");
  });

  it("shows existing answer in textarea", () => {
    render(<MockQuestionBlock {...baseProps} answer="raspuns initial" />);
    expect(screen.getByRole("textbox")).toHaveValue("raspuns initial");
  });

  it("shows answered badge when answer is non-empty", () => {
    render(<MockQuestionBlock {...baseProps} answer="x" />);
    expect(screen.getByTestId("mock-question-0")).toHaveAttribute("data-answered", "true");
  });

  it("does not show answered badge when answer is empty", () => {
    render(<MockQuestionBlock {...baseProps} answer="" />);
    expect(screen.getByTestId("mock-question-0")).toHaveAttribute("data-answered", "false");
  });
});
