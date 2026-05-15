# Surface Claude-CLI Provider + Y Naivety-Hardening Implementation Plan (Spec B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Spec B in two halves: (1) a reusable `tools/lib/claude-cli.mjs` provider that Surface X + Surface Z + future capability-wanted tools can opt into; (2) Surface Y naivety-hardening — pinned model + `:free`-band-only fallback list + behavioral-competence tripwire postprocessor. The CLI provider is STRUCTURALLY INACCESSIBLE to Y's persona (per council `1778839098`).

**Architecture:** `tools/lib/claude-cli.mjs` mirrors `tools/lib/openrouter.mjs`'s `callLlm({...}) → {text, model_resolved, ...}` API exactly. Surface X+Z gain a `--provider` flag that routes between the two providers (default openrouter). Surface Y gains a `--fallback-models` CSV flag, parsed/validated to `:free`-band-only (loud exit on violation). A new `tools/surface-y-tripwire.mjs` is a pure transcript postprocessor; `surface-y.mjs` calls it after the run loop and embeds the result in DRAFT-Y frontmatter + body. `tools/lib/provenance.mjs` gains a `provider_name` field that flows through `getStamp()`.

**Tech Stack:** Node.js ESM (`.mjs`), `node --test`, `child_process.spawn` (injectable as `spawnImpl` for tests), `fetch` (already injectable as `transport` in openrouter.mjs).

**Spec:** `docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md` (commit `4832be2`).

**Human-in-the-loop note:** None. Acceptance run at Task 8 is live but autonomous; if `claude --print` surfaces an unexpected error shape, the task pauses with a clear log line for the next session to inspect, but does not block other tasks.

**TDD discipline:** Every task is RED → GREEN → REFACTOR. Write the failing test, run it, see it fail, write minimal code, run again, see it pass, refactor with tests green.

---

## File structure

- **New:** `tools/lib/claude-cli.mjs` — `callLlm({apiKey, model, systemPrompt, userPrompt, ...}) → {text, model_resolved, prompt_sha256, tokens_in, tokens_out, latency_ms, cli_version}`.
- **New:** `tools/lib/claude-cli.test.mjs` — node `--test` suite, mocked `spawnImpl`.
- **New:** `tools/surface-y-tripwire.mjs` — `flagSuspectRun(transcript) → {suspect, signals, thresholds, rationale}`.
- **New:** `tools/surface-y-tripwire.test.mjs` — fixtures: authentic-naive transcript (clean) + synthetic hyper-competent transcript (suspect).
- **Modify:** `tools/lib/provenance.mjs` — `getStamp()` gains `provider_name` field (default `null`); callsites pass it through.
- **Modify:** `tools/surface-x.mjs` — new `--provider=openrouter|claude-cli` flag; default openrouter. Wires the right `callLlm` based on the flag. Records `provider_name` in DRAFT-X.md frontmatter.
- **Modify:** `tools/surface-x.test.mjs` — provider-routing tests.
- **Modify:** `tools/surface-z.mjs` — same `--provider` flag shape as X.
- **Modify:** `tools/surface-z.test.mjs` — provider-routing tests.
- **Modify:** `tools/surface-y.mjs` — `--fallback-models` CSV flag with `:free`-band-only validation; wrap the existing `callLlm` to advance through the fallback list on non-retryable failures; call `flagSuspectRun` at end of run and embed result in DRAFT-Y output. Provider remains hard-wired to OpenRouter.
- **Modify:** `tools/surface-y.test.mjs` — fallback-routing tests; tripwire-embedding tests.
- **Unchanged:** `tools/package.json` (existing `test:tools` already discovers `*.test.mjs` via `node --test`).

---

### Task 1: Build `tools/lib/claude-cli.mjs` — subprocess provider mirroring openrouter.mjs

**Files:**
- New: `tools/lib/claude-cli.mjs`
- New: `tools/lib/claude-cli.test.mjs`

The provider must accept the same call shape as `openrouter.mjs` and return the same shape, with one extra field (`cli_version`) recorded for provenance. `child_process.spawn` is injectable via a `spawnImpl` option so tests do not invoke the real binary.

- [ ] **Step 1: Write failing tests**

