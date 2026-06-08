import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TermLanding } from "./TermLanding";

describe("TermLanding", () => {
  it("renders term with accent background", () => {
    render(<TermLanding termRo="Invariant" termEn="Invariant" />);
    expect(screen.getByTestId("term-landing")).toBeInTheDocument();
    const termEl = screen.getByTestId("term-landing-term");
    expect(termEl).toHaveTextContent("Invariant");
    // accent bg class is on the element
    expect(termEl.className).toMatch(/accent|bg-accent/);
  });

  it("renders EN gloss when provided", () => {
    render(<TermLanding termRo="Nod" termEn="Node" />);
    expect(screen.getByTestId("term-landing-gloss")).toHaveTextContent("Node");
  });

  it("does not render gloss when termEn is empty", () => {
    render(<TermLanding termRo="Nod" termEn="" />);
    expect(screen.queryByTestId("term-landing-gloss")).toBeNull();
  });

  it("renders nothing when termRo is empty", () => {
    const { container } = render(<TermLanding termRo="" termEn="Node" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when termRo is whitespace only", () => {
    const { container } = render(<TermLanding termRo="  " termEn="" />);
    expect(container.firstChild).toBeNull();
  });
});
