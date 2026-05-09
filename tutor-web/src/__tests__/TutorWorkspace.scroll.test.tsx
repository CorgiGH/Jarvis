import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/chat")) {
      // jsdom can't measure layout — long reply only ensures messages render so
      // we can locate the scroll container under chat-pane. Actual scroll
      // behavior is verified by the Phase 1 Playwright gate, not here.
      const longReply = Array.from({ length: 200 }, (_, i) => `line ${i}: laplace transform of f(t) = ...`).join("\n");
      return new Response(JSON.stringify({ reply: longReply }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("workspace outer container is flex (not grid) so flex children get bounded height", () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  const chat = screen.getByTestId("chat-pane");
  // Walk up from chat-pane until we find the two-col container (the one whose
  // sibling subtree contains pdf-pane). It must carry `flex` not `grid`.
  let twoColParent: HTMLElement | null = chat.parentElement;
  while (twoColParent && !twoColParent.querySelector('[data-testid="pdf-pane"]')) {
    twoColParent = twoColParent.parentElement;
  }
  expect(twoColParent, "expected ancestor containing both pdf-pane and chat-pane").not.toBeNull();
  expect(twoColParent!.className).toMatch(/\bflex\b/);
  expect(twoColParent!.className).not.toMatch(/\bgrid\b/);
  expect(twoColParent!.className).toMatch(/flex-col/);
  expect(twoColParent!.className).toMatch(/sm:flex-row/);
});

test("chat pane root carries min-w-0 so flex sibling can shrink", () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  const chat = screen.getByTestId("chat-pane");
  expect(chat.className).toMatch(/\bmin-w-0\b/);
});

test("chat scroll container uses overflow-auto for KaTeX horizontal overflow", async () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  fireEvent.change(screen.getByPlaceholderText(/message/i), { target: { value: "explain laplace" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  await waitFor(() => expect(screen.getAllByText(/^line 0:/).length).toBeGreaterThan(0));

  const chat = screen.getByTestId("chat-pane");
  const scrollContainer = Array.from(chat.querySelectorAll("div")).find(d =>
    /\boverflow-auto\b/.test(d.className) && /\bflex-1\b/.test(d.className),
  );
  expect(scrollContainer, "expected an overflow-auto + flex-1 scroll container under chat-pane").toBeDefined();
  expect(scrollContainer!.className).not.toMatch(/overflow-y-auto/);
  expect(scrollContainer!.className).not.toMatch(/overflow-x-hidden/);
});
