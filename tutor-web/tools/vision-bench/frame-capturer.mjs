/**
 * frame-capturer.mjs — generalized BARE-figure capture for the vision-judge bench (spec §3.0 #3, T1).
 *
 * Lifts the PROVEN pipeline from tools/__fixtures__/capture-one.mjs (the merge-compare seed) and
 * PARAMETERIZES it over (route, rootTestid, scrubberTestid, scrubberIndex, frameIndex) so it works
 * for all 5 bench families, not just sort-merge. Everything load-bearing is kept byte-identical to
 * the seed / frame-conjunction-gate.mjs::captureFigure:
 *   • viewport 1536×730, DPR 1, reducedMotion "reduce"  (Alex's real screen; deterministic, no tween)
 *   • SETTLE_MS 650 after each scrubber move (reducedMotion makes the tween instant; let React commit)
 *   • screenshot the figure SVG (svg.algo-stepper-shell-svg) clipped to its boundingBox
 *   • crop OUT the top callout band (live chip-rect measurement) so the saved PNG is BARE / un-captioned
 *
 * Generalizations vs the seed:
 *   • root/scrubber are PARAMETERS. The seed hardcoded `sort-merge-root` / `sort-merge-scrubber`
 *     and `.nth(SCRUBBER_INDEX)`. Here `rootTestid` + `scrubberTestid` + `scrubberIndex` select the
 *     right family + the right COPY when a root appears multiple times on the page (on /viz-demo each
 *     of the 5 family roots is painted TWICE — a dark-section copy and a standalone tile — verified
 *     live: roots=2 for every family; the standalone tile is scrubberIndex 1).
 *   • PREDICT-GATE unlock (`[data-testid^="predict-"]` + `lectie-next`) is applied ONLY when
 *     predictGate:true (the lectie routes), exactly as frame-conjunction-gate.mjs:109-115 does. The
 *     scrubber is `disabled={gateLocked}` (verified AlgoStepperShell.tsx:414) so we MUST unlock first.
 *   • per-family CROP PROFILE (crop-profiles.mjs): in addition to the top-band crop, mask any
 *     answer-bearing mid/bottom region (chart P-box, matrix rowOp rail) IN-DOM before the screenshot,
 *     then run the HARD answer-leak assert and FAIL the capture if any answer token survives.
 *   • the LABEL never touches pixels: it is written only to the side `frames.json` manifest.
 *
 * captureBareFigure({ route, rootTestid, scrubberTestid, scrubberIndex, frameIndex, predictGate,
 *                      cropProfile, query }) → { pngPath, clip, browserErrors, maskCount }
 *
 * captureMany(items, { outDir, manifestPath, label fields... }) → writes PNGs + frames.json,
 *   reusing ONE browser/page per (route, query) so a corpus run is bounded-serial (spec §3.1).
 *
 * Usage (self-smoke):
 *   node tutor-web/tools/vision-bench/frame-capturer.mjs --smoke <outDirAbs>
 * Prereq: vite dev server on http://localhost:5173 (fails loud if down).
 */

import { chromium } from "playwright-core";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { getCropProfile, applyDomMasks, assertNoAnswerLeak } from "./crop-profiles.mjs";

export const BASE_URL = "http://localhost:5173";
export const VIEWPORT = { width: 1536, height: 730 };
export const DPR = 1; // Alex's real DPR (1536×864 @ 1x) — matches the seed gate.
export const SETTLE_MS = 650; // reducedMotion=reduce makes the tween instant; let React commit + paint.

const PNG_SIG = [137, 80, 78, 71, 13, 10, 26, 10];
const isPng = (buf) => PNG_SIG.every((v, i) => buf[i] === v);

/**
 * Probe the dev server for a route; fail loud if down (never silently capture against a dead server).
 * @param {string} route
 */
async function probeServer(route) {
  const probe = await fetch(BASE_URL + route).catch(() => null);
  if (!probe || !probe.ok) {
    throw new Error(
      `[frame-capturer] dev server not reachable at ${BASE_URL}${route} ` +
        `(got ${probe ? probe.status : "no response"}). Start it: (cd tutor-web && npm run dev).`,
    );
  }
}

