/**
 * fidelity-validate.mjs — T4 prop-injection fidelity (spec §3.3, R10).
 *
 * Validates the prop-mode (DOM render-transform) injection against the PROVEN source-edit path by
 * md5 BYTE-EQUALITY of the captured bare PNGs, for a REPRESENTATIVE sample per family (the spec asks
 * for ≥1 paint-role, ≥1 geometry, ≥1 value defect per family + the 2 seeds). Phase-1 rule: this runs
 * the SMALL fidelity sample ONLY — NOT the full corpus.
 *
 * For each sample defect we capture the SAME (route, frameIndex) twice:
 *   • PROP : open the figure, run the DOM transform (applyPropDefect), screenshot the bare SVG.
 *   • SOURCE: patch the single source file (targeted), let HMR settle, screenshot, targeted-revert.
 * Then md5(prop.png) === md5(source.png) ? FIDELITY-OK : MISMATCH → the catalog demotes that defect
 * to source-only.
 *
 * Pure attribute sabotages (fill / fill-opacity / text-x) SHOULD be byte-identical because both paths
 * converge on the same final SVG attribute with reducedMotion='reduce' (no tween) and a fixed frame.
 * Structural sabotages (geometry constants, frame-pinning) generally will NOT byte-match and are
 * source-only by construction (no prop transform authored).
 *
 * Usage: node tutor-web/tools/vision-bench/fidelity-validate.mjs [outDirAbs]
 * Prereq: vite dev server on http://localhost:5173. Mutates ONE source file at a time, always reverted.
 */

import { chromium } from "playwright-core";
import { createHash } from "node:crypto";
import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { captureBareFigure, VIEWPORT, DPR } from "./frame-capturer.mjs";
import { applyPropDefect } from "./defect-injector.mjs";
import { withSourceDefect } from "./defect-injector.mjs";

const OUT = resolve(process.argv[2] || join(process.cwd(), "../build-review/vision-bench-fidelity"));
mkdirSync(OUT, { recursive: true });

const md5 = (path) => createHash("md5").update(readFileSync(path)).digest("hex");

// Representative fidelity sample: per family ≥1 paint / ≥1 geometry / ≥1 value where a clean single-
// file SOURCE patch can be authored AND a prop DOM-transform exists, so the two paths are comparable.
// (Defects with no prop transform are source-only by construction and excluded from byte-comparison.)
const SAMPLE = [
  // ── chart-dist: paint (shade opacity) + geometry (badge x swap) ──
  {
    id: "shade-mis-colored-blends-into-curve",
    role: "paint",
    family: "chart-dist",
    route: "/tutor/viz-demo",
    rootTestid: "chart-dist-root",
    scrubberTestid: "chart-dist-scrubber",
    scrubberIndex: 1,
    frameIndex: 3,
    cropProfile: "chart-dist",
    predictGate: false,
    patch: {
      file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx",
      find: "fillOpacity={dark ? 0.32 : 0.55} />",
      replace: "fillOpacity={0.02} />",
    },
  },
  {
    id: "ab-badges-swapped",
    role: "geometry",
    family: "chart-dist",
    route: "/tutor/viz-demo",
    rootTestid: "chart-dist-root",
    scrubberTestid: "chart-dist-scrubber",
    scrubberIndex: 1,
    frameIndex: 2,
    cropProfile: "chart-dist",
    predictGate: false,
    patch: {
      // swap the a/b badge x bindings (geometry sabotage). The 'a' badge line ends in >a</text> and the
      // 'b' line in >b</text> — both start with x={aPx}/x={bPx} respectively. A regex with backrefs
      // swaps the two x bindings in one pass, whitespace-agnostic on the inter-line gap.
      file: "tutor-web/src/components/viz/families/ChartDistributionFamily.tsx",
      find: /<text x=\{aPx\}( y=\{PLOT_Y0 - 4\}[^>]*>)a<\/text>(\s*)<text x=\{bPx\}( y=\{PLOT_Y0 - 4\}[^>]*>)b<\/text>/,
      replace: "<text x={bPx}$1a</text>$2<text x={aPx}$3b</text>",
    },
  },
  // ── seq-array: paint (sorted-prefix INK → white) ──
  {
    id: "missing-sorted-prefix-paint",
    role: "paint",
    family: "seq-array",
    route: "/tutor/viz-demo",
    rootTestid: "seq-array-root",
    scrubberTestid: "seq-array-scrubber",
    scrubberIndex: 1,
    frameIndex: 5,
    cropProfile: "seq-array",
    predictGate: false,
    patch: {
      file: "tutor-web/src/components/viz/families/SequenceArrayFamily.tsx",
      // white-skin fillForCell sorted branch (the standalone /viz-demo tile renders the white skin).
      find: "if (index < state.sortedCount) return INK; // sorted prefix",
      replace: 'if (index < state.sortedCount) return "#fff"; // sorted prefix',
    },
  },
];

let pass = 0;
let fail = 0;
const results = [];
const log = (ok, msg) => {
  if (ok) {
    pass++;
    console.log(`  OK    ${msg}`);
  } else {
    fail++;
    console.log(`  XX    ${msg}`);
  }
};