In `tools/lib/claude-cli.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { callLlm } from "./claude-cli.mjs";

function makeMockChild({ stdout = "", stderr = "", exitCode = 0, signalKill = null } = {}) {
  const listeners = { stdout: [], stderr: [], close: [], error: [] };
  return {
    stdout: { on: (e, fn) => listeners.stdout.push(fn) },
    stderr: { on: (e, fn) => listeners.stderr.push(fn) },
    stdin: { write: () => {}, end: () => {
      // Fire stdout/stderr/close after stdin closes — order matches real subprocess lifecycle.
      queueMicrotask(() => {
        if (stdout) listeners.stdout.forEach(fn => fn(Buffer.from(stdout)));
        if (stderr) listeners.stderr.forEach(fn => fn(Buffer.from(stderr)));
        listeners.close.forEach(fn => fn(exitCode, signalKill));
      });
    } },
    on: (event, fn) => { if (event === "close") listeners.close.push(fn); if (event === "error") listeners.error.push(fn); },
    kill: () => {},
  };
}

test("callLlm: returns {text, model_resolved, ...} on successful CLI exit 0", async () => {
  const r = await callLlm({
    systemPrompt: "sys",
    userPrompt: "hi",
    model: "claude-opus-4-7",
    spawnImpl: () => makeMockChild({ stdout: "hello world" }),
    versionImpl: () => "2.1.141",
  });
  assert.equal(r.text, "hello world");
  assert.equal(r.model_resolved, "claude-opus-4-7");
  assert.equal(r.cli_version, "2.1.141");
  assert.equal(typeof r.prompt_sha256, "string");
  assert.equal(r.prompt_sha256.length, 64);
  assert.equal(typeof r.latency_ms, "number");
  assert.equal(r.tokens_in, null);
  assert.equal(r.tokens_out, null);
});

test("callLlm: falls back model_resolved to claude-cli@<version> when no model flag was passed", async () => {
  const r = await callLlm({
    systemPrompt: "sys",
    userPrompt: "hi",
    spawnImpl: () => makeMockChild({ stdout: "ok" }),
    versionImpl: () => "2.1.141",
  });
  assert.equal(r.model_resolved, "claude-cli@2.1.141");
});

test("callLlm: throws on non-zero exit code with stderr in error message", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ stdout: "", stderr: "rate-limit exceeded", exitCode: 1 }),
      versionImpl: () => "2.1.141",
    }),
    /callLlm: claude CLI exit 1.*rate-limit/,
  );
});

test("callLlm: throws on timeout (signal kill)", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ exitCode: null, signalKill: "SIGTERM" }),
      versionImpl: () => "2.1.141",
    }),
    /timeout|SIGTERM/i,
  );
});

test("callLlm: rejects when CLAUDE_CLI_BIN does not resolve (versionImpl throws)", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ stdout: "ok" }),
      versionImpl: () => { throw new Error("ENOENT"); },
    }),
    /claude.*not.*found|ENOENT/i,
  );
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test lib/claude-cli.test.mjs`
Expected: FAIL — `claude-cli.mjs` does not exist.

- [ ] **Step 3: Write implementation**

Create `tools/lib/claude-cli.mjs`:

