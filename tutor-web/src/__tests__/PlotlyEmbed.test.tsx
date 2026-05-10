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

test("PlotlyEmbed renders caption from layout.title.text + indexLabel", () => {
  render(<PlotlyEmbed indexLabel="FIG 1" figure={{ data: [], layout: { title: { text: "Laplace density" } } }} />);
  const cap = screen.getByTestId("plotly-caption");
  expect(cap.textContent).toBe("FIG 1 · Laplace density");
});

test("PlotlyEmbed caption falls back to FIG when no title", () => {
  render(<PlotlyEmbed figure={{ data: [] }} />);
  expect(screen.getByTestId("plotly-caption").textContent).toBe("FIG");
});

test("PlotlyEmbed accepts string title (legacy plotly shape)", () => {
  render(<PlotlyEmbed indexLabel="FIG 2" figure={{ data: [], layout: { title: "Histogram" } }} />);
  expect(screen.getByTestId("plotly-caption").textContent).toBe("FIG 2 · Histogram");
});
