import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  TcpHandshake,
  FRAME_COUNT,
} from "../../components/viz/TcpHandshake";

describe("TcpHandshake (RC-2)", () => {
  test("renders Shell scrubber + actor columns", () => {
    render(<TcpHandshake />);
    expect(
      screen.getByTestId("tcp-handshake-scrubber")
    ).toBeInTheDocument();
    expect(screen.getByText("CLIENT")).toBeInTheDocument();
    expect(screen.getByText("SERVER")).toBeInTheDocument();
  });

  test("initial frame shows CLOSED state on client", () => {
    render(<TcpHandshake />);
    // FadeText renders the state name; CLOSED appears for the client on F0.
    const matches = screen.getAllByText(/CLOSED/);
    expect(matches.length).toBeGreaterThan(0);
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<TcpHandshake />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("FRAME_COUNT is 17", () => {
    // 7 (NORMAL) + 6 (FLOOD) + 3 (COOKIES) + 1 (SUMMARY) = 17 frames
    expect(FRAME_COUNT).toBe(17);
  });

  test("renders role=group from Shell", () => {
    render(<TcpHandshake />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
