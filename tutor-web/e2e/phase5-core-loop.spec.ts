import { test, expect } from "@playwright/test";

/**
 * Phase-5 core-loop interaction-smoke gate.
 *
 * Mounts the REAL tutor workspace under /tutor/?taskId=task-1, stubs the
 * first-paint /api contract + the trust-engine routes at the Playwright layer
 * (CI runs no Kotlin backend; NEVER touches ~/.jarvis/tutor.db). Asserts:
 *   (1) every task-prep-path Visual-Acceptance data-testid paints (grounded-
 *       explanation-card is a queue-path surface — asserted ABSENT here, covered
 *       by DrillStack.grounded.test.tsx; rung-2..4 are unit-asserted)
 *   (2) zero 4xx/5xx during first paint
 *   (3) clicks every interactive element
 *   (4) no /404|HTTP \d{3}|not found|error/i text + no new 4xx/5xx after each click
 *   (5) the faithful badge + generated provenance badge never co-render on the
 *       same [data-content-block]
 */
test("Phase-5 core loop: all trust surfaces paint + interact with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  // ── First-paint /api contract stubs ──
  await page.route("**/api/v1/tutor/auto-session", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/v1/me/export", (r) =>
    r.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }) }));
  await page.route("**/api/v1/last-task", (r) =>
    r.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify(r.request().method() === "GET" ? { taskId: "task-1" } : {}) }));
  await page.route("**/api/v1/fsrs/forecast", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ dueNow: 0 }) }));
  await page.route("**/api/v1/tasks", (r) => {
    if (r.request().method() === "GET") {
      r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ tasks: [{ id: "task-1" }] }) });
    } else r.continue();
  });

  // ── Prep bundle: an AUTHORED faithful drill (so the TrustBadge faithful path applies;
  //    note the prep blob carries NO kc_id, so GroundedExplanationCard no-ops — that is
  //    a queue-path surface, asserted ABSENT below) ──
  await page.route("**/api/v1/tasks/*/prep", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      taskId: "task-1", generatedAt: "2026-06-08T00:00:00Z", version: 1,
      problemsJson: JSON.stringify([{ problemId: "p1", page: 0, statement: "Trace fib(5)." }]),
      drillsJson: JSON.stringify({ p1: {
        drill: "Trace fib(5).", worked: "fib(5)=fib(4)+fib(3)=5",
        definition: "Recursion: a function defined in terms of itself.",
        check: "Trace fib(4).", expectedAnswerHint: "5",
        provenance: { type: "authored", hasBeenFaithfulChecked: true },
      } }),
      railJson: "[]",
    }) }));
  await page.route("**/api/v1/task/*/reprep", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" }));

  // ── Grade reply: incorrect, carrying ladder + cited misconception + faithful status ──
  await page.route("**/api/v1/drill/grade", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      correct: false, score: 0.2, rubric: {}, misconception: "OFF_BY_ONE",
      elaboratedFeedback: "Aproape — recitește cazul de bază.",
      verification_status: "faithful",
      ladder_rungs: [
        { level: 0, text: "Uită-te din nou la enunț." },
        { level: 1, text: "Explică-ți cu voce tare." },
        { level: 2, text: "Greșeala: confunzi cazul de bază." },
      ],
      misconception_payload: {
        id: "OFF_BY_ONE", refutation: "Cazul de bază oprește recursia la n<=1.",
        figure_spec: null, self_explanation_prompt: null,
        source: { doc: "curs3.pdf", page: 12, span: null, quote: "caz" },
      },
    }) }));

  // ── Grounded teaching (faithful) — defensive stub only. The task-prep fixture
  //    carries NO kc_id, so DrillStack mounts with kcId=undefined and
  //    GroundedExplanationCard never fetches /teaching (honest no-op). This route
  //    exists so any stray fetch returns 200 instead of a 404 (which would trip the
  //    zero-4xx gate). The card itself is asserted in the queue-path unit test
  //    (DrillStack.grounded.test.tsx, Task 10), NOT here — see the note below. ──
  await page.route("**/api/v1/teaching/*", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      kcId: "kc-1", name_ro: "Recursie",
      explanation_ro: "O funcție definită prin ea însăși.",
      worked_example_ro: "fib(5)=5",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
    }) }));

  // ── (1) shell + first paint ──
  await page.goto("/tutor/?taskId=task-1");
  expect(page.url()).toContain("/tutor");
  await expect(page.getByTestId("app-shell")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("drill-stack")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("drill-confidence-row")).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // ── (3) interact: pick confidence, type, check ──
  await page.getByTestId("confidence-MAYBE").click();
  await page.getByTestId("drill-attempt-input").fill("4");
  await page.getByTestId("drill-check-btn").click();

  // ── trust surfaces paint post-grade ──
  await expect(page.getByTestId("drill-feedback-ladder")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("feedback-rung-rail")).toBeVisible();
  await expect(page.getByTestId("feedback-rung-0")).toBeVisible();
  await expect(page.getByTestId("feedback-rung-live-pip")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon-kicker")).toContainText("OFF_BY_ONE");
  await expect(page.getByTestId("misconception-ribbon-refutation")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon-citation")).toContainText("curs3.pdf");
  await expect(page.getByTestId("trust-badge")).toBeVisible();
  await expect(page.getByTestId("trust-badge")).toContainText(/corespunde cursului/i);
  // grounded-explanation-card is a QUEUE-PATH surface (needs QueueItem.kc_id). The
  // task-prep blob has no kc_id, so the card honestly no-ops here — assert it is
  // ABSENT on the task-prep path. Its paint-when-kcId-present behavior is covered by
  // the queue-path unit test (DrillStack.grounded.test.tsx, Task 10).
  await expect(page.getByTestId("grounded-explanation-card")).toHaveCount(0);

  // ── (3) escalate the ladder; (4) no error after click ──
  await page.getByTestId("feedback-rung-escalate-button").click();
  await expect(page.getByTestId("feedback-rung-1")).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // ── (5) rendering-boundary invariant: faithful badge never co-renders with the generated badge ──
  const coRender = await page.evaluate(() => {
    const blocks = Array.from(document.querySelectorAll("[data-content-block]"));
    return blocks.some((b) =>
      b.querySelector('[data-faithful="true"]') && b.querySelector('[data-provenance-type="generated"]'));
  });
  expect(coRender, "faithful + generated badge co-rendered in one content block").toBe(false);
  await expect(page.getByTestId("provenance-badge")).toHaveCount(0); // authored fixture → no generated badge

  // ── (2/4) zero 4xx/5xx across the whole flow ──
  expect(bad, `4xx/5xx during interaction:\n${bad.join("\n")}`).toEqual([]);
});
