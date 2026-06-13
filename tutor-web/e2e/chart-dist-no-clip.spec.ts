import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// The chart-dist family renders inside AlgoStepperShell with testIdPrefix="chart-dist". Its
// user-facing surface is the /tutor/viz-demo gallery card (REQUIRED mount). The standing no-clip
// gate (viz_no_clip_gate): ZERO overlapping text + ZERO clipping at ≥2 viewport heights, asserted
// across ALL 4 reveal beats (the worst case is the final beat — interval marks + shade + the
// probability annotation all painted at once).
test("chart-dist family: no clip across all 4 reveal beats at 2 viewports", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  await page.goto("/tutor/viz-demo");
  await expect(page.getByTestId("chart-dist-root")).toBeVisible({ timeout: 10000 });

  const counter = page.getByTestId("chart-dist-frame-counter");
  const fwd = page.getByTestId("chart-dist-step-fwd");
  const back = page.getByTestId("chart-dist-step-back");

  // Step through every beat; re-assert no-clip at every beat × every viewport. The annotation beat
  // (last) is the densest, so any label collision would surface there.
  for (let beat = 0; beat < 4; beat++) {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize(vp);
      await assertNoClip(page, "chart-dist-root");
    }
    if (beat < 3) await fwd.click();
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  // scrubber forward then back must change the frame counter (no one-shot)
  await back.click();
  await back.click();
  await back.click(); // back to beat 0
  const start = await counter.textContent();
  expect(start).toContain("1 / 4");
  await fwd.click();
  const afterFwd = await counter.textContent();
  expect(afterFwd).not.toBe(start);
  await back.click();
  expect(await counter.textContent()).toBe(start);

  // no error text + no 4xx/5xx, scoped to the chart-dist subtree.
  const root = page.getByTestId("chart-dist-root");
  await expect(root.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
});
