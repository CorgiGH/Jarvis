/**
 * frame-conjunction-gate.mjs — the STATE↔FIGURE CONJUNCTION gate.
 *
 * THE INVARIANT (the bug class this gate exists to catch):
 *   For every consecutive frame pair (N → N+1) whose typed STATE changed, the rasterized FIGURE
 *   REGION must change above a threshold. A pair whose STATE changed but whose FIGURE PIXELS did NOT
 *   is a HARD FAIL — a frozen figure over a live state — and can NEVER be reclassified as a "hold".
 *   A pair whose state did NOT change is an exempt hold (figure may legitimately be identical).
 *
 * WHY the naive gates this REPLACES all pass on the merge-sort divide defect:
 *   • a STATE-delta-only gate passes — runs DID change (1→2→4→6 groups).
 *   • a WHOLE-FRAME pixel diff passes — the callout chip text + step counter change = many changed
 *     pixels (the "liveness alibi").
 *   • an author/phase "hold" whitelist exempts divide — but divide here is NOT a hold, the state moves.
 *   • "identical-pixels ⇒ hold" is circular — it auto-exempts the very frames that are broken.
 *   This gate is the CONJUNCTION: state-changed AND figure-frozen ⇒ FAIL, with the figure region
 *   measured WITHOUT the callout band (so the alibi can't mask the freeze).
 *
 * STATE half  — tools/frame-conjunction-seed.mjs::stateDelta over the typed steps (array/runs/frontOf/
 *               outFilled/sortedSpan/phase). The source of truth, not pixels.
 * FIGURE half — the REAL renderer (SortMergeFamily via the DEMO routes /tutor/lectie-mergesort +
 *               /tutor/merge-compare, where the defect lives), rasterized at 1536×730 real DPR, each
 *               frame screenshotted clipped to the SVG, then CROPPED to exclude the callout chip (top
 *               band, measured live from the chip's own painted rect) + everything outside the SVG
 *               (PAS counter / pips / masthead / footer are already outside the SVG, so the SVG clip
 *               handles them). Diff = a perceptual per-pixel RGB compare with TWO calibrated knobs:
 *               PIXEL_EPS (a per-channel floor that ignores AA / subpixel jitter) and MEANINGFUL_PX
 *               (how many above-floor pixels count as "the figure moved"). Below MEANINGFUL_PX between
 *               two STATE-CHANGED frames ⇒ the figure is FROZEN ⇒ FAIL.
 *
 * RUNS PER SKIN: boxes (lectie route, default variant) AND bars (merge-compare right column). Both
 * share computePlacement(), so both inherit the divide defect.
 *
 * Usage:  node tutor-web/tools/frame-conjunction-gate.mjs
 * Prereq: vite dev server on http://localhost:5173 (the harness checks + fails loud if down).
 * Exit:   0 = gate GREEN (no frozen-over-live-state pair); 1 = gate RED (≥1 such pair) or harness error.
 *
 * Deps: playwright-core (installed), node:zlib (PNG inflate). NO pngjs / pixelmatch / sharp (absent in
 * this repo) — a compact hand-rolled PNG decoder + perceptual diff lives at the bottom of this file.
 */

import { chromium } from "playwright-core";
import zlib from "node:zlib";
import { MERGE_STEPS, stateDelta } from "./frame-conjunction-seed.mjs";

const BASE_URL = "http://localhost:5173";
const VIEWPORT = { width: 1536, height: 730 };
const DPR = 1; // Alex's real DPR per reference_alex_screen_resolution (1536×864 @ 1x).

// ── Two calibrated thresholds (the floor + the meaningful-change count) ──────────────────────────────
// PIXEL_EPS: a pixel counts as "changed" only if max per-channel |Δ| exceeds this. Kills AA / subpixel
// jitter / 1-LSB encoder noise. MEANINGFUL_PX: how many changed pixels constitute "the figure moved".
// The divide defect leaves the figure region BYTE-frozen (0 changed pixels) — these are set well above
// 0 but well below any genuine token reflow, so the gate is robust to render noise yet bites the freeze.
const PIXEL_EPS = 24; // per-channel 0..255 floor
const MEANINGFUL_PX = 80; // changed-pixel count that means "the figure genuinely moved"

const SETTLE_MS = 650; // reducedMotion=reduce makes the tween instant, but let React commit + paint.

// ── The two skins under test (both drive the same computePlacement) ──────────────────────────────────
const SKINS = [
  {
    name: "boxes @ /tutor/lectie-mergesort",
    route: "/tutor/lectie-mergesort",
    // single SortMergeFamily, behind a PREDICT gate + a "continuă" advance to the figure beat.
    needsPredictGate: true,
    scrubberIndex: 0,
  },
  {
    name: "bars @ /tutor/merge-compare",
    route: "/tutor/merge-compare",
    // two SortMergeFamily columns (boxes, bars); the BARS skin is the 2nd scrubber. No predict gate.
    needsPredictGate: false,
    scrubberIndex: 1,
  },
];

