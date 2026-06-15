/**
 * INV-9.4 self-test for the gate-coverage registry checker.
 *
 * Seeded-bad drill: feed checkRegistry a known-bad registry (one UNMAPPED clause +
 * one PROOF-backed-by-estimator) and assert it REDS (violations found). Plus a clean
 * fixture asserting zero violations, and a spawn of the REAL registry asserting exit 0.
 *
 * Run:  node --test tools/gate-registry-check.test.mjs
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { resolve, dirname, join } from "node:path";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { checkRegistry, summarize } from "./gate-registry-check.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const CHECKER = resolve(HERE, "gate-registry-check.mjs");
const REAL_REGISTRY = resolve(
  HERE,
  "..",
  "docs",
  "superpowers",
  "plans",
  "2026-06-15-gate-coverage-registry.json",
);

/** A minimally-valid clean registry: one LIVE-GREEN PROOF clause naming a real gate. */
function cleanRegistry() {
  return {
    acceptedDebt: [],
    counts: { "LIVE-GREEN": 1, PLANNED: 0, "RED-UNMAPPED": 0, total: 1 },
    clauses: [
      {
        id: "INV-CLEAN-1",
        dimension: "demo",
        layer: "SYS",
        tag: "PROOF",
        status: "LIVE-GREEN",
        backingGate: "backend job `gradle :check` → CleanDemoTest.kt",
        evidence: "fixture",
      },
    ],
  };
}

// ---------------------------------------------------------------------------
// (1) seeded-BAD: UNMAPPED clause + PROOF-backed-by-estimator → must RED
// ---------------------------------------------------------------------------
test("seeded-bad registry REDS: UNMAPPED clause + PROOF-backed-by-estimator", () => {
  const bad = {
    acceptedDebt: [],
    counts: { "LIVE-GREEN": 1, PLANNED: 1, "RED-UNMAPPED": 0, total: 2 },
    clauses: [
      {
        // UNMAPPED: no status, no tag
        id: "INV-BAD-UNMAPPED",
        dimension: "demo",
        layer: "SYS",
        backingGate: "something",
        evidence: "fixture",
      },
      {
        // PROOF clause claiming LIVE-GREEN but backed only by an estimator lane
        id: "INV-BAD-PROOF-ESTIMATOR",
        dimension: "demo",
        layer: "L1",
        tag: "PROOF",
        status: "LIVE-GREEN",
        backingGate: "serves neverificat; an LLM estimator judges experience — undecidable",
        evidence: "fixture",
      },
    ],
  };

  const v = checkRegistry(bad);
  assert.ok(v.length > 0, "expected at least one violation");

  const codes = new Set(v.map((x) => x.code));
  assert.ok(codes.has("U"), "expected an UNMAPPED (U) violation");
  assert.ok(codes.has("P"), "expected a PROOF-by-estimator (P) violation");

  const byId = (id) => v.filter((x) => x.id === id);
  assert.ok(byId("INV-BAD-UNMAPPED").some((x) => x.code === "U"), "UNMAPPED clause must be flagged");
  assert.ok(
    byId("INV-BAD-PROOF-ESTIMATOR").some((x) => x.code === "P"),
    "PROOF-by-estimator clause must be flagged",
  );
});

// ---------------------------------------------------------------------------
// (2) seeded-BAD: RED-UNMAPPED not in allow-list, duplicate id, counts drift
// ---------------------------------------------------------------------------
test("seeded-bad registry REDS: unaccounted RED + duplicate id + counts drift", () => {
  const bad = {
    acceptedDebt: [],
    counts: { "LIVE-GREEN": 0, PLANNED: 99, "RED-UNMAPPED": 0, total: 2 },
    clauses: [
      {
        id: "DUP",
        layer: "SYS",
        tag: "PROOF",
        status: "RED-UNMAPPED",
        backingGate: "NONE — unbuilt",
        evidence: "fixture",
      },
      {
        id: "DUP",
        layer: "SYS",
        tag: "PROOF",
        status: "PLANNED",
        backingGate: "NONE — unbuilt",
        evidence: "fixture",
      },
    ],
  };

  const v = checkRegistry(bad);
  const codes = new Set(v.map((x) => x.code));
  assert.ok(codes.has("R"), "expected an UNACCOUNTED-RED (R) violation");
  assert.ok(codes.has("D"), "expected a DUPLICATE-ID (D) violation");
  assert.ok(codes.has("C"), "expected a COUNTS-DRIFT (C) violation");
});

