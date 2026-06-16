/**
 * capture-frame-fixtures.mjs — PROVENANCE script for the deterministic liveness self-test fixtures.
 *
 * WHY: the frame-conjunction liveness vitest must exercise the REAL decode+diff path on REAL renderer
 * output, not synthetic numbers (council ruling). This script captures TWO genuinely-different figure
 * crops from the LIVE renderer — figA (frame 6) and figB (frame 22) — so the vitest can assert:
 *   • a FROZEN pair (the same png buffer compared to itself) ⇒ changedPx == 0
 *   • a MOVED  pair (figA vs figB) ⇒ changedPx large (the figure genuinely moved)
 *
 * The capture path is a byte-for-byte mirror of tools/frame-conjunction-gate.mjs::captureFigure:
 *   • viewport 1536×730, deviceScaleFactor 1, reducedMotion 'reduce' (deterministic, no tween)
 *   • the bars skin (merge-compare right column = the 2nd sort-merge-root / scrubberIndex 1)
 *   • the scrubber is React-controlled — native value setter + bubbling input + change events
 *   • SETTLE_MS 650 so React commits + paints after each scrub
 *   • screenshot the SVG (svg.algo-stepper-shell-svg) clipped to its boundingBox, then crop OUT the
 *     top callout band (topmost wide rect in the top 30% of the SVG, bottom+4px; fallback sb.top+22%)
 *
 * Frames 6 and 22 are far apart in the 30-frame trace (both MERGE/divide frames that move), so the
 * cropped figures are visibly different.
 *
 * Usage:  node tutor-web/tools/__fixtures__/capture-frame-fixtures.mjs
 * Prereq: vite dev server on http://localhost:5173 (fails loud if down).
 * Output: src/components/viz/__tests__/fixtures/frameConjunction/{figA,figB}.png
 */

import { chromium } from "playwright-core";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";

const BASE_URL = "http://localhost:5173";
const ROUTE = "/tutor/merge-compare"; // bars skin lives here (right column = scrubberIndex 1)
const SCRUBBER_INDEX = 1; // the bars SortMergeFamily is the 2nd sort-merge-root
const VIEWPORT = { width: 1536, height: 730 };
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), matches the gate
const SETTLE_MS = 650; // reducedMotion=reduce makes the tween instant; let React commit + paint
const FRAME_A = 6;
const FRAME_B = 22;

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(__dirname, "../../src/components/viz/__tests__/fixtures/frameConjunction");
const OUT_A = resolve(OUT_DIR, "figA.png");
const OUT_B = resolve(OUT_DIR, "figB.png");

async function run() {
  // Fail loud if the dev server is down (never silently capture against a dead server).
  const probe = await fetch(BASE_URL + ROUTE).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[capture] dev server not reachable at ${BASE_URL}${ROUTE} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[capture] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }

  mkdirSync(OUT_DIR, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  try {
    const ctx = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: DPR,
      reducedMotion: "reduce", // deterministic: render jumps to target, no 460ms tween
    });
    const page = await ctx.newPage();
    const errs = [];
    page.on("pageerror", (e) => errs.push("PAGE: " + e.message.slice(0, 200)));
    page.on("console", (m) => { if (m.type() === "error") errs.push("CON: " + m.text().slice(0, 200)); });

    await page.goto(BASE_URL + ROUTE, { waitUntil: "networkidle", timeout: 30_000 });
    await page.waitForTimeout(500);

    const scrubbers = page.locator('[data-testid="sort-merge-scrubber"]');
    await scrubbers.first().waitFor({ state: "visible", timeout: 15_000 });
    const scrubber = scrubbers.nth(SCRUBBER_INDEX);
    // The SVG that owns THIS scrubber (its sibling figure region).
    const svg = page.locator('[data-testid="sort-merge-root"]').nth(SCRUBBER_INDEX).locator("svg.algo-stepper-shell-svg");
    await svg.first().waitFor({ state: "visible", timeout: 15_000 });

    // Step the scrubber to frame `idx` (React-controlled — set value + fire input/change).
    const goToFrame = async (idx) => {
      await scrubber.evaluate((el, v) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        setter.call(el, String(v));
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      }, idx);
      await page.waitForTimeout(SETTLE_MS);
    };

    // Capture the figure region for frame `idx`: screenshot the SVG, crop OUT the callout band.
    // Mirrors frame-conjunction-gate.mjs::captureFigure exactly.
    const captureFigure = async (idx) => {
      await goToFrame(idx);
      const box = await svg.boundingBox();
      if (!box) throw new Error(`no bounding box for SVG on frame ${idx}`);

      const calloutBottom = await svg.evaluate((svgEl) => {
        const sb = svgEl.getBoundingClientRect();
        const rects = Array.from(svgEl.querySelectorAll("rect"));
        let best = null;
        for (const r of rects) {
          const rb = r.getBoundingClientRect();
          // candidate callout chip: starts in the top 30% of the SVG and is a wide-ish band
          if (rb.top - sb.top < sb.height * 0.3 && rb.width > sb.width * 0.25) {
            if (best === null || rb.bottom > best) best = rb.bottom;
          }
        }
        if (best === null) return sb.top + sb.height * 0.22; // fallback top-band fraction
        return best + 4; // a hair of margin below the chip
      });

      // Clip = the SVG box minus the callout band at the top.
      const clipTop = Math.max(box.y, calloutBottom);
      const clip = {
        x: Math.round(box.x),
        y: Math.round(clipTop),
        width: Math.round(box.width),
        height: Math.round(box.y + box.height - clipTop),
      };
      if (clip.height < 20) throw new Error(`figure crop too short (${clip.height}px) on frame ${idx}`);
      const png = await page.screenshot({ clip });
      return { png, clip };
    };

    const a = await captureFigure(FRAME_A);
    writeFileSync(OUT_A, a.png);
    console.log(`[capture] figA  frame ${FRAME_A}  clip=${JSON.stringify(a.clip)}  bytes=${a.png.length}  → ${OUT_A}`);

    const b = await captureFigure(FRAME_B);
    writeFileSync(OUT_B, b.png);
    console.log(`[capture] figB  frame ${FRAME_B}  clip=${JSON.stringify(b.clip)}  bytes=${b.png.length}  → ${OUT_B}`);

    if (errs.length) console.warn(`[capture] [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);
    await ctx.close();

    // Provenance sanity: both are PNGs, non-trivial, and differ.
    const SIG = [137, 80, 78, 71, 13, 10, 26, 10];
    const isPng = (buf) => SIG.every((v, i) => buf[i] === v);
    const okA = isPng(a.png) && a.png.length > 1024;
    const okB = isPng(b.png) && b.png.length > 1024;
    const differ = a.png.length !== b.png.length || !a.png.equals(b.png);
    console.log(`[capture] figA isPNG=${isPng(a.png)} bytes=${a.png.length} (>1KB=${a.png.length > 1024})`);
    console.log(`[capture] figB isPNG=${isPng(b.png)} bytes=${b.png.length} (>1KB=${b.png.length > 1024})`);
    console.log(`[capture] DIFFER=${differ} (byteLenA=${a.png.length}, byteLenB=${b.png.length})`);
    if (!okA || !okB || !differ) {
      console.error(`[capture] FIXTURE INVALID: okA=${okA} okB=${okB} differ=${differ}`);
      process.exit(1);
    }
    console.log(`[capture] OK — two valid, distinct PNG fixtures written.`);
  } finally {
    await browser.close();
  }
}

run().catch((e) => {
  console.error("[capture] FATAL:", e);
  process.exit(1);
});
