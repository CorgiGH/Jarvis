/**
 * class-diagram-layout-gate.mjs — the LAYOUT/RENDER gate for the family-7 `class-diagram` figure
 * (ClassDiagramFamily.tsx, instance viz-poo-animals-001, mounted on /tutor/viz-demo).
 *
 * THE THREE BUG CLASSES this gate exists to catch (all verified in code by the 5-agent council):
 *
 *   D1 — member text overflows its own box's right border (BOTH skins). boxWidth() sizes the box
 *        from an off-DOM canvas ctx.measureText estimate that UNDER-measures the real loaded-webfont
 *        advance, so a long member row ("+ makeSound(): void") paints past the box's right edge.
 *        INVARIANT: for every <text> under [data-field]/[data-method] inside a <g data-class-id>, the
 *        text's right edge (getBBox().x + width, in the box's local space) must be ≤ the box rect's
 *        width attribute (+ a sub-pixel slack). Text outside its box = HARD FAIL.
 *
 *   D2 — a multiplicity / edge label bbox intersects a CLASS box (BOTH skins). renderEdge() places
 *        toMult/fromMult/label at fixed offsets off the connector endpoint with ZERO box-collision
 *        avoidance; the endpoint sits ON the box border, so "1..*" lands inside the Owner box.
 *        INVARIANT: every [data-edge] <text> label (absolute SVG space) must sit OUTSIDE every class
 *        box rect. Label-inside-a-box = HARD FAIL.
 *
 *   D3 — on the DARK skin, the hollow inheritance triangle (and hollow aggregation diamond) paint
 *        SOLID WHITE on the dark canvas. renderArrowhead() hardcodes fill=PAPER ("#ffffff") for the
 *        hollow shapes; PAPER is correct on the LIGHT paper skin but a glaring white blob on the dark
 *        canvas (#0e0e0e). INVARIANT (DARK root only): every [data-edge] <polygon> arrowhead fill must
 *        NOT be near-white — it must read as hollow against the dark canvas (a dark fill ≈ the canvas
 *        bg). White-on-dark = HARD FAIL. The LIGHT root's white hollow fill is CORRECT and is NOT
 *        flagged (the gate is SKIN-AWARE).
 *
 * THE FIGURE is mounted on /tutor/viz-demo TWICE (both stamp data-testid="cd-root"):
 *   cd-root[0] = the DARK lesson-surface tile  (variant="dark", inside "DARK LESSON-SURFACE FIGURES")
 *   cd-root[1] = the LIGHT standalone section.
 * Skin is auto-detected from the first [data-class-id] outer <rect> fill (#161616 ⇒ dark, else light),
 * so the gate never assumes which root is which — it reads the rendered reality.
 *
 * Every assertion runs at BOTH viewport heights (1536×648 and 1536×730 — the ≥2-heights cardinal-sin
 * rule) on BOTH roots (D1/D2 both roots; D3 dark root only).
 *
 * Usage:  node tools/class-diagram-layout-gate.mjs   (from tutor-web)
 * Prereq: vite dev server on http://localhost:5173 (the harness checks + fails loud if down).
 * Exit:   0 = GREEN (every assertion passed on every root/viewport); 1 = RED (≥1 fail) or harness err.
 *
 * Measurement is geometric (getBBox / rect width attr / fill attr in page.evaluate), never pixels.
 */

import { chromium } from "playwright";

const BASE_URL = "http://localhost:5173";
const ROUTE = "/tutor/viz-demo";
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), per reference_alex_screen_resolution.

// Both viewport heights the cardinal-sin gate mandates (≥2 heights). Width is Alex's 1536.
const VIEWPORTS = [
  { width: 1536, height: 648 }, // content area WITH the bookmarks bar
  { width: 1536, height: 730 }, // content area with NO bookmarks bar
];

