# Deterministic Tutor-Event Seeder v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the seeder's auth model so a live run actually authenticates and lands real `drill_grade` events — fix-forward on top of the shipped v1 code (commits `d85a44d`..`0ba7497`).

**Architecture:** The v1 seeder authenticated with only a `jarvis_session` cookie + a self-issued csrf — both wrong. The verified flow is: read `jarvis_auth` (the outer auth-token interceptor) from `$JARVIS_AUTH_COOKIE` or `tools/AUTH_TOKEN.txt` → `GET /api/v1/tutor/auto-session` mints a fresh `jarvis_session` + `csrf` → `POST /api/v1/drill/grade` with all three cookies + `X-CSRF-Token`. Two new functions (`loadAuthToken`, `mintSession`) are added; `buildHeaders`/`seedOne`/`seedAll`/the CLI are rewritten; `buildRequest`/`classifyOutcome`/`loadFixture`/the fixture are kept.

**Tech Stack:** Node.js ESM (`.mjs`), `node --test`, `fetch` (injectable as `transport` for tests).

**Spec:** `docs/superpowers/specs/2026-05-15-deterministic-tutor-event-seeder-v2-design.md` (commit `708283a`).

**Human-in-the-loop note:** None. The v2 design needs no manual credential capture — `tools/AUTH_TOKEN.txt` exists (gitignored, 48 chars) and the seeder reads it. Tasks 1-5 are all autonomous.

---

## File Structure

- **Modify:** `tools/seed-tutor-events.mjs` — add `AUTH_TOKEN_PATH` + `loadAuthToken` + `mintSession`; rewrite `buildHeaders`, `seedOne`, `seedAll`, the CLI entrypoint; minor wording fix in `classifyOutcome`; remove the stale `CSRF_TOKEN` constant and the stale `TutorRoutes.kt`/`Csrf.kt` comments.
- **Modify:** `tools/seed-tutor-events.test.mjs` — add `loadAuthToken` + `mintSession` tests; rewrite the `buildHeaders` / `seedOne` / `seedAll` tests for the new signatures; keep the `buildRequest` + `classifyOutcome` tests. Ends at 21 tests.
- **Unchanged:** `tools/seed-tutor-events.fixture.json` (correct, committed `27ceb11`); `tools/package.json` (`seed-tutor-events.test.mjs` already wired into `test:tools` by v1 Task 5).

---

### Task 1: Add `loadAuthToken()` — resolve `jarvis_auth` from env or `AUTH_TOKEN.txt`

**Files:**
- Modify: `tools/seed-tutor-events.mjs`
- Modify: `tools/seed-tutor-events.test.mjs`

Purely additive — `loadAuthToken` is a new export; nothing existing is touched. All 14 existing tests stay green; 3 new tests are added → 17.

- [ ] **Step 1: Write the failing tests**

Append to the END of `tools/seed-tutor-events.test.mjs` (the file already uses mid-file `import` statements — this follows that established pattern):

```js
import { loadAuthToken } from "./seed-tutor-events.mjs";
import { writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

test("loadAuthToken: returns the env var value when set", () => {
  assert.equal(loadAuthToken({ env: { JARVIS_AUTH_COOKIE: "ENVTOK" } }), "ENVTOK");
});

test("loadAuthToken: falls back to AUTH_TOKEN.txt when env is unset", () => {
  const p = join(tmpdir(), `att-${Date.now()}-${Math.random().toString(36).slice(2)}.txt`);
  writeFileSync(p, "  FILETOK\n");
  try {
    assert.equal(loadAuthToken({ env: {}, authTokenPath: p }), "FILETOK");
  } finally {
    rmSync(p, { force: true });
  }
});

test("loadAuthToken: throws JARVIS_AUTH_UNRESOLVED when neither env nor file is available", () => {
  assert.throws(
    () => loadAuthToken({ env: {}, authTokenPath: join(tmpdir(), "nonexistent-att-file-xyz.txt") }),
    /JARVIS_AUTH_UNRESOLVED/,
  );
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — `loadAuthToken` is not exported / not a function.

- [ ] **Step 3: Write the implementation**

In `tools/seed-tutor-events.mjs`, make two edits.

Edit A — add the `AUTH_TOKEN_PATH` constant. Replace:

```js
export const DEFAULT_BASE_URL = "https://corgflix.duckdns.org";
export const DEFAULT_TASK_ID = "01KR6K07T6PATPRR5KH1JXYF8E";
```

with:

```js
export const DEFAULT_BASE_URL = "https://corgflix.duckdns.org";
export const DEFAULT_TASK_ID = "01KR6K07T6PATPRR5KH1JXYF8E";
export const AUTH_TOKEN_PATH = join(HERE, "AUTH_TOKEN.txt");
```

Edit B — add `loadAuthToken` after `loadFixture`. Replace:

```js
export function loadFixture() {
  return JSON.parse(readFileSync(join(HERE, "seed-tutor-events.fixture.json"), "utf8"));
}
```

with:

```js
export function loadFixture() {
  return JSON.parse(readFileSync(join(HERE, "seed-tutor-events.fixture.json"), "utf8"));
}

