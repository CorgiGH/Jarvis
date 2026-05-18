import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProcessFSM, FRAME_COUNT } from "../../components/viz/ProcessFSM";

describe("ProcessFSM (SO-1)", () => {
  test("renders Shell scrubber + a static label", () => {
    render(<ProcessFSM />);
    expect(screen.getByTestId("process-fsm-scrubber")).toBeInTheDocument();
    expect(screen.getByText("PROCESSES")).toBeInTheDocument();
  });

  test("initial frame shows P1 NEW", () => {
    render(<ProcessFSM />);
    expect(screen.getAllByText(/NEW/).length).toBeGreaterThan(0);
    expect(screen.getByText("100")).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<ProcessFSM />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 15", () => {
    // F0..F14 inclusive = 15 frames (fork + wait + zombie + reap walk).
    expect(FRAME_COUNT).toBe(15);
  });

  test("renders role=group from Shell", () => {
    render(<ProcessFSM />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
