/**
 * crop-profiles.mjs — per-family crop spec + a HARD answer-leak assert (spec §3.4, R11).
 *
 * WHY this exists:
 *   The bare cropped figure handed to the vision judge must NOT carry an answer-bearing token
 *   (the printed probability, the rowOp operation text, a phase/PASS-FAIL word). If it does, a
 *   "label-reader" judge passes BAD frames by reading the leaked answer instead of the pixels.
 *   The seed gate (frame-conjunction-gate.mjs::captureFigure) crops only the TOP callout band —
 *   verified sufficient for sort-merge/seq-array, but NOT for:
 *     • chart-dist: the P-value annotation box (`<g data-cd="annot">`) is clamped INSIDE the plot
 *       box [PLOT_Y1, PLOT_Y0] (verified ChartDistributionFamily.tsx:485-490, :492-497) — it sits
 *       MID-PLOT, below the top callout band, so the standard crop does NOT remove it. It must be
 *       MASKED (painted over) before capture.
 *     • matrix-grid: the `rowOp` bottom rail carries the operation text ("E2 ← E2 + (-3)·E1",
 *       "dp[2]=dp[1]+dp[0]") — an answer the judge could read instead of the cells. Masked.
 *       The rail is an UN-ATTRIBUTED <text> at y=VIEWBOX_H-5 (verified MatrixGridFamily.tsx:509-520 —
 *       there is NO `data-mg-rowop` attribute, contrary to an earlier assumption). It is the only
 *       text painted in the bottom ROWOP_BAND_H=18px band of the 360-tall viewBox (grid content is
 *       centered in [GRID_TOP, VIEWBOX_H - ROWOP_BAND_H]). So we mask GEOMETRICALLY: any SVG <text>
 *       whose vertical center sits in the bottom `maskBottomBandFrac` of the SVG bbox.
 *
 * DESIGN: each profile is a declarative spec consumed by frame-capturer.mjs. Masking is done
 *   IN-DOM (paint an opaque rect over the leaking element by its bbox) BEFORE the screenshot, so
 *   the leaked region is genuinely gone from pixels — not merely clipped away (clipping the top
 *   band cannot remove a mid-plot box). The top-band callout crop is still applied via the same
 *   live chip-rect measurement the seed gate uses.
 *
 * The answer-leak ASSERT (assertNoAnswerLeak) re-derives, from the LIVE DOM, the bounding boxes of
 * every answer-bearing element this profile claims to have removed, intersects them with the final
 * crop rectangle, and FAILS the capture if any such element still intersects the saved region.
 * DOM-text-bbox intersection (not OCR) — deterministic, no extra dep, and exact for SVG text.
 */

/**
 * @typedef {Object} CropProfile
 * @property {string} family
 * @property {boolean} cropTopCallout      — apply the seed top-band callout crop (chip-rect measure + margin).
 * @property {string[]} maskSelectors      — CSS selectors (relative to the SVG) of answer-bearing elements
 *                                            to paint over before capture (e.g. the chart P-box `<g data-cd="annot">`).
 * @property {number}  [maskBottomBandFrac] — if set (>0), additionally mask every SVG <text> whose
 *                                            vertical CENTER lies in the bottom this-fraction of the SVG bbox.
 *                                            Used for matrix-grid's un-attributed rowOp rail (bottom 18/360 ≈ 0.06,
 *                                            captured generously at 0.10 to cover descenders + font growth).
 * @property {string[]} leakSelectors      — selectors whose bbox must NOT survive into the final crop
 *                                            (the answer-leak assert checks these). For a band mask the assert
 *                                            relies on the painted mask rects directly (see assertNoAnswerLeak).
 */

