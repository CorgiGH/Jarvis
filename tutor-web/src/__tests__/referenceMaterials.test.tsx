import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("reference materials rail renders when GET /tasks/{id} returns paths", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/T1")) {
      return new Response(JSON.stringify({
        id: "T1", subject: "PA", title: "Tema 5",
        deadline: new Date().toISOString(), status: "ACTIVE",
        materialPaths: ["_extras/PA/study_guide/laplace.pdf", "_extras/PA/seminars/sem3.pdf"],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  const rail = await waitFor(() => screen.getByTestId("reference-materials"));
  expect(rail.textContent).toMatch(/REFERENCE MATERIALS \(2\)/);
  expect(rail.textContent).toMatch(/laplace\.pdf/);
  expect(rail.textContent).toMatch(/sem3\.pdf/);
});

test("reference materials rail absent when paths empty", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/T2")) {
      return new Response(JSON.stringify({ materialPaths: [] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T2" /></MemoryRouter>);
  await new Promise(r => setTimeout(r, 0));
  expect(screen.queryByTestId("reference-materials")).toBeNull();
});
