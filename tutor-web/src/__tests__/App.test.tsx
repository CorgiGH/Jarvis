import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=cc", configurable: true, writable: true });
  // Clear localStorage so last-task fallback doesn't leak between tests.
  try { localStorage.clear(); } catch (_) {}
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks")) {
      return new Response(JSON.stringify({
        tasks: [{ id: "T-REAL", subject: "PS", title: "Tema A", deadline: new Date(Date.now() + 5 * 86400000).toISOString(), status: "ACTIVE" }],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("default route shows QuickStart panel (no real task pinned)", async () => {
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("task-quickstart")).toBeInTheDocument());
  // Header AND quickstart references TEST-TASK-A.
  expect(screen.getAllByText(/TEST-TASK-A/).length).toBeGreaterThan(0);
});

test("real taskId pinned in URL renders TutorWorkspace", async () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-REAL"]}><App /></MemoryRouter>);
  // Wait for the existence-check to pass so workspace renders instead of QuickStart.
  await waitFor(() => expect(screen.getByTestId("pdf-pane")).toBeInTheDocument());
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
  expect(screen.getAllByText(/T-REAL/).length).toBeGreaterThan(0);
});

test("missing taskId falls back to QuickStart even when explicit", async () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-NONEXISTENT"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("task-quickstart")).toBeInTheDocument());
});

test("QuickStart presets visible", async () => {
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("task-quickstart")).toBeInTheDocument());
  expect(screen.getByTestId("task-preset-PS")).toBeInTheDocument();
  expect(screen.getByTestId("task-preset-PA")).toBeInTheDocument();
});
