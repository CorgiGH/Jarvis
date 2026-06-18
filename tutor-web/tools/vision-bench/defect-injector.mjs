/**
 * defect-injector.mjs — apply a catalogued defect to a rendered bench figure (spec §3.0 #2, §3.2, T3).
 *
 * TWO modes, prop-first (spec §3.1/§3.2 layer-1 default → layer-2 serialized source fallback):
 *
 *   • PROP mode (default, ADDITIVE, ZERO permanent source change):
 *       The defect is applied as a RENDER-TIME TRANSFORM in the page, AFTER the family paints, by
 *       mutating the live SVG DOM keyed off the stable data-* stamps the families already emit
 *       (data-cell-index, data-pivot-row/-col, data-cell-fill, data-node-id, data-cd*, the value
 *       <text>). This is the spec's "additive, never changes the DEFAULT render, parallel-safe" prop
 *       channel realized WITHOUT a `?defect=` source edit — see DESIGN NOTE below for why the DOM-
 *       transform realization is used in this landmine tree instead of a wired URL query param.
 *       It is gated to the bench's own page context (it never runs in the shipped bundle), it is
 *       purely additive (no `?defect` → identity), and it evaporates on reload (no revert needed).
 *
 *   • SOURCE mode (fallback for structural defects DOM-mutation can't faithfully express, AND the
 *       md5 ground-truth for T4 fidelity): write a single-file working-tree patch → wait for Vite HMR
 *       to settle → (caller captures) → `git checkout -- <ONE path>` → assert `git status --porcelain
 *       <that path>` is clean. NEVER `git checkout .` / `git clean` / `git add -A` (the tree carries
 *       ~185 untracked door/demo files — the R-MUTABLE-TREE landmine). A global mutex serializes
 *       source edits so two are never in flight and a prop capture never interleaves a source window.
 *
 * DESIGN NOTE (recorded as a controlled deviation from the literal T3 wording):
 *   T3 as written asks for a DEV-only `?defect=<id>` URL channel WIRED INTO each of the 5 family /
 *   demo mounts. Wiring that requires permanently editing 5 currently-CLEAN tracked source files
 *   (the families) or the one shared AlgoStepperShell. In a working tree with ~185 untracked
 *   landmines and a hard "one source edit at a time / targeted revert only" rule, a permanent multi-
 *   file source seam is the riskiest possible move. The DOM-render-transform realization achieves the
 *   SAME three guarantees the spec demands of the prop channel — (1) additive, (2) DEV/bench-only
 *   (never in the shipped bundle), (3) never perturbs the default render — with ZERO source mutation.
 *   Source mode (which DOES touch one file, transiently, with a targeted revert) remains available as
 *   the fidelity ground-truth and for the residual structural defects. This is surfaced as a BLOCKER
 *   in the structured output so a PM can ratify or ask for the URL-param seam instead.
 *
 * Exports:
 *   applyPropDefect(svgLocator, defectId)  → { applied:boolean, mutated:number, note }   (in-page DOM transform)
 *   injectDefect({ defect, capture })       → { framePngPaths, label, restored:true }      (mode dispatch, T3 contract)
 *   withSourceDefect(patch, fn)             → result of fn   (file-lock + targeted revert wrapper)
 *   DEFECT_DOM_TRANSFORMS                    → the prop-mode transform table (defectId → fn body string)
 */

import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

