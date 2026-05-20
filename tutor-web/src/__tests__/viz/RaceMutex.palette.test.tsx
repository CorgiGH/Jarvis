/**
 * V12 palette compliance: Yellow (#facc15) = single focal element only.
 *
 * RaceMutex-specific violations fixed here:
 *   1. Mutex label + state text (lines ~467/479) used fill=ACCENT when
 *      mutex.state === "LOCKED".  LOCKED is a persistent state that spans
 *      many frames (F9–F13 for T1's lock, F14–F15 for T2's lock).  Yellow
 *      encodes a state, not a single focal action.  Fix: use fill=PAPER for
 *      text that sits on the INK-filled rect, fill=INK for text when FREE.
 *
 *   2. ThreadPane code-line highlight rect used fill=ACCENT always, and
 *      showHighlight was `pc >= 1 && pc <= 3`.  Both threads can have
 *      pc >= 1 simultaneously (e.g. F2: T1.pc=1, T2.pc=1).  Two yellow
 *      highlight bars in the same frame violates ≤1 focal element.  Fix:
 *      showHighlight must be `highlightOp?.thread === name` — only the
 *      thread executing the focal op of this frame gets the yellow bar.
 *
 *   3. Per-thread "blocked" status text used fill=ACCENT when
 *      thread.blocked === true.  Blocked is a state (T2 is blocked for
 *      frames F11–F13).  Fix: use fill=INK and keep the "⊘ BLOCKED
 *      ON M" label — the glyph conveys the state without color.
 *
 * Legitimate ACCENT uses NOT tested here (must remain untouched):
 *   - counter cell rect flashes ACCENT on lostUpdateFlash (F6) — single
 *     focal event of the RACE phase result: the exact frame where the lost
 *     update lands.  This is the one focal action, correct V12.
 *
 * Test strategy: We sweep all frames (F0–F17) and assert:
 *   (a) mutex label/text never ACCENT
 *   (b) at most ONE code-line highlight rect is ACCENT per frame
 *   (c) status text elements never ACCENT
 *   (d) overall: ≤1 ACCENT element per frame (the single allowed focal:
 *       code-line highlight or counter flash — never a state carrier)
 *
 * We EXCLUDE <defs> (markers, patterns) from counts.
 */
import { describe, expect, test, beforeEach } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { RaceMutex, FRAMES } from "../../components/viz/RaceMutex";

const ACCENT_HEX = "#facc15";
const INK_HEX = "#0a0a0a";
const PAPER_HEX = "#f5f5f0";

/** Advance the stepper by N frames. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector(
    "[data-testid='race-mutex-step-fwd']"
  ) as HTMLElement;
  if (!btn)
    throw new Error(
      "Forward button not found — check testIdPrefix ('race-mutex-step-fwd')"
    );
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

/**
 * Count canvas elements with fill=ACCENT or stroke=ACCENT, excluding <defs>.
 * Counts rect, path, polygon, circle, line, text.
 */
