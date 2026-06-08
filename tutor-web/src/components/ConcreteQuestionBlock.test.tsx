import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ConcreteQuestionBlock } from "./ConcreteQuestionBlock";

describe("ConcreteQuestionBlock", () => {
  const question = "Ce este un invariant de buclă? Dă un exemplu din viața de zi cu zi.";

  it("renders the question text", () => {
    render(<ConcreteQuestionBlock question={question} onAnswer={() => {}} />);
    expect(screen.getByTestId("concrete-question-block")).toBeInTheDocument();
    expect(screen.getByTestId("concrete-question-block")).toHaveTextContent(question);
  });

  it("has an answer input", () => {
    render(<ConcreteQuestionBlock question={question} onAnswer={() => {}} />);
    expect(screen.getByTestId("concrete-question-input")).toBeInTheDocument();
  });

  it("submit button fires onAnswer with the typed text", () => {
    const onAnswer = vi.fn();
    render(<ConcreteQuestionBlock question={question} onAnswer={onAnswer} />);
    const input = screen.getByTestId("concrete-question-input");
    fireEvent.change(input, { target: { value: "Un semafor este un invariant." } });
    fireEvent.click(screen.getByTestId("concrete-question-submit"));
    expect(onAnswer).toHaveBeenCalledWith("Un semafor este un invariant.");
  });

  it("submit button is disabled when input is empty", () => {
    render(<ConcreteQuestionBlock question={question} onAnswer={() => {}} />);
    expect(screen.getByTestId("concrete-question-submit")).toBeDisabled();
  });

  it("submit button is disabled when input is whitespace only", () => {
    render(<ConcreteQuestionBlock question={question} onAnswer={() => {}} />);
    fireEvent.change(screen.getByTestId("concrete-question-input"), {
      target: { value: "   " },
    });
    expect(screen.getByTestId("concrete-question-submit")).toBeDisabled();
  });
});
