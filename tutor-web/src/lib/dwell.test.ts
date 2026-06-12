import { describe, it, expect } from "vitest";
import { readMs } from "./dwell";

/**
 * Plan-3 Task 5 — INV-4.2 (spec §4.8): the reading-time dwell FLOOR, ported verbatim from the demo
 * constants at build-review/viz-fork-demo/lectie.html:185-186. Formula:
 *   min(5500, max(1400, round(900 + words * 320)))
 * Boundaries from plan core §0.9E: 0 words -> 1400 (floor), 2 -> 1540, 14 -> 5380, 15 -> 5500 (cap),
 * 1000 -> 5500 (cap). Dwell is a FLOOR, never a ceiling (§4.1) — the scrubber stays learner-paced.
 */
describe("readMs (INV-4.2 dwell floor)", () => {
  it("0 words clamps to the 1400 ms floor", () => {
    expect(readMs("")).toBe(1400);
    expect(readMs("   ")).toBe(1400); // whitespace-only -> 0 words after filter(Boolean)
  });

  it("2 words -> 900 + 2*320 = 1540 ms (above the floor)", () => {
    expect(readMs("două cuvinte")).toBe(1540);
  });

  it("14 words -> 900 + 14*320 = 5380 ms (below the cap)", () => {
    const text = Array.from({ length: 14 }, (_, i) => `cuvânt${i}`).join(" ");
    expect(readMs(text)).toBe(5380);
  });

  it("15 words -> 900 + 15*320 = 5700 -> clamped to the 5500 ms cap", () => {
    const text = Array.from({ length: 15 }, (_, i) => `cuvânt${i}`).join(" ");
    expect(readMs(text)).toBe(5500);
  });

  it("1000 words clamps to the 5500 ms cap", () => {
    const text = Array.from({ length: 1000 }, (_, i) => `c${i}`).join(" ");
    expect(readMs(text)).toBe(5500);
  });

  it("collapses runs of whitespace to single word boundaries", () => {
    // two words separated by mixed whitespace -> still 2 words -> 1540
    expect(readMs("două \n\t  cuvinte")).toBe(1540);
  });
});
