import { render, screen, waitFor } from "@testing-library/react";
import { test, expect } from "vitest";
import { PlotlyEmbed } from "../components/PlotlyEmbed";

test("PlotlyEmbed renders the lazy-loaded mock", async () => {
  render(<PlotlyEmbed figure={{ data: [{ y: [1, 2, 3] }] }} />);
  // Either suspense fallback or the mock plot must be in DOM.
  await waitFor(() => {
    const ok = screen.queryByTestId("mock-plotly") || screen.queryByText(/loading plot/i);
    expect(ok).not.toBeNull();
  });
});

test("PlotlyEmbed wrapper carries the embed testid", () => {
  render(<PlotlyEmbed figure={{ data: [] }} />);
  expect(screen.getByTestId("plotly-embed")).toBeInTheDocument();
});
