/**
 * Phase-0 gate-COVERAGE checker (plan §2 F26 + Phase-0 EXIT "zero UNMAPPED").
 *
 * Parses the status+tag-stamped gate-coverage registry and FAILS (exit 1, prints
 * the offending rows) when the registry is internally INCONSISTENT — i.e. when it
 * would silently absorb a hole. It is the machine half of the registry: the .json
 * is the source of truth, this script is the gate that keeps it honest.
 *
 * Mirrors the style of tools/impeccable-filter.mjs / tools/chrome-en-grep.mjs:
 *   - no deps, ESM, node:fs only
 *   - exit 0 = clean, exit 1 = offending rows listed on stdout
 *   - the accepted-debt allow-list is PRINTED every run so debt is never silent
 *
 * Usage:
 *   node tools/gate-registry-check.mjs
 *   node tools/gate-registry-check.mjs path/to/registry.json   (self-test fixtures)
 *
 * FAIL conditions (any one → exit 1):
 *   (U) UNMAPPED            — a clause missing `status` or `tag`, or a value outside the enum.
 *   (R) UNACCOUNTED-RED     — a clause with status RED-UNMAPPED whose id is NOT in `acceptedDebt`.
 *   (P) PROOF-BY-ESTIMATOR  — a PROOF-tagged clause whose ONLY backing is an estimator /
 *                             `neverificat` / `verifică sursa` lane (audit F43: an estimator
 *                             backing a PROOF clause is RED on coverage). LIVE-GREEN PROOF rows
 *                             must name a real decidable gate (a CI test / job / grep),
 *                             never "NONE"/estimator-only.
 *   (D) DUPLICATE-ID        — two clauses share an id.
 *   (C) COUNTS-DRIFT        — the registry's `counts` block disagrees with the actual array
 *                             (so the board header can't silently overclaim).
 *   (A) ORPHAN-DEBT         — an `acceptedDebt` id that no clause carries, OR a debt entry
 *                             pointing at a clause that is NOT RED-UNMAPPED (stale allow-list).
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_REGISTRY = resolve(
  HERE,
  "..",
  "docs",
  "superpowers",
  "plans",
  "2026-06-15-gate-coverage-registry.json",
);

const LAYERS = new Set(["L1", "L2", "SYS"]);
const TAGS = new Set(["PROOF", "ESTIMATOR"]);
const STATUSES = new Set(["LIVE-GREEN", "PLANNED", "RED-UNMAPPED"]);

/**
 * Phrases that signal an ESTIMATOR / non-decidable lane. If a PROOF-tagged clause is
 * LIVE-GREEN but its backingGate is described ONLY in these terms (and names no real
 * gate), it is a PROOF-backed-by-estimator violation (audit F43).
 */
const ESTIMATOR_LANE_RE =
  /\b(neverificat|verific[aă]\s+sursa|estimator(?!\s+green)|judges?\s+experience|second\s+independent\s+llm|undecidable|process\s+step|not\s+a\s+ci\s+gate|empirical\s+measurement|human\/council)\b/i;

/**
 * Phrases that name a REAL decidable gate. A LIVE-GREEN PROOF clause must contain at
 * least one of these in its backingGate (a CI job / test / grep / blocking lint).
 */
const REAL_GATE_RE =
  /\b(gradle\s*:?\s*check|test\.yml|\.spec\.ts|\.test\.(tsx?|mjs)|Test\.kt|vitest|playwright|practice-e2e|lesson-gates|backup-tooling|verifier-deps-loud-red|grep\b|blocking|job\b|harness)\b/i;

/** "NONE" / "PARTIAL" / "NONE EFFECTIVE" prefixes = no live decidable gate today. */
const NO_LIVE_GATE_RE = /^\s*(NONE|PARTIAL)\b/i;