```js
import { createHash } from "node:crypto";
import { spawn as nodeSpawn } from "node:child_process";
import { execFileSync } from "node:child_process";

function defaultVersionImpl(bin) {
  // execFileSync throws if binary missing — propagated as "claude CLI not found"
  const out = execFileSync(bin, ["--version"], { encoding: "utf8" });
  const m = out.match(/(\d+\.\d+\.\d+)/);
  return m ? m[1] : out.trim();
}

export async function callLlm({
  apiKey = null,                 // ignored — CLI uses subscription pool
  model = null,                  // optional --model passthrough
  systemPrompt,
  userPrompt,
  temperature = null,            // ignored by CLI; preserved in API shape
  seed = null,                   // ignored
  maxTokens = null,              // ignored
  bin = process.env.CLAUDE_CLI_BIN || "claude",
  timeoutMs = Number(process.env.CLAUDE_CLI_TIMEOUT_MS) || 120000,
  spawnImpl = nodeSpawn,
  versionImpl = defaultVersionImpl,
}) {
  let cli_version;
  try {
    cli_version = versionImpl(bin);
  } catch (e) {
    throw new Error(`callLlm: claude CLI not found (${bin}): ${e.message}`);
  }

  const t0 = Date.now();
  const prompt_sha256 = createHash("sha256")
    .update(systemPrompt + "\n---\n" + userPrompt)
    .digest("hex");

  const args = ["--print"];
  if (model) args.push("--model", model);
  // System+user join — Claude CLI --print takes prompt from stdin or arg;
  // we use stdin to avoid arg-size limits and shell escaping.
  const fullPrompt = `${systemPrompt}\n\n${userPrompt}`;

  const child = spawnImpl(bin, args, { stdio: ["pipe", "pipe", "pipe"] });
  let stdoutBuf = "", stderrBuf = "";
  child.stdout.on("data", (d) => { stdoutBuf += d.toString(); });
  child.stderr.on("data", (d) => { stderrBuf += d.toString(); });
  child.stdin.write(fullPrompt);
  child.stdin.end();

  const timer = setTimeout(() => { try { child.kill("SIGTERM"); } catch {} }, timeoutMs);
  const [exitCode, signal] = await new Promise((resolve) => {
    child.on("close", (code, sig) => resolve([code, sig]));
    child.on("error", (err) => resolve([null, err.message]));
  });
  clearTimeout(timer);

  if (signal === "SIGTERM" || exitCode === null) {
    throw new Error(`callLlm: claude CLI timeout (${timeoutMs}ms) or signal ${signal}`);
  }
  if (exitCode !== 0) {
    throw new Error(`callLlm: claude CLI exit ${exitCode} — ${stderrBuf.slice(0, 200) || "(no stderr)"}`);
  }

  return {
    text: stdoutBuf,
    model_resolved: model ?? `claude-cli@${cli_version}`,
    prompt_sha256,
    tokens_in: null,
    tokens_out: null,
    latency_ms: Date.now() - t0,
    cli_version,
  };
}
```

- [ ] **Step 4: Run tests, verify GREEN**

Run: `cd tools && node --test lib/claude-cli.test.mjs`

- [ ] **Step 5: Refactor pass**

Walk the diff once. If anything reads awkward, fix it without changing test outcomes. Commit.

**Commit message:**
```
feat(provider): tools/lib/claude-cli.mjs — subprocess provider mirroring openrouter.mjs

callLlm({...}) accepts the same shape as lib/openrouter.mjs and returns
{text, model_resolved, prompt_sha256, tokens_in, tokens_out, latency_ms,
cli_version}. spawnImpl + versionImpl injectable for testability.
Stdin-piped prompt avoids arg-size limits + shell escaping. SIGTERM
timeout (default CLAUDE_CLI_TIMEOUT_MS=120000) caps subscription-pool
blast radius. apiKey/temperature/seed/maxTokens accepted for API
parity with openrouter.mjs but ignored (CLI doesn't accept them).

Spec: docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md
```

---

### Task 2: Modify `tools/lib/provenance.mjs` — `getStamp()` gains `provider_name` field

**Files:**
- Modify: `tools/lib/provenance.mjs`
- Modify: `tools/lib/provenance.test.mjs` (if exists; create if not)

Pure additive — old callsites that don't pass `provider_name` get `null`. No callsite break.

- [ ] **Step 1: Inspect the existing `getStamp` signature.** Run `grep -n "export.*getStamp\|provider_name" tools/lib/provenance.mjs`. If `provenance.test.mjs` doesn't exist, create the test file with a smoke test for the existing return shape first.

- [ ] **Step 2: Write failing test (in `tools/lib/provenance.test.mjs`)**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { getStamp } from "./provenance.mjs";

test("getStamp: provider_name defaults to null when not provided", async () => {
  const s = await getStamp(null, {});
  assert.equal(s.provider_name, null);
});

test("getStamp: provider_name flows through from overrides", async () => {
  const s = await getStamp(null, { provider_name: "claude-cli" });
  assert.equal(s.provider_name, "claude-cli");
});
```

- [ ] **Step 3: Run, see fail.**

- [ ] **Step 4: Implementation.**

In `tools/lib/provenance.mjs`, find `getStamp` and the existing return object. Add `provider_name` to the destructured `overrides` parameter and to the returned object (default `null`).

- [ ] **Step 5: Run tests, verify GREEN.**

- [ ] **Step 6: Commit.**

```
feat(provenance): getStamp() emits provider_name field for X/Y/Z DRAFTs

