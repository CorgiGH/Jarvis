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
