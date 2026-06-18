/**
 * scorer.mjs — the METRICS engine (spec §6, T12).
 *
 * Joins three inputs into the bench's numbers:
 *   1. frames.json   (T6 capturer manifest)        — every captured frame + its ground-truth label.
 *   2. detgate.json  (T8 det-probe)                — the EMPIRICAL detGateBlind per BAD defect.
 *   3. verdicts      (T14 orchestrator, K=3 judge) — per-frame { imagePath, perRun[], majority, ... }.
 *
 * Produces (spec §6):
 *   • Matrix-2 (ALL defects)            — precision + recall, per-family AND overall, Wilson 95% CI.
 *   • Matrix-1 (empirically-blind subset)— RECALL-led (R8). Precision (if reported) uses GOOD frames
 *     SCOPED to the subset's families — never the whole-corpus FP pool against a subset numerator.
 *     The two real sort-merge catchables + chip-occludes-label (if empirically RED) are EXCLUDED from
 *     Matrix-1 (the floor owns them) but INCLUDED in Matrix-2.
 *   • K-agreement (unanimous vs split), abstention rate (UNPARSEABLE fraction), and the §6.3
 *     SENSITIVITY ROW (precision/recall if abstentions were DROPPED instead of FAIL-counted; the
 *     headline is flagged FRAGILE if it moves).
 *   • The §6.5 RECONCILIATION ASSERT: catalog.expectedDetGateBlind === empirical.detGateBlind for
 *     every BAD defect. On mismatch → FAIL LOUD (default) or QUARANTINE that defect from BOTH matrices
 *     and surface it. Controlled by reconcileMode.
 *
 * Label convention (§6): BAD=positive, GOOD=negative; judge majority FAIL = positive-prediction.
 *   TP = BAD→FAIL (caught) · FN = BAD→PASS (DANGEROUS miss) · FP = GOOD→FAIL (safe-noisy) · TN = GOOD→PASS.
 *   UNPARSEABLE folds into FAIL for the headline decision (done in reduceMajority); the abstention
 *   sensitivity row recomputes with UNPARSEABLE runs DROPPED.
 *
 * Pure-ish: no network, no browser. Reads JSON files / takes objects; returns a results object the
 * report-emitter (T13) renders. The reduction rule lives in verdict-parser.mjs (reduceMajority) so the
 * judge and the scorer never diverge.
 */

import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { reduceMajority } from "./verdict-parser.mjs";

/**
 * Wilson score interval for a binomial proportion (spec §6.4, R17). Returns { p, lo, hi, n } in [0,1].
 * z = 1.959963985 for 95%. n=0 → { p:null, lo:null, hi:null, n:0 } (no estimate).
 */
export function wilson(successes, n, z = 1.959963985) {
  if (!n || n <= 0) return { p: null, lo: null, hi: null, n: 0 };
  const phat = successes / n;
  const z2 = z * z;
  const denom = 1 + z2 / n;
  const center = (phat + z2 / (2 * n)) / denom;
  const margin = (z * Math.sqrt((phat * (1 - phat) + z2 / (4 * n)) / n)) / denom;
  return { p: phat, lo: Math.max(0, center - margin), hi: Math.min(1, center + margin), n };
}

/** Minimum positive-N below which a per-family cell is "insufficient sample", not a point estimate. */
export const MIN_CELL_N = 8;

/**
 * Recompute the per-frame decision from a frame's perRun verdicts, with abstentions either FAIL-counted
 * (the headline rule, §6.3) or DROPPED (the sensitivity rule). Uses reduceMajority for the FAIL-counted
 * path so the scorer never diverges from the judge.
 *
 * @param {Array<{verdict:string}>} perRun
 * @param {'fail-counted'|'dropped'} abstentionMode
 * @returns {{decision:'PASS'|'FAIL'|'NO-VOTE', kPass, kFail, kUnparseable}}
 */
export function frameDecision(perRun, abstentionMode = "fail-counted") {
  const runs = Array.isArray(perRun) ? perRun : [];
  const kUnparseable = runs.filter((r) => r && r.verdict === "UNPARSEABLE").length;
  const kPass = runs.filter((r) => r && r.verdict === "PASS").length;
  const kFail = runs.filter((r) => r && r.verdict === "FAIL").length;
  if (abstentionMode === "dropped") {
    const voting = kPass + kFail;
    if (voting === 0) return { decision: "NO-VOTE", kPass, kFail, kUnparseable };
    // majority of the PARSED votes only; tie ⇒ FAIL (same safety bias as the headline).
    const decision = kFail >= kPass ? "FAIL" : "PASS";
    return { decision, kPass, kFail, kUnparseable };
  }
  const red = reduceMajority(runs);
  return { decision: red.decision, kPass: red.kPass, kFail: red.kFail, kUnparseable: red.kUnparseable };
}