Default null; X+Z populate when --provider is set; Y populates with
"openrouter" since its provider is structurally hard-wired.
```

---

### Task 3: Wire `--provider` flag into `surface-x.mjs`

**Files:**
- Modify: `tools/surface-x.mjs`
- Modify: `tools/surface-x.test.mjs`

Add a `--provider=openrouter|claude-cli` CLI flag. Default `openrouter` (zero-behavior-change for existing flow). Routes `callLlm` to the right provider. Records `provider_name` via `getStamp` overrides.

- [ ] **Step 1: Inspect `surface-x.mjs`.** Identify the existing `callLlm` import + the CLI arg parsing. Find where `getStamp` is called.

- [ ] **Step 2: Write failing tests in `tools/surface-x.test.mjs`**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
// Import the new provider-resolution helper (to be added to surface-x.mjs)
import { resolveProvider } from "./surface-x.mjs";

test("resolveProvider: default returns openrouter callLlm", () => {
  const p = resolveProvider({ provider: "openrouter" });
  assert.equal(p.name, "openrouter");
  assert.equal(typeof p.callLlm, "function");
});

test("resolveProvider: 'claude-cli' returns the CLI callLlm", () => {
  const p = resolveProvider({ provider: "claude-cli" });
  assert.equal(p.name, "claude-cli");
  assert.equal(typeof p.callLlm, "function");
});

test("resolveProvider: invalid value throws with clear error", () => {
  assert.throws(
    () => resolveProvider({ provider: "groq" }),
    /unknown provider: groq.*openrouter|claude-cli/i,
  );
});
```

- [ ] **Step 3: Run, fail.**

- [ ] **Step 4: Implementation in `tools/surface-x.mjs`.**

Add near top:
```js
import { callLlm as openrouterCallLlm } from "./lib/openrouter.mjs";
import { callLlm as claudeCliCallLlm } from "./lib/claude-cli.mjs";

export function resolveProvider({ provider = "openrouter" } = {}) {
  if (provider === "openrouter") return { name: "openrouter", callLlm: openrouterCallLlm };
  if (provider === "claude-cli") return { name: "claude-cli", callLlm: claudeCliCallLlm };
  throw new Error(`unknown provider: ${provider} (expected: openrouter | claude-cli)`);
}
```

In the CLI entrypoint at the bottom of `surface-x.mjs`, parse `--provider` from `args`, default `"openrouter"`. Call `resolveProvider({provider})` and use `provider.callLlm` where the existing code calls the imported `callLlm`. Pass `provider_name: provider.name` into `getStamp({...})` overrides.

In the help text / CLI usage line, add `[--provider=openrouter|claude-cli]`.

- [ ] **Step 5: Run all tools tests** (`npm run test:tools` from repo root). Existing X tests must stay green; new tests must pass.

- [ ] **Step 6: Commit.**

```
feat(surface-x): --provider=openrouter|claude-cli flag (default openrouter)

Opt-in routing to lib/claude-cli.mjs; default unchanged so existing
runs are not disturbed. provider_name flows into DRAFT-X.md frontmatter
via getStamp() overrides.
```

---

### Task 4: Wire `--provider` flag into `surface-z.mjs`

**Files:**
- Modify: `tools/surface-z.mjs`
- Modify: `tools/surface-z.test.mjs`

Same shape as Task 3. Copy/paste the `resolveProvider` helper (or extract to `tools/lib/provider-resolver.mjs` if both X and Z would benefit — but YAGNI; copy-paste of a 6-line function across two files is cheaper than a shared util right now).

- [ ] **Step 1: Inspect `surface-z.mjs`** — confirm import shape + CLI parsing matches X's pattern.
- [ ] **Step 2: Write failing tests** — same 3 tests as Task 3, imported from `surface-z.mjs`.
- [ ] **Step 3: Run, fail.**
- [ ] **Step 4: Implementation — `resolveProvider` + CLI wiring + `provider_name` in `getStamp`.**
- [ ] **Step 5: Tests green.**
- [ ] **Step 6: Commit.**

```
feat(surface-z): --provider=openrouter|claude-cli flag (default openrouter)

Same shape as Surface X (Spec B Task 3). resolveProvider copy-pasted
locally per YAGNI — a shared util is a future move when a 3rd surface
needs it.
```

