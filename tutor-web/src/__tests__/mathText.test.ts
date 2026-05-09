import { describe, expect, it } from "vitest";
import { splitMath, renderMath } from "../lib/mathText";

describe("splitMath", () => {
  it("returns single text segment when no math present", () => {
    expect(splitMath("just plain words here")).toEqual([
      { type: "text", text: "just plain words here" },
    ]);
  });

  it("splits inline math fragment", () => {
    expect(splitMath("the prob $f(x;\\mu,b)$ peaks at the mean")).toEqual([
      { type: "text", text: "the prob " },
      { type: "math", tex: "f(x;\\mu,b)", display: false },
      { type: "text", text: " peaks at the mean" },
    ]);
  });

  it("splits display math fragment", () => {
    const out = splitMath("density:\n$$f(x) = \\frac{1}{2b}e^{-|x-\\mu|/b}$$\nlooks like");
    expect(out[0]).toEqual({ type: "text", text: "density:\n" });
    expect(out[1]).toEqual({
      type: "math",
      tex: "f(x) = \\frac{1}{2b}e^{-|x-\\mu|/b}",
      display: true,
    });
    expect(out[2]).toEqual({ type: "text", text: "\nlooks like" });
  });

  it("display takes precedence over inline (so $$x$$ is one display, not two empty inlines)", () => {
    const out = splitMath("$$x^2$$");
    expect(out).toHaveLength(1);
    expect(out[0]).toEqual({ type: "math", tex: "x^2", display: true });
  });

  it("inline does not cross newlines (stray $ in code is safe)", () => {
    // The $ in "Bob owes me $50" should NOT swallow the next line's text.
    const out = splitMath("Bob owes me $50\nand also $100 more");
    // Neither fragment is single-line-safe so both should be plain text.
    // Behavior: regex requires non-newline content between $...$. "50\nand also $100"
    // crosses a newline so the FIRST $ has no closing partner before the newline.
    // Result: it's all text.
    const joined = out.map(s => s.type === "text" ? s.text : `[M:${s.type}]`).join("");
    expect(joined).toBe("Bob owes me $50\nand also $100 more");
  });

  it("handles multiple inline fragments in one line", () => {
    const out = splitMath("$a$ then $b$ then end");
    expect(out).toEqual([
      { type: "math", tex: "a", display: false },
      { type: "text", text: " then " },
      { type: "math", tex: "b", display: false },
      { type: "text", text: " then end" },
    ]);
  });
});

describe("renderMath", () => {
  it("returns HTML containing katex class", () => {
    const html = renderMath("x^2", false);
    expect(html).toContain("katex");
  });

  it("returns display-mode wrapper for display=true", () => {
    const html = renderMath("\\int_0^1 x dx", true);
    expect(html).toContain("katex-display");
  });

  it("does not throw on malformed LaTeX (throwOnError=false)", () => {
    expect(() => renderMath("\\frac{}{}", false)).not.toThrow();
  });
});
