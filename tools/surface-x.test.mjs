import { test } from "node:test";
import assert from "node:assert/strict";
import { gradeOne, majorityVote, ofN } from "./surface-x.mjs";

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
