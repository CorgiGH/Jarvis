import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MisconceptionRibbon } from "./MisconceptionRibbon";
import type { MisconceptionPayload } from "../lib/drillGrader";

const cited: MisconceptionPayload = {
  id: "OFF_BY_ONE",
  refutation: "Cazul de bază oprește recursia la n<=1, nu la n<=0.",
  figure_spec: null,
  self_explanation_prompt: null,
  source: { doc: "curs3.pdf", page: 12, span: { start: 100, end: 140 }, quote: "cazul de bază" },
};

describe("MisconceptionRibbon", () => {
  it("renders the kicker with the id, the refutation, and the citation", () => {
    render(<MisconceptionRibbon payload={cited} />);
    expect(screen.getByTestId("misconception-ribbon")).toBeInTheDocument();
    expect(screen.getByTestId("misconception-ribbon-kicker")).toHaveTextContent("OFF_BY_ONE");
    expect(screen.getByTestId("misconception-ribbon-refutation"))
      .toHaveTextContent("Cazul de bază oprește recursia");
    expect(screen.getByTestId("misconception-ribbon-citation")).toHaveTextContent("curs3.pdf");
  });

  it("renders nothing when payload is null", () => {
    const { container } = render(<MisconceptionRibbon payload={null} />);
    expect(container.firstChild).toBeNull();
  });

  it("omits the citation pill when source is null (legacy [from] builder)", () => {
    render(<MisconceptionRibbon payload={{ ...cited, source: null }} />);
    expect(screen.queryByTestId("misconception-ribbon-citation")).not.toBeInTheDocument();
  });
});
