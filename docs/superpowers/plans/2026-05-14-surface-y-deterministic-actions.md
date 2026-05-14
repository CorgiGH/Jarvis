# Surface Y Deterministic `submit` Action — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic `submit` action to Surface Y so the persona's "my answer is ready" intent triggers a controller-executed CHECK ANSWER click — ending the `type`→`click` loop that has stalled all six prior runs.

**Architecture:** One semantic action `submit` is added to the persona vocabulary (`surface-y-persona.mjs`). The controller (`surface-y.mjs`) resolves the CHECK ANSWER button via an exact role+name selector, asserts exactly one match, and clicks — hard-failing loudly (visible `error` step + run halt) on ambiguity. `click`/`type`/`navigate`/`ask_sidekick`/`give_up` and the affordance scan / field-targeting are untouched. Spec: `docs/superpowers/specs/2026-05-14-surface-y-deterministic-actions-design.md`.

**Tech Stack:** Node ESM, Playwright, native `node --test` runner. All tool tests run via `npm run test:tools` (from the `tools/` directory).

**Pre-existing state:** `tools/surface-y.mjs` already carries uncommitted step-1 instrumentation (an `affordancesShown` field logged onto each transcript entry + an "Affordances shown to persona" doc section) from earlier this session — that change tested green at 57/57. Task 1 lands it.

**Selector source of truth:** The CHECK ANSWER button is `tutor-web/src/components/DrillStack.tsx:256-262` — a `<button>` with `onClick={handleCheckAnswer}`, `disabled={phase === "grading" || attempt.trim().length === 0}`, text `"CHECK ANSWER"` (or `"GRADING…"` while grading). It has **no `data-testid`**. The frontend's own tests target it with `getByRole("button", { name: /check answer/i })` — this plan uses the same, anchored: `getByRole("button", { name: /^check answer$/i })`.

---

### Task 1: Land pre-existing session work (instrumentation + spec)

Cleans the working tree before the new TDD work. The instrumentation is already written and was verified green earlier this session; the spec is approved.

**Files:**
- Modify (commit only, no edit): `tools/surface-y.mjs`
- Commit only: `docs/superpowers/specs/2026-05-14-surface-y-deterministic-actions-design.md`, `docs/superpowers/plans/2026-05-14-surface-y-deterministic-actions.md`

- [ ] **Step 1: Confirm the existing suite is green**

Run: `cd tools && npm run test:tools`
Expected: `tests 57 / pass 57 / fail 0`.

- [ ] **Step 2: Commit the affordance-payload instrumentation**

```bash
git add tools/surface-y.mjs
git commit -m "feat(standin): log affordance list per Surface Y step (diagnostic instrumentation)"
```

- [ ] **Step 3: Commit the spec + this plan**

```bash
git add docs/superpowers/specs/2026-05-14-surface-y-deterministic-actions-design.md docs/superpowers/plans/2026-05-14-surface-y-deterministic-actions.md
git commit -m "docs(standin): deterministic submit action — spec + implementation plan"
```

---

### Task 2: Add the `submit` action to the persona vocabulary (GATING sub-task)

Per the council, the persona prompt must reliably elicit `submit` *before* a quota-scarce live run is spent. This task lands first.

**Files:**
- Modify: `tools/surface-y-persona.mjs` (function `buildPersonaPrompt`)
- Test: `tools/surface-y-persona.test.mjs`

- [ ] **Step 1: Write the failing test**

Append to `tools/surface-y-persona.test.mjs`:

```javascript
test("buildPersonaPrompt exposes the submit action and instructs using it over click", () => {
  const prompt = buildPersonaPrompt({
    schema,
    ledger: new Set(),
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>x</p>",
  });
  // `submit` is in the JSON action enum
  assert.match(prompt, /"action":.*\| "submit" \|/);
  // the ACTION RULES instruct using `submit`, not clicking CHECK ANSWER
  assert.match(prompt, /use action "submit"/);
  assert.doesNotMatch(prompt, /To submit or check an answer, "click"/);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools && node --test surface-y-persona.test.mjs`
Expected: FAIL — the new assertions don't match (no `submit` in the enum; old click-to-submit rule still present).

- [ ] **Step 3: Add `submit` to the action enum**

In `tools/surface-y-persona.mjs`, in the `buildPersonaPrompt` return template, change the action line of the STRICT JSON block:

