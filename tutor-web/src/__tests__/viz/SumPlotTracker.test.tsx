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

  describe("ARIA — V19 compliance (display-only diagram)", () => {
    test("svg has role=img", () => {
      const { container } = render(<SumPlotTracker data={data} mu={8} />);
      const svg = container.querySelector("svg[data-testid='sum-plot-tracker']")!;
      expect(svg.getAttribute("role")).toBe("img");
    });

    test("svg has aria-labelledby referencing a <title> and <desc> inside it", () => {
      const { container } = render(<SumPlotTracker data={data} mu={8} />);
      const svg = container.querySelector("svg[data-testid='sum-plot-tracker']")!;
      const labelledBy = svg.getAttribute("aria-labelledby") ?? "";
      const ids = labelledBy.split(" ").filter(Boolean);
      expect(ids.length).toBeGreaterThanOrEqual(2);
      for (const id of ids) {
        const el = svg.querySelector(`#${id}`);
        expect(el).not.toBeNull();
      }
    });

    test("first aria-labelledby id resolves to a <title> element", () => {
      const { container } = render(<SumPlotTracker data={data} mu={8} />);
      const svg = container.querySelector("svg[data-testid='sum-plot-tracker']")!;
      const ids = (svg.getAttribute("aria-labelledby") ?? "").split(" ").filter(Boolean);
      const titleEl = svg.querySelector(`#${ids[0]}`);
      expect(titleEl?.tagName.toLowerCase()).toBe("title");
      expect((titleEl?.textContent ?? "").trim().length).toBeGreaterThan(0);
    });

    test("second aria-labelledby id resolves to a <desc> element", () => {
      const { container } = render(<SumPlotTracker data={data} mu={8} />);
      const svg = container.querySelector("svg[data-testid='sum-plot-tracker']")!;
      const ids = (svg.getAttribute("aria-labelledby") ?? "").split(" ").filter(Boolean);
      const descEl = svg.querySelector(`#${ids[1]}`);
      expect(descEl?.tagName.toLowerCase()).toBe("desc");
      expect((descEl?.textContent ?? "").trim().length).toBeGreaterThan(0);
    });

    test("no aria-live region (display-only, no own interaction)", () => {
      const { container } = render(<SumPlotTracker data={data} mu={8} />);
      expect(container.querySelector("[aria-live]")).toBeNull();
    });
  });
});
