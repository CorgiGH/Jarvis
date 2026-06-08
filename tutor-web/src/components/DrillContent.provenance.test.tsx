import { describe, it, expectTypeOf } from "vitest";
import type { DrillContent, DrillProvenance } from "./DrillStack";

describe("T0 DrillContent.provenance", () => {
  it("provenance is an optional {type, hasBeenFaithfulChecked} marker", () => {
    const authored: DrillProvenance = { type: "authored", hasBeenFaithfulChecked: true };
    const generated: DrillProvenance = { type: "generated", hasBeenFaithfulChecked: false };
    expectTypeOf(authored.type).toEqualTypeOf<"authored" | "generated">();
    void generated;
    expectTypeOf<DrillContent>().toHaveProperty("provenance");
  });
});