// Per-family crop + mask spec. Selectors are matched WITHIN the figure SVG (svg.algo-stepper-shell-svg).
export const CROP_PROFILES = {
  // sort-merge / seq-array: top callout band only (the seed heuristic is sufficient — verified).
  "seq-array": {
    family: "seq-array",
    cropTopCallout: true,
    maskSelectors: [],
    leakSelectors: [],
  },
  "sort-merge": {
    family: "sort-merge",
    cropTopCallout: true,
    maskSelectors: [],
    leakSelectors: [],
  },
  // chart-dist: top callout crop AND mask the mid-plot P-value annotation box.
  // The annotation group is `<g data-cd="annot">` (verified ChartDistributionFamily.tsx:492);
  // it holds the rect + the `P(a ≤ X ≤ b) = 0.NN` text (the answer). Mask it.
  "chart-dist": {
    family: "chart-dist",
    cropTopCallout: true,
    maskSelectors: ['[data-cd="annot"]'],
    leakSelectors: ['[data-cd="annot"]'],
  },
  // matrix-grid (dp-fill + gauss): top callout crop AND mask the rowOp bottom rail (operation text).
  // The rail is an un-attributed <text> at y=VIEWBOX_H-5 (the ONLY text in the bottom band); masked
  // geometrically by bottom-band fraction. A frame with no rowOp paints no text there → nothing masked,
  // which is correct (GOOD-clean dp-fill step 0/1 has no rowOp).
  "matrix-grid": {
    family: "matrix-grid",
    cropTopCallout: true,
    maskSelectors: [],
    maskBottomBandFrac: 0.1, // bottom 10% of the SVG bbox (rail is at ~18/360 ≈ 0.05; 0.10 covers font growth)
    leakSelectors: [],
  },
  // graph-tree: top callout crop only (no mid-figure answer text).
  "graph-tree": {
    family: "graph-tree",
    cropTopCallout: true,
    maskSelectors: [],
    leakSelectors: [],
  },
};

/**
 * Resolve a cropProfile key → the profile object. Accepts either a family name
 * ("chart-dist") or an explicit profile key. Throws loud on an unknown key (never
 * silently fall through to "no masking", which would leak an answer).
 * @param {string} key
 * @returns {CropProfile}
 */
export function getCropProfile(key) {
  const p = CROP_PROFILES[key];
  if (!p) {
    throw new Error(
      `[crop-profiles] unknown cropProfile '${key}'. Known: ${Object.keys(CROP_PROFILES).join(", ")}. ` +
        `A missing profile would risk leaking an answer into the bare frame — fail loud instead.`,
    );
  }
  return p;
}

/**
 * Paint an opaque rect over each maskSelector element inside the SVG, IN-DOM, before capture.
 * Runs in the page context (passed the profile's maskSelectors). Returns the count masked so the
 * caller can log/verify. Idempotent within a single frame (re-running re-paints).
 *
 * The mask rect is appended as the LAST child of the SVG (top of paint order) at the element's
 * own bbox (in the SVG's local user-space, via getBBox) so it covers exactly the leaking glyphs.
 *
 * @param {import('playwright-core').Locator} svgLocator
 * @param {CropProfile} profile
 * @param {string} maskColor  fill for the mask rect (any opaque CSS color; default a neutral grey
 *                            so the mask reads as "no information here", not a styled element).
 * @returns {Promise<number>} number of elements masked
 */
export async function applyDomMasks(svgLocator, profile, maskColor = "#7d7d7d") {
  const bandFrac = profile.maskBottomBandFrac || 0;
  if (!profile.maskSelectors.length && !bandFrac) return 0;
  return await svgLocator.evaluate(
    (svgEl, { selectors, color, bandFrac }) => {
      const SVG_NS = "http://www.w3.org/2000/svg";
      let masked = 0;
      const paintMask = (bb) => {
        if (!bb || bb.width <= 0 || bb.height <= 0) return;
        const rect = document.createElementNS(SVG_NS, "rect");
        // Pad a hair so antialiased glyph edges are fully covered.
        rect.setAttribute("x", String(bb.x - 2));
        rect.setAttribute("y", String(bb.y - 2));
        rect.setAttribute("width", String(bb.width + 4));
        rect.setAttribute("height", String(bb.height + 4));
        rect.setAttribute("fill", color);
        rect.setAttribute("data-vision-bench-mask", "1");
        svgEl.appendChild(rect);
        masked++;
      };
      // (a) explicit selector masks (getBBox = SVG local user-space; same as the appended rect)
      for (const sel of selectors) {
        for (const el of svgEl.querySelectorAll(sel)) {
          let bb;
          try {
            bb = el.getBBox();
          } catch {
            continue;
          }
          paintMask(bb);
        }
      }
      // (b) geometric bottom-band mask: any <text> whose vertical center sits in the bottom
      // `bandFrac` of the SVG's own bbox. Used for matrix-grid's un-attributed rowOp rail.
      if (bandFrac > 0) {
        let svgBox;
        try {
          svgBox = svgEl.getBBox();
        } catch {
          svgBox = null;
        }
        if (svgBox) {
          const bandTop = svgBox.y + svgBox.height * (1 - bandFrac);
          for (const t of svgEl.querySelectorAll("text")) {
            let bb;
            try {
              bb = t.getBBox();
            } catch {
              continue;
            }
            if (!bb || bb.width <= 0 || bb.height <= 0) continue;
            const cy = bb.y + bb.height / 2;
            if (cy >= bandTop) paintMask(bb);
          }
        }
      }
      return masked;
    },
    { selectors: profile.maskSelectors, color: maskColor, bandFrac },
  );
}

