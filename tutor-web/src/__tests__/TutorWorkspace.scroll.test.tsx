/**
 * TutorWorkspace layout / scroll tests.
 *
 * Prior to Slice 1.5 C1, these tests targeted the old PdfPane + ChatPane layout.
 * That layout is gone — TutorWorkspace now renders:
 *   header → ProblemStepper → ProgressStrip → flex(main + ResourceRail) → StatusBar
 *
 * Scroll behaviour for the DrillStack main column is tested here.
 * The equivalent Playwright visual gate (7 selectors) lives in the E2E suite.
 */
import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

// ── Mock all heavy sub-components (same set as TutorWorkspace.test.tsx) ──
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

const PREP_FIXTURE = {
  taskId: "T1",
  generatedAt: "2026-05-11T00:00:00Z",
  version: 1,
  problemsJson: '[{"problem_id":"A1","page":1,"statement":"test"}]',
  drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
  railJson: '[]',
};

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      return new Response(JSON.stringify(PREP_FIXTURE), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("workspace outer container is flex (not grid) so flex children get bounded height", async () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  // Wait for prep to load and full layout to render
  await waitFor(() => expect(screen.getByTestId("problem-stepper")).toBeInTheDocument());

  // The root workspace div should be flex + flex-col
  const drill = screen.getByTestId("drill-stack");
  // Walk up to find the main column container (flex-1 overflow-y-auto)
  let mainCol: HTMLElement | null = drill.parentElement;
  while (mainCol && !/\boverflow-y-auto\b/.test(mainCol.className)) {
    mainCol = mainCol.parentElement;
  }
  expect(mainCol, "expected overflow-y-auto main column ancestor").not.toBeNull();
  expect(mainCol!.className).toMatch(/\bflex\b/);
  expect(mainCol!.className).toMatch(/\bflex-col\b/);
});

test("main column carries min-w-0 so flex sibling (ResourceRail) can shrink", async () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("drill-stack")).toBeInTheDocument());

  const drill = screen.getByTestId("drill-stack");
  let mainCol: HTMLElement | null = drill.parentElement;
  while (mainCol && !/\bmin-w-0\b/.test(mainCol.className)) {
    mainCol = mainCol.parentElement;
  }
  expect(mainCol, "expected min-w-0 on main column").not.toBeNull();
  expect(mainCol!.className).toMatch(/\bmin-w-0\b/);
});

test("main scroll column uses overflow-y-auto for content overflow", async () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("drill-stack")).toBeInTheDocument());

  const drill = screen.getByTestId("drill-stack");
  let scrollCol: HTMLElement | null = drill.parentElement;
  while (scrollCol && !/\boverflow-y-auto\b/.test(scrollCol.className)) {
    scrollCol = scrollCol.parentElement;
  }
  expect(scrollCol, "expected overflow-y-auto scroll column").not.toBeNull();
  expect(scrollCol!.className).toMatch(/\boverflow-y-auto\b/);
});
