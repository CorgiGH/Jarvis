import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { RecursionTree, FRAME_COUNT } from "../../components/viz/RecursionTree";

describe("RecursionTree (PA-1)", () => {
  test("renders title + Shell scrubber on mount", () => {
    render(<RecursionTree />);
    // AlgoStepperShell wraps everything in a role="group"
    expect(screen.getByRole("group")).toBeInTheDocument();
    expect(screen.getByTestId("recursion-tree-scrubber")).toBeInTheDocument();
  });

  test("renders pane labels", () => {
    render(<RecursionTree />);
    expect(screen.getByText("CALL STACK")).toBeInTheDocument();
    expect(screen.getByText("RECURSION TREE")).toBeInTheDocument();
  });

  test("initial frame shows fib(5) on stack", () => {
    render(<RecursionTree />);
    // First frame is CALL fib(5) — stack card + tree node both render "fib(5)"
    // Use getAllByText since both the stack card label and the tree node label appear
    const matches = screen.getAllByText("fib(5)");
    expect(matches.length).toBeGreaterThanOrEqual(1);
  });

  test("frame readout shows 1 / N", () => {
    render(<RecursionTree />);
    // Shell renders "1 / N" frame counter
    expect(
      screen.getByText(new RegExp(`^1\\s*/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 30 (15 CALLs + 15 RETURNs for fib(5))", () => {
    expect(FRAME_COUNT).toBe(30);
  });
});
