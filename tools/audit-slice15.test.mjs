import { test } from "node:test";
import assert from "node:assert/strict";
import { parseStateMatrix } from "./audit-slice15.mjs";

const SAMPLE_SPEC = `
# Some spec

## State matrix

| ID | Route | Reach (Playwright sequence) | Required \`[data-testid]\` selectors visible | Bug-class lint expectations |
|----|-------|----------------------------|---------------------------------------------|-----------------------------|
| S-01 | \`/\` (cold) | \`goto /\` | \`active-task-dashboard\`, \`active-task-row\` (≥1) | no snake_case in any \`active-task-row\` |
| S-07 | \`/?taskId=PS-Tema-A\` | \`goto /?taskId=01KR6K07T6PATPRR5KH1JXYF8E\` | \`drill-rubric\`, \`drill-stack\` | NO snake_case in \`drill-rubric\` |

## Some other section
`;

test("parseStateMatrix extracts S-NN rows from a spec's State matrix table", () => {
  const rows = parseStateMatrix(SAMPLE_SPEC);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].id, "S-01");
  assert.equal(rows[0].route, "/ (cold)");
  assert.deepEqual(rows[0].selectors, ["active-task-dashboard", "active-task-row"]);
  assert.equal(rows[1].id, "S-07");
  assert.ok(rows[1].reach.includes("01KR6K07T6PATPRR5KH1JXYF8E"));
});

test("parseStateMatrix returns empty array when no matrix present", () => {
  const rows = parseStateMatrix("# Header only\n\nNo table.");
  assert.deepEqual(rows, []);
});

test("parseStateMatrix skips example rows inside ``` fenced code blocks", () => {
  const spec = [
    "## State matrix",
    "",
    "| ID | Route | Reach | Selectors | Expectations |",
    "|----|-------|-------|-----------|--------------|",
    "| S-01 | `/a` | `goto /a` | `foo` | none |",
    "",
    "## Example findings",
    "",
    "```",
    "| S-99 | MED | example-only | should-not-parse | example | none |",
    "```",
    "",
    "| S-02 | `/b` | `goto /b` | `bar` | none |",
  ].join("\n");
  const rows = parseStateMatrix(spec);
  assert.equal(rows.length, 2);
  assert.deepEqual(rows.map(r => r.id), ["S-01", "S-02"]);
});

import { classifySeverity } from "./audit-slice15.mjs";

test("classifySeverity: missing required selector → HIGH", () => {
  assert.equal(classifySeverity({ category: "missing-selector" }), "HIGH");
});

test("classifySeverity: pageerror → HIGH", () => {
  assert.equal(classifySeverity({ category: "pageerror" }), "HIGH");
});

test("classifySeverity: 4xx/5xx on first paint → HIGH", () => {
  assert.equal(classifySeverity({ category: "first-paint-http-error" }), "HIGH");
});

test("classifySeverity: axe AA violation → HIGH", () => {
  assert.equal(classifySeverity({ category: "axe-violation", axeLevel: "wcag2aa" }), "HIGH");
});

test("classifySeverity: axe AAA violation → MED", () => {
  assert.equal(classifySeverity({ category: "axe-violation", axeLevel: "wcag2aaa" }), "MED");
});

test("classifySeverity: snake_case → MED", () => {
  assert.equal(classifySeverity({ category: "snake-case-leak" }), "MED");
});

test("classifySeverity: SCREAMING_SNAKE → MED", () => {
  assert.equal(classifySeverity({ category: "screaming-snake-leak" }), "MED");
});

test("classifySeverity: dotted model name → MED", () => {
  assert.equal(classifySeverity({ category: "model-name-leak" }), "MED");
});

test("classifySeverity: raw HTTP error visible (outside allowlist) → HIGH", () => {
  assert.equal(classifySeverity({ category: "raw-http-error" }), "HIGH");
});

test("classifySeverity: placeholder text → MED", () => {
  assert.equal(classifySeverity({ category: "placeholder-leak" }), "MED");
});

test("classifySeverity: LLM judge HIGH → MED (one-band downgrade — judge is subjective)", () => {
  assert.equal(classifySeverity({ category: "llm-judge", judgeSeverity: "HIGH" }), "MED");
});

test("classifySeverity: LLM judge MED → LOW", () => {
  assert.equal(classifySeverity({ category: "llm-judge", judgeSeverity: "MED" }), "LOW");
});

test("classifySeverity: LLM judge LOW → LOW", () => {
  assert.equal(classifySeverity({ category: "llm-judge", judgeSeverity: "LOW" }), "LOW");
});

test("classifySeverity: unknown category → LOW (safe default)", () => {
  assert.equal(classifySeverity({ category: "unknown" }), "LOW");
});

// Internal helper — re-import via dynamic import to test the non-exported chain resolver.
const audit = await import("./audit-slice15.mjs");

test("parseStateMatrix recognises S-29 ;-separated reach with viewport keyword", () => {
  const spec = [
    "| ID | Route | Reach | Selectors | Expectations |",
    "|----|-------|-------|-----------|--------------|",
    "| S-29 | mobile | viewport 375x812 ; goto /tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E | `tutor-header` | none |",
  ].join("\n");
  const rows = audit.parseStateMatrix(spec);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, "S-29");
  assert.ok(rows[0].reach.includes("viewport 375x812"));
  assert.ok(rows[0].reach.includes("goto /tutor/"));
  assert.deepEqual(rows[0].selectors, ["tutor-header"]);
});
