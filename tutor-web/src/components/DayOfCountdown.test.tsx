import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DayOfCountdown } from "./DayOfCountdown";

describe("DayOfCountdown", () => {
  it("renders the countdown container", () => {
    render(<DayOfCountdown subject="PA" startAt="2026-06-09T09:00:00Z" />);
    expect(screen.getByTestId("day-of-countdown")).toBeInTheDocument();
  });

  it("displays the subject name", () => {
    render(<DayOfCountdown subject="Probabilități și Statistică" startAt="2026-06-09T09:00:00Z" />);
    const el = screen.getByTestId("day-of-countdown");
    expect(el.textContent).toContain("Probabilități și Statistică");
  });

  it("shows hours remaining when within 24h", () => {
    const future = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString();
    render(<DayOfCountdown subject="PA" startAt={future} />);
    const el = screen.getByTestId("day-of-countdown");
    // Should display hours (3h or similar)
    expect(el.textContent).toMatch(/\d+h|\d+\s*or/i);
  });

  it("shows reappraisal sentence in Romanian", () => {
    const future = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString();
    render(<DayOfCountdown subject="PA" startAt={future} />);
    const el = screen.getByTestId("day-of-countdown");
    // Reappraisal sentence should be in Romanian
    expect(el.textContent).toMatch(/pregătit|examen|ești|succes/i);
  });
});
