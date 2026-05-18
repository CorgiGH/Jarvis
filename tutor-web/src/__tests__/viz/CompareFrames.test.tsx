import { describe, expect, test } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CompareFrames } from "../../components/viz/CompareFrames";

describe("CompareFrames (visx impl)", () => {
  test("renders root, play button, and N data points", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    expect(screen.getByTestId("compare-frames-plotly")).toBeInTheDocument();
    expect(screen.getByTestId("compare-frames-play")).toBeInTheDocument();
    expect(screen.getAllByTestId("compare-data-point")).toHaveLength(5);
  });

  test("clicking play cycles baseline → mean → median → baseline", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    const readout = screen.getByTestId("compare-mode-readout");
    expect(readout).toHaveTextContent("mode: baseline");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: mean");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: median");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: baseline");
  });

  test("renders an estimate line", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    expect(screen.getByTestId("compare-estimate-line")).toBeInTheDocument();
  });
});
