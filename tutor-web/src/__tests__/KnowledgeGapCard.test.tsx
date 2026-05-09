import { render, screen, fireEvent } from "@testing-library/react";
import { test, expect, vi } from "vitest";
import { KnowledgeGapCard } from "../components/KnowledgeGapCard";
import type { KnowledgeGap } from "../lib/knowledgeGap";

const baseGap: KnowledgeGap = {
  id: "g1", topic: "kotlin closures", language: "kotlin",
  type: "CONCEPT", trigger: "EXPLICIT_ASK",
  content: "A closure captures variables from its enclosing scope.",
  exampleCode: "val add = { x: Int -> x + 1 }",
  sourceCitation: "PA notes p.42",
};

test("renders type, topic, content, code preview, citation", () => {
  render(<KnowledgeGapCard gap={baseGap} />);
  expect(screen.getByText(/GAP · CONCEPT · kotlin closures/)).toBeInTheDocument();
  expect(screen.getByText(/A closure captures/)).toBeInTheDocument();
  expect(screen.getByTestId("knowledge-gap-code")).toHaveTextContent("val add");
  expect(screen.getByTestId("knowledge-gap-citation")).toHaveTextContent("PA notes p.42");
});

test("INSERT → SCRATCHPAD invokes callback with full gap", () => {
  const fn = vi.fn();
  render(<KnowledgeGapCard gap={baseGap} onInsertScratchpad={fn} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-insert"));
  expect(fn).toHaveBeenCalledWith(baseGap);
});

test("MARK RESOLVED hides actions + emits USER_MARKED_DONE", () => {
  const fn = vi.fn();
  render(<KnowledgeGapCard gap={baseGap} onResolve={fn} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-resolve"));
  expect(fn).toHaveBeenCalledWith("g1", "USER_MARKED_DONE");
  expect(screen.queryByTestId("knowledge-gap-insert")).toBeNull();
  expect(screen.getByTestId("knowledge-gap-status")).toHaveTextContent("USER_MARKED_DONE");
});

test("FLAG WRONG (dismiss) hides actions + emits USER_DISMISSED", () => {
  const fn = vi.fn();
  render(<KnowledgeGapCard gap={baseGap} onResolve={fn} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-dismiss"));
  expect(fn).toHaveBeenCalledWith("g1", "USER_DISMISSED");
  expect(screen.queryByTestId("knowledge-gap-resolve")).toBeNull();
});

test("renders without code or citation when absent", () => {
  const minimal: KnowledgeGap = {
    id: "x", topic: "y", type: "COMMAND", trigger: "MANUAL_FLAG",
    content: "just text",
  };
  render(<KnowledgeGapCard gap={minimal} />);
  expect(screen.queryByTestId("knowledge-gap-code")).toBeNull();
  expect(screen.queryByTestId("knowledge-gap-citation")).toBeNull();
});

test("truncates long example code in preview", () => {
  const long = "y".repeat(500);
  render(<KnowledgeGapCard gap={{ ...baseGap, exampleCode: long }} />);
  const code = screen.getByTestId("knowledge-gap-code");
  expect(code.textContent!.length).toBeLessThanOrEqual(321);
  expect(code.textContent!.endsWith("…")).toBe(true);
});
