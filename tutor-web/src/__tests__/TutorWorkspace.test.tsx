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

test("renders left PDF pane and right chat pane", () => {
  render(<TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" />);
  expect(screen.getByTestId("pdf-pane")).toBeInTheDocument();
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
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
