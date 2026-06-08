import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PlacementQuestion } from "./PlacementQuestion";

const FIXTURE = {
  id: "q1",
  promptRo: "Care este formula EWMA?",
  options: ["E(t) = α·x(t) + (1-α)·E(t-1)", "E(t) = x(t) / n", "E(t) = Σx / n", "E(t) = x(t)·n"],
};

describe("PlacementQuestion", () => {
  it("renders the placement-question testid", () => {
    render(
      <PlacementQuestion
        question={FIXTURE}
        onAnswer={vi.fn()}
        disabled={false}
      />,
    );
    expect(screen.getByTestId("placement-question")).toBeInTheDocument();
  });

  it("renders the question prompt", () => {
    render(
      <PlacementQuestion question={FIXTURE} onAnswer={vi.fn()} disabled={false} />,
    );
    expect(screen.getByText(/EWMA/)).toBeInTheDocument();
  });

  it("renders all answer options", () => {
    render(
      <PlacementQuestion question={FIXTURE} onAnswer={vi.fn()} disabled={false} />,
    );
    FIXTURE.options.forEach((opt) => {
      expect(screen.getByText(opt)).toBeInTheDocument();
    });
  });

  it("calls onAnswer with the selected option index when an option is clicked", () => {
    const onAnswer = vi.fn();
    render(
      <PlacementQuestion question={FIXTURE} onAnswer={onAnswer} disabled={false} />,
    );
    fireEvent.click(screen.getAllByRole("button")[0]);
    expect(onAnswer).toHaveBeenCalledWith(0);
  });

  it("does not call onAnswer when disabled", () => {
    const onAnswer = vi.fn();
    render(
      <PlacementQuestion question={FIXTURE} onAnswer={onAnswer} disabled={true} />,
    );
    fireEvent.click(screen.getAllByRole("button")[0]);
    expect(onAnswer).not.toHaveBeenCalled();
  });
});
