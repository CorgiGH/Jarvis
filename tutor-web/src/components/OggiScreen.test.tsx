import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter, Routes, Route, useLocation } from "react-router-dom";
import { OggiScreen } from "./OggiScreen";
import * as taskPrep from "../lib/taskPrep";
import type { QueueItem } from "../lib/taskPrep";

/** Renders the current pathname so navigate-behaviour tests can assert routing. */
function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname}</div>;
}

const ITEMS: QueueItem[] = [
  {
    kc_id: "kc-1",
    kc_name_ro: "Recursie",
    kc_name_en: "Recursion",
    subject: "PA",
    phase: "practice",
    mastery_ewma: 0.55,
    fsrs_card_id: null,
    verification_status: "faithful",
    worked_example_first: false,
    mode: "drill",
  },
  {
    kc_id: "kc-2",
    kc_name_ro: "Arbori",
    kc_name_en: "Trees",
    subject: "PA",
    phase: "intro",
    mastery_ewma: 0.1,
    fsrs_card_id: null,
    verification_status: "unverified",
    worked_example_first: true,
    mode: "worked",
  },
  {
    kc_id: "kc-3",
    kc_name_ro: "Sortare",
    kc_name_en: "Sorting",
    subject: "ALO",
    phase: "retrieval",
    mastery_ewma: 0.85,
    fsrs_card_id: "card-3",
    verification_status: "faithful",
    worked_example_first: false,
    mode: "retrieve",
  },
];

describe("OggiScreen", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders queue + next-kc panels when getQueueToday returns 3 items", async () => {
    vi.spyOn(taskPrep, "getQueueToday").mockResolvedValue({
      items: ITEMS,
      total_due: 3,
      day: "2026-06-09",
    });

    render(<MemoryRouter><OggiScreen /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByTestId("oggi-screen")).toBeInTheDocument();
      expect(screen.getByTestId("oggi-queue-panel")).toBeInTheDocument();
      expect(screen.getByTestId("oggi-next-kc-panel")).toBeInTheDocument();
    });

    // queue list rendered inside queue panel
    expect(screen.getByTestId("learner-queue-list")).toBeInTheDocument();
    // next-kc panel shows the top item
    expect(screen.getByTestId("oggi-next-kc-panel")).toHaveTextContent("Recursie");
  });

  it("renders oggi-empty when queue is empty", async () => {
    vi.spyOn(taskPrep, "getQueueToday").mockResolvedValue({
      items: [],
      total_due: 0,
      day: "2026-06-09",
    });

    render(<MemoryRouter><OggiScreen /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByTestId("oggi-empty")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("oggi-queue-panel")).toBeNull();
    expect(screen.queryByTestId("oggi-next-kc-panel")).toBeNull();
  });

  it("renders oggi-error when getQueueToday throws", async () => {
    vi.spyOn(taskPrep, "getQueueToday").mockRejectedValue(new Error("network fail"));

    render(<MemoryRouter><OggiScreen /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByTestId("oggi-error")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("oggi-queue-panel")).toBeNull();
  });

  it("shows oggi-screen testid on first paint (loading state)", () => {
    vi.spyOn(taskPrep, "getQueueToday").mockReturnValue(new Promise(() => {})); // never resolves
    render(<MemoryRouter><OggiScreen /></MemoryRouter>);
    expect(screen.getByTestId("oggi-screen")).toBeInTheDocument();
  });

  it("Începe button navigates to /lesson/{kc_id} (E10 wire-up)", async () => {
    vi.spyOn(taskPrep, "getQueueToday").mockResolvedValue({
      items: ITEMS,
      total_due: 3,
      day: "2026-06-12",
    });

    render(
      <MemoryRouter initialEntries={["/oggi"]}>
        <Routes>
          <Route path="/oggi" element={<><OggiScreen /><LocationProbe /></>} />
          <Route path="/lesson/:kcId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    // Wait for queue to load and the panel to render
    await waitFor(() => expect(screen.getByTestId("oggi-next-kc-panel")).toBeInTheDocument());

    // Click Începe (the button inside NextKcPanel)
    fireEvent.click(screen.getByRole("button", { name: /Începe/i }));

    // Top item is ITEMS[0] with kc_id "kc-1"
    await waitFor(() =>
      expect(screen.getByTestId("loc").textContent).toBe("/lesson/kc-1"),
    );
  });
});
