import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LessonEntryBand } from "./LessonEntryBand";

describe("LessonEntryBand", () => {
  it("renders with accent-rule", () => {
    render(<LessonEntryBand subject="Probabilități și Statistică" kcNameRo="Estimator" />);
    const el = screen.getByTestId("lesson-entry-band");
    expect(el).toBeInTheDocument();
    // Band should contain the subject and kc name
    expect(el).toHaveTextContent("Probabilități și Statistică");
    expect(el).toHaveTextContent("Estimator");
  });

  it("renders subject pill and kc name", () => {
    render(<LessonEntryBand subject="PA" kcNameRo="Quicksort" />);
    expect(screen.getByTestId("lesson-entry-band")).toBeInTheDocument();
    expect(screen.getByTestId("lesson-entry-band")).toHaveTextContent("PA");
    expect(screen.getByTestId("lesson-entry-band")).toHaveTextContent("Quicksort");
  });
});
