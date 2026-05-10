import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach } from "vitest";
import { CompareFrames } from "../../components/viz/CompareFrames";

const animateSpy = vi.fn().mockResolvedValue(undefined);

vi.mock("plotly.js-dist-min", () => ({
  default: { animate: animateSpy },
}));

beforeEach(() => { animateSpy.mockClear(); });

describe("CompareFrames", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders without crashing", () => { render(<CompareFrames data={data} />); });

  test("renders play button", () => {
    render(<CompareFrames data={data} />);
    expect(screen.getByTestId("compare-frames-play")).toBeInTheDocument();
  });

  test("play calls Plotly.animate with 3 frames", async () => {
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, frames] = animateSpy.mock.calls[0];
    expect(Array.isArray(frames)).toBe(true);
    expect(frames.length).toBe(3);
  });

  test("transition duration = 600ms by default", async () => {
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, , opts] = animateSpy.mock.calls[0];
    expect(opts.transition.duration).toBe(600);
  });

  test("transition duration = 0 under reduced-motion", async () => {
    const original = window.matchMedia;
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn(),
    }) as any;
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, , opts] = animateSpy.mock.calls[0];
    expect(opts.transition.duration).toBe(0);
    window.matchMedia = original;
  });
});
