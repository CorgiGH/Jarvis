import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CalibrationTable } from "./CalibrationTable";
import type { CalibrationBucket } from "./CalibrationPlot";

const BUCKETS_OVER: CalibrationBucket[] = [
  // DEFINITELY but only 50% accuracy → OVER-CONFIDENT
  { student_confidence: "DEFINITELY", attempts: 20, correct: 10, accuracy: 0.5 },
  // IDK but 80% correct → UNDER-CONFIDENT
  { student_confidence: "IDK", attempts: 10, correct: 8, accuracy: 0.8 },
  // MAYBE at 75% → CALIBRATED
  { student_confidence: "MAYBE", attempts: 15, correct: 11, accuracy: 0.733 },
];

describe("CalibrationTable", () => {
  it("renders calibration-table container", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    expect(screen.getByTestId("calibration-table")).toBeInTheDocument();
  });

  it("renders a row for each bucket", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    expect(screen.getByTestId("calibration-row-DEFINITELY")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-row-IDK")).toBeInTheDocument();
    expect(screen.getByTestId("calibration-row-MAYBE")).toBeInTheDocument();
  });

  it("labels DEFINITELY at 50% accuracy as OVER", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    const row = screen.getByTestId("calibration-row-DEFINITELY");
    expect(row).toHaveAttribute("data-band", "OVER");
  });

  it("labels IDK at 80% accuracy as UNDER", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    const row = screen.getByTestId("calibration-row-IDK");
    expect(row).toHaveAttribute("data-band", "UNDER");
  });

  it("labels MAYBE at ~73% accuracy as CALIBRATED", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    const row = screen.getByTestId("calibration-row-MAYBE");
    expect(row).toHaveAttribute("data-band", "CALIBRATED");
  });

  it("shows total_attempts", () => {
    render(<CalibrationTable buckets={BUCKETS_OVER} totalAttempts={45} />);
    expect(screen.getByTestId("calibration-table")).toHaveTextContent("45");
  });

  it("renders gracefully with empty buckets", () => {
    render(<CalibrationTable buckets={[]} totalAttempts={0} />);
    expect(screen.getByTestId("calibration-table")).toBeInTheDocument();
  });
});
