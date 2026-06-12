import { test } from "node:test";
import assert from "node:assert/strict";
import { generateBlock, spliceIntoDesign } from "./generate-design-md.mjs";

test("generateBlock parses :root + @theme tokens into a sorted table", () => {
  const css = `
:root {
  --color-b: #222;
  /* a comment with a ; semicolon inside should be ignored */
  --color-a: #111;
}
@theme {
  --ease-x: var(--ease-x);
}
`;
  const block = generateBlock(css);
  // sorted: --color-a before --color-b
  assert.ok(block.indexOf("--color-a") < block.indexOf("--color-b"));
  assert.ok(block.includes("| `--color-a` | `#111` |"));
  assert.ok(block.includes("| `--ease-x` | `var(--ease-x)` |"));
  assert.ok(!block.includes("a comment"));
});

test("spliceIntoDesign replaces only the marker region", () => {
  const design = "PROSE\n<!-- AUTOGEN:tokens BEGIN -->\nOLD\n<!-- AUTOGEN:tokens END -->\nTAIL";
  const out = spliceIntoDesign(design, "NEW");
  assert.ok(out.startsWith("PROSE\n<!-- AUTOGEN:tokens BEGIN -->"));
  assert.ok(out.endsWith("<!-- AUTOGEN:tokens END -->\nTAIL"));
  assert.ok(out.includes("NEW"));
  assert.ok(!out.includes("OLD"));
});

test("spliceIntoDesign throws when markers are absent", () => {
  assert.throws(() => spliceIntoDesign("no markers here", "X"), /AUTOGEN markers/);
});

// Plan 4a review-fix: blockBody() MUST scan ALL matching top-level blocks and concatenate
// their bodies (not just the FIRST).  The Step-8 drift probe appends a second :root block via
// Add-Content — if blockBody() returned only the first block the probe token would be missed,
// the gate would stay green when it should red, violating fix-claim discipline.  This test
// documents and enforces the ALL-blocks behaviour that the implementation deliberately chose
// (deviation from the plan's Step-1 canonical code, which was single-match only).
test("generateBlock picks up tokens from a SECOND :root block (multi-block concatenation)", () => {
  const css = `
:root {
  --color-a: #111;
}
/* tooling appended a second :root block (e.g. drift-probe via Add-Content) */
:root { --plan4a-drift-probe: 1px; }
@theme {
  --ease-x: var(--ease-x);
}
`;
  const block = generateBlock(css);
  // Both blocks' tokens must appear in the generated table.
  assert.ok(
    block.includes("| `--color-a` | `#111` |"),
    "first :root block token missing",
  );
  assert.ok(
    block.includes("| `--plan4a-drift-probe` | `1px` |"),
    "second :root block token missing — blockBody() returned only the first block",
  );
});
