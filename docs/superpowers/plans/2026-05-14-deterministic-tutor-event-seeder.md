# Deterministic Tutor-Event Seeder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A deterministic, non-LLM CLI that POSTs hardcoded attempts to `/api/v1/drill/grade` so the Surface X golden fixture has real `drill_grade` events to draw from.

**Architecture:** A single Node ESM logic file (`tools/seed-tutor-events.mjs`) reads a data fixture (`tools/seed-tutor-events.fixture.json`) holding the captured `ApiDrillGradeRequest` template + 2 hardcoded R-code attempts. It issues direct authenticated POSTs (self-issued CSRF double-submit pair, `jarvis_session` cookie from env, `X-Standin-Run: 1` header). The server grades each attempt server-side and appends a `drill_grade` event; the seeder controls *what is submitted*, not the grade.

**Tech Stack:** Node.js ESM (`.mjs`), `node --test`, `fetch` (injectable as `transport` for tests — same pattern as `tools/lib/openrouter.mjs`).

**Spec:** `docs/superpowers/specs/2026-05-14-deterministic-tutor-event-seeder-design.md` (commit `467b7b7`).

**Human-in-the-loop note:** Task 1 (devtools capture) and Task 6 (live run + read-back) require a human with browser access to the live tutor and a valid `jarvis_session` cookie. Tasks 2-5 are pure code and need neither.

---

## File Structure

- **Create:** `tools/seed-tutor-events.mjs` — the seeder logic: constants, pure builders (`buildRequest`, `buildHeaders`, `classifyOutcome`), `seedOne`, `seedAll`, and the CLI entrypoint.
- **Create:** `tools/seed-tutor-events.fixture.json` — captured `ApiDrillGradeRequest` template + the 2 attempts. Data, not logic — kept separate so the `.mjs` stays fully concrete and the fixture is trivially extendable.
- **Create:** `tools/seed-tutor-events.test.mjs` — `node --test` unit tests with an injected mock transport.
- **Modify:** `tools/package.json` — add the new test file to the `test:tools` script.

---

### Task 1: Capture the drill request template + build the fixture file

**Files:**
- Create: `tools/seed-tutor-events.fixture.json`

This task is manual data-gathering — no test, no code logic. It produces the data fixture the rest of the plan depends on.

- [ ] **Step 1: Capture a real `POST /api/v1/drill/grade` request body**

  1. Open `https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E` in a browser, logged in.
  2. Open devtools → Network tab.
  3. In the drill workspace, type anything into the R-code textarea and click **CHECK ANSWER**.
  4. In the Network tab, find the `POST /api/v1/drill/grade` request.
  5. Copy its **request payload** (the JSON body).
  6. From that JSON, record these exact field values: `problemId`, `problemStatement`, `expectedAnswerHint`, `referenceSolution`, `rubricItems`, `prediction`, `language`. (`taskId` will be `01KR6K07T6PATPRR5KH1JXYF8E`; `userAttempt` is whatever you typed — discard it, the fixture supplies its own.)
  7. While in devtools → Application → Cookies, copy the `jarvis_session` cookie value. Save it for Task 6 (the live run). Do **not** put it in the fixture file or any committed file.

- [ ] **Step 2: Write `tools/seed-tutor-events.fixture.json`**

Create the file with the captured `template` values from Step 1 and the two `attempts` below **verbatim** (the R scripts are provided — do not invent them):

```json
{
  "template": {
    "taskId": "01KR6K07T6PATPRR5KH1JXYF8E",
    "problemId": "<paste captured problemId>",
    "problemStatement": "<paste captured problemStatement>",
    "expectedAnswerHint": "<paste captured expectedAnswerHint>",
    "referenceSolution": "<paste captured referenceSolution>",
    "rubricItems": ["<paste captured rubricItems entries>"],
    "prediction": "<paste captured prediction, or null>",
    "language": "<paste captured language, e.g. r>"
  },
  "attempts": [
    {
      "label": "known-good",
      "userAttempt": "library(VGAM)\nn <- 10000\nfor (b in c(0.5, 1, 2, 4)) {\n  x <- rlaplace(n, location = 0, scale = b)\n  hist(x, breaks = 60, freq = FALSE, main = paste('Laplace b =', b), xlab = 'x')\n  curve(dlaplace(x, location = 0, scale = b), add = TRUE, col = 'red', lwd = 2)\n}"
    },
    {
      "label": "known-bad",
      "userAttempt": "n <- 10000\nx <- rnorm(n, mean = 0, sd = 1)\nhist(x, main = 'samples')"
    }
  ]
}
```