/** Tally a confusion matrix from a list of {label, decision} where label∈{BAD,GOOD,GOOD-borderline}. */
function tally(rows) {
  let tp = 0, fn = 0, fp = 0, tn = 0;
  for (const r of rows) {
    const isPositive = r.label === "BAD";
    const predPositive = r.decision === "FAIL";
    if (isPositive && predPositive) tp++;
    else if (isPositive && !predPositive) fn++;
    else if (!isPositive && predPositive) fp++;
    else tn++;
  }
  return { tp, fn, fp, tn };
}

/** precision/recall (+ Wilson CIs) from a confusion matrix. null when the denominator is 0. */
function rates(cm) {
  const precDen = cm.tp + cm.fp;
  const recDen = cm.tp + cm.fn;
  return {
    ...cm,
    precision: precDen ? wilson(cm.tp, precDen) : { p: null, lo: null, hi: null, n: 0 },
    recall: recDen ? wilson(cm.tp, recDen) : { p: null, lo: null, hi: null, n: 0 },
  };
}

/**
 * Score the bench.
 *
 * @param {object} a
 * @param {object|string} a.framesManifest  frames.json object OR absolute path
 * @param {object|string} a.detgate         detgate.json object OR absolute path
 * @param {Array|object|string} a.verdicts  array of per-frame verdict objects OR { results:[...] } OR a path
 * @param {Array} a.catalog                 the BAD_DEFECTS catalog array (for expectedDetGateBlind + per-family counts)
 * @param {'fail'|'quarantine'} [a.reconcileMode='fail']  §6.5 behaviour on a mismatch
 * @returns {object} results — consumed by report-emitter.mjs
 */
