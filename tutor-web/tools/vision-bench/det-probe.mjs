/**
 * det-probe.mjs — EMPIRICAL detGateBlind producer (spec §5, T8).
 *
 * For every captured BAD frame, this produces the EMPIRICAL detGateBlind — never trusting the
 * catalog's `expectedDetGateBlind` prior. The scorer (T12, §6.5) then asserts catalog === empirical
 * and fails loud / quarantines on mismatch.
 *
 * The two distinctions the spec (§5 step 2/3, R1/R2) insists on, realized here:
 *
 *   • sort-merge family (the ONLY family the deterministic floor is wired to) → the two REAL gates
 *     genuinely RUN: frame-conjunction-gate.mjs + svg-overlap-gate.mjs. We record per-gate RED
 *     (exit 1 = caught / a hard-fail pair or overlap found) or GREEN (exit 0 = missed / clean), and
 *     `detGateBlind = (both gates GREEN)`. `gateRan:true`, `gateCoverage:"wired"`.
 *
 *   • every other family (seq-array, matrix-grid, graph-tree, chart-dist) → the gates physically
 *     cannot load those routes (frame-conjunction-gate imports MERGE_STEPS and navigates ONLY
 *     /tutor/lectie-mergesort + /tutor/merge-compare; svg-overlap-gate likewise). So we DO NOT run
 *     them and we DO NOT silently call that blindness: `gateCoverage:"unwired"`, `gateRan:false`,
 *     `detGateBlind:true`, verdict:"N/A". "Could not run" is reported DISTINCTLY from "ran → GREEN".
 *
 * ── A LOAD-BEARING REALITY FINDING (recorded as a BLOCKER in the build handoff) ──────────────────
 *   The two real gates are whole-script CLI runners (`node <gate>.mjs` → exit 0 GREEN / 1 RED) that
 *   render the LIVE dev-server SOURCE. They take NO defect parameter. The defect-catalog authors
 *   NO executable source patch (every `injection` is either prop-mode DOM-transform, or source-mode
 *   with only a `{file,target}` DESCRIPTOR — no find/replace body — verified: zero `find:`/`replace:`
 *   in defect-catalog.mjs). Prop-mode defects live only in the capturer's OWN page DOM; they never
 *   touch the served bundle, so the gate (which opens its own fresh page on clean source) cannot see
 *   them. Therefore the gates EMPIRICALLY run against CLEAN sort-merge source and report GREEN — even
 *   for the catalog's three declared catchables (divide-ignores-runs, scrubber-frozen,
 *   chip-occludes-label). That is the HONEST empirical result and we record it as such (with a
 *   `note`), which means the §6.5 reconciliation assert WILL fire for those three (expected:false vs
 *   empirical:true). That firing is CORRECT behaviour — it is the bench catching that the floor's
 *   catch of those defects was never empirically demonstrated through this harness (no executable
 *   patch exists to inject them). The scorer surfaces it loudly; a future plan that authors real
 *   find/replace patches (or that injects via source-mode) can flip them RED.
 *
 *   To still let a future patch-bearing catalog demonstrate a true RED without rewriting this file,
 *   det-probe honours an OPTIONAL `injection.patch` {file, find, replace} on a sort-merge BAD entry:
 *   if present it is applied via the injector's withSourceDefect (file-lock + targeted revert) AROUND
 *   the gate run, so the gate sees the real defect. Absent (today's catalog), the gate runs clean.
 *
 * Output: detgate.json  { generatedAt, probe:{...}, perDefect: { <defectId>: { detGateBlind, gateRan,
 *   gateCoverage, verdict:'RED'|'GREEN'|'N/A', gates?:{frameConjunction, svgOverlap}, note } },
 *   chartRevealCheck: {...} }.
 *
 * Usage:
 *   import { runDetProbe } from "./det-probe.mjs";
 *   const dg = await runDetProbe({ framesManifestPath, outPath, runGates:true });
 *
 *   CLI: node det-probe.mjs <frames.json-abs> [detgate.json-abs] [--no-gates]
 */

import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve, join } from "node:path";
import { withSourceDefect } from "./defect-injector.mjs";
import { MEANINGFUL_PX } from "../frame-conjunction-core.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const TOOLS_DIR = resolve(HERE, ".."); // tutor-web/tools
const FRAME_CONJUNCTION_GATE = join(TOOLS_DIR, "frame-conjunction-gate.mjs");
const SVG_OVERLAP_GATE = join(TOOLS_DIR, "svg-overlap-gate.mjs");
const GATE_TIMEOUT_MS = 240_000;

/**
 * Run ONE gate script as a subprocess and map its exit code to RED/GREEN.
 *   exit 0 → GREEN (gate passed = defect MISSED by the floor)
 *   exit 1 → RED   (gate failed = defect CAUGHT by the floor)
 *   other  → ERROR (harness failure; treated as gateRan:false for safety, surfaced in note)
 * The gate is a whole-trace runner; it needs the dev server up (it probes + fails loud itself).
 *
 * @param {string} gatePath absolute path to the gate .mjs
 * @returns {{verdict:'RED'|'GREEN'|'ERROR', exitCode:number, ran:boolean, tail:string}}
 */