---

### Task 5: Build `tools/surface-y-tripwire.mjs` — behavioral-competence postprocessor

**Files:**
- New: `tools/surface-y-tripwire.mjs`
- New: `tools/surface-y-tripwire.test.mjs`

Pure function: `flagSuspectRun(transcript) → { suspect: bool, signals: {...}, thresholds: {...}, rationale: string[] }`. Per the spec's tripwire section.

- [ ] **Step 1: Write failing tests with two fixture transcripts.**

In `tools/surface-y-tripwire.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { flagSuspectRun } from "./surface-y-tripwire.mjs";

// Authentic-naive: mirrors the gpt-oss-120b prior run shape — confusion present,
// ask_sidekick used, types incrementally, never submits.
const AUTHENTIC_NAIVE = [
  { action: "click", target: "button:nth-of-type(2)", observation: "I'm confused about which task to pick" },
  { action: "ask_sidekick", target: "", payload: "what is rlaplace?", observation: "I don't know this function" },
  { action: "type", target: "textarea[placeholder=\"R code here\"]", payload: "x <-", observation: "trying something" },
  { action: "type", target: "textarea[placeholder=\"R code here\"]", payload: "x <- rlaplace(", observation: "still unsure" },
  { action: "type", target: "textarea[placeholder=\"R code here\"]", payload: "x <- rlaplace(1000)", observation: "[LEAK?] this might be wrong" },
  { action: "stuck", target: "submit", observation: "Gave up — repeated type 3x" },
];

// Synthetic Claude-style: zero confusion, no ask_sidekick, submit at step 2 with full clean R.
const HYPER_COMPETENT = [
  { action: "click", target: "button:nth-of-type(2)", observation: "" },
  { action: "type", target: "textarea[placeholder=\"R code here\"]", payload: "rlaplace(n=1000, location=0, scale=1)", observation: "ready" },
  { action: "submit", target: "", observation: "" },
];

test("flagSuspectRun: authentic-naive transcript → clean", () => {
  const r = flagSuspectRun(AUTHENTIC_NAIVE);
  assert.equal(r.suspect, false);
  assert.ok(r.signals.ask_sidekick_count >= 1);
  assert.ok(r.signals.confusion_step_count >= 1);
});

test("flagSuspectRun: hyper-competent transcript → suspect", () => {
  const r = flagSuspectRun(HYPER_COMPETENT);
  assert.equal(r.suspect, true);
  assert.ok(r.rationale.length > 0);
  assert.equal(r.signals.ask_sidekick_count, 0);
  assert.equal(r.signals.confusion_step_count, 0);
});

test("flagSuspectRun: short clean run (< 6 steps, no submit) → clean (insufficient data)", () => {
  const SHORT = [
    { action: "click", target: "x", observation: "" },
    { action: "click", target: "y", observation: "" },
  ];
  const r = flagSuspectRun(SHORT);
  assert.equal(r.suspect, false);
});

test("flagSuspectRun: empty transcript → clean (no-op)", () => {
  const r = flagSuspectRun([]);
  assert.equal(r.suspect, false);
});

test("flagSuspectRun: thresholds object is included in output for audit", () => {
  const r = flagSuspectRun(AUTHENTIC_NAIVE);
  assert.equal(typeof r.thresholds, "object");
  assert.equal(typeof r.thresholds.min_steps_for_zero_confusion_flag, "number");
});
```

- [ ] **Step 2: Run, fail.**

- [ ] **Step 3: Implement.**

