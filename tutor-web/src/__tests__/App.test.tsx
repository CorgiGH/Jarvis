import { render, screen } from "@testing-library/react";
import { App } from "../App";

test("renders ONLINE banner", () => {
  render(<App />);
  expect(screen.getByText(/ONLINE/)).toBeInTheDocument();
});
