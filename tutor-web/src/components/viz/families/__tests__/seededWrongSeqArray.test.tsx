/**
 * Plan-V family 2 — Seeded wrong-trace drill (INV-9.4 gate-3 seed), §0.9C.
 *
 * Runs the SAME harness assertion function from traceMatchHarness.test.tsx over the committed
 * known-bad seq-array fixture and asserts it THROWS naming the offending step. The fixture's step 5
 * (the first swap) claims the array became [1,3,8,5,7], but selection sort on the seed [5,3,8,1,6]
 * produces [1,3,8,5,6] — the "7" is not in the seed, so the step's array is not a permutation of the
 * seed (a swap that edited a value). The selectionSortReference oracle re-runs the real algorithm and
 * the trace-match trips at step 5.
 *
 * Assert-red test: if the harness does NOT throw, gate-3 is dead (it would pass a trace whose array
 * silently mutated a value) — that makes THIS test fail (CI red). If the harness throws as expected,
 * this test passes (gate 3 is alive for the seq-array family).
 *
 * The fixture lives under __tests__/fixtures/ — NEVER under content/ so ContentRepo + the totality
 * harness glob cannot see it.
 */

import { describe, it, expect } from "vitest";
import wrongSeqArrayYamlRaw from "./fixtures/seeded-wrong-seq-array.yaml?raw";
import { assertHarnessForInstance } from "./traceMatchHarness.test";

describe("seededWrongSeqArray — INV-9.4 gate-3 drill (seq-array)", () => {
  it("harness THROWS on the wrong-array fixture naming the offending step index", () => {
    expect(() =>
      assertHarnessForInstance(wrongSeqArrayYamlRaw, "seeded-wrong-seq-array.yaml"),
    ).toThrow(/step 5/);
  });

  it("the error message names a trace/array mismatch (not a parse or missing-family error)", () => {
    let thrown: Error | null = null;
    try {
      assertHarnessForInstance(wrongSeqArrayYamlRaw, "seeded-wrong-seq-array.yaml");
    } catch (e) {
      thrown = e as Error;
    }
    expect(thrown, "Expected harness to throw but it did not").not.toBeNull();
    expect(thrown!.message).toMatch(/step 5/);
    expect(thrown!.message).toMatch(/trace mismatch|array/i);
  });
});
