// tools/surface-y-gate.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { extractConceptReferences, checkResponse, gateLoop } from "./surface-y-gate.mjs";

const schema = {
  concepts: [
    { id: "laplace_distribution", aliases: ["Laplace"] },
    { id: "inverse_cdf_sampling", aliases: ["inverse-CDF", "rlaplace"] },
    { id: "rejection_sampling", aliases: ["rejection sampling"] },  // not in ledger
  ],
};

test("extractConceptReferences finds aliases in text", () => {
  const refs = extractConceptReferences("I will use rlaplace to sample.", schema);
  assert.ok(refs.includes("inverse_cdf_sampling"));
});

test("checkResponse OK when only seen concepts referenced", () => {
  const ledger = new Set(["laplace_distribution"]);
  const r = checkResponse({
    text: "I will think about Laplace.",
    schema, ledger,
  });
  assert.equal(r.ok, true);
});

test("checkResponse VIOLATION when off-ledger concept referenced", () => {
  const ledger = new Set(["laplace_distribution"]);
  const r = checkResponse({
    text: "I will use rejection sampling.",
    schema, ledger,
  });
  assert.equal(r.ok, false);
  assert.ok(r.violations.includes("rejection_sampling"));
});

test("gateLoop regenerates up to maxRegens then logs leak", async () => {
  let attempt = 0;
  const fakeCall = async () => {
    attempt++;
    return { text: "I will use rejection sampling.", model_resolved: "fake", prompt_sha256: "x".repeat(64), tokens_in: 10, tokens_out: 5, latency_ms: 100 };
  };
  const r = await gateLoop({
    initialUserPrompt: "go",
    schema, ledger: new Set(),
    callLlm: fakeCall,
    maxRegens: 2,
  });
  assert.equal(r.leaked, true);
  assert.equal(attempt, 3);  // 1 initial + 2 regenerates
});
