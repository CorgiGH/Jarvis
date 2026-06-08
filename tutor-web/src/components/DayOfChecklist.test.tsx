import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DayOfChecklist } from "./DayOfChecklist";

describe("DayOfChecklist", () => {
  it("renders the checklist container", () => {
    render(<DayOfChecklist />);
    expect(screen.getByTestId("day-of-checklist")).toBeInTheDocument();
  });

  it("renders all FII checklist items", () => {
    render(<DayOfChecklist />);
    const items = screen.getAllByRole("checkbox");
    expect(items.length).toBeGreaterThanOrEqual(3);
  });

  it("allows checking an item", () => {
    render(<DayOfChecklist />);
    const [first] = screen.getAllByRole("checkbox");
    expect(first).not.toBeChecked();
    fireEvent.click(first);
    expect(first).toBeChecked();
  });

  it("shows Romanian labels on checklist items", () => {
    render(<DayOfChecklist />);
    const el = screen.getByTestId("day-of-checklist");
    // Must have Romanian content
    expect(el.textContent).toMatch(/legitimație|pix|apă|odihnit/i);
  });
});
