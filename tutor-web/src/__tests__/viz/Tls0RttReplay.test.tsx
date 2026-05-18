import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { Tls0RttReplay, FRAME_COUNT } from "../../components/viz/Tls0RttReplay";

describe("Tls0RttReplay (RC-6)", () => {
  test("renders Shell scrubber + actor columns", () => {
    render(<Tls0RttReplay />);
    expect(screen.getByTestId("tls-0rtt-replay-scrubber")).toBeInTheDocument();
    expect(screen.getByText("CLIENT")).toBeInTheDocument();
    expect(screen.getByText("SERVER")).toBeInTheDocument();
  });

  test("initial frame is Phase 1", () => {
    render(<Tls0RttReplay />);
    // Multiple elements match /Phase 1/ (SVG phase indicator + footer text + live region)
    const matches = screen.getAllByText(/Phase 1/);
    expect(matches.length).toBeGreaterThan(0);
  });

  test("frame counter shows 1 / 15", () => {
    render(<Tls0RttReplay />);
    expect(screen.getByText(/^1\s*\/\s*15$/)).toBeInTheDocument();
  });

  test("FRAME_COUNT is 15", () => {
    expect(FRAME_COUNT).toBe(15);
  });

  test("renders role=group from Shell", () => {
    render(<Tls0RttReplay />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
