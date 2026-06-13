/**
 * Plan-V family 4 — Seeded wrong-trace drill (INV-9.4 gate-3 seed), §0.9C.
 *
 * Runs the SAME harness assertion function from traceMatchHarness.test.tsx over the committed
 * known-bad chart-dist fixture and asserts it THROWS naming the offending step. The fixture's
 * declared probability (0.95) contradicts the area under its own curve (0.64), so the oracle's
 * independent integration trips at the annotation beat (reveal 4 = step 3, 0-indexed).
 *
 * Assert-red test: if the harness does NOT throw, gate-3 is dead (it would pass a chart whose
 * annotation teaches a wrong probability) — that makes THIS test fail (CI red). If the harness
 * throws as expected, this test passes (gate 3 is alive for the chart family).
 *
 * The fixture lives under __tests__/fixtures/ — NEVER under content/ so ContentRepo + the totality
 * harness glob cannot see it.
 */

import { describe, it, expect } from "vitest";
import wrongChartYamlRaw from "./fixtures/seeded-wrong-chart-dist.yaml?raw";
import { assertHarnessForInstance } from "./traceMatchHarness.test";

describe("seededWrongChartDist — INV-9.4 gate-3 drill (chart-dist)", () => {
  it("harness THROWS on the wrong-probability fixture naming the offending step index", () => {
    expect(() =>
      assertHarnessForInstance(wrongChartYamlRaw, "seeded-wrong-chart-dist.yaml"),
    ).toThrow(/step 3/);
  });

  it("the error message names a trace/probability mismatch (not a parse or missing-family error)", () => {
    let thrown: Error | null = null;
    try {
      assertHarnessForInstance(wrongChartYamlRaw, "seeded-wrong-chart-dist.yaml");
    } catch (e) {
      thrown = e as Error;
    }
    expect(thrown, "Expected harness to throw but it did not").not.toBeNull();
    expect(thrown!.message).toMatch(/step 3/);
    expect(thrown!.message).toMatch(/probability|trace mismatch|∫curve/i);
  });
});