// Repo root = three levels up from tutor-web/tools/vision-bench/.
const REPO_ROOT = resolve(new URL(".", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"), "../../..");

// ── PROP-MODE TRANSFORM TABLE ─────────────────────────────────────────────────────────────────────
// Each entry is a function run IN THE PAGE against the figure's <svg.algo-stepper-shell-svg> element.
// It returns the number of DOM nodes it mutated (0 ⇒ the defect was a no-op on THIS frame, which the
// label-audit (§8/T7) will flag — a no-op frame is GOOD-looking, not BAD). The transforms key ONLY off
// the stable stamps the families emit, so they are robust to the line drift the catalog records.
//
// IMPORTANT: these are *visual* sabotages chosen to MATCH the catalogued defect's pixel intent, so the
// vision judge sees the same wrongness a source edit would produce. They are NOT required to be byte-
// identical to source mode for every defect — T4 measures which ARE byte-identical and demotes the rest
// to source-only in the catalog.
export const DEFECT_DOM_TRANSFORMS = {
  // ── seq-array ──
  // duplicated-value-token: paint a duplicate digit — copy cell 0's value <text> content onto cell 1.
  "duplicated-value-token": (svg) => {
    const cells = svg.querySelectorAll("[data-cell-index] text");
    if (cells.length < 2) return 0;
    cells[1].textContent = cells[0].textContent;
    return 1;
  },
  // missing-sorted-prefix-paint: strip the sorted-prefix INK/accent fill — repaint any non-white cell rect white.
  "missing-sorted-prefix-paint": (svg) => {
    let n = 0;
    for (const r of svg.querySelectorAll("[data-cell-index] rect")) {
      const f = (r.getAttribute("fill") || "").toLowerCase();
      if (f && f !== "#fff" && f !== "#ffffff" && f !== "white" && f !== "none") {
        r.setAttribute("fill", "#ffffff");
        n++;
      }
    }
    return n;
  },
  // ── matrix-grid ──
  // pivot-highlight-wrong-cell: move the pivot stroke from the real pivot cell to its right neighbour.
  "pivot-highlight-wrong-cell": (svg) => {
    const cells = Array.from(svg.querySelectorAll("[data-cell-index] rect, g[transform] rect"));
    // pivot rect = the one with the thickest stroke; bump the next sibling rect's stroke instead.
    let pivot = null,
      maxW = 0;
    for (const r of cells) {
      const w = parseFloat(r.getAttribute("stroke-width") || "0");
      if (w > maxW) {
        maxW = w;
        pivot = r;
      }
    }
    if (!pivot || maxW < 2) return 0;
    const idx = cells.indexOf(pivot);
    const next = cells[idx + 1] || cells[idx - 1];
    if (!next) return 0;
    pivot.setAttribute("stroke-width", "1");
    next.setAttribute("stroke-width", String(maxW));
    next.setAttribute("stroke", pivot.getAttribute("stroke") || "#000");
    return 1;
  },
  // mis-colored-pivot-blends-into-grid: neutralize the pivot's distinguishing stroke (thin it to grid).
  "mis-colored-pivot-blends-into-grid": (svg) => {
    let n = 0;
    for (const r of svg.querySelectorAll("rect")) {
      const w = parseFloat(r.getAttribute("stroke-width") || "0");
      if (w >= 2) {
        r.setAttribute("stroke-width", "1");
        n++;
      }
    }
    return n;
  },
  // ── graph-tree ──
  // duplicated-value-across-nodes: copy node 0's label text onto node 1.
  "duplicated-value-across-nodes": (svg) => {
    const labels = svg.querySelectorAll("[data-node-id] text");
    if (labels.length < 2) return 0;
    labels[1].textContent = labels[0].textContent;
    return 1;
  },
  // mis-colored-highlight-frontier: drop the highlight ring/accent on every node (repaint stroke grey).
  "mis-colored-highlight-frontier": (svg) => {
    let n = 0;
    for (const r of svg.querySelectorAll("[data-node-id] rect")) {
      const s = (r.getAttribute("stroke") || "").toLowerCase();
      if (s && s !== "#333333" && s !== "#333" && s !== "none") {
        r.setAttribute("stroke", "#333333");
        n++;
      }
    }
    return n;
  },
  // ── chart-dist ──
  // shade-mis-colored-blends-into-curve: make the shade nearly invisible (fillOpacity → 0.02).
  "shade-mis-colored-blends-into-curve": (svg) => {
    const shade = svg.querySelector('[data-cd="shade"]');
    if (!shade) return 0;
    shade.setAttribute("fill-opacity", "0.02");
    return 1;
  },
  // y-axis-or-zero-tick-missing: remove the y-axis line.
  "y-axis-or-zero-tick-missing": (svg) => {
    const ax = svg.querySelector('[data-cd="axis-y"]');
    if (!ax) return 0;
    ax.remove();
    return 1;
  },
  // ab-badges-swapped: swap the x of the 'a' and 'b' badge <text> glyphs.
  "ab-badges-swapped": (svg) => {
    const texts = Array.from(svg.querySelectorAll("text")).filter((t) => {
      const c = (t.textContent || "").trim();
      return c === "a" || c === "b";
    });
    if (texts.length < 2) return 0;
    const ax = texts[0].getAttribute("x");
    texts[0].setAttribute("x", texts[1].getAttribute("x"));
    texts[1].setAttribute("x", ax);
    return 1;
  },
  // ── sort-merge ──
  // value-dup-missing: copy token 0's value glyph onto token 1 (a visible duplicate in the painted row).
  "value-dup-missing": (svg) => {
    const toks = svg.querySelectorAll("[data-cell-index] text");
    if (toks.length < 2) return 0;
    toks[1].textContent = toks[0].textContent;
    return 1;
  },
  // output-not-accent: repaint the accent (output) cells to a neutral body fill.
  "output-not-accent": (svg) => {
    let n = 0;
    for (const r of svg.querySelectorAll("rect")) {
      const f = (r.getAttribute("fill") || "").toLowerCase();
      if (f === "#fde047") {
        r.setAttribute("fill", "#ffffff");
        n++;
      }
    }
    return n;
  },
};

/**
 * Apply a prop-mode (DOM-transform) defect to a live figure SVG. Runs the registered transform IN the
 * page. Returns { applied, mutated, note }. `applied:false` means there is no DOM transform for this
 * defect id → the caller must fall back to source mode.
 *
 * @param {import('playwright-core').Locator} svgLocator  the svg.algo-stepper-shell-svg locator
 * @param {string} defectId
 * @returns {Promise<{applied:boolean, mutated:number, note:string}>}
 */
export async function applyPropDefect(svgLocator, defectId) {
  const fn = DEFECT_DOM_TRANSFORMS[defectId];
  if (!fn) return { applied: false, mutated: 0, note: `no prop transform for '${defectId}' → source mode` };
  // Ship the transform body to the page (Function#toString) and run it against the svg element.
  const mutated = await svgLocator.evaluate((svgEl, body) => {
    // eslint-disable-next-line no-new-func
    const f = new Function("svg", `return (${body})(svg);`);
    return f(svgEl) | 0;
  }, fn.toString());
  return { applied: true, mutated, note: mutated > 0 ? "ok" : "no-op on this frame (label-audit will flag)" };
}

// ── SOURCE-MODE: global mutex + targeted patch + targeted revert ────────────────────────────────────
let _sourceLock = Promise.resolve();

/**
 * Serialize a source-edit window behind the process-global mutex, run `fn` (which does the capture),
 * then ALWAYS restore the single edited file with a TARGETED `git checkout -- <path>` and assert the
 * path is porcelain-clean. NEVER touches any path other than `patch.file`.
 *
 * @param {{file:string, find:string|RegExp, replace:string}} patch  single-file string patch (absolute or repo-relative file)
 * @param {() => Promise<any>} fn  capture work to run while the patch is live
 * @returns {Promise<{result:any, restored:true}>}
 */
export async function withSourceDefect(patch, fn) {
  const release = _acquire();
  try {
    return await release.then(() => _doSourceDefect(patch, fn));
  } finally {
    release.done();
  }
}

function _acquire() {
  let doneFn;
  const prev = _sourceLock;
  const gate = new Promise((res) => (doneFn = res));
  _sourceLock = gate;
  const ready = prev.then(() => {});
  ready.done = doneFn;
  return ready;
}

async function _doSourceDefect(patch, fn) {
  const file = resolve(REPO_ROOT, patch.file);
  if (!existsSync(file)) throw new Error(`[defect-injector] source-mode target does not exist: ${file}`);
  const rel = _relToRepo(file);

  // Pre-assert the file is clean — never patch a file that already has uncommitted edits (we could not
  // safely revert it without clobbering the user's work).
  const beforeStatus = _git(["status", "--porcelain", "--", rel]).stdout.trim();
  if (beforeStatus) {
    throw new Error(
      `[defect-injector] refusing source-mode: '${rel}' is already dirty (${JSON.stringify(beforeStatus)}). ` +
        `Source mode only patches a CLEAN tracked file so the targeted revert is safe.`,
    );
  }

  const original = readFileSync(file, "utf8");
  let patched;
  if (patch.find instanceof RegExp) {
    if (!patch.find.test(original)) throw new Error(`[defect-injector] source patch regex did not match in ${rel}`);
    patched = original.replace(patch.find, patch.replace);
  } else {
    if (!original.includes(patch.find)) throw new Error(`[defect-injector] source patch find-string absent in ${rel}: ${patch.find.slice(0, 60)}`);
    patched = original.split(patch.find).join(patch.replace);
  }
  if (patched === original) throw new Error(`[defect-injector] source patch was a no-op in ${rel}`);

  let result;
  try {
    writeFileSync(file, patched);
    // Let Vite HMR pick up the edit + the page re-render. The caller's capture has its own SETTLE_MS;
    // this is the file-watch → module-reload settle.
    await _sleep(900);
    result = await fn();
  } finally {
    // TARGETED single-file revert — scoped path only, never `.` and never `git clean`.
    _git(["checkout", "--", rel]);
    const afterStatus = _git(["status", "--porcelain", "--", rel]).stdout.trim();
    if (afterStatus) {
      throw new Error(`[defect-injector] FAILED to restore '${rel}' (still dirty after targeted revert: ${afterStatus}).`);
    }
  }
  return { result, restored: true };
}

function _relToRepo(abs) {
  return abs.replace(REPO_ROOT.replace(/\\/g, "/"), "").replace(/^[/\\]+/, "");
}

function _git(args) {
  const r = spawnSync("git", args, { cwd: REPO_ROOT, encoding: "utf8" });
  if (r.status !== 0 && args[0] !== "status") {
    throw new Error(`[defect-injector] git ${args.join(" ")} failed (${r.status}): ${r.stderr}`);
  }
  return r;
}

const _sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * T3 top-level contract: inject a defect (mode chosen by `defect.injection.mode`) and capture it.
 * For prop mode, `capture` is a closure that (a) opens the figure, (b) is handed the svg locator to
 * run the prop transform, (c) screenshots, returning the PNG path(s). For source mode the patch is
 * applied around `capture`.
 *
 * @param {object} a
 * @param {object} a.defect      a defect-catalog entry (must carry injection:{mode, ...})
 * @param {(applyTransform:(svg)=>Promise<any>)=>Promise<string[]>} a.capture
 * @returns {Promise<{framePngPaths:string[], label:string, restored:true}>}
 */
export async function injectDefect({ defect, capture }) {
  const mode = defect?.injection?.mode || "prop";
  if (mode === "prop") {
    const framePngPaths = await capture((svg) => applyPropDefect(svg, defect.id));
    return { framePngPaths, label: defect.label || "BAD", restored: true };
  }
  if (mode === "source") {
    const patch = defect.injection.patch;
    if (!patch) throw new Error(`[defect-injector] defect '${defect.id}' is source-mode but carries no injection.patch`);
    const { result } = await withSourceDefect(patch, () => capture(async () => 0 /* no DOM transform in source mode */));
    return { framePngPaths: result, label: defect.label || "BAD", restored: true };
  }
  throw new Error(`[defect-injector] unknown injection mode '${mode}' for defect '${defect.id}'`);
}
