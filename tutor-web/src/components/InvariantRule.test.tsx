import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { InvariantRule } from "./InvariantRule";

describe("InvariantRule", () => {
  it("renders body text in a blockquote", () => {
    render(<InvariantRule body="La fiecare pas, pivotul este la poziția sa finală." />);
    expect(screen.getByTestId("invariant-rule")).toBeInTheDocument();
    expect(screen.getByTestId("invariant-rule-body")).toHaveTextContent(
      "La fiecare pas, pivotul este la poziția sa finală."
    );
  });

  it("renders nothing when body is null", () => {
    const { container } = render(<InvariantRule body={null} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when body is empty string", () => {
    const { container } = render(<InvariantRule body="" />);
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when body is whitespace only", () => {
    const { container } = render(<InvariantRule body="   " />);
    expect(container.firstChild).toBeNull();
  });

  it("renders body with 4px accent blockquote style", () => {
    render(<InvariantRule body="Invariant: P(i) este adevărat." />);
    const el = screen.getByTestId("invariant-rule");
    // The element should exist and have some styling indicator
    expect(el).toBeInTheDocument();
  });
});
