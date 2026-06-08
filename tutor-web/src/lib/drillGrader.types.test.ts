import { describe, it, expectTypeOf } from "vitest";
import type { GradeResult, LadderRung, MisconceptionPayload, StudentConfidence } from "./drillGrader";

describe("T0 trust wire types", () => {
  it("GradeResult carries the served-but-formerly-ignored trust fields", () => {
    expectTypeOf<GradeResult>().toHaveProperty("ladder_rungs").toEqualTypeOf<LadderRung[] | undefined>();
    expectTypeOf<GradeResult>().toHaveProperty("misconception_payload");
    expectTypeOf<GradeResult>().toHaveProperty("verification_status");
    expectTypeOf<GradeResult>().toHaveProperty("self_explanation_prompt");
  });

  it("LadderRung mirrors the Kotlin wire 1:1", () => {
    const r: LadderRung = { level: 0, text: "x" };
    expectTypeOf(r.level).toEqualTypeOf<number>();
    expectTypeOf(r.text).toEqualTypeOf<string>();
  });

  it("MisconceptionPayload uses snake_case figure_spec on the wire shape", () => {
    const m: MisconceptionPayload = {
      id: "OFF_BY_ONE", refutation: "…", figure_spec: null,
      self_explanation_prompt: null, source: null,
    };
    expectTypeOf(m.figure_spec).toEqualTypeOf<string | null>();
  });

  it("StudentConfidence is the four-value enum + null", () => {
    const c: StudentConfidence = "DEFINITELY";
    expectTypeOf<StudentConfidence>().toEqualTypeOf<"DEFINITELY" | "MAYBE" | "GUESS" | "IDK">();
    void c;
  });
});
