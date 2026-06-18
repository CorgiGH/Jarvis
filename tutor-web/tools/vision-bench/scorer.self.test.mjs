/**
 * scorer.self.test.mjs — PURE self-test for scorer.mjs + report-emitter.mjs (no browser, no relay).
 * Mirrors the unit style of frameConjunction.self.test.ts. Run: node scorer.self.test.mjs
 * Builds a synthetic manifest + detgate + verdicts and asserts the matrices, Wilson CIs, the §6.5
 * reconciliation assert (both clean and mismatch cases), the abstention sensitivity, and that a report
 * file is produced.
 */
import { scoreBench, wilson, frameDecision } from "./scorer.mjs";
import { emitReport } from "./report-emitter.mjs";
import { reduceMajority } from "./verdict-parser.mjs";
import { rmSync, existsSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

let pass = 0, fail = 0;
const eq = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${ok ? "" : ` — got ${JSON.stringify(got)} want ${JSON.stringify(want)}`}`);
  ok ? pass++ : fail++;
};
const approx = (name, got, want, tol = 0.01) => {
  const ok = got != null && Math.abs(got - want) <= tol;
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${ok ? "" : ` — got ${got} want ~${want}`}`);
  ok ? pass++ : fail++;
};
const truthy = (name, v) => { const ok = !!v; console.log(`${ok ? "PASS" : "FAIL"} ${name}`); ok ? pass++ : fail++; };

// ── wilson sanity ──
const w = wilson(8, 10);
approx("wilson p=0.8", w.p, 0.8);
truthy("wilson lo<p<hi", w.lo < w.p && w.p < w.hi);
eq("wilson n=0 → null", wilson(0, 0), { p: null, lo: null, hi: null, n: 0 });

// ── frameDecision: UNPARSEABLE folds into FAIL (headline) but drops in sensitivity ──
const pr = [{ verdict: "PASS" }, { verdict: "PASS" }, { verdict: "UNPARSEABLE" }];
eq("headline: 2 PASS + 1 UNPARSEABLE ⇒ PASS (2 vs 1)", frameDecision(pr, "fail-counted").decision, "PASS");
const pr2 = [{ verdict: "PASS" }, { verdict: "UNPARSEABLE" }, { verdict: "UNPARSEABLE" }];
eq("headline: 1 PASS + 2 UNPARSEABLE ⇒ FAIL (1 vs 2)", frameDecision(pr2, "fail-counted").decision, "FAIL");
eq("dropped: 1 PASS + 2 UNPARSEABLE ⇒ PASS (only the parsed vote counts)", frameDecision(pr2, "dropped").decision, "PASS");
eq("reduceMajority parity", reduceMajority(pr2).decision, "FAIL");

// ── Synthetic corpus: 1 unwired BAD (seq) caught, 1 unwired BAD missed, 1 GOOD pass, 1 GOOD fail (FP),
//    + 1 wired sort-merge BAD with a RECONCILIATION MISMATCH (expected catchable=false, empirical blind=true) ──
const mkFrame = (path, label, family, defectId, frameIndex) => ({ path, label, family, defectId, frameIndex, skin: null, cropProfile: family });
const framesManifest = {
  frames: [
    mkFrame("/s/BAD_seq_caught.png", "BAD", "seq-array", "missing-sorted-prefix-paint", 5),
    mkFrame("/s/BAD_seq_missed.png", "BAD", "seq-array", "duplicated-value-token", 0),
    mkFrame("/s/GOOD_seq_pass.png", "GOOD", "seq-array", null, 5),
    mkFrame("/s/GOODbl_seq_fp.png", "GOOD-borderline", "seq-array", null, 10),
    mkFrame("/s/BAD_sm_mismatch.png", "BAD", "sort-merge", "divide-ignores-runs", 1),
  ],
};
const detgate = {
  perDefect: {
    "missing-sorted-prefix-paint": { detGateBlind: true, gateRan: false, gateCoverage: "unwired", verdict: "N/A" },
    "duplicated-value-token": { detGateBlind: true, gateRan: false, gateCoverage: "unwired", verdict: "N/A" },
    // empirical blind=true on clean source (no executable patch) but catalog expects catchable=false ⇒ MISMATCH
    "divide-ignores-runs": { detGateBlind: true, gateRan: true, gateCoverage: "wired", verdict: "GREEN", note: "clean source" },
  },
  chartRevealCheck: { measured: false, note: "n/a in unit", meaningfulPx: 80 },
};
const FAIL3 = [{ verdict: "FAIL" }, { verdict: "FAIL" }, { verdict: "FAIL" }];
const PASS3 = [{ verdict: "PASS" }, { verdict: "PASS" }, { verdict: "PASS" }];
const verdicts = [
  { imagePath: "/s/BAD_seq_caught.png", perRun: FAIL3 },   // TP
  { imagePath: "/s/BAD_seq_missed.png", perRun: PASS3 },   // FN
  { imagePath: "/s/GOOD_seq_pass.png", perRun: PASS3 },    // TN
  { imagePath: "/s/GOODbl_seq_fp.png", perRun: FAIL3 },    // FP
  { imagePath: "/s/BAD_sm_mismatch.png", perRun: FAIL3 },  // would be TP but QUARANTINED on mismatch
];
const catalog = [
  { id: "missing-sorted-prefix-paint", label: "BAD", family: "seq-array", expectedDetGateBlind: true },
  { id: "duplicated-value-token", label: "BAD", family: "seq-array", expectedDetGateBlind: true },
  { id: "divide-ignores-runs", label: "BAD", family: "sort-merge", expectedDetGateBlind: false }, // mismatch vs empirical true
];

