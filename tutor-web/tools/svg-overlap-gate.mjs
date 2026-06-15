/**
 * svg-overlap-gate.mjs — the general SVG ELEMENT-OVERLAP gate.
 *
 * THE INVARIANT (the bug class this gate exists to catch):
 *   No two PAINTED figure elements that belong to DIFFERENT logical token groups (or are figure CHROME:
 *   callout chip, band label, ✓ rule, captions, brackets, chevrons, baselines) may overlap on screen
 *   beyond an anti-alias sliver. A real occlusion — chip-over-label, bar-on-bar, cell-on-cell, a divide
 *   bracket crossing a non-owner cell, a chevron sitting on the wrong bar, two run-groups whose gap
 *   collapsed, a label over a token, text-on-text — is a HARD FAIL.
 *
 * WHY the standing no-clip gate (e2e/helpers/assertNoClip.ts) catches NONE of this:
 *   • its text-clip pass SKIPS the SVG namespace outright (`if (el.namespaceURI…includes("svg")) continue`).
 *   • its overlap pass only compares INTERACTIVE elements (`button, a, input, select, [role]`) — every
 *     figure element is a non-interactive <rect>/<text>/<path>/<line>/<polygon>, so zero are considered.
 *   This gate walks the figure SVG root and does pairwise bbox-intersection over EVERY painted element,
 *   with an EXPLICIT allow-list for the intended adjacencies (a value on its own bar, a glow on its own
 *   bar, a thin structural baseline touching bars, a chevron tick directly above its owner column).
 *
 * THE KNOWN SEED DEFECT this run must catch RED: the bars skin, on every 2-line `take` callout, the
 * callout chip (a wide bordered <rect> at the top + its accent <text>) grows tall enough to occlude the
 * run-band label "DOUĂ LISTE SORTATE → SE CONTOPESC". Frames (0-based) 5,8,10,13,16,20,22,24,26,28.
 *
 * GROUPING — how "same logical token group" is decided:
 *   Each token is a <g data-cell-index="pos"> (one per stable id). The renderer emits the cell/bar rect,
 *   the value text, and (on the just-taken token) a glow halo — ALL as children of that one <g>. So two
 *   elements share a logical group iff they share the SAME nearest ancestor carrying [data-cell-index].
 *   A pair INSIDE one token group is an intended adjacency (value-on-its-own-cell, glow-on-its-own-bar)
 *   and is allow-listed (skipped before the area test). CHROME elements (callout chip, band label, ✓ rule
 *   + caption, divide brackets, front-pointer chevrons, band baselines, index strip, POZIȚII caption)
 *   carry NO [data-cell-index]; each is its own group. Overlaps BETWEEN groups (token↔token, chrome↔token,
 *   chrome↔chrome) are the candidates; the allow-list then carves out the handful of INTENDED cross-group
 *   adjacencies (see allowReason()).
 *
 * Usage:  node tutor-web/tools/svg-overlap-gate.mjs
 * Prereq: vite dev server on http://localhost:5173 (the harness checks + fails loud if down).
 * Exit:   0 = GREEN (no un-allow-listed overlap on any frame/skin/viewport); 1 = RED (≥1) or harness err.
 *
 * Deps: playwright-core (installed). No image deps — overlap is computed from getBoundingClientRect in
 * page.evaluate, not from pixels.
 */

import { chromium } from "playwright-core";
import { MERGE_STEPS } from "./frame-conjunction-seed.mjs";

const BASE_URL = "http://localhost:5173";
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), per reference_alex_screen_resolution.

// Both viewport heights the cardinal-sin gate mandates (≥2 heights). Width is Alex's 1536.
const VIEWPORTS = [
  { width: 1536, height: 648 }, // content area WITH the bookmarks bar
  { width: 1536, height: 730 }, // content area with NO bookmarks bar
];

