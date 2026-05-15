import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, readFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gradeOne, majorityVote, ofN, gradeSession, calibrateAgainstFixture, resolveProvider } from "./surface-x.mjs";

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

test("gradeOne returns PASS/FAIL/N_A from LLM JSON", async () => {
  const fakeCallLlm = async () => ({
    text: '{"status":"PASS","evidence":{"event_ids":["e1"],"excerpt":"predict typed at 14:02:11"},"reason":"predict before rcode"}',
    model_resolved: "qwen/qwen-2.5-7b:free",
    prompt_sha256: "abc".repeat(21) + "a",
    tokens_in: 100,
    tokens_out: 30,
    latency_ms: 1500,
  });
  const result = await gradeOne({
    invariantId: "INV-01",
    invariantStatement: "PREDICT before R-CODE",
    events: [{ event_id: "e1" }],
    callLlm: fakeCallLlm,
  });
  assert.equal(result.status, "PASS");
  assert.equal(result.model_resolved, "qwen/qwen-2.5-7b:free");
  assert.ok(result.prompt_sha256);
});

test("majorityVote returns the most common status across runs", () => {
  const runs = [
    { status: "PASS" }, { status: "PASS" }, { status: "FAIL" },
  ];
  assert.equal(majorityVote(runs).status, "PASS");
});

test("majorityVote returns N_A when no majority", () => {
  const runs = [{ status: "PASS" }, { status: "FAIL" }, { status: "N_A" }];
  assert.equal(majorityVote(runs).status, "N_A");
});

test("ofN computes K-of-N exact match", () => {
  const pairs = [
    { judge: "PASS", gold: "PASS" },
    { judge: "PASS", gold: "PASS" },
    { judge: "FAIL", gold: "PASS" },
    { judge: "PASS", gold: "PASS" },
  ];
  const r = ofN(pairs, 0.80);
  assert.equal(r.matched, 3);
  assert.equal(r.total, 4);
  assert.equal(r.k, 4);
  assert.equal(r.passed, false);
});

test("gradeSession runs each invariant, writes finding doc with provenance", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "x-doc-"));
  const fakeCallLlm = async ({ userPrompt }) => {
    const status = userPrompt.includes("INV-08") ? "FAIL" : "PASS";
    return {
      text: `{"status":"${status}","evidence":{"event_ids":["e1"]},"reason":"test"}`,
      model_resolved: "qwen/qwen-2.5-7b:free",
      prompt_sha256: "x".repeat(64),
      tokens_in: 50, tokens_out: 10, latency_ms: 500,
    };
  };
  const events = [
    { event_id: "e1", event_type: "drill_grade", ts_utc: "2026-05-13T10:00:00Z", task_id: "t1", is_synthetic: false },
    { event_id: "e2", event_type: "sidekick_ask", ts_utc: "2026-05-13T10:05:00Z", task_id: "t1", is_synthetic: false },
  ];
  const docPath = await gradeSession({
    sessionId: "test-session-1",
    events,
    invariantIds: ["INV-01", "INV-08"],
    outputDir: tmp,
    callLlm: fakeCallLlm,
    runsPerInvariant: 1,
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: X/);
  assert.match(text, /session_id: test-session-1/);
  assert.match(text, /git_head:/);
  assert.match(text, /INV-01.*PASS/);
  assert.match(text, /INV-08.*FAIL/);
});

test("calibrateAgainstFixture reports agreement against gold labels", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "x-calib-"));
  const fixturePath = join(tmp, "fixture.md");
  writeFileSync(fixturePath, [
    "# Bootstrap fixture",
    "",
    "### Trace 1",
    "```yaml",
    "events:",
    "  - {event_id: e1, event_type: drill_grade, task_id: t1, ts_utc: '2026-05-13T10:00:00Z'}",
    "labels:",
    "  INV-01: PASS",
    "  INV-08: FAIL",
    "```",
    "",
    "### Trace 2",
    "```yaml",
    "events:",
    "  - {event_id: e2, event_type: sidekick_ask, task_id: t1, ts_utc: '2026-05-13T10:01:00Z'}",
    "labels:",
    "  INV-05: PASS",
    "```",
  ].join("\n"));

  const fakeCallLlm = async () => ({
    text: '{"status":"PASS","evidence":{},"reason":"test"}',
    model_resolved: "fake", prompt_sha256: "x".repeat(64),
    tokens_in: 10, tokens_out: 5, latency_ms: 100,
  });
  const r = await calibrateAgainstFixture({
    fixturePath,
    callLlm: fakeCallLlm,
    runsPerInvariant: 1,
    thresholdPct: 0.80,
  });
  assert.equal(r.total, 3);
  assert.equal(r.matched, 2);
  assert.equal(r.passed, false);
});
