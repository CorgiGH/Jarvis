import { describe, it, expect } from "vitest";
import { readdirSync, statSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Plan-4a Task 4 (§0.9D, spec INV-9.5 line 520) — INV-9.5 baseline-scope gate.
 *
 * Structural enforcement of the spec's "fixed shell + 2 theme reference pages ONLY" rule:
 * every PNG under e2e/visual/__screenshots__ MUST live under a directory whose name contains
 * "shell.visual", "theme-dark.visual", or "theme-light.visual" (Playwright's snapshotPathTemplate
 * nests baselines under {testFilePath}). Any other baseline — a lesson/drill/gallery spec quietly
 * growing a snapshot — REDS this gate. Runs in `npm test` (the frontend vitest job), so it gates on
 * every CI run, JVM-free.
 */
// This test lives at tutor-web/src/__tests__/; the baselines live at tutor-web/e2e/visual/__screenshots__.
const HERE = dirname(fileURLToPath(import.meta.url));
const SCREENSHOTS = join(HERE, "..", "..", "e2e", "visual", "__screenshots__");
const ALLOWED = ["shell.visual", "theme-dark.visual", "theme-light.visual"];

function allPngPaths(dir: string): string[] {
  if (!existsSync(dir)) return [];
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...allPngPaths(full));
    else if (name.endsWith(".png")) out.push(full);
  }
  return out;
}

describe("INV-9.5 visual-baseline scope", () => {
  it("every baseline PNG traces to exactly the three permitted spec files", () => {
    const pngs = allPngPaths(SCREENSHOTS);
    // The three baselines are generated in Task 4; if the dir is empty here the generation step was
    // skipped — that is itself a scope failure (the gate must guard real baselines, not nothing).
    expect(pngs.length, `no baselines found under ${SCREENSHOTS}; run e2e:visual:update`).toBeGreaterThan(0);
    const stray = pngs.filter((p) => !ALLOWED.some((a) => p.replace(/\\/g, "/").includes(a)));
    expect(stray, `baselines outside the permitted shell + 2 theme-ref specs:\n${stray.join("\n")}`).toEqual([]);
  });

  it("all three permitted baselines are present (shell + theme-dark + theme-light)", () => {
    const pngs = allPngPaths(SCREENSHOTS).map((p) => p.replace(/\\/g, "/"));
    expect(pngs.some((p) => p.includes("shell.visual")), "shell baseline missing").toBe(true);
    expect(pngs.some((p) => p.includes("theme-dark.visual")), "theme-dark baseline missing").toBe(true);
    expect(pngs.some((p) => p.includes("theme-light.visual")), "theme-light baseline missing").toBe(true);
  });
});
