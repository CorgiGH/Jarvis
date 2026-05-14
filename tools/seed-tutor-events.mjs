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