The `<paste captured ...>` slots take the **exact** values recorded in Step 1 — they are real server data, not free invention. The `known-good` attempt is a correct VGAM `rlaplace`/`dlaplace` solution with the b-loop and theoretical overlay; the `known-bad` attempt is a realistic novice error (wrong distribution `rnorm`, no b-loop, no theoretical curve).

- [ ] **Step 3: Verify the fixture parses**

Run: `node -e "console.log(JSON.parse(require('fs').readFileSync('tools/seed-tutor-events.fixture.json','utf8')).attempts.length)"`
Expected: `2`

- [ ] **Step 4: Commit**

```bash
git add tools/seed-tutor-events.fixture.json
git commit -m "feat(standin): drill-grade request fixture for the event seeder"
```

---

### Task 2: Constants + pure builders (`buildRequest`, `buildHeaders`, `classifyOutcome`)

**Files:**
- Create: `tools/seed-tutor-events.mjs`
- Create: `tools/seed-tutor-events.test.mjs`

- [ ] **Step 1: Write the failing tests**

Create `tools/seed-tutor-events.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  buildRequest, buildHeaders, classifyOutcome,
} from "./seed-tutor-events.mjs";

const FAKE_TEMPLATE = {
  taskId: "T1", problemId: "P1", problemStatement: "PS",
  expectedAnswerHint: "H", referenceSolution: "R",
  rubricItems: ["r1"], prediction: null, language: "r",
};
const FAKE_ATTEMPT = { label: "good", userAttempt: "x <- 1" };

test("buildRequest merges userAttempt into the template, leaves other fields intact", () => {
  const req = buildRequest(FAKE_TEMPLATE, FAKE_ATTEMPT);
  assert.equal(req.userAttempt, "x <- 1");
  assert.equal(req.problemId, "P1");
  assert.equal(req.taskId, "T1");
  assert.equal(req.problemStatement, "PS");
});

test("buildHeaders self-issues a matching CSRF pair + X-Standin-Run + jarvis_session cookie", () => {
  const h = buildHeaders("SESS123", "TOK");
  assert.equal(h["Content-Type"], "application/json");
  assert.equal(h["X-Standin-Run"], "1");
  assert.equal(h["X-CSRF-Token"], "TOK");
  assert.equal(h["Cookie"], "jarvis_session=SESS123; csrf=TOK");
});

test("classifyOutcome: 401 -> hard auth_error", () => {
  const c = classifyOutcome(401, null);
  assert.equal(c.outcome, "auth_error");
  assert.equal(c.hard, true);
});

test("classifyOutcome: 403 -> hard csrf_error", () => {
  const c = classifyOutcome(403, null);
  assert.equal(c.outcome, "csrf_error");
  assert.equal(c.hard, true);
});

test("classifyOutcome: 400 -> hard bad_request", () => {
  const c = classifyOutcome(400, null);
  assert.equal(c.outcome, "bad_request");
  assert.equal(c.hard, true);
});

test("classifyOutcome: other non-200 -> hard http_error", () => {
  const c = classifyOutcome(500, null);
  assert.equal(c.outcome, "http_error");
  assert.equal(c.hard, true);
});

test("classifyOutcome: 200 + misconception UNGRADED -> soft ungraded warning", () => {
  const c = classifyOutcome(200, { misconception: "UNGRADED", correct: false });
  assert.equal(c.outcome, "ungraded");
  assert.equal(c.hard, false);
});

test("classifyOutcome: 200 + real grade -> success", () => {
  const c = classifyOutcome(200, { misconception: "NONE", correct: true, score: 1.0 });
  assert.equal(c.outcome, "success");
  assert.equal(c.hard, false);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — `Cannot find module './seed-tutor-events.mjs'`

- [ ] **Step 3: Write the minimal implementation**

Create `tools/seed-tutor-events.mjs`:

```js
// tools/seed-tutor-events.mjs
//
// Deterministic, non-LLM seeder for drill_grade events. POSTs hardcoded
// attempts to POST /api/v1/drill/grade (TutorRoutes.kt:1580) so the Surface X
// golden fixture has real events to draw from. The seeder controls WHAT is
// submitted; the server grades each attempt server-side.
//
// Spec: docs/superpowers/specs/2026-05-14-deterministic-tutor-event-seeder-design.md

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