// jarvis_auth is the outer auth-token interceptor gating all /api/v1/* routes.
// Same source pattern as slice2-prefetch-gate.mjs / surface-y.mjs: the env var
// first, then the gitignored tools/AUTH_TOKEN.txt fallback. Inputs are
// injectable so the resolution logic is unit-testable without touching the
// real environment or file.
export function loadAuthToken({ env = process.env, authTokenPath = AUTH_TOKEN_PATH } = {}) {
  const fromEnv = env.JARVIS_AUTH_COOKIE?.trim();
  if (fromEnv) return fromEnv;
  try {
    const fromFile = readFileSync(authTokenPath, "utf8").trim();
    if (fromFile) return fromFile;
  } catch { /* fall through to the throw */ }
  throw new Error("JARVIS_AUTH_UNRESOLVED: set $JARVIS_AUTH_COOKIE or create tools/AUTH_TOKEN.txt");
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — **17 tests passing** (14 prior + 3 new).

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): loadAuthToken — resolve jarvis_auth from env or AUTH_TOKEN.txt"
```

---

### Task 2: Add `mintSession()` — `GET /auto-session` mints `jarvis_session` + `csrf`

**Files:**
- Modify: `tools/seed-tutor-events.mjs`
- Modify: `tools/seed-tutor-events.test.mjs`

Purely additive — `mintSession` is a new export. 17 tests stay green; 4 new tests added → 21.

- [ ] **Step 1: Write the failing tests**

Append to the END of `tools/seed-tutor-events.test.mjs`:

```js
import { mintSession } from "./seed-tutor-events.mjs";

// Mock transport for mintSession: GET auto-session returns a mint response
// shaped like Node's fetch Response (status, json(), headers.getSetCookie()).
function mockMintTransport({ status = 200, body = { ok: true, userId: "owner", csrf: "C32" }, setCookie = ["jarvis_session=S64; Max-Age=1209600; Path=/; Secure; HttpOnly"] } = {}) {
  return async (url) => {
    if (url.endsWith("/api/v1/tutor/auto-session")) {
      return { status, json: async () => body, headers: { getSetCookie: () => setCookie } };
    }
    throw new Error(`unexpected url ${url}`);
  };
}

test("mintSession: 200 -> parses jarvisSession from Set-Cookie and csrf from the body", async () => {
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport: mockMintTransport() });
  assert.equal(r.ok, true);
  assert.equal(r.jarvisSession, "S64");
  assert.equal(r.csrf, "C32");
});

test("mintSession: 401 -> hard auth_error telling the user to refresh the token", async () => {
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport: mockMintTransport({ status: 401 }) });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "auth_error");
  assert.equal(r.hard, true);
  assert.match(r.detail, /AUTH_TOKEN\.txt/);
});

test("mintSession: non-200/non-401 -> hard mint_error", async () => {
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport: mockMintTransport({ status: 503 }) });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "mint_error");
  assert.equal(r.hard, true);
});

test("mintSession: 200 but missing csrf body field -> hard mint_error", async () => {
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport: mockMintTransport({ body: { ok: true } }) });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "mint_error");
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — `mintSession` is not exported / not a function.

- [ ] **Step 3: Write the implementation**

In `tools/seed-tutor-events.mjs`, add `mintSession` after `classifyOutcome`. Replace:

```js
  return { outcome: "success", hard: false, detail: `correct=${reply?.correct} score=${reply?.score}` };
}

// One authenticated POST for one attempt. transport defaults to fetch and is
```

with:

```js
  return { outcome: "success", hard: false, detail: `correct=${reply?.correct} score=${reply?.score}` };
}

// GET /api/v1/tutor/auto-session with the jarvis_auth cookie mints a fresh
// jarvis_session + csrf pair (verified 2026-05-15). jarvis_session is parsed
// from Set-Cookie; csrf is read from the JSON response body. transport is
// injectable for tests. Returns { ok:true, jarvisSession, csrf } on success,
// or a hard-failure shape { ok:false, outcome, hard:true, detail }.
export async function mintSession({ jarvisAuth, baseUrl, transport = globalThis.fetch }) {
  let resp;
  try {
    resp = await transport(`${baseUrl}/api/v1/tutor/auto-session`, {
      method: "GET",
      headers: { "Cookie": `jarvis_auth=${jarvisAuth}` },
    });
  } catch (e) {
    return { ok: false, outcome: "network_error", hard: true, detail: String(e).slice(0, 160) };
  }
  if (resp.status === 401) {
    return { ok: false, outcome: "auth_error", hard: true, detail: "401 from auto-session — jarvis_auth invalid/expired; refresh tools/AUTH_TOKEN.txt" };
  }
  if (resp.status !== 200) {
    return { ok: false, outcome: "mint_error", hard: true, detail: `auto-session HTTP ${resp.status}` };
  }
  let body = null;
  try { body = await resp.json(); } catch { body = null; }
  const setCookies = resp.headers?.getSetCookie?.() ?? [];
  const pair = setCookies.find((c) => c.startsWith("jarvis_session="));
  const jarvisSession = pair ? pair.slice("jarvis_session=".length).split(";")[0] : null;
  const csrf = body?.csrf ?? null;
  if (!jarvisSession || !csrf) {
    return { ok: false, outcome: "mint_error", hard: true, detail: "auto-session 200 but missing jarvis_session Set-Cookie or csrf body field" };
  }
  return { ok: true, jarvisSession, csrf };
}

// One authenticated POST for one attempt. transport defaults to fetch and is
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — **21 tests passing** (17 prior + 4 new).

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): mintSession — GET /auto-session mints jarvis_session + csrf"
```

---

### Task 3: Rewrite the auth chain — `buildHeaders`, `seedOne`, `seedAll`, CLI

**Files:**
- Modify (overwrite): `tools/seed-tutor-events.mjs`
- Modify (overwrite): `tools/seed-tutor-events.test.mjs`

This is the cascade: `buildHeaders`'s signature change forces `seedOne` → `seedAll` → the CLI. They must change together to keep the file consistent, so this is one task. Both files are overwritten with their complete final content (this absorbs Tasks 1-2's additions verbatim and presents every function in final position). Test count stays **21** (the old `buildHeaders`/`seedOne`/`seedAll` tests are replaced 1:1-ish by new-signature versions).

> **Note — intentional changes to Task-1/Task-2 functions, folding in their code-review findings (not accidental drift):**
> - `loadAuthToken`'s catch block is hardened vs. Task 1's version — it now distinguishes `ENOENT` (file missing → fall through) from other fs errors like `EACCES` (file unreadable → throw with the real cause). The existing `loadAuthToken` tests still pass (a nonexistent path is `ENOENT` → still falls through to the generic throw).
> - `mintSession`'s single `!jarvisSession || !csrf` guard is split into two separate guards with distinct `detail` strings — so a hard failure tells you *which* field the `auto-session` 200 was missing (Set-Cookie session vs. body csrf), since those have different root causes. The existing `mintSession` "missing csrf body field" test still passes (`jarvisSession` is present → first guard skipped → second guard fires → `mint_error`).

- [ ] **Step 1: Overwrite the test file**

Overwrite `tools/seed-tutor-events.test.mjs` with EXACTLY this complete content:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildRequest, buildHeaders, classifyOutcome,
  loadAuthToken, mintSession, seedOne, seedAll,
} from "./seed-tutor-events.mjs";

const FAKE_TEMPLATE = {
  taskId: "T1", problemId: "P1", problemStatement: "PS",
  expectedAnswerHint: "H", referenceSolution: "R",
  rubricItems: ["r1"], prediction: null, language: "r",
};
const FAKE_ATTEMPT = { label: "good", userAttempt: "x <- 1" };

// Mock transport dispatching on URL: GET auto-session mints a session,
// POST drill/grade returns a graded reply. Records every call.
function mockTransport({
  mintStatus = 200,
  mintBody = { ok: true, userId: "owner", csrf: "C32" },
  mintSetCookie = ["jarvis_session=S64; Max-Age=1209600; Path=/; Secure; HttpOnly"],
  gradeStatus = 200,
  gradeBody = { misconception: "NONE", correct: true, score: 1 },
} = {}) {
  const calls = [];
  const transport = async (url, opts) => {
    calls.push({ url, opts });
    if (url.endsWith("/api/v1/tutor/auto-session")) {
      return { status: mintStatus, json: async () => mintBody, headers: { getSetCookie: () => mintSetCookie } };
    }
    if (url.endsWith("/api/v1/drill/grade")) {
      return { status: gradeStatus, json: async () => gradeBody };
    }
    throw new Error(`unexpected url ${url}`);
  };
  return { transport, calls };
}

// --- buildRequest ---
test("buildRequest merges userAttempt into the template, leaves other fields intact", () => {
  const req = buildRequest(FAKE_TEMPLATE, FAKE_ATTEMPT);
  assert.equal(req.userAttempt, "x <- 1");
  assert.equal(req.problemId, "P1");
  assert.equal(req.taskId, "T1");
  assert.equal(req.problemStatement, "PS");
  assert.equal("label" in req, false);
});

