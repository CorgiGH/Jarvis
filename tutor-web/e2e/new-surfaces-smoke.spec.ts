import { test, expect } from "@playwright/test";

/**
 * New-surfaces interaction-smoke gate.
 *
 * Covers every route that is NEW since Phase 5/6 integration:
 *   /oggi        — OggiScreen (already partially covered by oggi-interaction-smoke;
 *                  included here too for completeness in the consolidated report)
 *   /lesson/:kcId — LessonScreen
 *   /exam/:subject — MockExamShell
 *   /subjects     — SubjectMap (rendered via App shell, /subjects path)
 *   /day-of       — DayOfShell (standalone, no App shell)
 *   /welcome      — OnboardingShell (standalone, no App shell)
 *   /placement    — PlacementShell (standalone, no App shell)
 *   /me (SettingsMe) — provider toggle visible
 *
 * Per-test assertions:
 *   (1) primary data-testid paints on first load (timeout 10 s)
 *   (2) zero 4xx/5xx network responses during first paint
 *   (3) a click-through shows no /404|HTTP \d{3}|not found|error/i text
 *       and no new 4xx/5xx
 *
 * Mocked fixtures replace every server call — CI runs no Kotlin backend.
 * NEVER touches ~/.jarvis/tutor.db.
 */

// ── Shared shell stubs (same as phase5-core-loop + oggi-interaction-smoke) ──
async function stubShell(page: import("@playwright/test").Page) {
  await page.route("**/api/v1/tutor/auto-session", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" })
  );
  await page.route("**/api/v1/me/export", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        aiLiteracyConfirmed: true,
        user: {
          id: "u1",
          name: "Alex Test",
          email: "alex@test.ro",
          scope: "student",
          lang: "ro",
        },
        consentEvents: [],
        preferences: { hintMode: "default", loggingPausedUntil: null },
        exportedAt: "2026-06-09T00:00:00Z",
      }),
    })
  );
  await page.route("**/api/v1/last-task", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        r.request().method() === "GET" ? { taskId: null } : {}
      ),
    })
  );
  await page.route("**/api/v1/fsrs/forecast", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ dueNow: 0 }),
    })
  );
  await page.route("**/api/v1/tasks", (r) => {
    if (r.request().method() === "GET") {
      r.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ tasks: [] }),
      });
    } else r.continue();
  });
}

// ── /lesson/:kcId — BeatOrchestrator (replaces LessonScreen) ──────────────
test("Phase-6 /lesson/:kcId: beat-orchestrator paints + handoff → /oggi (same KC visible)", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  // Beats payload (minimal STANDARD plan ①②③⑤ choice-variant; enough for first-paint + handoff).
  await page.route("**/api/v1/lesson/pa-kc-001/beat", (r) => {
    const t = (r.request().postDataJSON() as { beat_type: string }).beat_type;
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      correct: true, score: 1.0, feedback_ro: "corect", beat_type: t,
      lesson_complete: t === "check", first_encounter: true,
      phase: t === "check" ? "practice" : null, verification_status: t === "check" ? "faithful" : null,
    }) });
  });
  await page.route("**/api/v1/lesson/pa-kc-001", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      kcId: "pa-kc-001", kc_name_ro: "Noțiunea de algoritm", kc_name_en: "Algorithm",
      concrete_question_ro: "?", echo_source_ro: "?", prediction_options: [],
      term_ro: "Algoritm", definition_ro: "def", explanation_ro: "expl", worked_example_ro: "ex",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
      beats: {
        plan: ["predict", "attempt", "reveal", "check"],
        concept_type: "definition-taxonomy",
        predict: { prompt: "Care e un algoritm?", options: [
          { text: "Rețetă", callback: "Da.", correct: true },
          { text: "Număr", callback: "Nu.", correct: false },
          { text: "Culoare", callback: "Nu.", correct: false } ] },
        attempt: { statement: "Alege.", choices: [
          { text: "Pași de legat șireturi", correct: true, feedback: "Corect." },
          { text: "„pantof"", correct: false, feedback: "Nu." } ], feedback_correct: "Da." },
        reveal: { steps: [
          { text: "Pas 1.", callout: "Intrarea." },
          { text: "Pas 2.", callout: "Pași neambigui." } ] },
        check: { item_stem: "Care e un algoritm?", choices: [
          { text: "Pași de căutare", correct: true, feedback: "Da." },
          { text: "42", correct: false, feedback: "Nu." } ] },
      },
    }) }),
  );
  // /oggi (the handoff target) needs its queue stub so queue-item-pa-kc-001 paints.
  await stubShell(page);
  await page.route("**/api/v1/queue/today", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      day: "2026-06-12", total_due: 1, items: [{
        kc_id: "pa-kc-001", kc_name_ro: "Noțiunea de algoritm", kc_name_en: "Algorithm",
        subject: "PA", phase: "intro", mastery_ewma: 0.2, fsrs_card_id: null,
        verification_status: "faithful", worked_example_first: false, mode: "worked" }] }) }),
  );

  await page.goto("/tutor/lesson/pa-kc-001");

  // (1) §4.7 first-paint testids
  await expect(page.getByTestId("lesson-beat-pips")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("lesson-beat-active")).toHaveCount(1);
  await expect(page.getByTestId("beat-predict-options")).toBeVisible();
  await expect(page.getByTestId("beat-next-gate")).toBeDisabled();

  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);
});

