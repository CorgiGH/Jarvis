/**
 * capture-figC.mjs — PROVENANCE script for the THIRD liveness self-test fixture (figC).
 *
 * WHY: the frame-conjunction self-test's "moved=live" leg compares figA (crop 620×347) vs
 * figB (crop 620×333). Because the crop HEIGHTS differ, diffPng takes its REFLOW short-circuit
 * (size mismatch ⇒ changedPx = w*h+1, a sentinel) and the per-pixel changed-counting loop is
 * NEVER exercised to produce a meaningful count. The frozen leg (figA vs figA) exercises the loop
 * only to return 0. So a regression where the counting loop always returns ~0 would go undetected
 * (false-GREEN). figC is a SAME-SIZE (620×347 == figA) but BYTE-DIFFERENT crop, so the self-test
 * can prove the per-pixel counting loop produces a real, meaningful count on the non-reflow path.
 *
 * The capture path is a byte-for-byte mirror of tools/frame-conjunction-gate.mjs::captureFigure
 * (and capture-frame-fixtures.mjs): viewport 1536×730, DPR 1, reducedMotion 'reduce', the bars skin
 * (merge-compare right column = the 2nd sort-merge-root / scrubberIndex 1), SETTLE_MS 650, screenshot
 * the SVG (svg.algo-stepper-shell-svg) clipped to its boundingBox, then crop OUT the top callout band.
 *
 * STRATEGY: capture frame 6 (= figA's source frame) to learn the reference crop dimensions
 * (must be 620×347 to match the committed figA), then scan candidate merge-phase frames and emit the
 * FIRST whose crop has the SAME width AND height as the reference but whose PNG bytes DIFFER from the
 * committed figA.png on disk. Candidate scan order = merge-phase frames first, then (if none) all 0..29.
 *
 * Usage:  node tutor-web/tools/__fixtures__/capture-figC.mjs
 * Prereq: vite dev server on http://localhost:5173 (fails loud if down).
 * Output: src/components/viz/__tests__/fixtures/frameConjunction/figC.png
 */

import { chromium } from "playwright-core";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";

const BASE_URL = "http://localhost:5173";
const ROUTE = "/tutor/merge-compare"; // bars skin lives here (right column = scrubberIndex 1)
const SCRUBBER_INDEX = 1; // the bars SortMergeFamily is the 2nd sort-merge-root
const VIEWPORT = { width: 1536, height: 730 };
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), matches the gate
const SETTLE_MS = 650; // reducedMotion=reduce makes the tween instant; let React commit + paint
const FRAME_A = 6; // figA's source frame — its crop dims are the reference (must be 620×347)

// Candidate frames to scan for a same-size-but-different crop. Merge-phase frames first (they tend to
// share the figA crop height because the merge layout band is stable), then a full-range fallback.
const MERGE_CANDIDATES = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21];
const ALL_CANDIDATES = [];
for (let n = 0; n < 30; n++) if (n !== FRAME_A) ALL_CANDIDATES.push(n);

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(__dirname, "../../src/components/viz/__tests__/fixtures/frameConjunction");
const OUT_C = resolve(OUT_DIR, "figC.png");
const FIG_A_PATH = resolve(OUT_DIR, "figA.png");

const SIG = [137, 80, 78, 71, 13, 10, 26, 10];
const isPng = (buf) => SIG.every((v, i) => buf[i] === v);

async function run() {
  // Fail loud if the dev server is down (never silently capture against a dead server).
  const probe = await fetch(BASE_URL + ROUTE).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[capture] dev server not reachable at ${BASE_URL}${ROUTE} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[capture] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }

  const figABytes = readFileSync(FIG_A_PATH);
  console.log(`[capture] reference figA on disk: bytes=${figABytes.length} isPNG=${isPng(figABytes)}`);

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

    // 1. Capture frame 6 to learn the reference crop dimensions (must match committed figA = 620×347).
    const ref = await captureFigure(FRAME_A);
    const refW = ref.clip.width;
    const refH = ref.clip.height;
    console.log(`[capture] reference frame ${FRAME_A} crop = ${refW}x${refH} (bytes=${ref.png.length})`);
    if (refW !== 620 || refH !== 347) {
      console.error(`[capture] WARNING: reference crop ${refW}x${refH} != expected 620x347 — figA may have been recaptured under different layout.`);
    }

    // 2. Scan candidates for the FIRST same-size-but-byte-different crop vs the committed figA.png.
    const scanFor = async (candidates) => {
      const tried = [];
      for (const idx of candidates) {
        const cap = await captureFigure(idx);
        const sameSize = cap.clip.width === refW && cap.clip.height === refH;
        const differs = cap.png.length !== figABytes.length || !cap.png.equals(figABytes);
        tried.push(`f${idx}:${cap.clip.width}x${cap.clip.height}${sameSize ? (differs ? " SAME-SIZE+DIFF✓" : " same-size-but-equal") : " size-mismatch"}`);
        if (sameSize && differs) {
          return { idx, cap, tried };
        }
      }
      return { idx: null, cap: null, tried };
    };

    let hit = await scanFor(MERGE_CANDIDATES);
    console.log(`[capture] merge-phase scan: ${hit.tried.join("  ")}`);
    if (hit.idx === null) {
      console.log(`[capture] no merge-phase hit — widening to all frames 0..29.`);
      const wide = await scanFor(ALL_CANDIDATES.filter((n) => !MERGE_CANDIDATES.includes(n)));
      console.log(`[capture] wide scan: ${wide.tried.join("  ")}`);
      hit = wide;
    }

    if (errs.length) console.warn(`[capture] [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);

    if (hit.idx === null) {
      await ctx.close();
      console.error(`[capture] BLOCKED: no frame in 0..29 yields a ${refW}x${refH} crop that differs from figA.`);
      console.error(`[capture] blocked=true`);
      process.exit(2);
    }

    // 3. Write figC and verify on disk.
    writeFileSync(OUT_C, hit.cap.png);
    console.log(`[capture] figC  frame ${hit.idx}  clip=${JSON.stringify(hit.cap.clip)}  bytes=${hit.cap.png.length}  → ${OUT_C}`);
    await ctx.close();

    // Provenance sanity: valid PNG, non-trivial, exact reference size, byte-different from figA.
    const onDisk = readFileSync(OUT_C);
    const okPng = isPng(onDisk) && onDisk.length > 1024;
    const okDiff = onDisk.length !== figABytes.length || !onDisk.equals(figABytes);
    console.log(`[capture] figC isPNG=${isPng(onDisk)} bytes=${onDisk.length} (>1KB=${onDisk.length > 1024}) DIFF-from-figA=${okDiff}`);
    if (!okPng || !okDiff) {
      console.error(`[capture] FIXTURE INVALID: okPng=${okPng} okDiff=${okDiff}`);
      process.exit(1);
    }
    console.log(`[capture] OK — figC is a valid, same-size-${refW}x${refH}, byte-distinct PNG (frame ${hit.idx}).`);
  } finally {
    await browser.close();
  }
}

run().catch((e) => {
  console.error("[capture] FATAL:", e);
  process.exit(1);
});
