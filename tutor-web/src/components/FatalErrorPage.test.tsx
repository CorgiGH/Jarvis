import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { FatalErrorPage } from "./FatalErrorPage";

describe("FatalErrorPage", () => {
  it("renders the fatal-error-page testid", () => {
    render(<FatalErrorPage />);
    expect(screen.getByTestId("fatal-error-page")).toBeInTheDocument();
  });

  it("shows a Romanian fatal error heading", () => {
    render(<FatalErrorPage />);
    const el = screen.getByTestId("fatal-error-page");
    expect(el.textContent).toMatch(/eroare|aplicație|problemă/i);
  });

  it("shows the custom error message when provided", () => {
    render(<FatalErrorPage message="Sesiunea a expirat. Reconectează-te." />);
    expect(
      screen.getByText("Sesiunea a expirat. Reconectează-te."),
    ).toBeInTheDocument();
  });

  it("shows a reload button by default", () => {
    render(<FatalErrorPage />);
    expect(screen.getByRole("button", { name: /reîncarcă|reload/i })).toBeInTheDocument();
  });

  it("calls onRetry when retry/reload is clicked", () => {
    const onRetry = vi.fn();
    render(<FatalErrorPage onRetry={onRetry} />);
    fireEvent.click(screen.getByRole("button", { name: /reîncarcă|reload/i }));
    expect(onRetry).toHaveBeenCalled();
  });
});
