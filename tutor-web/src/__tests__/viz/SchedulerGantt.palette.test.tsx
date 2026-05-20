/**
 * V12 palette compliance: Yellow = focus only, categories via hatch not opacity.
 *
 * Rule: Within a SchedulerGantt frame showing ≥2 distinct jobs, at most one SVG
 * element may carry fill="#facc15". Job segments MUST use distinct fills drawn
 * from the hatch/ink vocabulary — NOT opacity-as-category.
 */
import { describe, expect, test } from "vitest";
import { render, fireEvent } from "@testing-library/react";
import { SchedulerGantt } from "../../components/viz/SchedulerGantt";
import { ACCENT, INK, HATCH_LIGHT, HATCH_DENSE, HATCH_CROSS } from "../../components/viz/theme";

const ACCENT_HEX = "#facc15";
const PAPER_HEX = "#f5f5f0";
const ALLOWED_JOB_FILLS = new Set([INK, HATCH_LIGHT, HATCH_DENSE, HATCH_CROSS, ACCENT]);

/** Advance to a frame by clicking the step-fwd button N times. */
function advanceTo(container: HTMLElement, steps: number) {
  const btn = container.querySelector("[data-testid='sched-gantt-step-fwd']") as HTMLElement;
  if (!btn) throw new Error("Forward button not found — check testIdPrefix ('sched-gantt-step-fwd')");
  for (let i = 0; i < steps; i++) {
    fireEvent.click(btn);
  }
}

describe("SchedulerGantt — V12 palette compliance", () => {
  test("at most ONE ACCENT fill element in SVG (yellow = focus only, not category)", () => {
    const { container } = render(<SchedulerGantt />);
    // Advance to frame 3 (FCFS t=8, shows J1 full bar + J2 started)
    advanceTo(container, 2);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();

    // Count ALL SVG elements (rect + path) with fill=ACCENT_HEX
    const accentRects = Array.from(svg!.querySelectorAll("rect, path")).filter(
      (el) => el.getAttribute("fill") === ACCENT_HEX
    );
    // At most one ACCENT element allowed (the current-time tick box or focal mark).
    // Job category rects must NOT be yellow.
    expect(accentRects.length).toBeLessThanOrEqual(1);
  });

  test("job category fills come from hatch/ink vocabulary (not opacity tiers)", () => {
    const { container } = render(<SchedulerGantt />);
    // Advance to frame 6 (FCFS final, t=26) — all 4 jobs visible in Gantt + table
    advanceTo(container, 5);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    const allRects = Array.from(svg!.querySelectorAll("rect"));

    // Collect ALL fill values (excluding PAPER background rects and ACCENT focal marks)
    const markFills = allRects
      .map((r) => r.getAttribute("fill"))
      .filter((f): f is string => f !== null && f !== PAPER_HEX && f !== ACCENT_HEX);

    // Every fill must come from the allowed vocabulary
    for (const f of markFills) {
      expect(ALLOWED_JOB_FILLS, `unexpected fill value: ${f}`).toContain(f);
    }

    // At least 2 distinct fill values must be present among non-paper marks
    // (ensures jobs are not all rendered with the same single fill)
    const distinctFills = new Set(markFills);
    expect(distinctFills.size).toBeGreaterThanOrEqual(2);
  });

  test("INK-filled rects do NOT carry multiple distinct opacity tiers (no opacity-as-category)", () => {
    const { container } = render(<SchedulerGantt />);
    // Advance to frame 3 — shows J1 and (partial) J2 in Gantt
    advanceTo(container, 2);

    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    const allRects = Array.from(svg!.querySelectorAll("rect"));

    // Collect opacity of ALL rects that have fill=INK.
    // After the fix: J1 → INK (opacity 1); J2 → HATCH_DENSE (no INK fill).
    // So INK rects must all share the same opacity (no multi-tier opacity encoding).
    const inkRectOpacities = allRects
      .filter((r) => r.getAttribute("fill") === INK)
      .map((r) => r.getAttribute("opacity") ?? "1");

    const distinctOpacities = new Set(inkRectOpacities);
    // Old code: 4 distinct opacity tiers (0.85, 0.6, 0.4, 0.25).
    // New code: J1 alone has INK fill — only 1 opacity tier among INK rects.
    expect(distinctOpacities.size).toBeLessThanOrEqual(1);
  });

  test("hatch-light and hatch-dense patterns are present in SVG defs (AlgoStepperShell mounts HatchDefs)", () => {
    const { container } = render(<SchedulerGantt />);
    const svg = container.querySelector("svg");
    expect(svg).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-light")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-dense")).not.toBeNull();
    expect(svg!.querySelector("pattern#hatch-cross")).not.toBeNull();
  });
});
