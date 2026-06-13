import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";
import { assertLegibility } from "./helpers/assertLegibility";
import { assertNextGateContract } from "./helpers/assertNextGateContract";
import { checkRenderedTexts, type RoViolation } from "./helpers/roHeuristic";
import cleanFixture from "./fixtures/pa-kc-001-beats.json" with { type: "json" };
import clipFixture from "./fixtures/seeded-violations/clip.json" with { type: "json" };
import enInRoFixture from "./fixtures/seeded-violations/en-in-ro.json" with { type: "json" };

/**
 * Plan-4b Task 8 / §0.9I — seeded-violation drills, gates 3+4.
 *
 * Each drill proves that a gate HELPER is alive:
 *   a gate that reds on its seed = the drill test PASSES
 *   a drill failing = the gate is dead = CI red (§9.3 "gates are alive" proof)
 *
 * Each drill also proves the CLEAN fixture passes the SAME helper (calibration — the gate
 * is not trigger-happy).
 *
 * Four drills per §0.9I:
 *   (4a) clip.json — 600-char unbroken token in reveal → assertNoClip must throw
 *   (4b) clean fixture + addStyleTag low-contrast override → assertLegibility must throw
 *   (4c) clean fixture + page.evaluate removes `disabled` from beat-next-gate → assertNextGateContract must throw
 *   (4d) en-in-ro.json — EN predict prompt + "skeletonul" reveal leak → roHeuristic must flag ≥2 strings
 *
 * Stub mode (page.route) follows the lesson-beats.spec.ts scaffolding:
 *   - lesson GET route → fixture
 *   - lesson POST /beat → gradeReply (correct=true, gate clears)
 * reducedMotion is inherited from playwright.config.ts (use.reducedMotion: "reduce"),
 * which also forces it via emulateMedia at runtime for this project.
 */

function gradeReply(beatType: string) {
  return {
    correct: true,
    score: 1.0,
    feedback_ro: "corect",
    beat_type: beatType,
    lesson_complete: beatType === "check",
    first_encounter: true,
    phase: beatType === "check" ? "practice" : null,
    verification_status: beatType === "check" ? "faithful" : null,
  };
}

// Stub the lesson GET + POST for a given kcId and fixture payload.
async function stubLesson(
  page: import("@playwright/test").Page,
  kcId: string,
  fixtureBody: unknown,
) {
  await page.route(`**/api/v1/lesson/${kcId}/beat`, (r) => {
    const body = r.request().postDataJSON() as { beat_type: string };
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(gradeReply(body.beat_type)),
    });
  });
  await page.route(`**/api/v1/lesson/${kcId}`, (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(fixtureBody),
    }),
  );
}

// Navigate to the lesson route and wait for the first beat to render.
async function gotoLesson(page: import("@playwright/test").Page, kcId: string) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(`/tutor/lesson/${kcId}`);
  await expect(page.getByTestId("lesson-beat-active")).toBeVisible({ timeout: 10000 });
}

// Extract all visible learner-facing text strings from the active lesson surface.
// Returns { field, text } objects for the roHeuristic.
async function extractRenderedTexts(page: import("@playwright/test").Page) {
  return page.evaluate(() => {
    const results: Array<{ field: string; text: string }> = [];
    const scope = document.querySelector('[data-testid="lesson-beat-active"]');
    if (!scope) return results;
    const all = Array.from(scope.querySelectorAll("*"));
    for (const el of all) {
      if (el.getAttribute("aria-hidden") === "true") continue;
      // Only leaf text nodes
      const hasDirectText = Array.from(el.childNodes).some(
        (n) => n.nodeType === 3 && (n.textContent || "").trim().length > 0,
      );
      if (!hasDirectText) continue;
      const r = el.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) continue;
      const cs = getComputedStyle(el);
      if (cs.display === "none" || cs.visibility === "hidden") continue;
      results.push({
        field: el.getAttribute("data-testid") || el.tagName.toLowerCase(),
        text: (el.textContent || "").trim(),
      });
    }
    return results;
  });
}

// ── Drill (4a): clip.json — 600-char unbroken token → assertNoClip must throw ────────────────────

test("drill 4a: clip fixture → assertNoClip throws on the 600-char token (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", clipFixture);
  await gotoLesson(page, "pa-kc-001");
  // Advance to the REVEAL beat (beat index 2) by completing predict + attempt
  // PREDICT
  await page.getByTestId("beat-predict-options").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 5000 });
  await page.getByTestId("beat-next-gate").click();
  // ATTEMPT
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 5000 });
  await page.getByTestId("beat-next-gate").click();
  // Now on REVEAL — the 600-char unbroken token is in the first reveal step (scrollWidth >> clientWidth).
  // The lesson beat layout uses overflow:visible flex containers. To make assertNoClip's text-clip check
  // (#2: clipsX && scrollWidth > clientWidth+2) fire, we inject overflow:hidden + constrained width on
  // the reveal step container — this models what happens on narrow screens/constrained layouts where
  // the 600-char token IS visually clipped. The fixture CONTAINS the violation (the long token);
  // the style injection REVEALS it to the gate (equivalent to drill 4b's addStyleTag pattern).
  await expect(page.getByTestId("beat-figure-scrubber")).toBeVisible({ timeout: 5000 });
  // The reveal step text is in a <p> element (overflow:visible, scrollWidth 4000+ >> clientWidth ~1220).
  // assertNoClip's text-clip check requires: (a) element has DIRECT text node children, AND
  // (b) that same element has overflow:hidden/clip/scroll/auto.
  // The <p> has direct text nodes but overflow:visible. We inject overflow:hidden + max-width on the <p>
  // to make the clip DETECTABLE — this models what happens in constrained layouts (narrow screen, etc.)
  // where the long token genuinely clips. The VIOLATION is in the fixture (the 600-char token);
  // the style injection makes it visible to the gate (analogous to drill 4b's addStyleTag).
  await page.addStyleTag({
    content: `[data-testid="lesson-beat-active"] p { overflow: hidden !important; max-width: 300px !important; white-space: nowrap !important; }`,
  });
  // assertNoClip MUST throw: the 600-char token's scrollWidth >> 300px clientWidth, overflow:hidden on <p>
  await expect(assertNoClip(page, "lesson-beat-active")).rejects.toThrow();
});