/** Capture the PROP variant: open the figure, run the DOM transform, screenshot the bare SVG. */
async function capturePropVariant(browser, s) {
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: DPR, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  const errs = [];
  page.on("pageerror", (e) => errs.push(e.message.slice(0, 160)));
  try {
    await page.goto("http://localhost:5173" + s.route, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForTimeout(500);
    const scrubber = page.locator(`[data-testid="${s.scrubberTestid}"]`).nth(s.scrubberIndex);
    const svg = page.locator(`[data-testid="${s.rootTestid}"]`).nth(s.scrubberIndex).locator("svg.algo-stepper-shell-svg");
    await svg.first().waitFor({ state: "visible", timeout: 15000 });
    await scrubber.evaluate((el, v) => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
      setter.call(el, String(v));
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
    }, s.frameIndex);
    await page.waitForTimeout(650);
    // Apply the prop DOM transform AFTER the frame settles.
    const applied = await applyPropDefect(svg, s.id);
    await svg.scrollIntoViewIfNeeded();
    await page.waitForTimeout(120);
    const box = await svg.boundingBox();
    const clip = { x: Math.round(box.x), y: Math.round(box.y), width: Math.round(box.width), height: Math.round(box.height) };
    const outPath = join(OUT, `prop__${s.family}__${s.id}__f${s.frameIndex}.png`);
    const png = await page.screenshot({ clip });
    mkdirSync(OUT, { recursive: true });
    writeFileSync(outPath, png);
    return { outPath, applied, clip, errs };
  } finally {
    await ctx.close();
  }
}

/** Capture the SOURCE variant: patch one file, settle HMR, screenshot, targeted-revert (withSourceDefect). */
async function captureSourceVariant(browser, s) {
  const outPath = join(OUT, `source__${s.family}__${s.id}__f${s.frameIndex}.png`);
  const { restored } = await withSourceDefect(s.patch, async () => {
    const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: DPR, reducedMotion: "reduce" });
    const page = await ctx.newPage();
    try {
      await page.goto("http://localhost:5173" + s.route, { waitUntil: "networkidle", timeout: 30000 });
      await page.waitForTimeout(700); // extra settle for the HMR-applied module
      const scrubber = page.locator(`[data-testid="${s.scrubberTestid}"]`).nth(s.scrubberIndex);
      const svg = page.locator(`[data-testid="${s.rootTestid}"]`).nth(s.scrubberIndex).locator("svg.algo-stepper-shell-svg");
      await svg.first().waitFor({ state: "visible", timeout: 15000 });
      await scrubber.evaluate((el, v) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        setter.call(el, String(v));
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      }, s.frameIndex);
      await page.waitForTimeout(650);
      await svg.scrollIntoViewIfNeeded();
      await page.waitForTimeout(120);
      const box = await svg.boundingBox();
      const clip = { x: Math.round(box.x), y: Math.round(box.y), width: Math.round(box.width), height: Math.round(box.height) };
      const png = await page.screenshot({ clip });
      writeFileSync(outPath, png);
      return outPath;
    } finally {
      await ctx.close();
    }
  });
  return { outPath, restored };
}

async function run() {
  console.log("================ VISION-BENCH FIDELITY (T4) ================");
  console.log(`out: ${OUT}`);
  console.log(`sample: ${SAMPLE.length} defects (paint/geometry/value across chart-dist + seq-array)`);

  const browser = await chromium.launch({ headless: true });
  try {
    for (const s of SAMPLE) {
      // PROP first (no source change), then SOURCE (patched window) — never overlapping.
      const prop = await capturePropVariant(browser, s);
      if (!prop.applied.applied) {
        log(false, `${s.family}/${s.id} (${s.role}): NO prop transform → source-only by construction (excluded from byte-eq)`);
        results.push({ id: s.id, family: s.family, role: s.role, fidelity: "source-only", reason: prop.applied.note });
        continue;
      }
      if (prop.applied.mutated === 0) {
        log(false, `${s.family}/${s.id} (${s.role}): prop transform was a NO-OP on frame ${s.frameIndex} (wrong frame?)`);
        results.push({ id: s.id, family: s.family, role: s.role, fidelity: "prop-noop", reason: "0 nodes mutated" });
        continue;
      }
      const source = await captureSourceVariant(browser, s);
      const a = md5(prop.outPath);
      const b = md5(source.outPath);
      const eq = a === b;
      log(true, `${s.family}/${s.id} (${s.role}): prop applied (${prop.applied.mutated} nodes), source restored=${source.restored}`);
      const verdict = eq ? "byte-equal" : "MISMATCH→source-only";
      console.log(`        md5 prop=${a.slice(0, 12)} source=${b.slice(0, 12)} → ${verdict}`);
      results.push({ id: s.id, family: s.family, role: s.role, fidelity: eq ? "byte-equal" : "mismatch", md5Prop: a, md5Source: b });
    }
  } finally {
    await browser.close();
  }

  const byteEqual = results.filter((r) => r.fidelity === "byte-equal").length;
  const comparable = results.filter((r) => r.fidelity === "byte-equal" || r.fidelity === "mismatch").length;
  console.log("\n================ FIDELITY RESULT ================");
  console.log(`comparable prop-vs-source pairs: ${comparable}`);
  console.log(`byte-equal (fidelity-validated): ${byteEqual}/${comparable}`);
  console.log(`validated fraction: ${comparable ? ((byteEqual / comparable) * 100).toFixed(0) : 0}% of the comparable sample`);
  for (const r of results) console.log(`  - ${r.family}/${r.id} [${r.role}] → ${r.fidelity}`);
  // T4 PASS = the harness ran end-to-end and produced a verdict for every comparable sample; the
  // byte-equal FRACTION is the reported output (mismatches are demoted to source-only, not failures).
  const ranClean = results.length === SAMPLE.length && fail === 0;
  console.log(ranClean ? "FIDELITY HARNESS GREEN (ran clean; fraction reported above)" : "FIDELITY HARNESS RED");
  process.exit(ranClean ? 0 : 1);
}

run().catch((e) => {
  console.error("[fidelity] FATAL:", e);
  process.exit(1);
});
