import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { DeliverableTracker } from "./DeliverableTracker";
import type { DeliverablesReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

// Mock practiceApi so no real network calls happen.
vi.mock("../../lib/practiceApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/practiceApi")>();
  return {
    ...actual,
    listDeliverables: vi.fn(),
  };
});

import { listDeliverables } from "../../lib/practiceApi";
const mockListDeliverables = listDeliverables as ReturnType<typeof vi.fn>;

const ALO_T1_DELIVERABLE = {
  id: "task-alo-t1",
  subject: "ALO",
  title_ro: "Temă ALO T1",
  deadline: null,
  sub_problems: [{ label_ro: "Temă T1", points: 50 }],
  prep_drill_ids: [],
  source_doc: "https://edu.info.uaic.ro/algebra-liniara/",
  synthetic: false,
};

const PS_A_DELIVERABLE = {
  id: "task-ps-a",
  subject: "PS",
  title_ro: "Temă PS A",
  deadline: null,
  sub_problems: [{ label_ro: "Temă A", points: 20 }],
  prep_drill_ids: ["ps-drill-001"],
  source_doc: "https://edu.info.uaic.ro/probabilitati-si-statistica/",
  synthetic: false,
};

const REPLY_WITH_DELIVERABLES: DeliverablesReply = {
  deliverables: [ALO_T1_DELIVERABLE, PS_A_DELIVERABLE],
};

const REPLY_EMPTY: DeliverablesReply = { deliverables: [] };

describe("DeliverableTracker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── REQ-19: honesty line always visible ──────────────────────────────────────

  it("honesty line (deliverable-honesty-line) is visible on first paint (REQ-19)", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_EMPTY);
    render(<DeliverableTracker />);
    // The honesty line renders immediately, before the fetch resolves
    expect(screen.getByTestId("deliverable-honesty-line")).toBeInTheDocument();
    expect(screen.getByTestId("deliverable-honesty-line")).toHaveTextContent(
      practiceStrings.deliverableHonestyLine,
    );
  });

  it("honesty line contains the exact required text (REQ-19 verbatim)", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_EMPTY);
    render(<DeliverableTracker />);
    expect(screen.getByTestId("deliverable-honesty-line")).toHaveTextContent(
      "Aplicația te pregătește pentru predare — nu notează predările reale; profesorul o face.",
    );
  });

  // ── deliverable-card renders for each deliverable ────────────────────────────

  it("renders deliverable-card for each deliverable", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_WITH_DELIVERABLES);
    render(<DeliverableTracker />);
    await waitFor(() => expect(screen.getAllByTestId("deliverable-card")).toHaveLength(2));
  });

  // ── deliverable-points renders sub_problems ───────────────────────────────────

  it("deliverable-points renders sub-problem points", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_WITH_DELIVERABLES);
    render(<DeliverableTracker />);
    await waitFor(() => expect(screen.getAllByTestId("deliverable-points")).toHaveLength(2));
    // ALO T1 has 50 pt
    expect(screen.getAllByTestId("deliverable-points")[0]).toHaveTextContent("50");
    // PS A has 20 pt
    expect(screen.getAllByTestId("deliverable-points")[1]).toHaveTextContent("20");
  });

  // ── deliverable-deadline: null → "necunoscut" (REQ-21) ─────────────────────

  it("null deadline renders 'necunoscut' (REQ-21)", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_WITH_DELIVERABLES);
    render(<DeliverableTracker />);
    await waitFor(() => expect(screen.getAllByTestId("deliverable-deadline")).toHaveLength(2));
    screen.getAllByTestId("deliverable-deadline").forEach((el) => {
      expect(el).toHaveTextContent(practiceStrings.deliverableDeadlineUnknown);
    });
  });

  // ── deliverable-prep-drills: empty list → honest copy ────────────────────────

  it("empty prep_drill_ids renders the honest empty state copy", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_WITH_DELIVERABLES);
    render(<DeliverableTracker />);
    await waitFor(() => expect(screen.getAllByTestId("deliverable-prep-drills")).toHaveLength(2));
    // ALO T1 has no prep drills
    expect(screen.getAllByTestId("deliverable-prep-drills")[0]).toHaveTextContent(
      practiceStrings.deliverableNoPrepDrills,
    );
    // PS A has a prep drill
    expect(screen.getAllByTestId("deliverable-prep-drills")[1]).not.toHaveTextContent(
      practiceStrings.deliverableNoPrepDrills,
    );
    expect(screen.getAllByTestId("deliverable-prep-drills")[1]).toHaveTextContent("ps-drill-001");
  });

  // ── empty state when no deliverables ─────────────────────────────────────────

  it("shows empty state when deliverables list is empty", async () => {
    mockListDeliverables.mockResolvedValue(REPLY_EMPTY);
    render(<DeliverableTracker />);
    await waitFor(() => expect(mockListDeliverables).toHaveBeenCalled());
    expect(screen.queryByTestId("deliverable-card")).not.toBeInTheDocument();
  });
});
