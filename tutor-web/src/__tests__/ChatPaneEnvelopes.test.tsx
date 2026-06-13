import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ChatPane } from "../components/ChatPane";
import { chatPane as S } from "../lib/chromeStrings";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("ChatPane renders <concept> envelope as ConceptInline button", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps")) {
      return new Response(JSON.stringify({ gaps: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({
        reply: "explain <concept>laplace</concept> please",
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T-c" />);
  fireEvent.change(screen.getByPlaceholderText(S.inputPlaceholder), { target: { value: "go" } });
  fireEvent.click(screen.getByRole("button", { name: S.sendButton }));
  await waitFor(() => {
    const btn = screen.getByTestId("concept-inline");
    expect(btn).toHaveAttribute("data-concept", "laplace");
  });
});

test("ChatPane renders ```plotly fenced block as PlotlyEmbed", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps")) {
      return new Response(JSON.stringify({ gaps: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({
        reply: "here:\n```plotly\n{\"data\":[{\"y\":[1,2,3]}]}\n```\nfin",
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T-p" />);
  fireEvent.change(screen.getByPlaceholderText(S.inputPlaceholder), { target: { value: "plot" } });
  fireEvent.click(screen.getByRole("button", { name: S.sendButton }));
  await waitFor(() => expect(screen.getByTestId("plotly-embed")).toBeInTheDocument());
});
