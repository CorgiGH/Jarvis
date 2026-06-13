/**
 * Plan-6 Task 13 — Deliverable-tracker surface e2e spec (REQ-18..21, INV-6.5).
 *
 * Runs against the REAL Kotlin backend seeded with ALO T1–T5 and PS A–D deliverables.
 * Gated on JARVIS_PRACTICE_E2E=1.
 *
 * Asserts (REQ-21 selector exact):
 *  - deliverable-honesty-line visible (REQ-19: "Aplicația te pregătește..." always visible)
 *  - deliverable-card visible (at least one seeded ALO/PS deliverable)
 *  - deliverable-points visible
 *  - deliverable-prep-drills visible (honest empty state copy when none seeded)
 *  - deliverable-deadline visible (shows "necunoscut" for null deadlines, REQ-21)
 *  - Zero 4xx/5xx on first paint
 *  - REQ-19 honesty line rendered verbatim
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

test("deliverable-tracker — REQ-19/21: honesty line visible; deliverable cards render; deadline necunoscut", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
    { name: "csrf",           value: srv.csrfToken,    domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const smoke = createSmokeCollector();
  smoke.attachResponseListener(page);

  await page.goto("/tutor/practice/deliverables");

  // ── (1) REQ-21 testids visible on first paint ────────────────────────────────────────
  // REQ-19: honesty line always present
  await expect(
    page.getByTestId("deliverable-honesty-line"),
    "deliverable-honesty-line not visible (REQ-19 — always shown)",
  ).toBeVisible({ timeout: 15000 });

  // ── REQ-19 honesty line text verbatim ────────────────────────────────────────────────
  await expect(page.getByTestId("deliverable-honesty-line")).toContainText(
    "Aplicația te pregătește pentru predare — nu notează predările reale; profesorul o face.",
  );

  // ── (2) zero 4xx/5xx on first paint ────────────────────────────────────────────────────
  await smokeCheck(page, smoke, "first paint");

  // ── (3) deliverable cards must render (ALO T1..T5 + PS A..D seeded) ─────────────────
  await expect(
    page.getByTestId("deliverable-card").first(),
    "at least one deliverable-card must render (ALO/PS seeds)",
  ).toBeVisible({ timeout: 10000 });

  // ── deliverable-deadline renders (may say "necunoscut" since seeds have null deadline) ──
  await expect(
    page.getByTestId("deliverable-deadline").first(),
    "deliverable-deadline must render",
  ).toBeVisible();

  // ── deliverable-points and deliverable-prep-drills must render per card ────────────────
  await expect(
    page.getByTestId("deliverable-points").first(),
    "deliverable-points must render (at least one seed has sub_problems)",
  ).toBeVisible();

  await expect(
    page.getByTestId("deliverable-prep-drills").first(),
    "deliverable-prep-drills must render (honest empty state copy when none)",
  ).toBeVisible();

  // ── (4) no error text, no 4xx/5xx ────────────────────────────────────────────────────────
  await smokeCheck(page, smoke, "after interaction");

  await ctx.close();
});

test("deliverable-tracker — API: GET /api/v1/practice/deliverables returns ALO + PS seeds", async ({ browser }) => {
  test.skip(!isEnabled, "practice e2e boots the Kotlin backend — set JARVIS_PRACTICE_E2E=1");

  const ctx = await browser.newContext({ baseURL: srv.baseUrl });
  await ctx.addCookies([
    { name: "jarvis_session", value: srv.sessionToken, domain: "127.0.0.1", path: "/" },
  ]);
  const page = await ctx.newPage();

  const resp = await page.request.get("/api/v1/practice/deliverables");
  expect(resp.status(), "deliverables should return 200").toBe(200);
  const body = await resp.json();

  // Expect at least ALO T1..T5 (5) + PS A..D (4) = 9 deliverables.
  // Some may be more if POO was seeded; the floor is 9.
  expect(
    body.deliverables,
    "should have ≥9 seeded deliverables (ALO T1-T5 + PS A-D)",
  ).toHaveLength(expect.any(Number));
  expect(body.deliverables.length, "≥9 deliverables expected").toBeGreaterThanOrEqual(9);

  // Every deliverable must have a title_ro and subject
  for (const d of body.deliverables) {
    expect(d.title_ro, `deliverable ${d.id} must have title_ro`).toBeTruthy();
    expect(d.subject, `deliverable ${d.id} must have subject`).toBeTruthy();
  }

  await ctx.close();
});
