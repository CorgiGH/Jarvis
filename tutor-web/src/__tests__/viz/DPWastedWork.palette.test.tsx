/**
 * V12 / V6 palette compliance: DPWastedWork — wasted-work magnitude via hatch density.
 *
 * Pre-existing violation (Gate 1 sweep):
 *   dupShade() ramped ACCENT (yellow #facc15) opacity proportional to a node's
 *   duplicate-call count, applied to ALL duplicated nodes simultaneously.
 *   → Yellow-as-magnitude heat scale across many nodes = V12 violation.
 *   → Magnitude via opacity/color rather than hatch density = V6 violation.
 *
 * Fix: replace dupShade's ACCENT ramp with HATCH-DENSITY mapping:
 *   - count=1 (not duplicated): PAPER (plain)
 *   - count=2:                  HATCH_LIGHT
 *   - count=3-4:                HATCH_DENSE
 *   - count=5+:                 HATCH_CROSS
 *
 * fib(5) duplicate-count ranges observed in tree:
 *   fib(4)=1×, fib(5)=1×, fib(0)=3×, fib(3)=3×, fib(2)=5×, fib(1)=8×
 *
 * Legitimate ACCENT uses (untouched):
 *   - isCurrent?ACCENT on the active DP cell (DP phase, left pane)
 *   - isCurrent?ACCENT on the active tree node (NAIVE phase, right pane)
 *   These mark the single per-frame focal element — correct V12.
 *
 * Test strategy (tree-node circles only):
 *   - NAIVE-phase frame where multiple duplicated nodes are rendered.
 *   - Assert NO circle uses fill=ACCENT unless it is the current-step node.
 *   - At most ONE circle carries fill=ACCENT.
 *   - Distinct duplicate-count buckets map to distinct hatch fills
 *     (magnitude signal preserved via hatch density, not color).
 *
 * NOTE: We scope ACCENT-count assertions to <circle> elements (tree nodes) only,
 * not all SVG shapes. The DP-phase motion.rect elements carry framer-motion
 * animated fill state in JSDOM that may lag behind (stale from prior frames)
 * and would produce false positives. The tree-node circles use static fill props
 * (fill={nodeFill} as a static SVG attribute) so getAttribute("fill") is reliable.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { DPWastedWork } from "../../components/viz/DPWastedWork";

const ACCENT_HEX = "#facc15";

// HATCH fill strings as rendered in SVG attributes
const HATCH_LIGHT_FILL = "url(#hatch-light)";
const HATCH_DENSE_FILL = "url(#hatch-dense)";
const HATCH_CROSS_FILL = "url(#hatch-cross)";

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='dp-wasted-work-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('dp-wasted-work-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Collect all <circle> elements NOT inside <defs> in the SVG.
 * These are the tree nodes rendered in the NAIVE recursion pane.
 * The fill prop on these circles is static (not inside framer-motion's animate{}),
 * so getAttribute("fill") reliably returns the current computed fill.
 */
function treeNodeCircles(svg: Element): Element[] {
  return Array.from(svg.querySelectorAll("circle")).filter((el) => {
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  });
}

/**
 * Count <circle> elements (tree nodes) with fill=ACCENT, excluding <defs>.
 * We scope to circles only — tree nodes use static fill props, so this count
 * is reliable. DP-phase motion.rect fills are NOT counted here.
 */
function accentCircleCount(svg: Element): number {
  return treeNodeCircles(svg).filter(
    (el) => el.getAttribute("fill") === ACCENT_HEX
  ).length;
}

