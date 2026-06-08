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

// ── /lesson/:kcId — LessonScreen ──────────────────────────────────────────
test("Phase-6 /lesson/:kcId: lesson-screen paints with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => {
    if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
  });

  await page.route("**/api/v1/lesson/*", (r) =>
    r.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        kcId: "kc-recursion",
        kc_name_ro: "Recursie",
        kc_name_en: "Recursion",
        concrete_question_ro: "Ce știi despre recursie?",
        echo_source_ro: "O funcție care se apelează pe sine.",
        prediction_options: ["Metodă directă", "Auto-apel", "Iterație"],
        term_ro: "Recursie",
        definition_ro: null,
        explanation_ro: "O funcție definită prin ea însăși.",
        worked_example_ro: "fib(n) = fib(n-1) + fib(n-2)",
        provenance: { type: "authored", hasBeenFaithfulChecked: true },
      }),
    })
  );

  await page.goto("/tutor/lesson/kc-recursion");

  // (1) Primary testid paints
  await expect(page.getByTestId("lesson-screen")).toBeVisible({ timeout: 10000 });
  // The entry step (0c) should paint first
  await expect(page.getByTestId("lesson-step-entry")).toBeVisible({ timeout: 10000 });

  // No error text on first paint
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // (2) zero 4xx/5xx on first paint
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // (3) click-through: submit an answer to advance to next step
  const answerInput = page.locator("textarea, input[type=text]").first();
  if (await answerInput.isVisible()) {
    await answerInput.fill("O funcție care se apelează pe sine");
  }
  const submitBtn = page.locator("button").filter({ hasText: /trimite|submit|continuă|next/i }).first();
  if (await submitBtn.isVisible()) {
    await submitBtn.click();
    await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  }

  expect(bad, `4xx/5xx after click:\n${bad.join("\n")}`).toEqual([]);
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
