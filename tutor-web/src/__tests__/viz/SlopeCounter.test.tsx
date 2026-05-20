import { render, screen } from "@testing-library/react";
import { describe, test, expect } from "vitest";
import { SlopeCounter } from "../../components/viz/SlopeCounter";

describe("SlopeCounter", () => {
  const data = [3, 7, 8, 9, 14];

  test("LEFT chip count for mu=8 → 2", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-left-chip").textContent).toContain("2");
  });

  test("RIGHT chip count for mu=8 → 2", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-right-chip").textContent).toContain("2");
  });

  test("diff = 0 for mu=8", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("0");
  });

  test("diff = -4 for mu=3 (no left, 4 right)", () => {
    render(<SlopeCounter data={data} mu={3} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("-4");
  });

  test("diff = 4 for mu=14 (4 left, no right)", () => {
    render(<SlopeCounter data={data} mu={14} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("4");
  });

  test("instantly updates on mu prop change", () => {
    const { rerender } = render(<SlopeCounter data={data} mu={3} />);
    rerender(<SlopeCounter data={data} mu={14} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("4");
  });

  test("excludes ties (x === mu) from both counts", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-left-chip").textContent).toContain("2");
    expect(screen.getByTestId("slope-right-chip").textContent).toContain("2");
  });

  // Accessibility: slope readout is a live-region status widget
  test("slope-diff element has role=status for screen-reader live region", () => {
    render(<SlopeCounter data={data} mu={8} />);
    const readout = screen.getByRole("status");
    expect(readout).toBeDefined();
  });

  test("slope readout aria-label names the metric and current diff value", () => {
    render(<SlopeCounter data={data} mu={8} />);
    const readout = screen.getByRole("status");
    // diff = left(2) - right(2) = 0, label should read "slope: +0" or "slope: 0"
    expect(readout.getAttribute("aria-label")).toMatch(/slope:\s*\+?0/);
  });

  test("aria-label updates when diff changes (mu=3 → diff=-4)", () => {
    render(<SlopeCounter data={data} mu={3} />);
    const readout = screen.getByRole("status");
    expect(readout.getAttribute("aria-label")).toMatch(/slope:\s*-4/);
  });

  test("aria-label uses + prefix for positive diff (mu=14 → diff=+4)", () => {
    render(<SlopeCounter data={data} mu={14} />);
    const readout = screen.getByRole("status");
    expect(readout.getAttribute("aria-label")).toMatch(/slope:\s*\+4/);
  });
});
