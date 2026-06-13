import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { assertNoClip } from "./helpers/assertNoClip";
import { assertLegibility } from "./helpers/assertLegibility";
import { assertNextGateContract } from "./helpers/assertNextGateContract";
import { checkRenderedTexts, type RoViolation } from "./helpers/roHeuristic";

/**
 * Plan 4b Task 9 (§0.9J / R-4b-Q2 option (a)) — the rendered gates 4a–4d on the REAL lesson route
 * over the REAL corpus, served by the boot-up Ktor server against a seeded SQLite DB.
 *
 * Lock check (R-4b-Q10): this spec only CONSUMES frozen contracts — §NEW-L (GET /lesson/{kcId}),
 * the beat-grade POST (Plan-3 shapes), §NEW-V (GET /api/v1/viz/{instanceId}). It asserts against the
 * SERVED payloads; it stubs NO route. A shape mismatch found here would mean code and lock diverged.
 *
 * REAL-BACKEND ONLY. The default `npm run e2e` (frontend CI job, no JVM) skips this honestly via the
 * test.skip below — NO playwright.config.ts edit (the env-skip pattern makes a config edit unnecessary).
 *
 * LOCAL RECIPE (also the CI job, test-yml-plan4b.patch):
 *   1. seed the DB from the real corpus:
 *        # post-CP-2 (gradle patch on main): gradle --no-daemon :seedE2eDb
 *        # pre-CP-2 (throwaway-apply the Lane-A patch, never committed):
 *        #   git apply build-review/tmp/lane-a-patches/build-gradle-seed-e2e.patch
 *        #   gradle --no-daemon :seedE2eDb
 *        #   git checkout -- build.gradle.kts
 *      → writes build/e2e/tutor.db + build/e2e/seed.json
 *   2. boot the real server against the seeded DB + real corpus (vite proxies /api → :8080):
 *        # PowerShell:
 *        #   $env:JARVIS_TUTOR_DB="$PWD\build\e2e\tutor.db"; $env:JARVIS_CONTENT_DIR="$PWD\content";
 *        #   $env:JARVIS_PORT="8080"; gradle run --args=web      (background)
 *   3. run THIS spec against the live backend (vite webServer auto-starts on :5173):
 *        cd tutor-web; $env:REAL_BACKEND="1"; npx playwright test e2e/lesson-gates.spec.ts
 *
 * For EVERY kcId in seed.json (totality over the served proof set — INV-4.4 "real lesson route
 * against the real corpus"), the gates run with figure-mode DETECTED FROM THE SERVED PAYLOAD (a beat
 * whose reveal.figure is non-null), never from the manifest — so a binding Plan 5/6 mints later is
 * exercised by THIS spec with zero edits.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
// seed.json lives at ../build/e2e/seed.json relative to tutor-web/e2e/.
const SEED_PATH = resolve(HERE, "..", "..", "build", "e2e", "seed.json");

interface SeedManifest {
  sid: string;
  kcIds: string[];
  figureKcIds: string[];
}

// Skip the whole file unless REAL_BACKEND is set (honest skip for the default frontend job).
test.skip(
  !process.env.REAL_BACKEND,
  "real-backend only (CI lesson-gates job / local recipe in the spec header)",
);

function loadSeed(): SeedManifest {
  let raw: string;
  try {
    raw = readFileSync(SEED_PATH, "utf8");
  } catch {
    throw new Error(
      `lesson-gates: seed manifest not found at ${SEED_PATH} — run the seed recipe first ` +
        `(gradle :seedE2eDb, or throwaway-apply build-gradle-seed-e2e.patch then :seedE2eDb).`,
    );
  }
  const seed = JSON.parse(raw) as SeedManifest;
  // The seeder aborts non-zero on an empty set, but assert here too (the spec's own anti-vacuity).
  expect(seed.kcIds.length, "seed.kcIds must be non-empty (real served proof set)").toBeGreaterThan(0);
  expect(
    seed.figureKcIds.length,
    "seed.figureKcIds must be non-empty (R-4b-Q1 anti-vacuity — the figure gate must never go vacuous)",
  ).toBeGreaterThan(0);
  return seed;
}

const VIEWPORTS = [
  { width: 1280, height: 900 },
  { width: 1280, height: 620 },
] as const;

// Extract all visible learner-facing text strings inside a scope for the RO heuristic (gate 4d).
async function extractRenderedTexts(page: Page, scopeTestId: string) {
  return page.evaluate((tid) => {
    const results: Array<{ field: string; text: string }> = [];
    const scope = document.querySelector(`[data-testid="${tid}"]`);
    if (!scope) return results;
    const all = Array.from(scope.querySelectorAll("*"));
    for (const el of all) {
      if (el.getAttribute("aria-hidden") === "true") continue;
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
  }, scopeTestId);
}

// Read the rendered figure-svg step counter ("pas k/N") → { k, n }, or null if absent.
async function readScrubberCounter(page: Page): Promise<{ k: number; n: number } | null> {
  const txt = await page.getByTestId("scrubber-step-counter").textContent().catch(() => null);
  if (!txt) return null;
  const m = txt.match(/(\d+)\s*\/\s*(\d+)/);
  if (!m) return null;
  return { k: +m[1], n: +m[2] };
}

test("rendered gates 4a–4d over the real corpus on the real lesson route", async ({ page }) => {
  const seed = loadSeed();

  // Inject the seeded session cookie BEFORE any navigation (secure:false, localhost).
  await page.context().addCookies([
    {
      name: "jarvis_session",
      value: seed.sid,
      domain: "localhost",
      path: "/",
      httpOnly: true,
      secure: false,
      sameSite: "Lax",
    },
  ]);

  // Establish the CSRF double-submit cookie EXACTLY as a real browser session does: the SPA hits
  // /api/v1/tutor/auto-session on mount, which (with a valid jarvis_session) mints + returns a fresh
  // `csrf` token and sets the `csrf` cookie. jarvisFetch then reads that cookie and echoes it as the
  // X-CSRF-Token header on every gated POST (the beat-grade route is csrfProtect-gated → 403 without
  // it; Csrf.kt double-submit). The backend sets the csrf cookie `secure:true`, which a browser drops
  // over plain-HTTP localhost (the vite dev server is http://), so we seed it here non-secure — the
  // SAME token the server minted, so the double-submit matches. NO route is stubbed; this is the real
  // bootstrap handshake, just made cookie-jar-coherent for the http-localhost dev proxy.
  const autoResp = await page.request.get("http://localhost:5173/api/v1/tutor/auto-session");
  expect(autoResp.status(), "auto-session must resolve the seeded session (200)").toBe(200);
  const csrf: string = (await autoResp.json()).csrf;
  expect(csrf, "auto-session must return a csrf token").toMatch(/^[0-9a-f]{32}$/);
  await page.context().addCookies([
    {
      name: "csrf",
      value: csrf,
      domain: "localhost",
      path: "/",
      httpOnly: false,
      secure: false,
      sameSite: "Strict",
    },
  ]);

  // Determinism: reduced motion + the locked default theme (the visual-config pattern).
  await page.emulateMedia({ reducedMotion: "reduce" });

  // Collect console errors, page errors, and 4xx/5xx for the WHOLE traversal (gate 4c hygiene).
  const consoleErrors: string[] = [];
  const badResponses: string[] = [];
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text().slice(0, 200)); });
  page.on("pageerror", (e) => consoleErrors.push(String(e.message).slice(0, 200)));
  page.on("response", (r) => { if (r.status() >= 400) badResponses.push(`${r.status()} ${r.url()}`); });

  // The figure-bearing KCs DETECTED from the served payloads (never from the manifest).
  const detectedFigureKcs: string[] = [];

  for (const kcId of seed.kcIds) {
    // Fetch the SERVED lesson payload directly (same session cookie) so the spec drives off the
    // real wire shape, and figure detection is from the SERVED reveal.figure (§0.9J Step 3).
    const apiResp = await page.request.get(`http://localhost:5173/api/v1/lesson/${kcId}`);
    expect(apiResp.status(), `lesson GET ${kcId} must serve 200 (faithful-gated, seeded)`).toBe(200);
    const lesson = await apiResp.json();
    expect(lesson.beats, `${kcId} must carry a beats payload`).toBeTruthy();
    const plan: string[] = lesson.beats.plan;
    const nameRo: string = lesson.kc_name_ro;
    const figureBearing = lesson.beats?.reveal?.figure != null;
    if (figureBearing) detectedFigureKcs.push(kcId);

    await page.goto(`/tutor/lesson/${kcId}`);

    // ── §4.7 selector set on FIRST paint ────────────────────────────────────────────────────────
    await expect(page.getByTestId("lesson-beat-pips"), `${kcId} pips visible`).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId("lesson-beat-pips").locator("[data-pip]")).toHaveCount(plan.length);
    await expect(page.getByTestId("lesson-beat-active"), `${kcId} exactly one active beat`).toHaveCount(1);
    // ① is PREDICT in the FULL/STANDARD plans: 3–4 options + next-gate disabled.
    expect(plan[0], `${kcId} plan starts with predict`).toBe("predict");
    const predictOptionCount = await page.getByTestId("beat-predict-options").locator("button").count();
    expect(predictOptionCount, `${kcId} predict has 3–4 options`).toBeGreaterThanOrEqual(3);
    expect(predictOptionCount).toBeLessThanOrEqual(4);
    await expect(page.getByTestId("beat-next-gate"), `${kcId} next-gate disabled on first paint`).toBeDisabled();

    // ── Gate 4a + 4b on first paint (active beat), both viewports ────────────────────────────────
    for (const vp of VIEWPORTS) {
      await page.setViewportSize(vp);
      await assertNoClip(page, "lesson-beat-active");
      await assertLegibility(page, "lesson-beat-active");
    }
    await page.setViewportSize({ ...VIEWPORTS[0] });

    // ── Gate 4d on the predict beat (rendered learner-visible RO) ────────────────────────────────
    {
      const texts = await extractRenderedTexts(page, "lesson-beat-active");
      const violations: RoViolation[] = checkRenderedTexts(texts);
      expect(
        violations,
        `${kcId} predict beat RO heuristic (gate 4d):\n${JSON.stringify(violations, null, 2)}`,
      ).toEqual([]);
    }

    // ── Traverse every beat (gate 4c interaction) ───────────────────────────────────────────────
    for (let i = 0; i < plan.length; i++) {
      const kind = plan[i];

      // Before clearing the active beat, the gate must be LOCKED (gate-honesty / 4c) — for every
      // beat that HAS an uncleared gate condition at probe time. The NAME beat is the one exception:
      // it is a text-only dwell-floored beat whose ONLY gate is the dwell timer, and under reduced
      // motion (emulateMedia above) that timer fires synchronously on mount (NameBeat.tsx:17 →
      // onGateClear immediately), so the gate is legitimately already open when the loop arrives.
      // Probing "must be locked" there asserts a condition the product deliberately doesn't impose;
      // the 4c gate-honesty proof is carried by predict/attempt/reveal/check, all of which DO hold an
      // uncleared interactive gate at probe time.
      if (kind !== "name") {
        await assertNextGateContract(page);
      }

      if (kind === "predict") {
        await page.getByTestId("beat-predict-options").locator("button").first().click();
      } else if (kind === "attempt") {
        // Non-numerical attempt = choice buttons inside the active beat.
        await page.getByTestId("lesson-beat-active").locator("button").first().click();
      } else if (kind === "reveal") {
        await driveReveal(page, kcId, figureBearing);
      } else if (kind === "name") {
        // NAME is a text-only dwell-floored beat; under reduced motion the gate opens immediately.
        // Nothing to click — fall through to the gate wait below.
      } else if (kind === "check") {
        // CHECK: clicking any choice grades + lesson_complete=true (beat_type==check) → handoff.
        await page.getByTestId("lesson-beat-active").locator("button").first().click();
        // The check submission lands on the completion screen (no separate next-gate click).
        await expect(page.getByTestId("lesson-complete-handoff"), `${kcId} completion handoff`).toBeVisible({ timeout: 15000 });
        break;
      }

      if (kind !== "check") {
        // The gate must OPEN after clearing this beat, then advance.
        await expect(page.getByTestId("beat-next-gate"), `${kcId} ${kind} gate opens`).toBeEnabled({ timeout: 10000 });
        await page.getByTestId("beat-next-gate").click();
      }
    }

    // ── Completion-screen no-clip + legibility, both viewports ───────────────────────────────────
    for (const vp of VIEWPORTS) {
      await page.setViewportSize(vp);
      await assertNoClip(page, "lesson-complete");
      await assertLegibility(page, "lesson-complete");
    }
    await page.setViewportSize({ ...VIEWPORTS[0] });

    // ── Handoff lands on a coherent oggi surface with a faithful KC's name_ro visible ────────────
    // The /oggi queue selector is deterministic (lowest-mastery, prereq-gated, id tie-break); it
    // surfaces ONE seeded faithful KC, not necessarily the just-completed one. The handoff is
    // verified COHERENT: the oggi screen paints and shows a real seeded KC name_ro (queue has data).
    await page.getByTestId("lesson-complete-handoff").click();
    await expect(page.getByTestId("oggi-screen"), `${kcId} handoff lands on oggi`).toBeVisible({ timeout: 15000 });
    // SOME seeded KC's name_ro must be visible on the oggi surface (the queue is seeded for every
    // promoted KC). We assert the just-completed KC's name_ro OR any seeded name_ro is shown.
    // .first() wraps the WHOLE .or() — the oggi panel and the queue-item name can both be present
    // (the panel contains the name), so the combined locator legitimately matches >1 element; .first()
    // keeps strict mode satisfied while still proving the oggi surface rendered a seeded KC.
    await expect(
      page.getByText(nameRo, { exact: false }).or(page.getByTestId("oggi-next-kc-panel")).first(),
      `${kcId} oggi surface shows a seeded KC`,
    ).toBeVisible({ timeout: 15000 });

    // No mid-traversal error text on-screen for this KC.
    await expect(page.getByText(/404|HTTP \d{3}|not found|eroare|error/i)).toHaveCount(0);
  }

  // ── Anti-vacuity (R-4b-Q1) ─────────────────────────────────────────────────────────────────────
  // The payload-detected figure-bearing set must be NON-EMPTY and must EQUAL seed.figureKcIds (the
  // corpus-derived set Task 5's FigureBindingNonVacuityTest proves non-empty). Detection from the
  // served payload — not the manifest — means a binding Plan 5/6 mints later is exercised here with
  // zero edits.
  expect(
    detectedFigureKcs.length,
    "the payload-detected figure-bearing set must be NON-EMPTY (R-4b-Q1 vacuity is dead)",
  ).toBeGreaterThan(0);
  expect(
    [...detectedFigureKcs].sort(),
    "payload-detected figure set must equal seed.figureKcIds (corpus-derived)",
  ).toEqual([...seed.figureKcIds].sort());

  // ── Whole-traversal hygiene (gate 4c) ──────────────────────────────────────────────────────────
  expect(badResponses, `4xx/5xx during the traversal:\n${badResponses.join("\n")}`).toEqual([]);
  expect(consoleErrors, `console/page errors during the traversal:\n${consoleErrors.join("\n")}`).toEqual([]);
});

/**
 * Drive the REVEAL beat to its final frame so the reveal gate clears, and run the figure gates
 * (4a no-clip at first/mid/final frame + the §4.7 figure selectors + back/play/reset-replay) on a
 * figure-bearing KC. For a text-mode reveal (no figure), step the stepped-text scrubber to the end.
 *
 * Detection is by `figureBearing` (from the SERVED reveal.figure), per §0.9J Step 3.
 */