// ---------------------------------------------------------------------------
// (3) seeded-BAD: stale allow-list (acceptedDebt id points at a non-RED clause)
// ---------------------------------------------------------------------------
test("seeded-bad registry REDS: orphan/stale acceptedDebt entry", () => {
  const bad = {
    acceptedDebt: [{ id: "INV-OK", owner: "x", reason: "y" }],
    counts: { "LIVE-GREEN": 0, PLANNED: 1, "RED-UNMAPPED": 0, total: 1 },
    clauses: [
      {
        id: "INV-OK",
        layer: "SYS",
        tag: "PROOF",
        status: "PLANNED", // not RED-UNMAPPED → debt entry is stale
        backingGate: "NONE — unbuilt",
        evidence: "fixture",
      },
    ],
  };
  const v = checkRegistry(bad);
  assert.ok(v.some((x) => x.code === "A"), "expected an ORPHAN-DEBT (A) violation");
});

// ---------------------------------------------------------------------------
// (4) clean fixture → zero violations
// ---------------------------------------------------------------------------
test("clean registry has zero violations", () => {
  const v = checkRegistry(cleanRegistry());
  assert.equal(v.length, 0, `expected zero violations, got: ${JSON.stringify(v)}`);
});

test("RED-UNMAPPED in the allow-list is accepted (no R violation)", () => {
  const reg = {
    acceptedDebt: [{ id: "INV-DEBT", owner: "PM", reason: "scoped out" }],
    counts: { "LIVE-GREEN": 0, PLANNED: 0, "RED-UNMAPPED": 1, total: 1 },
    clauses: [
      {
        id: "INV-DEBT",
        layer: "SYS",
        tag: "PROOF",
        status: "RED-UNMAPPED",
        backingGate: "NONE EFFECTIVE — flagged debt",
        evidence: "fixture",
      },
    ],
  };
  const v = checkRegistry(reg);
  assert.equal(v.length, 0, `expected zero violations, got: ${JSON.stringify(v)}`);
});

// ---------------------------------------------------------------------------
// (5) end-to-end spawn: checker EXITS 1 on a written bad fixture, EXIT 0 on real registry
// ---------------------------------------------------------------------------
test("checker process exits 1 on a bad fixture file", () => {
  const dir = mkdtempSync(join(tmpdir(), "gate-reg-"));
  const badPath = join(dir, "bad.json");
  writeFileSync(
    badPath,
    JSON.stringify({
      acceptedDebt: [],
      clauses: [{ id: "X", layer: "SYS", tag: "PROOF", status: "LIVE-GREEN", backingGate: "NONE", evidence: "f" }],
    }),
  );
  const r = spawnSync(process.execPath, [CHECKER, badPath], { encoding: "utf8" });
  assert.equal(r.status, 1, `expected exit 1, got ${r.status}\n${r.stdout}\n${r.stderr}`);
  assert.match(r.stdout, /\[P\]/, "expected a [P] PROOF-by-estimator code in output");
});

test("checker process exits 0 on a clean fixture file and on the REAL registry", () => {
  const dir = mkdtempSync(join(tmpdir(), "gate-reg-"));
  const goodPath = join(dir, "good.json");
  writeFileSync(goodPath, JSON.stringify(cleanRegistry()));
  const rGood = spawnSync(process.execPath, [CHECKER, goodPath], { encoding: "utf8" });
  assert.equal(rGood.status, 0, `expected exit 0 on clean fixture, got ${rGood.status}\n${rGood.stdout}`);

  const rReal = spawnSync(process.execPath, [CHECKER, REAL_REGISTRY], { encoding: "utf8" });
  assert.equal(rReal.status, 0, `expected exit 0 on REAL registry, got ${rReal.status}\n${rReal.stdout}`);
  assert.match(rReal.stdout, /registry internally consistent, zero UNMAPPED/);
});

// sanity: summarize matches checkRegistry's internal counting
test("summarize counts statuses", () => {
  const s = summarize(cleanRegistry().clauses);
  assert.equal(s["LIVE-GREEN"], 1);
});