Find:
```
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "give_up",
```
Replace with:
```
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "submit" | "give_up",
```

- [ ] **Step 4: Replace the click-to-submit ACTION RULE with the submit-action rule**

In the same file, in the `ACTION RULES` block, find:
```
- To submit or check an answer, "click" the relevant button (e.g. one labelled CHECK / SUBMIT / ANSWER). A button only works when it is NOT marked [disabled].
```
Replace with:
```
- To submit or check your answer, use action "submit" (no "target" needed). Use this when your answer field shows [current value: "..."] and you believe your answer is ready. Do NOT keep typing into a filled field, and do NOT try to "click" the CHECK ANSWER button yourself — "submit" handles it.
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd tools && node --test surface-y-persona.test.mjs`
Expected: PASS — all persona tests green, including the new one.

- [ ] **Step 6: Commit**

```bash
git add tools/surface-y-persona.mjs tools/surface-y-persona.test.mjs
git commit -m "feat(standin): add semantic submit action to Surface Y persona vocab"
```

---

### Task 3: Add the `submit` controller branch — happy path

The controller resolves CHECK ANSWER and clicks it when the persona emits `submit`. (The single-match guard is Task 4 — this task is the minimal happy path so the next task's failure test is a real red.)

**Files:**
- Modify: `tools/surface-y.mjs` (the action-execution `try` block, inside `runStandin`'s loop)
- Test: `tools/surface-y.test.mjs`

- [ ] **Step 1: Write the failing test**

Append to `tools/surface-y.test.mjs`:

```javascript
test("submit action resolves CHECK ANSWER and clicks it", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let submitClicked = false;
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":"ready to submit"}',
    model_resolved: "fake", prompt_sha256: "z".repeat(64),
    tokens_in: 50, tokens_out: 20, latency_ms: 200,
  });
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (scriptOrFn) => {
          if (typeof scriptOrFn === "function") return 'button: "CHECK ANSWER"';
          if (typeof scriptOrFn === "string" && scriptOrFn.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        getByRole: (_role, _opts) => ({
          count: async () => 1,
          click: async () => { submitClicked = true; },
        }),
        click: async () => {},
        fill: async () => {},
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 1, outputDir: tmp, sessionId: "test-y-submit",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  assert.ok(submitClicked, "submit action must resolve + click CHECK ANSWER");
  assert.match(readFileSync(docPath, "utf8"), /surface: Y/);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: FAIL — `submitClicked` stays `false` (no `submit` branch exists; the action falls through the `if/else if` chain doing nothing).

- [ ] **Step 3: Add the `submit` branch to the action-execution block**

In `tools/surface-y.mjs`, inside `runStandin`'s `while` loop, find the `ask_sidekick` branch at the end of the action-execution `try` block:

```javascript
        } else if (action.action === "ask_sidekick") {
          await page.evaluate((q) => {
            const evt = new CustomEvent("standin-sidekick-ask", { detail: { question: q } });
            window.dispatchEvent(evt);
          }, action.payload).catch(() => {});
        }
```

Replace it with (adds the `submit` branch immediately after):

```javascript
        } else if (action.action === "ask_sidekick") {
          await page.evaluate((q) => {
            const evt = new CustomEvent("standin-sidekick-ask", { detail: { question: q } });
            window.dispatchEvent(evt);
          }, action.payload).catch(() => {});
        } else if (action.action === "submit") {
          // Deterministic submit: the persona CHOSE `submit`; the controller resolves
          // CHECK ANSWER and clicks it. Role+name selector — the button has no
          // data-testid (see tutor-web/src/components/DrillStack.tsx:256-262).
          await page.getByRole("button", { name: /^check answer$/i }).click();
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: PASS — `submitClicked` is `true`; all existing `surface-y.test.mjs` tests still green.

- [ ] **Step 5: Commit**

```bash
git add tools/surface-y.mjs tools/surface-y.test.mjs
git commit -m "feat(standin): controller executes submit action via CHECK ANSWER selector"
```

---

### Task 4: `submit` hard-fail on ambiguous selector + loop-detection regression

The submit selector must resolve to exactly one element. Zero or multiple matches is a loud, recoverable hard-fail — never a guess. Also locks in that a repeated `submit` still trips loop-detection.

**Files:**
- Modify: `tools/surface-y.mjs` (the `submit` branch from Task 3)
- Test: `tools/surface-y.test.mjs`

- [ ] **Step 1: Write the failing hard-fail test**

Append to `tools/surface-y.test.mjs`:

```javascript
test("submit hard-fails loudly when CHECK ANSWER is ambiguous", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":"ready"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {}, waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (s) => {
          if (typeof s === "function") return 'button: "CHECK ANSWER"';
          if (typeof s === "string" && s.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        getByRole: () => ({ count: async () => 2, click: async () => {} }),  // ambiguous: 2 matches
        click: async () => {}, fill: async () => {}, close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 10, outputDir: tmp, sessionId: "test-y-submitfail",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  assert.equal(callsMade, 1, "run must STOP after the first ambiguous submit, not loop");
  assert.match(readFileSync(docPath, "utf8"), /submit_failed: CHECK ANSWER resolved to 2 matches/);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: FAIL — Task 3's branch never checks `count()`, so the ambiguous selector is not caught. The guard never stops the run on the first submit (loop-detection eventually halts it at 3, so `callsMade` is `3`, not the asserted `1`), and no `submit_failed` text is ever written.

- [ ] **Step 3: Add the single-match guard to the `submit` branch**

In `tools/surface-y.mjs`, replace the Task 3 `submit` branch:

```javascript
        } else if (action.action === "submit") {
          // Deterministic submit: the persona CHOSE `submit`; the controller resolves
          // CHECK ANSWER and clicks it. Role+name selector — the button has no
          // data-testid (see tutor-web/src/components/DrillStack.tsx:256-262).
          await page.getByRole("button", { name: /^check answer$/i }).click();
        }
