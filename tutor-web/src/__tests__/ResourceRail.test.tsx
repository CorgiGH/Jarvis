import { render, screen, fireEvent } from "@testing-library/react";
import { describe, test, expect, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ResourceRail } from "../components/ResourceRail";
import type { RailItem } from "../lib/taskPrep";

vi.mock("../components/PdfPane", () => ({ PdfPane: () => <div data-testid="mock-pdf-pane">PDF</div> }));
vi.mock("../components/Scratchpad", () => ({ Scratchpad: ({ value }: { value: string }) => <textarea data-testid="mock-scratchpad" value={value} readOnly /> }));
vi.mock("../components/ConceptDrawer", () => ({ ConceptDrawer: () => <div data-testid="mock-concept">CONCEPT</div> }));
vi.mock("../components/KnowledgeGapCard", () => ({ KnowledgeGapCard: () => <div data-testid="mock-gap">GAP</div> }));

const STUB_ITEMS: RailItem[] = [
  { type: "PDF", label: "Tema_A.pdf p.4", action: "OPEN_DRAWER", payload: { path: "_extras/PS/ps_hw/Tema_A.pdf" } },
  { type: "SCRATCHPAD", label: "draft answers", action: "OPEN_DRAWER", payload: {} },
  { type: "CONCEPT", label: "Laplace MLE", action: "OPEN_DRAWER", payload: { conceptId: "abc123" } },
  { type: "FSRS_DUE", label: "4 cards due", action: "NAVIGATE", payload: { count: 4, route: "/tutor/review" } },
];

function Wrap({ items, taskId = "task-01" }: { items: RailItem[]; taskId?: string }) {
  return (
    <MemoryRouter initialEntries={["/?taskId=task-01"]}>
      <Routes>
        <Route path="/" element={<ResourceRail taskId={taskId} items={items} />} />
        <Route path="/tutor/review" element={<div data-testid="review-route">REVIEW</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("ResourceRail", () => {
  test("renders aside with data-testid='resource-rail'", () => {
    render(<Wrap items={STUB_ITEMS} />);
    expect(screen.getByTestId("resource-rail")).toBeInTheDocument();
  });

  test("renders one button per item with data-testid='rail-item-{TYPE}'", () => {
    render(<Wrap items={STUB_ITEMS} />);
    expect(screen.getByTestId("rail-item-PDF")).toBeInTheDocument();
    expect(screen.getByTestId("rail-item-SCRATCHPAD")).toBeInTheDocument();
    expect(screen.getByTestId("rail-item-CONCEPT")).toBeInTheDocument();
    expect(screen.getByTestId("rail-item-FSRS_DUE")).toBeInTheDocument();
  });

  test("button label includes item.label text", () => {
    render(<Wrap items={STUB_ITEMS} />);
    expect(screen.getByText("Tema_A.pdf p.4")).toBeInTheDocument();
    expect(screen.getByText("4 cards due")).toBeInTheDocument();
  });

  test("clicking OPEN_DRAWER item shows the drawer", () => {
    render(<Wrap items={STUB_ITEMS} />);
    fireEvent.click(screen.getByTestId("rail-item-PDF"));
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    expect(screen.getByTestId("rail-drawer").getAttribute("data-type")).toBe("PDF");
  });

  test("clicking NAVIGATE item changes route (no drawer)", () => {
    render(<Wrap items={STUB_ITEMS} />);
    fireEvent.click(screen.getByTestId("rail-item-FSRS_DUE"));
    expect(screen.getByTestId("review-route")).toBeInTheDocument();
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
  });

  test("drawer close button hides the drawer", () => {
    render(<Wrap items={STUB_ITEMS} />);
    fireEvent.click(screen.getByTestId("rail-item-PDF"));
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("rail-drawer-close"));
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
  });

  test("Escape key closes the drawer", () => {
    render(<Wrap items={STUB_ITEMS} />);
    fireEvent.click(screen.getByTestId("rail-item-PDF"));
    expect(screen.getByTestId("rail-drawer")).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
  });

  test("empty items list renders empty rail (no items, no drawer)", () => {
    render(<Wrap items={[]} />);
    expect(screen.getByTestId("resource-rail")).toBeInTheDocument();
    expect(screen.queryAllByTestId(/^rail-item-/)).toHaveLength(0);
    expect(screen.queryByTestId("rail-drawer")).toBeNull();
  });
});
