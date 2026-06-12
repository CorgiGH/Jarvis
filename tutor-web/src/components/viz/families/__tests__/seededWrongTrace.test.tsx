/**
 * Plan 4b Task 2 — Seeded wrong-trace drill (INV-9.4 gate-3 seed), §0.9C.
 *
 * Runs the SAME harness assertion function from traceMatchHarness.test.tsx over the
 * committed known-bad fixture seeded-wrong-trace-mergesort.yaml and asserts it THROWS
 * naming the offending step index.
 *
 * This is an assert-red test: if the harness does NOT throw, it means gate-3 is dead
 * (it would pass a wrong-trace figure) — that makes this test FAIL (CI red).
 * If the harness throws as expected, this test PASSES (gate 3 is alive).
 *
 * The fixture lives under __tests__/fixtures/ — NEVER under content/ so ContentRepo
 * cannot see it (ContentRepo.loadVizInstances globs content/{subject}/viz/ only).
 *
 * See §0.9C and the INV-9.4 table for the authoritative contract.
 */

import { describe, it, expect } from "vitest";
import wrongTraceYamlRaw from "./fixtures/seeded-wrong-trace-mergesort.yaml?raw";
import { assertHarnessForInstance } from "./traceMatchHarness.test";

describe("seededWrongTrace — INV-9.4 gate-3 drill", () => {
  it("harness THROWS on the wrong-trace fixture naming the offending step index", () => {
    // The fixture has swapped merge deltas at step 6 (0-indexed).
    // The harness must throw with a message that contains "step 6".
    expect(() =>
      assertHarnessForInstance(wrongTraceYamlRaw, "seeded-wrong-trace-mergesort.yaml"),
    ).toThrow(/step 6/);
  });

  it("the error message names a trace mismatch (not a parse error or missing-family error)", () => {
    let thrown: Error | null = null;
    try {
      assertHarnessForInstance(wrongTraceYamlRaw, "seeded-wrong-trace-mergesort.yaml");
    } catch (e) {
      thrown = e as Error;
    }
    expect(thrown, "Expected harness to throw but it did not").not.toBeNull();
    // The message must name the step and the trace mismatch (not a structural error)
    expect(thrown!.message).toMatch(/step 6/);
    // Confirm it is a trace-mismatch error (labels mismatch), not a parse/family error
    expect(thrown!.message).toMatch(/trace mismatch|labels/i);
  });
});
