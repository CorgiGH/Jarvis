import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TrustBadge } from "./TrustBadge";

describe("TrustBadge", () => {
  it("renders the faithful badge with PINNED 'matches your lecture' copy", () => {
    render(<TrustBadge status="faithful" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/matches your lecture/i);
    expect(b).toHaveAttribute("data-faithful", "true");
    expect(b).not.toHaveTextContent(/verified correct/i);
  });

  it("renders the honest unverified badge for non-faithful statuses", () => {
    render(<TrustBadge status="uncertain" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/unverified/i);
    expect(b).toHaveAttribute("data-faithful", "false");
  });

  it("renders nothing when status is null/undefined", () => {
    const { container } = render(<TrustBadge status={null} />);
    expect(container.firstChild).toBeNull();
  });
});
