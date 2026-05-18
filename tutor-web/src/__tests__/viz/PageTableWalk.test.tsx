import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { PageTableWalk, FRAME_COUNT } from "../../components/viz/PageTableWalk";

describe("PageTableWalk (SO-4)", () => {
  test("renders Shell scrubber + pane labels", () => {
    render(<PageTableWalk />);
    expect(screen.getByTestId("page-table-walk-scrubber")).toBeInTheDocument();
    expect(screen.getByText("TLB")).toBeInTheDocument();
    expect(screen.getByText("PAGE TABLE")).toBeInTheDocument();
    expect(screen.getByText("PHYS FRAMES")).toBeInTheDocument();
  });

  test("initial frame is Phase 1", () => {
    render(<PageTableWalk />);
    expect(screen.getAllByText(/Phase 1/).length).toBeGreaterThanOrEqual(1);
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<PageTableWalk />);
    expect(
      screen.getByText(new RegExp(`^1\\s*/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 19 (5 TLB-miss + 3 TLB-hit + 5 page-fault + 6 COW)", () => {
    expect(FRAME_COUNT).toBe(19);
  });

  test("renders role=group from Shell", () => {
    render(<PageTableWalk />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
