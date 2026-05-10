import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { KnowledgeGapCard } from "../components/KnowledgeGapCard";
import type { KnowledgeGap } from "../lib/knowledgeGap";

const gap: KnowledgeGap = {
  id: "g-1", topic: "laplace", language: "math",
  type: "CONCEPT", trigger: "EXPLICIT_ASK",
  content: "explain laplace transform",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("SHOW DOCS POSTs + renders inline results", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/gap/g-1/search-docs") && init?.method === "POST") {
      return new Response(JSON.stringify({
        results: [
          { filename: "PA/study_guide/laplace.pdf", snippet: "Laplace transform definition...", lineRef: null },
          { filename: "PS/Tema_A.pdf", snippet: "Apply the transform...", lineRef: null },
        ],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<KnowledgeGapCard gap={gap} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-show-docs"));
  await waitFor(() => expect(screen.getByTestId("knowledge-gap-docs")).toBeInTheDocument());
  expect(screen.getByText(/laplace.pdf/)).toBeInTheDocument();
  expect(screen.getByText(/Tema_A.pdf/)).toBeInTheDocument();
});

test("SHOW DOCS toggles HIDE DOCS on second click without re-fetch", async () => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ results: [] }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  render(<KnowledgeGapCard gap={gap} />);
  const btn = screen.getByTestId("knowledge-gap-show-docs");
  fireEvent.click(btn);
  await waitFor(() => expect(btn.textContent).toMatch(/HIDE DOCS/));
  fireEvent.click(btn);
  expect(btn.textContent).toMatch(/SHOW DOCS/);
  expect(fetchMock.mock.calls.length).toBe(1);
});
