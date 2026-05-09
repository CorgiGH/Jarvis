import { render, screen, fireEvent } from "@testing-library/react";
import { vi, test, expect } from "vitest";
import { ChipRow } from "../components/ChipRow";

test("renders nothing when chips array is empty", () => {
  const { container } = render(<ChipRow chips={[]} onPick={() => {}} />);
  expect(container.querySelector("[data-testid='chip-row']")).toBeNull();
});

test("renders one button per chip with label as text", () => {
  render(
    <ChipRow
      chips={[
        { label: "Why?", prompt: "explain why" },
        { label: "Show example", prompt: "give a concrete worked example" },
      ]}
      onPick={() => {}}
    />,
  );
  const buttons = screen.getAllByTestId("chip-button");
  expect(buttons).toHaveLength(2);
  expect(buttons[0].textContent).toBe("Why?");
  expect(buttons[1].textContent).toBe("Show example");
});

test("click invokes onPick with the chip's prompt", () => {
  const onPick = vi.fn();
  render(
    <ChipRow
      chips={[{ label: "Why?", prompt: "explain why this works" }]}
      onPick={onPick}
    />,
  );
  fireEvent.click(screen.getByTestId("chip-button"));
  expect(onPick).toHaveBeenCalledOnce();
  expect(onPick).toHaveBeenCalledWith("explain why this works");
});