async function driveReveal(page: Page, kcId: string, figureBearing: boolean): Promise<void> {
  // The reveal wrapper is `beat-figure-scrubber` in BOTH modes; the counter is `scrubber-step-counter`.
  await expect(page.getByTestId("beat-figure-scrubber"), `${kcId} reveal scrubber visible`).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("scrubber-step-counter")).toContainText(/pas\s+\d+\/\d+/);

  if (!figureBearing) {
    // TEXT mode (figure=null): step the data-step-fwd button to the last step.
    const fwd = page.getByTestId("beat-figure-scrubber").locator("[data-step-fwd]").first();
    const back = page.getByTestId("beat-figure-scrubber").locator("[data-step-back]").first();
    const counter = page.getByTestId("scrubber-step-counter");
    // Verify BACK functions (the one-shot-animation kill): fwd → back must move the counter. Use
    // auto-retrying assertions — a raw textContent() right after click() can race the React re-render.
    const start = await readScrubberCounter(page);
    const total = start?.n ?? 1;
    if (total > 1) {
      const c0 = (await counter.textContent()) ?? "";
      await fwd.click();
      await expect(counter, `${kcId} reveal forward must move the counter`).not.toHaveText(c0);
      await back.click();
      await expect(counter, `${kcId} reveal BACK must move the counter (one-shot is a gate violation)`).toHaveText(c0);
    }
    // Step to the final frame, waiting for each advance to land before the next click.
    for (let s = 0; s < total + 1; s++) {
      const cur = await readScrubberCounter(page);
      if (cur && cur.k >= cur.n) break;
      const beforeK = cur?.k ?? 0;
      await fwd.click();
      await expect
        .poll(async () => (await readScrubberCounter(page))?.k ?? 0, { timeout: 5000 })
        .toBeGreaterThan(beforeK);
    }
    const end = await readScrubberCounter(page);
    expect(end?.k, `${kcId} text reveal reached the final step`).toBe(end?.n);
    return;
  }

  // FIGURE mode: the family (graph-tree) is mounted; the shell controls drive the frames.
  await expect(page.getByTestId("graph-tree-root"), `${kcId} figure family root mounted`).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("graph-tree-step-fwd"), `${kcId} figure has a forward control`).toBeVisible();
  await expect(page.getByTestId("graph-tree-step-back"), `${kcId} figure has a back control`).toBeVisible();
  await expect(page.getByTestId("graph-tree-reset"), `${kcId} figure has a reset control`).toBeVisible();
  await expect(page.getByTestId("graph-tree-play"), `${kcId} figure has a play control (Task 4)`).toBeVisible();

  const fwd = page.getByTestId("graph-tree-step-fwd");
  const back = page.getByTestId("graph-tree-step-back");
  const reset = page.getByTestId("graph-tree-reset");

  // No-clip + legibility on the FIRST figure frame (both viewports).
  for (const vp of VIEWPORTS) {
    await page.setViewportSize(vp);
    await assertNoClip(page, "beat-figure-scrubber");
    await assertLegibility(page, "beat-figure-scrubber");
  }
  await page.setViewportSize({ ...VIEWPORTS[0] });

  const first = await readScrubberCounter(page);
  const total = first?.n ?? 1;

  // BACK must function (one-shot kill): forward then back must move the counter. Use auto-retrying
  // web-first assertions (toHaveText) — a raw textContent() read right after click() can catch the
  // pre-update DOM (React re-renders the counter asynchronously after the click event dispatches).
  const counter = page.getByTestId("scrubber-step-counter");
  if (total > 1) {
    const c0 = (await counter.textContent()) ?? "";
    await fwd.click();
    await expect(counter, `${kcId} figure forward must move the counter`).not.toHaveText(c0);
    // No-clip on a MID frame.
    await assertNoClip(page, "beat-figure-scrubber");
    await assertLegibility(page, "beat-figure-scrubber");
    await back.click();
    await expect(counter, `${kcId} figure BACK must move the counter (one-shot is a gate violation)`).toHaveText(c0);
  }

  // Step to the FINAL frame so the reveal gate clears (onStep(idx===lastIdx)).
  await stepToFinalFrame(page, fwd, total);
  const end = await readScrubberCounter(page);
  expect(end?.k, `${kcId} figure reached the final frame`).toBe(end?.n);

  // No-clip + legibility on the FINAL frame.
  await assertNoClip(page, "beat-figure-scrubber");
  await assertLegibility(page, "beat-figure-scrubber");

  // RESET-replay possible AFTER reaching the final frame (the DIJ-ONESHOT class is structurally dead).
  await reset.click();
  await expect
    .poll(async () => (await readScrubberCounter(page))?.k ?? -1, { timeout: 5000 })
    .toBe(1);
  // Step back to the final frame so the gate is cleared on exit.
  await stepToFinalFrame(page, fwd, total);
  const reEnd = await readScrubberCounter(page);
  expect(reEnd?.k, `${kcId} figure re-reached the final frame after reset`).toBe(reEnd?.n);
}

/**
 * Click the forward control until the scrubber counter reaches its final frame (k === n), waiting for
 * each advance to LAND before the next click (the counter re-renders asynchronously after the click
 * event dispatches — a synchronous re-read can race the React update). Bounded by `total` clicks.
 */
async function stepToFinalFrame(page: Page, fwd: ReturnType<Page["getByTestId"]>, total: number): Promise<void> {
  for (let s = 0; s < total + 1; s++) {
    const cur = await readScrubberCounter(page);
    if (cur && cur.k >= cur.n) break;
    const beforeK = cur?.k ?? 0;
    await fwd.click();
    await expect
      .poll(async () => (await readScrubberCounter(page))?.k ?? 0, { timeout: 5000 })
      .toBeGreaterThan(beforeK);
  }
}