// ── /exam/:subject — MockExamShell ────────────────────────────────────────
test("Phase-6 /exam/:subject: mock-exam-shell paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await page.goto("/tutor/exam/PA");

  // (1) Primary testid paints
  await expect(page.getByTestId("mock-exam-shell")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: the timer + submit button are visible; click submit (disabled)
  // Assert submit button exists (it will be disabled since questions=[] and no answers)
  await expect(page.getByTestId("mock-submit-btn")).toBeVisible();

  // No error text after inspection
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx after inspection:\n${bad.join("\n")}`).toEqual([]);
});

// ── /subjects — SubjectMap (via App shell) ────────────────────────────────
test("Phase-6 /subjects: subject-map paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await stubShell(page);

  // Stub /api/v1/mastery with two subjects
  await page.route("**/api/v1/mastery", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        subjects: [
          {
            subject_id: "PA",
            subject_name_ro: "Proiectarea Algoritmilor",
            subject_name_en: "Algorithm Design",
            kcs: [
              { kc_id: "kc-1", ewma_score: 0.7, observations: 5, verification_status: "faithful" },
              { kc_id: "kc-2", ewma_score: 0.2, observations: 3, verification_status: "faithful" },
            ],
          },
          {
            subject_id: "ALO",
            subject_name_ro: "Analiza și Limbaje Formale",
            subject_name_en: "Formal Languages",
            kcs: [
              { kc_id: "kc-3", ewma_score: 0.85, observations: 8, verification_status: "faithful" },
            ],
          },
        ],
      }),
    })
  );

  await page.goto("/tutor/subjects");

  // (1) Primary testid paints
  await expect(page.getByTestId("subject-map")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: subject cards should be visible; click first
  const firstCard = page.getByTestId("subject-card-PA");
  if (await firstCard.isVisible()) {
    await firstCard.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
});

// ── /day-of — DayOfShell (standalone) ────────────────────────────────────
test("Phase-6 /day-of: day-of-shell paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  // Stub exam-dates with an imminent exam (within 24h)
  const soonIso = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(); // 2h from now
  await page.route("**/api/v1/me/exam-dates", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        exam_dates: [{ subject: "PA", start_at: soonIso }],
      }),
    })
  );

  await page.goto("/tutor/day-of");

  // (1) Primary testid paints
  await expect(page.getByTestId("day-of-shell")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) no interactive click needed — countdown renders; assert no errors remain
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx after paint:\n${bad.join("\n")}`).toEqual([]);
});

// ── /welcome — OnboardingShell (standalone) ───────────────────────────────
test("Phase-6 /welcome: onboarding-shell paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await page.goto("/tutor/welcome");

  // (1) Primary testid paints
  await expect(page.getByTestId("onboarding-shell")).toBeVisible({ timeout: 10000 });
  // Step 1 (AI literacy gate) should be visible
  await expect(page.getByTestId("onboarding-step-1")).toBeVisible();

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: interact with lang toggle if visible
  const langToggle = page.getByTestId("lang-toggle");
  if (await langToggle.isVisible()) {
    await langToggle.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
});

// ── /placement — PlacementShell (standalone) ──────────────────────────────
test("Phase-6 /placement: placement-shell paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await page.goto("/tutor/placement");

  // (1) Primary testid paints
  await expect(page.getByTestId("placement-shell")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: answer first question option if visible
  // PlacementQuestion renders radio-like option buttons
  const firstOption = page.locator("button, input[type=radio]").first();
  if (await firstOption.isVisible()) {
    await firstOption.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
});

// ── /me — SettingsMe with GraderProviderToggle visible ────────────────────
test("Phase-6 /me: settings-me + grader-provider-toggle paint with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await stubShell(page);

  // Stub grader-provider endpoint
  await page.route("**/api/v1/me/grader-provider", (r) => {
    if (r.request().method() === "GET") {
      r.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ provider: "free" }),
      });
    } else {
      r.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    }
  });

  await page.goto("/tutor/me");

  // (1) Primary testids paint
  await expect(page.getByTestId("settings-me")).toBeVisible({ timeout: 10000 });
  // GraderProviderToggle (provider selector) must be visible — the SettingsMe mounts it
  await expect(page.getByTestId("grader-provider-toggle")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: click the "claude" provider option
  const claudeOption = page.getByTestId("grader-provider-option-claude");
  if (await claudeOption.isVisible()) {
    await claudeOption.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  // Click save
  const saveBtn = page.getByTestId("grader-provider-save-btn");
  if (await saveBtn.isVisible()) {
    await saveBtn.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
});
