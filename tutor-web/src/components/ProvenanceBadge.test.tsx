import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProvenanceBadge } from "./ProvenanceBadge";

describe("ProvenanceBadge", () => {
  it("renders the honest 'AI practice — not checked' badge for generated content", () => {
    render(<ProvenanceBadge provenance={{ type: "generated", hasBeenFaithfulChecked: false }} />);
    const b = screen.getByTestId("provenance-badge");
    expect(b).toHaveTextContent(/AI practice — not checked against your lecture/i);
    expect(b).toHaveAttribute("data-provenance-type", "generated");
    expect(b).toHaveAttribute("data-faithful", "false");
  });

  it("renders nothing for authored content (the faithful path owns that badge)", () => {
    const { container } = render(
      <ProvenanceBadge provenance={{ type: "authored", hasBeenFaithfulChecked: true }} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when provenance is null/undefined", () => {
    const { container } = render(<ProvenanceBadge provenance={null} />);
    expect(container.firstChild).toBeNull();
  });
});
