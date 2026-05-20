import { describe, expect, test } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CompareFrames } from "../../components/viz/CompareFrames";

describe("CompareFrames (visx impl)", () => {
  test("renders root, play button, and N data points", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    // stale testid must be gone; new testid must exist
    expect(screen.getByTestId("compare-frames")).toBeInTheDocument();
    expect(screen.queryByTestId("compare-frames-plotly")).toBeNull();
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

  test("svg has role=img and aria-labelledby referencing an in-SVG title and desc", () => {
    const { container } = render(<CompareFrames data={[2, 4, 6, 8, 10]} />);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.getAttribute("role")).toBe("img");

    const labelledBy = svg!.getAttribute("aria-labelledby");
    expect(labelledBy).not.toBeNull();
    expect(labelledBy!.trim().length).toBeGreaterThan(0);

    // Each id in aria-labelledby must resolve to an element inside the svg
    const ids = labelledBy!.trim().split(/\s+/);
    expect(ids.length).toBeGreaterThanOrEqual(2); // title id + desc id

    for (const id of ids) {
      const el = svg!.querySelector(`#${id}`);
      expect(el).not.toBeNull();
    }

    // Must have a <title> and a <desc> as first children of the svg
    const svgTitle = svg!.querySelector("title");
    expect(svgTitle).not.toBeNull();
    expect(svgTitle!.id.length).toBeGreaterThan(0);

    const svgDesc = svg!.querySelector("desc");
    expect(svgDesc).not.toBeNull();
    expect(svgDesc!.id.length).toBeGreaterThan(0);

    // Both title and desc ids must appear in aria-labelledby
    expect(ids).toContain(svgTitle!.id);
    expect(ids).toContain(svgDesc!.id);
  });
});
