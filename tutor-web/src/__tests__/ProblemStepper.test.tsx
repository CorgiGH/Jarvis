import { render, screen, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter, Route, Routes, useSearchParams } from "react-router-dom";
import { ProblemStepper, parseProblemParam } from "../components/ProblemStepper";

describe("parseProblemParam", () => {
  test("returns 0 when param is absent", () => {
    expect(parseProblemParam(null)).toBe(0);
  });
  test("returns 0 for non-numeric string", () => {
    expect(parseProblemParam("abc")).toBe(0);
  });
  test("returns 0 for negative number", () => {
    expect(parseProblemParam("-1")).toBe(0);
  });
  test("returns parsed index for valid positive integer string", () => {
    expect(parseProblemParam("2")).toBe(2);
  });
  test("returns 0 for '0'", () => {
    expect(parseProblemParam("0")).toBe(0);
  });
});

describe("ProblemStepper component", () => {
  const problems = [
    { problemId: "A1", label: "A1" },
    { problemId: "A2", label: "A2" },
    { problemId: "A3", label: "A3" },
  ];

  function Wrapper({ initialParam = "" }: { initialParam?: string }) {
    return (
      <MemoryRouter initialEntries={[`/?taskId=T1${initialParam}`]}>
        <Routes>
          <Route
            path="/"
            element={
              <ProblemStepper
                problems={problems}
                activeProblemIndex={parseProblemParam(
                  new URLSearchParams(initialParam).get("problem")
                )}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    );
  }

  test("renders all problem labels as buttons", () => {
    render(<Wrapper />);
    expect(screen.getByRole("button", { name: /A1/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /A2/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /A3/ })).toBeInTheDocument();
  });

  test("first problem button is filled (◉) when active index is 0", () => {
    render(<Wrapper />);
    const btn = screen.getByRole("button", { name: /A1/ });
    expect(btn.getAttribute("aria-current")).toBe("true");
    expect(btn.textContent).toContain("◉");
  });

  test("inactive problem buttons show hollow dot (○)", () => {
    render(<Wrapper />);
    const btn2 = screen.getByRole("button", { name: /A2/ });
    expect(btn2.textContent).toContain("○");
    expect(btn2.getAttribute("aria-current")).toBeNull();
  });

  test("active problem index 2 marks A3 as current", () => {
    render(<Wrapper initialParam="&problem=2" />);
    const btn = screen.getByRole("button", { name: /A3/ });
    expect(btn.getAttribute("aria-current")).toBe("true");
    expect(btn.textContent).toContain("◉");
  });

  test("onProblemSelect fires with problem index when button clicked", () => {
    const onSelect = vi.fn();
    render(
      <MemoryRouter>
        <ProblemStepper
          problems={problems}
          activeProblemIndex={0}
          onProblemSelect={onSelect}
        />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole("button", { name: /A2/ }));
    expect(onSelect).toHaveBeenCalledWith(1);
  });
});