```js
// tools/surface-y-tripwire.mjs
//
// Behavioral-competence tripwire — postprocesses a Surface Y transcript and
// flags runs where the persona shows hyper-competence (no confusion, no
// sidekick use, first-try-flawless submit). Diagnostic only — never blocks.
//
// Spec: docs/superpowers/specs/2026-05-15-surface-claude-cli-provider-design.md
// Council: .claude/council-cache/council-1778839098-y-competence-band.md

const CONFUSION_KEYWORDS = ["confused", "don't know", "dont know", "unclear", "[leak?]", "stuck", "no idea"];

const DEFAULT_THRESHOLDS = {
  min_steps_for_zero_confusion_flag: 6,
  submit_step_index_max_for_flawless_flag: 3,
};

export function flagSuspectRun(transcript, thresholds = DEFAULT_THRESHOLDS) {
  const signals = computeSignals(transcript);
  const rationale = [];

  // Path A: zero-confusion AND zero-ask_sidekick across a long run.
  if (
    transcript.length >= thresholds.min_steps_for_zero_confusion_flag &&
    signals.ask_sidekick_count === 0 &&
    signals.confusion_step_count === 0
  ) {
    rationale.push(
      `Run ran ${transcript.length} steps with zero ask_sidekick AND zero confusion-keyword observations — ` +
      `a real naive student would have used at least one.`,
    );
  }

  // Path B: first submit at step <= N with no prior friction.
  const submitIdx = transcript.findIndex(t => t.action === "submit");
  if (
    submitIdx !== -1 &&
    submitIdx <= thresholds.submit_step_index_max_for_flawless_flag &&
    transcript.slice(0, submitIdx + 1).every(t =>
      t.action !== "ask_sidekick" && t.action !== "give_up" && !t.error
    )
  ) {
    rationale.push(
      `submit at step ${submitIdx + 1} with no prior ask_sidekick, give_up, or error — ` +
      `model produced a clean first-try answer.`,
    );
  }

  return {
    suspect: rationale.length > 0,
    signals,
    thresholds,
    rationale,
  };
}

function computeSignals(transcript) {
  const ask_sidekick_count = transcript.filter(t => t.action === "ask_sidekick").length;
  const confusion_step_count = transcript.filter(t => {
    const obs = (t.observation || "").toLowerCase();
    return (
      CONFUSION_KEYWORDS.some(k => obs.includes(k)) ||
      t.action === "give_up" ||
      t.action === "ask_sidekick"
    );
  }).length;
  return { ask_sidekick_count, confusion_step_count };
}
```

- [ ] **Step 4: Run, GREEN.**

- [ ] **Step 5: Refactor + commit.**

```
feat(surface-y): tripwire postprocessor — flagSuspectRun(transcript)

Behavioral-competence tripwire per council 1778839098. Pure function;
diagnostic only; never blocks. Two suspect paths:
- zero ask_sidekick + zero confusion across >=6 steps (silent glide)
- submit at step <=3 with no prior friction (first-try flawless)

Thresholds object returned for audit. Fixtures: gpt-oss-120b-shaped
authentic-naive (clean) vs synthetic Claude-style (suspect).
```

---

### Task 6: Wire tripwire into `surface-y.mjs` — frontmatter + body section

**Files:**
- Modify: `tools/surface-y.mjs`
- Modify: `tools/surface-y.test.mjs`

After the run loop completes, `surface-y.mjs` calls `flagSuspectRun(transcript)` and embeds the result in DRAFT-Y output: a `tripwire_status: clean|suspect` line in frontmatter + a `## Behavioral-competence tripwire` section in the body with signals + rationale.

- [ ] **Step 1: Inspect `surface-y.mjs` DRAFT-Y emission block** (`writeFileSync(docPath, md)` near line 326).

- [ ] **Step 2: Write failing test** — invoke `runStandin` with a mocked `callLlm` that returns a synthetic hyper-competent transcript shape, then `readFileSync` the emitted DRAFT and assert it contains `tripwire_status: suspect` AND a `## Behavioral-competence tripwire` heading. (Mock the page object via passing in `browser` with a fake context — leverage existing test patterns in surface-y.test.mjs.)

If the existing tests don't already mock the browser, an alternative cleaner test is a unit test on a smaller helper: extract the "build DRAFT-Y markdown" logic into a `buildFindingDoc({transcript, signals, ...})` function and test it in isolation. Decide based on existing test scaffolding.

- [ ] **Step 3: Run, fail.**

- [ ] **Step 4: Implementation.**

Import the tripwire:
```js
import { flagSuspectRun } from "./surface-y-tripwire.mjs";
```

After the `while` loop completes (around line 265), call:
```js
const tripwire = flagSuspectRun(transcript);
```

In the frontmatter array (around line 274), add:
```js
`tripwire_status: ${tripwire.suspect ? "suspect" : "clean"}`,
```

