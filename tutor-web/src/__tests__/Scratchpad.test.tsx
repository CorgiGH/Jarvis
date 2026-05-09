import { render, screen, fireEvent } from "@testing-library/react";
import { test, expect, vi } from "vitest";
import { Scratchpad } from "../components/Scratchpad";

test("renders header label + textarea with provided value", () => {
  render(<Scratchpad value="initial" onChange={vi.fn()} />);
  expect(screen.getByText("SCRATCHPAD")).toBeInTheDocument();
  expect(screen.getByTestId("scratchpad-input")).toHaveValue("initial");
});

test("calls onChange on textarea input", () => {
  const fn = vi.fn();
  render(<Scratchpad value="" onChange={fn} />);
  const input = screen.getByTestId("scratchpad-input");
  fireEvent.change(input, { target: { value: "typed" } });
  expect(fn).toHaveBeenCalledWith("typed");
});
