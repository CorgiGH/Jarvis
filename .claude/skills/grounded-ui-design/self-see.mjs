#!/usr/bin/env node
/**
 * SELF-SEE — the deterministic half of the vision-in-the-loop (Stage ⑤, NO LLM).
 *
 * The skill's #1 historical failure: every agent reasoned over a JSON/ASCII
 * abstraction and NEVER looked at a pixel (findings/2026-06-01-grounded-ui-design-skill-failures).
 * Vision (Claude looking at a screenshot) closes most of it — but vision calls
 * cost budget, so we gate them: a cheap deterministic probe runs first and the
 * agent only spends a vision call when the probe flags an anomaly OR to score taste.
 * (Playbook §3.1 conditional-screenshot pattern; §2.3/§3 contrast on the RENDERED
 * DOM; §3.4 spatial assertions.)
 *
 * TWO modes — this script never drives a browser (the loop uses Playwright MCP,
 * per playbook §3 "one toolchain"); it only (a) HANDS the agent the probe to paste
 * into mcp__playwright__browser_evaluate, and (b) GRADES the JSON the probe returns.
 *
 *   node self-see.mjs --emit-probe
 *       → prints the diagnostic JS. Paste it as the `function` arg of
 *         browser_evaluate on the rendered candidate route. It returns a JSON
 *         report (overflow / animation / viewBox / clipping / contrast / density).
 *
 *   node self-see.mjs --grade <probe-output.json>
 *       → applies thresholds, prints { pass, blocking[], warnings[] }.
 *         Any blocking[] entry means: FIX before this candidate may be taste-ranked
 *         (gates-before-ranking, REVISION #2). Whitespace/density is a WARNING the
 *         agent must look at, not an auto-fail (a poster's negative space is legal).
 *
 * Contrast math is intentionally identical to verify-proposals.mjs (the verifier
 * checks DECLARED token pairs; this checks the COMPUTED pairs the browser actually
 * painted — belt-and-suspenders for hand-composed surfaces).
 */
import { readFileSync } from "node:fs";

