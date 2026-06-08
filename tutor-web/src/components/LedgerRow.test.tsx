import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { LedgerRow } from "./LedgerRow";

const baseGap = {
  id: "gap-1",
  topic: "Teorema lui Bayes",
  taskId: null,
  type: "CONCEPT_GAP",
  reusedCount: 3,
  resolvedBy: null as string | null,
};

function renderRow(gap = baseGap, onClose = vi.fn()) {
  return render(
    <MemoryRouter>
      <ul>
        <LedgerRow gap={gap} onClose={onClose} />
      </ul>
    </MemoryRouter>,
  );
}

describe("LedgerRow", () => {
  it("renders ledger-row-{id} testid", () => {
    renderRow();
    expect(screen.getByTestId("ledger-row-gap-1")).toBeInTheDocument();
  });

  it("renders ledger-row-status-{id} testid", () => {
    renderRow();
    expect(screen.getByTestId("ledger-row-status-gap-1")).toBeInTheDocument();
  });

  it("shows 'open' status when resolvedBy is null", () => {
    renderRow();
    expect(screen.getByTestId("ledger-row-status-gap-1")).toHaveTextContent(/open/i);
  });

  it("shows 'resolved' status when resolvedBy is set", () => {
    renderRow({ ...baseGap, resolvedBy: "manual" });
    expect(screen.getByTestId("ledger-row-status-gap-1")).toHaveTextContent(/resolved/i);
  });

  it("displays topic text", () => {
    renderRow();
    expect(screen.getByTestId("ledger-row-gap-1")).toHaveTextContent("Teorema lui Bayes");
  });

  it("displays reuse count", () => {
    renderRow();
    expect(screen.getByTestId("ledger-row-gap-1")).toHaveTextContent("3");
  });
});
