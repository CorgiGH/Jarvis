import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ConceptDrawer } from "../components/ConceptDrawer";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ gaps: [] }), { status: 200 })));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("Escape key closes the concept drawer", () => {
  const onClose = vi.fn();
  render(<ConceptDrawer concept="closures" onClose={onClose} />);
  fireEvent.keyDown(document, { key: "Escape" });
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("backdrop click closes the concept drawer", () => {
  const onClose = vi.fn();
  render(<ConceptDrawer concept="closures" onClose={onClose} />);
  fireEvent.click(screen.getByTestId("concept-drawer-backdrop"));
  expect(onClose).toHaveBeenCalledTimes(1);
});

test("close button is auto-focused on mount", () => {
  render(<ConceptDrawer concept="closures" onClose={() => {}} />);
  const closeBtn = screen.getByLabelText("Close concept drawer");
  expect(document.activeElement).toBe(closeBtn);
});

test("HTTP error surfaces distinct load-error UI (not just empty 'no past gaps')", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("nope", { status: 500 })));
  render(<ConceptDrawer concept="closures" onClose={() => {}} />);
  await waitFor(() => expect(screen.getByTestId("concept-drawer-load-error")).toHaveTextContent(/HTTP 500/));
});
