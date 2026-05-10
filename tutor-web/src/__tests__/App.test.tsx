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

test("default route shows ActiveTaskDashboard (no real task pinned)", async () => {
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
  // Header doesn't show task chip or × close button when on dashboard.
  expect(screen.queryByTestId("pick-another-task-btn")).toBeNull();
});

test("pick=1 query param forces dashboard even with last-task in localStorage", async () => {
  try { localStorage.setItem("jarvis.lastTaskId", "T-REAL"); } catch (_) {}
  render(<MemoryRouter initialEntries={["/?pick=1"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
});

test("× close button clears last-task and returns to dashboard", async () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-REAL"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("pick-another-task-btn")).toBeInTheDocument());
  // Persisted by the cold-start effect.
  expect(localStorage.getItem("jarvis.lastTaskId")).toBe("T-REAL");
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.click(screen.getByTestId("pick-another-task-btn"));
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
  expect(localStorage.getItem("jarvis.lastTaskId")).toBeNull();
});

test("real taskId pinned in URL renders TutorWorkspace", async () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-REAL"]}><App /></MemoryRouter>);
  // Wait for the existence-check to pass so workspace renders instead of dashboard.
  await waitFor(() => expect(screen.getByTestId("pdf-pane")).toBeInTheDocument());
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
  expect(screen.getAllByText(/T-REAL/).length).toBeGreaterThan(0);
});

test("missing taskId falls back to dashboard even when explicit", async () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-NONEXISTENT"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
});

test("manual-entry path reveals TaskQuickStart presets", async () => {
  const { fireEvent } = await import("@testing-library/react");
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
  fireEvent.click(screen.getByTestId("active-task-manual-btn"));
  await waitFor(() => expect(screen.getByTestId("task-preset-PS")).toBeInTheDocument());
  expect(screen.getByTestId("task-preset-PA")).toBeInTheDocument();
});