After the existing "## Session transcript" block, before the "## Affordances shown" block, add:
```js
"",
"## Behavioral-competence tripwire",
`**Status:** ${tripwire.suspect ? "suspect" : "clean"}`,
"",
"**Signals:**",
`- ask_sidekick_count: ${tripwire.signals.ask_sidekick_count}`,
`- confusion_step_count: ${tripwire.signals.confusion_step_count}`,
"",
"**Thresholds:**",
...Object.entries(tripwire.thresholds).map(([k, v]) => `- ${k}: ${v}`),
"",
"**Rationale:**",
...(tripwire.rationale.length ? tripwire.rationale.map(r => `- ${r}`) : ["- (none — run is clean)"]),
```

Pass `provider_name: "openrouter"` into `getStamp({...})` overrides (Y is hard-wired to OpenRouter per spec).

- [ ] **Step 5: Run all tools tests, verify GREEN.**

- [ ] **Step 6: Commit.**

```
feat(surface-y): embed tripwire status + signals in DRAFT-Y output

Calls flagSuspectRun(transcript) after the run loop. Adds
tripwire_status: clean|suspect to frontmatter and a
## Behavioral-competence tripwire section to the body with signals,
thresholds, rationale.
```

---

### Task 7: Add `--fallback-models` to `surface-y.mjs` with `:free`-band enforcement

**Files:**
- Modify: `tools/surface-y.mjs`
- Modify: `tools/surface-y.test.mjs`

New `--fallback-models=<csv>` CLI flag. The CSV is parsed, each entry is validated to end in `:free` (loud exit if not). When the primary model fails non-retryably (daily-quota 429 OR any HTTP 5xx after retries exhausted), the wrapper advances to the next model.

- [ ] **Step 1: Write failing tests.**

```js
import { parseFallbackModels } from "./surface-y.mjs";

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
```

Also test the fallback routing: `callLlmWithFallback({primary, fallbacks, callLlm})` advances on `Error("free-models-per-day")` and surfaces the final error if all models fail.

```js
test("callLlmWithFallback: succeeds on primary when primary succeeds", async () => {
  const { callLlmWithFallback } = await import("./surface-y.mjs");
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
  const { callLlmWithFallback } = await import("./surface-y.mjs");
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
  const { callLlmWithFallback } = await import("./surface-y.mjs");
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
```

- [ ] **Step 2: Run, fail.**

- [ ] **Step 3: Implementation.**

In `tools/surface-y.mjs`:

```js
export function parseFallbackModels(csv) {
  if (!csv || !csv.trim()) return [];
  const entries = csv.split(",").map(s => s.trim()).filter(Boolean);
  for (const e of entries) {
    if (!e.endsWith(":free")) {
      throw new Error(
        `fallback list contains non-:free model: ${e} — only :free band allowed (council 1778839098)`,
      );
    }
  }
  return entries;
}

const DAILY_QUOTA_MARKERS = ["free-models-per-day", "daily limit", "quota exhausted"];

function isDailyQuotaError(err) {
  const msg = (err?.message || "").toLowerCase();
  return DAILY_QUOTA_MARKERS.some(m => msg.includes(m));
}

export async function callLlmWithFallback({ primary, fallbacks, callLlm, ...callArgs }) {
  const models = [primary, ...fallbacks];
  let lastErr;
  for (const model of models) {
    try {
      return await callLlm({ ...callArgs, model });
    } catch (e) {
      lastErr = e;
      if (!isDailyQuotaError(e) && !(e.message || "").includes("503") && !(e.message || "").includes("502")) {
        throw e; // non-retryable — surface immediately
      }
      // fallthrough: try next model
    }
  }
  throw new Error(`all models failed (last error: ${lastErr?.message || "(unknown)"})`);
}
```

In `runStandin`, wrap the existing `callLlm` to use `callLlmWithFallback` when `fallbackModels` is non-empty. Add `fallbackModels` parameter to `runStandin`:

```js
export async function runStandin({
  ...
  model = "openai/gpt-oss-120b:free",
  fallbackModels = [],
  ...
}) {
  ...
  const effectiveCallLlm = fallbackModels.length > 0
    ? (args) => callLlmWithFallback({ primary: args.model ?? model, fallbacks: fallbackModels, callLlm, ...args })
    : callLlm;
  ...
  // Use effectiveCallLlm everywhere the existing code uses callLlm directly
}
```

