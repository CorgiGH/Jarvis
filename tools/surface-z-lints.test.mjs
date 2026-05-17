// tools/surface-z-lints.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { detectSnakeCase } from "./surface-z-lints.mjs";

// Pure unit tests for the snake-case detector (other 3 lints run in browser only)
test("detectSnakeCase finds snake_case in plain text", () => {
  const out = detectSnakeCase("foo bar uses_rlaplace_or_inverse_cdf baz");
  assert.deepEqual(out, ["uses_rlaplace_or_inverse_cdf"]);
});

test("detectSnakeCase ignores single-word lowercase", () => {
  assert.deepEqual(detectSnakeCase("foo bar baz"), []);
});

test("detectSnakeCase scans plain text only — caller strips code/pre tags", () => {
  // Caller is responsible for stripping <code>/<pre> before passing to detector
  // This test confirms the detector itself only scans plain text.
  assert.deepEqual(detectSnakeCase("hello_world test"), ["hello_world"]);
});

test("detectSnakeCase returns all matches in order", () => {
  assert.deepEqual(
    detectSnakeCase("foo_bar and baz_qux end"),
    ["foo_bar", "baz_qux"],
  );
});

test("detectSnakeCase skipFilenameTokens drops intro_print embedded in .pdf filename", () => {
  const text = "see lectures__OS1.1_Linux-intro_print-ro.pdf for details";
  assert.deepEqual(detectSnakeCase(text, { skipFilenameTokens: true }), []);
});

test("detectSnakeCase skipFilenameTokens keeps real enum leaks (no extension on token)", () => {
  assert.deepEqual(
    detectSnakeCase("rubric: uses_rlaplace_or_inverse_cdf_sampler is required", { skipFilenameTokens: true }),
    ["uses_rlaplace_or_inverse_cdf_sampler"],
  );
});

test("detectSnakeCase default (no opts) preserves legacy permissive match for back-compat", () => {
  assert.deepEqual(detectSnakeCase("see foo_bar.pdf details"), ["foo_bar"]);
});

import { detectScreamingSnake } from "./surface-z-lints.mjs";

test("detectScreamingSnake finds SCREAMING_SNAKE in plain text", () => {
  const r = detectScreamingSnake("status is USER_MARKED_DONE today");
  assert.deepEqual(r.matches, ["USER_MARKED_DONE"]);
});

test("detectScreamingSnake ignores single ALLCAPS words (PDF, OK)", () => {
  const r = detectScreamingSnake("PDF is OK and ALSO fine");
  assert.deepEqual(r.matches, []);
});

test("detectScreamingSnake finds multiple distinct matches", () => {
  const r = detectScreamingSnake("got EXPLICIT_ASK and USER_MARKED_DONE here");
  assert.deepEqual(r.matches, ["EXPLICIT_ASK", "USER_MARKED_DONE"]);
});

test("detectScreamingSnake ignores ALLCAPS_with_lowercase mixed (those are different class)", () => {
  const r = detectScreamingSnake("ALLCAPS_then_lower not flagged here");
  assert.deepEqual(r.matches, []);
});

import { detectDottedModelName } from "./surface-z-lints.mjs";

test("detectDottedModelName finds OpenRouter-style model names", () => {
  const r = detectDottedModelName("served by z-ai/glm-4.5-air:free model");
  assert.deepEqual(r.matches, ["z-ai/glm-4.5-air:free"]);
});

test("detectDottedModelName finds non-:free variants too", () => {
  const r = detectDottedModelName("anthropic/claude-haiku-4-5 wrapped");
  assert.deepEqual(r.matches, ["anthropic/claude-haiku-4-5"]);
});

test("detectDottedModelName ignores file paths (path/to/file.js)", () => {
  const r = detectDottedModelName("see path/to/file.js or src/main.ts");
  assert.deepEqual(r.matches, []);
});

test("detectDottedModelName ignores URLs", () => {
  const r = detectDottedModelName("https://corgflix.duckdns.org/tutor/");
  assert.deepEqual(r.matches, []);
});

import { detectRawHttpError } from "./surface-z-lints.mjs";

test("detectRawHttpError finds 'HTTP 500' style leaks", () => {
  const r = detectRawHttpError("save failed: HTTP 500 internal");
  assert.deepEqual(r.matches, ["HTTP 500"]);
});

test("detectRawHttpError finds multiple HTTP error mentions", () => {
  const r = detectRawHttpError("HTTP 404 not found, also HTTP 502");
  assert.deepEqual(r.matches, ["HTTP 404", "HTTP 502"]);
});

test("detectRawHttpError ignores 2xx and 3xx (success / redirect ranges)", () => {
  const r = detectRawHttpError("HTTP 200 OK; HTTP 301 redirect");
  assert.deepEqual(r.matches, []);
});

test("detectRawHttpError ignores non-HTTP-prefixed numbers (e.g. counts)", () => {
  const r = detectRawHttpError("404 reused × today, 200 entries");
  assert.deepEqual(r.matches, []);
});

import { detectPlaceholder } from "./surface-z-lints.mjs";

test("detectPlaceholder finds TODO/TBD/FIXME/XXX as word boundary tokens", () => {
  const r = detectPlaceholder("TODO: finish this; TBD next sprint; FIXME later");
  assert.deepEqual(r.matches.sort(), ["FIXME", "TBD", "TODO"]);
});

test("detectPlaceholder is case-insensitive", () => {
  const r = detectPlaceholder("todo something fixme later");
  assert.deepEqual(r.matches.sort(), ["fixme", "todo"]);
});

test("detectPlaceholder ignores substrings (todoist, fixmesomething)", () => {
  const r = detectPlaceholder("todoist app and fixmeisntaword");
  assert.deepEqual(r.matches, []);
});

test("detectPlaceholder finds lorem ipsum", () => {
  const r = detectPlaceholder("body: lorem ipsum dolor sit");
  assert.deepEqual(r.matches, ["lorem ipsum"]);
});
