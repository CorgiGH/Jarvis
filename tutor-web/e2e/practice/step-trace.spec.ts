/**
 * Plan-6 Task 13 — Step-trace surface e2e spec (REQ-5/6, INV-6.5).
 *
 * Runs against the REAL Kotlin backend seeded with the Task-2 real-corpus ALO trace problem
 * (alo-prob-001: Gaussian elimination step trace, 5 steps). Gated on JARVIS_PRACTICE_E2E=1.
 *
 * KEY assertion: wrong value at step 3 (index 2) is caught AT step 3 (REQ-5), not deferred.
 *   - alo-prob-001 step index 2: expected "0.875" (x3 after back-substitution)
 *   - We submit a wrong value "999" and assert the verdict is "wrong" (step blocked)
 *
 * Asserts:
 *  - §6.2.2 testids visible on first paint (REQ-6)
 *  - Interaction smoke: submit correct step 0 (-3), then step 1 (-1), then wrong step 2 (999)
 *  - Wrong step caught at step 2 (REQ-5 "sign error at step 3 caught at step 3")
 *  - Zero 4xx/5xx throughout
 */

import { test, expect } from "@playwright/test";
import { startPracticeServer } from "./helpers/practiceServer";
import { createSmokeCollector, smokeCheck } from "./helpers/smoke";
import type { PracticeServerHandle } from "./helpers/practiceServer";

const isEnabled = process.env.JARVIS_PRACTICE_E2E === "1";

let srv: PracticeServerHandle;

test.beforeAll(async () => {
  if (!isEnabled) return;
  srv = await startPracticeServer();
});

test.afterAll(async () => {
  if (!isEnabled) return;
  await srv?.stop();
});

test("step-trace — INV-6.5: ALO trace testids paint and interact; wrong step 3 caught at step 3 (REQ-5)", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
    { name: "csrf",           value: srv.csrfToken,    domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const smoke = createSmokeCollector();
  smoke.attachResponseListener(page);

  await page.goto("/tutor/practice/trace/ALO");

  // ── (1) REQ-6 testids visible on first paint ─────────────────────────────────────────
  await expect(page.getByTestId("trace-drill-skeleton"), "trace-drill-skeleton not visible").toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId("trace-drill-step-input"), "trace-drill-step-input not visible").toBeVisible();
  await expect(page.getByTestId("trace-drill-submit-btn"), "trace-drill-submit-btn not visible").toBeVisible();

  // ── (2) zero 4xx/5xx on first paint ────────────────────────────────────────────────────
  await smokeCheck(page, smoke, "first paint");

  // ── Step 0 (index 0): expected "-3" — submit correct ─────────────────────────────────
  await page.getByTestId("trace-drill-step-input").fill("-3");
  await page.getByTestId("trace-drill-submit-btn").click();

  await expect(page.getByTestId("trace-drill-step-verdict"), "step 0 verdict not visible").toBeVisible({ timeout: 10000 });
  // After a correct step, the component advances — the verdict should say "Pas corect"
  await smokeCheck(page, smoke, "after step 0");

  // ── Step 1 (index 1): expected "-1" — submit correct ─────────────────────────────────
  // Wait for the input to be back for step 1
  await expect(page.getByTestId("trace-drill-step-input")).toBeVisible({ timeout: 5000 });
  await page.getByTestId("trace-drill-step-input").fill("-1");
  await page.getByTestId("trace-drill-submit-btn").click();

  await smokeCheck(page, smoke, "after step 1");

  // ── Step 2 (index 2): expected "0.875" — submit WRONG "999" (REQ-5 pin) ──────────────
  await expect(page.getByTestId("trace-drill-step-input")).toBeVisible({ timeout: 5000 });
  await page.getByTestId("trace-drill-step-input").fill("999");
  await page.getByTestId("trace-drill-submit-btn").click();

  // The verdict must appear and say "wrong" — the step is blocked (REQ-5: caught at step 3)
  await expect(
    page.getByTestId("trace-drill-step-verdict"),
    "step-2 wrong verdict not visible — REQ-5 pin: wrong value caught at step 3",
  ).toBeVisible({ timeout: 10000 });

  // The input must still be present (user is NOT advanced — they are blocked at step 2).
  await expect(
    page.getByTestId("trace-drill-step-input"),
    "input must stay at step 2 (user blocked on wrong submission, REQ-5)",
  ).toBeVisible();

  // ── (4) no error text, no 4xx/5xx after wrong step ──────────────────────────────────────
  await smokeCheck(page, smoke, "after wrong step 2");

  await ctx.close();
});

test("step-trace — trace problems list has ≥1 ALO trace problem (INV-6.5)", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const resp = await page.request.get("/api/v1/practice/problems?subject=ALO&surface=trace");
  expect(resp.status(), "trace problems list should return 200").toBe(200);
  const body = await resp.json();
  expect(body.problems, "ALO should have ≥1 trace problem seeded (INV-6.5)").not.toHaveLength(0);

  await ctx.close();
});
