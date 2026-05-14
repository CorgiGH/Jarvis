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
  if (!sessionCookie) throw new Error("buildHeaders: sessionCookie is required");
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
// A 200 with a null reply (non-JSON body) is also soft — the event still landed.
export function classifyOutcome(httpStatus, reply) {
  if (httpStatus === 401) return { outcome: "auth_error", hard: true, detail: "401 — invalid/missing jarvis_session cookie" };
  if (httpStatus === 403) return { outcome: "csrf_error", hard: true, detail: "403 — CSRF check failed (unexpected; seeder self-issues the pair)" };
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
