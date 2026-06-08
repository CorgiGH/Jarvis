import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MockScoreSparkline } from "./MockScoreSparkline";

describe("MockScoreSparkline", () => {
  it("renders mock-score-sparkline container", () => {
    render(<MockScoreSparkline score={0.7} previousScore={0.5} />);
    expect(screen.getByTestId("mock-score-sparkline")).toBeInTheDocument();
  });

  it("renders mastery-sparkline inside", () => {
    render(<MockScoreSparkline score={0.7} previousScore={0.5} />);
    expect(screen.getByTestId("mastery-sparkline")).toBeInTheDocument();
  });

  it("passes score as ewmaScore to MasterySparkline", () => {
    render(<MockScoreSparkline score={0.9} previousScore={0.8} />);
    const ms = screen.getByTestId("mastery-sparkline");
    expect(ms).toHaveAttribute("data-mastery-value", "0.9");
    expect(ms).toHaveAttribute("data-mastery-band", "high");
  });

  it("shows delta when previousScore provided", () => {
    render(<MockScoreSparkline score={0.7} previousScore={0.5} />);
    expect(screen.getByTestId("mock-score-sparkline")).toHaveTextContent("+20%");
  });

  it("shows negative delta correctly", () => {
    render(<MockScoreSparkline score={0.4} previousScore={0.6} />);
    expect(screen.getByTestId("mock-score-sparkline")).toHaveTextContent("-20%");
  });

  it("does not show delta when previousScore is null", () => {
    render(<MockScoreSparkline score={0.7} previousScore={null} />);
    const el = screen.getByTestId("mock-score-sparkline");
    expect(el).not.toHaveTextContent("%");
  });
});
