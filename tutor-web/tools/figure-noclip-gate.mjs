/**
 * figure-noclip-gate.mjs — the FIGURE-HUG NO-CLIP + ELEMENT-OVERLAP gate for the 5 content-sized
 * viz families (the families whose AlgoStepperShell SVG viewBox now SELF-SIZES to content, so a small
 * figure no longer floats in a fixed 480×360 void). It is the machine enforcement the council flagged
 * was MISSING: until now "the hug didn't crop anything" was confirmed only by a human eyeballing the
 * renders — svg-overlap-gate.mjs + frame-conjunction-gate.mjs are hard-scoped to the mergesort corpus
 * (/tutor/lectie-mergesort, /tutor/merge-compare, sort-merge-root) and never visit these families.
 *
 * THE TWO INVARIANTS this gate enforces, per family × skin × viewport × FRAME:
 *
 *   (A) NO-CLIP (the "hug didn't crop" check) — the WHOLE point of content-sizing the viewBox is that
 *       the hard black frame HUGS the content. The shell SVG uses preserveAspectRatio="xMidYMid meet",
 *       so the viewBox `0 0 viewBoxW viewBoxH` is the visible window: ANY painted element whose bbox
 *       crosses the bottom or right (or top/left) edge of the viewBox is CROPPED off-screen. If a
 *       family under-computes its viewBoxH/viewBoxW (e.g. GraphTree caps height at SVG_H=360 and a deep
 *       tree exceeds it), the deepest row silently clips. We measure every painted element's bbox in
 *       SVG USER UNITS (getBBox — already in the SVG's own coordinate space, directly comparable to the
 *       viewBox) and HARD-FAIL any element that pokes past a viewBox edge beyond a sub-pixel slack.
 *
 *   (B) NO-OVERLAP — no two painted figure elements from DIFFERENT logical groups occlude each other
 *       beyond an anti-alias sliver. These 5 families are no-overlap-BY-CONSTRUCTION (measured labels,
 *       disjoint vertical bands, centered grids), so the allow-list here is GENERIC + CONSERVATIVE
 *       (DOM-nested pairs, same data-group pairs, thin structural rules/baselines, a box backing its own
 *       text) — NOT the sort-merge-calibrated allow-list of svg-overlap-gate.mjs (that one models
 *       chevrons/glow-halos/callout-chips specific to the merge figure and WOULD false-positive here,
 *       per the assertNoSvgOverlap TODO in e2e/helpers/assertNoClip.ts). A real cross-group occlusion is
 *       a HARD FAIL.
 *
 * COVERAGE (discovered from VizDemoPage.tsx, not guessed — the dark + light roots share a testid, so we
 * scope by the WRAPPER testid and find the *-root svg inside it):
 *   families (5): graph-tree · seq-array · matrix-grid (dp = mg) · matrix-grid (gauss = mgg) · class-diagram (cd)
 *   skins   (2): LIGHT gallery tiles (viz-demo-*) AND DARK lesson-surface tiles (dark-fig-*)
 *   viewports(2): 1536×648 (with bookmarks bar) AND 1536×730 (no bookmarks) — the ≥2-heights cardinal rule
 *   frames     : EVERY reachable frame, stepped via the family's own `${prefix}-step-fwd` control to maxReachable.
 *
 * Usage:  node tutor-web/tools/figure-noclip-gate.mjs   (from repo root)  — or  node tools/figure-noclip-gate.mjs (from tutor-web)
 * Prereq: vite dev server on http://localhost:5173 (the harness checks + fails loud if down).
 * Exit:   0 = GREEN (no clip, no un-allow-listed overlap on any family/skin/viewport/frame) + a coverage
 *             summary; 1 = RED (≥1 clip or overlap) with a per-failure report, or a harness error.
 *
 * Deps: playwright-core (installed). No image deps — clip + overlap are computed from getBBox /
 * getBoundingClientRect in page.evaluate, never from pixels.
 */

