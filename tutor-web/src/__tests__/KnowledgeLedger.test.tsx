import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
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
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
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
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByTestId("ledger-row").length).toBe(2));
  fireEvent.click(screen.getByTestId("ledger-filter-open"));
  expect(screen.getAllByTestId("ledger-row").length).toBe(1);
});

test("Escape key closes the ledger", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  const onClose = vi.fn();
  render(<MemoryRouter><KnowledgeLedger onClose={onClose} /></MemoryRouter>);
  fireEvent.keyDown(document, { key: "Escape" });
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("backdrop click closes the ledger", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  const onClose = vi.fn();
  render(<MemoryRouter><KnowledgeLedger onClose={onClose} /></MemoryRouter>);
  fireEvent.click(screen.getByTestId("knowledge-ledger-backdrop"));
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("close button is auto-focused on mount", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
  const closeBtn = screen.getByLabelText("Close ledger");
  expect(document.activeElement).toBe(closeBtn);
});

test("re-fetches gaps on jarvis:gap-resolved event", async () => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  window.dispatchEvent(new CustomEvent("jarvis:gap-resolved", { detail: { id: "x" } }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
});

test("re-fetches gaps on jarvis:gap-created event", async () => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  window.dispatchEvent(new CustomEvent("jarvis:gap-created", { detail: { id: "y" } }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
});

test("HTTP error surfaces distinct load-error UI (not just empty state)", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("nope", { status: 500 })));
  render(<MemoryRouter><KnowledgeLedger onClose={() => {}} /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("ledger-load-error")).toHaveTextContent(/HTTP 500/));
  expect(screen.queryByTestId("ledger-empty")).toBeNull();
});

test("row with taskId is a clickable button that closes the ledger", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({
      gaps: [
        { id: "g1", topic: "closures", taskId: "T123", type: "CONCEPT", reusedCount: 1, resolvedBy: null },
        { id: "g2", topic: "orphan", taskId: null, type: "CONCEPT", reusedCount: 0, resolvedBy: null },
      ],
    }), { status: 200 }),
  ));
  const onClose = vi.fn();
  render(<MemoryRouter><KnowledgeLedger onClose={onClose} /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByTestId("ledger-row").length).toBe(2));
  // Row with taskId carries the clickable wrapper; orphan does not.
  const openButtons = screen.getAllByTestId("ledger-row-open");
  expect(openButtons.length).toBe(1);
  fireEvent.click(openButtons[0]);
  expect(onClose).toHaveBeenCalledTimes(1);
});