// --- loadAuthToken ---
test("loadAuthToken: returns the env var value when set", () => {
  assert.equal(loadAuthToken({ env: { JARVIS_AUTH_COOKIE: "ENVTOK" } }), "ENVTOK");
});

test("loadAuthToken: falls back to AUTH_TOKEN.txt when env is unset", () => {
  const p = join(tmpdir(), `att-${Date.now()}-${Math.random().toString(36).slice(2)}.txt`);
  writeFileSync(p, "  FILETOK\n");
  try {
    assert.equal(loadAuthToken({ env: {}, authTokenPath: p }), "FILETOK");
  } finally {
    rmSync(p, { force: true });
  }
});

test("loadAuthToken: throws JARVIS_AUTH_UNRESOLVED when neither env nor file is available", () => {
  assert.throws(
    () => loadAuthToken({ env: {}, authTokenPath: join(tmpdir(), "nonexistent-att-file-xyz.txt") }),
    /JARVIS_AUTH_UNRESOLVED/,
  );
});

// --- buildHeaders ---
test("buildHeaders emits all three cookies + matching X-CSRF-Token + X-Standin-Run", () => {
  const h = buildHeaders({ jarvisAuth: "A", jarvisSession: "S", csrf: "C" });
  assert.equal(h["Content-Type"], "application/json");
  assert.equal(h["X-Standin-Run"], "1");
  assert.equal(h["X-CSRF-Token"], "C");
  assert.equal(h["Cookie"], "jarvis_auth=A; jarvis_session=S; csrf=C");
});

// --- classifyOutcome ---
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

test("classifyOutcome: 200 + null reply -> soft ungraded (non-JSON body)", () => {
  const c = classifyOutcome(200, null);
  assert.equal(c.outcome, "ungraded");
  assert.equal(c.hard, false);
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

// --- mintSession ---
test("mintSession: 200 -> parses jarvisSession from Set-Cookie and csrf from the body", async () => {
  const { transport, calls } = mockTransport();
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport });
  assert.equal(r.ok, true);
  assert.equal(r.jarvisSession, "S64");
  assert.equal(r.csrf, "C32");
  assert.equal(calls[0].url, "https://x.test/api/v1/tutor/auto-session");
  assert.equal(calls[0].opts.method, "GET");
  assert.equal(calls[0].opts.headers["Cookie"], "jarvis_auth=A");
});

test("mintSession: 401 -> hard auth_error telling the user to refresh the token", async () => {
  const { transport } = mockTransport({ mintStatus: 401 });
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "auth_error");
  assert.equal(r.hard, true);
  assert.match(r.detail, /AUTH_TOKEN\.txt/);
});

test("mintSession: non-200/non-401 -> hard mint_error", async () => {
  const { transport } = mockTransport({ mintStatus: 503 });
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "mint_error");
  assert.equal(r.hard, true);
});

test("mintSession: 200 but missing csrf body field -> hard mint_error", async () => {
  const { transport } = mockTransport({ mintBody: { ok: true } });
  const r = await mintSession({ jarvisAuth: "A", baseUrl: "https://x.test", transport });
  assert.equal(r.ok, false);
  assert.equal(r.outcome, "mint_error");
});

// --- seedOne ---
test("seedOne: POSTs to the grade endpoint with the right URL, method, body, headers", async () => {
  let captured;
  const transport = async (url, opts) => {
    captured = { url, opts };
    return { status: 200, json: async () => ({ misconception: "NONE", correct: true, score: 0.9 }) };
  };
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", jarvisAuth: "A", jarvisSession: "S", csrf: "C", transport,
  });
  assert.equal(captured.url, "https://x.test/api/v1/drill/grade");
  assert.equal(captured.opts.method, "POST");
  assert.equal(JSON.parse(captured.opts.body).userAttempt, "x <- 1");
  assert.equal(captured.opts.headers["X-Standin-Run"], "1");
  assert.equal(captured.opts.headers["X-CSRF-Token"], "C");
  assert.equal(captured.opts.headers["Cookie"], "jarvis_auth=A; jarvis_session=S; csrf=C");
  assert.equal(r.label, "good");
  assert.equal(r.outcome, "success");
  assert.equal(r.hard, false);
});

test("seedOne: a network throw -> hard network_error", async () => {
  const transport = async () => { throw new Error("ECONNREFUSED"); };
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", jarvisAuth: "A", jarvisSession: "S", csrf: "C", transport,
  });
  assert.equal(r.outcome, "network_error");
  assert.equal(r.hard, true);
  assert.equal(r.httpStatus, null);
});

test("seedOne: a 401 from the server -> hard auth_error (even if body is not JSON)", async () => {
  const transport = async () => ({ status: 401, json: async () => { throw new Error("not json"); } });
  const r = await seedOne({
    template: FAKE_TEMPLATE, attempt: FAKE_ATTEMPT,
    baseUrl: "https://x.test", jarvisAuth: "A", jarvisSession: "S", csrf: "C", transport,
  });
  assert.equal(r.outcome, "auth_error");
  assert.equal(r.hard, true);
});

