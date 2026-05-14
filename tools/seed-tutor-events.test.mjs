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
