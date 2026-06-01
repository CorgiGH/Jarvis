#!/usr/bin/env node
/**
 * Grounded-UI MECHANICAL VERIFIER (Stage ④ — ZERO LLM).
 * Reads pack.json + proposals/*.json from a run dir, checks every proposal
 * against repo reality, writes verifier-report.json. Exit 0 always; pass/fail
 * lives in the report (so a runner agent just relays it).
 *
 * Usage: node verify-proposals.mjs <runDir>
 *
 * Checks (per proposal):
 *   1. every element.primitive is a real pack primitive  (unless isNew:true / HTML:* )
 *   2. every element.tokens[] entry is a real pack token name (canonical --color-<name>/--type-<name>)
 *   3. NO raw hex anywhere in the proposal text  (#abc / #aabbcc)
 *   4. PROHIBITED denylist regex = 0 hits over the whole proposal text
 *   5. every reused primitive names a dataSource (correct-component/wrong-prop class)
 *   6. the one USABLE viz (recursion-tree) is accounted: USED or REJECTED-with-reason
 *   7. any builtButUnreachable viz referenced must carry requestWiring:true (never assume render)
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from "node:fs";
import { join } from "node:path";

const runDir = process.argv[2];
if (!runDir) { console.error("usage: verify-proposals.mjs <runDir>"); process.exit(2); }

const pack = JSON.parse(readFileSync(join(runDir, "pack.json"), "utf8"));
const primitiveNames = new Set(pack.primitives.map(p => p.name));
const tokenNames = new Set(pack.tokenNames);            // canonical --color-<name> / --type-<name>
const usableViz = new Set(pack.viz.usable);
const unreachableViz = new Set(pack.viz.builtButUnreachable);
const prohibited = pack.prohibited.map(p => new RegExp(p, "i"));
const HEX = /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\b/g;

// --- WCAG contrast: token name -> hex, then ratio. Catches the white-on-yellow
//     class: two VALID tokens that are an unreadable PAIR (the existence check
//     alone passes them). Pairs declared via element.fg + element.bg. ---
const rgbOf = (v) => {
  if (!v) return null;
  v = v.trim();
  let m = v.match(/^#([0-9a-fA-F]{3})$/);
  if (m) return m[1].split("").map(c => parseInt(c + c, 16));
  m = v.match(/^#([0-9a-fA-F]{6})$/);
  if (m) return [0, 2, 4].map(i => parseInt(m[1].slice(i, i + 2), 16));
  m = v.match(/^rgba?\(([^)]+)\)/);
  if (m) return m[1].split(",").slice(0, 3).map(n => parseInt(n.trim(), 10));
  return null;
};
// resolve each token to a literal rgb, skipping the @theme var() self-refs
const hexByToken = {};
for (const { name, value } of (pack.tokenDecls || [])) {
  if (hexByToken[name]) continue;          // first literal decl wins (:root, not @theme)
  const rgb = rgbOf(value);
  if (rgb) hexByToken[name] = rgb;
}
const lum = ([r, g, b]) => {
  const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
};
const contrastRatio = (a, b) => {
  const L1 = lum(a), L2 = lum(b);
  return (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05);
};

const propDir = join(runDir, "proposals");
const files = existsSync(propDir)
  ? readdirSync(propDir).filter(f => /^p.*\.json$/.test(f)).sort()
  : [];

const reports = files.map(f => {
  const raw = readFileSync(join(propDir, f), "utf8");
  let p; try { p = JSON.parse(raw); } catch (e) { return { file: f, pass: false, fails: ["INVALID JSON: " + e.message] }; }
  const fails = [], warns = [];
  const elements = Array.isArray(p.elements) ? p.elements : [];

  // 1 + 5: primitives real + dataSource named
  for (const el of elements) {
    const prim = el.primitive || "";
    const isNew = el.isNew === true || /^NEW:/.test(prim);
    const isHtml = /^HTML:/.test(prim);
    if (!isNew && !isHtml && !primitiveNames.has(prim))
      fails.push(`element "${el.role}": primitive "${prim}" not in pack (use exact pack name, or isNew:true, or HTML:<tag>)`);
    if (!isHtml && !el.dataSource)
      fails.push(`element "${el.role}": no dataSource named (correct-component/wrong-prop guard)`);
  }

  // 2: tokens real
  for (const el of elements)
    for (const t of (el.tokens || []))
      if (!tokenNames.has(t))
        fails.push(`element "${el.role}": token "${t}" not in pack tokenNames`);

  // 2b: CONTRAST — any element that pairs fg + bg tokens must clear WCAG AA.
  //     Two valid tokens can still be an unreadable pair (white-on-yellow).
  for (const el of elements) {
    if (!el.fg || !el.bg) continue;
    const fgRgb = hexByToken[el.fg], bgRgb = hexByToken[el.bg];
    if (!fgRgb) { fails.push(`element "${el.role}": fg token "${el.fg}" has no resolvable color`); continue; }
    if (!bgRgb) { fails.push(`element "${el.role}": bg token "${el.bg}" has no resolvable color`); continue; }
    const ratio = contrastRatio(fgRgb, bgRgb);
    const min = el.largeText === true ? 3.0 : 4.5;   // WCAG AA
    if (ratio < min)
      fails.push(`element "${el.role}": contrast ${ratio.toFixed(2)}:1 (${el.fg} on ${el.bg}) below AA ${min}:1 — unreadable pair`);
  }

  // 3: no raw hex
  const hex = raw.match(HEX);
  if (hex) fails.push(`raw hex present (use tokens): ${[...new Set(hex)].join(", ")}`);

  // 4: prohibited
  for (const re of prohibited)
    if (re.test(raw)) fails.push(`PROHIBITED term matched /${re.source}/i`);

  // 6 + 7: viz accounting
  const vizPlan = Array.isArray(p.vizPlan) ? p.vizPlan : [];
  const accounted = new Set(vizPlan.map(v => v.viz));
  for (const u of usableViz)
    if (!accounted.has(u))
      fails.push(`USABLE viz "${u}" unaccounted (must be USED or REJECTED-with-reason in vizPlan)`);
  for (const v of vizPlan) {
    if (v.status === "REJECTED" && !v.reason)
      fails.push(`viz "${v.viz}" REJECTED without reason`);
    if (unreachableViz.has(v.viz) && v.status === "USED" && v.requestWiring !== true)
      fails.push(`viz "${v.viz}" is BUILT-BUT-UNREACHABLE; USED requires requestWiring:true (never assume render)`);
  }

  return { file: f, thesis: p.thesis || "(none)", pass: fails.length === 0, fails, warns };
});

const report = {
  runDir, gitSha: pack.gitSha, surface: pack.surface,
  proposalCount: reports.length,
  allPass: reports.length > 0 && reports.every(r => r.pass),
  reports,
};
writeFileSync(join(runDir, "verifier-report.json"), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
