import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ChatPane } from "../components/ChatPane";
import { chatPane as S } from "../lib/chromeStrings";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("ChatPane GETs historical gaps on mount and renders PREVIOUSLY FLAGGED", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps?taskId=T1")) {
      return new Response(JSON.stringify({
        gaps: [{ id: "g-old-1", topic: "closures", language: "kotlin", type: "CONCEPT",
                 trigger: "EXPLICIT_ASK", content: "explain closures",
                 exampleCode: null, sourceCitation: null, resolvedBy: null, reusedCount: 0 }],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T1" />);
  await waitFor(() => expect(screen.getByTestId("historical-gaps")).toBeInTheDocument());
  expect(screen.getAllByText(/closures/).length).toBeGreaterThan(0);
});

test("ChatPane POSTs each gap parsed from assistant reply with the active taskId", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps?taskId=")) {
      return new Response(JSON.stringify({ gaps: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({
        reply: `intro: <gap>{"id":"g1","topic":"laplace","language":"math","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"explain laplace","exampleCode":null}</gap>`,
      }), { status: 200 });
    }
    if (typeof url === "string" && url === "/api/v1/gap" && init?.method === "POST") {
      return new Response(JSON.stringify({ id: "g-new", reusedCount: 0 }), { status: 201 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T2" />);
  fireEvent.change(screen.getByPlaceholderText(S.inputPlaceholder), { target: { value: "explain laplace" } });
  fireEvent.click(screen.getByRole("button", { name: S.sendButton }));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0] === "/api/v1/gap" && c[1]?.method === "POST");
    expect(calls.length).toBeGreaterThanOrEqual(1);
    const body = JSON.parse(calls[0][1].body);
    expect(body.topic).toBe("laplace");
    expect(body.taskId).toBe("T2");
  });
});