/**
 * Open a fresh page on `route` (optionally with `?query`), unlock the predict gate if asked, and
 * return { browser, ctx, page, scrubber, svg, browserErrors }. Caller owns teardown via ctx.close()/
 * browser.close(). Reused by captureMany for all frames sharing the same (route, query) navigation.
 *
 * @param {object} a
 * @param {string} a.route
 * @param {string} a.rootTestid
 * @param {string} a.scrubberTestid
 * @param {number} a.scrubberIndex   which copy of the root/scrubber on the page (.nth)
 * @param {boolean} a.predictGate
 * @param {string} [a.query]         additive query string, e.g. "defect=foo" (no leading '?')
 * @param {import('playwright-core').Browser} [a.browser]  reuse an existing browser if provided
 */
async function openFigurePage({ route, rootTestid, scrubberTestid, scrubberIndex, predictGate, query, browser }) {
  const ownBrowser = !browser;
  const b = browser || (await chromium.launch({ headless: true }));
  const ctx = await b.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: DPR,
    reducedMotion: "reduce", // deterministic: render jumps to target, no tween
  });
  const page = await ctx.newPage();
  const browserErrors = [];
  page.on("pageerror", (e) => browserErrors.push("PAGE: " + e.message.slice(0, 200)));
  page.on("console", (m) => {
    if (m.type() === "error") browserErrors.push("CON: " + m.text().slice(0, 200));
  });

  const url = BASE_URL + route + (query ? (route.includes("?") ? "&" : "?") + query : "");
  await page.goto(url, { waitUntil: "networkidle", timeout: 30_000 });
  await page.waitForTimeout(500);

  if (predictGate) {
    // unlock the gated beat, then advance to the figure beat (mirrors frame-conjunction-gate.mjs:109-115).
    await page.locator('[data-testid^="predict-"]').first().click();
    await page.waitForTimeout(300);
    await page.locator('[data-testid="lectie-next"]').click();
    await page.waitForTimeout(400);
  }

  const scrubbers = page.locator(`[data-testid="${scrubberTestid}"]`);
  await scrubbers.first().waitFor({ state: "visible", timeout: 15_000 });
  const scrubber = scrubbers.nth(scrubberIndex);
  const svg = page.locator(`[data-testid="${rootTestid}"]`).nth(scrubberIndex).locator("svg.algo-stepper-shell-svg");
  await svg.first().waitFor({ state: "visible", timeout: 15_000 });

  return { browser: b, ownBrowser, ctx, page, scrubber, svg, browserErrors };
}

/**
 * Step the React-controlled scrubber to `idx` (set value + fire input/change), then settle.
 * Byte-identical to the seed goToFrame.
 */
async function goToFrame(scrubber, page, idx) {
  await scrubber.evaluate((el, v) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
    setter.call(el, String(v));
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }, idx);
  await page.waitForTimeout(SETTLE_MS);
}

/**
 * Measure the top callout band's painted bottom (CSS px, page coords) — the seed heuristic, verbatim:
 * the topmost wide <rect> in the SVG's top 30% defines the callout chip's bottom edge; fallback to a
 * 22% top-band fraction. Returns the y to crop down to.
 */
async function measureCalloutBottom(svg) {
  return await svg.evaluate((svgEl) => {
    const sb = svgEl.getBoundingClientRect();
    const rects = Array.from(svgEl.querySelectorAll("rect"));
    let best = null;
    for (const r of rects) {
      const rb = r.getBoundingClientRect();
      if (rb.top - sb.top < sb.height * 0.3 && rb.width > sb.width * 0.25) {
        if (best === null || rb.bottom > best) best = rb.bottom;
      }
    }
    if (best === null) return sb.top + sb.height * 0.22; // fallback top-band fraction
    return best + 4; // a hair of margin below the chip
  });
}

/**
 * Capture ONE bare figure frame. If `page`/`scrubber`/`svg` are passed (corpus reuse) the navigation
 * is skipped; otherwise a fresh browser/page is opened and torn down.
 *
 * @param {object} args  see openFigurePage args, PLUS:
 * @param {number} args.frameIndex
 * @param {string} args.cropProfile   key into crop-profiles.mjs (usually = family)
 * @param {string} args.outPath       absolute PNG path to write
 * @param {(svg)=>Promise<any>} [args.postFrameHook]  ADDITIVE: runs AFTER the frame settles +
 *        scrolls into view, BEFORE the per-family masks/crop/leak-assert/screenshot. The vision-bench
 *        prop-mode injector (applyPropDefect) is wired here so the screenshot captures the mutated DOM
 *        and the leak-assert/masks see the same DOM. Default undefined ⇒ no behaviour change (the
 *        corpus-capture path that captures CLEAN frames is unaffected).
 * @param {object} [reuse]            { page, scrubber, svg } from a previously-opened figure page
 * @returns {Promise<{pngPath:string, clip:object, maskCount:number, browserErrors:string[], hookResult?:any}>}
 */
