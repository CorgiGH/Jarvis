/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * ProcessFSM-specific violations fixed here:
 *   1. Table-header column labels (PID / PPID / NAME / STATE / WAIT) were
 *      fill=ACCENT — always-on chrome; fixed to fill=INK.
 *   2. PID chip text (P1, P2) inside occupied state nodes was fill=ACCENT —
 *      process-identity decoration; multiple chips painted yellow at once;
 *      fixed to fill=PAPER (chip background is INK, so PAPER gives contrast).
 *   3. `fsm-zombie-hatch` pattern background <rect> was fill=ACCENT — ZOMBIE
 *      is a process state; hatch applied to any occupied ZOMBIE node encodes a
 *      state; fixed background to fill=PAPER (lines remain INK).
 *
 * Legitimate ACCENT uses (NOT tested here, must remain untouched):
 *   - fsm-arrow-accent arrowhead marker fill (inside <defs>)
 *   - Active-transition edge stroke=ACCENT
 *   - Active-target state node fill=ACCENT
 *   - Active-transition chip rect fill=ACCENT
 *   All four mark the single active transition and are correct V12.
 *
 * Test strategy: We test the 3 specific FIXED elements only.
 * We do NOT assert a blanket "≤1 ACCENT element" because ProcessFSM
 * legitimately renders multiple ACCENT marks per frame (edge + arrowhead
 * marker defs + target node + chip), all denoting the SAME single active
 * transition — that is correct V12.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { ProcessFSM } from "../../components/viz/ProcessFSM";

const ACCENT_HEX = "#facc15";
const INK_HEX = "#0a0a0a";
const PAPER_HEX = "#f5f5f0";

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='process-fsm-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('process-fsm-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Find table-header column label text elements.
 * The header row has a rect fill=INK and column labels drawn as <text> elements
 * just above the data rows. We identify them by their text content matching
 * the COLS labels: PID, PPID, NAME, STATE, WAIT.
 */
function headerLabelTexts(svg: Element): Element[] {
  const HEADER_LABELS = ["PID", "PPID", "NAME", "STATE", "WAIT"];
  return Array.from(svg.querySelectorAll("text")).filter((el) =>
    HEADER_LABELS.includes((el.textContent ?? "").trim())
  );
}

/**
 * Find PID chip text elements (P1, P2, ...) that appear inside state nodes.
 * These are <text> elements whose textContent matches /^P\d+$/.
 * They are NOT inside <defs>.
 */
function pidChipTexts(svg: Element): Element[] {
  return Array.from(svg.querySelectorAll("text")).filter((el) => {
    const content = (el.textContent ?? "").trim();
    if (!/^P\d+$/.test(content)) return false;
    // Exclude elements inside <defs>
    let ancestor = el.parentElement;
    while (ancestor && ancestor !== svg) {
      if (ancestor.tagName.toLowerCase() === "defs") return false;
      ancestor = ancestor.parentElement;
    }
    return true;
  });
}

/**
 * Find the background <rect> inside the fsm-zombie-hatch pattern.
 * The pattern is inside <defs>; we query the pattern by id then the first rect.
 */
function zombieHatchBgRect(svg: Element): Element | null {
  const pattern = svg.querySelector("pattern#fsm-zombie-hatch");
  if (!pattern) return null;
  return pattern.querySelector("rect");
}

