import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { BilingualText } from "./BilingualText";

// Glossary terms that are in the hardcoded Set
const KNOWN_TERM = "EWMA";
const UNKNOWN_TERM = "randomword";

describe("BilingualText", () => {
  it("renders children text", () => {
    render(<BilingualText lang="ro">Text simplu</BilingualText>);
    expect(screen.getByText(/text simplu/i)).toBeInTheDocument();
  });

  it("highlights a known glossary term", () => {
    render(
      <BilingualText lang="ro">
        {`Formula ${KNOWN_TERM} calculează media.`}
      </BilingualText>,
    );
    // The glossary term should be wrapped in a highlighted span
    const highlighted = document.querySelector("[data-glossary-term]");
    expect(highlighted).not.toBeNull();
    expect(highlighted?.textContent).toContain(KNOWN_TERM);
  });

  it("does not highlight unknown terms", () => {
    render(
      <BilingualText lang="ro">{`Cuvânt ${UNKNOWN_TERM} obișnuit`}</BilingualText>,
    );
    const highlighted = document.querySelector("[data-glossary-term]");
    expect(highlighted).toBeNull();
  });

  it("renders EN text unchanged when lang=en", () => {
    render(<BilingualText lang="en">English text</BilingualText>);
    expect(screen.getByText(/english text/i)).toBeInTheDocument();
  });
});
