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
  if (!jarvisAuth || !jarvisSession || !csrf) {
    throw new Error("buildHeaders: jarvisAuth, jarvisSession, and csrf are all required");
  }
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
