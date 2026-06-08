import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
  it("renders the empty-state testid", () => {
    render(<EmptyState variant="no-queue" />);
    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
  });

  it("renders variant 'no-queue'", () => {
    render(<EmptyState variant="no-queue" />);
    expect(screen.getByTestId("empty-state-variant-no-queue")).toBeInTheDocument();
    expect(screen.getByText(/terminat|azi|queue/i)).toBeInTheDocument();
  });

  it("renders variant 'no-content'", () => {
    render(<EmptyState variant="no-content" />);
    expect(screen.getByTestId("empty-state-variant-no-content")).toBeInTheDocument();
    expect(screen.getByText(/conținut|material/i)).toBeInTheDocument();
  });

  it("renders variant 'no-results'", () => {
    render(<EmptyState variant="no-results" />);
    expect(screen.getByTestId("empty-state-variant-no-results")).toBeInTheDocument();
    expect(screen.getByText(/rezultat|căutare/i)).toBeInTheDocument();
  });

  it("renders custom message when provided", () => {
    render(<EmptyState variant="no-queue" message="Mesaj personalizat" />);
    expect(screen.getByText("Mesaj personalizat")).toBeInTheDocument();
  });
});
