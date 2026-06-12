import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";
// generator lives at <repo>/tools; this test lives at <repo>/tutor-web/src/__tests__
import { generateBlock } from "../../../tools/generate-design-md.mjs";

const REPO_ROOT = resolve(__dirname, "..", "..", "..");
const css = readFileSync(resolve(REPO_ROOT, "tutor-web/src/index.css"), "utf8");
const design = readFileSync(resolve(REPO_ROOT, "DESIGN.md"), "utf8");

const BEGIN = "<!-- AUTOGEN:tokens BEGIN -->";
const END = "<!-- AUTOGEN:tokens END -->";

function committedBlock(text: string): string {
  const b = text.indexOf(BEGIN);
  const e = text.indexOf(END);
  if (b === -1 || e === -1) throw new Error("DESIGN.md AUTOGEN markers missing");
  return text.slice(b + BEGIN.length, e).replace(/^\n/, "").replace(/\n$/, "");
}

describe("DESIGN.md token block stays in sync with index.css (Plan 4a §0.9B)", () => {
  it("regenerated block equals the committed block (run `npm run design:check` to fix drift)", () => {
    const regenerated = generateBlock(css);
    expect(committedBlock(design)).toBe(regenerated);
  });
});
