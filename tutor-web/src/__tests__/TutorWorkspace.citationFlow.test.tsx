/**
 * C6 – CitationPill click opens ResourceRail drawer.
 *
 * Strategy: test ResourceRail's forceOpenPath behaviour in isolation (the
 * "narrower fallback" allowed by plan step 10).  TutorWorkspace integration
 * requires mocking getTaskPrep + full router setup; the click-to-drawer
 * wiring in TutorWorkspace itself is trivially verified by the TypeScript
 * compiler (the prop names line up) and the C7 Playwright gate covers the
 * live interaction.  The unit layer asserts the contract that matters:
 *   forceOpenPath="known/path" → drawer opens for that rail item.
 *   forceOpenPath="archival/path.md" → drawer synthesises a CONCEPT item.
 *   Closing the drawer fires onDrawerClosed (clears pendingDrawerPath).
 */

import { render, screen, fireEvent } from "@testing-library/react";
import { describe, test, expect, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ResourceRail } from "../components/ResourceRail";
import type { RailItem } from "../lib/taskPrep";

// ── Mocks for drawer-content components ──────────────────────────────────────
vi.mock("../components/PdfPane", () => ({ PdfPane: () => <div data-testid="mock-pdf-pane">PDF</div> }));
vi.mock("../components/Scratchpad", () => ({ Scratchpad: ({ value }: { value: string }) => <textarea data-testid="mock-scratchpad" value={value} readOnly /> }));
vi.mock("../components/ConceptDrawer", () => ({
  ConceptDrawer: ({ concept, onClose }: { concept: string; onClose: () => void }) => (
    <div data-testid="mock-concept-drawer" data-concept={concept}>
      <button data-testid="concept-close" onClick={onClose}>close</button>
    </div>
  ),
}));
vi.mock("../components/KnowledgeGapCard", () => ({ KnowledgeGapCard: () => <div data-testid="mock-gap">GAP</div> }));

// ── Stub rail items ──────────────────────────────────────────────────────────
const RAIL_ITEMS: RailItem[] = [
  {
    type: "PDF",
    label: "Tema_A.pdf",
    action: "OPEN_DRAWER",
    payload: { path: "lectures/week03/slides.pdf" },
  },
  {
    type: "CONCEPT",
    label: "Laplace MLE",
    action: "OPEN_DRAWER",
    payload: { conceptId: "laplace-mle", path: "concepts/laplace-mle.md" },
  },
];

function Wrap({
  items = RAIL_ITEMS,
  forceOpenPath,
  onDrawerClosed,
}: {
  items?: RailItem[];
  forceOpenPath?: string | null;
  onDrawerClosed?: () => void;
}) {
  return (
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route
          path="/"
          element={
            <ResourceRail
              taskId="task-01"
              items={items}
              forceOpenPath={forceOpenPath}
              onDrawerClosed={onDrawerClosed}
            />
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe("ResourceRail — forceOpenPath (C6 CitationPill flow)", () => {
  test("forceOpenPath matching a known rail item opens its drawer", () => {
    render(<Wrap forceOpenPath="lectures/week03/slides.pdf" />);
    const drawer = screen.getByTestId("rail-drawer");
    expect(drawer).toBeInTheDocument();
    // PDF type matched → data-type="PDF"
    expect(drawer.getAttribute("data-type")).toBe("PDF");
  });

  test("forceOpenPath NOT matching any rail item opens a synthetic CONCEPT drawer", () => {
    render(<Wrap forceOpenPath="archival/some-notes.md" />);
    const drawer = screen.getByTestId("rail-drawer");
    expect(drawer).toBeInTheDocument();
    expect(drawer.getAttribute("data-type")).toBe("CONCEPT");
    // Drawer header should show the basename
    expect(drawer.textContent).toMatch(/some-notes\.md/);
  });

  test("synthetic drawer item does NOT appear in the rail list", () => {
    render(<Wrap forceOpenPath="archival/some-notes.md" />);
    // Only the two stub items should be in the aside buttons
    const railButtons = screen.getAllByRole("button").filter(
      (b) => b.getAttribute("data-testid")?.startsWith("rail-item-"),
    );
    expect(railButtons).toHaveLength(RAIL_ITEMS.length);
  });

  test("closing drawer after forceOpenPath fires onDrawerClosed", () => {
    const onDrawerClosed = vi.fn();
    render(<Wrap forceOpenPath="archival/some-notes.md" onDrawerClosed={onDrawerClosed} />);
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("rail-drawer-close"));
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
    expect(onDrawerClosed).toHaveBeenCalledOnce();
  });

  test("closing drawer after manual rail-item click does NOT fire onDrawerClosed", () => {
    const onDrawerClosed = vi.fn();
    render(<Wrap onDrawerClosed={onDrawerClosed} />);
    // Click the PDF rail item directly (not via forceOpenPath)
    fireEvent.click(screen.getByTestId("rail-item-PDF"));
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("rail-drawer-close"));
    expect(onDrawerClosed).not.toHaveBeenCalled();
  });

  test("null forceOpenPath does not open a drawer", () => {
    render(<Wrap forceOpenPath={null} />);
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
  });

  test("existing rail items still open drawer on direct click (no regression)", () => {
    render(<Wrap />);
    fireEvent.click(screen.getByTestId("rail-item-PDF"));
    const drawer = screen.getByTestId("rail-drawer");
    expect(drawer.getAttribute("data-type")).toBe("PDF");
  });

  test("Escape key closes forceOpenPath-triggered drawer and fires onDrawerClosed", () => {
    const onDrawerClosed = vi.fn();
    render(<Wrap forceOpenPath="archival/esc-test.md" onDrawerClosed={onDrawerClosed} />);
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
    expect(onDrawerClosed).toHaveBeenCalledOnce();
  });
});
