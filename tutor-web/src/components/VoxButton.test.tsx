import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VoxButton } from "./VoxButton";

describe("VoxButton (disabled tombstone)", () => {
  it("renders with the vox-btn-disabled testid", () => {
    render(<VoxButton />);
    expect(screen.getByTestId("vox-btn-disabled")).toBeInTheDocument();
  });

  it("is always disabled", () => {
    render(<VoxButton />);
    const btn = screen.getByRole("button");
    expect(btn).toBeDisabled();
  });

  it("shows a Romanian label indicating voice is unavailable", () => {
    render(<VoxButton />);
    const el = screen.getByTestId("vox-btn-disabled");
    expect(el.textContent).toMatch(/voce|audio|indisponibil/i);
  });
});
