import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { PredictionGate } from "./PredictionGate";

describe("PredictionGate", () => {
  const options = ["Da, funcționează mereu", "Nu, depinde de condiții"];

  it("renders 2 options", () => {
    render(<PredictionGate options={options} onPredict={() => {}} />);
    expect(screen.getByTestId("prediction-gate")).toBeInTheDocument();
    expect(screen.getByTestId("prediction-option-0")).toHaveTextContent(options[0]);
    expect(screen.getByTestId("prediction-option-1")).toHaveTextContent(options[1]);
  });

  it("clicking an option calls onPredict with that option text", () => {
    const onPredict = vi.fn();
    render(<PredictionGate options={options} onPredict={onPredict} />);
    fireEvent.click(screen.getByTestId("prediction-option-1"));
    expect(onPredict).toHaveBeenCalledWith(options[1]);
  });

  it("after selection renders committed state (prediction-submitted)", () => {
    render(<PredictionGate options={options} onPredict={() => {}} />);
    fireEvent.click(screen.getByTestId("prediction-option-0"));
    expect(screen.getByTestId("prediction-submitted")).toBeInTheDocument();
  });

  it("renders nothing when options is empty", () => {
    const { container } = render(<PredictionGate options={[]} onPredict={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders 4 options when 4 provided", () => {
    const four = ["A", "B", "C", "D"];
    render(<PredictionGate options={four} onPredict={() => {}} />);
    four.forEach((_, i) => {
      expect(screen.getByTestId(`prediction-option-${i}`)).toBeInTheDocument();
    });
  });
});