export async function captureBareFigure(args, reuse = null) {
  const { route, frameIndex, cropProfile, outPath, postFrameHook } = args;
  const profile = getCropProfile(cropProfile);
  if (!outPath) throw new Error("[frame-capturer] captureBareFigure requires args.outPath");

  let opened = reuse;
  let teardown = null;
  if (!opened) {
    await probeServer(route);
    const o = await openFigurePage(args);
    opened = o;
    teardown = async () => {
      await o.ctx.close();
      if (o.ownBrowser) await o.browser.close();
    };
  }
  const { page, scrubber, svg } = opened;
  const browserErrors = opened.browserErrors || [];

  try {
    await goToFrame(scrubber, page, frameIndex);

    // Scroll the figure into the viewport BEFORE measuring/clipping. On /viz-demo each family root is
    // painted twice and the standalone tile can sit thousands of px below the fold (verified: the
    // seq-array tile is at y≈2791 in a 730px viewport) — page.screenshot({clip}) is in current-scroll
    // page coords, so an off-screen clip throws "Clipped area … outside the resulting image". The seed
    // (merge-compare) never hit this because its figure is near the top. Re-settle after the scroll.
    await svg.scrollIntoViewIfNeeded();
    await page.waitForTimeout(150);

    // ADDITIVE prop-mode injection hook: mutate the live SVG (e.g. applyPropDefect) AFTER the frame is
    // settled + on-screen and BEFORE masks/crop/screenshot, so the captured pixels carry the defect and
    // the leak-assert sees the same DOM. No-op when no hook is passed (the clean-frame path).
    let hookResult;
    if (typeof postFrameHook === "function") {
      hookResult = await postFrameHook(svg);
      await page.waitForTimeout(120); // let the mutation paint
    }

    const box = await svg.boundingBox();
    if (!box) throw new Error(`[frame-capturer] no bounding box for SVG on ${route} frame ${frameIndex}`);

    // Apply the per-family DOM masks (chart P-box / matrix rowOp band) BEFORE measuring the crop, so
    // a masked element no longer counts as the wide top-band rect (the mask rects are appended last;
    // they live in the bottom band / mid-plot, not the top 30%, so they do not perturb the callout
    // measurement — but we mask first to keep the leak-assert and the screenshot consistent).
    const maskCount = await applyDomMasks(svg, profile);

    // Top callout band crop (only when the profile asks for it).
    let clipTop = box.y;
    if (profile.cropTopCallout) {
      const calloutBottom = await measureCalloutBottom(svg);
      clipTop = Math.max(box.y, calloutBottom);
    }
    const clip = {
      x: Math.round(box.x),
      y: Math.round(clipTop),
      width: Math.round(box.width),
      height: Math.round(box.y + box.height - clipTop),
    };
    if (clip.height < 20) throw new Error(`[frame-capturer] figure crop too short (${clip.height}px) on frame ${frameIndex}`);

    // HARD answer-leak assert: no answer-bearing element may survive into the final crop unmasked.
    const leak = await assertNoAnswerLeak(svg, profile, clip);
    if (!leak.ok) {
      throw new Error(
        `[frame-capturer] ANSWER LEAK on ${route} frame ${frameIndex} (profile '${cropProfile}'): ` +
          JSON.stringify(leak.leaks),
      );
    }

    mkdirSync(dirname(outPath), { recursive: true });
    const png = await page.screenshot({ clip });
    writeFileSync(outPath, png);

    const onDisk = readFileSync(outPath);
    if (!(isPng(onDisk) && onDisk.length > 1024)) {
      throw new Error(`[frame-capturer] INVALID PNG written to ${outPath} (bytes=${onDisk.length}, isPNG=${isPng(onDisk)})`);
    }

    return { pngPath: outPath, clip, maskCount, browserErrors: browserErrors.slice(0, 5), hookResult };
  } finally {
    if (teardown) await teardown();
  }
}