import { chromium } from "playwright-core";

const BASE_URL = "http://localhost:5173";
const ROUTE = "/tutor/viz-demo";
const DPR = 1; // Alex's real DPR (1536×864 @ 1x), per reference_alex_screen_resolution.

// Both viewport heights the cardinal-sin gate mandates (≥2 heights). Width is Alex's 1536.
const VIEWPORTS = [
  { width: 1536, height: 648 }, // content area WITH the bookmarks bar
  { width: 1536, height: 730 }, // content area with NO bookmarks bar
];

// ── The 5 content-sized families × 2 skins. The dark + light root share a data-testid (`graph-tree-root`
// etc.), so we scope by the WRAPPER testid (unique) and find the *-root svg inside it. `prefix` = the
// family's AlgoStepperShell testIdPrefix (drives the -root, -step-fwd, -frame-counter, -scrubber testids).
const TARGETS = [
  // family            skin     wrapperTid                        prefix
  { family: "graph-tree",   skin: "light", wrapper: "viz-demo-graph-tree",        prefix: "graph-tree" },
  { family: "graph-tree",   skin: "dark",  wrapper: "dark-fig-graph-tree",        prefix: "graph-tree" },
  { family: "seq-array",    skin: "light", wrapper: "viz-demo-seq-array",         prefix: "seq-array" },
  { family: "seq-array",    skin: "dark",  wrapper: "dark-fig-seq-array",         prefix: "seq-array" },
  { family: "matrix-grid-dp", skin: "light", wrapper: "viz-demo-matrix-grid",      prefix: "mg" },
  { family: "matrix-grid-dp", skin: "dark",  wrapper: "dark-fig-matrix-grid",      prefix: "mg" },
  { family: "matrix-grid-gauss", skin: "light", wrapper: "viz-demo-matrix-grid-gauss", prefix: "mgg" },
  { family: "matrix-grid-gauss", skin: "dark",  wrapper: "dark-fig-matrix-grid-gauss", prefix: "mgg" },
  { family: "class-diagram", skin: "light", wrapper: "viz-demo-class-diagram",     prefix: "cd" },
  { family: "class-diagram", skin: "dark",  wrapper: "dark-fig-class-diagram",     prefix: "cd" },
];

// ── Deep-tree fixture (depth ≥ 5) — added to viz-demo to exercise GraphTree's SVG_H ceiling path.
// Same testIdPrefix ("graph-tree") as the other graph-tree mounts, so it is scoped by its unique wrapper.
const DEEP_TREE = { family: "graph-tree-deep", skin: "light", wrapper: "viz-demo-graph-tree-deep", prefix: "graph-tree" };

// ── Thresholds ────────────────────────────────────────────────────────────────────────────────────
// CLIP_SLACK_U: an element bbox edge ≤ this many SVG user-units past a viewBox edge is a sub-pixel /
//   anti-alias / stroke-half-width graze, not a real crop. A real clip (a row cut off, a deep tree
//   exceeding the height ceiling) pokes WELL past this.
const CLIP_SLACK_U = 1.5;
// OVERLAP thresholds (mirror svg-overlap-gate.mjs CSS-px calibration; the overlap pass works in screen
// px from getBoundingClientRect, so these are CSS-px like that gate).
const AREA_FLOOR = 12;       // CSS px² — below this is AA noise, never a real collision
const MIN_OVERLAP_PX = 2.0;  // CSS px on the SHORTER overlap dimension (edge-graze guard)
const THIN_RULE_PX = 3.5;    // a rule/baseline this thin or thinner is structural furniture

const SETTLE_MS = 450; // reducedMotion=reduce makes tweens instant, but let React commit + paint.

