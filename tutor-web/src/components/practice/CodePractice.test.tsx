import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { CodePractice } from "./CodePractice";
import type { PracticeProblem, CodeGradeReply, CodeRunReply } from "../../lib/practiceApi";
import { practiceStrings } from "../../lib/practiceStrings";

// Mock practiceApi so no real network calls happen in tests.
vi.mock("../../lib/practiceApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/practiceApi")>();
  return {
    ...actual,
    runCode: vi.fn(),
    gradeCode: vi.fn(),
  };
});

import { runCode, gradeCode } from "../../lib/practiceApi";
const mockRunCode = runCode as ReturnType<typeof vi.fn>;
const mockGradeCode = gradeCode as ReturnType<typeof vi.fn>;

const CODE_PROBLEM_R: PracticeProblem = {
  id: "ps-prob-001",
  subject: "PS",
  archetype: "confidence-interval",
  surface: "code",
  statement_ro: "Scrieți o funcție R care calculează intervalul de încredere.",
  exam_language: "r",
};

const CODE_PROBLEM_ALK: PracticeProblem = {
  id: "pa-prob-code-001",
  subject: "PA",
  archetype: "np-code",
  surface: "code",
  statement_ro: "Implementați algoritmul Dijkstra în Alk.",
  exam_language: "alk",
};

const CODE_PROBLEM_CPP: PracticeProblem = {
  id: "alo-prob-cpp-001",
  subject: "ALO",
  archetype: "gaussian-elim",
  surface: "code",
  statement_ro: "Implementați eliminarea Gauss în C++.",
  exam_language: "cpp",
};

const CODE_PROBLEM_BASH: PracticeProblem = {
  id: "so-prob-bash-001",
  subject: "SO",
  archetype: "bash-script",
  surface: "code",
  statement_ro: "Scrieți un script bash care listează fișierele.",
  exam_language: "bash",
};

const RUN_REPLY_OK: CodeRunReply = {
  compiled: true,
  stdout_trunc: "hi",
  stderr_trunc: "",
  timed_out: false,
  degraded_legs_ro: [],
};

const RUN_REPLY_DEGRADED: CodeRunReply = {
  compiled: false,
  stdout_trunc: "",
  stderr_trunc: "",
  timed_out: false,
  degraded_legs_ro: ["Rularea codului indisponibilă pe acest server — verificat structural."],
};

const GRADE_REPLY_WITH_REF: CodeGradeReply = {
  item_verdicts: [],
  score: 1.0,
  correct: true,
  decided_by: "execution",
  feedback_ro: "Corect.",
  degraded_legs_ro: [],
  reference_solution_ro: "interval_z = function(n, sample_mean, sigma, alfa) { ... }",
};

const GRADE_REPLY_NO_REF: CodeGradeReply = {
  item_verdicts: [],
  score: 0.0,
  correct: false,
  decided_by: "rubric",
  feedback_ro: "Incorect.",
  degraded_legs_ro: [],
  reference_solution_ro: null,
};