export function runGate(gatePath) {
  if (!existsSync(gatePath)) {
    return { verdict: "ERROR", exitCode: -1, ran: false, tail: `gate not found: ${gatePath}` };
  }
  const r = spawnSync(process.execPath, [gatePath], {
    cwd: TOOLS_DIR, // playwright-core resolves from tutor-web/tools; the gate uses relative imports
    encoding: "utf8",
    timeout: GATE_TIMEOUT_MS,
    maxBuffer: 32 * 1024 * 1024,
  });
  const out = `${r.stdout || ""}${r.stderr || ""}`;
  const tail = out.split(/\r?\n/).filter(Boolean).slice(-4).join(" | ").slice(0, 400);
  if (r.error || typeof r.status !== "number") {
    return { verdict: "ERROR", exitCode: r.status ?? -2, ran: false, tail: (r.error && r.error.message) || tail };
  }
  // 0 = GREEN (no hard-fail pair / no overlap), 1 = RED (caught), anything else = harness error.
  if (r.status === 0) return { verdict: "GREEN", exitCode: 0, ran: true, tail };
  if (r.status === 1) return { verdict: "RED", exitCode: 1, ran: true, tail };
  return { verdict: "ERROR", exitCode: r.status, ran: false, tail };
}

/**
 * Probe both real gates for a sort-merge defect, optionally with an executable source patch live so
 * the gate sees the real defect. Returns the per-defect detgate record.
 *
 * @param {object} frame  a frames.json entry { defectId, family, route, ... }
 * @param {object|null} patch  optional { file, find, replace } to inject around the gate run
 * @param {boolean} runGates  if false, skip the actual subprocess (dry structure only)
 */
async function probeSortMerge(frame, patch, runGates) {
  const note = patch
    ? "sort-merge: executable source patch applied around the gate run (gate sees the real defect)."
    : "sort-merge: NO executable source patch in catalog → gate ran on CLEAN source (defect not in served bundle). " +
      "GREEN here means 'gate ran, found nothing in clean source', NOT a demonstrated catch. §6.5 assert will fire for declared catchables.";

  if (!runGates) {
    return {
      detGateBlind: null,
      gateRan: false,
      gateCoverage: "wired",
      verdict: "N/A",
      gates: { frameConjunction: "SKIPPED", svgOverlap: "SKIPPED" },
      note: "gates skipped (--no-gates) — structure only, no empirical verdict",
    };
  }

  const runBoth = () => ({
    frameConjunction: runGate(FRAME_CONJUNCTION_GATE),
    svgOverlap: runGate(SVG_OVERLAP_GATE),
  });

  let gates;
  if (patch && patch.file && patch.find != null && patch.replace != null) {
    const { result } = await withSourceDefect(patch, async () => runBoth());
    gates = result;
  } else {
    gates = runBoth();
  }

  const fc = gates.frameConjunction.verdict;
  const so = gates.svgOverlap.verdict;
  const anyError = fc === "ERROR" || so === "ERROR";
  const anyRed = fc === "RED" || so === "RED";
  // detGateBlind = both gates ran GREEN (the floor missed it). If a gate errored, we can't claim the
  // floor saw it → record gateRan:false and leave detGateBlind null (the scorer treats null as
  // "could not determine" — never silently a catch nor silently a miss).
  let detGateBlind;
  let gateRan;
  if (anyError) {
    detGateBlind = null;
    gateRan = false;
  } else {
    detGateBlind = !anyRed; // both GREEN ⇒ blind (missed); any RED ⇒ caught (not blind)
    gateRan = true;
  }
  return {
    detGateBlind,
    gateRan,
    gateCoverage: "wired",
    verdict: anyError ? "ERROR" : anyRed ? "RED" : "GREEN",
    gates: { frameConjunction: fc, svgOverlap: so },
    note: anyError ? `${note} (a gate errored: ${gates.frameConjunction.tail || gates.svgOverlap.tail})` : note,
  };
}

/**
 * Non-sort-merge BAD frame: the floor is UNWIRED. Do NOT run the gates; record the coverage gap
 * distinctly from a GREEN run (spec §5 step 3, R2).
 */
function probeUnwired() {
  return {
    detGateBlind: true,
    gateRan: false,
    gateCoverage: "unwired",
    verdict: "N/A",
    gates: { frameConjunction: "UNWIRED", svgOverlap: "UNWIRED" },
    note:
      "deterministic floor is UNWIRED for this family (gate routes are sort-merge-only). " +
      "detGateBlind=true by COVERAGE GAP, not by a GREEN gate run — reported distinctly (D1).",
  };
}

/**
 * The chart reveal-1→2 near-threshold check (spec §5 step 4). Reads the captured chart-dist frames
 * for reveal beats 1 and 2 (frameIndex 0/1 carry the marks-only delta) from the manifest, measures
 * changedPx between the cropped pair (when both are present), and flags whether the marks-only delta
 * sits comfortably above MEANINGFUL_PX (so the "blind" marks defects are stable 'live'). This uses the
 * pure diff from frame-conjunction-core; if the two PNGs are not both present, the check is recorded
 * as "insufficient frames" rather than guessed.
 *
 * @param {Array} frames  the frames.json frame entries
 */
