import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// The family renders inside AlgoStepperShell with testIdPrefix="graph-tree". The 4 faithful KCs
// carry no figure (§0.6 #1), so the lesson route never shows a figure-bound reveal; the family's
// real user-facing surface is the /tutor/viz-demo gallery card (Task 8 Step 7, REQUIRED mount).
// Route: /tutor/viz-demo paints the gallery; the graph-tree card carries data-testid="graph-tree-root".
test("graph-tree family: no clip at 2 viewports, scrubber back/forward works", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  await page.goto("/tutor/viz-demo");
  await expect(page.getByTestId("graph-tree-root")).toBeVisible({ timeout: 10000 });

  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "graph-tree-root");
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  // scrubber forward then back must change the frame counter (no one-shot)
  const counter = page.getByTestId("graph-tree-frame-counter");
  const start = await counter.textContent();
  await page.getByTestId("graph-tree-step-fwd").click();
  const afterFwd = await counter.textContent();
  expect(afterFwd).not.toBe(start);
  await page.getByTestId("graph-tree-step-back").click();
  expect(await counter.textContent()).toBe(start);

  // re-check no-clip after stepping (layout must not drift into a clip mid-animation)
  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "graph-tree-root");
  }

  // Scope the error-text check to the graph-tree-root subtree only (the full page includes
  // OsiEncap which renders "HTTP 1400B" — valid viz content that falsely matches /HTTP \d{3}/i).
  const graphRoot = page.getByTestId("graph-tree-root");
  await expect(graphRoot.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx:\n${bad.join("\n")}`).toEqual([]);
});
