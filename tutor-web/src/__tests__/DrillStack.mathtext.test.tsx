import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DrillStack } from "../components/DrillStack";

describe("DrillStack body math rendering", () => {
  it("renders drill body via MathText so $$...$$ becomes KaTeX", () => {
    render(
      <DrillStack
        taskId="t"
        problemId="A1"
        content={{
          drill: "What is $$\\hat{\\mu}_{MLE}$$ for x=(1,2,5)?",
          worked: "$$\\text{median} = 2$$",
          definition: "Laplace MLE is median.",
          check: "Verify: $$\\text{median}(1,2,5)=2$$",
          expectedAnswerHint: "median equals 2",
        }}
        onProblemComplete={() => {}}
      />
    );
    // At least the visible drill card should have MathText present
    expect(screen.getAllByTestId("math-text").length).toBeGreaterThan(0);
  });
});
