#!/usr/bin/env node
/**
 * Plan 4a §0.9B — generate the DESIGN.md token table from tutor-web/src/index.css.
 *
 * Source of truth for the TABLE is index.css (:root + @theme custom properties).
 * Hand-written prose in DESIGN.md OUTSIDE the AUTOGEN markers is never touched.
 *
 * Usage:
 *   node tools/generate-design-md.mjs            # print the generated block to stdout
 *   node tools/generate-design-md.mjs --write    # splice it into DESIGN.md between the markers
 *
 * The drift test (designMdSync.test.ts) calls the stdout mode and string-compares
 * against the committed DESIGN.md block — any divergence reds the frontend job.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, "..");
const CSS_PATH = resolve(REPO_ROOT, "tutor-web", "src", "index.css");
const DESIGN_PATH = resolve(REPO_ROOT, "DESIGN.md");

const BEGIN = "<!-- AUTOGEN:tokens BEGIN -->";
const END = "<!-- AUTOGEN:tokens END -->";

/** Extract the body of ALL top-level `<selector> { ... }` blocks whose header matches `headerRe`,
 *  concatenated (handles multiple :root / @theme blocks that may be appended by tooling or tests).
 *  brace-match defensively so nested rules don't silently truncate the table. */
function blockBody(css, headerRe) {
  const bodies = [];
  const re = new RegExp(headerRe.source, "g");
  let m;
  while ((m = re.exec(css)) !== null) {
    const start = css.indexOf("{", m.index);
    if (start === -1) continue;
    let depth = 0;
    for (let i = start; i < css.length; i++) {
      if (css[i] === "{") depth++;
      else if (css[i] === "}") {
        depth--;
        if (depth === 0) {
          bodies.push(css.slice(start + 1, i));
          // Advance the regex past this block so the next match starts after it.
          re.lastIndex = i + 1;
          break;
        }
      }
    }
  }
  return bodies.length > 0 ? bodies.join("\n") : null;
}

/** Parse `--name: value;` declarations (ignores comments and blank lines). Returns [{name, value}] sorted by name. */
function parseTokens(body) {
  if (body == null) return [];
  // strip /* ... */ comments first so a `;` inside a comment can't fake a declaration
  const clean = body.replace(/\/\*[\s\S]*?\*\//g, "");
  const out = [];
  const re = /(--[A-Za-z0-9-]+)\s*:\s*([^;]+);/g;
  let m;
  while ((m = re.exec(clean)) !== null) {
    out.push({ name: m[1].trim(), value: m[2].replace(/\s+/g, " ").trim() });
  }
  out.sort((a, b) => a.name.localeCompare(b.name, "en"));
  return out;
}

function renderTable(title, tokens) {
  const lines = [`### ${title}`, "", "| Token | Value |", "|---|---|"];
  for (const t of tokens) {
    // escape pipe + backtick the value so a value containing `|` (none today) can't break the table
    const value = t.value.replace(/\|/g, "\\|");
    lines.push(`| \`${t.name}\` | \`${value}\` |`);
  }
  return lines.join("\n");
}

export function generateBlock(css) {
  const root = parseTokens(blockBody(css, /:root\s*{/));
  const theme = parseTokens(blockBody(css, /@theme\s*{/));
  return [
    "_Auto-generated from `tutor-web/src/index.css` by `tools/generate-design-md.mjs`. Do not edit by hand — run `npm run design:check`._",
    "",
    renderTable(":root custom properties", root),
    "",
    renderTable("@theme utilities", theme),
  ].join("\n");
}

export function spliceIntoDesign(designText, block) {
  const b = designText.indexOf(BEGIN);
  const e = designText.indexOf(END);
  if (b === -1 || e === -1 || e < b) {
    throw new Error(
      `DESIGN.md is missing the AUTOGEN markers (${BEGIN} / ${END}); add them once before --write.`,
    );
  }
  const before = designText.slice(0, b + BEGIN.length);
  const after = designText.slice(e);
  return `${before}\n${block}\n${after}`;
}

function main() {
  const css = readFileSync(CSS_PATH, "utf8");
  const block = generateBlock(css);
  if (process.argv.includes("--write")) {
    const design = readFileSync(DESIGN_PATH, "utf8");
    writeFileSync(DESIGN_PATH, spliceIntoDesign(design, block));
    process.stderr.write("DESIGN.md token block updated.\n");
  } else {
    process.stdout.write(block);
  }
}

// Run main only when invoked directly (not when imported by the test).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
