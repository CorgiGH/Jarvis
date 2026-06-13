import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ConceptInline } from "../components/ConceptInline";
import { conceptDrawer as S } from "../lib/chromeStrings";

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
  fireEvent.click(screen.getByLabelText(S.closeAriaLabel));
  await waitFor(() => expect(screen.queryByTestId("concept-drawer")).toBeNull());
});

test("ConceptInline collapses to plain text when server says linked=false", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ linked: false, confidence: 0.95 }), { status: 200 }),
  ));
  render(<ConceptInline name="derivative" />);
  await waitFor(() => expect(screen.getByTestId("concept-plain")).toBeInTheDocument());
  expect(screen.queryByTestId("concept-inline")).toBeNull();
});

test("ConceptInline keeps inline-link when server says linked=true (default)", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ linked: true, confidence: 0.1 }), { status: 200 }),
  ));
  render(<ConceptInline name="laplace" />);
  await waitFor(() => expect(screen.getByTestId("concept-inline")).toBeInTheDocument());
  expect(screen.queryByTestId("concept-plain")).toBeNull();
});
