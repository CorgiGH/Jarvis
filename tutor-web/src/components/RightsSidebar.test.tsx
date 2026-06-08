/**
 * RightsSidebar — unit tests.
 *
 * Coverage:
 *  - Renders the sidebar container.
 *  - Renders gdpr-export-btn and gdpr-delete-btn.
 *  - Clicking gdpr-delete-btn fires onDelete.
 *  - Clicking gdpr-export-btn fires onExport.
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RightsSidebar } from "./RightsSidebar";

describe("RightsSidebar", () => {
  it("renders the sidebar container", () => {
    render(<RightsSidebar />);
    expect(screen.getByTestId("rights-sidebar")).toBeInTheDocument();
  });

  it("renders the GDPR export button", () => {
    render(<RightsSidebar />);
    expect(screen.getByTestId("gdpr-export-btn")).toBeInTheDocument();
  });

  it("renders the GDPR delete button", () => {
    render(<RightsSidebar />);
    expect(screen.getByTestId("gdpr-delete-btn")).toBeInTheDocument();
  });

  it("calls onDelete when delete button is clicked", () => {
    const onDelete = vi.fn();
    render(<RightsSidebar onDelete={onDelete} />);
    fireEvent.click(screen.getByTestId("gdpr-delete-btn"));
    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it("calls onExport when export link is clicked", () => {
    const onExport = vi.fn();
    render(<RightsSidebar onExport={onExport} />);
    fireEvent.click(screen.getByTestId("gdpr-export-btn"));
    expect(onExport).toHaveBeenCalledTimes(1);
  });

  it("shows all five rights entries", () => {
    render(<RightsSidebar />);
    const sidebar = screen.getByTestId("rights-sidebar");
    // Check each article is mentioned
    expect(sidebar.textContent).toContain("Art. 15");
    expect(sidebar.textContent).toContain("Art. 17");
    expect(sidebar.textContent).toContain("Art. 18");
    expect(sidebar.textContent).toContain("Art. 22");
    expect(sidebar.textContent).toContain("AI Act");
  });
});
