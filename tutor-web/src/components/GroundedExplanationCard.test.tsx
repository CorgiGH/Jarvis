import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { GroundedExplanationCard } from "./GroundedExplanationCard";
import * as teaching from "../lib/teaching";

beforeEach(() => { vi.restoreAllMocks(); });

describe("GroundedExplanationCard", () => {
  it("fetches /teaching/{kcId} and renders explanation + worked example when faithful", async () => {
    const spy = vi.spyOn(teaching, "getTeaching").mockResolvedValue({
      kcId: "kc-1", name_ro: "Recursie",
      explanation_ro: "O funcție definită prin ea însăși.",
      worked_example_ro: "fib(5)=5",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
    });
    render(<GroundedExplanationCard kcId="kc-1" />);
    await waitFor(() => expect(screen.getByTestId("grounded-explanation-card")).toBeInTheDocument());
    expect(spy).toHaveBeenCalledWith("kc-1");
    expect(screen.getByTestId("grounded-explanation-card"))
      .toHaveTextContent("O funcție definită prin ea însăși.");
    // Mutual-exclusion contract: the faithful card carries the faithful marker, never the generated one.
    expect(screen.getByTestId("grounded-explanation-card")).toHaveAttribute("data-faithful", "true");
  });

  it("renders nothing when /teaching returns null (404 non-faithful gate)", async () => {
    vi.spyOn(teaching, "getTeaching").mockResolvedValue(null);
    const { container } = render(<GroundedExplanationCard kcId="kc-x" />);
    await waitFor(() => expect(teaching.getTeaching).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="grounded-explanation-card"]')).toBeNull();
  });

  it("renders nothing when kcId is undefined (no fetch)", () => {
    const spy = vi.spyOn(teaching, "getTeaching");
    const { container } = render(<GroundedExplanationCard kcId={undefined} />);
    expect(spy).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });

  it("never renders when provenance is not faithful-checked (defensive double-gate)", async () => {
    vi.spyOn(teaching, "getTeaching").mockResolvedValue({
      kcId: "kc-1", name_ro: "X", explanation_ro: "x", worked_example_ro: null,
      provenance: { type: "authored", hasBeenFaithfulChecked: false },
    });
    const { container } = render(<GroundedExplanationCard kcId="kc-1" />);
    await waitFor(() => expect(teaching.getTeaching).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="grounded-explanation-card"]')).toBeNull();
  });
});
