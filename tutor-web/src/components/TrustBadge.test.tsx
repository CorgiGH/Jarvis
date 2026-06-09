import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TrustBadge } from "./TrustBadge";

describe("TrustBadge", () => {
  it("renders the faithful badge with PINNED 'corespunde cursului' copy", () => {
    render(<TrustBadge status="faithful" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/corespunde cursului/i);
    expect(b).toHaveAttribute("data-faithful", "true");
    // Trust-language lock survives translation: the badge NEVER claims correctness.
    expect(b).not.toHaveTextContent(/verified correct/i);
    expect(b).not.toHaveTextContent(/\bcorect\b/i);
  });

  it("renders the honest unverified badge for non-faithful statuses", () => {
    render(<TrustBadge status="uncertain" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/neverificat/i);
    expect(b).toHaveAttribute("data-faithful", "false");
  });

  it("renders nothing when status is null/undefined", () => {
    const { container } = render(<TrustBadge status={null} />);
    expect(container.firstChild).toBeNull();
  });
});