async function run() {
  // Fail loud if the dev server is down (the gate must never silently pass on a dead server).
  const probe = await fetch(BASE_URL + ROUTE).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`[harness] dev server not reachable at ${BASE_URL}${ROUTE} (got ${probe ? probe.status : "no response"}).`);
    console.error(`[harness] start it:  (cd tutor-web && npm run dev)  then re-run.`);
    process.exit(1);
  }
  // Include the deep-tree fixture only if it is actually mounted (so the gate runs before AND after the
  // fixture lands; it never silently skips a target that should be there).
  const haveDeep = await probeDeepTreeMounted();
  const targets = haveDeep ? [...TARGETS, DEEP_TREE] : [...TARGETS];

  console.log(`[harness] dev server up @ ${BASE_URL}${ROUTE}.`);
  console.log(`[harness] targets=${targets.length} (5 families × 2 skins${haveDeep ? " + deep-tree fixture" : " — deep-tree fixture NOT mounted yet"}).`);
  console.log(`[harness] viewports=${VIEWPORTS.map((v) => `${v.width}x${v.height}`).join(",")} · stepping EVERY frame to maxReachable.`);
  console.log(`[harness] thresholds: CLIP_SLACK=${CLIP_SLACK_U}u · AREA_FLOOR=${AREA_FLOOR}px² · MIN_OVERLAP_PX=${MIN_OVERLAP_PX} · THIN_RULE_PX=${THIN_RULE_PX}\n`);

  const browser = await chromium.launch({ headless: true });
  const clips = [];     // real NO-CLIP failures
  const overlaps = [];  // real OVERLAP failures
  let allowedOverlaps = 0;
  // coverage matrix: key = `${family}|${skin}|${viewport}` → frames stepped
  const coverage = new Map();

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
      // wait for the loaded webfont so getBBox uses the REAL SVG advance (measured layouts depend on it).
      await page.evaluate(() => (document.fonts ? document.fonts.ready : Promise.resolve()));
      await page.waitForTimeout(SETTLE_MS);

      for (const t of targets) {
        const vpTag = `${vp.width}x${vp.height}`;
        const covKey = `${t.family}|${t.skin}|${vpTag}`;
        const wrapper = page.getByTestId(t.wrapper);
        const wrapCount = await wrapper.count();
        if (wrapCount === 0) {
          clips.push({ ...t, viewport: vpTag, frame: -1, edge: "MOUNT", detail: `wrapper testid '${t.wrapper}' not found` });
          continue;
        }
        const root = wrapper.getByTestId(`${t.prefix}-root`).first();
        const svg = root.locator("svg.algo-stepper-shell-svg").first();
        await svg.waitFor({ state: "visible", timeout: 15_000 });

        // How many reachable frames? Step the family's own step-fwd until disabled, counting. We reset to
        // frame 0 first (the demo mounts at the initial frame). class-diagram is a single degenerate frame.
        const fwd = wrapper.getByTestId(`${t.prefix}-step-fwd`).first();
        const reset = wrapper.getByTestId(`${t.prefix}-reset`).first();
        const hasReset = (await reset.count()) > 0;
        if (hasReset) { await reset.click().catch(() => {}); await page.waitForTimeout(120); }

        const framesStepped = [];
        let frame = 0;
        // Inspect + step. Cap at 60 to avoid a runaway if a control misbehaves (no family has >40 frames).
        for (; frame < 60; frame++) {
          await page.waitForTimeout(SETTLE_MS);
          const res = await inspectFrame(svg);
          framesStepped.push(frame);

          for (const c of res.clips) {
            clips.push({ ...t, viewport: vpTag, frame, ...c });
          }
          for (const o of res.overlaps) {
            if (o.allowed) allowedOverlaps++;
            else overlaps.push({ ...t, viewport: vpTag, frame, ...o });
          }

          // advance; stop when step-fwd is gone or disabled (last reachable frame).
          const fwdCount = await fwd.count();
          if (fwdCount === 0) break;
          const disabled = await fwd.isDisabled().catch(() => true);
          if (disabled) break;
          await fwd.click().catch(() => {});
        }
        coverage.set(covKey, framesStepped);
      }

      if (errs.length) console.warn(`  [browser errors @ ${vp.width}x${vp.height}] ${JSON.stringify(errs.slice(0, 5))}`);
      await ctx.close();
    }
  } finally {
    await browser.close();
  }

  // ── Coverage summary ────────────────────────────────────────────────────────────────────────────
  console.log(`================ COVERAGE EXERCISED ================`);
  let totalFrameChecks = 0;
  for (const [key, frames] of coverage) {
    totalFrameChecks += frames.length;
    console.log(`  ${key.padEnd(46)} frames stepped: ${frames.length} [${frames[0]}..${frames[frames.length - 1]}]`);
  }
  console.log(`  ──`);
  console.log(`  total (family×skin×viewport×frame) checks: ${totalFrameChecks}`);
  console.log(`  allow-listed intended adjacencies (not failed): ${allowedOverlaps}\n`);

  // ── Verdict ─────────────────────────────────────────────────────────────────────────────────────
  console.log(`================ FIGURE NO-CLIP + OVERLAP GATE VERDICT ================`);
  console.log(`  NO-CLIP failures (element crosses a viewBox edge): ${clips.length}`);
  console.log(`  OVERLAP failures (cross-group occlusion)         : ${overlaps.length}`);

  if (clips.length) {
    console.log(`\n  ---- NO-CLIP FAILURES (the hug cropped content) ----`);
    for (const c of clips) {
      console.log(
        `  [CLIP] ${c.family} · ${c.skin} · ${c.viewport} · frame ${c.frame} · edge ${c.edge}`,
      );
      console.log(`        ${c.detail}`);
    }
  }
  if (overlaps.length) {
    console.log(`\n  ---- OVERLAP FAILURES (cross-group occlusion) ----`);
    for (const o of overlaps) {
      console.log(
        `  [OVERLAP] ${o.family} · ${o.skin} · ${o.viewport} · frame ${o.frame}: ` +
          `${o.a}  ✕  ${o.b}  — area=${o.area}px², overlap ${o.ow}×${o.oh}px`,
      );
    }
  }

  if (clips.length || overlaps.length) {
    console.error(`\nGATE RED — ${clips.length} clip + ${overlaps.length} overlap failure(s). Exit 1.`);
    process.exit(1);
  }
  console.log(`\nGATE GREEN — every painted element of all 5 families (both skins, both viewports, every frame) stays inside its hugged viewBox with no cross-group overlap. Exit 0.`);
  process.exit(0);
}