export function checkRegistry(registry) {
  const violations = [];
  const clauses = Array.isArray(registry.clauses) ? registry.clauses : [];
  const acceptedDebt = Array.isArray(registry.acceptedDebt) ? registry.acceptedDebt : [];
  const acceptedDebtIds = new Set(acceptedDebt.map((d) => d.id));

  if (clauses.length === 0) {
    violations.push({ code: "EMPTY", id: "(registry)", detail: "clauses array is empty or missing" });
    return violations;
  }

  // (D) duplicate ids
  const seen = new Set();
  for (const cl of clauses) {
    const id = cl && cl.id;
    if (id == null) {
      violations.push({ code: "U", id: "(missing id)", detail: "clause has no id" });
      continue;
    }
    if (seen.has(id)) {
      violations.push({ code: "D", id, detail: "duplicate id" });
    }
    seen.add(id);
  }

  // per-clause checks
  for (const cl of clauses) {
    const id = (cl && cl.id) || "(missing id)";

    // (U) UNMAPPED — missing or out-of-enum status / tag / layer
    if (!cl.status) {
      violations.push({ code: "U", id, detail: "missing status (UNMAPPED)" });
    } else if (!STATUSES.has(cl.status)) {
      violations.push({ code: "U", id, detail: `status "${cl.status}" not in enum {LIVE-GREEN,PLANNED,RED-UNMAPPED}` });
    }
    if (!cl.tag) {
      violations.push({ code: "U", id, detail: "missing tag (UNMAPPED)" });
    } else if (!TAGS.has(cl.tag)) {
      violations.push({ code: "U", id, detail: `tag "${cl.tag}" not in enum {PROOF,ESTIMATOR}` });
    }
    if (!cl.layer) {
      violations.push({ code: "U", id, detail: "missing layer (UNMAPPED)" });
    } else if (!LAYERS.has(cl.layer)) {
      violations.push({ code: "U", id, detail: `layer "${cl.layer}" not in enum {L1,L2,SYS}` });
    }

    // (R) UNACCOUNTED-RED — RED-UNMAPPED not in the allow-list
    if (cl.status === "RED-UNMAPPED" && !acceptedDebtIds.has(id)) {
      violations.push({
        code: "R",
        id,
        detail: "status RED-UNMAPPED but id is NOT in acceptedDebt allow-list (would silently absorb a hole)",
      });
    }

    // (P) PROOF-BY-ESTIMATOR — a PROOF clause that claims LIVE-GREEN but is backed only
    // by an estimator/neverificat lane, OR names no real decidable gate.
    if (cl.tag === "PROOF" && cl.status === "LIVE-GREEN") {
      const bg = String(cl.backingGate || "");
      const estimatorOnly = ESTIMATOR_LANE_RE.test(bg);
      const namesRealGate = REAL_GATE_RE.test(bg);
      const noLiveGate = NO_LIVE_GATE_RE.test(bg);
      if (estimatorOnly || noLiveGate || !namesRealGate) {
        violations.push({
          code: "P",
          id,
          detail:
            "PROOF clause marked LIVE-GREEN but backingGate is estimator-only / names no real decidable gate" +
            (estimatorOnly ? " [estimator lane phrase]" : "") +
            (noLiveGate ? " [NONE/PARTIAL prefix]" : "") +
            (!namesRealGate ? " [no CI test/job/grep named]" : ""),
        });
      }
    }
  }

  // (A) ORPHAN-DEBT — allow-list entries that don't point at a real RED-UNMAPPED clause
  const clausesById = new Map(clauses.map((c) => [c.id, c]));
  for (const d of acceptedDebt) {
    const target = clausesById.get(d.id);
    if (!target) {
      violations.push({ code: "A", id: d.id, detail: "acceptedDebt id matches no clause (stale allow-list)" });
    } else if (target.status !== "RED-UNMAPPED") {
      violations.push({
        code: "A",
        id: d.id,
        detail: `acceptedDebt entry points at a clause whose status is "${target.status}", not RED-UNMAPPED (stale allow-list)`,
      });
    }
  }

  // (C) COUNTS-DRIFT — declared counts vs actual
  if (registry.counts) {
    const actual = { "LIVE-GREEN": 0, PLANNED: 0, "RED-UNMAPPED": 0 };
    for (const cl of clauses) if (cl.status in actual) actual[cl.status]++;
    const declared = registry.counts;
    for (const k of ["LIVE-GREEN", "PLANNED", "RED-UNMAPPED"]) {
      if (declared[k] != null && declared[k] !== actual[k]) {
        violations.push({
          code: "C",
          id: "(counts)",
          detail: `declared counts.${k}=${declared[k]} but actual=${actual[k]}`,
        });
      }
    }
    if (declared.total != null && declared.total !== clauses.length) {
      violations.push({
        code: "C",
        id: "(counts)",
        detail: `declared counts.total=${declared.total} but actual=${clauses.length}`,
      });
    }
  }

  return violations;
}

export function summarize(clauses) {
  const c = { "LIVE-GREEN": 0, PLANNED: 0, "RED-UNMAPPED": 0 };
  for (const cl of clauses) if (cl.status in c) c[cl.status]++;
  return c;
}

function loadRegistry(path) {
  return JSON.parse(readFileSync(path, "utf8"));
}

function main(argv) {
  const path = argv[2] ? resolve(process.cwd(), argv[2]) : DEFAULT_REGISTRY;
  let registry;
  try {
    registry = loadRegistry(path);
  } catch (e) {
    process.stdout.write(`gate-registry-check: FAILED to load ${path}: ${e.message}\n`);
    process.exit(1);
  }

  const clauses = Array.isArray(registry.clauses) ? registry.clauses : [];
  const counts = summarize(clauses);

  // Always print the accepted-debt allow-list so debt is never silently absorbed.
  const debt = Array.isArray(registry.acceptedDebt) ? registry.acceptedDebt : [];
  process.stdout.write(`gate-registry-check: ${path}\n`);
  process.stdout.write(
    `  clauses=${clauses.length}  LIVE-GREEN=${counts["LIVE-GREEN"]}  ` +
      `PLANNED=${counts.PLANNED}  RED-UNMAPPED=${counts["RED-UNMAPPED"]}\n`,
  );
  process.stdout.write(`  accepted-debt allow-list (${debt.length} ids — never laundered green):\n`);
  for (const d of debt) {
    process.stdout.write(`    - ${d.id}  [owner: ${d.owner || "?"}]\n`);
  }

  const violations = checkRegistry(registry);
  if (violations.length === 0) {
    process.stdout.write(`gate-registry-check: OK — registry internally consistent, zero UNMAPPED.\n`);
    process.exit(0);
  }

  process.stdout.write(`gate-registry-check: ${violations.length} violation(s):\n`);
  for (const v of violations) {
    process.stdout.write(`  [${v.code}] ${v.id}: ${v.detail}\n`);
  }
  process.exit(1);
}

// Run main only when invoked directly (not when imported by tests).
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main(process.argv);
}