export const DEFAULT_BASE_URL = "https://corgflix.duckdns.org";
export const DEFAULT_TASK_ID = "01KR6K07T6PATPRR5KH1JXYF8E";
// Csrf.kt:8-16 is a double-submit cookie check — the server only verifies
// header("X-CSRF-Token") === cookie("csrf"), both non-blank. A first-party
// tool legitimately controls both sides, so any constant works.
export const CSRF_TOKEN = "seed-tutor-events-csrf";

export function loadFixture() {
  return JSON.parse(readFileSync(join(HERE, "seed-tutor-events.fixture.json"), "utf8"));
}

// Merge one attempt's userAttempt into the captured ApiDrillGradeRequest
// template (TutorRoutes.kt:1930-1940).
export function buildRequest(template, attempt) {
  return { ...template, userAttempt: attempt.userAttempt };
}

// jarvis_session cookie authenticates (TutorRoutes.kt:1585). X-Standin-Run:1
// tags the event is_synthetic=true (TutorRoutes.kt:1602,1686). The csrf
// cookie + X-CSRF-Token header are the self-issued double-submit pair.
export function buildHeaders(sessionCookie, csrfToken = CSRF_TOKEN) {
  return {
    "Content-Type": "application/json",
    "X-Standin-Run": "1",
    "X-CSRF-Token": csrfToken,
    "Cookie": `jarvis_session=${sessionCookie}; csrf=${csrfToken}`,
  };
}

// Map (httpStatus, reply) -> { outcome, hard, detail }. `hard` true means the
// run should exit non-zero. A 200 always means an event landed; UNGRADED is a
// soft warning (server OpenRouter was down — event still appended, status:error).
export function classifyOutcome(httpStatus, reply) {
  if (httpStatus === 401) return { outcome: "auth_error", hard: true, detail: "401 — invalid/missing jarvis_session cookie" };
  if (httpStatus === 403) return { outcome: "csrf_error", hard: true, detail: "403 — CSRF check failed (unexpected; seeder self-issues the pair)" };
  if (httpStatus === 400) return { outcome: "bad_request", hard: true, detail: "400 — payload no longer matches ApiDrillGradeRequest" };
  if (httpStatus !== 200) return { outcome: "http_error", hard: true, detail: `HTTP ${httpStatus}` };
  if (reply?.misconception === "UNGRADED") {
    return { outcome: "ungraded", hard: false, detail: "server OpenRouter unavailable — event still landed with status:error" };
  }
  return { outcome: "success", hard: false, detail: `correct=${reply?.correct} score=${reply?.score}` };
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — 8 tests passing.

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): seeder constants + pure request/header/outcome builders"
```

---

### Task 3: `seedOne()` — single authenticated POST

**Files:**
- Modify: `tools/seed-tutor-events.mjs`
- Modify: `tools/seed-tutor-events.test.mjs`

- [ ] **Step 1: Write the failing tests**

Append to `tools/seed-tutor-events.test.mjs`:

```js
import { seedOne } from "./seed-tutor-events.mjs";

test("seedOne: POSTs to the grade endpoint with the right URL, method, body, headers", async () => {
  let captured;
  const transport = async (url, opts) => {
    captured = { url, opts };
    return { status: 200, json: async () => ({ misconception: "NONE", correct: true, score: 0.9 }) };
  };
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", sessionCookie: "S", csrfToken: "TOK", transport,
  });
  assert.equal(captured.url, "https://x.test/api/v1/drill/grade");
  assert.equal(captured.opts.method, "POST");
  assert.equal(JSON.parse(captured.opts.body).userAttempt, "x <- 1");
  assert.equal(captured.opts.headers["X-Standin-Run"], "1");
  assert.equal(captured.opts.headers["Cookie"], "jarvis_session=S; csrf=TOK");
  assert.equal(r.label, "good");
  assert.equal(r.outcome, "success");
  assert.equal(r.hard, false);
});

test("seedOne: a network throw -> hard network_error", async () => {
  const transport = async () => { throw new Error("ECONNREFUSED"); };
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", sessionCookie: "S", transport,
  });
  assert.equal(r.outcome, "network_error");
  assert.equal(r.hard, true);
  assert.equal(r.httpStatus, null);
});

test("seedOne: a 401 from the server -> hard auth_error (even if body is not JSON)", async () => {
  const transport = async () => ({ status: 401, json: async () => { throw new Error("not json"); } });
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", sessionCookie: "S", transport,
  });
  assert.equal(r.outcome, "auth_error");
  assert.equal(r.hard, true);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — `seedOne` is not exported / not a function.

- [ ] **Step 3: Write the minimal implementation**

Append to `tools/seed-tutor-events.mjs` (after `classifyOutcome`):

