import { test, expect, type Page } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";

// Alex's REAL screen: 1080p @ 125% = logical 1536×864; content area ≈648 (with bookmarks) and ≈730.
// The standing viz_no_clip_gate asserts ZERO overlap + ZERO clipping at HIS viewport heights —
// viewport (not fullPage) — before AND after stepping EVERY frame. See reference_alex_screen_resolution.
const VIEWPORTS = [
  { width: 1536, height: 648 },
  { width: 1536, height: 730 },
] as const;

// Both matrix-grid family instances live on the /tutor/viz-demo gallery (REQUIRED mounts).
//  - DP fib(5):           prefix "mg",  6 fill steps (1×6 grid).
//  - Gauss elimination:   prefix "mgg", 7 steps (3×4 augmented, partial-pivot row-swap + rowOp rail).
const INSTANCES = [
  { prefix: "mg", total: 6, label: "DP fib (1×6)" },
  { prefix: "mgg", total: 7, label: "Gauss elimination (3×4, row-swap)" },
] as const;

async function sweepNoClip(page: Page, prefix: string) {
  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, `${prefix}-root`);
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });
}

for (const inst of INSTANCES) {
  test(`matrix-grid family — ${inst.label}: no clip before+after stepping at 2 viewports`, async ({ page }) => {
    const bad: string[] = [];
    page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

    await page.goto("/tutor/viz-demo");
    await expect(page.getByTestId(`${inst.prefix}-root`)).toBeVisible({ timeout: 10000 });

    const counter = page.getByTestId(`${inst.prefix}-frame-counter`);
    const fwd = page.getByTestId(`${inst.prefix}-step-fwd`);
    const back = page.getByTestId(`${inst.prefix}-step-back`);

    await sweepNoClip(page, inst.prefix);
    expect(await counter.textContent()).toContain(`1 / ${inst.total}`);

    for (let beat = 1; beat < inst.total; beat++) {
      await fwd.click();
      await sweepNoClip(page, inst.prefix);
    }

    // scrubber round-trips the counter (not a one-shot)
    for (let b = 1; b < inst.total; b++) await back.click();
    const start = await counter.textContent();
    expect(start).toContain(`1 / ${inst.total}`);
    await fwd.click();
    expect(await counter.textContent()).not.toBe(start);
    await back.click();
    expect(await counter.textContent()).toBe(start);

    const root = page.getByTestId(`${inst.prefix}-root`);
    await expect(root.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
    expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
  });
}
