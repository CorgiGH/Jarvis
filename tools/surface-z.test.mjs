// tools/surface-z.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, basename } from "node:path";
import { sweepPages } from "./surface-z.mjs";

test("sweepPages emits findings doc with lint output per page", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "z-"));
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        screenshot: async ({ path }) => { writeFileSync(path, "PNG"); },
        // Fix 4: differentiate the two evaluate calls by argument
        evaluate: async (scriptOrExpr) => {
          if (typeof scriptOrExpr === "string" && scriptOrExpr.startsWith("document.body")) {
            return "Sample DOM text for testing";
          }
          return {
            snake_case: [{ text: "uses_rlaplace_or_inverse", selector: "div.rubric-chip" }],
            low_contrast: [], small_font: [], h_overflow: false,
          };
        },
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const fakeCallLlm = async () => ({
    text: '{"severity":"readability","observations":[{"what":"snake_case visible","where":"chip","why_it_hurts":"hard to read"}],"one_liner":"chip labels look like code"}',
    model_resolved: "gpt-oss-120b:free", prompt_sha256: "y".repeat(64),
    tokens_in: 80, tokens_out: 30, latency_ms: 1200,
  });
  const docPath = await sweepPages({
    pages: ["/tutor/", "/tutor/review"],
    viewports: [{ width: 1280, height: 800, name: "desktop" }],
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    outputDir: tmp,
    screenshotDir: tmp,
    sessionId: "test-z-1",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Z/);
  assert.match(text, /uses_rlaplace_or_inverse/);
  assert.match(text, /chip labels look like code/);
  // Fix 5b: provenance frontmatter present
  assert.match(text, /git_head:/);
  // Fix 5b: BOTH pages processed, not just the first
  assert.match(text, /tutor\/review/);
});

test("sweepPages sanitizes query-string page paths into filesystem-safe screenshot names", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "z-"));
  const screenshotPaths = [];
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        screenshot: async ({ path }) => { screenshotPaths.push(path); writeFileSync(path, "PNG"); },
        evaluate: async (scriptOrExpr) => {
          if (typeof scriptOrExpr === "string" && scriptOrExpr.startsWith("document.body")) return "dom";
          return { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };
        },
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const fakeCallLlm = async () => ({
    text: '{"severity":"none","observations":[],"one_liner":"ok"}',
    model_resolved: "m", prompt_sha256: "z".repeat(64), tokens_in: 1, tokens_out: 1, latency_ms: 1,
  });
  await sweepPages({
    // Two distinct paths that sanitize to the SAME safePath — the page-index
    // prefix must still keep their screenshot filenames distinct.
    pages: ["/tutor/?a=b_c", "/tutor/?a=b&c"],
    viewports: [{ width: 1280, height: 800, name: "desktop" }],
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    outputDir: tmp,
    screenshotDir: tmp,
    sessionId: "test-z-2",
  });
  assert.equal(screenshotPaths.length, 2);
  // Filename (not the full path — drive letters legitimately carry ':') must
  // not contain characters Windows rejects: ? : = etc.
  for (const p of screenshotPaths) assert.doesNotMatch(basename(p), /[?:=]/);
  // Colliding sanitized paths must NOT overwrite each other.
  assert.notEqual(screenshotPaths[0], screenshotPaths[1]);
});
