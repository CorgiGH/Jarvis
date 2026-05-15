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
