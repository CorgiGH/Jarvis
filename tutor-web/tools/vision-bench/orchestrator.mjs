/**
 * orchestrator.mjs — the two-phase bench driver (spec §3.1, §3.0 #10, T14).
 *
 * PHASE 1 — CAPTURE (bounded-serial / lock-guarded):
 *   • probe the single :5173 dev server fail-loud (never capture against a dead server),
 *   • clean the scratch dir idempotently (a fresh run starts from empty PNGs/manifest),
 *   • expand the requested catalog entries into capture items, attach the prop-mode injection hook
 *     (applyPropDefect) to BAD prop items so the screenshot carries the defect, and capture serially
 *     via captureMany (ONE browser, one navigation per (route,query,scrubberIndex)).
 *   Source-mode BAD defects with an executable patch are captured behind the injector's file-lock;
 *   today's catalog authors no executable patch, so source-mode BAD frames cannot be self-injected and
 *   are SKIPPED with a recorded note (surfaced as a blocker, never silently labelled).
 *
 * PHASE 2 — JUDGE (safely parallel, pool 3-4):
 *   • read the static frames.json, run vision-judge.judgeFrame(K) per frame through a bounded worker
 *     pool (each call spawns a fresh `claude --print` relay subprocess; ANTHROPIC_API_KEY stripped),
 *   • the judge gets ONLY the defect-blind rubric (by rubricKey=family) + the frame's declared INPUT
 *     (NO oracle) — the declared input is derived from the frame's own family/route/frameIndex, never
 *     from the catalog's expected values.
 *
 * THEN: det-probe (T8) → scorer (T12) → report-emitter (T13).
 *
 * Phase-1-only / Phase-2-only / both are selectable so a smoke can run a tiny slice without the full
 * corpus. The date is a PARAMETER (smoke passes 'smoke'); committed code never calls Date.now for the
 * report filename.
 *
 * Usage:
 *   node orchestrator.mjs --scratch <absDir> --date <date|smoke> [--phase capture|judge|both]
 *                         [--k 3] [--pool 3] [--families seq-array,...] [--reconcile fail|quarantine]
 *                         [--limit N] [--no-gates]
 */

import { mkdirSync, rmSync, readFileSync, writeFileSync, existsSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve, join } from "node:path";
import { captureMany } from "./frame-capturer.mjs";
import { applyPropDefect } from "./defect-injector.mjs";
import { judgeFrame } from "./vision-judge.mjs";
import { runDetProbe } from "./det-probe.mjs";
import { scoreBench } from "./scorer.mjs";
import { emitReport } from "./report-emitter.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE_URL = "http://localhost:5173";

/** Probe :5173 fail-loud. */
async function probeServerOrDie() {
  const probe = await fetch(`${BASE_URL}/tutor/viz-demo`).catch(() => null);
  if (!probe || !probe.ok) {
    throw new Error(`[orchestrator] dev server not reachable at ${BASE_URL}/tutor/viz-demo (got ${probe ? probe.status : "no response"}). Start it: (cd tutor-web && npm run dev).`);
  }
}

/** Idempotent clean scratch dir (rm + recreate). Never touches anything outside the scratch dir. */
function cleanScratch(scratchDir) {
  if (existsSync(scratchDir)) rmSync(scratchDir, { recursive: true, force: true });
  mkdirSync(scratchDir, { recursive: true });
}

/**
 * Derive the figure's DECLARED INPUT string for the judge (NO oracle). It names ONLY what the renderer
 * itself consumes — family, route, frame index, skin — so the judge knows which elements should appear,
 * never which values are "right". This deliberately carries no expected value / active cell /
 * probability / highlight set (spec §4.2 / R6).
 */
function declaredInputFor(frame) {
  const bits = [`family: ${frame.family}`, `route: ${frame.route}`, `frame index (scrubber position): ${frame.frameIndex}`];
  if (frame.skin) bits.push(`skin: ${frame.skin}`);
  if (frame.family === "chart-dist") bits.push(`reveal beat: ${frame.frameIndex + 1} (frameIndex ${frame.frameIndex})`);
  return bits.join("\n");
}

/**
 * PHASE 1 — capture the requested catalog entries into the scratch dir.
 * @returns {Promise<{manifestPath, frames, skippedSource:Array}>}
 */
