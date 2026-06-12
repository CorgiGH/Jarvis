import { test, expect } from "@playwright/test";
import { assertNoClip } from "./helpers/assertNoClip";
import fixture from "./fixtures/pa-kc-001-beats.json" with { type: "json" };

// VERBATIM-PIN (specTrust#3): the fixture's beats MUST be byte-identical to the Task-7 authored
// content/PA/kcs/pa-kc-001.yaml beats.ro. If Task 7 edits the authored beats, re-sync this fixture.
// These three anchors catch the common drift (prompt/first-option/check-stem) so the fixture cannot
// silently revert to invented content while claiming source-provenance.
test("fixture is the Task-7 authored pa-kc-001 content (verbatim pin)", () => {
  expect(fixture.beats.predict.prompt).toBe(
    "Care dintre următoarele descrieri este un algoritm, conform definiției din curs?",
  );
  expect(fixture.beats.predict.options[0].text).toBe(
    "„Citește a și b, calculează s = a + b, afișează s și oprește-te.”",
  );
  expect(fixture.beats.check.item_stem).toBe(
    "O rețetă de bucătărie cu pași clari care se termină după ultimul pas îndeplinește definiția algoritmului din curs?",
  );
});

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// A graded reply for any beat POST. predict/attempt: correct=true (so the gate clears);
// check: lesson_complete=true (so the handoff renders).
function gradeReply(beatType: string) {
  return {
    correct: true,
    score: beatType === "check" ? 1.0 : 1.0,
    feedback_ro: "corect",
    beat_type: beatType,
    lesson_complete: beatType === "check",
    first_encounter: true,
    phase: beatType === "check" ? "practice" : null,
    verification_status: beatType === "check" ? "faithful" : null,
  };
}