```

with:

```javascript
        } else if (action.action === "submit") {
          // Deterministic submit: the persona CHOSE `submit`; the controller resolves
          // CHECK ANSWER and clicks it. Role+name selector — the button has no
          // data-testid (see tutor-web/src/components/DrillStack.tsx:256-262).
          const submitBtn = page.getByRole("button", { name: /^check answer$/i });
          const matches = await submitBtn.count();
          if (matches !== 1) {
            // Loud hard-fail: CHECK ANSWER ambiguous or missing — never guess which
            // button to click on the live site. Record a visible error step and end
            // the run. `break` exits the while loop; the surrounding catch does not
            // fire on a break.
            transcript.push({
              action: "error", target: "",
              observation: `submit_failed: CHECK ANSWER resolved to ${matches} matches (expected exactly 1)`,
              ts: new Date().toISOString(),
            });
            break;
          }
          await submitBtn.click();
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: PASS — `callsMade` is `1`, the doc contains `submit_failed: CHECK ANSWER resolved to 2 matches`. Task 3's happy-path test still green (count `1` → click).

- [ ] **Step 5: Write the loop-detection regression test**

Append to `tools/surface-y.test.mjs`:

```javascript
test("repeated submit trips loop-detection (no infinite submit loop)", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":"submitting"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {}, waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (s) => {
          if (typeof s === "function") return 'button: "CHECK ANSWER"';
          if (typeof s === "string" && s.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        getByRole: () => ({ count: async () => 1, click: async () => {} }),
        click: async () => {}, fill: async () => {}, close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 20, outputDir: tmp, sessionId: "test-y-submitloop",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  assert.equal(callsMade, 3, "3 identical submit actions must trip loop-detection at exactly 3");
  assert.match(readFileSync(docPath, "utf8"), /stuck/);
});
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: PASS — existing `(action,target)` loop-detection already handles repeated `submit` (target is `""` for all three), so no implementation change is needed; `callsMade` is `3`, doc contains `stuck`.

- [ ] **Step 7: Commit**

```bash
git add tools/surface-y.mjs tools/surface-y.test.mjs
git commit -m "feat(standin): submit hard-fails loudly on ambiguous CHECK ANSWER selector"
```

---

### Task 5: Mark the `submit` step controller-executed in the finding doc

Per the session decision ("mark anyway"), a successful `submit` step records both that the persona CHOSE it (the `action: "submit"` value) and that the controller EXECUTED it.

**Files:**
- Modify: `tools/surface-y.mjs` (the `submit` branch + the doc-builder transcript table)
- Test: `tools/surface-y.test.mjs`

- [ ] **Step 1: Write the failing test**

Append to `tools/surface-y.test.mjs`:

```javascript
test("a successful submit step is marked controller-executed in the finding doc", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":"ready to submit"}',
    model_resolved: "fake", prompt_sha256: "z".repeat(64),
    tokens_in: 50, tokens_out: 20, latency_ms: 200,
  });
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {}, waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (s) => {
          if (typeof s === "function") return 'button: "CHECK ANSWER"';
          if (typeof s === "string" && s.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        getByRole: () => ({ count: async () => 1, click: async () => {} }),
        click: async () => {}, fill: async () => {}, close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 1, outputDir: tmp, sessionId: "test-y-submitmark",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  assert.match(readFileSync(docPath, "utf8"), /\[exec: controller-deterministic\]/);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: FAIL — nothing sets or renders an `executor` marker yet.

- [ ] **Step 3: Set the `executor` field after a successful submit click**

In `tools/surface-y.mjs`, in the `submit` branch, find:

```javascript
          await submitBtn.click();
        }
```

Replace with (the `submit` action entry was pushed onto `transcript` earlier this iteration, so it is `transcript[transcript.length - 1]` here — same pattern as the existing `.error` assignment in the surrounding `catch`):

```javascript
          await submitBtn.click();
          transcript[transcript.length - 1].executor = "controller-deterministic";
        }
```

- [ ] **Step 4: Render `executor` in the doc-builder transcript table**

In `tools/surface-y.mjs`, in the markdown doc-builder, find the transcript-table row mapper:

```javascript
      ...transcript.map((t, i) => {
        const payload = (t.payload || "").replace(/\s+/g, " ").replace(/\|/g, "\\|").trim().slice(0, 60);
        return `| ${i + 1} | ${t.action} | ${(t.target || "").slice(0, 40)} | ${payload} | ${(t.observation || "").slice(0, 80)} |`;
      }),
```

Replace with:

```javascript
      ...transcript.map((t, i) => {
        const payload = (t.payload || "").replace(/\s+/g, " ").replace(/\|/g, "\\|").trim().slice(0, 60);
        const obs = (t.observation || "").slice(0, 80);
        const obsCell = t.executor ? `${obs} [exec: ${t.executor}]` : obs;
        return `| ${i + 1} | ${t.action} | ${(t.target || "").slice(0, 40)} | ${payload} | ${obsCell} |`;
      }),
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: PASS — the doc contains `[exec: controller-deterministic]`; all other `surface-y.test.mjs` tests still green.

- [ ] **Step 6: Commit**

```bash
git add tools/surface-y.mjs tools/surface-y.test.mjs
git commit -m "feat(standin): mark submit steps controller-executed in finding doc"
```

---

### Task 6: Full-suite verification + live-run handoff

No code change — confirms the whole tool suite is green and documents the acceptance test that closes the spec.

**Files:** none.

- [ ] **Step 1: Run the full tool test suite**

Run: `cd tools && npm run test:tools`
Expected: `tests 62 / pass 62 / fail 0` (57 prior + 5 new: 1 persona, 1 submit happy-path, 2 submit failure-mode, 1 executor-marking).

- [ ] **Step 2: Confirm no regressions in the existing tests**

Verify the three pre-existing `runStandin` tests (`enforces hard cap`, `survives a piggyback screenshot failure`, `breaks out of an action loop`) and all `surface-y-persona.test.mjs` / `surface-y-gate.test.mjs` tests are still listed as `pass`.

- [ ] **Step 3: Record the live-run acceptance test (do not run — quota-gated)**

The spec's acceptance test is a live Surface Y run, gated on the OpenRouter `:free` quota reset (00:00 UTC). When quota permits, the run is:

```bash
cd tools && OPENROUTER_API_KEY_STANDIN=<key> node surface-y.mjs \
  --task=01KR6K07T6PATPRR5KH1JXYF8E \
  --schema=../docs/standin-findings/schemas/PS-Tema-A.yaml \
  --max-calls=16
```

Acceptance: the finding doc shows a `submit` transcript step marked `[exec: controller-deterministic]` AND a `drill_grade` event reaches the backend `tutor_events` log. **If the persona still loops `type` and never emits `submit`** despite the updated prompt — the council's Devil's Advocate concern was correct; escalate (consider a stronger `:free` model or the Groq fallback), do not prompt-tune past the model-capability floor.

- [ ] **Step 4: No commit**

Tasks 1–5 each committed their own slice; this task is verification only.
