import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ProofDrill } from "./ProofDrill";
import type { PracticeProblem, ProofGradeReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

// Mock practiceApi so no real network calls happen in tests.
vi.mock("../../lib/practiceApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/practiceApi")>();
  return {
    ...actual,
    gradeProof: vi.fn(),
  };
});

import { gradeProof } from "../../lib/practiceApi";
const mockGradeProof = gradeProof as ReturnType<typeof vi.fn>;

const PROOF_PROBLEM: PracticeProblem = {
  id: "pa-prob-001",
  subject: "PA",
  archetype: "np-reduction-proof",
  surface: "proof",
  statement_ro: "Demonstrați că problema X este NP-completă.",
  proof_frame: {
    template_ro:
      "Pasul 1: {{step1}}\nPasul 2: {{step2}}\nConcluzice: {{conclusion}}",
    substeps: [
      { id: "step1", label_ro: "Arătați că X ∈ NP" },
      { id: "step2", label_ro: "Construiți reducerea de la 3-SAT la X" },
      { id: "conclusion", label_ro: "Concluzie" },
    ],
  },
};

const GRADE_REPLY_PASS: ProofGradeReply = {
  item_verdicts: [
    { id: "step1", label: "Arătați că X ∈ NP", passed: true, points_earned: 1, points_max: 1 },
    { id: "step2", label: "Construiți reducerea de la 3-SAT la X", passed: true, points_earned: 2, points_max: 2 },
    { id: "conclusion", label: "Concluzie", passed: true, points_earned: 1, points_max: 1 },
  ],
  score: 1.0,
  correct: true,
  decided_by: "rubric",
  feedback_ro: "Demonstrație corectă.",
};

const GRADE_REPLY_PARTIAL: ProofGradeReply = {
  item_verdicts: [
    { id: "step1", label: "Arătați că X ∈ NP", passed: true, points_earned: 1, points_max: 1 },
    { id: "step2", label: "Construiți reducerea de la 3-SAT la X", passed: false, points_earned: 0, points_max: 2 },
    { id: "conclusion", label: "Concluzie", passed: false, points_earned: 0, points_max: 1 },
  ],
  score: 0.25,
  correct: false,
  decided_by: "rubric",
  feedback_ro: "Reducerea este incorectă.",
};

describe("ProofDrill", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders proof-drill-frame testid", () => {
    render(<ProofDrill problem={PROOF_PROBLEM} />);
    expect(screen.getByTestId("proof-drill-frame")).toBeInTheDocument();
  });

  it("renders proof-drill-substeps testid", () => {
    render(<ProofDrill problem={PROOF_PROBLEM} />);
    expect(screen.getByTestId("proof-drill-substeps")).toBeInTheDocument();
  });

  it("renders one input per substep", () => {
    render(<ProofDrill problem={PROOF_PROBLEM} />);
    expect(screen.getAllByRole("textbox")).toHaveLength(3);
  });

  it("per-substep verdict (proof-drill-substep-score) not present before submit", () => {
    render(<ProofDrill problem={PROOF_PROBLEM} />);
    expect(screen.queryByTestId("proof-drill-substep-score")).not.toBeInTheDocument();
  });

  it("renders per-substep verdicts after submit with mocked reply", async () => {
    mockGradeProof.mockResolvedValue(GRADE_REPLY_PASS);
    render(<ProofDrill problem={PROOF_PROBLEM} />);

    // Fill in each substep textarea
    const inputs = screen.getAllByRole("textbox");
    fireEvent.change(inputs[0], { target: { value: "X are un certificat verificabil în timp polinomial" } });
    fireEvent.change(inputs[1], { target: { value: "Se reduce 3-SAT la X în timp polinomial" } });
    fireEvent.change(inputs[2], { target: { value: "X este NP-completă" } });

    fireEvent.click(screen.getByTestId("proof-drill-submit-btn"));
    await waitFor(() =>
      expect(screen.getAllByTestId("proof-drill-substep-score")).toHaveLength(3),
    );
  });

  it("partial grade shows mixed verdicts in per-substep scores", async () => {
    mockGradeProof.mockResolvedValue(GRADE_REPLY_PARTIAL);
    render(<ProofDrill problem={PROOF_PROBLEM} />);

    const inputs = screen.getAllByRole("textbox");
    inputs.forEach((inp) => fireEvent.change(inp, { target: { value: "test" } }));
    fireEvent.click(screen.getByTestId("proof-drill-submit-btn"));

    await waitFor(() =>
      expect(screen.getAllByTestId("proof-drill-substep-score")).toHaveLength(3),
    );
    // First verdict passed, second failed
    const verdicts = screen.getAllByTestId("proof-drill-substep-score");
    expect(verdicts[0]).toHaveTextContent(practiceStrings.proofDrillVerdictPass);
    expect(verdicts[1]).toHaveTextContent(practiceStrings.proofDrillVerdictFail);
  });

  it("uses only practiceStrings for learner copy — no hardcoded RO text", () => {
    render(<ProofDrill problem={PROOF_PROBLEM} />);
    // The submit button label must come from practiceStrings
    expect(
      screen.getByTestId("proof-drill-submit-btn"),
    ).toHaveTextContent(practiceStrings.proofDrillSubmitBtn);
  });
});
