import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect } from "vitest";
import { ThemePicker } from "../door/ThemePicker";

const noop = () => {};
const choice = { paletteId: "brand-yellow" };

test("production picker (concepts=[]) renders NO concept row", async () => {
  render(
    <ThemePicker
      skin="brutalist"
      onSkin={noop}
      choice={choice}
      onChoice={noop}
      concepts={[]}
      conceptId=""
      onConcept={noop}
    />,
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.queryByText("Concept")).toBeNull();
  expect(screen.queryByTestId(/^concept-/)).toBeNull();
  // palette swatches still present
  expect(screen.getByTestId("palette-brand-yellow")).toBeTruthy();
});

test("demo picker (concepts present) still shows the concept row", async () => {
  render(
    <ThemePicker
      skin="brutalist"
      onSkin={noop}
      choice={choice}
      onChoice={noop}
      concepts={[{ id: "recurrence", label: "Recurrence" }]}
      conceptId="recurrence"
      onConcept={noop}
    />,
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.getByTestId("concept-recurrence")).toBeTruthy();
});
