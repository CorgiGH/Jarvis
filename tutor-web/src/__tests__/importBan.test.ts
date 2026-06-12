import { describe, it, expect } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * Plan-3 INV-5.4 (spec §5.6) — SVG-only policy, machine-enforced over the LESSON-FACING dirs.
 * BLOCK three.js / WebGL / WebGPU / canvas-based FIGURES everywhere a learner sees a figure.
 *
 * Scope = lesson + family render code only (the bare word "three"/"canvas" appears in unrelated
 * comments across src/, recon-verified — so we match IMPORT specifiers + canvas-figure APIs, not words).
 * Allowlist = two legacy non-lesson primitives that use an off-DOM canvas for measurement ONLY
 * (not a rendered figure); they are outside the lesson path and predate the family system.
 */
const LESSON_DIRS = [
  "src/components/lesson",
  "src/components/viz/families",
];

// Exact-path allowlist (legacy, non-lesson; SVG figures, canvas used for text measurement only).
const ALLOWLIST = new Set<string>([
  "src/components/viz/SumPlotTracker.tsx",
  "src/components/viz/NumLineDirect.tsx",
]);

// import specifiers that pull a GPU/canvas renderer:
const BANNED_IMPORT = /\bfrom\s+['"](three|three\/.*|@react-three\/.*|.*webgl.*|.*webgpu.*|regl|pixi\.js)['"]/i;
// canvas-FIGURE APIs (rendering, not off-DOM measureText):
const BANNED_CANVAS = /<canvas[\s>]|getContext\(\s*['"](webgl|webgl2|webgpu)['"]/i;

function walk(dir: string, acc: string[] = []): string[] {
  let entries: string[] = [];
  try { entries = readdirSync(dir); } catch { return acc; }
  for (const e of entries) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (/\.(ts|tsx)$/.test(e) && !/\.(test|spec)\.tsx?$/.test(e)) acc.push(p);
  }
  return acc;
}

describe("INV-5.4 import ban (lesson-facing SVG-only)", () => {
  it("no lesson/family source imports three/webgl/webgpu or uses a canvas figure", () => {
    const offenders: string[] = [];
    for (const d of LESSON_DIRS) {
      for (const file of walk(d)) {
        const rel = file.replace(/\\/g, "/");
        if (ALLOWLIST.has(rel)) continue;
        const src = readFileSync(file, "utf8");
        if (BANNED_IMPORT.test(src)) offenders.push(`${rel}: banned renderer import`);
        if (BANNED_CANVAS.test(src)) offenders.push(`${rel}: canvas-figure API`);
      }
    }
    expect(offenders, `SVG-only policy violations:\n${offenders.join("\n")}`).toEqual([]);
  });

  it("the allowlisted files exist (a stale allowlist entry is itself a smell)", () => {
    for (const p of ALLOWLIST) {
      expect(() => statSync(p), `${p} (allowlist entry) must exist`).not.toThrow();
    }
  });
});
