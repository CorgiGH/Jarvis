import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { BayesTree, FRAME_COUNT } from "../../components/viz/BayesTree";

describe("BayesTree (PS-2)", () => {
  test("renders Shell scrubber + pane labels", () => {
    render(<BayesTree />);
    expect(screen.getByTestId("bayes-tree-scrubber")).toBeInTheDocument();
    expect(screen.getByText("PROB TREE")).toBeInTheDocument();
  });

  test("initial frame shows prior P(D)=1.00%", () => {
    render(<BayesTree />);
    // SVG text children may be split; match the percentage node alone
    expect(screen.getAllByText(/1\.00%/).length).toBeGreaterThanOrEqual(1);
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<BayesTree />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 10", () => {
    expect(FRAME_COUNT).toBe(10);
  });

  test("renders role=group from Shell", () => {
    render(<BayesTree />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