export function scoreBench({ framesManifest, detgate, verdicts, catalog, reconcileMode = "fail" } = {}) {
  const fm = typeof framesManifest === "string" ? JSON.parse(readFileSync(framesManifest, "utf8")) : framesManifest;
  const dg = typeof detgate === "string" ? JSON.parse(readFileSync(detgate, "utf8")) : detgate;
  let vd = typeof verdicts === "string" ? JSON.parse(readFileSync(verdicts, "utf8")) : verdicts;
  if (vd && !Array.isArray(vd) && Array.isArray(vd.results)) vd = vd.results;
  if (!Array.isArray(vd)) throw new Error("[scorer] verdicts must be an array (or {results:[]})");
  const frames = Array.isArray(fm.frames) ? fm.frames : [];
  const perDefectGate = (dg && dg.perDefect) || {};
  const catBad = (catalog || []).filter((c) => c.label === "BAD");
  const expectedById = new Map(catBad.map((c) => [c.id, c.expectedDetGateBlind]));
  const familyById = new Map(catBad.map((c) => [c.id, c.family]));

  // Index verdicts by imagePath (the join key with frames.json's frame.path).
  const verdictByPath = new Map();
  for (const v of vd) {
    const key = v.imagePath || v.path;
    if (key) verdictByPath.set(resolve(key), v);
  }

  // ── §6.5 RECONCILIATION ASSERT ─────────────────────────────────────────────────────────────────
  // For every BAD defect that the det-probe actually determined (empirical !== null), compare
  // catalog.expectedDetGateBlind === empirical.detGateBlind. Collect mismatches; FAIL LOUD or quarantine.
  const reconciliation = { checked: 0, matches: 0, mismatches: [], undetermined: [], quarantined: [] };
  for (const c of catBad) {
    const emp = perDefectGate[c.id];
    if (!emp) continue; // not probed (frame not captured) — not a reconciliation failure by itself
    if (emp.detGateBlind === null || emp.detGateBlind === undefined) {
      reconciliation.undetermined.push({ id: c.id, reason: emp.note || "empirical undetermined" });
      continue;
    }
    reconciliation.checked++;
    if (c.expectedDetGateBlind === emp.detGateBlind) {
      reconciliation.matches++;
    } else {
      reconciliation.mismatches.push({
        id: c.id,
        family: c.family,
        expected: c.expectedDetGateBlind,
        empirical: emp.detGateBlind,
        gateCoverage: emp.gateCoverage,
        verdict: emp.verdict,
        note: emp.note,
      });
    }
  }
  const quarantinedIds = new Set();
  if (reconciliation.mismatches.length) {
    if (reconcileMode === "fail") {
      // FAIL LOUD: the caller decides whether to abort. We surface a hard flag; the orchestrator/CLI
      // throws on it. The report still renders the mismatch table so the failure is legible.
      reconciliation.failLoud = true;
    } else {
      for (const m of reconciliation.mismatches) {
        quarantinedIds.add(m.id);
        reconciliation.quarantined.push(m.id);
      }
      reconciliation.failLoud = false;
    }
  }

  // ── Build the scored frame rows (join frames → verdict → empirical detGateBlind) ──────────────────
  const rows = [];
  let missingVerdicts = 0;
  for (const f of frames) {
    const v = verdictByPath.get(resolve(f.path));
    const perRun = v && Array.isArray(v.perRun) ? v.perRun : null;
    const labelGroup = f.label === "GOOD-borderline" ? "GOOD" : f.label; // borderline GOODs are negatives
    if (!perRun) {
      // No verdict for this captured frame → record but exclude from the matrices (can't score it).
      missingVerdicts++;
      rows.push({ ...f, decision: null, decisionDropped: null, kPass: null, kFail: null, kUnparseable: null, scored: false });
      continue;
    }
    const headline = frameDecision(perRun, "fail-counted");
    const dropped = frameDecision(perRun, "dropped");
    const emp = f.label === "BAD" ? perDefectGate[f.defectId] : null;
    rows.push({
      path: f.path,
      label: f.label,
      labelGroup,
      family: f.family,
      defectId: f.defectId,
      skin: f.skin ?? null,
      frameIndex: f.frameIndex,
      decision: headline.decision,
      decisionDropped: dropped.decision,
      kPass: headline.kPass,
      kFail: headline.kFail,
      kUnparseable: headline.kUnparseable,
      empiricalDetGateBlind: emp ? emp.detGateBlind : null,
      empiricalGateVerdict: emp ? emp.verdict : null,
      empiricalGateCoverage: emp ? emp.gateCoverage : null,
      quarantined: quarantinedIds.has(f.defectId),
      scored: true,
    });
  }

  const scored = rows.filter((r) => r.scored && !r.quarantined);
  // Frames usable as positives/negatives for the matrices.
  const labelOf = (r) => r.labelGroup; // BAD | GOOD

  // ── Matrix-2: ALL defects (every scored BAD positive + every scored GOOD negative). ──────────────
  const m2rows = scored.map((r) => ({ label: labelOf(r), decision: r.decision }));
  const matrix2 = buildMatrixWithFamilies(scored, labelOf, (r) => r.decision);

  // ── Matrix-1: empirically-blind subset, RECALL-led (R8). ──────────────────────────────────────────
  // Positives = scored BAD frames whose empiricalDetGateBlind === true (floor does NOT catch them).
  // The catchables (empirical RED) are EXCLUDED. Precision (if any) uses GOOD frames scoped to the
  // SAME families present in the blind-positive subset.
  const blindPositives = scored.filter((r) => labelOf(r) === "BAD" && r.empiricalDetGateBlind === true);
  const subsetFamilies = new Set(blindPositives.map((r) => r.family));
  const scopedNegatives = scored.filter((r) => labelOf(r) === "GOOD" && subsetFamilies.has(r.family));
  const matrix1 = buildMatrixWithFamilies([...blindPositives, ...scopedNegatives], labelOf, (r) => r.decision);
  // Excluded-from-Matrix-1 (the floor owns them): BAD frames empirically caught (RED) by the floor.
  const matrix1Excluded = scored
    .filter((r) => labelOf(r) === "BAD" && r.empiricalDetGateBlind === false)
    .map((r) => ({ defectId: r.defectId, family: r.family, frameIndex: r.frameIndex, gateVerdict: r.empiricalGateVerdict }));

  // ── K-agreement + abstention + sensitivity (§6.3) ────────────────────────────────────────────────
  const kFrames = scored.filter((r) => r.kPass != null);
  const totalRuns = kFrames.reduce((s, r) => s + r.kPass + r.kFail + r.kUnparseable, 0);
  const totalUnparseable = kFrames.reduce((s, r) => s + r.kUnparseable, 0);
  const unanimousFrames = kFrames.filter((r) => r.kUnparseable === 0 && (r.kPass === 0 || r.kFail === 0)).length;
  const splitFrames = kFrames.length - unanimousFrames;
  const framesWithAbstention = kFrames.filter((r) => r.kUnparseable > 0).length;

  // Sensitivity: recompute Matrix-2 overall with abstentions DROPPED. Flag fragile if headline moves.
  const m2Dropped = buildMatrixWithFamilies(
    scored.filter((r) => r.decisionDropped !== "NO-VOTE"),
    labelOf,
    (r) => r.decisionDropped,
  );
  const headlineRecall = matrix2.overall.recall.p;
  const droppedRecall = m2Dropped.overall.recall.p;
  const headlinePrecision = matrix2.overall.precision.p;
  const droppedPrecision = m2Dropped.overall.precision.p;
  const moved = (a, b) => a != null && b != null && Math.abs(a - b) > 0.05; // >5pp shift = fragile
  const sensitivity = {
    headline: { precision: headlinePrecision, recall: headlineRecall },
    abstentionsDropped: { precision: droppedPrecision, recall: droppedRecall },
    fragile: moved(headlineRecall, droppedRecall) || moved(headlinePrecision, droppedPrecision),
  };

  // ── Advisory council bar (§6.3, SEPARATE column, NOT the decision rule) ──────────────────────────
  // bad ⇒ FAIL when ≥2/3 runs FAIL; good ⇒ PASS only when all K runs PASS (K/K). Reported, not used.
  const councilRows = scored.map((r) => {
    const k = r.kPass + r.kFail + r.kUnparseable;
    const failish = r.kFail + r.kUnparseable;
    const councilDecision =
      labelOf(r) === "BAD"
        ? failish * 3 >= k * 2 ? "FAIL" : "PASS" // ≥2/3 FAIL
        : r.kPass === k ? "PASS" : "FAIL"; // GOOD passes only on K/K PASS
    return { label: labelOf(r), decision: councilDecision };
  });
  const councilMatrix = tally(councilRows);

  return {
    meta: {
      framesManifestPath: typeof framesManifest === "string" ? framesManifest : null,
      detgatePath: typeof detgate === "string" ? detgate : null,
      generatedAt: new Date().toISOString(),
      capturedFrames: frames.length,
      scoredFrames: scored.length,
      missingVerdicts,
      reconcileMode,
      minCellN: MIN_CELL_N,
    },
    reconciliation,
    matrix2,
    matrix1: {
      ...matrix1,
      headline: "RECALL",
      excluded: matrix1Excluded,
      scopedFamilies: [...subsetFamilies],
      note:
        "Matrix-1 = empirically-detGateBlind BAD positives (the floor misses them) + GOOD negatives " +
        "SCOPED to the same families. RECALL-led (R8). Empirically-caught (RED) defects are excluded.",
    },
    kAgreement: {
      kFrames: kFrames.length,
      unanimousFrames,
      splitFrames,
      totalRuns,
      totalUnparseable,
      abstentionRate: totalRuns ? totalUnparseable / totalRuns : 0,
      framesWithAbstention,
    },
    sensitivity,
    councilAdvisory: { ...councilMatrix, note: "ADVISORY column only — NOT the scorer's decision rule (§6.3)." },
    rows,
    chartRevealCheck: dg ? dg.chartRevealCheck : null,
  };
}

