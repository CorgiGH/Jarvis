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
  assert.equal("label" in req, false);
});

test("buildHeaders self-issues a matching CSRF pair + X-Standin-Run + jarvis_session cookie", () => {
  const h = buildHeaders("SESS123", "TOK");
  assert.equal(h["Content-Type"], "application/json");
  assert.equal(h["X-Standin-Run"], "1");
  assert.equal(h["X-CSRF-Token"], "TOK");
  assert.equal(h["Cookie"], "jarvis_session=SESS123; csrf=TOK");
  assert.equal(buildHeaders("S")["X-CSRF-Token"], "seed-tutor-events-csrf");
});

test("buildHeaders throws when sessionCookie is falsy", () => {
  assert.throws(() => buildHeaders(""), /sessionCookie is required/);
  assert.throws(() => buildHeaders(undefined), /sessionCookie is required/);
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
