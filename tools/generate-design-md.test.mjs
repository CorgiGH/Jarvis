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
