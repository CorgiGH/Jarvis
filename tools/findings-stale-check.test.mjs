import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { checkStaleness } from "./findings-stale-check.mjs";

const tmp = mkdtempSync(join(tmpdir(), "stale-test-"));

test("checkStaleness flags STALE when git_head differs", async () => {
  const fp = join(tmp, "doc1.md");
  writeFileSync(fp, [
    "---",
    "surface: X",
    "provenance:",
    "  git_head: dead000",
    "  bundle_hash: B-Xy35Ve",
    "  live_dom_fingerprint: null",
    "  ts_utc: 2026-05-13T17:55:55Z",
    "  surface_version: x-v1.0",
    "  judge_model_resolved: null",
    "  judge_prompt_sha256: null",
    "---",
    "# Findings",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.git_head.status, "STALE");
  assert.equal(result.fields.bundle_hash.status, "OK");
  assert.equal(result.overall, "STALE");
});

test("checkStaleness reports OK when everything matches", async () => {
  const currentGit = (await import("node:child_process")).execSync("git rev-parse --short HEAD").toString().trim();
  const fp = join(tmp, "doc2.md");
  writeFileSync(fp, [
    "---",
    "surface: X",
    "provenance:",
    `  git_head: ${currentGit}`,
    "  bundle_hash: B-Xy35Ve",
    "---",
    "",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.git_head.status, "OK");
});

test("checkStaleness reports overall OK for a fully-fresh doc incl. surface_version", async () => {
  // Regression: surface_version used to be drift-checked against
  // process.env.SURFACE_VERSION (unset in this process) — every doc
  // false-flagged DRIFT → STALE. It is now a recorded-only PRESENT field.
  const currentGit = (await import("node:child_process")).execSync("git rev-parse --short HEAD").toString().trim();
  const fp = join(tmp, "doc4.md");
  writeFileSync(fp, [
    "---",
    "surface: Z",
    "provenance:",
    `  git_head: ${currentGit}`,
    "  bundle_hash: B-Xy35Ve",
    `  ts_utc: ${new Date().toISOString()}`,
    "  surface_version: z-v1.0",
    "  judge_model_resolved: openai/gpt-oss-120b:free",
    "  judge_prompt_sha256: abc123",
    "---",
    "# Findings",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.surface_version.status, "PRESENT");
  assert.equal(result.fields.surface_version.stamped, "z-v1.0");
  assert.equal(result.overall, "OK");
});

test("checkStaleness parser is not fooled by same-named key in another frontmatter block", async () => {
  // A fake `git_head` inside a `description:` block above provenance must NOT leak.
  // The real `git_head` inside `provenance:` is `dead000` (stale vs. current HEAD).
  // If the parser leaked, it would pick up `cafe123` from the description block first
  // and we could not predict the status; the scoped parser must see only `dead000`.
  const fp = join(tmp, "doc3.md");
  writeFileSync(fp, [
    "---",
    "surface: X",
    "description:",
    "  git_head: cafe123",
    "  notes: this is not real provenance",
    "provenance:",
    "  git_head: dead000",
    "  bundle_hash: B-Xy35Ve",
    "  ts_utc: 2026-05-13T17:55:55Z",
    "---",
    "# Findings",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.git_head.stamped, "dead000",
    "parser must read git_head from the provenance: block only, not from description:");
  assert.equal(result.fields.git_head.status, "STALE");
});

process.on("exit", () => rmSync(tmp, { recursive: true, force: true }));
