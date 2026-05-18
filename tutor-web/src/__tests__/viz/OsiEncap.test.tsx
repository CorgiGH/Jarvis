import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { OsiEncap, FRAME_COUNT } from "../../components/viz/OsiEncap";

describe("OsiEncap (RC-1)", () => {
  test("renders Shell scrubber + SENDER + MTU label", () => {
    render(<OsiEncap />);
    expect(screen.getByTestId("osi-encap-scrubber")).toBeInTheDocument();
    expect(screen.getByText("SENDER")).toBeInTheDocument();
    expect(screen.getByText(/MTU\s*=\s*800B/i)).toBeInTheDocument();
  });

  test("initial frame shows L7 layer + HTTP payload label", () => {
    render(<OsiEncap />);
    // Multiple L7 labels (sender + receiver). At least one must be present.
    expect(screen.getAllByText(/L7/i).length).toBeGreaterThan(0);
    // The 1400B HTTP packet label appears on the initial frame.
    expect(screen.getAllByText(/HTTP\s*1400B/i).length).toBeGreaterThan(0);
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<OsiEncap />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("FRAME_COUNT is 16", () => {
    expect(FRAME_COUNT).toBe(16);
  });

  test("renders role=group from Shell", () => {
    render(<OsiEncap />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