test("lesson route: §4.7 beats render, gate, scrub-back, complete — zero 4xx/5xx + zero console errors", async ({ page }) => {
  const bad: string[] = [];
  const consoleErrors: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text().slice(0, 200)); });
  page.on("pageerror", (e) => consoleErrors.push(String(e.message).slice(0, 200)));

  // Force reduced motion at RUNTIME (not via playwright.config's `use.reducedMotion`, which silently
  // does NOT flip window.matchMedia in this Playwright version — verified probe: matchMedia stayed
  // false under the config flag, true under emulateMedia). RevealBeat/NameBeat read
  // `prefersReducedMotionNow()` = matchMedia("(prefers-reduced-motion: reduce)") to zero their per-step
  // dwell FLOOR (readMs caps at 5500ms — every authored reveal step here is 24-29 words = the 5500ms
  // cap). Without reduced motion the reveal gate only clears at ~5.5s, past the default 5s
  // expect timeout — the traversal would red on dwell, NOT on the gate mechanics it means to test.
  await page.emulateMedia({ reducedMotion: "reduce" });

  await page.route("**/api/v1/lesson/pa-kc-001/beat", (r) => {
    const body = r.request().postDataJSON() as { beat_type: string };
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(gradeReply(body.beat_type)) });
  });
  await page.route("**/api/v1/lesson/pa-kc-001", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(fixture) }),
  );

  await page.goto("/tutor/lesson/pa-kc-001");

  // (1) pips: N == plan length; exactly one active beat
  await expect(page.getByTestId("lesson-beat-pips")).toBeVisible({ timeout: 10000 });
  const pips = page.getByTestId("lesson-beat-pips").locator("[data-pip]");
  await expect(pips).toHaveCount(fixture.beats.plan.length);
  await expect(page.getByTestId("lesson-beat-active")).toHaveCount(1);

  // first paint no-clip at both viewports
  for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "lesson-beat-active"); }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  // (2) ① PREDICT: 3-4 options; next-gate disabled before commit, enabled after
  await expect(page.getByTestId("beat-predict-options").locator("button")).toHaveCount(3);
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  await page.getByTestId("beat-predict-options").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // (3) ② ATTEMPT: next disabled until submitted, then enabled
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // (4) ③ REVEAL: stepped text with a scrubber "pas k/N" + a functioning BACK (one-shot violation gate)
  await expect(page.getByTestId("scrubber-step-counter")).toBeVisible();
  await expect(page.getByTestId("scrubber-step-counter")).toContainText(/pas\s+\d+\/\d+/);
  // step to the final reveal step (forward), then verify BACK actually moves the counter.
  // Locate by the [data-step-fwd]/[data-step-back] DATA ATTRIBUTES, NOT by accessible name:
  // the stepped-text RevealBeat's forward button renders "{lessonStrings.next} ›" = "Continuă ›"
  // (and back = "‹ Înapoi") with NO aria-label — "Continuă" matches none of /înainte|forward|▶/,
  // so a name-regex locator hangs (consistency#2). The data attributes are on both the stepped-text
  // buttons (Task 6 RevealBeat) AND match how BeatOrchestrator.test.tsx / the Task-12/13 mjs drivers
  // locate the scrubber, so this is the robust, drift-proof anchor.
  const fwd = page.getByTestId("beat-figure-scrubber").locator("[data-step-fwd]").first();
  const back = page.getByTestId("beat-figure-scrubber").locator("[data-step-back]").first();
  await fwd.click();
  const counterAfterFwd = await page.getByTestId("scrubber-step-counter").textContent();
  await back.click();
  const counterAfterBack = await page.getByTestId("scrubber-step-counter").textContent();
  expect(counterAfterBack, "BACK must move the step counter (one-shot animation is a gate violation)").not.toBe(counterAfterFwd);
  // The reveal gate clears ONLY once the learner reaches the FINAL step (RevealBeat: reachedEnd &&
  // dwell). The fixture's reveal has N>2 steps, so a single fwd after the back lands mid-way (e.g.
  // "pas 2/3"), NOT the last step — step forward until the counter shows "pas N/N", driven by the
  // fixture's reveal length so the walk stays honest if the authored reveal grows/shrinks.
  const stepCount = fixture.beats.reveal.steps.length;
  const finalCounter = new RegExp(`pas\\s+${stepCount}/${stepCount}`);
  // before reaching the final step the gate must still be CLOSED (gate honesty).
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();
  for (let i = 0; i < stepCount - 1; i++) {
    if (finalCounter.test((await page.getByTestId("scrubber-step-counter").textContent()) ?? "")) break;
    await fwd.click();
  }
  await expect(page.getByTestId("scrubber-step-counter")).toContainText(finalCounter);
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled();
  await page.getByTestId("beat-next-gate").click();

  // ④ NAME: text-only beat, dwell-floored; next enables after the floor
  await expect(page.getByTestId("lesson-beat-active")).toBeVisible();
  await expect(page.getByTestId("beat-next-gate")).toBeEnabled({ timeout: 7000 });
  await page.getByTestId("beat-next-gate").click();

  // (5) ⑤ CHECK: answer, then lesson-complete-handoff appears
  await page.getByTestId("lesson-beat-active").locator("button").first().click();
  await expect(page.getByTestId("lesson-complete-handoff")).toBeVisible({ timeout: 10000 });

  // no-clip on the completion screen, both viewports. Scope to the completion WRAPPER
  // (`lesson-complete`, Task 6) so the heading + body + handoff are all covered. The old
  // `assertNoClip(page,"lesson-beat-active").catch(()=>{})` swallowed a guaranteed "scope not
  // found" (there is NO lesson-beat-active on the completion screen — it only exists mid-lesson),
  // making it a dead assertion; scoping the real check to the handoff BUTTON alone was near-vacuous
  // (a button has no descendants). `lesson-complete` covers the actual completion layout (specTrust#6).
  for (const vp of VIEWPORTS) { await page.setViewportSize(vp); await assertNoClip(page, "lesson-complete"); }

  // smoke totals
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx during traversal:\n${bad.join("\n")}`).toEqual([]);
  expect(consoleErrors, `console errors:\n${consoleErrors.join("\n")}`).toEqual([]);
});
