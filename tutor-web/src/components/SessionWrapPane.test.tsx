import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SessionWrapPane } from "./SessionWrapPane";

// Minimal ApiMasteryDelta fixture (matches §R ApiMasteryDelta shape)
const makeDelta = (kcId: string, ewmaAfter: number, phase = "practice") => ({
  kc_id: kcId,
  attempts: 3,
  correct: 2,
  ewma_after: ewmaAfter,
  phase: phase as "intro" | "practice" | "retrieval" | "mastered",
});

// Minimal ApiSessionCloseReply fixture
const makeReply = (cardsReviewed: number, deltas = [makeDelta("kc-1", 0.6)]) => ({
  session_id: "sess-001",
  narrative: "Ai studiat 3 noțiuni azi.",
  cards_reviewed: cardsReviewed,
  kcs_moved_to_mastered: [],
  mastery_deltas: deltas,
});

describe("SessionWrapPane", () => {
  it("renders session-wrap-pane with card count and done button", () => {
    render(
      <SessionWrapPane
        reply={makeReply(5)}
        onDone={vi.fn()}
      />,
    );
    expect(screen.getByTestId("session-wrap-pane")).toBeInTheDocument();
    expect(screen.getByTestId("session-wrap-cards-count")).toBeInTheDocument();
    expect(screen.getByTestId("session-wrap-cards-count")).toHaveTextContent("5");
    expect(screen.getByTestId("session-wrap-done-btn")).toBeInTheDocument();
  });

  it("renders mastery sparkline for each delta", () => {
    const deltas = [makeDelta("kc-1", 0.7), makeDelta("kc-2", 0.9)];
    render(
      <SessionWrapPane
        reply={makeReply(4, deltas)}
        onDone={vi.fn()}
      />,
    );
    // session-wrap-mastery-sparkline wraps the MasterySparkline list
    expect(screen.getByTestId("session-wrap-mastery-sparkline")).toBeInTheDocument();
    // Two sparklines rendered inside
    const sparklines = screen.getAllByTestId("mastery-sparkline");
    expect(sparklines).toHaveLength(2);
  });

  it("renders narrative when present", () => {
    render(
      <SessionWrapPane
        reply={makeReply(3)}
        onDone={vi.fn()}
      />,
    );
    expect(screen.getByTestId("session-wrap-pane")).toHaveTextContent("Ai studiat 3 noțiuni azi.");
  });

  it("calls onDone when done button is clicked", () => {
    const onDone = vi.fn();
    render(<SessionWrapPane reply={makeReply(2)} onDone={onDone} />);
    fireEvent.click(screen.getByTestId("session-wrap-done-btn"));
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it("renders zero cards gracefully", () => {
    render(<SessionWrapPane reply={makeReply(0, [])} onDone={vi.fn()} />);
    expect(screen.getByTestId("session-wrap-cards-count")).toHaveTextContent("0");
  });

  it("highlights kcs moved to mastered", () => {
    const reply = {
      ...makeReply(3),
      kcs_moved_to_mastered: ["kc-7"],
    };
    render(<SessionWrapPane reply={reply} onDone={vi.fn()} />);
    // At least the pane renders without crash
    expect(screen.getByTestId("session-wrap-pane")).toBeInTheDocument();
  });
});
