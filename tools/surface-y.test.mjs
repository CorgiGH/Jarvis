// tools/surface-y.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { runStandin, parseFallbackModels, callLlmWithFallback } from "./surface-y.mjs";

test("runStandin enforces hard cap on LLM calls", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"give_up","target":"","payload":"","observation":"stuck"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (scriptOrFn) => {
          if (typeof scriptOrFn === "function") {
            // affordances scan
            return 'button: "CHECK ANSWER" [disabled]\ntextarea: "R code"';
          }
          if (typeof scriptOrFn === "string" && scriptOrFn.startsWith("document.body")) {
            return "page text";
          }
          // LINT_EVAL_SCRIPT (piggyback)
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        click: async () => {},
        fill: async () => {},
        url: () => "https://corgflix.duckdns.org/tutor/?taskId=t1",
        close: async () => {},
        setExtraHTTPHeaders: async () => {},
        on: () => {},
      }),
      setExtraHTTPHeaders: async () => {},
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1",
    schemaPath,
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    maxCallsPerSession: 3,
    outputDir: tmp,
    sessionId: "test-y-1",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
    piggybackZ: false,
  });
  assert.ok(callsMade <= 3, `expected ≤3 calls, got ${callsMade}`);
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Y/);
  assert.match(text, /calls_used: \d+/);
});

test("runStandin survives a piggyback screenshot failure and still writes the doc", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"click","target":"button","payload":"","observation":"trying a button"}',
    model_resolved: "fake", prompt_sha256: "z".repeat(64),
    tokens_in: 50, tokens_out: 20, latency_ms: 200,
  });
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        screenshot: async () => { throw new Error("screenshot boom"); },
        evaluate: async (scriptOrExpr) => {
          if (typeof scriptOrExpr === "function") {
            // affordances scan
            return 'button: "CHECK ANSWER" [disabled]\ntextarea: "R code"';
          }
          if (typeof scriptOrExpr === "string" && scriptOrExpr.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        click: async () => {},
        fill: async () => {},
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1",
    schemaPath,
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    maxCallsPerSession: 1,
    outputDir: tmp,
    sessionId: "test-y-2",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
    piggybackZ: true,
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Y/);
  assert.match(text, /piggyback failed/);
});

test("runStandin breaks out of an action loop instead of burning all calls", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  // Always returns the SAME click action — a stuck loop.
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"click","target":"locked card","payload":"","observation":"trying to unlock"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async (scriptOrFn) => {
          if (typeof scriptOrFn === "function") return 'button: "CHECK ANSWER" [disabled]';
          if (typeof scriptOrFn === "string" && scriptOrFn.startsWith("document.body")) return "page text";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        click: async () => {},
        fill: async () => {},
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1",
    schemaPath,
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    maxCallsPerSession: 20,        // generous cap — loop-detection should stop us WELL before this
    outputDir: tmp,
    sessionId: "test-y-3",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
    piggybackZ: false,
  });
  // 3 identical clicks trip the detector — fakeCallLlm passes the gate in 1 call
  // each iteration, so the loop breaks at exactly 3 (not the 20-call cap).
  assert.equal(callsMade, 3, `loop-detection should break at exactly 3 calls, got ${callsMade}`);
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /stuck/);
});

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

test("controller error steps do not pollute the Discovered unknown-unknowns section", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":"ready"}',
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
        getByRole: () => ({ count: async () => 0, click: async () => {} }),  // 0 matches → submit_failed error step
        click: async () => {}, fill: async () => {}, close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 10, outputDir: tmp, sessionId: "test-y-errorfilter",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  const doc = readFileSync(docPath, "utf8");
  // the error IS recorded in the full transcript table...
  assert.match(doc, /submit_failed/);
  // ...but must NOT appear in the persona-curated "Discovered unknown-unknowns" bullet section
  const uuSection = doc.split("## Discovered unknown-unknowns")[1].split("\n## ")[0];
  assert.doesNotMatch(uuSection, /submit_failed/);
});

test("DRAFT-Y emits tripwire_status: clean for a give_up-only short run", async () => {
  // Mirrors the very first test's shape: persona immediately gives up, so the
  // run is 1 step long — below min_steps_for_zero_confusion_flag (6) and never
  // submits, so neither tripwire path fires → status MUST be `clean`.
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"give_up","target":"","payload":"","observation":"stuck"}',
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
        click: async () => {}, fill: async () => {}, close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1", schemaPath, browser: fakeBrowser, callLlm: fakeCallLlm,
    maxCallsPerSession: 3, outputDir: tmp, sessionId: "test-y-tripwire-clean",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /tripwire_status: clean/);
  assert.match(text, /## Behavioral-competence tripwire/);
  assert.match(text, /\*\*Status:\*\* clean/);
});

