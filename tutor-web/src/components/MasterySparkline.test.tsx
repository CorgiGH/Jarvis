import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MasterySparkline } from "./MasterySparkline";

describe("MasterySparkline", () => {
  it("renders high band (≥0.8) with accent fill", () => {
    render(<MasterySparkline ewmaScore={0.9} />);
    const el = screen.getByTestId("mastery-sparkline");
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute("data-mastery-value", "0.9");
    expect(el).toHaveAttribute("data-mastery-band", "high");
  });

  it("renders mid band (0.3..0.8)", () => {
    render(<MasterySparkline ewmaScore={0.5} />);
    const el = screen.getByTestId("mastery-sparkline");
    expect(el).toHaveAttribute("data-mastery-value", "0.5");
    expect(el).toHaveAttribute("data-mastery-band", "mid");
  });

  it("renders low band (<0.3)", () => {
    render(<MasterySparkline ewmaScore={0.1} />);
    const el = screen.getByTestId("mastery-sparkline");
    expect(el).toHaveAttribute("data-mastery-value", "0.1");
    expect(el).toHaveAttribute("data-mastery-band", "low");
  });

  it("boundary: exactly 0.8 is high", () => {
    render(<MasterySparkline ewmaScore={0.8} />);
    expect(screen.getByTestId("mastery-sparkline")).toHaveAttribute("data-mastery-band", "high");
  });

  it("boundary: exactly 0.3 is mid", () => {
    render(<MasterySparkline ewmaScore={0.3} />);
    expect(screen.getByTestId("mastery-sparkline")).toHaveAttribute("data-mastery-band", "mid");
  });
});