async function phaseCapture({ scratchDir, entries }) {
  await probeServerOrDie();
  // Build capture items, attaching the prop injection hook to BAD prop-mode items.
  const items = [];
  const skippedSource = [];
  for (const e of entries) {
    for (const fi of e.frameIndices) {
      const base = {
        id: e.frameIndices.length > 1 ? `${e.id}__f${fi}` : e.id,
        defectId: e.id,
        family: e.family,
        label: e.label,
        route: e.route,
        rootTestid: e.rootTestid,
        scrubberTestid: e.scrubberTestid,
        scrubberIndex: e.scrubberIndex,
        frameIndex: fi,
        predictGate: e.predictGate,
        cropProfile: e.cropProfile,
        skin: e.skin ?? null,
        expectedDetGateBlind: e.expectedDetGateBlind,
        gateCoverage: e.gateCoverage,
      };
      const mode = e.injection?.mode || (e.label === "BAD" ? "source" : "none");
      if (e.label === "BAD" && mode === "prop") {
        // The prop hook mutates the live SVG before the screenshot.
        base.postFrameHook = (svg) => applyPropDefect(svg, e.id);
      } else if (e.label === "BAD" && mode === "source") {
        const patch = e.injection?.patch;
        if (!patch || patch.find == null || patch.replace == null) {
          // No executable patch in the catalog → cannot self-inject. Record + skip (never label a
          // clean frame as BAD). The blocker is surfaced in the structured output.
          skippedSource.push({ id: e.id, frameIndex: fi, reason: "source-mode BAD with no executable injection.patch in catalog" });
          continue;
        }
        // (A real patch would be captured behind withSourceDefect — left for the patch-bearing corpus.)
        skippedSource.push({ id: e.id, frameIndex: fi, reason: "executable source patch present but source-mode capture loop not exercised in this run" });
        continue;
      }
      // GOOD / GOOD-borderline carry no hook (clean capture).
      items.push(base);
    }
  }
  const { manifestPath, frames } = await captureMany(items, { outDir: scratchDir });
  return { manifestPath, frames, skippedSource };
}

/** Simple bounded worker pool over an array of async tasks. */
async function pool(items, concurrency, worker) {
  const results = new Array(items.length);
  let next = 0;
  const runners = Array.from({ length: Math.min(concurrency, items.length || 1) }, async () => {
    while (true) {
      const i = next++;
      if (i >= items.length) return;
      results[i] = await worker(items[i], i);
    }
  });
  await Promise.all(runners);
  return results;
}

/**
 * PHASE 2 — judge each captured frame K times through a bounded pool. Writes verdicts.json.
 */
async function phaseJudge({ scratchDir, manifestPath, k, poolSize }) {
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const frames = manifest.frames;
  const results = await pool(frames, poolSize, async (frame) => {
    const imagePath = resolve(frame.path);
    const declaredInput = declaredInputFor(frame);
    const r = await judgeFrame({ imagePath, rubricKey: frame.family, declaredInput, k });
    return {
      imagePath,
      family: frame.family,
      label: frame.label,
      defectId: frame.defectId,
      frameIndex: frame.frameIndex,
      majority: r.majority,
      kPass: r.kPass,
      kFail: r.kFail,
      kUnparseable: r.kUnparseable,
      parseErrors: r.parseErrors,
      unanimous: r.unanimous,
      perRun: r.perRun.map((x) => ({ verdict: x.verdict, exitCode: x.exitCode, error: x.error })),
    };
  });
  const verdictsPath = join(scratchDir, "verdicts.json");
  writeFileSync(verdictsPath, JSON.stringify({ generatedAt: new Date().toISOString(), k, results }, null, 2));
  return { verdictsPath, results };
}

/**
 * Full run. Returns the produced artifact paths.
 *
 * @param {object} opts
 * @param {string} opts.scratchDir   absolute scratch dir (idempotently cleaned unless phase=judge)
 * @param {string} opts.date         report date stamp (param; 'smoke' for the smoke)
 * @param {Array} opts.entries       catalog entries to capture/judge (BAD + GOOD)
 * @param {Array} opts.catalogForScore  the full catalog (for the reconciliation assert)
 * @param {'capture'|'judge'|'both'} [opts.phase='both']
 * @param {number} [opts.k=3]
 * @param {number} [opts.poolSize=3]
 * @param {'fail'|'quarantine'} [opts.reconcileMode='fail']
 * @param {boolean} [opts.runGates=true]
 * @param {object} [opts.extras]     report provenance lines
 */
