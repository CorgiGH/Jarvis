import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SubjectCard } from "./SubjectCard";

const baseProps = {
  subjectId: "pa",
  subjectNameRo: "Probabilități și Statistică",
  subjectNameEn: "Probability and Statistics",
  kcCount: 20,
  masteredCount: 5,
  avgEwma: 0.6,
  retentionGapCount: 2,
  onClick: vi.fn(),
};

describe("SubjectCard", () => {
  it("renders subject-card-{subjectId} testid", () => {
    render(<SubjectCard {...baseProps} />);
    expect(screen.getByTestId("subject-card-pa")).toBeInTheDocument();
  });

  it("displays Romanian subject name", () => {
    render(<SubjectCard {...baseProps} />);
    expect(screen.getByTestId("subject-card-pa")).toHaveTextContent(
      "Probabilități și Statistică",
    );
  });

  it("calls onClick when clicked", () => {
    const onClick = vi.fn();
    render(<SubjectCard {...baseProps} onClick={onClick} />);
    fireEvent.click(screen.getByTestId("subject-card-pa"));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("renders MasterySparkline with avgEwma", () => {
    render(<SubjectCard {...baseProps} avgEwma={0.9} />);
    const sparkline = screen.getByTestId("mastery-sparkline");
    expect(sparkline).toHaveAttribute("data-mastery-band", "high");
  });

  it("renders retention-gap-badge when retentionGapCount > 0", () => {
    render(<SubjectCard {...baseProps} retentionGapCount={3} />);
    expect(screen.getByTestId("retention-gap-badge-pa")).toBeInTheDocument();
    expect(screen.getByTestId("retention-gap-badge-pa")).toHaveTextContent("3");
  });

  it("does not render retention-gap-badge when retentionGapCount is 0", () => {
    render(<SubjectCard {...baseProps} retentionGapCount={0} />);
    expect(screen.queryByTestId("retention-gap-badge-pa")).not.toBeInTheDocument();
  });

  it("displays mastered / total count", () => {
    render(<SubjectCard {...baseProps} masteredCount={5} kcCount={20} />);
    const card = screen.getByTestId("subject-card-pa");
    expect(card).toHaveTextContent("5");
    expect(card).toHaveTextContent("20");
  });
});