In CLI entrypoint, parse `--fallback-models`:

```js
const fallbackModels = parseFallbackModels(args["fallback-models"] || "");
...
runStandin({ ..., fallbackModels })
```

Usage line update: `[--fallback-models=<csv-of-:free-models>]`.

- [ ] **Step 4: Run all tests, verify GREEN.**

- [ ] **Step 5: Commit.**

```
feat(surface-y): --fallback-models CSV flag, :free-band-only enforced

parseFallbackModels rejects any entry not ending in :free (loud exit
per council 1778839098: NEVER up-band to Claude). callLlmWithFallback
advances on daily-quota / 5xx errors; surfaces non-retryable errors
immediately. Default behavior unchanged when flag omitted.
```

---

### Task 8: Live verification — feature-shipped gate

**Files:** None (operational).

Per the load-bearing feature-shipped rule: bundle hash + tests green ≠ shipped. Live invocation is the gate.

- [ ] **Step 1: Full test suite green.** `cd .. && npm run test:tools` from repo root. Confirm 84 → ≥90+ tests, all green.

- [ ] **Step 2: Live `claude-cli` smoke probe.**

```bash
node -e "import('./tools/lib/claude-cli.mjs').then(async m => {
  const r = await m.callLlm({ systemPrompt: 'You are a calculator.', userPrompt: 'What is 2+2? Respond with just the number.' });
  console.log('text:', r.text.slice(0, 80));
  console.log('cli_version:', r.cli_version);
  console.log('latency_ms:', r.latency_ms);
})"
```

Expected: prints "4" (or similar terse answer), version 2.1.141, latency_ms.

- [ ] **Step 3: Live Surface Y burn — pinned default model + tripwire active.**

```bash
node tools/surface-y.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E --schema=tools/surface-y.schema.yaml --max-calls=6
```

(`--max-calls=6` caps blast radius for the acceptance run; spec line "≥6 steps" threshold means the tripwire's zero-confusion path won't fire on a too-short run — adjust if existing schema/task pairing differs.)

Open the DRAFT-Y-*.md emitted in `docs/standin-findings/` and confirm:
- frontmatter contains `tripwire_status: clean` (or `suspect` — either is a valid finding; suspect on `:free` would itself be informative).
- body contains a `## Behavioral-competence tripwire` section with signals + thresholds + rationale.

- [ ] **Step 4: Capture acceptance evidence.**

Write `docs/standin-findings/spec-b-acceptance-2026-05-15.md` summarizing:
- Live `claude-cli` smoke probe output.
- Live Y run summary (`tripwire_status`, signal values, transcript length).
- Live `npm run test:tools` count + green/red.

Commit:
```
docs(spec-b): acceptance evidence — CLI provider smoke + Y tripwire live

Records the feature-shipped gate per CLAUDE.md's load-bearing rule.
```

- [ ] **Step 5: Final wrap.** All commits land on `main`. Push to origin (single-user trunk; pushing is the expected close-out).

---

## Notes for the executing subagent

- **Don't push commits until Task 8 is complete.** Live verification may surface issues that require fix-forward commits; push is a single batched action at end of plan, per past sessions' pattern.
- **If `claude --print` returns unexpected output at Task 8 smoke probe** (e.g. empty stdout but exit 0, or stderr-only), pause and log the actual shape. Fix-forward by adjusting the provider's parsing, then re-run the smoke. Do not silently swallow.
- **The fallback-list testing path needs a real `:free` candidate.** If `openai/gpt-oss-120b:free` is the only viable pinned model, the fallback list test can use a dummy `:free` entry that is expected to 404 — the test asserts the wrapper advances correctly, not that the second model succeeds.
- **Surface X/Z opt-in `--provider=claude-cli` is NOT acceptance-gated.** The default flow (openrouter) is regression-tested; the CLI flow is a follow-up live probe if time permits.
- **The behavioral tripwire's calibration corpus is n=1.** This is documented in the spec. First Y run under Spec B grows it to n=2. Do not silently retune thresholds during execution — if the live run trips `suspect` on `:free`, log it as a finding and surface to the next session.
- **`provider_name` field for Y is hard-coded "openrouter"** (Task 6, in `getStamp` overrides), NOT routed from a flag — Y's provider is structurally hard-wired per the spec's anti-features.