// Probe (cheap fetch-free) whether the deep-tree wrapper exists in the rendered DOM, on a throwaway page.
async function probeDeepTreeMounted() {
  const b = await chromium.launch({ headless: true });
  try {
    const ctx = await b.newContext({ viewport: { width: 1536, height: 730 }, deviceScaleFactor: DPR, reducedMotion: "reduce" });
    const page = await ctx.newPage();
    await page.goto(BASE_URL + ROUTE, { waitUntil: "domcontentloaded", timeout: 30_000 });
    await page.waitForTimeout(300);
    const n = await page.getByTestId(DEEP_TREE.wrapper).count();
    await ctx.close();
    return n > 0;
  } catch {
    return false;
  } finally {
    await b.close();
  }
}

/**
 * Inspect ONE rendered frame of an SVG figure: run BOTH passes inside the page (needs live DOM +
 * getBoundingClientRect + getScreenCTM + DOM ancestry). Returns { clips:[…], overlaps:[…] }.
 *
 *  • NO-CLIP works in SVG USER UNITS, mapped from SCREEN px. We can't use a bare el.getBBox() against the
 *    viewBox — getBBox is in the element's OWN local space (inside its translated <g>), so a node at
 *    translate(40,28) reports x=-41.5 and would false-fail "left clip". Instead we take each element's
 *    SCREEN rect (getBoundingClientRect, which IS the painted box after all ancestor transforms) and map
 *    its 4 corners through the SVG's INVERSE screen CTM → the box in viewBox user units, then compare
 *    against [vbX,vbW]×[vbY,vbH]. preserveAspectRatio="xMidYMid meet" makes the viewBox the visible
 *    window, so a mapped box past an edge is genuinely cropped on screen.
 *  • OVERLAP works in SCREEN px from getBoundingClientRect (so the thresholds match svg-overlap-gate.mjs).
 */
