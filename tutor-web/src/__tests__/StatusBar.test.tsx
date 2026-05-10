import { render, screen } from "@testing-library/react";
import { test, expect } from "vitest";
import { StatusBar } from "../components/StatusBar";

test("StatusBar renders pulse + clock + hostname", () => {
  render(<StatusBar hostname="test.example.com" />);
  expect(screen.getByTestId("status-bar")).toBeInTheDocument();
  expect(screen.getByTestId("status-bar-pulse")).toBeInTheDocument();
  expect(screen.getByText(/READY · CTRL\+ENTER · TEST.EXAMPLE.COM/)).toBeInTheDocument();
  // Clock shows HH:MM · BUC. We don't assert specific time.
  expect(screen.getByTestId("status-bar-clock").textContent).toMatch(/\d{2}:\d{2} · BUC/);
});

test("StatusBar is hidden from screen readers", () => {
  render(<StatusBar />);
  expect(screen.getByTestId("status-bar")).toHaveAttribute("aria-hidden", "true");
});
