/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * Rule: At most ONE SVG element in an OsiEncap frame may carry fill="#facc15".
 * PacketGlyph rects must NOT all be yellow (yellow is not "packet category").
 * The active layer row must NOT be filled ACCENT (row highlight via stroke, not fill).
 * The single focal packet (the one in the highlightLayer row) MAY be ACCENT.
 */
import { describe, expect, test } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { OsiEncap } from "../../components/viz/OsiEncap";

const ACCENT_HEX = "#facc15";

/**
 * Return all rendered packet-glyph rects for the given frame step index.
 *
 * OsiEncap uses AnimatePresence which keeps exiting motion.g elements in the DOM
 * in JSDOM (exit animations don't run). Each PacketGlyph motion.g carries
 * data-packet-step={frameIdx} so we can scope queries to only the active frame's
 * packets, ignoring stale elements from previous frames.
 */
function activePacketRects(svg: Element, step: number): Element[] {
  // Query motion.g elements that belong to the current frame step.
  const activeGlyphs = Array.from(
    svg.querySelectorAll(`g[data-packet-step="${step}"]`)
  );
  // Collect all direct rect children of those groups.
  const rects: Element[] = [];
  for (const g of activeGlyphs) {
    rects.push(...Array.from(g.querySelectorAll("rect")));
  }
  return rects;
}

/**
 * Return all SVG rendered marks (rect/path/polygon/circle) that carry a given fill,
 * EXCLUDING elements inside <defs> (markers, patterns — not rendered canvas marks).
 * Used for the layer-row-rect check which is not packet-scoped.
 */
function accentMarksInSvg(svg: Element): Element[] {
  const all = Array.from(
    svg.querySelectorAll("rect, path, polygon, circle")
  );
  return all.filter((el) => {
    if (el.getAttribute("fill") !== ACCENT_HEX) return false;
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  });
}

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='osi-encap-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('osi-encap-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

describe("OsiEncap — V12 palette compliance", () => {
  test("initial frame (F0, step=0, 1 packet): at most ONE ACCENT fill packet rect", () => {
    const { container } = render(<OsiEncap />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    // Use data-packet-step scoping so AnimatePresence stale-DOM is excluded.
    const currentRects = activePacketRects(svg!, 0);
    const accentRects = currentRects.filter(
      (el) => el.getAttribute("fill") === ACCENT_HEX
    );

    // At most one ACCENT rect among current-frame packet glyphs (the focal packet).
    expect(accentRects.length).toBeLessThanOrEqual(1);
  });

  test("frame 5 (step=5, MTU fragment, 2 packets both at L3 sender): exactly ≤1 ACCENT fill", () => {
    // Frame 5 is the fragmentation frame: two packets (frag1, frag2) visible in the
    // SAME L3-sender row.  Pre-fix: both packets got fill=ACCENT → 2 yellow glyphs
    // + the row rect also yellow → 3 yellow marks.  Post-fix: at most 1.
    const { container } = render(<OsiEncap />);
    advanceTo(container, 5); // step from F0 → F5

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const currentRects = activePacketRects(svg!, 5);
    const accentRects = currentRects.filter(
      (el) => el.getAttribute("fill") === ACCENT_HEX
    );
    expect(accentRects.length).toBeLessThanOrEqual(1);
  });

  test("frame 6 (step=6, frag1 at L2, frag2 still at L3): exactly ≤1 ACCENT fill", () => {
    // Two packets at DIFFERENT layers.  highlightLayer = SENDER/L2 → only frag1 (L2)
    // is focal.  Pre-fix: both rects yellow + L2 row yellow → 3 yellows.
    const { container } = render(<OsiEncap />);
    advanceTo(container, 6); // step to F6

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const currentRects = activePacketRects(svg!, 6);
    const accentRects = currentRects.filter(
      (el) => el.getAttribute("fill") === ACCENT_HEX
    );
    expect(accentRects.length).toBeLessThanOrEqual(1);
  });

  test("frame 8 (step=8, frag1 arrived receiver L1, frag2 on wire): exactly ≤1 ACCENT fill", () => {
    // Two packets, different sides (RECEIVER/L1 and SENDER/L1).
    // highlightLayer = SENDER/L1.  Post-fix: only the frag2 glyph (focal) may be ACCENT.
    const { container } = render(<OsiEncap />);
    advanceTo(container, 8);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    const currentRects = activePacketRects(svg!, 8);
    const accentRects = currentRects.filter(
      (el) => el.getAttribute("fill") === ACCENT_HEX
    );
    expect(accentRects.length).toBeLessThanOrEqual(1);
  });

  test("active layer row does NOT carry fill=ACCENT (no yellow-on-yellow row background)", () => {
    // LayerRowRect used to animate fill to ACCENT when isActive.
    // Post-fix it must NOT do so.  Check on frame 3 (header-add, L4 sender active).
    const { container } = render(<OsiEncap />);
    advanceTo(container, 3);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    // Collect rendered ACCENT rects (not from defs) that are wide — those would be row rects.
    const accentRects = accentMarksInSvg(svg!).filter(
      (el) => el.tagName.toLowerCase() === "rect"
    );

    // If any ACCENT rect has width >= 200 (approaching COL_W=220), it is a row rect.
    const accentRowRects = accentRects.filter((r) => {
      const w = parseFloat((r as SVGRectElement).getAttribute("width") ?? "0");
      return w >= 200;
    });
    expect(accentRowRects.length).toBe(0);
  });

  test("hatch patterns are present in the SVG defs (Shell mounts HatchDefs)", () => {
    const { container } = render(<OsiEncap />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-light")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-dense")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-cross")).not.toBeNull();
  });
});
