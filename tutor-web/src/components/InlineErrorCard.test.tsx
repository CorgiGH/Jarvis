import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { InlineErrorCard } from "./InlineErrorCard";

describe("InlineErrorCard", () => {
  it("renders the inline-error-card testid", () => {
    render(<InlineErrorCard message="Ceva a mers greșit." />);
    expect(screen.getByTestId("inline-error-card")).toBeInTheDocument();
  });

  it("displays the error message", () => {
    render(<InlineErrorCard message="Eroare de rețea." />);
    expect(screen.getByText("Eroare de rețea.")).toBeInTheDocument();
  });

  it("calls onRetry when retry button is clicked", () => {
    const onRetry = vi.fn();
    render(<InlineErrorCard message="Eroare." onRetry={onRetry} />);
    fireEvent.click(screen.getByRole("button", { name: /încearcă|retry/i }));
    expect(onRetry).toHaveBeenCalled();
  });

  it("does not render retry button when onRetry is not provided", () => {
    render(<InlineErrorCard message="Eroare." />);
    expect(screen.queryByRole("button", { name: /încearcă|retry/i })).not.toBeInTheDocument();
  });
});
