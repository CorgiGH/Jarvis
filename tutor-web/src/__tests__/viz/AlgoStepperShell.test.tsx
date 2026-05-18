import { describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { AlgoStepperShell, type Frame } from "../../components/viz/AlgoStepperShell";

type CounterState = { n: number };

const counterFrames: Frame<CounterState>[] = [
  { state: { n: 0 }, aria: "count is 0" },
  { state: { n: 1 }, aria: "count is 1" },
  { state: { n: 2 }, aria: "count is 2" },
];

const renderCounter = (f: Frame<CounterState>) => (
  <text data-testid="counter-readout" x={240} y={180}>
    {f.state.n}
  </text>
);

describe("AlgoStepperShell — initial render", () => {
  test("renders title, desc, and frame 0 via renderFrame", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo counter"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByText("Counter")).toBeInTheDocument();
    expect(screen.getByText("Demo counter")).toBeInTheDocument();
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("renderFrame is called with frame at current index", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo counter"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const readout = screen.getByTestId("counter-readout");
    expect(readout).toHaveTextContent("0");
  });

  test("materializes generator function into frame array on mount", () => {
    function* gen(): Generator<Frame<CounterState>> {
      yield { state: { n: 10 }, aria: "ten" };
      yield { state: { n: 20 }, aria: "twenty" };
    }
    render(
      <AlgoStepperShell
        title="Gen counter"
        desc="Generator demo"
        frames={gen}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("10");
  });
});

describe("AlgoStepperShell — stepping", () => {
  test("scrubber changes frame", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const scrubber = screen.getByTestId("stepper-scrubber") as HTMLInputElement;
    expect(scrubber.value).toBe("0");
    fireEvent.change(scrubber, { target: { value: "2" } });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });

  test("ArrowRight steps forward; ArrowLeft steps back", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    fireEvent.keyDown(svg, { key: "ArrowLeft" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("J/K keys step (vim-style)", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "k" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    fireEvent.keyDown(svg, { key: "j" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("step controls clamp at boundaries (no negative idx, no idx > last)", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowLeft" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
    for (let i = 0; i < 10; i++) {
      fireEvent.keyDown(svg, { key: "ArrowRight" });
    }
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });
});

describe("AlgoStepperShell — play/pause/reset", () => {
  test("play advances frames over time", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    const playBtn = screen.getByTestId("stepper-play");
    fireEvent.click(playBtn);
    act(() => { vi.advanceTimersByTime(105); });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    act(() => { vi.advanceTimersByTime(105); });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
    vi.useRealTimers();
  });

  test("R resets to frame 0 + pauses", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
    fireEvent.keyDown(svg, { key: "r" });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("Space toggles play/pause", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: " " });
    act(() => { vi.advanceTimersByTime(105); });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    fireEvent.keyDown(svg, { key: " " });
    act(() => { vi.advanceTimersByTime(500); });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.useRealTimers();
  });

  test("reduced-motion disables auto-tick (play steps once per click)", () => {
    const matchMediaSpy = vi
      .spyOn(window, "matchMedia")
      .mockImplementation((q) => ({
        matches: q === "(prefers-reduced-motion: reduce)",
        media: q,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
        onchange: null,
      }));
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    fireEvent.click(screen.getByTestId("stepper-play"));
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    act(() => { vi.advanceTimersByTime(500); });
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.useRealTimers();
    matchMediaSpy.mockRestore();
  });
});
