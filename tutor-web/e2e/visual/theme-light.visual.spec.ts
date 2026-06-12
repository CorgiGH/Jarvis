import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — visual baseline of the LIGHT theme reference page.
 *
 * DESIGN.md "Two surfaces, never a third": the brutalist LIGHT work surface (page-bg/page-fg/accent;
 * "long-form reading stays on light", DESIGN.md:115). Served by the Lane-B-owned harness
 * (theme-ref.html?surface=light), driven entirely by index.css tokens. No /api, no emoji, deterministic.
 */
test("LIGHT theme reference page matches the baseline", async ({ page }) => {
  await page.goto("/tutor/theme-ref.html?surface=light"); // PM AMENDMENT: Vite base "/tutor/"
  await expect(page.getByTestId("theme-ref-light")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("theme-ref-light-begin")).toBeVisible();
  await expect(page).toHaveScreenshot("theme-light.png", { fullPage: true });
});
