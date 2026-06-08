import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RetentionGapBadge } from "./SubjectCard";

describe("RetentionGapBadge", () => {
  it("renders with correct testid and count", () => {
    render(<RetentionGapBadge subjectId="pa" count={5} />);
    const badge = screen.getByTestId("retention-gap-badge-pa");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent("5");
  });

  it("renders different subjectId correctly", () => {
    render(<RetentionGapBadge subjectId="ml" count={1} />);
    expect(screen.getByTestId("retention-gap-badge-ml")).toBeInTheDocument();
  });
});
