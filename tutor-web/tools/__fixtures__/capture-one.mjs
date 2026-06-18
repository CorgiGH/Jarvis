/**
 * capture-one.mjs — generic single-frame capture for the merge-compare BARS skin.
 *
 * Mirrors the capture path of capture-figC.mjs / frame-conjunction-gate.mjs::captureFigure:
 *   viewport 1536×730, DPR 1, reducedMotion 'reduce', the bars skin (merge-compare right column =
 *   the 2nd sort-merge-root / scrubberIndex 1), SETTLE_MS 650, screenshot the SVG
 *   (svg.algo-stepper-shell-svg) clipped to its boundingBox, then crop OUT the top callout band
 *   so the saved PNG is BARE / un-captioned.
 *
 * Navigates fresh every run (picks up the current bundle via HMR).
 *
 * Usage:  node tutor-web/tools/__fixtures__/capture-one.mjs <frameIndex> <outAbsPath>
 * Prereq: vite dev server on http://localhost:5173 (fails loud if down).
 */

import { chromium } from "playwright-core";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { dirname } from "node:path";

const BASE_URL = "http://localhost:5173";
const ROUTE = "/tutor/merge-compare"; // bars skin lives here (right column = scrubberIndex 1)
const SCRUBBER_INDEX = 1; // the bars SortMergeFamily is the 2nd sort-merge-root
const VIEWPORT = { width: 1536, height: 730 };
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), matches the gate
const SETTLE_MS = 650; // reducedMotion=reduce makes the tween instant; let React commit + paint

const SIG = [137, 80, 78, 71, 13, 10, 26, 10];
const isPng = (buf) => SIG.every((v, i) => buf[i] === v);

const frameIndex = Number(process.argv[2]);
const outAbsPath = process.argv[3];
if (!Number.isFinite(frameIndex) || !outAbsPath) {
  console.error("usage: node capture-one.mjs <frameIndex> <outAbsPath>");
  process.exit(1);
}

async function run() {
  // Fail loud if the dev server is down (never silently capture against a dead server).
  const probe = await fetch(BASE_URL + ROUTE).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[capture-one] dev server not reachable at ${BASE_URL}${ROUTE} (got ${probe ? probe.status : "no response"}).`);
    process.exit(1);
  }

  mkdirSync(dirname(outAbsPath), { recursive: true });

  const browser = await chromium.launch({ headless: true });
  try {
    const ctx = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: DPR,
      reducedMotion: "reduce", // deterministic: render jumps to target, no tween
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

    const cap = await captureFigure(frameIndex);
    writeFileSync(outAbsPath, cap.png);
    await ctx.close();

    const onDisk = readFileSync(outAbsPath);
    const okPng = isPng(onDisk) && onDisk.length > 1024;
    if (errs.length) console.warn(`[capture-one] [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);
    console.log(`[capture-one] frame ${frameIndex}  clip=${JSON.stringify(cap.clip)}  bytes=${onDisk.length}  isPNG=${isPng(onDisk)}  → ${outAbsPath}`);
    if (!okPng) {
      console.error(`[capture-one] INVALID PNG: okPng=${okPng}`);
      process.exit(1);
    }
  } finally {
    await browser.close();
  }
}

run().catch((e) => {
  console.error("[capture-one] FATAL:", e);
  process.exit(1);
});