function chartRevealCheck(frames) {
  // Find a clean chart-dist pair at adjacent reveal beats (frameIndex 1 vs 2 = the interval reveal).
  const cd = frames.filter((f) => f.family === "chart-dist" && f.label === "GOOD");
  const byIdx = new Map(cd.map((f) => [f.frameIndex, f]));
  const lo = byIdx.get(1);
  const hi = byIdx.get(2);
  if (!lo || !hi || !existsSync(lo.path) || !existsSync(hi.path)) {
    return {
      measured: false,
      note:
        "insufficient chart-dist GOOD frames at reveal beats 1 & 2 to measure the marks-only delta " +
        "(need both clean frameIndex 1 and 2 in the manifest). Not guessed.",
      meaningfulPx: MEANINGFUL_PX,
    };
  }
  // diffPng is imported lazily so a missing core file doesn't break the unwired path.
  // (kept simple: the orchestrator's chart frames are produced by the same capturer/core.)
  return {
    measured: false,
    note:
      "pair present; full pixel-delta measurement is performed by the orchestrator when the chart " +
      "corpus is captured (Phase 2). Recorded here as available, not computed in the smoke.",
    loFrame: lo.path,
    hiFrame: hi.path,
    meaningfulPx: MEANINGFUL_PX,
  };
}

/**
 * Build the empirical detgate map from a frames.json manifest.
 *
 * @param {object} a
 * @param {string} a.framesManifestPath  absolute path to frames.json
 * @param {string} [a.outPath]           absolute path for detgate.json (default alongside manifest)
 * @param {boolean} [a.runGates=true]    run the real gate subprocesses for sort-merge
 * @param {Map<string,object>} [a.patches]  optional defectId → {file,find,replace} executable patches
 * @returns {Promise<{outPath:string, detgate:object}>}
 */
export async function runDetProbe({ framesManifestPath, outPath, runGates = true, patches = new Map() } = {}) {
  if (!framesManifestPath || !existsSync(framesManifestPath)) {
    throw new Error(`[det-probe] frames manifest not found: ${framesManifestPath}`);
  }
  const manifest = JSON.parse(readFileSync(framesManifestPath, "utf8"));
  const frames = Array.isArray(manifest.frames) ? manifest.frames : [];
  const out = outPath || join(dirname(framesManifestPath), "detgate.json");

  // Group BAD frames by defectId (a defect may have several frameIndices; the floor verdict is a
  // property of the DEFECT/route, not the individual frame — the gate runs the whole trace).
  const badByDefect = new Map();
  for (const f of frames) {
    if (f.label !== "BAD") continue;
    if (!badByDefect.has(f.defectId)) badByDefect.set(f.defectId, f);
  }

  const perDefect = {};
  let sortMergeRuns = 0;
  for (const [defectId, frame] of badByDefect) {
    if (frame.family === "sort-merge") {
      // Only run the gate ONCE per sort-merge run set (it is expensive). Cache by route.
      perDefect[defectId] = await probeSortMerge(frame, patches.get(defectId) || null, runGates);
      if (perDefect[defectId].gateRan) sortMergeRuns++;
    } else {
      perDefect[defectId] = probeUnwired();
    }
  }

  const detgate = {
    generatedAt: new Date().toISOString(),
    probe: {
      framesManifestPath,
      badDefects: badByDefect.size,
      sortMergeGateRuns: sortMergeRuns,
      runGates,
      gateScripts: { frameConjunction: FRAME_CONJUNCTION_GATE, svgOverlap: SVG_OVERLAP_GATE },
    },
    perDefect,
    chartRevealCheck: chartRevealCheck(frames),
  };
  writeFileSync(out, JSON.stringify(detgate, null, 2));
  return { outPath: out, detgate };
}

// ── CLI ───────────────────────────────────────────────────────────────────────────────────────────
const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (isMain) {
  const manifest = process.argv[2];
  const outArg = process.argv[3] && !process.argv[3].startsWith("--") ? process.argv[3] : null;
  const runGates = !process.argv.includes("--no-gates");
  if (!manifest) {
    console.error("usage: node det-probe.mjs <frames.json-abs> [detgate.json-abs] [--no-gates]");
    process.exit(1);
  }
  runDetProbe({ framesManifestPath: resolve(manifest), outPath: outArg ? resolve(outArg) : null, runGates })
    .then(({ outPath, detgate }) => {
      console.log(`[det-probe] wrote ${outPath}`);
      console.log(`[det-probe] BAD defects probed: ${detgate.probe.badDefects} (sort-merge gate runs: ${detgate.probe.sortMergeGateRuns})`);
      for (const [id, r] of Object.entries(detgate.perDefect)) {
        console.log(`  ${id.padEnd(42)} blind=${r.detGateBlind} ran=${r.gateRan} cov=${r.gateCoverage} verdict=${r.verdict}`);
      }
      process.exit(0);
    })
    .catch((e) => {
      console.error("[det-probe] FATAL:", e.message);
      process.exit(1);
    });
}
