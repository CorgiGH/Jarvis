import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D) — visual baseline of the AppShell FIXED shell at /tutor/.
 *
 * CI runs NO Kotlin backend (the frontend job is npm ci -> vitest -> playwright, no :8080), so the
 * first-paint /api contract is stubbed at the Playwright layer — the SAME stub set proven by
 * e2e/tutor-shell-api-contract.spec.ts. Without these, ensureTutorSession() 401s and <App/> redirects
 * to /tutor/login (a different, non-shell paint). The shot is fullPage with maxDiffPixelRatio 0
 * (config). Only the fixed shell is in frame on a no-data dashboard: header brand, nav, ledger button.
 */
test("AppShell fixed shell at /tutor/ matches the baseline", async ({ page }) => {
  // ── First-paint /api stubs (verbatim from tutor-shell-api-contract.spec.ts) ──
  await page.route("**/api/v1/tutor/auto-session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
  await page.route("**/api/v1/me/export", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }),
    }),
  );
  await page.route("**/api/v1/last-task", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ taskId: null }) }),
  );
  await page.route("**/api/v1/fsrs/forecast", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ dueNow: 0 }) }),
  );
  await page.route("**/api/v1/tasks", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ tasks: [] }) });
    } else {
      route.continue();
    }
  });

  await page.goto("/tutor/");

  // Anchor: the shell actually mounted (not a /login redirect) before we snapshot.
  // PM AMENDMENT 2026-06-12: "JARVIS · TUTOR" lives in TutorWorkspace, which only renders with an
  // active task — the no-task stubs paint ActiveTaskDashboard. app-shell + header-ledger-btn suffice.
  await expect(page.getByTestId("app-shell")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("header-ledger-btn")).toBeVisible();

  await expect(page).toHaveScreenshot("shell.png", { fullPage: true });
});