export async function runBench(opts) {
  const {
    scratchDir, date, entries, catalogForScore, phase = "both",
    k = 3, poolSize = 3, reconcileMode = "fail", runGates = true, extras = {},
  } = opts;
  const out = { scratchDir, date, phase };

  if (phase === "capture" || phase === "both") {
    cleanScratch(scratchDir);
    const cap = await phaseCapture({ scratchDir, entries });
    out.manifestPath = cap.manifestPath;
    out.capturedFrames = cap.frames.length;
    out.skippedSource = cap.skippedSource;
    console.log(`[orchestrator] PHASE 1 captured ${cap.frames.length} frame(s) → ${cap.manifestPath}`);
    if (cap.skippedSource.length) console.log(`[orchestrator] skipped ${cap.skippedSource.length} source-mode BAD frame(s) (no executable patch).`);
  }
  if (phase === "capture") return out;

  const manifestPath = out.manifestPath || join(scratchDir, "frames.json");
  if (!existsSync(manifestPath)) throw new Error(`[orchestrator] no frames manifest at ${manifestPath} for the judge phase`);

  // det-probe (T8) — empirical detGateBlind.
  const { outPath: detgatePath } = await runDetProbe({ framesManifestPath: manifestPath, runGates });
  out.detgatePath = detgatePath;
  console.log(`[orchestrator] det-probe → ${detgatePath}`);

  // judge (Phase 2).
  const { verdictsPath } = await phaseJudge({ scratchDir, manifestPath, k, poolSize });
  out.verdictsPath = verdictsPath;
  console.log(`[orchestrator] PHASE 2 judged → ${verdictsPath}`);

  // score (T12).
  const results = scoreBench({
    framesManifest: manifestPath,
    detgate: detgatePath,
    verdicts: verdictsPath,
    catalog: catalogForScore,
    reconcileMode,
  });
  out.scored = results.meta;
  out.reconciliation = results.reconciliation;

  // report (T13).
  const { mdPath, jsonPath } = emitReport({ results, date, extras });
  out.reportMd = mdPath;
  out.reportJson = jsonPath;
  console.log(`[orchestrator] report → ${mdPath}`);

  // FAIL LOUD on a reconciliation mismatch in 'fail' mode (after the report renders so it is legible).
  if (results.reconciliation.failLoud) {
    out.failLoud = true;
    console.error(`[orchestrator] §6.5 RECONCILIATION FAILED LOUD — ${results.reconciliation.mismatches.length} mismatch(es). See ${mdPath}.`);
  }
  return out;
}

// ── CLI ───────────────────────────────────────────────────────────────────────────────────────────
function argVal(flag, def) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : def;
}
const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url));
if (isMain) {
  const scratchDir = resolve(argVal("--scratch", join(HERE, "../../../build-review/vision-bench-scratch")));
  const date = argVal("--date", "smoke");
  const phase = argVal("--phase", "both");
  const k = Number(argVal("--k", "3"));
  const poolSize = Number(argVal("--pool", "3"));
  const reconcileMode = argVal("--reconcile", "fail");
  const runGates = !process.argv.includes("--no-gates");
  const familiesArg = argVal("--families", "");
  const limit = Number(argVal("--limit", "0"));

  import("./defect-catalog.mjs").then(async ({ DEFECT_CATALOG }) => {
    let entries = DEFECT_CATALOG;
    if (familiesArg) {
      const fams = new Set(familiesArg.split(",").map((s) => s.trim()));
      entries = entries.filter((e) => fams.has(e.family));
    }
    if (limit > 0) entries = entries.slice(0, limit);
    try {
      const out = await runBench({ scratchDir, date, entries, catalogForScore: DEFECT_CATALOG, phase, k, poolSize, reconcileMode, runGates });
      console.log(JSON.stringify(out, null, 2));
      process.exit(out.failLoud ? 2 : 0);
    } catch (e) {
      console.error("[orchestrator] FATAL:", e.message);
      process.exit(1);
    }
  });
}
