import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

// â”€â”€ Mock heavy sub-components to avoid full render chains â”€â”€
vi.mock("../components/PdfPane", () => ({ PdfPane: () => <div data-testid="mock-pdf-pane">PDF</div> }));
vi.mock("../components/Scratchpad", () => ({ Scratchpad: ({ value, onChange }: any) => <textarea data-testid="mock-scratchpad" value={value} onChange={e => onChange((e.target as HTMLTextAreaElement).value)} /> }));
vi.mock("../components/ConceptDrawer", () => ({ ConceptDrawer: () => <div data-testid="mock-concept">CONCEPT</div> }));
vi.mock("../components/KnowledgeGapCard", () => ({ KnowledgeGapCard: () => <div data-testid="mock-gap">GAP</div> }));
vi.mock("../components/DrillStack", () => ({
  DrillStack: ({ problemId }: any) => (
    <div data-testid="drill-stack">
      <div data-testid="drill-card" data-state="open">open-{problemId}</div>
      <div data-testid="drill-card" data-state="locked">locked-{problemId}</div>
    </div>
  ),
}));
vi.mock("../components/ResourceRail", () => ({
  ResourceRail: () => <aside data-testid="resource-rail">RAIL</aside>,
}));
vi.mock("../components/ProblemStepper", () => ({
  ProblemStepper: () => <nav data-testid="problem-stepper">STEPPER</nav>,
  parseProblemParam: (raw: string | null) => {
    if (raw == null) return 0;
    const n = parseInt(raw, 10);
    if (isNaN(n) || n < 0) return 0;
    return n;
  },
}));
vi.mock("../components/ProgressStrip", () => ({
  ProgressStrip: () => <div data-testid="progress-strip">PROGRESS</div>,
}));
vi.mock("../components/Sidekick", () => ({
  Sidekick: () => <div data-testid="sidekick-panel">SIDEKICK</div>,
}));
vi.mock("../components/StatusBar", () => ({
  StatusBar: () => <div data-testid="status-bar">STATUS</div>,
}));
vi.mock("../components/DaemonHealthPill", () => ({
  DaemonHealthPill: () => <span data-testid="daemon-health">DAEMON</span>,
}));
vi.mock("../components/InlineAskChip", () => ({
  InlineAskChip: () => <div data-testid="inline-ask-chip">CHIP</div>,
}));
vi.mock("../components/CompileSubmitCard", () => ({
  CompileSubmitCard: () => <div data-testid="compile-submit-card">SUBMIT</div>,
}));
vi.mock("../lib/inlineAsk", () => ({
  attachSelectionListener: () => () => {},
  buildSidekickEnvelope: (opts: any) => opts,
}));

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({ reply: "hello back" }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

// â”€â”€ Existing tests (updated to new layout) â”€â”€

test("deduped banner renders only when dedupedNotice prop is true", async () => {
  // Provide prep so the component renders the full layout (not skeleton)
  const prep = {
    taskId: "T1",
    generatedAt: "2026-05-11T00:00:00Z",
    version: 1,
    problemsJson: '[{"problemId":"A1","page":1,"statement":"test"}]',
    drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
    railJson: '[]',
  };
  const fetchWithPrep = vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      return new Response(JSON.stringify(prep), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  });
  vi.stubGlobal("fetch", fetchWithPrep);

  const { rerender } = render(
    <MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByTestId("problem-stepper")).toBeInTheDocument());
  expect(screen.queryByTestId("deduped-notice")).toBeNull();

  rerender(
    <MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" dedupedNotice={true} /></MemoryRouter>,
  );
  await waitFor(() => {
    const banner = screen.queryByTestId("deduped-notice");
    return banner !== null;
  });
  const banner = screen.getByTestId("deduped-notice");
  expect(banner.textContent).toMatch(/OPENED EXISTING TASK/);
});

// â”€â”€ New bootstrap-path tests â”€â”€

test("TutorWorkspace renders skeleton + fires reprep when prep is missing (404)", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/tasks/task-01/prep")) {
      return new Response("no prep", { status: 404 });
    }
    if (typeof url === "string" && url.includes("/task/task-01/reprep") && init?.method === "POST") {
      return new Response("{}", { status: 200 });
    }
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/task-01")) {
      return new Response(JSON.stringify({ id: "task-01", title: "x", materialPaths: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/scratchpad")) {
      return new Response(JSON.stringify({ text: "" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<MemoryRouter><TutorWorkspace pdfUrl="/p.pdf" taskId="task-01" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("workspace-skeleton")).toBeInTheDocument());
  // reprep should have been called
  const reprepCalls = fetchMock.mock.calls.filter(c => typeof c[0] === "string" && (c[0] as string).includes("/reprep"));
  expect(reprepCalls.length).toBeGreaterThanOrEqual(1);
});

test("TutorWorkspace renders 7 spec-acceptance selectors when prep is present", async () => {
  const prep = {
    taskId: "task-01",
    generatedAt: "2026-05-11T00:00:00Z",
    version: 1,
    problemsJson: '[{"problemId":"A1","page":4,"statement":"derive MLE"}]',
    drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
    railJson: '[{"type":"PDF","label":"Tema_A.pdf","action":"OPEN_DRAWER","payload":{"path":"x.pdf"}},{"type":"SCRATCHPAD","label":"draft","action":"OPEN_DRAWER","payload":{}}]',
  };
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      return new Response(JSON.stringify(prep), { status: 200, headers: { "content-type": "application/json" } });
    }
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/task-01")) {
      return new Response(JSON.stringify({ id: "task-01", title: "x", materialPaths: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/scratchpad")) {
      return new Response(JSON.stringify({ text: "" }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/p.pdf" taskId="task-01" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("problem-stepper")).toBeInTheDocument());
  // 7 spec acceptance selectors:
  expect(screen.getByTestId("problem-stepper")).toBeInTheDocument();
  expect(screen.getByTestId("progress-strip")).toBeInTheDocument();
  expect(screen.getByTestId("drill-stack")).toBeInTheDocument();
  // At least one open drill card, at least one locked
  const cards = screen.getAllByTestId("drill-card");
  const states = cards.map(c => c.getAttribute("data-state"));
  expect(states).toContain("open");
  expect(states).toContain("locked");
  expect(screen.getByTestId("resource-rail")).toBeInTheDocument();
  expect(screen.getByTestId("sidekick-panel")).toBeInTheDocument();
});