describe("DPWastedWork — V12/V6 palette compliance (dupShade hatch fix)", () => {
  // Clear hash between tests so AlgoStepperShell initializes from frame 0,
  // not from a stale hash set by a prior test's writeHashIdx side-effect.
  beforeEach(() => {
    window.location.hash = "";
  });

  // ── Frame selection ──────────────────────────────────────────────────────
  // Frame 0-5: DP phase. Frame 6+: NAIVE phase (index 6 = step 6 from frame 0).
  // Frame 30 (step 30): deep into the NAIVE tree — fib(1), fib(2), fib(3) all
  // have count>=2, many duplicated nodes visible.

  describe("NAIVE-phase frame with multiple duplicated nodes (step 30)", () => {
    test("no tree-node circle uses ACCENT as its duplicate-shade fill", () => {
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 30);
      const svg = container.querySelector("svg")!;
      expect(svg).not.toBeNull();

      const circles = treeNodeCircles(svg);
      expect(circles.length).toBeGreaterThan(0); // sanity: circles are rendered

      const accentCircles = circles.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      // At most ONE circle may carry ACCENT (the current-step node).
      // All others must use PAPER or a hatch fill — not yellow.
      expect(accentCircles.length).toBeLessThanOrEqual(1);
    });

    test("duplicated nodes (count>=2) use hatch fills, not ACCENT", () => {
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 30);
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      expect(circles.length).toBeGreaterThan(0);

      // Non-ACCENT, non-PAPER circles should exist (hatched duplicated nodes)
      const hatchCircles = circles.filter((el) => {
        const fill = el.getAttribute("fill") ?? "";
        return fill.startsWith("url(#hatch-");
      });
      // At frame 30, fib(1), fib(2), fib(3) all have count>=2 → multiple hatch circles
      expect(hatchCircles.length).toBeGreaterThan(0);
    });

    test("at most ONE circle carries ACCENT across the whole frame (focal = current-step only)", () => {
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 30);
      const svg = container.querySelector("svg")!;

      expect(accentCircleCount(svg)).toBeLessThanOrEqual(1);
    });
  });

  // ── Magnitude signal preserved: distinct counts → distinct hatch tiers ──
  describe("Hatch density encodes magnitude (distinct counts → distinct fills)", () => {
    test("HATCH_LIGHT appears on lower-count duplicated nodes (count=2 bucket)", () => {
      // At step 20: mid-NAIVE. Some n-values have count=2 (e.g. fib(3) called twice).
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 20);
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      const lightHatchCircles = circles.filter(
        (el) => el.getAttribute("fill") === HATCH_LIGHT_FILL
      );
      expect(lightHatchCircles.length).toBeGreaterThan(0);
    });

    test("HATCH_DENSE appears on higher-count duplicated nodes (count=3-4 bucket)", () => {
      // At step 30+, fib(3) has been called 3 times → HATCH_DENSE
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 30);
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      const denseHatchCircles = circles.filter(
        (el) => el.getAttribute("fill") === HATCH_DENSE_FILL
      );
      expect(denseHatchCircles.length).toBeGreaterThan(0);
    });

    test("HATCH_CROSS appears on highest-count nodes (count=5+ — fib(2) and fib(1))", () => {
      // Final frames: fib(2)=5×, fib(1)=8× → HATCH_CROSS
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 35); // frame 35 = last frame (index 35)
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      const crossHatchCircles = circles.filter(
        (el) => el.getAttribute("fill") === HATCH_CROSS_FILL
      );
      expect(crossHatchCircles.length).toBeGreaterThan(0);
    });

    test("all three hatch tiers co-exist in the final frame (light < dense < cross)", () => {
      // At the last frame, all duplicate tiers are visible simultaneously.
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 35);
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      const fills = new Set(
        circles
          .map((el) => el.getAttribute("fill") ?? "")
          .filter((f) => f.startsWith("url(#hatch-"))
      );
      // All three hatch tiers should be present in the final frame
      expect(fills.has(HATCH_LIGHT_FILL)).toBe(true);
      expect(fills.has(HATCH_DENSE_FILL)).toBe(true);
      expect(fills.has(HATCH_CROSS_FILL)).toBe(true);
    });
  });

  // ── Legitimate ACCENT use (isCurrent node) must remain ──────────────────
  describe("Legitimate ACCENT use: current tree node in NAIVE phase", () => {
    test("first NAIVE frame (step 6): exactly one ACCENT circle (current node = fib(5))", () => {
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 6);
      const svg = container.querySelector("svg")!;

      // Frame 6 = first NAIVE call (fib(5), count=1, currentTreeNodeId=0).
      // The current node is fib(5) itself — highlighted ACCENT.
      // It's the only node in the tree at this frame.
      expect(accentCircleCount(svg)).toBe(1);
    });

    test("mid NAIVE frame (step 25): exactly one ACCENT circle (current-step node only)", () => {
      const { container } = render(<DPWastedWork />);
      advanceTo(container, 25);
      const svg = container.querySelector("svg")!;

      // Exactly one ACCENT circle allowed (the current-step node)
      expect(accentCircleCount(svg)).toBeLessThanOrEqual(1);
    });

    test("ACCENT circle count never exceeds 1 on any NAIVE frame (sampling 5 frames)", () => {
      // Spot-check a range of NAIVE frames — yellow should mark only one node per frame.
      const sampleFrames = [6, 14, 20, 28, 35];
      for (const step of sampleFrames) {
        const { container } = render(<DPWastedWork />);
        advanceTo(container, step);
        const svg = container.querySelector("svg")!;
        const count = accentCircleCount(svg);
        expect(count, `frame step=${step}: expected ≤1 ACCENT circle, got ${count}`).toBeLessThanOrEqual(1);
      }
    });
  });

  // ── No ACCENT used as magnitude signal ───────────────────────────────────
  describe("Zero ACCENT-fill circles when no current node is ACCENT (DP phase)", () => {
    test("DP phase (step 0, initial): tree is empty, zero ACCENT circles in tree pane", () => {
      const { container } = render(<DPWastedWork />);
      // Frame 0 = first DP frame, no NAIVE tree yet
      const svg = container.querySelector("svg")!;

      const circles = treeNodeCircles(svg);
      // Tree is empty at step 0 — no circles exist
      const accentCircles = circles.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentCircles.length).toBe(0);
    });
  });
});
