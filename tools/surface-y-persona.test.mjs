// tools/surface-y-persona.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildPersonaPrompt, updateLedger, sampleConfusionTuple } from "./surface-y-persona.mjs";

const schema = {
  concepts: [
    { id: "laplace_distribution", aliases: ["Laplace", "distribuția Laplace"] },
    { id: "inverse_cdf_sampling", aliases: ["inverse-CDF", "rlaplace"] },
    { id: "histogram_overlay", generic: true },
  ],
  confusion_tuples: [
    { between: ["laplace_distribution", "normal_distribution"], why: "both symmetric" },
  ],
};

test("updateLedger picks up concept aliases from DOM text", () => {
  const ledger = new Set();
  const newLedger = updateLedger(ledger, schema, "Distribuția Laplace is parameterized by μ and b.");
  assert.ok(newLedger.has("laplace_distribution"));
});

test("updateLedger does not add concepts whose aliases aren't present", () => {
  const ledger = new Set();
  const newLedger = updateLedger(ledger, schema, "Some other text about probabilities.");
  assert.equal(newLedger.size, 0);
});

test("updateLedger preserves pre-existing ledger entries", () => {
  const ledger = new Set(["inverse_cdf_sampling"]);
  const newLedger = updateLedger(ledger, schema, "Distribuția Laplace appears here.");
  assert.ok(newLedger.has("inverse_cdf_sampling"), "pre-existing entry must survive");
  assert.ok(newLedger.has("laplace_distribution"), "newly-seen concept must be added");
  assert.equal(ledger.size, 1, "input ledger must NOT be mutated");
});

test("buildPersonaPrompt lists UNKNOWN concepts (schema minus ledger minus generic)", () => {
  const ledger = new Set(["laplace_distribution"]);
  const prompt = buildPersonaPrompt({
    schema,
    ledger,
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>hello</p>",
  });
  assert.match(prompt, /You do NOT know.*inverse_cdf_sampling/);
  assert.doesNotMatch(prompt, /You do NOT know.*histogram_overlay/);  // generic
  assert.doesNotMatch(prompt, /You do NOT know.*laplace_distribution/);  // seen
});

test("sampleConfusionTuple picks one tuple at random from schema", () => {
  const t = sampleConfusionTuple(schema, 0);  // seeded
  assert.ok(t);
  assert.deepEqual(t.between, ["laplace_distribution", "normal_distribution"]);
});

test("sampleConfusionTuple returns null when schema has no confusion_tuples", () => {
  assert.equal(sampleConfusionTuple({ concepts: [] }, 0), null);
  assert.equal(sampleConfusionTuple({ concepts: [], confusion_tuples: [] }, 0), null);
});

test("buildPersonaPrompt shows (none) when every concept is known or generic", () => {
  const ledger = new Set(["laplace_distribution", "inverse_cdf_sampling"]);
  const prompt = buildPersonaPrompt({
    schema,
    ledger,
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>x</p>",
  });
  assert.match(prompt, /You do NOT know.*\(none\)/);
});

test("buildPersonaPrompt includes active confusion-tuple text", () => {
  const prompt = buildPersonaPrompt({
    schema,
    ledger: new Set(),
    sessionHistory: [],
    activeConfusionTuple: { between: ["laplace_distribution", "normal_distribution"], why: "both symmetric" },
    currentDom: "<p>x</p>",
  });
  assert.match(prompt, /You frequently confuse laplace_distribution with normal_distribution/);
});

test("buildPersonaPrompt includes authentic-naive exemplars (council 1778881175)", () => {
  // External hand-authored exemplars — must NOT come from calibration corpus
  // (would create overfitting / transcript-mimicry per Risk Analyst's
  // circular-dependency warning). Markers locked in below to catch
  // accidental deletion in future edits.
  const prompt = buildPersonaPrompt({
    schema,
    ledger: new Set(),
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>x</p>",
  });
  assert.match(prompt, /AUTHENTIC-NAIVE EXEMPLAR/i, "must include hand-authored exemplar block");
  assert.match(prompt, /ask_sidekick/i, "exemplar must demonstrate sidekick use");
  assert.match(prompt, /don't know|confused|stuck/i, "exemplar must demonstrate confusion expression");
  assert.match(prompt, /SHAPE references only|hand-authored|NOT.*calibration/i,
    "exemplar block must warn against verbatim copying");
});

test("buildPersonaPrompt exposes the submit action and instructs using it over click", () => {
  const prompt = buildPersonaPrompt({
    schema,
    ledger: new Set(),
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>x</p>",
  });
  // `submit` is in the JSON action enum
  assert.match(prompt, /"action":.*\| "submit" \|/);
  // the ACTION RULES instruct using `submit`, not clicking CHECK ANSWER
  assert.match(prompt, /use action "submit"/);
  assert.doesNotMatch(prompt, /To submit or check an answer, "click"/);
});
