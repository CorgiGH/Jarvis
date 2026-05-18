import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { RaceMutex, FRAME_COUNT } from "../../components/viz/RaceMutex";

describe("RaceMutex (SO-3)", () => {
  test("renders Shell scrubber + SHARED HEAP label", () => {
    render(<RaceMutex />);
    expect(screen.getByTestId("race-mutex-scrubber")).toBeInTheDocument();
    expect(screen.getByText("SHARED HEAP")).toBeInTheDocument();
  });

  test("initial frame shows counter = 0", () => {
    render(<RaceMutex />);
    // Counter cell renders as "= 0" via TweenText
    expect(screen.getByText(/=\s*0/)).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<RaceMutex />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 18", () => {
    // PHASE 1 (RACE): 8 frames (F0..F7)
    // PHASE 2 (MUTEX): 9 frames (F8..F16)
    // PHASE 3 (SUMMARY): 1 frame (F17)
    expect(FRAME_COUNT).toBe(18);
  });

  test("renders role=group from Shell", () => {
    render(<RaceMutex />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
