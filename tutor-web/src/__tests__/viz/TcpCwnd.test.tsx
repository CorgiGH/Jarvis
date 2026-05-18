import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { TcpCwnd, FRAME_COUNT } from "../../components/viz/TcpCwnd";

describe("TcpCwnd (RC-3)", () => {
  test("renders Shell scrubber + state panel", () => {
    render(<TcpCwnd />);
    expect(screen.getByTestId("tcp-cwnd-scrubber")).toBeInTheDocument();
    expect(screen.getByText("STATE")).toBeInTheDocument();
  });

  test("initial frame shows RTT 0 + cwnd 1", () => {
    render(<TcpCwnd />);
    expect(screen.getByText(/RTT: 0/)).toBeInTheDocument();
    expect(screen.getByText(/cwnd: 1/)).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<TcpCwnd />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("frame count is 31", () => {
    // 30 RTTs (0..29) + 1 summary = 31 frames
    expect(FRAME_COUNT).toBe(31);
  });

  test("renders role=group from Shell", () => {
    render(<TcpCwnd />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
