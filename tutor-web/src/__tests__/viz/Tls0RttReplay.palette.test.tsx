/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * Rule: At most ONE SVG element in a Tls0RttReplay frame may carry
 * fill="#facc15" OR stroke="#facc15".
 *
 * Violations before fix:
 *   - Message data: fillColor:"accent" on PSK ticket (id=4), 0-RTT EarlyData (id=5),
 *     replay message (id=9) — yellow encodes message KIND/danger
 *   - MessageRow: msg.replay → strokeC=ACCENT, markerEnd="tls-arrow-accent"
 *     — yellow encodes replay category (kind)
 *   - ATTACKER column header: fill=ACCENT — yellow encodes actor category
 *   - Mitigation badges: fill=ACCENT (up to 3 at once) — yellow encodes badge kind
 *
 * After fix: each frame has ≤1 ACCENT element (the focal message label box,
 * if any). Replay danger is encoded via ⚠ glyph + HATCH_DENSE stroke-dash.
 * ATTACKER header uses INK fill with PAPER text. Mitigation badges use
 * INK/PAPER with stroke-dash borders.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { Tls0RttReplay } from "../../components/viz/Tls0RttReplay";

const ACCENT_HEX = "#facc15";

/**
 * Count all SVG canvas elements (rect, path, polygon, circle, line, text)
 * that carry fill=ACCENT_HEX OR stroke=ACCENT_HEX, EXCLUDING <defs> subtrees
 * (markers, patterns — not rendered canvas marks).
 */
function accentElementCount(svg: Element): number {
  const candidates = Array.from(
    svg.querySelectorAll("rect, path, polygon, circle, line, text")
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

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='tls-0rtt-replay-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('tls-0rtt-replay-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

describe("Tls0RttReplay — V12 palette compliance", () => {
  // AlgoStepperShell persists frame idx to window.location.hash.
  // Reset between tests to avoid cross-test frame bleed.
  beforeEach(() => {
    window.location.hash = "";
  });

  test("F0 (ClientHello in flight — 1 message, highlighted): at most 1 ACCENT element", () => {
    const { container } = render(<Tls0RttReplay />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Frame 0: msg id=1 (ClientHello) is highlighted — its label box may be ACCENT.
    // No replay messages yet. Pre-fix: only label-box ACCENT → still ≤1. Post-fix: same.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F3 (PSK ticket highlighted — NewSessionTicket in view): at most 1 ACCENT element", () => {
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 3);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Frame 3: highlightedMessageId=4 (NewSessionTicket / PSK).
    // Pre-fix: fillColor:"accent" on msg 4 → extra ACCENT stroke (even more if ACCENT arrowhead).
    // Post-fix: only the label box of the focal message (id=4) may be ACCENT.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F4 (0-RTT EarlyData highlighted — phase 2 starts): at most 1 ACCENT element", () => {
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 4);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Frame 4: highlightedMessageId=5 (EarlyData). msg 4 (PSK) still visible.
    // Pre-fix: msg 4 has fillColor:"accent" → ACCENT stroke; msg 5 has fillColor:"accent"
    // → ACCENT stroke → 2+ ACCENT elements (kind-encoding).
    // Post-fix: only focal label box (msg 5) is ACCENT.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F8 (replay message highlighted — ATTACKER appears, phase 3): at most 1 ACCENT element", () => {
    // This is the key regression frame: msg 9 is replay=true, previously got ACCENT stroke
    // AND ACCENT arrowhead — yellow encoding replay KIND (not a focus signal).
    // Additionally, the ATTACKER actor column header used fill=ACCENT.
    // Pre-fix: ATTACKER col fill=ACCENT + msg 9 stroke=ACCENT + tls-arrow-accent = 3+ ACCENT marks.
    // Post-fix: ATTACKER header = INK fill; msg 9 uses hatch/glyph; ≤1 ACCENT (focal label only).
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 8);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F9 (double-charge frame — replay accepted, 2 replay messages visible): at most 1 ACCENT element", () => {
    // Frame 9: msgs 9 + 10 both have replay=true. highlightedMessageId=10.
    // Pre-fix: both replay msgs get ACCENT stroke + ATTACKER header ACCENT fill → 4+ ACCENT.
    // Post-fix: only focal label box (msg 10) is ACCENT; replay kind = glyph/hatch.
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 9);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F10 (mitigations start — phase 4, no highlighted message): zero ACCENT elements", () => {
    // Frame 10: highlightedMessageId=null, phase=4. First mitigation badge appears.
    // Pre-fix: mitigation badge fill=ACCENT (badge kind encoding) = 1+ ACCENT.
    // Post-fix: badges use INK/PAPER borders — zero ACCENT.
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 10);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBe(0);
  });

  test("F12 (3 mitigation badges simultaneously — phase 4): zero ACCENT elements", () => {
    // Frame 12 (step=12, appearOn=12): all 3 badges visible simultaneously.
    // Pre-fix: 3 × fill=ACCENT badge rects = 3 ACCENT marks.
    // Post-fix: badges are INK/PAPER stroked — zero ACCENT.
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 12);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBe(0);
  });

  test("hatch patterns are present in SVG defs (Shell mounts HatchDefs)", () => {
    const { container } = render(<Tls0RttReplay />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-light")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-dense")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-cross")).not.toBeNull();
  });

  test("replay messages do NOT have stroke=ACCENT (replay = glyph/hatch, not yellow)", () => {
    // Step to F9 where both replay messages (id=9, id=10) are visible with non-null highlight.
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 9);
    const svgEl = container.querySelector("svg");
    expect(svgEl).not.toBeNull();
    const svg = svgEl as SVGSVGElement;
    // All lines in the SVG canvas (excluding defs) must not have stroke=ACCENT.
    const lines = Array.from(svg.querySelectorAll("line, path")).filter((el) => {
      if (el.getAttribute("stroke") !== ACCENT_HEX) return false;
      let ancestor = el.parentElement;
      while (ancestor && ancestor !== (svg as Element)) {
        if (ancestor.tagName.toLowerCase() === "defs") return false;
        ancestor = ancestor.parentElement;
      }
      return true;
    });
    expect(lines.length).toBe(0);
  });

  test("ATTACKER column header does NOT have fill=ACCENT (actor column not yellow)", () => {
    // Step to F7 where the ATTACKER column first appears (phase 3).
    const { container } = render(<Tls0RttReplay />);
    advanceTo(container, 7);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // ATTACKER_X = 360, COL_W = 100 — the actor header rect is at x=360.
    // It must NOT be filled ACCENT.
    const attackerRect = Array.from(svg!.querySelectorAll("rect")).find((r) => {
      const x = parseFloat(r.getAttribute("x") ?? "-1");
      return Math.abs(x - 360) < 2;
    });
    // If the attacker column is rendered, its fill must not be ACCENT.
    if (attackerRect) {
      expect(attackerRect.getAttribute("fill")).not.toBe(ACCENT_HEX);
    }
    // Also: total ACCENT count ≤1 on this frame (highlightedMessageId=8 focal).
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });
});
