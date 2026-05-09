import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { Sidebar } from "../components/Sidebar";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

function stubTasks(tasks: Array<{ id: string; subject: string; title: string; deadline: string; status: string }>) {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
}

test("renders empty state when no tasks", async () => {
  stubTasks([]);
  render(<MemoryRouter><Sidebar /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("sidebar-empty")).toBeInTheDocument());
});

test("groups tasks by subject and orders by deadline ascending", async () => {
  const now = Date.now();
  stubTasks([
    { id: "T1", subject: "PA", title: "later PA",   deadline: new Date(now + 7 * 86400000).toISOString(), status: "open" },
    { id: "T2", subject: "PA", title: "earlier PA", deadline: new Date(now + 2 * 86400000).toISOString(), status: "open" },
    { id: "T3", subject: "PS", title: "ps task",    deadline: new Date(now + 5 * 86400000).toISOString(), status: "open" },
  ]);
  render(<MemoryRouter><Sidebar /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("sidebar-subject-PA")).toBeInTheDocument());
  expect(screen.getByTestId("sidebar-subject-PS")).toBeInTheDocument();
  // Tasks within PA should be ordered earliest-first: "earlier PA" appears before "later PA".
  const buttons = screen.getAllByTestId("sidebar-task");
  const titles = buttons.map(b => b.textContent ?? "");
  const earlierIdx = titles.findIndex(t => t.includes("earlier PA"));
  const laterIdx = titles.findIndex(t => t.includes("later PA"));
  expect(earlierIdx).toBeGreaterThanOrEqual(0);
  expect(laterIdx).toBeGreaterThan(earlierIdx);
});

test("highlights active task by data-task-id match", async () => {
  const now = Date.now();
  stubTasks([
    { id: "T-ACTIVE", subject: "PA", title: "active one",  deadline: new Date(now + 3 * 86400000).toISOString(), status: "open" },
    { id: "T-OTHER",  subject: "PA", title: "other one",   deadline: new Date(now + 4 * 86400000).toISOString(), status: "open" },
  ]);
  render(<MemoryRouter><Sidebar activeTaskId="T-ACTIVE" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("sidebar-subject-PA")).toBeInTheDocument());
  const active = screen.getAllByTestId("sidebar-task").find(b => b.getAttribute("data-task-id") === "T-ACTIVE")!;
  const other  = screen.getAllByTestId("sidebar-task").find(b => b.getAttribute("data-task-id") === "T-OTHER")!;
  expect(active.className).toContain("font-bold");
  expect(other.className).not.toContain("font-bold");
});

test("NEW TASK button navigates to QuickStart route (?pick=1)", async () => {
  stubTasks([]);
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Sidebar />
      <LocationProbe />
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByTestId("sidebar-empty")).toBeInTheDocument());
  fireEvent.click(screen.getByTestId("sidebar-new-task"));
  await waitFor(() => expect(screen.getByTestId("location-probe").textContent).toContain("pick=1"));
});

function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="location-probe">{loc.pathname}{loc.search}</div>;
}