// ── Overlap thresholds (CALIBRATION) ──────────────────────────────────────────────────────────────────
// AREA_FLOOR: an intersection below this many CSS-px² is an anti-alias sliver / sub-pixel touch — ignored
// (a real occlusion is tens-to-hundreds of px²). MIN_OVERLAP_PX: the SHORTER overlap dimension must exceed
// this — kills 1-px edge grazes where two adjacent cells/bars share a border line. THIN_RULE_PX: a
// <line>/<rect> whose own rendered thickness is ≤ this is a structural rule/baseline allowed to touch bars.
const AREA_FLOOR = 12; // CSS px² — below this is AA noise, never a real collision
const MIN_OVERLAP_PX = 2.0; // CSS px on the SHORTER overlap dimension (edge-graze guard)
const THIN_RULE_PX = 3.5; // a rule/baseline this thin or thinner is structural furniture
// GLOW_SLIVER_PX: the just-taken token's glow halo is a DECORATIVE ring drawn 4px LARGER than its bar
// (fill=none, semi-transparent stroke). Its 4px outer ring can graze a neighbouring band's edge by a few
// px — that is a decorative-halo sliver, NOT a content occlusion. A glow overlapping a DIFFERENT group is
// allow-listed ONLY when the shorter overlap dimension is ≤ this (a halo edge graze); a DEEPER overlap
// (the bar BODY genuinely sitting under another bar) is a real collision and stays flagged.
const GLOW_SLIVER_PX = 4.0;

const SETTLE_MS = 600; // reducedMotion=reduce makes the tween instant, but let React commit + paint.

// ── The two skins under test (both drive the same computePlacement) ──────────────────────────────────
// boxes lives on /tutor/lectie-mergesort (single figure, behind a predict gate). bars lives on
// /tutor/merge-compare which renders BOTH skins side by side — the bars skin is the 2nd sort-merge-root.
const SKINS = [
  {
    name: "boxes @ /tutor/lectie-mergesort",
    short: "boxes",
    route: "/tutor/lectie-mergesort",
    needsPredictGate: true,
    rootIndex: 0,
  },
  {
    name: "bars @ /tutor/merge-compare (bars column)",
    short: "bars",
    route: "/tutor/merge-compare",
    needsPredictGate: false,
    rootIndex: 1,
  },
];

const FRAME_COUNT = MERGE_STEPS.length; // 30

