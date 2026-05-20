import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { AlgoStepperShell, type Frame } from "../../components/viz/AlgoStepperShell";

// Reset location hash between every test to prevent hash state leakage
afterEach(() => {
  window.location.hash = "";
});

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

describe("AlgoStepperShell — predict-gate", () => {
  test("locks scrubber until answered, then unlocks", () => {
    const onAnswered = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        predictionGates={new Map([[0, {
          question: "Pick the right one",
          answers: [
            { label: "wrong", isCorrect: false },
            { label: "right", isCorrect: true },
          ],
          onAnswered,
        }]])}
      />
    );
    const scrubber = screen.getByTestId("stepper-scrubber") as HTMLInputElement;
    expect(scrubber.disabled).toBe(true);
    expect(screen.getByTestId("predict-gate")).toBeInTheDocument();
    fireEvent.click(screen.getByText("right"));
    expect(onAnswered).toHaveBeenCalledWith(true);
    expect(scrubber.disabled).toBe(false);
    expect(screen.queryByTestId("predict-gate")).toBeNull();
  });

  test("incorrect answer still unlocks but reports isCorrect=false", () => {
    const onAnswered = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        predictionGates={new Map([[0, {
          question: "Pick",
          answers: [
            { label: "wrong", isCorrect: false },
            { label: "right", isCorrect: true },
          ],
          onAnswered,
        }]])}
      />
    );
    fireEvent.click(screen.getByText("wrong"));
    expect(onAnswered).toHaveBeenCalledWith(false);
    expect(screen.queryByTestId("predict-gate")).toBeNull();
  });
});

describe("AlgoStepperShell — share-link", () => {
  beforeEach(() => {
    window.location.hash = "";
  });

  test("on mount, reads idx from hash if present", () => {
    window.location.hash = "#stepper-idx-2";
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });

  test("on step, writes idx to hash", () => {
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
    expect(window.location.hash).toBe("#stepper-idx-1");
  });

  test("share button calls onShare with current hash payload", () => {
    const onShare = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        onShare={onShare}
      />
    );
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    fireEvent.click(screen.getByTestId("stepper-share"));
    expect(onShare).toHaveBeenCalledWith("stepper-idx-1");
  });
});

describe("AlgoStepperShell — predictionGates (per-frame map)", () => {
  function gateFixture(): Map<number, import("../../components/viz/AlgoStepperShell").PredictionGate> {
    return new Map([
      [2, { question: "next?", answers: [{ label: "A", isCorrect: true }, { label: "B", isCorrect: false }] }],
    ]);
  }

  test("predictionGates: gate at frame 2 blocks advance past frame 2 until answered", () => {
    const frames = Array.from({ length: 6 }, (_, i) => ({ state: i, aria: `f${i}` }));
    render(
      <AlgoStepperShell
        title="t" desc="d" frames={frames}
        renderFrame={(f) => <text>{String(f.state)}</text>}
        predictionGates={gateFixture()}
        testIdPrefix="gt"
      />
    );
    fireEvent.click(screen.getByTestId("gt-step-fwd"));
    fireEvent.click(screen.getByTestId("gt-step-fwd"));
    expect(screen.getByTestId("gt-frame-counter").textContent).toContain("3 / 6");
    expect(screen.getByTestId("predict-gate")).toBeInTheDocument();
    expect(screen.getByTestId("gt-step-fwd")).toBeDisabled();
    fireEvent.click(screen.getByText("A"));
    expect(screen.queryByTestId("predict-gate")).toBeNull();
    fireEvent.click(screen.getByTestId("gt-step-fwd"));
    expect(screen.getByTestId("gt-frame-counter").textContent).toContain("4 / 6");
  });
});

describe("AlgoStepperShell — hash deep-link clamped to gate ceiling", () => {
  beforeEach(() => {
    window.location.hash = "#hg-idx-6";
  });

  afterEach(() => {
    window.location.hash = "";
  });

  test("hash past gate ceiling is clamped to first unanswered gate frame", () => {
    const frames = Array.from({ length: 8 }, (_, i) => ({ state: i, aria: `f${i}` }));
    render(
      <AlgoStepperShell
        title="t"
        desc="d"
        frames={frames}
        renderFrame={(f) => <text>{String(f.state)}</text>}
        testIdPrefix="hg"
        predictionGates={new Map([[2, { question: "q", answers: [{ label: "A", isCorrect: true }] }]])}
      />
    );
    // Hash was idx-6 (7th frame), but gate at frame 2 is unanswered.
    // initialIdx must be clamped to 2 → displayed as "3 / 8".
    expect(screen.getByTestId("hg-frame-counter").textContent).toContain("3 / 8");
  });
});

describe("AlgoStepperShell — ARIA live", () => {
  test("live region updates with frame.aria", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const live = screen.getByTestId("stepper-live");
    expect(live).toHaveTextContent("count is 0");
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    expect(live).toHaveTextContent("count is 1");
  });
});

describe("AlgoStepperShell — voice", () => {
  let originalAudio: typeof Audio;
  const audioInstances: Array<{ src: string | null; played: boolean; paused: boolean }> = [];

  beforeEach(() => {
    audioInstances.length = 0;
    originalAudio = window.Audio;
    window.Audio = class FakeAudio {
      _src: string | null = null;
      played = false;
      paused = true;
      onended: (() => void) | null = null;
      currentTime = 0;
      constructor() {
        const inst = { src: null as string | null, played: false, paused: true };
        audioInstances.push(inst);
        Object.defineProperty(this, "src", {
          get: () => inst.src,
          set: (v: string) => { inst.src = v; },
        });
        Object.defineProperty(this, "paused", { get: () => inst.paused });
        this.play = () => {
          inst.played = true;
          inst.paused = false;
          return Promise.resolve();
        };
        this.pause = () => {
          inst.paused = true;
        };
      }
      play: () => Promise<void>;
      pause: () => void;
    } as unknown as typeof Audio;
  });

  afterEach(() => {
    window.Audio = originalAudio;
  });

  test("voice off by default", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3", 1: "/b.mp3" }}
      />
    );
    const voiceBtn = screen.getByTestId("stepper-voice") as HTMLButtonElement;
    expect(voiceBtn.textContent?.toLowerCase()).toContain("off");
  });

  test("voice on + frame change triggers Audio.play with voiceMap url", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3", 1: "/b.mp3" }}
      />
    );
    fireEvent.click(screen.getByTestId("stepper-voice"));
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(audioInstances.some((a) => a.src === "/b.mp3" && a.played)).toBe(true);
    vi.useRealTimers();
  });

  test("voiceMap miss is a no-op", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3" }}
      />
    );
    fireEvent.click(screen.getByTestId("stepper-voice"));
    const svg = screen.getByRole("img");
    fireEvent.keyDown(svg, { key: "ArrowRight" });
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(audioInstances.some((a) => a.src === "/b.mp3")).toBe(false);
    vi.useRealTimers();
  });
});
