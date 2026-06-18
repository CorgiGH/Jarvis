/**
 * smoke.mjs — Phase-1 unit/smoke for T1 (frame-capturer) + T2 (crop-profiles). TINY by design
 * (a handful of frames; the full corpus is Phase 2, NOT run here).
 *
 * Asserts:
 *   1. seq-array frame 5 captures a valid BARE PNG on /viz-demo (the §10 T1 smoke).
 *   2. chart-dist frame 3 (reveal=4, the annotation beat) captures WITH the P-box masked, and the
 *      printed P-value annotation `<g data-cd="annot">` is GONE from the bare cropped PNG. Proven two
 *      ways: (a) the answer-leak assert passes (would THROW if the P-box survived), and (b) a direct
 *      DOM cross-check that a mask rect was painted over the annot group AND the annot text bbox is
 *      covered by that mask. We ALSO capture the SAME frame with NO mask (a control) and confirm the
 *      leak assert WOULD have fired there — proving the assert has teeth (RED→GREEN demonstration).
 *   3. matrix-grid gauss frame 5 (a rowOp step) masks the bottom rowOp rail (band mask) and the
 *      leak assert passes; control with no mask trips the assert.
 *   4. All 5 family roots are reachable + scrubbable (generalization claim).
 *
 * Usage: node tutor-web/tools/vision-bench/smoke.mjs [outDirAbs]
 * Prereq: vite dev server on http://localhost:5173.
 */

import { chromium } from "playwright-core";
import { readFileSync, mkdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { captureBareFigure, BASE_URL, VIEWPORT, DPR } from "./frame-capturer.mjs";
import { getCropProfile, applyDomMasks, assertNoAnswerLeak } from "./crop-profiles.mjs";

const OUT = resolve(process.argv[2] || join(process.cwd(), "../build-review/vision-bench-smoke"));
mkdirSync(OUT, { recursive: true });

const FAMILIES = [
  { root: "seq-array-root", scrubber: "seq-array-scrubber" },
  { root: "mg-root", scrubber: "mg-scrubber" },
  { root: "mgg-root", scrubber: "mgg-scrubber" },
  { root: "graph-tree-root", scrubber: "graph-tree-scrubber" },
  { root: "chart-dist-root", scrubber: "chart-dist-scrubber" },
];

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

/**
 * Open a figure page raw (for the control / DOM cross-checks that captureBareFigure abstracts away).
 */
async function openRaw(browser, { route, rootTestid, scrubberTestid, scrubberIndex, frameIndex }) {
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: DPR, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  await page.goto(BASE_URL + route, { waitUntil: "networkidle", timeout: 30_000 });
  await page.waitForTimeout(500);
  const scrubber = page.locator(`[data-testid="${scrubberTestid}"]`).nth(scrubberIndex);
  const svg = page.locator(`[data-testid="${rootTestid}"]`).nth(scrubberIndex).locator("svg.algo-stepper-shell-svg");
  await svg.first().waitFor({ state: "visible", timeout: 15_000 });
  await scrubber.evaluate((el, v) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
    setter.call(el, String(v));
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }, frameIndex);
  await page.waitForTimeout(650);
  await svg.scrollIntoViewIfNeeded();
  await page.waitForTimeout(150);
  const box = await svg.boundingBox();
  const clip = { x: Math.round(box.x), y: Math.round(box.y), width: Math.round(box.width), height: Math.round(box.height) };
  return { ctx, page, svg, clip };
}

