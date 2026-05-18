import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { DPWastedWork, FRAME_COUNT } from "../../components/viz/DPWastedWork";

describe("DPWastedWork (PA-3)", () => {
  test("renders Shell scrubber + pane labels", () => {
    render(<DPWastedWork />);
    expect(screen.getByTestId("dp-wasted-work-scrubber")).toBeInTheDocument();
    // Multiple elements may match because the description text also contains "DP table"
    expect(screen.getAllByText(/DP TABLE/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/NAÏVE RECURSION/i).length).toBeGreaterThanOrEqual(1);
  });

  test("initial frame is DP phase", () => {
    render(<DPWastedWork />);
    expect(screen.getByText(/Phase: DP/i)).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<DPWastedWork />);
    // AlgoStepperShell renders "{idx+1} / {total}" in the controls panel
    expect(
      screen.getByText(new RegExp(`^1\\s*/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 36 (6 DP + 30 naive fib(5))", () => {
    // 6 DP frames (dp[0]..dp[5]) + 15 calls × 2 (call+return) = 30 naive frames
    expect(FRAME_COUNT).toBe(36);
  });

  test("renders WASTED WORK tally text", () => {
    render(<DPWastedWork />);
    expect(screen.getByText(/WASTED WORK/i)).toBeInTheDocument();
  });

  test("renders role=group from Shell", () => {
    render(<DPWastedWork />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
