import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter, Routes, Route, useLocation } from "react-router-dom";
import { TaskQuickStart } from "../components/TaskQuickStart";

function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}{loc.search}</div>;
}

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("dedup-200 navigates with deduped=1 query param", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && (!init || init.method === undefined)) {
      return new Response(JSON.stringify({ tasks: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Response(JSON.stringify({
        id: "EXISTING-123", subject: "PA", title: "Partial 2021",
        deadline: "2026-05-21T00:00:00Z", status: "ACTIVE",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
  render(
    <MemoryRouter initialEntries={["/?pick=1"]}>
      <Routes>
        <Route path="/" element={<><TaskQuickStart /><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByTestId("task-preset-PA")).not.toBeDisabled());
  fireEvent.click(screen.getByTestId("task-preset-PA"));
  await waitFor(() => expect(screen.getByTestId("loc").textContent).toMatch(/deduped=1/));
  expect(screen.getByTestId("loc").textContent).toContain("taskId=EXISTING-123");
});

test("fresh-201 navigates without deduped flag", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && (!init || init.method === undefined)) {
      return new Response(JSON.stringify({ tasks: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Response(JSON.stringify({
        id: "NEW-456", subject: "PA", title: "Partial 2021",
        deadline: "2026-05-21T00:00:00Z", status: "ACTIVE",
      }), { status: 201, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
  render(
    <MemoryRouter initialEntries={["/?pick=1"]}>
      <Routes>
        <Route path="/" element={<><TaskQuickStart /><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByTestId("task-preset-PA")).not.toBeDisabled());
  fireEvent.click(screen.getByTestId("task-preset-PA"));
  await waitFor(() => expect(screen.getByTestId("loc").textContent).toContain("taskId=NEW-456"));
  expect(screen.getByTestId("loc").textContent).not.toMatch(/deduped/);
});
