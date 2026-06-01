// 0.E.4 — NumLineDirect: sync μ label with marker
// The fix: drop the imperative setAttribute path in useEffect; both circle cx and
// label x derive from React state (toSvgX(mu, lo, hi)) so they always agree.

import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { NumLineDirect } from "../NumLineDirect";

const DATA = [2, 4, 6, 8, 10];
const MU_INITIAL = 6;

function renderComponent(mu = MU_INITIAL) {
  const { rerender } = render(
    <NumLineDirect data={DATA} mu={mu} onMu={() => {}} />
  );
  return { rerender };
}

describe("NumLineDirect — μ label stays in sync with marker (0.E.4)", () => {
  it("renders mu-marker and mu-label", () => {
    renderComponent();
    expect(screen.getByTestId("mu-marker")).toBeTruthy();
    expect(screen.getByTestId("mu-label")).toBeTruthy();
  });

  it("mu-label x === mu-marker cx on initial render", () => {
    renderComponent(MU_INITIAL);
    const marker = screen.getByTestId("mu-marker");
    const label = screen.getByTestId("mu-label");
    expect(marker.getAttribute("cx")).toBe(label.getAttribute("x"));
  });

  it("mu-label x === mu-marker cx after a prop change (re-render with new mu)", () => {
    const { rerender } = renderComponent(3);
    rerender(<NumLineDirect data={DATA} mu={8} onMu={() => {}} />);
    const marker = screen.getByTestId("mu-marker");
    const label = screen.getByTestId("mu-label");
    // Both must be equal — no imperative path diverging from the React-rendered value.
    expect(marker.getAttribute("cx")).toBe(label.getAttribute("x"));
  });

  it("no imperative setAttribute divergence: cx attribute on marker matches expected toSvgX value", () => {
    // This test asserts the fix: cx on the circle must equal what toSvgX produces
    // from the React prop — not a stale imperative override.
    // Using the exported constants isn't straightforward, so we calculate inline:
    // SVG_W=480, PAD=24, USABLE=480-24*2=432, lo=min(DATA)-2=0, hi=max(DATA)+2=12
    // toSvgX(8, 0, 12) = 24 + (8/12)*432 = 24 + 288 = 312
    const { rerender } = renderComponent(3);
    rerender(<NumLineDirect data={DATA} mu={8} onMu={() => {}} />);
    const marker = screen.getByTestId("mu-marker");
    const label = screen.getByTestId("mu-label");

    const lo = Math.min(...DATA) - 2; // 0
    const hi = Math.max(...DATA) + 2; // 12
    const SVG_W = 480;
    const PAD = 24;
    const USABLE = SVG_W - PAD * 2;
    const expectedX = String(PAD + ((8 - lo) / (hi - lo)) * USABLE);

    expect(marker.getAttribute("cx")).toBe(expectedX);
    expect(label.getAttribute("x")).toBe(expectedX);
  });
});
