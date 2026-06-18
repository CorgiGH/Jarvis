/**
 * report-emitter.mjs — render the scored bench into a dated markdown report + results.json (spec §3.0
 * #9, T13).
 *
 * Consumes the scorer.mjs (T12) results object and writes:
 *   • build-review/<date>-vision-judge-bench.md   — human report
 *   • build-review/<date>-vision-judge-bench.results.json — the machine results (scorer output verbatim)
 *
 * The §9 honest caveats are carried VERBATIM (the floor stays authoritative + hard-blocking; the leg
 * stays ADVISORY / SHADOW regardless of any number this bench produces). The report ALSO carries:
 *   • the two confusion matrices (Matrix-2 all defects, Matrix-1 recall-led blind subset) with CIs,
 *   • the §6.5 reconciliation result (loud if it fired),
 *   • the per-defect verdict table,
 *   • the independence-check line, the fidelity-validated-fraction line, and the
 *     adjudication-disagreement line (passed in by the caller; the report does not invent them).
 *
 * Date discipline (per the build rule): the date is a PARAMETER. Committed code NEVER calls Date.now
 * for the filename — the orchestrator passes a real date; the smoke passes the literal 'smoke'.
 */

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// ── §9 honest caveats — VERBATIM from build-review/2026-06-16-vision-bench-design-spec.md §9. ─────────
// Carried byte-for-byte; do NOT paraphrase. The floor is authoritative + hard-blocking; the leg is
// advisory/shadow regardless of the measured number.
export const HONEST_CAVEATS_VERBATIM = [
  "The deterministic floor (frame-conjunction-gate + svg-overlap-gate) stays **authoritative and hard-blocking**. This bench changes nothing about that.",
  "The vision leg stays **ADVISORY / SHADOW** regardless of the measured precision/recall. This bench MEASURES; it does not graduate the leg.",
  "The floor is wired ONLY to sort-merge today; for the other 4 families the \"marginal value\" question is the WHOLE value (the floor catches nothing there). D1 records porting as separate future work.",
  "Numbers are reported with CIs; small-N cells are flagged, not point-estimated. The fidelity-validated prop fraction and the label-adjudication disagreement rate are reported so a reader can discount accordingly.",
];

const pct = (w) => (w == null || w.p == null ? "—" : `${(w.p * 100).toFixed(1)}%`);
const ci = (w) => (w == null || w.p == null ? "(n=0)" : `[${(w.lo * 100).toFixed(1)}, ${(w.hi * 100).toFixed(1)}] n=${w.n}`);
const cell = (w, minN) => {
  if (w == null || w.p == null) return "— (n=0)";
  const den = w.n;
  if (den < (minN ?? 8)) return `*insufficient sample — n=${den}*`;
  return `${pct(w)} ${ci(w)}`;
};

function matrixTable(matrix, minN) {
  const lines = [];
  lines.push("| family | TP | FN | FP | TN | precision | recall |");
  lines.push("|---|---|---|---|---|---|---|");
  for (const fam of matrix.families) {
    const m = matrix.perFamily[fam];
    lines.push(`| ${fam} | ${m.tp} | ${m.fn} | ${m.fp} | ${m.tn} | ${cell(m.precision, minN)} | ${cell(m.recall, minN)} |`);
  }
  const o = matrix.overall;
  lines.push(`| **OVERALL** | ${o.tp} | ${o.fn} | ${o.fp} | ${o.tn} | **${cell(o.precision, minN)}** | **${cell(o.recall, minN)}** |`);
  return lines.join("\n");
}

/**
 * Render the report.
 *
 * @param {object} a
 * @param {object} a.results        the scorer.mjs output object
 * @param {string} a.date           the report date stamp (e.g. "2026-06-17" or "smoke") — a PARAMETER
 * @param {string} [a.outDir]       absolute build-review dir (default <repo>/build-review)
 * @param {object} [a.extras]       { independenceCheck, fidelityFraction, adjudicationDisagreement, runMode, corpusNote }
 * @returns {{ mdPath:string, jsonPath:string }}
 */
