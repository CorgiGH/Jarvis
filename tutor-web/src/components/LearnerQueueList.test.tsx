import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LearnerQueueList } from "./LearnerQueueList";
import type { QueueItem } from "../lib/taskPrep";

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
    fsrs_card_id: "card-2",
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

describe("LearnerQueueList", () => {
  it("renders 3 rows for a 3-item fixture", () => {
    render(<LearnerQueueList items={ITEMS} onSelect={() => {}} />);
    expect(screen.getByTestId("learner-queue-list")).toBeInTheDocument();
    expect(screen.getByTestId("queue-item-kc-1")).toBeInTheDocument();
    expect(screen.getByTestId("queue-item-kc-2")).toBeInTheDocument();
    expect(screen.getByTestId("queue-item-kc-3")).toBeInTheDocument();
  });

  it("renders phase badge per row", () => {
    render(<LearnerQueueList items={ITEMS} onSelect={() => {}} />);
    expect(screen.getByTestId("queue-item-phase-kc-1")).toHaveTextContent("practice");
    expect(screen.getByTestId("queue-item-phase-kc-2")).toHaveTextContent("intro");
    expect(screen.getByTestId("queue-item-phase-kc-3")).toHaveTextContent("retrieval");
  });

  it("renders mastery sparkline per row with correct band", () => {
    render(<LearnerQueueList items={ITEMS} onSelect={() => {}} />);
    const m1 = screen.getByTestId("queue-item-mastery-kc-1");
    expect(m1).toBeInTheDocument();
    // kc-1: ewma=0.55 → mid
    const sparkline1 = m1.querySelector("[data-testid='mastery-sparkline']");
    expect(sparkline1).toHaveAttribute("data-mastery-band", "mid");
    // kc-3: ewma=0.85 → high
    const m3 = screen.getByTestId("queue-item-mastery-kc-3");
    const sparkline3 = m3.querySelector("[data-testid='mastery-sparkline']");
    expect(sparkline3).toHaveAttribute("data-mastery-band", "high");
  });

  it("shows queue-empty when list is empty", () => {
    render(<LearnerQueueList items={[]} onSelect={() => {}} />);
    expect(screen.getByTestId("queue-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("learner-queue-list")).toBeNull();
  });

  it("fires onSelect via CTRL+ENTER on first item", () => {
    const onSelect = vi.fn();
    render(<LearnerQueueList items={ITEMS} onSelect={onSelect} />);
    const firstRow = screen.getByTestId("queue-item-kc-1");
    fireEvent.keyDown(firstRow, { key: "Enter", ctrlKey: true });
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(ITEMS[0]);
  });

  it("fires onSelect on click", () => {
    const onSelect = vi.fn();
    render(<LearnerQueueList items={ITEMS} onSelect={onSelect} />);
    fireEvent.click(screen.getByTestId("queue-item-kc-2"));
    expect(onSelect).toHaveBeenCalledWith(ITEMS[1]);
  });
});