/**
 * HARD answer-leak assert (§3.4). After the crop rectangle is decided AND any DOM masks applied,
 * verify that NO leakSelector element's painted region survives into the saved crop. We do this by
 * re-reading each leakSelector's page-coordinate bounding client rect from the live DOM and checking:
 *   (a) the element is masked (a sibling [data-vision-bench-mask] rect covers it), OR
 *   (b) the element's rect lies ENTIRELY outside the final crop rectangle (cropped away).
 * If a leak element is neither masked nor cropped away → it would survive in pixels → FAIL the capture.
 *
 * This is DOM-text-bbox intersection (deterministic, exact for SVG text), not OCR.
 *
 * @param {import('playwright-core').Locator} svgLocator
 * @param {CropProfile} profile
 * @param {{x:number,y:number,width:number,height:number}} cropPageRect  final crop in PAGE css px.
 * @returns {Promise<{ok:boolean, leaks:Array<{selector:string, reason:string, rect:object}>}>}
 */
export async function assertNoAnswerLeak(svgLocator, profile, cropPageRect) {
  const bandFrac = profile.maskBottomBandFrac || 0;
  if (!profile.leakSelectors.length && !bandFrac) return { ok: true, leaks: [] };
  const leaks = await svgLocator.evaluate(
    (svgEl, { selectors, crop, bandFrac }) => {
      const out = [];
      // Page-coord rects of all the mask rects we appended (so we can confirm an element is covered).
      const masks = Array.from(svgEl.querySelectorAll('[data-vision-bench-mask="1"]')).map((m) =>
        m.getBoundingClientRect(),
      );
      const cropRight = crop.x + crop.width;
      const cropBottom = crop.y + crop.height;
      const intersectsCrop = (r) =>
        !(r.right <= crop.x || r.left >= cropRight || r.bottom <= crop.y || r.top >= cropBottom);
      const coveredByMask = (r) =>
        masks.some(
          (m) =>
            // mask fully contains the element rect (with 1px slack)
            m.left - 1 <= r.left && m.top - 1 <= r.top && m.right + 1 >= r.right && m.bottom + 1 >= r.bottom,
        );
      const checkEl = (el, sel) => {
        const r = el.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) return; // not painted
        const rr = { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height };
        if (!intersectsCrop(rr)) return; // cropped away → safe
        if (coveredByMask(rr)) return; // masked over → safe
        out.push({ selector: sel, reason: "leak element intersects final crop and is NOT masked", rect: rr });
      };
      for (const sel of selectors) {
        for (const el of svgEl.querySelectorAll(sel)) checkEl(el, sel);
      }
      // Bottom-band coverage check: every <text> in the bottom band must be masked-or-cropped (so the
      // un-attributed rowOp rail can't survive). We re-derive the band from the live SVG client rect.
      if (bandFrac > 0) {
        const sb = svgEl.getBoundingClientRect();
        const bandTop = sb.top + sb.height * (1 - bandFrac);
        for (const t of svgEl.querySelectorAll("text")) {
          const r = t.getBoundingClientRect();
          if (r.width <= 0 || r.height <= 0) continue;
          const cyPage = r.top + r.height / 2;
          if (cyPage < bandTop) continue; // not in the band
          checkEl(t, `<bottom-band text:${bandFrac}>`);
        }
      }
      return out;
    },
    { selectors: profile.leakSelectors, crop: cropPageRect, bandFrac },
  );
  return { ok: leaks.length === 0, leaks };
}