export function emitReport({ results, date, outDir, extras = {} } = {}) {
  if (!results) throw new Error("[report-emitter] results object is required");
  if (!date) throw new Error("[report-emitter] date is required (a param — never Date.now in committed code)");
  const HERE = dirname(fileURLToPath(import.meta.url));
  const buildReview = outDir || resolve(HERE, "../../../build-review");
  mkdirSync(buildReview, { recursive: true });
  const base = `${date}-vision-judge-bench`;
  const mdPath = join(buildReview, `${base}.md`);
  const jsonPath = join(buildReview, `${base}.results.json`);

  const r = results;
  const rec = r.reconciliation || {};
  const md = [];

  md.push(`# Vision-Judge Precision/Recall Bench — ${date}`);
  md.push("");
  md.push(`**Status:** MEASURE-only. ${extras.runMode ? `Run mode: ${extras.runMode}.` : ""}`);
  md.push("");
  md.push("## Honest caveats (carried verbatim from spec §9)");
  md.push("");
  for (const c of HONEST_CAVEATS_VERBATIM) md.push(`- ${c}`);
  md.push("");

  // ── §6.5 reconciliation — surfaced LOUD if it fired ──
  md.push("## §6.5 Reconciliation assert (catalog.expectedDetGateBlind === empirical.detGateBlind)");
  md.push("");
  if (rec.failLoud) {
    md.push(`> **RECONCILIATION FAILED LOUD — ${rec.mismatches.length} mismatch(es).** The run is NOT clean; the table below names every defect whose catalogued det-gate expectation did not match the empirical gate probe.`);
  } else if (rec.mismatches && rec.mismatches.length) {
    md.push(`> **${rec.mismatches.length} mismatch(es) QUARANTINED** (reconcileMode=quarantine): excluded from BOTH matrices, listed below.`);
  } else {
    md.push(`Checked **${rec.checked || 0}** BAD defects with a determined empirical value · **${rec.matches || 0}** matched · **0** mismatches.`);
  }
  md.push("");
  if (rec.mismatches && rec.mismatches.length) {
    md.push("| defect | family | expected blind | empirical blind | gate coverage | gate verdict |");
    md.push("|---|---|---|---|---|---|");
    for (const m of rec.mismatches) {
      md.push(`| ${m.id} | ${m.family} | ${m.expected} | ${m.empirical} | ${m.gateCoverage} | ${m.verdict} |`);
    }
    md.push("");
  }
  if (rec.undetermined && rec.undetermined.length) {
    md.push(`*Undetermined (empirical could not be decided — gate error / not probed):* ${rec.undetermined.map((u) => u.id).join(", ")}`);
    md.push("");
  }

  // ── Matrix-2 (ALL defects) ──
  md.push("## Matrix-2 — ALL defects (full coverage)");
  md.push("");
  md.push("Positives = all scored BAD frames; negatives = all scored GOOD (incl. borderline) frames. Precision = TP/(TP+FP), recall = TP/(TP+FN), Wilson 95% CI.");
  md.push("");
  md.push(matrixTable(r.matrix2, r.meta.minCellN));
  md.push("");

  // ── Matrix-1 (recall-led blind subset) ──
  md.push("## Matrix-1 — empirically det-gate-blind subset (RECALL-led, R8)");
  md.push("");
  md.push(r.matrix1.note);
  md.push("");
  md.push(`Scoped families: ${r.matrix1.scopedFamilies.join(", ") || "(none)"}. **Headline metric = RECALL.**`);
  md.push("");
  md.push(matrixTable(r.matrix1, r.meta.minCellN));
  md.push("");
  if (r.matrix1.excluded && r.matrix1.excluded.length) {
    md.push(`*Excluded from Matrix-1 (floor empirically catches them — RED):* ${r.matrix1.excluded.map((e) => `${e.defectId}@f${e.frameIndex}`).join(", ")}`);
    md.push("");
  }

  // ── K-agreement + abstention + sensitivity ──
  const k = r.kAgreement;
  md.push("## K-runs agreement + abstention (§6.3)");
  md.push("");
  md.push(`- K-frames scored: **${k.kFrames}** · unanimous: **${k.unanimousFrames}** · split: **${k.splitFrames}**`);
  md.push(`- Total judge runs: **${k.totalRuns}** · UNPARSEABLE: **${k.totalUnparseable}** · abstention rate: **${(k.abstentionRate * 100).toFixed(1)}%** · frames with ≥1 abstention: **${k.framesWithAbstention}**`);
  md.push("");
  const s = r.sensitivity;
  md.push("### Sensitivity row — abstentions DROPPED instead of FAIL-counted");
  md.push("");
  md.push(`| metric | headline (UNPARSEABLE⇒FAIL) | abstentions dropped |`);
  md.push(`|---|---|---|`);
  md.push(`| precision | ${s.headline.precision == null ? "—" : (s.headline.precision * 100).toFixed(1) + "%"} | ${s.abstentionsDropped.precision == null ? "—" : (s.abstentionsDropped.precision * 100).toFixed(1) + "%"} |`);
  md.push(`| recall | ${s.headline.recall == null ? "—" : (s.headline.recall * 100).toFixed(1) + "%"} | ${s.abstentionsDropped.recall == null ? "—" : (s.abstentionsDropped.recall * 100).toFixed(1) + "%"} |`);
  md.push("");
  md.push(s.fragile ? "> **FRAGILE:** the headline moves >5pp when abstentions are dropped — treat the headline number with caution." : "Headline is STABLE under the abstention-drop sensitivity (moves ≤5pp).");
  md.push("");

  // ── Advisory council column (NOT the decision rule) ──
  const cmA = r.councilAdvisory;
  md.push("### Advisory council column (SEPARATE — not the scorer's decision rule, §6.3)");
  md.push("");
  md.push(`bad⇒FAIL when ≥2/3 runs FAIL; good⇒PASS only on K/K PASS. TP=${cmA.tp} FN=${cmA.fn} FP=${cmA.fp} TN=${cmA.tn}. ${cmA.note}`);
  md.push("");

  // ── Independence / fidelity / adjudication lines (passed in; not invented) ──
  md.push("## Provenance lines (R5 / R10 / R7-R18)");
  md.push("");
  md.push(`- **Independence check (§4.1):** ${extras.independenceCheck || "NOT RUN in this pass — record the reviewer-without-catalog result here."}`);
  md.push(`- **Prop-fidelity validated fraction (§3.3 / R10):** ${extras.fidelityFraction || "NOT RUN in this pass — md5 prop-vs-source fraction per family goes here."}`);
  md.push(`- **Adjudication disagreement (§8 / R7-R18):** ${extras.adjudicationDisagreement || "NOT RUN in this pass — the one-time human/second-judge disagreement rate goes here (the bench's own error floor)."}`);
  md.push("");

  // ── Chart reveal-1→2 near-threshold check ──
  if (r.chartRevealCheck) {
    md.push("## Chart reveal-1→2 near-threshold check (§5 step 4)");
    md.push("");
    md.push(`measured=${r.chartRevealCheck.measured} · MEANINGFUL_PX=${r.chartRevealCheck.meaningfulPx}`);
    md.push(`> ${r.chartRevealCheck.note}`);
    md.push("");
  }

  // ── Per-defect verdict table ──
  md.push("## Per-defect verdict table");
  md.push("");
  md.push("| frame | label | family | defect | frameIdx | decision | k(P/F/U) | empirical blind | gate verdict |");
  md.push("|---|---|---|---|---|---|---|---|---|");
  for (const row of r.rows) {
    if (!row.scored) {
      md.push(`| ${shortPath(row.path)} | ${row.label} | ${row.family} | ${row.defectId || "—"} | ${row.frameIndex ?? "—"} | *no verdict* | — | — | — |`);
      continue;
    }
    const kstr = `${row.kPass}/${row.kFail}/${row.kUnparseable}`;
    const q = row.quarantined ? " ⚠quarantined" : "";
    md.push(
      `| ${shortPath(row.path)} | ${row.label}${q} | ${row.family} | ${row.defectId || "—"} | ${row.frameIndex ?? "—"} | ${row.decision} | ${kstr} | ${row.empiricalDetGateBlind ?? "—"} | ${row.empiricalGateVerdict ?? "—"} |`,
    );
  }
  md.push("");
  md.push("---");
  md.push(`_Generated by tutor-web/tools/vision-bench/report-emitter.mjs · scored ${r.meta.scoredFrames}/${r.meta.capturedFrames} frames · missing verdicts ${r.meta.missingVerdicts} · reconcileMode=${r.meta.reconcileMode}._`);
  md.push("");

  writeFileSync(mdPath, md.join("\n"));
  writeFileSync(jsonPath, JSON.stringify(results, null, 2));
  return { mdPath, jsonPath };
}

function shortPath(p) {
  if (!p) return "—";
  const parts = String(p).split(/[\\/]/);
  return parts[parts.length - 1];
}

// ── CLI ───────────────────────────────────────────────────────────────────────────────────────────
const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (isMain) {
  const [, , resultsArg, dateArg] = process.argv;
  if (!resultsArg || !dateArg) {
    console.error("usage: node report-emitter.mjs <scorer-results.json> <date|smoke>");
    process.exit(1);
  }
  import("node:fs").then(({ readFileSync }) => {
    const results = JSON.parse(readFileSync(resolve(resultsArg), "utf8"));
    const { mdPath, jsonPath } = emitReport({ results, date: dateArg });
    console.log(`[report-emitter] wrote ${mdPath}`);
    console.log(`[report-emitter] wrote ${jsonPath}`);
    process.exit(0);
  });
}
