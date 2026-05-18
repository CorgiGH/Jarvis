import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { NPGadget, FRAME_COUNT } from "../../components/viz/NPGadget";

describe("NPGadget (PA-7)", () => {
  test("renders Shell scrubber + pane labels", () => {
    render(<NPGadget />);
    expect(screen.getByTestId("np-gadget-scrubber")).toBeInTheDocument();
    expect(screen.getByText("3-SAT φ")).toBeInTheDocument();
    expect(screen.getByText(/GRAPH G/)).toBeInTheDocument();
  });

  test("renders 3 clauses", () => {
    render(<NPGadget />);
    expect(screen.getByText(/C1:/)).toBeInTheDocument();
    expect(screen.getByText(/C2:/)).toBeInTheDocument();
    expect(screen.getByText(/C3:/)).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<NPGadget />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 12", () => {
    expect(FRAME_COUNT).toBe(12);
  });

  test("renders role=group from Shell", () => {
    render(<NPGadget />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
