import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// The seq-array family renders inside AlgoStepperShell with testIdPrefix="seq-array". Its
// user-facing surface is the /tutor/viz-demo gallery card (REQUIRED mount). The standing no-clip
// gate (viz_no_clip_gate): ZERO overlapping text + ZERO clipping at ≥2 viewport heights, asserted
// before AND after stepping. The selection-sort trace is 18 steps (5-element array); the densest
// beats are the early scans where i/j/min pointers + callout + cells all paint together.
const TOTAL = 18;

test("seq-array family: no clip before+after stepping at 2 viewports", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  await page.goto("/tutor/viz-demo");
  await expect(page.getByTestId("seq-array-root")).toBeVisible({ timeout: 10000 });

  const counter = page.getByTestId("seq-array-frame-counter");
  const fwd = page.getByTestId("seq-array-step-fwd");
  const back = page.getByTestId("seq-array-step-back");

  // no-clip at the start across both viewports
  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "seq-array-root");
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });
  expect(await counter.textContent()).toContain(`1 / ${TOTAL}`);

  // step forward through several beats (scan beats, a swap, more scans) re-asserting no-clip
  for (let beat = 1; beat < 8; beat++) {
    await fwd.click();
    for (const vp of VIEWPORTS) {
      await page.setViewportSize(vp);
      await assertNoClip(page, "seq-array-root");
    }
    await page.setViewportSize({ ...VIEWPORTS[0] });
  }

  // scrubber forward then back must round-trip the frame counter (no one-shot)
  await back.click();
  await back.click();
  await back.click();
  await back.click();
  await back.click();
  await back.click();
  await back.click(); // back to beat 0
  const start = await counter.textContent();
  expect(start).toContain(`1 / ${TOTAL}`);
  await fwd.click();
  const afterFwd = await counter.textContent();
  expect(afterFwd).not.toBe(start);
  await back.click();
  expect(await counter.textContent()).toBe(start);

  // no error text + no 4xx/5xx, scoped to the seq-array subtree.
  const root = page.getByTestId("seq-array-root");
  await expect(root.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
});
