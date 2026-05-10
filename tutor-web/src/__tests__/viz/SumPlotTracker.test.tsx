import { render } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { SumPlotTracker } from "../../components/viz/SumPlotTracker";

beforeEach(() => {
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => { cb(0); return 0; });
  vi.stubGlobal("cancelAnimationFrame", () => {});
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("SumPlotTracker", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders an SVG element", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("svg")).not.toBeNull();
  });

  test("renders a persistent path element for the curve", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-curve']")).not.toBeNull();
  });

  test("renders the tracking marker circle", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-marker']")).not.toBeNull();
  });

  test("marker cx is within SVG bounds", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    const marker = container.querySelector("[data-testid='sum-marker']")!;
    const cx = parseFloat(marker.getAttribute("cx") ?? "0");
    expect(cx).toBeGreaterThanOrEqual(0);
    expect(cx).toBeLessThanOrEqual(480);
  });

  test("sum curve d attribute is non-empty", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    const path = container.querySelector("[data-testid='sum-curve']")!;
    expect((path.getAttribute("d") ?? "").length).toBeGreaterThan(0);
  });

  test("marker position changes when mu prop changes", () => {
    const { container, rerender } = render(<SumPlotTracker data={data} mu={3} />);
    const before = container.querySelector("[data-testid='sum-marker']")!.getAttribute("cx");
    rerender(<SumPlotTracker data={data} mu={14} />);
    const after = container.querySelector("[data-testid='sum-marker']")!.getAttribute("cx");
    expect(before).not.toBe(after);
  });

  test("renders a y-axis label", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-axis-label']")).not.toBeNull();
  });
});
