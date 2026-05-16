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
// Per council 1778881175 first-principles refinement: invalid state is competent-shape AND zero findings,
// so this fixture is intentionally barren of `observation` text (findings_count == 0).
const HYPER_COMPETENT = [
  { action: "click", target: "button:nth-of-type(2)", observation: "" },
  { action: "type", target: "textarea[placeholder=\"R code here\"]", payload: "rlaplace(n=1000, location=0, scale=1)", observation: "" },
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

// --- Council 1778881175 first-principles refinement: AND-gate suspect on findings_count == 0 ---
// A competent persona that nevertheless surfaces friction findings is high-signal, NOT invalid.
// Only the (competent transcript ∧ zero findings) cell of the 2x2 is invalid.

test("flagSuspectRun does NOT flag suspect when competent transcript surfaces findings", () => {
  const transcript = [
    { action: "type", observation: "tried solution X" },
    { action: "type", observation: "result is wrong somehow" },
    { action: "type", observation: "Y also wrong" },
    { action: "type", observation: "found UX issue: button unclear" },
    { action: "type", observation: "found UX issue: tooltip missing" },
    { action: "submit", observation: "found UX issue: error message vague" },
  ];
  const findingsCount = 3;
  const r = flagSuspectRun(transcript, findingsCount);
  assert.equal(r.suspect, false, "competent + nonzero findings = high-signal, NOT suspect");
});

test("flagSuspectRun flags suspect when competent transcript surfaces ZERO findings", () => {
  const transcript = [
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "submit", observation: "" },
  ];
  const findingsCount = 0;
  const r = flagSuspectRun(transcript, findingsCount);
  assert.equal(r.suspect, true, "competent + zero findings = invalid friction signal");
});

test("flagSuspectRun ignores findings_count for naive transcripts", () => {
  // Short run with confusion — naive path, doesn't trip the heuristic regardless of findings.
  const transcript = [
    { action: "ask_sidekick", observation: "confused about which formula" },
    { action: "type", observation: "let me try X" },
    { action: "submit", observation: "guess" },
  ];
  const r = flagSuspectRun(transcript, 0);
  assert.equal(r.suspect, false, "naive run is never suspect regardless of findings_count");
});

// --- Council 1778960917: adversarial-leaked fixture corpus (n=3) ---
// Replay-shaped from authentic DRAFT-Y traces per the council's recommended path.
// Each fixture targets a distinct branch of flagSuspectRun so threshold tuning can
// later move each cell independently. All 3 must produce suspect=true with
// findingsCount=0 — that is the negative class the tripwire is calibrated to flag.

// Variant 1 — Path B only (very early submit at step 2, index 1).
// Replay-shape source: DRAFT-Y-...-2026-05-15T10-33-33-020Z.md (clean R-script paste run),
// with the typing loop collapsed to a single typed cell + an immediate submit.
const ADVERSARIAL_LEAKED_1 = [
  { action: "type", target: "textarea[placeholder=\"# R code\"]", payload: "n <- 10000; for (b in c(0.5,1,2,4)) { x <- rmutil::rlaplace(n, 0, b); hist(x, freq=FALSE, breaks=100, main=paste0('Laplace b=',b)); curve(rmutil::dlaplace(x, 0, b), add=TRUE, col='red') }", observation: "" },
  { action: "submit", target: "button:nth-of-type(5)", observation: "" },
];

// Variant 2 — Path A only (7-step browse, zero ask_sidekick, zero confusion, no submit).
// Replay-shape source: DRAFT-Y-...-2026-05-14T18-04-21-810Z.md (exploration-style run),
// with all confused/leak observations stripped and the give_up tail removed.
const ADVERSARIAL_LEAKED_2 = [
  { action: "click", target: "button:nth-of-type(2)", observation: "" },
  { action: "click", target: "button:nth-of-type(8)", observation: "" },
  { action: "click", target: "button:nth-of-type(9)", observation: "" },
  { action: "click", target: "button:nth-of-type(10)", observation: "" },
  { action: "click", target: "button:nth-of-type(3)", observation: "" },
  { action: "click", target: "button:nth-of-type(2)", observation: "" },
  { action: "type", target: "#drill-prediction-input", payload: "histogram is symmetric around 0 with exponential decay; scale b widens the peak", observation: "" },
];

// Variant 3 — BOTH paths fire (6-step run ending in early submit at index 3).
// Replay-shape source: DRAFT-Y-...-2026-05-14T14-56-31-416Z.md (multi-step then submit),
// trimmed and prose-stripped so neither the run-length-zero-friction gate nor the
// early-submit gate has any escape.
const ADVERSARIAL_LEAKED_3 = [
  { action: "click", target: "button:nth-of-type(2)", observation: "" },
  { action: "type", target: "#drill-prediction-input", payload: "heavy tails, symmetric peak at 0", observation: "" },
  { action: "type", target: "textarea[placeholder=\"# R code\"]", payload: "x <- rmutil::rlaplace(10000, 0, 1); hist(x, freq=FALSE)", observation: "" },
  { action: "submit", target: "button:nth-of-type(5)", observation: "" },
  { action: "click", target: "button:nth-of-type(3)", observation: "" },
  { action: "click", target: "button:nth-of-type(4)", observation: "" },
];

test("flagSuspectRun: ADVERSARIAL_LEAKED_1 (Path B only — submit at step 2) → suspect", () => {
  const r = flagSuspectRun(ADVERSARIAL_LEAKED_1, 0);
  assert.equal(r.suspect, true);
  assert.equal(r.signals.ask_sidekick_count, 0);
  assert.equal(r.signals.confusion_step_count, 0);
  assert.ok(r.rationale.some(s => s.includes("submit at step")), "Path B rationale must mention early submit");
});

test("flagSuspectRun: ADVERSARIAL_LEAKED_2 (Path A only — 7-step zero-friction browse, no submit) → suspect", () => {
  const r = flagSuspectRun(ADVERSARIAL_LEAKED_2, 0);
  assert.equal(r.suspect, true);
  assert.equal(r.signals.ask_sidekick_count, 0);
  assert.equal(r.signals.confusion_step_count, 0);
  assert.ok(r.rationale.some(s => s.includes("zero ask_sidekick")), "Path A rationale must mention zero ask_sidekick");
  assert.equal(ADVERSARIAL_LEAKED_2.find(t => t.action === "submit"), undefined, "fixture intentionally has no submit");
});

test("flagSuspectRun: ADVERSARIAL_LEAKED_3 (both paths — long clean run ending in early submit) → suspect", () => {
  const r = flagSuspectRun(ADVERSARIAL_LEAKED_3, 0);
  assert.equal(r.suspect, true);
  assert.equal(r.signals.ask_sidekick_count, 0);
  assert.equal(r.signals.confusion_step_count, 0);
  assert.equal(r.rationale.length, 2, "both Path A and Path B must fire");
});
