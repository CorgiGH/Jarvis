import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CalibrationPlot } from "./CalibrationPlot";
import type { CalibrationBucket } from "./CalibrationPlot";

const BUCKETS: CalibrationBucket[] = [
  { student_confidence: "DEFINITELY", attempts: 20, correct: 18, accuracy: 0.9 },
  { student_confidence: "MAYBE",      attempts: 15, correct: 10, accuracy: 0.667 },
  { student_confidence: "GUESS",      attempts: 10, correct: 4,  accuracy: 0.4 },
  { student_confidence: "IDK",        attempts: 5,  correct: 1,  accuracy: 0.2 },
];

const EXPECTED_LINE: Record<string, number> = {
  DEFINITELY: 0.95,
  MAYBE: 0.75,
  GUESS: 0.5,
  IDK: 0.25,
};

describe("CalibrationPlot", () => {
  it("renders calibration-plot container", () => {
    render(<CalibrationPlot buckets={BUCKETS} expectedLine={EXPECTED_LINE} />);
    expect(screen.getByTestId("calibration-plot")).toBeInTheDocument();
  });

  it("renders a dot per bucket", () => {
    render(<CalibrationPlot buckets={BUCKETS} expectedLine={EXPECTED_LINE} />);
    expect(screen.getByTestId("calibration-dot-DEFINITELY")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-dot-MAYBE")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-dot-GUESS")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-dot-IDK")).toBeInTheDocument();
  });

  it("encodes accuracy in data-accuracy attribute", () => {
    render(<CalibrationPlot buckets={BUCKETS} expectedLine={EXPECTED_LINE} />);
    const dot = screen.getByTestId("calibration-dot-DEFINITELY");
    expect(parseFloat(dot.getAttribute("data-accuracy") ?? "0")).toBeCloseTo(0.9);
  });

  it("renders gracefully with empty buckets", () => {
    render(<CalibrationPlot buckets={[]} expectedLine={{}} />);
    expect(screen.getByTestId("calibration-plot")).toBeInTheDocument();
  });

  it("renders the perfect-calibration diagonal line marker", () => {
    render(<CalibrationPlot buckets={BUCKETS} expectedLine={EXPECTED_LINE} />);
    expect(screen.getByTestId("calibration-diagonal")).toBeInTheDocument();
  });
});
