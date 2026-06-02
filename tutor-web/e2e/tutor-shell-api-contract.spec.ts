import { test, expect } from '@playwright/test';

/**
 * Stage-0 gating net (REPAIRED — replaces the vacuous door-real-backend.spec.ts).
 *
 * WHY THE OLD SPEC WAS A FALSE-GREEN (council 1780431232):
 *   The old `door-real-backend.spec.ts` did `page.goto('/')`. With playwright
 *   baseURL http://localhost:5173 that resolves to http://localhost:5173/ —
 *   OFF the `/tutor` base. The SPA mounts under <BrowserRouter basename="/tutor">
 *   (main.tsx) and vite serves it at base "/tutor/" (vite.config.ts). So `/`
 *   never exercised the mounted App's first-paint /api calls, yet asserted
 *   "zero 4xx/5xx" — vacuously true with no backend. A gate that can never fail
 *   gates nothing.
 *
 * WHAT THIS SPEC FIXES (grounded by live MCP-browser probe on this branch):
 *   1. Navigates under `/tutor/` so <App/> ACTUALLY mounts (verified: the
 *      header shell "JARVIS · TUTOR" + nav paints; without a stub the bare
 *      first /api call `GET /api/v1/tutor/auto-session` returns 401 and App
 *      redirects to /tutor/login — proof /api is really exercised under base).
 *   2. CI's frontend job starts NO Kotlin backend (.github/workflows/test.yml
 *      frontend job = npm ci -> npm test -> playwright -> npm run e2e, no :8080).
 *      So we STUB the first-paint /api contract at the Playwright layer (the
 *      same proven pattern as drill-viz-paint.spec.ts) — a deterministic
 *      "real route, real /api, zero 4xx/5xx" gate that runs without a JVM.
 *   3. Asserts the App shell paints (a real shell testid) AND zero 4xx/5xx on
 *      first paint. This is a TRUE gate: flip any stub to 5xx and it goes red
 *      (demonstrated red->green on this branch — see commit message).
 *
 * NOTE on the local working tree: the uncommitted door playground
 * (src/door/*, and main.tsx's `/door-compare` route) is unfinished work that
 * is NOT pushed and NOT present in CI. This spec asserts only the committed
 * App shell, which mounts identically with or without the door route.
 */
test('App shell mounts under /tutor and first-paint /api contract returns zero 4xx/5xx', async ({ page }) => {
  const bad: string[] = [];
  page.on('response', r => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  // ── Deterministic first-paint /api stubs (CI has no :8080 backend) ──
  // auto-session: 200 authenticated (else App redirects to /login).
  await page.route('**/api/v1/tutor/auto-session', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
  );
  // me/export: aiLiteracyConfirmed true (else App redirects to /welcome/ai-literacy).
  await page.route('**/api/v1/me/export', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: 'ro' } }),
    }),
  );
  // last-task: GET → none pinned (dashboard); POST → ack.
  await page.route('**/api/v1/last-task', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ taskId: null }) }),
  );
  // fsrs/forecast: no cards due.
  await page.route('**/api/v1/fsrs/forecast', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ dueNow: 0 }) }),
  );
  // tasks: empty list (default dashboard, no workspace pin).
  await page.route('**/api/v1/tasks', route => {
    if (route.request().method() === 'GET') {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tasks: [] }) });
    } else {
      route.continue();
    }
  });

  // ── Navigate under the /tutor base so <App/> mounts and /api fires ──
  await page.goto('/tutor/');

  // Sanity: we are actually under the base, not the off-base routeless page.
  expect(page.url()).toContain('/tutor');

  // ── Assert the App shell painted (real, always-rendered shell surface) ──
  // The header brand + nav render on every route regardless of body state.
  await expect(page.getByText('JARVIS · TUTOR')).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId('header-ledger-btn')).toBeVisible();

  // ── Assert no error text on first paint ──
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // ── Assert zero 4xx/5xx across first paint (the real gate) ──
  expect(bad, `4xx/5xx during first paint:\n${bad.join('\n')}`).toEqual([]);
});
