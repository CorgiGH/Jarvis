import { render, screen } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { ProgressStrip } from "../components/ProgressStrip";

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockReducedMotion(false);
});
afterEach(() => {
  vi.restoreAllMocks();
});

describe("ProgressStrip outer tier", () => {
  test("renders outer progressbar with correct aria values", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const outer = screen.getByTestId("outer-progress");
    expect(outer.getAttribute("role")).toBe("progressbar");
    expect(outer.getAttribute("aria-valuenow")).toBe("3");
    expect(outer.getAttribute("aria-valuemin")).toBe("0");
    expect(outer.getAttribute("aria-valuemax")).toBe("7");
    expect(outer.getAttribute("aria-label")).toMatch(/3 of 7 problems/i);
  });

  test("renders 7 outer dots for total=7", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("outer-dot");
    expect(dots).toHaveLength(7);
  });

  test("3 outer dots are filled, 4 are empty for done=3 total=7", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("outer-dot");
    const filled = dots.filter((d) => d.getAttribute("data-filled") === "true");
    const empty = dots.filter((d) => d.getAttribute("data-filled") === "false");
    expect(filled).toHaveLength(3);
    expect(empty).toHaveLength(4);
  });

  test("label shows correct fraction text", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    expect(screen.getByTestId("outer-label").textContent).toMatch(/3 \/ 7/);
  });
});

describe("ProgressStrip inner tier", () => {
  test("renders inner progressbar with correct aria values", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const inner = screen.getByTestId("inner-progress");
    expect(inner.getAttribute("role")).toBe("progressbar");
    expect(inner.getAttribute("aria-valuenow")).toBe("2");
    expect(inner.getAttribute("aria-valuemax")).toBe("4");
    expect(inner.getAttribute("aria-label")).toMatch(/2 of 4 cards.*A3/i);
  });

  test("renders 4 inner dots for total=4", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("inner-dot");
    expect(dots).toHaveLength(4);
  });

  test("2 inner dots are filled for done=2", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("inner-dot");
    const filled = dots.filter((d) => d.getAttribute("data-filled") === "true");
    expect(filled).toHaveLength(2);
  });

  test("inner label shows fraction and current problem label", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const label = screen.getByTestId("inner-label");
    expect(label.textContent).toMatch(/2 \/ 4/);
    expect(label.textContent).toContain("A3");
  });
});

describe("ProgressStrip edge cases", () => {
  test("all outer done → all dots filled", () => {
    render(
      <ProgressStrip
        outer={{ done: 4, total: 4 }}
        inner={{ done: 4, total: 4 }}
        currentProblemLabel="A4"
      />
    );
    const outerDots = screen.getAllByTestId("outer-dot");
    expect(outerDots.every((d) => d.getAttribute("data-filled") === "true")).toBe(true);
  });

  test("done=0 → no dots filled", () => {
    render(
      <ProgressStrip
        outer={{ done: 0, total: 3 }}
        inner={{ done: 0, total: 4 }}
        currentProblemLabel="A1"
      />
    );
    const outerDots = screen.getAllByTestId("outer-dot");
    expect(outerDots.every((d) => d.getAttribute("data-filled") === "false")).toBe(true);
  });

  test("under prefers-reduced-motion, dot animation class absent", () => {
    mockReducedMotion(true);
    render(
      <ProgressStrip
        outer={{ done: 2, total: 4 }}
        inner={{ done: 1, total: 4 }}
        currentProblemLabel="A2"
      />
    );
    const filledOuterDots = screen
      .getAllByTestId("outer-dot")
      .filter((d) => d.getAttribute("data-filled") === "true");
    filledOuterDots.forEach((dot) => {
      expect(dot.className).not.toContain("animate-dot-fill");
    });
  });
});