async function run() {
  console.log(`================ VISION-BENCH SMOKE (T1+T2) ================`);
  console.log(`out: ${OUT}`);

  // ── (4) generalization: all 5 family roots reachable + scrubbable ──
  const b0 = await chromium.launch({ headless: true });
  try {
    const ctx = await b0.newContext({ viewport: VIEWPORT, deviceScaleFactor: DPR, reducedMotion: "reduce" });
    const p = await ctx.newPage();
    await p.goto(BASE_URL + "/tutor/viz-demo", { waitUntil: "networkidle", timeout: 30_000 });
    await p.waitForTimeout(700);
    for (const f of FAMILIES) {
      const roots = await p.locator(`[data-testid="${f.root}"]`).count();
      const scrubs = await p.locator(`[data-testid="${f.scrubber}"]`).count();
      const max = scrubs > 0 ? await p.locator(`[data-testid="${f.scrubber}"]`).first().getAttribute("max") : null;
      log(roots >= 1 && scrubs >= 1, `${f.root}: roots=${roots} scrubbers=${scrubs} max=${max}`);
    }
    await ctx.close();
  } finally {
    await b0.close();
  }

  // ── (1) seq-array frame 5 — valid bare PNG ──
  const r1 = await captureBareFigure({
    route: "/tutor/viz-demo",
    rootTestid: "seq-array-root",
    scrubberTestid: "seq-array-scrubber",
    scrubberIndex: 1,
    frameIndex: 5,
    predictGate: false,
    cropProfile: "seq-array",
    outPath: join(OUT, "smoke-seq-array-f5.png"),
  });
  const sz1 = readFileSync(r1.pngPath).length;
  log(sz1 > 1024 && r1.clip.height >= 20, `seq-array f5 captured (bytes=${sz1}, clip.h=${r1.clip.height}, masks=${r1.maskCount})`);

  // ── (2) chart-dist frame 3 — P-box MASKED + GONE, with a control proving the assert has teeth ──
  const r2 = await captureBareFigure({
    route: "/tutor/viz-demo",
    rootTestid: "chart-dist-root",
    scrubberTestid: "chart-dist-scrubber",
    scrubberIndex: 1,
    frameIndex: 3, // reveal=4 → annotation beat (the P-value appears)
    predictGate: false,
    cropProfile: "chart-dist",
    outPath: join(OUT, "smoke-chart-dist-f3-masked.png"),
  });
  log(r2.maskCount >= 1, `chart-dist f3 masked the P-box (maskCount=${r2.maskCount}, expected ≥1)`);
  const sz2 = readFileSync(r2.pngPath).length;
  log(sz2 > 1024, `chart-dist f3 captured a valid bare PNG (bytes=${sz2})`);

  // DOM cross-check + control (RED→GREEN): re-open raw, confirm the annot group exists + intersects
  // the crop UNMASKED (control would leak → assert RED), then mask + confirm assert GREEN.
  const bb = await chromium.launch({ headless: true });
  try {
    const raw = await openRaw(bb, {
      route: "/tutor/viz-demo",
      rootTestid: "chart-dist-root",
      scrubberTestid: "chart-dist-scrubber",
      scrubberIndex: 1,
      frameIndex: 3,
    });
    const profile = getCropProfile("chart-dist");
    const annotExists = (await raw.svg.locator('[data-cd="annot"]').count()) >= 1;
    log(annotExists, `chart-dist f3: P-box <g data-cd="annot"> exists in the live DOM (the thing we must remove)`);

    // CONTROL: no mask → assert must FIND a leak (proves the assert isn't vacuously green). Use the
    // FULL SVG bbox as the crop so the P-box is unambiguously INSIDE it (the standard top-band crop
    // can incidentally remove the annot box because it matches the wide-top-rect callout heuristic —
    // verified: annot top y≈143 < measured callout-bottom y≈173 — which is fragile, hence the
    // explicit DOM mask is the real defense being demonstrated here, not the accidental crop).
    const cropFull = { x: raw.clip.x, y: raw.clip.y, width: raw.clip.width, height: raw.clip.height };
    const leakBefore = await assertNoAnswerLeak(raw.svg, profile, cropFull);
    log(!leakBefore.ok && leakBefore.leaks.length >= 1, `CONTROL (no mask, full-SVG crop): leak assert RED — P-box would survive (leaks=${leakBefore.leaks.length})`);

    // GREEN: apply the mask, re-assert → no leak.
    const masked = await applyDomMasks(raw.svg, profile);
    const leakAfter = await assertNoAnswerLeak(raw.svg, profile, cropFull);
    log(masked >= 1 && leakAfter.ok, `MASKED: leak assert GREEN — P-box covered (masks=${masked}, leaks=${leakAfter.leaks.length})`);
    await raw.ctx.close();

    // ── (3) matrix-grid gauss frame 5 — rowOp band mask ──
    const r3 = await captureBareFigure({
      route: "/tutor/viz-demo",
      rootTestid: "mgg-root",
      scrubberTestid: "mgg-scrubber",
      scrubberIndex: 1,
      frameIndex: 5, // "schimbă E2 ↔ E3" — has a rowOp rail
      predictGate: false,
      cropProfile: "matrix-grid",
      outPath: join(OUT, "smoke-mgg-f5-masked.png"),
    });
    log(r3.maskCount >= 1, `matrix-grid(gauss) f5 masked the rowOp rail (band mask count=${r3.maskCount}, expected ≥1)`);

    // control for matrix band: raw, count bottom-band text, assert RED without mask.
    const rawM = await openRaw(bb, {
      route: "/tutor/viz-demo",
      rootTestid: "mgg-root",
      scrubberTestid: "mgg-scrubber",
      scrubberIndex: 1,
      frameIndex: 5,
    });
    const mProfile = getCropProfile("matrix-grid");
    const cropFullM = { x: rawM.clip.x, y: rawM.clip.y, width: rawM.clip.width, height: rawM.clip.height };
    const mLeakBefore = await assertNoAnswerLeak(rawM.svg, mProfile, cropFullM);
    log(!mLeakBefore.ok && mLeakBefore.leaks.length >= 1, `CONTROL (no mask): matrix rowOp band leak RED (leaks=${mLeakBefore.leaks.length})`);
    const mMasked = await applyDomMasks(rawM.svg, mProfile);
    const mLeakAfter = await assertNoAnswerLeak(rawM.svg, mProfile, cropFullM);
    log(mMasked >= 1 && mLeakAfter.ok, `MASKED: matrix rowOp band GREEN (masks=${mMasked}, leaks=${mLeakAfter.leaks.length})`);
    await rawM.ctx.close();
  } finally {
    await bb.close();
  }

  console.log(`\n================ SMOKE RESULT ================`);
  console.log(`  PASS=${pass}  FAIL=${fail}`);
  if (fail > 0) {
    console.error(`SMOKE RED — ${fail} assertion(s) failed.`);
    process.exit(1);
  }
  console.log(`SMOKE GREEN — all ${pass} assertions passed.`);
  process.exit(0);
}

run().catch((e) => {
  console.error("[smoke] FATAL:", e);
  process.exit(1);
});
