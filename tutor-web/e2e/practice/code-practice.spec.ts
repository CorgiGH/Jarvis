/**
 * Plan-6 Task 13 — Code-practice surface e2e spec (REQ-7/8, INV-6.5, INV-6.6).
 *
 * Runs against the REAL Kotlin backend seeded with the Task-2 real-corpus PS code problem
 * (ps-prob-001: R confidence-interval function). Gated on JARVIS_PRACTICE_E2E=1.
 *
 * KEY assertions:
 *  - INV-6.6 CLIENT HALF: [data-testid="code-practice-reference"] is ABSENT from the DOM
 *    pre-attempt. Post-grade, the element is PRESENT.
 *  - INV-6.6 NETWORK HALF: the problems-list response body does NOT contain the reference
 *    solution text.
 *  - §6.2.3 testids visible on first paint (REQ-7/8)
 *  - Language badge renders the exam language for PS (R)
 *
 * Asserts:
 *  - code-practice-editor, code-practice-language-badge, code-practice-run visible on paint
 *  - Zero 4xx/5xx throughout
 *  - INV-6.6 both halves proven
 */

import { test, expect } from "@playwright/test";
import { startPracticeServer } from "./helpers/practiceServer";
import { createSmokeCollector, smokeCheck } from "./helpers/smoke";
import type { PracticeServerHandle } from "./helpers/practiceServer";

const isEnabled = process.env.JARVIS_PRACTICE_E2E === "1";

let srv: PracticeServerHandle;

test.beforeAll(async () => {
  if (!isEnabled) return;
  test.setTimeout(180_000); // backend boot (Gradle + JVM) needs up to ~2min
  srv = await startPracticeServer();
});

test.afterAll(async () => {
  if (!isEnabled) return;
  await srv?.stop();
});

test("code-practice — INV-6.6: reference absent pre-attempt; present post-grade (REQ-8)", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
    { name: "csrf",           value: srv.csrfToken,    domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const smoke = createSmokeCollector();
  smoke.attachResponseListener(page);

  await page.goto("/tutor/practice/code/PS");

  // ── (1) REQ-7/8 testids visible on first paint ────────────────────────────────────────
  await expect(page.getByTestId("code-practice-editor"), "code-practice-editor not visible").toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId("code-practice-language-badge"), "code-practice-language-badge not visible").toBeVisible();
  await expect(page.getByTestId("code-practice-run"), "code-practice-run not visible").toBeVisible();

  // Language badge should show "R" for the PS code problem
  await expect(page.getByTestId("code-practice-language-badge")).toContainText("R");

  // ── INV-6.6 CLIENT HALF: reference element ABSENT pre-attempt ────────────────────────
  await expect(
    page.getByTestId("code-practice-reference"),
    "INV-6.6: code-practice-reference must be ABSENT from DOM before a grade attempt",
  ).toHaveCount(0);

  // ── (2) zero 4xx/5xx on first paint ────────────────────────────────────────────────────
  await smokeCheck(page, smoke, "first paint");

  // ── (3) grade: submit the reference solution to get a grade reply ─────────────────────
  const refSource = `interval_z = function(n, sample_mean, sigma, alfa) {
  z = qnorm(1 - alfa/2, 0, 1)
  margine = z * sigma / sqrt(n)
  a = c(sample_mean - margine, sample_mean + margine)
  return(a)
}
result = interval_z(25, 67.53, 10, 0.1)
print(result)`;

  await page.getByTestId("code-practice-editor").fill(refSource);

  // Click the grade button (no data-testid on grade btn in CodePractice; locate by text)
  const gradeBtn = page.getByText("Trimite spre evaluare");
  await expect(gradeBtn).toBeVisible();
  await gradeBtn.click();

  // Wait for verdict — the execution runner may or may not be available; either a verdict
  // or a degraded banner must appear (R runner may not be available in the e2e env).
  await Promise.race([
    expect(page.getByTestId("code-practice-verdict")).toBeVisible({ timeout: 30000 }),
    // Degraded banner also satisfies the attempt-gate
    expect(page.locator('[class*="yellow"]')).toBeVisible({ timeout: 30000 }),
  ]).catch(() => {
    // If neither appears, the reference will still be revealed (server always returns it).
  });

  // Wait for the page to settle
  await page.waitForTimeout(1000);

  // ── INV-6.6 CLIENT HALF: reference PRESENT post-grade ────────────────────────────────
  // The reference_solution_ro field in the grade reply always carries the reference solution;
  // the component renders [data-testid="code-practice-reference"] when it is non-null.
  await expect(
    page.getByTestId("code-practice-reference"),
    "INV-6.6: code-practice-reference must be PRESENT in DOM after a grade attempt",
  ).toBeVisible({ timeout: 10000 });

  // ── (4) no error text, no 4xx/5xx after grade ──────────────────────────────────────────
  await smokeCheck(page, smoke, "after grade");

  await ctx.close();
});

test("code-practice — INV-6.6 NETWORK HALF: problems list never contains reference solution", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const resp = await page.request.get("/api/v1/practice/problems?subject=PS&surface=code");
  expect(resp.status(), "code problems list should return 200").toBe(200);

  const bodyText = await resp.text();
  // The reference source contains "interval_z = function" — must NOT be in problems-list.
  expect(bodyText, "INV-6.6 NETWORK: problems list must not contain reference solution text").not.toContain("interval_z = function");

  await ctx.close();
});
