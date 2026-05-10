import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TasksScreen } from "../components/TasksScreen";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("TasksScreen CREATE button disables while POST in-flight, re-enables after", async () => {
  let resolvePost: (r: Response) => void = () => {};
  vi.stubGlobal("fetch", vi.fn((url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Promise<Response>(res => { resolvePost = res; });
    }
    return Promise.resolve(new Response(JSON.stringify({ tasks: [] }), { status: 200 }));
  }));
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);

  const submit = await screen.findByTestId("task-create-btn");
  expect(submit).not.toBeDisabled();
  fireEvent.click(submit);

  await waitFor(() => expect(submit).toBeDisabled());

  resolvePost(new Response(JSON.stringify({
    id: "T1", subject: "PS", title: "Tema A — derivation example",
    deadline: new Date().toISOString(), status: "ACTIVE",
  }), { status: 201, headers: { "content-type": "application/json" } }));

  await waitFor(() => expect(submit).not.toBeDisabled());
});
