import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TasksScreen } from "../components/TasksScreen";

const sampleTask = {
  id: "T-001", subject: "PS", title: "Tema A — derivation",
  deadline: new Date(Date.now() + 5 * 86400000).toISOString(),
  status: "ACTIVE",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=tt", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url !== "string") return new Response("{}", { status: 200 });
    if (url.endsWith("/api/v1/tasks") && (!init || (init.method ?? "GET") === "GET")) {
      return new Response(JSON.stringify({ tasks: [sampleTask] }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    if (url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Response(JSON.stringify({ ...sampleTask, id: "T-NEW" }), {
        status: 201, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("renders create form + lists existing tasks", async () => {
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("tasks-list")).toBeInTheDocument());
  expect(screen.getByText(/Tema A — derivation/)).toBeInTheDocument();
  // PS appears in subject preset button + task row; assert at least one.
  expect(screen.getAllByText(/PS/).length).toBeGreaterThan(0);
});

test("subject preset switches selected subject", () => {
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  fireEvent.click(screen.getByTestId("task-subject-PA"));
  const customInput = screen.getByTestId("task-subject-custom") as HTMLInputElement;
  expect(customInput.value).toBe("PA");
});

test("create POSTs payload with computed deadline", async () => {
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  await waitFor(() => screen.getByTestId("task-create-form"));
  fireEvent.change(screen.getByTestId("task-title"), { target: { value: "PS Tema A" } });
  fireEvent.change(screen.getByTestId("task-deadline-days"), { target: { value: "12" } });
  fireEvent.click(screen.getByTestId("task-create-btn"));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0].endsWith("/api/v1/tasks") && c[1]?.method === "POST");
    expect(calls.length).toBe(1);
    const body = JSON.parse(calls[0][1].body);
    expect(body.subject).toBe("PS");
    expect(body.title).toBe("PS Tema A");
    expect(typeof body.deadline).toBe("string");
    expect(new Date(body.deadline).getTime()).toBeGreaterThan(Date.now());
  });
});

test("OPEN link points at workspace with taskId query", async () => {
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  await waitFor(() => screen.getByTestId("task-open-btn"));
  const a = screen.getByTestId("task-open-btn") as HTMLAnchorElement;
  expect(a.getAttribute("href")).toBe("/?taskId=T-001");
});

test("error shown on server failure", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Response("validation failed", { status: 400 });
    }
    return new Response(JSON.stringify({ tasks: [] }), { status: 200 });
  }));
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  await waitFor(() => screen.getByTestId("task-create-form"));
  fireEvent.click(screen.getByTestId("task-create-btn"));
  await waitFor(() => expect(screen.getByTestId("tasks-error").textContent).toMatch(/HTTP 400/));
});
