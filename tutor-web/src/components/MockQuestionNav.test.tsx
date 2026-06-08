import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MockQuestionNav } from "./MockQuestionNav";

describe("MockQuestionNav", () => {
  it("renders the nav container", () => {
    render(
      <MockQuestionNav
        count={3}
        answers={["", "", ""]}
        activeIndex={0}
        onSelect={() => {}}
      />
    );
    expect(screen.getByTestId("mock-question-nav")).toBeInTheDocument();
  });

  it("renders a pip per question", () => {
    render(
      <MockQuestionNav
        count={3}
        answers={["", "", ""]}
        activeIndex={0}
        onSelect={() => {}}
      />
    );
    expect(screen.getAllByRole("button")).toHaveLength(3);
  });

  it("marks the active pip", () => {
    render(
      <MockQuestionNav
        count={3}
        answers={["", "", ""]}
        activeIndex={1}
        onSelect={() => {}}
      />
    );
    const btns = screen.getAllByRole("button");
    expect(btns[1]).toHaveAttribute("data-active", "true");
    expect(btns[0]).toHaveAttribute("data-active", "false");
  });

  it("calls onSelect with the question index on click", () => {
    const onSelect = vi.fn();
    render(
      <MockQuestionNav
        count={3}
        answers={["", "", ""]}
        activeIndex={0}
        onSelect={onSelect}
      />
    );
    fireEvent.click(screen.getAllByRole("button")[2]);
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it("marks answered pips", () => {
    render(
      <MockQuestionNav
        count={3}
        answers={["raspuns", "", ""]}
        activeIndex={0}
        onSelect={() => {}}
      />
    );
    const btns = screen.getAllByRole("button");
    expect(btns[0]).toHaveAttribute("data-answered", "true");
    expect(btns[1]).toHaveAttribute("data-answered", "false");
  });
});
