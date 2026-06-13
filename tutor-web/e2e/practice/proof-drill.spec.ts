/**
 * Plan-6 Task 13 — Proof-drill surface e2e spec (REQ-2/3/4, INV-6.5).
 *
 * Runs against the REAL Kotlin backend seeded with the Task-2 real-corpus PA proof problem
 * (pa-prob-001: HAM-PATH NP-completeness proof). Gated on JARVIS_PRACTICE_E2E=1.
 *
 * Asserts:
 *  - §6.2.1 testids visible on first paint (REQ-4)
 *  - Interaction smoke: submit a correct substep; per-substep verdict renders (REQ-2)
 *  - Zero 4xx/5xx throughout
 *  - No error text at any point
 */

import { test, expect } from "@playwright/test";
import { startPracticeServer } from "./helpers/practiceServer";
import { createSmokeCollector, smokeCheck } from "./helpers/smoke";
import type { PracticeServerHandle } from "./helpers/practiceServer";

// ENV-GATE — the existing node-only CI frontend job runs playwright over the same testDir; this
// gate makes the spec self-skip (named, visible in the report) without booting any JVM there.
// JARVIS_PRACTICE_E2E=1 is set ONLY in the CI practice-e2e job and local Task-13/14 runs.
const isEnabled = process.env.JARVIS_PRACTICE_E2E === "1";

// Shared server across tests in this file (one boot per worker process).
let srv: PracticeServerHandle;

test.beforeAll(async () => {
  if (!isEnabled) return;
  srv = await startPracticeServer();
});

test.afterAll(async () => {
  if (!isEnabled) return;
  await srv?.stop();
});

test("proof-drill — INV-6.5: PA proof-surface testids paint and interact against real corpus", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  // Inject session + csrf cookies (the CSRF check compares cookie vs X-CSRF-Token header).
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
    { name: "csrf",           value: srv.csrfToken,    domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const smoke = createSmokeCollector();
  smoke.attachResponseListener(page);

  // Navigate to the proof-drill surface for subject PA (mounted at /tutor/practice/proof/PA).
  await page.goto("/tutor/practice/proof/PA");

  // ── (1) REQ-4 testids visible on first paint ─────────────────────────────────────────
  await expect(page.getByTestId("proof-drill-frame"), "proof-drill-frame not visible").toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId("proof-drill-substeps"), "proof-drill-substeps not visible").toBeVisible();
  await expect(page.getByTestId("proof-drill-submit-btn"), "proof-drill-submit-btn not visible").toBeVisible();

  // ── (2) zero 4xx/5xx on first paint ────────────────────────────────────────────────────
  await smokeCheck(page, smoke, "first paint");

  // ── (3) interact: type a correct answer for the first substep (step-np-guess) ──────────
  // The PA proof problem has substep id=step-np-guess whose matcher is contains="permutare".
  const substepInputs = page.locator('textarea[aria-label]');
  const firstInput = substepInputs.first();
  await expect(firstInput).toBeVisible();
  await firstInput.fill("ghicim o permutare a nodurilor");

  // Fill the other substeps minimally so the form is not empty-looking
  const allInputs = await substepInputs.all();
  for (let i = 1; i < allInputs.length; i++) {
    await allInputs[i].fill("O(n)"); // passes step-np-verify regex O\(n
  }

  // Submit
  await page.getByTestId("proof-drill-submit-btn").click();

  // ── (4) per-substep verdict renders (REQ-2 ─ "per-sub-step ItemVerdict") ───────────────
  await expect(
    page.getByTestId("proof-drill-substep-score").first(),
    "proof-drill-substep-score not visible after submit",
  ).toBeVisible({ timeout: 15000 });

  // ── (4) no error text, no 4xx/5xx after submit ──────────────────────────────────────────
  await smokeCheck(page, smoke, "after submit");

  await ctx.close();
});

test("proof-drill — smoke: no pending state for PA proof (coverage confirmed)", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  // Confirm the problems list API returns at least one PA proof problem (INV-6.5 pending check).
  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const resp = await page.request.get("/api/v1/practice/problems?subject=PA&surface=proof");
  expect(resp.status(), "practice problems list should return 200").toBe(200);
  const body = await resp.json();
  expect(body.problems, "PA should have ≥1 proof problem seeded (INV-6.5)").not.toHaveLength(0);

  // REF solution must NOT appear in the problems-list response (INV-6.6 server half).
  const bodyText = await resp.text();
  // The reference solution contains "HAM-PATH ∈ NP" — check it is not in the problems-list response.
  expect(bodyText, "problems list must not contain reference solution text (INV-6.6)").not.toContain("HAM-PATH ∈ NP");

  await ctx.close();
});