// reconcileMode=fail → failLoud true, but the sort-merge mismatch is NOT quarantined (stays in matrix).
const failRes = scoreBench({ framesManifest, detgate, verdicts, catalog, reconcileMode: "fail" });
truthy("fail mode: reconciliation.failLoud true", failRes.reconciliation.failLoud === true);
eq("fail mode: 1 mismatch", failRes.reconciliation.mismatches.length, 1);

// reconcileMode=quarantine → the mismatched sort-merge BAD is excluded from BOTH matrices.
const res = scoreBench({ framesManifest, detgate, verdicts, catalog, reconcileMode: "quarantine" });
truthy("quarantine mode: not failLoud", res.reconciliation.failLoud === false);
eq("quarantine mode: 1 quarantined", res.reconciliation.quarantined.length, 1);
// Matrix-2 over the seq-array frames only (sort-merge BAD quarantined): TP=1 FN=1 FP=1 TN=1.
const m2 = res.matrix2.overall;
eq("Matrix-2 overall {tp,fn,fp,tn}", { tp: m2.tp, fn: m2.fn, fp: m2.fp, tn: m2.tn }, { tp: 1, fn: 1, fp: 1, tn: 1 });
approx("Matrix-2 precision = 1/(1+1) = 0.5", m2.precision.p, 0.5);
approx("Matrix-2 recall = 1/(1+1) = 0.5", m2.recall.p, 0.5);
// Matrix-1 = empirically blind subset. Both seq BAD are blind=true (positives); GOOD seq scoped in.
const m1 = res.matrix1.overall;
eq("Matrix-1 positives = 2 blind BAD (tp+fn)", m1.tp + m1.fn, 2);
truthy("Matrix-1 headline is RECALL", res.matrix1.headline === "RECALL");
truthy("Matrix-1 scoped to seq-array", res.matrix1.scopedFamilies.includes("seq-array"));
// the GOOD frame is a negative (TN) in Matrix-2.
truthy("GOOD frame counted as negative (TN≥1)", m2.tn >= 1);

// ── report-emitter produces a file with verbatim caveats ──
const outDir = join(tmpdir(), `vbench-selftest-${process.pid}`);
const { mdPath, jsonPath } = emitReport({ results: res, date: "selftest", outDir, extras: { runMode: "unit", independenceCheck: "n/a", fidelityFraction: "n/a", adjudicationDisagreement: "n/a" } });
truthy("report md written", existsSync(mdPath));
truthy("results json written", existsSync(jsonPath));
const mdText = readFileSync(mdPath, "utf8");
truthy("caveat: floor authoritative+hard-blocking verbatim", mdText.includes("**authoritative and hard-blocking**"));
truthy("caveat: leg ADVISORY/SHADOW verbatim", mdText.includes("**ADVISORY / SHADOW**"));
truthy("report shows reconciliation quarantine line", mdText.includes("QUARANTINED"));
truthy("report has Matrix-2 + Matrix-1 sections", mdText.includes("## Matrix-2") && mdText.includes("## Matrix-1"));
rmSync(outDir, { recursive: true, force: true });

console.log(`\n${fail === 0 ? "ALL GREEN" : "RED"} — ${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
