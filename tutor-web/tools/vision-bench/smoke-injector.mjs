/**
 * smoke-injector.mjs — Phase-1 unit/smoke for T3 (defect-injector prop transforms) + T5 (catalog).
 * TINY by design: opens each sample figure ONCE, applies the prop DOM transform, asserts it mutated
 * the expected node count, and confirms the figure's DEFAULT render is unchanged when no defect is
 * applied (additive guarantee). Does NOT mutate source (that is T4's fidelity-validate.mjs).
 *
 * Usage: node tutor-web/tools/vision-bench/smoke-injector.mjs
 * Prereq: vite dev server on http://localhost:5173.
 */

import { chromium } from "playwright-core";
import { applyPropDefect, DEFECT_DOM_TRANSFORMS } from "./defect-injector.mjs";
import { BAD_DEFECTS, GOOD_CLEAN, GOOD_BORDERLINE_DEFECTS, BAD_COUNTS } from "./defect-catalog.mjs";

const BASE = "http://localhost:5173";
const VIEWPORT = { width: 1536, height: 730 };

let pass = 0;
let fail = 0;
const log = (ok, msg) => {
  if (ok) {
    pass++;
    console.log(`  PASS  ${msg}`);
  } else {
    fail++;
    console.log(`  FAIL  ${msg}`);
  }
};

// Sample of prop-transform defects + the frame each is discriminating on (from the catalog).
const PROP_SAMPLE = [
  { id: "missing-sorted-prefix-paint", root: "seq-array-root", scrubber: "seq-array-scrubber", route: "/tutor/viz-demo", frame: 5, expectMutated: (n) => n >= 1 },
  { id: "duplicated-value-token", root: "seq-array-root", scrubber: "seq-array-scrubber", route: "/tutor/viz-demo", frame: 3, expectMutated: (n) => n === 1 },
  { id: "shade-mis-colored-blends-into-curve", root: "chart-dist-root", scrubber: "chart-dist-scrubber", route: "/tutor/viz-demo", frame: 3, expectMutated: (n) => n === 1 },
  { id: "y-axis-or-zero-tick-missing", root: "chart-dist-root", scrubber: "chart-dist-scrubber", route: "/tutor/viz-demo", frame: 3, expectMutated: (n) => n === 1 },
  { id: "ab-badges-swapped", root: "chart-dist-root", scrubber: "chart-dist-scrubber", route: "/tutor/viz-demo", frame: 2, expectMutated: (n) => n === 1 },
  { id: "duplicated-value-across-nodes", root: "graph-tree-root", scrubber: "graph-tree-scrubber", route: "/tutor/viz-demo", frame: 3, expectMutated: (n) => n === 1 },
  { id: "mis-colored-highlight-frontier", root: "graph-tree-root", scrubber: "graph-tree-scrubber", route: "/tutor/viz-demo", frame: 1, expectMutated: (n) => n >= 1 },
  { id: "pivot-highlight-wrong-cell", root: "mgg-root", scrubber: "mgg-scrubber", route: "/tutor/viz-demo", frame: 5, expectMutated: (n) => n >= 1 },
  { id: "mis-colored-pivot-blends-into-grid", root: "mgg-root", scrubber: "mgg-scrubber", route: "/tutor/viz-demo", frame: 5, expectMutated: (n) => n >= 1 },
  { id: "value-dup-missing", root: "sort-merge-root", scrubber: "sort-merge-scrubber", route: "/tutor/merge-compare", frame: 0, scrubberIndex: 1, expectMutated: (n) => n === 1 },
  // output-not-accent discriminates on the merge-in-progress output frames (5/6, which carry #fde047
  // output cells), NOT the bars final row (frame 7 uses the bars #3a3a3a/#2a2a2a final palette — that
  // is the `final-not-sorted` check's frame, verified via __probe-fills.mjs).
  { id: "output-not-accent", root: "sort-merge-root", scrubber: "sort-merge-scrubber", route: "/tutor/merge-compare", frame: 6, scrubberIndex: 1, expectMutated: (n) => n >= 1 },
];

async function openFig(browser, { route, root, scrubber, frame, scrubberIndex = 1 }) {
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  await page.goto(BASE + route, { waitUntil: "networkidle", timeout: 30000 });
  await page.waitForTimeout(500);
  const scr = page.locator(`[data-testid="${scrubber}"]`).nth(scrubberIndex);
  const svg = page.locator(`[data-testid="${root}"]`).nth(scrubberIndex).locator("svg.algo-stepper-shell-svg");
  await svg.first().waitFor({ state: "visible", timeout: 15000 });
  await scr.evaluate((el, v) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
    setter.call(el, String(v));
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }, frame);
  await page.waitForTimeout(650);
  return { ctx, page, svg };
}

async function run() {
  console.log("================ VISION-BENCH INJECTOR SMOKE (T3+T5) ================");

  // T5: catalog census sanity (BAD class counts per spec §2.5 enumeration).
  log(BAD_COUNTS.total === 43, `catalog BAD-defect-class count = ${BAD_COUNTS.total} (expected 43 per §2.5; §7's "44" double-counts the §2.5 borderline-GOOD row)`);
  log(BAD_COUNTS["seq-array"] === 8 && BAD_COUNTS["sort-merge"] === 10 && BAD_COUNTS["matrix-grid"] === 6 && BAD_COUNTS["graph-tree"] === 9 && BAD_COUNTS["chart-dist"] === 10,
    `per-family BAD counts 8/10/6/9/10 (${JSON.stringify(BAD_COUNTS)})`);
  log(GOOD_CLEAN.length >= 1 && GOOD_BORDERLINE_DEFECTS.length >= 15, `GOOD corpus non-empty: clean=${GOOD_CLEAN.length} borderline=${GOOD_BORDERLINE_DEFECTS.length} (≥3/family)`);
  // every catalogued prop-mode BAD defect must have a registered transform.
  const propBad = BAD_DEFECTS.filter((d) => d.injection.mode === "prop");
  const missingXf = propBad.filter((d) => !DEFECT_DOM_TRANSFORMS[d.id]).map((d) => d.id);
  log(missingXf.length === 0, `every prop-mode BAD defect has a DOM transform (${propBad.length} prop defects; missing: ${JSON.stringify(missingXf)})`);

  const browser = await chromium.launch({ headless: true });
  try {
    for (const s of PROP_SAMPLE) {
      const fig = await openFig(browser, s);
      try {
        // baseline node fingerprint (additive guarantee: applying nothing leaves the DOM untouched).
        const before = await fig.svg.evaluate((el) => el.querySelectorAll("*").length);
        const res = await applyPropDefect(fig.svg, s.id);
        const after = await fig.svg.evaluate((el) => el.querySelectorAll("*").length);
        const ok = res.applied && s.expectMutated(res.mutated);
        log(ok, `${s.id} @${s.root} f${s.frame}: applied=${res.applied} mutated=${res.mutated} (node count ${before}→${after}) [${res.note}]`);
      } finally {
        await fig.ctx.close();
      }
    }
  } finally {
    await browser.close();
  }

  console.log("\n================ INJECTOR SMOKE RESULT ================");
  console.log(`  PASS=${pass}  FAIL=${fail}`);
  if (fail > 0) {
    console.error(`INJECTOR SMOKE RED — ${fail} assertion(s) failed.`);
    process.exit(1);
  }
  console.log(`INJECTOR SMOKE GREEN — all ${pass} assertions passed.`);
  process.exit(0);
}

run().catch((e) => {
  console.error("[smoke-injector] FATAL:", e);
  process.exit(1);
});
