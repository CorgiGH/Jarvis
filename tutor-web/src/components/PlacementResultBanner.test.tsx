import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlacementResultBanner } from "./PlacementResultBanner";

const FIXTURE_RESULT = {
  score: 6,
  total: 8,
  recommendation: "Nivelul tău estimat: intermediar. Vom începe cu lecții adaptate.",
};

describe("PlacementResultBanner", () => {
  it("renders the placement-result-banner testid", () => {
    render(
      <PlacementResultBanner result={FIXTURE_RESULT} onContinue={vi.fn()} />,
    );
    expect(screen.getByTestId("placement-result-banner")).toBeInTheDocument();
  });

  it("displays the score", () => {
    render(
      <PlacementResultBanner result={FIXTURE_RESULT} onContinue={vi.fn()} />,
    );
    const el = screen.getByTestId("placement-result-banner");
    expect(el.textContent).toContain("6");
    expect(el.textContent).toContain("8");
  });

  it("displays the recommendation text", () => {
    render(
      <PlacementResultBanner result={FIXTURE_RESULT} onContinue={vi.fn()} />,
    );
    expect(screen.getByText(/nivelul tău/i)).toBeInTheDocument();
  });

  it("calls onContinue when the continue button is clicked", () => {
    const onContinue = vi.fn();
    render(
      <PlacementResultBanner result={FIXTURE_RESULT} onContinue={onContinue} />,
    );
    fireEvent.click(screen.getByRole("button", { name: /continuă|start|începe/i }));
    expect(onContinue).toHaveBeenCalled();
  });
});