```js
// One authenticated POST for one attempt. transport defaults to fetch and is
// injectable for tests. A thrown transport error is a hard network_error; a
// non-JSON body is tolerated (reply stays null, classifyOutcome handles it).
export async function seedOne({ template, attempt, baseUrl, sessionCookie, csrfToken = CSRF_TOKEN, transport = globalThis.fetch }) {
  const body = JSON.stringify(buildRequest(template, attempt));
  const headers = buildHeaders(sessionCookie, csrfToken);
  let httpStatus, reply = null;
  try {
    const resp = await transport(`${baseUrl}/api/v1/drill/grade`, { method: "POST", headers, body });
    httpStatus = resp.status;
    try { reply = await resp.json(); } catch { reply = null; }
  } catch (e) {
    return { label: attempt.label, httpStatus: null, reply: null, outcome: "network_error", hard: true, detail: String(e).slice(0, 160) };
  }
  const c = classifyOutcome(httpStatus, reply);
  return { label: attempt.label, httpStatus, reply, ...c };
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — 11 tests passing.

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): seedOne — single authenticated drill-grade POST"
```

---

### Task 4: `seedAll()` + CLI entrypoint

**Files:**
- Modify: `tools/seed-tutor-events.mjs`
- Modify: `tools/seed-tutor-events.test.mjs`

- [ ] **Step 1: Write the failing test**

Append to `tools/seed-tutor-events.test.mjs`:

```js
import { seedAll } from "./seed-tutor-events.mjs";

test("seedAll: loops every attempt and returns one result per attempt, in order", async () => {
  const transport = async () => ({ status: 200, json: async () => ({ misconception: "NONE", correct: true, score: 1 }) });
  const attempts = [
    { label: "a", userAttempt: "1" },
    { label: "b", userAttempt: "2" },
  ];
  const results = await seedAll({
    template: FAKE_TEMPLATE, attempts,
    baseUrl: "https://x.test", sessionCookie: "S", transport,
  });
  assert.equal(results.length, 2);
  assert.deepEqual(results.map(r => r.label), ["a", "b"]);
  assert.equal(results.every(r => r.outcome === "success"), true);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — `seedAll` is not exported / not a function.

- [ ] **Step 3: Write the implementation + CLI entrypoint**

Append to `tools/seed-tutor-events.mjs` (after `seedOne`):

```js
// Seed every attempt sequentially. Sequential, not parallel: the server's
// TutorEventLog append is the point — keep it simple and ordered.
export async function seedAll({ template, attempts, baseUrl, sessionCookie, csrfToken = CSRF_TOKEN, transport = globalThis.fetch }) {
  const results = [];
  for (const attempt of attempts) {
    results.push(await seedOne({ template, attempt, baseUrl, sessionCookie, csrfToken, transport }));
  }
  return results;
}

