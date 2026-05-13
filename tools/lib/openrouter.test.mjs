import { test } from "node:test";
import assert from "node:assert/strict";
import { callLlm } from "./openrouter.mjs";

test("callLlm returns envelope shape", async (t) => {
  // Mock the fetch transport via dependency injection.
  const fakeFetch = async (url, init) => {
    return new Response(JSON.stringify({
      choices: [{ message: { content: '{"status": "PASS"}' } }],
      model: "qwen/qwen-2.5-7b:free",
      usage: { prompt_tokens: 120, completion_tokens: 8 }
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const result = await callLlm({
    apiKey: "sk-test",
    model: "qwen/qwen-2.5-7b:free",
    systemPrompt: "You are a judge.",
    userPrompt: "Grade INV-01.",
    temperature: 0,
    seed: 42,
    transport: fakeFetch,
  });

  assert.equal(result.text, '{"status": "PASS"}');
  assert.equal(result.model_resolved, "qwen/qwen-2.5-7b:free");
  assert.equal(typeof result.prompt_sha256, "string");
  assert.equal(result.prompt_sha256.length, 64);
  assert.equal(result.tokens_in, 120);
  assert.equal(result.tokens_out, 8);
  assert.equal(typeof result.latency_ms, "number");
});
