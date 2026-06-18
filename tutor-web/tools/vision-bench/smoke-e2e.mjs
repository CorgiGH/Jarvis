/**
 * smoke-e2e.mjs — the 1-FAMILY E2E integration smoke for the vision-bench cluster (T8/T12/T13/T14).
 *
 * Picks ONE family (seq-array, the simplest), ONE BAD defect (prop-mode: duplicated-value-token at
 * frame 0, where the array [5,3,8,1,6] is all-distinct so copying cell 0's "5" onto cell 1 yields a
 * VISIBLE duplicate "5,5,8,1,6" — verified by eye) + its PAIRED GOOD-clean frame (same route +
 * frameIndex, no defect), and runs the FULL pipeline:
 *
 * NOTE on defect choice: missing-sorted-prefix-paint was tried first but is a NO-OP-LOOKING defect at
 * seq-array frame 5 (the sorted-prefix cell there is border-only / white-filled, so stripping fills is
 * invisible — a real §8 label-audit finding). duplicated-value-token@f0 is unambiguously visible.
 *   capture (with prop injection) → det-probe → judge(K=3) → score → report
 * on JUST those 2 frames (NOT the full corpus — Phase-1 rule).
 *
 * Confirms (spec E2E acceptance):
 *   1. a report file is produced,
 *   2. the §6.5 reconciliation assert did NOT falsely fire (seq-array is unwired ⇒ expected blind=true
 *      ⇒ empirical blind=true ⇒ match),
 *   3. the GOOD frame is a NEGATIVE in the matrix (label GOOD, scored),
 *   4. no answer token leaked into either PNG (the capturer's assertNoAnswerLeak would have thrown).
 *
 * Usage: node smoke-e2e.mjs [--capture-only] [--k 3]
 * Prereq: dev server on :5173 (capture phase) + the free claude relay on PATH (judge phase).
 */
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { existsSync, readFileSync } from "node:fs";
import { runBench } from "./orchestrator.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRATCH = resolve(HERE, "../../../build-review/vision-bench-smoke-e2e");

function argVal(flag, def) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : def;
}

const SEQ_MOUNT = {
  family: "seq-array", route: "/tutor/viz-demo", rootTestid: "seq-array-root",
  scrubberTestid: "seq-array-scrubber", scrubberIndex: 1, predictGate: false,
  gateCoverage: "unwired", cropProfile: "seq-array", skin: null, rubricKey: "seq-array",
};

// The chosen BAD (prop) + its paired GOOD-clean, both at frame 0.
const ENTRIES = [
  {
    ...SEQ_MOUNT, id: "duplicated-value-token", label: "BAD", expectedDetGateBlind: true,
    frameIndices: [0], injection: { mode: "prop" },
  },
  {
    ...SEQ_MOUNT, id: "good-clean__seq-array__f0", label: "GOOD", expectedDetGateBlind: null,
    frameIndices: [0], injection: { mode: "none" },
  },
];

// Catalog the scorer uses for the §6.5 reconciliation (only the BAD carries an expectation).
const CATALOG_FOR_SCORE = [
  { id: "duplicated-value-token", label: "BAD", family: "seq-array", expectedDetGateBlind: true },
];

async function main() {
  const captureOnly = process.argv.includes("--capture-only");
  const k = Number(argVal("--k", "3"));
  const out = await runBench({
    scratchDir: SCRATCH,
    date: "smoke",
    entries: ENTRIES,
    catalogForScore: CATALOG_FOR_SCORE,
    phase: captureOnly ? "capture" : "both",
    k,
    poolSize: 3,
    reconcileMode: "fail", // the smoke must show the assert does NOT falsely fire
    runGates: false, // seq-array is unwired; no gate to run (the unwired path is exercised in det-probe)
    extras: {
      runMode: "E2E smoke (1 family, 1 BAD prop + 1 paired GOOD)",
      independenceCheck: "deferred to T15 council (not part of this smoke)",
      fidelityFraction: "deferred (T4 owns the md5 prop-vs-source validation)",
      adjudicationDisagreement: "deferred (T7 label-audit owns the human/second-judge pass)",
    },
  });

  console.log("\n===== E2E SMOKE ACCEPTANCE =====");
  // 1. report produced
  const reportOk = !captureOnly && out.reportMd && existsSync(out.reportMd);
  console.log(`1. report produced            : ${captureOnly ? "SKIPPED (capture-only)" : reportOk ? "YES → " + out.reportMd : "NO"}`);

  if (captureOnly) {
    // verify the manifest + the prop mutated + 2 PNGs exist
    const m = JSON.parse(readFileSync(out.manifestPath, "utf8"));
    const bad = m.frames.find((f) => f.label === "BAD");
    console.log(`   manifest frames           : ${m.frames.length}`);
    console.log(`   BAD prop mutated count     : ${bad ? bad.injectionMutated : "n/a"}`);
    for (const f of m.frames) console.log(`   PNG ${f.label.padEnd(4)} f${f.frameIndex} exists=${existsSync(f.path)} → ${f.path}`);
    process.exit(0);
  }

  const results = JSON.parse(readFileSync(out.reportJson, "utf8"));
  // 2. reconciliation did NOT falsely fire
  const recOk = !results.reconciliation.failLoud && results.reconciliation.mismatches.length === 0;
  console.log(`2. reconciliation not fired   : ${recOk ? "YES (matches=" + results.reconciliation.matches + ", mismatches=0)" : "NO — " + JSON.stringify(results.reconciliation.mismatches)}`);
  // 3. GOOD frame is a negative
  const goodRow = results.rows.find((r) => r.label === "GOOD" && r.scored);
  const goodNeg = goodRow && goodRow.labelGroup === "GOOD";
  console.log(`3. GOOD frame is a negative   : ${goodNeg ? "YES (decision=" + goodRow.decision + ", counted as " + goodRow.labelGroup + ")" : "NO"}`);
  // 4. no answer leak (the capturer throws on leak; reaching here with 2 frames captured ⇒ no leak)
  const both = results.meta.capturedFrames === 2;
  console.log(`4. no answer leak (2 PNGs ok) : ${both ? "YES (capturer's assertNoAnswerLeak passed for both)" : "frames=" + results.meta.capturedFrames}`);

  // The 2x2 cells
  const m2 = results.matrix2.overall;
  console.log(`\n2x2 (Matrix-2 overall): TP=${m2.tp} FN=${m2.fn} FP=${m2.fp} TN=${m2.tn}`);
  const badRow = results.rows.find((r) => r.label === "BAD" && r.scored);
  console.log(`BAD  decision=${badRow ? badRow.decision : "?"} k(P/F/U)=${badRow ? badRow.kPass + "/" + badRow.kFail + "/" + badRow.kUnparseable : "?"} empiricalBlind=${badRow ? badRow.empiricalDetGateBlind : "?"}`);
  console.log(`GOOD decision=${goodRow ? goodRow.decision : "?"} k(P/F/U)=${goodRow ? goodRow.kPass + "/" + goodRow.kFail + "/" + goodRow.kUnparseable : "?"}`);

  const allOk = reportOk && recOk && goodNeg && both;
  console.log(`\nSMOKE ${allOk ? "GREEN" : "RED"}`);
  process.exit(allOk ? 0 : 1);
}

main().catch((e) => { console.error("[smoke-e2e] FATAL:", e); process.exit(1); });
