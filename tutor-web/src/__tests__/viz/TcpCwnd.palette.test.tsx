/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * Rule: At most ONE SVG element in a TcpCwnd frame may carry
 * fill="#facc15" OR stroke="#facc15" — that element is the current cwnd
 * dot (the "you are here" focal marker on the trajectory).
 *
 * Violations before fix:
 *   - modeColor(): cwnd trajectory line segments use ACCENT for SLOW_START
 *     mode and INK for CONG_AVOIDANCE — yellow encodes a mode category.
 *   - motion.rect for MODE display: animate fill to ACCENT when mode===SLOW_START
 *     — yellow encodes a mode category in the state panel.
 *
 * After fix:
 *   - cwnd trajectory lines are uniformly INK (stroke="#0a0a0a") end-to-end.
 *   - Mode regions are distinguished via hatch band (HATCH_LIGHT background
 *     behind slow-start x-range) or annotation — never by line/fill color.
 *   - ACCENT reserved for ≤1 focal element per frame: the current cwnd dot.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { TcpCwnd } from "../../components/viz/TcpCwnd";
import { INK } from "../../components/viz/theme";

const ACCENT_HEX = "#facc15";
const INK_HEX = INK; // sourced from theme.ts (drift-proof; §0 #11)

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='tcp-cwnd-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('tcp-cwnd-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Count all SVG canvas elements (rect, path, polygon, circle, line)
 * that carry fill=ACCENT_HEX OR stroke=ACCENT_HEX, EXCLUDING <defs> subtrees.
 *
 * We exclude <defs> (markers, patterns) — they're not rendered canvas marks.
 * We also exclude the Shell chrome's outer focus ring (SVG outline style) —
 * that's a CSS property, not an SVG attribute, so attribute queries miss it anyway.
 */
function accentElementCount(svg: Element): number {
  const candidates = Array.from(
    svg.querySelectorAll("rect, path, polygon, circle, line")
  );
  return candidates.filter((el) => {
    const fillMatch = el.getAttribute("fill") === ACCENT_HEX;
    const strokeMatch = el.getAttribute("stroke") === ACCENT_HEX;
    if (!fillMatch && !strokeMatch) return false;
    // Exclude elements inside <defs>
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  }).length;
}

/**
 * Collect all line elements from the cwnd trajectory (DrawLine components).
 * DrawLine renders as a <line> element. Trajectory lines are the ones inside
 * the plot area (x between PLOT_X=50 and PLOT_X+PLOT_W=330).
 */
function trajectoryLines(svg: Element): Element[] {
  const lines = Array.from(svg.querySelectorAll("line"));
  return lines.filter((el) => {
    // Exclude elements inside <defs>
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    // Trajectory lines are the only lines with strokeWidth="2.5" (set by DrawLine calls).
    const sw = el.getAttribute("stroke-width") ?? el.getAttribute("strokeWidth") ?? "";
    return sw === "2.5";
  });
}

describe("TcpCwnd — V12 palette compliance", () => {
  // AlgoStepperShell persists frame idx to window.location.hash.
  // Clear between tests so advanceTo() always starts from frame 0.
  beforeEach(() => {
    window.location.hash = "";
  });

  test("F0 (INIT, single cwnd=1 point): at most 1 ACCENT element (the cwnd dot)", () => {
    const { container } = render(<TcpCwnd />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // F0 has no trajectory line segments (history length=1, no DrawLine rendered).
    // The cwnd dot (motion.circle fill=ACCENT) should be the only ACCENT element.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F3 (slow-start phase, cwnd=8): cwnd trajectory lines are INK, not ACCENT", () => {
    // Pre-fix: SLOW_START mode → modeColor() returns ACCENT → DrawLine stroke=ACCENT.
    // Post-fix: all trajectory lines use stroke=INK.
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 3);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const tLines = trajectoryLines(svg!);
    // There should be some trajectory lines (3 RTT steps = 3 line segments).
    expect(tLines.length).toBeGreaterThan(0);

    // NONE of the trajectory lines may have stroke=ACCENT.
    const accentLines = tLines.filter(
      (el) => el.getAttribute("stroke") === ACCENT_HEX
    );
    expect(accentLines.length).toBe(0);

    // All trajectory lines must have stroke=INK.
    const inkLines = tLines.filter(
      (el) => el.getAttribute("stroke") === INK_HEX
    );
    expect(inkLines.length).toBe(tLines.length);
  });

  test("F3 (slow-start): total ACCENT element count ≤ 1 (cwnd dot only)", () => {
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 3);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: 3 trajectory lines (SLOW_START) = ACCENT + mode panel rect = ACCENT → ≥2.
    // Post-fix: only the cwnd dot circle may be ACCENT → ≤1.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F8 (cong-avoidance phase after ssthresh): all trajectory lines are INK", () => {
    // F8 = RTT 8, mode should be CONG_AVOIDANCE (reached ssthresh at RTT 4).
    // Pre-fix: CONG_AVOIDANCE lines were already INK (modeColor returned INK).
    // Post-fix: still INK (no regression).
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 8);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const tLines = trajectoryLines(svg!);
    expect(tLines.length).toBeGreaterThan(0);

    const accentLines = tLines.filter(
      (el) => el.getAttribute("stroke") === ACCENT_HEX
    );
    expect(accentLines.length).toBe(0);
  });

  test("F8 (cong-avoidance): total ACCENT element count ≤ 1 (cwnd dot only)", () => {
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 8);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F12 (first packet loss, reset to ssthresh+3): at most 1 ACCENT element", () => {
    // F12 = RTT 12, packet loss event.
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 12);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F12 (packet loss): cwnd trajectory lines are all INK — no mode-colored segments", () => {
    const { container } = render(<TcpCwnd />);
    advanceTo(container, 12);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const tLines = trajectoryLines(svg!);
    expect(tLines.length).toBeGreaterThan(0);

    const accentLines = tLines.filter(
      (el) => el.getAttribute("stroke") === ACCENT_HEX
    );
    expect(accentLines.length).toBe(0);
  });

  test("mode panel rect does NOT use fill=ACCENT for SLOW_START (mode via annotation, not color)", () => {
    // Pre-fix: motion.rect in the STATE panel animates fill to ACCENT when mode===SLOW_START.
    // Post-fix: mode panel rect uses INK stroke + PAPER fill with hatch or label — no ACCENT.
    const { container } = render(<TcpCwnd />);
    // Start at F2 (still in slow-start)
    advanceTo(container, 2);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    // Find rects that are in the state panel x range (STATE_X=350, STATE_W=120 → x=350–470).
    const stateRects = Array.from(svg!.querySelectorAll("rect")).filter((r) => {
      const x = parseFloat(r.getAttribute("x") ?? "-1");
      return x >= 340 && x <= 480;
    });

    const accentStateRects = stateRects.filter(
      (r) => r.getAttribute("fill") === ACCENT_HEX
    );
    expect(accentStateRects.length).toBe(0);
  });

  test("hatch patterns are present in SVG defs (Shell mounts HatchDefs)", () => {
    const { container } = render(<TcpCwnd />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-light")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-dense")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-cross")).not.toBeNull();
  });

  test("summary frame (last frame): at most 1 ACCENT element", () => {
    // The last frame is a summary — cwnd dot is still focal.
    const { container } = render(<TcpCwnd />);
    // Advance past all frames by clicking many times (31 frames total: RTT 0–29 + summary)
    advanceTo(container, 31);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });
});
