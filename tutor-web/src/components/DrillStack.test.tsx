import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "What does fib(5) expand to?",
  worked: "fib(5) = fib(4) + fib(3) ...",
  definition: "Recursion: a function defined in terms of itself.",
  check: "Trace fib(4).",
  expectedAnswerHint: "5",
  vizId: "recursion-tree",
};

describe("DrillStack routing", () => {
  it("mounts the routed viz and the drill card", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    expect(screen.getAllByTestId("drill-card").length).toBeGreaterThan(0);
    expect(screen.getByTestId("routed-viz-recursion-tree")).toBeInTheDocument();
    expect(screen.getByTestId("recursion-tree-root")).toBeInTheDocument();
  });

  it("renders no routed viz when vizId is absent", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={{ ...content, vizId: undefined }} onProblemComplete={() => {}} />);
    expect(screen.queryByTestId("routed-viz-recursion-tree")).not.toBeInTheDocument();
  });
});
