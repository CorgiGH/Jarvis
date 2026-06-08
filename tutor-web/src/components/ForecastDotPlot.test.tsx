import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ForecastDotPlot } from "./ForecastDotPlot";

describe("ForecastDotPlot", () => {
  it("renders the forecast-dot-plot container", () => {
    render(<ForecastDotPlot tomorrow={5} thisWeek={20} thisMonth={60} />);
    expect(screen.getByTestId("fsrs-forecast-plot")).toBeInTheDocument();
  });

  it("renders tomorrow bar", () => {
    render(<ForecastDotPlot tomorrow={5} thisWeek={20} thisMonth={60} />);
    expect(screen.getByTestId("forecast-bar-tomorrow")).toBeInTheDocument();
  });

  it("renders thisWeek bar", () => {
    render(<ForecastDotPlot tomorrow={5} thisWeek={20} thisMonth={60} />);
    expect(screen.getByTestId("forecast-bar-week")).toBeInTheDocument();
  });

  it("renders thisMonth bar", () => {
    render(<ForecastDotPlot tomorrow={5} thisWeek={20} thisMonth={60} />);
    expect(screen.getByTestId("forecast-bar-month")).toBeInTheDocument();
  });

  it("shows 0 bars gracefully when all zeros", () => {
    render(<ForecastDotPlot tomorrow={0} thisWeek={0} thisMonth={0} />);
    expect(screen.getByTestId("fsrs-forecast-plot")).toBeInTheDocument();
  });

  it("bar widths are proportional — tomorrow bar is narrower than month bar", () => {
    render(<ForecastDotPlot tomorrow={5} thisWeek={20} thisMonth={60} />);
    const tBar = screen.getByTestId("forecast-bar-tomorrow");
    const mBar = screen.getByTestId("forecast-bar-month");
    const tWidth = parseFloat(tBar.getAttribute("data-bar-pct") ?? "0");
    const mWidth = parseFloat(mBar.getAttribute("data-bar-pct") ?? "0");
    expect(tWidth).toBeLessThan(mWidth);
  });

  it("displays count labels", () => {
    render(<ForecastDotPlot tomorrow={7} thisWeek={21} thisMonth={63} />);
    expect(screen.getByTestId("fsrs-forecast-plot")).toHaveTextContent("7");
    expect(screen.getByTestId("fsrs-forecast-plot")).toHaveTextContent("21");
    expect(screen.getByTestId("fsrs-forecast-plot")).toHaveTextContent("63");
  });
});
