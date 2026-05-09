import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { TrustSettings } from "../components/TrustSettings";

const sampleGrant = {
  id: "G1", scope: ["file:///c/work/**"], ops: ["APPLY_EDIT"],
  expiresAt: "2026-05-09T13:00:00Z", callsUsed: 2, maxCalls: 10,
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=ttt", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url !== "string") return new Response("{}", { status: 200 });
    if (url.endsWith("/api/v1/grants") && (!init || (init.method ?? "GET") === "GET")) {
      return new Response(JSON.stringify({ grants: [sampleGrant] }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    if (url.endsWith("/api/v1/grants") && init?.method === "POST") {
      return new Response(JSON.stringify({ ...sampleGrant, id: "Gnew" }), {
        status: 201, headers: { "content-type": "application/json" },
      });
    }
    if (url.includes("/revoke")) {
      return new Response("", { status: 204 });
    }
    return new Response("{}", { status: 200 });
  }));
});

afterEach(() => { vi.unstubAllGlobals(); });

test("loads + lists active grants", async () => {
  render(<TrustSettings />);
  await waitFor(() => {
    expect(screen.getByTestId("trust-grants-list")).toBeInTheDocument();
  });
  expect(screen.getByText(/file:\/\/\/c\/work\/\*\*/)).toBeInTheDocument();
  expect(screen.getByText(/2\/10 calls/)).toBeInTheDocument();
});

test("create form POSTs with chosen scope, ttl, maxCalls", async () => {
  render(<TrustSettings />);
  await waitFor(() => screen.getByTestId("trust-create-form"));
  fireEvent.change(screen.getByTestId("trust-scope-input"), {
    target: { value: "file:///c/uaic/**" },
  });
  fireEvent.change(screen.getByTestId("trust-ttl-input"), { target: { value: "120" } });
  fireEvent.change(screen.getByTestId("trust-max-calls-input"), { target: { value: "20" } });
  fireEvent.click(screen.getByTestId("trust-create-btn"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].endsWith("/api/v1/grants") && c[1]?.method === "POST");
    expect(calls.length).toBe(1);
    const body = JSON.parse(calls[0][1].body);
    expect(body.scope).toEqual(["file:///c/uaic/**"]);
    expect(body.ttlSeconds).toBe(120 * 60);
    expect(body.maxCalls).toBe(20);
  });
});

test("REVOKE button POSTs to revoke endpoint and refreshes", async () => {
  render(<TrustSettings />);
  await waitFor(() => screen.getByTestId("trust-revoke-btn"));
  fireEvent.click(screen.getByTestId("trust-revoke-btn"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].includes("/revoke"));
    expect(calls.length).toBe(1);
    expect(calls[0][0]).toContain("/api/v1/grants/G1/revoke");
    expect(calls[0][1].method).toBe("POST");
  });
});

test("surfaces server error on create failure", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/grants") && init?.method === "POST") {
      return new Response("rate limit: 5/hour", { status: 429 });
    }
    return new Response(JSON.stringify({ grants: [] }), { status: 200 });
  }));
  render(<TrustSettings />);
  await waitFor(() => screen.getByTestId("trust-create-form"));
  fireEvent.click(screen.getByTestId("trust-create-btn"));
  await waitFor(() => {
    expect(screen.getByTestId("trust-error").textContent).toMatch(/HTTP 429/);
  });
});
