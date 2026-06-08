import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { DayOfShell } from "./DayOfShell";

// Mock jarvisFetch for exam-dates API
vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(),
}));

import { jarvisFetch } from "../lib/api";

const mockFetch = jarvisFetch as ReturnType<typeof vi.fn>;

describe("DayOfShell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the shell container", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        exam_dates: [
          { subject: "PA", start_at: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString() },
        ],
      }),
    });
    render(<DayOfShell />);
    await waitFor(() => expect(screen.getByTestId("day-of-shell")).toBeInTheDocument());
  });

  it("renders day-of-countdown when exam is within 24h", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        exam_dates: [
          { subject: "PA", start_at: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString() },
        ],
      }),
    });
    render(<DayOfShell />);
    await waitFor(() => expect(screen.getByTestId("day-of-countdown")).toBeInTheDocument());
  });

  it("renders day-of-checklist", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        exam_dates: [
          { subject: "PA", start_at: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString() },
        ],
      }),
    });
    render(<DayOfShell />);
    await waitFor(() => expect(screen.getByTestId("day-of-checklist")).toBeInTheDocument());
  });

  it("renders day-of-review-strip", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        exam_dates: [
          { subject: "PA", start_at: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString() },
        ],
      }),
    });
    render(<DayOfShell />);
    await waitFor(() => expect(screen.getByTestId("day-of-review-strip")).toBeInTheDocument());
  });

  it("shows empty state when no exam within 24h", async () => {
    // Exam is 48h away — outside the 24h window
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        exam_dates: [
          { subject: "PA", start_at: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString() },
        ],
      }),
    });
    render(<DayOfShell />);
    await waitFor(() => {
      const shell = screen.getByTestId("day-of-shell");
      expect(shell).toBeInTheDocument();
      // No countdown rendered for far-future exams
      expect(screen.queryByTestId("day-of-countdown")).not.toBeInTheDocument();
    });
  });

  it("handles API error gracefully", async () => {
    mockFetch.mockRejectedValue(new Error("network error"));
    render(<DayOfShell />);
    await waitFor(() => expect(screen.getByTestId("day-of-shell")).toBeInTheDocument());
  });
});