async function inspectFrame(svgLocator) {
  return svgLocator.evaluate(
    (svgEl, cfg) => {
      const { CLIP_SLACK_U, AREA_FLOOR, MIN_OVERLAP_PX, THIN_RULE_PX } = cfg;

      const PAINT = new Set(["rect", "text", "path", "line", "polygon", "circle", "ellipse"]);
      const isVisible = (el) => {
        const cs = getComputedStyle(el);
        if (cs.display === "none" || cs.visibility === "hidden") return false;
        if (parseFloat(cs.opacity || "1") <= 0.05) return false;
        const r = el.getBoundingClientRect();
        return r.width >= 0.5 || r.height >= 0.5;
      };
      const textOf = (el) => (el.textContent || "").replace(/\s+/g, " ").trim();
      // <defs> content + the HatchDefs pattern tiles live off-canvas by design; skip anything inside <defs>.
      const inDefs = (el) => {
        let n = el;
        while (n && n !== svgEl) { if (n.tagName && n.tagName.toLowerCase() === "defs") return true; n = n.parentNode; }
        return false;
      };

      // ── viewBox (the visible window in user units) ──
      const vb = (svgEl.getAttribute("viewBox") || "").trim().split(/\s+/).map(Number);
      const [vbX, vbY, vbW, vbH] = vb.length === 4 ? vb : [0, 0, 0, 0];

      // SCREEN → viewBox user-unit mapping. getScreenCTM() maps SVG user space → screen px; its inverse
      // maps a screen point back to user space. Mapping the element's painted screen rect this way gives
      // the box in the SAME units as the viewBox, correctly accounting for EVERY ancestor transform.
      const screenCTM = svgEl.getScreenCTM();
      const inv = screenCTM ? screenCTM.inverse() : null;
      const toUser = (clientX, clientY) => {
        const pt = svgEl.createSVGPoint();
        pt.x = clientX; pt.y = clientY;
        return pt.matrixTransform(inv);
      };
      // map a screen DOMRect to a user-space {x,y,w,h} (axis-aligned bound of the 4 mapped corners).
      const rectToUser = (r) => {
        const c = [toUser(r.left, r.top), toUser(r.right, r.top), toUser(r.right, r.bottom), toUser(r.left, r.bottom)];
        const xs = c.map((p) => p.x), ys = c.map((p) => p.y);
        const x = Math.min(...xs), y = Math.min(...ys);
        return { x, y, w: Math.max(...xs) - x, h: Math.max(...ys) - y };
      };

      const painted = Array.from(svgEl.querySelectorAll("*")).filter(
        (el) => PAINT.has(el.tagName.toLowerCase()) && !inDefs(el) && isVisible(el),
      );

      // ── (A) NO-CLIP — every painted element's user-space box inside [vbX,vbX+vbW]×[vbY,vbY+vbH] ──
      const clips = [];
      if (vbW > 0 && vbH > 0 && inv) {
        for (const el of painted) {
          const sr = el.getBoundingClientRect();
          const ub = rectToUser(sr);
          if (ub.w === 0 && ub.h === 0) continue;
          const tag = el.tagName.toLowerCase();
          const checks = [
            ["right", ub.x + ub.w - (vbX + vbW)],
            ["bottom", ub.y + ub.h - (vbY + vbH)],
            ["left", vbX - ub.x],
            ["top", vbY - ub.y],
          ];
          for (const [edge, over] of checks) {
            if (over > CLIP_SLACK_U) {
              const lbl = `${tag}${textOf(el) ? ` "${textOf(el).slice(0, 40)}"` : ""}`;
              clips.push({
                edge,
                detail:
                  `${lbl} user-box=[x=${ub.x.toFixed(1)},y=${ub.y.toFixed(1)},w=${ub.w.toFixed(1)},h=${ub.h.toFixed(1)}]` +
                  ` crosses the ${edge} viewBox edge (viewBox ${vbX} ${vbY} ${vbW} ${vbH}) by +${over.toFixed(1)}u`,
              });
              break; // one edge report per element is enough
            }
          }
        }
      }

      // ── (B) NO-OVERLAP — pairwise bbox-intersection over painted elements (screen px), generic allow-list ──
      // A logical group key: the nearest ancestor carrying ANY of the family data-* group stamps. Two
      // elements in the SAME group are an intended adjacency (a value on its own cell, a member text in its
      // own class box, an arrowhead on its own edge).
      const GROUP_ATTRS = [
        "data-cell-index", "data-node-id", "data-class-id", "data-field", "data-method",
        "data-edge", "data-pointer", "data-pivot",
      ];
      const groupKeyOf = (el) => {
        let n = el;
        while (n && n !== svgEl) {
          if (n.nodeType === 1 && n.getAttribute) {
            for (const a of GROUP_ATTRS) {
              const v = n.getAttribute(a);
              if (v !== null) return `${a}#${v}`;
            }
            const r = n.getAttribute("data-cell-row");
            const c = n.getAttribute("data-cell-col");
            if (r !== null && c !== null) return `cell#${r},${c}`;
          }
          n = n.parentNode;
        }
        return null; // chrome (callout chip + text, headers, rowOp rail, baselines, captions)
      };
      // The OUTER class-box <g data-class-id> contains BOTH the box <rect> AND sibling <g data-field>/
      // <g data-method> rows; the box's nearest group is `data-class-id#X` while a member's nearest group
      // is `data-field#X:n`, so they are DIFFERENT keys even though the text sits inside the box. Likewise
      // the dark callout CHIP (<rect fill="#161616">) backs its own callout <text> (both chrome, no group).
      // Both are intended "container backs its own content". We detect them geometrically: a SOLID rect that
      // fully CONTAINS a text/inner element is its backing surface, not an occlusion.
      const classBoxIdOf = (el) => {
        let n = el;
        while (n && n !== svgEl) {
          if (n.nodeType === 1 && n.getAttribute && n.getAttribute("data-class-id") !== null) return n.getAttribute("data-class-id");
          n = n.parentNode;
        }
        return null;
      };

      const items = painted.map((el) => {
        const r = el.getBoundingClientRect();
        const tag = el.tagName.toLowerCase();
        const fill = (el.getAttribute("fill") || "").toLowerCase();
        return {
          el, r, tag, fill,
          group: groupKeyOf(el),
          classBox: classBoxIdOf(el),
          txt: textOf(el),
          thin: Math.min(r.width, r.height),
        };
      });

      const lbl = (X) => `${X.tag}${X.txt ? ` "${X.txt.slice(0, 40)}"` : X.group ? ` ${X.group}` : ""}`;
      const contains = (R, r, slack = 2) =>
        r.left >= R.left - slack && r.right <= R.right + slack && r.top >= R.top - slack && r.bottom <= R.bottom + slack;
      // Fraction of element `r`'s area that lies inside rect `R`. Used for "a backing surface (chip/box)
      // covers ITS OWN text" — the canvas-measured chip width under-measures the real SVG webfont advance
      // (the documented DRIFT_FACTOR phenomenon), so the dark callout text can poke a few px past its own
      // chip border. A surface covering ≥ this fraction of the text IS its backing, not an occlusion; a
      // chip half-covering an UNRELATED label (the real defect class) covers far less and stays flagged.
      const coveredFraction = (R, r) => {
        const ow = Math.min(R.right, r.right) - Math.max(R.left, r.left);
        const oh = Math.min(R.bottom, r.bottom) - Math.max(R.top, r.top);
        if (ow <= 0 || oh <= 0) return 0;
        const ra = (r.right - r.left) * (r.bottom - r.top);
        return ra > 0 ? (ow * oh) / ra : 0;
      };

      // GENERIC conservative allow-list (these 5 families are no-overlap-by-construction — measured labels,
      // disjoint vertical bands, centered grids):
      //   1. same logical group (value-on-its-cell, member-in-its-box, arrowhead-on-its-edge).
      //   2. a THIN structural rule/line/divider/baseline touching anything — compartment dividers, the
      //      sorted baseline, grid/cell hairlines, the bar baseline (own painted thickness ≤ THIN_RULE_PX).
      //   3. a fill="none" element (glow halo / hollow shape outline / transparent hit-area) — paints only
      //      its stroke, so a bbox overlap is not a content occlusion.
      //   4. a SOLID rect fully CONTAINING the other element — a container backing its own content (the
      //      class box behind its member rows; the dark callout chip behind its caption text). This is the
      //      whole purpose of the surface, never an occlusion.
      //   5. two members of the SAME class box (a field row + a method row never collide — disjoint bands).
      const allowReason = (A, B) => {
        if (A.group && A.group === B.group) return "same logical group";
        const thinStructural = (X) =>
          (X.tag === "line" || X.tag === "path" || (X.tag === "rect" && X.thin <= THIN_RULE_PX)) && X.thin <= THIN_RULE_PX;
        if (thinStructural(A) || thinStructural(B)) return "thin structural rule/divider/baseline";
        if (A.fill === "none" || B.fill === "none") return "fill=none outline (glow halo / hollow shape / hit-area)";
        // a SOLID rect backs an inner element when it CONTAINS it, OR (drift-tolerant) covers ≥85% of a
        // text's area — the chip/box surface under-measures by a few px of webfont drift but is plainly the
        // text's own backing (co-centered, sole content), never an occlusion of an unrelated label.
        const backs = (C, T) =>
          C.tag === "rect" && C.fill !== "none" &&
          (contains(C.r, T.r) || (T.tag === "text" && coveredFraction(C.r, T.r) >= 0.85));
        if (backs(A, B) || backs(B, A)) return "solid rect backing its own contained content (box/chip surface)";
        if (A.classBox && A.classBox === B.classBox) return "two elements within the same class box (disjoint compartments)";
        return null;
      };

      const overlaps = [];
      for (let i = 0; i < items.length; i++) {
        for (let j = i + 1; j < items.length; j++) {
          const A = items[i], B = items[j];
          if (A.el.contains(B.el) || B.el.contains(A.el)) continue; // DOM-nested
          if (A.group && A.group === B.group) continue;             // same group (fast path)
          const ow = Math.min(A.r.right, B.r.right) - Math.max(A.r.left, B.r.left);
          const oh = Math.min(A.r.bottom, B.r.bottom) - Math.max(A.r.top, B.r.top);
          if (ow <= 0 || oh <= 0) continue;
          const area = ow * oh;
          if (area < AREA_FLOOR || Math.min(ow, oh) < MIN_OVERLAP_PX) continue;
          const reason = allowReason(A, B);
          overlaps.push({
            allowed: !!reason,
            reason: reason || undefined,
            a: lbl(A),
            b: lbl(B),
            area: Math.round(area),
            ow: Math.round(ow * 10) / 10,
            oh: Math.round(oh * 10) / 10,
          });
        }
      }

      return { clips, overlaps };
    },
    { CLIP_SLACK_U, AREA_FLOOR, MIN_OVERLAP_PX, THIN_RULE_PX },
  );
}

run().catch((e) => {
  console.error("[harness] FATAL:", e);
  process.exit(1);
});
