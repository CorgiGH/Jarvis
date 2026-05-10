import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ActiveTaskDashboard } from "../components/ActiveTaskDashboard";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("renders empty state when no tasks", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ tasks: [] }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-empty")).toBeInTheDocument());
});

test("renders ranked task list — higher urgency × weight ranks first", async () => {
  const future = (d: number) => new Date(Date.now() + d * 86400000).toISOString();
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({
      tasks: [
        { id: "T1", subject: "PA", title: "Seminar 1", deadline: future(13), status: "ACTIVE" },
        { id: "T2", subject: "PS", title: "Partial 2021", deadline: future(2),  status: "ACTIVE" },
      ],
    }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByTestId("active-task-row").length).toBe(2));
  const rows = screen.getAllByTestId("active-task-row");
  expect(rows[0].getAttribute("data-task-id")).toBe("T2");
  expect(rows[1].getAttribute("data-task-id")).toBe("T1");
});

test("manual entry toggle reveals TaskQuickStart", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ tasks: [] }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-empty")).toBeInTheDocument());
  fireEvent.click(screen.getByTestId("active-task-manual-btn"));
  await waitFor(() => expect(screen.getByTestId("task-quickstart")).toBeInTheDocument());
});
