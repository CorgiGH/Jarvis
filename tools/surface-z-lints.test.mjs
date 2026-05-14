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
