import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LessonScreen } from "./LessonScreen";

// Mock the lesson API client
vi.mock("../lib/lesson", () => ({
  getLesson: vi.fn(),
}));

import { getLesson } from "../lib/lesson";

const mockLesson = {
  kcId: "pa-kc-001",
  kc_name_ro: "Quicksort",
  kc_name_en: "Quicksort",
  concrete_question_ro: "Ce este un pivot în Quicksort?",
  echo_source_ro: "Algoritmul Quicksort selectează un pivot.",
  prediction_options: ["Da", "Nu"],
  term_ro: "Quicksort",
  definition_ro: null,
  explanation_ro: "Quicksort este un algoritm de sortare recursiv.",
  worked_example_ro: null,
  provenance: { type: "authored" as const, hasBeenFaithfulChecked: true },
};

describe("LessonScreen", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders lesson-screen and lesson-step-entry on first paint (faithful KC)", async () => {
    (getLesson as ReturnType<typeof vi.fn>).mockResolvedValue(mockLesson);

    render(<LessonScreen kcId="pa-kc-001" />);

    await waitFor(() => {
      expect(screen.getByTestId("lesson-screen")).toBeInTheDocument();
      expect(screen.getByTestId("lesson-step-entry")).toBeInTheDocument();
    });
  });

  it("renders lesson-unavailable when /lesson returns 404 (non-faithful gate)", async () => {
    (getLesson as ReturnType<typeof vi.fn>).mockResolvedValue(null);

    render(<LessonScreen kcId="pa-kc-unknown" />);

    await waitFor(() => {
      expect(screen.getByTestId("lesson-unavailable")).toBeInTheDocument();
    });

    // Faithful-serve rule: must show the honest Romanian fallback message
    expect(screen.getByTestId("lesson-unavailable")).toHaveTextContent(
      /KC nu este încă verificat/i
    );
  });

  it("submitting entry answer advances to lesson-step-term", async () => {
    (getLesson as ReturnType<typeof vi.fn>).mockResolvedValue(mockLesson);

    render(<LessonScreen kcId="pa-kc-001" />);

    // Wait for entry step
    await waitFor(() => expect(screen.getByTestId("lesson-step-entry")).toBeInTheDocument());

    // Fill and submit entry answer
    const input = screen.getByTestId("concrete-question-input");
    fireEvent.change(input, { target: { value: "Un pivot este un element ales." } });
    fireEvent.click(screen.getByTestId("concrete-question-submit"));

    // lesson-step-term should appear
    await waitFor(() => {
      expect(screen.getByTestId("lesson-step-term")).toBeInTheDocument();
    });
  });

  it("after prediction renders lesson-step-retrieval", async () => {
    (getLesson as ReturnType<typeof vi.fn>).mockResolvedValue(mockLesson);

    render(<LessonScreen kcId="pa-kc-001" />);

    // Entry step
    await waitFor(() => expect(screen.getByTestId("lesson-step-entry")).toBeInTheDocument());
    fireEvent.change(screen.getByTestId("concrete-question-input"), {
      target: { value: "Un pivot este un element ales." },
    });
    fireEvent.click(screen.getByTestId("concrete-question-submit"));

    // Term step — click a prediction option
    await waitFor(() => expect(screen.getByTestId("lesson-step-term")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("prediction-option-0"));

    // Retrieval step should appear
    await waitFor(() => {
      expect(screen.getByTestId("lesson-step-retrieval")).toBeInTheDocument();
    });
  });
});
