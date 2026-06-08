import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SubjectMap } from "./SubjectMap";

// Mock jarvisFetch so tests don't hit real network
vi.mock("../lib/api", () => ({
  jarvisFetch: vi.fn(),
}));
import { jarvisFetch } from "../lib/api";
const mockFetch = jarvisFetch as ReturnType<typeof vi.fn>;

const makeSubjectData = (id: string) => ({
  subject_id: id,
  subject_name_ro: `Materia ${id.toUpperCase()}`,
  subject_name_en: `Subject ${id.toUpperCase()}`,
  kcs: [
    { kc_id: `${id}-1`, ewma_score: 0.7, observations: 5, verification_status: "faithful" },
    { kc_id: `${id}-2`, ewma_score: 0.2, observations: 2, verification_status: "faithful" },
  ],
});

describe("SubjectMap", () => {
  it("renders subject-map testid", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ subjects: [makeSubjectData("pa")] }),
    } as Response);
    render(<SubjectMap />);
    // Loading state renders quickly
    expect(screen.getByTestId("subject-map")).toBeInTheDocument();
  });

  it("renders a subject-card per subject on success", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ subjects: [makeSubjectData("pa"), makeSubjectData("ml")] }),
    } as Response);
    render(<SubjectMap />);
    // Wait for async render
    await screen.findByTestId("subject-card-pa");
    expect(screen.getByTestId("subject-card-pa")).toBeInTheDocument();
    expect(screen.getByTestId("subject-card-ml")).toBeInTheDocument();
  });

  it("shows retention-gap-badge for subjects with low-ewma KCs", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ subjects: [makeSubjectData("pa")] }),
    } as Response);
    render(<SubjectMap />);
    await screen.findByTestId("subject-card-pa");
    // kc-2 has ewma 0.2 (<0.3) → retention gap count = 1
    expect(screen.getByTestId("retention-gap-badge-pa")).toBeInTheDocument();
    expect(screen.getByTestId("retention-gap-badge-pa")).toHaveTextContent("1");
  });

  it("renders subject-map-empty when no subjects", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ subjects: [] }),
    } as Response);
    render(<SubjectMap />);
    await screen.findByTestId("subject-map-empty");
    expect(screen.getByTestId("subject-map-empty")).toBeInTheDocument();
  });

  it("renders subject-map-error on network failure", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 500,
      text: async () => "internal error",
    } as unknown as Response);
    render(<SubjectMap />);
    await screen.findByTestId("subject-map-error");
    expect(screen.getByTestId("subject-map-error")).toBeInTheDocument();
  });
});
