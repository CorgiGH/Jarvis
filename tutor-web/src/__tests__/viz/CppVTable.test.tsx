import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { CppVTable, FRAME_COUNT } from "../../components/viz/CppVTable";

describe("CppVTable (POO-1/POO-4)", () => {
  test("renders Shell scrubber + panes", () => {
    render(<CppVTable />);
    expect(screen.getByTestId("cpp-vtable-scrubber")).toBeInTheDocument();
    expect(screen.getByText("SOURCE")).toBeInTheDocument();
  });

  test("frame counter shows 1 / 15", () => {
    render(<CppVTable />);
    expect(screen.getByText(/^1\s*\/\s*15$/)).toBeInTheDocument();
  });

  test("initial frame is Phase 1", () => {
    render(<CppVTable />);
    const phaseEls = screen.getAllByText(/Phase 1/);
    expect(phaseEls.length).toBeGreaterThan(0);
  });

  test("FRAME_COUNT is 15", () => {
    expect(FRAME_COUNT).toBe(15);
  });

  test("renders role=group from Shell", () => {
    render(<CppVTable />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
