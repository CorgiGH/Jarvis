import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";

/**
 * Plan 4a Task 1 — fonts are self-hosted, not CDN-injected (§0.9A).
 * (a) index.css declares the three @font-face families served from /tutor/fonts/.
 * (b) DoorWarm no longer injects a fonts.googleapis stylesheet at runtime.
 * Reading the source text (not the rendered DOM) keeps this deterministic and CI-cheap.
 */
const ROOT = resolve(__dirname, "..", "..");           // tutor-web/
const css = readFileSync(resolve(ROOT, "src/index.css"), "utf8");
const doorWarm = readFileSync(resolve(ROOT, "src/door/DoorWarm.tsx"), "utf8");

describe("self-hosted fonts (Plan 4a §0.9A)", () => {
  it("index.css declares @font-face for JetBrains Mono, Fraunces, and Nunito", () => {
    for (const family of ["JetBrains Mono", "Fraunces", "Nunito"]) {
      const re = new RegExp(
        `@font-face\\s*{[^}]*font-family:\\s*'${family}'`,
        "s",
      );
      expect(re.test(css), `missing @font-face for ${family}`).toBe(true);
    }
  });

  it("every @font-face serves woff2 from the /tutor/fonts/ base with font-display: swap", () => {
    const faces = css.match(/@font-face\s*{[^}]*}/gs) ?? [];
    const self = faces.filter((f) => /url\('\/tutor\/fonts\//.test(f));
    expect(self.length).toBeGreaterThanOrEqual(5);
    for (const f of self) {
      expect(/font-display:\s*swap/.test(f), `font-display: swap missing in: ${f}`).toBe(true);
      expect(/\.woff2'\)\s*format\('woff2/.test(f), `not a woff2 src in: ${f}`).toBe(true);
    }
  });

  it("DoorWarm no longer injects a fonts.googleapis stylesheet", () => {
    expect(doorWarm.includes("fonts.googleapis")).toBe(false);
    expect(doorWarm.includes("door-warm-fonts")).toBe(false);
  });
});
