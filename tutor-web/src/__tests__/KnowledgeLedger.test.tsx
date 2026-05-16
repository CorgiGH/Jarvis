import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { KnowledgeLedger } from "../components/KnowledgeLedger";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("KnowledgeLedger renders gap rows from /api/v1/gaps", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({
      gaps: [
        { id: "g1", topic: "closures", taskId: "T1", type: "CONCEPT", reusedCount: 2, resolvedBy: null },
        { id: "g2", topic: "laplace", taskId: "T2", type: "CONCEPT", reusedCount: 0, resolvedBy: "USER_TYPED" },
      ],
    }), { status: 200 }),
  ));
  render(<KnowledgeLedger onClose={() => {}} />);
  await waitFor(() => expect(screen.getAllByTestId("ledger-row").length).toBe(2));
});

test("filter open shows only unresolved", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({
      gaps: [
        { id: "g1", topic: "a", taskId: null, type: "CONCEPT", reusedCount: 0, resolvedBy: null },
        { id: "g2", topic: "b", taskId: null, type: "CONCEPT", reusedCount: 0, resolvedBy: "USER_DISMISSED" },
      ],
    }), { status: 200 }),
  ));
  render(<KnowledgeLedger onClose={() => {}} />);
  await waitFor(() => expect(screen.getAllByTestId("ledger-row").length).toBe(2));
  fireEvent.click(screen.getByTestId("ledger-filter-open"));
  expect(screen.getAllByTestId("ledger-row").length).toBe(1);
});

test("Escape key closes the ledger", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  const onClose = vi.fn();
  render(<KnowledgeLedger onClose={onClose} />);
  fireEvent.keyDown(document, { key: "Escape" });
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("backdrop click closes the ledger", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  const onClose = vi.fn();
  render(<KnowledgeLedger onClose={onClose} />);
  fireEvent.click(screen.getByTestId("knowledge-ledger-backdrop"));
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("close button is auto-focused on mount", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  render(<KnowledgeLedger onClose={() => {}} />);
  const closeBtn = screen.getByLabelText("Close ledger");
  expect(document.activeElement).toBe(closeBtn);
});
