import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EchoBand } from "./EchoBand";

describe("EchoBand", () => {
  it("renders the quoted text verbatim", () => {
    render(<EchoBand text="Algoritmul Quicksort împarte lista recursiv." />);
    expect(screen.getByTestId("echo-band")).toBeInTheDocument();
    expect(screen.getByTestId("echo-band-quote")).toHaveTextContent(
      "Algoritmul Quicksort împarte lista recursiv."
    );
  });

  it("renders nothing when text is empty string", () => {
    const { container } = render(<EchoBand text="" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when text is whitespace only", () => {
    const { container } = render(<EchoBand text="   " />);
    expect(container.firstChild).toBeNull();
  });

  it("preserves full text content verbatim (no truncation)", () => {
    const longText = "Un exemplu mult mai lung care testează că textul nu este trunchiat în niciun fel.";
    render(<EchoBand text={longText} />);
    expect(screen.getByTestId("echo-band-quote")).toHaveTextContent(longText);
  });
});
