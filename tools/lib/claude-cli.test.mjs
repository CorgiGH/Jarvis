import { test } from "node:test";
import assert from "node:assert/strict";
import { callLlm } from "./claude-cli.mjs";

function makeMockChild({ stdout = "", stderr = "", exitCode = 0, signalKill = null } = {}) {
  const listeners = { stdout: [], stderr: [], close: [], error: [] };
  return {
    stdout: { on: (e, fn) => listeners.stdout.push(fn) },
    stderr: { on: (e, fn) => listeners.stderr.push(fn) },
    stdin: { write: () => {}, end: () => {
      // Fire stdout/stderr/close after stdin closes — order matches real subprocess lifecycle.
      queueMicrotask(() => {
        if (stdout) listeners.stdout.forEach(fn => fn(Buffer.from(stdout)));
        if (stderr) listeners.stderr.forEach(fn => fn(Buffer.from(stderr)));
        listeners.close.forEach(fn => fn(exitCode, signalKill));
      });
    } },
    on: (event, fn) => { if (event === "close") listeners.close.push(fn); if (event === "error") listeners.error.push(fn); },
    kill: () => {},
  };
}

test("callLlm: returns {text, model_resolved, ...} on successful CLI exit 0", async () => {
  const r = await callLlm({
    systemPrompt: "sys",
    userPrompt: "hi",
    model: "claude-opus-4-7",
    spawnImpl: () => makeMockChild({ stdout: "hello world" }),
    versionImpl: () => "2.1.141",
  });
  assert.equal(r.text, "hello world");
  assert.equal(r.model_resolved, "claude-opus-4-7");
  assert.equal(r.cli_version, "2.1.141");
  assert.equal(typeof r.prompt_sha256, "string");
  assert.equal(r.prompt_sha256.length, 64);
  assert.equal(typeof r.latency_ms, "number");
  assert.equal(r.tokens_in, null);
  assert.equal(r.tokens_out, null);
});

test("callLlm: falls back model_resolved to claude-cli@<version> when no model flag was passed", async () => {
  const r = await callLlm({
    systemPrompt: "sys",
    userPrompt: "hi",
    spawnImpl: () => makeMockChild({ stdout: "ok" }),
    versionImpl: () => "2.1.141",
  });
  assert.equal(r.model_resolved, "claude-cli@2.1.141");
});

test("callLlm: throws on non-zero exit code with stderr in error message", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ stdout: "", stderr: "rate-limit exceeded", exitCode: 1 }),
      versionImpl: () => "2.1.141",
    }),
    /callLlm: claude CLI exit 1.*rate-limit/,
  );
});

test("callLlm: throws on timeout (signal kill)", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ exitCode: null, signalKill: "SIGTERM" }),
      versionImpl: () => "2.1.141",
    }),
    /timeout|SIGTERM/i,
  );
});

test("callLlm: rejects when CLAUDE_CLI_BIN does not resolve (versionImpl throws)", async () => {
  await assert.rejects(
    () => callLlm({
      systemPrompt: "sys",
      userPrompt: "hi",
      spawnImpl: () => makeMockChild({ stdout: "ok" }),
      versionImpl: () => { throw new Error("ENOENT"); },
    }),
    /claude.*not.*found|ENOENT/i,
  );
});
