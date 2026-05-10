import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { KnowledgeGapCard } from "../components/KnowledgeGapCard";
import { SuggestedEditCard } from "../components/SuggestedEditCard";
import type { KnowledgeGap } from "../lib/knowledgeGap";
import type { SuggestedEdit } from "../lib/suggestedEdit";

const baseGap: KnowledgeGap = {
  id: "g-1",
  topic: "closures",
  language: "kotlin",
  type: "CONCEPT",
  trigger: "EXPLICIT_ASK",
  content: "a closure captures vars",
  exampleCode: "val f = { x -> x + 1 }",
};

const baseEdit: SuggestedEdit = {
  id: "e-1",
  type: "clipboard",
  payload: "fun foo() = 42",
  status: "pending",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("KnowledgeGapCard MARK RESOLVED POSTs status to /api/v1/gap/{id}/status", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/gap/g-1/status") && init?.method === "POST") {
      return new Response(JSON.stringify({ logId: "log-1" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<KnowledgeGapCard gap={baseGap} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-resolve"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/api/v1/gap/g-1/status"));
    expect(calls.length).toBe(1);
    expect(JSON.parse(calls[0][1].body).status).toBe("USER_MARKED_DONE");
  });
});

test("KnowledgeGapCard FLAG WRONG POSTs USER_DISMISSED", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ logId: "x" }), { status: 200 })));
  render(<KnowledgeGapCard gap={baseGap} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-dismiss"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls;
    expect(calls.length).toBe(1);
    expect(JSON.parse(calls[0][1].body).status).toBe("USER_DISMISSED");
  });
});

test("SuggestedEditCard REJECT POSTs status to /api/v1/edit/{id}/status", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ logId: "log-2" }), { status: 200 })));
  render(<SuggestedEditCard edit={baseEdit} />);
  fireEvent.click(screen.getByTestId("suggested-edit-reject"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/api/v1/edit/e-1/status"));
    expect(calls.length).toBe(1);
    expect(JSON.parse(calls[0][1].body).status).toBe("REJECTED");
  });
});

test("KnowledgeGapCard show-more reveals content > 240 chars", () => {
  const longGap = { ...baseGap, content: "x".repeat(300) };
  render(<KnowledgeGapCard gap={longGap} />);
  const btn = screen.getByTestId("knowledge-gap-show-more");
  expect(btn).toBeInTheDocument();
  fireEvent.click(btn);
  expect(screen.queryByTestId("knowledge-gap-show-more")).toBeNull();
});

test("SuggestedEditCard show-more reveals payload > 320 chars", () => {
  const longEdit = { ...baseEdit, payload: "y".repeat(400) };
  render(<SuggestedEditCard edit={longEdit} />);
  const btn = screen.getByTestId("suggested-edit-show-more");
  expect(btn).toBeInTheDocument();
  fireEvent.click(btn);
  expect(screen.queryByTestId("suggested-edit-show-more")).toBeNull();
});