describe("CodePractice", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── testids present on first paint ───────────────────────────────────────────

  it("renders code-practice-editor textarea", () => {
    render(<CodePractice problem={CODE_PROBLEM_R} />);
    expect(screen.getByTestId("code-practice-editor")).toBeInTheDocument();
  });

  it("code-practice-language-badge shows 'R' for exam_language=r (REQ-7)", () => {
    render(<CodePractice problem={CODE_PROBLEM_R} />);
    const badge = screen.getByTestId("code-practice-language-badge");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent("R");
  });

  it("code-practice-language-badge shows 'Alk' for exam_language=alk (REQ-7)", () => {
    render(<CodePractice problem={CODE_PROBLEM_ALK} />);
    expect(screen.getByTestId("code-practice-language-badge")).toHaveTextContent("Alk");
  });

  it("code-practice-language-badge shows 'C++' for exam_language=cpp (REQ-7)", () => {
    render(<CodePractice problem={CODE_PROBLEM_CPP} />);
    expect(screen.getByTestId("code-practice-language-badge")).toHaveTextContent("C++");
  });

  it("code-practice-language-badge shows 'Bash' for exam_language=bash (REQ-7)", () => {
    render(<CodePractice problem={CODE_PROBLEM_BASH} />);
    expect(screen.getByTestId("code-practice-language-badge")).toHaveTextContent("Bash");
  });

  it("code-practice-run button is present", () => {
    render(<CodePractice problem={CODE_PROBLEM_R} />);
    expect(screen.getByTestId("code-practice-run")).toBeInTheDocument();
  });

  // ── INV-6.6 client half: reference absent pre-attempt ────────────────────────

  it("code-practice-reference is ABSENT from DOM before any grade attempt (INV-6.6 client half)", () => {
    render(<CodePractice problem={CODE_PROBLEM_R} />);
    expect(screen.queryByTestId("code-practice-reference")).not.toBeInTheDocument();
  });

  it("code-practice-reference is still ABSENT after a run (run is not a grade attempt)", async () => {
    mockRunCode.mockResolvedValue(RUN_REPLY_OK);
    render(<CodePractice problem={CODE_PROBLEM_R} />);

    const editor = screen.getByTestId("code-practice-editor");
    fireEvent.change(editor, { target: { value: "cat('hi')" } });
    fireEvent.click(screen.getByTestId("code-practice-run"));

    await waitFor(() => expect(mockRunCode).toHaveBeenCalled());
    expect(screen.queryByTestId("code-practice-reference")).not.toBeInTheDocument();
  });

  // ── reference present ONLY after grade attempt (INV-6.6 client half) ─────────

  it("code-practice-reference renders ONLY from the grade reply (INV-6.6 + REQ-8)", async () => {
    mockGradeCode.mockResolvedValue(GRADE_REPLY_WITH_REF);
    render(<CodePractice problem={CODE_PROBLEM_R} />);

    // Confirm absent before grading
    expect(screen.queryByTestId("code-practice-reference")).not.toBeInTheDocument();

    const editor = screen.getByTestId("code-practice-editor");
    fireEvent.change(editor, { target: { value: "interval_z = function(n, sample_mean, sigma, alfa) {}" } });

    // Click grade button (second button, no testid — use accessible name)
    const gradeBtn = screen.getByRole("button", { name: practiceStrings.codePracticeGradeBtn });
    fireEvent.click(gradeBtn);

    // After grading, reference appears
    await waitFor(() =>
      expect(screen.getByTestId("code-practice-reference")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("code-practice-reference")).toHaveTextContent("interval_z = function");
  });

  it("code-practice-reference absent when grade reply has null reference_solution_ro", async () => {
    mockGradeCode.mockResolvedValue(GRADE_REPLY_NO_REF);
    render(<CodePractice problem={CODE_PROBLEM_R} />);

    const gradeBtn = screen.getByRole("button", { name: practiceStrings.codePracticeGradeBtn });
    fireEvent.click(gradeBtn);

    await waitFor(() => expect(screen.getByTestId("code-practice-verdict")).toBeInTheDocument());
    expect(screen.queryByTestId("code-practice-reference")).not.toBeInTheDocument();
  });

  // ── verdict rendered after grade ──────────────────────────────────────────────

  it("code-practice-verdict renders after grade attempt", async () => {
    mockGradeCode.mockResolvedValue(GRADE_REPLY_WITH_REF);
    render(<CodePractice problem={CODE_PROBLEM_R} />);

    const gradeBtn = screen.getByRole("button", { name: practiceStrings.codePracticeGradeBtn });
    fireEvent.click(gradeBtn);

    await waitFor(() =>
      expect(screen.getByTestId("code-practice-verdict")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("code-practice-verdict")).toHaveTextContent(practiceStrings.correct);
  });

  // ── degraded banner (R-6-Q1 honesty) ─────────────────────────────────────────

  it("renders degraded banner when run reply has degraded_legs_ro", async () => {
    mockRunCode.mockResolvedValue(RUN_REPLY_DEGRADED);
    render(<CodePractice problem={CODE_PROBLEM_R} />);

    fireEvent.click(screen.getByTestId("code-practice-run"));
    await waitFor(() => expect(mockRunCode).toHaveBeenCalled());

    // The banner text appears in the container; getAllByText returns all matching elements.
    const bannerElements = screen.getAllByText(practiceStrings.codePracticeDegradedBanner);
    expect(bannerElements.length).toBeGreaterThan(0);
  });

  // ── strings come from practiceStrings (INV-8.3) ──────────────────────────────

  it("run button label comes from practiceStrings (no hardcoded RO text)", () => {
    render(<CodePractice problem={CODE_PROBLEM_R} />);
    expect(screen.getByTestId("code-practice-run")).toHaveTextContent(
      practiceStrings.codePracticeRunBtn,
    );
  });
});
