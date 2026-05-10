import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ConceptInline } from "../components/ConceptInline";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ gaps: [] }), { status: 200 }),
  ));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("ConceptInline opens drawer on click + closes on ×", async () => {
  render(<ConceptInline name="laplace" />);
  fireEvent.click(screen.getByTestId("concept-inline"));
  await waitFor(() => expect(screen.getByTestId("concept-drawer")).toBeInTheDocument());
  fireEvent.click(screen.getByLabelText(/close concept drawer/i));
  await waitFor(() => expect(screen.queryByTestId("concept-drawer")).toBeNull());
});
