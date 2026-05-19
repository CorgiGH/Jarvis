import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { FsrsReview } from "../components/FsrsReview";

vi.mock("../lib/fsrsClient", () => ({
  getDue: vi.fn(),
  gradeCard: vi.fn(),
  getForecast: vi.fn(),
}));

import * as fsrsClient from "../lib/fsrsClient";

const mockCard = {
  id: "c1",
  front: "What is the MLE of μ for Laplace(μ, b)?",
  back: "The sample median — argmin Σ|x_i − μ|.",
  sourceTaskId: "task-ps-1",
  difficulty: 2.1,
  stability: 4.0,
  retrievability: 0.9,
  dueAt: "2026-05-10T10:00:00Z",
  lapses: 0,
};
const mockForecast = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };

beforeEach(() => {
  localStorage.clear();  // FsrsReview persists an in-progress session — isolate tests
  vi.mocked(fsrsClient.getDue).mockResolvedValue([mockCard]);
  vi.mocked(fsrsClient.getForecast).mockResolvedValue(mockForecast);
  vi.mocked(fsrsClient.gradeCard).mockResolvedValue({
    cardId: "c1", nextDueAt: "2026-05-14T10:00:00Z", newDifficulty: 2.2, newStability: 4.5,
  });
});
afterEach(() => { vi.clearAllMocks(); localStorage.clear(); });

describe("FsrsReview — basic render", () => {
  test("renders header with due count and streak", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-header")).toBeInTheDocument());
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/REVIEW/);
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/1 DUE/);
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/12-DAY STREAK/);
  });

  test("shows card front and SHOW ANSWER button before flip", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("card-front")).toBeInTheDocument());
    expect(screen.getByTestId("card-front").textContent).toContain("What is the MLE");
    expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument();
    expect(screen.queryByTestId("grade-buttons")).toBeNull();
  });

  test("empty state when 0 cards due", async () => {
    vi.mocked(fsrsClient.getDue).mockResolvedValue([]);
    render(<FsrsReview streak={3} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-empty")).toBeInTheDocument());
    expect(screen.queryByTestId("card-front")).toBeNull();
  });
});

describe("FsrsReview — 3D flip", () => {
  test("clicking SHOW ANSWER adds is-flipped class to card wrapper", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    const wrapper = screen.getByTestId("card-flip-wrapper");
    expect(wrapper.classList.contains("is-flipped")).toBe(false);
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    expect(wrapper.classList.contains("is-flipped")).toBe(true);
  });

  test("grade buttons appear after flip", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-buttons")).toBeInTheDocument());
    expect(screen.getByTestId("grade-btn-1")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-2")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-4")).toBeInTheDocument();
  });
});

describe("FsrsReview — grading", () => {
  test("clicking GOOD calls gradeCard(id, 3) then advances", async () => {
    vi.mocked(fsrsClient.getDue).mockResolvedValueOnce([
      mockCard,
      { ...mockCard, id: "c2", front: "Second card" },
    ]);
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(vi.mocked(fsrsClient.gradeCard)).toHaveBeenCalledWith("c1", 3));
    await waitFor(() => expect(screen.getByTestId("card-front").textContent).toContain("Second card"));
  });

  test("clicking AGAIN calls gradeCard(id, 1)", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-1")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-1"));
    await waitFor(() => expect(vi.mocked(fsrsClient.gradeCard)).toHaveBeenCalledWith("c1", 1));
  });

  test("after all cards graded shows empty state", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(screen.getByTestId("fsrs-empty")).toBeInTheDocument());
  });
});

describe("FsrsReview — session persistence", () => {
  test("grading mid-queue persists a resumable session to localStorage", async () => {
    vi.mocked(fsrsClient.getDue).mockResolvedValueOnce([
      mockCard,
      { ...mockCard, id: "c2", front: "Second card" },
    ]);
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(screen.getByTestId("card-front").textContent).toContain("Second card"));
    const saved = JSON.parse(localStorage.getItem("jarvis.review.session")!);
    expect(saved.index).toBe(1);
    expect(saved.cards).toHaveLength(2);
  });

  test("remount resumes the saved session instead of re-fetching", async () => {
    localStorage.setItem("jarvis.review.session", JSON.stringify({
      cards: [mockCard, { ...mockCard, id: "c2", front: "Resumed card" }],
      index: 1,
      day: new Date().toISOString().slice(0, 10),
    }));
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("card-front")).toBeInTheDocument());
    expect(screen.getByTestId("card-front").textContent).toContain("Resumed card");
    expect(vi.mocked(fsrsClient.getDue)).not.toHaveBeenCalled();
  });

  test("finishing the queue clears the saved session", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(screen.getByTestId("fsrs-empty")).toBeInTheDocument());
    expect(localStorage.getItem("jarvis.review.session")).toBeNull();
  });

  test("a stale session from a prior day is discarded", async () => {
    localStorage.setItem("jarvis.review.session", JSON.stringify({
      cards: [{ ...mockCard, front: "Stale card" }],
      index: 0,
      day: "2020-01-01",
    }));
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("card-front")).toBeInTheDocument());
    // falls through to the mocked getDue queue, not the stale card
    expect(screen.getByTestId("card-front").textContent).toContain("What is the MLE");
    expect(vi.mocked(fsrsClient.getDue)).toHaveBeenCalled();
  });
});

describe("FsrsReview — forecast strip", () => {
  test("renders forecast counts", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-forecast")).toBeInTheDocument());
    const strip = screen.getByTestId("fsrs-forecast");
    expect(strip.textContent).toMatch(/tomorrow.*4/i);
    expect(strip.textContent).toMatch(/week.*18/i);
    expect(strip.textContent).toMatch(/month.*41/i);
  });
});