// Which consecutive pairs to probe. We cover the whole trace so the gate is general, but the DIVIDE
// pairs (0→1, 1→2, 2→3) are the seed defect this run must catch RED.
const PAIRS = [];
for (let n = 0; n < MERGE_STEPS.length - 1; n++) PAIRS.push([n, n + 1]);

async function run() {
  // Fail loud if the dev server is down (the gate must never silently pass on a dead server).
  const probe = await fetch(BASE_URL + "/tutor/merge-compare").catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[harness] dev server not reachable at ${BASE_URL} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[harness] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }

  const browser = await chromium.launch({ headless: true });
  const failures = []; // { skin, pair:[a,b], stateFields, changedPx } — the frozen-over-live-state hard fails
  const holds = []; // { skin, pair } — exempt (state unchanged)
  const lives = []; // { skin, pair, changedPx } — state changed AND figure moved (correct)

  try {
    for (const skin of SKINS) {
      console.log(`\n========== SKIN: ${skin.name} ==========`);
      const ctx = await browser.newContext({
        viewport: VIEWPORT,
        deviceScaleFactor: DPR,
        reducedMotion: "reduce", // deterministic: render jumps to target, no 460ms tween
      });
      const page = await ctx.newPage();
      const errs = [];
      page.on("pageerror", (e) => errs.push("PAGE: " + e.message.slice(0, 200)));
      page.on("console", (m) => { if (m.type() === "error") errs.push("CON: " + m.text().slice(0, 200)); });

      await page.goto(BASE_URL + skin.route, { waitUntil: "networkidle", timeout: 30_000 });
      await page.waitForTimeout(500);

      if (skin.needsPredictGate) {
        // unlock the gated beat, then advance to the figure beat (PRIVEȘTE).
        await page.locator('[data-testid^="predict-"]').first().click();
        await page.waitForTimeout(300);
        await page.locator('[data-testid="lectie-next"]').click();
        await page.waitForTimeout(400);
      }

      const scrubbers = page.locator('[data-testid="sort-merge-scrubber"]');
      await scrubbers.first().waitFor({ state: "visible", timeout: 15_000 });
      const scrubber = scrubbers.nth(skin.scrubberIndex);
      // The SVG that owns THIS scrubber (its sibling figure region).
      const svg = page.locator('[data-testid="sort-merge-root"]').nth(skin.scrubberIndex).locator("svg.algo-stepper-shell-svg");
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
      // The callout chip is the ONE element inside the SVG that changes on the alibi-only divide frames;
      // we measure its painted bottom edge live and crop everything above it away.
      const captureFigure = async (idx) => {
        await goToFrame(idx);
        const box = await svg.boundingBox();
        if (!box) throw new Error(`no bounding box for SVG on frame ${idx}`);

        // Find the callout chip's painted bottom (in CSS px, page coords). The chip is the bordered
        // <rect> the renderer draws last in the top band (stroke = the yellow accent #fde047). We locate
        // the topmost <rect> whose top is within the SVG's top ~25% and take its bottom edge. Fallback:
        // a fixed 22% top-band crop derived from the viewBox callout band (y∈[6,~58] of ~288/432).
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

      // Walk every consecutive pair; conjoin state-delta with figure pixel-delta.
      let prev = null;
      let prevIdx = -1;
      for (const [a, bIdx] of PAIRS) {
        if (prevIdx !== a) {
          prev = await captureFigure(a);
          prevIdx = a;
        }
        const cur = await captureFigure(bIdx);

        const sd = stateDelta(MERGE_STEPS[a], MERGE_STEPS[bIdx]);
        // perceptual diff on the cropped figure region
        const { changedPx, note } = diffPng(prev.png, cur.png);

        if (!sd.changed) {
          holds.push({ skin: skin.name, pair: [a, bIdx] });
          // (exempt — state held; figure freeze is allowed here.)
        } else if (changedPx < MEANINGFUL_PX) {
          failures.push({ skin: skin.name, pair: [a, bIdx], stateFields: sd.fields, changedPx, note });
          console.log(
            `  FAIL  frame ${a}→${bIdx}: STATE CHANGED [${sd.fields.join(",")}] but FIGURE FROZEN ` +
              `(changed pixels=${changedPx} < MEANINGFUL_PX=${MEANINGFUL_PX}; ${note})`,
          );
        } else {
          lives.push({ skin: skin.name, pair: [a, bIdx], changedPx });
          // (correct — state changed AND figure moved.)
        }

        prev = cur;
        prevIdx = bIdx;
      }

      if (errs.length) console.warn(`  [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);
      await ctx.close();
    }
  } finally {
    await browser.close();
  }

  // ── Verdict ──────────────────────────────────────────────────────────────────────────────────────
  console.log(`\n================ CONJUNCTION GATE VERDICT ================`);
  console.log(`  exempt holds (state unchanged)        : ${holds.length}`);
  console.log(`  live pairs (state+figure both moved)  : ${lives.length}`);
  console.log(`  HARD FAILS (state moved, figure frozen): ${failures.length}`);
  if (failures.length) {
    console.log(`\n  ---- HARD FAILS (frozen figure over live state — NEVER a hold) ----`);
    for (const f of failures) {
      console.log(
        `  [${f.skin}] frame ${f.pair[0]}→${f.pair[1]}: state changed {${f.stateFields.join(", ")}}, ` +
          `figure region changedPx=${f.changedPx} (< ${MEANINGFUL_PX}) — FROZEN`,
      );
    }
    console.error(`\nGATE RED — ${failures.length} frozen-figure-over-live-state pair(s). Exit 1.`);
    process.exit(1);
  }
  console.log(`\nGATE GREEN — every state-changed pair also moved the figure region. Exit 0.`);
  process.exit(0);
}

// ── Perceptual diff over two equal-size PNG buffers ──────────────────────────────────────────────────
// Returns { changedPx, note }. A pixel is "changed" iff max per-channel |Δ| > PIXEL_EPS. If the two
// crops differ in size (a layout reflow between frames), THAT is itself a figure change — report it as
// a large changedPx so a genuine reflow can never be mistaken for a freeze.
function diffPng(aBuf, bBuf) {
  const a = decodePng(aBuf);
  const b = decodePng(bBuf);
  if (a.width !== b.width || a.height !== b.height) {
    return { changedPx: a.width * a.height + 1, note: `crop size differs ${a.width}x${a.height} vs ${b.width}x${b.height} (reflow)` };
  }
  const { data: da } = a;
  const { data: db } = b;
  let changed = 0;
  let maxDelta = 0;
  for (let i = 0; i < da.length; i += 4) {
    const dr = Math.abs(da[i] - db[i]);
    const dg = Math.abs(da[i + 1] - db[i + 1]);
    const dbl = Math.abs(da[i + 2] - db[i + 2]);
    const d = Math.max(dr, dg, dbl);
    if (d > maxDelta) maxDelta = d;
    if (d > PIXEL_EPS) changed++;
  }
  return { changedPx: changed, note: `maxΔ=${maxDelta}, region=${a.width}x${a.height}` };
}

// ── Minimal PNG decoder (8-bit, color type 2 RGB or 6 RGBA; the format Playwright screenshots emit) ──
// Parses chunks, concatenates IDAT, inflates (zlib), then un-filters scanlines into a flat RGBA buffer.
function decodePng(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47 || buf.readUInt32BE(4) !== 0x0d0a1a0a)
    throw new Error("not a PNG");
  let off = 8;
  let width = 0;
  let height = 0;
  let bitDepth = 0;
  let colorType = 0;
  const idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString("ascii", off + 4, off + 8);
    const dataStart = off + 8;
    if (type === "IHDR") {
      width = buf.readUInt32BE(dataStart);
      height = buf.readUInt32BE(dataStart + 4);
      bitDepth = buf[dataStart + 8];
      colorType = buf[dataStart + 9];
    } else if (type === "IDAT") {
      idat.push(buf.subarray(dataStart, dataStart + len));
    } else if (type === "IEND") {
      break;
    }
    off = dataStart + len + 4; // skip data + CRC
  }
  if (bitDepth !== 8 || (colorType !== 6 && colorType !== 2))
    throw new Error(`unsupported PNG (bitDepth=${bitDepth}, colorType=${colorType})`);
  const channels = colorType === 6 ? 4 : 3;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const out = Buffer.alloc(width * height * 4);
  const cur = Buffer.alloc(stride);
  const prev = Buffer.alloc(stride);
  let rp = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[rp++];
    for (let x = 0; x < stride; x++) cur[x] = raw[rp++];
    unfilter(filter, cur, prev, channels, stride);
    // write RGBA row
    const orow = y * width * 4;
    for (let x = 0; x < width; x++) {
      const ci = x * channels;
      const oi = orow + x * 4;
      out[oi] = cur[ci];
      out[oi + 1] = cur[ci + 1];
      out[oi + 2] = cur[ci + 2];
      out[oi + 3] = channels === 4 ? cur[ci + 3] : 255;
    }
    cur.copy(prev);
  }
  return { width, height, data: out };
}

// PNG scanline un-filter (filter types 0..4), in place on `cur`, using `prev` (the previous, already
// un-filtered scanline) and `bpp` (bytes per pixel).
function unfilter(filter, cur, prev, channels, stride) {
  const bpp = channels;
  for (let x = 0; x < stride; x++) {
    const a = x >= bpp ? cur[x - bpp] : 0; // left
    const b = prev[x]; // up
    const c = x >= bpp ? prev[x - bpp] : 0; // upper-left
    let val = cur[x];
    switch (filter) {
      case 0: break; // None
      case 1: val = (val + a) & 0xff; break; // Sub
      case 2: val = (val + b) & 0xff; break; // Up
      case 3: val = (val + ((a + b) >> 1)) & 0xff; break; // Average
      case 4: { // Paeth
        const p = a + b - c;
        const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        const pr = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
        val = (val + pr) & 0xff;
        break;
      }
      default: throw new Error(`bad PNG filter ${filter}`);
    }
    cur[x] = val;
  }
}

run().catch((e) => {
  console.error("[harness] FATAL:", e);
  process.exit(1);
});
