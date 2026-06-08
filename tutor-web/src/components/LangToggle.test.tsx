import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LangToggle } from "./LangToggle";

describe("LangToggle", () => {
  it("renders the lang-toggle", () => {
    render(<LangToggle lang="ro" onToggle={vi.fn()} />);
    expect(screen.getByTestId("lang-toggle")).toBeInTheDocument();
  });

  it("shows active lang highlight on RO when lang=ro", () => {
    render(<LangToggle lang="ro" onToggle={vi.fn()} />);
    const toggle = screen.getByTestId("lang-toggle");
    expect(toggle.textContent).toContain("RO");
  });

  it("calls onToggle with 'en' when EN is clicked and current is 'ro'", () => {
    const onToggle = vi.fn();
    render(<LangToggle lang="ro" onToggle={onToggle} />);
    fireEvent.click(screen.getByRole("button", { name: /en/i }));
    expect(onToggle).toHaveBeenCalledWith("en");
  });

  it("calls onToggle with 'ro' when RO is clicked and current is 'en'", () => {
    const onToggle = vi.fn();
    render(<LangToggle lang="en" onToggle={onToggle} />);
    fireEvent.click(screen.getByRole("button", { name: /ro/i }));
    expect(onToggle).toHaveBeenCalledWith("ro");
  });
});