// --- seedAll ---
test("seedAll: mints once then seeds every attempt in order, reusing the minted session", async () => {
  const { transport, calls } = mockTransport();
  const attempts = [
    { label: "a", userAttempt: "1" },
    { label: "b", userAttempt: "2" },
  ];
  const results = await seedAll({
    template: FAKE_TEMPLATE, attempts, baseUrl: "https://x.test", jarvisAuth: "A", transport,
  });
  assert.equal(results.length, 2);
  assert.deepEqual(results.map((r) => r.label), ["a", "b"]);
  assert.equal(results.every((r) => r.outcome === "success"), true);
  assert.equal(calls.filter((c) => c.url.endsWith("/auto-session")).length, 1);
  assert.equal(calls.filter((c) => c.url.endsWith("/drill/grade")).length, 2);
  for (const c of calls.filter((c) => c.url.endsWith("/drill/grade"))) {
    assert.equal(c.opts.headers["Cookie"], "jarvis_auth=A; jarvis_session=S64; csrf=C32");
  }
});

test("seedAll: a mint failure -> one hard result per attempt, zero grade POSTs", async () => {
  const { transport, calls } = mockTransport({ mintStatus: 401 });
  const attempts = [
    { label: "a", userAttempt: "1" },
    { label: "b", userAttempt: "2" },
  ];
  const results = await seedAll({
    template: FAKE_TEMPLATE, attempts, baseUrl: "https://x.test", jarvisAuth: "A", transport,
  });
  assert.equal(results.length, 2);
  assert.equal(results.every((r) => r.hard === true), true);
  assert.equal(results.every((r) => r.outcome === "auth_error"), true);
  assert.equal(calls.filter((c) => c.url.endsWith("/drill/grade")).length, 0);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: FAIL — the rewritten `buildHeaders` / `seedOne` / `seedAll` tests fail against the still-old implementation signatures (old `buildHeaders` takes positional `(sessionCookie, csrfToken)`, old `seedOne`/`seedAll` take `sessionCookie`). The `buildRequest`, `classifyOutcome`, `loadAuthToken`, and `mintSession` tests still pass.

- [ ] **Step 3: Overwrite the implementation file**

Overwrite `tools/seed-tutor-events.mjs` with EXACTLY this complete content:

```js
// tools/seed-tutor-events.mjs
//
// Deterministic, non-LLM seeder for drill_grade events. POSTs hardcoded
// attempts to POST /api/v1/drill/grade so the Surface X golden fixture has
// real events to draw from. The seeder controls WHAT is submitted; the server
// grades each attempt server-side.
//
// Auth (verified 2026-05-15): jarvis_auth (env || tools/AUTH_TOKEN.txt) is the
// outer auth-token interceptor gating all /api/v1/* routes; GET
// /api/v1/tutor/auto-session mints a jarvis_session + csrf pair; POST
// /api/v1/drill/grade needs all three cookies + an X-CSRF-Token matching csrf.
//
// Spec: docs/superpowers/specs/2026-05-15-deterministic-tutor-event-seeder-v2-design.md

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

export const DEFAULT_BASE_URL = "https://corgflix.duckdns.org";
export const DEFAULT_TASK_ID = "01KR6K07T6PATPRR5KH1JXYF8E";
export const AUTH_TOKEN_PATH = join(HERE, "AUTH_TOKEN.txt");

export function loadFixture() {
  return JSON.parse(readFileSync(join(HERE, "seed-tutor-events.fixture.json"), "utf8"));
}

// jarvis_auth is the outer auth-token interceptor gating all /api/v1/* routes.
// Same source pattern as slice2-prefetch-gate.mjs / surface-y.mjs: the env var
// first, then the gitignored tools/AUTH_TOKEN.txt fallback. Inputs are
// injectable so the resolution logic is unit-testable without touching the
// real environment or file.
export function loadAuthToken({ env = process.env, authTokenPath = AUTH_TOKEN_PATH } = {}) {
  const fromEnv = env.JARVIS_AUTH_COOKIE?.trim();
  if (fromEnv) return fromEnv;
  try {
    const fromFile = readFileSync(authTokenPath, "utf8").trim();
    if (fromFile) return fromFile;
  } catch (e) {
    if (e.code !== "ENOENT") {
      throw new Error(`JARVIS_AUTH_UNRESOLVED: could not read ${authTokenPath}: ${e.message}`);
    }
    // ENOENT (file missing) is the expected "not configured" case — fall through.
  }
  throw new Error("JARVIS_AUTH_UNRESOLVED: set $JARVIS_AUTH_COOKIE or create tools/AUTH_TOKEN.txt");
}

// Merge one attempt's userAttempt into the captured ApiDrillGradeRequest template.
export function buildRequest(template, attempt) {
  return { ...template, userAttempt: attempt.userAttempt };
}

// POST /api/v1/drill/grade requires all three cookies (verified 2026-05-15):
// jarvis_auth (outer gate), jarvis_session + csrf (minted via auto-session).
// X-CSRF-Token must equal the csrf cookie. X-Standin-Run:1 is intended to tag
// the appended event is_synthetic=true — to be confirmed on the first live run.
export function buildHeaders({ jarvisAuth, jarvisSession, csrf }) {
  return {
    "Content-Type": "application/json",
    "X-Standin-Run": "1",
    "X-CSRF-Token": csrf,
    "Cookie": `jarvis_auth=${jarvisAuth}; jarvis_session=${jarvisSession}; csrf=${csrf}`,
  };
}

// Map (httpStatus, reply) -> { outcome, hard, detail }. `hard` true means the
// run should exit non-zero. A 200 always means an event landed; UNGRADED is a
// soft warning (server OpenRouter was down — event still appended, status:error).
// A 200 with a null reply (non-JSON body) is also soft — the event still landed.
export function classifyOutcome(httpStatus, reply) {
  if (httpStatus === 401) return { outcome: "auth_error", hard: true, detail: "401 — auth rejected (jarvis_auth or jarvis_session invalid)" };
  if (httpStatus === 403) return { outcome: "csrf_error", hard: true, detail: "403 — CSRF check failed (csrf contract changed)" };
  if (httpStatus === 400) return { outcome: "bad_request", hard: true, detail: "400 — payload no longer matches ApiDrillGradeRequest" };
  if (httpStatus !== 200) return { outcome: "http_error", hard: true, detail: `HTTP ${httpStatus}` };
  if (reply === null) {
    return { outcome: "ungraded", hard: false, detail: "200 but reply body was not JSON — event still landed" };
  }
  if (reply?.misconception === "UNGRADED") {
    return { outcome: "ungraded", hard: false, detail: "server OpenRouter unavailable — event still landed with status:error" };
  }
  return { outcome: "success", hard: false, detail: `correct=${reply?.correct} score=${reply?.score}` };
}

// GET /api/v1/tutor/auto-session with the jarvis_auth cookie mints a fresh
// jarvis_session + csrf pair (verified 2026-05-15). jarvis_session is parsed
// from Set-Cookie; csrf is read from the JSON response body. transport is
// injectable for tests. Returns { ok:true, jarvisSession, csrf } on success,
// or a hard-failure shape { ok:false, outcome, hard:true, detail }.
export async function mintSession({ jarvisAuth, baseUrl, transport = globalThis.fetch }) {
  let resp;
  try {
    resp = await transport(`${baseUrl}/api/v1/tutor/auto-session`, {
      method: "GET",
      headers: { "Cookie": `jarvis_auth=${jarvisAuth}` },
    });
  } catch (e) {
    return { ok: false, outcome: "network_error", hard: true, detail: String(e).slice(0, 160) };
  }
  if (resp.status === 401) {
    return { ok: false, outcome: "auth_error", hard: true, detail: "401 from auto-session — jarvis_auth invalid/expired; refresh tools/AUTH_TOKEN.txt" };
  }
  if (resp.status !== 200) {
    return { ok: false, outcome: "mint_error", hard: true, detail: `auto-session HTTP ${resp.status}` };
  }
  let body = null;
  try { body = await resp.json(); } catch { body = null; }
  const setCookies = resp.headers?.getSetCookie?.() ?? [];
  const pair = setCookies.find((c) => c.startsWith("jarvis_session="));
  const jarvisSession = pair ? pair.slice("jarvis_session=".length).split(";")[0] : null;
  const csrf = body?.csrf ?? null;
  if (!jarvisSession) {
    return { ok: false, outcome: "mint_error", hard: true, detail: "auto-session 200 but Set-Cookie did not include jarvis_session" };
  }
  if (!csrf) {
    return { ok: false, outcome: "mint_error", hard: true, detail: "auto-session 200 but JSON body missing csrf field" };
  }
  return { ok: true, jarvisSession, csrf };
}

// One authenticated POST for one attempt. transport defaults to fetch and is
// injectable for tests. A thrown transport error is a hard network_error; a
// non-JSON body is tolerated (reply stays null, classifyOutcome handles it).
export async function seedOne({ template, attempt, baseUrl, jarvisAuth, jarvisSession, csrf, transport = globalThis.fetch }) {
  const body = JSON.stringify(buildRequest(template, attempt));
  const headers = buildHeaders({ jarvisAuth, jarvisSession, csrf });
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

// Mint once, then seed every attempt sequentially reusing the minted session.
// If the mint fails, every attempt hard-fails with the mint's detail and
// nothing is POSTed.
export async function seedAll({ template, attempts, baseUrl, jarvisAuth, transport = globalThis.fetch }) {
  const minted = await mintSession({ jarvisAuth, baseUrl, transport });
  if (!minted.ok) {
    return attempts.map((a) => ({
      label: a.label, httpStatus: null, reply: null,
      outcome: minted.outcome, hard: true, detail: minted.detail,
    }));
  }
  const results = [];
  for (const attempt of attempts) {
    results.push(await seedOne({
      template, attempt, baseUrl, jarvisAuth,
      jarvisSession: minted.jarvisSession, csrf: minted.csrf, transport,
    }));
  }
  return results;
}

// CLI entrypoint — follows the tools/ convention (process.argv[1] guard +
// --key=value parsing) used in surface-z.mjs / surface-y.mjs.
if (process.argv[1]?.endsWith("seed-tutor-events.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map((a) => {
    const m = a.match(/^--([^=]+)=(.+)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));

  let jarvisAuth;
  try {
    jarvisAuth = loadAuthToken();
  } catch {
    console.error("ERR: jarvis_auth unresolved. Set $JARVIS_AUTH_COOKIE or create tools/AUTH_TOKEN.txt with a valid jarvis_auth value.");
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
    const minted = await mintSession({ jarvisAuth, baseUrl });
    if (!minted.ok) {
      console.error(`ERR: --dry-run mint failed: ${minted.detail}`);
      process.exit(1);
    }
    const mask = (v) => `<redacted:${String(v).length}-chars>`;
    for (const attempt of attempts) {
      const headers = buildHeaders({ jarvisAuth, jarvisSession: minted.jarvisSession, csrf: minted.csrf });
      const maskedHeaders = {
        ...headers,
        "X-CSRF-Token": mask(headers["X-CSRF-Token"]),
        "Cookie": `jarvis_auth=${mask(jarvisAuth)}; jarvis_session=${mask(minted.jarvisSession)}; csrf=${mask(minted.csrf)}`,
      };
      console.log(`--- ${attempt.label} ---`);
      console.log("POST", `${baseUrl}/api/v1/drill/grade`);
      console.log("headers:", JSON.stringify(maskedHeaders, null, 2));
      console.log("body:", JSON.stringify(buildRequest(template, attempt), null, 2));
    }
    process.exit(0);
  }

  const results = await seedAll({ template, attempts, baseUrl, jarvisAuth });
  let hardFail = false;
  for (const r of results) {
    const tag = r.hard ? "FAIL" : (r.outcome === "ungraded" ? "WARN" : "OK");
    console.log(`[${tag}] ${r.label}: ${r.outcome} — ${r.detail}`);
    if (r.hard) hardFail = true;
  }
  process.exit(hardFail ? 1 : 0);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd tools && node --test seed-tutor-events.test.mjs`
Expected: PASS — **21 tests passing, 0 failing**.

- [ ] **Step 5: Commit**

```bash
git add tools/seed-tutor-events.mjs tools/seed-tutor-events.test.mjs
git commit -m "feat(standin): rewrite seeder auth chain — jarvis_auth + mintSession + 3-cookie POST"
```

---

### Task 4: Verification — full suite, `--dry-run`, unset-guard

**Files:** none (verification only — no commit).

- [ ] **Step 1: Full tools test suite**

Run: `cd tools && npm run test:tools`
Expected: PASS — **84 tests** (63 prior + 21 seeder), 0 failures.

- [ ] **Step 2: `--dry-run` behavioral check**

Run: `cd tools && node seed-tutor-events.mjs --dry-run`
Expected: prints `--- known-good ---` and `--- known-bad ---` blocks, each with `POST https://corgflix.duckdns.org/api/v1/drill/grade`, a `headers:` object, and a `body:` object. **Every secret value is masked** — the output contains `<redacted:NN-chars>` for `jarvis_auth`, `jarvis_session`, `csrf`, and `X-CSRF-Token`; NO raw 48/64/32-char hex/token strings appear. Posts nothing. Exits 0. (This performs a real `auto-session` GET using `tools/AUTH_TOKEN.txt` — harmless.)

- [ ] **Step 3: Unset-guard behavioral check**

Run (one line — the `;` separators guarantee `AUTH_TOKEN.txt` is always restored even if `node` exits non-zero):

```bash
cd tools && mv AUTH_TOKEN.txt AUTH_TOKEN.txt.bak ; JARVIS_AUTH_COOKIE= node seed-tutor-events.mjs ; echo "exit:$?" ; mv AUTH_TOKEN.txt.bak AUTH_TOKEN.txt
```

(`JARVIS_AUTH_COOKIE=` sets it empty for that one command; `loadAuthToken` treats an empty string as unset and falls through to the file, which is now absent → the unresolved throw → the CLI's exit 2.)

Expected: prints `ERR: jarvis_auth unresolved. Set $JARVIS_AUTH_COOKIE or create tools/AUTH_TOKEN.txt ...` then `exit:2`. Confirm afterward that `tools/AUTH_TOKEN.txt` is back (`ls tools/AUTH_TOKEN.txt`).

- [ ] **Step 4: No commit**

This task changes no files.

---

### Task 5: Live acceptance run + read-back verification

**Files:** none (behavior-anchored acceptance — no commit).

Uses `tools/AUTH_TOKEN.txt` directly; no manual credential capture needed.

- [ ] **Step 1: Baseline — count existing synthetic `drill_grade` events**

Run:

```bash
cd tools && node -e "
import('./lib/event-log-reader.mjs').then(async ({ readEvents, filterEvents }) => {
  const all = await readEvents({ sshTarget: 'root@46.247.109.91' });
  const syn = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: true });
  const real = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: false });
  console.log('BASELINE synthetic-only drill_grade:', syn.length - real.length);
});
"
```

Record the baseline `synthetic-only` count (expected 0 — no v1 run ever succeeded — but record whatever it is).

- [ ] **Step 2: Live run**

Run: `cd tools && node seed-tutor-events.mjs`
Expected: two result lines — `[OK] known-good: success — ...` and `[OK] known-bad: success — ...` (or `[WARN] ... ungraded ...` if the server's OpenRouter is quota-dead — still acceptable, an event still landed). Exit 0. If you see `[FAIL] ... auth_error` with a "refresh tools/AUTH_TOKEN.txt" detail, `tools/AUTH_TOKEN.txt` holds a stale `jarvis_auth` — that is the one human-in-the-loop fallback: the token needs refreshing. If you see `[FAIL] ... mint_error` or a `drill/grade` 400/403, escalate (the endpoint contract changed).

- [ ] **Step 3: Read-back — confirm exactly 2 new synthetic events + the `is_synthetic` mapping**

Run:

```bash
cd tools && node -e "
import('./lib/event-log-reader.mjs').then(async ({ readEvents, filterEvents }) => {
  const all = await readEvents({ sshTarget: 'root@46.247.109.91' });
  const syn = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: true });
  const real = filterEvents(all, { task_id: '01KR6K07T6PATPRR5KH1JXYF8E', event_type: 'drill_grade', include_synthetic: false });
  console.log('AFTER synthetic-only drill_grade:', syn.length - real.length);
  console.log('(synthetic-only count must be BASELINE + 2)');
});
"
```

Expected: `AFTER synthetic-only` = the Step 1 baseline **+ 2**. This confirms two things at once: (a) the seeder's two events landed, and (b) the previously-unverified `X-Standin-Run: 1` → `is_synthetic: true` mapping actually works (the events are visible with `include_synthetic:true` and excluded without it). If the count went up by 2 in the *real* (`include_synthetic:false`) set instead, `X-Standin-Run` is NOT tagging events synthetic — that is a finding to escalate, not a pass.

- [ ] **Step 4: No commit**

This task verifies behavior; it changes no files. If Step 3 shows baseline + 2 synthetic events, Spec A v2's acceptance criteria are met.

---

## Acceptance criteria (from the spec)

- [ ] `tools/seed-tutor-events.mjs` resolves `jarvis_auth` from `$JARVIS_AUTH_COOKIE` or `tools/AUTH_TOKEN.txt`; both missing → loud exit 2 before any request (Task 4 Step 3 + the `loadAuthToken` unit test).
- [ ] `mintSession` does `GET /api/v1/tutor/auto-session` and returns `{ jarvisSession, csrf }`; a `401` produces a loud "refresh AUTH_TOKEN.txt" hard failure (Task 2 unit tests).
- [ ] `--dry-run` mints, prints the 2 constructed POSTs with all secret values masked, POSTs nothing (Task 4 Step 2).
- [ ] A live run produces exactly 2 new `drill_grade` events for task `01KR6K07T6PATPRR5KH1JXYF8E`, both `is_synthetic:true`, visible via `event-log-reader.mjs` with `include_synthetic:true` and absent without it (Task 5 Steps 1-3).
- [ ] The `X-Standin-Run:1` → `is_synthetic:true` mapping is confirmed by that read-back (Task 5 Step 3).
- [ ] `401`/`403`/`400`/mint-failure each produce a loud failure + non-zero exit; a soft `UNGRADED` reply is a warning, not a failure (Tasks 2-3 unit tests + Task 3 CLI exit logic).
- [ ] `seed-tutor-events.test.mjs` passes (21 tests) and the full `test:tools` suite stays green at 84 (Task 4 Step 1).
