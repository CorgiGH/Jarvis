import { test, expect } from "@playwright/test";

/**
 * Track B smoke: routed viz + drill card paint on the REAL tutor drill surface.
 *
 * Real tutor route (App.tsx analysis):
 *   - BrowserRouter basename="/tutor"
 *   - TutorWorkspace renders at pathname "/" (within the basename) when ?taskId is set
 *   - Full URL: http://localhost:5173/tutor/?taskId=task-1
 *
 * First-paint API calls the app makes (logged via page.on("request")) and how they
 * are stubbed here to avoid 401 redirects / auth gating:
 *
 *   POST /api/v1/tutor/auto-session  → 200 {} (prevents 401 → /login redirect)
 *   GET  /api/v1/me/export           → 200 { aiLiteracyConfirmed: true, user: { lang: "ro" } }
 *                                       (prevents /welcome/ai-literacy redirect)
 *   GET  /api/v1/last-task           → 200 { taskId: "task-1" }
 *   POST /api/v1/last-task           → 200 {}
 *   GET  /api/v1/fsrs/forecast       → 200 { dueNow: 0 }
 *   GET  /api/v1/tasks               → 200 { tasks: [{ id: "task-1" }] }
 *   GET  /api/v1/tasks/task-1/prep   → 200 (drill bundle with vizId: "recursion-tree")
 *   POST /api/v1/task/task-1/reprep  → 200 {} (defensive; prep hit prevents this firing)
 */
test("routed viz + drill card paint on the real tutor surface, no errors", async ({ page }) => {
  const bad: { url: string; status: number }[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push({ url: r.url(), status: r.status() });
  });

  // Log all API requests to aid debugging if the test fails.
  page.on("request", (req) => {
    if (req.url().includes("/api/")) {
      // eslint-disable-next-line no-console
      console.log(`[api-req] ${req.method()} ${req.url()}`);
    }
  });

  // ── Stub: GET /api/v1/tutor/auto-session → 200 (authenticated) ──
  // Without this, ensureTutorSession() returns 401 (or 500 via vite proxy to dead backend)
  // and the app redirects to /login. The call is a bare fetch (default GET).
  await page.route("**/api/v1/tutor/auto-session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );

  // ── Stub: GET /api/v1/me/export → aiLiteracyConfirmed: true ──
  // Without this, the app redirects to /welcome/ai-literacy gate.
  await page.route("**/api/v1/me/export", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }),
    }),
  );

  // ── Stub: GET /api/v1/last-task → task-1 ──
  await page.route("**/api/v1/last-task", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ taskId: "task-1" }),
      });
    } else {
      // POST /api/v1/last-task (sync last-task cookie)
      route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });

  // ── Stub: GET /api/v1/fsrs/forecast → no cards due ──
  await page.route("**/api/v1/fsrs/forecast", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ dueNow: 0 }),
    }),
  );

  // ── Stub: GET /api/v1/tasks → task-1 exists ──
  // App.tsx checks this to decide whether to show TutorWorkspace or QuickStart.
  await page.route("**/api/v1/tasks", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ tasks: [{ id: "task-1" }] }),
      });
    } else {
      route.continue();
    }
  });

  // ── Stub: GET /api/v1/tasks/task-1/prep → drill bundle with vizId ──
  // This is the primary stub: returns a problemsJson + drillsJson carrying
  // vizId: "recursion-tree" so the RoutedViz panel mounts.
  await page.route("**/api/v1/tasks/*/prep", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        taskId: "task-1",
        generatedAt: "2026-05-29T00:00:00Z",
        version: 1,
        problemsJson: JSON.stringify([
          {
            problemId: "p1",
            page: 0,
            statement: "Trace fib(5) — what is the recursion tree?",
            shape: "analysis-trace",
            vizId: "recursion-tree",
          },
        ]),
        drillsJson: JSON.stringify({
          p1: {
            drill: "Trace fib(5) — what is the recursion tree?",
            worked: "fib(5) = fib(4) + fib(3) = … = 5",
            definition: "Recursion: a function defined in terms of itself.",
            check: "Trace fib(4).",
            expectedAnswerHint: "5",
            vizId: "recursion-tree",
          },
        }),
        railJson: "[]",
      }),
    }),
  );

  // ── Stub: POST /api/v1/task/task-1/reprep → 200 (defensive; prep hit prevents this) ──
  await page.route("**/api/v1/task/*/reprep", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );

  // ── Navigate to the real tutor workspace ──
  // BrowserRouter basename="/tutor"; workspace renders at "/" when ?taskId is set.
  await page.goto("/tutor/?taskId=task-1");

  // ── Assert: drill card visible ──
  await expect(page.getByTestId("drill-card").first()).toBeVisible({ timeout: 10000 });

  // ── Assert: routed viz panel painted (RoutedViz stamps data-testid="routed-viz-<id>") ──
  await expect(page.getByTestId("routed-viz-recursion-tree")).toBeVisible({ timeout: 10000 });

  // ── Assert: recursion-tree-root visible (AlgoStepperShell stamps data-testid="${testIdPrefix}-root") ──
  await expect(page.getByTestId("recursion-tree-root")).toBeVisible({ timeout: 10000 });

  // ── Assert: no error text on page ──
  await expect(page.locator("body")).not.toContainText(/404|not found|error/i);

  // ── Assert: zero 4xx/5xx responses ──
  expect(bad, `4xx/5xx responses: ${JSON.stringify(bad)}`).toHaveLength(0);
});
