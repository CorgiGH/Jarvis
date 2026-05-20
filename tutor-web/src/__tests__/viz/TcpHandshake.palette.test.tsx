/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * Rule: At most ONE SVG element in a TcpHandshake frame may carry
 * fill="#facc15" OR stroke="#facc15".
 *
 * Violations before fix:
 *   - MessageArrow: SYN-ACK + spoofed arrows unconditionally ACCENT (yellow = kind)
 *   - Dropped-message × glyph: ACCENT fill (yellow = dropped category)
 *   - Spoofed backlog slots: ACCENT fill (yellow = spoofed category)
 *   - BACKLOG FULL border: ACCENT stroke (yellow = full-backlog category)
 *   - ATTACKER column header: ACCENT fill when active (yellow = actor category)
 *   - SUMMARY CLIENT/SERVER headers: ACCENT fill (yellow = "won" category, 2 at once)
 *   - SYN COOKIES ON badge: ACCENT fill (yellow = mode category)
 *
 * After fix: each frame has ≤1 ACCENT element (the focal/active message arrow,
 * if any).  Categories (spoofed, ATTACKER, backlog-full, SYN-ACK kind, cookies-on)
 * are encoded via hatch fill, stroke-dash, or label — not yellow.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { TcpHandshake } from "../../components/viz/TcpHandshake";

const ACCENT_HEX = "#facc15";

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='tcp-handshake-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('tcp-handshake-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Count all SVG canvas elements (rect, path, polygon, circle, line, text)
 * that carry fill=ACCENT_HEX OR stroke=ACCENT_HEX, EXCLUDING <defs> subtrees.
 *
 * We include `line` and `text` because arrows and drop-× glyphs may use those.
 * We exclude <defs> (markers, patterns) — they're not rendered canvas marks.
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

describe("TcpHandshake — V12 palette compliance", () => {
  // AlgoStepperShell persists the current frame idx to window.location.hash
  // (writeHashIdx / parseHashIdx). Without clearing the hash between tests,
  // each test inherits the idx left by the previous test and advanceTo()
  // lands on a wrong frame. Reset before each test.
  beforeEach(() => {
    window.location.hash = "";
  });

  test("F0 (INIT, no messages): zero ACCENT elements", () => {
    const { container } = render(<TcpHandshake />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    const count = accentElementCount(svg!);
    expect(count).toBe(0);
  });

  test("F1 (SYN in flight, 1 message): at most 1 ACCENT element", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 1);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F3 (SYN-ACK in flight): at most 1 ACCENT element — SYN-ACK is not yellow-by-kind", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 3);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: SYN-ACK arrow = ACCENT (kind-encoding) → violates rule.
    // Post-fix: SYN-ACK uses stroke-dash, stays INK; ≤1 ACCENT total.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F8 (SYN-flood: 1 spoofed backlog slot visible): at most 1 ACCENT element", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 8);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: spoofed slot fill=ACCENT + ATTACKER header fill=ACCENT → ≥2 yellows.
    // Post-fix: spoofed slot uses HATCH_DENSE; ATTACKER header uses INK/PAPER+stroke.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F11 (backlog FULL — all 4 spoofed slots + FULL border): at most 1 ACCENT element", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 11);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: 4 spoofed slots (ACCENT fill) + FULL border (ACCENT stroke)
    //          + ATTACKER header (ACCENT fill) = 6+ ACCENT marks.
    // Post-fix: spoofed slots hatched; FULL border INK; ATTACKER header INK.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F12 (legit SYN dropped): at most 1 ACCENT element — drop × is not yellow", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 12);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: 4 spoofed slots (ACCENT) + ATTACKER header (ACCENT) +
    //          FULL border (ACCENT stroke) + drop × (ACCENT fill) = many.
    // Post-fix: ≤1 ACCENT (could be the focal dropped-message indicator if kept
    //           accent, or 0 if the drop is encoded with INK + dash only).
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F14 (COOKIES: spoofed SYN + server SYN-ACK response — 2 in-flight messages): at most 1 ACCENT element", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 14);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // Pre-fix: both arrows ACCENT (one is SYN-ACK kind, one has spoofedSrc)
    //          + ATTACKER header + COOKIES ON badge = 4+ ACCENT marks.
    // Post-fix: kind encoded by dash; COOKIES badge uses INK/PAPER stroke; ≤1 ACCENT.
    expect(accentElementCount(svg!)).toBeLessThanOrEqual(1);
  });

  test("F15 (COOKIES: 3 in-flight messages — all 3 message kinds): exactly 1 ACCENT element on focal (ACK) indicator", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 15);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // F15 has SYN + SYN-ACK + ACK simultaneously.  The ACK is the transition-
    // causing message (server re-hashes → ESTABLISHED), so it is the focal
    // message (last in inFlight, idx=2).  Exactly 1 ACCENT element must exist —
    // the FocalIndicator diamond polygon rendered outside AnimatePresence at
    // the midpoint of the focal ACK arrow.
    // The ≤1 rule is still satisfied; we also verify it is not 0 (focus applied).
    expect(accentElementCount(svg!)).toBe(1);
  });

  test("F16 (SUMMARY — no in-flight messages): exactly 0 ACCENT elements", () => {
    const { container } = render(<TcpHandshake />);
    advanceTo(container, 16);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // F16 is a pure summary frame: inFlight=[], phase="SUMMARY".
    // No active transition → no focal arrow → zero ACCENT elements.
    // Pre-fix: CLIENT header ACCENT fill + SERVER header ACCENT fill = 2 yellows.
    // Post-fix: SUMMARY victory encoded via INK border + label text, not yellow.
    expect(accentElementCount(svg!)).toBe(0);
  });

  test("hatch patterns are present in SVG defs (Shell mounts HatchDefs)", () => {
    const { container } = render(<TcpHandshake />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-light")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-dense")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-cross")).not.toBeNull();
  });

  test("spoofed backlog slots do NOT have fill=ACCENT (spoofed = hatch, not yellow)", () => {
    const { container } = render(<TcpHandshake />);
    // F11 has all 4 spoofed slots
    advanceTo(container, 11);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    // BACKLOG_X = 446, BACKLOG_SLOT_W = 22.
    // The slot rects sit at x=446.  They must NOT have fill=ACCENT.
    const slotRects = Array.from(svg!.querySelectorAll("rect")).filter((r) => {
      const x = parseFloat(r.getAttribute("x") ?? "-1");
      return x >= 440 && x <= 452; // BACKLOG_X ± tolerance
    });
    // We expect to find some slot rects (the component renders them)
    const accentSlots = slotRects.filter(
      (r) => r.getAttribute("fill") === ACCENT_HEX
    );
    expect(accentSlots.length).toBe(0);
  });
});
