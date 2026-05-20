import { render, screen } from "@testing-library/react";
import { describe, test, expect, vi } from "vitest";
import { SigmaStackedBar } from "../../components/viz/SigmaStackedBar";
import { ACCENT, INK } from "../../components/viz/theme";

describe("SigmaStackedBar", () => {
  const data = [3, 7, 8, 9, 14];

  // ── V7: SVG structure (not HTML divs) ─────────────────────────────────────
  test("bar renders as an <svg> element (V7)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
  });

  test("segments are <rect> elements inside the <svg>, NOT <div>s (V7/V18)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    expect(rects.length).toBe(data.length);
    // Old HTML div segments must not exist
    const divSegs = container.querySelectorAll("div[data-testid^='sigma-seg-']");
    expect(divSegs.length).toBe(0);
  });

  // ── V19: ARIA ─────────────────────────────────────────────────────────────
  test("svg has role='img' (V19)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const svg = container.querySelector("svg");
    expect(svg?.getAttribute("role")).toBe("img");
  });

  test("svg has <title> and <desc> elements (V19)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const svg = container.querySelector("svg");
    expect(svg?.querySelector("title")).not.toBeNull();
    expect(svg?.querySelector("desc")).not.toBeNull();
  });

  test("svg aria-labelledby references title and desc ids (V19)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const svg = container.querySelector("svg");
    const labelledBy = svg?.getAttribute("aria-labelledby") ?? "";
    const titleId = svg?.querySelector("title")?.id ?? "";
    const descId = svg?.querySelector("desc")?.id ?? "";
    expect(titleId.length).toBeGreaterThan(0);
    expect(descId.length).toBeGreaterThan(0);
    expect(labelledBy).toContain(titleId);
    expect(labelledBy).toContain(descId);
  });

  // ── V12: No ACCENT as category color ──────────────────────────────────────
  test("no segment uses ACCENT fill when no focused segment (V12)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    rects.forEach((rect) => {
      expect(rect.getAttribute("fill")).not.toBe(ACCENT);
    });
  });

  test("all segments use INK fill by default (V12)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    rects.forEach((rect) => {
      expect(rect.getAttribute("fill")).toBe(INK);
    });
  });

  // ── No opacity-as-category ─────────────────────────────────────────────────
  test("no segment uses per-index opacity alternation", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    // All segments must have opacity 1 (or no opacity attr). Opacity < 1 is disallowed.
    rects.forEach((rect) => {
      const opacity = rect.getAttribute("opacity");
      if (opacity !== null) {
        expect(parseFloat(opacity)).toBe(1);
      }
    });
  });

  // ── Data correctness (preserved from original tests) ─────────────────────
  test("renders one segment per data point", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    expect(rects.length).toBe(data.length);
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

  test("zero-deviation segment has zero width (x2=x1 → rect width=0)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    // data[2] = 8, mu = 8 → deviation = 0 → width attr = 0 or "0"
    const idx = data.indexOf(8);
    const seg = container.querySelector(`svg rect[data-testid='sigma-seg-${idx}']`) as SVGRectElement;
    expect(parseFloat(seg?.getAttribute("width") ?? "1")).toBe(0);
  });

  test("each segment has data-value matching |xi − μ|", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    data.forEach((x, i) => {
      const seg = container.querySelector(`svg rect[data-testid='sigma-seg-${i}']`)!;
      expect(parseFloat(seg.getAttribute("data-value") ?? "")).toBeCloseTo(Math.abs(x - 8), 3);
    });
  });

  test("focused segment uses ACCENT fill; others remain INK (V12)", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} focusedIndex={1} />);
    const rects = container.querySelectorAll("svg rect[data-testid^='sigma-seg-']");
    rects.forEach((rect, i) => {
      if (i === 1) expect(rect.getAttribute("fill")).toBe(ACCENT);
      else expect(rect.getAttribute("fill")).toBe(INK);
    });
  });
});
