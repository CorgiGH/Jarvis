import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter, Routes, Route, useLocation } from "react-router-dom";
import { App } from "../App";
import { ThemeProvider } from "../theme/ThemeProvider";

function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}{loc.search}</div>;
}

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  localStorage.clear();
});
afterEach(() => { vi.unstubAllGlobals(); });

test("App prefers server jarvis_last_task cookie over localStorage on cold mount", async () => {
  localStorage.setItem("jarvis.lastTaskId", "TASK_LOCAL");
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/me/export")) {
      return new Response(JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/last-task")) {
      return new Response(JSON.stringify({ taskId: "TASK_SERVER" }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tutor/auto-session")) {
      return new Response("{}", { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks: [{ id: "TASK_SERVER" }] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<><ThemeProvider><App /></ThemeProvider><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByTestId("loc").textContent).toContain("TASK_SERVER");
  });
});

test("App falls back to localStorage when server cookie absent", async () => {
  localStorage.setItem("jarvis.lastTaskId", "TASK_LOCAL");
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/me/export")) {
      return new Response(JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/last-task")) {
      return new Response(JSON.stringify({ taskId: null }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tutor/auto-session")) {
      return new Response("{}", { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks: [{ id: "TASK_LOCAL" }] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<><ThemeProvider><App /></ThemeProvider><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByTestId("loc").textContent).toContain("TASK_LOCAL");
  });
});
