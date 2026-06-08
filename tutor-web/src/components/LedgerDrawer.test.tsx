import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { LedgerDrawer } from "./LedgerDrawer";

vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(),
}));
import { jarvisFetch } from "../lib/api";
const mockFetch = jarvisFetch as ReturnType<typeof vi.fn>;

const makeGap = (id: string, topic: string, resolved = false) => ({
  id,
  topic,
  taskId: null,
  type: "CONCEPT_GAP",
  reusedCount: 1,
  resolvedBy: resolved ? "manual" : null,
});

function renderDrawer(onClose = vi.fn()) {
  return render(
    <MemoryRouter>
      <LedgerDrawer onClose={onClose} />
    </MemoryRouter>,
  );
}

describe("LedgerDrawer", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("renders ledger-drawer testid", () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ gaps: [] }),
    } as Response);
    renderDrawer();
    expect(screen.getByTestId("ledger-drawer")).toBeInTheDocument();
  });

  it("renders ledger-row-{id} for each gap", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ gaps: [makeGap("g1", "Bayes"), makeGap("g2", "FSRS")] }),
    } as Response);
    renderDrawer();
    await screen.findByTestId("ledger-row-g1");
    expect(screen.getByTestId("ledger-row-g1")).toBeInTheDocument();
    expect(screen.getByTestId("ledger-row-g2")).toBeInTheDocument();
  });

  it("renders ledger-row-status-{id} with correct status", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        gaps: [makeGap("g1", "Bayes", false), makeGap("g2", "FSRS", true)],
      }),
    } as Response);
    renderDrawer();
    await screen.findByTestId("ledger-row-status-g1");
    expect(screen.getByTestId("ledger-row-status-g1")).toHaveTextContent(/open/i);
    expect(screen.getByTestId("ledger-row-status-g2")).toHaveTextContent(/resolved/i);
  });

  it("calls onClose when backdrop is clicked", () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ gaps: [] }),
    } as Response);
    const onClose = vi.fn();
    renderDrawer(onClose);
    fireEvent.click(screen.getByTestId("ledger-drawer-backdrop"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("calls onClose when Escape key is pressed", () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ gaps: [] }),
    } as Response);
    const onClose = vi.fn();
    renderDrawer(onClose);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("renders empty state when no gaps", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ gaps: [] }),
    } as Response);
    renderDrawer();
    await screen.findByTestId("ledger-drawer-empty");
    expect(screen.getByTestId("ledger-drawer-empty")).toBeInTheDocument();
  });
});
