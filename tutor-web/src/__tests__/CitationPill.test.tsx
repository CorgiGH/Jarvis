import { render, screen, fireEvent } from "@testing-library/react";
import { test, expect, vi } from "vitest";
import { CitationPill } from "../components/CitationPill";
import type { Citation } from "../lib/sidekickContext";

const cit: Citation = {
  path: "lectures/week03/slides.pdf",
  snippet: "The median minimises sum of absolute deviations.",
  score: 0.87,
};

test("CitationPill renders the basename of the path", () => {
  render(<CitationPill citation={cit} onClick={vi.fn()} />);
  const pill = screen.getByTestId("citation-pill");
  expect(pill).toBeInTheDocument();
  expect(pill.textContent).toMatch(/slides\.pdf/);
});

test("CitationPill fires onClick with full citation on click", () => {
  const handler = vi.fn();
  render(<CitationPill citation={cit} onClick={handler} />);
  fireEvent.click(screen.getByTestId("citation-pill"));
  expect(handler).toHaveBeenCalledOnce();
  expect(handler).toHaveBeenCalledWith(cit);
});
