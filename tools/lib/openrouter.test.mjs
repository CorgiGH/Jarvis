import { test } from "node:test";
import assert from "node:assert/strict";
import { callLlm } from "./openrouter.mjs";

const noSleep = async () => {};

test("callLlm returns envelope shape", async () => {
  const fakeFetch = async () => new Response(JSON.stringify({
    choices: [{ message: { content: '{"status": "PASS"}' } }],
    model: "qwen/qwen-2.5-7b:free",
    usage: { prompt_tokens: 120, completion_tokens: 8 },
  }), { status: 200, headers: { "content-type": "application/json" } });

  const result = await callLlm({
    apiKey: "sk-test",
    model: "qwen/qwen-2.5-7b:free",
    systemPrompt: "You are a judge.",
    userPrompt: "Grade INV-01.",
    temperature: 0,
    seed: 42,
    transport: fakeFetch,
    sleep: noSleep,
  });

  assert.equal(result.text, '{"status": "PASS"}');
  assert.equal(result.model_resolved, "qwen/qwen-2.5-7b:free");
  assert.equal(result.prompt_sha256.length, 64);
  assert.equal(result.tokens_in, 120);
  assert.equal(result.tokens_out, 8);
});

test("callLlm retries on transient upstream 429 then succeeds", async () => {
  let calls = 0;
  const fakeFetch = async () => {
    calls += 1;
    if (calls < 3) {
      return new Response(JSON.stringify({
        error: { message: "Provider returned error", code: 429,
          metadata: { raw: "google/gemma-4-26b-a4b-it:free is temporarily rate-limited upstream", provider_name: "Google AI Studio" } },
      }), { status: 429 });
    }
    return new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }],
      model: "google/gemma-4-26b-a4b-it:free",
      usage: { prompt_tokens: 10, completion_tokens: 2 },
    }), { status: 200 });
  };
  const result = await callLlm({
    apiKey: "sk-test", model: "google/gemma-4-26b-a4b-it:free",
    systemPrompt: "s", userPrompt: "u",
    transport: fakeFetch, sleep: noSleep,
  });
  assert.equal(calls, 3);
  assert.equal(result.text, "ok");
});

test("callLlm gives up after maxRetries on persistent 429", async () => {
  let calls = 0;
  const fakeFetch = async () => {
    calls += 1;
    return new Response(JSON.stringify({
      error: { message: "Provider returned error", code: 429,
        metadata: { raw: "temporarily rate-limited upstream" } },
    }), { status: 429 });
  };
  await assert.rejects(async () => {
    await callLlm({
      apiKey: "sk-test", model: "m",
      systemPrompt: "s", userPrompt: "u",
      transport: fakeFetch, sleep: noSleep, maxRetries: 2,
    });
  }, /callLlm: 429/);
  assert.equal(calls, 3); // initial + 2 retries
});

test("callLlm does NOT retry daily-quota 429 (free-models-per-day)", async () => {
  let calls = 0;
  const fakeFetch = async () => {
    calls += 1;
    return new Response(JSON.stringify({
      error: { message: "Rate limit exceeded: free-models-per-day", code: 429,
        metadata: { headers: { "X-RateLimit-Remaining": "0" } } },
    }), { status: 429 });
  };
  await assert.rejects(async () => {
    await callLlm({
      apiKey: "sk-test", model: "m",
      systemPrompt: "s", userPrompt: "u",
      transport: fakeFetch, sleep: noSleep,
    });
  }, /callLlm: 429/);
  assert.equal(calls, 1);
});

test("callLlm does NOT retry non-429 errors (e.g. 500)", async () => {
  let calls = 0;
  const fakeFetch = async () => {
    calls += 1;
    return new Response("server error", { status: 500 });
  };
  await assert.rejects(async () => {
    await callLlm({
      apiKey: "sk-test", model: "m",
      systemPrompt: "s", userPrompt: "u",
      transport: fakeFetch, sleep: noSleep,
    });
  }, /callLlm: 500/);
  assert.equal(calls, 1);
});

test("callLlm honors retry_after_seconds from response metadata", async () => {
  let calls = 0;
  let lastSleepMs = null;
  const sleep = async (ms) => { lastSleepMs = ms; };
  const fakeFetch = async () => {
    calls += 1;
    if (calls === 1) {
      return new Response(JSON.stringify({
        error: { code: 429, metadata: { retry_after_seconds: 12, raw: "upstream" } },
      }), { status: 429 });
    }
    return new Response(JSON.stringify({
      choices: [{ message: { content: "ok" } }], model: "m", usage: {},
    }), { status: 200 });
  };
  await callLlm({
    apiKey: "sk-test", model: "m",
    systemPrompt: "s", userPrompt: "u",
    transport: fakeFetch, sleep, baseDelayMs: 1000,
  });
  assert.ok(lastSleepMs >= 12000, `expected >=12000ms, got ${lastSleepMs}`);
});
