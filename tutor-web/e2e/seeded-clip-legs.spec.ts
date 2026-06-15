import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";
import cleanFixture from "./fixtures/pa-kc-001-beats.json" with { type: "json" };

/**
 * master-plan-v2 Phase-0 gate-self-test — the TWO uncovered assertNoClip legs.
 *
 * assertNoClip (e2e/helpers/assertNoClip.ts) has THREE legs (ported from audit.viz.mjs):
 *   leg1 = viewport-width overflow  : element.right > vw+1 OR element.left < -1   (lines 26-38)
 *   leg2 = text clipping            : scroll{W,H} > client{W,H}+2 on a clipping el (lines 39-55)
 *   leg3 = interactive overlap      : two non-nested interactives, intersection > 16 px² (lines 56-74)
 *
 * Audit finding: only leg2 (text clip) had a known-bad seed today — seeded-violations.spec.ts
 * drill 4a (clip.json + addStyleTag). leg1 and leg3 had NO fixture proving they go RED, so a
 * future regression that softens those two legs would pass CI silently.
 *
 * This spec mirrors the drill-4a pattern for the two missing legs. It reuses the SAME stub
 * scaffolding (stubLesson / gotoLesson) and the SAME "inject the violation at runtime, then
 * assert .rejects.toThrow()" approach drills 4a/4b/4c use (addStyleTag / page.evaluate). The
 * VIOLATION is induced at runtime on the real clean lesson surface — equivalent to drill 4b's
 * addStyleTag low-contrast override and drill 4c's removeAttribute("disabled") injection.
 *
 * Each leg gets:
 *   - a RED test: inject the geometry violation → assertNoClip MUST throw (the leg is alive)
 *   - a CLEAN calibration test: same surface, NO injection → assertNoClip MUST pass
 *     (the leg is not trigger-happy; a regression that softens it is caught either way)
 *
 * Stub mode + reducedMotion follow seeded-violations.spec.ts verbatim.
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

// ── Leg 1: viewport-width overflow — element pushed past the viewport right edge ───────────────────
//
// assertNoClip leg1 fires when a VISIBLE element's getBoundingClientRect().right > vw+1
// (or .left < -1). On the clean lesson surface every element sits inside the viewport. We inject
// a runtime style that shoves the predict-options block far past the right edge via a large
// positive translateX — the element stays visible (opacity/display untouched) but its right edge
// is now well beyond document.documentElement.clientWidth. This models a real layout regression
// where a column/figure busts the viewport (the cardinal-sin clip the gate exists to catch).

test("leg1: predict block translated past the viewport right edge → assertNoClip throws (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  // PREDICT beat is active; the options block is a visible element inside lesson-beat-active.
  await expect(page.getByTestId("beat-predict-options")).toBeVisible({ timeout: 5000 });
  // Shove the options block 5000px to the right — its right edge now >> viewport width.
  // translateX keeps the element laid out + visible (no display/opacity change), so assertNoClip's
  // visible() filter still counts it, and r.right > vw+1 → leg1 fires.
  await page.addStyleTag({
    content: `[data-testid="beat-predict-options"] { transform: translateX(5000px) !important; }`,
  });
  // assertNoClip MUST throw, and specifically on leg1 (the "viewport overflow" assertion
  // message) — so a regression that softens leg1 (not merely a different leg) is caught.
  await expect(assertNoClip(page, "lesson-beat-active")).rejects.toThrow(/viewport overflow/);
});

test("leg1: clean fixture, no transform → assertNoClip passes (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  await expect(page.getByTestId("beat-predict-options")).toBeVisible({ timeout: 5000 });
  // No injection — the clean PREDICT surface must pass all three legs (incl. leg1).
  await assertNoClip(page, "lesson-beat-active");
});

// ── Leg 3: interactive-element overlap — two overlapping interactive elements ──────────────────────
//
// assertNoClip leg3 fires when two NON-nested interactives (button/a/input/select/[role=button|slider])
// have an intersection area > 16 px². The PREDICT beat has multiple sibling option buttons inside
// beat-predict-options (none nested in one another). On the clean surface they stack with gap and do
// NOT overlap. We inject a runtime style that absolutely-positions every option button at the same
// top-left corner with a fixed size — they now occupy the same rect, intersection ~ full button area
// (>> 16 px²), so leg3 fires. This models a real layout regression where two tap targets collide.

test("leg3: predict option buttons stacked at the same position → assertNoClip throws (gate alive)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  await expect(page.getByTestId("beat-predict-options")).toBeVisible({ timeout: 5000 });
  // Confirm there ARE >= 2 sibling option buttons to overlap (else the seed proves nothing).
  const optionCount = await page
    .getByTestId("beat-predict-options")
    .locator("button")
    .count();
  expect(
    optionCount,
    "leg3 seed needs >= 2 predict option buttons to force an overlap",
  ).toBeGreaterThanOrEqual(2);
  // Pin every option button to the SAME absolute rect → their bounding boxes coincide.
  // Container is given position:relative so the absolute children anchor to it; each button is
  // forced to a 200x44 box at (0,0). Two coincident 200x44 buttons → intersection 8800 px² >> 16.
  await page.addStyleTag({
    content: `
      [data-testid="beat-predict-options"] { position: relative !important; min-height: 60px !important; }
      [data-testid="beat-predict-options"] > button {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 200px !important;
        height: 44px !important;
        margin: 0 !important;
      }
    `,
  });
  // assertNoClip MUST throw, and specifically on leg3 (the "interactive overlap" assertion
  // message) — so a regression that softens leg3 (not merely a different leg) is caught.
  await expect(assertNoClip(page, "lesson-beat-active")).rejects.toThrow(/interactive overlap/);
});

test("leg3: clean fixture, buttons stack with gap → assertNoClip passes (gate calibrated)", async ({ page }) => {
  await stubLesson(page, "pa-kc-001", cleanFixture);
  await gotoLesson(page, "pa-kc-001");
  await expect(page.getByTestId("beat-predict-options")).toBeVisible({ timeout: 5000 });
  // No injection — the clean PREDICT surface's interactives do not overlap → leg3 (and all legs) pass.
  await assertNoClip(page, "lesson-beat-active");
});
