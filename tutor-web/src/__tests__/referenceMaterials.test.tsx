/**
 * referenceMaterials.test.tsx
 *
 * Prior to Slice 1.5 C1, TutorWorkspace fetched GET /tasks/{id} and rendered
 * a `reference-materials` panel when materialPaths was non-empty. That panel
 * was removed in the C1 rewire — material paths are now surfaced via the
 * ResourceRail (rail items built on the server in A3/reprep). These tests are
 * updated to verify the new bootstrap behaviour instead.
 */
import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

// ── Mocks ──
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
  ResourceRail: ({ items }: any) => (
    <aside data-testid="resource-rail" data-item-count={items.length}>RAIL</aside>
  ),
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
afterEach(() => { vi.unstubAllGlobals(); });

test("ResourceRail renders with rail items from prep when prep is present", async () => {
  const prep = {
    taskId: "T1",
    generatedAt: "2026-05-11T00:00:00Z",
    version: 1,
    problemsJson: '[{"problem_id":"A1","page":1,"statement":"test"}]',
    drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
    railJson: '[{"type":"PDF","label":"laplace.pdf","action":"OPEN_DRAWER","payload":{"path":"laplace.pdf"}},{"type":"SCRATCHPAD","label":"draft","action":"OPEN_DRAWER","payload":{}}]',
  };
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      return new Response(JSON.stringify(prep), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("resource-rail")).toBeInTheDocument());
  // Mock ResourceRail exposes item count via data-item-count
  expect(screen.getByTestId("resource-rail").getAttribute("data-item-count")).toBe("2");
});

test("reference-materials panel is absent in new layout (moved to ResourceRail)", async () => {
  // The old `reference-materials` testid no longer exists — material paths
  // are surfaced via ResourceRail items built server-side. Assert it's gone.
  const prep = {
    taskId: "T2",
    generatedAt: "2026-05-11T00:00:00Z",
    version: 1,
    problemsJson: '[{"problem_id":"A1","page":1,"statement":"test"}]',
    drillsJson: '{"A1":{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"h"}}',
    railJson: '[]',
  };
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/prep")) {
      return new Response(JSON.stringify(prep), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T2" /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("resource-rail")).toBeInTheDocument());
  expect(screen.queryByTestId("reference-materials")).toBeNull();
});
