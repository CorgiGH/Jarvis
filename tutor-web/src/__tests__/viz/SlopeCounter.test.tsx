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
});
