/**
 * Plan-V family 3 — Seeded wrong-trace drill (INV-9.4 gate-3 seed), §0.9C.
 *
 * Runs the SAME harness assertion function from traceMatchHarness.test.tsx over the committed
 * known-bad matrix-grid fixture and asserts it THROWS naming the offending step. The fixture's step 4
 * writes dp[4]="4", but the real fib recurrence gives dp[4]=dp[3]+dp[2]=2+1=3. matrixGridReference
 * re-runs the recurrence; the materialized grid[0][4] becomes "4" → trace mismatch at step 4 (table).
 *
 * Assert-red test: if the harness does NOT throw, gate-3 is dead — that makes THIS test fail (CI red).
 * If the harness throws as expected, this test passes (gate 3 is alive for the matrix-grid family).
 *
 * The fixture lives under __tests__/fixtures/ — NEVER under content/ so ContentRepo + the totality
 * harness glob cannot see it.
 */

import { describe, it, expect } from "vitest";
import wrongYamlRaw from "./fixtures/seeded-wrong-matrix-grid.yaml?raw";
import { assertHarnessForInstance } from "./traceMatchHarness.test";

describe("seededWrongMatrixGrid — INV-9.4 gate-3 drill (matrix-grid)", () => {
  it("harness THROWS on the wrong-table fixture naming the offending step index", () => {
    expect(() =>
      assertHarnessForInstance(wrongYamlRaw, "seeded-wrong-matrix-grid.yaml"),
    ).toThrow(/step 4/);
  });

  it("the error message names a trace/table mismatch (not a parse or missing-family error)", () => {
    let thrown: Error | null = null;
    try {
      assertHarnessForInstance(wrongYamlRaw, "seeded-wrong-matrix-grid.yaml");
    } catch (e) {
      thrown = e as Error;
    }
    expect(thrown, "Expected harness to throw but it did not").not.toBeNull();
    expect(thrown!.message).toMatch(/step 4/);
    expect(thrown!.message).toMatch(/trace mismatch|table/i);
  });
});
