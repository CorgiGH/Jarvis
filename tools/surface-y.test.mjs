// tools/surface-y.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { runStandin } from "./surface-y.mjs";

test("runStandin enforces hard cap on LLM calls", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"give_up","target":"","payload":"","observation":"stuck"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async () => "page text",
        click: async () => {},
        fill: async () => {},
        url: () => "https://corgflix.duckdns.org/tutor/?taskId=t1",
        close: async () => {},
        setExtraHTTPHeaders: async () => {},
        on: () => {},
      }),
      setExtraHTTPHeaders: async () => {},
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1",
    schemaPath,
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    maxCallsPerSession: 3,
    outputDir: tmp,
    sessionId: "test-y-1",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
    piggybackZ: false,
  });
  assert.ok(callsMade <= 3, `expected ≤3 calls, got ${callsMade}`);
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Y/);
  assert.match(text, /calls_used: \d+/);
});