async function run() {
  // Fail loud if the dev server is down (the gate must never silently pass on a dead server).
  const probe = await fetch(BASE_URL + "/tutor/merge-compare").catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[harness] dev server not reachable at ${BASE_URL} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[harness] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }
  console.log(
    `[harness] dev server up. Frames/skin=${FRAME_COUNT}. Skins=${SKINS.length}. ` +
      `Viewports=${VIEWPORTS.map((v) => `${v.width}x${v.height}`).join(",")}.`,
  );
  console.log(`[harness] thresholds: AREA_FLOOR=${AREA_FLOOR}px², MIN_OVERLAP_PX=${MIN_OVERLAP_PX}, THIN_RULE_PX=${THIN_RULE_PX}\n`);

  const browser = await chromium.launch({ headless: true });
  const overlaps = []; // every flagged overlap (real + allowed) across all skins/frames/viewports

  try {
    for (const vp of VIEWPORTS) {
      for (const skin of SKINS) {
        console.log(`========== VIEWPORT ${vp.width}x${vp.height} · SKIN: ${skin.name} ==========`);
        const ctx = await browser.newContext({
          viewport: { width: vp.width, height: vp.height },
          deviceScaleFactor: DPR,
          reducedMotion: "reduce", // deterministic: render jumps to target, no 460ms tween
        });
        const page = await ctx.newPage();
        const errs = [];
        page.on("pageerror", (e) => errs.push("PAGE: " + e.message.slice(0, 200)));
        page.on("console", (m) => { if (m.type() === "error") errs.push("CON: " + m.text().slice(0, 200)); });

        await page.goto(BASE_URL + skin.route, { waitUntil: "networkidle", timeout: 30_000 });
        await page.waitForTimeout(400);

        if (skin.needsPredictGate) {
          await page.locator('[data-testid^="predict-"]').first().click();
          await page.waitForTimeout(250);
          await page.locator('[data-testid="lectie-next"]').click();
          await page.waitForTimeout(350);
        }

        const scrubbers = page.locator('[data-testid="sort-merge-scrubber"]');
        await scrubbers.first().waitFor({ state: "visible", timeout: 15_000 });
        const scrubber = scrubbers.nth(skin.rootIndex);
        const svg = page
          .locator('[data-testid="sort-merge-root"]')
          .nth(skin.rootIndex)
          .locator("svg.algo-stepper-shell-svg");
        await svg.first().waitFor({ state: "visible", timeout: 15_000 });

        const goToFrame = async (idx) => {
          await scrubber.evaluate((el, v) => {
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
            setter.call(el, String(v));
            el.dispatchEvent(new Event("input", { bubbles: true }));
            el.dispatchEvent(new Event("change", { bubbles: true }));
          }, idx);
          await page.waitForTimeout(SETTLE_MS);
        };

        for (let frame = 0; frame < FRAME_COUNT; frame++) {
          await goToFrame(frame);

          // ── Collect every painted element, classify it, do pairwise bbox-intersection, and run the
          // allow-list — ALL in the page (it needs live DOM ancestry access). Returns the flagged pairs
          // (each tagged allowed:true with a reason, or allowed:false = a real overlap). ──
          const result = await svg.evaluate(
            (svgEl, cfg) => {
              const { AREA_FLOOR, MIN_OVERLAP_PX, THIN_RULE_PX, GLOW_SLIVER_PX } = cfg;

              // nearest ancestor (incl self) carrying [data-cell-index] → a token group key.
              const groupKeyOf = (el) => {
                let n = el;
                while (n && n !== svgEl) {
                  if (n.nodeType === 1 && n.getAttribute && n.getAttribute("data-cell-index") !== null)
                    return "cell#" + n.getAttribute("data-cell-index");
                  n = n.parentNode;
                }
                return null; // chrome
              };
              // nearest ancestor (incl self) carrying [data-pointer] → a front-pointer group key.
              const pointerGOf = (el) => {
                let n = el;
                while (n && n !== svgEl) {
                  if (n.nodeType === 1 && n.getAttribute && n.getAttribute("data-pointer") !== null) return n;
                  n = n.parentNode;
                }
                return null;
              };

              const textOf = (el) => (el.textContent || "").replace(/\s+/g, " ").trim();

              const classify = (el) => {
                const tag = el.tagName.toLowerCase();
                const cellKey = groupKeyOf(el);
                const ptrG = pointerGOf(el);
                const txt = textOf(el);
                const fill = (el.getAttribute("fill") || "").toLowerCase();
                const stroke = (el.getAttribute("stroke") || "").toLowerCase();
                let role = "unknown";
                if (cellKey) {
                  if (tag === "text") role = "tokenValue";
                  else if (tag === "rect") role = fill === "none" ? "tokenGlow" : "tokenBody";
                  else role = "tokenOther";
                } else if (ptrG) {
                  role = "chevron";
                } else if (tag === "rect" && fill === "#161616") {
                  role = "calloutChip";
                } else if (tag === "text" && /CONTOPESC|LISTE SORTATE/i.test(txt)) {
                  role = "bandLabel";
                } else if (tag === "text" && /INTERCLASAT|SORTAT/i.test(txt)) {
                  role = "ruleCaption";
                } else if (tag === "text" && /POZI[ȚT]II/i.test(txt)) {
                  role = "posCaption";
                } else if (tag === "text") {
                  // chip text is accent (#fde047); index-strip digits are faint (#6f6f6f). Both are chrome.
                  role = fill === "#fde047" ? "calloutText" : "chromeText";
                } else if (tag === "line") {
                  role = "baselineRule";
                } else if (tag === "path") {
                  role = "bracket";
                } else if (tag === "polygon") {
                  role = "polygon";
                } else if (tag === "rect") {
                  role = "chromeRect";
                }
                return { tag, role, cellKey, ptrEl: ptrG, txt, fill, stroke };
              };

              const isVisible = (el) => {
                const cs = getComputedStyle(el);
                if (cs.display === "none" || cs.visibility === "hidden") return false;
                if (parseFloat(cs.opacity || "1") <= 0.05) return false;
                const r = el.getBoundingClientRect();
                return r.width >= 0.5 && r.height >= 0.5;
              };

              const PAINT_TAGS = new Set(["rect", "text", "path", "line", "polygon", "circle", "ellipse"]);
              const all = Array.from(svgEl.querySelectorAll("*")).filter(
                (el) => PAINT_TAGS.has(el.tagName.toLowerCase()) && isVisible(el),
              );

              const items = all.map((el, i) => {
                const r = el.getBoundingClientRect();
                const meta = classify(el);
                return { i, el, r, meta, thin: Math.min(r.width, r.height) };
              });

              const descOf = (X) => {
                const m = X.meta, r = X.r;
                let label = m.role;
                if (m.txt) label += ` "${m.txt.slice(0, 50)}"`;
                else if (m.cellKey) label += ` ${m.cellKey}`;
                else if (m.ptrEl) label += ` ${m.ptrEl.getAttribute("data-pointer")}`;
                return {
                  role: m.role,
                  tag: m.tag,
                  label,
                  cellKey: m.cellKey,
                  rect: { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) },
                };
              };

              // rect-A fully contains rect-B (with a small slack) — used for the chip-backs-own-caption rule.
              const contains = (R, r, slack = 1.5) =>
                r.left >= R.left - slack &&
                r.right <= R.right + slack &&
                r.top >= R.top - slack &&
                r.bottom <= R.bottom + slack;

              // ── the EXPLICIT allow-list of INTENDED cross-group adjacencies ──
              // returns a reason string (allowed) or null (flag as a real overlap). `ow`/`oh` = the pair's
              // overlap width/height (passed from the loop) so the glow-sliver rule can size-gate the graze.
              const allowReason = (A, B, ow, oh) => {
                // (1) SAME token group (value on its own cell, glow on its own bar). Defensive belt — the
                //     pair loop already skips same-cellKey pairs before calling this.
                if (A.meta.cellKey && A.meta.cellKey === B.meta.cellKey)
                  return "same token group (data-cell-index match)";

                // (1b) the CALLOUT CHIP backing its OWN caption text. The chip is a bordered <rect> and its
                //     caption <text> is a SIBLING (not a DOM descendant) inside the same chrome <g>, so the
                //     DOM-contains check misses it — but GEOMETRICALLY the caption sits fully INSIDE the chip
                //     (that is the chip's entire purpose). Allow a chip↔chrome-text pair when the text rect
                //     is contained within the chip rect. The SEED defect (chip bottom crossing the run-band
                //     LABEL, which sits BELOW/outside the chip) is NOT contained → stays flagged.
                const chipBacksText = (CHIP, T) =>
                  CHIP.meta.role === "calloutChip" &&
                  (T.meta.role === "calloutText" || T.meta.role === "ruleCaption" || T.meta.role === "chromeText" || T.meta.role === "posCaption") &&
                  contains(CHIP.r, T.r);
                if (chipBacksText(A, B) || chipBacksText(B, A))
                  return "callout chip backing its OWN caption text (text rect contained in chip)";

                // (2) a THIN structural rule/baseline (a <line>, or a thin non-callout <rect>) touching a
                //     token body/value/glow or a chevron — it is the FLOOR the bars/cells stand on, or the
                //     index rule beneath the row. Allowed only when the rule's own thickness ≤ THIN_RULE_PX.
                const thinStructural = (X, Y) =>
                  (X.meta.role === "baselineRule" ||
                    (X.meta.tag === "rect" && X.meta.role !== "calloutChip" && X.meta.role !== "tokenBody")) &&
                  X.thin <= THIN_RULE_PX &&
                  (Y.meta.role === "tokenBody" ||
                    Y.meta.role === "tokenValue" ||
                    Y.meta.role === "tokenGlow" ||
                    Y.meta.role === "chevron");
                if (thinStructural(A, B) || thinStructural(B, A))
                  return "thin structural baseline/rule touching a bar/cell (≤THIN_RULE_PX)";

                // (3) a front-pointer chevron sitting DIRECTLY ABOVE its OWN run-head column. The chevron
                //     group carries data-pointer-index = the array position it points at; its owner token
                //     is the <g data-cell-index> at that same position. The tip is drawn a few px above the
                //     cell/bar top, so a small graze with its OWN head is intended; with ANY OTHER cell it
                //     is a real "chevron on the wrong bar" → flag.
                const chevronOverOwner = (CH, TOK) => {
                  if (CH.meta.role !== "chevron" || TOK.meta.role !== "tokenBody") return false;
                  const ptr = CH.meta.ptrEl;
                  if (!ptr) return false;
                  const idx = ptr.getAttribute("data-pointer-index");
                  return TOK.meta.cellKey === "cell#" + idx;
                };
                if (chevronOverOwner(A, B) || chevronOverOwner(B, A))
                  return "front-pointer chevron over its OWN run-head column (intended tick)";

                // (4) a glow halo (tokenGlow, fill=none) of the just-taken token grazing a thin structural
                //     baseline — the halo is 4px bigger than its bar and the output baseline runs right
                //     under it. Same intent as (2) but the glow is its own token-group child, so it can be
                //     cross-group only against chrome; allow a glow↔baselineRule touch.
                const glowOnRule = (X, Y) =>
                  X.meta.role === "tokenGlow" && Y.meta.role === "baselineRule" && Y.thin <= THIN_RULE_PX;
                if (glowOnRule(A, B) || glowOnRule(B, A))
                  return "just-taken glow halo grazing a thin output baseline (≤THIN_RULE_PX)";

                // (5) a just-taken glow HALO (decorative ring, fill=none, drawn 4px larger than its bar)
                //     edge-grazing a DIFFERENT token group by ≤ GLOW_SLIVER_PX on the shorter dim. The 4px
                //     outer ring clips a neighbour's edge by a few px — a decorative sliver, not a content
                //     occlusion. A DEEPER glow overlap (shorter dim > GLOW_SLIVER_PX) is the bar genuinely
                //     sitting over another bar → NOT allowed (stays a real collision).
                const glowSliver = (G, OTHER) =>
                  G.meta.role === "tokenGlow" &&
                  (OTHER.meta.role === "tokenBody" || OTHER.meta.role === "tokenValue" || OTHER.meta.role === "tokenGlow") &&
                  Math.min(ow, oh) <= GLOW_SLIVER_PX;
                if (glowSliver(A, B) || glowSliver(B, A))
                  return "decorative glow-halo edge grazing a neighbour token by ≤GLOW_SLIVER_PX";

                return null; // not intended → flag
              };

              // ── pairwise intersection ──
              const flagged = [];
              for (let i = 0; i < items.length; i++) {
                for (let j = i + 1; j < items.length; j++) {
                  const A = items[i], B = items[j];
                  if (A.el.contains(B.el) || B.el.contains(A.el)) continue; // DOM-nested, not a collision
                  if (A.meta.cellKey && A.meta.cellKey === B.meta.cellKey) continue; // same token group

                  const ra = A.r, rb = B.r;
                  const ow = Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left);
                  const oh = Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top);
                  if (ow <= 0 || oh <= 0) continue;
                  const area = ow * oh;
                  if (area < AREA_FLOOR) continue;
                  if (Math.min(ow, oh) < MIN_OVERLAP_PX) continue;

                  const reason = allowReason(A, B, ow, oh);
                  flagged.push({
                    allowed: !!reason,
                    reason: reason || undefined,
                    a: descOf(A),
                    b: descOf(B),
                    area: Math.round(area),
                    ow: Math.round(ow * 10) / 10,
                    oh: Math.round(oh * 10) / 10,
                  });
                }
              }
              return { flagged, elementCount: items.length };
            },
            { AREA_FLOOR, MIN_OVERLAP_PX, THIN_RULE_PX, GLOW_SLIVER_PX },
          );

          for (const f of result.flagged) {
            const rec = {
              skin: skin.name,
              skinShort: skin.short,
              viewport: `${vp.width}x${vp.height}`,
              frame,
              phase: MERGE_STEPS[frame].phase,
              ...f,
            };
            if (f.allowed) {
              overlaps.push({ ...rec, kind: "allowed" });
            } else {
              overlaps.push({ ...rec, kind: "real" });
              console.log(
                `  REAL OVERLAP  frame ${frame} (${MERGE_STEPS[frame].phase}): ` +
                  `${f.a.label}  ✕  ${f.b.label}  — area=${f.area}px², overlap ${f.ow}×${f.oh}px`,
              );
            }
          }
        }

        if (errs.length) console.warn(`  [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);
        await ctx.close();
      }
    }
  } finally {
    await browser.close();
  }

  // ── Verdict ────────────────────────────────────────────────────────────────────────────────────────
  const real = overlaps.filter((o) => o.kind === "real");
  const allowed = overlaps.filter((o) => o.kind === "allowed");

  console.log(`\n================ SVG OVERLAP GATE VERDICT ================`);
  console.log(`  allow-listed intended adjacencies (not failed): ${allowed.length}`);
  console.log(`  REAL cross-group overlaps (HARD FAILS)         : ${real.length}`);

  if (allowed.length) {
    const byReason = {};
    for (const a of allowed) byReason[a.reason] = (byReason[a.reason] || 0) + 1;
    console.log(`\n  ---- allow-list reasons that fired (auditable, not a silent sink) ----`);
    for (const [reason, count] of Object.entries(byReason))
      console.log(`    ${count.toString().padStart(4)} × ${reason}`);
  }

  if (real.length) {
    // distinct frames per skin×viewport that failed
    const bySkin = new Map();
    for (const o of real) {
      const k = `${o.skinShort}|${o.viewport}`;
      if (!bySkin.has(k)) bySkin.set(k, new Set());
      bySkin.get(k).add(o.frame);
    }
    console.log(`\n  ---- REAL OVERLAPS — the fix-step work list ----`);
    for (const o of real) {
      console.log(
        `  [${o.skinShort} · ${o.viewport} · frame ${o.frame} (${o.phase})] ` +
          `${o.a.label}  ✕  ${o.b.label}  — area=${o.area}px², overlap ${o.ow}×${o.oh}px`,
      );
      console.log(`        A rect=${JSON.stringify(o.a.rect)}  B rect=${JSON.stringify(o.b.rect)}`);
    }
    console.log(`\n  ---- FRAMES WITH A REAL OVERLAP (per skin × viewport) ----`);
    for (const [k, frames] of bySkin) {
      const sorted = [...frames].sort((a, b) => a - b);
      console.log(`  [${k}] frames: [${sorted.join(", ")}]  (${sorted.length})`);
    }
    console.log(`\n  ---- REAL_OVERLAPS_JSON ----`);
    console.log(
      JSON.stringify(
        real.map((o) => ({
          skin: o.skinShort, frame: o.frame, viewport: o.viewport, phase: o.phase,
          a: o.a.label, b: o.b.label, areaPx: o.area, overlapW: o.ow, overlapH: o.oh,
        })),
        null,
        2,
      ),
    );
    console.error(`\nGATE RED — ${real.length} real cross-group SVG overlap(s). Exit 1.`);
    process.exit(1);
  }

  console.log(`\nGATE GREEN — no un-allow-listed SVG element overlap on any frame/skin/viewport. Exit 0.`);
  process.exit(0);
}

run().catch((e) => {
  console.error("[harness] FATAL:", e);
  process.exit(1);
});