/**
 * Capture MANY frames, reusing ONE browser. Groups items by (route, query, predictGate, scrubberIndex)
 * so each navigation is done once; writes labeled PNGs + a frames.json manifest. The LABEL lives ONLY
 * in the manifest (never burned into pixels).
 *
 * @param {Array<object>} items  each: { id|defectId, family, label, route, rootTestid, scrubberTestid,
 *                                       scrubberIndex, frameIndex, predictGate, cropProfile, query,
 *                                       expectedDetGateBlind, skin? }
 * @param {object} opts
 * @param {string} opts.outDir         absolute dir for PNGs + manifest
 * @param {string} [opts.manifestPath] absolute path for frames.json (default outDir/frames.json)
 * @returns {Promise<{manifestPath:string, frames:Array}>}
 */
export async function captureMany(items, { outDir, manifestPath } = {}) {
  if (!outDir) throw new Error("[frame-capturer] captureMany requires opts.outDir");
  const mPath = manifestPath || join(outDir, "frames.json");
  mkdirSync(outDir, { recursive: true });

  // probe once on the first item's route
  if (items.length) await probeServer(items[0].route);

  const groupKey = (it) => `${it.route}|${it.query || ""}|${it.predictGate ? 1 : 0}|${it.scrubberIndex}|${it.rootTestid}`;
  const groups = new Map();
  for (const it of items) {
    const k = groupKey(it);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(it);
  }

  const frames = [];
  const browser = await chromium.launch({ headless: true });
  try {
    for (const [, group] of groups) {
      const first = group[0];
      const opened = await openFigurePage({ ...first, browser });
      try {
        for (const it of group) {
          const fileName = `${it.label}__${it.family}__${it.id || it.defectId || "frame"}__f${it.frameIndex}.png`;
          const outPath = join(outDir, fileName);
          // A per-item postFrameHook (e.g. the prop-mode applyPropDefect closure) is passed straight
          // through to captureBareFigure. Clean frames carry no hook (no behaviour change).
          const res = await captureBareFigure({ ...it, outPath, postFrameHook: it.postFrameHook }, opened);
          frames.push({
            path: outPath,
            label: it.label,
            family: it.family,
            defectId: it.id || it.defectId || null,
            expectedDetGateBlind: it.expectedDetGateBlind ?? null,
            route: it.route,
            frameIndex: it.frameIndex,
            skin: it.skin ?? null,
            cropProfile: it.cropProfile,
            maskCount: res.maskCount,
            clip: res.clip,
            injectionMutated: res.hookResult && typeof res.hookResult === "object" ? res.hookResult.mutated ?? null : null,
          });
        }
      } finally {
        await opened.ctx.close();
      }
    }
  } finally {
    await browser.close();
  }

  writeFileSync(mPath, JSON.stringify({ generatedAt: new Date().toISOString(), frames }, null, 2));
  return { manifestPath: mPath, frames };
}

// ── Self-smoke (T1): seq-array frame 5 on /viz-demo + the chart P-box-gone check (delegated to the
// dedicated smoke script). Kept tiny per the Phase-1 rule (no corpus run here). ───────────────────
async function smoke(outDir) {
  const dir = resolve(outDir);
  // seq-array frame 5 on the standalone /viz-demo tile (scrubberIndex 1 = the non-dark copy).
  const res = await captureBareFigure({
    route: "/tutor/viz-demo",
    rootTestid: "seq-array-root",
    scrubberTestid: "seq-array-scrubber",
    scrubberIndex: 1,
    frameIndex: 5,
    predictGate: false,
    cropProfile: "seq-array",
    outPath: join(dir, "smoke-seq-array-f5.png"),
  });
  console.log(`[smoke] seq-array f5 → ${res.pngPath}  clip=${JSON.stringify(res.clip)}  masks=${res.maskCount}`);
  if (res.browserErrors.length) console.warn(`[smoke] browser errors: ${JSON.stringify(res.browserErrors)}`);
  return res;
}

if (process.argv[2] === "--smoke") {
  const outDir = process.argv[3] || resolve(process.cwd(), "../build-review/vision-bench-smoke");
  smoke(outDir)
    .then(() => process.exit(0))
    .catch((e) => {
      console.error("[frame-capturer] SMOKE FATAL:", e);
      process.exit(1);
    });
}