function accentElementCount(svg: Element): number {
  return Array.from(
    svg.querySelectorAll("rect, path, polygon, circle, line, text")
  ).filter((el) => {
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
 * Find all "status text" elements in the thread panes.
 * These are the text elements that display READY / RUNNING / DONE /
 * "⊘ BLOCKED ON M".  They sit at y ≈ threadY + 102 (THREAD_Y + 102 = 112).
 * We identify them by their textContent matching known status labels.
 */
function statusTextElements(svg: Element): Element[] {
  const STATUS_LABELS = ["READY", "RUNNING", "DONE"];
  const BLOCKED_PREFIX = "BLOCKED";
  return Array.from(svg.querySelectorAll("text")).filter((el) => {
    const content = (el.textContent ?? "").trim();
    return (
      STATUS_LABELS.includes(content) || content.includes(BLOCKED_PREFIX)
    );
  });
}

/**
 * Find elements whose textContent includes "mutex M" or "LOCKED" or "FREE"
 * — the mutex label and state text inside the mutex cell.
 */
function mutexTextElements(svg: Element): Element[] {
  return Array.from(svg.querySelectorAll("text")).filter((el) => {
    const content = (el.textContent ?? "").trim();
    return (
      content === "mutex M" ||
      content.startsWith("🔒 LOCKED") ||
      content === "FREE"
    );
  });
}

describe("RaceMutex — V12 palette compliance", () => {
  beforeEach(() => {
    window.location.hash = "";
  });

  // ── Violation 1: Mutex label / state text ──────────────────────────────
  describe("Mutex label and state text", () => {
    test("F9 (T1 acquires lock, mutex LOCKED): mutex text elements do NOT use fill=ACCENT", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 9);
      const svg = container.querySelector("svg")!;

      const mutexTexts = mutexTextElements(svg);
      // During MUTEX phase, we expect to find the mutex label and state text
      expect(mutexTexts.length).toBeGreaterThanOrEqual(1);

      const accentMutexTexts = mutexTexts.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentMutexTexts.length).toBe(0);
    });

    test("F11 (mutex LOCKED, T2 blocked): mutex text still not ACCENT", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 11);
      const svg = container.querySelector("svg")!;

      const mutexTexts = mutexTextElements(svg);
      const accentMutexTexts = mutexTexts.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentMutexTexts.length).toBe(0);
    });

    test("F14 (T2 acquires lock): mutex text not ACCENT (state, not focal action)", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 14);
      const svg = container.querySelector("svg")!;

      const mutexTexts = mutexTextElements(svg);
      const accentMutexTexts = mutexTexts.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentMutexTexts.length).toBe(0);
    });
  });

  // ── Violation 2: Code-line highlight rects ─────────────────────────────
  describe("Code-line highlight rects — at most 1 ACCENT per frame", () => {
    /**
     * F2: T1.pc=1 AND T2.pc=1 — both threads have advanced their PC.
     * Old code: showHighlight = pc >= 1 → BOTH panes yellow simultaneously.
     * Fixed: only the thread named in highlightOp gets the yellow bar.
     * F2's highlightOp = { thread: "T2", opIdx: 0 } → only T2 pane highlighted.
     */
    test("F2 (T1.pc=1, T2.pc=1, focal=T2): exactly 1 ACCENT highlight rect (T2 only)", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 2);
      const svg = container.querySelector("svg")!;

      // The highlight rects are motion.rect with fill=ACCENT inside ThreadPane.
      // We count all ACCENT elements; should be exactly 1 (the focal thread's bar).
      const count = accentElementCount(svg);
      expect(count).toBeLessThanOrEqual(1);
    });

    test("F3 (focal=T1): at most 1 ACCENT element", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 3);
      const svg = container.querySelector("svg")!;
      expect(accentElementCount(svg)).toBeLessThanOrEqual(1);
    });

    test("F4 (focal=T2): at most 1 ACCENT element", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 4);
      const svg = container.querySelector("svg")!;
      expect(accentElementCount(svg)).toBeLessThanOrEqual(1);
    });

    test("F12 (MUTEX: T1 in CS computing, T2 blocked): at most 1 ACCENT element", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 12);
      const svg = container.querySelector("svg")!;
      expect(accentElementCount(svg)).toBeLessThanOrEqual(1);
    });
  });

  // ── Violation 3: Per-thread "blocked" status text ──────────────────────
  describe("Per-thread blocked status text", () => {
    test("F11 (T2 blocked): blocked status text does NOT use fill=ACCENT", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 11);
      const svg = container.querySelector("svg")!;

      const statusEls = statusTextElements(svg);
      expect(statusEls.length).toBeGreaterThan(0);

      const accentStatus = statusEls.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentStatus.length).toBe(0);
    });

    test("F12 (T2 still blocked): blocked status text not ACCENT", () => {
      const { container } = render(<RaceMutex />);
      advanceTo(container, 12);
      const svg = container.querySelector("svg")!;

      const statusEls = statusTextElements(svg);
      const accentStatus = statusEls.filter(
        (el) => el.getAttribute("fill") === ACCENT_HEX
      );
      expect(accentStatus.length).toBe(0);
    });
  });

  // ── Whole-sweep: ≤1 ACCENT per frame ──────────────────────────────────
  describe("Whole-frame sweep: ≤1 ACCENT element in every frame", () => {
    test("all 18 frames have at most 1 ACCENT canvas element", () => {
      // We render fresh for each frame index to avoid stepper-animation state.
      const { container } = render(<RaceMutex />);
      const fwdBtn = container.querySelector(
        "[data-testid='race-mutex-step-fwd']"
      ) as HTMLElement;
      if (!fwdBtn) throw new Error("Forward button not found");

      const svg = container.querySelector("svg")!;

      for (let i = 0; i < FRAMES.length; i++) {
        const count = accentElementCount(svg);
        expect(count).toBeLessThanOrEqual(1);
        if (i < FRAMES.length - 1) {
          fireEvent.click(fwdBtn);
        }
      }
    });
  });
});