// CLI entrypoint — follows the tools/ convention (process.argv[1] guard +
// --key=value parsing) used in surface-z.mjs / surface-y.mjs.
if (process.argv[1]?.endsWith("seed-tutor-events.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));

  const sessionCookie = process.env.JARVIS_SESSION_COOKIE;
  if (!sessionCookie) {
    console.error("ERR: JARVIS_SESSION_COOKIE unset. Copy a valid jarvis_session cookie value from browser devtools (Application > Cookies) and export it.");
    process.exit(2);
  }

  const baseUrl = args["base-url"] ?? DEFAULT_BASE_URL;
  const taskId = args.task ?? DEFAULT_TASK_ID;
  if (taskId !== DEFAULT_TASK_ID) {
    console.error(`ERR: --task=${taskId} has no hardcoded payloads. V1 only supports ${DEFAULT_TASK_ID}.`);
    process.exit(2);
  }

  const { template, attempts } = loadFixture();

  if (args["dry-run"]) {
    for (const attempt of attempts) {
      console.log(`--- ${attempt.label} ---`);
      console.log("POST", `${baseUrl}/api/v1/drill/grade`);
      console.log("headers:", JSON.stringify(buildHeaders(sessionCookie), null, 2));
      console.log("body:", JSON.stringify(buildRequest(template, attempt), null, 2));
    }
    process.exit(0);
  }

  const results = await seedAll({ template, attempts, baseUrl, sessionCookie });
  let hardFail = false;
  for (const r of results) {
    const tag = r.hard ? "FAIL" : (r.outcome === "ungraded" ? "WARN" : "OK");
    console.log(`[${tag}] ${r.label}: ${r.outcome} — ${r.detail}`);
    if (r.hard) hardFail = true;
  }
  process.exit(hardFail ? 1 : 0);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — 12 tests passing.

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): seedAll loop + CLI entrypoint with --dry-run"
```

---

### Task 5: Wire into `package.json` `test:tools`

**Files:**
- Modify: `tools/package.json`

- [ ] **Step 1: Add the test file to the `test:tools` script**

In `tools/package.json`, the `test:tools` script currently ends with `... surface-y-gate.test.mjs surface-y.test.mjs`. Append ` seed-tutor-events.test.mjs` to the end of the file list inside that script string. The full line becomes:

```json
    "test:tools": "node --test extract-fiimaterials.test.mjs lib/openrouter.test.mjs lib/provenance.test.mjs lib/event-log-reader.test.mjs findings-stale-check.test.mjs surface-x-invariants.test.mjs surface-x.test.mjs surface-z-lints.test.mjs surface-z.test.mjs surface-y-persona.test.mjs surface-y-gate.test.mjs surface-y.test.mjs seed-tutor-events.test.mjs",
```

- [ ] **Step 2: Run the full tools test suite**

Run: `cd tools && npm run test:tools`
Expected: PASS — the prior 63 tests + the 12 new seeder tests, all green. No failures.

- [ ] **Step 3: Commit**

```bash
git add tools/package.json
git commit -m "test(standin): wire seed-tutor-events tests into test:tools"
```

---

### Task 6: Live acceptance run + read-back verification

**Files:** none (behavior-anchored acceptance — this is a CLI tool, acceptance is behavior, per the spec).

Requires the `jarvis_session` cookie value captured in Task 1, Step 1.7.

- [ ] **Step 1: Dry-run check**

Run: `cd tools && JARVIS_SESSION_COOKIE='<captured cookie>' node seed-tutor-events.mjs --dry-run`
Expected: prints the 2 constructed requests (URL + headers + body) for `known-good` and `known-bad`; posts nothing; exits 0.

- [ ] **Step 2: Unset-cookie guard check**

Run: `cd tools && node seed-tutor-events.mjs`
Expected: prints the `ERR: JARVIS_SESSION_COOKIE unset...` message; exits 2.

- [ ] **Step 3: Live run against the tutor**

Run: `cd tools && JARVIS_SESSION_COOKIE='<captured cookie>' node seed-tutor-events.mjs`
Expected: two result lines — `[OK] known-good: success — ...` and `[OK] known-bad: success — ...` (or `[WARN] ... ungraded ...` if the server's OpenRouter is quota-dead — still acceptable, an event still landed). Exit 0. If you see `[FAIL] ... auth_error` the captured cookie is stale — re-capture it.

- [ ] **Step 4: Read back the events from the VPS log**

Run:
```bash
cd tools && node -e "
import('./lib/event-log-reader.mjs').then(async ({ readEvents, filterEvents }) => {
  const all = await readEvents({ sshTarget: 'root@46.247.109.91' });
  const withSyn = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: true });
  const withoutSyn = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: false });
  console.log('drill_grade events (include_synthetic:true) :', withSyn.length);
  console.log('drill_grade events (include_synthetic:false):', withoutSyn.length);
  console.log('synthetic-only count:', withSyn.length - withoutSyn.length);
});
"
```
Expected: `synthetic-only count` is **2** (the two seeded events) — present with `include_synthetic:true`, absent without it. This confirms the events landed AND are correctly partitioned as `is_synthetic`.

- [ ] **Step 5: No commit**

This task verifies behavior; it changes no files. If Step 4 shows 2 synthetic events, Spec A's acceptance criteria are met.

---

## Acceptance criteria (from the spec)

- [ ] `tools/seed-tutor-events.mjs` exists; `JARVIS_SESSION_COOKIE` unset → loud exit before any request (Task 6 Step 2).
- [ ] `--dry-run` prints the 2 constructed requests and POSTs nothing (Task 6 Step 1).
- [ ] A live run produces exactly 2 new `drill_grade` events for task `01KR6K07T6PATPRR5KH1JXYF8E`, both `is_synthetic: true`, visible via `event-log-reader.mjs` `readEvents({ sshTarget })` with `include_synthetic: true` and absent without it (Task 6 Steps 3-4).
- [ ] 401 / 403 / 400 each produce a loud failure + non-zero exit; a soft `UNGRADED` reply is a warning, not a failure (Tasks 2-3 unit tests + Task 4 CLI exit logic).
- [ ] `seed-tutor-events.test.mjs` passes and is wired into `package.json` `test:tools`; the full `test:tools` suite stays green (Task 5).