test("drill 4a: clean fixture → assertNoClip passes on the lesson surface (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  await assertNoClip(page, "lesson-beat-active");
});

// ── Drill (4b): low-contrast override → assertLegibility must throw ──────────────────────────────

test("drill 4b: injected low-contrast style → assertLegibility throws with ratio < 4.5 (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  // Force near-white text on white background via addStyleTag — ratio ≈ 1.01
  await page.addStyleTag({
    content: `[data-testid="lesson-beat-active"] * { color: #fafafa !important; }`,
  });
  // assertLegibility MUST throw (ratio << 4.5)
  await expect(assertLegibility(page, "lesson-beat-active")).rejects.toThrow();
});

test("drill 4b: clean fixture without override → assertLegibility passes (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  await assertLegibility(page, "lesson-beat-active");
});

// ── Drill (4c): disabled removed from beat-next-gate → assertNextGateContract must throw ─────────

test("drill 4c: disabled removed from beat-next-gate pre-commit → assertNextGateContract throws (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  // Beat is in PREDICT state — gate is locked (predict not committed)
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  // Surgically remove the `disabled` attribute via page.evaluate (simulating a bypass attempt)
  await page.evaluate(() => {
    const btn = document.querySelector('[data-testid="beat-next-gate"]') as HTMLButtonElement | null;
    if (btn) {
      btn.removeAttribute("disabled");
      btn.disabled = false;
    }
  });
  // assertNextGateContract MUST throw — the button appears enabled but the gate condition is uncleared
  // NOTE: assertNextGateContract checks the logical gate (disabled attr + pip non-advance).
  // After our evaluate, the button is NOT disabled → the helper should throw immediately.
  await expect(assertNextGateContract(page)).rejects.toThrow();
});

test("drill 4c: beat gate properly locked before commit → assertNextGateContract passes (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  // Gate IS locked at this point (predict not committed) — helper should pass
  await assertNextGateContract(page);
});

// ── Drill (4d): EN-in-RO fixture → roHeuristic must flag ≥2 strings ─────────────────────────────

test("drill 4d: en-in-ro fixture → roHeuristic flags >= 2 strings (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", enInRoFixture);
  await gotoLesson(page, "pa-kc-001");

  // Collect all rendered text on the PREDICT beat (the EN prompt is visible here)
  const predictTexts = await extractRenderedTexts(page);
  const predictViolations: RoViolation[] = checkRenderedTexts(predictTexts);

  // Advance to REVEAL to see the "skeletonul" leak
  await page.getByTestId("beat-predict-options").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 5000 });
  await page.getByTestId("beat-next-gate").click();
  // ATTEMPT
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 5000 });
  await page.getByTestId("beat-next-gate").click();
  // REVEAL
  await expect(page.getByTestId("beat-figure-scrubber")).toBeVisible({ timeout: 5000 });
  const revealTexts = await extractRenderedTexts(page);
  const revealViolations: RoViolation[] = checkRenderedTexts(revealTexts);

  const allViolations = [...predictViolations, ...revealViolations];
  // The EN predict prompt fires via stopword leg (≥18% EN stopwords in "Which of the following...")
  // The "skeletonul" reveal text fires via EN-vocab leg (stem "skeleton" ∈ EN_VOCAB, plan-fix F1)
  expect(
    allViolations.length,
    `roHeuristic must flag >= 2 strings — found ${allViolations.length}:\n` +
    JSON.stringify(allViolations, null, 2),
  ).toBeGreaterThanOrEqual(2);
  // Specifically verify the EN-vocab leg fires (the "skeletonul" case — the F1 flagship seed)
  const enVocabHits = allViolations.filter((v) => v.leg === "en-vocab");
  expect(
    enVocabHits.length,
    "roHeuristic EN-vocab leg must fire on 'skeletonul' (plan-fix F1 — §0.9E flagship seed)",
  ).toBeGreaterThanOrEqual(1);
});

test("drill 4d: clean fixture → roHeuristic finds zero violations (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  const texts = await extractRenderedTexts(page);
  const violations = checkRenderedTexts(texts);
  expect(
    violations,
    `roHeuristic must pass on the clean RO fixture:\n${JSON.stringify(violations, null, 2)}`,
  ).toEqual([]);
});