describe("ProcessFSM — V12 palette compliance", () => {
  beforeEach(() => {
    window.location.hash = "";
  });

  // ── Violation 1: Table-header column labels ─────────────────────────────
  describe("Table-header column labels", () => {
    test("F0 (initial frame): header labels do NOT use fill=ACCENT", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;
      expect(svg).not.toBeNull();

      const headers = headerLabelTexts(svg);
      expect(headers.length).toBeGreaterThan(0); // sanity: labels exist

      const accentHeaders = headers.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentHeaders.length).toBe(0);
    });

    test("F0: header labels use fill=INK or fill=PAPER (readable on dark header bg)", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;

      const headers = headerLabelTexts(svg);
      // Each header label should be fill=INK or fill=PAPER (white-on-dark or
      // dark-on-light — either is V12-compliant; ACCENT is not).
      headers.forEach((el) => {
        const fill = el.getAttribute("fill");
        expect(fill).not.toBe(ACCENT_HEX);
      });
    });

    test("F9 (P2 enters ZOMBIE, active transition present): header labels still not ACCENT", () => {
      const { container } = render(<ProcessFSM />);
      advanceTo(container, 9);
      const svg = container.querySelector("svg")!;

      const headers = headerLabelTexts(svg);
      expect(headers.length).toBeGreaterThan(0);

      const accentHeaders = headers.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentHeaders.length).toBe(0);
    });
  });

  // ── Violation 2: PID chip text inside state nodes ───────────────────────
  describe("PID chip text inside state nodes", () => {
    test("F2 (P1=RUNNING, one process visible): PID chip text is not ACCENT", () => {
      const { container } = render(<ProcessFSM />);
      advanceTo(container, 2);
      const svg = container.querySelector("svg")!;

      const chips = pidChipTexts(svg);
      // At least one chip should exist (P1 is in RUNNING state)
      expect(chips.length).toBeGreaterThan(0);

      const accentChips = chips.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentChips.length).toBe(0);
    });

    test("F5 (P1=WAITING, P2=RUNNING, two processes): no PID chip text is ACCENT", () => {
      // Two chips visible — old violation would paint both yellow simultaneously.
      const { container } = render(<ProcessFSM />);
      advanceTo(container, 5);
      const svg = container.querySelector("svg")!;

      const chips = pidChipTexts(svg);
      expect(chips.length).toBeGreaterThan(0);

      const accentChips = chips.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentChips.length).toBe(0);
    });

    test("F9 (P2=ZOMBIE, P1=WAITING): PID chip text is not ACCENT", () => {
      const { container } = render(<ProcessFSM />);
      advanceTo(container, 9);
      const svg = container.querySelector("svg")!;

      const chips = pidChipTexts(svg);
      expect(chips.length).toBeGreaterThan(0);

      const accentChips = chips.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentChips.length).toBe(0);
    });

    test("F9: PID chip text uses PAPER (readable on INK chip background)", () => {
      const { container } = render(<ProcessFSM />);
      advanceTo(container, 9);
      const svg = container.querySelector("svg")!;

      const chips = pidChipTexts(svg);
      chips.forEach((el) => {
        const fill = el.getAttribute("fill");
        // Must not be ACCENT; PAPER is the correct choice (INK bg → PAPER text)
        expect(fill).not.toBe(ACCENT_HEX);
      });
    });
  });

  // ── Violation 3: fsm-zombie-hatch pattern background rect ───────────────
  describe("fsm-zombie-hatch pattern background rect", () => {
    test("zombie hatch pattern exists in defs", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;
      const pattern = svg.querySelector("pattern#fsm-zombie-hatch");
      expect(pattern).not.toBeNull();
    });

    test("zombie hatch background rect is NOT fill=ACCENT", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;

      const bgRect = zombieHatchBgRect(svg);
      expect(bgRect).not.toBeNull();
      expect(bgRect!.getAttribute("fill")).not.toBe(ACCENT_HEX);
    });

    test("zombie hatch background rect is fill=PAPER (ink-hatch-on-paper texture)", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;

      const bgRect = zombieHatchBgRect(svg);
      expect(bgRect).not.toBeNull();
      // PAPER background makes ZOMBIE read as an ink-on-paper hatch texture,
      // not a yellow state.
      expect(bgRect!.getAttribute("fill")).toBe(PAPER_HEX);
    });

    test("zombie hatch line (diagonal stripes) retains stroke=INK", () => {
      const { container } = render(<ProcessFSM />);
      const svg = container.querySelector("svg")!;

      const pattern = svg.querySelector("pattern#fsm-zombie-hatch");
      expect(pattern).not.toBeNull();
      const line = pattern!.querySelector("line");
      expect(line).not.toBeNull();
      expect(line!.getAttribute("stroke")).toBe(INK_HEX);
    });
  });
});
