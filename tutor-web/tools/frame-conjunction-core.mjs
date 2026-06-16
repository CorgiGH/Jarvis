/**
 * frame-conjunction-core.mjs — the PURE, side-effect-free core of the STATE↔FIGURE CONJUNCTION gate.
 *
 * Extracted verbatim from frame-conjunction-gate.mjs so the same detector logic can be exercised by
 * unit tests without launching a browser. The prior council called this "the only working detector" —
 * the bodies below are byte-identical to the gate's originals; only their home moved.
 *
 *   • PIXEL_EPS / MEANINGFUL_PX — the two calibrated thresholds (per-channel floor + meaningful-change
 *     count). See the gate file header for the calibration rationale.
 *   • decodePng / unfilter      — the minimal PNG decoder (8-bit, color type 2 RGB or 6 RGBA).
 *   • diffPng                   — the perceptual per-pixel RGB compare returning { changedPx, note }.
 *   • classifyPair              — the conjunction verdict: !stateChanged ⇒ 'hold';
 *                                 changedPx < MEANINGFUL_PX ⇒ 'fail'; else 'live'.
 *
 * Deps: node:zlib only (PNG inflate). NO pngjs / pixelmatch / sharp (absent in this repo).
 */

import zlib from "node:zlib";

// ── Two calibrated thresholds (the floor + the meaningful-change count) ──────────────────────────────
// PIXEL_EPS: a pixel counts as "changed" only if max per-channel |Δ| exceeds this. Kills AA / subpixel
// jitter / 1-LSB encoder noise. MEANINGFUL_PX: how many changed pixels constitute "the figure moved".
// The divide defect leaves the figure region BYTE-frozen (0 changed pixels) — these are set well above
// 0 but well below any genuine token reflow, so the gate is robust to render noise yet bites the freeze.
export const PIXEL_EPS = 24; // per-channel 0..255 floor
export const MEANINGFUL_PX = 80; // changed-pixel count that means "the figure genuinely moved"

// ── The conjunction verdict for a single consecutive frame pair ──────────────────────────────────────
// state-changed AND figure-frozen (changedPx < MEANINGFUL_PX) ⇒ 'fail' — a frozen figure over a live
// state, which can NEVER be reclassified as a hold. state unchanged ⇒ 'hold' (figure may legitimately be
// identical). state changed AND figure moved ⇒ 'live' (correct).
export function classifyPair({ stateChanged, changedPx }) {
  if (!stateChanged) return "hold";
  if (changedPx < MEANINGFUL_PX) return "fail";
  return "live";
}

// ── Perceptual diff over two equal-size PNG buffers ──────────────────────────────────────────────────
// Returns { changedPx, note }. A pixel is "changed" iff max per-channel |Δ| > PIXEL_EPS. If the two
// crops differ in size (a layout reflow between frames), THAT is itself a figure change — report it as
// a large changedPx so a genuine reflow can never be mistaken for a freeze.
export function diffPng(aBuf, bBuf) {
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
export function decodePng(buf) {
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
export function unfilter(filter, cur, prev, channels, stride) {
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