// ─────────────────────────────────────────────────────────────────────────────
// The in-page diagnostic. MUST be self-contained plain browser JS — no imports,
// no node, no closures over this module (it is serialized and run in the page).
// ─────────────────────────────────────────────────────────────────────────────
function DIAGNOSTIC_PROBE() {
  // WCAG relative luminance + contrast (mirror of verify-proposals.mjs).
  const parseRGB = (s) => {
    if (!s) return null;
    const m = s.match(/rgba?\(([^)]+)\)/i);
    if (!m) return null;
    const parts = m[1].split(",").map((n) => parseFloat(n.trim()));
    const a = parts.length > 3 ? parts[3] : 1;
    return { r: parts[0], g: parts[1], b: parts[2], a };
  };
  const lum = (c) => {
    const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  };
  const ratio = (a, b) => { const L1 = lum(a), L2 = lum(b); return (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05); };
  // walk ancestors to find the first opaque background actually painted behind el
  const effectiveBg = (el) => {
    let n = el;
    while (n && n !== document.documentElement) {
      const bg = parseRGB(getComputedStyle(n).backgroundColor);
      if (bg && bg.a > 0.05) return bg;
      n = n.parentElement;
    }
    return { r: 255, g: 255, b: 255, a: 1 };
  };
  const isVisible = (el) => {
    const cs = getComputedStyle(el);
    if (cs.display === "none" || cs.visibility === "hidden" || parseFloat(cs.opacity) < 0.05) return false;
    const r = el.getBoundingClientRect();
    return r.width > 1 && r.height > 1;
  };

  const overflow = [];
  const contrast = [];
  const animations = [];
  const clipping = [];
  const viewBox = [];

  // 1. overflow — any element whose content overflows its box (KaTeX spans are
  //    the notorious case; flag them explicitly).
  for (const el of document.querySelectorAll("*")) {
    if (!isVisible(el)) continue;
    if (el.scrollWidth - el.clientWidth > 2 || el.scrollHeight - el.clientHeight > 2) {
      const isKatex = !!el.closest(".katex, .katex-display, [class*='katex']");
      overflow.push({
        tag: el.tagName.toLowerCase(),
        cls: (el.className && el.className.toString().slice(0, 60)) || "",
        over: { x: el.scrollWidth - el.clientWidth, y: el.scrollHeight - el.clientHeight },
        katex: isKatex,
      });
    }
  }

  // 2. contrast — every element with its own non-empty text node, computed fg vs
  //    the effective painted bg. This is the gate that catches white-on-yellow.
  for (const el of document.querySelectorAll("*")) {
    if (!isVisible(el)) continue;
    const ownText = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 1);
    if (!ownText) continue;
    const cs = getComputedStyle(el);
    const fg = parseRGB(cs.color);
    if (!fg) continue;
    const bg = effectiveBg(el);
    const cr = ratio(fg, bg);
    const fontPx = parseFloat(cs.fontSize);
    const bold = parseInt(cs.fontWeight, 10) >= 700;
    const large = fontPx >= 24 || (fontPx >= 18.66 && bold); // WCAG large-text
    const min = large ? 3.0 : 4.5;
    if (cr < min) {
      contrast.push({
        text: (el.textContent || "").trim().slice(0, 40),
        ratio: +cr.toFixed(2), min, fontPx: +fontPx.toFixed(1), large,
        fg: cs.color, bg: `rgb(${bg.r},${bg.g},${bg.b})`,
      });
    }
  }

  // 3. animations still running / stuck not-forwards after the capture moment.
  for (const el of document.querySelectorAll("*")) {
    if (typeof el.getAnimations !== "function") break;
    for (const a of el.getAnimations()) {
      const fill = a.effect && a.effect.getComputedTiming ? a.effect.getTiming().fill : null;
      if (a.playState === "running") {
        animations.push({ state: "running", fill, name: (a.animationName || a.id || "anim") });
      }
    }
  }

  // 4. SVG viewBox vs rendered size mismatch (squashed/clipped viz).
  for (const svg of document.querySelectorAll("svg[viewBox]")) {
    if (!isVisible(svg)) continue;
    const vb = svg.getAttribute("viewBox").split(/\s+/).map(Number);
    const r = svg.getBoundingClientRect();
    if (vb.length === 4 && vb[2] > 0 && vb[3] > 0) {
      const vbAR = vb[2] / vb[3], renAR = r.width / r.height;
      if (Math.abs(vbAR - renAR) / vbAR > 0.25) {
        viewBox.push({ vbAspect: +vbAR.toFixed(2), renderedAspect: +renAR.toFixed(2) });
      }
    }
  }

  // 5. clipping — an animating element inside an overflow:hidden ancestor.
  for (const el of document.querySelectorAll("*")) {
    if (typeof el.getAnimations !== "function") break;
    if (el.getAnimations().length === 0) continue;
    let n = el.parentElement;
    while (n && n !== document.documentElement) {
      if (getComputedStyle(n).overflow === "hidden") {
        const er = el.getBoundingClientRect(), nr = n.getBoundingClientRect();
        if (er.right > nr.right + 2 || er.bottom > nr.bottom + 2 || er.left < nr.left - 2 || er.top < nr.top - 2)
          clipping.push({ cls: (el.className && el.className.toString().slice(0, 40)) || "" });
        break;
      }
      n = n.parentElement;
    }
  }

  // 6. density — painted-coverage estimate over the viewport. Sample a grid; a
  //    cell "has ink" if elementFromPoint is something other than the page bg
  //    chrome. Catches the "blank mid-screen on desktop" failure (#8). This is a
  //    heuristic the agent must EYEBALL, not an auto-fail (poster negative space).
  const W = window.innerWidth, H = window.innerHeight;
  const cols = 16, rows = 12;
  let inked = 0, total = 0;
  const rootBg = getComputedStyle(document.body).backgroundColor;
  for (let i = 0; i < cols; i++) for (let j = 0; j < rows; j++) {
    const x = ((i + 0.5) / cols) * W, y = ((j + 0.5) / rows) * H;
    const el = document.elementFromPoint(x, y);
    total++;
    if (!el) continue;
    const cs = getComputedStyle(el);
    const hasText = (el.textContent || "").trim().length > 0 && el.children.length === 0;
    const hasBg = parseRGB(cs.backgroundColor) && cs.backgroundColor !== rootBg && parseRGB(cs.backgroundColor).a > 0.05;
    const isSvgInk = el.closest("svg") && el.tagName.toLowerCase() !== "svg";
    if (hasText || hasBg || isSvgInk || el.tagName === "IMG" || el.tagName === "BUTTON" || el.tagName === "INPUT") inked++;
  }
  const inkRatio = total ? +(inked / total).toFixed(3) : 0;

  return {
    url: location.href,
    viewport: { w: W, h: H },
    overflow, contrast, animations, viewBox, clipping,
    density: { inkRatio, inkedCells: inked, totalCells: total },
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Node-side grader. Turns a probe report into blocking / warning lists.
// ─────────────────────────────────────────────────────────────────────────────
function grade(report) {
  const blocking = [];
  const warnings = [];

  for (const c of report.contrast || [])
    blocking.push(`CONTRAST ${c.ratio}:1 (<${c.min}) "${c.text}" — ${c.fg} on ${c.bg}`);

  for (const o of report.overflow || []) {
    const msg = `OVERFLOW ${o.tag}.${o.cls} by ${o.over.x}x${o.over.y}px${o.katex ? " [KaTeX]" : ""}`;
    (o.katex || o.over.x > 0 ? blocking : warnings).push(msg); // horizontal/KaTeX overflow = blocking
  }

  for (const v of report.viewBox || [])
    blocking.push(`SVG SQUASH viewBox aspect ${v.vbAspect} vs rendered ${v.renderedAspect} (>25% off)`);

  for (const c of report.clipping || [])
    blocking.push(`CLIPPED animation in overflow:hidden — ${c.cls}`);

  for (const a of report.animations || [])
    if (a.fill !== "forwards" && a.fill !== "both")
      warnings.push(`ANIM "${a.name}" ${a.state}, fill=${a.fill} (may snap back — capture at rest, verify fill)`);

  const ink = report.density?.inkRatio ?? 1;
  if (ink < 0.18) warnings.push(`SPARSE inkRatio=${ink} (<0.18) — likely blank mid-screen; LOOK: composed negative space or accidental emptiness?`);
  if (ink > 0.85) warnings.push(`DENSE inkRatio=${ink} (>0.85) — likely cramped; LOOK for breathing room.`);

  return { url: report.url, viewport: report.viewport, pass: blocking.length === 0, blocking, warnings };
}

// ─────────────────────────────────────────────────────────────────────────────
const mode = process.argv[2];
if (mode === "--emit-probe") {
  // Print the probe as an IIFE-returning expression to paste into browser_evaluate.
  process.stdout.write(`(${DIAGNOSTIC_PROBE.toString()})()\n`);
} else if (mode === "--grade") {
  const path = process.argv[3];
  if (!path) { console.error("usage: self-see.mjs --grade <probe-output.json>"); process.exit(2); }
  const report = JSON.parse(readFileSync(path, "utf8"));
  console.log(JSON.stringify(grade(report), null, 2));
} else {
  console.error("usage: self-see.mjs --emit-probe | --grade <probe-output.json>");
  process.exit(2);
}

export { DIAGNOSTIC_PROBE, grade };
