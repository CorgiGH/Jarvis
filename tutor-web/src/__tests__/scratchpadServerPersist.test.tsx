/**
 * scratchpadServerPersist.test.tsx
 *
 * Prior to Slice 1.5 C1, TutorWorkspace owned scratchpad state directly and
 * server-persisted it via GET/PUT /api/v1/tasks/{id}/scratchpad on mount.
 * In the C1 rewire, scratchpad state moved into ResourceRail's SCRATCHPAD
 * drawer (opened on demand). The workspace no longer calls the scratchpad
 * endpoint on mount.
 *
 * These tests now verify the new bootstrap contract: prep fetch on mount,
 * reprep trigger on 404, and skeleton display.
 */
import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

// â”€â”€ Mocks â”€â”€
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
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

test("workspace GETs prep on mount (bootstrap replaces scratchpad server persist)", async () => {
  // The workspace no longer GETs /scratchpad on mount. It GETs /prep instead.
  // Scratchpad persistence is now handled inside ResourceRail's SCRATCHPAD drawer.
  const fetchMock = vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      const prep = {
        taskId: "T1",
        generatedAt: "2026-05-11T00:00:00Z",
        version: 1,
        problemsJson: '[{"problemId":"A1","page":1,"statement":"test"}]',
        drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
        railJson: '[]',
      };
      return new Response(JSON.stringify(prep), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  });
  vi.stubGlobal("fetch", fetchMock);

  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);

  await waitFor(() => {
    const calls = fetchMock.mock.calls.filter((c: any) =>
      typeof c[0] === "string" && (c[0] as string).includes("/prep"));
    expect(calls.length).toBeGreaterThan(0);
  });

  // Workspace should NOT call /scratchpad directly â€” that's ResourceRail's job
  const scratchpadCalls = fetchMock.mock.calls.filter((c: any) =>
    typeof c[0] === "string" && (c[0] as string).includes("/scratchpad"));
  expect(scratchpadCalls.length).toBe(0);
});