test("DRAFT-Y emits tripwire_status: suspect for an instant-submit hyper-competent run", async () => {
  // The persona submits on step 1 with no prior friction → Path B fires
  // (submit at step <=3 with no ask_sidekick/give_up/error) → status MUST be `suspect`.
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1", "subject: PS", "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  // Empty observation → no friction findings surfaced. Per council 1778881175
  // first-principles refinement: suspect = competent-shape AND zero findings.
  // A submit with `observation: "ready"` would count as a finding (any non-empty
  // observation outside error/stuck) and the AND-gate would clear the run.
  const fakeCallLlm = async () => ({
    text: '{"thinking":"x","action":"submit","target":"","payload":"","observation":""}',
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
    maxCallsPerSession: 1, outputDir: tmp, sessionId: "test-y-tripwire-suspect",
    baseUrl: "https://corgflix.duckdns.org", authCookie: "test", piggybackZ: false,
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /tripwire_status: suspect/);
  assert.match(text, /## Behavioral-competence tripwire/);
  assert.match(text, /\*\*Status:\*\* suspect/);
  // Rationale must reference Path B's wording.
  assert.match(text, /submit at step \d+ with no prior/);
  // Council 1778881175 frontmatter additions: findings_count + confidence_band.
  assert.match(text, /^tripwire_findings_count: 0$/m);
  assert.match(text, /^tripwire_confidence_band: thin_corpus_n\d+$/m);
});

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

// --- Task 7: --fallback-models parsing + :free-band enforcement ---

test("parseFallbackModels: empty string returns []", () => {
  assert.deepEqual(parseFallbackModels(""), []);
});

test("parseFallbackModels: valid :free csv returns array", () => {
  assert.deepEqual(
    parseFallbackModels("openai/gpt-oss-120b:free,deepseek/deepseek-chat:free"),
    ["openai/gpt-oss-120b:free", "deepseek/deepseek-chat:free"],
  );
});

test("parseFallbackModels: rejects non-:free entry with clear error", () => {
  assert.throws(
    () => parseFallbackModels("anthropic/claude-opus-4-7,openai/gpt-oss-120b:free"),
    /fallback list contains non-:free model: anthropic\/claude-opus-4-7/i,
  );
});

test("parseFallbackModels: trims whitespace", () => {
  assert.deepEqual(
    parseFallbackModels(" openai/gpt-oss-120b:free , x/y:free "),
    ["openai/gpt-oss-120b:free", "x/y:free"],
  );
});

// --- Task 7: callLlmWithFallback routing ---

test("callLlmWithFallback: succeeds on primary when primary succeeds", async () => {
  const calls = [];
  const fakeCallLlm = async ({ model }) => {
    calls.push(model);
    return { text: "ok", model_resolved: model, prompt_sha256: "x", tokens_in: 0, tokens_out: 0, latency_ms: 1 };
  };
  const r = await callLlmWithFallback({
    primary: "openai/gpt-oss-120b:free",
    fallbacks: ["other:free"],
    callLlm: fakeCallLlm,
    apiKey: "k", systemPrompt: "s", userPrompt: "u",
  });
  assert.equal(r.model_resolved, "openai/gpt-oss-120b:free");
  assert.deepEqual(calls, ["openai/gpt-oss-120b:free"]);
});

test("callLlmWithFallback: advances on daily-quota error from primary", async () => {
  const fakeCallLlm = async ({ model }) => {
    if (model === "primary:free") throw new Error("free-models-per-day exceeded");
    return { text: "ok", model_resolved: model, prompt_sha256: "x", tokens_in: 0, tokens_out: 0, latency_ms: 1 };
  };
  const r = await callLlmWithFallback({
    primary: "primary:free",
    fallbacks: ["fallback1:free"],
    callLlm: fakeCallLlm,
    apiKey: "k", systemPrompt: "s", userPrompt: "u",
  });
  assert.equal(r.model_resolved, "fallback1:free");
});

test("callLlmWithFallback: all models failing surfaces final error", async () => {
  const fakeCallLlm = async () => { throw new Error("free-models-per-day exhausted"); };
  await assert.rejects(
    () => callLlmWithFallback({
      primary: "p:free",
      fallbacks: ["f1:free", "f2:free"],
      callLlm: fakeCallLlm,
      apiKey: "k", systemPrompt: "s", userPrompt: "u",
    }),
    /all models failed|exhausted/i,
  );
});
