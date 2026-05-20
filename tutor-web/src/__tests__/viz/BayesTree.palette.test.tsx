/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * BayesTree pre-existing violations fixed here:
 *   1. Disease branch node (circle at branchX1/branch1Y) was fill=ACCENT —
 *      encodes Disease-vs-Healthy CATEGORY, always-on; fixed to HATCH_DENSE.
 *   2. Disease ∩ positive joint-prob rect was fill=ACCENT — encodes Disease
 *      branch category; fixed to HATCH_DENSE.
 *   3. Disease ∩ negative joint-prob rect was fill=ACCENT — same violation;
 *      fixed to HATCH_DENSE.
 *
 * Legitimate ACCENT uses (NOT tested here, must remain untouched):
 *   - Posterior-result box: pops in on frame 5+ (showPosterior=true).
 *     Marks the per-frame focal payoff — correct V12.
 *   - "Surprise" badge: appears only on step 6 (frame 6).
 *     Marks a single per-frame focal element — correct V12.
 *
 * Test strategy:
 *   - On early frames (frames 0-4, before posterior appears), assert that
 *     the three violation sites do NOT use fill=ACCENT.
 *   - Assert Disease/Healthy remain visually distinct: Disease areas use a
 *     hatch fill (url(#hatch-...)), Healthy stays PAPER.
 *   - Assert no ACCENT elements exist on these early frames (none of the
 *     legitimate uses fire yet on frames 0-1).
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { BayesTree } from "../../components/viz/BayesTree";

const ACCENT_HEX = "#facc15";
const PAPER_HEX = "#f5f5f0";
const HATCH_DENSE_FILL = "url(#hatch-dense)";

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='bayes-tree-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('bayes-tree-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Find the Disease branch node circle.
 * This is the <circle> that carries the D label (branch intermediate node).
 * In the SVG there are two branch circles: D and H. We pick the top one (smaller cy).
 * Both circles share the same cx (branchX1=90+20=110). The D node is at the smaller y.
 */
function diseaseBranchCircle(svg: Element): Element | null {
  // All circles that are NOT inside <defs> and NOT the root/leaf markers.
  // The root node and branch nodes are the only non-defs circles.
  // Branch circles are at x≈110 (branchX1 = TREE_X+90 = 20+90=110).
  // Root circle is at x≈40 (rootX = TREE_X+20 = 20+20=40).
  // We want the branch circles (cx≈110); the D node has a smaller cy.
  const circles = Array.from(svg.querySelectorAll("circle")).filter((el) => {
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  });
  // Branch circles: cx approximately 110 (branchX1)
  const branchCircles = circles.filter((el) => {
    const cx = parseFloat(el.getAttribute("cx") ?? "0");
    return cx > 100 && cx < 120;
  });
  if (branchCircles.length === 0) return null;
  // D node = smallest cy among branch circles
  branchCircles.sort(
    (a, b) =>
      parseFloat(a.getAttribute("cy") ?? "0") -
      parseFloat(b.getAttribute("cy") ?? "0")
  );
  return branchCircles[0];
}

/**
 * Find the Healthy branch node circle (largest cy among branch circles).
 */
function healthyBranchCircle(svg: Element): Element | null {
  const circles = Array.from(svg.querySelectorAll("circle")).filter((el) => {
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  });
  const branchCircles = circles.filter((el) => {
    const cx = parseFloat(el.getAttribute("cx") ?? "0");
    return cx > 100 && cx < 120;
  });
  if (branchCircles.length === 0) return null;
  branchCircles.sort(
    (a, b) =>
      parseFloat(b.getAttribute("cy") ?? "0") -
      parseFloat(a.getAttribute("cy") ?? "0")
  );
  return branchCircles[0];
}

/**
 * Find the Disease-column joint-probability rects (the two left-column rects
 * in the area pane). Area pane starts at AREA_X=270. The Disease column rects
 * are anchored at x=AREA_X=270 (their animate x stays at 270). We find all
 * <rect> elements anchored at x=270 that are NOT inside <defs>.
 * The two Disease rects share x=270 (AREA_X); the background rect also starts
 * at 270 — exclude it by filtering for those that also have a fill != PAPER and
 * fill != none and stroke=INK.
 * More robustly: we look for motion.rect elements at x=270 with a width that
 * animates. In JSDOM, motion.rect renders as a plain <rect>. We distinguish
 * Disease rects from the background rect by checking that they are NOT stroke-only
 * (background rect has strokeWidth=1, fill=PAPER, no data attributes).
 * Simplest reliable selector: take all rects at x=270, exclude the one with
 * fill=PAPER and stroke=INK and large width (the background).
 */
function diseaseRects(svg: Element): Element[] {
  const rects = Array.from(svg.querySelectorAll("rect")).filter((el) => {
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    // x must be 270 (AREA_X)
    const x = parseFloat(el.getAttribute("x") ?? "-1");
    return Math.abs(x - 270) < 1;
  });
  // Exclude the background rect: fill=PAPER + width=200 (full AREA_W)
  return rects.filter((el) => {
    const fill = el.getAttribute("fill") ?? "";
    const width = parseFloat(el.getAttribute("width") ?? "0");
    // Background is fill=PAPER (or "#f5f5f0"), width=200
    if ((fill === PAPER_HEX || fill === "#f5f5f0") && width >= 190) return false;
    return true;
  });
}

/**
 * Count canvas elements (rect, circle, path, polygon, line) with
 * fill=ACCENT or stroke=ACCENT, excluding <defs> subtrees.
 */
function accentElementCount(svg: Element): number {
  const candidates = Array.from(
    svg.querySelectorAll("rect, circle, path, polygon, line")
  );
  return candidates.filter((el) => {
    const fillMatch = el.getAttribute("fill") === ACCENT_HEX;
    const strokeMatch = el.getAttribute("stroke") === ACCENT_HEX;
    if (!fillMatch && !strokeMatch) return false;
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  }).length;
}

describe("BayesTree — V12 palette compliance", () => {
  beforeEach(() => {
    window.location.hash = "";
  });

  // ── Violation 1: Disease branch node circle ─────────────────────────────
  describe("Disease branch node (D circle)", () => {
    test("F0 (initial frame): Disease node circle is NOT fill=ACCENT", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;
      expect(svg).not.toBeNull();

      const dCircle = diseaseBranchCircle(svg);
      expect(dCircle).not.toBeNull();
      expect(dCircle!.getAttribute("fill")).not.toBe(ACCENT_HEX);
    });

    test("F0: Disease node uses hatch fill (url(#hatch-...)) for category encoding", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;

      const dCircle = diseaseBranchCircle(svg);
      expect(dCircle).not.toBeNull();
      const fill = dCircle!.getAttribute("fill") ?? "";
      expect(fill).toMatch(/^url\(#hatch-/);
    });

    test("F0: Healthy node uses PAPER (plain, no hatch) — visual distinction preserved", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;

      const hCircle = healthyBranchCircle(svg);
      expect(hCircle).not.toBeNull();
      // Healthy stays plain — fill must NOT be a hatch url
      const fill = hCircle!.getAttribute("fill") ?? "";
      expect(fill).not.toMatch(/^url\(#hatch-/);
      expect(fill).not.toBe(ACCENT_HEX);
    });

    test("F2 (Disease branch active): Disease node still uses hatch, not ACCENT", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 2);
      const svg = container.querySelector("svg")!;

      const dCircle = diseaseBranchCircle(svg);
      expect(dCircle).not.toBeNull();
      expect(dCircle!.getAttribute("fill")).not.toBe(ACCENT_HEX);
      const fill = dCircle!.getAttribute("fill") ?? "";
      expect(fill).toMatch(/^url\(#hatch-/);
    });
  });

  // ── Violations 2 & 3: Disease joint-probability rects ───────────────────
  describe("Disease joint-probability area rects (D ∩ + and D ∩ −)", () => {
    test("F0: Disease area rects are NOT fill=ACCENT", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;

      const dRects = diseaseRects(svg);
      expect(dRects.length).toBeGreaterThan(0); // sanity: rects exist

      const accentRects = dRects.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentRects.length).toBe(0);
    });

    test("F0: Disease area rects use hatch fill for category encoding", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;

      const dRects = diseaseRects(svg);
      expect(dRects.length).toBeGreaterThan(0);

      dRects.forEach((el) => {
        const fill = el.getAttribute("fill") ?? "";
        expect(fill).toMatch(/^url\(#hatch-/);
      });
    });

    test("F2 (DP rect shown): Disease ∩ positive rect uses hatch, not ACCENT", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 2);
      const svg = container.querySelector("svg")!;

      const dRects = diseaseRects(svg);
      expect(dRects.length).toBeGreaterThan(0);

      const accentRects = dRects.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentRects.length).toBe(0);
    });

    test("F3 (both DP and DN rects shown): neither Disease rect is ACCENT", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 3);
      const svg = container.querySelector("svg")!;

      const dRects = diseaseRects(svg);
      expect(dRects.length).toBeGreaterThan(0);

      dRects.forEach((el) => {
        expect(el.getAttribute("fill")).not.toBe(ACCENT_HEX);
      });
    });
  });

  // ── Zero ACCENT on early frames (before posterior payoff) ────────────────
  describe("ACCENT count on early frames (no focal element yet)", () => {
    test("F0: zero ACCENT canvas elements (no posterior, no surprise badge)", () => {
      const { container } = render(<BayesTree />);
      const svg = container.querySelector("svg")!;
      expect(accentElementCount(svg)).toBe(0);
    });

    test("F1: zero ACCENT canvas elements", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 1);
      const svg = container.querySelector("svg")!;
      expect(accentElementCount(svg)).toBe(0);
    });
  });

  // ── Legitimate ACCENT uses must remain ──────────────────────────────────
  describe("Legitimate ACCENT uses (must remain untouched)", () => {
    test("F5 (showPosterior=true): posterior-result box uses ACCENT (per-frame focal payoff)", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 5);
      const svg = container.querySelector("svg")!;
      // At least one ACCENT rect should exist (the posterior box)
      expect(accentElementCount(svg)).toBeGreaterThanOrEqual(1);
    });

    test("F6 (Surprise badge frame): ACCENT elements include the Surprise badge", () => {
      const { container } = render(<BayesTree />);
      advanceTo(container, 6);
      const svg = container.querySelector("svg")!;
      // Posterior box + Surprise badge = at least 2 ACCENT rects
      expect(accentElementCount(svg)).toBeGreaterThanOrEqual(2);
    });
  });
});
