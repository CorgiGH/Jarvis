import { describe, expect, it } from "vitest";
import { parseChips } from "../lib/chip";

describe("parseChips", () => {
  it("returns no chips for empty / chip-free reply", () => {
    expect(parseChips("just a normal reply").chips).toEqual([]);
    expect(parseChips("").chips).toEqual([]);
  });

  it("extracts a single well-formed chip and strips envelope from body", () => {
    const reply = `Here is the answer.

<chip>{"label":"Why?","prompt":"Why does this work?"}</chip>`;
    const out = parseChips(reply);
    expect(out.chips).toEqual([{ label: "Why?", prompt: "Why does this work?" }]);
    expect(out.body).toBe("Here is the answer.");
  });

  it("extracts multiple chips in document order", () => {
    const reply = `Body.
<chip>{"label":"A","prompt":"first prompt"}</chip>
<chip>{"label":"B","prompt":"second prompt"}</chip>`;
    const out = parseChips(reply);
    expect(out.chips).toHaveLength(2);
    expect(out.chips[0].label).toBe("A");
    expect(out.chips[1].label).toBe("B");
    expect(out.body).toBe("Body.");
  });

  it("drops chips with missing fields", () => {
    const reply = `<chip>{"label":"only label"}</chip>`;
    expect(parseChips(reply).chips).toEqual([]);
  });

  it("drops oversize chips (label > 40 or prompt > 200)", () => {
    const longLabel = "x".repeat(41);
    const longPrompt = "y".repeat(201);
    expect(parseChips(`<chip>{"label":"${longLabel}","prompt":"ok"}</chip>`).chips).toEqual([]);
    expect(parseChips(`<chip>{"label":"ok","prompt":"${longPrompt}"}</chip>`).chips).toEqual([]);
  });

  it("drops malformed JSON without breaking surrounding text", () => {
    const reply = `Body.
<chip>{not json}</chip>`;
    const out = parseChips(reply);
    expect(out.chips).toEqual([]);
    // Malformed envelope is still stripped from body so we don't render the
    // raw JSON to the user (consistent with the suggestedEdit/gap parsers).
    expect(out.body).toBe("Body.");
  });

  it("trims whitespace inside label/prompt", () => {
    const reply = `<chip>{"label":"  trimmed  ","prompt":"  hello  "}</chip>`;
    const out = parseChips(reply);
    expect(out.chips).toEqual([{ label: "trimmed", prompt: "hello" }]);
  });
});
