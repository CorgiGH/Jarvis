import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";

test("renders TutorWorkspace with PDF and chat panes", () => {
  render(<MemoryRouter><App /></MemoryRouter>);
  expect(screen.getByTestId("pdf-pane")).toBeInTheDocument();
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
});

test("uses default taskId TEST-TASK-A when no query param", () => {
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  // Header AND inner ChatPane both render the taskId; assert at least one match.
  expect(screen.getAllByText(/TEST-TASK-A/).length).toBeGreaterThan(0);
});

test("reads taskId from URL query param", () => {
  render(<MemoryRouter initialEntries={["/?taskId=T-XYZ"]}><App /></MemoryRouter>);
  expect(screen.getAllByText(/T-XYZ/).length).toBeGreaterThan(0);
});
