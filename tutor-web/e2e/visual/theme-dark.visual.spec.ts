import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — visual baseline of the DARK theme reference page.
 *
 * DESIGN.md "Two surfaces, never a third": the brutalist DARK surface. Served by the Lane-B-owned
 * harness (theme-ref.html?surface=dark), which renders a self-contained inline specimen driven by
 * index.css tokens — a token change reflows this baseline. No /api, no emoji, SVG figure, self-hosted
 * fonts (Task 1) → a 0-tolerance full-page shot is deterministic on the reference OS.
 */
test("DARK theme reference page matches the baseline", async ({ page }) => {
  await page.goto("/tutor/theme-ref.html?surface=dark"); // PM AMENDMENT: Vite base "/tutor/"
  await expect(page.getByTestId("theme-ref-dark")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("door-brutalist")).toBeVisible();
  await expect(page).toHaveScreenshot("theme-dark.png", { fullPage: true });
});