/**
 * Build per-family + overall confusion matrices from scored rows.
 * @param {Array} rows  scored rows (each carries .family)
 * @param {(r)=>'BAD'|'GOOD'} labelFn
 * @param {(r)=>'PASS'|'FAIL'} decisionFn
 */
function buildMatrixWithFamilies(rows, labelFn, decisionFn) {
  const families = [...new Set(rows.map((r) => r.family))].sort();
  const perFamily = {};
  for (const fam of families) {
    const famRows = rows.filter((r) => r.family === fam).map((r) => ({ label: labelFn(r), decision: decisionFn(r) }));
    const cm = tally(famRows);
    perFamily[fam] = rates(cm);
  }
  const allRows = rows.map((r) => ({ label: labelFn(r), decision: decisionFn(r) }));
  const overall = rates(tally(allRows));
  return { perFamily, overall, families };
}

// ── CLI ───────────────────────────────────────────────────────────────────────────────────────────
const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (isMain) {
  const [, , framesArg, detgateArg, verdictsArg, reconcileArg] = process.argv;
  if (!framesArg || !detgateArg || !verdictsArg) {
    console.error("usage: node scorer.mjs <frames.json> <detgate.json> <verdicts.json> [fail|quarantine]");
    process.exit(1);
  }
  import("./defect-catalog.mjs").then(({ BAD_DEFECTS, DEFECT_CATALOG }) => {
    const res = scoreBench({
      framesManifest: resolve(framesArg),
      detgate: resolve(detgateArg),
      verdicts: resolve(verdictsArg),
      catalog: DEFECT_CATALOG,
      reconcileMode: reconcileArg === "quarantine" ? "quarantine" : "fail",
    });
    console.log(JSON.stringify({ meta: res.meta, reconciliation: res.reconciliation, matrix2overall: res.matrix2.overall, matrix1overall: res.matrix1.overall, kAgreement: res.kAgreement, sensitivity: res.sensitivity }, null, 2));
    if (res.reconciliation.failLoud) {
      console.error(`\n[scorer] §6.5 RECONCILIATION FAILED LOUD — ${res.reconciliation.mismatches.length} mismatch(es). Exit 2.`);
      process.exit(2);
    }
    process.exit(0);
  });
}
