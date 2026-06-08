import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DegradedBanner } from "./DegradedBanner";

describe("DegradedBanner", () => {
  it("renders the degraded-banner testid", () => {
    render(<DegradedBanner />);
    expect(screen.getByTestId("degraded-banner")).toBeInTheDocument();
  });

  it("shows a Romanian degraded mode message", () => {
    render(<DegradedBanner />);
    const el = screen.getByTestId("degraded-banner");
    expect(el.textContent).toMatch(/mod degradat|offline|probleme|conexiune/i);
  });

  it("shows a custom message when provided", () => {
    render(<DegradedBanner message="Serviciul de verificare este temporar indisponibil." />);
    expect(
      screen.getByText("Serviciul de verificare este temporar indisponibil."),
    ).toBeInTheDocument();
  });
});
