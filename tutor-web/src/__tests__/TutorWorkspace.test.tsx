import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({ reply: "hello back" }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("renders left PDF pane, right chat pane, and scratchpad", () => {
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" />);
  expect(screen.getByTestId("pdf-pane")).toBeInTheDocument();
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
  expect(screen.getByTestId("scratchpad")).toBeInTheDocument();
});

test("INSERT → SCRATCHPAD from gap card appends to scratchpad slot", async () => {
  // Reply embeds a gap envelope. Click INSERT and assert scratchpad receives content.
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({
        reply: `here is the concept: <gap>{"id":"g1","topic":"closures","language":"kotlin","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"a closure captures vars","exampleCode":"val f = { x -> x+1 }"}</gap>`,
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T-scratch" />);
  fireEvent.change(screen.getByPlaceholderText(/message/i), { target: { value: "explain closures" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  // Wait for the gap card to render.
  const insertBtn = await waitFor(() => screen.getByTestId("knowledge-gap-insert"));
  fireEvent.click(insertBtn);
  // Scratchpad textarea should now contain the example code (with topic header).
  const scratch = screen.getByTestId("scratchpad-input") as HTMLTextAreaElement;
  expect(scratch.value).toContain("closures");
  expect(scratch.value).toContain("val f");
});

test("send button POSTs to /api/chat and shows reply", async () => {
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" />);
  const input = screen.getByPlaceholderText(/message/i);
  fireEvent.change(input, { target: { value: "hi" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  await waitFor(() => expect(screen.getByText("hello back")).toBeInTheDocument());
  const call = (globalThis.fetch as any).mock.calls.find((c: any) =>
    typeof c[0] === "string" && c[0].includes("/api/chat"));
  expect(call?.[1]?.headers?.["X-CSRF-Token"]).toBe("zzz");
});
