import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { ActiveTaskDashboard } from "../components/ActiveTaskDashboard";
import { TrustSettings } from "../components/TrustSettings";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/grants")) {
      return new Response(JSON.stringify({ grants: [] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("ActiveTaskDashboard empty state has no axe violations", async () => {
  const { container } = render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await new Promise(r => setTimeout(r, 0));
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
  });
  expect(results).toHaveNoViolations();
});

test("TrustSettings empty state has no axe violations", async () => {
  const { container } = render(<MemoryRouter><TrustSettings /></MemoryRouter>);
  await new Promise(r => setTimeout(r, 0));
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
  });
  expect(results).toHaveNoViolations();
});
