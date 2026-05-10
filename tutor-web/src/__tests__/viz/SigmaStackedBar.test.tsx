import { render, screen } from "@testing-library/react";
import { describe, test, expect, vi } from "vitest";
import { SigmaStackedBar } from "../../components/viz/SigmaStackedBar";

describe("SigmaStackedBar", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders one segment per data point", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const segments = container.querySelectorAll("[data-testid^='sigma-seg-']");
    expect(segments.length).toBe(data.length);
  });

  test("sum label = 13 for mu=8", () => {
    render(<SigmaStackedBar data={data} mu={8} />);
    expect(screen.getByTestId("sigma-sum").textContent).toContain("13");
  });

  test("sum updates when mu changes", () => {
    const { rerender } = render(<SigmaStackedBar data={data} mu={8} />);
    rerender(<SigmaStackedBar data={data} mu={3} />);
    expect(screen.getByTestId("sigma-sum").textContent).toContain("26");
  });

  test("zero-deviation segment has zero width", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const seg = container.querySelector(`[data-testid='sigma-seg-${data.indexOf(8)}']`) as HTMLElement;
    expect(parseFloat(seg.style.width)).toBe(0);
  });

  test("each segment has data-value matching |xi − μ|", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    data.forEach((x, i) => {
      const seg = container.querySelector(`[data-testid='sigma-seg-${i}']`)!;
      expect(parseFloat(seg.getAttribute("data-value") ?? "")).toBeCloseTo(Math.abs(x - 8), 3);
    });
  });

  test("transition: none under prefers-reduced-motion", () => {
    const original = window.matchMedia;
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }) as any;
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const seg = container.querySelector("[data-testid^='sigma-seg-']") as HTMLElement;
    expect(seg.style.transition).toBe("none");
    window.matchMedia = original;
  });
});