// ── Thresholds ──────────────────────────────────────────────────────────────────────────────────────
// D1: a text right-edge ≤ boxW + this slack is "inside". A real overflow (the +2.4u council fact) is
//     well above a sub-pixel rounding sliver.
const D1_SLACK_U = 0.5;
// D2: a label↔box intersection below this many SVG-user-units² is an anti-alias / shared-border graze,
//     not a label genuinely sitting inside a box.
const D2_AREA_FLOOR_U = 1.0;
// D3: a fill whose relative luminance is at/above this is "near-white" (white = 1.0; the dark canvas
//     #0e0e0e ≈ 0.005, the dark box #161616 ≈ 0.0085). 0.5 cleanly separates white from any dark fill.
const D3_LUMA_NEAR_WHITE = 0.5;

const SETTLE_MS = 350; // let React commit + the webfont settle so getBBox uses real advances.

async function run() {
  // Fail loud if the dev server is down (the gate must never silently pass on a dead server).
  const probe = await fetch(BASE_URL + ROUTE).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[harness] dev server not reachable at ${BASE_URL}${ROUTE} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[harness] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }
  console.log(`[harness] dev server up @ ${BASE_URL}${ROUTE}.`);
  console.log(`[harness] viewports=${VIEWPORTS.map((v) => `${v.width}x${v.height}`).join(",")} · roots=both (skin auto-detected).`);
  console.log(`[harness] thresholds: D1_SLACK=${D1_SLACK_U}u, D2_AREA_FLOOR=${D2_AREA_FLOOR_U}u², D3_LUMA_NEAR_WHITE=${D3_LUMA_NEAR_WHITE}\n`);

  const browser = await chromium.launch({ headless: true });
  const failures = []; // every assertion failure across all viewports/roots
  let assertionsRun = 0;

  try {
    for (const vp of VIEWPORTS) {
      const ctx = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        deviceScaleFactor: DPR,
        reducedMotion: "reduce",
      });
      const page = await ctx.newPage();
      const errs = [];
      page.on("pageerror", (e) => errs.push("PAGE: " + e.message.slice(0, 200)));
      page.on("console", (m) => { if (m.type() === "error") errs.push("CON: " + m.text().slice(0, 200)); });

      await page.goto(BASE_URL + ROUTE, { waitUntil: "networkidle", timeout: 30_000 });
      // wait for the loaded webfont so getBBox uses the REAL SVG advance (the source of the D1 drift).
      await page.evaluate(() => (document.fonts ? document.fonts.ready : Promise.resolve()));
      await page.waitForTimeout(SETTLE_MS);

      const roots = page.locator('[data-testid="cd-root"]');
      const rootCount = await roots.count();
      if (rootCount < 2) {
        failures.push({
          viewport: `${vp.width}x${vp.height}`, root: "-", defect: "MOUNT",
          detail: `expected 2 cd-root instances (dark + light), found ${rootCount}`,
        });
        await ctx.close();
        continue;
      }

      for (let ri = 0; ri < rootCount; ri++) {
        const root = roots.nth(ri);
        const svg = root.locator("svg.algo-stepper-shell-svg").first();
        await svg.waitFor({ state: "visible", timeout: 15_000 });

        // ── extract ALL geometry for this root in ONE page.evaluate (needs live DOM + getBBox) ──
        const data = await svg.evaluate((svgEl, cfg) => {
          const { D3_LUMA_NEAR_WHITE } = cfg;

          // parse a translate(x,y) off a <g> transform attr.
          const translateOf = (g) => {
            const t = g.getAttribute("transform") || "";
            const m = /translate\(\s*([-\d.]+)\s*[, ]\s*([-\d.]+)\s*\)/.exec(t);
            return m ? { x: parseFloat(m[1]), y: parseFloat(m[2]) } : { x: 0, y: 0 };
          };

          // parse a fill attr → {hex, luma}. Handles #rgb / #rrggbb / "white"/"none".
          const parseFill = (raw) => {
            const f = (raw || "").trim().toLowerCase();
            if (f === "none" || f === "") return { hex: f || "none", luma: null };
            if (f === "white") return { hex: "#ffffff", luma: 1 };
            if (f === "black") return { hex: "#000000", luma: 0 };
            let hex = f;
            if (/^#[0-9a-f]{3}$/.test(f)) hex = "#" + f.slice(1).split("").map((c) => c + c).join("");
            if (!/^#[0-9a-f]{6}$/.test(hex)) return { hex: f, luma: null };
            const r = parseInt(hex.slice(1, 3), 16) / 255;
            const g = parseInt(hex.slice(3, 5), 16) / 255;
            const b = parseInt(hex.slice(5, 7), 16) / 255;
            // perceptual relative luminance
            const luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            return { hex, luma };
          };

          // ── class boxes ──
          const classGs = Array.from(svgEl.querySelectorAll("[data-class-id]"));
          const boxes = classGs.map((g) => {
            const id = g.getAttribute("data-class-id");
            const rect = g.querySelector("rect"); // first rect = the outer box
            const w = parseFloat(rect.getAttribute("width"));
            const h = parseFloat(rect.getAttribute("height"));
            const t = translateOf(g);
            const boxFill = (rect.getAttribute("fill") || "").toLowerCase();

            // members: each [data-field]/[data-method] <text>, bbox in the group's LOCAL space
            // (the <g> is translated, text has no own transform → getBBox is box-local, directly
            // comparable to the rect width attr).
            const members = [];
            for (const sel of ["[data-field]", "[data-method]"]) {
              for (const mg of g.querySelectorAll(sel)) {
                const txtEl = mg.querySelector("text");
                if (!txtEl) continue;
                const bb = txtEl.getBBox();
                members.push({
                  kind: sel === "[data-field]" ? "field" : "method",
                  key: mg.getAttribute("data-field") || mg.getAttribute("data-method"),
                  text: (txtEl.textContent || "").trim(),
                  right: bb.x + bb.width, // box-local right edge
                });
              }
            }
            return { id, boxFill, abs: { x: t.x, y: t.y, w, h }, w, members };
          });

          // skin from the first class box's outer-rect fill (#161616 ⇒ dark).
          const skin = boxes.length && boxes[0].boxFill === "#161616" ? "dark" : "light";

          // ── edge labels (absolute SVG space; edge <g> are NOT transformed) ──
          const edgeGs = Array.from(svgEl.querySelectorAll("[data-edge]"));
          const edgeLabels = [];
          const arrowheads = [];
          for (const eg of edgeGs) {
            const edge = eg.getAttribute("data-edge");
            for (const txtEl of eg.querySelectorAll("text")) {
              const bb = txtEl.getBBox(); // absolute (no enclosing transform)
              edgeLabels.push({
                edge,
                text: (txtEl.textContent || "").trim(),
                abs: { x: bb.x, y: bb.y, w: bb.width, h: bb.height },
              });
            }
            for (const poly of eg.querySelectorAll("polygon")) {
              const pf = parseFill(poly.getAttribute("fill"));
              arrowheads.push({
                edge,
                fillRaw: poly.getAttribute("fill"),
                hex: pf.hex,
                luma: pf.luma,
                nearWhite: pf.luma !== null && pf.luma >= D3_LUMA_NEAR_WHITE,
              });
            }
          }

          return { skin, boxes, edgeLabels, arrowheads };
        }, { D3_LUMA_NEAR_WHITE });

        const tag = `${vp.width}x${vp.height} · root[${ri}] · skin=${data.skin}`;
        const okLines = [];

        // ── D1: every member text right-edge ≤ its box width ──
        for (const box of data.boxes) {
          for (const mem of box.members) {
            assertionsRun++;
            const overflow = mem.right - box.w;
            if (overflow > D1_SLACK_U) {
              failures.push({
                viewport: `${vp.width}x${vp.height}`, root: `[${ri}] ${data.skin}`, defect: "D1",
                detail: `box '${box.id}' (w=${box.w.toFixed(1)}u): member "${mem.text}" right-edge=${mem.right.toFixed(1)}u OVERFLOWS by +${overflow.toFixed(1)}u`,
              });
            } else {
              okLines.push(`D1 ok  box '${box.id}' w=${box.w.toFixed(1)}u · "${mem.text}" right=${mem.right.toFixed(1)}u (margin ${(-overflow).toFixed(1)}u)`);
            }
          }
        }

        // ── D2: every edge label sits OUTSIDE every class box ──
        for (const lbl of data.edgeLabels) {
          for (const box of data.boxes) {
            assertionsRun++;
            const ax = lbl.abs, bx = box.abs;
            const ow = Math.min(ax.x + ax.w, bx.x + bx.w) - Math.max(ax.x, bx.x);
            const oh = Math.min(ax.y + ax.h, bx.y + bx.h) - Math.max(ax.y, bx.y);
            if (ow > 0 && oh > 0 && ow * oh > D2_AREA_FLOOR_U) {
              failures.push({
                viewport: `${vp.width}x${vp.height}`, root: `[${ri}] ${data.skin}`, defect: "D2",
                detail: `edge '${lbl.edge}' label "${lbl.text}" (bbox ${ax.x.toFixed(1)},${ax.y.toFixed(1)} ${ax.w.toFixed(1)}×${ax.h.toFixed(1)}) INTERSECTS box '${box.id}' by ${(ow * oh).toFixed(1)}u² (overlap ${ow.toFixed(1)}×${oh.toFixed(1)}u)`,
              });
            } else {
              okLines.push(`D2 ok  label "${lbl.text}" clear of box '${box.id}'`);
            }
          }
        }

        // ── D3 (DARK root only): no near-white hollow arrowhead on the dark canvas ──
        if (data.skin === "dark") {
          for (const ah of data.arrowheads) {
            assertionsRun++;
            if (ah.nearWhite) {
              failures.push({
                viewport: `${vp.width}x${vp.height}`, root: `[${ri}] ${data.skin}`, defect: "D3",
                detail: `edge '${ah.edge}' arrowhead polygon fill=${ah.fillRaw} (luma=${ah.luma.toFixed(3)}) is NEAR-WHITE on the dark canvas — must be a dark hollow fill`,
              });
            } else {
              okLines.push(`D3 ok  edge '${ah.edge}' arrowhead fill=${ah.fillRaw}${ah.luma !== null ? ` (luma=${ah.luma.toFixed(3)})` : ""}`);
            }
          }
        }

        console.log(`---------- ${tag} ----------`);
        for (const l of okLines) console.log(`  ${l}`);
        if (errs.length) console.warn(`  [browser errors] ${JSON.stringify(errs.slice(0, 5))}`);
      }

      await ctx.close();
    }
  } finally {
    await browser.close();
  }

  // ── Verdict ────────────────────────────────────────────────────────────────────────────────────────
  console.log(`\n================ CLASS-DIAGRAM LAYOUT GATE VERDICT ================`);
  console.log(`  assertions run : ${assertionsRun}`);
  console.log(`  failures       : ${failures.length}`);

  if (failures.length) {
    const byDefect = {};
    for (const f of failures) byDefect[f.defect] = (byDefect[f.defect] || 0) + 1;
    console.log(`  by defect      : ${Object.entries(byDefect).map(([d, n]) => `${d}=${n}`).join(", ")}`);
    console.log(`\n  ---- FAILURES ----`);
    for (const f of failures) {
      console.log(`  [${f.defect}] ${f.viewport} · root ${f.root}`);
      console.log(`        ${f.detail}`);
    }
    console.error(`\nGATE RED — ${failures.length} layout/render failure(s). Exit 1.`);
    process.exit(1);
  }

  console.log(`\nGATE GREEN — D1 (no member overflow), D2 (no label-in-box), D3 (no white-on-dark arrowhead) on both roots, both viewports. Exit 0.`);
  process.exit(0);
}

run().catch((e) => {
  console.error("[harness] FATAL:", e);
  process.exit(1);
});
