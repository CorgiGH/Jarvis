import { test, expect } from "@playwright/test";

/**
 * Phase-6 /oggi interaction-smoke gate.
 *
 * Mounts the /oggi route with stubbed /api/v1/queue/today. Asserts:
 *   (1) oggi-screen + oggi-queue-panel paint on first load
 *   (2) zero 4xx/5xx during first paint
 *   (3) click first queue-item → no error text, no new 4xx/5xx
 *
 * Red→green proof: the test was authored BEFORE OggiScreen was mounted in
 * main.tsx (the route did not exist). It failed with a timeout on oggi-screen
 * (the app redirected to "/" via the catch-all). After T6-4 adds the /oggi
 * route the test goes green.
 */

const QUEUE_ITEMS = [
  {
    kc_id: "kc-1",
    kc_name_ro: "Recursie",
    kc_name_en: "Recursion",
    subject: "PA",
    phase: "practice",
    mastery_ewma: 0.55,
    fsrs_card_id: null,
    verification_status: "faithful",
    worked_example_first: false,
    mode: "drill",
  },
  {
    kc_id: "kc-2",
    kc_name_ro: "Arbori",
    kc_name_en: "Trees",
    subject: "PA",
    phase: "intro",
    mastery_ewma: 0.1,
    fsrs_card_id: null,
    verification_status: "unverified",
    worked_example_first: true,
    mode: "worked",
  },
  {
    kc_id: "kc-3",
    kc_name_ro: "Sortare",
    kc_name_en: "Sorting",
    subject: "ALO",
    phase: "retrieval",
    mastery_ewma: 0.85,
    fsrs_card_id: "card-3",
    verification_status: "faithful",
    worked_example_first: false,
    mode: "retrieve",
  },
];

test("Phase-6 /oggi: queue + next-kc panels paint + interact with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  // ── Session + shell stubs (same as phase5-core-loop) ──
  await page.route("**/api/v1/tutor/auto-session", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/v1/me/export", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }),
    }));
  await page.route("**/api/v1/last-task", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify(r.request().method() === "GET" ? { taskId: null } : {}),
    }));
  await page.route("**/api/v1/fsrs/forecast", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ dueNow: 0 }),
    }));
  await page.route("**/api/v1/tasks", (r) => {
    if (r.request().method() === "GET") {
      r.fulfill({
        status: 200, contentType: "application/json",
        body: JSON.stringify({ tasks: [] }),
      });
    } else r.continue();
  });

  // ── Queue stub ──
  await page.route("**/api/v1/queue/today", (r) =>
    r.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({
        items: QUEUE_ITEMS,
        total_due: QUEUE_ITEMS.length,
        day: "2026-06-09",
      }),
    }));

  // ── (1) Navigate and assert first-paint ──
  await page.goto("/tutor/oggi");

  await expect(page.getByTestId("oggi-screen")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("oggi-queue-panel")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("oggi-next-kc-panel")).toBeVisible();

  // Queue list rendered inside queue panel
  await expect(page.getByTestId("learner-queue-list")).toBeVisible();

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // ── (2) zero 4xx/5xx on first paint ──
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // ── (3) click first queue-item ──
  const firstItem = page.getByTestId("queue-item-kc-1");
  await expect(firstItem).toBeVisible();
  await firstItem.click();

  // No error text after click
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // No new 4xx/5xx after click
  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
});
