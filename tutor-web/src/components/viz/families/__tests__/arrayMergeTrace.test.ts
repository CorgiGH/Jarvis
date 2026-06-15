/**
 * Plan-V family (sort-merge) — oracle unit tests for arrayMergeTrace.ts.
 *
 * These are the load-bearing proofs that the merge-sort figure ACTUALLY SORTS (the defect the legacy
 * graph-tree figure has): the final frame's array is the sorted seed, and the multiset is conserved
 * on every single frame (no number invented or dropped mid-merge). Run independently of the
 * rendered-DOM harness so a regression in the oracle reds fast and clearly.
 */

import { describe, it, expect } from "vitest";
import { arrayMergeReference, arrayMergeOutput } from "./arrayMergeTrace";

const SEED = [5, 2, 8, 1, 9, 3]; // the shipped instance seed (familiar 6-element divide tree)

describe("arrayMergeReference — the merge-sort oracle", () => {
  it("FINAL frame's array === sorted(seed) (the figure actually sorts)", () => {
    const steps = arrayMergeReference(SEED);
    const last = steps[steps.length - 1];
    expect(last.phase).toBe("final");
    expect(last.array).toEqual(arrayMergeOutput(SEED));
    expect(last.array).toEqual([1, 2, 3, 5, 8, 9]);
  });

  it("MULTISET conserved on EVERY frame (no number invented or dropped)", () => {
    const steps = arrayMergeReference(SEED);
    const expected = [...SEED].sort((a, b) => a - b);
    for (let i = 0; i < steps.length; i++) {
      const got = [...steps[i].array].sort((a, b) => a - b);
      expect(got, `frame ${i} (phase ${steps[i].phase}) broke the multiset`).toEqual(expected);
    }
  });

  it("every frame's array length === seed length (cardinality preserved)", () => {
    for (const s of arrayMergeReference(SEED)) {
      expect(s.array.length).toBe(SEED.length);
    }
  });

  it("front pointers are always inside their run spans", () => {
    for (const s of arrayMergeReference(SEED)) {
      expect(s.frontOf.length).toBe(s.runs.length);
      s.runs.forEach((r, k) => {
        expect(s.frontOf[k]).toBeGreaterThanOrEqual(r.lo);
        expect(s.frontOf[k]).toBeLessThan(r.hi);
      });
    }
  });

  it("on take/drain frames the committed output prefix is non-decreasing (always took the smaller front)", () => {
    for (const s of arrayMergeReference(SEED)) {
      if (s.phase !== "take" && s.phase !== "drain") continue;
      const mergeLo = s.runs.length > 0 ? s.runs[0].lo - s.outFilled : s.sortedSpan.lo;
      const prefix = s.array.slice(mergeLo, mergeLo + s.outFilled);
      for (let k = 1; k < prefix.length; k++) {
        expect(prefix[k], `output prefix [${prefix.join(",")}] not sorted`).toBeGreaterThanOrEqual(prefix[k - 1]);
      }
    }
  });

  it("generalizes to other distinct-integer seeds (final sorts, multiset conserved)", () => {
    for (const seed of [[3, 1, 2], [9, 7, 5, 3, 1], [4, 8, 2, 6, 1, 9, 3, 7]]) {
      const steps = arrayMergeReference(seed);
      const last = steps[steps.length - 1];
      expect(last.phase).toBe("final");
      expect(last.array).toEqual([...seed].sort((a, b) => a - b));
      const expectedMs = [...seed].sort((a, b) => a - b);
      for (const s of steps) {
        expect([...s.array].sort((a, b) => a - b)).toEqual(expectedMs);
      }
    }
  });
});
