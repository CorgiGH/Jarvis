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
