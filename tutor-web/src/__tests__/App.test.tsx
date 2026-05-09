import { render, screen } from "@testing-library/react";
import { App } from "../App";

test("renders TutorWorkspace with PDF and chat panes", () => {
  render(<App />);
  expect(screen.getByTestId("pdf-pane")).toBeInTheDocument();
  expect(screen.getByTestId("chat-pane")).toBeInTheDocument();
});
